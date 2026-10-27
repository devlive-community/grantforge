---
title: Apache Solr
description: Installer et configurer le plug-in de service Solr, joindre un ou plusieurs nœuds, se connecter par authentification basic ou jeton web JSON, rechercher les collections et leurs chemins de requête, et gérer qui peut interroger, mettre à jour et administrer quoi.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Solr recherche les collections et les chemins de requête qu’elles contiennent, et gère des politiques d’accès sur ce que Solr décide : interroger, mettre à jour, lire et modifier schémas et configs, administrer les collections et administrer le cluster. Son identifiant de plug-in et son type de service sont tous deux `solr`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent Solr](/fr/external/solr-agent/), installé dans les nœuds, applique les politiques.

Les tests d’intégration Testcontainers du dépôt couvrent des nœuds SolrCloud de Solr `9.8.1`, `9.10.1`, `9.11.0` et `10.0.0` ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution contient le plug-in dans `plugins/solr`. Vérifiez dans **Administration de la plateforme → Plug-ins** que `solr` est activé ; analysez à nouveau le répertoire des plug-ins après une mise à jour. Le plug-in dialogue avec les nœuds via le client HTTP JDK de SolrJ 9.11, dans son propre chargeur de classes.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Apache Solr (`solr`).
2. Saisissez l’URL de base d’un nœud, ou de plusieurs.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez ses identifiants, testez la connexion et enregistrez le service. Le test se connecte et décrit le cluster : sa version de Solr, s’il exécute SolrCloud et combien de collections il compte.

L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des utilisateurs qui interrogent et mettent à jour. Un test de connexion réussi ne signifie pas que l’accès est déjà soumis aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `solr.urls` | Obligatoire : l’URL de base d’un nœud, par exemple `https://solr1.example.com:8983/solr` ; plusieurs séparées par des virgules, essayées tour à tour quand l’une est injoignable |
| `authentication` | `none` (par défaut), `basic` ou `jwt` |
| `username`, `password` | basic : l’utilisateur de recherche et son mot de passe ; le mot de passe est stocké chiffré |
| `token` | jwt : le jeton web JSON de l’utilisateur de recherche ; stocké chiffré |
| `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword`, `javax.net.ssl.trustStoreType` | https : le magasin de confiance, sur le **serveur GrantForge**, auquel les certificats des nœuds sont confrontés, son mot de passe et son type, `PKCS12` par défaut ; vide utilise celui de la JVM |
| `timeout` | Durée maximale d’une requête, `30000` par défaut, plage `1..600000` millisecondes |

Les paramètres d’un mode de connexion sont refusés avec un autre, de sorte qu’un service dit exactement comment il se connecte. Une connexion refusée, un utilisateur qui n’a pas le droit de voir quelque chose et un nœud injoignable, y compris derrière un proxy qui répond à sa place, sont signalés comme tels.

### Ce que voit l’utilisateur de recherche

Lister les collections relève de la permission Solr `collection-admin-read`, et les chemins de requête d’une collection viennent de sa config, `config-read`. Accordez-les à l’utilisateur de recherche dans les règles de Solr ou, une fois l’agent installé, `admin_read` sur le cluster et `config_read` sur les collections. Les chemins des collections que l’utilisateur de recherche ne peut pas voir sont absents des suggestions. Un nœud sans SolrCloud a des cores au lieu de collections, et le plug-in les présente comme des collections.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `collection` | Joker, sensible à la casse ; le nom de la collection | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit`, `admin_collection` |
| `path` | Joker, sensible à la casse ; sous une collection ; le chemin de requête, par exemple `/select`, `/export` ou `/update/json` | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit` |
| `cluster` | Son unique valeur, `*` | `health`, `metrics`, `admin_read`, `security`, `admin` |

