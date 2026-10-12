---
title: MySQL プロキシエージェント
description: MySQL ネットワークプロキシを配置し、TLS とポリシー同期を設定して、列権限、マスキング、行フィルター、拒否エラーを確認します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MySQL エージェントは独立したネットワークプロキシです。クライアントはサーバーの代わりにプロキシへ接続し、プロキシは対応する文が MySQL に届く前に `mysql` データサービスの署名付きポリシーで確認します。MySQL サーバー内部へのインストールは不要です。配布物には実行可能な JAR `agents/mysql/grantforge-agent-mysql-<version>.jar` が含まれ、Java 17 以降が必要です。

リポジトリの Testcontainers 統合テストは `mysql:8.0` と `mysql:8.4` を対象に、MariaDB Connector/J と MySQL Connector/J のクライアントを使用します。ほかのサーバーバージョンや MySQL プロトコルの全機能を検証済みという意味ではありません。サービスプラグインのバージョンと接続要件は [MySQL](/ja/plugins/mysql/) を参照してください。

## 仕組み

- **サインインはそのまま通ります。** サーバーのグリーティングはクライアントへ、クライアントの応答はサーバーへ届くので、パスワードは MySQL 自身が確認します（`caching_sha2_password` と `mysql_native_password`）。プロキシは MySQL が受け入れたユーザーを知ります。ポリシーのユーザーはこのユーザー名で、ホスト部分は含みません。
- **SQL リソースを確認します。** プロキシは COM_QUERY とサーバー側プリペアドステートメントを解析し、対象のデータベース、テーブル、列に必要なアクセスタイプを確認します。拒否された文は MySQL に届かず、プロキシが 1142 や 1143 など MySQL と互換性のあるエラーコードを生成します。
- **読み取りはマスクされ、絞り込まれます。** 文が読むテーブルに、そのユーザー向けのマスク列や行フィルターがある場合、プロキシは同じ名前の派生テーブルに置き換えます。派生テーブルはマスク後の値とフィルター後の行だけを示すので、結合・条件・関数はすべてユーザーが見てよい内容を扱います。
- **リソースの認可イベント**をユーザー、クライアントアドレス、リソース、アクセスタイプ、判定、文とともに GrantForge へ送信します。監査範囲は後述します。
- MySQL の実行結果をクライアントへ中継します。プロキシがデータベースに格納された元の値を書き換えることはありません。

## 配置

1. `mysql` データサービスを追加してポリシーを設定します。**データ権限 → エージェント** でサービスを選択し、エージェントトークンを発行します。トークンは一度だけ表示されるため、プロキシの実行アカウントだけが読めるファイルに保存してください。
2. プロキシ用のメタデータユーザーを作成します。`information_schema` からテーブルの列とストアドファンクションを読み、データは読みません。`REFERENCES` ですべての列、`SHOW_ROUTINE` ですべてのストアドファンクションが見えます。ロール経由ではなくユーザーに直接付与してください。`SHOW_ROUTINE` がないとプロキシは起動しません。

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. 設定ファイルを書き、例のアドレス・パス・名前を置き換えます。

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   この例では、クライアントからプロキシ、プロキシから MySQL の両方で TLS を有効にします。`mysql-proxy.crt` はクライアントが使うプロキシのホスト名に、MySQL の証明書は `mysql.internal` に一致する必要があります。パスはプロキシのマシン上で解決され、キャッシュディレクトリには書き込み権限が必要です。パスワードファイルは UTF-8 で読み込んだ後に前後の空白を除去するため、前後の空白が必要なパスワードはこの方法で保存できません。

