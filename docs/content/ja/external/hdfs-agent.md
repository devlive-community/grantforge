---
title: Apache Hadoop HDFS NameNode エージェント
description: Hadoop バージョンに対応する NameNode エージェントで GrantForge パスポリシーを適用し、アクセス監査を報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

サーバー側の `grantforge-plugin-hdfs` が `hdfs` サービス種別、リソース、接続設定をまとめて提供します。NameNode エージェントは Hadoop のバージョンごとにビルドされ、そのバージョンの `INodeAttributeProvider` と `AccessControlEnforcer` でアクセスを検査し、署名付きポリシー、キャッシュ、ポリシー評価、監査のロジックを共有します。

ソースでは、サーバー側プラグインは `plugins/grantforge-plugin-hdfs`、バージョン別エージェントは `agents/grantforge-agent-hdfs-<line>`、Hadoop に依存しない共通の本番ロジックは `agents/grantforge-agent-hdfs-common` にあります。共通のネイティブアダプターは正式な Maven 本番モジュール `agents/grantforge-agent-hdfs-native` にあり、Java 8 / Hadoop 2.7.7 を基準にしています。番号付きの各モジュールは Maven のバイナリ依存としてこれを参照し、バージョンごとの入口と固有のコールバックだけを持ち、共通の本番ソースを重複してコンパイルしません。プロトコル、スナップショットキャッシュ、監査の基盤は引き続き `core/grantforge-agent-core` にあります。

サーバー側プラグインは `hdfs` 種別を 1 つだけ持ち、クライアントのバージョンはエージェントのバージョンとは独立しています。Hadoop 2.x の接続とパス検索には `webhdfs://namenode:50070`（HTTPS を有効にした場合は対応する `swebhdfs://` アドレス）を使います。Hadoop 3.x では RPC の `hdfs://` と WebHDFS のどちらも使えます。コンテナーのマトリクスは 2.x の WebHDFS と 3.x の RPC・WebHDFS を検証しています。2.x の RPC は認定しておらず、接続プロトコルを自動で切り替えることはありません。

Apache Hadoop 2.7.7 では inode 属性の拡張を有効にすると、一般ユーザーがルートパス `/` を直接問い合わせたときに、エージェントのコールバックより前にネイティブコードでヌルポインターが発生します。このバージョンではデータ操作とサービスの `lookup.path` に `/data` のような実際のデータディレクトリーを使ってください。テストはルートパスが失敗することを独立したアサーションとして残しています。

## バージョンの選択

| エージェントモジュールのサフィックス | 検証済みの Apache Hadoop | コンテナーの JVM | 認可インターフェース | スーパーユーザーパスのコールバック |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | パラメーター方式 | なし |
| `2.10` | 2.10.2 | Java 8 | パラメーター方式 | なし |
| `3.2` | 3.2.4 | Java 8 | パラメーター方式 | なし |
| `3.3` | 3.3.6 | Java 8 | コンテキスト方式 | なし |
| `3.4` | 3.4.3 | Java 11 | コンテキスト方式、スーパーユーザーと拒否のコールバック | あり |
| `3.5` | 3.5.0 | Java 17 | コンテキスト方式、スーパーユーザーと拒否のコールバック | あり |

たとえば Hadoop 2.10.2 には `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar` をインストールします。各 NameNode にはバージョン別のエージェントを 1 つだけインストールし、アップグレード時は古い jar を削除します。設定の provider クラス名は変わりません。エージェントは起動時に Hadoop のメジャー／マイナーバージョンと必須のコールバックを確認し、バージョンが違えば起動を中止します。同じメジャー／マイナーバージョンのほかのパッチやベンダーのディストリビューションは個別に検証する必要があります。

`common` と `2.7`〜`3.4` のエージェント本体は Java 8 のバイトコード、`3.5` は Java 17 を使います。上の表は統合テストで実際に使った JVM であり、3.4.3 を Java 8 で検証したという意味ではありません。3.3.6 の公式テストイメージは amd64 だけを提供しているため、ARM ホストでは明示的に amd64 のエミュレーションで実行します。

