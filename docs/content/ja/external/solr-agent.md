---
title: Apache Solr エージェント
description: Solr ノードに認可プラグインをインストールし、GrantForge のポリシーで、誰がどのコレクションをどのリクエストパスで検索・更新でき、どのコレクションとクラスターを管理でき、どのネットワークから操作できるかを決めます。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Solr エージェントはノードの認可プラグインです。Solr はすべてのリクエストについてエージェントに問い合わせ、エージェントは `solr` データサービスの署名付きポリシーに基づいて答えます。配布物はこれを 1 つの Java 11 JAR、`agents/solr/grantforge-agent-solr-<version>.jar` として提供し、依存関係は埋め込まれて再配置されています。

リポジトリの Testcontainers 統合テストは、Solr `9.8.1`、`9.10.1`、`9.11.0`、`10.0.0` の SolrCloud ノード内で JAR を実行します。これは他のすべてのバージョンを認定するものではありません。サービスプラグインは [Apache Solr](/ja/plugins/solr/) を参照してください。

## 仕組み

- **リクエストは Solr 自身の分類どおりに分類されます。** 各リクエストハンドラーは、Solr 自身のルールベースプラグインに対するのと同じく、`read` や `collection-admin-edit` のように必要な権限を示します。エージェントはその名前をアクセスタイプに変換し、何も示さないハンドラーはリクエストの種類で判定します。
- **コレクションへのリクエストはリクエストパスでも判定されます。** エージェントは `/select` 経由の `sales` の検索を、コレクション `sales` と「`sales` とパス `/select`」の両方で判定します。どちらかでの拒否がリクエストを拒否するため、ポリシーでコレクションのハンドラー 1 つを拒否できます。
- **チームは自分たちのコレクションを管理します。** コレクションを指定する Collections API リクエストには指定された各コレクションの `admin_collection` が必要なため、ポリシーでチームに、名前がそのプレフィックスで始まるコレクションだけを作成・リロード・削除させることができます。
- **クラスターの管理者は何でもできます。** クラスターの `admin` は、Solr 自身の `all` 権限と同じくすべてのリクエストを許可します。
- **ポリシーは一部のネットワークにだけ適用できます。** すべてのチェックにクライアントのアドレスが含まれます。エージェントが信頼するリバースプロキシの背後では、クライアントは信頼するプロキシが追加していない `X-Forwarded-For` の最後のアドレスです。
- **JSON Web Token が検証したロールはグループとして扱われます。** Solr の JWT 認証では、トークンのクレームがユーザーに与えるロールが判定のグループになり、GrantForge がユーザーを入れているグループと併せて使われます。
- **拒否は理由とともにログに残ります。** Solr は拒否したリクエストに自身の 403 で応答し、ノードのログには拒否した GrantForge ポリシー、または許可するポリシーがないことが記録されます。
- **判定はキャッシュされ、アクセスはまとめられます。** 現在のスナップショットの判定は既定で 100,000 件を 10 秒間保持し、新しいスナップショットでは最初からやり直します。ウィンドウ（既定 10 秒）内の同一の許可アクセスは回数付きの 1 件のアクセスイベントとして報告され、拒否はすぐに 1 件ずつ報告されます。各イベントにはコレクションとパス（またはクラスター）、クライアントアドレスが含まれます。
- **ノード自身はチェックしません。** Solr がノード自身として署名するノード間のリクエストは通過し、ユーザーのないリクエストにはサインインが求められます。

| リクエスト | 必要なアクセス |
| --- | --- |
| 任意の読み取りハンドラーでの検索、取得、エクスポート、クエリ | コレクションとパスの `query` |
| ドキュメントの追加、更新、削除 | コレクションとパスの `update` |
| コレクションの schema の読み取りまたは変更 | `schema_read` または `schema_edit` |
| コレクションの config の読み取りまたは変更 | `config_read` または `config_edit` |
| コレクションを指定する Collections API リクエスト: 作成、リロード、バックアップ、リストア、削除、変更、そのクラスター状態 | 指定された各コレクションの `admin_collection` |
| ヘルスチェック、メトリクス | クラスターの `health`、`metrics` |
| コレクションの一覧、core の状態、ZooKeeper・ファイルストア・パッケージの読み取り | クラスターの `admin_read` |
| 認証と認可の設定の読み取りまたは変更 | クラスターの `security` |
| core の管理、ファイルストアとパッケージの変更、どのハンドラーも示さない操作 | クラスターの `admin` |

