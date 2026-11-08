---
title: MongoDB エージェント
description: デプロイメントの隣で MongoDB エージェントを実行し、GrantForge のポリシーを MongoDB 自身のロールにし、ロールの権限を一度に置き換え、GrantForge の外の組み込みロールとカスタムロールが何を与えるかを読み取り、アクセスを監査ログまたはプロファイラーから報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MongoDB エージェントは、デプロイメントの隣で動くサービスです。MongoDB 自身のアクセス制御を `mongodb` データサービスの署名済みポリシーに一致させます。ユーザーごとに 1 つのロールが、ポリシーがそのユーザーに与えるものだけを正確に持ちます。あとは MongoDB がすべてのコマンドを自分で確認し、エージェントはどのクライアントの前にも入りません。配布物は実行可能 JAR `agents/mongodb/grantforge-agent-mongodb-<version>.jar` として提供し、Java 17 以降が必要です。

リポジトリの Testcontainers 統合テストは、エージェントと配布される JAR を MongoDB `6.0`、`7.0`、`8.0`、`9.0` のレプリカセットと `9.0` のシャードクラスターで実行し、Percona Server for MongoDB `7.0`、`8.0` の監査ログを読みます。これは他のすべてのバージョンを認定するものではなく、エージェントは `6.0` より古いデプロイメントを拒否します。サービスプラグインは [MongoDB](/ja/plugins/mongodb/) を参照してください。

## 仕組み

- **ユーザーごとに 1 つのロールが GrantForge の与えるものを持ちます。** ポリシーが何かを与える各ユーザーについて、エージェントは `admin` にその名前のロール、例えば `alice` なら `grantforge_alice` を維持し、`alice@admin` や `alice@sales` のようにその名前のすべての MongoDB ユーザーに付与します。`rolesInfo` で GrantForge が `alice` に与えるものが分かります。エージェントは他のロールを変更しません。
- **変更は一度に行われます。** `updateRole` はロールの権限を一度に置き換えるため、ユーザーが新旧の権限を同時に持つことも、どちらも持たないこともありません。
- **下で別の決定がなければデータベース全体に付与します。** ポリシーがデータベース全体で許可し、下でどのポリシーも取り上げられないアクセスタイプはデータベースに付与され、後から作られたコレクションもすぐに持ちます。それ以外はコレクションごとに付与され、新しいコレクションは次の同期で対象になります。
- **ポリシーが許す以上は与えません。** MongoDB のデータベース削除権限は、その中のすべてのコレクションを削除する権限です。エージェントはポリシーがそれも与える場合にだけ付与します。そうでなければ保留し、どのユーザーが何をなぜできないかを 1 度ログに残します。
- **現時点のポリシー。** 各同期はその時点で適用されるポリシーと、MongoDB にあるユーザー、データベース、コレクションから計画するため、有効期間は時間どおりに始まって終わり、新しいものは次の同期で対象になります。
- **MongoDB のロールがグループです。** グループのポリシーは、その名前の MongoDB ロールを持つユーザーと、GrantForge がユーザーを入れたグループに適用されます。
- **手作業の変更は元に戻されます。** 同期のたびにエージェントは自分のロールを計画と比べ、手作業で変えられた権限、継承させられたロール、他のユーザーへのロールの付与、誰にも必要のないロールを正します。何も変わっていなければ何も送りません。
- **GrantForge の外のロールが何を与えるかを読み取ります。** MongoDB はユーザーのすべてのロールが与えるものを足し合わせるため、エージェントはユーザーが別のロールで持つものを取り上げられません。MongoDB は組み込みや継承のロールも含めて各ロールが何を与えるかを示すため、エージェントは `readWrite@hr` のようにポリシーが拒否するものを与えるロールを、ユーザー、アクセス、ポリシーを挙げて 1 度ずつ警告します。ユーザーを管理できて自分に何でも与えられるユーザー、`root` またはすべてのリソースのすべてのアクションを持つユーザー、認可をまったく確認しないサーバーも警告します。
- **アクセスは監査ログまたはプロファイラーから得ます。** 下のアクセスの報告を参照してください。各イベントは、GrantForge が決定したか、つまりポリシーが MongoDB と同じ決定をするか、外のロールがコマンドを許可したかを示します。集計は、`$lookup`、`$graphLookup`、`$unionWith` ステージが読み `$out`、`$merge` ステージが書く各コレクションについて報告されます。イベントはコマンドと名前空間だけを示し、ドキュメントやパスワードを含みうる引数は決して示しません。

