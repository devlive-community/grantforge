---
title: "Vue d’ensemble"
description: "Chaque système dont GrantForge gère l’accès, la façon dont son agent applique les politiques, ce qu’il peut filtrer et masquer, et les versions vérifiées par les tests d’intégration."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge gère l’accès à 15 systèmes. Pour chacun, un plug-in de service dans le serveur GrantForge décrit les ressources et les types d’accès du système, recherche les ressources pour l’éditeur de politiques et teste la connexion ; un agent applique les politiques dans le système et signale chaque accès à l’audit des accès. Les plug-ins ci-dessous sont classés par type de système, comme dans le menu.

## Comment les agents appliquent

Un agent applique les politiques de l’une de trois façons :

### Dans le système

Le système interroge l’agent à chaque requête : un autorisateur, coprocesseur, contrôle d’accès ou plug-in chargé dans les processus du système lui-même. Chaque requête est décidée aussitôt à partir de l’instantané en mémoire, jusqu’à ce qui se trouve sous une ressource. L’agent est installé dans le système et chargé à son redémarrage.

Systèmes: [Apache Hadoop HDFS](/fr/plugins/hdfs/), [Apache Hadoop YARN](/fr/plugins/yarn/), [Apache ZooKeeper](/fr/plugins/zookeeper/), [Apache Hive](/fr/plugins/hive/), [Apache HBase](/fr/plugins/hbase/), [Trino](/fr/plugins/trino/), [Apache Kafka](/fr/plugins/kafka/), [Apache Pulsar](/fr/plugins/pulsar/), [Apache Solr](/fr/plugins/solr/)

### À côté du système

Un service à côté du système maintient le contrôle d’accès propre du système, ses rôles, autorisations et politiques de lignes, en accord avec les politiques. Rien n’est installé dans le système, et le système vérifie lui-même chaque requête, par tous les protocoles. Un changement s’applique à la synchronisation suivante, dix secondes plus tard par défaut ; les autorisations accordées hors de GrantForge restent, et l’agent signale celles qu’une politique refuse.

Systèmes: [ClickHouse](/fr/plugins/clickhouse/), [Apache Doris](/fr/plugins/doris/), [PostgreSQL](/fr/plugins/postgresql/), [Elasticsearch](/fr/plugins/elasticsearch/), [OpenSearch](/fr/plugins/opensearch/)

### Devant le système

Un proxy réseau auquel les clients se connectent à la place du serveur. Les instructions sont vérifiées, filtrées et masquées avant d’atteindre le serveur ; les clients se connectent par le proxy.

Systèmes: [MySQL](/fr/plugins/mysql/)

## Systèmes

| Type | Système | Application | Filtres de lignes | Masquage | Versions vérifiées |
| --- | --- | --- | --- | --- | --- |
| Base de données relationnelle | [MySQL](/fr/plugins/mysql/) · [agent](/fr/external/mysql-agent/) | Devant le système | Oui | Oui | 8.0, 8.4 |
| Base de données relationnelle | [PostgreSQL](/fr/plugins/postgresql/) · [agent](/fr/external/postgresql-agent/) | À côté du système | Oui | Non (PostgreSQL n’a pas de masquage de colonnes) | 15, 16, 17, 18 |
| Base de données analytique (OLAP) | [Apache Doris](/fr/plugins/doris/) · [agent](/fr/external/doris-agent/) | À côté du système | Oui | Non (Doris ne masque les valeurs que par Apache Ranger) | 2.1, 3.1, 4.0, 4.1 |
| Base de données analytique (OLAP) | [ClickHouse](/fr/plugins/clickhouse/) · [agent](/fr/external/clickhouse-agent/) | À côté du système | Oui | Non (ClickHouse ne masque les colonnes que dans son cloud) | 24.8, 25.3, 25.8, 26.3, 26.8 |
| Base orientée colonnes (NoSQL) | [Apache HBase](/fr/plugins/hbase/) · [agent](/fr/external/hbase-agent/) | Dans le système | Non | Non | 2.5.16, 2.6.7, 3.0.0 |
| Entrepôt de données et moteur de requêtes | [Apache Hive](/fr/plugins/hive/) · [agent](/fr/external/hive-agent/) | Dans le système | Oui | Oui | 3.1.3, 4.0.1, 4.1.0 |
| Entrepôt de données et moteur de requêtes | [Trino](/fr/plugins/trino/) · [agent](/fr/external/trino-agent/) | Dans le système | Oui | Oui | 470, 476, 479, 484 |
| Messagerie et streaming | [Apache Kafka](/fr/plugins/kafka/) · [agent](/fr/external/kafka-agent/) | Dans le système | Non | Non | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| Messagerie et streaming | [Apache Pulsar](/fr/plugins/pulsar/) · [agent](/fr/external/pulsar-agent/) | Dans le système | Non | Non | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| Moteur de recherche | [Apache Solr](/fr/plugins/solr/) · [agent](/fr/external/solr-agent/) | Dans le système | Non | Non | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| Moteur de recherche | [Elasticsearch](/fr/plugins/elasticsearch/) · [agent](/fr/external/elasticsearch-agent/) | À côté du système | Oui | Non (Elasticsearch ne masque pas de valeurs) | 8.19, 9.3, 9.4, 9.5 |
| Moteur de recherche | [OpenSearch](/fr/plugins/opensearch/) · [agent](/fr/external/opensearch-agent/) | À côté du système | Oui | Oui | 2.19, 3.3, 3.6, 3.9 |
| Stockage distribué | [Apache Hadoop HDFS](/fr/plugins/hdfs/) · [agent](/fr/external/hdfs-agent/) | Dans le système | Non | Non | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Gestion des ressources | [Apache Hadoop YARN](/fr/plugins/yarn/) · [agent](/fr/external/yarn-agent/) | Dans le système | Non | Non | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Coordination | [Apache ZooKeeper](/fr/plugins/zookeeper/) · [agent](/fr/external/zookeeper-agent/) | Dans le système | Non | Non | 3.9.5 (l’agent est construit avec ZooKeeper 3.6.4) |

Les versions sont celles sur lesquelles s’exécutent les tests d’intégration Testcontainers du dépôt ; les autres versions ne sont pas certifiées. Les pages de chaque plug-in et de son agent donnent les détails.

## Ce que fait chaque agent

Chaque agent télécharge l’instantané des politiques de son service, vérifie sa signature Ed25519 avant de l’utiliser et garde le dernier valide sur disque, de sorte qu’il continue d’appliquer les politiques quand le serveur GrantForge est absent. Les accès partent par lots vers l’audit des accès, les accès autorisés identiques regroupés en un événement avec leur nombre, et sont écrits sur disque tant que le serveur est injoignable.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Plug-ins et types de service](/fr/develop/plugins/)
