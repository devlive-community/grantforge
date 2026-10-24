---
title: Apache Pulsar
description: Installer et configurer le plug-in de service Pulsar, joindre un broker ou plusieurs, se connecter avec un jeton, un mot de passe, OAuth 2.0 ou un certificat client, rechercher les tenants, namespaces, topics et abonnements et gérer qui peut produire, consommer et administrer quoi.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Pulsar recherche les tenants, namespaces, topics, abonnements et clusters, et gère les politiques d’accès sur ce que Pulsar décide : produire, consommer, les functions, sources, sinks et packages, et l’administration des topics, namespaces, tenants et clusters. L’ID du plug-in et le type de service sont tous deux `pulsar`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent Pulsar](/fr/external/pulsar-agent/) installé dans les brokers applique les politiques.

Les tests d’intégration Testcontainers du dépôt couvrent les brokers de Pulsar `3.0.17`, `3.3.9`, `4.0.14` et `4.2.5` ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution contient le plug-in dans `plugins/pulsar`. Vérifiez dans **Administration de la plateforme → Plug-ins** que `pulsar` est activé ; analysez à nouveau le répertoire des plug-ins après une mise à jour. Le plug-in dialogue avec le service web des brokers via le client d’administration de Pulsar lui-même, en version 4.2.5, dans son propre chargeur de classes.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Apache Pulsar (`pulsar`).
2. Saisissez l’URL du service web d’un broker, ou de plusieurs brokers.
3. Choisissez comment le rôle de recherche se connecte, saisissez ses identifiants, testez la connexion et enregistrez le service. Le test se connecte et décrit le cluster : ses clusters et combien de tenants il comporte.

Les paramètres portent les noms du `client.conf` de Pulsar et peuvent en être repris. Le rôle de recherche permet à GrantForge de découvrir les ressources ; il est distinct des rôles qui produisent et consomment. Un test de connexion réussi ne signifie pas que l’accès est déjà soumis aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `webServiceUrl` | Obligatoire : le service web d’un broker, `http://host:port` ou `https://host:port`, port par défaut `8080` ou `8443` ; plusieurs brokers séparés par des virgules, par exemple `http://broker1:8080,broker2:8080` |
| `authentication` | `none` (par défaut), `token`, `basic`, `oauth2` ou `tls` |
| `token` | token : le jeton du rôle de recherche ; stocké chiffré |
| `username`, `password` | basic : le nom d’utilisateur et le mot de passe du rôle de recherche ; le mot de passe est stocké chiffré |
| `issuerUrl`, `privateKey`, `audience`, `scope` | oauth2 : le serveur d’autorisation, le chemin du fichier JSON d’identifiants avec `client_id` et `client_secret` sur le **serveur GrantForge**, et l’audience et le scope, si les brokers les attendent |
| `tlsCertificateFilePath`, `tlsKeyFilePath` | tls : chemins du certificat client et de sa clé privée, PEM, sur le **serveur GrantForge** |
| `tlsTrustCertsFilePath` | https : les certificats d’autorité qui vérifient ceux des brokers, PEM ; vide utilise ceux de la JVM |
| `tlsEnableHostnameVerification` | https : si le certificat d’un broker doit nommer son hôte, par défaut `true` |
| `timeout` | Durée permise pour une requête, par défaut `30000`, plage `1..600000` millisecondes |

Les paramètres d’un mode de connexion sont refusés avec un autre, de sorte qu’un service dit exactement comment il se connecte. Une connexion refusée, un rôle qui ne peut pas voir quelque chose et un broker injoignable, y compris derrière un proxy qui répond à sa place, sont signalés comme tels.

### Ce que voit le rôle de recherche

Pulsar ne laisse que ses superutilisateurs lister les clusters et les tenants. Nommez le rôle de recherche dans `superUserRoles` des brokers ou, une fois l’agent installé, autorisez-lui `admin` sur le cluster, ce qui en fait aussi un superutilisateur. Les namespaces, topics et abonnements que le rôle de recherche ne peut pas voir sont absents des suggestions.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `tenant` | Joker, sensible à la casse | Tous sauf `admin` |
| `namespace` | Joker, sensible à la casse ; sous un tenant ; le nom propre du namespace, par exemple `orders` | `produce`, `consume`, `functions`, `sources`, `sinks`, `packages`, `admin_topic`, `admin_namespace` |
| `topic` | Joker, sensible à la casse ; sous un namespace ; le nom propre du topic, par exemple `payments` | `produce`, `consume`, `admin_topic` |
| `subscription` | Joker, sensible à la casse ; sous un topic ; le nom de l’abonnement | `consume` |
| `cluster` | Joker, sensible à la casse ; le nom du cluster, par exemple `standalone` | `admin` : l’accès superutilisateur de Pulsar |

