---
title: Apache Hadoop HDFS NameNode エージェント
description: Hadoop バージョンに対応する NameNode エージェントで GrantForge パスポリシーを適用し、アクセス監査を報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

サーバープラグイン `grantforge-plugin-hdfs` がリソースと接続を定義します。Hadoop 2.7、2.10、3.2、3.3、3.4、3.5 向けの番号付き NameNode エージェントが、それぞれの SPI でアクセスを検査し、署名付きスナップショット、ポリシー評価、監査報告のロジックを共有します。

共有 HDFS ロジックは `agents/grantforge-agent-hdfs-common`、バージョン別アダプターは `agents/grantforge-agent-hdfs-<line>` にあります。共有のネイティブアダプターは、Java 8 / Hadoop 2.7.7 を基準とする本番 Maven モジュール `agents/grantforge-agent-hdfs-native` に置きます。各番号付きモジュールはそのバイナリを Maven 依存関係として使用し、バージョン固有の入口とコールバックを保持します。共有の本番ソースを各モジュールで再コンパイルしません。`core/grantforge-agent-core` は共通のプロトコルと実行基盤として残ります。

サーバープラグインは単一の `hdfs` サービスタイプを維持し、クライアントのバージョンはエージェントから独立しています。Hadoop 2.x の接続とパス検索には `webhdfs://namenode:50070`、HTTPS では対応する `swebhdfs://` を使用します。Hadoop 3.x は RPC `hdfs://` または WebHDFS を使用できます。コンテナテストは 2.x の WebHDFS と 3.x の両プロトコルを検証します。2.x の RPC は未認定で、設定したプロトコルを自動変更しません。

inode 属性拡張を有効にした Apache Hadoop 2.7.7 では、一般ユーザーが `/` を直接照会すると、エージェントのコールバック前にネイティブコードで null ポインターエラーが発生します。このバージョンのデータ操作と `lookup.path` には `/data` などの実際のデータディレクトリを使用してください。コンテナテストはこのルートパスの制限も明示的に検証します。

## HDFS エージェントの対象バージョン

| Hadoop 基準バージョン | コンテナーの Java | エージェントのディレクトリー |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8（amd64 イメージ） | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

クラスターの Hadoop 系列に合わせて、`agents/hdfs/<line>/` から `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` を選択します。共有ロジックは Java 8、3.5 アダプターは Java 17 を対象とします。

Hadoop 2.7、2.10、3.2、3.3 には、エージェントが利用するスーパーユーザー認可コールバックがありません。これらのスーパーユーザーのアクセスは Hadoop が管理します。GrantForge ポリシーで制御するデータアクセスには一般ユーザーを使ってください。

## 権限の関係

以下の適用動作は一般ユーザーのアクセスを説明します。スーパーユーザーの制限は上記およびコールバックの説明を参照してください。

エージェントはまず HDFS のネイティブ権限チェックを行い、その後に GrantForge のポリシーを適用します。ユーザーはネイティブ権限とポリシーの両方を満たす必要があります。GrantForge の許可ポリシーは POSIX 権限、ACL、所有者チェック、sticky bit を迂回することはなく、拒否ポリシーは常に拒否します。ネイティブ権限の設定は、引き続き Hadoop の管理ツールで保守します。

既定値の `grantforge.hdfs.native.fallback=false` では、ローカルのポリシースナップショットがない場合、一致するポリシーがない場合、またはエージェントがまだ起動していない場合に、データアクセスを拒否します。`true` に設定すると、ポリシーが判定しなかったアクセスはネイティブ権限を使い、明示的な拒否ポリシーはそのまま有効です。サーバーに一時的に到達できない間も、最後に署名検証を通ったローカルのスナップショットを使い続けます。

1 回の認可コールバックに含まれる祖先、対象、サブツリー、スナップショットのパス投影は、すべて同じバージョンのポリシースナップショットを使います。更新されたポリシーは次のコールバックから有効になるため、異なるバージョンの許可ルールが組み合わさることはありません。アクセス監査には、実際に使われたポリシーのバージョンが記録されます。

エージェントは一般ユーザーが対象にアクセスするために必要な `read`、`write`、`execute` を検査し、親ディレクトリー、祖先ディレクトリー、再帰的な検証が必要なサブディレクトリーも検査します。作成、削除、名前変更などの操作は複数のパスに関わるため、許可ポリシーがそれらをすべてカバーしている必要があります。厳格モードでは、対象ファイルに `read` ポリシーがあるだけでは不十分で、ユーザーに祖先ディレクトリーの `execute` ポリシーが必要です。たとえば `/` の `execute` を許可して再帰をチェックし、その後、実際のデータディレクトリーに読み取りと書き込みの権限を付与してください。

