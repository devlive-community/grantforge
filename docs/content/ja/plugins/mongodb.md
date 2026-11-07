---
title: MongoDB
description: MongoDB サービスプラグインをインストールして設定し、レプリカセットまたはシャードクラスターに接続し、SCRAM、クライアント証明書、LDAP、Kerberos でサインインし、データベースとコレクションを検索し、誰が何を読み、書き、管理できるかをコレクションごとに管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MongoDB プラグインは、データベースとコレクションを検索し、MongoDB 自身の考え方でアクセスポリシーを管理します。コレクションごとに読み取り、変更ストリーム、挿入、更新と削除、インデックス、コレクションオプション、ドキュメント検証の省略とコレクションの削除を、データベースごとにコレクションの作成とデータベースの削除を、デプロイメント全体ですべてのデータベースの一覧と監視を決めます。プラグイン ID とサービスタイプはどちらも `mongodb` です。サービスプラグインは GrantForge サーバーで動き、デプロイメントの隣で動く [MongoDB エージェント](/ja/external/mongodb-agent/) が MongoDB 自身のロールでポリシーを適用します。

リポジトリの Testcontainers 統合テストは、MongoDB `6.0`、`7.0`、`8.0`、`9.0` のレプリカセットと、Percona Server for MongoDB `8.0` の Kerberos を対象としています。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/mongodb` にこのプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `mongodb` が有効になっていることを確認し、プラグインディレクトリを更新したら再スキャンしてください。プラグインは独自のクラスローダーの中で、公式の MongoDB Java ドライバーを使って MongoDB と通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して MongoDB（`mongodb`）を選びます。
2. どの MongoDB クライアントでも使える形式で、デプロイメントの接続文字列を入力します。
3. 検索ユーザーのサインイン方法を入力して接続をテストし、サービスを保存します。テストはサインインしてデプロイメントを説明します。MongoDB のバージョン、レプリカセット、シャードクラスター、単一サーバーのどれか、誰がサインインしたか、そのユーザーに見えるデータベースの数です。

検索ユーザーは GrantForge がリソースを見つけるためのもので、読み書きをするユーザーとは別です。接続テストが成功しても、アクセスがすでにポリシーに従っているわけではありません。

| 設定 | 用途 |
| --- | --- |
| `mongodb.uri` | 必須：接続文字列。例：`mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` または `mongodb+srv://cluster.example.com/`。`tls=true` などのオプションはここに書きます。認証情報は書かず、含む文字列は拒否されます |
| `auth.mechanism` | パスワードを持つユーザーは `SCRAM-SHA-256`（既定）または `SCRAM-SHA-1`、クライアント証明書は `MONGODB-X509`、LDAP は `PLAIN`、Kerberos は `GSSAPI` |
| `username` | 検索ユーザー。`GSSAPI` ではプリンシパル。`MONGODB-X509` では空なら証明書から取ります |
| `password` | 検索ユーザーのパスワード。暗号化して保存 |
| `auth.source` | SCRAM：ユーザーが定義されたデータベース。既定は `admin`。他のメカニズムは `$external` を使います |
| `tls.ca.file` | `tls=true` のとき：サーバーを検証する **GrantForge サーバー** 上の PEM CA 証明書のパス。空なら JVM のものを使います |
| `tls.certificate.key.file` | `tls=true` のとき：`mongosh` が受け取るのと同じ、クライアント証明書と鍵を 1 つにした PEM ファイルの **GrantForge サーバー** 上のパス |
| `tls.certificate.key.password` | 暗号化された鍵のパスワード。暗号化して保存 |
| `kerberos.keytab` | `GSSAPI`：**GrantForge サーバー** 上のプリンシパルの keytab のパス。空ならパスワードを使います |
| `kerberos.kdc` | `GSSAPI`：レルムの KDC。カンマ区切り。空ならサーバーの `krb5.conf` を使います |
| `kerberos.service.name` | `GSSAPI`：サーバーのプリンシパルのサービス名。既定は `mongodb` |
| `timeout` | サーバーの検索、接続、1 つのコマンドにかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |

拒否されたサインイン、権限が足りない検索ユーザー、接続できないデプロイメントは、そのとおりに報告されます。

