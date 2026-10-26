---
title: OpenSearch エージェント
description: クラスターの隣で OpenSearch エージェントを動かし、GrantForge のポリシーをフィールドレベルセキュリティ、マスクフィールド、ドキュメントレベルセキュリティを備えたセキュリティプラグイン自身のロールにし、インデックスパターンをパターンのまま保って新しいインデックスにすぐ届くようにし、監査ログからアクセスを報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

OpenSearch エージェントは、クラスターの隣で動くサービスです。セキュリティプラグイン自身のアクセス制御を `opensearch` データサービスの署名付きポリシーに合わせ続けます。ユーザーごとに、ポリシーがそのユーザーに与えるものだけをちょうど持つロールを 1 つ、そのユーザーだけにマッピングし、フィールドレベルセキュリティ、マスクフィールド、ドキュメントレベルセキュリティを含めます。その後は OpenSearch がすべてのリクエストを自ら確認し、エージェントがクライアントとクラスターの間に入ることはありません。配布物は実行可能な JAR `agents/opensearch/grantforge-agent-opensearch-<version>.jar` として提供し、Java 17 以降が必要です。

リポジトリの Testcontainers 統合テストは、エージェントと配布される JAR を、セキュリティプラグインを備えた OpenSearch `2.19`、`3.3`、`3.6`、`3.9` で動かします。これは他のすべてのバージョンを認定するものではありません。エージェントは `2.19` より古いクラスターとセキュリティプラグインのないクラスターを拒否します。サービスプラグインについては [OpenSearch](/ja/plugins/opensearch/) を参照してください。

## 仕組み

- **ユーザーごとに 1 つのロールが GrantForge の与えるものを持ちます。** ポリシーが何かを与える各ユーザーについて、エージェントはその名前のロール（`alice` なら `grantforge_alice`）を保ち、そのユーザーだけにマッピングします。説明がエージェントのものであることを示し、エージェントはこの印のないロールを自分のものとみなさないため、他のロールやマッピングは変更しません。
- **インデックスパターンはパターンのままです。** `logs-*` のようなポリシーのインデックスパターンは、その範囲全体ですべてのポリシーが同じ決定をする場合にパターンとして付与されます。インデックスがパターンと重なるあらゆる種類のポリシーが、そのパターン全体を覆っていることが条件です。エージェントはパターンの有限オートマトンでこれを正確に判断します。こうして明日のログのように後から作られるインデックスにもすぐ届き、最初のドキュメントの書き込みでインデックスが作られる場合も同じです。`logs-*` の中で `logs-secret` を拒否するように、パターンの中でポリシーが別の決定をする場合、エージェントはクラスターにある各インデックスとデータストリームを名前で付与し、新しいものは次の同期で付与します。
- **フィールドレベルセキュリティは下位で別の決定がある場合だけです。** ユーザーが全体を読めて、どのポリシーもフィールドを取り上げられないインデックスは、フィールドレベルセキュリティなしで読むため、後から加わったフィールドもすぐ読めます。それ以外は、ドキュメントにあるフィールドとポリシーが指すフィールドのうち、ポリシーが許可するフィールドを読みます。`name.keyword` のようなマルチフィールドは元のフィールドに従います。
- **マスクと行フィルターはセキュリティプラグイン自身のものです。** マスクはエントリのマスクフィールドに、行フィルターはそのドキュメントレベルセキュリティのクエリになります。OpenSearch の validate API が付与の前に各クエリを確認し、OpenSearch が実行しないクエリはユーザーにドキュメントを 1 件も見せず、ログに記録されます。セキュリティプラグインが知らないマスクは、代わりにそのフィールドを隠します。
- **権限の組ごとに 1 つのエントリ。** 権限が同じインデックスとパターンは 1 つのエントリを共有します。セキュリティプラグインは重なるエントリのフィールドレベル・ドキュメントレベルセキュリティを組み合わせるため、エントリ同士が重なることはありません。
- **クラスターの複合操作は読み取りと書き込みに伴います。** 読み取りにはマルチ検索、マルチ取得、スクロールのための `cluster_composite_ops_ro`、書き込みには bulk のための `cluster_composite_ops` が伴います。
- **変更は 1 つのパッチです。** エージェントはロールとマッピングを JSON パッチで書き込み、セキュリティプラグインはそれをクラスター全体に一度に適用します。ロールはそれを指すマッピングより先に、マッピングはそれが指す削除対象のロールより先に処理します。セキュリティプラグインが拒否したパッチは変更ごとに適用されるため、不具合のあるロールだけが失敗します。
- **現時点のポリシー。** 各同期はその時点で有効なポリシーで計画を立てるため、有効期間は時間どおりに始まり終わります。
- **バックエンドロールがグループです。** グループのポリシーは、その名前のバックエンドロールを持つ内部ユーザーと、GrantForge がユーザーを入れたグループに適用されます。
- **手作業の変更は元に戻ります。** エージェントは同期のたびに自分のロールとマッピングをセキュリティプラグインの保持内容と比べ、異なる部分を書き直します。何も変わっていなければ何も送りません。
- **GrantForge 外のロールを報告します。** OpenSearch はユーザーのいずれかのロールが与えるものを許可するため、エージェントはユーザーにマッピングされた別のロールが与えるものを取り上げられません。ポリシーが拒否するものを与えるそのようなロールごとに一度、ロール、ユーザー、アクセス、インデックス、ポリシーを示して警告し、どのロールにも縛られない `all_access` にマッピングされたユーザーも警告します。
- **アクセスは監査ログから得ます。** エージェントは、セキュリティプラグインが書き込むインデックス `security-auditlog-*` から監査ログを読みます。拒否したものは `MISSING_PRIVILEGES`、許可したものは監査設定が記録する場合に `GRANTED_PRIVILEGES` です。各イベントはユーザー、アクセスタイプ、届いた各インデックスやデータストリーム、またはクラスターを示し、GrantForge が決めたのか、GrantForge 外のロールが許可したのかを伝えます。

