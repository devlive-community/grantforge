---
title: Agent Trino
description: Installer le contrôle d’accès système dans le coordinator Trino pour vérifier les requêtes avec les politiques GrantForge, ne lister que ce qu’un utilisateur peut utiliser, filtrer les lignes et masquer les colonnes, et soumettre l’emprunt d’identité et les requêtes des autres utilisateurs aux politiques.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent Trino est un contrôle d’accès système nommé `grantforge` : le coordinator l’interroge sur chaque catalogue, schéma, table, colonne, routine, propriété de session et utilisateur que touche une requête, et il répond à partir des politiques signées du service de données `trino`. Trino 470 à 478 s’exécutent sur Java 23 ou 24 et Trino 479 et suivants sur Java 25 ; la distribution fournit donc deux JAR, chacun avec ses dépendances relocalisées à l’intérieur :

| Trino | JAR |
| --- | --- |
| 470 à 478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 et suivants | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

Les tests d’intégration Testcontainers du dépôt exécutent le premier JAR dans Trino `470` et `476` et le second dans `479` et `484`. Cela ne certifie pas toutes les autres versions. Voir [Trino](/fr/plugins/trino/) pour le plug-in de service.

## Fonctionnement

- **Chaque vérification est décidée à chaque portée qu’elle nomme.** Une permission sur un catalogue, un schéma ou une table couvre ce qu’il contient ; l’agent décide donc une colonne à son catalogue, à son schéma, à sa table et à elle-même : un refus à l’une d’elles rejette, une autorisation à l’une d’elles accorde.
- **Les listes omettent ce qui ne peut pas être utilisé.** `SHOW CATALOGS`, `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW FUNCTIONS`, `information_schema` et les métadonnées du pilote ne montrent que ce que l’utilisateur peut utiliser, ou dans quoi il peut utiliser quelque chose.
- **Ce qui décrit les données reste ouvert.** L’`information_schema` de chaque catalogue, les schémas `jdbc` et `metadata` du catalogue `system`, que lisent les pilotes JDBC et les outils de BI, et les fonctions intégrées de Trino ne demandent aucune politique ; leurs lignes sont filtrées comme les listes.
- **Les lignes sont filtrées et les colonnes masquées.** Les politiques de filtrage de lignes s’appliquent à chaque requête qui lit la table, et les masques sont adaptés au type de la colonne, pour qu’un masque ne casse jamais une requête : les masques de texte s’appliquent aux colonnes `varchar` et `char` et sont reconvertis au type de la colonne, le masque d’année aux dates et horodatages, une expression personnalisée est convertie au type, et toute autre colonne qu’un masque de texte nomme se lit comme un `NULL` de son type.
- **L’emprunt d’identité et les requêtes d’autrui sont soumis aux politiques.** Exécuter une requête en tant qu’un autre utilisateur exige `impersonate` sur cet utilisateur ; voir ou interrompre les requêtes d’un autre utilisateur exige `view_query` ou `kill_query` sur lui. Chacun peut exécuter des requêtes, et voir et interrompre les siennes.
- **Les refus disent pourquoi.** Le client reçoit `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`, ou qu’aucune politique ne l’autorise, ou qu’aucune politique n’est encore arrivée.
- **Les décisions sont conservées et les accès regroupés.** Les décisions de l’instantané courant sont conservées, par défaut 100 000 pendant 10 secondes, et un nouvel instantané repart de zéro. Les accès autorisés identiques dans une fenêtre, par défaut 10 secondes, sont signalés comme un seul événement d’accès qui indique leur nombre ; les refus sont signalés un par un, aussitôt. Chaque événement nomme la requête.
- **Les privilèges appartiennent à GrantForge.** Les instructions `GRANT`, `REVOKE`, `DENY` et de rôles de Trino sont refusées, car les politiques se gèrent dans GrantForge.

