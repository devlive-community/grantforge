---
title: Apache Kafka
description: Installer et configurer le plug-in de service Kafka, se connecter avec PLAIN, SCRAM ou Kerberos en clair ou en TLS, rechercher topics, groupes, identifiants transactionnels et utilisateurs, et gérer des politiques d’accès dans les termes de Kafka.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Kafka recherche les topics, groupes de consommateurs, identifiants transactionnels, utilisateurs SCRAM et jetons de délégation, et gère des politiques d’accès dans les termes de Kafka : ses types de ressources sont les niveaux de ressource et ses opérations ACL les types d’accès. Son identifiant de plug-in et son type de service sont tous deux `kafka`. Le plug-in de service s’exécute dans le serveur GrantForge ; l’[agent Kafka](/fr/external/kafka-agent/), installé dans les brokers et les contrôleurs, applique les politiques.

Les tests d’intégration Testcontainers du dépôt couvrent Kafka `3.7.2`, `3.9.2`, `4.1.2` et `4.3.1` avec des connexions SASL PLAIN et SCRAM et l’autoriseur propre à Kafka, ainsi que Kerberos (keytab et mot de passe) et TLS sur `4.3.1` ; cela ne certifie pas toutes les autres versions.

## Installation

La distribution inclut le plug-in dans `plugins/kafka`. Vérifiez que `kafka` est activé dans **Administration de la plateforme → Plug-ins** ; relancez l’analyse du répertoire des plug-ins après une mise à jour. Le plug-in dialogue avec les brokers via le client Admin de Kafka (`kafka-clients` 4.3.1), dans son propre chargeur de classes.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez Apache Kafka (`kafka`).
2. Saisissez les serveurs d’amorçage et la manière dont le listener des brokers est sécurisé.
3. Saisissez l’utilisateur de recherche, testez la connexion et enregistrez le service. Le test se connecte et décrit le cluster : son identifiant et son nombre de brokers.

Les paramètres portent les noms des clients Kafka et peuvent être repris d’une configuration cliente. L’utilisateur de recherche permet à GrantForge de découvrir les ressources ; il est distinct des clients qui produisent et consomment. Un test de connexion réussi ne signifie pas que les requêtes sont déjà soumises aux politiques.

| Paramètre | Rôle |
| --- | --- |
| `bootstrap.servers` | Brokers de départ obligatoires, `hôte:port` séparés par des virgules, par exemple `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT` (par défaut), `SSL`, `SASL_PLAINTEXT` ou `SASL_SSL` |
| `sasl.mechanism` | Protocoles SASL uniquement : `PLAIN`, `SCRAM-SHA-256` ou `SCRAM-SHA-512` avec un mot de passe, `GSSAPI` pour Kerberos |
| `username` | SASL : l’utilisateur de recherche ; avec Kerberos, le principal complet, par exemple `grantforge@EXAMPLE.COM` |
| `password` | Le mot de passe de l’utilisateur, ou le mot de passe Kerberos du principal sans keytab ; stocké chiffré |
| `keytab` | Kerberos : chemin de la keytab du principal, lisible sur le **serveur GrantForge** |
| `kerberos.kdc` | Kerberos : KDC du royaume du principal, `hôte[:port]` séparés par des virgules ; vide utilise le `krb5.conf` du serveur |
| `sasl.kerberos.service.name` | Kerberos : la première partie des principaux des brokers, `kafka` par défaut |
| `ssl.truststore.location`, `ssl.truststore.password`, `ssl.truststore.type` | TLS : le magasin de confiance qui vérifie les brokers (`JKS`, `PKCS12` ou `PEM`) ; vide fait confiance à ce que Java approuve |
| `ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password` | TLS : le certificat de GrantForge, pour les brokers qui en demandent un aux clients |
| `ssl.verify.hostname` | TLS : si les certificats des brokers doivent nommer les hôtes contactés, `true` par défaut |
| `request.timeout.ms` | Délai des requêtes, `30000` par défaut, plage `1..600000` |

Les paramètres SASL sont refusés avec `PLAINTEXT` et `SSL`, et les paramètres TLS avec `PLAINTEXT` et `SASL_PLAINTEXT`. `PLAIN` et SCRAM exigent un mot de passe ; `GSSAPI` exige un mot de passe ou une keytab.

### Kerberos

