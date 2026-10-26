---
title: PostgreSQL エージェント
description: サーバーの隣で PostgreSQL エージェントを動かし、GrantForge のポリシーを列と行の単位まで PostgreSQL 自身のロール、権限、行セキュリティポリシーにし、データベースごとに 1 つのトランザクションで変更して、サーバーログからアクセスを報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

PostgreSQL エージェントはサーバーの隣で動くサービスです。PostgreSQL 自身のアクセス制御を `postgresql` データサービスの署名付きポリシーと一致させます。ユーザーごとに、エージェントが管理する各データベースでポリシーがそのユーザーに与えるものだけをちょうど持つロールを 1 つ、行フィルターごとに行セキュリティポリシーを 1 つ保ちます。そのうえで PostgreSQL 自身がすべての文を検査し、エージェントがクライアントとサーバーの間に入ることはありません。配布物は実行可能な JAR `agents/postgresql/grantforge-agent-postgresql-<version>.jar` として提供し、Java 17 以降が必要です。

リポジトリの Testcontainers 統合テストは、エージェントと配布されるとおりの JAR を、pgaudit を入れた PostgreSQL `15`、`16`、`17`、`18` に対して実行します。これは他のすべてのバージョンを認定するものではありません。エージェントは `15` より古いサーバーを拒否します。サービスプラグインは [PostgreSQL](/ja/plugins/postgresql/) を参照してください。

## 仕組み

- **ユーザーごとのロールが GrantForge の与えるものを持ちます。** ポリシーが何かを与えるユーザーごとに、エージェントはその名前のロール（`alice` なら `grantforge_alice`）を保ち、ユーザーを `SET ROLE` なしでロールの権限を持つメンバーにします。エージェントは自分のロールすべてにコメントを書き、コメントのないロールを自分のものとは見なさないため、他のロールや自分が付与していない権限は変更しません。
- **データベースごとに 1 つのトランザクションで変更します。** エージェントは管理する各データベースにサインインし、その中で 1 つのトランザクションとして変更します。そのため、ユーザーに見えるのは、あるポリシーバージョンがデータベースに丸ごと適用された状態か、まったく適用されていない状態だけです。
- **下位で別の判定がなければテーブル全体に付与します。** ポリシーがテーブル全体に許可し、ある列からそれを取り上げうるポリシーがないアクセスタイプは、テーブルに付与されます。そのため、後から追加した列もすぐにそれを持ちます。そうでなければ、読み取り、挿入、更新は列ごとに付与されます。データベースの `CONNECT`、スキーマの `USAGE`、ユーザーが挿入できる serial 列のシーケンスの `USAGE` が一緒に付与されます。
- **行フィルターは絞り込むだけです。** 行フィルターは、テーブルに対するユーザーのロールの `SELECT` 用の制限型（restrictive）行セキュリティポリシーです。行セキュリティがオフのテーブルでは、エージェントはそれをオンにし、全員にすべての行を見せる許可型ポリシーを接頭辞の名前（`grantforge`）で同時に作るため、他の人には何も変わりません。テーブルにフィルターも独自のポリシーも残らなくなると、行セキュリティを再びオフにします。所有者がオンにした行セキュリティは所有者のもののままで、エージェントのポリシーはそれが見せるものを絞り込むだけです。PostgreSQL が拒否するフィルター、ポリシーの対象を変えてしまうフィルター、または複数の文からなるテキストは、ユーザーに行を一切見せないポリシーに置き換えられ、ログに記録されます。
- **現時点のポリシー。** 各同期はその時点で適用されるポリシーで計画するため、有効期間は時刻どおりに始まり終わります。また PostgreSQL にあるユーザー、スキーマ、テーブル、列で計画するため、新しいものは次の同期で扱われます。
- **ロールはグループです。** グループ向けのポリシーは、直接または他のロールを通じてその名前の PostgreSQL ロールのメンバーであるユーザーに、GrantForge がそのユーザーを入れたグループと合わせて適用されます。
- **手動の変更は元に戻されます。** エージェントは同期のたびに、自分のロールの権限を PostgreSQL が示すものと、自分の行セキュリティポリシーを最後に作成したものと比べます。違う部分は次の同期で復元されます。何も変わっていなければ、エージェントは何も送りません。
- **GrantForge の外の権限を報告します。** PostgreSQL は権限を足し合わせるため、エージェントはユーザーが自分自身で、他のロールを通じて、または PUBLIC を通じて持つものも、自分が所有するテーブルでできることも取り上げられません。ポリシーが拒否するものを与えるそうした権限ごとに一度警告し、ユーザー、権限、ユーザーがそれをどう持っているか、ポリシーを示します。
- **アクセスはサーバーログから得ます。** エージェントは PostgreSQL の JSON 形式のサーバーログを読みます。PostgreSQL が許可したものは pgaudit のセッションログから、文が読み書きしたテーブルごとに 1 つのイベントとして、権限がなくて PostgreSQL が拒否したものは、エラーが示す対象について 1 つの拒否イベントとして報告します。各イベントは、GrantForge が決めたのか、つまりポリシーが PostgreSQL と同じ判定をするのか、それとも GrantForge の外の権限が許可したのかを示します。

