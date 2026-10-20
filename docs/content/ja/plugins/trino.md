---
title: Trino
description: Trino サービスプラグインをインストール・設定し、TLS 上でパスワード、JSON Web Token、Kerberos でサインインし、カタログ・スキーマ・テーブル・カラム・セッションプロパティを検索して、Trino 自身の概念でアクセス・行フィルター・マスキングのポリシーを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Trino プラグインはカタログ、スキーマ、テーブル、カラム、セッションプロパティを検索し、Trino が検査する対象であるデータ、ルーチン、セッションプロパティ、ユーザー、システム情報について、アクセス・行フィルター・マスキングのポリシーを管理します。プラグイン ID とサービスタイプはどちらも `trino` です。サービスプラグインは GrantForge サーバーで動作し、coordinator にインストールした [Trino エージェント](/ja/external/trino-agent/) がポリシーを適用します。

リポジトリの Testcontainers 統合テストは Trino `470`、`476`、`479`、`484` を対象とし、`470` と `484` ではパスワードファイルと JSON Web Token を使う HTTPS、および Kerberos も対象としています。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/trino` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `trino` が有効であることを確認し、プラグインディレクトリを更新したら再スキャンしてください。プラグインは独自のクラスローダー内の Trino JDBC ドライバー（`trino-jdbc` 484）で coordinator と通信し、このドライバーは上記のすべての Trino バージョンと通信できます。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して Trino（`trino`）を選びます。
2. coordinator の JDBC URL を入力します。例：`jdbc:trino://trino.example.com:8443`。
3. 検索ユーザーのサインイン方法を選んで入力し、接続をテストしてサービスを保存します。テストはサインインして coordinator のバージョンと、ユーザーに見えるカタログの数を示します。

設定名はドライバー自身の名前なので、Trino の JDBC ドキュメントと同じように読めます。検索ユーザーは GrantForge がリソースを見つけるためのもので、クエリを実行するユーザーとは別です。接続テストの成功は、クエリがすでにポリシーに従うことを意味しません。

| 設定 | 用途 |
| --- | --- |
| `jdbc.url` | 必須：`jdbc:trino://host:port`。カタログとスキーマ、資格情報以外のドライバープロパティ（例：`?SSL=true`）を付けられる |
| `authentication` | `none`（既定）：ユーザー名のみ。`password`。`jwt`：JSON Web Token。`kerberos`：keytab またはパスワードを持つプリンシパル |
| `user` | 必須の検索ユーザー。Kerberos では完全なプリンシパル、例：`grantforge@EXAMPLE.COM` |
| `password` | ユーザーのパスワード。Kerberos では keytab がない場合のプリンシパルのパスワード。暗号化して保存 |
| `accessToken` | `jwt`：coordinator が受け入れるトークン。暗号化して保存 |
| `keytab` | Kerberos：プリンシパルの keytab のパス。**GrantForge サーバー** で読める必要がある |
| `kerberos.kdc` | Kerberos：プリンシパルの realm の KDC、カンマ区切りの `host[:port]`。空ならサーバーの `krb5.conf` を使用 |
| `KerberosRemoteServiceName` | Kerberos：coordinator のプリンシパルの最初の部分、既定 `trino` |
| `KerberosUseCanonicalHostname` | Kerberos：coordinator のプリンシパルが URL のホストの正規名を使うか、既定 `true`。プリンシパルがその名前を持つロードバランサーや別名の背後ではオフにする |
| `SSL` | TLS を使うか、既定 `false`。Trino は TLS 上でのみユーザーをサインインさせるため、`none` 以外のサインインでは必要 |
| `SSLTrustStorePath`、`SSLTrustStorePassword`、`SSLTrustStoreType` | TLS：coordinator を検証するトラストストア、そのパスワードと種類（`JKS` または `PKCS12`）。空なら Java が信頼するものを信頼 |
| `SSLVerification` | TLS：`FULL`（既定）は証明書とホスト名、`CA` は証明書のみを検査し、`NONE` は何も検査せずトラストストアを受け付けない |
| `timeout` | リクエストのタイムアウト（ミリ秒）、既定 `30000`、範囲 `1..600000` |

サービスはフィールドが示すとおりのものです。URL には資格情報や別の方法でサインインさせるものを含められません。`user`、`password`、`accessToken`、`sessionUser`、`extraCredentials`、外部認証と OAuth 2.0、Kerberos のファイルと委任、キーストアとトラストストアのパスワードは拒否されます。パスワード認証にトークンを入れるなど、別のサインイン方法のフィールドも拒否されます。

証明書を検証できない coordinator とは通信せず、テストは到達不能と報告します。拒否されたサインイン、権限不足、到達不能な coordinator はそれぞれそのとおりに報告されます。

### Kerberos

`kerberos` では、GrantForge が keytab またはパスワードでプリンシパルをサインインさせ、そのサインインの資格情報をドライバーに渡します。GrantForge サーバーの他の部分に Kerberos 設定は不要です。`kerberos.kdc` に KDC を入力した realm は、HDFS、Hive、Kafka、HBase プラグインと同じく GrantForge サーバー全体に宣言されるため、複数のサービスが異なる realm を並行して使えます。セッションユーザーは、Trino の既定のプリンシパルマッピングと同じく realm より前のプリンシパル名です。

### 検索ユーザーに見えるもの

