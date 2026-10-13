---
title: Apache Hive エージェント
description: HiveServer2 にエージェントをインストールし、GrantForge のポリシーで文をチェックし、SHOW を絞り込み、列をマスクして行をフィルターします。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Hive エージェントは、Apache Ranger の Hive プラグインと同じく、Hive の認可 API のオーソライザーです。HiveServer2 は各文を実行する前にエージェントに問い合わせ、エージェントは `hive` データサービスの署名付きポリシーに基づいて答えます。配布物では Java 8 互換の単一 JAR、`agents/hive/grantforge-agent-hive-<version>.jar` として提供され、依存関係は内部に再配置されています。

リポジトリの Testcontainers 統合テストは、HiveServer2 `3.1.3`、`4.0.1`、`4.1.0` の中でこの JAR を実行します。これはほかのすべてのバージョンを認定するものではありません。サービスプラグインは [Apache Hive](/ja/plugins/hive/) を参照してください。

## 仕組み

- **文をチェックします。** 文が読み書きする各オブジェクトについて、エージェントは Ranger と同じく操作に必要なアクセスタイプをポリシーに問い合わせます。最初の拒否で文は `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]` として失敗します。クエリは列ごとにチェックします。
- **一覧を絞り込みます。** `SHOW DATABASES` と `SHOW TABLES` は、ユーザーが使えるもの、つまりデータベースやテーブル、またはその中の何かに何らかのアクセス権があるものだけを表示します。`USE` と `DESCRIBE DATABASE` にも同じ条件が必要です。
- **クエリをマスクしフィルターします。** ユーザーにテーブルのマスク列や行フィルターがある場合、HiveServer2 がマスクと条件でクエリを書き換えるため、結合・条件・関数はユーザーが見てよいものだけを見ます。
- **マスクは回避できません。** ユーザーにマスクや行フィルターがあるテーブルへの書き込み、変更、エクスポートは、Ranger の既定と同様に拒否されます。
- **権限とロールは GrantForge が管理します。** Hive の `GRANT`、`REVOKE`、`SHOW GRANT`、`CREATE ROLE`、`SET ROLE` などのロール文は拒否され、`SHOW CURRENT ROLES` は GrantForge がユーザーに与えたロールを表示します。
- **安全でない文を拒否します。** HiveServer2 自身の権限でファイルシステムにアクセスすることになる `dfs` コマンドは拒否されます。HiveServer2 のセッションでは `TRANSFORM` スクリプトが禁止され、ユーザーは Hive の SQL 標準認可が許可する設定だけを `SET` できます。
- **すべての決定を報告します。** ユーザー、クライアントアドレス、リソース、アクセスタイプ、結果、操作、文を GrantForge に送ります。

| 操作 | アクセスタイプ |
| --- | --- |
| クエリ、`SHOW CREATE TABLE`、`SHOW PARTITIONS`、`SHOW TBLPROPERTIES`、`DESCRIBE`、`SHOW COLUMNS`、`ANALYZE` | `select` |
| `INSERT`、`UPDATE`、`DELETE`、`MERGE`、`TRUNCATE`、`LOAD`（対象） | `update` |
| `CREATE DATABASE`、`CREATE TABLE`、`CREATE VIEW`、`CREATE FUNCTION` | 作成するものに `create`、読み取るものに `select` |
| `ALTER …`、`MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`、`UNLOCK` | `lock` |
| `SHOW DATABASES`、`SHOW TABLES`、`USE`、`DESCRIBE DATABASE` | オブジェクトまたはその中の何かへの任意のアクセス |
| `REPL DUMP`、`REPL LOAD`、`REPL STATUS` | `repladmin` |
| `KILL QUERY`、リソースプラン、LLAP 管理 | `hiveservice` に対する `serviceadmin` |
| 一時関数、`ADD JAR`、`COMPILE` | `global` に対する `tempudfadmin` |
| 文が読み書きする場所 | `url` に対する `read` または `write` |

## 配置

1. `hive` データサービスを追加してポリシーを設定します。HiveServer2 が自身の処理を行うユーザー（`hive` など）に必要なすべてを許可するポリシーも含めてください。**データ権限 → エージェント**を開き、サービスを選択してエージェントトークンを発行します。トークンは一度だけ表示されるので、HiveServer2 のアカウントだけが読めるファイルに保存してください。
2. **すべての HiveServer2** の `lib`（例：`/opt/hive/lib`）に JAR をコピーします。
3. 各 HiveServer2 の `hiveserver2-site.xml` または `hive-site.xml` に次の設定を追加し、例のアドレス、パス、インスタンス名を置き換えます。

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   キャッシュディレクトリは HiveServer2 のアカウントが書き込める必要があります。Ranger と同様に、`hive.server2.enable.doAs=false` にすると HiveServer2 は自身のアカウントでデータを読み書きし、誰が何を見られるかはポリシーが決めます。
4. HiveServer2 を再起動します。エージェントは最初のセッションとともに起動し、サーバーとともに停止して、未送信のイベントを送信またはキューに保存します。
5. **HiveServer2 を迂回する経路を閉じてください。** エージェントがチェックするのは HiveServer2 で実行される文だけです。Hive CLI、Spark、HDFS クライアントなど、メタストアやウェアハウスのファイルに直接アクセスするクライアントはチェックされません。メタストアと HDFS 自身の権限、または [HDFS エージェント](/ja/external/hdfs-agent/)で保護してください。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、この HiveServer2 の名前、署名付きスナップショットとキューのイベント用の書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると初回接続時にサーバーから鍵を取得します |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続・読み取りタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを問い合わせる間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスクキューの上限（既定 64 MiB） |

エージェントは HiveServer2 の設定ファイルとシステムプロパティ（`-Dgrantforge.…`、こちらが優先）から設定を読み、クライアントが決められるセッションの設定からは決して読みません。

## ユーザー、グループ、ロール

ポリシーが指すのは、HiveServer2 がセッションに与えるユーザーです。クライアントがサインインしたユーザー名、または Kerberos プリンシパルの短縮名（`alice@EXAMPLE.COM` なら `alice`）です。グループは HiveServer2 で Hadoop のグループマッピングがユーザーに与えるグループと、GrantForge でユーザーが属するグループで、ロールは GrantForge がユーザーに与えるロールです。

## 決定と制限

- 使えるスナップショットがない場合、またはポリシーを読めない場合は、すべての文が拒否されます。使用されていないサービスは、オーソライザーがない場合と同じく HiveServer2 をチェックしません。
- タイプが `hive` でないサービスに結び付いたトークンは、すべてを拒否します。
- 文が読み書きする場所は `url` ポリシーが決めます。Ranger と異なり、エージェントはファイルシステム自身の権限にフォールバックしません。
- Hive がオブジェクトを示さない文は、すべてのデータベースについてチェックされます。オブジェクトのない `REPL` 文には `repladmin`、オブジェクトのないテーブルやデータベースの所有者変更には `alter` が、データベース `*` に対して必要です。
- `EXPLAIN`、`DESCRIBE FUNCTION`、`SHOW FUNCTIONS`、`SET` など、Ranger がチェックしないほかの文もチェックしません。`getTables` などの JDBC クライアントのメタデータ呼び出しは絞り込まれません。
- エージェントは HiveServer2 で動作します。メタストア内部の認可は対象外です。

## 関連ガイド

- [Apache Hive](/ja/plugins/hive/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
