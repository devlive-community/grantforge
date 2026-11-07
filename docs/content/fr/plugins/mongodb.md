---
title: MongoDB
description: Installer et configurer le plug-in de service MongoDB, se connecter à un replica set ou un cluster partitionné, s’authentifier par SCRAM, certificat client, LDAP ou Kerberos, rechercher bases de données et collections, et gérer qui peut lire, écrire et administrer quoi, collection par collection.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in MongoDB recherche les bases de données et les collections, et gère les politiques d’accès dans les termes de MongoDB : lecture, flux de modifications, insertion, mise à jour et suppression, index, options de collection, contournement de la validation des documents et suppression de la collection, par collection ; création de collections et suppression de la base, par base de données ; liste de toutes les bases et supervision, pour le déploiement dans son ensemble. L’ID du plug-in et le type de service sont tous deux `mongodb`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent MongoDB](/fr/external/mongodb-agent/), qui s’exécute à côté du déploiement, applique les politiques avec les rôles propres à MongoDB.

Les tests d’intégration Testcontainers du dépôt couvrent des replica sets MongoDB `6.0`, `7.0`, `8.0` et `9.0`, ainsi que Kerberos sur Percona Server for MongoDB `8.0`. Cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/mongodb`. Vérifiez que `mongodb` est activé dans **Administration de la plateforme → Plug-ins** ; relancez l’analyse après avoir mis à jour le répertoire des plug-ins. Le plug-in communique avec MongoDB via le pilote Java officiel de MongoDB, dans son propre chargeur de classes.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez MongoDB (`mongodb`).
2. Saisissez la chaîne de connexion du déploiement, telle que tout client MongoDB la prend.
3. Indiquez comment l’utilisateur de recherche s’authentifie, testez la connexion et enregistrez le service. Le test s’authentifie et décrit le déploiement : la version de MongoDB, s’il s’agit d’un replica set, d’un cluster partitionné ou d’un serveur seul, qui s’est authentifié et combien de bases cet utilisateur voit.

L’utilisateur de recherche permet à GrantForge de trouver les ressources et n’est pas l’utilisateur qui lit ou écrit. Un test de connexion réussi ne signifie pas que l’accès suit déjà les politiques.

| Paramètre | Rôle |
| --- | --- |
| `mongodb.uri` | Obligatoire : la chaîne de connexion, par exemple `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` ou `mongodb+srv://cluster.example.com/`. Les options comme `tls=true` vont ici ; les identifiants non, et une chaîne qui en contient est refusée |
| `auth.mechanism` | `SCRAM-SHA-256` (par défaut) ou `SCRAM-SHA-1` pour les utilisateurs avec mot de passe, `MONGODB-X509` pour un certificat client, `PLAIN` pour LDAP, `GSSAPI` pour Kerberos |
| `username` | L’utilisateur de recherche. Avec `GSSAPI`, le principal ; avec `MONGODB-X509`, vide le prend dans le certificat |
| `password` | Le mot de passe de l’utilisateur de recherche, stocké chiffré |
| `auth.source` | SCRAM : la base où l’utilisateur est défini, `admin` par défaut. Les autres mécanismes utilisent `$external` |
| `tls.ca.file` | Avec `tls=true` : chemin sur le **serveur GrantForge** des certificats CA PEM qui vérifient les serveurs. Vide utilise ceux de la JVM |
| `tls.certificate.key.file` | Avec `tls=true` : chemin sur le **serveur GrantForge** d’un fichier PEM contenant le certificat client et sa clé, tel que `mongosh` le prend |
| `tls.certificate.key.password` | Le mot de passe d’une clé chiffrée, stocké chiffré |
| `kerberos.keytab` | `GSSAPI` : chemin sur le **serveur GrantForge** du keytab du principal. Vide utilise le mot de passe |
| `kerberos.kdc` | `GSSAPI` : les KDC du royaume, séparés par des virgules. Vide utilise le `krb5.conf` du serveur |
| `kerberos.service.name` | `GSSAPI` : le nom de service du principal des serveurs, `mongodb` par défaut |
| `timeout` | Durée permise pour trouver un serveur, se connecter et exécuter une commande, `30000` par défaut, dans la plage `1..600000` millisecondes |