| アクション | アクセスイベント |
| --- | --- |
| `indices:data/read/*`：検索、取得、カウント | 各インデックスまたはデータストリームの `read` |
| `indices:data/write/index`、`update`、`bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`、`auto_create`、`data_stream/create` | `create_index` |
| `indices:admin/aliases*`、`indices:monitor/*` | `manage_aliases`、`monitor` |
| その他の `indices:admin/*` | `manage` |
| `cluster:monitor/*`、`cluster:admin/snapshot/*` と `repository/*` | クラスターの `cluster_monitor`、`manage_snapshots` |
| インデックスとコンポーネントのテンプレート、取り込みパイプライン | クラスターの `manage_templates`、`manage_pipelines` |

## 配置

1. `opensearch` データサービスを追加し、ポリシーを設定します。**データ権限 → エージェント** を開いてサービスを選び、エージェントトークンを発行します。トークンは一度しか表示されません。エージェントのアカウントだけが読めるファイルに保存してください。
2. セキュリティプラグインの設定を変更できるユーザーをエージェントに与えます。`plugins.security.restapi.roles_enabled` に挙がっている間 `all_access` にマッピングされたユーザー、または `plugins.security.authcz.admin_dn` が指す管理者証明書です。テストはデモ設定の `admin` と、その管理者証明書でエージェントを動かします。
3. アクセスを報告するには、監査ログをクラスター内に保ちます（`plugins.security.audit.type: internal_opensearch`）。OpenSearch が許可したものも報告するには、監査設定の無効なカテゴリから `GRANTED_PRIVILEGES` も外してください。拒否は既定で記録されます。
4. 例のアドレス、パス、名前を置き換えて設定ファイルを書きます。

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   これらのパスはエージェントのマシン上のもので、キャッシュディレクトリは書き込み可能でなければなりません。パスワードファイルは UTF-8 で読み、前後の空白を取り除きます。
5. 例えば systemd サービスとしてエージェントを起動します。

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   `<version>` を配布物のバージョンに置き換えてください。`java -Dgrantforge.…=… -jar …` で与えた設定はファイルより優先されます。エージェントは SIGTERM できれいに停止し、保留中のイベントを送信またはスプールします。OpenSearch は与えられたものを保ちます。
6. **既存のロールをポリシーに移す：** GrantForge の外でユーザーにマッピングされたロールは、エージェントのロールと並んで効き続けます。対応するポリシーを作成してから、それらのマッピングを削除してください。エージェントの警告が、ポリシーが拒否するものを与えるロールを示します。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、このエージェントの名前、署名付きスナップショット、スプールしたイベント、監査ログの読み取り位置を置く書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードの X.509 Ed25519 公開鍵をコピーします。省略すると最初の接続時にサーバーから取得します |
| `grantforge.opensearch.hosts` | 必須：URL 形式のノード。接続できないときは順に試します。スキームがなければ `https`、ポートの既定は `9200` |
| `grantforge.opensearch.user`、`grantforge.opensearch.password.file` | エージェントのユーザーとそのパスワードのファイル |
| `grantforge.opensearch.tls.cert`、`grantforge.opensearch.tls.key` | 代わりに：証明書でサインインするための PEM クライアント証明書とその秘密鍵を組で指定。`grantforge.opensearch.tls.key.passphrase.file` には暗号化された鍵のパスフレーズを置きます |
| `grantforge.opensearch.ssl.verification.mode` | `full`（既定）はノードの証明書と名前を、`certificate` は証明書だけを確認し、`none` は何も確認しません |
| `grantforge.opensearch.tls.ca` | ノードを検証する PEM CA 証明書。空なら Java が信頼するものを信頼します |
| `grantforge.opensearch.timeout.ms` | 接続とリクエストにかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |
| `grantforge.opensearch.role.prefix` | エージェントのロール名の先頭。既定 `grantforge_`。小文字、数字、ハイフン、アンダースコアからなり、アンダースコアで終わり、最大 32 文字。名前がこれで始まり、エージェントが印を付けたロールがエージェントのものです |
| `grantforge.opensearch.unmanaged.users` | エージェントが手を付けないユーザー。カンマ区切りで、例えばサービスアカウント。自分のユーザーと `all_access` にマッピングされたユーザーには常に手を付けません |
| `grantforge.sync.interval.ms` | エージェントが OpenSearch を合わせる間隔。既定 `10000`、範囲 `1000..3600000` ミリ秒 |
| `grantforge.audit.log` | 既定 `true`。`false` は監査ログを読まず、アクセスを報告しません |
| `grantforge.audit.log.index`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.max.documents` | 監査ログのインデックス（既定 `security-auditlog-*`）、読む間隔（既定 `5000` ミリ秒）、1 回の読み取りの最大ドキュメント数（既定 `1000`）。たまったものは数回に分けて読みます |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを求める間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスク上のスプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同じ許可されたアクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ送ります |

## ユーザー、グループ、ロール

ポリシーはセキュリティプラグインが知る OpenSearch ユーザーを指します。エージェントは、内部ユーザー、LDAP や SAML などのディレクトリでサインインするかもしれないポリシーに挙がるユーザー、以前にロールを保っていたユーザーのロールを保ちます。`kibanaserver` のようなセキュリティプラグインの予約ユーザーと隠しユーザーには手を付けません。グループは内部ユーザーのバックエンドロールと、GrantForge がユーザーを入れたグループです。ディレクトリがサインイン時に与えるバックエンドロールはエージェントには分からないため、そのようなユーザーのグループは GrantForge で設定してください。ロールは GrantForge がユーザーに与えるロールです。

## 決定と制限

- スナップショットがなければ、エージェントは何も変更しません。使われていないサービスや、タイプが `opensearch` でないサービスに結び付いたトークンは何も付与しません。エージェントは自分のロールとマッピングを削除し、セキュリティプラグイン自身のロールが効きます。
- エージェントが動いていない間、OpenSearch は最後に与えられたものを保ち、ポリシーの変更はエージェントを待ちます。変更は更新間隔と同期間隔のうちに OpenSearch に届きます。
- パターンではなく名前で付与されるインデックスは、次の同期で権限を得ます。
- エイリアスはその背後のインデックスに届きます。エイリアスを検索するには、それが含むすべてのインデックスへの権限が必要なため、ポリシーはエイリアスではなくインデックスとデータストリームを指します。
- 行フィルターが確かめられるのはユーザーが読めるフィールドだけで、マスクは text と keyword フィールドの値に効きます。
- 名前がドットで始まるインデックスには、ドットで始まるポリシーのパターンを通じてだけ届きます。
- OpenSearch Dashboards 向けのセキュリティプラグインのテナントは、エージェントは管理しません。

## 関連ガイド

- [OpenSearch](/ja/plugins/opensearch/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
