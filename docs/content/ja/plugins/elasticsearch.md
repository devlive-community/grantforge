---
title: Elasticsearch
description: Elasticsearch サービスプラグインをインストールして設定し、1 つまたは複数のノードに接続し、パスワード、API キー、TLS クライアント証明書でサインインし、インデックス、データストリーム、フィールド、クラスターを検索し、誰が何を読み、書き、管理できるかを Elasticsearch 自身の権限でフィールドとドキュメントまで管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Elasticsearch プラグインは、インデックス、データストリーム、フィールド、クラスターを検索し、Elasticsearch 自身の考え方でアクセスポリシーを管理します。フィールドの読み取り、ドキュメントの索引付けと削除、インデックスの作成・削除・管理、クラスター自身の操作に加え、インデックスの行フィルターにも対応します。プラグイン ID とサービスタイプはどちらも `elasticsearch` です。サービスプラグインは GrantForge サーバーで動き、クラスターの隣で動く [Elasticsearch エージェント](/ja/external/elasticsearch-agent/) が Elasticsearch 自身のロール、フィールドレベルセキュリティ、ドキュメントレベルセキュリティでポリシーを適用します。

リポジトリの Testcontainers 統合テストは、セキュリティを有効にした Elasticsearch `8.19`、`9.3`、`9.4`、`9.5` を対象としています。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/elasticsearch` にこのプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `elasticsearch` が有効になっていることを確認し、プラグインディレクトリを更新したら再スキャンしてください。プラグインは独自のクラスローダーの中で、Elasticsearch の低レベル REST クライアントを使ってノードと通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して Elasticsearch（`elasticsearch`）を選びます。
2. 1 つまたは複数のノードを URL で入力します。
3. 検索ユーザーのサインイン方法を選び、資格情報を入力して接続をテストし、サービスを保存します。テストはサインインしてクラスターを説明します。Elasticsearch のバージョンとクラスター名、ライセンスとそれがドキュメントレベル・フィールドレベルセキュリティを持つかどうか、サインインしたユーザー、インデックスとデータストリームの数です。セキュリティのないクラスターはテストに失敗します。エージェントは Elasticsearch のロールでポリシーを適用するためです。

検索ユーザーは GrantForge がリソースを見つけるためのもので、読み書きするユーザーとは別です。接続テストが成功しても、アクセスがすでにポリシーに従っているわけではありません。

| 設定 | 用途 |
| --- | --- |
| `elasticsearch.hosts` | 必須：URL 形式のノード。例：`https://es1.example.com:9200`。複数はカンマ区切りで、接続できないときは順に試します。スキームがなければ `https`、ポートの既定は `9200` で、1 つのサービスのノードはすべて `http` かすべて `https` です |
| `authentication` | `password`（既定）、`api_key`、または `certificate`：PKI レルムがユーザーに対応付ける TLS クライアント証明書 |
| `username`、`password` | password：検索ユーザーとそのパスワード。パスワードは暗号化して保存 |
| `apiKey` | api_key：Elasticsearch が作成時に返すとおりのエンコード済み API キー。暗号化して保存 |
| `ssl.verificationMode` | Kibana と同じ名前：`full`（既定）はノードの証明書と名前を、`certificate` は証明書だけを確認し、`none` は何も確認しません |
| `ssl.certificateAuthorities` | ノードを検証する **GrantForge サーバー** 上の PEM CA 証明書のパス。Elasticsearch の `http_ca.crt` など。空なら JVM のものを使います |
| `ssl.certificate`、`ssl.key` | certificate：GrantForge サーバー上の PEM クライアント証明書とその秘密鍵のパス。鍵は PKCS #8 または旧来の OpenSSL 形式。`https` のノードが必要です |
| `ssl.keyPassphrase` | certificate：暗号化された秘密鍵のパスフレーズ。暗号化して保存 |
| `timeout` | 接続とリクエストにかけてよい時間。既定 `30000`、範囲 `1..600000` ミリ秒 |

あるサインイン方法の設定は他の方法では拒否されるため、サービスはどうサインインするかを明確にします。拒否されたサインイン、失効した API キー、足りない権限、接続できないノード、検証できない証明書は、そのとおりに報告されます。

### 検索ユーザーに見えるもの

検索ユーザーは一覧にするものを読める必要があります。インデックスとデータストリームは Elasticsearch の resolve API、フィールドはフィールド機能（field capabilities）、クラスター名はクラスターの説明から得ます。インデックスに対する `read` と `view_index_metadata` を持つロールが前の 2 つを与えます。クラスター権限 `monitor` のないユーザーも接続テストには合格し、その場合テストはバージョンとライセンスを示しません。名前がドットで始まるインデックスとデータストリーム、つまり隠しインデックスとシステムインデックスは、入力がドットで始まるときだけ一覧に出ます。データストリームのバッキングインデックスはそのデータストリームとして一覧に出ます。フィールドはドキュメントのフィールドです。Elasticsearch 自身のメタデータフィールド、オブジェクト、`name.keyword` のようにフィールドをもう一度索引するマルチフィールドは除外されます。エージェントはそれらを元のフィールドとともに付与するためです。

