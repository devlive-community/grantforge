---
title: Apache Kafka
description: Kafka サービスプラグインをインストール・設定し、平文または TLS で PLAIN、SCRAM、Kerberos によりサインインして、トピック、グループ、トランザクション ID、ユーザーを検索し、Kafka 自身の概念でアクセスポリシーを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Kafka プラグインは、トピック、コンシューマーグループ、トランザクション ID、SCRAM ユーザー、委任トークンを検索し、Kafka 自身の概念でアクセスポリシーを管理します。Kafka のリソースタイプがリソースレベル、ACL 操作がアクセスタイプです。プラグイン ID とサービスタイプはいずれも `kafka` です。サービスプラグインは GrantForge サーバーで動作し、ブローカーとコントローラーにインストールした [Kafka エージェント](/ja/external/kafka-agent/)がポリシーを適用します。

リポジトリの Testcontainers 統合テストは、SASL PLAIN と SCRAM のサインイン、Kafka 自身のオーソライザーを使って Kafka `3.7.2`、`3.9.2`、`4.1.2`、`4.3.1` を対象とし、`4.3.1` では Kerberos（keytab とパスワード）と TLS も対象とします。これは他のすべてのバージョンを認定するものではありません。

## インストール

配布物の `plugins/kafka` にプラグインが含まれています。**プラットフォーム管理 → プラグイン** で `kafka` が有効であることを確認し、プラグインディレクトリを更新したら再スキャンしてください。プラグインは独自のクラスローダーで Kafka 自身の Admin クライアント（`kafka-clients` 4.3.1）を使ってブローカーと通信します。

## データサービスの追加

1. **データ権限 → データサービス** を開き、サービスを追加して Apache Kafka（`kafka`）を選択します。
2. ブートストラップサーバーと、ブローカーのリスナーの保護方式を入力します。
3. 検索ユーザーを入力し、接続をテストしてからサービスを保存します。テストはサインインしてクラスターを記述します。クラスター ID とブローカー数です。

設定は Kafka クライアント自身の名前を使うため、クライアント設定からそのままコピーできます。検索ユーザーは GrantForge がリソースを見つけるためのもので、メッセージを生成・消費するクライアントとは別です。接続テストが成功しても、リクエストがすでにポリシーの対象になっているわけではありません。

| 設定 | 用途 |
| --- | --- |
| `bootstrap.servers` | 必須。起点となるブローカー。`ホスト:ポート` をカンマ区切りで指定。例：`kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT`（既定）、`SSL`、`SASL_PLAINTEXT`、`SASL_SSL` |
| `sasl.mechanism` | SASL プロトコルのみ：パスワードを使う `PLAIN`、`SCRAM-SHA-256`、`SCRAM-SHA-512`、Kerberos の `GSSAPI` |
| `username` | SASL：検索ユーザー。Kerberos では完全なプリンシパル。例：`grantforge@EXAMPLE.COM` |
| `password` | ユーザーのパスワード、または keytab がない場合はプリンシパルの Kerberos パスワード。暗号化して保存されます |
| `keytab` | Kerberos：**GrantForge サーバー**上で読めるプリンシパルの keytab のパス |
| `kerberos.kdc` | Kerberos：プリンシパルのレルムの KDC。`ホスト[:ポート]` をカンマ区切りで指定。空ならサーバーの `krb5.conf` を使います |
| `sasl.kerberos.service.name` | Kerberos：ブローカーのプリンシパルの最初の部分。既定 `kafka` |
| `ssl.truststore.location`、`ssl.truststore.password`、`ssl.truststore.type` | TLS：ブローカーを検証するトラストストア（`JKS`、`PKCS12`、`PEM`）。空なら Java が信頼するものを信頼します |
| `ssl.keystore.location`、`ssl.keystore.password`、`ssl.key.password` | TLS：クライアント証明書を求めるブローカー向けの GrantForge の証明書 |
| `ssl.verify.hostname` | TLS：ブローカーの証明書に接続先ホスト名が必要かどうか。既定 `true` |
| `request.timeout.ms` | リクエストのタイムアウト。既定 `30000`、範囲 `1..600000` |

SASL の設定は `PLAINTEXT` と `SSL` で、TLS の設定は `PLAINTEXT` と `SASL_PLAINTEXT` で拒否されます。`PLAIN` と SCRAM にはパスワードが、`GSSAPI` にはパスワードか keytab が必要です。

