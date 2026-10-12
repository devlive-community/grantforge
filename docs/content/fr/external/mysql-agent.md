---
title: Agent proxy MySQL
description: Déployer le proxy réseau MySQL, configurer TLS et la synchronisation des politiques, puis vérifier les droits sur les colonnes, le masquage, les filtres de lignes et les erreurs de refus.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent MySQL est un proxy réseau indépendant : les clients s’y connectent à la place du serveur, et il vérifie les instructions prises en charge selon les politiques signées du service de données `mysql` avant leur arrivée dans MySQL. Il ne nécessite aucune installation dans MySQL. La distribution fournit le JAR exécutable `agents/mysql/grantforge-agent-mysql-<version>.jar`, qui nécessite Java 17 ou une version ultérieure.

Les tests d’intégration Testcontainers du dépôt ciblent `mysql:8.0` et `mysql:8.4` avec les clients MariaDB Connector/J et MySQL Connector/J. Cela ne certifie ni d’autres versions du serveur ni toutes les fonctions du protocole MySQL. Consultez [MySQL](/fr/plugins/mysql/) pour les exigences de version et de connexion du plug-in de service.

## Fonctionnement

- **La connexion passe telle quelle.** Le message d’accueil du serveur parvient au client et les réponses du client au serveur ; MySQL vérifie donc lui-même les mots de passe (`caching_sha2_password` et `mysql_native_password`) et le proxy apprend l’utilisateur que MySQL a accepté. Les politiques nomment cet utilisateur, sans son hôte.
- **Les ressources SQL sont vérifiées.** Le proxy analyse COM_QUERY et les instructions préparées côté serveur, puis vérifie les types d’accès requis sur leurs bases, tables et colonnes. Une instruction refusée n’atteint pas MySQL ; le proxy produit une erreur avec un code compatible MySQL, comme 1142 ou 1143.
- **Les lectures sont masquées et filtrées.** Quand une instruction lit une table qui a des colonnes masquées ou un filtre de lignes pour l’utilisateur, le proxy la remplace par une table dérivée du même nom qui montre les valeurs masquées et seulement les lignes filtrées ; jointures, conditions et fonctions voient ainsi ce que l’utilisateur peut voir.
- Les **événements d’autorisation des ressources** sont envoyés à GrantForge avec l’utilisateur, l’adresse du client, la ressource, le type d’accès, la décision et l’instruction. La portée de l’audit est décrite ci-dessous.
- Les résultats d’exécution de MySQL sont transmis au client ; le proxy ne modifie pas les valeurs originales stockées dans la base.

## Déploiement

1. Ajoutez un service de données `mysql` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; conservez-le dans un fichier lisible uniquement par le compte du proxy.
2. Créez un utilisateur de métadonnées pour le proxy. Il lit les colonnes des tables et les fonctions stockées dans `information_schema` et n’a besoin d’aucune donnée : `REFERENCES` montre toutes les colonnes, `SHOW_ROUTINE` toutes les fonctions stockées. Accordez-les directement à l’utilisateur, pas via un rôle ; sans `SHOW_ROUTINE`, le proxy ne démarre pas.

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. Écrivez le fichier de paramètres en remplaçant les adresses, chemins et noms d’exemple :

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   Cet exemple active TLS entre les clients et le proxy, puis entre le proxy et MySQL. `mysql-proxy.crt` doit correspondre au nom d’hôte du proxy utilisé par les clients ; le certificat MySQL doit correspondre à `mysql.internal`. Ces chemins sont ceux de la machine du proxy, et le répertoire de cache doit être accessible en écriture. Les fichiers de mots de passe sont lus en UTF-8 et les espaces en début et en fin sont supprimés ; les mots de passe qui en contiennent ne peuvent donc pas être stockés ainsi.