スナップショットのパスは、実際に要求されたパスと、`.snapshot/<スナップショット名>` を除いた元のパスの両方を検査します。たとえば `/data/.snapshot/s1/secret` は `/data/secret` としても検査されます。これにより、元のパスの拒否ポリシーもスナップショットに及び、明示的なスナップショットのパスには、より厳しい制限を別途設けられます。メタデータの照会は、HDFS のディレクトリー巡回権限の意味をそのまま引き継ぎます。

1 回の再帰的な認可で検査するのは最大 `100000` 個の inode で、上限を超える場合は NameNode 内でメモリを無限に割り当てないように操作を拒否します。極端に長いパスはパス全体でポリシーを判定し、監査でのリソース表示は `1000` 文字に制限し、リクエストの詳細に元の長さと SHA-256 ダイジェストを記録します。

以下のスーパーユーザーコールバックは Hadoop 3.4 と 3.5 に適用されます。HDFS のスーパーユーザーは引き続き Hadoop が管理します。パス付きのスーパーユーザーコールバックは、まず Hadoop のスーパーユーザーチェックを通った後、Hadoop 3.4 / 3.5 が提供する操作名でポリシーを検査します。ファイルの読み取りとメタデータの照会では `read`、ディレクトリーの列挙では `read` + `execute`、既知の変更操作では `write` が必要です。未知、欠落、または正確に推定できない操作（例: `checkAccess` と `concat`）は、保守的に 3 つの権限をすべて要求します。

スーパーユーザーコールバックには完全な inode とサブツリーのコンテキストがなく、パスのないクラスター管理呼び出しはネイティブチェックを維持します。サブディレクトリーのポリシーでスーパーユーザーのすべての再帰操作を制限することはできません。データの利用者は一般の Hadoop ユーザーを使ってください。

## 指標

エージェントは Hadoop の Metrics2 の仕組みを通じて指標を報告し、NameNode 自身の dfs 指標と同じ sink を使います。NameNode の JMX では `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` として現れ、Prometheus の JMX エクスポーターがそのまま取得できます。すべての指標は `instance`（データサービスのインスタンス名）と `agentVersion` のタグを持つため、HA の 2 つの NameNode を区別して確認できます。指標の登録に失敗しても失うのは指標だけで、エージェントは警告を記録し、指標なしで認可を続行します。

| 指標 | 説明 |
| --- | --- |
| `Callbacks` | エージェントが実行した認可コールバックの数 |
| `SuperuserCallbacks` | エージェントが実行したスーパーユーザーコールバックの数 (3.4 / 3.5) |
| `NativeDenies` | エージェントより前に Hadoop が拒否したアクセスの数 |
| `EvaluationFailures` | ポリシーの評価の例外によりフェイルクローズしたコールバックの数 |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | ポリシーが許可 / 拒否 / 未決と判定した権限の数。未決は厳格モードでも同様に拒否します |
| `MissingSnapshots` | 検証済みのポリシースナップショットがない間に対処したコールバックの数 |
| `SnapshotVersion` | 現在使っているポリシースナップショットのバージョン。なければ 0 |
| `QueuedEvents` / `DroppedEvents` | メモリ上で報告を待っている監査イベントの数 / キューまたはディスクバッファが満杯で破棄されたイベントの数 |
| `ServerReachable` | 最後のポリシーサーバーへのアクセスが成功したかどうか（1/0） |

## デプロイ