| 文 | アクセスイベント |
| --- | --- |
| `SELECT`、`COPY … TO`、他の文の読み取り部分 | 読み取った各テーブルの `select` |
| `INSERT`、`COPY … FROM` | 書き込んだテーブルの `insert` |
| `UPDATE`、`MERGE`、`SELECT … FOR UPDATE` | テーブルの `update` |
| `DELETE`、`TRUNCATE` | テーブルの `delete`、`truncate` |
| `CREATE TRIGGER` | テーブルの `trigger` |
| `CREATE TABLE`、`CREATE VIEW` とその他のオブジェクト | スキーマの `create` |
| `CREATE SCHEMA`、`CREATE TEMPORARY TABLE` | データベースの `create_schema`、`temporary` |
| 拒否された接続、`USAGE` がなくて拒否されたスキーマ | データベースの `connect`、スキーマの `usage` |

## 配置

1. `postgresql` データサービスを追加してポリシーを設定します。**データ権限 → エージェント** を開き、サービスを選んでエージェントトークンを発行します。トークンは一度しか表示されないため、エージェントの実行アカウントだけが読めるファイルに保存してください。
2. エージェントの PostgreSQL ユーザーをスーパーユーザーとして作成します。このユーザーは自分が所有しないテーブルに権限を付与し、行セキュリティをオン・オフし、ロールを保ち、サーバーログを読みます。

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   テストはエージェントをスーパーユーザーとして実行します。スーパーユーザーは権限にも行セキュリティにも縛られないため、エージェントは彼らのロールを保ちません。
3. アクセスを報告するには、PostgreSQL がサーバーログを JSON で書き、pgaudit（PGDG の `postgresql-17-pgaudit` などのパッケージ、またはプラットフォームのもの）を読み込むよう `postgresql.conf` を設定し、サーバーを再起動します。

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   pgaudit がなければ、エージェントは PostgreSQL が拒否したものだけを報告し、許可したものは報告しません。JSON ログがなければ何も報告せず、そのことを一度だけ伝えます。
4. 例のアドレス、パス、名前を置き換えて設定ファイルを書きます。

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   これらのパスはエージェントのマシン上のもので、キャッシュディレクトリは書き込み可能でなければなりません。パスワードファイルは UTF-8 で読み、前後の空白を取り除きます。
5. 例えば systemd サービスとしてエージェントを起動します。

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   `<version>` を配布物のバージョンに置き換えてください。`java -Dgrantforge.…=… -jar …` で与えた設定はファイルより優先されます。エージェントは SIGTERM できれいに停止し、保留中のイベントを送るか退避します。PostgreSQL は受け取ったものを保持します。
6. **既存の権限をポリシーに移してください。** GrantForge の外で付与された権限は、エージェントのロールと並んで適用され続けます。対応するポリシーを作成してから権限を取り消してください。エージェントの警告は、ポリシーが拒否するものを与える権限を示します。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、このエージェントの名前、署名付きスナップショット・退避したイベント・サーバーログの読み取り位置のための書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショット署名鍵を固定する任意のファイル。エージェントページから Base64 でエンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると、最初の接続時にサーバーから鍵を取得します |
| `grantforge.postgresql.hosts`、`grantforge.postgresql.user` | 必須：`host:port` 形式のサーバー（接続できるサーバーがプライマリになるまで順に試します）と、エージェントの PostgreSQL ユーザー |
| `grantforge.postgresql.database` | ロール、データベース、サーバーログを読むためにエージェントが最初にサインインするデータベース。既定は `postgres` |
| `grantforge.postgresql.password.file` | ユーザーのパスワード。省略するとエージェントはパスワードなしでサインインします |
| `grantforge.postgresql.ssl.mode` | libpq の `sslmode` が呼ぶ TLS の方式。既定は `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca と verify-full：サーバーを検証する PEM 形式の CA 証明書。空なら Java が信頼するものを信頼します |
| `grantforge.postgresql.tls.cert`、`grantforge.postgresql.tls.key` | パスワードの代わりに証明書（`cert` 認証）でサインインするための PEM 形式のクライアント証明書とその PKCS #8 秘密鍵（両方を設定）。`require` か検証方式が必要で、検証方式と併用するには `grantforge.postgresql.tls.ca` も必要です |
| `grantforge.postgresql.timeout.ms` | 接続と文にかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |
| `grantforge.postgresql.databases` | エージェントが管理するデータベース（カンマ区切り）。空なら接続を受け付けるすべてのデータベースを管理します |
| `grantforge.postgresql.role.prefix` | エージェントのロール名の先頭。既定は `grantforge_`。英字・数字・アンダースコアからなり、アンダースコアで終わり、最大 32 文字で、`pg_` で始まらないこと。名前がこれで始まりエージェントが印を付けたロールと、それらのロール名または末尾のアンダースコアを除いた接頭辞の名前を持つ行セキュリティポリシーは、エージェントのものです |
| `grantforge.postgresql.unmanaged.users` | エージェントが手を付けないユーザー（カンマ区切り）。例：管理者。エージェントは自分自身のユーザーとスーパーユーザーには常に手を付けません |
| `grantforge.sync.interval.ms` | エージェントが PostgreSQL を一致させる間隔。既定 `10000`、範囲 `1000..3600000` ミリ秒 |
| `grantforge.audit.server.log` | 既定は `true`。`false` ならサーバーログを読まず、アクセスも報告しません |
| `grantforge.audit.server.log.interval.ms`、`grantforge.audit.server.log.max.bytes` | ログを読む間隔（既定 `5000` ミリ秒）と、1 回の読み取りの最大バイト数（既定 16 MiB）。たまったログは複数回に分けて読みます |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを問い合わせる間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスク上の退避上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同一の許可されたアクセスを 1 つのイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` なら 1 件ずつ送ります |

