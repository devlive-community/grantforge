---
title: Agent Redis
description: Exécuter l’agent Redis à côté d’un déploiement Redis ou Valkey pour maintenir les sélecteurs ACL de chaque utilisateur sur chaque nœud en phase avec les politiques GrantForge, remplacés en une commande, demander à Redis avec ACL DRYRUN ce que permettent les droits hors de GrantForge, et signaler les refus depuis les journaux ACL des nœuds.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent Redis est un service à côté du déploiement. Il maintient l’ACL propre à Redis en phase avec les politiques signées d’un service de données `redis` sur chaque nœud, réplicas et nœuds d’un cluster compris, Redis tenant une ACL sur chacun. Redis vérifie ensuite chaque commande lui-même, et l’agent ne se place devant aucun client. La distribution le fournit sous forme de JAR exécutable `agents/redis/grantforge-agent-redis-<version>.jar` ; il nécessite Java 17 ou ultérieur.

Les tests d’intégration Testcontainers du dépôt exécutent l’agent, et le JAR tel que livré, sur un primaire avec un réplica de Redis `7.2`, `7.4`, `8.2` et `8.10` et de Valkey `8.1` et `9.1`, ainsi que sur un cluster Redis `8.10`. Cela ne certifie pas toutes les autres versions ; l’agent refuse Redis antérieur à `7.2`, dont le journal ACL ne numérote pas les entrées. Pour le plug-in de service, voir [Redis](/fr/plugins/redis/).

## Fonctionnement

- **Les sélecteurs sont à GrantForge, les droits racine à l’exploitant.** Redis autorise une commande quand les droits racine d’un utilisateur ou l’un de ses sélecteurs l’autorisent. L’agent tient tous les sélecteurs des utilisateurs dont il s’occupe et ne touche jamais à leurs droits racine ni à leurs mots de passe : un sélecteur contient les clés que l’utilisateur peut lire, écrire ou les deux, un les canaux auxquels il peut s’abonner, un ceux sur lesquels il peut publier, et sur les nœuds qu’il peut superviser, un de plus.
- **Un changement se fait en une seule commande.** `ACL SETUSER user clearselectors (…)` remplace d’un coup tous les sélecteurs d’un utilisateur sur un nœud : un utilisateur ne détient jamais à la fois les anciens et les nouveaux, ni aucun ; les sélecteurs modifiés à la main sont corrigés de même à la synchronisation suivante.
- **Des motifs, accordés seulement là où ils tiennent.** Un motif de clés ou de canaux d’une politique est accordé comme motif propre à Redis là où les politiques décident l’utilisateur de la même façon sur tout le motif ; l’agent découpe le motif le long de chaque politique qui le chevauche et décide chaque partie, de sorte que la politique d’un autre utilisateur ne le retient pas. Un motif à l’intérieur duquel les politiques décident autrement est retenu et journalisé une fois.
- **Rien de dangereux, pas de liste des clés d’autrui.** Les sélecteurs ne donnent jamais ce que Redis juge dangereux, et ne donnent `SCAN` et `RANDOMKEY` qu’à un utilisateur qui peut lire toutes les clés.
- **Chaque nœud, enregistré là où c’est possible.** L’agent trouve les réplicas et les nœuds du cluster à partir des nœuds indiqués et synchronise chacun. Un nœud qui garde son ACL dans un fichier l’enregistre avec `ACL SAVE` ; un nœud qui ne le fait pas perd les sélecteurs à son redémarrage et les retrouve à la synchronisation suivante.
- **On demande à Redis ce qu’il autorise.** Les droits racine décident à côté des sélecteurs de l’agent. Plutôt que de lire leurs règles, l’agent demande à Redis avec `ACL DRYRUN` si un utilisateur peut exécuter une commande sur une clé, un canal ou un nœud qu’une politique lui refuse, et avertit une fois de chaque cas, en nommant l’utilisateur, l’accès et la politique. Il avertit aussi des utilisateurs qu’aucun sélecteur ne lie, avec toutes les commandes sur toutes les clés, et des utilisateurs sous le nom desquels n’importe qui peut se connecter sans mot de passe.
- **Les refus viennent du journal ACL de chaque nœud.** Voir Signaler les refus plus bas.
- **Les politiques du moment présent.** Chaque synchronisation planifie d’après les politiques telles qu’elles s’appliquent à cet instant, de sorte que les périodes de validité commencent et finissent à l’heure, et d’après les utilisateurs et nœuds de Redis.

## Déploiement