4. 例えば systemd サービスとしてプロキシを起動します。

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   `<version>` を配布物のバージョンに置き換えてください。`java -Dgrantforge.…=… -jar …` で指定した設定はファイルより優先されます。プロキシは SIGTERM で正常に停止し、未送信のイベントを送信または退避します。
5. クライアントの接続先をプロキシのアドレスとポートにします。以後 MySQL にはプロキシのアドレスが見えるため、アカウントのホスト部分でそれを許可する必要があります。
6. **プロキシを迂回する経路を閉じます。** MySQL に直接接続できるクライアントは確認されません。サーバーのポートにはプロキシと管理者だけが届くようにしてください。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、プロキシの名前、署名付きスナップショットと退避イベント用の書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | 任意。スナップショットの署名鍵を固定するファイル。エージェント画面から Base64 エンコードされた X.509 Ed25519 公開鍵をコピー。省略時は初回接続でサーバーから鍵を取得 |
| `grantforge.proxy.host`, `grantforge.proxy.port` | 待ち受けアドレス。既定はすべてのアドレスとポート `3307`。ポート範囲 `0..65535`、`0` は自動割り当て |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | クライアントに TLS を提供する PEM 証明書と秘密鍵。両方を同時に設定する必要あり |
| `grantforge.proxy.tls.required` | 既定値 `false`。`true` は TLS を使わないクライアントを拒否（エラー 3159）し、証明書と秘密鍵が必須 |
| `grantforge.mysql.host`, `grantforge.mysql.port` | MySQL ホストは必須。ポートの既定値 `3306`、範囲 `1..65535` |
| `grantforge.mysql.tls` | MySQL への TLS：`disabled`（既定）、`required`（暗号化するが検証しない）、`verify-ca`、`verify-identity` |
| `grantforge.mysql.tls.ca` | MySQL を検証する PEM CA。空の場合は Java が信頼する証明書を信頼 |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | メタデータユーザーは必須。パスワードファイルは任意で、省略時はドライバーにパスワードを渡さない |
| `grantforge.mysql.metadata.cache.seconds` | 列とストアドファンクションのキャッシュ有効期間。既定値 `30` 秒、範囲 `0..86400`。`0` はキャッシュの再利用を無効化 |
| `grantforge.mysql.connect.timeout.ms` | MySQL 接続タイムアウト。既定値 `10000` ミリ秒、範囲 `1..600000`。メタデータ読み取りタイムアウトは `30000` ミリ秒で固定 |
| `grantforge.proxy.check.threads` | 文を確認するスレッド数。既定値 `8`、範囲 `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 接続と読み取りのタイムアウト。既定値 `5000` / `8000` ミリ秒、範囲 `1..2147483647` |
| `grantforge.refresh.interval.ms` | 初回またはサーバーに接続できない場合の代替更新間隔。既定値 `30000` ミリ秒、最小 `1000`。通常のハートビートにはサーバーが返す間隔を使用 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | 既定のバッチ `500`、キュー `10000` イベント。バッチ範囲 `1..1000`、キューはバッチ以上で最大 `1000000` |
| `grantforge.audit.flush.interval.ms` | 監査送信間隔。既定値 `5000` ミリ秒、範囲 `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | 監査のディスク退避上限。既定値 `67108864`（64 MiB）、最小 `0` |

両側の TLS は独立しています。片側だけが TLS を使い、MySQL がパスワード全体を求める場合（キャッシュにない `caching_sha2_password`）、プロキシがパスワードを橋渡しします。平文のクライアントがサーバーの鍵を求めると独自の RSA 鍵で応じ、TLS のクライアントのパスワードは平文のサーバー向けに MySQL の鍵で暗号化します。プロキシ経由で接続するクライアントに MySQL の公開鍵ファイルを設定しないでください。

## 文に必要なアクセス

