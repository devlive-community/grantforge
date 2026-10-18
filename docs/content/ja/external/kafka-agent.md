---
title: Apache Kafka エージェント
description: Kafka のブローカーとコントローラーにエージェントをインストールし、すべてのリクエストを GrantForge のポリシーで認可し、ポリシーを読み取り専用の ACL として表示し、繰り返しのアクセスを 1 つの監査イベントにまとめます。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Kafka エージェントは、`authorizer.class.name` に指定する Kafka の `Authorizer` API のオーソライザーです。ブローカーとコントローラーはすべてのリクエストについてエージェントに問い合わせ、エージェントは `kafka` データサービスの署名付きポリシーで答えます。配布物には、依存関係を再配置して同梱した Java 8 互換の JAR 1 つ、`agents/kafka/grantforge-agent-kafka-<version>.jar` として含まれます。

リポジトリの Testcontainers 統合テストは、ブローカーとコントローラーを兼ねる KRaft ノードで、Kafka `3.7.2`、`3.9.2`、`4.1.2`、`4.3.1` の中で JAR を実行します。これは他のすべてのバージョンを認定するものではありません。サービスプラグインは [Apache Kafka](/ja/plugins/kafka/) を参照してください。

## 仕組み

- **すべてのリクエストを検査します。** リクエストが扱う各リソースについて Kafka が ACL 操作を問い合わせ、エージェントは Kafka のリソースタイプをリソースレベル、操作をアクセスタイプとしてポリシーで答えます。拒否は `TOPIC_AUTHORIZATION_FAILED` などの Kafka 自身のエラーとしてクライアントに届きます。
- **冪等プロデューサーにはトピック 1 つで十分です。** Kafka は、プロデューサーがいずれかのトピックに書き込めるなら、クラスターの `idempotent_write` なしで起動を許し、その可否をオーソライザーに問い合わせます。エージェントはポリシーそのものから答えます。どのトピックへの `write` の許可も数えられ、拒否はすべてのトピックを対象とするポリシーでのみ数えられます。
- **ポリシーは ACL として見えます。** `kafka-acls --list` と Admin API の `describeAcls` は、現在適用されるポリシーを表示します。ワイルドカードのない値はリテラル ACL、`orders*` のような値は前方一致 ACL、`*` は Kafka のワイルドカードとして、ユーザーは `User:` プリンシパル、グループは `Group:`、ロールは `Role:`、`public` グループは `User:*` として表示されます。ACL で表現できないものは除外されます。ほかのワイルドカード、除外された値、条件付きの項目、例外を持つポリシーの該当項目です。ACL の作成と削除は拒否されます：*the ACLs of this cluster are GrantForge policies; change them in GrantForge*。
- **決定を保持します。** ブローカーは生成と取得のたびに同じ問い合わせをするため、エージェントは現在のスナップショットの決定を既定で 100,000 件、10 秒間保持します。新しいスナップショットでは一から始めるため、ポリシーの変更はエージェントが受け取った時点で適用されます。
- **繰り返しのアクセスをまとめます。** 既定 10 秒のウィンドウ内の同一の許可アクセスは、件数と最後の時刻を示す 1 つのアクセスイベントとして報告され、コンソールには *同一アクセス N 回* と表示されます。ユーザー、クライアントアドレス、リソース、アクセスタイプ、リクエストタイプ、決定したポリシーが同じアクセスを同一とみなします。拒否は 1 件ずつ即座に報告されます。何を報告するかは Kafka が決めます。たとえば一覧から除外したトピックについては報告を求めません。
- **スーパーユーザーは検査しません。** `super.users` のプリンシパルは、Kafka 自身のオーソライザーと同様に何でも行え、監査されません。

## 配置

1. `kafka` データサービスを追加してポリシーを設定します。**データ権限 → エージェント** を開き、サービスを選択してエージェントトークンを発行します。トークンは一度しか表示されないため、Kafka のアカウントだけが読めるファイルに保存してください。
2. JAR を **すべてのブローカーとコントローラー** の `libs`（例：`/opt/kafka/libs`）にコピーします。
3. 各ノードの `server.properties` に設定を追加し、例のプリンシパル、アドレス、パス、インスタンス名を置き換えます。

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` には、ブローカー間リスナーのプリンシパルなど、ブローカーとコントローラーが相互に使うプリンシパルを含める必要があります。含めない場合は、クラスターの `cluster_action` など、それらが行う操作をポリシーで許可してください。`PLAINTEXT` リスナーへの接続は `User:ANONYMOUS` です。クライアントがそのようなリスナーに到達できない場合にだけ含めてください。キャッシュディレクトリは Kafka のアカウントが書き込める必要があります。
4. ブローカーとコントローラーを 1 台ずつ再起動します。コントローラーのリスナー以外のリスナーは、エージェントが最初のスナップショットを得た時点で開き、最長で `grantforge.start.timeout.ms` 待ちます。それまでの間、およびスナップショットがないときは、リクエストが拒否されます。
5. **既存の ACL を先に移行してください。** エージェントが Kafka 自身のオーソライザーを置き換えると、それが保存していた ACL は適用されなくなります。事前に `kafka-acls --list` で一覧し、対応するポリシーを作成してください。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、このブローカーまたはコントローラーの名前、署名付きスナップショットとキューのイベント用の書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると初回接続時にサーバーから鍵を取得します |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続・読み取りタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを問い合わせる間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスクキューの上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同一の許可アクセスを 1 つのイベントにまとめる期間。既定 `10000`、最大 `3600000` ミリ秒。`0` なら 1 件ずつ報告します |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 保持する決定の最大数。既定 `100000`、最大 `10000000`、`0` なら保持しません。および保持期間。既定 `10000`、`1..3600000` ミリ秒 |
| `grantforge.start.timeout.ms` | リスナーが最初のスナップショットを待つ時間。既定 `30000`、`0..600000` ミリ秒 |

エージェントは、Kafka がオーソライザーに渡す設定から値を読み取ります。ブローカーとコントローラーを兼ねるノードはロールごとにオーソライザーを作成し、両者は 1 つのエージェントを共有します。

## ユーザー、グループ、ロール

ポリシーでは Kafka が接続に与えるプリンシパルの名前を指定します。SASL のユーザー名、`sasl.kerberos.principal.to.local.rules` による Kerberos プリンシパルの短縮名、または `ssl.principal.mapping.rules` による TLS 証明書の名前です。Kafka にはグループとロールがなく、ポリシーのグループとロールは GrantForge がユーザーに与えるものです。

## 決定と制限

- 使用できるスナップショットがない場合、スーパーユーザーのリクエスト以外はすべて拒否されます。使用していないサービスは、オーソライザーがない場合と同様に Kafka を検査しません。
- トークンが `kafka` 以外のタイプのサービスに紐付いている場合は、すべて拒否します。
- アクセスタイプを拒否すると、それが含むタイプも拒否されます。トピックの `write` を拒否すると `describe` も拒否され、ユーザーはそのトピックを見られません。この点は Kafka 自身の ACL と異なります。
- ユーザーがあるタイプのいずれかのリソースに対して操作できるかという Kafka の問い合わせはアクセスではないため、報告されません。
- 決定は `grantforge.decision.cache.ttl.ms` の間保持されるため、ポリシーの有効期間の開始や終了は最大でその分遅れます。
- エージェントはブローカーとコントローラーで動作します。Kafka Connect の REST API、スキーマレジストリなど Kafka 周辺のサービスは対象外です。

## 関連ガイド

- [Apache Kafka](/ja/plugins/kafka/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
