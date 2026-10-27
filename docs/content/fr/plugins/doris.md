---
title: Apache Doris
description: Installer et configurer le plug-in de service Apache Doris, joindre un ou plusieurs frontends, se connecter avec un mot de passe, rechercher les catalogues, bases de données, tables, colonnes et groupes de charge de travail, et gérer qui peut lire, charger et administrer quoi, jusqu’aux colonnes et aux lignes, et qui peut exécuter des requêtes dans quel groupe de charge de travail.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Apache Doris recherche les catalogues, bases de données, tables, colonnes et groupes de charge de travail, et gère les politiques d’accès dans les termes propres à Doris : lire des colonnes, charger, modifier et supprimer des tables et afficher la définition de leurs vues, créer des tables dans une base de données et la supprimer, créer des bases de données dans un catalogue et exécuter des requêtes dans un groupe de charge de travail, avec des filtres de lignes de tables. Son identifiant de plug-in et son type de service sont tous deux `doris`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent Apache Doris](/fr/external/doris-agent/), un service à côté du cluster, applique les politiques avec les rôles, droits et politiques de lignes propres à Doris.

Les tests d’intégration Testcontainers du dépôt couvrent Apache Doris `2.1`, `3.1`, `4.0` et `4.1`, chacun sur un cluster d’un frontend et d’un backend ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/doris`. Vérifiez que `doris` est activé dans **Administration de la plateforme → Plug-ins** ; relancez l’analyse du répertoire des plug-ins après une mise à jour. Le plug-in communique avec le protocole MySQL des frontends par MariaDB Connector/J, dans un chargeur de classes qui lui est propre.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Apache Doris (`doris`).
2. Saisissez l’adresse du protocole MySQL d’un frontend, ou de plusieurs.
3. Saisissez le nom et le mot de passe de l’utilisateur de recherche, testez la connexion et enregistrez le service. Le test se connecte et décrit le cluster : sa version de Doris, l’utilisateur connecté, le nombre de catalogues qu’il voit et le nombre de bases de données du catalogue interne. Il n’interroge que le frontend, et répond donc tant qu’aucun backend ne tourne.

L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des utilisateurs qui lisent et chargent. Un test de connexion réussi ne signifie pas que l’accès est déjà soumis aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `doris.hosts` | Obligatoire : l’adresse du protocole MySQL d’un frontend, par exemple `fe1.example.com:9030` ; plusieurs séparées par des virgules, essayées tour à tour lorsqu’une est injoignable. Sans port, c’est `9030` |
| `username` | Obligatoire : l’utilisateur de recherche |
| `password` | Le mot de passe de l’utilisateur de recherche ; stocké chiffré |
| `ssl.mode` | `disable` (par défaut), `trust` pour chiffrer sans vérifier le frontend, `verify-ca` pour vérifier son certificat ou `verify-full` pour vérifier aussi son nom ; pour les frontends qui offrent TLS sur le protocole MySQL |
| `ssl.root.certificate` | verify-ca et verify-full : le certificat d’AC PEM qui vérifie les frontends, sur le **serveur GrantForge** ; vide utilise ceux de la JVM |
| `timeout` | Durée maximale de la connexion et d’une instruction, par défaut `30000`, plage `1..600000` millisecondes |

Les connexions refusées, un utilisateur de recherche à qui manque un droit et les frontends injoignables sont signalés comme tels.

### Ce que voit l’utilisateur de recherche

Les instructions `SHOW` de Doris ne listent que les catalogues, bases de données, tables et colonnes sur lesquels un utilisateur a un droit ; une recherche montre donc ce que l’utilisateur de recherche peut voir. Donnez-lui `SELECT_PRIV` sur ce pour quoi des politiques doivent être écrites, par exemple `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'` ; `SHOW WORKLOAD GROUPS` liste les groupes qu’il peut voir. Les bases de données propres à Doris, `information_schema`, `mysql` et `__internal_schema`, sont omises : les politiques ne les décident pas.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `catalog` | Caractères génériques, sensible à la casse ; le catalogue interne ou un catalogue externe | `create_database` |
| `database` | Caractères génériques, sensible à la casse ; sous un catalogue | `create`, `drop_database` |
| `table` | Caractères génériques, sensible à la casse ; sous une base de données, une table ou une vue | `load`, `alter`, `drop`, `show_view` |
| `column` | Caractères génériques, insensible à la casse, comme Doris compare les noms de colonnes ; sous une table | `select` |
| `workload_group` | Caractères génériques, sensible à la casse ; le nom du groupe | `usage` |

