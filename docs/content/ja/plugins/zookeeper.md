---
title: Apache ZooKeeper
description: ZooKeeper プラグインをインストール・設定し、znode を参照して znode パスのアクセスポリシーを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache ZooKeeper プラグインは、GrantForge にアンサンブルへの接続、znode パスの検索、ポリシー管理を提供します。プラグイン ID とサービスタイプはどちらも `zookeeper` です。

## インストール

配布物の `plugins/zookeeper` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `zookeeper` が有効か確認し、更新後はプラグインディレクトリを再スキャンします。プラグインは独自のクラスローダーで ZooKeeper 3.9 クライアントを使い、サーバーのライブラリとは分離されています。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して ZooKeeper（`zookeeper`）を選びます。
2. 接続文字列を入力します。アンサンブルのサーバー一覧で、chroot を付けられます（例：`zk1:2181,zk2:2181,zk3:2181/kafka`）。クライアントはサーバー間を自動で切り替えます。
3. 認証方式を選び、検索用 znode を設定して接続をテストし（znode が存在し子ノードを一覧できることを確認）、サービスを保存します。

| 設定 | 用途 |
| --- | --- |
| `zookeeper.connect` | 必須の接続文字列：カンマ区切りの `host[:port]`、必要なら後ろに `/chroot`。chroot は `/` で終われません |
| `authentication` | `none`（既定）、`digest` または `kerberos` |
| `username` | digest：ACL の digest ユーザー（コロン不可）。Kerberos：principal（例：`grantforge@EXAMPLE.COM`） |
| `password` | digest ユーザーのパスワード。暗号化して保存 |
| `keytab` | Kerberos：GrantForge サーバー上の principal の keytab のパス |
| `zookeeper.sasl.client.username` | Kerberos：サーバー principal の先頭部分。既定は `zookeeper`（`zookeeper/<host>@<REALM>`） |
| `zookeeper.server.principal` | Kerberos：サーバー principal がその形式でない場合の完全な principal（例：`zookeeper/zk.example.com@EXAMPLE.COM`） |
| `zookeeper.client.secure` | サーバーの TLS クライアントポートに接続するか。既定は `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | サーバーを検証するトラストストア。空ならサーバーの Java が信頼するものを信頼。種類は `jks`、`pkcs12`、`pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | クライアント証明書を求めるサーバー用のキーストア |
| `session.timeout.ms` | セッションタイムアウト。既定 `15000`、範囲 `2000..120000` |
| `connection.timeout.ms` | 初回接続と認証のみの待機上限。既定 `10000` ms、範囲 `1000..60000`。接続後の同期リクエストには適用しません |
| `request.timeout.ms` | 1 回の同期リクエストの待機上限。既定 `8000` ms、範囲 `1000..60000` |
| `lookup.path` | chroot 以下で検索と参照を始める znode。既定 `/`（例：`/brokers`）。参照はこの znode 内に限られます |
| `lookup.max.entries` | 1 つの znode から一度に読む子ノードの上限。既定 `10000`、範囲 `1..100000` |

## 認証

- **none**：サインインしません。ACL が `world:anyone` に開いている znode だけを読めます。
- **digest**：`username:password` でサインインし、ZooKeeper ACL の `digest` ID に対応します。パスワードが誤っていてもサインインは拒否されず別の ID になるため、保護された znode はアクセス拒否として表示されます。
- **kerberos**：keytab で SASL サインインします。サーバーの `krb5.conf` から KDC に到達できる必要があります。principal と keytab ごとに、プロセスの既存設定とは別の JAAS セクションを持つため、1 つの GrantForge が異なる principal で複数のアンサンブルに接続できます。パスワードだけの Kerberos サインインはまだサポートしていません。

保存時に、接続文字列の形式、digest のユーザーとパスワード、Kerberos の principal と keytab、Kerberos 専用の keytab とサーバー principal、各タイムアウトと上限の範囲を検査します。

TLS を無効にする場合、トラストストアとキーストアのパスとパスワードは空にしてください。ストアのパスワードを指定するには、対応するストアのパスも必要です。両ストアの種類には `jks`、`pkcs12`、`pem` のみを指定できます。

TLS ストアのパスワードの先頭と末尾の空白は、そのまま保持されます。サービス設定によって、この接続の TLS モードと使用するストアが決まり、JVM に既存の ZooKeeper TLS とストアの設定を上書きします。サーバー証明書とホスト名の検証は引き続き行います。

## znode の参照

**データ権限 → ポリシー** でサービスを選び、`path` の横の **参照** を使用します。

- 子ノードを名前順にページ単位で一覧します。子ノードを持つ znode はさらに開けます。
- 各 znode の ACL（ZooKeeper 標準ツールと同じ `scheme:id:cdrwa`）、データサイズ、更新日時を表示します。
- 検索ユーザーが ACL を読めない znode も名前は一覧され、開くと拒否の理由が表示されます。

ページ分割中に znode が削除されても、カーソルは最後に走査した子ノード名から進み、その子ノードが残っている必要はありません。子ノード一覧はスナップショットではなく、同時に行われる追加や削除は ZooKeeper のセマンティクスに従うため、後続ページの内容が変わる場合があります。

