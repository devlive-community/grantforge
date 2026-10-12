---
title: MySQL
description: Installer et configurer le plug-in de service MySQL, rechercher bases, tables et colonnes, et gérer les politiques d’accès, de masquage et de filtrage des lignes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in MySQL fournit à GrantForge la recherche des bases, tables et colonnes, ainsi que les politiques d’accès, de masquage et de filtrage des lignes. Son identifiant de plug-in et son type de service sont tous deux `mysql`. Le plug-in de service fonctionne sur le serveur GrantForge ; un [agent proxy MySQL](/fr/external/mysql-agent/) indépendant vérifie les requêtes SQL des applications.

Le test de connexion accepte MySQL 8.0 et les versions ultérieures. Les tests d’intégration Testcontainers du dépôt couvrent `mysql:8.0` et `mysql:8.4` ; cela ne valide pas automatiquement toutes les autres versions. Les serveurs MariaDB et MySQL 5.7 ne sont pas pris en charge. `jdbc:mariadb://` est un format d’URL du pilote, pas une indication de compatibilité avec les serveurs MariaDB.

## Installation

La distribution inclut le plug-in dans `plugins/mysql`. Vérifiez dans **Administration de la plateforme → Plug-ins** que `mysql` est activé ; après une mise à jour, relancez l’analyse du répertoire des plug-ins. Le plug-in parle à MySQL via MariaDB Connector/J (LGPL), qui gère le protocole de MySQL, y compris `caching_sha2_password` et TLS, dans son propre chargeur de classes. Le Connector/J de MySQL est sous GPL et n’est pas distribué.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez MySQL (`mysql`).
2. Saisissez l’URL JDBC du serveur, par exemple `jdbc:mysql://mysql.example.com:3306`. `jdbc:mariadb://` fonctionne de même, ainsi que les formes de basculement du pilote comme `jdbc:mysql:replication://primary:3306,replica:3306`.
3. Saisissez l’utilisateur de recherche et son mot de passe, testez la connexion et enregistrez le service. Le test se connecte, vérifie la version du serveur et lit `information_schema` comme la recherche.

L’utilisateur de recherche sert à GrantForge pour découvrir les ressources ; il est distinct des utilisateurs avec lesquels les applications se connectent à MySQL. Un test de connexion réussi ne signifie pas que les requêtes SQL des applications sont déjà soumises aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `jdbc.url` | URL JDBC obligatoire : `jdbc:mysql://host[:port]` ou `jdbc:mariadb://…`, avec une base et des `?options` facultatives ; les identifiants n’ont pas leur place dans l’URL |
| `username` | Utilisateur de recherche obligatoire |
| `password` | Mot de passe de l’utilisateur de recherche ; stocké chiffré |
| `jdbc.properties` | Options du pilote, une `key=value` par ligne, par exemple `sslMode=verify-full` et `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | Délai de connexion, `10000` par défaut, plage `1..600000` |
| `read.timeout.ms` | Délai de lecture d’une recherche, `30000` par défaut, plage `1..600000` |

Les options qui feraient lire des fichiers locaux au serveur, exécuteraient des instructions à la connexion, autoriseraient plusieurs instructions à la fois, porteraient des identifiants ou chargeraient des classes sont refusées dans l’URL comme dans `jdbc.properties`, quelle que soit leur casse : `user`, `password`, `allowLocalInfile`, `allowMultiQueries`, `initSql`, `createDatabaseIfNotExist`, `socketFactory`, `credentialType` et les options de pool. Les connexions de recherche sont en lecture seule.

Pour une connexion TLS qui vérifie l’identité du serveur, ajoutez ceci à `jdbc.properties` :

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

Le fichier de certificat doit être lisible sur le **serveur GrantForge**, et le nom d’hôte de l’URL doit correspondre au certificat. Le plug-in n’impose pas TLS par défaut ; configurez-le explicitement. Ces noms d’options sont ceux de MariaDB Connector/J.

`information_schema` ne montre que les objets sur lesquels l’utilisateur a des privilèges. Pour découvrir uniquement les ressources de `sales`, un administrateur de base de données peut accorder les droits suivants. Remplacez `grantforge-host` par l’adresse source du serveur GrantForge :

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

N’utilisez `REFERENCES ON *.*` que si la découverte de toutes les bases est nécessaire. Le compte de métadonnées du proxy a aussi besoin de `SHOW_ROUTINE` ; consultez le guide de l’agent.

## Ressources et types d’accès

| Niveau | Correspondance | Remarques |
| --- | --- | --- |
| `database` | Jokers, sensible à la casse | Les politiques peuvent s’arrêter ici |
| `table` | Jokers, sensible à la casse | Sous une base ; les politiques peuvent s’arrêter ici |
| `column` | Jokers, insensible à la casse | Sous une table ; types d’accès `select`, `insert` et `update` |

Bases et tables se comparent en tenant compte de la casse, comme MySQL les stocke sous Linux. Sur un serveur où `lower_case_table_names` vaut 1 ou 2, l’agent met en minuscules les noms qu’il vérifie : écrivez alors ces politiques en minuscules. Les noms de colonnes se comparent toujours sans la casse, comme dans MySQL.

Les types d’accès suivent les privilèges de MySQL : `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `index` et `all`, qui inclut ces huit types. `all` n’est pas le `ALL PRIVILEGES` de MySQL et ne supprime pas les restrictions du proxy sur les instructions.