4. Démarrez le proxy, par exemple comme service systemd :

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   Remplacez `<version>` par la version de la distribution. Les paramètres fournis sous la forme `java -Dgrantforge.…=… -jar …` remplacent ceux du fichier. Le proxy s’arrête proprement sur SIGTERM et envoie ou conserve sur disque les événements en attente.
5. Faites pointer les clients vers l’adresse et le port du proxy. MySQL voit désormais l’adresse du proxy ; la partie hôte des comptes doit l’autoriser.
6. **Fermez le chemin qui contourne le proxy :** les clients qui atteignent MySQL directement ne sont pas vérifiés. Ne laissez que le proxy et les administrateurs atteindre le port du serveur.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour ce proxy et un répertoire accessible en écriture pour l’instantané signé et les événements conservés sur disque |
| `grantforge.signing.key.file` | Fichier facultatif épinglant la clé de signature des instantanés ; copiez la clé publique Ed25519 X.509 encodée en Base64 depuis la page des agents. Sans ce fichier, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.proxy.host`, `grantforge.proxy.port` | Adresse d’écoute ; toutes les adresses et port `3307` par défaut. Plage `0..65535` ; `0` attribue automatiquement un port |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | Certificat et clé privée PEM ; les deux sont requis ensemble pour proposer TLS aux clients |
| `grantforge.proxy.tls.required` | `false` par défaut ; `true` refuse les clients sans TLS (erreur 3159) et exige le certificat et la clé privée |
| `grantforge.mysql.host`, `grantforge.mysql.port` | Hôte MySQL obligatoire ; port `3306` par défaut, plage `1..65535` |
| `grantforge.mysql.tls` | TLS vers MySQL : `disabled` (par défaut), `required` (chiffré, non vérifié), `verify-ca` ou `verify-identity` |
| `grantforge.mysql.tls.ca` | AC PEM pour vérifier MySQL ; une valeur vide utilise les autorités approuvées par Java |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | Utilisateur de métadonnées obligatoire ; le fichier de mot de passe est facultatif et, sans lui, aucun mot de passe n’est transmis au pilote |
| `grantforge.mysql.metadata.cache.seconds` | Durée du cache des colonnes et fonctions stockées : `30` par défaut, plage `0..86400` secondes ; `0` désactive sa réutilisation |
| `grantforge.mysql.connect.timeout.ms` | Délai de connexion à MySQL : `10000` par défaut, plage `1..600000` millisecondes ; les lectures de métadonnées ont un délai fixe de `30000` millisecondes |
| `grantforge.proxy.check.threads` | Fils de vérification des instructions : `8` par défaut, plage `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture de GrantForge : `5000` / `8000` millisecondes par défaut, plage `1..2147483647` |
| `grantforge.refresh.interval.ms` | Intervalle initial ou de repli lorsque le serveur est inaccessible : `30000` millisecondes par défaut, minimum `1000` ; les battements de cœur normaux utilisent l’intervalle renvoyé par le serveur |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | Lot `500` et file `10000` événements par défaut ; plage du lot `1..1000`, file au moins égale au lot et au plus `1000000` |
| `grantforge.audit.flush.interval.ms` | Intervalle d’envoi de l’audit : `5000` millisecondes par défaut, plage `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | Limite de stockage des événements d’audit sur disque : `67108864` (64 MiB) par défaut, minimum `0` |

TLS est indépendant des deux côtés. Quand un seul côté l’utilise et que MySQL demande le mot de passe entier (`caching_sha2_password` sans entrée en cache), le proxy le transmet : à un client sans TLS qui demande la clé du serveur, il répond avec sa propre clé RSA, et il chiffre le mot de passe d’un client TLS avec la clé de MySQL pour un serveur sans TLS. Les clients ne doivent pas être configurés avec le fichier de clé publique de MySQL à travers le proxy.

## Ce dont les instructions ont besoin

| Instruction | Accès |
| --- | --- |
| `SELECT`, sous-requêtes, `WITH`, `EXPLAIN`, `SET @x = (SELECT …)` | `select` sur chaque colonne utilisée ; une table lue sans colonne, comme avec `COUNT(*)` ou `EXISTS`, demande `select` sur toute la table |
| `INSERT`, `LOAD DATA` | `insert` sur les colonnes écrites (toutes si aucune n’est listée) ; `ON DUPLICATE KEY UPDATE` ajoute `update` |
| `REPLACE` | `insert` sur les colonnes et `delete` sur la table |
| `UPDATE` | `update` sur les colonnes modifiées et `select` sur les colonnes lues |
| `DELETE` | `delete` sur la table et `select` sur les colonnes lues |
| `CREATE TABLE`, `CREATE VIEW` / `DROP TABLE`, `DROP VIEW`, `TRUNCATE` / `ALTER TABLE`, `ALTER VIEW`, `OPTIMIZE` | `create` / `drop` / `alter` sur la table ou la vue |
| `RENAME TABLE` | `alter` et `drop` sur l’ancien nom, `create` et `insert` sur le nouveau |
| `CREATE INDEX`, `DROP INDEX` | `index` sur la table |
| `CREATE DATABASE`, `DROP DATABASE` | `create`, `drop` sur la base |
| `LOCK TABLES`, `ANALYZE`, `CHECK`, `CHECKSUM TABLE` | `select` sur la table |

Les instructions qui n’utilisent pas de données relèvent des privilèges de MySQL : transactions, `SET`, `USE`, `SHOW`, `DESCRIBE`, `KILL` et instructions de compte comme `GRANT` et `CREATE USER`. `information_schema` et `performance_schema` sont des bases comme les autres et nécessitent des politiques.

Le proxy refuse les instructions qu’il ne peut pas vérifier entièrement : commentaires exécutables (`/*! … */`), fonctions stockées et `CALL`, `CREATE PROCEDURE`, `FUNCTION`, `TRIGGER` et `EVENT`, `PREPARE`/`EXECUTE` au niveau SQL, `HANDLER`, commandes de réplication et de clonage, `USE` ou `SET NAMES` avec d’autres instructions dans un même lot, et instructions qu’il ne peut pas analyser.

## Masques, filtres de lignes et leurs limites

- Masques et filtres s’appliquent partout où une instruction lit la table, y compris sous-requêtes, jointures, `INSERT … SELECT` et `CREATE TABLE … SELECT` (dont la copie contient les valeurs masquées).
- Un `UPDATE` ou `DELETE` sur une seule table ne modifie que les lignes que le filtre montre. Un `UPDATE` ou `DELETE` multitable d’une table filtrée est refusé, de même qu’une écriture qui utilise une colonne masquée de la table modifiée, car elle pourrait copier ou sonder les valeurs cachées.
- Une vue sur une table masquée ou filtrée ne peut être ni créée ni modifiée : MySQL exécute la vue plus tard, sans le proxy.
- Les instructions préparées réutilisent leur vérification et leur réécriture tant que la version des politiques ne change pas. Une nouvelle version déclenche une vérification : l’exécution continue si l’accès reste autorisé et si le texte réécrit est inchangé ; sinon, l’erreur 1615 demande au client de préparer à nouveau l’instruction.
- Une colonne nommée sans sa table compte pour chaque table visible qui a une telle colonne ; qualifiez les colonnes pour éviter des refus dus à une colonne restreinte de même nom.
- Les colonnes lues via `*` proviennent de la vue de `information_schema` accessible à l’utilisateur de métadonnées et sont mises en cache pendant `grantforge.mysql.metadata.cache.seconds`. Un DDL réussi envoyé au proxy comme commande SQL ordinaire invalide le cache : la prochaine recherche relit les métadonnées. L’invalidation à l’exécution n’est pas garantie pour un DDL externe ou préparé ; attendez l’expiration du cache ou redémarrez le proxy.

## Portée de l’application des politiques

Le proxy vérifie les ressources qu’il peut analyser dans le SQL du client. Il ne développe pas récursivement les vues existantes, les déclencheurs ou d’autres opérations exécutées en interne par le serveur. Pour protéger les tables sous-jacentes, restreignez aussi les privilèges natifs MySQL et l’utilisation des vues et déclencheurs existants. N’accordez pas de privilèges administrateur aux utilisateurs métier en comptant sur le proxy pour intercepter les instructions administratives.

Un filtre de lignes n’est pas une contrainte générale d’écriture : `INSERT`, `REPLACE` et `LOAD DATA` vérifient les droits d’écriture, mais n’exigent pas automatiquement que les nouvelles lignes respectent l’expression du filtre. La branche de `INSERT … ON DUPLICATE KEY UPDATE` qui met à jour une ligne existante ne reçoit pas non plus de condition de filtre de lignes. La compression du protocole, ZSTD, les attributs de requête et les métadonnées de résultat facultatives sont désactivés pendant la négociation.

## Politiques et défaillances

- Sans instantané utilisable, ou avec un jeton lié à un service qui n’est pas `mysql`, toute instruction qui utilise des données est refusée.
- Si GrantForge est temporairement inaccessible ou si la signature d’un nouvel instantané est invalide, le proxy conserve l’instantané déjà appliqué. Un premier démarrage sans instantané utilisable refuse l’accès aux ressources.
- Désactiver le service ignore l’autorisation des ressources, le masquage et le filtrage des lignes de GrantForge ; l’analyse SQL et les restrictions du protocole continuent de s’appliquer. Désactiver le service ne bloque pas l’accès aux données.
- Les privilèges de MySQL s’appliquent toujours après les vérifications du proxy ; le proxy ne peut que les restreindre.
- `COM_CHANGE_USER` est suivi, et refusé quand les côtés client et serveur diffèrent en TLS ; reconnectez-vous plutôt sous l’autre utilisateur.

## Exemple de vérification

Dans une base de test distincte, demandez à un administrateur de base de données de créer les données et le compte suivants. Remplacez `proxy-host` par l’adresse source du proxy réellement vue par MySQL :

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

Créez trois politiques GrantForge pour `alice` : autorisez `select` sur `database=grantforge_demo`, `table=orders`, `column=*` ; masquez `card` avec `mask_show_last_4` ; filtrez la table avec `region = 'EU'`. Gardez le service et les trois politiques activés. Attendez que **Autorisations sur les données → Agents** indique que la dernière version des politiques a été appliquée.

Connectez-vous au proxy avec le client MySQL en ligne de commande. `-p` demande le mot de passe ; le fichier d’AC doit approuver le certificat du proxy :

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- One row only: 1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000: GrantForge does not grant update
```