1. Ajoutez un service de données `redis` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; conservez-le dans un fichier que seul le compte de l’agent peut lire.
2. Créez l’utilisateur ACL de l’agent sur chaque nœud. Il lit les utilisateurs, fixe leurs sélecteurs, enregistre le fichier ACL, lit le journal ACL, fait des essais à blanc et trouve les nœuds du cluster. `+acl|setuser` peut se donner n’importe quoi : protégez cet utilisateur comme un administrateur.

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. Limitez les droits racine des utilisateurs dont l’agent s’occupe à ce dont tout utilisateur a besoin, comme se connecter ; l’agent donne le reste. Les droits racine qui donnent davantage sont détectés et signalés.

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. Écrivez le fichier de paramètres en remplaçant les adresses, chemins et noms d’exemple :

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   Ces chemins sont sur la machine de l’agent, et le répertoire de cache doit être accessible en écriture. Les fichiers de mot de passe sont lus en UTF-8 et rognés.
5. Démarrez l’agent, par exemple comme service systemd :

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   Remplacez `<version>` par la version de la distribution. Les paramètres fournis sous la forme `java -Dgrantforge.…=… -jar …` remplacent ceux du fichier. L’agent s’arrête proprement sur SIGTERM et envoie ou met en file les événements en attente ; Redis garde ce qu’il a reçu.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour cet agent et un répertoire accessible en écriture pour l’instantané signé, les événements en file et la position de lecture du journal d’audit |
| `grantforge.signing.key.file` | Fichier facultatif qui fixe la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. S’il est omis, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.redis.nodes` | Obligatoire : les nœuds en `host:port`, le port `6379` s’il n’est pas indiqué |
| `grantforge.redis.discover` | `true` par défaut : le reste d’un cluster et les réplicas d’un primaire sont trouvés à partir des nœuds indiqués. `false` prend les nœuds indiqués et aucun autre, quand les adresses annoncées par les nœuds ne sont pas joignables depuis l’agent |
| `grantforge.redis.user`, `grantforge.redis.password.file` | L’utilisateur ACL de l’agent, vide pour l’utilisateur par défaut, et son mot de passe |
| `grantforge.redis.tls.mode`, `grantforge.redis.tls.ca.file`, `grantforge.redis.tls.certificate.file`, `grantforge.redis.tls.key.file`, `grantforge.redis.tls.key.password.file` | `disable` (par défaut), `verify-ca` ou `verify-full` ; les certificats CA PEM qui vérifient les serveurs, vide pour ce à quoi Java fait confiance ; un certificat client PEM, sa clé si elle a son propre fichier, et le mot de passe de la clé |
| `grantforge.redis.timeout.ms` | Durée permise pour se connecter et exécuter une commande, `10000` par défaut, dans la plage `1..600000` millisecondes |
| `grantforge.redis.unmanaged.users` | Les utilisateurs que l’agent laisse tranquilles, séparés par des virgules, comme des comptes de service. Il laisse toujours tranquilles son propre utilisateur et les utilisateurs avec toutes les commandes sur toutes les clés |
| `grantforge.sync.interval.ms` | Fréquence à laquelle l’agent met Redis en phase, par défaut `10000`, plage `1000..3600000` millisecondes |
| `grantforge.audit.log`, `grantforge.audit.log.interval.ms` | `true` par défaut ; `false` ne lit aucun journal ACL. Fréquence de lecture des journaux ACL, `5000` millisecondes par défaut |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite de file sur disque (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` envoie chacun |

## Signaler les refus

Redis ne garde aucune trace de ce qu’il a autorisé ; l’agent signale donc ce qu’il a refusé, depuis le journal ACL de chaque nœud : qui s’est vu refuser quoi, d’où et quand, et si les politiques décident comme Redis. Redis regroupe un refus répété en moins d’une minute avec l’entrée précédente ; l’agent signale de nouveau une entrée chaque fois qu’elle est mise à jour.

| Motif journalisé | Événement d’accès |
| --- | --- |
| `key` | `read` ou `write` sur la clé, selon le marquage de la commande par Redis |
| `channel` | `subscribe` ou `publish` sur le canal |
| `command` | `monitor` sur le nœud pour les commandes de supervision ; les autres commandes sont journalisées sans leurs clés et sont omises |
| `auth` | Omis : une authentification refusée ne nomme aucun accès |

## Utilisateurs, groupes et rôles

Les politiques nomment les utilisateurs ACL de Redis par leur nom. Redis n’a ni rôles ni groupes propres ; les groupes sont donc ceux où GrantForge place les utilisateurs.

## Décisions et limites

- Sans instantané, l’agent ne change rien. Un jeton lié à un service non utilisé, ou à un service dont le type n’est pas `redis`, n’accorde rien : l’agent vide les sélecteurs des utilisateurs dont il s’occupe.
- Tant que l’agent ne s’exécute pas, Redis garde ce qu’il a reçu en dernier et les changements de politiques attendent. Un changement atteint Redis dans les intervalles d’actualisation et de synchronisation.
- L’agent tient tous les sélecteurs des utilisateurs dont il s’occupe : un sélecteur ajouté à la main par l’exploitant est supprimé à la synchronisation suivante. Donnez les droits supplémentaires dans les droits racine, ou laissez l’utilisateur à l’exploitant.
- L’ACL de Redis nomme des clés et des canaux, pas des bases ; un motif couvre donc ses clés dans chaque base d’un nœud.
- Une commande refusée que l’utilisateur ne peut pas exécuter du tout est journalisée par Redis sans ses clés, et n’est pas signalée.
- Redis ne filtre rien à l’intérieur d’une valeur et ne masque rien.

## Guides associés

- [Redis](/fr/plugins/redis/)
- [Services de données, politiques et agents](/fr/external/data-services/)
