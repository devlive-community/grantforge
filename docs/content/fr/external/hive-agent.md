---
title: Agent Apache Hive
description: Installer l’agent dans HiveServer2 pour contrôler les instructions, filtrer SHOW, masquer les colonnes et filtrer les lignes avec les politiques GrantForge.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent Hive est un autorisateur de l’API d’autorisation de Hive, comme le plug-in Hive d’Apache Ranger : HiveServer2 l’interroge avant chaque instruction, et il répond d’après les politiques signées du service de données `hive`. La distribution le fournit sous la forme d’un JAR unique compatible Java 8, `agents/hive/grantforge-agent-hive-<version>.jar`, avec ses dépendances relocalisées à l’intérieur.

Les tests d’intégration Testcontainers du dépôt exécutent le JAR dans HiveServer2 `3.1.3`, `4.0.1` et `4.1.0`. Les autres versions ne sont pas certifiées pour autant. Le plug-in de service est décrit dans [Apache Hive](/fr/plugins/hive/).

## Fonctionnement

- **Les instructions sont contrôlées.** Pour chaque objet qu’une instruction lit ou écrit, l’agent demande aux politiques le type d’accès que l’opération exige, comme Ranger ; le premier refus fait échouer l’instruction avec `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`. Les requêtes sont contrôlées colonne par colonne.
- **Les listes sont filtrées.** `SHOW DATABASES` et `SHOW TABLES` ne listent que ce que l’utilisateur peut utiliser : un accès quelconque à la base ou à la table, ou à quelque chose qu’elle contient. `USE` et `DESCRIBE DATABASE` exigent la même chose.
- **Les requêtes sont masquées et filtrées.** Lorsque l’utilisateur a des colonnes masquées ou un filtre de lignes sur une table, HiveServer2 réécrit la requête avec les masques et la condition, de sorte que jointures, conditions et fonctions ne voient que ce que l’utilisateur peut voir.
- **Les masques ne se contournent pas.** Écrire, modifier ou exporter une table sur laquelle l’utilisateur a un masque ou un filtre de lignes est refusé, comme Ranger le refuse par défaut.
- **Les droits et les rôles relèvent de GrantForge.** `GRANT`, `REVOKE`, `SHOW GRANT`, `CREATE ROLE`, `SET ROLE` et les autres instructions de rôle de Hive sont refusés ; `SHOW CURRENT ROLES` liste les rôles que GrantForge donne à l’utilisateur.
- **Les instructions dangereuses sont refusées.** Les commandes `dfs`, qui atteindraient le système de fichiers avec les droits propres de HiveServer2, sont refusées. Dans les sessions HiveServer2, les scripts `TRANSFORM` sont interdits et les utilisateurs ne peuvent modifier par `SET` que les paramètres que permet l’autorisation SQL standard de Hive.
- **Chaque décision est signalée** à GrantForge avec l’utilisateur, l’adresse du client, la ressource, le type d’accès, le résultat, l’opération et l’instruction.

