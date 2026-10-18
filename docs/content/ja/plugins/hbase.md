---
title: Apache HBase
description: HBase サービスプラグインをインストール・設定し、ZooKeeper またはブートストラップサーバーでクラスターを見つけ、simple または Kerberos でサインインして、名前空間・テーブル・カラムファミリーを検索し、HBase 自身のスコープでアクセスポリシーを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

HBase プラグインは名前空間、テーブル、カラムファミリーを検索し、HBase 自身の権限のスコープでアクセスポリシーを管理します。スコープはグローバル、名前空間、テーブル、カラムファミリー、カラムです。プラグイン ID とサービスタイプはいずれも `hbase` です。サービスプラグインは GrantForge サーバーで動作し、master と region server にインストールした [HBase エージェント](/ja/external/hbase-agent/)がポリシーを適用します。

リポジトリの Testcontainers 統合テストは、SHA-512 で検証した Apache リリースから構築した HBase `2.5.16`、`2.6.7`、`3.0.0` と、`2.6.7` の Kerberos クラスターを対象とします。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/hbase` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `hbase` が有効であることを確認し、プラグインディレクトリを更新したら再スキャンしてください。プラグインは独自のクラスローダーで、Hadoop の shaded クライアント 3.5.0 上の HBase 自身のクライアント（`hbase-shaded-client-byo-hadoop` 2.6.7）を使ってクラスターと通信するため、HBase 2.x と 3.x のどちらでも動作します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して Apache HBase（`hbase`）を選択します。
2. ZooKeeper の quorum、または HBase 2.5 以降のクラスターのブートストラップサーバーを入力します。
3. 検索ユーザーのサインイン方式を選んでユーザーを入力し、接続をテストしてからサービスを保存します。テストはサインインしてクラスターを記述します。クラスター ID、リリース、稼働中の region server 数です。

設定は HBase クライアント自身の名前を使うため、`hbase-site.xml` からそのままコピーできます。検索ユーザーは GrantForge がリソースを見つけるためのもので、データを読み書きするクライアントとは別です。接続テストが成功しても、リクエストがすでにポリシーの対象になっているわけではありません。

| 設定 | 用途 |
| --- | --- |
| `hbase.zookeeper.quorum` | ZooKeeper ホスト。`ホスト` または `ホスト:ポート` をカンマ区切りで指定。例：`zk1,zk2,zk3:2181`。ブートストラップサーバーがない場合は必須 |
| `hbase.zookeeper.property.clientPort` | ポートを指定しない quorum ホストのポート。既定 `2181` |
| `zookeeper.znode.parent` | ZooKeeper 内のクラスターのルート。既定 `/hbase` |
| `hbase.client.bootstrap.servers` | ZooKeeper の代わりに、HBase 2.5 以降：master または region server。`ホスト:ポート` をカンマ区切りで指定 |
| `hbase.security.authentication` | `simple`（既定）：ユーザー名、`kerberos`：keytab またはパスワードを使うプリンシパル |
| `username` | 必須の検索ユーザー。Kerberos では完全なプリンシパル。例：`grantforge@EXAMPLE.COM` |
| `password` | Kerberos：keytab がない場合のプリンシパルのパスワード。暗号化して保存されます |
| `keytab` | Kerberos：**GrantForge サーバー**上で読めるプリンシパルの keytab のパス |
| `kerberos.kdc` | Kerberos：プリンシパルのレルムの KDC。`ホスト[:ポート]` をカンマ区切りで指定。空ならサーバーの `krb5.conf` を使います |
| `hbase.master.kerberos.principal` | Kerberos：master のプリンシパル。既定は `hbase/_HOST@` と検索プリンシパルのレルム |
| `hbase.regionserver.kerberos.principal` | Kerberos：region server のプリンシパル。既定は master と同じ |
| `hbase.rpc.protection` | `authentication`（既定）、`integrity`、`privacy`。クラスターと一致させます |
| `hbase.rpc.timeout` | RPC タイムアウト。既定 `30000`、範囲 `1..600000` |

サービスは設定に書かれたとおりです。GrantForge サーバーの `hbase-site.xml` は読み込みません。simple 認証では Kerberos の設定は拒否され、Kerberos にはパスワードか keytab が必要です。検索は HBase の既定のように数分待たず、数回試して諦めます。

### Kerberos

`kerberos` では、GrantForge は Hadoop 自身のログインで keytab またはパスワードを使ってプリンシパルをサインインさせ、サインインを保持して keytab から更新します。GrantForge サーバーにほかの Kerberos 設定は不要です。`kerberos.kdc` に KDC を入力したレルムは、HDFS や Hive プラグインと同じ方法で GrantForge サーバー全体に宣言されるため、複数のサービスが異なるレルムを並行して使えます。

クラスターは、想定と異なる方法でサインインするクライアントの接続を、理由を告げずに閉じます。プラグインはこれをサインインの拒否として報告し、クラスターと一致している必要がある `hbase.security.authentication` と `hbase.rpc.protection` を案内します。

### 検索ユーザーに見えるもの

[HBase エージェント](/ja/external/hbase-agent/)がポリシーを適用すると、一覧から検索ユーザーが使えない名前空間とテーブルが除かれます。名前空間 `*` で `read` を許可するポリシーを与えるか、クラスターの `hbase.superuser` に含めてください。

## リソースとアクセスタイプ

| レベル | 照合 | アクセスタイプ |
| --- | --- | --- |
| `global` | 唯一の値 `*` | `admin`：バランサー、クォータ、レプリケーションなどクラスター全体の操作 |
| `namespace` | ワイルドカード、大文字と小文字を区別 | `read`、`write`、`create`、`admin` |
| `table` | ワイルドカード、大文字と小文字を区別。名前空間の下にあり、名前空間なしで名付ける | `read`、`write`、`create`、`admin` |
| `column_family` | ワイルドカード、大文字と小文字を区別。テーブルの下 | `read`、`write` |
| `column` | ワイルドカード、大文字と小文字を区別。カラムファミリーの下。UTF-8 テキストで名付ける | `read`、`write` |

アクセスタイプは HBase の `read`、`write`、`create`、`admin` です。コプロセッサーのエンドポイント用の HBase の `exec` は提供しません。エージェントがすべての HBase リリースでエンドポイント呼び出しを検査できないためです。

HBase と同じく、権限はそのスコープ内のすべてを含みます。名前空間 `sales` の `read` はその中のすべてのテーブルを読み、カラムファミリーの `write` はそのすべてのカラムに書き込みます。リクエストは指定した各スコープで決定され、いずれかのスコープで拒否されればリクエストは拒否されます。そのため、ポリシーでテーブルを許可しつつ、その中の 1 つのカラムを拒否できます。グローバルスコープはクラスターそのものであり、すべてのテーブルではありません。すべてのテーブルは名前空間 `*` のポリシーが含みます。

ポリシーの値では、`*` は任意個の文字、`?` は 1 文字に一致します。ポリシーエディターは名前空間、ポリシーが指定する名前空間内のテーブル、そのテーブルのカラムファミリーを提案します。カラムはデータなので提案しません。

## ポリシーの例

名前空間 `sales` に、カラムファミリー `d`（`name` と `ssn` を含む）と `meta` を持つテーブル `orders` があるとします。同じデータサービスに、有効な次のアクセスポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `namespace=sales` | ユーザー `alice` に `read` を許可 |
| `namespace=sales`、`table=orders`、`column_family=d` | ユーザー `bob` に `read` を許可 |
| `namespace=sales`、`table=orders` | グループ `auditors` に `read` を許可 |
| `namespace=sales`、`table=orders`、`column_family=d`、`column=ssn` | グループ `auditors` の `read` を拒否 |
| `namespace=analytics` | ユーザー `dave` に `create` と `admin` を許可 |

エージェントが新しいスナップショットを適用すると、`alice` は `sales` のすべてのテーブルを読めます。`bob` は `orders` を読むと `d` のカラムだけを受け取り、テーブル一覧でも `orders` だけが見えます。`auditors` のメンバーは `d:ssn` を除く `orders` 全体を読めます。`dave` は名前空間 `analytics` とそのテーブルを作成・管理でき、ほかの場所でのテーブル作成は拒否されます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、すべての master と region server に HBase エージェントが必要です。配置方法と検査内容は [HBase エージェント](/ja/external/hbase-agent/)を参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [HBase エージェント](/ja/external/hbase-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
