---
title: ClickHouse
description: ClickHouse サービスプラグインをインストール・設定し、1 台または複数のサーバーに接続し、パスワードか TLS クライアント証明書でサインインして、データベース・テーブル・列を検索し、誰が何を読み取り・書き込み・管理できるかを列と行の単位まで管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

ClickHouse プラグインはデータベース、テーブル、列を検索し、ClickHouse 自身の考え方でアクセスポリシーを管理します。対象は、列の読み取りと書き込み、テーブルの変更・削除・空にすること・最適化、データベース内でのテーブル作成とデータベースの削除、そしてサーバーでのデータベース作成、`KILL QUERY` と `SYSTEM` 文で、テーブルの行フィルターも扱います。プラグイン ID とサービスタイプはどちらも `clickhouse` です。サービスプラグインは GrantForge サーバーで動作し、サーバーの隣で動くサービスである [ClickHouse エージェント](/ja/external/clickhouse-agent/)が、ClickHouse 自身の権限と行ポリシーでポリシーを適用します。

リポジトリの Testcontainers 統合テストは、直近 2 年の長期サポートリリースである ClickHouse `24.8`、`25.3`、`25.8`、`26.3`、`26.8` を対象とします。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/clickhouse` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `clickhouse` が有効であることを確認し、更新後はプラグインディレクトリを再スキャンします。プラグインは専用のクラスローダー内で、ClickHouse 自身の Java クライアントを使って HTTP でサーバーと通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して ClickHouse（`clickhouse`）を選びます。
2. 1 台のサーバー、または複数のサーバーの HTTP インターフェースの URL を入力します。
3. 検索ユーザーのサインイン方法を選び、ユーザー名と資格情報を入力して接続をテストし、サービスを保存します。テストはサインインしてサーバーを説明します。ClickHouse のバージョン、サインインしたユーザー、そのユーザーに見えるデータベースの数です。

検索ユーザーは GrantForge がリソースを見つけるためのもので、読み書きするユーザーとは別です。接続テストが成功しても、アクセスがすでにポリシーに従っているわけではありません。

| 設定 | 用途 |
| --- | --- |
| `clickhouse.urls` | 必須: サーバーの HTTP インターフェース（例: `https://clickhouse1.example.com:8443`）。複数はカンマ区切りで、到達できないサーバーがあれば順に次を試します |
| `authentication` | `password`（既定）または `certificate`: ClickHouse がユーザーに対応付ける TLS クライアント証明書（`IDENTIFIED WITH ssl_certificate`） |
| `username` | 必須: 検索ユーザー |
| `password` | password: 検索ユーザーのパスワード。暗号化して保存されます |
| `ssl.root.certificate` | https: **GrantForge サーバー** 上でサーバーを検証する PEM 形式の CA 証明書。空の場合は JVM のものを使います |
| `ssl.client.certificate`、`ssl.client.key` | certificate: GrantForge サーバー上の PEM 形式のクライアント証明書とその秘密鍵。証明書でのサインインには `https://` のサーバーが必要です |
| `timeout` | リクエストにかけられる時間。既定 `30000`、範囲 `1..600000` ミリ秒 |

あるサインイン方法の設定は別の方法では拒否されるため、サービスはどのようにサインインするかを明確に示します。拒否されたサインイン、読み取り専用のユーザー、到達できないか証明書を検証できないサーバー、そしてプロキシのように ClickHouse 以外が代わりに応答した場合は、そのとおりに報告されます。

### 検索ユーザーに見えるもの

ClickHouse は、ユーザーが何らかの権限を持つデータベース、テーブル、列だけを見せます。ポリシーを書く対象について検索ユーザーに `SHOW` を与えてください。たとえば `GRANT SHOW ON *.* TO grantforge_lookup` なら、データを読まずに名前だけが見えます。ClickHouse 自身のデータベース `system` と `information_schema` は対象外です。ポリシーはこれらを決めません。

## リソースとアクセスタイプ