| 文 | アクセス |
| --- | --- |
| `SELECT`、サブクエリー、`WITH`、`EXPLAIN`、`SET @x = (SELECT …)` | 使うすべての列の `select`。`COUNT(*)` や `EXISTS` のように列を読まないテーブルの読み取りには、テーブル全体の `select` が必要です |
| `INSERT`、`LOAD DATA` | 書き込む列の `insert`（列を指定しない場合はすべての列）。`ON DUPLICATE KEY UPDATE` には `update` も必要です |
| `REPLACE` | 列の `insert` とテーブルの `delete` |
| `UPDATE` | 設定する列の `update` と読み取る列の `select` |
| `DELETE` | テーブルの `delete` と読み取る列の `select` |
| `CREATE TABLE`、`CREATE VIEW` / `DROP TABLE`、`DROP VIEW`、`TRUNCATE` / `ALTER TABLE`、`ALTER VIEW`、`OPTIMIZE` | テーブルまたはビューの `create` / `drop` / `alter` |
| `RENAME TABLE` | 旧名の `alter` と `drop`、新名の `create` と `insert` |
| `CREATE INDEX`、`DROP INDEX` | テーブルの `index` |
| `CREATE DATABASE`、`DROP DATABASE` | データベースの `create`、`drop` |
| `LOCK TABLES`、`ANALYZE`、`CHECK`、`CHECKSUM TABLE` | テーブルの `select` |

データを使わない文は MySQL 自身の権限に任せます：トランザクション、`SET`、`USE`、`SHOW`、`DESCRIBE`、`KILL`、および `GRANT` や `CREATE USER` などのアカウント文。`information_schema` と `performance_schema` もほかのデータベースと同じくポリシーが必要です。

プロキシは完全に確認できない文を拒否します。実行可能なコメント（`/*! … */`）、ストアドファンクションと `CALL`、`CREATE PROCEDURE`・`FUNCTION`・`TRIGGER`・`EVENT`、SQL レベルの `PREPARE`/`EXECUTE`、`HANDLER`、レプリケーションとクローンのコマンド、1 つのバッチでほかの文と一緒に送る `USE` や `SET NAMES`、解析できない文が該当します。

## マスキング、行フィルターとその制限

- マスクとフィルターは、文がそのテーブルを読むあらゆる場所に適用されます。サブクエリー、結合、`INSERT … SELECT`、`CREATE TABLE … SELECT`（コピーにはマスク後の値が入ります）も含みます。
- 単一テーブルの `UPDATE` と `DELETE` は、フィルターで見える行だけを変更します。フィルターのあるテーブルに対する複数テーブルの `UPDATE` や `DELETE` は拒否されます。変更するテーブルのマスク列を使う書き込みも、隠された値をコピーしたり探ったりできるため拒否されます。
- マスクや行フィルターのあるテーブルに対するビューは作成・変更できません。MySQL は後でプロキシを通さずにビューを実行するからです。
- ポリシーバージョンが変わらなければ、プリペアドステートメントは既存の確認と書き換えを再利用します。バージョンが変わると再確認し、引き続きアクセスが許可され、書き換え後のテキストも同じなら実行を続けます。それ以外はエラー 1615 となり、クライアントで準備し直す必要があります。
- テーブル名を付けずに書いた列は、その列が見える範囲で同じ名前の列を持つすべてのテーブルに対して数えられます。同名の制限された列による拒否を避けるには、列をテーブルで修飾してください。
- `*` で読む列はメタデータユーザーが見る `information_schema` に基づき、`grantforge.mysql.metadata.cache.seconds` の間キャッシュされます。通常の SQL コマンドとしてプロキシ経由で送った DDL が成功するとキャッシュを無効化し、次の参照で読み直します。外部または準備済みの DDL は実行時のキャッシュ無効化を保証しないため、有効期間の終了を待つかプロキシを再起動してください。

## 適用範囲

プロキシはクライアント SQL から解析できるリソースを確認します。既存のビュー、トリガー、その他のサーバー内部で実行される操作を再帰的に展開することはありません。基になるテーブルを保護するには、MySQL 本来の権限と既存のビューやトリガーの使用も制限してください。業務ユーザーに管理者権限を付与して、プロキシが管理用の文を遮断すると期待しないでください。

行フィルターは汎用の書き込み制約ではありません。`INSERT`、`REPLACE`、`LOAD DATA` は書き込み権限を確認しますが、新しい行がフィルター式を満たすことを自動的に要求しません。`INSERT … ON DUPLICATE KEY UPDATE` が既存行を更新する分岐にも、行フィルター条件は追加されません。プロトコル圧縮、ZSTD、クエリー属性、任意の結果メタデータはハンドシェイク時に無効になります。