## 権限の関係

エージェントは先に HDFS ネイティブの権限チェックを行い、その後で GrantForge のポリシーを適用します。ユーザーはネイティブの権限とポリシーの両方を満たす必要があります。GrantForge の許可ポリシーが POSIX 権限、ACL、所有者チェック、sticky bit を迂回することはなく、拒否ポリシーは常に拒否します。ネイティブの権限設定は引き続き Hadoop の管理ツールで維持します。

既定値は `grantforge.hdfs.native.fallback=false` です。ローカルのポリシースナップショットがない、一致するポリシーがない、またはエージェントがまだ起動していない場合、データアクセスは拒否されます。`true` にすると、ポリシーで決まらないアクセスにはネイティブの権限を使います。明示的な拒否ポリシーは引き続き有効です。サーバーに一時的に到達できない間は、署名検証に合格した最後のローカルスナップショットを使い続けます。

1 回の認可コールバック内の祖先、対象、サブツリー、スナップショットパスの投影には同じバージョンのポリシースナップショットを使います。更新されたポリシーは次のコールバックから有効になり、異なるバージョンの許可ルールが混ざることはありません。アクセス監査には実際に使ったポリシーのバージョンを記録します。

エージェントは一般ユーザーが対象にアクセスするのに必要な `read`、`write`、`execute` を検査し、親ディレクトリー、祖先ディレクトリー、再帰的な確認が必要なサブディレクトリーも検査します。作成、削除、名前変更などの操作は複数のパスに関わるため、許可ポリシーはそれらをすべてカバーしなければなりません。厳格モードでは対象ファイルの `read` ポリシーだけでは足りず、祖先ディレクトリーの `execute` ポリシーもユーザーに設定する必要があります。たとえば `/` に対する `execute` を再帰付きで許可し、実際のデータディレクトリーに読み書きの権限を設定します。

スナップショットパスは、実際に要求されたパスと `.snapshot/<スナップショット名>` を取り除いた元のパスの両方を検査します。たとえば `/data/.snapshot/s1/secret` は `/data/secret` も検査します。そのため元のパスに対する拒否ポリシーはスナップショットにも及びます。明示的なスナップショットパスにさらに厳しい制限を設定することもできます。メタデータの問い合わせは HDFS のディレクトリー走査の権限セマンティクスに従います。

1 回の再帰的な認可で検査する inode は最大 `100000` 個で、上限を超えると操作を拒否し、NameNode 内で際限なくメモリーを確保することを防ぎます。非常に長いパスは完全なパスでポリシーを判定します。監査のリソース表示は `1000` 文字に制限され、リクエストの詳細に元の長さと SHA-256 ダイジェストを記録します。

Hadoop 2.7、2.10、3.2、3.3 はエージェントを呼び出す前にスーパーユーザーを素通りさせるため、エージェントはそれらのアクセスを制御も監査もできません。Hadoop 3.4、3.5 のパス付きスーパーユーザーコールバックは、先にネイティブチェックを通ったうえで操作名に応じてポリシーを検査します。ファイルの読み取りとメタデータの問い合わせには `read`、ディレクトリーの列挙には `read` + `execute`、既知の変更操作には `write` が必要です。未知、欠落、または正確に推定できない操作（`checkAccess` や `concat` など）には、保守的に 3 つの権限すべてを要求します。

スーパーユーザーのコールバックには完全な inode とサブツリーの文脈がないため、パスを伴わないクラスター管理の呼び出しはネイティブチェックのままです。サブディレクトリーのポリシーでスーパーユーザーのすべての再帰操作を制限することはできません。データを使う人は一般の Hadoop ユーザーを使ってください。

## メトリクス