Une authentification refusée, un utilisateur de recherche à qui il manque un privilège et un déploiement injoignable sont signalés comme tels.

### Ce que voit l’utilisateur de recherche

MongoDB liste les bases et les collections sur lesquelles un utilisateur a un privilège quelconque ; une recherche montre donc ce que l’utilisateur de recherche peut voir. Donnez-lui `read` sur ce pour quoi des politiques sont écrites, ou `readAnyDatabase` pour tout. Les bases propres à MongoDB, `admin`, `config` et `local`, et les collections système sont omises : les politiques n’en décident pas.

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `deployment` | Caractères génériques, sensible à la casse. Le nom du replica set, `sharded` pour un cluster partitionné, `standalone` pour un serveur seul | `list_databases`, `monitor` |
| `database` | Caractères génériques, sensible à la casse | `create`, `drop_database` |
| `collection` | Caractères génériques, sensible à la casse. Une collection ou une vue d’une base | `read`, `change_stream`, `insert`, `update`, `remove`, `create_index`, `drop_index`, `alter`, `bypass_validation`, `drop` |

| Type d’accès | Actions de MongoDB |
| --- | --- |
| `read` | `find`, `listIndexes`, `collStats`, `killCursors` ; sur une base entière aussi `listCollections` et `dbStats` |
| `change_stream` | `changeStream` ; pour en ouvrir un, MongoDB exige aussi `find` |
| `insert`, `update`, `remove` | `insert`, `update`, `remove` |
| `create_index`, `drop_index` | `createIndex`, `dropIndex` |
| `alter` | `collMod`, `validate`, `convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | `createCollection` sur la base : créer des collections et des vues |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases` : le nom de chaque base, pas seulement de celles sur lesquelles l’utilisateur a un privilège |
| `monitor` | `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `inprog` |

Dans les valeurs des politiques, `*` correspond à n’importe quel texte et `?` à un caractère. L’éditeur de politiques suggère le déploiement, les bases et les collections des bases qu’une politique nomme.

Le privilège de MongoDB de supprimer une base est le privilège de supprimer chacune de ses collections ; l’agent n’accorde donc `drop_database` que là où les politiques permettent aussi de supprimer chaque collection. Sinon il retient le privilège et le signale, plutôt que de donner plus que ce que les politiques permettent. L’administration des utilisateurs et des rôles n’est pas un type d’accès, et aucune politique ne la donne.

### Lignes et champs

MongoDB ne filtre aucune ligne et ne masque aucun champ par rôle ; ce type de service n’offre donc ni l’un ni l’autre. Une vue donne une lecture restreinte : MongoDB ne vérifie le lecteur d’une vue que par rapport à la vue, de sorte qu’une politique qui permet `read` sur une vue, et rien sur sa source, montre à l’utilisateur ce que la vue montre et rien de plus.

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## Exemple de politiques

Supposons que le déploiement ait les bases `sales` et `hr`. Créez ces politiques activées dans un service de données :

| Ressource | Règle |
| --- | --- |
| `database=sales`, `collection=*` | Autoriser `read` au groupe `analysts` |
| `database=sales`, `collection=orders_public` | Autoriser `read` à l’utilisateur `support` |
| `database=sales`, `collection=orders` | Autoriser `insert` et `update` à l’utilisateur `ingest` ; refuser `read` au groupe `contractors` |
| `database=team_*` | Autoriser `create` au groupe `data-team` |
| `database=team_*`, `collection=*` | Autoriser `read`, `insert`, `update`, `remove`, `create_index` et `drop` au groupe `data-team` |
| `deployment=*` | Autoriser `monitor` à l’utilisateur `dashboards` |

Une fois l’instantané appliqué par l’agent, les membres de `analysts` lisent tout `sales`, y compris les collections créées plus tard ; `support` lit la vue sans sa source ; `ingest` écrit dans `orders` sans le lire ; les membres de `data-team` travaillent librement dans les bases `team_` ; et `dashboards` voit l’état du serveur et aucune donnée.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Pour les appliquer, il faut un agent MongoDB par déploiement ; voir [Agent MongoDB](/fr/external/mongodb-agent/) pour le déploiement et les vérifications.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent MongoDB](/fr/external/mongodb-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
