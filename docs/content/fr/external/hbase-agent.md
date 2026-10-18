---
title: Agent Apache HBase
description: Installer le coprocesseur dans les masters et region servers HBase pour vérifier opérations, lectures et écritures avec les politiques GrantForge, ne renvoyer que les colonnes qu’un utilisateur peut lire et masquer ce qu’il ne peut pas utiliser.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent HBase est un coprocesseur des masters, des régions et des region servers : HBase l’interroge sur chaque opération, lecture et écriture, et il répond à partir des politiques signées du service de données `hbase`. La distribution le fournit sous forme d’un JAR compatible Java 8, `agents/hbase/grantforge-agent-hbase-<version>.jar`, avec ses dépendances relocalisées à l’intérieur.

Les tests d’intégration Testcontainers du dépôt exécutent le JAR dans HBase `2.5.16`, `2.6.7` et `3.0.0`. Cela ne certifie pas toutes les autres versions. Voir [Apache HBase](/fr/plugins/hbase/) pour le plug-in de service.

## Fonctionnement

- **Chaque requête est vérifiée à chaque portée qu’elle nomme.** Dans HBase, une autorisation couvre tout ce qui relève de sa portée ; l’agent décide donc une requête à son espace de noms, sa table, sa famille de colonnes et sa colonne : un refus à l’une d’elles la refuse, une autorisation à l’une d’elles l’accorde.
- **Les propriétaires d’un espace de noms en ont les tables.** `create` et `admin` sur un espace de noms permettent de le créer, d’y créer des tables et de les administrer, sans politique par table.
- **Les lectures renvoient ce qui peut être lu.** Quand un utilisateur ne peut lire qu’une partie d’une table, comme une famille de colonnes, un get ou un scan renvoie ces colonnes au lieu d’échouer ; un utilisateur qui ne peut rien lire de la table est refusé.
- **Les listes omettent ce qui ne peut pas être utilisé.** Lister les espaces de noms et les tables ne montre que ceux que l’utilisateur peut utiliser, ou dans lesquels il peut utiliser quelque chose, et lister les instantanés que ceux des tables qu’il administre ; décrire un espace de noms qu’il ne peut pas utiliser est refusé.
- **Les refus disent pourquoi.** Le client reçoit `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)`, ou la politique qui l’a refusé.
- **Les décisions sont conservées et les accès regroupés.** Les décisions de l’instantané courant sont conservées, par défaut 100 000 pendant 10 secondes, et un nouvel instantané repart de zéro. Les accès autorisés identiques au sein d’une fenêtre, de 10 secondes par défaut, sont signalés comme un seul événement d’accès qui indique leur nombre ; les refus sont signalés un par un, immédiatement.
- **Les serveurs ne sont pas vérifiés.** Les super-utilisateurs de HBase, nommés dans `hbase.superuser`, et le travail propre aux serveurs ne sont ni vérifiés ni audités. Les tables de l’espace de noms `hbase`, comme `hbase:meta`, sont lisibles par tous, comme les clients en ont besoin.

| Opération | Accès requis |
| --- | --- |
| Get, scan | `read` sur la table, ou sur les colonnes renvoyées |
| Put, increment, append, delete de colonnes | `write` sur chaque colonne écrite |
| Delete d’une famille de colonnes ou d’une ligne | `write` sur chaque famille de colonnes, car HBase supprime une ligne famille par famille |
| Check-and-mutate | `read` sur la colonne comparée, ou sur la table pour un filtre, et `write` sur ce qui est écrit |
| Création de table | `create` sur l’espace de noms |
| Suppression, troncature, modification, activation, désactivation, flush, split, fusion, compaction | `admin` ou `create` sur la table |
| Déplacement, affectation, désaffectation, mise hors ligne | `admin` sur la table |
| Créer, restaurer, supprimer ou lister un instantané | `admin` sur la table ; le clonage exige aussi `create` sur l’espace de noms cible |
| Chargement en masse | `create` sur la table |
| Créer, modifier, supprimer un espace de noms | `admin` sur l’espace de noms |
| Décrire un espace de noms ; lister espaces de noms et tables | Un accès quelconque à lui ou en son sein |
| Équilibreur, arrêt, quotas, pairs de réplication, groupes de region servers, procédures, mise hors service, arrêt des region servers, rotation des WAL | `admin` sur `global` |

