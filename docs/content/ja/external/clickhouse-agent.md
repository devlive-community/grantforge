---
title: ClickHouse エージェント
description: サーバーの隣で ClickHouse エージェントを動かし、GrantForge のポリシーを列と行の単位まで ClickHouse 自身のロール、権限、行ポリシーにし、アクセスをクエリログから報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

ClickHouse エージェントはサーバーの隣で動くサービスです。ClickHouse 自身のアクセス制御を `clickhouse` データサービスの署名付きポリシーと一致させます。ユーザーごとに、ポリシーがそのユーザーに与えるものだけをちょうど持つロールを 1 つ、行フィルターごとに行ポリシーを 1 つ置きます。そのうえで ClickHouse が自身のすべてのプロトコルですべてのクエリを自ら検査し、エージェントがクライアントとサーバーの間に入ることはありません。配布物はこれを、Java 17 以降が必要な実行可能な JAR、`agents/clickhouse/grantforge-agent-clickhouse-<version>.jar` として提供します。

リポジトリの Testcontainers 統合テストは、エージェントと配布されるとおりの JAR を ClickHouse `24.8`、`25.3`、`25.8`、`26.3`、`26.8` に対して実行します。これは他のすべてのバージョンを認定するものではありません。エージェントは `24.8` より古いサーバーを拒否します。サービスプラグインは [ClickHouse](/ja/plugins/clickhouse/) を参照してください。

## 仕組み

- **ユーザーごとのロールが GrantForge の与えるものを持ちます。** ポリシーが何かを与えるユーザーごとに、エージェントはその名前のロール（`alice` なら `grantforge_alice`）を保ち、既定のロールとしてユーザーに付与します。`SHOW GRANTS FOR grantforge_alice` は GrantForge が `alice` に与えるすべてを示します。エージェントは他のロールや、自分が行っていない権限付与を変更しません。
- **下位で別の判定がなければデータベース全体に付与します。** ポリシーがデータベース全体に許可し、その中のテーブルや列からそれを取り上げうるポリシーがないアクセスタイプは、データベースに付与されます。そのため、後から作成したテーブルもすぐにそれを持ちます。そうでなければテーブルごとに、読み書きは列ごとに付与します。
- **行フィルターは行ポリシーになります。** 行フィルターは、テーブルに対するユーザーのロールの許可型（permissive）行ポリシーです。新しいフィルターや変更されたフィルターは、それが絞り込む権限より先に適用されます。ClickHouse が拒否するフィルターや、その文面が行ポリシーを他者にも適用させてしまうフィルターは、ユーザーに一行も見せないフィルターに置き換えられ、ログに記録されます。
- **現在のポリシーを使います。** 各同期は、その時点で適用されるポリシーで計画するため、有効期間は時間どおりに始まり終わります。また ClickHouse にあるユーザー、データベース、テーブル、列で計画するため、新しいものは次の同期で対象になります。
- **ClickHouse のロールはグループです。** グループに対するポリシーは、その名前の ClickHouse ロールを直接または他のロール経由で持つユーザーに、GrantForge がユーザーを入れているグループと併せて適用されます。
- **手作業の変更は元に戻します。** エージェントは各ロールと行ポリシーに与えた内容を覚えており、異なるものは次の同期で元に戻します。何も変わっていなければ、エージェントは何も送りません。
- **GrantForge 外の権限付与を報告します。** ClickHouse は権限を合算するため、ユーザーが直接または他のロール経由で持つものをエージェントは取り上げられません。ポリシーが拒否するものを与えるそのような権限付与ごとに、ユーザー、権限、経由するロール、ポリシーを示して一度だけ警告します。
- **アクセスはクエリログから取ります。** エージェントは `system.query_log` を読み、各文を、それが触れたテーブル、データベース、サーバーごとに 1 件のイベントとして報告し、拒否は ClickHouse のエラーが示すものについて 1 件の拒否イベントとして報告します。各イベントは、GrantForge が決めたか、つまりポリシーが ClickHouse と同じ判定をするか、それとも GrantForge 外の権限付与が許可したかを示します。

| 文 | アクセスイベント |
| --- | --- |
| `SELECT`、`INSERT … SELECT` の読み取り側 | 読んだ各テーブルの `select` |
| `INSERT` | 書き込んだテーブルの `insert` |
| `ALTER`、`ALTER UPDATE`、`ALTER DELETE`、`DELETE`、`UPDATE` | テーブルの `alter` |
| `DROP TABLE`、`TRUNCATE`、`OPTIMIZE` | テーブルの `drop`、`truncate`、`optimize` |
| `CREATE TABLE`、`CREATE VIEW`、`CREATE DICTIONARY` | データベースの `create` |
| `DROP DATABASE` | データベースの `drop_database` |
| `CREATE DATABASE`、`KILL QUERY`、`SYSTEM …` | サーバーの `create_database`、`kill_query`、`system` |

## 配置

