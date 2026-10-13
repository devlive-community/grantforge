---
title: Apache Hive
description: Installer et configurer le plug-in de service Hive, se connecter par mot de passe ou Kerberos, rechercher bases, tables, colonnes et UDF, et gérer les politiques d’accès, de masquage et de filtrage des lignes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Hive fournit pour HiveServer2 la recherche de bases, de tables, de colonnes et d’UDF permanentes, ainsi que des politiques d’accès, de masquage et de filtrage des lignes, sur le modèle du service Hive d’Apache Ranger. Son identifiant de plug-in et son type de service sont tous deux `hive`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent Hive](/fr/external/hive-agent/), installé dans HiveServer2, applique les politiques.

Les tests d’intégration Testcontainers du dépôt couvrent HiveServer2 `3.1.3`, `4.0.1` et `4.1.0`, dont une connexion Kerberos à `4.0.1` ; les autres versions ne sont pas certifiées pour autant.

## Installation

La distribution contient le plug-in dans `plugins/hive`. Vérifiez sous **Administration de la plateforme → Plug-ins** que `hive` est activé ; relancez l’analyse du répertoire des plug-ins après une mise à jour. Le plug-in dialogue avec HiveServer2 par le pilote JDBC de Hive lui-même (`hive-jdbc` 4.0.1, standalone), dans son propre chargeur de classes ; il fonctionne donc aussi bien avec HiveServer2 3.1 qu’avec 4.x.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Apache Hive (`hive`).
2. Saisissez l’URL JDBC de HiveServer2, par exemple `jdbc:hive2://hiveserver2.example.com:10000/default`. La découverte par ZooKeeper fonctionne aussi : `jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez-le, testez la connexion et enregistrez le service. Le test se connecte et liste les bases, comme le font les recherches.

L’utilisateur de recherche sert uniquement à GrantForge pour découvrir les ressources ; il est distinct des utilisateurs qui exécutent les requêtes. Un test de connexion réussi ne signifie pas que les requêtes sont déjà soumises aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `jdbc.url` | URL HiveServer2 obligatoire `jdbc:hive2://hôtes/base;variables de session`, avec TLS (`;ssl=true;sslTrustStore=…`), le transport HTTP ou, pour Kerberos, le principal du serveur (`;principal=hive/_HOST@EXAMPLE.COM`) |
| `authentication` | `password` (par défaut) : un utilisateur et son mot de passe, pour LDAP, PAM ou sans authentification ; `kerberos` : un principal avec keytab ou mot de passe |
| `username` | Utilisateur de recherche obligatoire ; avec Kerberos, le principal complet, par exemple `grantforge@EXAMPLE.COM` |
| `password` | Le mot de passe de l’utilisateur, ou celui du principal Kerberos à défaut de keytab ; stocké chiffré |
| `keytab` | Kerberos : chemin de la keytab du principal, lisible sur le **serveur GrantForge** |
| `kerberos.kdc` | Kerberos : KDC du domaine du principal, `host[:port]` séparés par des virgules ; vide, le `krb5.conf` du serveur s’applique |
| `read.timeout.ms` | Délai de lecture, `30000` par défaut, plage `1..600000` |

L’URL ne peut porter ni identifiants ni rien qui lise des fichiers ou des jetons sur le serveur GrantForge : `user`, `password`, `initFile`, `kerberosAuthType`, `delegationToken`, `storePasswordPath`, `jwt` et les variables `browser…` sont refusés, quelle que soit leur casse. `auth` ne peut valoir que `noSasl`. Un service Kerberos doit avoir `principal` dans l’URL ; un service à mot de passe ne doit pas en avoir.

### Kerberos

Avec `kerberos`, GrantForge connecte lui-même le principal, par keytab ou mot de passe, et ouvre la connexion sous ce principal ; aucun autre réglage Kerberos n’est nécessaire sur le serveur GrantForge. Les domaines dont les KDC figurent dans `kerberos.kdc` sont déclarés pour tout le serveur GrantForge, comme le fait le plug-in HDFS, de sorte que plusieurs services Hive et HDFS peuvent utiliser des domaines différents côte à côte.