## リソースとアクセスタイプ

| レベル | 照合 | アクセスタイプ |
| --- | --- | --- |
| `index` | ワイルドカード、大文字小文字を区別。インデックスまたはデータストリームの名前 | `index`、`delete`、`create_index`、`delete_index`、`view_index_metadata`、`monitor`、`manage` |
| `field` | ワイルドカード、大文字小文字を区別。インデックスの下、`address.city` のようなフィールドのパス | `read` |
| `cluster` | ワイルドカード、大文字小文字を区別。クラスターの名前 | `cluster_monitor`、`create_snapshot`、`manage_slm`、`manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` |

各アクセスタイプは同じ名前の Elasticsearch の権限です。ただし `cluster_monitor` はクラスター権限 `monitor` です。

| アクセスタイプ | 許可すること |
| --- | --- |
| `read` | 検索、取得、カウント、マルチ検索、スクロール |
| `index`、`delete` | ドキュメントの索引付けと更新（bulk も含む）、ドキュメントの削除 |
| `create_index`、`delete_index` | インデックスの作成（最初の書き込みで作られる場合も含む）、インデックスとデータストリームの削除 |
| `view_index_metadata`、`monitor` | マッピング、設定、エイリアスの読み取り、統計とリカバリーの読み取り |
| `manage` | インデックスのすべての管理操作。`create_index`、`delete_index`、`view_index_metadata`、`monitor` を含みます |
| `cluster_monitor` | クラスターのヘルス、状態、統計の読み取り |
| `create_snapshot`、`manage_slm` | スナップショットの作成、スナップショットライフサイクルポリシーの管理（`create_snapshot` を含む） |
| `manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` | インデックステンプレート、取り込みパイプライン、インデックスライフサイクルポリシーの管理 |

読み取りは、フィールドレベルセキュリティがドキュメントのフィールドを表示するとおりにフィールドごとに決まります。`read` のポリシーはフィールドを指定し、`*` はすべてのフィールドです。そのため、ポリシーはインデックスを許可しつつその中の 1 フィールドを拒否でき、ユーザーは残りのすべてのフィールドを読めます。ポリシーの値では、`*` は任意の文字列に、`?` は 1 文字に一致します。ポリシーエディターは、インデックスとデータストリーム、ポリシーが指すインデックスのフィールド、クラスターを候補として示します。

### 行フィルター

インデックスの行フィルターポリシーは、クエリに一致するドキュメントだけをユーザーに見せます。クエリはドキュメントレベルセキュリティが受け取る Elasticsearch のクエリ DSL で、例えば `{"term": {"region": "EU"}}` です。`{{_user.username}}` を使うようなテンプレートにもできます。他のサービスと同様に、ユーザーには自分に適用される最初のポリシーのフィルターが適用されます。行フィルターが確かめられるのはユーザーが読めるフィールドだけです。フィールドレベルセキュリティはクエリからも他のフィールドを隠します。

Elasticsearch は値をマスクしないため、このサービスタイプはマスクを提供しません。

### ライセンス

Elasticsearch のドキュメントレベル・フィールドレベルセキュリティは Platinum ライセンスから使えます。それより下では、ユーザーにインデックスの一部のフィールドや一部のドキュメントだけを読ませるポリシーは適用できません。そのためエージェントは、ポリシーが許す以上を与える代わりに、そのユーザーにインデックスの読み取りを付与せず、その旨を伝えます。インデックス全体、書き込み、管理を許可するポリシーはどのライセンスでも動きます。

## ポリシーの例

Platinum ライセンスのクラスターにインデックス `logs-2026.10.26` と `hr` があるとします。同じデータサービスに、次の有効なポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `index=logs-*`、`field=*` | グループ `analysts` に `read` を許可 |
| `index=logs-*`、`field=client_ip` | グループ `analysts` に `read` を拒否 |
| `index=logs-*` | ユーザー `logstash` に `index` と `create_index` を許可 |
| `index=hr`、`field=*` | グループ `hr` に `read` を許可 |
| `index=hr`（行フィルター） | ユーザー `emea-hr` に `{"term": {"region": "EMEA"}}` のドキュメントだけを表示 |
| `cluster=*` | グループ `ops` に `cluster_monitor` を許可 |

エージェントが新しいスナップショットを適用すると、`analysts` のメンバーはクライアントのアドレスを除くすべてのログフィールドを読め、`logstash` は明日の `logs-2026.10.27` にすぐ書き込み、最初のドキュメントでそれを作成し、`hr` のメンバーは従業員を読め、`emea-hr` はその地域の従業員だけを見て、`ops` のメンバーはクラスターの状態と統計を読めます。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、クラスターごとに 1 つの Elasticsearch エージェントが必要です。配置と確認は [Elasticsearch エージェント](/ja/external/elasticsearch-agent/) にあります。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [Elasticsearch エージェント](/ja/external/elasticsearch-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