| Opération | Type d’accès |
| --- | --- |
| Requêtes, `SHOW CREATE TABLE`, `SHOW PARTITIONS`, `SHOW TBLPROPERTIES`, `DESCRIBE`, `SHOW COLUMNS`, `ANALYZE` | `select` |
| `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `LOAD` (cible) | `update` |
| `CREATE DATABASE`, `CREATE TABLE`, `CREATE VIEW`, `CREATE FUNCTION` | `create` sur ce qui est créé, `select` sur ce qui est lu |
| `ALTER …`, `MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`, `UNLOCK` | `lock` |
| `SHOW DATABASES`, `SHOW TABLES`, `USE`, `DESCRIBE DATABASE` | Un accès quelconque à l’objet ou à ce qu’il contient |
| `REPL DUMP`, `REPL LOAD`, `REPL STATUS` | `repladmin` |
| `KILL QUERY`, plans de ressources, administration LLAP | `serviceadmin` sur `hiveservice` |
| Fonctions temporaires, `ADD JAR`, `COMPILE` | `tempudfadmin` sur `global` |
| Emplacements qu’une instruction lit ou écrit | `read` ou `write` sur `url` |

## Déploiement

1. Ajoutez un service de données `hive` et configurez ses politiques, dont une qui autorise aux utilisateurs sous lesquels HiveServer2 effectue son propre travail, comme `hive`, tout ce dont ils ont besoin. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; stockez-le dans un fichier que seul le compte de HiveServer2 peut lire.
2. Copiez le JAR dans `lib` de **chaque HiveServer2**, par exemple `/opt/hive/lib`.
3. Ajoutez les paramètres dans `hiveserver2-site.xml` ou `hive-site.xml` de chaque HiveServer2, en remplaçant l’adresse, les chemins et le nom d’instance d’exemple :

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   Le répertoire de cache doit être accessible en écriture au compte de HiveServer2. Comme avec Ranger, `hive.server2.enable.doAs=false` laisse HiveServer2 lire et écrire les données sous son propre compte, pour que les politiques décident qui voit quoi.
4. Redémarrez HiveServer2. L’agent démarre avec la première session et s’arrête avec le serveur, en envoyant ou en mettant en file ses événements en attente.
5. **Fermez les chemins qui contournent HiveServer2 :** l’agent ne contrôle que les instructions exécutées dans HiveServer2. Les clients qui atteignent directement le metastore ou les fichiers de l’entrepôt, comme la CLI Hive, Spark ou les clients HDFS, ne sont pas contrôlés ; protégez-les avec les droits propres du metastore et de HDFS, ou avec l’[agent HDFS](/fr/external/hdfs-agent/).

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour ce HiveServer2 et un répertoire accessible en écriture pour l’instantané signé et les événements en file |
| `grantforge.signing.key.file` | Fichier facultatif qui épingle la clé de signature des instantanés ; copiez la clé publique Ed25519 X.509 encodée en Base64 depuis la page Agents. Sans lui, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, `5000` / `8000` millisecondes par défaut |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, `30000` par défaut, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille des lots (`500` par défaut), taille de la file (`10000` par défaut), intervalle d’envoi (`5000` millisecondes par défaut) et limite de mise en file (64 Mio par défaut) |

L’agent lit ses paramètres dans les fichiers de configuration de HiveServer2 et dans les propriétés système (`-Dgrantforge.…`, prioritaires), jamais dans les paramètres d’une session, qu’un client peut choisir.

## Utilisateurs, groupes et rôles

Les politiques nomment l’utilisateur que HiveServer2 attribue à la session : le nom d’utilisateur avec lequel un client se connecte, ou le nom court de son principal Kerberos (`alice` pour `alice@EXAMPLE.COM`). Les groupes sont ceux que la correspondance de groupes de Hadoop donne à l’utilisateur dans HiveServer2, avec les groupes où GrantForge place l’utilisateur ; les rôles sont ceux que GrantForge donne à l’utilisateur.

## Décisions et limites

- Sans instantané utilisable, ou lorsque les politiques ne peuvent pas être lues, toute instruction est refusée. Un service non utilisé laisse HiveServer2 sans contrôle, comme sans autorisateur.
- Un jeton lié à un service dont le type n’est pas `hive` refuse tout.
- Les politiques `url` décident des emplacements que les instructions lisent et écrivent ; contrairement à Ranger, l’agent ne se rabat pas sur les droits du système de fichiers.
- Les instructions pour lesquelles Hive ne nomme aucun objet sont contrôlées sur toutes les bases : les instructions `REPL` sans objet exigent `repladmin`, le changement de propriétaire d’une table ou d’une base sans objet exige `alter`, sur la base `*`.
- `EXPLAIN`, `DESCRIBE FUNCTION`, `SHOW FUNCTIONS`, `SET` et les autres instructions que Ranger ne contrôle pas ne sont pas contrôlées non plus ; les appels de métadonnées des clients JDBC, comme `getTables`, ne sont pas filtrés.
- L’agent fonctionne dans HiveServer2. L’autorisation au sein du metastore n’est pas couverte.

## Guides associés

- [Apache Hive](/fr/plugins/hive/)
- [Services de données, politiques et agents](/fr/external/data-services/)
