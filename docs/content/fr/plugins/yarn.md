---
title: Apache Hadoop YARN
description: Installer et configurer le plug-in de service YARN, joindre un ResourceManager ou une paire hautement disponible, se connecter en simple ou avec Kerberos, rechercher les files d’attente et les noms d’applications et gérer qui peut soumettre quelles applications et où.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in YARN recherche les files d’attente et les noms des applications qu’elles contiennent, et gère les politiques d’accès sur ce que YARN décide : soumettre des applications aux files d’attente, administrer les files d’attente et administrer le cluster. L’ID du plug-in et le type de service sont tous deux `yarn`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent YARN](/fr/external/yarn-agent/) installé dans les ResourceManagers applique les politiques.

Les tests d’intégration Testcontainers du dépôt couvrent les ResourceManagers de Hadoop `3.2.4`, construit à partir de la version Apache vérifiée par SHA-512, `3.3.6`, `3.4.3` et `3.5.0`, ainsi qu’un ResourceManager Kerberos `3.4.3` ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution contient le plug-in dans `plugins/yarn`. Vérifiez dans **Administration de la plateforme → Plug-ins** que `yarn` est activé ; analysez à nouveau le répertoire des plug-ins après une mise à jour. Le plug-in dialogue avec le ResourceManager via le client YARN de Hadoop lui-même, sur le client ombré de Hadoop 3.5.0, dans son propre chargeur de classes.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Apache Hadoop YARN (`yarn`).
2. Saisissez l’adresse client du ResourceManager, ou celle de chaque ResourceManager d’un cluster hautement disponible.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez-le, testez la connexion et enregistrez le service. Le test se connecte et décrit le cluster : combien de NodeManagers et combien de files d’attente il comporte.

Les paramètres portent les noms de Hadoop et peuvent être repris d’un `yarn-site.xml`. L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des utilisateurs qui soumettent des applications. Un test de connexion réussi ne signifie pas que les soumissions sont déjà soumises aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `yarn.resourcemanager.address` | Obligatoire : le ResourceManager, `host` ou `host:port`, port par défaut `8032` ; plusieurs, séparés par des virgules, pour un cluster hautement disponible, entre lesquels le client bascule |
| `hadoop.security.authentication` | `simple` (par défaut) : un nom d’utilisateur ; `kerberos` : un principal avec keytab ou mot de passe |
| `username` | Utilisateur de recherche obligatoire ; avec Kerberos, le principal complet, par exemple `grantforge@EXAMPLE.COM` |
| `password` | Kerberos : le mot de passe du principal, si aucun keytab n’est indiqué ; stocké chiffré |
| `keytab` | Kerberos : chemin du keytab du principal, lisible sur le **serveur GrantForge** |
| `kerberos.kdc` | Kerberos : KDC du realm du principal, `host[:port]`, séparés par des virgules ; vide utilise le `krb5.conf` du serveur |
| `yarn.resourcemanager.principal` | Kerberos : le principal des ResourceManagers, par défaut `yarn/_HOST@` suivi du realm du principal de recherche |
| `hadoop.rpc.protection` | `authentication` (par défaut), `integrity` ou `privacy`, comme dans le cluster |
| `timeout` | Durée permise pour une requête, par défaut `30000`, plage `1..600000` millisecondes ; le client YARN lui-même réessaierait pendant un quart d’heure |

Le service est exactement ce que disent ses paramètres : aucun `core-site.xml` ni `yarn-site.xml` du serveur GrantForge n’est lu. Les paramètres Kerberos sont refusés en authentification simple ; Kerberos exige un mot de passe ou un keytab. Un ResourceManager qui refuse la connexion, par exemple parce qu’il attend Kerberos, est signalé comme une connexion refusée.

### Kerberos

