---
title: Apache HBase エージェント
description: HBase の master と region server にコプロセッサーをインストールし、操作と読み書きを GrantForge のポリシーで検査し、ユーザーが読めるカラムだけを返し、使えないものを隠します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

HBase エージェントは master、region、region server のコプロセッサーです。HBase はすべての操作と読み書きについてエージェントに問い合わせ、エージェントは `hbase` データサービスの署名付きポリシーで答えます。配布物には、依存関係を再配置して同梱した Java 8 互換の JAR 1 つ、`agents/hbase/grantforge-agent-hbase-<version>.jar` として含まれます。

リポジトリの Testcontainers 統合テストは、HBase `2.5.16`、`2.6.7`、`3.0.0` の中で JAR を実行します。これは他のすべてのバージョンを認定するものではありません。サービスプラグインは [Apache HBase](/ja/plugins/hbase/) を参照してください。

## 仕組み

- **すべてのリクエストを、指定した各スコープで検査します。** HBase では権限がそのスコープ内のすべてを含むため、エージェントはリクエストの名前空間、テーブル、カラムファミリー、カラムでそれぞれ決定します。いずれかで拒否されれば拒否し、いずれかで許可されれば許可します。
- **名前空間の所有者はそのテーブルを持ちます。** 名前空間の `create` と `admin` があれば、テーブルごとのポリシーなしに、その名前空間を作成し、中にテーブルを作成して管理できます。
- **読み取りは読めるものを返します。** ユーザーがテーブルの一部（カラムファミリー 1 つなど）しか読めない場合、get と scan は失敗せずにそのカラムを返します。テーブルから何も読めないユーザーは拒否されます。
- **一覧から使えないものを除きます。** 名前空間とテーブルの一覧には、ユーザーが使えるもの、またはその中の何かを使えるものだけが表示され、スナップショットの一覧にはユーザーが管理するテーブルのスナップショットだけが表示されます。使えない名前空間の記述は拒否されます。
- **拒否の理由を伝えます。** クライアントは `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)`、または拒否したポリシーを受け取ります。
- **決定を保持し、アクセスをまとめます。** 現在のスナップショットの決定を既定で 100,000 件、10 秒間保持し、新しいスナップショットでは一から始めます。既定 10 秒のウィンドウ内の同一の許可アクセスは件数付きの 1 つのアクセスイベントとして報告され、拒否は 1 件ずつ即座に報告されます。
- **サーバー自身は検査しません。** `hbase.superuser` で指定した HBase のスーパーユーザーとサーバー自身の処理は、検査も監査もしません。`hbase` 名前空間のテーブル（`hbase:meta` など）は、クライアントに必要なため誰でも読めます。

| 操作 | 必要なアクセス |
| --- | --- |
| Get、scan | テーブル、または返されるカラムの `read` |
| カラムの put、increment、append、delete | 書き込む各カラムの `write` |
| カラムファミリーまたは行の削除 | 各カラムファミリーの `write`。HBase は行をファミリーごとに削除します |
| Check-and-mutate | 比較するカラムの `read`（フィルターではテーブルの `read`）と、書き込む対象の `write` |
| テーブルの作成 | 名前空間の `create` |
| 削除、truncate、変更、有効化、無効化、flush、split、マージ、コンパクション | テーブルの `admin` または `create` |
| 移動、割り当て、割り当て解除、オフライン化 | テーブルの `admin` |
| スナップショットの作成、復元、削除、一覧 | テーブルの `admin`。クローンには作成先名前空間の `create` も必要 |
| バルクロード | テーブルの `create` |
| 名前空間の作成、変更、削除 | 名前空間の `admin` |
| 名前空間の記述、名前空間とテーブルの一覧 | それ自体またはその中への何らかのアクセス |
| バランサー、シャットダウン、クォータ、レプリケーションピア、region server グループ、プロシージャ、デコミッション、region server の停止、WAL のロール | `global` の `admin` |

## 配置

1. `hbase` データサービスを追加してポリシーを設定します。**データ権限 → エージェント** を開き、サービスを選択してエージェントトークンを発行します。トークンは一度しか表示されないため、HBase のアカウントだけが読めるファイルに保存してください。
2. JAR を **すべての master と region server** の `lib`（例：`/opt/hbase/lib`）にコピーします。
3. 各ノードの `hbase-site.xml` に設定を追加し、例のアドレス、パス、インスタンス名を置き換えます。

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   キャッシュディレクトリは HBase のアカウントが書き込める必要があります。コプロセッサーの一覧で HBase 自身の `AccessController` を置き換え、両方を読み込まないでください。
4. master と region server を 1 台ずつ再起動します。エージェントが最初のスナップショットを得るまで、スーパーユーザー以外のリクエストは拒否されます。
5. **既存の権限を先に移行してください。** HBase の `AccessController` が `hbase:acl` に保存した権限は適用されなくなります。事前に `user_permission` で一覧し、対応するポリシーを作成してください。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、この master または region server の名前、署名付きスナップショットとキューのイベント用の書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードされた X.509 Ed25519 公開鍵をコピーします。省略すると初回接続時にサーバーから鍵を取得します |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続・読み取りタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを問い合わせる間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスクキューの上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同一の許可アクセスを 1 つのイベントにまとめる期間。既定 `10000`、最大 `3600000` ミリ秒。`0` なら 1 件ずつ報告します |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 保持する決定の最大数。既定 `100000`、最大 `10000000`、`0` なら保持しません。および保持期間。既定 `10000`、`1..3600000` ミリ秒 |

HBase は master、region server、開くすべての region ごとにコプロセッサーを開始します。それらはすべて 1 つのエージェント、決定キャッシュ、イベントのまとめを共有します。

## ユーザー、グループ、ロール

ポリシーでは、HBase が接続のユーザーに与える短縮名を指定します。simple 認証のユーザー名、または `hadoop.security.auth_to_local` による Kerberos プリンシパルの短縮名です。グループは Hadoop のグループマッピングがサーバー上でユーザーに与えるグループと、GrantForge がユーザーを入れたグループで、ロールは GrantForge がユーザーに与えるものです。

## 決定と制限

- 使用できるスナップショットがない場合、スーパーユーザーのリクエストと `hbase` 名前空間の読み取り以外はすべて拒否されます。使用していないサービスは、オーソライザーがない場合と同様に HBase を検査しません。
- トークンが `hbase` 以外のタイプのサービスに紐付いている場合は、すべて拒否します。
- HBase の `grant`、`revoke`、`user_permission` は *AccessController is not loaded* で失敗します。権限は GrantForge のポリシーです。
- コプロセッサーのエンドポイント呼び出しは検査しません。信頼できるエンドポイントだけを読み込んでください。
- ここでは HBase はテーブルの作成者に独自の権限を与えません。上記のような名前空間ポリシーが、所有者にテーブルを与えます。
- HBase は一部の拒否を返さずにログに記録するだけです。たとえばバランサーのスイッチは元のままですが、クライアントには通知されません。
- 決定は `grantforge.decision.cache.ttl.ms` の間保持されるため、ポリシーの有効期間の開始や終了は最大でその分遅れます。
- HBase のセルレベルの権限と可視性ラベルは対象外です。カラムは UTF-8 テキストとして比較します。

## 関連ガイド

- [Apache HBase](/ja/plugins/hbase/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
