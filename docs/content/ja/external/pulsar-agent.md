---
title: Apache Pulsar エージェント
description: Pulsar の broker に認可プロバイダーをインストールし、誰がどこでプロデュース・コンシュームでき、どのサブスクリプションを使え、Functions、Sources、Sinks を実行し、トピック、名前空間、テナント、クラスターを管理でき、どのネットワークから行えるかを GrantForge ポリシーで判断します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Pulsar エージェントは broker の認可プロバイダーです。Pulsar は誰がプロデュース、コンシュームし、Functions、Sources、Sinks を実行し、トピック、名前空間、テナント、クラスターを管理できるかをエージェントに尋ね、エージェントは `pulsar` データサービスの署名付きポリシーに基づいて答えます。配布物は、依存関係を同梱して再配置した Java 17 の JAR `agents/pulsar/grantforge-agent-pulsar-<version>.jar` として提供します。

リポジトリの Testcontainers 統合テストは、Pulsar `3.0.17`、`3.3.9`、`4.0.14`、`4.2.5` の broker で JAR を実行します。これは他のすべてのバージョンを認定するものではありません。サービスプラグインについては [Apache Pulsar](/ja/plugins/pulsar/) を参照してください。

## 仕組み

- **すべてのチェックはトピック名のすべてのレベルで判断します。** Pulsar 自身の権限と同様にテナントや名前空間に対する権限はその中のものに及ぶため、エージェントは `persistent://sales/orders/payments` へのプロデュースをテナント `sales`、名前空間 `orders`、トピック `payments` で判断します。どれか一つで拒否されれば拒否し、どれか一つで許可されれば許可します。
- **コンシュームはサブスクリプションでも判断します。** エージェントは各レベルで、トピックとサブスクリプションの組み合わせも尋ねます。そのため、ポリシーでロールに `billing-*` のような一部のサブスクリプションだけでコンシュームさせられます。
- **クラスターの管理者は Pulsar のスーパーユーザーです。** broker のクラスターの `admin` はロールをスーパーユーザーにし、Pulsar はスーパーユーザーにすべてを許すため、下のポリシーはその人たちを制限しません。Pulsar の broker とクラスターの操作には、対象のクラスターの `admin` が必要です。
- **ポリシーを一部のネットワークにだけ適用できます。** すべてのチェックはクライアントアドレスを伴い、エージェントは broker のネットワークスタックが使う Netty のアドレス範囲で **クライアントアドレス** 条件を評価します。
- **Pulsar を通じた権限付与は拒否します。** Pulsar 自身の権限とテナントの管理者ロールは使われないため、`pulsar-admin namespaces grant-permission` などは HTTP 405 で応答され、GrantForge で付与するよう案内されます。
- **拒否は理由とともにログに記録します。** クライアントが受け取る拒否の文言は Pulsar が決めます。broker のログには、どの GrantForge ポリシーが拒否したか、または許可するポリシーがないことが記録されます。
- **決定を保持し、アクセスをまとめます。** 現在のスナップショットの決定は既定で 100,000 件を 10 秒間保持し、新しいスナップショットでは最初からやり直します。既定 10 秒のウィンドウ内の同一の許可アクセスは回数を付けた 1 件のアクセスイベントとして報告し、拒否は 1 件ずつすぐに報告します。各イベントにはトピック、名前空間、テナントまたはクラスターと、クライアントアドレスが含まれます。
- **1 つのエージェントが broker 全体を担います。** broker はバイナリプロトコル、Web サービス、Functions Worker ごとに認可プロバイダーを読み込みます。これらは 1 つのエージェントを共有し、エージェントは broker とともに停止します。

