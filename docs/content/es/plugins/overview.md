---
title: "Visión general"
description: "Cada sistema cuyo acceso gestiona GrantForge, cómo su agente aplica las políticas, qué puede filtrar y enmascarar y las versiones que verifican las pruebas de integración."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge gestiona el acceso a 14 sistemas. Para cada uno, un plugin de servicio en el servidor de GrantForge describe los recursos y tipos de acceso del sistema, busca recursos para el editor de políticas y prueba la conexión; un agente aplica las políticas en el sistema e informa de cada acceso a la auditoría de accesos. Los plugins siguientes están ordenados por tipo de sistema, como en el menú.

## Cómo aplican los agentes

Un agente aplica las políticas de una de tres maneras:

### En el sistema

El sistema pregunta al agente en cada solicitud: un autorizador, coprocesador, control de acceso o plugin cargado en los procesos del propio sistema. Cada solicitud se decide al instante con la instantánea en memoria, hasta lo que hay por debajo de un recurso. El agente se instala en el sistema y se carga al reiniciarlo.

Sistemas: [Apache Hadoop HDFS](/es/plugins/hdfs/), [Apache Hadoop YARN](/es/plugins/yarn/), [Apache ZooKeeper](/es/plugins/zookeeper/), [Apache Hive](/es/plugins/hive/), [Apache HBase](/es/plugins/hbase/), [Trino](/es/plugins/trino/), [Apache Kafka](/es/plugins/kafka/), [Apache Pulsar](/es/plugins/pulsar/), [Apache Solr](/es/plugins/solr/)

### Junto al sistema

Un servicio junto al sistema mantiene el control de acceso propio del sistema, sus roles, permisos y políticas de filas, de acuerdo con las políticas. No se instala nada en el sistema, y el sistema verifica cada solicitud por sí mismo, por cualquier protocolo. Un cambio se aplica en la siguiente sincronización, diez segundos después por defecto; los permisos concedidos fuera de GrantForge se mantienen, y el agente avisa de los que una política deniega.

Sistemas: [ClickHouse](/es/plugins/clickhouse/), [PostgreSQL](/es/plugins/postgresql/), [Elasticsearch](/es/plugins/elasticsearch/), [OpenSearch](/es/plugins/opensearch/)

### Delante del sistema

Un proxy de red al que los clientes se conectan en lugar del servidor. Las sentencias se verifican, filtran y enmascaran antes de llegar al servidor; los clientes se conectan a través del proxy.

Sistemas: [MySQL](/es/plugins/mysql/)

## Sistemas

| Tipo | Sistema | Aplicación | Filtros de filas | Enmascaramiento | Versiones verificadas |
| --- | --- | --- | --- | --- | --- |
| Base de datos relacional | [MySQL](/es/plugins/mysql/) · [agente](/es/external/mysql-agent/) | Delante del sistema | Sí | Sí | 8.0, 8.4 |
| Base de datos relacional | [PostgreSQL](/es/plugins/postgresql/) · [agente](/es/external/postgresql-agent/) | Junto al sistema | Sí | No (PostgreSQL no tiene enmascaramiento de columnas) | 15, 16, 17, 18 |
| Base de datos analítica (OLAP) | [ClickHouse](/es/plugins/clickhouse/) · [agente](/es/external/clickhouse-agent/) | Junto al sistema | Sí | No (ClickHouse enmascara columnas solo en su nube) | 24.8, 25.3, 25.8, 26.3, 26.8 |
| Base de datos de columnas anchas (NoSQL) | [Apache HBase](/es/plugins/hbase/) · [agente](/es/external/hbase-agent/) | En el sistema | No | No | 2.5.16, 2.6.7, 3.0.0 |
| Almacén de datos y motor de consulta | [Apache Hive](/es/plugins/hive/) · [agente](/es/external/hive-agent/) | En el sistema | Sí | Sí | 3.1.3, 4.0.1, 4.1.0 |
| Almacén de datos y motor de consulta | [Trino](/es/plugins/trino/) · [agente](/es/external/trino-agent/) | En el sistema | Sí | Sí | 470, 476, 479, 484 |
| Mensajería y streaming | [Apache Kafka](/es/plugins/kafka/) · [agente](/es/external/kafka-agent/) | En el sistema | No | No | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| Mensajería y streaming | [Apache Pulsar](/es/plugins/pulsar/) · [agente](/es/external/pulsar-agent/) | En el sistema | No | No | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| Motor de búsqueda | [Apache Solr](/es/plugins/solr/) · [agente](/es/external/solr-agent/) | En el sistema | No | No | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| Motor de búsqueda | [Elasticsearch](/es/plugins/elasticsearch/) · [agente](/es/external/elasticsearch-agent/) | Junto al sistema | Sí | No (Elasticsearch no enmascara valores) | 8.19, 9.3, 9.4, 9.5 |
| Motor de búsqueda | [OpenSearch](/es/plugins/opensearch/) · [agente](/es/external/opensearch-agent/) | Junto al sistema | Sí | Sí | 2.19, 3.3, 3.6, 3.9 |
| Almacenamiento distribuido | [Apache Hadoop HDFS](/es/plugins/hdfs/) · [agente](/es/external/hdfs-agent/) | En el sistema | No | No | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Gestión de recursos | [Apache Hadoop YARN](/es/plugins/yarn/) · [agente](/es/external/yarn-agent/) | En el sistema | No | No | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Coordinación | [Apache ZooKeeper](/es/plugins/zookeeper/) · [agente](/es/external/zookeeper-agent/) | En el sistema | No | No | 3.9.5 (el agente se compila contra ZooKeeper 3.6.4) |

Las versiones son aquellas contra las que se ejecutan las pruebas de integración con Testcontainers del repositorio; otras versiones no están certificadas. Las páginas de cada plugin y de su agente dan los detalles.

## Lo que hace todo agente

Todo agente descarga la instantánea de políticas de su servicio, verifica su firma Ed25519 antes de usarla y guarda la última válida en disco, de modo que sigue aplicando las políticas mientras el servidor de GrantForge no está disponible. Los accesos van por lotes a la auditoría de accesos, con los permitidos idénticos agrupados en un evento con su recuento, y se guardan en disco mientras no se puede llegar al servidor.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Plugins y tipos de servicio](/es/develop/plugins/)