Les privilèges natifs MySQL autorisent déjà `UPDATE` dans cet exemple ; son refus vérifie donc que le proxy applique la politique. Changez ensuite le filtre de lignes en `region = 'US'`, attendez que le proxy applique la nouvelle version, puis relancez la requête. Seule la ligne 2 doit apparaître, avec la carte `xxxxxxxxxxxx4444`.

## Dépannage et audit

| Code d’erreur / SQLSTATE | Signification et vérifications |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | Accès à la base, à la table ou à la colonne refusé. Pour les messages contenant `by GrantForge`, vérifiez l’utilisateur de la politique, les ressources, le type d’accès et la version de l’instantané ; vérifiez aussi les privilèges natifs MySQL pour les autres refus |
| `1046 / 3D000` | Aucune base sélectionnée ; utilisez `USE`, une base par défaut dans la connexion ou un nom de table qualifié |
| `1227 / 42000` | Opération interdite ou impossible à vérifier de façon sûre ; utilisez le message pour vérifier les restrictions d’instruction et les droits sur les métadonnées |
| `1064 / 42000` | Le SQL ne peut pas être analysé |
| `1615 / HY000` | Préparez à nouveau l’instruction après une modification des politiques |
| `3159 / HY000` | Le proxy exige TLS, mais le client s’est connecté sans TLS |
| `1043 / 08S01` | Échec du protocole ou de la négociation TLS avec le serveur en arrière-plan ; vérifiez le mode TLS, les certificats, le nom d’hôte et les capacités du serveur |
| `2003 / HY000` | Le proxy ne peut pas se connecter à MySQL ; vérifiez l’adresse, le réseau et l’état du serveur en arrière-plan |

Sous **Autorisations sur les données → Audit des accès**, recherchez les autorisations et les refus pour `alice` et les ressources concernées. Une instruction SQL peut produire plusieurs événements d’autorisation des ressources. Ils sont enregistrés avant l’exécution sur le serveur ; `ALLOWED` ne signifie donc pas que MySQL a réussi l’exécution. Les échecs d’analyse, les instructions sans accès aux ressources et les services désactivés peuvent ne produire aucun événement de ressource. Les exécutions répétées d’une instruction préparée avec la même version des politiques ne répètent pas les événements d’autorisation. Cet audit porte sur l’autorisation des ressources ; il ne constitue pas un journal complet d’exécution SQL.

## Guides associés

- [MySQL](/fr/plugins/mysql/)
- [Services de données, politiques et agents](/fr/external/data-services/)
