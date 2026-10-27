---
title: "Übersicht"
description: "Jedes System, dessen Zugriff GrantForge verwaltet, wie sein Agent die Richtlinien durchsetzt, was er filtern und maskieren kann und welche Versionen die Integrationstests prüfen."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge verwaltet den Zugriff auf 14 Systeme. Für jedes beschreibt ein Service-Plug-in im GrantForge-Server die Ressourcen und Zugriffstypen des Systems, sucht Ressourcen für den Richtlinieneditor und testet die Verbindung; ein Agent setzt die Richtlinien im System durch und meldet jeden Zugriff an das Zugriffsaudit. Die Plug-ins unten sind nach Art des Systems geordnet, wie im Menü.

## Wie Agenten durchsetzen

Ein Agent setzt die Richtlinien auf eine von drei Arten durch:

### Im System

Das System fragt den Agenten bei jeder Anfrage: ein Autorisierer, Coprozessor, eine Zugriffskontrolle oder ein Plug-in in den eigenen Prozessen des Systems. Jede Anfrage wird sofort aus dem Snapshot im Speicher entschieden, bis hinunter zu dem, was unterhalb einer Ressource liegt. Der Agent wird im System installiert und beim Neustart geladen.

Systeme: [Apache Hadoop HDFS](/de/plugins/hdfs/), [Apache Hadoop YARN](/de/plugins/yarn/), [Apache ZooKeeper](/de/plugins/zookeeper/), [Apache Hive](/de/plugins/hive/), [Apache HBase](/de/plugins/hbase/), [Trino](/de/plugins/trino/), [Apache Kafka](/de/plugins/kafka/), [Apache Pulsar](/de/plugins/pulsar/), [Apache Solr](/de/plugins/solr/)

### Neben dem System

Ein Dienst neben dem System hält die eigene Zugriffskontrolle des Systems, seine Rollen, Berechtigungen und Row Policies, mit den Richtlinien im Einklang. Im System wird nichts installiert, und das System prüft jede Anfrage selbst, über jedes Protokoll. Eine Änderung gilt mit dem nächsten Sync, standardmäßig nach zehn Sekunden; Berechtigungen außerhalb von GrantForge bleiben, und der Agent warnt vor denen, die eine Richtlinie verweigert.

Systeme: [ClickHouse](/de/plugins/clickhouse/), [PostgreSQL](/de/plugins/postgresql/), [Elasticsearch](/de/plugins/elasticsearch/), [OpenSearch](/de/plugins/opensearch/)

### Vor dem System

Ein Netzwerk-Proxy, mit dem sich Clients statt mit dem Server verbinden. Anweisungen werden geprüft, gefiltert und maskiert, bevor sie den Server erreichen; Clients verbinden sich über den Proxy.

Systeme: [MySQL](/de/plugins/mysql/)

## Systeme

| Art | System | Durchsetzung | Zeilenfilter | Maskierung | Geprüfte Versionen |
| --- | --- | --- | --- | --- | --- |
| Relationale Datenbank | [MySQL](/de/plugins/mysql/) · [Agent](/de/external/mysql-agent/) | Vor dem System | Ja | Ja | 8.0, 8.4 |
| Relationale Datenbank | [PostgreSQL](/de/plugins/postgresql/) · [Agent](/de/external/postgresql-agent/) | Neben dem System | Ja | Nein (PostgreSQL hat keine Spaltenmaskierung) | 15, 16, 17, 18 |
| Analytische Datenbank (OLAP) | [ClickHouse](/de/plugins/clickhouse/) · [Agent](/de/external/clickhouse-agent/) | Neben dem System | Ja | Nein (ClickHouse maskiert Spalten nur in seiner Cloud) | 24.8, 25.3, 25.8, 26.3, 26.8 |
| Wide-Column-Datenbank (NoSQL) | [Apache HBase](/de/plugins/hbase/) · [Agent](/de/external/hbase-agent/) | Im System | Nein | Nein | 2.5.16, 2.6.7, 3.0.0 |
| Data Warehouse und Abfrage-Engine | [Apache Hive](/de/plugins/hive/) · [Agent](/de/external/hive-agent/) | Im System | Ja | Ja | 3.1.3, 4.0.1, 4.1.0 |
| Data Warehouse und Abfrage-Engine | [Trino](/de/plugins/trino/) · [Agent](/de/external/trino-agent/) | Im System | Ja | Ja | 470, 476, 479, 484 |
| Messaging und Streaming | [Apache Kafka](/de/plugins/kafka/) · [Agent](/de/external/kafka-agent/) | Im System | Nein | Nein | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| Messaging und Streaming | [Apache Pulsar](/de/plugins/pulsar/) · [Agent](/de/external/pulsar-agent/) | Im System | Nein | Nein | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| Suchmaschine | [Apache Solr](/de/plugins/solr/) · [Agent](/de/external/solr-agent/) | Im System | Nein | Nein | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| Suchmaschine | [Elasticsearch](/de/plugins/elasticsearch/) · [Agent](/de/external/elasticsearch-agent/) | Neben dem System | Ja | Nein (Elasticsearch maskiert keine Werte) | 8.19, 9.3, 9.4, 9.5 |
| Suchmaschine | [OpenSearch](/de/plugins/opensearch/) · [Agent](/de/external/opensearch-agent/) | Neben dem System | Ja | Ja | 2.19, 3.3, 3.6, 3.9 |
| Verteilter Speicher | [Apache Hadoop HDFS](/de/plugins/hdfs/) · [Agent](/de/external/hdfs-agent/) | Im System | Nein | Nein | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Ressourcenverwaltung | [Apache Hadoop YARN](/de/plugins/yarn/) · [Agent](/de/external/yarn-agent/) | Im System | Nein | Nein | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Koordination | [Apache ZooKeeper](/de/plugins/zookeeper/) · [Agent](/de/external/zookeeper-agent/) | Im System | Nein | Nein | 3.9.5 (der Agent wird gegen ZooKeeper 3.6.4 gebaut) |

Die Versionen sind die, gegen die die Testcontainers-Integrationstests des Repositorys laufen; andere Versionen sind nicht zertifiziert. Die Seiten jedes Plug-ins und seines Agenten nennen die Einzelheiten.

## Was jeder Agent tut

Jeder Agent lädt den Richtlinien-Snapshot seines Dienstes, prüft dessen Ed25519-Signatur vor der Verwendung und behält den letzten gültigen auf der Festplatte, sodass er weiter durchsetzt, während der GrantForge-Server nicht erreichbar ist. Zugriffe gehen gebündelt an das Zugriffsaudit, identische erlaubte zu einem Ereignis mit Anzahl zusammengefasst, und werden auf die Festplatte geschrieben, solange der Server nicht erreichbar ist.

## Verwandte Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