1. `clickhouse` データサービスを追加してポリシーを設定します。**データ権限 → エージェント** を開き、サービスを選んでエージェントトークンを発行します。トークンは一度しか表示されないため、エージェントの実行アカウントだけが読めるファイルに保存します。
2. エージェントの ClickHouse ユーザーを作成します。このユーザーはポリシーが与えるものを付与するため、それらの権限を付与オプション付きで持ち、ロールと行ポリシーを保つためのアクセス管理権限が必要です。

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   エージェントがロールを保つユーザーは、変更できる場所、つまり `CREATE USER` が保存する `local_directory` か `replicated` のユーザーディレクトリになければなりません。`users.xml` と LDAP のユーザーには手を付けず、一度だけログに記録します。クラスターでは、ユーザー、ロール、行ポリシーを `replicated` のユーザーディレクトリに置いてください。そうすれば 1 つのエージェントでクラスター全体を扱えます。
3. 設定ファイルを書き、例のアドレス、パス、名前を置き換えます。

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   これらのパスはエージェントのマシン上にあり、キャッシュディレクトリは書き込み可能でなければなりません。パスワードファイルは UTF-8 で読み、前後の空白を除きます。
4. エージェントを起動します。たとえば systemd サービスとして:

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   `<version>` は配布物のバージョンに置き換えます。`java -Dgrantforge.…=… -jar …` で指定した設定はファイルより優先されます。エージェントは SIGTERM できれいに停止し、保留中のイベントを送信するかスプールします。ClickHouse は与えられたものを保ちます。
5. **既存の権限付与をポリシーに移してください:** GrantForge の外で行った権限付与は、エージェントのロールと並んで引き続き有効です。対応するポリシーを作成し、そのうえで権限を取り消してください。エージェントの警告が、ポリシーの拒否するものを与えている付与を示します。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須: GrantForge サーバー、エージェントトークンのファイル、このエージェントの名前、署名付きスナップショット・スプールしたイベント・クエリログの読み取り位置用の書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると、初回接続時にサーバーから取得します |
| `grantforge.clickhouse.urls`、`grantforge.clickhouse.user` | 必須: サーバーの HTTP インターフェース（到達できなければ順に次を試します）と、エージェントの ClickHouse ユーザー |
| `grantforge.clickhouse.password.file` | ユーザーのパスワード。省略するとパスワードなしでサインインします |
| `grantforge.clickhouse.tls.ca` | `https://` のサーバーを検証する PEM 形式の CA 証明書。空の場合は Java が信頼するものを信頼します |
| `grantforge.clickhouse.tls.cert`、`grantforge.clickhouse.tls.key` | パスワードの代わりに証明書（`IDENTIFIED WITH ssl_certificate`）でサインインするための PEM 形式のクライアント証明書と秘密鍵。両方を指定します |
| `grantforge.clickhouse.timeout.ms` | ClickHouse へのリクエストにかけられる時間。既定 `30000`、範囲 `1..600000` ミリ秒 |
| `grantforge.clickhouse.role.prefix` | エージェントのロール名の先頭。既定 `grantforge_`。英字、数字、アンダースコアからなり、アンダースコアで終わります。名前がこれで始まるロールと行ポリシーはすべてエージェントのものです |
| `grantforge.clickhouse.unmanaged.users` | エージェントが手を付けないユーザー。カンマ区切りで、管理者などを指定します。エージェント自身のユーザーには常に手を付けません |
| `grantforge.sync.interval.ms` | エージェントが ClickHouse を合わせる間隔。既定 `10000`、範囲 `1000..3600000` ミリ秒 |
| `grantforge.audit.query.log` | 既定 `true`。`false` はクエリログを読まず、アクセスを報告しません |
| `grantforge.audit.query.log.cluster` | `clusterAllReplicas` でレプリカのクエリログを読むクラスター。空の場合はエージェントが問い合わせるサーバーを読みます |
| `grantforge.audit.query.log.delay.ms`、`grantforge.audit.query.log.interval.ms`、`grantforge.audit.query.log.batch` | ClickHouse は 7.5 秒ごとにログを書くため、現在からどれだけ遅れて読むか（既定 `10000` ミリ秒）、読む間隔（既定 `5000` ミリ秒）、1 回に読む最大件数（既定 `1000`） |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを取得する間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント: バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスク上のスプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同一の許可アクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ報告 |

## ユーザー、グループ、ロール

ポリシーは ClickHouse のユーザーを指定します。グループは、ユーザーが直接または他のロール経由で持つ ClickHouse のロールと、GrantForge がユーザーを入れているグループです。ロールは GrantForge がユーザーに与えたロールです。

## 決定と制限

- スナップショットがない間、エージェントは何も変更しません。使われていないサービス、またはタイプが `clickhouse` ではないサービスに紐づいたトークンは何も付与しません。エージェントは自身のロールと行ポリシーを削除し、ClickHouse 自身の権限が適用されます。
- エージェントが動いていない間、ClickHouse は最後に与えられたものを保ち、ポリシーの変更はエージェントを待ちます。変更は更新間隔と同期間隔のうちに ClickHouse に届きます。
- エージェントがテーブルごとに付与するデータベースで作成したテーブルは、次の同期で権限を得ます。
- ClickHouse 上に行ポリシーが 1 つもないユーザーは、テーブルのすべての行を見ます。これは ClickHouse の `users_without_row_policies_can_read_rows` 設定が既定で許す動作で、オフにするとフィルターのないユーザーはフィルター付きのテーブルの行を 1 行も見られません。同じユーザーに対して GrantForge の外で作成した許可型の行ポリシーは、そのフィルターが見せる範囲を広げます。
- `system` は `SYSTEM SHUTDOWN` を含むすべての `SYSTEM` 文を付与します。
- クエリログは ClickHouse が記録するものだけを報告します。`log_queries` をオフにしたユーザーの文は報告されません。拒否では、ユーザーに列が 1 つ足りないとき ClickHouse がテーブルだけを示すことがあり、その場合イベントもテーブルを示します。
- ClickHouse は ClickHouse Cloud 以外では列のマスキングを持たないため、ポリシーで値をマスクすることはできません。

## 関連ガイド

- [ClickHouse](/ja/plugins/clickhouse/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
