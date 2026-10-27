---
title: Apache Solr
description: Solr サービスプラグインをインストール・設定し、1 つまたは複数のノードに接続し、basic 認証か JSON Web Token でサインインして、コレクションとそのリクエストパスを検索し、誰が何を検索・更新・管理できるかを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Solr プラグインはコレクションとその中のリクエストパスを検索し、Solr が判断する操作のアクセスポリシーを管理します。対象は、検索、更新、schema と config の読み取りと変更、コレクションの管理、クラスターの管理です。プラグイン ID とサービスタイプはどちらも `solr` です。サービスプラグインは GrantForge サーバーで動作し、ノードにインストールした [Solr エージェント](/ja/external/solr-agent/) がポリシーを適用します。

リポジトリの Testcontainers 統合テストは Solr `9.8.1`、`9.10.1`、`9.11.0`、`10.0.0` の SolrCloud ノードを対象とします。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/solr` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `solr` が有効であることを確認し、更新後はプラグインディレクトリを再スキャンします。プラグインは専用のクラスローダー内で、SolrJ 9.11 の JDK HTTP クライアントを使ってノードと通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して Apache Solr（`solr`）を選びます。
2. 1 つのノード、または複数のノードのベース URL を入力します。
3. 検索ユーザーのサインイン方法を選んで資格情報を入力し、接続をテストしてサービスを保存します。テストはサインインしてクラスターを説明します。Solr のバージョン、SolrCloud で動作しているか、コレクションがいくつあるかです。

検索ユーザーは GrantForge がリソースを見つけるためのもので、検索や更新を行うユーザーとは別です。接続テストが成功しても、アクセスがすでにポリシーに従っているわけではありません。

| 設定 | 用途 |
| --- | --- |
| `solr.urls` | 必須: ノードのベース URL（例: `https://solr1.example.com:8983/solr`）。複数はカンマ区切りで、到達できないノードがあれば順に次を試します |
| `authentication` | `none`（既定）、`basic`、`jwt` |
| `username`、`password` | basic: 検索ユーザーとそのパスワード。パスワードは暗号化して保存されます |
| `token` | jwt: 検索ユーザーの JSON Web Token。暗号化して保存されます |
| `javax.net.ssl.trustStore`、`javax.net.ssl.trustStorePassword`、`javax.net.ssl.trustStoreType` | https: **GrantForge サーバー** 上でノードの証明書を検証するトラストストア、そのパスワードと種類（既定 `PKCS12`）。空の場合は JVM のものを使います |
| `timeout` | リクエストにかけられる時間。既定 `30000`、範囲 `1..600000` ミリ秒 |

あるサインイン方法の設定は別の方法では拒否されるため、サービスはどのようにサインインするかを明確に示します。拒否されたサインイン、何かを見る権限のないユーザー、到達できないノード（前段のプロキシが代わりに応答する場合も含む）は、そのとおりに報告されます。

### 検索ユーザーに見えるもの

コレクションの一覧は Solr の `collection-admin-read` 権限で、コレクションのリクエストパスはその config、つまり `config-read` から得られます。Solr 自身のルールで検索ユーザーにこれらを与えるか、エージェントのインストール後にクラスターの `admin_read` とコレクションの `config_read` を与えます。検索ユーザーが見られないコレクションのパスは候補に表示されません。SolrCloud なしで動作するノードにはコレクションではなく core があり、プラグインはそれをコレクションとして扱います。

## リソースとアクセスタイプ

| レベル | マッチング | アクセスタイプ |
| --- | --- | --- |
| `collection` | ワイルドカード、大文字小文字を区別。コレクション名 | `query`、`update`、`schema_read`、`schema_edit`、`config_read`、`config_edit`、`admin_collection` |
| `path` | ワイルドカード、大文字小文字を区別。コレクションの下。リクエストパス（例: `/select`、`/export`、`/update/json`） | `query`、`update`、`schema_read`、`schema_edit`、`config_read`、`config_edit` |
| `cluster` | 唯一の値 `*` | `health`、`metrics`、`admin_read`、`security`、`admin` |