## 配置

1. `mongodb` データサービスを追加し、ポリシーを設定します。**データ権限 → エージェント** を開いてサービスを選び、エージェントトークンを発行します。トークンは一度しか表示されません。エージェントのアカウントだけが読めるファイルに保存してください。
2. エージェントの MongoDB ユーザーを作成します。`userAdminAnyDatabase` でロールを作成、変更し、ユーザーとロールを読みます。それだけを持つロールでコレクションを一覧にし、`clusterMonitor` でサーバーのオプションとプロファイラーを読みます。`userAdminAnyDatabase` は自分に何でも与えられるので、このユーザーは管理者と同じように守ってください。一覧用ロールの名前は、エージェントのロール接頭辞で始めないでください。

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. アクセスを報告するには、下のアクセスの報告にあるソースのいずれかを設定します。
4. 例のアドレス、パス、名前を置き換えて設定ファイルを書きます。

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   これらのパスはエージェントのマシン上にあり、キャッシュディレクトリは書き込み可能でなければなりません。パスワードファイルは UTF-8 で読み、前後の空白を除きます。
5. 例えば systemd サービスとしてエージェントを起動します。

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   `<version>` を配布物のバージョンに置き換えてください。`java -Dgrantforge.…=… -jar …` で与えた設定はファイルより優先されます。エージェントは SIGTERM できれいに停止し、保留中のイベントを送信またはスプールします。MongoDB は与えられたものを保ちます。
6. **既存の権限をポリシーに移す：** GrantForge の外で付与されたロールは、エージェントのロールと並んで適用され続けます。対応するポリシーを作成してから、それらのロールを取り消してください。エージェントの警告が、ポリシーが拒否するものを与えるロールを示します。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、このエージェントの名前、署名付きスナップショット、スプールしたイベント、監査ログの読み取り位置を置く書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードの X.509 Ed25519 公開鍵をコピーします。省略すると最初の接続時にサーバーから取得します |
| `grantforge.mongodb.uri` | 必須：認証情報を含まない接続文字列 |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256`（既定）、`SCRAM-SHA-1`、`MONGODB-X509`、`PLAIN`、`GSSAPI` |
| `grantforge.mongodb.user`、`grantforge.mongodb.password.file`、`grantforge.mongodb.auth.source` | エージェントのユーザー。`GSSAPI` ではプリンシパル、`MONGODB-X509` では省略可。そのパスワード。SCRAM ではユーザーが定義されたデータベース、既定は `admin` |
| `grantforge.mongodb.tls.ca.file`、`grantforge.mongodb.tls.certificate.key.file`、`grantforge.mongodb.tls.certificate.key.password.file` | 接続文字列に `tls=true` があるとき：サーバーを検証する PEM CA 証明書、空なら Java が信頼するもの。クライアント証明書と鍵を 1 つにした PEM ファイル。暗号化された鍵のパスワード |
| `grantforge.mongodb.kerberos.keytab`、`grantforge.mongodb.kerberos.service.name` | `GSSAPI`：プリンシパルの keytab、なければパスワードを使います。サーバーのプリンシパルのサービス名、既定は `mongodb`。レルムはマシンの `krb5.conf` から取ります |
| `grantforge.mongodb.timeout.ms` | サーバーの検索、接続、1 つのコマンドにかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |
| `grantforge.mongodb.role.prefix` | エージェントのロール名の先頭。既定は `grantforge_`。英字、数字、アンダースコアからなり、アンダースコアで終わり、最大 32 文字。`admin` でこれで始まる名前のロールはすべてエージェントのものです |
| `grantforge.mongodb.unmanaged.users` | エージェントが触らないユーザー。カンマ区切り。例：サービスアカウント。自分のユーザーと、`root`、`__system`、すべてのリソースのすべてのアクションを持つユーザーには常に触りません |
| `grantforge.mongodb.databases` | エージェントがコレクションごとに付与するデータベース。カンマ区切り、空ならすべて。それ以外は全体に付与できるものだけを受け取るため、コレクションが非常に多いデータベースを同期のたびに一覧にせずに済みます |
| `grantforge.mongodb.audit.files` | 読み取る監査ログファイル。カンマ区切り。空ならプロファイラーを読みます |
| `grantforge.sync.interval.ms` | エージェントが MongoDB を合わせる間隔。既定 `10000`、範囲 `1000..3600000` ミリ秒 |
| `grantforge.audit.log` | 既定は `true`。`false` ならアクセスを読まず報告しません |
| `grantforge.audit.log.delay.ms`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.batch` | プロファイラーを現在からどれだけ遅れて読むか（既定 `2000`）、アクセスを読む間隔（既定 `5000` ミリ秒）、ファイルまたはデータベースから 1 回に読む最大件数（既定 `1000`） |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを求める間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスク上のスプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同じ許可されたアクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ送ります |

