---
title: Elasticsearch エージェント
description: クラスターの隣で Elasticsearch エージェントを動かし、GrantForge のポリシーを Elasticsearch 自身のロールにして、ネイティブユーザーに与え、他のレルムのユーザーにはマッピングし、ライセンスが持つところではフィールドレベル・ドキュメントレベルセキュリティを使い、インデックスパターンをパターンのまま保って新しいインデックスにすぐ届くようにし、アクセスを監査ログファイルから報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Elasticsearch エージェントは、クラスターの隣で動くサービスです。Elasticsearch 自身のアクセス制御を `elasticsearch` データサービスの署名付きポリシーに合わせ続けます。ユーザーごとに、ポリシーがそのユーザーに与えるものだけをちょうど持つロールを 1 つ保ち、クラスターのライセンスが持つところではフィールドレベル・ドキュメントレベルセキュリティを含めます。ネイティブユーザーはそのロールを自分のロールの 1 つとして持ち、LDAP、SAML、OpenID Connect など他のレルムのユーザーは名前によるロールマッピングでそれを受け取ります。その後は Elasticsearch がすべてのリクエストを自ら確認し、エージェントがクライアントとクラスターの間に入ることはありません。配布物は実行可能な JAR `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar` として提供し、Java 17 以降が必要です。

リポジトリの Testcontainers 統合テストは、エージェントと配布される JAR を、セキュリティを有効にした Elasticsearch `8.19`、`9.3`、`9.4`、`9.5` で動かします。これは他のすべてのバージョンを認定するものではありません。エージェントは `8.19` より古いクラスター、セキュリティのないクラスター、OpenSearch クラスターを拒否します。OpenSearch クラスターは [OpenSearch エージェント](/ja/external/opensearch-agent/) が扱います。サービスプラグインについては [Elasticsearch](/ja/plugins/elasticsearch/) を参照してください。

## 仕組み

- **ユーザーごとに 1 つのロールが GrantForge の与えるものを持ちます。** ポリシーが何かを与える各ユーザーについて、エージェントはその名前のロール（`alice` なら `grantforge_alice`）を保ちます。メタデータがユーザーを示し、エージェントのものであることを示します。エージェントはこの印のないロールを自分のものとみなさないため、他のロールは変更しません。
- **ネイティブユーザーはロールを持ち、他のレルムは名前で受け取ります。** エージェントはネイティブユーザーのロールにそのロールを加え、ユーザーの他のロールはすべて残します。ロールマッピングはネイティブレルムとファイルレルム以外のすべてのレルムに効くため、エージェントは同じ名前のロールマッピングも保ち、ユーザーの名前にロールを与えます。これは LDAP、Active Directory、SAML、OpenID Connect、JWT、Kerberos、PKI でサインインするユーザーのためです。ファイルレルムは Elasticsearch の API で変更できないため、そのユーザーはロールを受け取りません。
- **インデックスパターンはパターンのままです。** `logs-*` のようなポリシーのインデックスパターンは、その範囲全体ですべてのポリシーが同じ決定をする場合にパターンとして付与されます。インデックスがパターンと重なるあらゆる種類のポリシーが、そのパターン全体を覆っていることが条件です。エージェントはパターンの有限オートマトンでこれを正確に判断します。こうして明日のログのように後から作られるインデックスにもすぐ届き、最初のドキュメントの書き込みでインデックスが作られる場合も同じです。`logs-*` の中で `logs-secret` を拒否するように、パターンの中でポリシーが別の決定をする場合、エージェントはクラスターにある各インデックスとデータストリームを名前で付与し、新しいものは次の同期で付与します。
- **フィールドレベルセキュリティは下位で別の決定がある場合だけです。** ユーザーが全体を読めて、どのポリシーもフィールドを取り上げられないインデックスは、フィールドレベルセキュリティなしで読むため、後から加わったフィールドもすぐ読めます。それ以外は、ドキュメントにあるフィールドとポリシーが指すフィールドのうち、ポリシーが許可するフィールドを読みます。`name` に対する `name.keyword` のように、各フィールドはそのマルチフィールドとともに付与されます。
- **行フィルターはドキュメントレベルセキュリティです。** 行フィルターはエントリのクエリになります。Elasticsearch の validate API が付与の前に各クエリを確認し、Elasticsearch が実行しないクエリはユーザーにドキュメントを 1 件も見せず、ログに記録されます。
- **Platinum 未満でもポリシーが許す以上は与えません。** Elasticsearch のドキュメントレベル・フィールドレベルセキュリティは Platinum ライセンスから使え、それより下ではそれらを使うロールをすべて無効にします。エージェントは同期のたびにライセンスを読みます。Platinum 未満では、ポリシーがインデックスのフィールドやドキュメントを制限するためにどちらかが必要な読み取りはまったく付与せず、どのユーザーがどのインデックスを読めないのか、その理由をログに記録します。インデックス全体の読み取りとその他のすべては通常どおり付与します。
- **権限の組ごとに 1 つのエントリ。** 権限が同じインデックスとパターンは 1 つのエントリを共有します。Elasticsearch は重なるエントリのフィールドレベル・ドキュメントレベルセキュリティを組み合わせるため、エントリ同士が重なることはありません。
- **現時点のポリシー。** 各同期はその時点で有効なポリシーで計画を立てるため、有効期間は時間どおりに始まり終わります。
- **ネイティブユーザーのロールがグループです。** グループのポリシーは、その名前のロールを持つネイティブユーザーと、GrantForge がユーザーを入れたグループに適用されます。
- **手作業の変更は元に戻ります。** エージェントは同期のたびに自分のロール、ロールマッピング、ネイティブユーザーのロールを Elasticsearch の保持内容と比べ、異なる部分を書き直し、あるユーザーのロールを別のユーザーが持っていれば取り上げます。ロールは与える前に 1 回のリクエストで書き込み、削除する前に取り上げます。何も変わっていなければ何も送りません。Elasticsearch が拒否した変更はログに記録され、他の変更を止めません。
- **GrantForge 外のロールを報告します。** Elasticsearch はユーザーのいずれかのロールが与えるものを許可するため、エージェントは別のロールが与えるものを取り上げられません。ネイティブユーザーが持つ、またはユーザーの名前のロールマッピングが与えるロールのうち、ポリシーが拒否するものを与えるロールごとに一度、ロール、ユーザー、アクセス、インデックス、ポリシーを示して警告し、どのロールにも縛られない `superuser` ロールを持つユーザーも警告します。
- **アクセスは監査ログファイルから得ます。** Elasticsearch は監査ログを各ノードのファイルにだけ書き込みます。エージェントは与えられたファイルを前回の読み取りが終わった位置から読み、Elasticsearch がローテーションしても追いかけ、`access_granted` と `access_denied` のイベントを報告します。各イベントはユーザー、アクセスタイプ、届いた各インデックスやデータストリーム、またはクラスターを示し、GrantForge が決めたのか、GrantForge 外のロールが許可したのかを伝えます。