## ユーザー、グループ、ロール

ポリシーは PostgreSQL のユーザー、つまりサインインできるロールを指定します。グループは、ユーザーが直接または他のロールを通じてメンバーであるロールと、GrantForge がユーザーを入れたグループです。ロールは GrantForge がユーザーに与えるロールです。

## 決定と制限

- スナップショットがなければ、エージェントは何も変更しません。使用中でないサービス、またはタイプが `postgresql` でないサービスに結び付いたトークンは何も付与しません。エージェントは自分のロールと行セキュリティポリシーを削除し、PostgreSQL 自身の権限が適用されます。
- エージェントが動いていない間、PostgreSQL は最後に受け取ったものを保持し、ポリシーの変更はエージェントを待ちます。変更は更新間隔と同期間隔のうちに PostgreSQL に届きます。
- 同期の後に作成したテーブルは、次の同期で権限を受け取ります。
- スーパーユーザーとテーブルの所有者は、そのテーブルで何でもできます。スーパーユーザー、`BYPASSRLS` を持つロール、テーブルの所有者は、所有者が強制しない限り、そのテーブルの行セキュリティに縛られません。
- ビューは `security_invoker` でない限り、所有者の権限でテーブルを読みます。サーバーログは、ビューが読んだテーブルを GrantForge の外の権限が許可したものとして報告します。
- パーティションを直接読むと、そのパーティションテーブルの行セキュリティでは絞り込まれません。
- pgaudit は PostgreSQL が文を実行する前に記録するため、シーケンスが拒否された挿入のように後で失敗する文は許可として、その拒否は拒否として報告されます。pgaudit が記録しないセッションの文は報告されません。
- PostgreSQL の拒否はスキーマなしでテーブル名だけを示します。イベントは、文が修飾したテーブル、その名前を持つ唯一のテーブル、または `public` のテーブルを示します。スキーマなしで作成されたオブジェクトは `public` として報告されます。
- エージェントは自分がサインインするサーバー、つまりプライマリのサーバーログを読み、レプリカのログは読みません。
- PostgreSQL には列のマスキングがないため、ポリシーは値をマスクできません。

## 関連ガイド

- [PostgreSQL](/ja/plugins/postgresql/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