1. GrantForge のデータサービスに `hdfs` サービスを追加し、設定を保存して接続をテストします。実際の Hadoop の短いユーザー名、ユーザーグループ、またはロールにパスポリシーを設定します。
2. 「データ権限 → エージェント」でこのサービスのトークンを発行します。トークンの原文を各 NameNode のローカルファイルに書き込みます。たとえば `/etc/hadoop/grantforge/token` とし、NameNode を実行するユーザーが読み取れるようにします。
3. クラスターの Hadoop 系列に合わせて、`agents/hdfs/<line>/` の `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` を NameNode のクラスパスにコピーします。例：`$HADOOP_HOME/share/hadoop/hdfs/lib/`。対応するアダプターを 1 つだけ配置してください。jar は共有ロジック、Jackson、署名ライブラリを含み、Hadoop のクラスは NameNode が提供します。
4. 各 NameNode の `hdfs-site.xml` に次のプロパティを設定します。HA の 2 つの NameNode は、異なる `instance` とそれぞれローカルのキャッシュディレクトリーを使います。

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. `dfs.permissions.enabled=true` であること、および `dfs.namenode.inode.attributes.provider.bypass.users` が空であることを確認します。認可コールバックを迂回できる設定は、エージェントの起動時に拒否します。NameNode を再起動し、その後 GrantForge のエージェントページでハートビートとポリシーのバージョンを確認します。エージェントは NameNode の既存の設定を読み取るだけで、inode のネイティブプロパティは変更しません。

初回のデプロイではまず `native.fallback=true` を使い、ポリシーのスナップショットが同期され、祖先ディレクトリーの権限が整ったことを確認してから、厳格モードに切り替えることができます。トークンがバインドしているサービスタイプは必ず `hdfs` である必要があります。設定が誤っている場合や、他のサービスタイプにバインドされている場合は、アクセスが拒否されます。

## 任意の設定

| プロパティ | 既定値 | 用途 |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | GrantForge に接続するときのタイムアウト |
| `grantforge.hdfs.read.timeout.ms` | `8000` | 応答を読み取るときのタイムアウト |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | サーバーに到達できないときのポリシー更新間隔。少なくとも `1000`。通常のハートビートはサーバーが提案した間隔を使います |
| `grantforge.hdfs.signing.key.file` | 未設定 | 任意の署名用公開鍵ファイル。内容はコンソールが提供する Base64 の X.509 公開鍵です。設定すると、その公開鍵の署名のみを受け入れます |
| `grantforge.hdfs.audit.batch.size` | `500` | 1 回の報告での最大イベント数。範囲は `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | メモリ上の監査キューの容量。範囲は `1..1000000` で、少なくとも 1 バッチ分は保持できる必要があります。キューが満杯になると、新しいイベントを数えて破棄します |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | 監査のフラッシュ間隔。正の整数で、最大 `2147483647`。小さくすると報告の遅延を抑えられます |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | サーバーに到達できないときのディスクバッファの上限。0 以上の整数。`0` でディスクバッファを無効にします |

アクセス量が急に多くなったときは、監査キューを大きくしてキューのあふれを減らし、フラッシュ間隔を短くすると監査の遅延は減りますが、報告の頻度は増えます。ディスクバッファの上限は、長時間の切断中のディスク使用量を抑えるものであり、バッファを無効にしたり使い切ったりするとイベントを失う可能性があります。監査の報告はバックグラウンドで実行され、ポリシーサーバーの応答は待ちません。

署名用公開鍵を設定していない場合、エージェントは初回にサーバーへアクセスしたときに公開鍵を取得し、スナップショットと一緒に保存します。エージェントは Hadoop が渡した短いユーザー名とユーザーグループを使い、ロールと追加のグループは署名付きスナップショットから取得します。Kerberos principal の短い名前へのマッピングは、クラスターの `hadoop.security.auth_to_local` が決めます。

## ソースからのビルドと検証

以下は 3.5 の例です。対象アダプターに合わせて 2.7、2.10、3.2、3.3、3.4 に置き換えてください。

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

エージェントの成果物は `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar` として生成されます。単体テストは NameNode の認可コールバック、設定、バージョンのメタデータ、ポリシーの判定を扱い、WebHDFS と Kerberos のテストはローカルの一時的なサービスを起動します。本番投入前には、対象のクラスターでも読み取りと書き込み、作成、名前変更、再帰的な削除、HA の切り替え、切断後のキャッシュの動作を検証する必要があります。

統合の検証は `verify` フェーズで Testcontainers を使って実行します（単体テストはクラスターを起動しません。`verify` には利用可能な Docker daemon が必要です）。

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# nightly と同じエントリーポイントを使います
bash script/ci/hdfs_integration.sh 3.5
# 単一系列または全バージョンの検証
bash script/ci/hdfs_integration.sh all
```