[Trino エージェント](/ja/external/trino-agent/) がポリシーを適用すると、一覧から検索ユーザーが使えないものが省かれます。カタログ `*`、またはポリシーエディターにリソースを提案させたいカタログで `select` を許可するポリシーを与えてください。

## リソースとアクセスタイプ

| レベル | 照合 | アクセスタイプ |
| --- | --- | --- |
| `catalog` | ワイルドカード、大文字小文字を区別しない | `select`、`insert`、`update`、`delete`、`create`、`drop`、`alter`、`all` |
| `schema` | ワイルドカード、大文字小文字を区別しない。カタログの下 | `catalog` と同じ |
| `table` | ワイルドカード、大文字小文字を区別しない。スキーマの下。テーブル、ビュー、マテリアライズドビュー | `catalog` と同じ |
| `column` | ワイルドカード、大文字小文字を区別しない。テーブルの下 | `select`、`update` |
| `function` | ワイルドカード、大文字小文字を区別しない。スキーマの下 | `execute`、`create_function`、`drop_function` |
| `procedure` | ワイルドカード、大文字小文字を区別しない。スキーマの下 | `execute` |
| `session_property` | ワイルドカード、大文字小文字を区別しない。カタログの下で、カタログを含めずに指定 | `set` |
| `system_session_property` | ワイルドカード、大文字小文字を区別しない | `set` |
| `user` | ワイルドカード、大文字小文字を区別する | `impersonate`、`view_query`、`kill_query` |
| `system_information` | 唯一の値 `*` | `read`、`write` |

`all` は `select`、`insert`、`update`、`delete`、`create`、`drop`、`alter` を含みます。

カタログ、スキーマ、テーブルの権限はその中のすべてに及びます。スキーマ `sales` の `select` はその中のすべてのテーブルを読め、カタログの `create` はその中にスキーマとテーブルを作れます。検査はそれが指す各スコープで判定され、どこかで拒否されれば拒否されるため、1 つのポリシーでスキーマを許可しつつ、その中の 1 テーブルの 1 カラムを拒否できます。関数、プロシージャ、セッションプロパティは名前で付与し、`*` はスキーマやカタログのすべてを表します。関数には独自のアクセスタイプがあるため、ルーチンへの付与がテーブルへの付与とみなされることはありません。

ポリシーの値では、`*` は任意個の文字に、`?` は 1 文字に一致します。ポリシーエディターはカタログ、ポリシーが指定したカタログ内のスキーマ、そのスキーマ内のテーブル、そのテーブル内のカラム、およびそのカタログとシステムのセッションプロパティを提案します。関数、プロシージャ、ユーザーは提案しません。

## 行フィルターとマスク

テーブルの行フィルターポリシーとカラムのマスキングポリシーは Trino SQL で、coordinator がそれらを読むすべてのクエリに適用します。行フィルターは `region = 'EU'` のようなテーブルのカラムに対する条件で、その中の名前はテーブルのカタログとスキーマで解決されます。

| マスクタイプ | 表示 |
| --- | --- |
| `mask` | 英字を `X` または `x`、数字を `n` |
| `mask_show_last_4`、`mask_show_first_4` | 最後または最初の 4 文字のみ |
| `mask_hash` | 値の SHA-256（16 進数） |
| `mask_null` | `NULL` |
| `mask_none` | マスクしない値 |
| `mask_date_show_year` | 日付またはタイムスタンプの年のみ |
| `custom` | ポリシーの Trino SQL 式。カラムは `{col}` |

エージェントは各マスクをカラムの型に合わせ、マスクがカラムを読むクエリを壊さないようにします。[Trino エージェント](/ja/external/trino-agent/) を参照してください。

## ポリシーの例

カタログ `hive` にスキーマ `sales` があり、その中に `region`、`customer`、`card` などのカラムを持つテーブル `orders` と、テーブル `returns` があるとします。同じデータサービスに次の有効なポリシーを作成します。

| ポリシー | リソース | ルール |
| --- | --- | --- |
| アクセス | `catalog=hive`、`schema=sales` | グループ `analysts` に `select` を許可 |
| アクセス | `catalog=hive`、`schema=sales`、`table=orders`、`column=card` | グループ `analysts` に `select` を拒否 |
| アクセス | `catalog=hive`、`schema=sales`、`table=returns` | ユーザー `alice` に `insert` を許可 |
| 行フィルター | `catalog=hive`、`schema=sales`、`table=orders` | ユーザー `alice` に `region = 'EU'` |
| マスキング | `catalog=hive`、`schema=sales`、`table=orders`、`column=customer` | グループ `analysts` に `mask_hash` |
| アクセス | `catalog=system`、`schema=runtime` | ユーザー `alice` に `select` を許可 |
| アクセス | `user=bob` | ユーザー `alice` に `view_query` を許可 |

エージェントが新しいスナップショットを適用すると、`analysts` のメンバーは `orders.card` を除く `sales` のすべてのテーブルを読め、`customer` はハッシュとして見えます。`alice` はヨーロッパの注文だけを見て、`returns` に書き込め、`system.runtime.queries` で自分のクエリと並べて Bob のクエリも見られます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには coordinator に Trino エージェントが必要です。配置方法と検査内容は [Trino エージェント](/ja/external/trino-agent/) を参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [Trino エージェント](/ja/external/trino-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
