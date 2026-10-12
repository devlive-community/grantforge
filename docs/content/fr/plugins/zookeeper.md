---
title: Apache ZooKeeper
description: Installer et configurer le plug-in ZooKeeper, parcourir les znodes et gérer les politiques d’accès aux chemins de znodes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Apache ZooKeeper fournit à GrantForge la connexion aux ensembles, la recherche de chemins de znodes et la gestion des politiques. Son identifiant de plug-in et son type de service sont tous deux `zookeeper`.

## Installation

La distribution inclut le plug-in dans `plugins/zookeeper`. Vérifiez dans **Administration de la plateforme → Plug-ins** que `zookeeper` est activé ; après une mise à jour, relancez l’analyse du répertoire des plug-ins. Le plug-in utilise le client ZooKeeper 3.9 dans son propre chargeur de classes, séparé des bibliothèques du serveur.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez ZooKeeper (`zookeeper`).
2. Saisissez la chaîne de connexion : les serveurs de l’ensemble avec un chroot facultatif, par exemple `zk1:2181,zk2:2181,zk3:2181/kafka`. Le client bascule seul d’un serveur à l’autre.
3. Choisissez l’authentification, définissez le znode de recherche et testez la connexion (elle vérifie que le znode existe et que ses enfants peuvent être listés), puis enregistrez le service.

| Paramètre | Rôle |
| --- | --- |
| `zookeeper.connect` | Chaîne de connexion obligatoire : `host[:port]` séparés par des virgules, suivis éventuellement de `/chroot` ; le chroot ne peut pas se terminer par `/` |
| `authentication` | `none` (par défaut), `digest` ou `kerberos` |
| `username` | Digest : l’utilisateur digest des ACL (sans deux-points) ; Kerberos : le principal, par exemple `grantforge@EXAMPLE.COM` |
| `password` | Mot de passe de l’utilisateur digest ; stocké chiffré |
| `keytab` | Kerberos : chemin du keytab du principal sur le serveur GrantForge |
| `zookeeper.sasl.client.username` | Kerberos : première partie des principals des serveurs, `zookeeper` par défaut, comme dans `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos : principal complet des serveurs lorsqu’il n’a pas cette forme, par exemple `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | Se connecter au port client TLS des serveurs, `false` par défaut |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | Magasin de confiance qui vérifie les serveurs ; vide, il fait confiance à ce que le Java du serveur approuve ; type `jks`, `pkcs12` ou `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | Magasin de clés avec le certificat client, pour les serveurs qui en exigent un |
| `session.timeout.ms` | Délai de session, `15000` par défaut, plage `2000..120000` |
| `connection.timeout.ms` | Attente de la connexion initiale et de l’authentification uniquement, `10000` ms par défaut, plage `1000..60000` ; ne limite pas les requêtes synchrones suivantes |
| `request.timeout.ms` | Attente maximale pour une requête synchrone, `8000` ms par défaut, plage `1000..60000` |
| `lookup.path` | Znode de départ pour la recherche et la navigation, sous le chroot, `/` par défaut ; par exemple `/brokers`, la navigation restant limitée à ce znode |
| `lookup.max.entries` | Nombre maximal d’enfants lus d’un znode à la fois, `10000` par défaut, plage `1..100000` |

## Authentification

- **none** : aucune connexion ; seuls les znodes dont l’ACL est ouverte à `world:anyone` sont lisibles.
- **digest** : connexion en tant que `username:password`, qui correspond aux identités `digest` des ACL de ZooKeeper. Un mauvais mot de passe n’est pas refusé à la connexion : il forme une autre identité, et les znodes protégés apparaissent comme accès refusé.
- **kerberos** : connexion SASL avec un keytab ; le `krb5.conf` du serveur doit mener au KDC. Chaque principal et son keytab reçoivent une section JAAS propre à côté de la configuration du processus, si bien qu’un même GrantForge atteint plusieurs ensembles sous des principals différents. La connexion Kerberos par mot de passe seul n’est pas encore prise en charge.

La configuration est vérifiée à l’enregistrement : la forme de la chaîne de connexion, un utilisateur et un mot de passe pour digest, un principal et un keytab pour Kerberos, un keytab et un principal de serveur uniquement avec Kerberos, et les plages des délais et de la limite.

Lorsque TLS est désactivé, les chemins et mots de passe des magasins de confiance et de clés doivent rester vides. Un mot de passe de magasin exige le chemin du magasin correspondant. Les seuls types acceptés pour les deux magasins sont `jks`, `pkcs12` et `pem`.

Les espaces au début et à la fin des mots de passe des magasins TLS sont conservés. La configuration du service détermine le mode TLS et les magasins utilisés pour cette connexion et remplace les paramètres TLS et de magasins ZooKeeper déjà présents dans la JVM ; les certificats et noms d’hôte des serveurs restent vérifiés.

## Parcourir les znodes

Choisissez le service dans **Permissions sur les données → Politiques** et utilisez **Parcourir** à côté de `path` :

- Les enfants sont listés page par page, par nom ; un znode qui a lui-même des enfants peut être ouvert.
- Chaque znode affiche son ACL (`scheme:id:cdrwa`, comme l’écrivent les outils de ZooKeeper), la taille de ses données et sa dernière modification.
- Un znode dont l’utilisateur de recherche ne peut pas lire l’ACL reste listé par son nom ; l’ouvrir indique pourquoi l’accès est refusé.

Si des znodes sont supprimés pendant la pagination, le curseur avance toujours à partir du dernier nom d’enfant examiné, même si ce nœud n’existe plus. La liste des enfants n’est pas un instantané : les ajouts et suppressions simultanés suivent la sémantique de ZooKeeper et peuvent modifier les pages suivantes.

La saisie d’un chemin propose des suggestions ; un parent inexistant n’en propose aucune, et les chemins hors de `lookup.path` sont refusés. Un znode ayant plus d’enfants que `lookup.max.entries` provoque une erreur plutôt qu’un affichage partiel.

## Appliquer les politiques

Il existe un seul niveau de ressource, `path`, et les types d’accès suivent les permissions d’ACL de ZooKeeper : `read`, `write`, `create`, `delete` et `admin` ; les politiques de chemin prennent en charge la récursivité et les exclusions.

Le plug-in serveur gère, recherche et parcourt. L’agent qui applique ces politiques dans les serveurs ZooKeeper n’est pas encore disponible ; d’ici là, les ACL de ZooKeeper décident de l’accès.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Développement de plug-ins et types de service](/fr/develop/plugins/)