| アクション | アクセスイベント |
| --- | --- |
| `indices:data/read/*`：検索、取得、カウント | 各インデックスまたはデータストリームの `read` |
| `indices:data/write/index`、`update`、`bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`、`auto_create`、`data_stream/create` | `create_index` |
| `indices:admin/delete`、`data_stream/delete` | `delete_index` |
| `indices:admin/get`、`mappings/get`、`settings/get`、`resolve/*` | `view_index_metadata` |
| `indices:monitor/*`、その他の `indices:admin/*` | `monitor`、`manage` |
| `cluster:monitor/*`、`cluster:admin/snapshot/*` と `repository/*`、`slm/*` | クラスターの `cluster_monitor`、`create_snapshot`、`manage_slm` |
| インデックスとコンポーネントのテンプレート、取り込みパイプライン、`cluster:admin/ilm/*` | クラスターの `manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` |

## 配置

1. `elasticsearch` データサービスを追加し、ポリシーを設定します。**データ権限 → エージェント** を開いてサービスを選び、エージェントトークンを発行します。トークンは一度しか表示されません。エージェントのアカウントだけが読めるファイルに保存してください。
2. ロール、ロールマッピング、ユーザーを管理するクラスター権限 `manage_security` と、各インデックスのマッピングを読みクエリを検証する権利を持つユーザーをエージェントに与えます。例えば `superuser` ロールを持つユーザーです。サインインにはパスワード、そのユーザーが作成した API キー、または PKI レルムがそのユーザーに対応付けるクライアント証明書を使えます。
3. アクセスを報告するには、Gold 以上のライセンスが必要な監査ログを有効にし（`xpack.security.audit.enabled: true`）、ノードの監査ログファイル、つまり各ノードのログディレクトリにある `<クラスター名>_audit.json` をエージェントが読めるようにします。ノード上でエージェントを動かすか、エージェントが読めるディレクトリにファイルを集めてください。`xpack.security.audit.logfile.events.include: access_granted, access_denied` でログをエージェントが報告するものに絞れます。
4. 例のアドレス、パス、名前を置き換えて設定ファイルを書きます。

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   これらのパスはエージェントのマシン上にあり、キャッシュディレクトリは書き込み可能でなければなりません。パスワードとキーのファイルは UTF-8 で読み、前後の空白を除きます。
5. 例えば systemd サービスとしてエージェントを起動します。

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   `<version>` を配布物のバージョンに置き換えてください。`java -Dgrantforge.…=… -jar …` で与えた設定はファイルより優先されます。エージェントは SIGTERM できれいに停止し、保留中のイベントを送信またはスプールします。Elasticsearch は与えられたものを保ちます。
6. **既存のロールをポリシーに移す：** GrantForge の外でユーザーが持つロールは、エージェントのロールと並んで効き続けます。対応するポリシーを作成してから、それらのロールをユーザーから外してください。エージェントの警告が、ポリシーが拒否するものを与えるロールを示します。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、このエージェントの名前、署名付きスナップショット、スプールしたイベント、監査ログの読み取り位置を置く書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードの X.509 Ed25519 公開鍵をコピーします。省略すると最初の接続時にサーバーから取得します |
| `grantforge.elasticsearch.hosts` | 必須：URL 形式のノード。接続できないときは順に試します。スキームがなければ `https`、ポートの既定は `9200` |
| `grantforge.elasticsearch.user`、`grantforge.elasticsearch.password.file` | エージェントのユーザーとそのパスワードのファイル |
| `grantforge.elasticsearch.api.key.file` | 代わりに：Elasticsearch が作成時に返すとおりのエンコード済み API キーのファイル |
| `grantforge.elasticsearch.tls.cert`、`grantforge.elasticsearch.tls.key` | 代わりに：証明書でサインインするための PEM クライアント証明書とその秘密鍵を組で指定。`grantforge.elasticsearch.tls.key.passphrase.file` には暗号化された鍵のパスフレーズを置きます |
| `grantforge.elasticsearch.ssl.verification.mode` | `full`（既定）はノードの証明書と名前を、`certificate` は証明書だけを確認し、`none` は何も確認しません |
| `grantforge.elasticsearch.tls.ca` | ノードを検証する PEM CA 証明書。Elasticsearch の `http_ca.crt` など。空なら Java が信頼するものを信頼します |
| `grantforge.elasticsearch.timeout.ms` | 接続とリクエストにかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |
| `grantforge.elasticsearch.role.prefix` | エージェントのロール名の先頭。既定 `grantforge_`。小文字、数字、ハイフン、アンダースコアからなり、アンダースコアで終わり、最大 32 文字。名前がこれで始まり、エージェントが印を付けたロールがエージェントのものです |
| `grantforge.elasticsearch.unmanaged.users` | エージェントが手を付けないユーザー。カンマ区切りで、例えばサービスアカウント。自分のユーザー、Elasticsearch の予約ユーザー、`superuser` ロールを持つユーザーには常に手を付けません |
| `grantforge.sync.interval.ms` | エージェントが Elasticsearch を合わせる間隔。既定 `10000`、範囲 `1000..3600000` ミリ秒 |
| `grantforge.audit.log.files` | 読む監査ログファイル。カンマ区切り。指定しなければ監査ログを読まず、アクセスを報告しません |
| `grantforge.audit.log.interval.ms`、`grantforge.audit.log.max.lines` | ファイルを読む間隔（既定 `5000` ミリ秒）と、1 回の読み取りで各ファイルから取る最大行数（既定 `5000`）。たまったものは数回に分けて読みます |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを求める間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスク上のスプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同じ許可されたアクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ送ります |