## Déploiement

1. Ajoutez un service de données `hbase` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; stockez-le dans un fichier que seul le compte de HBase peut lire.
2. Copiez le JAR dans `lib` de **chaque master et region server**, par exemple `/opt/hbase/lib`.
3. Ajoutez les paramètres au `hbase-site.xml` de chacun, en remplaçant l’adresse, les chemins et le nom d’instance d’exemple :

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   Le répertoire de cache doit être accessible en écriture au compte de HBase. Remplacez le `AccessController` propre à HBase dans les listes de coprocesseurs plutôt que de charger les deux.
4. Redémarrez les masters et les region servers un par un. Tant que l’agent n’a pas son premier instantané, les requêtes autres que celles des super-utilisateurs sont refusées.
5. **Migrez d’abord les autorisations existantes :** les droits que le `AccessController` de HBase a stockés dans `hbase:acl` ne s’appliquent plus. Listez-les au préalable avec `user_permission` et créez les politiques correspondantes.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour ce master ou region server et un répertoire accessible en écriture pour l’instantané signé et les événements en file |
| `grantforge.signing.key.file` | Fichier facultatif qui épingle la clé de signature des instantanés ; copiez la clé publique Ed25519 X.509 encodée en Base64 depuis la page Agents. Sans lui, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, `5000` / `8000` millisecondes par défaut |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, `30000` par défaut, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille des lots (`500` par défaut), taille de la file (`10000` par défaut), intervalle d’envoi (`5000` millisecondes par défaut) et limite de mise en file (64 Mio par défaut) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle les accès autorisés identiques sont regroupés en un événement, `10000` par défaut, au plus `3600000` millisecondes ; `0` signale chacun |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Nombre maximal de décisions conservées, `100000` par défaut, au plus `10000000`, `0` n’en conserve aucune ; et leur durée, `10000` par défaut, `1..3600000` millisecondes |

HBase démarre un coprocesseur pour le master, pour le region server et pour chaque région qu’il ouvre ; tous partagent un seul agent, un cache de décisions et le regroupement des événements.

## Utilisateurs, groupes et rôles

Les politiques nomment le nom court que HBase attribue à l’utilisateur de la connexion : le nom d’utilisateur de l’authentification simple, ou le nom court d’un principal Kerberos selon `hadoop.security.auth_to_local`. Les groupes sont ceux que l’association de groupes de Hadoop attribue à l’utilisateur sur le serveur, avec les groupes où GrantForge le place ; les rôles sont ceux que GrantForge lui attribue.

## Décisions et limites

- Sans instantané utilisable, tout est refusé hormis les requêtes des super-utilisateurs et les lectures de l’espace de noms `hbase`. Un service non utilisé laisse HBase sans contrôle, comme sans autoriseur.
- Un jeton lié à un service dont le type n’est pas `hbase` refuse tout.
- Les commandes `grant`, `revoke` et `user_permission` de HBase échouent avec *AccessController is not loaded* : les autorisations sont des politiques GrantForge.
- Les appels aux points d’accès des coprocesseurs ne sont pas vérifiés ; ne chargez que des points d’accès de confiance.
- Ici, HBase ne donne au créateur d’une table aucune autorisation propre ; une politique d’espace de noms, comme ci-dessus, donne leurs tables à ses propriétaires.
- HBase journalise certains refus au lieu de les renvoyer, comme celui de l’interrupteur de l’équilibreur : l’interrupteur reste tel quel, mais le client n’en est pas informé.
- Les décisions sont conservées pendant `grantforge.decision.cache.ttl.ms` : la période de validité d’une politique commence ou se termine donc jusqu’à autant plus tard.
- Les autorisations au niveau des cellules et les étiquettes de visibilité de HBase ne sont pas couvertes. Les colonnes sont comparées en texte UTF-8.

## Guides associés

- [Apache HBase](/fr/plugins/hbase/)
- [Services de données, politiques et agents](/fr/external/data-services/)