| Instruction | Accès nécessaire |
| --- | --- |
| `SELECT` | `select` sur chaque colonne lue ; une requête qui ne lit aucune colonne, comme `count(*)`, sur la table |
| `INSERT`, `DELETE`, `TRUNCATE` | `insert` ; `delete` pour les deux autres |
| `UPDATE` | `update` sur chaque colonne écrite |
| `CREATE SCHEMA`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW` | `create` sur ce qui est créé |
| `DROP` d’un schéma, d’une table, d’une vue ou d’une vue matérialisée | `drop` dessus |
| `ALTER` d’une table, d’une vue ou d’une colonne, `COMMENT`, `SET PROPERTIES`, `SET AUTHORIZATION`, `ALTER TABLE ... EXECUTE` | `alter` dessus |
| Renommer un schéma, une table, une vue ou une vue matérialisée | `alter` dessus et `create` sur le nouveau nom |
| `REFRESH MATERIALIZED VIEW` | `insert` dessus |
| Une vue que son propriétaire exécute | Son propriétaire a besoin de `select` sur les colonnes et d’`execute` sur les fonctions qu’elle lit |
| `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW COLUMNS`, `SHOW FUNCTIONS`, `SHOW CREATE` | Un accès quelconque à l’objet ou à son contenu |
| `CREATE CATALOG`, `DROP CATALOG` | `create` ou `drop` sur le catalogue |
| Appeler une fonction ; `CREATE FUNCTION`, `DROP FUNCTION` | `execute` ; `create_function`, `drop_function` sur la fonction |
| `CALL` | `execute` sur la procédure |
| `SET SESSION` | `set` sur la propriété de session du catalogue ou du système |
| S’exécuter en tant qu’un autre utilisateur, voir ou interrompre les requêtes d’autrui | `impersonate`, `view_query`, `kill_query` sur l’utilisateur |
| Lire ou écrire les informations système : nœuds, statistiques et vidages de threads de l’API REST et de l’interface web de Trino | `read` ou `write` sur `system_information` |

À partir de Trino 479, le second JAR vérifie aussi les branches de table : les lister exige de voir la table, et en créer, supprimer ou avancer une exige `alter` sur elle ; actualiser une vue et changer le propriétaire ou le commentaire d’une vue matérialisée exigent `alter` dessus.

## Déploiement

1. Ajoutez un service de données `trino` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; conservez-le dans un fichier que seul le compte de Trino peut lire.
2. Copiez le JAR de la version du coordinator dans un répertoire à lui dans le répertoire des plug-ins de Trino, par exemple `/usr/lib/trino/plugin/grantforge/`. Seul le coordinator vérifie les accès ; les workers n’ont besoin de rien.
3. Créez `etc/access-control.properties` sur le coordinator, en remplaçant l’adresse, les chemins et le nom d’instance d’exemple :

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   Le répertoire de cache doit être accessible en écriture au compte de Trino. GrantForge remplace le contrôle d’accès à base de fichiers ou autre de Trino au lieu de s’exécuter à côté.
4. Redémarrez le coordinator. Tant que l’agent n’a pas son premier instantané, les requêtes sont refusées, sauf ce qui décrit les données.
5. **Migrez d’abord les règles existantes :** les règles du contrôle d’accès à base de fichiers de Trino ne s’appliquent plus. Créez au préalable les politiques correspondantes.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour ce coordinator et un répertoire accessible en écriture pour l’instantané signé et les événements en attente |
| `grantforge.signing.key.file` | Fichier facultatif qui épingle la clé de signature des instantanés ; copiez la clé publique Ed25519 X.509 encodée en Base64 depuis la page Agents. Sans lui, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite du tampon disque (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle les accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` signale chacun |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Le plus de décisions conservées, par défaut `100000`, jusqu’à `10000000`, `0` n’en garde aucune ; et pour combien de temps, par défaut `10000`, `1..3600000` millisecondes |

## Utilisateurs, groupes et rôles

Les politiques nomment l’utilisateur de session de la requête, tel que Trino le donne après authentification et association des utilisateurs. Les groupes sont ceux que le fournisseur de groupes de Trino donne à l’utilisateur, avec les groupes où GrantForge place l’utilisateur ; les rôles sont ceux que GrantForge lui donne.

## Décisions et limites

- Sans instantané utilisable, tout est refusé sauf ce qui décrit les données, l’exécution de requêtes et ses propres requêtes. Un service non utilisé laisse Trino sans vérification, comme sans contrôle d’accès ; les instructions de privilèges et de rôles de Trino restent refusées.
- Un jeton lié à un service dont le type n’est pas `trino` refuse tout.
- Trino demande d’abord si un utilisateur peut utiliser un catalogue : un utilisateur sans aucune permission dans un catalogue reçoit le `Cannot access catalog` de Trino.
- Un `SELECT` qui lit une colonne que l’utilisateur ne peut pas lire est refusé, en nommant la première de ces colonnes ; les colonnes ne sont pas retirées du résultat.
- Trino n’écrit dans aucune table dont il masque des colonnes pour l’utilisateur : un tel `INSERT` échoue tant que le masque s’applique.
- Un masque est converti au type de la colonne ; un `varchar` borné affiche donc autant de la valeur masquée qu’il en tient, par exemple 3 caractères d’un hachage dans `varchar(3)`.
- Un filtre de lignes ou un masque que l’instantané ne peut pas lire n’affiche aucune ligne, ou la colonne comme `NULL`, plutôt que les données.
- Les décisions sont conservées pendant `grantforge.decision.cache.ttl.ms`, si bien que la période de validité d’une politique commence ou finit jusqu’à autant en retard.

## Guides associés

- [Trino](/fr/plugins/trino/)
- [Services de données, politiques et agents](/fr/external/data-services/)
