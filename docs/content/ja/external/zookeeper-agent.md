---
title: Apache ZooKeeper エージェント
description: すべての ZooKeeper ノードにエージェントを配置し、マーカー ACL で GrantForge ポリシーを適用します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

サーバープラグイン `grantforge-plugin-zookeeper` が `zookeeper` サービスタイプ、リソース、接続設定を一括して提供します。ポリシーを適用するには、ZooKeeper 内部で動作するもう一つの構成要素が必要です。それが SASL 認証プロバイダーとしてサーバーの ACL 判定に加わるエージェントです。エージェントは `agents/grantforge-agent-zookeeper` にあり、`mvn -pl agents/grantforge-agent-zookeeper -am package` で依存ライブラリを含む Java 8 互換の単一 shaded JAR を生成できます。配布物のパスは `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar` です。実装は ZooKeeper 3.6–3.9 API を対象としていますが、実際の各バージョンとの互換性は今後の検証が必要です。全バージョンの組み合わせで検証済み、または本番環境向けの受け入れ検証が完了したという意味ではありません。

## デプロイ

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

## ACL と制限

- GrantForge ポリシーに委ねるのは `sasl:grantforge` マーカー ACL だけです。例は `sasl:grantforge:cdrwa` で、`c=create`、`d=delete`、`r=read`、`w=write`、`a=admin` に対応します。他の SASL ID を含む標準 ACL は従来どおりに判定されます。
- エージェントが動作していない、利用可能なポリシースナップショットがない、その `serviceType` が `zookeeper` ではない、またはポリシーが一致しない／許可しない場合、マーカーはアクセスを拒否します。**これは全体に対する deny ではありません**。他の ACL、`world:anyone`、`super` ID がリクエストを許可する可能性は残ります。
- ポリシーはサーバー上の絶対ターゲットパスに一致させます。`create`／`delete` では、ACL を持つ親ではなく、作成／削除対象の znode が対象です。chroot を使うリクエストでは実際の chroot プレフィックスが必要です。chroot `/kafka` 内の `/brokers` は、ポリシーでは `/kafka/brokers` になります。シーケンシャル `create` は連番追加前のサーバー上の絶対リクエストプレフィックスを検査し、後続の `read`／`delete` は生成された完全なノード名を検査します。
- 標準 ACL は自動移行されず、ACL が再帰的に継承されることもありません。新しい znode にはマーカー ACL を明示的に設定してください。`setACL` を使う前に、小さな範囲で許可と拒否を検証し、独立した管理用アクセス経路を確保してください。ルートノードに不用意にマーカーを設定すると、アクセスできなくなるおそれがあります。
- エージェントを配置していないサーバーは、`sasl:grantforge` を標準 SASL ID `grantforge` として照合し、ポリシーを評価しません。そのため全ノードへの配置が必要であり、一部だけの配置ではアンサンブルを保護できません。
- follower/observer から leader に転送される書き込みリクエストは、このプロバイダーのインターフェースではクライアント接続がない場合があり、`clientAddress` コンテキストが空になる可能性があります。クライアント IP に基づくアンサンブル全体のエンドツーエンドのポリシー適用は保証できません。
- このプロバイダーはリクエストの最終結果を取得できません。そのため最終的な `AccessEvent` 監査イベントは送信せず、完全なアクセス監査は提供しません。
