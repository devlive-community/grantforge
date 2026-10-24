---
title: Apache Pulsar
description: Pulsar サービスプラグインをインストールして設定し、1 台または複数の broker に接続し、トークン、パスワード、OAuth 2.0 またはクライアント証明書でログインし、テナント、名前空間、トピック、サブスクリプションを検索して、誰がどこでプロデュース・コンシュームし、何を管理できるかを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Pulsar プラグインはテナント、名前空間、トピック、サブスクリプション、クラスターを検索し、Pulsar が判断すること、つまりプロデュース、コンシューム、Functions、Sources、Sinks、Packages、そしてトピック、名前空間、テナント、クラスターの管理についてアクセスポリシーを管理します。プラグイン ID とサービスタイプはどちらも `pulsar` です。サービスプラグインは GrantForge サーバーで動作し、broker にインストールした [Pulsar エージェント](/ja/external/pulsar-agent/)がポリシーを適用します。

リポジトリの Testcontainers 統合テストは、Pulsar `3.0.17`、`3.3.9`、`4.0.14`、`4.2.5` の broker を対象としています。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/pulsar` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `pulsar` が有効であることを確認し、更新後はプラグインディレクトリを再スキャンします。プラグインは専用のクラスローダー内で、Pulsar 自身の admin クライアント 4.2.5 を使って broker の Web サービスと通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して Apache Pulsar（`pulsar`）を選びます。
2. 1 台または複数の broker の Web サービス URL を入力します。
3. 検索ロールのログイン方法を選んで資格情報を入力し、接続をテストしてサービスを保存します。テストはログインしてクラスターを説明し、クラスターとテナントの数を示します。

設定は Pulsar 自身の `client.conf` の名前を使うため、そのまま写せます。検索ロールは GrantForge がリソースを見つけるためのもので、プロデュース・コンシュームするロールとは別です。接続テストが成功しても、アクセスがすでにポリシーに従うわけではありません。

| 設定 | 用途 |
| --- | --- |
| `webServiceUrl` | 必須：broker の Web サービス、`http://host:port` または `https://host:port`、既定ポート `8080` または `8443`。複数の broker はカンマ区切り、例：`http://broker1:8080,broker2:8080` |
| `authentication` | `none`（既定）、`token`、`basic`、`oauth2` または `tls` |
| `token` | token：検索ロールのトークン。暗号化して保存されます |
| `username`、`password` | basic：検索ロールのユーザー名とパスワード。パスワードは暗号化して保存されます |
| `issuerUrl`、`privateKey`、`audience`、`scope` | oauth2：認可サーバー、**GrantForge サーバー** 上の `client_id` と `client_secret` を含む JSON 資格情報ファイルのパス、broker が求める場合の audience と scope |
| `tlsCertificateFilePath`、`tlsKeyFilePath` | tls：**GrantForge サーバー** 上のクライアント証明書とその秘密鍵のパス、PEM |
| `tlsTrustCertsFilePath` | https：broker の証明書を検証する CA 証明書、PEM。空の場合は JVM のものを使います |
| `tlsEnableHostnameVerification` | https：broker の証明書がホスト名を含む必要があるか。既定 `true` |
| `timeout` | リクエストに許される時間。既定 `30000`、範囲 `1..600000` ミリ秒 |

あるログイン方法の設定は別の方法では拒否されるため、サービスはどうログインするかを正確に表します。拒否されたログイン、何かを見られないロール、到達できない broker（代わりに応答するプロキシの背後にある場合を含む）は、それぞれそのように報告されます。

### 検索ロールに見えるもの

Pulsar はスーパーユーザーにだけクラスターとテナントの一覧を許します。検索ロールを broker の `superUserRoles` に書くか、エージェントをインストールした後でクラスターの `admin` を許可してください。これもロールをスーパーユーザーにします。検索ロールが見られない名前空間、トピック、サブスクリプションは候補に含まれません。

## リソースとアクセスタイプ

| レベル | 一致 | アクセスタイプ |
| --- | --- | --- |
| `tenant` | ワイルドカード、大文字と小文字を区別 | `admin` 以外のすべて |
| `namespace` | ワイルドカード、大文字と小文字を区別。テナントの下。名前空間自身の名前、例：`orders` | `produce`、`consume`、`functions`、`sources`、`sinks`、`packages`、`admin_topic`、`admin_namespace` |
| `topic` | ワイルドカード、大文字と小文字を区別。名前空間の下。トピック自身の名前、例：`payments` | `produce`、`consume`、`admin_topic` |
| `subscription` | ワイルドカード、大文字と小文字を区別。トピックの下。サブスクリプションの名前 | `consume` |
| `cluster` | ワイルドカード、大文字と小文字を区別。クラスターの名前、例：`standalone` | `admin`：Pulsar のスーパーユーザー権限 |

