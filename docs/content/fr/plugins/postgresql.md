---
title: PostgreSQL
description: Installer et configurer le plug-in de service PostgreSQL, joindre un serveur ou plusieurs, se connecter avec un mot de passe ou un certificat client TLS, rechercher bases de données, schémas, tables et colonnes, et gérer qui peut lire, écrire et créer quoi, jusqu’aux colonnes et aux lignes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in PostgreSQL recherche les bases de données, les schémas, les tables et les colonnes, et gère des politiques d’accès dans les termes de PostgreSQL : lire, insérer et modifier des colonnes, supprimer, vider et munir de déclencheurs des tables, créer des objets dans un schéma, et créer des schémas et des tables temporaires dans une base, avec des filtres de lignes sur les tables. Son identifiant de plug-in et son type de service sont tous deux `postgresql`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent PostgreSQL](/fr/external/postgresql-agent/), un service à côté des serveurs, applique les politiques avec les droits et les politiques de sécurité des lignes de PostgreSQL.

Les tests d’intégration Testcontainers du dépôt couvrent PostgreSQL `15`, `16`, `17` et `18`, les versions que PostgreSQL maintient ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/postgresql`. Vérifiez sous **Administration de la plateforme → Plug-ins** que `postgresql` est activé ; relancez l’analyse du répertoire des plug-ins après une mise à jour. Le plug-in parle aux serveurs par le pilote JDBC de PostgreSQL, dans un chargeur de classes qui lui est propre.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez PostgreSQL (`postgresql`).
2. Saisissez un serveur sous la forme `host:port`, ou plusieurs serveurs.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez son nom et ses identifiants, testez la connexion et enregistrez le service. Le test se connecte et décrit le serveur : sa version de PostgreSQL, l’utilisateur sous lequel il s’est connecté et le nombre de bases du serveur.

L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des utilisateurs qui lisent et écrivent. Un test de connexion réussi ne signifie pas que l’accès est déjà soumis aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `postgresql.hosts` | Obligatoire : un serveur sous la forme `host:port`, par exemple `postgres1.example.com:5432` ; plusieurs séparés par des virgules, essayés tour à tour quand l’un est injoignable. Le port vaut `5432` par défaut |
| `postgresql.database` | La base à laquelle l’utilisateur de recherche se connecte d’abord, pour lister les bases ; par défaut `postgres` |
| `authentication` | `password` (par défaut) ou `certificate` : un certificat client TLS que l’authentification `cert` de PostgreSQL associe à l’utilisateur |
| `username` | Obligatoire : l’utilisateur de recherche |
| `password` | password : le mot de passe de l’utilisateur de recherche ; stocké chiffré |
| `ssl.mode` | TLS tel que le nomme `sslmode` de libpq : `disable`, `allow`, `prefer` (par défaut), `require`, `verify-ca` ou `verify-full`, qui vérifie aussi le nom du serveur |
| `ssl.root.certificate` | verify-ca et verify-full : le certificat d’AC PEM contre lequel les serveurs sont vérifiés, sur le **serveur GrantForge** ; vide utilise celui de la JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate : le certificat client PEM et sa clé privée PKCS #8, PEM ou DER, sur le serveur GrantForge ; la connexion par certificat demande `require` ou un mode de vérification, et un mode de vérification demande alors `ssl.root.certificate` |
| `timeout` | La durée permise pour se connecter et pour une requête, par défaut `30000`, plage `1..600000` millisecondes |

Les paramètres d’une connexion sont refusés avec une autre, si bien qu’un service dit exactement comment il se connecte. Le pilote de PostgreSQL refuse un fichier de clé privée que d’autres que son propriétaire peuvent lire. Une connexion refusée, une base absente, un serveur injoignable ou dont le certificat ne peut être vérifié sont signalés comme tels.

### Ce que voit l’utilisateur de recherche

Les catalogues de PostgreSQL montrent à chaque utilisateur les noms des bases, et dans chaque base à laquelle il peut se connecter les noms des schémas, des tables et des colonnes, sans lire de données. L’utilisateur de recherche n’a besoin que de `CONNECT` sur les bases pour lesquelles écrire des politiques, que PUBLIC détient sauf s’il a été révoqué. Une recherche de schémas, de tables ou de colonnes se connecte à chaque base que nomme la politique, 20 au plus, et passe les bases auxquelles l’utilisateur de recherche ne peut pas se connecter. Les schémas propres à PostgreSQL, ceux qui commencent par `pg_` et `information_schema`, sont exclus : les politiques n’en décident pas.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `database` | Jokers, sensible à la casse ; le nom de la base | `create_schema`, `temporary` |
| `schema` | Jokers, sensible à la casse ; sous une base | `create` |
| `table` | Jokers, sensible à la casse ; tables, tables partitionnées, vues, vues matérialisées et tables étrangères sous un schéma | `delete`, `truncate`, `trigger` |
| `column` | Jokers, sensible à la casse ; sous une table | `select`, `insert`, `update` |

| Type d’accès | Droits de PostgreSQL |
| --- | --- |
| `select`, `insert`, `update` | `SELECT`, `INSERT`, `UPDATE`, sur des colonnes ou sur la table entière |
| `delete`, `truncate`, `trigger` | `DELETE`, `TRUNCATE`, `TRIGGER` sur la table |
| `create` | `CREATE` sur le schéma : tables, vues, séquences, fonctions et autres objets dans celui-ci |
| `create_schema` | `CREATE` sur la base : schémas dans celle-ci |
| `temporary` | `TEMPORARY` sur la base |

La lecture, l’insertion et la modification se décident par colonne, comme PostgreSQL les accorde : une politique pour `select`, `insert` ou `update` nomme des colonnes, `*` pour toutes. Une politique peut ainsi autoriser une table et en refuser une colonne, et l’utilisateur peut lire toutes les autres. Dans les valeurs des politiques, `*` correspond à une suite quelconque de caractères et `?` à un seul. L’éditeur de politiques propose les bases, les schémas des bases que nomme une politique, leurs tables et leurs colonnes.

Atteindre ce qu’une politique autorise demande davantage à PostgreSQL : `CONNECT` sur la base, `USAGE` sur le schéma et, pour insérer dans une colonne serial, `USAGE` sur sa séquence. Ce ne sont pas des types d’accès à part : l’agent les accorde avec ce qui en a besoin. PostgreSQL n’a pas de masquage de colonnes, ce type de service n’en propose donc pas.

### Filtres de lignes

Une politique de filtrage des lignes sur une table montre à ses utilisateurs les lignes pour lesquelles une condition est vraie. La condition est une expression PostgreSQL, telle que la prend le `USING` d’une politique de sécurité des lignes, par exemple `region = 'EU'` ou `owner = current_user`. Un utilisateur voit le filtre de la première politique qui s’applique à lui, comme pour les autres services. PostgreSQL ne filtre que les lignes des tables et des tables partitionnées : une vue ou un objet semblable dont une politique filtre les lignes pour un utilisateur n’est pas lisible du tout pour cet utilisateur.

## Exemple de politiques

Supposons que le serveur ait les bases `sales` et `hr`, et que `sales` ait le schéma `crm`. Créez ces politiques activées dans le même service de données :

| Ressources | Règle |
| --- | --- |
| `database=sales`, `schema=crm`, `table=*`, `column=*` | Autoriser `select` au groupe `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=card` | Refuser `select` au groupe `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=*` | Autoriser `insert` à l’utilisateur `ingest` |
| `database=sales`, `schema=team_*` | Autoriser `create` au groupe `data-team` |
| `database=sales`, `schema=team_*`, `table=*` | Autoriser `delete` et `truncate` au groupe `data-team` |
| `database=sales`, `schema=team_*`, `table=*`, `column=*` | Autoriser `select`, `insert` et `update` au groupe `data-team` |
| `database=sales` | Autoriser `create_schema` au groupe `data-team` |
| `database=sales`, `schema=crm`, `table=orders` (filtre de lignes) | Montrer à l’utilisateur `emea` les lignes `region = 'EMEA'` |

Une fois que l’agent a appliqué le nouvel instantané, les membres d’`analysts` lisent toutes les tables de `crm` sauf les numéros de carte d’`orders` ; `ingest` insère dans `orders`, ses identifiants serial compris, et ne lit rien ; les membres de `data-team` créent des schémas, et dans ceux dont le nom commence par `team_` créent, lisent, modifient et vident des tables ; et `emea`, si une politique le laisse lire `orders`, ne voit que les lignes de sa région.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Les appliquer demande l’agent PostgreSQL, un par serveur ou par primaire. Le déploiement et les vérifications sont décrits dans l’[agent PostgreSQL](/fr/external/postgresql-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent PostgreSQL](/fr/external/postgresql-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