エージェントは Hadoop の Metrics2 でメトリクスを報告し、NameNode 自身の dfs メトリクスと同じ sink を使います。NameNode の JMX では `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` として現れます（Prometheus の JMX エクスポーターでそのまま収集できます）。各メトリクスには `instance`（データサービスのインスタンス名）と `agentVersion` のラベルが付くため、HA の 2 台の NameNode を別々に確認できます。メトリクスの登録に失敗しても失われるのはメトリクスだけで、エージェントは警告を記録し、メトリクスなしで認可を続けます。

| メトリクス | 説明 |
| --- | --- |
| `Callbacks` | エージェントが実行した認可コールバックの数 |
| `SuperuserCallbacks` | エージェントが実行したスーパーユーザーコールバックの数 |
| `NativeDenies` | エージェントより前に Hadoop が拒否したアクセスの数 |
| `EvaluationFailures` | ポリシー評価の例外によりフェイルクローズしたコールバックの数 |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | ポリシーが許可 / 拒否 / 未決定と判断した権限の数。未決定も厳格モードでは拒否されます |
| `MissingSnapshots` | 検証済みのポリシースナップショットがない状態で処理したコールバックの数 |
| `SnapshotVersion` | 現在使っているポリシースナップショットのバージョン。0 はなしを表します |
| `QueuedEvents` / `DroppedEvents` | メモリー内で報告待ちの監査イベント数 / キューまたはディスクバッファーが満杯で破棄したイベント数 |
| `ServerReachable` | 直近のポリシーサーバーへのアクセスが成功したか（1/0） |

## デプロイ

1. GrantForge のデータサービスに `hdfs` サービスを追加し、設定を保存して接続をテストします。実際の Hadoop の短いユーザー名、ユーザーグループ、ロールに対してパスのポリシーを設定します。
2. 「データ権限 → エージェント」でこのサービスのトークンを発行します。トークンの原文を各 NameNode のローカルファイル（例: `/etc/hadoop/grantforge/token`）に書き込み、NameNode の実行ユーザーが読めるようにします。
3. 上の表に従って配布パッケージの `agents/hdfs/<Hadoop バージョン系列>/` から対応するエージェント jar を選び、NameNode のクラスパス（例: `$HADOOP_HOME/share/hadoop/hdfs/lib/`）に置きます。エージェント jar は独自のポリシーエンジン、Jackson、署名ライブラリーを含み、Hadoop のクラスは NameNode が提供します。ハートビートのエージェントバージョンには製品バージョンとコンパイルに使った Hadoop のバージョンが含まれます。
4. 各 NameNode の `hdfs-site.xml` に次のプロパティーを設定します。HA の 2 台の NameNode には別々の `instance` とそれぞれローカルのキャッシュディレクトリーを使います。

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

5. `dfs.permissions.enabled=true` であり、`dfs.namenode.inode.attributes.provider.bypass.users` が空であることを確認します。エージェントは認可コールバックを迂回できる設定では起動を拒否します。NameNode を再起動し、GrantForge のエージェント画面でハートビートとポリシーのバージョンを確認します。エージェントは NameNode の既存の設定を読み取るだけで、inode のネイティブ属性は変更しません。

初回のデプロイでは、まず `native.fallback=true` で始め、ポリシースナップショットが同期されたことを確認し、祖先ディレクトリーの権限を補ってから厳格モードに切り替えられます。トークンに紐付くサービス種別は `hdfs` でなければなりません。設定の誤りや別のサービス種別への紐付けはアクセス拒否になります。

## オプション設定

