---
title: Trino
description: Installer et configurer le plug-in de service Trino, se connecter par mot de passe, jeton web JSON ou Kerberos sur TLS, rechercher catalogues, schémas, tables, colonnes et propriétés de session, et gérer des politiques d’accès, de filtrage de lignes et de masquage dans les termes de Trino.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Trino recherche les catalogues, schémas, tables, colonnes et propriétés de session, et gère des politiques d’accès, de filtrage de lignes et de masquage sur ce que Trino vérifie : les données, les routines, les propriétés de session, les utilisateurs et les informations système. Son ID de plug-in et son type de service valent tous deux `trino`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent Trino](/fr/external/trino-agent/), installé dans le coordinator, applique les politiques.

Les tests d’intégration Testcontainers du dépôt couvrent Trino `470`, `476`, `479` et `484`, et sur `470` et `484` HTTPS avec un fichier de mots de passe et des jetons web JSON, ainsi que Kerberos. Cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/trino`. Vérifiez que `trino` est activé sous **Administration de la plateforme → Plug-ins** ; relancez l’analyse du répertoire des plug-ins après une mise à jour. Le plug-in parle au coordinator par le pilote JDBC de Trino (`trino-jdbc` 484), dans un chargeur de classes qui lui est propre, et ce pilote parle à toutes les versions de Trino ci-dessus.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et sélectionnez Trino (`trino`).
2. Saisissez l’URL JDBC du coordinator, par exemple `jdbc:trino://trino.example.com:8443`.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez-le, testez la connexion et enregistrez le service. Le test se connecte et décrit le coordinator : sa version et le nombre de catalogues que l’utilisateur voit.

Les paramètres portent les noms du pilote, ils se lisent donc comme la documentation JDBC de Trino. L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des utilisateurs qui interrogent. Un test de connexion réussi ne signifie pas que les requêtes sont déjà soumises aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `jdbc.url` | Obligatoire : `jdbc:trino://host:port`, éventuellement avec un catalogue et un schéma, et avec des propriétés du pilote autres que des identifiants, par exemple `?SSL=true` |
| `authentication` | `none` (par défaut) : le seul nom d’utilisateur ; `password` ; `jwt` : un jeton web JSON ; `kerberos` : un principal avec keytab ou mot de passe |
| `user` | Utilisateur de recherche obligatoire ; avec Kerberos, le principal complet, par exemple `grantforge@EXAMPLE.COM` |
| `password` | Le mot de passe de l’utilisateur, ou avec Kerberos celui du principal en l’absence de keytab ; stocké chiffré |
| `accessToken` | `jwt` : le jeton que le coordinator accepte ; stocké chiffré |
| `keytab` | Kerberos : chemin du keytab du principal, lisible sur le **serveur GrantForge** |
| `kerberos.kdc` | Kerberos : KDC du realm du principal, `host[:port]` séparés par des virgules ; vide utilise le `krb5.conf` du serveur |
| `KerberosRemoteServiceName` | Kerberos : la première partie du principal du coordinator, par défaut `trino` |
| `KerberosUseCanonicalHostname` | Kerberos : si le principal du coordinator prend le nom canonique de l’hôte de l’URL, par défaut `true` ; à désactiver derrière un répartiteur de charge ou un alias dont le principal porte le nom |
| `SSL` | Parler TLS ou non, par défaut `false` ; toute connexion autre que `none` l’exige, car Trino ne connecte les utilisateurs que sur TLS |
| `SSLTrustStorePath`, `SSLTrustStorePassword`, `SSLTrustStoreType` | TLS : le magasin de confiance qui vérifie le coordinator, son mot de passe et son type (`JKS` ou `PKCS12`) ; vide fait confiance à ce que Java approuve |
| `SSLVerification` | TLS : `FULL` (par défaut) vérifie le certificat et le nom d’hôte, `CA` le certificat seul, `NONE` rien et n’accepte aucun magasin de confiance |
| `timeout` | Délai des requêtes en millisecondes, par défaut `30000`, plage `1..600000` |

Le service est exactement ce que disent ses champs. L’URL ne peut porter ni identifiants ni ce qui connecterait autrement : `user`, `password`, `accessToken`, `sessionUser`, `extraCredentials`, l’authentification externe et OAuth 2.0, les fichiers et la délégation Kerberos, ainsi que les mots de passe des magasins de clés et de confiance y sont refusés. Les champs d’un autre mode de connexion sont refusés aussi, par exemple un jeton avec l’authentification par mot de passe.

Un coordinator dont le certificat ne peut être vérifié n’est pas contacté : le test le signale injoignable. Une connexion refusée, des permissions manquantes et un coordinator injoignable sont signalés comme tels.

### Kerberos

Avec `kerberos`, GrantForge connecte le principal, depuis le keytab ou avec le mot de passe, et remet au pilote les identifiants de cette connexion ; rien d’autre sur le serveur GrantForge n’a besoin de paramètres Kerberos. Les realms dont les KDC sont saisis dans `kerberos.kdc` sont déclarés pour tout le serveur GrantForge, comme le font les plug-ins HDFS, Hive, Kafka et HBase, si bien que plusieurs services peuvent utiliser des realms différents côte à côte. L’utilisateur de session est le nom du principal avant le realm, comme Trino associe les principaux par défaut.

