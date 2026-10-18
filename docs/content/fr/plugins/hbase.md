---
title: Apache HBase
description: Installer et configurer le plug-in de service HBase, trouver le cluster par ZooKeeper ou par des serveurs d’amorçage, se connecter en simple ou avec Kerberos, rechercher espaces de noms, tables et familles de colonnes, et gérer des politiques d’accès aux portées propres à HBase.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in HBase recherche les espaces de noms, tables et familles de colonnes, et gère des politiques d’accès aux portées des autorisations propres à HBase : la portée globale, les espaces de noms, les tables, les familles de colonnes et les colonnes. Son identifiant de plug-in et son type de service sont tous deux `hbase`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent HBase](/fr/external/hbase-agent/), installé dans les masters et les region servers, applique les politiques.

Les tests d’intégration Testcontainers du dépôt couvrent HBase `2.5.16`, `2.6.7` et `3.0.0`, construits à partir des versions Apache vérifiées par SHA-512, ainsi qu’un cluster Kerberos en `2.6.7` ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/hbase`. Vérifiez que `hbase` est activé dans **Administration de la plateforme → Plug-ins** ; relancez l’analyse du répertoire des plug-ins après une mise à jour. Le plug-in dialogue avec le cluster via le client propre à HBase (`hbase-shaded-client-byo-hadoop` 2.6.7) sur le client shaded de Hadoop 3.5.0, dans son propre chargeur de classes, si bien qu’il fonctionne avec HBase 2.x comme 3.x.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Apache HBase (`hbase`).
2. Saisissez le quorum ZooKeeper, ou les serveurs d’amorçage d’un cluster HBase 2.5 ou ultérieur.
3. Choisissez comment l’utilisateur de recherche se connecte, saisissez-le, testez la connexion et enregistrez le service. Le test se connecte et décrit le cluster : son identifiant, sa version et le nombre de region servers actifs.

Les paramètres portent les noms des clients HBase et peuvent être repris d’un `hbase-site.xml`. L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des clients qui lisent et écrivent. Un test de connexion réussi ne signifie pas que les requêtes sont déjà soumises aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `hbase.zookeeper.quorum` | Hôtes ZooKeeper, `hôte` ou `hôte:port` séparés par des virgules, par exemple `zk1,zk2,zk3:2181` ; obligatoire sans serveurs d’amorçage |
| `hbase.zookeeper.property.clientPort` | Le port des hôtes du quorum qui n’en indiquent pas, `2181` par défaut |
| `zookeeper.znode.parent` | La racine du cluster dans ZooKeeper, `/hbase` par défaut |
| `hbase.client.bootstrap.servers` | Au lieu de ZooKeeper, à partir de HBase 2.5 : masters ou region servers, `hôte:port` séparés par des virgules |
| `hbase.security.authentication` | `simple` (par défaut) : un nom d’utilisateur ; `kerberos` : un principal avec une keytab ou un mot de passe |
| `username` | Utilisateur de recherche obligatoire ; avec Kerberos, le principal complet, par exemple `grantforge@EXAMPLE.COM` |
| `password` | Kerberos : le mot de passe du principal, sans keytab ; stocké chiffré |
| `keytab` | Kerberos : chemin de la keytab du principal, lisible sur le **serveur GrantForge** |
| `kerberos.kdc` | Kerberos : KDC du royaume du principal, `hôte[:port]` séparés par des virgules ; vide utilise le `krb5.conf` du serveur |
| `hbase.master.kerberos.principal` | Kerberos : le principal des masters, `hbase/_HOST@` et le royaume du principal de recherche par défaut |
| `hbase.regionserver.kerberos.principal` | Kerberos : le principal des region servers, celui des masters par défaut |
| `hbase.rpc.protection` | `authentication` (par défaut), `integrity` ou `privacy`, comme celui du cluster |
| `hbase.rpc.timeout` | Délai des RPC, `30000` par défaut, plage `1..600000` |

Un service est exactement ce que disent ses paramètres : aucun `hbase-site.xml` du serveur GrantForge n’est lu. Les paramètres Kerberos sont refusés en authentification simple ; Kerberos exige un mot de passe ou une keytab. Les recherches abandonnent après quelques tentatives plutôt qu’après les minutes de HBase.

### Kerberos

Avec `kerberos`, GrantForge connecte le principal par la connexion propre à Hadoop, avec la keytab ou le mot de passe, conserve la connexion et la renouvelle depuis la keytab ; rien d’autre sur le serveur GrantForge n’a besoin de réglages Kerberos. Les royaumes dont les KDC sont saisis dans `kerberos.kdc` sont déclarés pour tout le serveur GrantForge, comme le font les plug-ins HDFS et Hive, si bien que plusieurs services peuvent utiliser des royaumes différents côte à côte.

Un cluster ferme la connexion d’un client qui se connecte autrement qu’il ne l’attend, sans dire pourquoi. Le plug-in signale cela comme une connexion refusée et renvoie vers `hbase.security.authentication` et `hbase.rpc.protection`, qui doivent correspondre à ceux du cluster.

### Ce que voit l’utilisateur de recherche

Dès que l’[agent HBase](/fr/external/hbase-agent/) applique les politiques, les listes omettent les espaces de noms et tables que l’utilisateur de recherche ne peut pas utiliser : donnez-lui une politique qui autorise `read` sur l’espace de noms `*`, ou nommez-le dans `hbase.superuser` du cluster.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `global` | Sa seule valeur, `*` | `admin` : opérations sur tout le cluster, comme l’équilibreur, les quotas et la réplication |
| `namespace` | Caractères génériques, sensibles à la casse | `read`, `write`, `create`, `admin` |
| `table` | Caractères génériques, sensibles à la casse ; sous un espace de noms, nommée sans lui | `read`, `write`, `create`, `admin` |
| `column_family` | Caractères génériques, sensibles à la casse ; sous une table | `read`, `write` |
| `column` | Caractères génériques, sensibles à la casse ; sous une famille de colonnes ; nommée en texte UTF-8 | `read`, `write` |

Les types d’accès sont ceux de HBase : `read`, `write`, `create` et `admin`. Le `exec` de HBase, pour les points d’accès des coprocesseurs, n’est pas proposé, car l’agent ne peut pas vérifier ces appels sur toutes les versions de HBase.

Comme dans HBase, une autorisation couvre tout ce qui relève de sa portée : `read` sur l’espace de noms `sales` lit toutes ses tables, `write` sur une famille de colonnes écrit toutes ses colonnes. Une requête est décidée à chaque portée qu’elle nomme, et un refus à l’une d’elles la refuse ; une politique peut ainsi autoriser une table et refuser l’une de ses colonnes. La portée globale est le cluster lui-même, pas toutes les tables ; les politiques sur l’espace de noms `*` couvrent toutes les tables.

Dans les valeurs des politiques, `*` correspond à un nombre quelconque de caractères et `?` à un seul. L’éditeur de politiques propose les espaces de noms, les tables des espaces de noms qu’une politique nomme et les familles de colonnes de ses tables ; les colonnes contiennent des données et ne sont pas proposées.

## Exemple de politiques

Supposons que l’espace de noms `sales` ait une table `orders` avec les familles de colonnes `d`, qui contient `name` et `ssn`, et `meta`. Créez ces politiques d’accès activées dans le même service de données :

| Ressources | Règle |
| --- | --- |
| `namespace=sales` | Autoriser `read` pour l’utilisateur `alice` |
| `namespace=sales`, `table=orders`, `column_family=d` | Autoriser `read` pour l’utilisateur `bob` |
| `namespace=sales`, `table=orders` | Autoriser `read` pour le groupe `auditors` |
| `namespace=sales`, `table=orders`, `column_family=d`, `column=ssn` | Refuser `read` pour le groupe `auditors` |
| `namespace=analytics` | Autoriser `create` et `admin` pour l’utilisateur `dave` |

Dès que l’agent applique le nouvel instantané, `alice` lit toutes les tables de `sales` ; `bob` lit `orders` et n’obtient que les colonnes de `d`, et ne voit que `orders` quand il liste les tables ; les membres d’`auditors` lisent tout `orders` sauf `d:ssn` ; `dave` crée l’espace de noms `analytics` et ses tables, les administre, et se voit refuser des tables ailleurs.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Pour les appliquer, chaque master et chaque region server a besoin de l’agent HBase. Voir l’[agent HBase](/fr/external/hbase-agent/) pour son déploiement et ce qu’il vérifie.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent HBase](/fr/external/hbase-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