| プロパティー | 既定値 | 用途 |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | GrantForge への接続のタイムアウト |
| `grantforge.hdfs.read.timeout.ms` | `8000` | 応答の読み取りのタイムアウト |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | サーバーに到達できないときのポリシー更新間隔。最小 `1000`。通常のハートビートはサーバーが推奨する間隔を使います |
| `grantforge.hdfs.signing.key.file` | 未設定 | 任意の署名公開鍵ファイル。内容はコンソールが提供する Base64 の X.509 公開鍵で、設定するとこの鍵の署名だけを受け入れます |
| `grantforge.hdfs.audit.batch.size` | `500` | 1 回に報告する最大イベント数。範囲は `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | メモリー内の監査キューの容量。範囲は `1..1000000` で、少なくとも 1 バッチを収められる必要があります。満杯になると数を数えて新しいイベントを破棄します |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | 監査のフラッシュ間隔。正の整数で最大 `2147483647`。小さくすると報告の遅延が減ります |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | サーバーに到達できないときのディスクバッファーの上限。0 以上の整数で、`0` はディスクバッファーを無効にします |

アクセスが急増する場合は監査キューを大きくするとキューのあふれが減ります。フラッシュ間隔を短くすると監査の遅延は下がりますが、報告の頻度は上がります。ディスクバッファーの上限は長時間の切断時のディスク使用量を抑えるためのもので、無効にしたり使い切ったりするとイベントが失われることがあります。監査の報告はバックグラウンドで行われ、ポリシーサーバーの応答を待ちません。

署名公開鍵を設定していない場合、エージェントは最初にサーバーから公開鍵を取得し、スナップショットと一緒に保存します。エージェントは Hadoop から渡される短いユーザー名とユーザーグループを使い、ロールと追加のグループは署名付きスナップショットから取得します。Kerberos principal から短い名前への対応はクラスターの `hadoop.security.auth_to_local` で決まります。

## ソースからのビルドと検証

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

エージェントの成果物は `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar` にあります。ユニットテストは NameNode の認可コールバック、設定、バージョンのメタデータ、ポリシーの判断をカバーし、WebHDFS と Kerberos のテストはローカルに一時的なサービスを起動します。本番投入の前に、対象クラスターで読み書き、作成、名前変更、再帰削除、HA の切り替え、切断後のキャッシュの動作も検証してください。

統合検証は `verify` フェーズで Testcontainers を使って実行されます（ユニットテストはクラスターを起動しません。`verify` には利用可能な Docker デーモンが必要です）。

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# nightly と同じ入口
bash script/ci/hdfs_integration.sh
# 指定したバージョン系列だけを検証
bash script/ci/hdfs_integration.sh 2.10
```

テストはバージョンを固定した Apache Hadoop のイメージを使います。古いバージョンのイメージは、ダイジェストを固定した Java 8 のベースイメージと SHA-512 で検証した Apache の配布物からビルドします。対応するバージョンの実際のエージェント jar を NameNode のクラスパスに置き、実際の Hadoop と JVM のバージョンをアサートします。Testcontainers は分離されたネットワークを作成して NameNode と DataNode のライフサイクルを管理し、読み書き、作成、追記、名前変更、削除、再帰とスナップショットの拒否、ネイティブ権限と監査、ポリシーの更新、ポリシーサーバーから切断したあと NameNode を再起動して署名付きキャッシュを使うこと、スナップショットがないときの厳格モードとネイティブ権限へのフォールバックを検証します。

実行中の Docker デーモンと、テストイメージのダウンロードの許可が必要です。Docker が使えない場合、テストは黙ってスキップされずに失敗します。ファイルシステムのクライアントは Hadoop コンテナーの中で実行され、ポリシーの HTTP サービスには Testcontainers のホストポート転送を使うため、外部の Hadoop クラスターは不要です。テストの終了後にコンテナーとテストネットワークを片付け、ログは `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers` に保存します。

HA テストは NameNode 2 台、DataNode 1 台、JournalNode 1 台を起動し、2 つのエージェントに別々のインスタンス名とキャッシュディレクトリーを設定します。論理 HDFS クライアントでアクティブなノードを手動で切り替え、切り替え後の読み書きと拒否ポリシーを検証します。JournalNode 1 台はテスト専用で、過半数による耐障害性は検証せず、ZooKeeper による自動フェイルオーバーも扱いません。

