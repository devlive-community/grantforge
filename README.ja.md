<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge ロゴ" />

# GrantForge

統合権限プラットフォーム · ユーザー、ロール、メニュー、API、データ行とフィールド · 外部データシステム

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · 日本語 · [Deutsch](README.de.md) · [フランス語](README.fr.md) · [スペイン語](README.es.md) · [ポルトガル語](README.pt-BR.md) · [イタリア語](README.it.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.1.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge（旧称 AuthX）はオープンソース（MIT）の統合権限プラットフォームです。「誰が何をできるのか」（機能の権限付与）と「誰がどのデータを見られるのか」（データとフィールドの権限付与）という 2 つの問いに、一か所で答えられます。権限はコンソールで定義・説明・監査でき、アプリケーションは標準プロトコルで連携し、HDFS のような外部データシステムはプラグインとエージェントで同じポリシーモデルに組み込まれます。

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="GrantForge コンソール" />
</p>

## 機能

| 領域 | 内容 |
| --- | --- |
| アイデンティティと組織 | マルチテナンシー、部門ツリー、ユーザーグループと職位、CSV 一括インポート・エクスポート、LDAP / Active Directory ログインと同期、OIDC 連携ログイン |
| アカウントセキュリティ | セッション管理と強制ログアウト、ロック付きパスワードポリシー、リカバリコード付き TOTP 二段階認証、重要な操作の再検証 |
| 機能の権限付与 | リソースカタログ（モジュール、メニュー、ページ、タブ、ボタン、API）、ロールの継承、権限付与マトリクス、付与前の影響分析 |
| データ権限 | 行レベル条件（本人、所属部門と配下の部門、指定部門、カスタム条件）で読み取りと書き込みを個別に制御 |
| フィールド権限 | フィールドを非表示、マスク（メールアドレス、電話番号、マイナンバーなど）、または読み取り専用に設定 |
| 説明可能性と監査 | 権限の説明（権限付与の出所）、権限付与のシミュレーション、監査ログの照会とエクスポート |
| ガバナンス | 職務分離の制約、承認付き権限申請、定期的な権限確認 |
| アプリケーション連携 | OAuth 2.1 / OIDC 認可サーバー、権限照会オープン API、Java（Spring Boot starter）と JavaScript SDK |
| 外部システム | プラグインサービスタイプとポリシーエンジン：データサービス、アクセスポリシー、エージェントとアクセス監査 |
| 提供形態 | 実行可能なリリース 1 つ、Docker イメージ、Compose の例、Helm チャート、H2 / PostgreSQL / MySQL / MariaDB / Oracle / SQL Server |

外部システム対応には、プラグインフレームワーク、ポリシーエディター、署名付き配布、アクセス監査、および HDFS、ZooKeeper、MySQL、Hive、Kafka、HBase、Trino、YARN、Pulsar、Solr のサービスプラグインが含まれます。HDFS は番号付き NameNode エージェント、ZooKeeper はサーバー内エージェント、MySQL は独立したネットワークプロキシ、Hive は HiveServer2 内のオーソライザー、Kafka はブローカーとコントローラー内のオーソライザー、HBase は master と region server 内のコプロセッサー、Trino は coordinator 内のシステムアクセス制御、YARN は ResourceManager 内の認可プロバイダー、Pulsar はブローカー内の認可プロバイダー、Solr はノード内の認可プラグインを使用します。接続設定、データベース・テーブル・列の検索とポリシー機能は [MySQL プラグインガイド](https://grantforge.devlive.org/ja/plugins/mysql/)、配置と適用上の制限は [MySQL プロキシエージェントガイド](https://grantforge.devlive.org/ja/external/mysql-agent/) を参照してください。Hive は [Hive プラグインガイド](https://grantforge.devlive.org/ja/plugins/hive/) と [Hive エージェントガイド](https://grantforge.devlive.org/ja/external/hive-agent/) を参照してください。Kafka は [Kafka プラグインガイド](https://grantforge.devlive.org/ja/plugins/kafka/) と [Kafka エージェントガイド](https://grantforge.devlive.org/ja/external/kafka-agent/) を参照してください。HBase は [HBase プラグインガイド](https://grantforge.devlive.org/ja/plugins/hbase/) と [HBase エージェントガイド](https://grantforge.devlive.org/ja/external/hbase-agent/) を参照してください。Trino は [Trino プラグインガイド](https://grantforge.devlive.org/ja/plugins/trino/) と [Trino エージェントガイド](https://grantforge.devlive.org/ja/external/trino-agent/) を参照してください。YARN は [YARN プラグインガイド](https://grantforge.devlive.org/ja/plugins/yarn/) と [YARN エージェントガイド](https://grantforge.devlive.org/ja/external/yarn-agent/) を参照してください。Pulsar は [Pulsar プラグインガイド](https://grantforge.devlive.org/ja/plugins/pulsar/) と [Pulsar エージェントガイド](https://grantforge.devlive.org/ja/external/pulsar-agent/) を参照してください。Solr は [Solr プラグインガイド](https://grantforge.devlive.org/ja/plugins/solr/) と [Solr エージェントガイド](https://grantforge.devlive.org/ja/external/solr-agent/) を参照してください。


## サポートされるコンポーネントとバージョン

以下は、リポジトリの Testcontainers 統合テストで実行されたバージョンです。それ以外のバージョンは認定されていません。

| コンポーネント | サービスプラグイン | 検証済みバージョン | 適用エージェント | エージェントのバージョン | エージェント Java |
| --- | --- | --- | --- | --- | --- |
| Apache Hadoop HDFS | `plugins/grantforge-plugin-hdfs` | 2.7.7、2.10.2、3.2.4、3.3.6、3.4.3、3.5.0 | NameNode エージェント | 2.7、2.10、3.2、3.3、3.4、3.5 | 8（3.5：17） |
| Apache Hadoop YARN | `plugins/grantforge-plugin-yarn` | 3.2.4、3.3.6、3.4.3、3.5.0 | ResourceManager の認可プロバイダ | テストした Hadoop のバージョン線と同じ | 8 |
| Apache Hive | `plugins/grantforge-plugin-hive` | HiveServer2 3.1.3、4.0.1、4.1.0 | HiveServer2 オーソライザ | テストした HiveServer2 のバージョン線と同じ | 8 |
| Apache Kafka | `plugins/grantforge-plugin-kafka` | 3.7.2、3.9.2、4.1.2、4.3.1 | ブローカーとコントローラのオーソライザ | テストした Kafka のバージョン線と同じ | 8 |
| Apache Pulsar | `plugins/grantforge-plugin-pulsar` | 3.0.17、3.3.9、4.0.14、4.2.5 | ブローカーの認可プロバイダ | テストした Pulsar のバージョン線と同じ | 17 |
| Apache Solr | `plugins/grantforge-plugin-solr` | 9.4.1、9.8.1、9.11.0、10.0.0 | ノードの認可プラグイン | テストした Solr のバージョン線と同じ | 11 |
| Apache HBase | `plugins/grantforge-plugin-hbase` | 2.5.16、2.6.7、3.0.0 | マスターとリージョンサーバーのコプロセッサ | テストした HBase のバージョン線と同じ | 8 |
| Trino | `plugins/grantforge-plugin-trino` | 470、476、479、484 | システムアクセス制御 | 470（470〜478）、479（479 以降） | 23 / 25 |
| MySQL | `plugins/grantforge-plugin-mysql` | 8.0、8.4（MySQL 5.7 と MariaDB サーバーは非対応） | 独立したネットワークプロキシ | 単一のプロキシ JAR | 17 |
| Apache ZooKeeper | `plugins/grantforge-plugin-zookeeper` | リポジトリのテストには記載なし | サーバー内エージェント | 3.6.4（ビルド依存） | 8 |

**エージェント Java** 列は、各エージェントがコンパイルされる Java のバイトコード対象です。HDFS エージェントは Hadoop のバージョン線で選びます（例：Hadoop 2.10.2 は `agents/hdfs/2.10/`）。詳細は [HDFS エージェントガイド](https://grantforge.devlive.org/ja/external/hdfs-agent/) を参照してください。Hadoop 2.7、2.10、3.2、3.3 はスーパーユーザー認可コールバックを提供しないため、これらのバージョン線ではスーパーユーザーのアクセスは引き続き Hadoop が制御します。

## 動作の仕組み：2 つのプレーン

- **管理プレーン**: GrantForge サーバー（Spring Boot 4.1、Java 17 バイトコード）と Vue 3 コンソールが、テナント、アカウント、組織、ロール、権限付与、監査、データサービスとポリシーを担います。
- **データプレーン**: 保護対象システム内部に組み込むエージェント、または独立して配置するネットワークプロキシ。トークンで Ed25519 署名済みのポリシースナップショットを取得してローカルにキャッシュし、リソースへのアクセスを認可して監査イベントを送信します。サーバーに一時的に接続できなくても適用済みのスナップショットを使い続けます。検査範囲は各エージェントガイドに記載しています。

独自のシステムが HDFS の形に合わせる必要はありません。通常のアプリケーションはオープン API や Spring Boot starter でプロセス内で権限を評価し、データベースやファイルシステムなどストア内部、またはネットワークのアクセス経路でリクエストを制御する必要があるシステムが、`core/grantforge-agent-core` に対してエージェントを実装します。

## アプリケーションの連携

- **OAuth 2.1 / OpenID Connect**: GrantForge は認可サーバーなので、アプリケーションは GrantForge でユーザーをログインさせられます。既存のアイデンティティソース（LDAP / AD / OIDC）も接続できます。
- **Java アプリケーション**: `sdk/grantforge-spring-boot-starter` が、エンドポイント用の `@RequirePermission`、データエンティティ用の `@GrantForgeEntity`、プラットフォームのデータ権限を JPA `Specification` に変換する `GrantForgeDataScopes.scope(...)` を提供します。
- **フロントエンドアプリケーション**: `@grantforge/client` が自分のオリジンから OIDC + PKCE でユーザーをログインさせ、権限を照会します。
- **オープン API**: `/api/v1/open/me/authorization`、`/api/v1/open/me/data-access`、`/api/v1/open/catalog/data-entities`。
- **実行できるサンプル**: `samples/shop` と `samples/notes` が、第三者が連携する形をそのまま示します。

## クイックスタート

Java 17 以降が必要です。サービスはポート `9999` で待ち受け、初回起動時に一度だけ使える **セットアップトークン** を出力します。ブラウザで <http://127.0.0.1:9999/> を開き、トークンを入力して最初の管理者を作成します。

```bash
# リリースから（または ./mvnw clean package でソースをビルドすると、成果物は dist/ に入ります）
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# または Docker で
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# または Compose でデータベースに接続して
docker compose -f deploy/compose/postgres.yml up -d

# または Kubernetes で
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

組み込みの H2 ファイルデータベースがデフォルトなので、設定なしで起動できます。MySQL ドライバーは GPL ライセンスのためリリースに含めていないので、`drivers/` に入れてください。インストール、初回セットアップ、最初の権限付与については [ドキュメント](https://grantforge.devlive.org) を参照してください。

## データベース

デフォルトは組み込みの H2 ファイルデータベース（`${GRANTFORGE_HOME}/data`）なので、設定なしで起動できます。本番環境では環境変数で切り替えます。スキーマは Liquibase で管理します。

| データベース | バージョン（CI で検証済み） | `GRANTFORGE_DB_URL` の例 |
| --- | --- | --- |
| PostgreSQL | 14、17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0、8.4 | `jdbc:mysql://host:3306/grantforge`（`mysql-connector-j` を `lib/` に追加。GPL ライセンスのためリリースには含めません） |
| MariaDB | 10.11、11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

`GRANTFORGE_DB_USER` と `GRANTFORGE_DB_PASSWORD` も設定してください。クラスター内の各インスタンスは、それぞれ自分の `GRANTFORGE_ID_NODE`（0〜1023）を設定する必要があります。


## 運用と可観測性

- ヘルスプローブ: `/actuator/health/liveness`、`/actuator/health/readiness`（ステータスのみで詳細は返しません。readiness はデータベースに到達できマイグレーションが完了すると 200 を返します）。
- メトリクス: `/actuator/prometheus`（ラベル `application="grantforge"`、既定ではログインが必要。`GRANTFORGE_PROMETHEUS_PUBLIC=true` で信頼できるネットワークに公開できます）。
- ロギング: 既定は 1 行にリクエスト ID が付く読みやすいテキスト。JSON ログにするには `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`（または `logstash`）を設定します。
- リリース用スクリプト: `bin/startup.sh`、`shutdown.sh`、`restart.sh`、`debug.sh`、`import-legacy.sh`。

## 開発と検証

ビルドには JDK 17 以降が必要です。サーバーと Hadoop 3.5 アダプターは Java 17、ポリシーエンジン、エージェントコア、Hadoop 2.7–3.4 アダプターは Java 8 を対象とします。JDK 21 以降では Error Prone と NullAway が有効になります。フロントエンドは Vue 3.5、Tailwind CSS 4、Node.js 22.12 以降、pnpm 8.10.2 を使います。

```sh
# Java のビルドとユニットテスト（コンソールのビルドはスキップ）
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# 指定したデータベースでの永続化統合テスト（h2 以外は Docker が必要）
bash script/ci/db_integration.sh postgres:17

# リリースのパッケージ化（コンソールのビルドを含めて dist/ へ）
./mvnw clean package

# フロントエンドの開発とチェック
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# サンプルアプリケーションと SDK
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# API コントラクト: サーバー変更後に openapi.json とフロントエンドの型を再生成（CI で両方チェックされます）
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# ドキュメントサイト（docs/、Next.js + Tailwind CSS）
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # 実際のサービスとサンプルデータでスクリーンショットを再生成

# リポジトリのチェック（CI が実行するものと同じ）
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## リンク

- [リポジトリ](https://github.com/devlive-community/grantforge)
- [ドキュメント](https://grantforge.devlive.org): クイックスタート、ユーザーガイド、連携と技術リファレンス。ソースは [`docs/`](docs/)
- [コントリビューション](CONTRIBUTING.md) · [行動規範](CODE_OF_CONDUCT.md) · [変更履歴](CHANGELOG)
