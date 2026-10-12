---
title: MySQL
description: MySQL サービスプラグインをインストール・設定し、データベース・テーブル・列を検索して、アクセス・マスキング・行フィルターのポリシーを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MySQL プラグインは、GrantForge にデータベース・テーブル・列の検索と、アクセス・マスキング・行フィルターのポリシーを提供します。プラグイン ID とサービスタイプはどちらも `mysql` です。サービスプラグインは GrantForge サーバーで動作し、業務 SQL の実際の検査は独立した [MySQL プロキシエージェント](/ja/external/mysql-agent/) が行います。

接続チェックは MySQL 8.0 以降を受け付けます。リポジトリの Testcontainers 統合テストは `mysql:8.0` と `mysql:8.4` を対象としており、他のすべてのバージョンが検証済みという意味ではありません。MariaDB サーバーと MySQL 5.7 はサポート対象外です。`jdbc:mariadb://` はドライバーの URL 形式であり、MariaDB サーバーへの対応を意味しません。

## インストール

配布物の `plugins/mysql` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `mysql` が有効か確認し、更新後はプラグインディレクトリを再スキャンします。プラグインは MariaDB Connector/J（LGPL）で MySQL と通信します。これは `caching_sha2_password` や TLS を含む MySQL のプロトコルを扱い、プラグイン独自のクラスローダーで動作します。MySQL 公式の Connector/J は GPL のため同梱していません。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して MySQL（`mysql`）を選びます。
2. サーバーの JDBC URL を入力します。例：`jdbc:mysql://mysql.example.com:3306`。`jdbc:mariadb://` も同様に使え、`jdbc:mysql:replication://primary:3306,replica:3306` のようなドライバーのフェイルオーバー形式も使えます。
3. 検索用ユーザーとパスワードを入力し、接続をテストして保存します。テストではサインインしてサーバーのバージョンを確認し、検索と同じように `information_schema` を読み取ります。

検索用ユーザーは GrantForge がリソースを探すためのもので、業務クライアントが MySQL にサインインするユーザーとは別です。接続テストの成功だけでは、業務 SQL にポリシーが適用されていることにはなりません。

