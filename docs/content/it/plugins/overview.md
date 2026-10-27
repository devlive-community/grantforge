---
title: "Panoramica"
description: "Ogni sistema di cui GrantForge gestisce l’accesso, come il suo agente applica le policy, cosa può filtrare e mascherare e le versioni verificate dai test di integrazione."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge gestisce l’accesso a 14 sistemi. Per ciascuno, un plugin di servizio nel server GrantForge descrive le risorse e i tipi di accesso del sistema, cerca le risorse per l’editor delle policy e verifica la connessione; un agente applica le policy nel sistema e riporta ogni accesso all’audit degli accessi. I plugin qui sotto sono ordinati per tipo di sistema, come nel menu.

## Come applicano gli agenti

Un agente applica le policy in uno di tre modi:

### Nel sistema

Il sistema interroga l’agente a ogni richiesta: un autorizzatore, coprocessore, controllo degli accessi o plugin caricato nei processi del sistema stesso. Ogni richiesta è decisa subito dallo snapshot in memoria, fino a ciò che sta sotto una risorsa. L’agente è installato nel sistema e caricato al suo riavvio.

Sistemi: [Apache Hadoop HDFS](/it/plugins/hdfs/), [Apache Hadoop YARN](/it/plugins/yarn/), [Apache ZooKeeper](/it/plugins/zookeeper/), [Apache Hive](/it/plugins/hive/), [Apache HBase](/it/plugins/hbase/), [Trino](/it/plugins/trino/), [Apache Kafka](/it/plugins/kafka/), [Apache Pulsar](/it/plugins/pulsar/), [Apache Solr](/it/plugins/solr/)

### Accanto al sistema

Un servizio accanto al sistema mantiene il controllo degli accessi proprio del sistema, i suoi ruoli, permessi e row policy, allineato alle policy. Nel sistema non si installa nulla, e il sistema verifica da sé ogni richiesta, su ogni protocollo. Una modifica si applica alla sincronizzazione successiva, dieci secondi dopo per impostazione predefinita; i permessi concessi fuori da GrantForge restano, e l’agente avvisa di quelli che una policy nega.

Sistemi: [ClickHouse](/it/plugins/clickhouse/), [PostgreSQL](/it/plugins/postgresql/), [Elasticsearch](/it/plugins/elasticsearch/), [OpenSearch](/it/plugins/opensearch/)

### Davanti al sistema

Un proxy di rete a cui i client si collegano al posto del server. Le istruzioni sono verificate, filtrate e mascherate prima di raggiungere il server; i client si collegano tramite il proxy.

Sistemi: [MySQL](/it/plugins/mysql/)

## Sistemi

| Tipo | Sistema | Applicazione | Filtri di righe | Mascheramento | Versioni verificate |
| --- | --- | --- | --- | --- | --- |
| Database relazionale | [MySQL](/it/plugins/mysql/) · [agente](/it/external/mysql-agent/) | Davanti al sistema | Sì | Sì | 8.0, 8.4 |
| Database relazionale | [PostgreSQL](/it/plugins/postgresql/) · [agente](/it/external/postgresql-agent/) | Accanto al sistema | Sì | No (PostgreSQL non ha il mascheramento delle colonne) | 15, 16, 17, 18 |
| Database analitico (OLAP) | [ClickHouse](/it/plugins/clickhouse/) · [agente](/it/external/clickhouse-agent/) | Accanto al sistema | Sì | No (ClickHouse maschera le colonne solo nel suo cloud) | 24.8, 25.3, 25.8, 26.3, 26.8 |
| Database a colonne larghe (NoSQL) | [Apache HBase](/it/plugins/hbase/) · [agente](/it/external/hbase-agent/) | Nel sistema | No | No | 2.5.16, 2.6.7, 3.0.0 |
| Data warehouse e motore di query | [Apache Hive](/it/plugins/hive/) · [agente](/it/external/hive-agent/) | Nel sistema | Sì | Sì | 3.1.3, 4.0.1, 4.1.0 |
| Data warehouse e motore di query | [Trino](/it/plugins/trino/) · [agente](/it/external/trino-agent/) | Nel sistema | Sì | Sì | 470, 476, 479, 484 |
| Messaggistica e streaming | [Apache Kafka](/it/plugins/kafka/) · [agente](/it/external/kafka-agent/) | Nel sistema | No | No | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| Messaggistica e streaming | [Apache Pulsar](/it/plugins/pulsar/) · [agente](/it/external/pulsar-agent/) | Nel sistema | No | No | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| Motore di ricerca | [Apache Solr](/it/plugins/solr/) · [agente](/it/external/solr-agent/) | Nel sistema | No | No | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| Motore di ricerca | [Elasticsearch](/it/plugins/elasticsearch/) · [agente](/it/external/elasticsearch-agent/) | Accanto al sistema | Sì | No (Elasticsearch non maschera valori) | 8.19, 9.3, 9.4, 9.5 |
| Motore di ricerca | [OpenSearch](/it/plugins/opensearch/) · [agente](/it/external/opensearch-agent/) | Accanto al sistema | Sì | Sì | 2.19, 3.3, 3.6, 3.9 |
| Storage distribuito | [Apache Hadoop HDFS](/it/plugins/hdfs/) · [agente](/it/external/hdfs-agent/) | Nel sistema | No | No | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Gestione delle risorse | [Apache Hadoop YARN](/it/plugins/yarn/) · [agente](/it/external/yarn-agent/) | Nel sistema | No | No | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Coordinamento | [Apache ZooKeeper](/it/plugins/zookeeper/) · [agente](/it/external/zookeeper-agent/) | Nel sistema | No | No | 3.9.5 (l’agente è compilato con ZooKeeper 3.6.4) |

Le versioni sono quelle su cui girano i test di integrazione Testcontainers del repository; le altre versioni non sono certificate. Le pagine di ogni plugin e del suo agente danno i dettagli.

## Cosa fa ogni agente

Ogni agente scarica lo snapshot delle policy del proprio servizio, ne verifica la firma Ed25519 prima di usarlo e conserva su disco l’ultimo valido, così continua ad applicare le policy mentre il server GrantForge non è raggiungibile. Gli accessi vanno a lotti all’audit degli accessi, quelli consentiti identici raggruppati in un evento con il conteggio, e sono scritti su disco finché il server non è raggiungibile.

## Guide correlate

- [Servizi di dati, policy e agenti](/it/external/data-services/)
- [Plugin e tipi di servizio](/it/develop/plugins/)
