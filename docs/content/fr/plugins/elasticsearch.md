---
title: Elasticsearch
description: Installer et configurer le plug-in de service Elasticsearch, joindre un ou plusieurs nœuds, se connecter avec un mot de passe, une clé d’API ou un certificat client TLS, rechercher les index, les flux de données, les champs et le cluster, et gérer qui peut lire, écrire et administrer quoi avec les privilèges propres d’Elasticsearch, jusqu’aux champs et aux documents.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Elasticsearch recherche les index, les flux de données, les champs et le cluster, et gère les politiques d’accès dans les termes d’Elasticsearch : lire des champs, indexer et supprimer des documents, créer, supprimer et administrer des index, et les opérations du cluster lui-même, avec des filtres de lignes d’index. Son identifiant de plug-in et son type de service sont tous deux `elasticsearch`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent Elasticsearch](/fr/external/elasticsearch-agent/), un service à côté du cluster, applique les politiques avec les rôles propres d’Elasticsearch, la sécurité au niveau des champs et la sécurité au niveau des documents.

Les tests d’intégration Testcontainers du dépôt couvrent Elasticsearch `8.19`, `9.3`, `9.4` et `9.5` avec la sécurité activée ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/elasticsearch`. Vérifiez que `elasticsearch` est activé dans **Administration de la plateforme → Plug-ins** ; relancez l’analyse du répertoire des plug-ins après une mise à jour. Le plug-in communique avec les nœuds par le client REST bas niveau d’Elasticsearch, dans un chargeur de classes qui lui est propre.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Elasticsearch (`elasticsearch`).
2. Saisissez un nœud sous forme d’URL, ou plusieurs nœuds.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez ses identifiants, testez la connexion et enregistrez le service. Le test se connecte et décrit le cluster : sa version d’Elasticsearch et son nom, la licence et si elle offre la sécurité au niveau des documents et des champs, l’utilisateur connecté et le nombre d’index et de flux de données. Un cluster sans sécurité échoue au test, car l’agent applique les politiques avec les rôles d’Elasticsearch.

L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des utilisateurs qui lisent et écrivent. Un test de connexion réussi ne signifie pas que l’accès est déjà soumis aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `elasticsearch.hosts` | Obligatoire : un nœud sous forme d’URL, par exemple `https://es1.example.com:9200` ; plusieurs séparés par des virgules, essayés tour à tour lorsqu’un nœud est injoignable. Sans schéma, un nœud est `https`, et le port par défaut est `9200` ; les nœuds d’un même service sont tous `http` ou tous `https` |
| `authentication` | `password` (par défaut), `api_key` ou `certificate` : un certificat client TLS qu’un realm PKI associe à un utilisateur |
| `username`, `password` | password : l’utilisateur de recherche et son mot de passe, stocké chiffré |
| `apiKey` | api_key : la clé d’API encodée, telle qu’Elasticsearch la renvoie à sa création, stockée chiffrée |
| `ssl.verificationMode` | Comme le nomme Kibana : `full` (par défaut) vérifie les certificats et les noms des nœuds, `certificate` leurs certificats seulement, `none` rien |
| `ssl.certificateAuthorities` | Chemin sur le **serveur GrantForge** des certificats d’AC PEM qui vérifient les nœuds, comme le `http_ca.crt` d’Elasticsearch ; vide utilise ceux de la JVM |
| `ssl.certificate`, `ssl.key` | certificate : chemins sur le serveur GrantForge du certificat client PEM et de sa clé privée, PKCS #8 ou l’ancien format OpenSSL ; ils exigent des nœuds `https` |
| `ssl.keyPassphrase` | certificate : la phrase secrète d’une clé privée chiffrée, stockée chiffrée |
| `timeout` | Durée maximale de la connexion et d’une requête, par défaut `30000`, plage `1..600000` millisecondes |

Les paramètres d’un mode de connexion sont refusés pour les autres, afin que le service indique exactement comment il se connecte. Les connexions refusées, les clés d’API révoquées, les privilèges manquants, les nœuds injoignables et les certificats invérifiables sont signalés comme tels.

### Ce que voit l’utilisateur de recherche

L’utilisateur de recherche doit pouvoir lire ce qu’il liste : l’API resolve d’Elasticsearch pour les index et les flux de données, les capacités des champs pour les champs et la description du cluster pour son nom. Un rôle avec `read` et `view_index_metadata` sur les index donne les deux premiers ; un utilisateur sans le privilège de cluster `monitor` réussit tout de même le test de connexion, qui ne cite alors ni la version ni la licence. Les index et flux de données dont le nom commence par un point, cachés et système, ne sont listés que lorsque la saisie commence par un point ; les index sous-jacents des flux de données sont listés sous leur flux de données. Les champs sont ceux des documents : les champs de métadonnées propres d’Elasticsearch, les objets et les multi-champs qui indexent à nouveau un champ, comme `name.keyword`, sont omis, car l’agent les accorde avec leur champ.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `index` | Caractères génériques, sensible à la casse ; le nom d’un index ou d’un flux de données | `index`, `delete`, `create_index`, `delete_index`, `view_index_metadata`, `monitor`, `manage` |
| `field` | Caractères génériques, sensible à la casse ; sous un index, le chemin d’un champ comme `address.city` | `read` |
| `cluster` | Caractères génériques, sensible à la casse ; le nom du cluster | `cluster_monitor`, `create_snapshot`, `manage_slm`, `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` |

