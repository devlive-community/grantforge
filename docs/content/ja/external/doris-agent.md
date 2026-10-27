---
title: Apache Doris エージェント
description: クラスターの隣で Apache Doris エージェントを動かし、GrantForge のポリシーを Doris 自身のロール、列の権限、ワークロードグループの権限、行ポリシーにして、ユーザーのすべての ID に与え、Doris がそれ以上を与えない場合にだけデータベースやカタログ全体に付与し、アクセスを Doris の監査ログテーブルから報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Doris エージェントは、クラスターの隣で動くサービスです。Doris 自身のアクセス制御を `doris` データサービスの署名付きポリシーに合わせ続けます。ユーザーごとに、ポリシーがそのユーザーに与えるものだけをちょうど持つロールを 1 つ、列とワークロードグループまで含めて保ち、行フィルターごとに行ポリシーを 1 つ保ちます。その後は Doris がすべてのステートメントを自ら確認し、エージェントがクライアントとフロントエンドの間に入ることはありません。配布物は実行可能な JAR `agents/doris/grantforge-agent-doris-<version>.jar` として提供し、Java 17 以降が必要です。

リポジトリの Testcontainers 統合テストは、エージェントと配布される JAR を Apache Doris `2.1`、`3.1`、`4.0`、`4.1` で動かします。これは他のすべてのバージョンを認定するものではありません。エージェントは `2.1` より古いクラスターを拒否します。サービスプラグインについては [Apache Doris](/ja/plugins/doris/) を参照してください。

## 仕組み

- **ユーザーごとに 1 つのロールが GrantForge の与えるものを持ちます。** ポリシーが何かを与える各ユーザーについて、エージェントはその名前のロール（`alice` なら `grantforge_alice`）を、ユーザーを示すコメント付きで保ち、`'alice'@'%'` や `'alice'@'10.%'` のようにユーザーのすべての ID に付与します。`SHOW ROLES` で GrantForge が `alice` に与えるものが分かります。エージェントは他のロールも、自分が付与していない権限も変更しません。
- **下位で別の決定がなければデータベースやカタログ全体に付与します。** ポリシーがデータベースやカタログ全体で許可し、下位で取り上げるポリシーがない読み取り、ロード、ビューの表示は、データベースやカタログに付与するため、後から作られたテーブルもすぐに使えます。それ以外はテーブルごとに、読み取りは列ごとに付与します。変更はテーブルごとに付与します。Doris のデータベースへのこの権限はデータベースの名前変更も許すためです。
- **ポリシーが許す以上は与えません。** カタログにデータベースを作る Doris の権限は、そのすべてのデータベースにテーブルを作る権限であり、データベースを削除する権限は、そのすべてのテーブルを削除する権限です。エージェントは、ポリシーがより広い権限も与える場合にだけそれぞれを付与します。そうでなければ保留し、どのユーザーが何をできないのか、その理由を一度ログに記録します。
- **ワークロードグループは名前で付与します。** ワークロードグループへのポリシーは、それが許す各グループに `USAGE_PRIV` を与え、ユーザーはそこでクエリを実行できます。後から作られたグループは次の同期で付与します。
- **行フィルターは行ポリシーになります。** 行フィルターは、テーブル上のユーザーのロールの許容的（permissive）な行ポリシーです。新しいフィルターは、それが絞る権限より先に置かれます。変わるフィルターは、行を 1 つも見せない制限的（restrictive）なポリシーの陰で置き換えるため、ユーザーがどちらのフィルターより多くを見ることはありません。各フィルターはまずテーブルで実行され、Doris がそこで実行できない、または拒否するフィルター（テーブルにない列を指すものなど）は、行を 1 つも見せないフィルターに置き換えられ、ログに記録されます。
- **現時点のポリシー。** 各同期はその時点で有効なポリシーと、Doris にあるユーザー、カタログ、データベース、テーブル、列で計画を立てるため、有効期間は時間どおりに始まり終わり、新しいものは次の同期で対象になります。
- **Doris のロールがグループです。** グループのポリシーは、その名前の Doris ロールを持つユーザーと、GrantForge がユーザーを入れたグループに適用されます。
- **手作業の変更は元に戻ります。** エージェントは同期のたびに、`SHOW ROLES` が示す自分のロールの権限と自分の行ポリシーを計画と比べ、異なる部分を元に戻します。Doris は列の権限をユーザーごとに、そのすべての権限をまとめた形でしか示しません。エージェントは、計画が指す列をユーザーがすべて読めない場合にそれらを付与し、それ以外の列を自分のロールから一度取り消します。それでも読める列は GrantForge 外の権限によるものです。何も変わっていなければ何も送りません。
- **GrantForge 外の権限を報告します。** Doris は権限を足し合わせ、許容的な行ポリシーを OR で結ぶため、エージェントはユーザーが直接または別のロールで持つものを取り上げられません。ポリシーが拒否するものを与えるそのような権限、列の権限、行ポリシーごとに一度、ユーザー、権限、その出どころを示して警告し、どのロールにも縛られない `ADMIN_PRIV` または `NODE_PRIV` を持つユーザーも警告します。
- **アクセスは監査ログから得ます。** エージェントはすべてのフロントエンドが書く `__internal_schema.audit_log` を読み、各ステートメントを、触れたテーブル、データベース、カタログごとに 1 件のイベントとして報告します。ログはテーブルを示さないため、触れたテーブルは Druid の Doris パーサーでステートメント自体から読み取ります。拒否は、Doris のエラーが示す権限とオブジェクトについての 1 件の拒否イベントです。各イベントは、GrantForge が決めたのか、つまりポリシーが Doris と同じ決定をするのか、GrantForge 外の権限が許可したのかを伝えます。ログはフロントエンドのバッチ間隔 `audit_plugin_max_batch_interval_sec` に 5 秒を加えた分だけ現在より遅れて読みます。