Dans les valeurs de politique, `*` correspond à un nombre quelconque de caractères et `?` à un seul caractère. L’éditeur propose les noms par préfixe : bases, tables des bases nommées et colonnes de leurs tables. Dans la saisie des suggestions, `%` et `_` sont des caractères littéraux.

## Masquage

Les politiques de masquage s’appliquent aux colonnes. Chaque type de masque est une expression MySQL 8 où `{col}` représente la colonne :

| Type de masque | Affiche |
| --- | --- |
| `mask` | Lettres ASCII en `X`/`x` et chiffres en `n` ; les autres caractères sont conservés |
| `mask_show_last_4` | Seulement les quatre derniers caractères |
| `mask_show_first_4` | Seulement les quatre premiers caractères |
| `mask_hash` | Une empreinte SHA-256 sans sel |
| `mask_null` | `NULL` |
| `mask_none` | La valeur inchangée |
| `mask_date_show_year` | Le 1er janvier de l’année de la date |
| `custom` | Une expression MySQL de votre choix, avec `{col}` pour la colonne, par exemple `CONCAT('****', RIGHT({col}, 4))` |

Les masques qui affichent les quatre premiers ou derniers caractères conservent toute la valeur si elle ne dépasse pas quatre caractères. Par exemple, `mask_show_last_4` affiche `4111111111111111` sous la forme `xxxxxxxxxxxx1111`.

## Filtres de lignes

Les politiques de filtrage des lignes s’appliquent aux tables. Un filtre est une condition MySQL sur leurs colonnes, comme `region = 'EU'` : l’utilisateur ne voit que les lignes correspondantes, et les instructions `UPDATE` et `DELETE` portant sur une seule table ne modifient que ces lignes. Les écritures sur plusieurs tables sont refusées si la table cible nécessite un filtre de lignes. Consultez le guide de l’agent pour les autres restrictions.

Les masques et filtres de lignes n’accordent pas l’accès. Une politique d’accès distincte doit autoriser l’opération.

## Exemple de politiques

Supposons que MySQL contienne une table `sales.orders` avec les colonnes `id`, `region` et `card`. Créez trois politiques activées dans le même service de données pour l’utilisateur de connexion MySQL `alice` :

| Type de politique | Ressources | Règle |
| --- | --- | --- |
| Accès | `database=sales`, `table=orders`, `column=*` | Autoriser `select` pour l’utilisateur `alice` |
| Masquage | `database=sales`, `table=orders`, `column=card` | Appliquer `mask_show_last_4` à l’utilisateur `alice` |
| Filtre de lignes | `database=sales`, `table=orders` | Appliquer `region = 'EU'` à l’utilisateur `alice` |

Indiquez `alice` comme utilisateur de politique, pas `alice@host`. MySQL doit lui aussi autoriser ce compte à lire la table. Dès que le proxy applique le nouvel instantané, les requêtes de `alice` via le proxy ne renvoient que les lignes EU, avec uniquement les quatre derniers caractères de chaque numéro de carte visibles. Les conditions et expressions de jointure voient également les valeurs masquées. Consultez l’[agent proxy MySQL](/fr/external/mysql-agent/) pour le déploiement et la vérification.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Pour les appliquer, masques et filtres compris, il faut l’agent proxy MySQL, auquel les clients se connectent à la place du serveur. Voir le guide de l’agent pour son déploiement et ce qu’il vérifie.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent proxy MySQL](/fr/external/mysql-agent/)
- [Développement de plug-ins et types de service](/fr/develop/plugins/)