Avec `GSSAPI`, GrantForge connecte le principal par la connexion Kerberos de Kafka elle-même, avec la keytab ou avec le mot de passe que GrantForge lui transmet ; rien d’autre sur le serveur GrantForge n’a besoin de réglages Kerberos. Les royaumes dont les KDC sont saisis dans `kerberos.kdc` sont déclarés pour tout le serveur GrantForge, comme le font les plug-ins HDFS et Hive, si bien que plusieurs services peuvent utiliser des royaumes différents côte à côte.

### Ce que voit l’utilisateur de recherche

Kafka ne montre à un utilisateur que ce qu’il peut décrire : l’utilisateur de recherche a donc besoin de `describe` sur les topics, groupes et identifiants transactionnels à proposer, et de `describe` sur le cluster pour les utilisateurs SCRAM. Kafka omet les topics et groupes qu’il ne peut pas décrire ; les suggestions sont alors vides plutôt que refusées. Les topics internes, comme `__consumer_offsets`, sont proposés dès que le texte saisi commence par `_`. Un cluster sans jetons de délégation, ou sans SCRAM, n’en propose aucun.

Dès que l’[agent Kafka](/fr/external/kafka-agent/) applique les politiques, elles décident aussi de ce que l’utilisateur de recherche peut décrire : donnez-lui une politique qui autorise `describe`, ou nommez-le dans `super.users` des brokers.

## Ressources et types d’accès

| Niveau | Ressource Kafka | Types d’accès |
| --- | --- | --- |
| `topic` | Topics | `read`, `write`, `create`, `delete`, `alter`, `describe`, `describe_configs`, `alter_configs`, `all` |
| `group` | Groupes de consommateurs | `read`, `delete`, `describe`, `all` |
| `transactional_id` | Identifiants transactionnels | `write`, `describe`, `all` |
| `cluster` | Le cluster, toujours nommé `kafka-cluster` | `create`, `alter`, `describe`, `cluster_action`, `describe_configs`, `alter_configs`, `idempotent_write`, `all` |
| `delegation_token` | Jetons de délégation, par identifiant de jeton | `describe`, `all` |
| `user` | Utilisateurs dont les jetons de délégation sont créés ou décrits | `create_tokens`, `describe_tokens`, `all` |

Chaque niveau est indépendant ; une politique en nomme un. Les valeurs sont sensibles à la casse : `*` correspond à un nombre quelconque de caractères et `?` à un seul. Les types d’accès en impliquent d’autres comme les opérations ACL de Kafka : `read`, `write`, `delete` et `alter` impliquent `describe`, `alter_configs` implique `describe_configs`, et `all` implique tous les autres.

Les implications valent aussi pour les refus : une politique qui refuse `write` sur un topic refuse aussi le `describe` que `write` implique, si bien que l’utilisateur ne voit plus le topic. Les ACL propres à Kafka n’appliquent les implications qu’aux opérations autorisées.

L’éditeur de politiques propose les noms du cluster par préfixe ; pour `cluster`, il propose `kafka-cluster`.

## Exemple de politiques

Supposons que les producteurs de l’utilisateur `alice` écrivent dans les topics dont le nom commence par `orders`, et que le groupe `analysts` les lise avec le groupe de consommateurs `billing`. Créez trois politiques d’accès activées dans le même service de données :

| Ressources | Règle |
| --- | --- |
| `topic=orders*` | Autoriser `write` pour l’utilisateur `alice` |
| `topic=orders*` | Autoriser `read` pour le groupe `analysts` |
| `group=billing` | Autoriser `read` pour le groupe `analysts` |

Dès que l’agent applique le nouvel instantané, `alice` peut produire dans `orders-eu`, aussi avec un producteur idempotent et sans `idempotent_write` sur le cluster ; les membres d’`analysts` le consomment en tant que `billing` ; les autres topics et groupes les refusent tous deux. `kafka-acls --list` affiche les trois politiques comme des ACL préfixées et littérales.

## Appliquer les politiques

Le plug-in de service gère les politiques et recherche les ressources. Pour les appliquer, chaque broker et chaque contrôleur a besoin de l’agent Kafka. Voir l’[agent Kafka](/fr/external/kafka-agent/) pour son déploiement et ce qu’il vérifie.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent Kafka](/fr/external/kafka-agent/)
- [Plug-ins et types de service](/fr/develop/plugins/)