### 検索ユーザーに見えるもの

MongoDB はユーザーが何らかの権限を持つデータベースとコレクションだけを一覧にするため、検索結果は検索ユーザーに見えるものです。ポリシーを書く対象に `read` を、すべてなら `readAnyDatabase` を与えてください。MongoDB 自身のデータベース `admin`、`config`、`local` とシステムコレクションは除外されます。ポリシーはそれらを決定しません。

## リソースとアクセスタイプ

| レベル | 照合 | アクセスタイプ |
| --- | --- | --- |
| `deployment` | ワイルドカード、大文字小文字を区別。レプリカセットの名前、シャードクラスターは `sharded`、単一サーバーは `standalone` | `list_databases`、`monitor` |
| `database` | ワイルドカード、大文字小文字を区別 | `create`、`drop_database` |
| `collection` | ワイルドカード、大文字小文字を区別。データベースのコレクションまたはビュー | `read`、`change_stream`、`insert`、`update`、`remove`、`create_index`、`drop_index`、`alter`、`bypass_validation`、`drop` |

| アクセスタイプ | MongoDB のアクション |
| --- | --- |
| `read` | `find`、`listIndexes`、`collStats`、`killCursors`。データベース全体では `listCollections` と `dbStats` も |
| `change_stream` | `changeStream`。開くには MongoDB は `find` も求めます |
| `insert`、`update`、`remove` | `insert`、`update`、`remove` |
| `create_index`、`drop_index` | `createIndex`、`dropIndex` |
| `alter` | `collMod`、`validate`、`convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | データベースへの `createCollection`：コレクションとビューの作成 |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`：ユーザーが権限を持つものだけでなく、すべてのデータベースの名前 |
| `monitor` | `serverStatus`、`top`、`replSetGetStatus`、`connPoolStats`、`hostInfo`、`inprog` |

ポリシーの値では、`*` は任意の文字列に、`?` は 1 文字に一致します。ポリシーエディターは、デプロイメント、データベース、ポリシーが指すデータベースのコレクションを候補として示します。

MongoDB のデータベース削除権限は、その中のすべてのコレクションを削除する権限です。そのためエージェントは、ポリシーがすべてのコレクションの削除も許す場合にだけ `drop_database` を付与します。そうでなければ、ポリシーが許す以上を付与する代わりに権限を保留し、その旨を伝えます。ユーザーとロールの管理はアクセスタイプではなく、どのポリシーもそれらを与えません。

### 行とフィールド

MongoDB はロールごとに行をフィルターしたりフィールドをマスクしたりしないため、このサービスタイプはどちらも提供しません。ビューは制限付きの読み取りを提供します。MongoDB はビューの読み手をビューに対してのみ確認するため、ビューの `read` を許可しソースには何も許可しないポリシーは、ビューが見せるものだけをユーザーに見せます。

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## ポリシーの例

デプロイメントにデータベース `sales` と `hr` があるとします。同じデータサービスに、次の有効なポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `database=sales`、`collection=*` | グループ `analysts` に `read` を許可 |
| `database=sales`、`collection=orders_public` | ユーザー `support` に `read` を許可 |
| `database=sales`、`collection=orders` | ユーザー `ingest` に `insert` と `update` を許可；グループ `contractors` に `read` を拒否 |
| `database=team_*` | グループ `data-team` に `create` を許可 |
| `database=team_*`、`collection=*` | グループ `data-team` に `read`、`insert`、`update`、`remove`、`create_index`、`drop` を許可 |
| `deployment=*` | ユーザー `dashboards` に `monitor` を許可 |

エージェントがスナップショットを適用すると、`analysts` のメンバーは後から作られたコレクションも含めて `sales` 全体を読み、`support` はソースなしでビューを読み、`ingest` は `orders` を読まずに書き、`data-team` のメンバーは `team_` データベースの中で自由に作業し、`dashboards` はデータを見ずにサーバーの状態を見ます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、デプロイメントごとに 1 つの MongoDB エージェントが必要です。配置と確認は [MongoDB エージェント](/ja/external/mongodb-agent/) にあります。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [MongoDB エージェント](/ja/external/mongodb-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
