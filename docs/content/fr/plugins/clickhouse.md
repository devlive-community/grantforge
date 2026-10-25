---
title: ClickHouse
description: Installer et configurer le plug-in de service ClickHouse, joindre un serveur ou plusieurs, se connecter avec un mot de passe ou un certificat client TLS, rechercher bases, tables et colonnes, et gérer qui peut lire, écrire et administrer quoi, jusqu’aux colonnes et aux lignes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in ClickHouse recherche les bases de données, les tables et les colonnes, et gère des politiques d’accès dans les termes de ClickHouse : lire et écrire des colonnes, modifier, supprimer, vider et optimiser des tables, créer des tables dans une base et la supprimer, ainsi que, sur le serveur, la création de bases, `KILL QUERY` et les instructions `SYSTEM`, avec des filtres de lignes sur les tables. Son identifiant de plug-in et son type de service sont tous deux `clickhouse`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent ClickHouse](/fr/external/clickhouse-agent/), un service à côté des serveurs, applique les politiques avec les droits et les politiques de lignes propres à ClickHouse.

Les tests d’intégration Testcontainers du dépôt couvrent ClickHouse `24.8`, `25.3`, `25.8`, `26.3` et `26.8`, les versions à support long des deux dernières années ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution contient le plug-in dans `plugins/clickhouse`. Vérifiez dans **Administration de la plateforme → Plug-ins** que `clickhouse` est activé ; analysez à nouveau le répertoire des plug-ins après une mise à jour. Le plug-in dialogue avec les serveurs en HTTP via le client Java de ClickHouse, dans son propre chargeur de classes.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez ClickHouse (`clickhouse`).
2. Saisissez l’URL de l’interface HTTP d’un serveur, ou de plusieurs.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez son nom et ses identifiants, testez la connexion et enregistrez le service. Le test se connecte et décrit le serveur : sa version de ClickHouse, l’utilisateur sous lequel il s’est connecté et le nombre de bases que cet utilisateur voit.

L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des utilisateurs qui lisent et écrivent. Un test de connexion réussi ne signifie pas que l’accès est déjà soumis aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `clickhouse.urls` | Obligatoire : l’interface HTTP d’un serveur, par exemple `https://clickhouse1.example.com:8443` ; plusieurs séparées par des virgules, essayées tour à tour quand l’une est injoignable |
| `authentication` | `password` (par défaut) ou `certificate` : un certificat client TLS que ClickHouse associe à l’utilisateur (`IDENTIFIED WITH ssl_certificate`) |
| `username` | Obligatoire : l’utilisateur de recherche |
| `password` | password : le mot de passe de l’utilisateur de recherche ; stocké chiffré |
| `ssl.root.certificate` | https : le certificat d’AC PEM, sur le **serveur GrantForge**, avec lequel les serveurs sont vérifiés ; vide utilise celui de la JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate : le certificat client PEM et sa clé privée, sur le serveur GrantForge ; la connexion par certificat exige des serveurs `https://` |
| `timeout` | Durée qu’une requête peut prendre, par défaut `30000`, plage `1..600000` millisecondes |

Les paramètres d’un mode de connexion sont refusés avec un autre, de sorte qu’un service dit exactement comment il se connecte. Une connexion refusée, un utilisateur en lecture seule, un serveur injoignable ou dont le certificat ne peut être vérifié, et autre chose que ClickHouse qui répond à sa place, comme un proxy, sont signalés comme tels.

### Ce que voit l’utilisateur de recherche

ClickHouse ne montre à un utilisateur que les bases, tables et colonnes sur lesquelles il détient un droit. Donnez à l’utilisateur de recherche `SHOW` sur ce pour quoi des politiques doivent être écrites, par exemple `GRANT SHOW ON *.* TO grantforge_lookup`, qui lui fait voir les noms sans lire de données. Les bases propres à ClickHouse, `system` et `information_schema`, sont laissées de côté : les politiques ne les décident pas.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `database` | Joker, sensible à la casse ; le nom de la base | `create`, `drop_database` |
| `table` | Joker, sensible à la casse ; sous une base | `alter`, `drop`, `truncate`, `optimize` |
| `column` | Joker, sensible à la casse ; sous une table | `select`, `insert` |
| `server` | Son unique valeur, `*` | `create_database`, `kill_query`, `system` |