| Type d’accès | Implique |
| --- | --- |
| `produce`, `consume`, `functions`, `sources`, `sinks`, `packages` | — |
| `admin_topic` : terminer, compacter, déporter, décharger et tronquer les topics, et modifier leurs politiques | `produce`, `consume` |
| `admin_namespace` : créer et supprimer des topics, et modifier les politiques du namespace | `admin_topic` |
| `admin_tenant` : créer, lister et supprimer des namespaces | `admin_namespace`, `functions`, `sources`, `sinks`, `packages` |
| `admin` : tout ce que peuvent les superutilisateurs de Pulsar | `admin_tenant` |

Comme avec les autorisations de Pulsar elles-mêmes, une permission sur un tenant ou un namespace couvre ce qu’il contient : `produce` sur le namespace `orders` du tenant `sales` produit dans chacun de ses topics. Une vérification est décidée tour à tour au tenant, au namespace, au topic et à l’abonnement, et un refus à l’un d’eux la rejette ; une politique peut ainsi autoriser un namespace et refuser l’un de ses topics. Sous un topic, un niveau abonnement indique par quels abonnements un rôle peut consommer : `consume` sur le topic `*` avec l’abonnement `billing-*` laisse un rôle consommer chaque topic du namespace, mais seulement par des abonnements dont le nom commence par `billing-`.

Un topic est décidé par son nom propre, sans son domaine, de sorte qu’un topic persistant et un topic non persistant de même nom partagent leurs politiques ; une partition d’un topic partitionné est décidée comme le topic partitionné. Dans les valeurs de politique, `*` correspond à un nombre quelconque de caractères et `?` à un seul. L’éditeur de politiques propose les clusters, les tenants, et les namespaces, topics et abonnements de ce que nomme une politique.

### Adresses des clients

Un élément de politique peut ne valoir que pour certains clients : sa condition **Adresses des clients** (`ip-range`) accepte des adresses et des plages comme `10.1.0.0/16` ou `2001:db8::/32`. Les noms d’hôte ne sont pas résolus. Derrière un proxy, le client est le proxy, sauf s’il transmet l’adresse propre du client : avec le protocole HAProxy pour les producteurs et consommateurs, et avec `X-Forwarded-For` pour le service web lorsque les brokers lui font confiance.

## Exemple de politiques

Supposons que le tenant `sales` ait les namespaces `orders` et `returns`. Créez ces politiques d’accès activées dans le même service de données :

| Ressources | Règle |
| --- | --- |
| `tenant=sales`, `namespace=orders` | Autoriser `produce` pour le groupe `analysts` |
| `tenant=sales`, `namespace=orders`, `topic=audit` | Refuser `produce` pour l’utilisateur `alice` |
| `tenant=sales`, `namespace=orders`, `topic=*`, `subscription=billing-*` | Autoriser `consume` pour l’utilisateur `alice` |
| `tenant=sales` | Autoriser `admin_tenant` pour l’utilisateur `bob` |
| `tenant=public` | Autoriser `produce` pour le groupe `ingest`, depuis `10.0.0.0/8` |
| `cluster=standalone` | Autoriser `admin` pour le groupe `pulsar-admins` |

Une fois que l’agent applique le nouvel instantané, les membres de `analysts` produisent dans chaque topic de `sales/orders`, `alice` dans tous sauf `audit` ; `alice` y consomme uniquement par les abonnements `billing-` ; `bob` crée et administre des namespaces et des topics dans `sales` ; les membres de `ingest` produisent dans le tenant `public` depuis le réseau interne uniquement ; et les membres de `pulsar-admins` sont des superutilisateurs de Pulsar.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Pour les appliquer, il faut l’agent Pulsar dans chaque broker. Le déploiement et les vérifications sont décrits dans l’[agent Pulsar](/fr/external/pulsar-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent Pulsar](/fr/external/pulsar-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