Avec `kerberos`, GrantForge connecte le principal via la connexion de Hadoop, à partir du keytab ou avec le mot de passe, conserve cette connexion et la renouvelle à partir du keytab ; rien d’autre sur le serveur GrantForge n’a besoin de paramètres Kerberos. Les realms dont les KDC figurent dans `kerberos.kdc` sont déclarés pour tout le serveur GrantForge, comme le font les plug-ins HDFS, Hive, Kafka, HBase et Trino, de sorte que plusieurs services peuvent utiliser des realms différents côte à côte.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `queue` | Joker, sensible à la casse ; le chemin de la file depuis la racine, par exemple `root.sales.etl` | `submit_app`, `admin_queue` |
| `application` | Joker, sensible à la casse ; sous une file d’attente ; le nom de l’application | `submit_app` |
| `cluster` | Sa seule valeur, `*` | `admin` : l’administration de YARN, par exemple recharger les files d’attente, les nœuds et les étiquettes de nœud |

`admin_queue` implique `submit_app`, car les administrateurs d’une file d’attente peuvent y soumettre.

Comme dans YARN, une permission sur une file d’attente couvre les files situées en dessous : `submit_app` sur `root.sales` soumet à `root.sales.etl`. Une vérification est décidée dans chaque file du chemin, depuis la racine, et un refus dans l’une d’elles la rejette ; une politique peut ainsi autoriser `root.sales` et refuser `root.sales.adhoc`. Sous une file d’attente, un niveau application indique à quelles applications une permission s’applique : `submit_app` sur `root.hr` avec l’application `payroll-*` n’accepte que les applications dont le nom commence par `payroll-`, dans `root.hr` et les files en dessous.

Dans les valeurs de politique, `*` correspond à un nombre quelconque de caractères et `?` à un seul. L’éditeur de politiques propose les files d’attente par leur chemin, ou par leur propre nom quand c’est ce qui est saisi, quel que soit l’ordonnanceur du cluster, ainsi que les noms des applications dont le ResourceManager se souvient encore dans les files nommées par une politique.

### Adresses des clients

Un élément de politique peut ne valoir que pour certains clients : sa condition **Adresses des clients** (`ip-range`) accepte des adresses, des plages comme `10.1.0.0/16` et des noms d’hôte, comme les listes d’hôtes de Hadoop. Derrière un proxy, par exemple le proxy web de YARN ou un routeur, le client est la première adresse que les proxys ont transmise. Préférez les adresses et les plages aux noms d’hôte, qui coûtent une résolution de nom du client. YARN pose les questions d’administration sans l’adresse du client, la condition ne tient donc jamais sur `cluster`.

## Exemple de politiques

Supposons que le cluster exécute les files d’attente `root.sales.etl`, `root.sales.adhoc` et `root.hr`. Créez ces politiques d’accès activées dans le même service de données :

| Ressources | Règle |
| --- | --- |
| `queue=root.sales` | Autoriser `submit_app` pour le groupe `analysts` |
| `queue=root.sales.adhoc` | Refuser `submit_app` pour l’utilisateur `alice` |
| `queue=root.hr`, `application=payroll-*` | Autoriser `submit_app` pour l’utilisateur `bob` |
| `queue=root.sales` | Autoriser `admin_queue` pour l’utilisateur `carol` |
| `queue=root` | Autoriser `submit_app` pour le groupe `etl-robots`, depuis `10.0.0.0/8` |
| `cluster=*` | Autoriser `admin` pour le groupe `yarn-admins` |

Une fois que l’agent applique le nouvel instantané, les membres de `analysts` soumettent à `root.sales.etl` et `root.sales.adhoc`, mais `alice` seulement à `root.sales.etl` ; `bob` soumet des applications de paie à `root.hr` et rien d’autre ; `carol` soumet aux files des ventes et y arrête les applications ; les membres de `etl-robots` soumettent partout, mais uniquement depuis le réseau interne ; et les membres de `yarn-admins` rechargent les files d’attente.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Pour les appliquer, il faut l’agent YARN dans chaque ResourceManager. Le déploiement et les vérifications sont décrits dans l’[agent YARN](/fr/external/yarn-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent YARN](/fr/external/yarn-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
