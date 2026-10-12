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
| `connection.timeout.ms` | サーバーとサインインを待つ時間。既定 `10000`、範囲 `1000..60000` |
| `lookup.path` | chroot 以下で検索と参照を始める znode。既定 `/`（例：`/brokers`）。参照はこの znode 内に限られます |
| `lookup.max.entries` | 1 つの znode から一度に読む子ノードの上限。既定 `10000`、範囲 `1..100000` |

## 認証

- **none**：サインインしません。ACL が `world:anyone` に開いている znode だけを読めます。
- **digest**：`username:password` でサインインし、ZooKeeper ACL の `digest` ID に対応します。パスワードが誤っていてもサインインは拒否されず別の ID になるため、保護された znode はアクセス拒否として表示されます。
- **kerberos**：keytab で SASL サインインします。サーバーの `krb5.conf` から KDC に到達できる必要があります。principal と keytab ごとに、プロセスの既存設定とは別の JAAS セクションを持つため、1 つの GrantForge が異なる principal で複数のアンサンブルに接続できます。パスワードだけの Kerberos サインインはまだサポートしていません。

保存時に、接続文字列の形式、digest のユーザーとパスワード、Kerberos の principal と keytab、Kerberos 専用の keytab とサーバー principal、各タイムアウトと上限の範囲を検査します。

## znode の参照

**データ権限 → ポリシー** でサービスを選び、`path` の横の **参照** を使用します。

- 子ノードを名前順にページ単位で一覧します。子ノードを持つ znode はさらに開けます。
- 各 znode の ACL（ZooKeeper 標準ツールと同じ `scheme:id:cdrwa`）、データサイズ、更新日時を表示します。
- 検索ユーザーが ACL を読めない znode も名前は一覧され、開くと拒否の理由が表示されます。

パスを入力すると候補が表示されます。存在しない親には候補がなく、`lookup.path` の外のパスは拒否されます。子ノードが `lookup.max.entries` を超える znode は、一部だけを表示せずエラーになります。

## ポリシーの適用

リソースは `path` の 1 階層のみで、アクセスタイプは ZooKeeper の ACL 権限と同じ `read`、`write`、`create`、`delete`、`admin` です。パスのポリシーは再帰と除外に対応します。

サーバープラグインは管理、検索、参照を担当します。ZooKeeper サーバー内でこれらのポリシーを適用するエージェントはまだ提供されていません。それまでは ZooKeeper 自身の ACL がアクセスを決めます。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [プラグインとサービスタイプの開発](/ja/develop/plugins/)
