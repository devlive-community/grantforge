---
title: Apache Hive
description: Hive サービスプラグインをインストール・設定し、パスワードまたは Kerberos でサインインして、データベース・テーブル・列・UDF を検索し、アクセス・マスキング・行フィルターのポリシーを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Hive プラグインは、HiveServer2 向けにデータベース、テーブル、列、永続 UDF の検索と、アクセス・マスキング・行フィルターのポリシーを提供します。モデルは Apache Ranger の Hive サービスに倣っています。プラグイン ID とサービスタイプはどちらも `hive` です。サービスプラグインは GrantForge サーバーで動作し、HiveServer2 にインストールする [Hive エージェント](/ja/external/hive-agent/)がポリシーを適用します。

リポジトリの Testcontainers 統合テストは HiveServer2 `3.1.3`、`4.0.1`、`4.1.0` を対象とし、`4.0.1` への Kerberos サインインも含みます。これはほかのすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/hive` にプラグインが含まれています。**プラットフォーム管理 → プラグイン**で `hive` が有効であることを確認し、プラグインディレクトリを更新したら再スキャンしてください。プラグインは専用のクラスローダーで Hive 自身の JDBC ドライバー（`hive-jdbc` 4.0.1 standalone）を使って HiveServer2 と通信するため、HiveServer2 3.1 と 4.x のどちらでも動作します。

## データサービスの追加

1. **データ権限 → データサービス**を開き、サービスを追加して Apache Hive（`hive`）を選択します。
2. HiveServer2 の JDBC URL を入力します。例：`jdbc:hive2://hiveserver2.example.com:10000/default`。ZooKeeper によるサービス検出も使えます：`jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`。
3. 検索ユーザーのサインイン方法を選んでユーザーを入力し、接続をテストしてサービスを保存します。テストは検索と同じ方法でサインインし、データベースの一覧を取得します。

検索ユーザーは GrantForge がリソースを見つけるためだけに使い、クエリを実行するユーザーとは別です。接続テストが成功しても、クエリがすでにポリシーの対象になったわけではありません。

| 設定 | 用途 |
| --- | --- |
| `jdbc.url` | 必須の HiveServer2 URL `jdbc:hive2://ホスト/データベース;セッション変数`。TLS（`;ssl=true;sslTrustStore=…`）、HTTP トランスポート、Kerberos ではサーバーのプリンシパル（`;principal=hive/_HOST@EXAMPLE.COM`）を指定できます |
| `authentication` | `password`（既定）：LDAP、PAM、認証なし向けのユーザーとパスワード。`kerberos`：keytab またはパスワードを持つプリンシパル |
| `username` | 必須の検索ユーザー。Kerberos では `grantforge@EXAMPLE.COM` のような完全なプリンシパル |
| `password` | ユーザーのパスワード、または keytab がない場合はプリンシパルの Kerberos パスワード。暗号化して保存されます |
| `keytab` | Kerberos：プリンシパルの keytab のパス。**GrantForge サーバー**で読める必要があります |
| `kerberos.kdc` | Kerberos：プリンシパルのレルムの KDC。`host[:port]` をカンマ区切り。空ならサーバーの `krb5.conf` を使います |
| `read.timeout.ms` | 読み取りタイムアウト。既定 `30000`、範囲 `1..600000` |

URL には資格情報や、GrantForge サーバー上のファイル・トークンを読み込むものを含められません。`user`、`password`、`initFile`、`kerberosAuthType`、`delegationToken`、`storePasswordPath`、`jwt`、`browser…` 変数は大文字小文字に関係なく拒否されます。`auth` は `noSasl` のみ指定できます。Kerberos サービスは URL に `principal` が必要で、パスワードサービスには指定できません。

### Kerberos

`kerberos` では、GrantForge が keytab またはパスワードで自らプリンシパルをサインインさせ、そのプリンシパルとして接続を開きます。GrantForge サーバーにほかの Kerberos 設定は不要です。`kerberos.kdc` に KDC を入力したレルムは HDFS プラグインと同じ方法で GrantForge サーバー全体に宣言されるため、複数の Hive・HDFS サービスが異なるレルムを並行して使えます。