Chaque type d’accès est le privilège Elasticsearch du même nom, sauf `cluster_monitor`, qui est le privilège de cluster `monitor` :

| Type d’accès | Ce qu’il permet |
| --- | --- |
| `read` | Recherches, lectures, comptages, recherches multiples et défilement |
| `index`, `delete` | Indexer et mettre à jour des documents, y compris en bulk ; supprimer des documents |
| `create_index`, `delete_index` | Créer des index, y compris lorsque la première écriture en crée un ; supprimer des index et des flux de données |
| `view_index_metadata`, `monitor` | Lire les mappings, les paramètres et les alias ; lire les statistiques et la récupération |
| `manage` | Toute action d’administration d’un index ; implique `create_index`, `delete_index`, `view_index_metadata` et `monitor` |
| `cluster_monitor` | Lire l’état de santé, l’état et les statistiques du cluster |
| `create_snapshot`, `manage_slm` | Créer des snapshots ; gérer les politiques de cycle de vie des snapshots, ce qui implique `create_snapshot` |
| `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` | Gérer les modèles d’index, les pipelines d’ingestion et les politiques de cycle de vie des index |

La lecture se décide par champ, comme la sécurité au niveau des champs montre les champs des documents : une politique `read` nomme des champs, et `*` désigne tous les champs. Une politique peut ainsi autoriser un index et refuser l’un de ses champs, et l’utilisateur lit tous les autres. Dans les valeurs des politiques, `*` correspond à toute suite de caractères et `?` à un caractère. L’éditeur de politiques suggère les index et les flux de données, les champs des index que nomme la politique et le cluster.

### Filtres de lignes

Une politique de filtre de lignes d’un index ne montre à l’utilisateur que les documents qui correspondent à une requête. La requête est le DSL de requêtes d’Elasticsearch tel que le prend la sécurité au niveau des documents, par exemple `{"term": {"region": "EU"}}`, et peut être un modèle, par exemple avec `{{_user.username}}`. Comme pour les autres services, un utilisateur reçoit le filtre de la première politique qui s’applique à lui. Un filtre de lignes ne peut interroger que des champs que l’utilisateur peut lire : la sécurité au niveau des champs cache aussi les autres à la requête.

Elasticsearch ne masque pas de valeurs, le type de service n’offre donc pas de masquage.

### La licence

Elasticsearch offre la sécurité au niveau des documents et des champs à partir d’une licence Platinum. En dessous, une politique qui ne laisse un utilisateur lire que certains champs ou certains documents d’un index ne peut pas être appliquée ; l’agent n’accorde donc pas à cet utilisateur la lecture de l’index plutôt que plus que ce que permettent les politiques, et le signale. Les politiques qui autorisent des index entiers, l’écriture et l’administration fonctionnent avec toute licence.

## Exemple de politiques

Supposons que le cluster, sous licence Platinum, possède les index `logs-2026.10.26` et `hr`. Créez ces politiques activées dans le même service de données :

| Ressource | Règle |
| --- | --- |
| `index=logs-*`, `field=*` | Autoriser `read` au groupe `analysts` |
| `index=logs-*`, `field=client_ip` | Refuser `read` au groupe `analysts` |
| `index=logs-*` | Autoriser `index` et `create_index` à l’utilisateur `logstash` |
| `index=hr`, `field=*` | Autoriser `read` au groupe `hr` |
| `index=hr` (filtre de lignes) | Ne montrer à l’utilisateur `emea-hr` que les documents de `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Autoriser `cluster_monitor` au groupe `ops` |

Une fois que l’agent a appliqué le nouvel instantané, les membres de `analysts` lisent tous les champs des journaux sauf l’adresse du client ; `logstash` écrit aussitôt le `logs-2026.10.27` du lendemain et le crée avec son premier document ; les membres de `hr` lisent les employés ; `emea-hr` ne voit que les employés de la région ; et les membres de `ops` lisent l’état de santé et les statistiques du cluster.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources ; les appliquer exige un agent Elasticsearch par cluster. Le déploiement et les vérifications sont décrits dans [Agent Elasticsearch](/fr/external/elasticsearch-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent Elasticsearch](/fr/external/elasticsearch-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
