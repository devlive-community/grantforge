---
title: Trino エージェント
description: Trino coordinator にシステムアクセス制御をインストールし、GrantForge ポリシーでクエリを検査し、ユーザーが使えるものだけを一覧に出し、行をフィルターしてカラムをマスクし、ユーザーのなりすましと他ユーザーのクエリをポリシーの下に置きます。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Trino エージェントは `grantforge` という名前のシステムアクセス制御です。coordinator はクエリが触れるすべてのカタログ、スキーマ、テーブル、カラム、ルーチン、セッションプロパティ、ユーザーについてエージェントに問い合わせ、エージェントは `trino` データサービスの署名済みポリシーから答えます。Trino 470〜478 は Java 23 または 24、Trino 479 以降は Java 25 で動作するため、配布物には依存関係を内部に再配置した JAR が 2 つあります。

| Trino | JAR |
| --- | --- |
| 470〜478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 以降 | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

リポジトリの Testcontainers 統合テストは、1 つ目の JAR を Trino `470` と `476` で、2 つ目を `479` と `484` で実行します。これは他のすべてのバージョンを認定するものではありません。サービスプラグインは [Trino](/ja/plugins/trino/) を参照してください。

## 仕組み

- **すべての検査は、それが指す各スコープで判定されます。** カタログ、スキーマ、テーブルの権限はその中に及ぶため、エージェントはカラムをそのカタログ、スキーマ、テーブル、カラム自身で判定します。どこかで拒否されれば拒否し、どこかで許可されれば許可します。
- **一覧から使えないものが省かれます。** `SHOW CATALOGS`、`SHOW SCHEMAS`、`SHOW TABLES`、`SHOW FUNCTIONS`、`information_schema`、ドライバーのメタデータは、ユーザーが使えるもの、またはその中で何かを使えるものだけを示します。
- **データを説明するものは開いたままです。** 各カタログの `information_schema`、JDBC ドライバーや BI ツールが読む `system` カタログの `jdbc` と `metadata` スキーマ、Trino の組み込み関数にはポリシーが不要で、その行は一覧と同じようにフィルターされます。
- **行をフィルターし、カラムをマスクします。** 行フィルターポリシーはテーブルを読むすべてのクエリに適用され、マスクはカラムの型に合わせられるので、マスクがクエリを壊すことはありません。テキストマスクは `varchar` と `char` カラムに適用されてカラムの型に戻され、年のマスクは日付とタイムスタンプに適用され、カスタム式はその型に変換され、テキストマスクが指すそれ以外のカラムはその型の `NULL` として読まれます。
- **なりすましと他人のクエリはポリシーに従います。** 別のユーザーとしてクエリを実行するにはそのユーザーへの `impersonate` が、他のユーザーのクエリを見たり終了したりするにはそのユーザーへの `view_query` または `kill_query` が必要です。誰でもクエリを実行でき、自分のクエリは見て終了できます。
- **拒否は理由を示します。** クライアントには `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`、または許可するポリシーがないこと、まだポリシーが届いていないことが返ります。
- **判定を保持し、アクセスをまとめます。** 現在のスナップショットの判定を既定で 100,000 件、10 秒間保持し、新しいスナップショットでは最初からやり直します。時間枠（既定 10 秒）内の同一の許可されたアクセスは回数付きの 1 件のアクセスイベントとして報告され、拒否は 1 件ずつ即座に報告されます。各イベントにはクエリが記録されます。
- **権限は GrantForge のものです。** ポリシーは GrantForge で管理するため、Trino の `GRANT`、`REVOKE`、`DENY` とロールの文は拒否されます。

| 文 | 必要なアクセス |
| --- | --- |
| `SELECT` | 読む各カラムの `select`。`count(*)` のようにカラムを読まないクエリはテーブルの `select` |
| `INSERT`、`DELETE`、`TRUNCATE` | `insert`。残り 2 つは `delete` |
| `UPDATE` | 書き込む各カラムの `update` |
| `CREATE SCHEMA`、`CREATE TABLE`、`CREATE VIEW`、`CREATE MATERIALIZED VIEW` | 作成対象の `create` |
| スキーマ、テーブル、ビュー、マテリアライズドビューの `DROP` | 対象の `drop` |
| テーブル、ビュー、カラムの `ALTER`、`COMMENT`、`SET PROPERTIES`、`SET AUTHORIZATION`、`ALTER TABLE ... EXECUTE` | 対象の `alter` |
| スキーマ、テーブル、ビュー、マテリアライズドビューの名前変更 | 対象の `alter` と新しい名前の `create` |
| `REFRESH MATERIALIZED VIEW` | 対象の `insert` |
| 所有者の権限で実行されるビュー | 所有者に、読むカラムの `select` と関数の `execute` が必要 |
| `SHOW SCHEMAS`、`SHOW TABLES`、`SHOW COLUMNS`、`SHOW FUNCTIONS`、`SHOW CREATE` | 対象またはその中への何らかのアクセス |
| `CREATE CATALOG`、`DROP CATALOG` | カタログの `create` または `drop` |
| 関数の呼び出し。`CREATE FUNCTION`、`DROP FUNCTION` | `execute`。関数の `create_function`、`drop_function` |
| `CALL` | プロシージャの `execute` |
| `SET SESSION` | カタログまたはシステムのセッションプロパティの `set` |
| 別ユーザーとしての実行、他人のクエリの表示・終了 | ユーザーへの `impersonate`、`view_query`、`kill_query` |
| システム情報の読み書き：Trino の REST API と Web UI のノード、統計、スレッドダンプ | `system_information` の `read` または `write` |