## ポリシーと障害

- 使用できるスナップショットがない場合、またはトークンが `mysql` 以外のサービスに結び付いている場合、データにアクセスする文をすべて拒否します。
- GrantForge に一時的に接続できない場合や、新しいスナップショットの署名が無効な場合は、適用済みのスナップショットを保持します。初回起動で使用できるスナップショットがまったくない場合は、リソースアクセスを拒否します。
- サービスを無効にすると、GrantForge のリソース認可、マスキング、行フィルターを省略します。SQL 解析とプロトコル制限は引き続き適用されます。サービスの無効化はデータアクセスの遮断ではありません。
- プロキシの確認の後も MySQL の権限は適用されます。プロキシは権限を狭めることしかできません。
- `COM_CHANGE_USER` は追従しますが、クライアント側とサーバー側で TLS が異なる場合は拒否します。別のユーザーとして接続し直してください。

## 検証例

独立したテストデータベースで、データベース管理者に次のデータとアカウントを作成してもらいます。`proxy-host` は MySQL が実際に認識するプロキシの送信元アドレスに置き換えてください。

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

`alice` 用に 3 つの GrantForge ポリシーを作成します。`database=grantforge_demo`、`table=orders`、`column=*` の `select` を許可し、`mask_show_last_4` で `card` をマスクし、`region = 'EU'` でテーブルを絞り込みます。サービスと 3 つのポリシーをすべて有効にし、**データ権限 → エージェント** に最新のポリシーバージョンが適用されたと表示されるまで待ちます。

MySQL コマンドラインクライアントでプロキシに接続します。`-p` はパスワード入力を要求し、CA ファイルはプロキシの証明書を信頼する必要があります。

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- 1 行のみ：1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000：GrantForge は update を許可していない
```

この例では MySQL 本来の権限で `UPDATE` が許可されているため、拒否によりプロキシがポリシーを適用していることを確認できます。次に行フィルターを `region = 'US'` に変更し、プロキシが新バージョンを適用するまで待って再度問い合わせます。カードが `xxxxxxxxxxxx4444` の 2 行目だけが表示されるはずです。

## トラブルシューティングと監査

| エラーコード / SQLSTATE | 意味と確認事項 |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | データベース、テーブル、列のアクセス拒否。メッセージに `by GrantForge` があればポリシーのユーザー、リソース、アクセスタイプ、スナップショットバージョンを確認。それ以外の拒否では MySQL 本来の権限も確認 |
| `1046 / 3D000` | データベース未選択。`USE`、接続の既定データベース、または完全修飾テーブル名を使用 |
| `1227 / 42000` | 禁止された操作、または安全に確認できない操作。メッセージに従い文の制限とメタデータ権限を確認 |
| `1064 / 42000` | SQL を解析できない |
| `1615 / HY000` | ポリシー変更後にステートメントを準備し直す |
| `3159 / HY000` | プロキシが TLS を要求しているがクライアントは TLS を使っていない |
| `1043 / 08S01` | バックエンドのプロトコルまたは TLS ハンドシェイクの失敗。TLS モード、証明書、ホスト名、サーバーの機能を確認 |
| `2003 / HY000` | プロキシが MySQL に接続できない。アドレス、ネットワーク、バックエンドの状態を確認 |

**データ権限 → アクセス監査** で `alice` と該当リソースの許可・拒否イベントを確認します。1 つの SQL 文から複数のリソース認可イベントが生成される場合があります。記録はバックエンド実行前なので、`ALLOWED` は MySQL の実行成功を意味しません。解析失敗、リソースにアクセスしない文、無効なサービスではリソースイベントが生成されない場合があります。同じポリシーバージョンでプリペアドステートメントを繰り返し実行しても、認可イベントは繰り返されません。これはリソース認可の監査であり、完全な SQL 実行ログではありません。

## 関連ガイド

- [MySQL](/ja/plugins/mysql/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
