---
title: Apache Hadoop YARN
description: YARN サービスプラグインをインストールして設定し、ResourceManager または高可用性ペアに接続し、simple または Kerberos でログインし、キューとアプリケーション名を検索して、誰がどのアプリケーションをどこへ投入できるかを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

YARN プラグインはキューとその中のアプリケーション名を検索し、YARN が判断すること、つまりキューへのアプリケーション投入、キューの管理、クラスターの管理についてアクセスポリシーを管理します。プラグイン ID とサービスタイプはどちらも `yarn` です。サービスプラグインは GrantForge サーバーで動作し、ResourceManager にインストールした [YARN エージェント](/ja/external/yarn-agent/)がポリシーを適用します。

リポジトリの Testcontainers 統合テストは、SHA-512 で検証した Apache リリースからビルドした Hadoop `3.2.4`、および `3.3.6`、`3.4.3`、`3.5.0` の ResourceManager と、`3.4.3` の Kerberos ResourceManager を対象としています。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/yarn` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `yarn` が有効であることを確認し、更新後はプラグインディレクトリを再スキャンします。プラグインは専用のクラスローダー内で、Hadoop の shaded クライアント 3.5.0 上の Hadoop 自身の YARN クライアントを使って ResourceManager と通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して Apache Hadoop YARN（`yarn`）を選びます。
2. ResourceManager のクライアントアドレス、または高可用性クラスターの各 ResourceManager のアドレスを入力します。
3. 検索ユーザーのログイン方法を選んで入力し、接続をテストしてサービスを保存します。テストはログインしてクラスターを説明し、NodeManager とキューの数を示します。

設定は Hadoop 自身の名前を使うため、`yarn-site.xml` からそのまま写せます。検索ユーザーは GrantForge がリソースを見つけるためのもので、アプリケーションを投入するユーザーとは別です。接続テストが成功しても、投入がすでにポリシーに従うわけではありません。

| 設定 | 用途 |
| --- | --- |
| `yarn.resourcemanager.address` | 必須：ResourceManager、`host` または `host:port`、既定ポート `8032`。高可用性クラスターではカンマ区切りで複数指定し、クライアントがその間でフェイルオーバーします |
| `hadoop.security.authentication` | `simple`（既定）：ユーザー名、`kerberos`：keytab またはパスワード付きのプリンシパル |
| `username` | 必須の検索ユーザー。Kerberos では完全なプリンシパル、例：`grantforge@EXAMPLE.COM` |
| `password` | Kerberos：keytab がない場合のプリンシパルのパスワード。暗号化して保存されます |
| `keytab` | Kerberos：プリンシパルの keytab のパス。**GrantForge サーバー** で読み取れる必要があります |
| `kerberos.kdc` | Kerberos：プリンシパルの realm の KDC、`host[:port]`、カンマ区切り。空の場合はサーバーの `krb5.conf` を使います |
| `yarn.resourcemanager.principal` | Kerberos：ResourceManager のプリンシパル。既定は `yarn/_HOST@` と検索プリンシパルの realm です |
| `hadoop.rpc.protection` | `authentication`（既定）、`integrity` または `privacy`。クラスターと同じにします |
| `timeout` | リクエストに許される時間。既定 `30000`、範囲 `1..600000` ミリ秒。YARN 自身のクライアントは 15 分間再試行します |

サービスは設定がそのとおりに決めます。GrantForge サーバーの `core-site.xml` や `yarn-site.xml` は読み込みません。simple 認証では Kerberos の設定は拒否され、Kerberos にはパスワードか keytab が必要です。ログインを拒否する ResourceManager（例：Kerberos を期待している場合）は、ログイン拒否として報告されます。

### Kerberos

`kerberos` では、GrantForge は Hadoop 自身のログインで keytab またはパスワードからプリンシパルをログインさせ、そのログインを保持して keytab から更新します。GrantForge サーバーのほかの部分には Kerberos の設定は不要です。`kerberos.kdc` に KDC を書いた realm は、HDFS、Hive、Kafka、HBase、Trino の各プラグインと同様に GrantForge サーバー全体に宣言されるため、複数のサービスが異なる realm を並行して使えます。

## リソースとアクセスタイプ

| レベル | 一致 | アクセスタイプ |
| --- | --- | --- |
| `queue` | ワイルドカード、大文字と小文字を区別。ルートからのキューのパス、例：`root.sales.etl` | `submit_app`、`admin_queue` |
| `application` | ワイルドカード、大文字と小文字を区別。キューの下。アプリケーションの名前 | `submit_app` |
| `cluster` | 唯一の値 `*` | `admin`：YARN の管理。例：キュー、ノード、ノードラベルの再読み込み |

キューの管理者はそのキューに投入できるため、`admin_queue` は `submit_app` を含みます。

YARN と同様に、キューに対する権限はその下のキューにも及びます。`root.sales` の `submit_app` で `root.sales.etl` に投入できます。チェックはルートからパス上のすべてのキューで判断され、どれか一つでも拒否すれば拒否されます。そのため、ポリシーで `root.sales` を許可しつつ `root.sales.adhoc` を拒否できます。キューの下のアプリケーションレベルは、権限がどのアプリケーションに適用されるかを指定します。アプリケーション `payroll-*` を指定した `root.hr` の `submit_app` は、`root.hr` とその下のキューで名前が `payroll-` で始まるアプリケーションだけを受け付けます。

ポリシー値では、`*` は任意個の文字、`?` は 1 文字に一致します。ポリシーエディターは、クラスターがどのスケジューラーを使っていても、キューをパスで（キュー自身の名前が入力されたときは名前で）提案し、ポリシーが指定したキューで ResourceManager がまだ覚えているアプリケーション名も提案します。

### クライアントアドレス

ポリシー項目は一部のクライアントにだけ適用できます。**クライアントアドレス**（`ip-range`）条件は、Hadoop 自身のホストリストと同様に、アドレス、`10.1.0.0/16` のような範囲、ホスト名を受け付けます。YARN の Web プロキシやルーターなどのプロキシの背後では、プロキシが転送した最初のアドレスがクライアントです。ホスト名はクライアントの名前解決を要するため、アドレスと範囲を推奨します。YARN は管理権限を尋ねるときにクライアントアドレスを渡さないため、`cluster` ではこの条件は決して成立しません。

## ポリシーの例

クラスターがキュー `root.sales.etl`、`root.sales.adhoc`、`root.hr` を運用しているとします。同じデータサービスで、次の有効なアクセスポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `queue=root.sales` | グループ `analysts` に `submit_app` を許可 |
| `queue=root.sales.adhoc` | ユーザー `alice` に `submit_app` を拒否 |
| `queue=root.hr`、`application=payroll-*` | ユーザー `bob` に `submit_app` を許可 |
| `queue=root.sales` | ユーザー `carol` に `admin_queue` を許可 |
| `queue=root` | グループ `etl-robots` に `10.0.0.0/8` からの `submit_app` を許可 |
| `cluster=*` | グループ `yarn-admins` に `admin` を許可 |

エージェントが新しいスナップショットを適用すると、`analysts` のメンバーは `root.sales.etl` と `root.sales.adhoc` に投入できますが、`alice` は `root.sales.etl` にだけ投入できます。`bob` は給与アプリケーションだけを `root.hr` に投入します。`carol` は営業のキューに投入し、そのアプリケーションを終了できます。`etl-robots` のメンバーはどのキューにも投入できますが、内部ネットワークからだけです。`yarn-admins` のメンバーはキューを再読み込みできます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、すべての ResourceManager に YARN エージェントが必要です。配置とチェックについては [YARN エージェント](/ja/external/yarn-agent/)を参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [YARN エージェント](/ja/external/yarn-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