自動 HA テスト（Hadoop 2.10〜3.5、Java 17 と 21）は ZooKeeper、JournalNode 3 台、それぞれ ZKFC を持つ NameNode 2 台、DataNode 1 台を起動します。テストでは次を検証します。アクティブな NameNode を直接停止すると ZooKeeper が別のノードを選んで引き継ぎ、新しいアクティブノードがポリシーを引き続き適用し、拒否イベントはそのノードのインスタンスに帰属すること。ノードが離れている間に公開された新しいポリシーは Standby として復帰した後に有効になり、切り戻した後も古いバージョンに戻らず新しいバージョンを適用すること。ポリシーサービスが使えない間は、引き継いだノードが読み込み済みのスナップショットで適用を続け、サービスの復旧後に監査を再送すること。JournalNode 3 台のうち 1 台を止めても書き込みは成功し、2 台を止めると書き込みが失敗してアクティブな NameNode が停止し、過半数がないまま続行しないこと。さらに Kerberos 版（Hadoop 3.5.0 のみ）もあります。JournalNode も keytab でログインして HTTPS だけを公開し、ZKFC は SASL で ZooKeeper に認証し、選出用のノードには NameNode の principal だけがアクセスできます（認証していない ZooKeeper クライアントは読み取りさえ拒否されます）。テストでは、アクティブな NameNode を止めると別のノードが引き継いで Kerberos ユーザーへのポリシー適用を続け、チケットのないクライアントは常に拒否され、元のノードが復帰すると切り戻せることを検証します。JournalNode は NameNode と同じ短い名前の principal で動かします。JournalNode は edits を NameNode の完全な principal か、自分と同じ短い名前の要求元にしか渡さず、HTTP の認証では短い名前しか得られないためです。Hadoop 2.7 の自動 HA は検証していません。そのクライアントはすべての NameNode のホスト名を解決できるときにしか切り替えず、テストで停止したコンテナーの名前は解決できなくなるため、クライアントは別のノードを試さずに UnknownHostException を報告します。

Kerberos テストは 6 つのバージョン系列すべてで実行されます（Java 17 と 21）。KDC はテストの JVM 内で動き、NameNode と DataNode はそれぞれ keytab を使ってセキュアモードで起動します。DataNode のデータ転送には SASL が必要で HTTPS だけを公開し、WebHDFS は SPNEGO を使います。テストでは、principal を短い名前に対応付けたうえで GrantForge のポリシーが読み書きに効くこと、ポリシーのないユーザーが拒否されること、拒否イベントが短い名前で監査されること、チケットのないクライアントが拒否され simple 認証にフォールバックしないこと、NameNode の再起動後に再ログインしてポリシーの適用を続けることを検証します。

テストのソースは実際の本番モジュールの `src/test` にあります。共通のポリシーのユニットテストは common で、共通のネイティブのユニットテストは `agents/grantforge-agent-hdfs-native/src/test` で実行され、6 つのバージョン別エージェントにはコンパイルされません。バージョンごとのコールバックのテストは対応する番号付きモジュールに残り、共通のコンテナーテストのソース `agents/grantforge-agent-hdfs-common/src/test/shared` は引き続き各番号付きモジュールにコンパイルされます。独立した Maven のテストプロジェクトはありません。Testcontainers は test スコープだけです。nightly は Java 17 と 21 のホストで 6 つの Hadoop バージョンを検証し、Hadoop のコンテナーには上の表の JVM を使います。現在のコンテナーは Simple 認証、手動 HA、6 つすべてのバージョン系列での Kerberos と HTTPS、2.10〜3.5 の自動 HA、Hadoop 3.5.0 での Kerberos 下の自動 HA をカバーしています。2.7 の自動 HA、ほかのバージョンでの Kerberos 下の自動 HA、ベンダーのパッチは対象環境での検証が必要です。

Hadoop の拡張ポイントと権限のセマンティクスは [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) と [HDFS 権限ガイド](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html) を参照してください。
