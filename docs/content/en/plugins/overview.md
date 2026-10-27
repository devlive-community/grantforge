---
title: "Overview"
description: "Every system GrantForge manages access to, how its agent enforces the policies, what it can filter and mask, and the versions the integration tests verify."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge manages access to 15 systems. For each, a service plugin in the GrantForge server describes the system's resources and access types, looks resources up for the policy editor and tests the connection; an agent enforces the policies in the system and reports every access to the access audit. The plugins below are sorted by kind of system, as in the menu.

## How agents enforce

An agent enforces the policies in one of three ways:

### In the system

The system asks the agent at every request: an authorizer, coprocessor, access control or plugin loaded into the system's own processes. Each request is decided at once from the snapshot in memory, down to what lies below a resource. The agent is installed in the system and loaded when it restarts.

Systems: [Apache Hadoop HDFS](/en/plugins/hdfs/), [Apache Hadoop YARN](/en/plugins/yarn/), [Apache ZooKeeper](/en/plugins/zookeeper/), [Apache Hive](/en/plugins/hive/), [Apache HBase](/en/plugins/hbase/), [Trino](/en/plugins/trino/), [Apache Kafka](/en/plugins/kafka/), [Apache Pulsar](/en/plugins/pulsar/), [Apache Solr](/en/plugins/solr/)

### Beside the system

A service next to the system keeps the system's own access control, its roles, grants and row policies, in step with the policies. Nothing is installed in the system, and the system checks every request itself, over every protocol. A change applies at the next sync, ten seconds later by default; grants made outside GrantForge stay, and the agent warns of those a policy denies.

Systems: [ClickHouse](/en/plugins/clickhouse/), [Apache Doris](/en/plugins/doris/), [PostgreSQL](/en/plugins/postgresql/), [Elasticsearch](/en/plugins/elasticsearch/), [OpenSearch](/en/plugins/opensearch/)

### In front of the system

A network proxy that clients connect to instead of the server. Statements are checked, filtered and masked before they reach the server; clients connect through the proxy.

Systems: [MySQL](/en/plugins/mysql/)

## Systems

| Kind | System | Enforcement | Row filters | Masking | Verified versions |
| --- | --- | --- | --- | --- | --- |
| Relational database | [MySQL](/en/plugins/mysql/) · [agent](/en/external/mysql-agent/) | In front of the system | Yes | Yes | 8.0, 8.4 |
| Relational database | [PostgreSQL](/en/plugins/postgresql/) · [agent](/en/external/postgresql-agent/) | Beside the system | Yes | No (PostgreSQL has no column masking) | 15, 16, 17, 18 |
| Analytical database (OLAP) | [Apache Doris](/en/plugins/doris/) · [agent](/en/external/doris-agent/) | Beside the system | Yes | No (Doris masks values only through Apache Ranger) | 2.1, 3.1, 4.0, 4.1 |
| Analytical database (OLAP) | [ClickHouse](/en/plugins/clickhouse/) · [agent](/en/external/clickhouse-agent/) | Beside the system | Yes | No (ClickHouse masks columns only in its cloud) | 24.8, 25.3, 25.8, 26.3, 26.8 |
| Wide-column database (NoSQL) | [Apache HBase](/en/plugins/hbase/) · [agent](/en/external/hbase-agent/) | In the system | No | No | 2.5.16, 2.6.7, 3.0.0 |
| Data warehouse and query engine | [Apache Hive](/en/plugins/hive/) · [agent](/en/external/hive-agent/) | In the system | Yes | Yes | 3.1.3, 4.0.1, 4.1.0 |
| Data warehouse and query engine | [Trino](/en/plugins/trino/) · [agent](/en/external/trino-agent/) | In the system | Yes | Yes | 470, 476, 479, 484 |
| Messaging and streaming | [Apache Kafka](/en/plugins/kafka/) · [agent](/en/external/kafka-agent/) | In the system | No | No | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| Messaging and streaming | [Apache Pulsar](/en/plugins/pulsar/) · [agent](/en/external/pulsar-agent/) | In the system | No | No | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| Search engine | [Apache Solr](/en/plugins/solr/) · [agent](/en/external/solr-agent/) | In the system | No | No | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| Search engine | [Elasticsearch](/en/plugins/elasticsearch/) · [agent](/en/external/elasticsearch-agent/) | Beside the system | Yes | No (Elasticsearch masks no values) | 8.19, 9.3, 9.4, 9.5 |
| Search engine | [OpenSearch](/en/plugins/opensearch/) · [agent](/en/external/opensearch-agent/) | Beside the system | Yes | Yes | 2.19, 3.3, 3.6, 3.9 |
| Distributed storage | [Apache Hadoop HDFS](/en/plugins/hdfs/) · [agent](/en/external/hdfs-agent/) | In the system | No | No | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Resource management | [Apache Hadoop YARN](/en/plugins/yarn/) · [agent](/en/external/yarn-agent/) | In the system | No | No | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Coordination | [Apache ZooKeeper](/en/plugins/zookeeper/) · [agent](/en/external/zookeeper-agent/) | In the system | No | No | 3.9.5 (the agent is built against ZooKeeper 3.6.4) |

Versions are those the repository's Testcontainers integration tests run against; other releases are not certified. Each plugin's page and its agent's page give the details.

## What every agent does

Every agent downloads its service's policy snapshot, checks its Ed25519 signature before using it and keeps the last good one on disk, so it keeps enforcing while the GrantForge server is away. Accesses go to the access audit in batches, identical allowed ones folded into one event with a count, and are spooled to disk while the server cannot be reached.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Plugins and service types](/en/develop/plugins/)