| Type d’accès | Droits de ClickHouse |
| --- | --- |
| `select`, `insert` | `SELECT`, `INSERT`, sur des colonnes |
| `alter` | `ALTER` : toute modification d’une table, `ALTER UPDATE` et `ALTER DELETE` compris |
| `drop` | `DROP TABLE`, `DROP VIEW`, `DROP DICTIONARY` |
| `truncate`, `optimize` | `TRUNCATE`, `OPTIMIZE` |
| `create` | `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` dans la base |
| `drop_database` | `DROP DATABASE` |
| `create_database`, `kill_query`, `system` | `CREATE DATABASE`, `KILL QUERY`, `SYSTEM` : toute instruction `SYSTEM` |

La lecture et l’écriture se décident par colonne, comme ClickHouse les accorde : une politique pour `select` ou `insert` nomme des colonnes, `*` pour toutes. Une politique peut ainsi autoriser une table et refuser l’une de ses colonnes, et l’utilisateur peut lire toutes les autres. Dans les valeurs de politique, `*` correspond à toute suite de caractères et `?` à un seul. L’éditeur de politiques propose les bases, les tables des bases qu’une politique nomme et leurs colonnes.

Les types d’accès ne sont aucun des groupes de droits de ClickHouse comme `ALL` : chacun est ce qu’il dit, si bien qu’une politique ne donne jamais plus que ce qu’elle nomme. ClickHouse n’a pas de masquage de colonnes en dehors de ClickHouse Cloud ; ce type de service n’en propose donc pas.

### Filtres de lignes

Une politique de filtre de lignes sur une table montre à ses utilisateurs les lignes pour lesquelles une condition est vraie. La condition est une expression ClickHouse, comme les politiques de lignes la prennent, par exemple `region = 'EU'` ou `owner = currentUser()`. Un utilisateur voit le filtre de la première politique qui s’applique à lui, comme pour les autres services.

## Exemple de politiques

Supposons que le serveur ait les bases `sales` et `hr`. Créez ces politiques activées dans le même service de données :

| Ressources | Règle |
| --- | --- |
| `database=sales`, `table=*`, `column=*` | Autoriser `select` au groupe `analysts` |
| `database=sales`, `table=orders`, `column=card` | Refuser `select` au groupe `analysts` |
| `database=sales`, `table=orders`, `column=*` | Autoriser `insert` à l’utilisateur `ingest` |
| `database=team_*` | Autoriser `create` au groupe `data-team` |
| `database=team_*`, `table=*` | Autoriser `alter`, `drop`, `truncate` et `optimize` au groupe `data-team` |
| `database=team_*`, `table=*`, `column=*` | Autoriser `select` et `insert` au groupe `data-team` |
| `server=*` | Autoriser `create_database` au groupe `data-team` |
| `database=sales`, `table=orders` (filtre de lignes) | Montrer à l’utilisateur `emea` les lignes `region = 'EMEA'` |

Une fois que l’agent applique le nouvel instantané, les membres d’`analysts` lisent toutes les tables de `sales` sauf les numéros de carte d’`orders` ; `ingest` écrit `orders` et ne lit rien ; les membres de `data-team` créent des bases et, dans celles dont le nom commence par `team_`, des tables, qu’ils lisent, écrivent et modifient ; et `emea`, si une politique le laisse lire `orders`, ne voit que les lignes de sa propre région.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Les appliquer nécessite l’agent ClickHouse, un par serveur ou par cluster. Le déploiement et les vérifications figurent dans l’[agent ClickHouse](/fr/external/clickhouse-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent ClickHouse](/fr/external/clickhouse-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
