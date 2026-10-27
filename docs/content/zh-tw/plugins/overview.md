---
title: "總覽"
description: "GrantForge 管理存取的每個系統、其代理執行策略的方式、可以過濾和遮罩什麼，以及整合測試驗證的版本。"
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge 管理 13 個系統的存取。每個系統都有一個執行在 GrantForge 伺服器中的服務外掛，描述系統的資源與存取類型、為策略編輯器查詢資源並測試連線；還有一個代理在系統中執行策略，並把每次存取回報給存取稽核。下面的外掛依系統類型排列，與選單一致。

## 代理如何執行策略

代理以下面三種方式之一執行策略：

### 系統內

系統在每次請求時詢問代理：代理是載入到系統自身程序中的授權器、協處理器、存取控制或外掛。每個請求都根據記憶體中的快照立即判定，細化到資源之下的內容。代理安裝在系統中，系統重新啟動時載入。

系統：[Apache Hadoop HDFS](/zh-tw/plugins/hdfs/)、[Apache Hadoop YARN](/zh-tw/plugins/yarn/)、[Apache ZooKeeper](/zh-tw/plugins/zookeeper/)、[Apache Hive](/zh-tw/plugins/hive/)、[Apache HBase](/zh-tw/plugins/hbase/)、[Trino](/zh-tw/plugins/trino/)、[Apache Kafka](/zh-tw/plugins/kafka/)、[Apache Pulsar](/zh-tw/plugins/pulsar/)、[Apache Solr](/zh-tw/plugins/solr/)

### 系統旁

執行在系統旁的服務讓系統自身的存取控制（角色、授權與列策略）與策略保持一致。系統中不需要安裝任何東西，系統自己透過每種協定檢查每個請求。變更在下一次同步時生效，預設在十秒內；GrantForge 之外的授權保持不變，代理會警告其中被策略拒絕的部分。

系統：[ClickHouse](/zh-tw/plugins/clickhouse/)、[PostgreSQL](/zh-tw/plugins/postgresql/)、[OpenSearch](/zh-tw/plugins/opensearch/)

### 系統前

用戶端連線的網路代理，代替直接連線伺服器。語句在到達伺服器之前被檢查、過濾和遮罩；用戶端透過代理連線。

系統：[MySQL](/zh-tw/plugins/mysql/)

## 系統

| 類型 | 系統 | 執行方式 | 列過濾 | 遮罩 | 驗證版本 |
| --- | --- | --- | --- | --- | --- |
| 關聯式資料庫 | [MySQL](/zh-tw/plugins/mysql/) · [代理](/zh-tw/external/mysql-agent/) | 系統前 | 支援 | 支援 | 8.0, 8.4 |
| 關聯式資料庫 | [PostgreSQL](/zh-tw/plugins/postgresql/) · [代理](/zh-tw/external/postgresql-agent/) | 系統旁 | 支援 | 不支援（PostgreSQL 沒有欄遮罩） | 15, 16, 17, 18 |
| 分析型資料庫（OLAP） | [ClickHouse](/zh-tw/plugins/clickhouse/) · [代理](/zh-tw/external/clickhouse-agent/) | 系統旁 | 支援 | 不支援（ClickHouse 只在其雲端服務中遮罩欄） | 24.8, 25.3, 25.8, 26.3, 26.8 |
| 寬欄資料庫（NoSQL） | [Apache HBase](/zh-tw/plugins/hbase/) · [代理](/zh-tw/external/hbase-agent/) | 系統內 | 不支援 | 不支援 | 2.5.16, 2.6.7, 3.0.0 |
| 資料倉儲與查詢引擎 | [Apache Hive](/zh-tw/plugins/hive/) · [代理](/zh-tw/external/hive-agent/) | 系統內 | 支援 | 支援 | 3.1.3, 4.0.1, 4.1.0 |
| 資料倉儲與查詢引擎 | [Trino](/zh-tw/plugins/trino/) · [代理](/zh-tw/external/trino-agent/) | 系統內 | 支援 | 支援 | 470, 476, 479, 484 |
| 訊息與串流 | [Apache Kafka](/zh-tw/plugins/kafka/) · [代理](/zh-tw/external/kafka-agent/) | 系統內 | 不支援 | 不支援 | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| 訊息與串流 | [Apache Pulsar](/zh-tw/plugins/pulsar/) · [代理](/zh-tw/external/pulsar-agent/) | 系統內 | 不支援 | 不支援 | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| 搜尋引擎 | [Apache Solr](/zh-tw/plugins/solr/) · [代理](/zh-tw/external/solr-agent/) | 系統內 | 不支援 | 不支援 | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| 搜尋引擎 | [OpenSearch](/zh-tw/plugins/opensearch/) · [代理](/zh-tw/external/opensearch-agent/) | 系統旁 | 支援 | 支援 | 2.19, 3.3, 3.6, 3.9 |
| 分散式儲存 | [Apache Hadoop HDFS](/zh-tw/plugins/hdfs/) · [代理](/zh-tw/external/hdfs-agent/) | 系統內 | 不支援 | 不支援 | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| 資源排程 | [Apache Hadoop YARN](/zh-tw/plugins/yarn/) · [代理](/zh-tw/external/yarn-agent/) | 系統內 | 不支援 | 不支援 | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| 分散式協調 | [Apache ZooKeeper](/zh-tw/plugins/zookeeper/) · [代理](/zh-tw/external/zookeeper-agent/) | 系統內 | 不支援 | 不支援 | 3.9.5（代理基於 ZooKeeper 3.6.4 建置） |

版本是儲存庫的 Testcontainers 整合測試所執行的版本，其他版本不在認證範圍內。詳情見每個外掛及其代理的頁面。

## 每個代理都會做的事

每個代理都會下載其服務的策略快照，使用前驗證 Ed25519 簽章，並把最後一個有效快照保存在磁碟上，因此 GrantForge 伺服器無法使用時仍能繼續執行策略。存取會分批進入存取稽核，相同的允許存取合併為一筆帶次數的事件；伺服器無法連線時先寫入磁碟。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [外掛與服務類型](/zh-tw/develop/plugins/)