| 操作 | 必要なアクセス |
| --- | --- |
| プロデュース、またはプロデュースによるトピックの作成 | トピックの `produce` |
| サブスクライブ、コンシューム、サブスクリプション解除、スキップ、ピーク、メッセージの期限切れ、カーソルのリセット | トピックとサブスクリプションの `consume` |
| トピックの検索、統計・メタデータ・ポリシーの読み取り、名前空間のトピック一覧 | `produce` または `consume` |
| トピックの終了、コンパクション、オフロード、アンロード、切り詰め、ポリシーの変更 | トピックの `admin_topic` |
| トピックの作成と削除、名前空間のポリシーの変更 | 名前空間の `admin_namespace` |
| Functions、Sources、Sinks の実行、Packages の利用 | 名前空間の `functions`、`sources`、`sinks`、`packages` |
| テナントの名前空間の作成、一覧、削除 | テナントの `admin_tenant` |
| テナントとクラスターの作成、変更、削除、Pulsar の broker 操作 | クラスターの `admin` |

## 配置

1. `pulsar` データサービスを追加してポリシーを設定します。**データ権限 → エージェント** を開き、サービスを選んでエージェントトークンを発行します。トークンは一度しか表示されないため、Pulsar の実行アカウントだけが読めるファイルに保存します。
2. JAR を **すべての broker** の `lib`（例：`/pulsar/lib`）にコピーします。
3. すべての broker の `broker.conf` または `standalone.conf` に次の設定を追加し、例のアドレス、パス、インスタンス名を置き換えます。

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   クライアントがロールを持つよう、認証も有効にする必要があります。`superUserRoles` と `proxyRoles` には broker 自身のロールとプロキシのロールだけを指定してください。エージェントはそれらをチェックせず、Pulsar はプロキシを経由した元のクライアントもチェックします。管理者には代わりにクラスターの `admin` を付与してください。キャッシュディレクトリは Pulsar の実行アカウントが書き込める必要があります。コンテナでは `grantforge.*` の設定をファイルに書いてください。一部の Pulsar イメージのシェルは、`PULSAR_PREFIX_grantforge.token.file` のように名前にドットを含む環境変数を捨てます。
4. broker を 1 台ずつ再起動します。エージェントが最初のスナップショットを受け取るまで、broker とプロキシ以外のリクエストは拒否されます。
5. **既存の権限を先に移行してください。** 名前空間、トピック、サブスクリプションに対する Pulsar 自身の権限と、テナントの管理者ロールは適用されなくなります。事前に対応するポリシーを作成してください。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、この broker の名前、署名付きスナップショットと保留イベント用の書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショット署名鍵を固定する任意のファイル。エージェントページから Base64 でエンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると最初の接続時にサーバーから鍵を取得します |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを取得する間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、スプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同一の許可アクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ報告します |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 保持する決定の最大数（既定 `100000`、最大 `10000000`、`0` は保持しない）と保持期間（既定 `10000`、`1..3600000` ミリ秒） |

## ユーザー、グループ、ロール

ポリシーは、Pulsar の認証がクライアントに与えるロール（例：トークンの subject や証明書の common name）をユーザーとして指定します。Pulsar には独自のグループがありません。グループは GrantForge でそのロールが属するグループで、ロールは GrantForge が付与したロールです。

## 決定と制限

- 使用できるスナップショットがない場合、broker とプロキシのリクエストを除くすべてが拒否されます。使用されていないサービスは、`authorizationEnabled` をオフにしたときと同様に Pulsar をチェックしません。
- タイプが `pulsar` でないサービスに紐付いたトークンは、すべてを拒否します。
- Pulsar は多くのチェックの前に、ロールがスーパーユーザーかテナントの管理者かを尋ねます。リクエストを通す回答だけが報告され、リクエスト自体のチェックは結果にかかわらず報告されます。Pulsar がこの質問だけで判断するリクエスト（Pulsar 3.0 のテナントとクラスターの操作）は、拒否された場合に debug レベルでのみ記録されます。
- クラスターの `admin` は、そのクラスターの broker のスーパーユーザーにするだけです。
- `pulsar-admin namespaces permissions` などの Pulsar の権限一覧は失敗します。ポリシーは GrantForge が保持します。
- クライアントアドレスはアドレスと範囲だけです。ホスト名はどのクライアントにも一致しません。
- 決定は `grantforge.decision.cache.ttl.ms` の間保持されるため、ポリシーの適用開始や終了は最大でその時間だけ遅れます。

## 関連ガイド

- [Apache Pulsar](/ja/plugins/pulsar/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