## 配置

1. `solr` データサービスを追加してポリシーを設定します。Solr の管理者向けにクラスターの `admin` も含めます。**データ権限 → エージェント** を開き、サービスを選んでエージェントトークンを発行します。トークンは一度しか表示されないため、Solr の実行アカウントだけが読めるファイルに保存します。
2. JAR を **すべてのノード** の `server/solr-webapp/webapp/WEB-INF/lib`（例: `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`）にコピーし、ノードを 1 台ずつ再起動します。
3. クラスターの `security.json` の認可セクションを認証プラグインと並べて設定し、例のアドレス、パス、インスタンス名を置き換えます。

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>` でアップロードすると、ノードは再起動せずに反映します。リクエストにユーザーが付くよう、認証を有効にしておく必要があります。
4. **Solr 9 では、Solr が既定で有効にする Java のセキュリティマネージャーに注意してください。** トークン、固定した鍵、キャッシュディレクトリは `/var/solr/data` のような `SOLR_HOME` の下に置き、Solr 自身のポリシー以外にエージェントが必要とする権限を、`server/etc/security.policy`、または `-Djava.security.properties` が `policy.url.3` として追加する独自のポリシーファイルで付与します。

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10 はセキュリティマネージャーなしで動作します。
5. **既存の権限を先に移行してください:** Solr のルールベースプラグインの権限とユーザーロールは適用されなくなります。対応するポリシーを事前に作成してください。エージェントが最初のスナップショットを受け取るまで、ノード間のリクエスト以外は拒否されます。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須: GrantForge サーバー、エージェントトークンのファイル、クラスターのノードの名前、署名付きスナップショットとスプールしたイベント用の書き込み可能なディレクトリ |
| `grantforge.trusted.proxies` | エージェントが `X-Forwarded-For` を信頼するリバースプロキシ。アドレスまたは範囲をカンマ区切りで指定。既定はなし |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると、初回接続時にサーバーから取得します |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを取得する間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント: バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、スプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同一の許可アクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ報告 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 保持する判定の最大数（既定 `100000`、最大 `10000000`、`0` は保持しない）と保持期間（既定 `10000`、`1..3600000` ミリ秒） |

## ユーザー、グループ、ロール

ポリシーは、Solr の認証がサインインさせたユーザー（basic 認証のユーザーや JSON Web Token のプリンシパルなど）を指定します。グループは、JSON Web Token がユーザーについて検証したロールと、GrantForge がユーザーを入れているグループです。ロールは GrantForge がユーザーに与えたロールです。

## 決定と制限

- 使えるスナップショットがない場合、ノード間のリクエスト以外はすべて拒否されます。使われていないサービスでは、認可プラグインがない場合と同じく Solr はチェックされません。
- タイプが `solr` ではないサービスに紐づいたトークンはすべてを拒否します。
- 一部の v2 API リクエストのように JSON 本文でのみコレクションを指定する Collections API リクエストは、エージェントから見えるコレクションを指定しないため、クラスターの `admin` が必要です。
- Solr は問い合わせの前にエイリアスをコレクションに解決するため、ポリシーにはエイリアスではなくコレクションを書きます。
- Solr の `/admin/authorization` API ではエージェントのルールを編集できません。ポリシーは GrantForge が保持します。
- クライアントアドレスはアドレスと範囲のみで、ホスト名はどれにも一致しません。
- 判定は `grantforge.decision.cache.ttl.ms` の間保持されるため、ポリシーの適用開始や終了は最大でその時間だけ遅れます。

## 関連ガイド

- [Apache Solr](/ja/plugins/solr/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
