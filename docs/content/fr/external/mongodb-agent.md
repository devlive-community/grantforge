---
title: Agent MongoDB
description: Exécuter l’agent MongoDB à côté d’un déploiement pour transformer les politiques GrantForge en rôles propres à MongoDB, remplacer les privilèges d’un rôle en une seule étape, lire ce que donnent les rôles intégrés et personnalisés hors de GrantForge, et signaler les accès depuis le journal d’audit ou le profiler.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent MongoDB est un service à côté du déploiement. Il maintient le contrôle d’accès de MongoDB en phase avec les politiques signées d’un service de données `mongodb` : un rôle par utilisateur, qui contient exactement ce que les politiques lui donnent. MongoDB vérifie ensuite chaque commande lui-même, et l’agent ne se place devant aucun client. La distribution le fournit sous forme de JAR exécutable `agents/mongodb/grantforge-agent-mongodb-<version>.jar` ; il nécessite Java 17 ou ultérieur.

Les tests d’intégration Testcontainers du dépôt exécutent l’agent, et le JAR tel que livré, sur des replica sets MongoDB `6.0`, `7.0`, `8.0` et `9.0` et un cluster partitionné `9.0`, et lisent le journal d’audit de Percona Server for MongoDB `7.0` et `8.0`. Cela ne certifie pas toutes les autres versions ; l’agent refuse les déploiements antérieurs à `6.0`. Pour le plug-in de service, voir [MongoDB](/fr/plugins/mongodb/).

## Fonctionnement

- **Un rôle par utilisateur contient ce que donne GrantForge.** Pour chaque utilisateur à qui les politiques donnent quelque chose, l’agent maintient dans `admin` un rôle à son nom, `grantforge_alice` pour `alice`, et l’accorde à chaque utilisateur MongoDB de ce nom, comme `alice@admin` et `alice@sales`. `rolesInfo` montre ce que GrantForge donne à `alice`. L’agent ne modifie aucun autre rôle.
- **Un changement se fait en une seule étape.** `updateRole` remplace d’un coup les privilèges d’un rôle : un utilisateur ne détient jamais à la fois les anciens et les nouveaux, ni aucun des deux.
- **Des bases entières là où rien en dessous n’en décide autrement.** Un type d’accès qu’une politique permet sur toute une base, sans politique pouvant le retirer en dessous, est accordé sur la base, de sorte que les collections créées plus tard l’ont aussitôt. Sinon il est accordé collection par collection, et les nouvelles sont couvertes à la synchronisation suivante.
- **Jamais plus que ce que les politiques permettent.** Le privilège de MongoDB de supprimer une base est le privilège de supprimer chacune de ses collections. L’agent ne l’accorde que là où les politiques donnent aussi cela ; sinon il le retient et journalise une fois quel utilisateur ne peut pas faire quoi et pourquoi.
- **Les politiques du moment présent.** Chaque synchronisation planifie d’après les politiques telles qu’elles s’appliquent à cet instant, de sorte que les périodes de validité commencent et finissent à l’heure, et d’après les utilisateurs, bases et collections de MongoDB, de sorte que les nouveaux sont couverts à la synchronisation suivante.
- **Les rôles MongoDB sont des groupes.** Une politique pour un groupe s’applique aux utilisateurs qui détiennent un rôle MongoDB de ce nom, ainsi qu’aux groupes où GrantForge les place.
- **Les changements manuels sont annulés.** À chaque synchronisation, l’agent compare ses rôles aux plans et corrige les privilèges modifiés à la main, les rôles qu’on leur a fait hériter, les attributions d’un rôle à quelqu’un d’autre et les rôles dont personne n’a besoin. Si rien n’a changé, il n’envoie rien.
- **Les rôles hors de GrantForge sont lus pour ce qu’ils donnent.** MongoDB additionne ce que donnent tous les rôles d’un utilisateur ; l’agent ne peut donc pas retirer ce qu’un utilisateur détient par un autre rôle. MongoDB indique ce que donne chaque rôle, y compris les rôles intégrés et hérités, de sorte que l’agent avertit, une fois chacun, d’un rôle comme `readWrite@hr` qui donne ce qu’une politique refuse, en nommant l’utilisateur, l’accès et la politique. Il avertit aussi des utilisateurs qui peuvent administrer les utilisateurs, et donc se donner n’importe quoi, des utilisateurs avec `root` ou toute action sur toute ressource, et d’un serveur qui ne vérifie aucune autorisation.
- **Les accès viennent du journal d’audit ou du profiler.** Voir Signaler les accès plus bas. Chaque événement indique si GrantForge a décidé, c’est-à-dire si les politiques décident comme MongoDB, ou si un rôle extérieur a permis la commande. Une agrégation est signalée pour chaque collection que ses étapes `$lookup`, `$graphLookup` et `$unionWith` lisent et que `$out` et `$merge` écrivent. Les événements nomment la commande et son espace de noms, jamais ses arguments, qui peuvent contenir des documents et des mots de passe.