| Type d’accès | Droit de Doris |
| --- | --- |
| `select` | `SELECT_PRIV`, sur des colonnes, ou sur une table, une base de données ou un catalogue entiers |
| `load` | `LOAD_PRIV` : chargements, `INSERT`, `UPDATE`, `DELETE` et `TRUNCATE` |
| `alter`, `drop` | `ALTER_PRIV`, `DROP_PRIV` sur la table |
| `show_view` | `SHOW_VIEW_PRIV` : `SHOW CREATE VIEW` |
| `create` | `CREATE_PRIV` sur la base de données : la créer, et y créer des tables et des vues |
| `drop_database` | `DROP_PRIV` sur la base de données |
| `create_database` | `CREATE_PRIV` sur le catalogue |
| `usage` | `USAGE_PRIV` sur le groupe de charge de travail |

La lecture se décide par colonne, comme Doris l’accorde : une politique `select` nomme des colonnes, `*` pour toutes. Une politique peut ainsi autoriser une table et refuser l’une de ses colonnes, et l’utilisateur lit toutes les autres. Dans les valeurs des politiques, `*` correspond à toute suite de caractères et `?` à un caractère. L’éditeur de politiques suggère les catalogues, les bases de données des catalogues que nomme la politique, leurs tables et colonnes, et les groupes de charge de travail.

Le droit de Doris sur une base de données s’étend aussi à chacune de ses tables, et celui sur un catalogue à chacune de ses bases de données. L’agent n’accorde donc `create_database` que là où les politiques permettent aussi à l’utilisateur de créer des tables dans chaque base de données du catalogue, et `drop_database` que là où elles lui permettent aussi de supprimer chaque table de la base de données ; sinon il retient le droit et le signale, plutôt que d’accorder plus que ce que permettent les politiques. `ADMIN_PRIV`, `GRANT_PRIV` et `NODE_PRIV` ne sont pas des types d’accès : aucune politique ne les donne.

Doris ne masque les valeurs que par Apache Ranger, ce type de service n’offre donc pas de masquage.

### Filtres de lignes

Une politique de filtre de lignes d’une table ne montre à ses utilisateurs que les lignes pour lesquelles une condition est vraie. La condition est un prédicat Doris, tel que le prennent les politiques de lignes, par exemple `region = 'EU'` ; l’agent l’exécute d’abord sur la table, et un filtre qui ne peut pas s’y exécuter ne montre aucune ligne. Comme pour les autres services, un utilisateur reçoit le filtre de la première politique qui s’applique à lui.

## Exemple de politiques

Supposons que le cluster possède les bases de données `sales` et `hr` dans son catalogue interne et un groupe de charge de travail `etl`. Créez ces politiques activées dans le même service de données :

| Ressources | Règle |
| --- | --- |
| `catalog=internal`, `database=sales`, `table=*`, `column=*` | Autoriser `select` au groupe `analysts` |
| `catalog=internal`, `database=sales`, `table=orders`, `column=card` | Refuser `select` au groupe `analysts` |
| `catalog=internal`, `database=sales`, `table=orders` | Autoriser `load` à l’utilisateur `ingest` |
| `catalog=internal`, `database=team_*` | Autoriser `create` au groupe `data-team` |
| `catalog=internal`, `database=team_*`, `table=*` | Autoriser `load`, `alter` et `drop` au groupe `data-team` |
| `workload_group=etl` | Autoriser `usage` à l’utilisateur `ingest` et au groupe `data-team` |
| `catalog=internal`, `database=sales`, `table=orders` (filtre de lignes) | Ne montrer à l’utilisateur `emea` que les lignes `region = 'EMEA'` |

Une fois que l’agent a appliqué le nouvel instantané, les membres de `analysts` lisent toutes les tables de `sales` sauf les numéros de carte de `orders` ; `ingest` charge `orders` et peut exécuter ses chargements dans `etl` ; les membres de `data-team` créent des tables dans les bases de données `team_` qu’un administrateur a créées, et les chargent, modifient et suppriment ; et `emea`, si une politique le laisse lire `orders`, ne voit que les lignes de sa région.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Les appliquer exige l’agent Apache Doris, un par cluster. Le déploiement et les vérifications sont décrits dans [Agent Apache Doris](/fr/external/doris-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent Apache Doris](/fr/external/doris-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
