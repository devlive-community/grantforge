---
title: データベース
description: サポートするデータベースとバージョン、接続方式、ドライバー、データベースごとの注意事項。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## サポート範囲

| データベース | 検証済みのバージョン | ドライバー |
| --- | --- | --- |
| H2 | バージョンに同梱 | 同梱。試用のみを推奨 |
| PostgreSQL | 14、17 | 同梱 |
| MySQL | 8.0、8.4 | `drivers/` に自分で配置する必要があります（Connector/J は GPL ライセンスのため、リリースパッケージには含まれません） |
| MariaDB | 10.11、11.4 | 同梱 |
| Oracle | Free 23 | 同梱 |
| SQL Server | 2022 | 同梱 |

すべてのバージョンは CI で「空のデータベースの初期化 + すべての統合テスト」を実行します。中国製データベース（DaMeng、KingbaseES、openGauss、OceanBase など）はサポート範囲に含まれません。

## 接続例

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle（サービス名）
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

ユーザー名とパスワードは、それぞれ `GRANTFORGE_DB_USER` と `GRANTFORGE_DB_PASSWORD` で設定します。データベースは事前に作成しておく必要があり、アカウントにはテーブルを作成する権限が必要です。初回起動時に GrantForge は Liquibase ですべてのテーブルを作成し、その後のバージョンのアップグレードでも Liquibase が自動的に移行します。Hibernate はテーブル構造を検証するだけで、決して変更しません。

PostgreSQL では、GrantForge は `pg_trgm` 拡張を有効にし、ユーザーのログイン名、表示名、メールアドレスにトライグラムインデックスを作成します。これにより、100 万件規模のアカウントでも「含む」検索が数十ミリ秒で動作します。PostgreSQL 13 以降は信頼済み拡張なので、データベース所有者が有効化できます。アカウントにその権限がない場合もサービスは通常どおり起動し、検索は全テーブルスキャンになります。管理者が `CREATE EXTENSION pg_trgm` を実行すると、次回起動時にインデックスが自動的に追加作成されます。

## 文字セット

- **MySQL / MariaDB**：データベースを作成するときに `utf8mb4` 文字セットを使用する必要があります。中国語と絵文字を完全に保存するためです。
- **SQL Server、Oracle**：中国語を含む可能性のあるテキスト列には `NVARCHAR` を使用します。長いテキストは SQL Server では `NVARCHAR(MAX)`、Oracle では `CLOB` を使用します。これはデータベースのデフォルトの文字セットとは関係ありません。
- **Oracle**：空文字列は `NULL` として扱われます。GrantForge はドメイン層で空白値を一律「未入力」として扱うため、他のデータベースと同じ動作になります。

## バックアップと復元

すべての業務データはデータベースにあります（セッションも同様です）。そのためデータベースをバックアップすれば十分です。プラグインを使用している場合は `plugins/` も一緒にバックアップしてください。`grantforge.security.encryption-key` を設定していない場合、暗号化キーもデータベースに保存されるため、バックアップを復元すれば復号できます。キーを設定した場合は、そのキーも一緒に安全に保管する必要があります。