Trino 479 からは、2 つ目の JAR がテーブルのブランチも検査します。ブランチの一覧にはテーブルが見えることが、ブランチの作成・削除・fast-forward にはテーブルの `alter` が必要です。ビューの更新、マテリアライズドビューの所有者やコメントの変更には対象の `alter` が必要です。

## 配置

1. `trino` データサービスを追加してポリシーを設定します。**データ権限 → エージェント** を開き、サービスを選んでエージェントトークンを発行します。トークンは一度しか表示されないので、Trino のアカウントだけが読めるファイルに保存してください。
2. coordinator のバージョンに合う JAR を、Trino のプラグインディレクトリ内の専用ディレクトリ（例：`/usr/lib/trino/plugin/grantforge/`）にコピーします。アクセスを検査するのは coordinator だけで、worker には何も必要ありません。
3. coordinator に `etc/access-control.properties` を作成し、例のアドレス、パス、インスタンス名を置き換えます。

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   キャッシュディレクトリは Trino のアカウントが書き込める必要があります。GrantForge は Trino のファイルベースなどのアクセス制御と並行して動くのではなく、それを置き換えます。
4. coordinator を再起動します。エージェントが最初のスナップショットを得るまでは、データを説明するもの以外のクエリは拒否されます。
5. **既存のルールを先に移行してください：** Trino のファイルベースのアクセス制御のルールは適用されなくなります。事前に対応するポリシーを作成してください。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、この coordinator の名前、署名済みスナップショットと保留イベント用の書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショット署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると初回接続時にサーバーから取得 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続・読み取りタイムアウト、既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを問い合わせる間隔、既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、スプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同一の許可されたアクセスを 1 件にまとめる時間、既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ報告 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 保持する判定の最大数、既定 `100000`、最大 `10000000`、`0` は保持しない。保持時間、既定 `10000`、`1..3600000` ミリ秒 |

## ユーザー、グループ、ロール

ポリシーが指すのは、Trino が認証とユーザーマッピングの後に与えるクエリのセッションユーザーです。グループは Trino のグループプロバイダーがユーザーに与えるグループと、GrantForge がユーザーを入れたグループです。ロールは GrantForge がユーザーに与えたロールです。

## 決定と制限

- 使えるスナップショットがない間は、データを説明するもの、クエリの実行、自分のクエリを除いてすべて拒否されます。使用していないサービスでは、アクセス制御がない場合と同じく Trino は検査されませんが、Trino の権限とロールの文はそれでも拒否されます。
- タイプが `trino` でないサービスに結び付いたトークンは、すべてを拒否します。
- Trino は何よりも先に、ユーザーがそもそもカタログを使えるかを問い合わせます。カタログに何の権限もないユーザーには Trino 自身の `Cannot access catalog` が返ります。
- ユーザーが読めないカラムを読む `SELECT` は、最初のそのようなカラムを示して拒否されます。結果からカラムが除かれることはありません。
- Trino は、ユーザーに対してカラムをマスクしているテーブルには書き込みません。マスクが適用されている間、そのような `INSERT` は失敗します。
- マスクはカラムの型に変換されるため、長さに上限のある `varchar` ではマスクされた値のうち収まる分だけが表示されます。たとえば `varchar(3)` ではハッシュの 3 文字だけです。
- スナップショットが読めない行フィルターやマスクは、データの代わりに行を表示しないか、カラムを `NULL` として表示します。
- 判定は `grantforge.decision.cache.ttl.ms` の間保持されるため、ポリシーの有効期間はその分だけ遅れて始まったり終わったりします。

## 関連ガイド

- [Trino](/ja/plugins/trino/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