## Déploiement

1. Ajoutez un service de données `mongodb` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; conservez-le dans un fichier que seul le compte de l’agent peut lire.
2. Créez l’utilisateur MongoDB de l’agent. Il crée et modifie des rôles et lit les utilisateurs et les rôles avec `userAdminAnyDatabase` ; liste les collections avec un rôle qui ne contient que cela ; et lit les options du serveur et le profiler avec `clusterMonitor`. `userAdminAnyDatabase` peut se donner n’importe quoi : protégez cet utilisateur comme un administrateur. Le nom du rôle de liste ne doit pas commencer par le préfixe de rôles de l’agent.

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. Pour signaler les accès, configurez l’une des sources de Signaler les accès plus bas.
4. Écrivez le fichier de paramètres en remplaçant les adresses, chemins et noms d’exemple :

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   Ces chemins sont sur la machine de l’agent, et le répertoire de cache doit être accessible en écriture. Les fichiers de mot de passe sont lus en UTF-8 et rognés.
5. Démarrez l’agent, par exemple comme service systemd :

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   Remplacez `<version>` par la version de la distribution. Les paramètres fournis sous la forme `java -Dgrantforge.…=… -jar …` remplacent ceux du fichier. L’agent s’arrête proprement sur SIGTERM et envoie ou met en file les événements en attente ; MongoDB garde ce qu’il a reçu.
6. **Migrer les privilèges existants vers des politiques :** les rôles accordés hors de GrantForge continuent de s’appliquer à côté de ceux de l’agent. Créez les politiques correspondantes, puis révoquez ces rôles. Les avertissements de l’agent indiquent ceux qui donnent ce qu’une politique refuse.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour cet agent et un répertoire accessible en écriture pour l’instantané signé, les événements en file et la position de lecture du journal d’audit |
| `grantforge.signing.key.file` | Fichier facultatif qui fixe la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. S’il est omis, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.mongodb.uri` | Obligatoire : la chaîne de connexion, sans identifiants |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256` (par défaut), `SCRAM-SHA-1`, `MONGODB-X509`, `PLAIN` ou `GSSAPI` |
| `grantforge.mongodb.user`, `grantforge.mongodb.password.file`, `grantforge.mongodb.auth.source` | L’utilisateur de l’agent, avec `GSSAPI` son principal et avec `MONGODB-X509` facultatif ; son mot de passe ; et pour SCRAM la base où il est défini, `admin` par défaut |
| `grantforge.mongodb.tls.ca.file`, `grantforge.mongodb.tls.certificate.key.file`, `grantforge.mongodb.tls.certificate.key.password.file` | Avec `tls=true` dans la chaîne de connexion : les certificats CA PEM qui vérifient les serveurs, vide pour ce à quoi Java fait confiance ; un fichier PEM contenant le certificat client et sa clé ; et le mot de passe d’une clé chiffrée |
| `grantforge.mongodb.kerberos.keytab`, `grantforge.mongodb.kerberos.service.name` | `GSSAPI` : le keytab du principal, sinon le mot de passe est utilisé, et le nom de service du principal des serveurs, `mongodb` par défaut. Le royaume vient du `krb5.conf` de la machine |
| `grantforge.mongodb.timeout.ms` | Durée permise pour trouver un serveur, se connecter et exécuter une commande, `30000` par défaut, dans la plage `1..600000` millisecondes |
| `grantforge.mongodb.role.prefix` | Ce par quoi commencent les noms des rôles de l’agent, `grantforge_` par défaut. Lettres, chiffres et traits de soulignement, finissant par un trait de soulignement, 32 caractères au plus. Tout rôle de `admin` dont le nom commence ainsi appartient à l’agent |
| `grantforge.mongodb.unmanaged.users` | Les utilisateurs que l’agent laisse tranquilles, séparés par des virgules, comme des comptes de service. Il laisse toujours tranquilles son propre utilisateur et les utilisateurs avec `root`, `__system` ou toute action sur toute ressource |
| `grantforge.mongodb.databases` | Les bases où l’agent accorde par collection, séparées par des virgules, vide pour toutes. Les autres ne reçoivent que ce qui peut être accordé globalement, afin qu’une base ayant de très nombreuses collections ne soit pas listée à chaque synchronisation |
| `grantforge.mongodb.audit.files` | Les fichiers du journal d’audit à lire, séparés par des virgules. Vide lit le profiler |
| `grantforge.sync.interval.ms` | Fréquence à laquelle l’agent met MongoDB en phase, par défaut `10000`, plage `1000..3600000` millisecondes |
| `grantforge.audit.log` | `true` par défaut. `false` ne lit ni ne signale aucun accès |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | De combien le profiler est lu en retard sur le présent (`2000` par défaut), à quelle fréquence les accès sont lus (`5000` millisecondes par défaut) et le nombre maximal d’entrées qu’une lecture prend d’un fichier ou d’une base (`1000` par défaut) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite de file sur disque (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` envoie chacun |

## Signaler les accès

| Source | Où | Ce qu’elle indique | Comment l’activer |
| --- | --- | --- | --- |
| Journal d’audit | MongoDB Enterprise et Percona Server for MongoDB | Ce qui a été permis et ce qui a été refusé, sur chaque membre dont l’agent peut lire le fichier | Démarrez `mongod` avec `--auditDestination file --auditFormat JSON --auditPath <path>` et `--setParameter auditAuthorizationSuccess=true`, et nommez les fichiers dans `grantforge.mongodb.audit.files` |
| Profiler | Toutes les éditions | Ce qui s’est exécuté sur le membre auquel l’agent parle, le primaire d’un replica set ; aucun refus | `db.setProfilingLevel(2)` dans chaque base à signaler. Cela coûte au serveur, donc l’agent ne l’active jamais, et `system.profile` est une petite collection plafonnée |

Le journal d’audit est lu depuis des fichiers : exécutez l’agent sur le nœud ou donnez-lui un répertoire qui les rassemble. Les tests lisent le journal d’audit de Percona Server, qui a le format de MongoDB Enterprise. Un `mongos` n’a pas de profiler ; un cluster partitionné ne signale donc les accès que depuis le journal d’audit.

| Commande | Événement d’accès |
| --- | --- |
| `find`, `aggregate`, `count`, `distinct`, `listIndexes` | `read` sur chaque collection lue |
| `aggregate` commençant par `$changeStream` | `change_stream` |
| `insert`, `update`, `delete`, `findAndModify`, `bulkWrite` | `insert`, `update` ou `remove` sur chaque collection écrite ; aussi `bypass_validation` si demandé |
| `createIndexes`, `dropIndexes` | `create_index`, `drop_index` |
| `collMod`, `validate`, `convertToCapped` | `alter` |
| `drop`, `create`, `dropDatabase` | `drop`, `create`, `drop_database` |
| `listDatabases` ; `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `currentOp` | `list_databases` ; `monitor` |

MongoDB refuse une commande dans son ensemble sans dire laquelle de ses collections en est la cause ; un refus est donc signalé pour celles que les politiques refusent, ou pour la première si elles n’en refusent aucune.

## Utilisateurs, groupes et rôles

Les politiques nomment les utilisateurs MongoDB par leur nom d’utilisateur. Un nom défini dans plusieurs bases est un seul utilisateur, et le rôle de l’agent est accordé à tous. Les groupes sont les rôles MongoDB d’un utilisateur et les groupes où GrantForge le place. Les rôles sont ceux que GrantForge donne à l’utilisateur.

## Décisions et limites

- Sans instantané, l’agent ne change rien. Un jeton lié à un service non utilisé, ou à un service dont le type n’est pas `mongodb`, n’accorde rien : l’agent supprime ses rôles, et ceux de MongoDB restent.
- Tant que l’agent ne s’exécute pas, MongoDB garde ce qu’il a reçu en dernier et les changements de politiques attendent. Un changement atteint MongoDB dans les intervalles d’actualisation et de synchronisation ; un `mongos` l’applique une fois son cache d’utilisateurs expiré, par défaut sous 30 secondes (`userCacheInvalidationIntervalSecs`).
- Les privilèges de MongoDB nomment exactement les bases et les collections ; un caractère générique d’une politique est donc accordé pour ce qui existe. Une base existe dès qu’elle a une collection : jusque-là, `create` sur une base qu’une politique nomme n’a rien sur quoi être accordé.
- Les utilisateurs définis nulle part dans MongoDB, comme ceux que l’autorisation LDAP associe elle-même à des rôles, ne sont pas vus, et l’agent ne maintient aucun rôle pour eux.
- Un privilège sur une base entière couvre ses collections mais pas ses collections `system.`, et les bases propres à MongoDB restent à MongoDB.
- Le renommage de collections et les autres actions d’administration de MongoDB ne sont pas des types d’accès : ils restent aux rôles hors de GrantForge.
- MongoDB ne filtre aucune ligne et ne masque aucun champ par rôle ; une [vue](/fr/plugins/mongodb/) donne une lecture restreinte.

## Guides associés

- [MongoDB](/fr/plugins/mongodb/)
- [Services de données, politiques et agents](/fr/external/data-services/)
