---
title: OpenSearch
description: Installer et configurer le plug-in de service OpenSearch, joindre un ou plusieurs nœuds, se connecter avec un mot de passe ou un certificat client TLS, rechercher index, flux de données, champs et le cluster, et gérer qui peut lire, écrire et administrer quoi, jusqu’aux champs, aux valeurs masquées et aux documents.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in OpenSearch recherche les index, les flux de données, les champs et le cluster, et gère les politiques d’accès dans les termes d’OpenSearch : lire des champs, indexer, supprimer, créer et administrer des index, et les opérations du cluster lui-même, avec des masques de champs et des filtres de lignes d’index. Son identifiant de plug-in et son type de service sont tous deux `opensearch`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent OpenSearch](/fr/external/opensearch-agent/), un service à côté du cluster, applique les politiques avec les rôles, la sécurité au niveau des champs, les champs masqués et la sécurité au niveau des documents du plug-in de sécurité.

Les tests d’intégration Testcontainers du dépôt couvrent OpenSearch `2.19`, `3.3`, `3.6` et `3.9` avec le plug-in de sécurité ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/opensearch`. Vérifiez que `opensearch` est activé dans **Administration de la plateforme → Plug-ins** ; relancez l’analyse du répertoire des plug-ins après une mise à jour. Le plug-in communique avec les nœuds par le client REST bas niveau d’OpenSearch, dans un chargeur de classes qui lui est propre.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez OpenSearch (`opensearch`).
2. Saisissez un nœud sous forme d’URL, ou plusieurs nœuds.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez ses identifiants, testez la connexion et enregistrez le service. Le test se connecte et décrit le cluster : sa version d’OpenSearch et son nom, l’utilisateur connecté et le nombre d’index et de flux de données. Un cluster sans plug-in de sécurité échoue au test, car l’agent applique les politiques grâce à lui.

L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des utilisateurs qui lisent et écrivent. Un test de connexion réussi ne signifie pas que l’accès est déjà soumis aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `opensearch.hosts` | Obligatoire : un nœud sous forme d’URL, par exemple `https://opensearch1.example.com:9200` ; plusieurs séparés par des virgules, essayés tour à tour lorsqu’un nœud est injoignable. Sans schéma, un nœud est `https`, et le port par défaut est `9200` ; les nœuds d’un même service sont tous `http` ou tous `https` |
| `authentication` | `password` (par défaut) ou `certificate` : un certificat client TLS que le plug-in de sécurité associe à un utilisateur |
| `username`, `password` | password : l’utilisateur de recherche et son mot de passe, stocké chiffré |
| `ssl.verificationMode` | Comme OpenSearch Dashboards le nomme : `full` (par défaut) vérifie les certificats et les noms des nœuds, `certificate` leurs certificats seulement, `none` rien |
| `ssl.certificateAuthorities` | Chemin sur le **serveur GrantForge** des certificats d’AC PEM qui vérifient les nœuds ; vide utilise ceux de la JVM |
| `ssl.certificate`, `ssl.key` | certificate : chemins sur le serveur GrantForge du certificat client PEM et de sa clé privée, PKCS #8 ou l’ancienne forme OpenSSL ; ils exigent des nœuds `https` |
| `ssl.keyPassphrase` | certificate : la phrase secrète d’une clé privée chiffrée, stockée chiffrée |
| `timeout` | Durée permise pour la connexion et une requête, par défaut `30000`, plage `1..600000` millisecondes |

Les paramètres d’une méthode de connexion sont refusés pour l’autre, de sorte que le service indique exactement comment il se connecte. Les connexions refusées, les permissions manquantes, les nœuds injoignables et les certificats invérifiables sont signalés comme tels.

### Ce que voit l’utilisateur de recherche

L’utilisateur de recherche doit pouvoir lire ce qu’il liste : l’API resolve d’OpenSearch pour les index et flux de données, les capacités de champs (field capabilities) pour les champs et la description du cluster pour son nom. Le rôle intégré `readall` donne les deux premiers ; un utilisateur sans `cluster:monitor/main` réussit quand même le test de connexion, qui ne nomme alors pas la version. Les index et flux de données dont le nom commence par un point, cachés et système, ne sont listés que si la saisie commence par un point ; les index de stockage des flux de données sont listés par leur flux. Les champs sont ceux des documents : les champs de métadonnées d’OpenSearch, les objets et les multi-champs qui indexent un champ une fois de plus, comme `name.keyword`, sont omis, car la sécurité au niveau des champs les montre ou les cache avec leur champ.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `index` | Jokers, sensible à la casse ; le nom d’un index ou d’un flux de données | `index`, `delete`, `create_index`, `manage_aliases`, `monitor`, `manage` |
| `field` | Jokers, sensible à la casse ; sous un index, le chemin d’un champ comme `address.city` | `read` |
| `cluster` | Jokers, sensible à la casse ; le nom du cluster | `cluster_monitor`, `manage_snapshots`, `manage_templates`, `manage_pipelines` |