## ユーザー、グループ、ロール

ポリシーは Elasticsearch ユーザーをそのユーザー名で指します。エージェントは、ネイティブユーザー、他のレルムでサインインするかもしれないポリシーに挙がるユーザー、以前にロールを保っていたユーザーのロールを保ちます。`kibana_system` のような Elasticsearch の予約ユーザーには手を付けません。グループはネイティブユーザーの他のロールと、GrantForge がユーザーを入れたグループです。ディレクトリがサインイン時に与えるグループはエージェントには分からないため、そのようなユーザーのグループは GrantForge で設定してください。ロールは GrantForge がユーザーに与えるロールです。

## 決定と制限

- スナップショットがなければ、エージェントは何も変更しません。使われていないサービスや、タイプが `elasticsearch` でないサービスに結び付いたトークンは何も付与しません。エージェントは自分のロールを取り上げ、ロールマッピングとともに削除し、Elasticsearch 自身のロールが効きます。
- エージェントが動いていない間、Elasticsearch は最後に与えられたものを保ち、ポリシーの変更はエージェントを待ちます。変更は更新間隔と同期間隔のうちに Elasticsearch に届きます。
- パターンではなく名前で付与されるインデックスは、次の同期で権限を得ます。
- Platinum 未満では、ポリシーがインデックスの読み取りを制限するユーザーはそのインデックスを何も読めません。Gold 未満では、エージェントはアクセスを報告しません。
- ユーザーの名前のロールマッピングは、そのユーザー名を持つすべてのレルムに効きます。ポリシーが指すのはユーザーで、レルムではありません。
- エイリアスはその背後のインデックスに届くため、ポリシーはエイリアスではなくインデックスとデータストリームを指します。名前がドットで始まるインデックスには、ドットで始まるポリシーのパターンを通じてだけ届きます。
- Kibana のスペースとアプリケーション権限は、エージェントは管理しません。

## 関連ガイド

- [Elasticsearch](/ja/plugins/elasticsearch/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