| 設定 | 用途 |
| --- | --- |
| `jdbc.url` | 必須の JDBC URL：`jdbc:mysql://host[:port]` または `jdbc:mariadb://…`。データベースと `?options` を付けられます。資格情報は URL に含めません |
| `username` | 必須の検索用ユーザー |
| `password` | 検索用ユーザーのパスワード。暗号化して保存されます |
| `jdbc.properties` | ドライバーのオプション。1 行に 1 つの `key=value`。例：`sslMode=verify-full`、`serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | 接続タイムアウト。既定値 `10000`、範囲 `1..600000` |
| `read.timeout.ms` | 検索 1 回の読み取りタイムアウト。既定値 `30000`、範囲 `1..600000` |

サーバーにローカルファイルを読ませる、接続時に文を実行する、複数の文を一度に実行する、資格情報を運ぶ、クラスを読み込む、といったオプションは、URL でも `jdbc.properties` でも大文字小文字を問わず拒否されます：`user`、`password`、`allowLocalInfile`、`allowMultiQueries`、`initSql`、`createDatabaseIfNotExist`、`socketFactory`、`credentialType`、およびプールのオプション。検索用の接続は読み取り専用です。

サーバーの身元を検証する TLS 接続には、`jdbc.properties` に次の設定を入力します。

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

証明書ファイルは **GrantForge サーバー上**で読み取れる必要があり、URL のホスト名が証明書と一致している必要があります。プラグインは既定では TLS を必須にしないため、明示的に設定してください。オプション名は MariaDB Connector/J のものです。

`information_schema` はユーザーが権限を持つオブジェクトだけを表示します。例えば `sales` データベース内のリソースだけを検索する場合、データベース管理者は次の権限を付与できます。`grantforge-host` は GrantForge サーバーの接続元アドレスに置き換えてください。

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

すべてのデータベースを検索する必要がある場合に限り、`REFERENCES ON *.*` を使用してください。プロキシのメタデータアカウントには `SHOW_ROUTINE` も必要です。詳細はエージェントのガイドを参照してください。

## リソースとアクセスタイプ

| レベル | 照合 | 備考 |
| --- | --- | --- |
| `database` | ワイルドカード、大文字小文字を区別 | ポリシーはここで終えられます |
| `table` | ワイルドカード、大文字小文字を区別 | データベースの下。ポリシーはここで終えられます |
| `column` | ワイルドカード、大文字小文字を区別しない | テーブルの下。アクセスタイプは `select`、`insert`、`update` |

データベースとテーブルは、MySQL が Linux で保存するとおり大文字小文字を区別して比較します。`lower_case_table_names` が 1 または 2 のサーバーでは、エージェントが確認する名前を小文字にそろえるため、そうしたポリシーは小文字で書いてください。列名は MySQL と同じく常に大文字小文字を区別しません。

アクセスタイプは MySQL の権限に対応します：`select`、`insert`、`update`、`delete`、`create`、`drop`、`alter`、`index`、およびこれら 8 種類を含む `all`。`all` は MySQL の `ALL PRIVILEGES` と同じではなく、プロキシの文に対する制限も解除しません。

ポリシー値の `*` は任意の長さの文字列に、`?` は 1 文字に一致します。ポリシーエディターは名前の前方一致で、データベース、ポリシーが指定するデータベース内のテーブル、そのテーブルの列を候補として表示します。候補検索の入力にある `%` と `_` は通常の文字として扱います。

## マスキング

マスキングポリシーは列に適用されます。各マスクタイプは MySQL 8 の式で、`{col}` が列を表します。

| マスクタイプ | 表示 |
| --- | --- |
| `mask` | ASCII の英字を `X`/`x`、数字を `n` に置換し、その他の文字は保持 |
| `mask_show_last_4` | 最後の 4 文字だけ |
| `mask_show_first_4` | 最初の 4 文字だけ |
| `mask_hash` | ソルトなしの SHA-256 ハッシュ |
| `mask_null` | `NULL` |
| `mask_none` | 値そのまま |
| `mask_date_show_year` | その日付の年の 1 月 1 日 |
| `custom` | 独自の MySQL 式。列は `{col}` で表します。例：`CONCAT('****', RIGHT({col}, 4))` |

先頭または末尾の 4 文字を表示するマスクは、値が 4 文字以下なら全体を保持します。例えば `mask_show_last_4` は `4111111111111111` を `xxxxxxxxxxxx1111` と表示します。

## 行フィルター

行フィルターポリシーはテーブルに適用されます。フィルターはテーブルの列に対する MySQL の条件で、例えば `region = 'EU'` です。ユーザーには条件を満たす行だけが見え、単一テーブルの `UPDATE` と `DELETE` もそれらの行だけを変更します。対象テーブルへの行フィルター適用を必要とする複数テーブルの書き込みは拒否されます。その他の制限はエージェントのガイドを参照してください。

マスキングと行フィルターはアクセスを許可するものではありません。操作を許可するアクセスポリシーを別途設定する必要があります。

## ポリシーの例

MySQL に `id`、`region`、`card` 列を持つ `sales.orders` テーブルがあるとします。同じデータサービスで、MySQL のログインユーザー `alice` に対して次の 3 つのポリシーを作成し、有効にします。

| ポリシーの種類 | リソース | ルール |
| --- | --- | --- |
| アクセス | `database=sales`、`table=orders`、`column=*` | ユーザー `alice` の `select` を許可 |
| マスキング | `database=sales`、`table=orders`、`column=card` | ユーザー `alice` に `mask_show_last_4` を適用 |
| 行フィルター | `database=sales`、`table=orders` | ユーザー `alice` に `region = 'EU'` を適用 |

ポリシーのユーザーには `alice@host` ではなく `alice` を指定します。MySQL 自体も、このアカウントによるテーブルの読み取りを許可している必要があります。プロキシが新しいスナップショットを適用すると、`alice` がプロキシ経由で取得できるのは EU の行だけとなり、カード番号は末尾 4 文字だけが表示されます。検索条件や結合式もマスキング後の値を参照します。デプロイと検証の手順は [MySQL プロキシエージェント](/ja/external/mysql-agent/) を参照してください。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。マスキングや行フィルターを含めてポリシーを適用するには、クライアントがサーバーの代わりに接続する MySQL プロキシエージェントが必要です。配置方法と確認内容はエージェントのガイドを参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [MySQL プロキシエージェント](/ja/external/mysql-agent/)
- [プラグインとサービスタイプの開発](/ja/develop/plugins/)