パスを入力すると候補が表示されます。存在しない親には候補がなく、`lookup.path` の外のパスは拒否されます。子ノードが `lookup.max.entries` を超える znode は、一部だけを表示せずエラーになります。

## ポリシーの適用

リソースは `path` の 1 階層のみで、アクセスタイプは ZooKeeper の ACL 権限と同じ `read`、`write`、`create`、`delete`、`admin` です。パスのポリシーは再帰と除外に対応します。

サービスプラグインは管理、検索、参照を担当し、サーバー内のエージェントは SASL 認証プロバイダーを通じてマーカー ACL にポリシーを適用します。`mvn -pl agents/grantforge-agent-zookeeper -am package` で、依存ライブラリを含む Java 8 互換の単一 shaded JAR を生成できます。配布物のパスは `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar` です。実装は ZooKeeper 3.6–3.9 API を対象としていますが、実際の各バージョンとの互換性は今後の検証が必要です。全バージョンの組み合わせで検証済み、または本番環境向けの受け入れ検証が完了したという意味ではありません。

### デプロイ

1. クライアントリクエストを受け付けるすべてのノードを含め、**各 ZooKeeper サーバー**の `lib` ディレクトリに JAR を配置します。GrantForge の対応する `zookeeper` データサービスで **Agent 認証情報**を作成します。トークンは独立したファイルに保存し、ZooKeeper の実行アカウントだけが読み取れるようにしてください。トークン自体を `zoo.cfg` に書かないでください。
2. 各サーバーの `zoo.cfg` に以下を設定し、サンプルのアドレス、パス、インスタンス識別子を置き換えます。`grantforge.server.url`（`http[s]://GrantForge`）、`grantforge.token.file`、ローカルの `grantforge.cache.dir`、`grantforge.instance` はすべて必須です。キャッシュディレクトリは ZooKeeper の実行アカウントが書き込める必要があります。

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. このプロバイダーは**標準の SASL プロバイダーを置き換えます**。別の `authProvider.N` に同じ `sasl` スキームのプロバイダーを登録したままにしないでください。`enableEagerACLCheck=false` は必須です。有効にするとプロバイダーの登録が拒否され、マーカーもアクセスを許可しません。通常の ZooKeeper SASL、JAAS、Kerberos 認証設定を維持し、動作を確認してください。エージェントは認証の代わりにはなりません。アンサンブルの保守手順に従ってサーバーを再起動します。
4. 必要に応じて `grantforge.signing.key.file` に、信頼する署名公開鍵を固定するためのファイルを指定します。`grantforge.principal.mapping=short` は既定値で、`kafka/host@REALM` を `kafka` に変換します。`full` は完全な principal を保持します。ポリシーのユーザーは変換後の ID と一致させてください。

### ACL と制限

- GrantForge ポリシーに委ねるのは `sasl:grantforge` マーカー ACL だけです。例は `sasl:grantforge:cdrwa` で、`c=create`、`d=delete`、`r=read`、`w=write`、`a=admin` に対応します。他の SASL ID を含む標準 ACL は従来どおりに判定されます。
- エージェントが動作していない、利用可能なポリシースナップショットがない、その `serviceType` が `zookeeper` ではない、またはポリシーが一致しない／許可しない場合、マーカーはアクセスを拒否します。**これは全体に対する deny ではありません**。他の ACL、`world:anyone`、`super` ID がリクエストを許可する可能性は残ります。
- ポリシーはサーバー上の絶対ターゲットパスに一致させます。`create`／`delete` では、ACL を持つ親ではなく、作成／削除対象の znode が対象です。chroot を使うリクエストでは実際の chroot プレフィックスが必要です。chroot `/kafka` 内の `/brokers` は、ポリシーでは `/kafka/brokers` になります。シーケンシャル `create` は連番追加前のサーバー上の絶対リクエストプレフィックスを検査し、後続の `read`／`delete` は生成された完全なノード名を検査します。
- 標準 ACL は自動移行されず、ACL が再帰的に継承されることもありません。新しい znode にはマーカー ACL を明示的に設定してください。`setACL` を使う前に、小さな範囲で許可と拒否を検証し、独立した管理用アクセス経路を確保してください。ルートノードに不用意にマーカーを設定すると、アクセスできなくなるおそれがあります。
- エージェントを配置していないサーバーは、`sasl:grantforge` を標準 SASL ID `grantforge` として照合し、ポリシーを評価しません。そのため全ノードへの配置が必要であり、一部だけの配置ではアンサンブルを保護できません。
- follower/observer から leader に転送される書き込みリクエストは、このプロバイダーのインターフェースではクライアント接続がない場合があり、`clientAddress` コンテキストが空になる可能性があります。クライアント IP に基づくアンサンブル全体のエンドツーエンドのポリシー適用は保証できません。
- このプロバイダーはリクエストの最終結果を取得できません。そのため最終的な `AccessEvent` 監査イベントは送信せず、完全なアクセス監査は提供しません。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [プラグインとサービスタイプの開発](/ja/develop/plugins/)
