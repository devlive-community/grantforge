---
title: Apache Doris
description: Apache Doris サービスプラグインをインストールして設定し、1 つまたは複数のフロントエンドに接続し、パスワードでサインインし、カタログ、データベース、テーブル、列、ワークロードグループを検索し、誰が何を読み、ロードし、管理できるかを列と行まで、誰がどのワークロードグループでクエリを実行できるかを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Doris プラグインは、カタログ、データベース、テーブル、列、ワークロードグループを検索し、Doris 自身の考え方でアクセスポリシーを管理します。列の読み取り、テーブルのロード・変更・削除とビュー定義の表示、データベースでのテーブル作成とデータベースの削除、カタログでのデータベース作成、ワークロードグループでのクエリ実行に加え、テーブルの行フィルターにも対応します。プラグイン ID とサービスタイプはどちらも `doris` です。サービスプラグインは GrantForge サーバーで動き、クラスターの隣で動く [Apache Doris エージェント](/ja/external/doris-agent/) が Doris 自身のロール、権限、行ポリシーでポリシーを適用します。

リポジトリの Testcontainers 統合テストは、Apache Doris `2.1`、`3.1`、`4.0`、`4.1` を、それぞれフロントエンド 1 台とバックエンド 1 台のクラスターで対象としています。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/doris` にこのプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `doris` が有効になっていることを確認し、プラグインディレクトリを更新したら再スキャンしてください。プラグインは独自のクラスローダーの中で、MariaDB Connector/J を使ってフロントエンドの MySQL プロトコルと通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して Apache Doris（`doris`）を選びます。
2. 1 つまたは複数のフロントエンドの MySQL プロトコルのアドレスを入力します。
3. 検索ユーザーの名前とパスワードを入力して接続をテストし、サービスを保存します。テストはサインインしてクラスターを説明します。Doris のバージョン、サインインしたユーザー、そのユーザーに見えるカタログの数、内部カタログのデータベースの数です。フロントエンドだけに問い合わせるため、バックエンドが動いていなくても答えます。

検索ユーザーは GrantForge がリソースを見つけるためのもので、読み取りやロードをするユーザーとは別です。接続テストが成功しても、アクセスがすでにポリシーに従っているわけではありません。

| 設定 | 用途 |
| --- | --- |
| `doris.hosts` | 必須：フロントエンドの MySQL プロトコルのアドレス。例：`fe1.example.com:9030`。複数はカンマ区切りで、接続できないときは順に試します。ポートを書かなければ `9030` です |
| `username` | 必須：検索ユーザー |
| `password` | 検索ユーザーのパスワード。暗号化して保存 |
| `ssl.mode` | `disable`（既定）、フロントエンドを確認せずに暗号化する `trust`、証明書を確認する `verify-ca`、名前も確認する `verify-full`。MySQL プロトコルで TLS を提供するフロントエンド向けです |
| `ssl.root.certificate` | verify-ca と verify-full：フロントエンドを検証する **GrantForge サーバー** 上の PEM CA 証明書。空なら JVM のものを使います |
| `timeout` | 接続と 1 つのステートメントにかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |

拒否されたサインイン、権限が足りない検索ユーザー、接続できないフロントエンドは、そのとおりに報告されます。

### 検索ユーザーに見えるもの

Doris の `SHOW` ステートメントは、ユーザーが何らかの権限を持つカタログ、データベース、テーブル、列だけを一覧にするため、検索結果は検索ユーザーに見えるものです。ポリシーを書く対象に `SELECT_PRIV` を与えてください。例：`GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`。`SHOW WORKLOAD GROUPS` は見えるグループを一覧にします。Doris 自身のデータベース、`information_schema`、`mysql`、`__internal_schema` は除外されます。ポリシーはそれらを決定しません。

## リソースとアクセスタイプ

| レベル | 照合 | アクセスタイプ |
| --- | --- | --- |
| `catalog` | ワイルドカード、大文字小文字を区別。内部カタログまたは外部カタログ | `create_database` |
| `database` | ワイルドカード、大文字小文字を区別。カタログの下 | `create`、`drop_database` |
| `table` | ワイルドカード、大文字小文字を区別。データベースの下のテーブルまたはビュー | `load`、`alter`、`drop`、`show_view` |
| `column` | ワイルドカード、Doris が列名を比べるとおり大文字小文字を区別しない。テーブルの下 | `select` |
| `workload_group` | ワイルドカード、大文字小文字を区別。グループの名前 | `usage` |

| アクセスタイプ | Doris の権限 |
| --- | --- |
| `select` | 列、またはテーブル、データベース、カタログ全体への `SELECT_PRIV` |
| `load` | `LOAD_PRIV`：ロード、`INSERT`、`UPDATE`、`DELETE`、`TRUNCATE` |
| `alter`、`drop` | テーブルへの `ALTER_PRIV`、`DROP_PRIV` |
| `show_view` | `SHOW_VIEW_PRIV`：`SHOW CREATE VIEW` |
| `create` | データベースへの `CREATE_PRIV`：データベースの作成と、その中でのテーブルとビューの作成 |
| `drop_database` | データベースへの `DROP_PRIV` |
| `create_database` | カタログへの `CREATE_PRIV` |
| `usage` | ワークロードグループへの `USAGE_PRIV` |

読み取りは、Doris が付与するとおり列ごとに決まります。`select` のポリシーは列を指定し、`*` はすべての列です。そのため、ポリシーはテーブルを許可しつつその中の 1 列を拒否でき、ユーザーは残りのすべての列を読めます。ポリシーの値では、`*` は任意の文字列に、`?` は 1 文字に一致します。ポリシーエディターは、カタログ、ポリシーが指すカタログのデータベース、そのテーブルと列、ワークロードグループを候補として示します。

Doris のデータベースへの権限はその中のすべてのテーブルにも、カタログへの権限はその中のすべてのデータベースにも及びます。そのためエージェントは、ポリシーがカタログのすべてのデータベースでのテーブル作成も許す場合にだけ `create_database` を、データベースのすべてのテーブルの削除も許す場合にだけ `drop_database` を付与します。そうでなければ、ポリシーが許す以上を付与する代わりに権限を保留し、その旨を伝えます。`ADMIN_PRIV`、`GRANT_PRIV`、`NODE_PRIV` はアクセスタイプではなく、どのポリシーもそれらを与えません。

Doris は Apache Ranger 経由でのみ値をマスクするため、このサービスタイプはマスクを提供しません。

### 行フィルター

テーブルの行フィルターポリシーは、条件に合う行だけをユーザーに見せます。条件は行ポリシーが受け取る Doris の述語で、例えば `region = 'EU'` です。エージェントはまずそれをテーブルで実行し、そこで実行できないフィルターは行を 1 つも見せません。他のサービスと同様に、ユーザーには自分に適用される最初のポリシーのフィルターが適用されます。

## ポリシーの例

クラスターの内部カタログにデータベース `sales` と `hr` があり、ワークロードグループ `etl` があるとします。同じデータサービスに、次の有効なポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `catalog=internal`、`database=sales`、`table=*`、`column=*` | グループ `analysts` に `select` を許可 |
| `catalog=internal`、`database=sales`、`table=orders`、`column=card` | グループ `analysts` に `select` を拒否 |
| `catalog=internal`、`database=sales`、`table=orders` | ユーザー `ingest` に `load` を許可 |
| `catalog=internal`、`database=team_*` | グループ `data-team` に `create` を許可 |
| `catalog=internal`、`database=team_*`、`table=*` | グループ `data-team` に `load`、`alter`、`drop` を許可 |
| `workload_group=etl` | ユーザー `ingest` とグループ `data-team` に `usage` を許可 |
| `catalog=internal`、`database=sales`、`table=orders`（行フィルター） | ユーザー `emea` に `region = 'EMEA'` の行だけを表示 |

エージェントが新しいスナップショットを適用すると、`analysts` のメンバーは `orders` のカード番号を除く `sales` のすべてのテーブルを読め、`ingest` は `orders` にロードし、そのロードを `etl` で実行でき、`data-team` のメンバーは管理者が作成した `team_` データベースにテーブルを作り、ロード・変更・削除でき、`emea` はポリシーが `orders` の読み取りを許していれば自分の地域の行だけを見ます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、クラスターごとに 1 つの Apache Doris エージェントが必要です。配置と確認は [Apache Doris エージェント](/ja/external/doris-agent/) にあります。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [Apache Doris エージェント](/ja/external/doris-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