| アクセスタイプ | 含むもの |
| --- | --- |
| `produce`、`consume`、`functions`、`sources`、`sinks`、`packages` | — |
| `admin_topic`：トピックの終了、コンパクション、階層型ストレージへのオフロード、アンロード、切り詰め、ポリシーの変更 | `produce`、`consume` |
| `admin_namespace`：トピックの作成と削除、名前空間のポリシーの変更 | `admin_topic` |
| `admin_tenant`：名前空間の作成、一覧、削除 | `admin_namespace`、`functions`、`sources`、`sinks`、`packages` |
| `admin`：Pulsar のスーパーユーザーができるすべて | `admin_tenant` |

Pulsar 自身の権限と同様に、テナントや名前空間に対する権限はその中のものに及びます。テナント `sales` の名前空間 `orders` に対する `produce` で、その中のすべてのトピックにプロデュースできます。チェックはテナント、名前空間、トピック、サブスクリプションの順に判断され、どれか一つでも拒否すれば拒否されます。そのため、ポリシーで名前空間を許可しつつ、その中の一つのトピックを拒否できます。トピックの下のサブスクリプションレベルは、ロールがどのサブスクリプションでコンシュームできるかを指定します。トピック `*`、サブスクリプション `billing-*` の `consume` は、名前空間のすべてのトピックのコンシュームを許可しますが、名前が `billing-` で始まるサブスクリプションに限ります。

トピックはドメインを除いた自身の名前で判断されるため、同じ名前の永続トピックと非永続トピックはポリシーを共有します。パーティション化トピックのパーティションは、パーティション化トピック自身として判断されます。ポリシー値では、`*` は任意個の文字、`?` は 1 文字に一致します。ポリシーエディターは、クラスター、テナント、そしてポリシーが指定した範囲の名前空間、トピック、サブスクリプションを提案します。

### クライアントアドレス

ポリシー項目は一部のクライアントにだけ適用できます。**クライアントアドレス**（`ip-range`）条件は、アドレスと `10.1.0.0/16` や `2001:db8::/32` のような範囲を受け付けます。ホスト名は解決しません。プロキシの背後ではプロキシがクライアントになります。ただし、プロキシがクライアント自身のアドレスを転送する場合はそのアドレスです。プロデューサーとコンシューマーは HAProxy プロトコルで、Web サービスは broker が信頼する場合に `X-Forwarded-For` で転送します。

## ポリシーの例

テナント `sales` に名前空間 `orders` と `returns` があるとします。同じデータサービスで、次の有効なアクセスポリシーを作成します。

| リソース | ルール |
| --- | --- |
| `tenant=sales`、`namespace=orders` | グループ `analysts` に `produce` を許可 |
| `tenant=sales`、`namespace=orders`、`topic=audit` | ユーザー `alice` に `produce` を拒否 |
| `tenant=sales`、`namespace=orders`、`topic=*`、`subscription=billing-*` | ユーザー `alice` に `consume` を許可 |
| `tenant=sales` | ユーザー `bob` に `admin_tenant` を許可 |
| `tenant=public` | グループ `ingest` に `10.0.0.0/8` からの `produce` を許可 |
| `cluster=standalone` | グループ `pulsar-admins` に `admin` を許可 |

エージェントが新しいスナップショットを適用すると、`analysts` のメンバーは `sales/orders` のすべてのトピックに、`alice` は `audit` 以外のすべてにプロデュースできます。`alice` はそこで `billing-` のサブスクリプションでのみコンシュームします。`bob` は `sales` で名前空間とトピックを作成して管理します。`ingest` のメンバーは内部ネットワークからだけ `public` テナントにプロデュースします。`pulsar-admins` のメンバーは Pulsar のスーパーユーザーです。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、すべての broker に Pulsar エージェントが必要です。配置とチェックについては [Pulsar エージェント](/ja/external/pulsar-agent/)を参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [Pulsar エージェント](/ja/external/pulsar-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
