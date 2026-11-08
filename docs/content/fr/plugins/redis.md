---
title: Redis
description: Installer et configurer le plug-in de service Redis pour Redis et Valkey, se connecter à un serveur, ses réplicas ou un cluster, s’authentifier avec un utilisateur ACL, en TLS si besoin, rechercher clés, canaux et nœuds, et gérer qui peut lire et écrire quelles clés, utiliser quels canaux et superviser quels nœuds.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Redis recherche les clés, canaux et nœuds des déploiements Redis et Valkey, et gère les politiques d’accès dans les termes de Redis : lire et écrire des clés par motif, s’abonner et publier sur des canaux, et superviser chaque nœud. L’ID du plug-in et le type de service sont tous deux `redis`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent Redis](/fr/external/redis-agent/), qui s’exécute à côté du déploiement, applique les politiques avec l’ACL propre à Redis sur chaque nœud.

Les tests d’intégration Testcontainers du dépôt couvrent un primaire avec un réplica de Redis `7.2`, `7.4`, `8.2` et `8.10` et de Valkey `8.1` et `9.1`, ainsi qu’un cluster Redis `8.10`. Cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/redis`. Vérifiez que `redis` est activé dans **Administration de la plateforme → Plug-ins** ; relancez l’analyse après avoir mis à jour le répertoire des plug-ins. Le plug-in communique avec Redis via Jedis, dans son propre chargeur de classes.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Redis (`redis`).
2. Saisissez un ou plusieurs nœuds. Le reste d’un cluster et les réplicas d’un primaire sont trouvés à partir d’eux.
3. Indiquez l’utilisateur de recherche, testez la connexion et enregistrez le service. Le test s’authentifie et décrit le déploiement : Redis ou Valkey et sa version, s’il s’agit d’un serveur seul, d’un primaire avec réplicas ou d’un cluster, et qui s’est authentifié.

L’utilisateur de recherche permet à GrantForge de trouver les ressources et n’est pas l’utilisateur qui lit ou écrit. Un test de connexion réussi ne signifie pas que l’accès suit déjà les politiques.

| Paramètre | Rôle |
| --- | --- |
| `redis.nodes` | Obligatoire : les nœuds en `host:port`, par exemple `redis1.example.com:6379`, séparés par des virgules et essayés dans l’ordre ; le port est `6379` s’il n’est pas indiqué |
| `username` | L’utilisateur de recherche, un utilisateur ACL ; vide s’authentifie comme utilisateur par défaut |
| `password` | Son mot de passe, stocké chiffré |
| `tls.mode` | `disable` (par défaut), `verify-ca` pour vérifier les certificats des serveurs, ou `verify-full` pour vérifier aussi leurs noms |
| `tls.ca.file` | TLS : chemin sur le **serveur GrantForge** des certificats CA PEM qui vérifient les serveurs. Vide utilise ceux de la JVM |
| `tls.certificate.file` | TLS, pour les serveurs qui demandent des certificats clients : chemin sur le **serveur GrantForge** du certificat client PEM, qui peut aussi contenir sa clé |
| `tls.key.file` | Chemin sur le **serveur GrantForge** de la clé PEM du certificat, lorsqu’elle a son propre fichier |
| `tls.key.password` | Le mot de passe d’une clé chiffrée, stocké chiffré |
| `timeout` | Durée permise pour se connecter et exécuter une commande, `10000` par défaut, dans la plage `1..600000` millisecondes |

Une authentification refusée, un utilisateur de recherche à qui il manque une permission et des nœuds injoignables sont signalés comme tels.

### Ce que voit l’utilisateur de recherche

Les clés sont trouvées avec `SCAN` sur chaque primaire, en quelques passes au plus pour qu’une recherche reste rapide quel que soit le nombre de clés ; les canaux sont ceux qui ont des abonnés en ce moment. `SCAN` liste toutes les clés quels que soient les motifs de clés de l’utilisateur, donc l’utilisateur de recherche n’a pas besoin du droit de lire les valeurs :

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## Ressources et types d’accès

| Niveau | Correspondance | Types d’accès |
| --- | --- | --- |
| `key` | Caractères génériques, sensible à la casse. Une clé ou un motif de clés | `read`, `write` |
| `channel` | Caractères génériques, sensible à la casse. Un canal ou un motif de canaux | `subscribe`, `publish` |
| `node` | Caractères génériques, insensible à la casse. L’adresse d’un nœud en `host:port` | `monitor` |

| Type d’accès | Ce que Redis autorise |
| --- | --- |
| `read` | Les commandes de `@read` sur la clé, `%R~` sur elle |
| `write` | Les commandes de `@write` sur la clé, `%W~` sur elle, qui la suppriment aussi |
| `subscribe` | `SUBSCRIBE`, `PSUBSCRIBE`, `SSUBSCRIBE` sur le canal, `&` |
| `publish` | `PUBLISH`, `SPUBLISH` sur le canal, `&` |
| `monitor` | `INFO`, `DBSIZE`, `LATENCY LATEST`, `LATENCY HISTORY`, `MEMORY STATS` |

Dans les valeurs des politiques, `*` correspond à n’importe quel texte et `?` à un caractère, comme dans les motifs de Redis. Redis accorde les clés et les canaux par motif et ne peut pas les lister à l’agent ; la valeur d’une politique est donc accordée comme motif à part entière, là où les politiques décident l’utilisateur de la même façon sur tout le motif. Un motif à l’intérieur duquel les politiques décident autrement, comme `sales:*` quand une politique refuse `sales:card:*`, est retenu et signalé ; nommez plutôt des clés ou des motifs que les politiques décident de la même façon. Les valeurs avec crochets, barres obliques inverses, parenthèses ou espaces ne sont jamais accordées.

Les commandes que Redis juge dangereuses, comme `FLUSHALL` et `KEYS`, ne sont jamais données, et `SCAN` et `RANDOMKEY`, qui listent les clés quels que soient leurs motifs, seulement à un utilisateur qui peut lire toutes les clés. Les motifs nomment des clés dans chaque base d’un nœud, l’ACL de Redis n’ayant pas de bases.

Redis ne filtre rien à l’intérieur d’une valeur et ne masque rien ; ce type de service n’offre donc ni l’un ni l’autre.

## Exemple de politiques

Créez ces politiques activées dans un service de données :

| Ressource | Règle |
| --- | --- |
| `key=sales:*` | Autoriser `read` au groupe `analysts` |
| `key=sales:card:*` | Autoriser `read` et `write` à l’utilisateur `billing` |
| `key=cart:*` | Autoriser `read` et `write` à l’utilisateur `shop` |
| `channel=orders.*` | Autoriser `subscribe` au groupe `analysts` ; autoriser `publish` à l’utilisateur `shop` |
| `node=*` | Autoriser `monitor` à l’utilisateur `dashboards` |

Une fois l’instantané appliqué par l’agent, les membres de `analysts` lisent toutes les clés `sales:` et suivent les canaux de commandes ; `billing` lit et écrit les clés de cartes, un motif à l’intérieur duquel aucune autre politique ne décide autrement ; `shop` tient les paniers et publie les événements de commande ; et `dashboards` voit l’état de chaque nœud et aucune donnée.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Pour les appliquer, il faut un agent Redis par déploiement ; voir [Agent Redis](/fr/external/redis-agent/) pour le déploiement et les vérifications.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent Redis](/fr/external/redis-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