## Ressources et types d’accès

| Niveau | Correspondance | Remarques |
| --- | --- | --- |
| `database` | Jokers, sans casse | Les politiques peuvent s’arrêter ici |
| `table` | Jokers, sans casse | Sous une base ; les politiques peuvent s’arrêter ici |
| `column` | Jokers, sans casse | Sous une table |
| `udf` | Jokers, sans casse | Fonctions permanentes, sous une base |
| `url` | Chemins, sensibles à la casse, récursifs | Données que les instructions lisent ou écrivent, par exemple `hdfs://nn:8020/landing` ; types d’accès `read` et `write` |
| `hiveservice` | Jokers | HiveServer2 lui-même ; type d’accès `serviceadmin` |
| `global` | Jokers | Fonctions temporaires et fichiers ajoutés ; type d’accès `tempudfadmin` |

Les types d’accès sont ceux de Hive : `select`, `update`, `create`, `drop`, `alter`, `index`, `lock`, `read`, `write`, `repladmin`, `serviceadmin`, `tempudfadmin`, `refresh` et `all`. Comme dans Ranger, `all` inclut tous les types sauf `tempudfadmin`, qui permet à un utilisateur d’exécuter le code de son choix dans HiveServer2.

Dans les valeurs des politiques, `*` correspond à un nombre quelconque de caractères et `?` à un seul. L’éditeur de politiques suggère des noms par préfixe : bases, tables et UDF des bases nommées par la politique, et colonnes de ses tables.

## Masquage

Les politiques de masquage s’appliquent aux colonnes. Chaque type de masque est l’une des fonctions de masquage de Hive, où `{col}` désigne la colonne :

| Type de masque | Affiche |
| --- | --- |
| `mask` | Majuscules en `X`, minuscules en `x` et chiffres en `n` |
| `mask_show_last_4` | Seulement les quatre derniers caractères |
| `mask_show_first_4` | Seulement les quatre premiers caractères |
| `mask_hash` | Un hachage SHA-256 |
| `mask_null` | `NULL` |
| `mask_none` | La valeur inchangée |
| `mask_date_show_year` | Le 1er janvier de l’année de la date |
| `custom` | Une expression Hive de votre choix, avec `{col}` pour la colonne, par exemple `concat('****', substr({col}, -4))` |

Ainsi, `mask_show_last_4` affiche `4111111111111111` sous la forme `xxxxxxxxxxxx1111`.

## Filtres de lignes

Les politiques de filtrage des lignes s’appliquent aux tables. Un filtre est une condition Hive sur les colonnes de la table, par exemple `region = 'EU'` : les requêtes ne voient que les lignes qui la remplissent.

Les masques et les filtres de lignes n’accordent pas d’accès. Une politique d’accès distincte doit autoriser la requête.

## Exemple de politiques

Supposons que Hive possède une table `sales.orders` avec les colonnes `id`, `region` et `card`. Créez trois politiques activées dans le même service de données pour l’utilisateur `alice` :

| Type de politique | Ressources | Règle |
| --- | --- | --- |
| Accès | `database=sales`, `table=orders`, `column=*` | Autoriser `select` pour l’utilisateur `alice` |
| Masquage | `database=sales`, `table=orders`, `column=card` | Utiliser `mask_show_last_4` pour l’utilisateur `alice` |
| Filtre de lignes | `database=sales`, `table=orders` | Utiliser `region = 'EU'` pour l’utilisateur `alice` |

Dès que l’agent applique le nouvel instantané, `alice` ne voit que les lignes EU de `sales.orders`, chaque carte masquée sauf ses quatre derniers caractères ; `SHOW DATABASES` lui liste `sales`, et les autres bases la refusent.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Pour les appliquer, masques et filtres de lignes compris, chaque HiveServer2 a besoin de l’agent Hive. Son déploiement et ses contrôles sont décrits dans l’[agent Hive](/fr/external/hive-agent/).

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent Hive](/fr/external/hive-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