### Kerberos

`GSSAPI` では、GrantForge は Kafka 自身の Kerberos ログインでプリンシパルをサインインさせます。keytab を使うか、GrantForge がログインに直接渡すパスワードを使います。GrantForge サーバーにほかの Kerberos 設定は不要です。`kerberos.kdc` に KDC を入力したレルムは、HDFS や Hive プラグインと同じ方法で GrantForge サーバー全体に宣言されるため、複数のサービスが異なるレルムを並行して使えます。

### 検索ユーザーに見えるもの

Kafka はユーザーが記述（describe）できるものだけを見せるため、検索ユーザーには候補として示すトピック、グループ、トランザクション ID への `describe` と、SCRAM ユーザー用にクラスターへの `describe` が必要です。Kafka は記述できないトピックとグループを除外するため、候補は拒否ではなく空になります。`__consumer_offsets` などの内部トピックは、入力が `_` で始まると候補に出ます。委任トークンや SCRAM のないクラスターでは、それらは候補に出ません。

[Kafka エージェント](/ja/external/kafka-agent/)がポリシーを適用すると、検索ユーザーが記述できるものもポリシーが決めます。`describe` を許可するポリシーを与えるか、ブローカーの `super.users` に含めてください。

## リソースとアクセスタイプ

| レベル | Kafka のリソース | アクセスタイプ |
| --- | --- | --- |
| `topic` | トピック | `read`、`write`、`create`、`delete`、`alter`、`describe`、`describe_configs`、`alter_configs`、`all` |
| `group` | コンシューマーグループ | `read`、`delete`、`describe`、`all` |
| `transactional_id` | トランザクション ID | `write`、`describe`、`all` |
| `cluster` | クラスター。名前は常に `kafka-cluster` | `create`、`alter`、`describe`、`cluster_action`、`describe_configs`、`alter_configs`、`idempotent_write`、`all` |
| `delegation_token` | 委任トークン（トークン ID 単位） | `describe`、`all` |
| `user` | 委任トークンを作成・記述される対象のユーザー | `create_tokens`、`describe_tokens`、`all` |

各レベルは独立しており、1 つのポリシーが 1 つのレベルを指定します。値は大文字と小文字を区別し、`*` は任意個の文字、`?` は 1 文字に一致します。アクセスタイプは Kafka の ACL 操作と同じように他のタイプを含みます。`read`、`write`、`delete`、`alter` は `describe` を、`alter_configs` は `describe_configs` を含み、`all` はほかのすべてを含みます。

この包含は拒否にも適用されます。トピックの `write` を拒否するポリシーは `write` が含む `describe` も拒否するため、ユーザーはそのトピックを見られなくなります。Kafka 自身の ACL は、許可された操作にだけ包含を適用します。

ポリシーエディターはクラスターから前方一致で名前を提案し、`cluster` には `kafka-cluster` を提案します。

## ポリシーの例

ユーザー `alice` のプロデューサーが名前が `orders` で始まるトピックに書き込み、グループ `analysts` がコンシューマーグループ `billing` でそれを読むとします。同じデータサービスに、有効なアクセスポリシーを 3 つ作成します。

| リソース | ルール |
| --- | --- |
| `topic=orders*` | ユーザー `alice` に `write` を許可 |
| `topic=orders*` | グループ `analysts` に `read` を許可 |
| `group=billing` | グループ `analysts` に `read` を許可 |

エージェントが新しいスナップショットを適用すると、`alice` は `orders-eu` に生成でき、クラスターの `idempotent_write` なしで冪等プロデューサーも使えます。`analysts` のメンバーは `billing` として消費し、ほかのトピックとグループは両者を拒否します。`kafka-acls --list` は 3 つのポリシーを前方一致 ACL とリテラル ACL として表示します。

## ポリシーの適用

サービスプラグインはポリシーを管理し、リソースを検索します。ポリシーを適用するには、すべてのブローカーとコントローラーに Kafka エージェントが必要です。配置方法と検査内容は [Kafka エージェント](/ja/external/kafka-agent/)を参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [Kafka エージェント](/ja/external/kafka-agent/)
- [プラグインとサービスタイプ](/ja/develop/plugins/)