### Ce que voit l’utilisateur de recherche

Une fois que l’[agent Trino](/fr/external/trino-agent/) applique les politiques, les listes omettent ce que l’utilisateur de recherche ne peut pas utiliser : donnez-lui une politique qui autorise `select` sur le catalogue `*`, ou sur les catalogues dont l’éditeur de politiques doit suggérer les ressources.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `catalog` | Jokers, sans casse | `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `all` |
| `schema` | Jokers, sans casse ; sous un catalogue | Comme `catalog` |
| `table` | Jokers, sans casse ; sous un schéma ; tables, vues et vues matérialisées | Comme `catalog` |
| `column` | Jokers, sans casse ; sous une table | `select`, `update` |
| `function` | Jokers, sans casse ; sous un schéma | `execute`, `create_function`, `drop_function` |
| `procedure` | Jokers, sans casse ; sous un schéma | `execute` |
| `session_property` | Jokers, sans casse ; sous un catalogue, nommée sans lui | `set` |
| `system_session_property` | Jokers, sans casse | `set` |
| `user` | Jokers, avec casse | `impersonate`, `view_query`, `kill_query` |
| `system_information` | Sa seule valeur, `*` | `read`, `write` |

`all` implique `select`, `insert`, `update`, `delete`, `create`, `drop` et `alter`.

Une permission sur un catalogue, un schéma ou une table couvre tout ce qu’il contient : `select` sur le schéma `sales` lit chacune de ses tables, `create` sur un catalogue y crée des schémas et des tables. Une vérification est décidée à chaque portée qu’elle nomme, et un refus à l’une d’elles la rejette ; une politique peut donc autoriser un schéma et refuser une colonne d’une de ses tables. Les fonctions, procédures et propriétés de session s’accordent en les nommant, avec `*` pour toutes celles d’un schéma ou d’un catalogue, et les fonctions ont leurs propres types d’accès : une autorisation sur des routines ne vaut jamais pour des tables.

Dans les valeurs de politique, `*` correspond à un nombre quelconque de caractères et `?` à un seul. L’éditeur de politiques suggère les catalogues, les schémas des catalogues qu’une politique nomme, les tables de ses schémas, les colonnes de ses tables et les propriétés de session de ses catalogues et du système ; les fonctions, procédures et utilisateurs ne sont pas suggérés.

## Filtres de lignes et masques

Les politiques de filtrage de lignes sur une table, et de masquage sur une colonne, sont du SQL Trino que le coordinator applique à chaque requête qui les lit. Un filtre de lignes est une condition sur les colonnes de la table, comme `region = 'EU'` ; les noms qu’il contient se résolvent dans le catalogue et le schéma de la table.

| Type de masque | Affiche |
| --- | --- |
| `mask` | Les lettres en `X` ou `x`, les chiffres en `n` |
| `mask_show_last_4`, `mask_show_first_4` | Seulement les quatre derniers ou premiers caractères |
| `mask_hash` | Le SHA-256 de la valeur, en hexadécimal |
| `mask_null` | `NULL` |
| `mask_none` | La valeur, non masquée |
| `mask_date_show_year` | Seulement l’année d’une date ou d’un horodatage |
| `custom` | Une expression SQL Trino de la politique, avec `{col}` pour la colonne |

L’agent adapte chaque masque au type de la colonne, pour qu’un masque ne casse jamais la requête qui lit la colonne : voir l’[agent Trino](/fr/external/trino-agent/).

## Exemple de politiques

Supposons que le catalogue `hive` ait un schéma `sales` avec les tables `orders`, dont les colonnes incluent `region`, `customer` et `card`, et `returns`. Créez ces politiques activées dans le même service de données :

| Politique | Ressources | Règle |
| --- | --- | --- |
| Accès | `catalog=hive`, `schema=sales` | Autoriser `select` pour le groupe `analysts` |
| Accès | `catalog=hive`, `schema=sales`, `table=orders`, `column=card` | Refuser `select` pour le groupe `analysts` |
| Accès | `catalog=hive`, `schema=sales`, `table=returns` | Autoriser `insert` pour l’utilisateur `alice` |
| Filtre de lignes | `catalog=hive`, `schema=sales`, `table=orders` | `region = 'EU'` pour l’utilisateur `alice` |
| Masquage | `catalog=hive`, `schema=sales`, `table=orders`, `column=customer` | `mask_hash` pour le groupe `analysts` |
| Accès | `catalog=system`, `schema=runtime` | Autoriser `select` pour l’utilisateur `alice` |
| Accès | `user=bob` | Autoriser `view_query` pour l’utilisateur `alice` |

Une fois que l’agent applique le nouvel instantané, les membres de `analysts` lisent toutes les tables de `sales` sauf `orders.card`, et voient `customer` haché ; `alice` ne voit que les commandes européennes, écrit dans `returns`, et voit les requêtes de Bob dans `system.runtime.queries` à côté des siennes.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Les appliquer exige l’agent Trino dans le coordinator. Voir l’[agent Trino](/fr/external/trino-agent/) pour son déploiement et ce qu’il vérifie.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent Trino](/fr/external/trino-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
