---
title: PostgreSQL
description: PostgreSQL サービスプラグインをインストール・設定し、1 台または複数のサーバーに接続し、パスワードか TLS クライアント証明書でサインインし、データベース・スキーマ・テーブル・列を検索して、列と行の単位まで、誰が何を読み取り・書き込み・作成できるかを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

PostgreSQL プラグインは、データベース、スキーマ、テーブル、列を検索し、PostgreSQL 自身の考え方でアクセスポリシーを管理します。列の読み取り・挿入・更新、テーブルの行の削除・空にすること・トリガーの作成、スキーマ内のオブジェクト作成、データベース内のスキーマと一時テーブルの作成に加え、テーブルの行フィルターにも対応します。プラグイン ID とサービスタイプはどちらも `postgresql` です。サービスプラグインは GrantForge サーバーで動き、サーバーの隣で動く [PostgreSQL エージェント](/ja/external/postgresql-agent/) が PostgreSQL 自身の権限と行セキュリティポリシーでポリシーを適用します。

リポジトリの Testcontainers 統合テストは、PostgreSQL が保守しているバージョンである PostgreSQL `15`、`16`、`17`、`18` を対象としています。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/postgresql` にこのプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `postgresql` が有効になっていることを確認し、プラグインディレクトリを更新したら再スキャンしてください。プラグインは独自のクラスローダーの中で、PostgreSQL の JDBC ドライバーを使ってサーバーと通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して PostgreSQL（`postgresql`）を選びます。
2. 1 台または複数のサーバーを `host:port` で入力します。
3. 検索ユーザーのサインイン方法を選び、ユーザー名と資格情報を入力して接続をテストし、サービスを保存します。テストはサインインしてサーバーを説明します。PostgreSQL のバージョン、サインインしたユーザー、サーバーにあるデータベースの数です。

検索ユーザーは GrantForge がリソースを見つけるためのもので、読み書きするユーザーとは別です。接続テストが成功しても、アクセスがすでにポリシーに従っているわけではありません。

| 設定 | 用途 |
| --- | --- |
| `postgresql.hosts` | 必須：`host:port` 形式のサーバー。例：`postgres1.example.com:5432`。複数はカンマ区切りで、接続できないときは順に試します。ポートの既定は `5432` |
| `postgresql.database` | データベースの一覧を得るために検索ユーザーが最初にサインインするデータベース。既定は `postgres` |
| `authentication` | `password`（既定）または `certificate`：PostgreSQL の `cert` 認証がユーザーに対応付ける TLS クライアント証明書 |
| `username` | 必須：検索ユーザー |
| `password` | password：検索ユーザーのパスワード。暗号化して保存 |
| `ssl.mode` | libpq の `sslmode` が呼ぶ TLS の方式：`disable`、`allow`、`prefer`（既定）、`require`、`verify-ca`、またはサーバー名も確認する `verify-full` |
| `ssl.root.certificate` | verify-ca と verify-full：**GrantForge サーバー** 上の、サーバーを検証する PEM 形式の CA 証明書。空なら JVM のものを使います |
| `ssl.client.certificate`、`ssl.client.key` | certificate：GrantForge サーバー上の PEM 形式のクライアント証明書と、その PKCS #8 秘密鍵（PEM または DER）。証明書でのサインインには `require` か検証方式が必要で、検証方式と併用するには `ssl.root.certificate` も必要です |
| `timeout` | 接続とクエリにかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |

あるサインイン方法の設定は別の方法では拒否されるため、サービスはどうサインインするかを明確にします。PostgreSQL のドライバーは、所有者以外が読める秘密鍵ファイルを拒否します。拒否されたサインイン、存在しないデータベース、接続できないサーバーや証明書を検証できないサーバーは、そのとおりに報告されます。

### 検索ユーザーに見えるもの

PostgreSQL のカタログは、すべてのユーザーにデータベースの名前を見せ、接続できる各データベースではデータを読まずにスキーマ、テーブル、列の名前を見せます。検索ユーザーに必要なのは、ポリシーを書く対象のデータベースへの `CONNECT` だけで、取り消されていなければ PUBLIC が持っています。スキーマ、テーブル、列の検索は、ポリシーが指す各データベース（最大 20 個）にサインインし、検索ユーザーが接続できないデータベースは飛ばします。PostgreSQL 自身のスキーマ、つまり `pg_` で始まるものと `information_schema` は除外されます。ポリシーはそれらを決めません。

## リソースとアクセスタイプ

| レベル | 照合 | アクセスタイプ |
| --- | --- | --- |
| `database` | ワイルドカード、大文字小文字を区別。データベースの名前 | `create_schema`、`temporary` |
| `schema` | ワイルドカード、大文字小文字を区別。データベースの下 | `create` |
| `table` | ワイルドカード、大文字小文字を区別。スキーマの下のテーブル、パーティションテーブル、ビュー、マテリアライズドビュー、外部テーブル | `delete`、`truncate`、`trigger` |
| `column` | ワイルドカード、大文字小文字を区別。テーブルの下 | `select`、`insert`、`update` |

| アクセスタイプ | PostgreSQL の権限 |
| --- | --- |
| `select`、`insert`、`update` | 列またはテーブル全体に対する `SELECT`、`INSERT`、`UPDATE` |
| `delete`、`truncate`、`trigger` | テーブルに対する `DELETE`、`TRUNCATE`、`TRIGGER` |
| `create` | スキーマに対する `CREATE`：その中のテーブル、ビュー、シーケンス、関数、その他のオブジェクト |
| `create_schema` | データベースに対する `CREATE`：その中のスキーマ |
| `temporary` | データベースに対する `TEMPORARY` |

読み取り、挿入、更新は、PostgreSQL が付与するとおり列ごとに決まります。`select`、`insert`、`update` のポリシーは列を指定し、`*` はすべての列です。そのため、ポリシーはテーブルを許可しつつその中の 1 列を拒否でき、ユーザーは残りのすべての列を読めます。ポリシーの値では、`*` は任意の文字列に、`?` は 1 文字に一致します。ポリシーエディターは、データベース、ポリシーが指すデータベースのスキーマ、そのテーブルと列を候補として示します。

ポリシーが許可するものに届くには、PostgreSQL ではさらに権限が要ります。データベースへの `CONNECT`、スキーマの `USAGE`、そして serial 列に挿入するにはそのシーケンスの `USAGE` です。これらは独立したアクセスタイプではなく、エージェントが必要とする権限と一緒に付与します。PostgreSQL には列のマスキングがないため、このサービスタイプはマスキングを提供しません。

### 行フィルター

テーブルの行フィルターポリシーは、条件が成り立つ行だけをユーザーに見せます。条件は行セキュリティポリシーの `USING` が受け取る形の PostgreSQL の式で、例えば `region = 'EU'` や `owner = current_user` です。他のサービスと同様に、ユーザーには自分に適用される最初のポリシーのフィルターが見えます。PostgreSQL が行を絞り込めるのはテーブルとパーティションテーブルだけです。ポリシーがあるユーザーのために行を絞り込むビューなどは、そのユーザーにはまったく読めません。

## ポリシーの例

サーバーにデータベース `sales` と `hr` があり、`sales` にスキーマ `crm` があるとします。同じデータサービスに、次の有効なポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `database=sales`、`schema=crm`、`table=*`、`column=*` | グループ `analysts` に `select` を許可 |
| `database=sales`、`schema=crm`、`table=orders`、`column=card` | グループ `analysts` に `select` を拒否 |
| `database=sales`、`schema=crm`、`table=orders`、`column=*` | ユーザー `ingest` に `insert` を許可 |
| `database=sales`、`schema=team_*` | グループ `data-team` に `create` を許可 |
| `database=sales`、`schema=team_*`、`table=*` | グループ `data-team` に `delete` と `truncate` を許可 |
| `database=sales`、`schema=team_*`、`table=*`、`column=*` | グループ `data-team` に `select`、`insert`、`update` を許可 |
| `database=sales` | グループ `data-team` に `create_schema` を許可 |
| `database=sales`、`schema=crm`、`table=orders`（行フィルター） | ユーザー `emea` に `region = 'EMEA'` の行だけを表示 |

エージェントが新しいスナップショットを適用すると、`analysts` のメンバーは `orders` のカード番号を除いて `crm` のすべてのテーブルを読め、`ingest` は serial の id も含めて `orders` に挿入できますが何も読めず、`data-team` のメンバーはスキーマを作成し、名前が `team_` で始まるスキーマでテーブルを作成・読み取り・変更・空にでき、`emea` は `orders` を読むことを許すポリシーがあれば自分の地域の行だけを見ます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、サーバーまたはプライマリごとに 1 つの PostgreSQL エージェントが必要です。配置と確認は [PostgreSQL エージェント](/ja/external/postgresql-agent/) にあります。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [PostgreSQL エージェント](/ja/external/postgresql-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