## リソースとアクセスタイプ

| レベル | 一致方法 | 補足 |
| --- | --- | --- |
| `database` | ワイルドカード、大文字小文字を区別しない | ポリシーはここで終わってもかまいません |
| `table` | ワイルドカード、大文字小文字を区別しない | データベースの下。ポリシーはここで終わってもかまいません |
| `column` | ワイルドカード、大文字小文字を区別しない | テーブルの下 |
| `udf` | ワイルドカード、大文字小文字を区別しない | データベースの下の永続関数 |
| `url` | パス、大文字小文字を区別、再帰 | 文が読み書きするデータの場所（例：`hdfs://nn:8020/landing`）。アクセスタイプ `read` と `write` |
| `hiveservice` | ワイルドカード | HiveServer2 自体。アクセスタイプ `serviceadmin` |
| `global` | ワイルドカード | 一時関数と追加ファイル。アクセスタイプ `tempudfadmin` |

アクセスタイプは Hive と同じです：`select`、`update`、`create`、`drop`、`alter`、`index`、`lock`、`read`、`write`、`repladmin`、`serviceadmin`、`tempudfadmin`、`refresh`、`all`。Ranger と同様に、`all` は `tempudfadmin` を除くすべてのタイプを含みます。`tempudfadmin` は HiveServer2 内で任意のコードを実行できるためです。

ポリシーの値では `*` が任意の数の文字、`?` が 1 文字に一致します。ポリシーエディターは接頭辞で名前を候補表示します：データベース、ポリシーが指定するデータベースのテーブルと UDF、そのテーブルの列。

## マスキング

マスキングポリシーは列に適用されます。各マスクタイプは Hive のマスキング関数で、`{col}` が列を表します。

| マスクタイプ | 表示 |
| --- | --- |
| `mask` | 大文字を `X`、小文字を `x`、数字を `n` で表示 |
| `mask_show_last_4` | 最後の 4 文字のみ |
| `mask_show_first_4` | 最初の 4 文字のみ |
| `mask_hash` | SHA-256 ハッシュ |
| `mask_null` | `NULL` |
| `mask_none` | 値をそのまま |
| `mask_date_show_year` | 日付の年の 1 月 1 日 |
| `custom` | `{col}` を列として使う独自の Hive 式。例：`concat('****', substr({col}, -4))` |

たとえば `mask_show_last_4` は `4111111111111111` を `xxxxxxxxxxxx1111` と表示します。

## 行フィルター

行フィルターポリシーはテーブルに適用されます。フィルターは `region = 'EU'` のようなテーブルの列に対する Hive の条件で、クエリには条件を満たす行だけが見えます。

マスクと行フィルターはアクセス権を付与しません。別のアクセスポリシーでクエリを許可する必要があります。

## ポリシーの例

Hive に `id`、`region`、`card` 列を持つ `sales.orders` テーブルがあるとします。同じデータサービスで、ユーザー `alice` に対して有効なポリシーを 3 つ作成します。

| ポリシーの種類 | リソース | ルール |
| --- | --- | --- |
| アクセス | `database=sales`、`table=orders`、`column=*` | ユーザー `alice` に `select` を許可 |
| マスキング | `database=sales`、`table=orders`、`column=card` | ユーザー `alice` に `mask_show_last_4` を使用 |
| 行フィルター | `database=sales`、`table=orders` | ユーザー `alice` に `region = 'EU'` を使用 |

エージェントが新しいスナップショットを適用すると、`alice` には `sales.orders` の EU の行だけが見え、各カード番号は最後の 4 文字以外がマスクされます。`SHOW DATABASES` は彼女に `sales` を表示し、ほかのデータベースはアクセスを拒否します。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。マスクや行フィルターを含めてポリシーを適用するには、すべての HiveServer2 に Hive エージェントが必要です。配置方法とチェック内容は [Hive エージェント](/ja/external/hive-agent/)を参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [Hive エージェント](/ja/external/hive-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
