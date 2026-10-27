---
title: "概要"
description: "GrantForge がアクセスを管理するすべてのシステムについて、エージェントがポリシーを適用する方法、フィルターとマスクの可否、統合テストで検証したバージョンをまとめます。"
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge は 15 のシステムへのアクセスを管理します。システムごとに、GrantForge サーバーのサービスプラグインがシステムのリソースとアクセス種別を定義し、ポリシーエディターのためにリソースを検索し、接続をテストします。エージェントはシステムでポリシーを適用し、すべてのアクセスをアクセス監査に報告します。以下のプラグインは、メニューと同じくシステムの種類ごとに並んでいます。

## エージェントの適用方法

エージェントは次の 3 つの方法のいずれかでポリシーを適用します。

### システム内

システムがリクエストごとにエージェントに問い合わせます。エージェントはシステム自身のプロセスに読み込まれる認可機能、コプロセッサー、アクセス制御またはプラグインです。各リクエストはメモリ上のスナップショットですぐに判定され、リソースの下位まで及びます。エージェントはシステムにインストールされ、再起動時に読み込まれます。

システム：[Apache Hadoop HDFS](/ja/plugins/hdfs/)、[Apache Hadoop YARN](/ja/plugins/yarn/)、[Apache ZooKeeper](/ja/plugins/zookeeper/)、[Apache Hive](/ja/plugins/hive/)、[Apache HBase](/ja/plugins/hbase/)、[Trino](/ja/plugins/trino/)、[Apache Kafka](/ja/plugins/kafka/)、[Apache Pulsar](/ja/plugins/pulsar/)、[Apache Solr](/ja/plugins/solr/)

### システムの隣

システムの隣で動くサービスが、システム自身のアクセス制御（ロール、権限、行ポリシー）をポリシーに合わせ続けます。システムには何もインストールせず、システム自身があらゆるプロトコルですべてのリクエストを検査します。変更は次の同期で反映され、既定では 10 秒後です。GrantForge の外で付与された権限は残り、ポリシーが拒否するものはエージェントが警告します。

システム：[ClickHouse](/ja/plugins/clickhouse/)、[Apache Doris](/ja/plugins/doris/)、[PostgreSQL](/ja/plugins/postgresql/)、[Elasticsearch](/ja/plugins/elasticsearch/)、[OpenSearch](/ja/plugins/opensearch/)

### システムの前

クライアントがサーバーの代わりに接続するネットワークプロキシです。ステートメントはサーバーに届く前に検査、フィルター、マスクされます。クライアントはプロキシ経由で接続します。

システム：[MySQL](/ja/plugins/mysql/)

## システム

| 種類 | システム | 適用方法 | 行フィルター | マスク | 検証済みバージョン |
| --- | --- | --- | --- | --- | --- |
| リレーショナルデータベース | [MySQL](/ja/plugins/mysql/) · [エージェント](/ja/external/mysql-agent/) | システムの前 | 対応 | 対応 | 8.0, 8.4 |
| リレーショナルデータベース | [PostgreSQL](/ja/plugins/postgresql/) · [エージェント](/ja/external/postgresql-agent/) | システムの隣 | 対応 | 非対応（PostgreSQL には列マスクがありません） | 15, 16, 17, 18 |
| 分析型データベース（OLAP） | [Apache Doris](/ja/plugins/doris/) · [エージェント](/ja/external/doris-agent/) | システムの隣 | 対応 | 非対応（Doris は Apache Ranger 経由でのみ値をマスクします） | 2.1, 3.1, 4.0, 4.1 |
| 分析型データベース（OLAP） | [ClickHouse](/ja/plugins/clickhouse/) · [エージェント](/ja/external/clickhouse-agent/) | システムの隣 | 対応 | 非対応（ClickHouse は自社クラウドでのみ列をマスクします） | 24.8, 25.3, 25.8, 26.3, 26.8 |
| ワイドカラムデータベース（NoSQL） | [Apache HBase](/ja/plugins/hbase/) · [エージェント](/ja/external/hbase-agent/) | システム内 | 非対応 | 非対応 | 2.5.16, 2.6.7, 3.0.0 |
| データウェアハウスとクエリエンジン | [Apache Hive](/ja/plugins/hive/) · [エージェント](/ja/external/hive-agent/) | システム内 | 対応 | 対応 | 3.1.3, 4.0.1, 4.1.0 |
| データウェアハウスとクエリエンジン | [Trino](/ja/plugins/trino/) · [エージェント](/ja/external/trino-agent/) | システム内 | 対応 | 対応 | 470, 476, 479, 484 |
| メッセージングとストリーミング | [Apache Kafka](/ja/plugins/kafka/) · [エージェント](/ja/external/kafka-agent/) | システム内 | 非対応 | 非対応 | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| メッセージングとストリーミング | [Apache Pulsar](/ja/plugins/pulsar/) · [エージェント](/ja/external/pulsar-agent/) | システム内 | 非対応 | 非対応 | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| 検索エンジン | [Apache Solr](/ja/plugins/solr/) · [エージェント](/ja/external/solr-agent/) | システム内 | 非対応 | 非対応 | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| 検索エンジン | [Elasticsearch](/ja/plugins/elasticsearch/) · [エージェント](/ja/external/elasticsearch-agent/) | システムの隣 | 対応 | 非対応（Elasticsearch は値をマスクしません） | 8.19, 9.3, 9.4, 9.5 |
| 検索エンジン | [OpenSearch](/ja/plugins/opensearch/) · [エージェント](/ja/external/opensearch-agent/) | システムの隣 | 対応 | 対応 | 2.19, 3.3, 3.6, 3.9 |
| 分散ストレージ | [Apache Hadoop HDFS](/ja/plugins/hdfs/) · [エージェント](/ja/external/hdfs-agent/) | システム内 | 非対応 | 非対応 | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| リソース管理 | [Apache Hadoop YARN](/ja/plugins/yarn/) · [エージェント](/ja/external/yarn-agent/) | システム内 | 非対応 | 非対応 | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| 分散協調 | [Apache ZooKeeper](/ja/plugins/zookeeper/) · [エージェント](/ja/external/zookeeper-agent/) | システム内 | 非対応 | 非対応 | 3.9.5（エージェントは ZooKeeper 3.6.4 でビルドされます） |

バージョンはリポジトリの Testcontainers 統合テストが対象とするものです。ほかのバージョンは認定していません。詳細は各プラグインとそのエージェントのページを参照してください。

## すべてのエージェントが行うこと

すべてのエージェントはサービスのポリシースナップショットをダウンロードし、使う前に Ed25519 署名を検証し、最後の有効なスナップショットをディスクに保存します。そのため GrantForge サーバーに接続できない間も適用を続けます。アクセスはまとめてアクセス監査に送られ、同一の許可されたアクセスは件数付きの 1 件のイベントにまとめられます。サーバーに接続できない間はディスクに書き込まれます。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
