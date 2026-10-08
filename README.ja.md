<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge ロゴ" />

# GrantForge

統合権限プラットフォーム · ユーザー、ロール、メニュー、API、データ行とフィールド · 外部データシステム

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · 日本語 · [Deutsch](README.de.md) · [フランス語](README.fr.md) · [スペイン語](README.es.md)

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

外部システム対応には、プラグインフレームワーク、ポリシーエディター、署名付き配布、アクセス監査、HDFS サービスタイプと、Hadoop 2.7、2.10、3.2、3.3、3.4、3.5 向けの番号付き NameNode エージェントが含まれます。Hive プラグインは開発中です。

## HDFS エージェントの対象バージョン

| Hadoop 基準バージョン | コンテナーの Java | エージェントのディレクトリー |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8（amd64 イメージ） | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

クラスターの Hadoop 系列に合わせて、`agents/hdfs/<line>/` から `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` を選択します。共有ロジックは Java 8、3.5 アダプターは Java 17 を対象とします。

Hadoop 2.7、2.10、3.2、3.3 には、エージェントが利用するスーパーユーザー認可コールバックがありません。これらのスーパーユーザーのアクセスは Hadoop が管理します。GrantForge ポリシーで制御するデータアクセスには一般ユーザーを使ってください。

## 動作の仕組み：2 つのプレーン

- **管理プレーン**: GrantForge サーバー（Spring Boot 4.1、Java 17 バイトコード）と Vue 3 コンソールが、テナント、アカウント、組織、ロール、権限付与、監査、データサービスとポリシーを担います。
- **データプレーン**: 保護対象システム内部に組み込むエージェント。エージェントはトークンで Ed25519 署名済みのポリシースナップショットを取得してローカルにキャッシュし、アクセスが行われる前に毎回判定し（ポリシーがなければ拒否）、アクセスイベントを監査用にサーバーへ報告します。

独自のシステムが HDFS の形に合わせる必要はありません。通常のアプリケーションはオープン API や Spring Boot starter でプロセス内で権限を評価し、データベースやファイルシステムなどストア内部のアクセスを横取りする必要があるシステムだけが、`core/grantforge-agent-core` に対してエージェントを実装します。

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

## プロジェクト構成

Maven ルート座標: `org.devlive.grantforge:grantforge:2026.0.0`。Java パッケージ接頭辞: `org.devlive.grantforge`。メインクラス: `org.devlive.grantforge.server.GrantForge`。

`core/` にサーバーと共有基盤、`plugins/` にサーバーが読み込むサービスタイプのプラグイン、`agents/` に保護対象システム内部に展開するエージェントが入ります。共有ライブラリ `grantforge-agent-core` は `core/` に、HDFS NameNode エージェントは `agents/grantforge-agent-hdfs-*` にあります。

| モジュール | 役割 |
| --- | --- |
| `core/grantforge-server` | Spring Boot のエントリーポイント: REST API、セキュリティ設定、オープン API、および Web コンソールの配信 |
| `core/grantforge-web` | Vue 3 / TypeScript / Tailwind CSS のコンソール |
| `core/grantforge-common` | エラーコードと problem details、CSV、エンドポイントアクセスのアノテーション |
| `core/grantforge-persistence` | エンティティ、テナント絞り込み、TSID、Liquibase、データ・フィールド権限 SPI |
| `core/grantforge-audit` | 監査イベントの記録、照会、保持とアーカイブ |
| `core/grantforge-identity` | テナント、アカウント、部門、グループ、職位、ログインとセッション、二段階認証、アイデンティティソース |
| `core/grantforge-authz` | リソースカタログ、ロール、権限付与、割り当てと評価、データ・フィールドポリシー、職務分離、申請と確認 |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | サービスタイプのプラグイン契約と、プラグインの読み込み・分離・呼び出し |
| `core/grantforge-policy-engine` | 外部システム向けポリシー評価エンジン（Java 8 API、エージェントに組み込み可能） |
| `core/grantforge-agent-core` | エージェント共通コード: 設定、署名済みスナップショット、アクセス判定、監査送信 |
| `core/grantforge-service` | データサービス、ポリシースナップショットの署名と配布、エージェントとアクセス監査 |
| `core/grantforge-oauth` | Spring Authorization Server 上に構築した OAuth 2.1 / OIDC サーバー |
| `plugins/grantforge-plugin-hdfs` | HDFS サービスタイプのプラグイン: ポリシー管理とリソース検索 |
| `plugins/grantforge-plugin-example` | カスタムサービスタイプの例となるプラグイン |
| `agents/grantforge-agent-hdfs-common` | 共有 HDFS 認可、設定、スナップショット、監査ロジック（Java 8） |
| `agents/grantforge-agent-hdfs-*` | Hadoop 2.7、2.10、3.2、3.3、3.4、3.5 向け番号付き NameNode エージェント：認可とアクセス監査 |
| `sdk/grantforge-spring-boot-starter`、`sdk/grantforge-js` | アプリケーション連携用の Java と JavaScript SDK |
| `script/ci`、`deploy/` | CI チェックスクリプト（ローカルと CI で同じもの）とデプロイ用リソース（Dockerfile、Compose、Helm） |

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