| Type d’accès | Permissions Solr | Implique |
| --- | --- | --- |
| `query` | `read` : rechercher, obtenir, exporter | — |
| `update` | `update` | — |
| `schema_read`, `schema_edit` | `schema-read`, `schema-edit` | `schema_edit` implique `schema_read` |
| `config_read`, `config_edit` | `config-read`, `config-edit` | `config_edit` implique `config_read` |
| `admin_collection` | `collection-admin-read` et `collection-admin-edit` des requêtes de l’API Collections qui nomment la collection : créer, recharger, sauvegarder, supprimer, etc. | `query`, `update`, `schema_edit`, `config_edit` |
| `health`, `metrics` | `health`, `metrics-read` | — |
| `admin_read` | `collection-admin-read` du cluster entier, `core-admin-read`, `zk-read`, `filestore-read`, `package-read`, et la lecture de la config et du schéma des nœuds | `health`, `metrics` |
| `security` | `security-read`, `security-edit` | — |
| `admin` | Tout | `admin_read`, `security`, `admin_collection` |

Une requête vers une collection est décidée sur la collection et sur la collection avec son chemin de requête, et un refus sur l’un ou l’autre la rejette ; une politique peut donc autoriser une collection et refuser son gestionnaire `/export`, ou n’autoriser la mise à jour que par `/update*`. Une requête vers plusieurs collections exige chacune d’elles. Sous une collection, le chemin de requête est le gestionnaire vers lequel Solr achemine la requête, tel que Solr le nomme.

Une requête de l’API Collections qui nomme des collections, par son paramètre `name` ou `collection`, exige `admin_collection` sur chacune ; une politique sur `team-*` permet donc à une équipe de créer des collections dont le nom commence par `team-`, de les recharger, sauvegarder ou supprimer, mais aucune autre. Une requête qui n’en nomme aucune, comme lister toutes les collections, relève du cluster. `admin` sur le cluster accorde tout, comme la permission `all` de Solr. Dans les valeurs de politique, `*` correspond à toute suite de caractères et `?` à un seul. L’éditeur de politiques suggère les collections et les chemins de requête des collections qu’une politique nomme.

### Adresses des clients

Un élément de politique peut ne valoir que pour certains clients : sa condition **Adresses des clients** (`ip-range`) accepte des adresses et des plages comme `10.1.0.0/16` ou `2001:db8::/32`. Les noms d’hôte ne sont pas résolus. Derrière un proxy inverse, le client est le proxy, sauf si l’agent lui fait confiance : le client est alors la dernière adresse de `X-Forwarded-For` qu’aucun proxy de confiance n’a ajoutée, de sorte qu’un client ne peut pas se prévaloir d’une adresse de son choix.

## Exemple de politiques

Supposons que le cluster ait les collections `sales` et `hr`. Créez ces politiques d’accès activées dans le même service de données :

| Ressources | Règle |
| --- | --- |
| `collection=sales` | Autoriser `query` au groupe `analysts` |
| `collection=sales`, `path=/export` | Refuser `query` à l’utilisateur `alice` |
| `collection=sales`, `path=/update*` | Autoriser `update` à l’utilisateur `ingest` |
| `collection=team-*` | Autoriser `admin_collection` au groupe `search-team` |
| `cluster=*` | Autoriser `metrics` et `health` à l’utilisateur `monitoring` |
| `collection=hr` | Autoriser `query` au groupe `hr-staff`, depuis `10.0.0.0/8` |
| `cluster=*` | Autoriser `admin` au groupe `solr-admins` |

Une fois que l’agent applique le nouvel instantané, les membres d’`analysts` interrogent `sales`, `alice` tout sauf son gestionnaire d’export ; `ingest` met à jour `sales` par ses gestionnaires de mise à jour et ne peut pas l’interroger ; les membres de `search-team` créent et administrent leurs propres collections `team-` ; `monitoring` lit les métriques et l’état de santé ; les membres de `hr-staff` n’interrogent `hr` que depuis le réseau interne ; et les membres de `solr-admins` peuvent tout faire.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Les appliquer nécessite l’agent Solr dans chaque nœud. Le déploiement et les vérifications figurent dans l’[agent Solr](/fr/external/solr-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent Solr](/fr/external/solr-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