| アクセスタイプ | Solr の権限 | 含意 |
| --- | --- | --- |
| `query` | `read`: 検索、取得、エクスポート | — |
| `update` | `update` | — |
| `schema_read`、`schema_edit` | `schema-read`、`schema-edit` | `schema_edit` は `schema_read` を含意 |
| `config_read`、`config_edit` | `config-read`、`config-edit` | `config_edit` は `config_read` を含意 |
| `admin_collection` | そのコレクションを指定する Collections API リクエストの `collection-admin-read` と `collection-admin-edit`: 作成、リロード、バックアップ、削除など | `query`、`update`、`schema_edit`、`config_edit` |
| `health`、`metrics` | `health`、`metrics-read` | — |
| `admin_read` | クラスター全体の `collection-admin-read`、`core-admin-read`、`zk-read`、`filestore-read`、`package-read`、ノードの config と schema の読み取り | `health`、`metrics` |
| `security` | `security-read`、`security-edit` | — |
| `admin` | すべて | `admin_read`、`security`、`admin_collection` |

コレクションへのリクエストは、コレクションと「コレクションとリクエストパス」の両方で判定され、どちらかでの拒否がリクエストを拒否します。そのため、ポリシーでコレクションを許可しつつ `/export` ハンドラーを拒否したり、`/update*` 経由の更新だけを許可したりできます。複数のコレクションへのリクエストには、それぞれのコレクションが必要です。コレクションの下では、リクエストパスは Solr がリクエストをルーティングするハンドラーで、名前は Solr のものと同じです。

`name` または `collection` パラメーターでコレクションを指定する Collections API リクエストには、指定された各コレクションの `admin_collection` が必要です。そのため `team-*` に対するポリシーで、チームは名前が `team-` で始まるコレクションを作成し、リロード、バックアップ、削除できますが、それ以外のコレクションは扱えません。コレクションを指定しないリクエスト（すべてのコレクションの一覧など）はクラスターに属します。クラスターの `admin` は、Solr 自身の `all` 権限と同じくすべてを許可します。ポリシーの値では、`*` は任意の文字列、`?` は 1 文字に一致します。ポリシーエディターは、コレクションと、ポリシーが指定したコレクションのリクエストパスを候補として示します。

### クライアントアドレス

ポリシー項目は一部のクライアントにだけ適用できます。**クライアントアドレス** 条件（`ip-range`）は、アドレスと `10.1.0.0/16` や `2001:db8::/32` のような範囲を受け付けます。ホスト名は解決しません。リバースプロキシの背後では、エージェントがそのプロキシを信頼しない限りクライアントはプロキシです。信頼する場合、クライアントは信頼するプロキシが追加していない `X-Forwarded-For` の最後のアドレスとなるため、クライアントが任意のアドレスを名乗ることはできません。

## ポリシーの例

クラスターにコレクション `sales` と `hr` があるとします。同じデータサービスに、次の有効なアクセスポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `collection=sales` | グループ `analysts` に `query` を許可 |
| `collection=sales`、`path=/export` | ユーザー `alice` に `query` を拒否 |
| `collection=sales`、`path=/update*` | ユーザー `ingest` に `update` を許可 |
| `collection=team-*` | グループ `search-team` に `admin_collection` を許可 |
| `cluster=*` | ユーザー `monitoring` に `metrics` と `health` を許可 |
| `collection=hr` | グループ `hr-staff` に `query` を許可（`10.0.0.0/8` から） |
| `cluster=*` | グループ `solr-admins` に `admin` を許可 |

エージェントが新しいスナップショットを適用すると、`analysts` のメンバーは `sales` を検索でき、`alice` はエクスポートハンドラー以外すべてを使えます。`ingest` は更新ハンドラーで `sales` を更新できますが、検索はできません。`search-team` のメンバーは自分たちの `team-` コレクションを作成・管理します。`monitoring` はメトリクスとヘルス状態を読み取ります。`hr-staff` のメンバーは内部ネットワークからのみ `hr` を検索できます。`solr-admins` のメンバーは何でもできます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、すべてのノードに Solr エージェントが必要です。配置と確認内容は [Solr エージェント](/ja/external/solr-agent/) を参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [Solr エージェント](/ja/external/solr-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