| ステートメント | アクセスイベント |
| --- | --- |
| `SELECT`、`INSERT … SELECT` の読み取り側 | 読み取られた各テーブルの `select` |
| `INSERT`、`UPDATE`、`DELETE`、`TRUNCATE` | 書き込まれたテーブルの `load` |
| `ALTER TABLE`、`DROP TABLE`、`SHOW CREATE VIEW` | テーブルの `alter`、`drop`、`show_view` |
| `CREATE TABLE`、`CREATE VIEW` | データベースの `create` |
| `DROP DATABASE` | データベースの `drop_database` |
| `CREATE DATABASE` | カタログの `create_database` |

## 配置

1. `doris` データサービスを追加し、ポリシーを設定します。**データ権限 → エージェント** を開いてサービスを選び、エージェントトークンを発行します。トークンは一度しか表示されません。エージェントのアカウントだけが読めるファイルに保存してください。
2. エージェントの Doris ユーザーを作成します。ロールと行ポリシーを作り、ポリシーが与えるものを付与し、各ユーザーの権限と監査ログを読みます。`ADMIN_PRIV` がこれを許します。

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. アクセスを報告するには、Doris 2.1 のように監査ログが無効なら有効にします：`SET GLOBAL enable_audit_plugin = true`。`audit_plugin_max_batch_interval_sec` を既定の 60 秒より短くすると、アクセスを早く報告できます。
4. 例のアドレス、パス、名前を置き換えて設定ファイルを書きます。

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   これらのパスはエージェントのマシン上にあり、キャッシュディレクトリは書き込み可能でなければなりません。パスワードファイルは UTF-8 で読み、前後の空白を除きます。
5. 例えば systemd サービスとしてエージェントを起動します。

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   `<version>` を配布物のバージョンに置き換えてください。`java -Dgrantforge.…=… -jar …` で与えた設定はファイルより優先されます。エージェントは SIGTERM できれいに停止し、保留中のイベントを送信またはスプールします。Doris は与えられたものを保ちます。
6. **既存の権限をポリシーに移す：** GrantForge の外で付与された権限は、エージェントのロールと並んで効き続けます。対応するポリシーを作成してから、それらの権限を取り消してください。エージェントの警告が、ポリシーが拒否するものを与える権限を示します。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、このエージェントの名前、署名付きスナップショット、スプールしたイベント、監査ログの読み取り位置を置く書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードの X.509 Ed25519 公開鍵をコピーします。省略すると最初の接続時にサーバーから取得します |
| `grantforge.doris.hosts`、`grantforge.doris.user` | 必須：フロントエンドの MySQL プロトコルのアドレス（接続できないときは順に試し、ポートを書かなければ `9030`）と、エージェントの Doris ユーザー |
| `grantforge.doris.password.file` | ユーザーのパスワード。省略するとパスワードなしでサインインします |
| `grantforge.doris.ssl.mode`、`grantforge.doris.tls.ca` | TLS を提供するフロントエンド向けの `disable`（既定）、`trust`、`verify-ca`、`verify-full` と、それらを検証する PEM CA 証明書。空なら Java が信頼するものを信頼します |
| `grantforge.doris.timeout.ms` | 接続と 1 つのステートメントにかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |
| `grantforge.doris.role.prefix` | エージェントのロール名の先頭。既定 `grantforge_`。英字、数字、アンダースコアからなり、アンダースコアで終わり、最大 32 文字。名前がこれで始まるロールと行ポリシーはすべてエージェントのものです |
| `grantforge.doris.unmanaged.users` | エージェントが手を付けないユーザー。カンマ区切りで、例えばサービスアカウント。自分のユーザー、`root`、`admin`、`ADMIN_PRIV` または `NODE_PRIV` を持つユーザーには常に手を付けません |
| `grantforge.doris.catalogs` | エージェントがデータベース、テーブル、列ごとに付与するカタログ。カンマ区切りで、空ならすべて。それ以外は全体として付与できるものだけを得るため、テーブルの多い外部カタログを同期のたびに一覧にせずに済みます |
| `grantforge.sync.interval.ms` | エージェントが Doris を合わせる間隔。既定 `10000`、範囲 `1000..3600000` ミリ秒 |
| `grantforge.audit.log` | 既定 `true`。`false` は監査ログを読まず、アクセスを報告しません |
| `grantforge.audit.log.delay.ms`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.batch` | ログを現在よりどれだけ遅れて読むか（既定はクラスターのバッチ間隔に 5 秒を加えた値）、読む間隔（既定 `5000` ミリ秒）、1 回の読み取りの最大件数（既定 `1000`） |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを求める間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスク上のスプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同じ許可されたアクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ送ります |

## ユーザー、グループ、ロール

ポリシーは Doris ユーザーをそのユーザー名で指します。複数のホストを持つユーザー名は 1 人のユーザーで、エージェントのロールはそのすべての ID に付与されます。グループはユーザーが持つ Doris のロールと、GrantForge がユーザーを入れたグループです。ロールは GrantForge がユーザーに与えるロールです。

## 決定と制限

- スナップショットがなければ、エージェントは何も変更しません。使われていないサービスや、タイプが `doris` でないサービスに結び付いたトークンは何も付与しません。エージェントは自分のロールと行ポリシーを削除し、Doris 自身の権限が効きます。
- エージェントが動いていない間、Doris は最後に与えられたものを保ち、ポリシーの変更はエージェントを待ちます。変更は更新間隔と同期間隔のうちに Doris に届きます。
- エージェントがテーブルごとに付与するデータベースで作られたテーブル、データベースごとに付与するカタログで作られたデータベースは、次の同期で権限を得ます。
- Doris の行ポリシーは `root` と `admin` を縛れません。Doris が行ポリシーを 1 つも示さないユーザーはテーブルのすべての行を見ます。GrantForge の外で同じユーザーに作られた許容的な行ポリシーは、そのフィルターが見せる範囲を広げます。
- Doris はクエリがバックエンドで動くときにワークロードグループの `USAGE_PRIV` を確認します。定数やキャッシュされた結果のように、フロントエンドが自分で答えるクエリはこの確認を通りません。Doris はすべてのユーザーに `normal` グループを与えるため、誰でもそれを使えます。
- エージェントがロールを保つユーザーは Doris に存在しなければなりません。Doris に自分のユーザーを持たずに LDAP でサインインするユーザーはロールを得ません。
- Doris は Apache Ranger 経由でのみ値をマスクするため、ポリシーで値をマスクすることはできません。

## 関連ガイド

- [Apache Doris](/ja/plugins/doris/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
