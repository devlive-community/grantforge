---
title: Apache Hadoop YARN エージェント
description: YARN ResourceManager に認可プロバイダーをインストールし、誰がどのアプリケーションをどのキューに投入でき、キューとクラスターを管理でき、どのネットワークから行えるかを GrantForge ポリシーで判断します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

YARN エージェントは ResourceManager の認可プロバイダーです。YARN は誰がキューに投入し、キューを管理し、クラスターを管理できるかをエージェントに尋ね、エージェントは `yarn` データサービスの署名付きポリシーに基づいて答えます。配布物は、依存関係を同梱して再配置した Java 8 互換の JAR `agents/yarn/grantforge-agent-yarn-<version>.jar` として提供します。

リポジトリの Testcontainers 統合テストは、Hadoop `3.2.4`、`3.3.6`、`3.4.3`、`3.5.0` の ResourceManager で JAR を実行します。これは他のすべてのバージョンを認定するものではありません。サービスプラグインについては [Apache Hadoop YARN](/ja/plugins/yarn/) を参照してください。

## 仕組み

- **すべてのチェックはパス上のすべてのキューで判断します。** YARN と同様にキューに対する権限はその下のキューにも及ぶため、エージェントは `root.sales.etl` への投入を `root`、`root.sales`、`root.sales.etl` で判断します。どれか一つで拒否されれば拒否し、どれか一つで許可されれば許可します。
- **投入はアプリケーション名でも判断します。** エージェントはそれぞれのキューで、キューとアプリケーション名の組み合わせも尋ねます。そのため、ポリシーで `payroll-*` のような一部のアプリケーションだけをキューとその下のキューに受け入れられます。
- **ポリシーを一部のネットワークにだけ適用できます。** すべてのチェックはクライアントアドレス（プロキシの背後では最初に転送されたアドレス）を伴い、エージェントは Hadoop 自身のホストリストで **クライアントアドレス** 条件を評価します。
- **クラスターの管理はポリシーに従います。** 本来 `yarn.admin.acl` が許可する YARN の管理操作には、クラスターの `admin` が必要です。
- **拒否は理由とともにログに記録します。** クライアントが受け取る拒否の文言は YARN が決めます。ResourceManager のログには、どの GrantForge ポリシーが拒否したか、または許可するポリシーがないことが記録されます。
- **決定を保持し、アクセスをまとめます。** 現在のスナップショットの決定は既定で 100,000 件を 10 秒間保持し、新しいスナップショットでは最初からやり直します。既定 10 秒のウィンドウ内の同一の許可アクセスは回数を付けた 1 件のアクセスイベントとして報告し、拒否は 1 件ずつすぐに報告します。各イベントにはアプリケーション、キュー、クライアントアドレスが含まれます。
- **ResourceManager 自身はチェックしません。** ResourceManager 自身のユーザー（例：`yarn`）はチェックも監査もしません。

| 操作 | 必要なアクセス |
| --- | --- |
| アプリケーションの投入 | そのキューの `submit_app`、またはそのキューとアプリケーション名の `submit_app`。キューの `admin_queue` でも構いません |
| 他のユーザーのアプリケーションの終了、移動、優先度変更、全詳細の表示 | そのキューの `admin_queue`。移動では移動先キューの `submit_app` も必要です。アプリケーションの所有者と、その ACL が許可したユーザーは常に可能です |
| `yarn rmadmin`：キュー、ノード、ユーザーグループ、ACL の再読み込み、ノードラベルと属性の変更、その他の管理操作 | クラスターの `admin` |

## 配置

1. `yarn` データサービスを追加してポリシーを設定します。**データ権限 → エージェント** を開き、サービスを選んでエージェントトークンを発行します。トークンは一度しか表示されないため、YARN の実行アカウントだけが読めるファイルに保存します。
2. JAR を **すべての ResourceManager** の `share/hadoop/yarn/lib`（例：`/opt/hadoop/share/hadoop/yarn/lib`）にコピーします。
3. すべての ResourceManager の `yarn-site.xml` に次の設定を追加し、例のアドレス、パス、ユーザー、インスタンス名を置き換えます。

   ```xml
   <configuration>
     <property>
       <name>yarn.acl.enable</name>
       <value>true</value>
     </property>
     <property>
       <name>yarn.authorization-provider</name>
       <value>org.devlive.grantforge.yarn.agent.GrantForgeYarnAuthorizer</value>
     </property>
     <property>
       <name>yarn.admin.acl</name>
       <value>yarn</value>
     </property>
     <property>
       <name>grantforge.server.url</name>
       <value>https://grantforge.example.com</value>
     </property>
     <property>
       <name>grantforge.token.file</name>
       <value>/etc/hadoop/grantforge.token</value>
     </property>
     <property>
       <name>grantforge.instance</name>
       <value>resourcemanager-1</value>
     </property>
     <property>
       <name>grantforge.cache.dir</name>
       <value>/var/lib/hadoop-yarn/grantforge-cache</value>
     </property>
   ```

   `yarn.admin.acl` には ResourceManager 自身のユーザーだけを指定してください。YARN はエージェントにキューを尋ねる前に、管理者にあらゆるアプリケーションへのあらゆる操作を許可します。キャッシュディレクトリは YARN の実行アカウントが書き込める必要があります。
4. ResourceManager を 1 台ずつ再起動します。エージェントが最初のスナップショットを受け取るまで、ResourceManager 自身以外のリクエストは拒否されます。
5. **既存の権限を先に移行してください。** `acl_submit_applications` や `acl_administer_queue` などのスケジューラー設定のキュー ACL は適用されなくなります。事前に対応するポリシーを作成してください。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、この ResourceManager の名前、署名付きスナップショットと保留イベント用の書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショット署名鍵を固定する任意のファイル。エージェントページから Base64 でエンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると最初の接続時にサーバーから鍵を取得します |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを取得する間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、スプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同一の許可アクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ報告します |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 保持する決定の最大数（既定 `100000`、最大 `10000000`、`0` は保持しない）と保持期間（既定 `10000`、`1..3600000` ミリ秒） |

## ユーザー、グループ、ロール

ポリシーのユーザーは、YARN が呼び出し元に与える短い名前です。simple 認証のユーザー名、または `hadoop.security.auth_to_local` による Kerberos プリンシパルの短い名前です。グループは、ResourceManager 上で Hadoop のグループマッピングがユーザーに与えるグループと、GrantForge でユーザーが属するグループです。ロールは GrantForge がユーザーに付与したロールです。

## 決定と制限

- 使用できるスナップショットがない場合、ResourceManager 自身のリクエストを除くすべてが拒否されます。使用されていないサービスは、`yarn.acl.enable` をオフにしたときと同様に YARN をチェックしません。
- タイプが `yarn` でないサービスに紐付いたトークンは、すべてを拒否します。
- YARN は管理権限を尋ねるときにクライアントアドレスを渡さないため、`cluster` ではクライアントアドレス条件は決して成立しません。
- アプリケーションの所有者と、その ACL が指定したユーザーは、YARN と同様にポリシーに関係なくアプリケーションを管理できます。
- アプリケーション優先度 ACL など、他のアクセスタイプのチェックは拒否されます。
- クライアントアドレス条件のホスト名は、保持した決定を使わずに新たに判断するたびにクライアントの名前解決を要します。
- 決定は `grantforge.decision.cache.ttl.ms` の間保持されるため、ポリシーの適用開始や終了は最大でその時間だけ遅れます。

## 関連ガイド

- [Apache Hadoop YARN](/ja/plugins/yarn/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