テストはバージョンが固定された Apache Hadoop のコンテナイメージを使い、実際にパッケージングしたエージェント jar を NameNode のクラスパスに入れます。Testcontainers は分離されたネットワークを作成し、NameNode と DataNode のライフサイクルを管理します。検証するのは、読み取りと書き込み、作成、追加、名前変更、削除、再帰とスナップショットの拒否、ネイティブ権限と監査、ポリシーの更新、ポリシーサーバーを切断した後に署名付きキャッシュで NameNode を再起動する場合、スナップショットがないときの厳格モードとネイティブ権限へのフォールバックです。

実行中の Docker daemon が必要で、テストイメージをダウンロードできる必要があります。Docker を利用できない場合、テストは失敗し、黙ってスキップされることはありません。ファイルシステムクライアントは Hadoop コンテナの内部で実行され、ポリシーの HTTP サービスは Testcontainers のホストポートフォワーディングを使うため、外部の Hadoop クラスターは不要です。テストの終了後、コンテナとテストネットワークを片付け、ログは `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers` に保存します。

HA のテストは NameNode 2 台、DataNode 1 台、JournalNode 1 台を起動し、2 つのエージェントにそれぞれ個別のインスタンス名とキャッシュディレクトリーを設定します。論理 HDFS クライアントでアクティブノードを手動で切り替え、切り替え後の読み取りと書き込み、拒否ポリシーを検証します。JournalNode 1 台はテスト用であり、過半数の障害耐性は検証せず、ZooKeeper の自動フェイルオーバーにも関係しません。

自動 HA テスト（Hadoop 3.5.0 のみ、Java 17 と 21）は ZooKeeper、JournalNode 3 台、それぞれ ZKFC を持つ NameNode 2 台、DataNode 1 台を起動します。アクティブな NameNode を強制停止すると ZooKeeper がもう一方に引き継がせ、引き継いだノードがポリシーを適用し続け、拒否が自身のインスタンスに記録されること、NameNode の不在中に公開したポリシーが Standby として復帰した時点で適用され、役割を戻した後も古いバージョンに戻らず適用されること、ポリシーサーバーが使えない間は引き継いだ NameNode が保持するスナップショットで適用を続け、サーバー復帰後に監査を送ること、JournalNode 3 台のうち 1 台停止では書き込みが成功し、2 台停止では書き込みが失敗してアクティブな NameNode がクォーラムなしで続行せず停止することを検証します。Kerberos 版では、JournalNode も keytab でサインインして HTTPS のみを提供し、ZKFC は SASL で ZooKeeper に認証し、選挙用の znode は NameNode の principal だけが使えます（認証されていない ZooKeeper クライアントは読み取りもできません）。アクティブな NameNode を停止するともう一方が引き継いで Kerberos ユーザーにポリシーを適用し続けること、チケットのないクライアントが常に拒否されること、最初の NameNode が戻ると役割を戻せることを検証します。JournalNode は NameNode と同じ短縮名の principal で動作します。JournalNode は edits を NameNode の完全な principal か自分と同じ短縮名の要求者にしか渡さず、HTTP 認証は短縮名しか渡さないためです。

Kerberos テストは Hadoop 3.5.0 でのみ実行します（Java 17 と 21）。KDC はテスト JVM 内で動き、NameNode と DataNode はそれぞれの keytab を使ってセキュアモードで起動し、DataNode は SASL を経たデータ転送と HTTPS のみを提供し、WebHDFS は SPNEGO を使います。principal が短い名前に対応付けられたうえで GrantForge のポリシーが読み書きに適用されること、ポリシーのないユーザーが拒否されること、拒否が短い名前で監査されること、チケットのないクライアントが simple 認証に戻らずに拒否されること、再起動した NameNode が再度サインインしてポリシーを適用し続けることを検証します。他のバージョンラインの Kerberos は未検証です。

共有のネイティブ単体テストは `agents/grantforge-agent-hdfs-native/src/test` で実行し、6 つのアダプターにはコンパイルしません。バージョン固有のコールバックのテストは各番号付きモジュールに残します。共有のコンテナーテストのソースは引き続き `agents/grantforge-agent-hdfs-common/src/test/shared` に置き、番号付きの本番モジュールにコンパイルします。テスト専用 Maven プロジェクトは作成せず、Testcontainers はテスト依存関係に限定します。nightly は Java 17/21 のテストホストで 6 つの Hadoop バージョンを検証し、コンテナー内部の Java は上記の表に従います。レポートとログを保存します。

Hadoop の拡張エントリーポイントと権限の意味については、[Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) と [HDFS 権限ガイド](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html) を参照してください。
