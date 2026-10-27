---
title: "总览"
description: "GrantForge 管理访问的每个系统、其代理执行策略的方式、可以过滤和屏蔽什么，以及集成测试验证的版本。"
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge 管理 14 个系统的访问。每个系统都有一个运行在 GrantForge 服务器中的服务插件，描述系统的资源与访问类型、为策略编辑器查询资源并测试连接；还有一个代理在系统中执行策略，并把每次访问报告给访问审计。下面的插件按系统类型排列，与菜单一致。

## 代理如何执行策略

代理以下面三种方式之一执行策略：

### 系统内

系统在每次请求时询问代理：代理是加载到系统自身进程中的授权器、协处理器、访问控制或插件。每个请求都根据内存中的快照立即判定，细化到资源之下的内容。代理安装在系统中，系统重启时加载。

系统：[Apache Hadoop HDFS](/plugins/hdfs/)、[Apache Hadoop YARN](/plugins/yarn/)、[Apache ZooKeeper](/plugins/zookeeper/)、[Apache Hive](/plugins/hive/)、[Apache HBase](/plugins/hbase/)、[Trino](/plugins/trino/)、[Apache Kafka](/plugins/kafka/)、[Apache Pulsar](/plugins/pulsar/)、[Apache Solr](/plugins/solr/)

### 系统旁

运行在系统旁的服务让系统自身的访问控制（角色、授权与行策略）与策略保持一致。系统中不需要安装任何东西，系统自己通过每种协议检查每个请求。变更在下一次同步时生效，默认在十秒内；GrantForge 之外的授权保持不变，代理会警告其中被策略拒绝的部分。

系统：[ClickHouse](/plugins/clickhouse/)、[PostgreSQL](/plugins/postgresql/)、[Elasticsearch](/plugins/elasticsearch/)、[OpenSearch](/plugins/opensearch/)

### 系统前

客户端连接的网络代理，代替直接连接服务器。语句在到达服务器之前被检查、过滤和屏蔽；客户端通过代理连接。

系统：[MySQL](/plugins/mysql/)

## 系统

| 类型 | 系统 | 执行方式 | 行过滤 | 屏蔽 | 验证版本 |
| --- | --- | --- | --- | --- | --- |
| 关系型数据库 | [MySQL](/plugins/mysql/) · [代理](/external/mysql-agent/) | 系统前 | 支持 | 支持 | 8.0, 8.4 |
| 关系型数据库 | [PostgreSQL](/plugins/postgresql/) · [代理](/external/postgresql-agent/) | 系统旁 | 支持 | 不支持（PostgreSQL 没有列屏蔽） | 15, 16, 17, 18 |
| 分析型数据库（OLAP） | [ClickHouse](/plugins/clickhouse/) · [代理](/external/clickhouse-agent/) | 系统旁 | 支持 | 不支持（ClickHouse 只在其云服务中屏蔽列） | 24.8, 25.3, 25.8, 26.3, 26.8 |
| 宽列数据库（NoSQL） | [Apache HBase](/plugins/hbase/) · [代理](/external/hbase-agent/) | 系统内 | 不支持 | 不支持 | 2.5.16, 2.6.7, 3.0.0 |
| 数据仓库与查询引擎 | [Apache Hive](/plugins/hive/) · [代理](/external/hive-agent/) | 系统内 | 支持 | 支持 | 3.1.3, 4.0.1, 4.1.0 |
| 数据仓库与查询引擎 | [Trino](/plugins/trino/) · [代理](/external/trino-agent/) | 系统内 | 支持 | 支持 | 470, 476, 479, 484 |
| 消息与流 | [Apache Kafka](/plugins/kafka/) · [代理](/external/kafka-agent/) | 系统内 | 不支持 | 不支持 | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| 消息与流 | [Apache Pulsar](/plugins/pulsar/) · [代理](/external/pulsar-agent/) | 系统内 | 不支持 | 不支持 | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| 搜索引擎 | [Apache Solr](/plugins/solr/) · [代理](/external/solr-agent/) | 系统内 | 不支持 | 不支持 | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| 搜索引擎 | [Elasticsearch](/plugins/elasticsearch/) · [代理](/external/elasticsearch-agent/) | 系统旁 | 支持 | 不支持（Elasticsearch 不对值做脱敏） | 8.19, 9.3, 9.4, 9.5 |
| 搜索引擎 | [OpenSearch](/plugins/opensearch/) · [代理](/external/opensearch-agent/) | 系统旁 | 支持 | 支持 | 2.19, 3.3, 3.6, 3.9 |
| 分布式存储 | [Apache Hadoop HDFS](/plugins/hdfs/) · [代理](/external/hdfs-agent/) | 系统内 | 不支持 | 不支持 | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| 资源调度 | [Apache Hadoop YARN](/plugins/yarn/) · [代理](/external/yarn-agent/) | 系统内 | 不支持 | 不支持 | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| 分布式协调 | [Apache ZooKeeper](/plugins/zookeeper/) · [代理](/external/zookeeper-agent/) | 系统内 | 不支持 | 不支持 | 3.9.5（代理基于 ZooKeeper 3.6.4 构建） |

版本是仓库的 Testcontainers 集成测试所运行的版本，其他版本不在认证范围内。详情见每个插件及其代理的页面。

## 每个代理都会做的事

每个代理都会下载其服务的策略快照，使用前校验 Ed25519 签名，并把最后一个有效快照保存在磁盘上，因此 GrantForge 服务器不可用时仍能继续执行策略。访问会分批进入访问审计，相同的允许访问合并为一条带次数的事件；服务器无法访问时先写入磁盘。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [插件与服务类型](/develop/plugins/)
