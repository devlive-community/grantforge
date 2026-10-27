---
title: "Visão geral"
description: "Cada sistema cujo acesso o GrantForge gerencia, como o seu agente aplica as políticas, o que pode filtrar e mascarar e as versões verificadas pelos testes de integração."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O GrantForge gerencia o acesso a 14 sistemas. Para cada um, um plug-in de serviço no servidor do GrantForge descreve os recursos e tipos de acesso do sistema, busca recursos para o editor de políticas e testa a conexão; um agente aplica as políticas no sistema e relata cada acesso à auditoria de acessos. Os plug-ins abaixo estão ordenados por tipo de sistema, como no menu.

## Como os agentes aplicam

Um agente aplica as políticas de uma de três formas:

### No sistema

O sistema consulta o agente a cada requisição: um autorizador, coprocessador, controle de acesso ou plug-in carregado nos processos do próprio sistema. Cada requisição é decidida na hora pelo snapshot em memória, até o que fica abaixo de um recurso. O agente é instalado no sistema e carregado quando ele reinicia.

Sistemas: [Apache Hadoop HDFS](/pt-br/plugins/hdfs/), [Apache Hadoop YARN](/pt-br/plugins/yarn/), [Apache ZooKeeper](/pt-br/plugins/zookeeper/), [Apache Hive](/pt-br/plugins/hive/), [Apache HBase](/pt-br/plugins/hbase/), [Trino](/pt-br/plugins/trino/), [Apache Kafka](/pt-br/plugins/kafka/), [Apache Pulsar](/pt-br/plugins/pulsar/), [Apache Solr](/pt-br/plugins/solr/)

### Ao lado do sistema

Um serviço ao lado do sistema mantém o controle de acesso próprio do sistema, seus papéis, permissões e políticas de linhas, de acordo com as políticas. Nada é instalado no sistema, e o próprio sistema verifica cada requisição, por qualquer protocolo. Uma mudança vale na próxima sincronização, dez segundos depois por padrão; permissões concedidas fora do GrantForge permanecem, e o agente avisa das que uma política nega.

Sistemas: [ClickHouse](/pt-br/plugins/clickhouse/), [PostgreSQL](/pt-br/plugins/postgresql/), [Elasticsearch](/pt-br/plugins/elasticsearch/), [OpenSearch](/pt-br/plugins/opensearch/)

### Na frente do sistema

Um proxy de rede ao qual os clientes se conectam em vez do servidor. As instruções são verificadas, filtradas e mascaradas antes de chegar ao servidor; os clientes se conectam pelo proxy.

Sistemas: [MySQL](/pt-br/plugins/mysql/)

## Sistemas

| Tipo | Sistema | Aplicação | Filtros de linhas | Mascaramento | Versões verificadas |
| --- | --- | --- | --- | --- | --- |
| Banco de dados relacional | [MySQL](/pt-br/plugins/mysql/) · [agente](/pt-br/external/mysql-agent/) | Na frente do sistema | Sim | Sim | 8.0, 8.4 |
| Banco de dados relacional | [PostgreSQL](/pt-br/plugins/postgresql/) · [agente](/pt-br/external/postgresql-agent/) | Ao lado do sistema | Sim | Não (o PostgreSQL não tem mascaramento de colunas) | 15, 16, 17, 18 |
| Banco de dados analítico (OLAP) | [ClickHouse](/pt-br/plugins/clickhouse/) · [agente](/pt-br/external/clickhouse-agent/) | Ao lado do sistema | Sim | Não (o ClickHouse mascara colunas apenas na sua nuvem) | 24.8, 25.3, 25.8, 26.3, 26.8 |
| Banco de colunas largas (NoSQL) | [Apache HBase](/pt-br/plugins/hbase/) · [agente](/pt-br/external/hbase-agent/) | No sistema | Não | Não | 2.5.16, 2.6.7, 3.0.0 |
| Data warehouse e motor de consulta | [Apache Hive](/pt-br/plugins/hive/) · [agente](/pt-br/external/hive-agent/) | No sistema | Sim | Sim | 3.1.3, 4.0.1, 4.1.0 |
| Data warehouse e motor de consulta | [Trino](/pt-br/plugins/trino/) · [agente](/pt-br/external/trino-agent/) | No sistema | Sim | Sim | 470, 476, 479, 484 |
| Mensageria e streaming | [Apache Kafka](/pt-br/plugins/kafka/) · [agente](/pt-br/external/kafka-agent/) | No sistema | Não | Não | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| Mensageria e streaming | [Apache Pulsar](/pt-br/plugins/pulsar/) · [agente](/pt-br/external/pulsar-agent/) | No sistema | Não | Não | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| Mecanismo de busca | [Apache Solr](/pt-br/plugins/solr/) · [agente](/pt-br/external/solr-agent/) | No sistema | Não | Não | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| Mecanismo de busca | [Elasticsearch](/pt-br/plugins/elasticsearch/) · [agente](/pt-br/external/elasticsearch-agent/) | Ao lado do sistema | Sim | Não (o Elasticsearch não mascara valores) | 8.19, 9.3, 9.4, 9.5 |
| Mecanismo de busca | [OpenSearch](/pt-br/plugins/opensearch/) · [agente](/pt-br/external/opensearch-agent/) | Ao lado do sistema | Sim | Sim | 2.19, 3.3, 3.6, 3.9 |
| Armazenamento distribuído | [Apache Hadoop HDFS](/pt-br/plugins/hdfs/) · [agente](/pt-br/external/hdfs-agent/) | No sistema | Não | Não | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Gerenciamento de recursos | [Apache Hadoop YARN](/pt-br/plugins/yarn/) · [agente](/pt-br/external/yarn-agent/) | No sistema | Não | Não | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| Coordenação | [Apache ZooKeeper](/pt-br/plugins/zookeeper/) · [agente](/pt-br/external/zookeeper-agent/) | No sistema | Não | Não | 3.9.5 (o agente é compilado contra o ZooKeeper 3.6.4) |

As versões são aquelas contra as quais rodam os testes de integração com Testcontainers do repositório; outras versões não são certificadas. As páginas de cada plug-in e do seu agente trazem os detalhes.

## O que todo agente faz

Todo agente baixa o snapshot de políticas do seu serviço, verifica a assinatura Ed25519 antes de usá-lo e guarda o último válido em disco, de modo que continua aplicando as políticas enquanto o servidor do GrantForge está fora do ar. Os acessos vão em lotes para a auditoria de acessos, os permitidos idênticos agrupados em um evento com a contagem, e são gravados em disco enquanto o servidor não pode ser alcançado.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