## アクセスの報告

| ソース | 対象 | 分かること | 有効にする方法 |
| --- | --- | --- | --- |
| 監査ログ | MongoDB Enterprise と Percona Server for MongoDB | エージェントがファイルを読める各メンバーで、何が許可され何が拒否されたか | `mongod` を `--auditDestination file --auditFormat JSON --auditPath <path>` と `--setParameter auditAuthorizationSuccess=true` で起動し、`grantforge.mongodb.audit.files` にファイルを指定します |
| プロファイラー | すべてのエディション | エージェントが通信するメンバー、つまりレプリカセットのプライマリで何が実行されたか。拒否は分かりません | 報告する各データベースで `db.setProfilingLevel(2)`。サーバーに負荷がかかるためエージェントは決して自分で有効にせず、`system.profile` は小さな固定サイズコレクションです |

監査ログはファイルから読むため、エージェントをノードで動かすか、ファイルを集めたディレクトリを与えてください。テストは MongoDB Enterprise と同じ形式の Percona Server の監査ログを読みます。`mongos` にはプロファイラーがないため、シャードクラスターは監査ログからのみアクセスを報告します。

| コマンド | アクセスイベント |
| --- | --- |
| `find`、`aggregate`、`count`、`distinct`、`listIndexes` | 読まれた各コレクションの `read` |
| `$changeStream` で始まる `aggregate` | `change_stream` |
| `insert`、`update`、`delete`、`findAndModify`、`bulkWrite` | 書かれた各コレクションの `insert`、`update`、`remove`。求められれば `bypass_validation` も |
| `createIndexes`、`dropIndexes` | `create_index`、`drop_index` |
| `collMod`、`validate`、`convertToCapped` | `alter` |
| `drop`、`create`、`dropDatabase` | `drop`、`create`、`drop_database` |
| `listDatabases`；`serverStatus`、`top`、`replSetGetStatus`、`connPoolStats`、`hostInfo`、`currentOp` | `list_databases`；`monitor` |

MongoDB はコマンド全体を拒否し、どのコレクションが理由かを示さないため、拒否はポリシーが拒否するコレクションについて、拒否するものがなければ最初のコレクションについて報告されます。

## ユーザー、グループ、ロール

ポリシーは MongoDB ユーザーをユーザー名で指定します。複数のデータベースで定義された名前は 1 人のユーザーで、エージェントのロールはそのすべてに付与されます。グループは、ユーザーが持つ MongoDB ロールと GrantForge がユーザーを入れたグループです。ロールは GrantForge がユーザーに与えるロールです。

## 決定と制限

- スナップショットがなければエージェントは何も変更しません。使われていないサービス、またはタイプが `mongodb` ではないサービスに結び付いたトークンは何も付与しません。エージェントは自分のロールを削除し、MongoDB 自身のロールは残ります。
- エージェントが動いていない間、MongoDB は最後に受け取ったものを保持し、ポリシーの変更は待ちます。変更は更新と同期の間隔内に MongoDB に届き、`mongos` はユーザーキャッシュの期限が切れると、既定で 30 秒以内に適用します（`userCacheInvalidationIntervalSecs`）。
- MongoDB の権限はデータベースとコレクションを正確に指定するため、ポリシーのワイルドカードは存在するものについて付与されます。データベースはコレクションができて初めて存在します。それまでは、ポリシーが指すデータベースの `create` には付与する対象がありません。
- LDAP 認可が自分でロールに対応付けるユーザーのように MongoDB のどこにも定義されていないユーザーは見えず、エージェントはロールを維持しません。
- データベース全体への権限はそのコレクションを含みますが `system.` コレクションは含まず、MongoDB 自身のデータベースは MongoDB に任せます。
- コレクションの名前変更や MongoDB の他の管理アクションはアクセスタイプではなく、GrantForge の外のロールに残ります。
- MongoDB はロールごとに行をフィルターしたりフィールドをマスクしたりしません。[ビュー](/ja/plugins/mongodb/) が制限付きの読み取りを提供します。

## 関連ガイド

- [MongoDB](/ja/plugins/mongodb/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