| Type d’accès | Groupe d’actions du plug-in de sécurité |
| --- | --- |
| `read` | `read` : recherches, lectures, comptages et similaires |
| `index` | `index` : indexer et mettre à jour des documents, y compris par bulk |
| `delete` | `delete` : supprimer des documents |
| `create_index` | `create_index` : créer des index, y compris lorsque la première écriture en crée un |
| `manage_aliases`, `monitor` | `manage_aliases`, `indices_monitor` |
| `manage` | `manage` : toute action d’administration d’un index, sa suppression comprise ; il implique `create_index`, `manage_aliases` et `monitor` |
| `cluster_monitor`, `manage_snapshots` | `cluster_monitor`, `manage_snapshots` |
| `manage_templates`, `manage_pipelines` | `cluster_manage_index_templates`, `cluster_manage_pipelines` |

La lecture se décide par champ, comme la sécurité au niveau des champs montre les champs des documents : une politique `read` nomme des champs, et `*` désigne tous les champs. Une politique peut ainsi autoriser un index et refuser l’un de ses champs, et l’utilisateur lit tous les autres. Dans les valeurs de politique, `*` correspond à toute suite de caractères et `?` à un caractère. L’éditeur de politiques propose les index et flux de données, les champs des index que la politique nomme et le cluster.

Utiliser ce qu’une politique autorise demande davantage dans OpenSearch : multi-search, multi-get et défilement pour lire, bulk pour écrire. Ce ne sont pas des types d’accès à part ; l’agent accorde avec eux les opérations composites du cluster.

### Masques

Une politique de masquage d’un champ montre à l’utilisateur ses valeurs masquées, comme les champs masqués du plug-in de sécurité, dans les documents comme dans les agrégations :

| Masque | Ce que voit l’utilisateur |
| --- | --- |
| Caviarder (Redact) | Majuscules en `X`, minuscules en `x` et chiffres en `n` |
| Masque partiel : montrer les 4 derniers / 4 premiers | Chaque autre caractère en `x` |
| Hachage (SHA-256) | Le hachage SHA-256 de la valeur |
| Hachage avec le sel du cluster (BLAKE2b) | Le hachage propre au plug-in de sécurité, clé avec le sel du cluster, de sorte que des valeurs égales restent égales sans pouvoir être retrouvées |
| Personnalisé | Un algorithme de hachage comme `SHA-512`, ou des expressions régulières avec leurs remplacements comme `/[0-9]/::*` |
| Non masqué | La valeur telle quelle |

### Filtres de lignes

Une politique de filtre de lignes d’un index ne montre à l’utilisateur que les documents qu’une requête trouve. La requête est le DSL de requête d’OpenSearch tel que la sécurité au niveau des documents le prend, par exemple `{"term": {"region": "EU"}}`, et peut utiliser les substitutions du plug-in de sécurité comme `${user.name}`. Comme pour les autres services, un utilisateur reçoit le filtre de la première politique qui s’applique à lui. Un filtre de lignes ne peut tester que les champs que l’utilisateur peut lire : la sécurité au niveau des champs cache aussi les autres à la requête.

## Exemple de politiques

Supposons que le cluster ait les index `logs-2026.10.26` et `hr`. Créez ces politiques activées dans le même service de données :

| Ressource | Règle |
| --- | --- |
| `index=logs-*`, `field=*` | Autoriser le groupe `analysts` à `read` |
| `index=logs-*`, `field=client_ip` | Refuser `read` au groupe `analysts` |
| `index=logs-*` | Autoriser l’utilisateur `logstash` à `index` et `create_index` |
| `index=hr`, `field=*` | Autoriser le groupe `hr` à `read` |
| `index=hr`, `field=salary` (masquage) | Montrer au groupe `hr` le hachage avec le sel du cluster |
| `index=hr` (filtre de lignes) | Ne montrer à l’utilisateur `emea-hr` que les documents de `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Autoriser le groupe `ops` à `cluster_monitor` |

Une fois que l’agent applique le nouvel instantané, les membres d’`analysts` lisent tous les champs des journaux sauf l’adresse du client ; `logstash` écrit aussitôt le `logs-2026.10.27` de demain, en le créant avec son premier document ; les membres de `hr` voient les salaires hachés ; `emea-hr` ne voit que les employés de la région ; et les membres d’`ops` lisent l’état et les statistiques du cluster.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources ; les appliquer demande un agent OpenSearch par cluster. Le déploiement et les vérifications sont décrits dans [Agent OpenSearch](/fr/external/opensearch-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent OpenSearch](/fr/external/opensearch-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