| レベル | マッチング | アクセスタイプ |
| --- | --- | --- |
| `database` | ワイルドカード、大文字小文字を区別。データベース名 | `create`、`drop_database` |
| `table` | ワイルドカード、大文字小文字を区別。データベースの下 | `alter`、`drop`、`truncate`、`optimize` |
| `column` | ワイルドカード、大文字小文字を区別。テーブルの下 | `select`、`insert` |
| `server` | 唯一の値 `*` | `create_database`、`kill_query`、`system` |

| アクセスタイプ | ClickHouse の権限 |
| --- | --- |
| `select`、`insert` | 列に対する `SELECT`、`INSERT` |
| `alter` | `ALTER`: `ALTER UPDATE` と `ALTER DELETE` を含む、テーブルのあらゆる変更 |
| `drop` | `DROP TABLE`、`DROP VIEW`、`DROP DICTIONARY` |
| `truncate`、`optimize` | `TRUNCATE`、`OPTIMIZE` |
| `create` | データベース内の `CREATE TABLE`、`CREATE VIEW`、`CREATE DICTIONARY` |
| `drop_database` | `DROP DATABASE` |
| `create_database`、`kill_query`、`system` | `CREATE DATABASE`、`KILL QUERY`、`SYSTEM`: すべての `SYSTEM` 文 |

読み取りと書き込みは、ClickHouse が権限を与えるのと同じく列単位で判定されます。`select` や `insert` のポリシーは列を指定し、`*` はすべての列です。そのため、ポリシーでテーブルを許可しつつその 1 列を拒否でき、ユーザーは残りの列をすべて読めます。ポリシーの値では、`*` は任意の文字列、`?` は 1 文字に一致します。ポリシーエディターは、データベース、ポリシーが指定したデータベースのテーブル、その列を候補として示します。

アクセスタイプは `ALL` のような ClickHouse の権限グループではありません。それぞれが書かれたとおりの意味なので、ポリシーが指定した以上のものを与えることはありません。ClickHouse は ClickHouse Cloud 以外では列のマスキングを持たないため、このサービスタイプもマスキングを提供しません。

### 行フィルター

テーブルの行フィルターポリシーは、条件が成り立つ行だけをそのユーザーに見せます。条件は行ポリシーが受け付ける形の ClickHouse の式で、たとえば `region = 'EU'` や `owner = currentUser()` です。他のサービスと同じく、ユーザーには最初に当てはまるポリシーのフィルターが適用されます。

## ポリシーの例

サーバーにデータベース `sales` と `hr` があるとします。同じデータサービスに、次の有効なポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `database=sales`、`table=*`、`column=*` | グループ `analysts` に `select` を許可 |
| `database=sales`、`table=orders`、`column=card` | グループ `analysts` に `select` を拒否 |
| `database=sales`、`table=orders`、`column=*` | ユーザー `ingest` に `insert` を許可 |
| `database=team_*` | グループ `data-team` に `create` を許可 |
| `database=team_*`、`table=*` | グループ `data-team` に `alter`、`drop`、`truncate`、`optimize` を許可 |
| `database=team_*`、`table=*`、`column=*` | グループ `data-team` に `select` と `insert` を許可 |
| `server=*` | グループ `data-team` に `create_database` を許可 |
| `database=sales`、`table=orders`（行フィルター） | ユーザー `emea` には `region = 'EMEA'` の行だけを見せる |

エージェントが新しいスナップショットを適用すると、`analysts` のメンバーは `orders` のカード番号を除く `sales` のすべてのテーブルを読み、`ingest` は `orders` に書き込みますが何も読めません。`data-team` のメンバーはデータベースを作成し、名前が `team_` で始まるデータベースにテーブルを作成して読み書き・変更します。`emea` は、ポリシーで `orders` の読み取りが許可されていれば、自分の地域の行だけを見ます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、サーバーまたはクラスターごとに 1 つの ClickHouse エージェントが必要です。配置と確認内容は [ClickHouse エージェント](/ja/external/clickhouse-agent/) を参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [ClickHouse エージェント](/ja/external/clickhouse-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
