---
title: 開発、テストと CI
description: ローカルビルド、テスト、コーディング規約と CI のチェック。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 環境

- JDK 21（成果物は Java 17 バイトコード、ポリシーエンジンは Java 8）
- Node.js 22 と pnpm 8.10.2
- Docker（データベースの統合テスト、性能ベンチマーク、イメージ）
- Python 3.12（CI スクリプト）

## よく使うコマンド

```bash
./mvnw verify                          # コンソールを含むすべての Java モジュールをビルドしてテスト
./mvnw verify -DskipFrontend           # コンソールのビルドをスキップ
bash script/ci/web.sh test             # コンソールのユニットテスト
bash script/ci/web.sh e2e              # コンソールのブラウザテスト（バックエンドのモック）
bash script/ci/e2e_fullstack.sh        # パッケージ化して実際のサービスを起動し、フルスタックテストを実行
bash script/ci/db_integration.sh postgres:17   # 指定したデータベースで統合テストを実行
bash script/ci/perf_benchmark.sh smoke # 小規模な性能ベンチマーク
```

コンソールを開発するときは `core/grantforge-web` で `pnpm dev` を実行します。Vite が `/api` などのリクエストを `localhost:9999` のサービスにプロキシします。

## IDE から起動

`org.devlive.grantforge.server.GrantForge`（モジュール `grantforge-server`）を直接実行すると、デフォルトで H2 データベースを使用します。サーバーはリポジトリの `plugins/` 配下でビルド済みのプラグインモジュールを自動的にロードします（[プラグインとサービスタイプ](/ja/develop/plugins/) を参照）。プラグインモジュールを初めて使う前に、`./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` を一度実行して依存関係をコピーしてください。

## コーディング規約

- バックエンドは Error Prone と NullAway（デフォルトは非 null、null になり得る箇所には JSpecify の `@Nullable`）、Checkstyle、PMD、SpotBugs を使います。ArchUnit が共通の規約を守ります（フィールドインジェクションの禁止、ネイティブ SQL の禁止、エンティティが API に現れないこと、`Optional.get()` の禁止など）。
- フロントエンドは TypeScript の strict モードと ESLint の警告ゼロを守り、文言はすべて i18n を通します。キーは必ずリテラルでなければならず、中国語のキーと英語のキーは完全に一致している必要があります。
- すべてのソースファイルには MIT ライセンスヘッダーがあり、すべての主要コードクラスには対応するテストクラスが必要です（例外は `script/ci/test_mapping_exclusions.txt` に登録）。
- カバレッジのしきい値はモジュールごとに設定します（`script/ci/coverage_thresholds.txt`）。
- データベースのマイグレーションは Liquibase YAML で、変更 1 件につきファイル 1 つ、追加のみで修正はできません。型には `${text}` などのデータベース共通プロパティを使います。
- コミットメッセージは Conventional Commits に従い、タイトルは 72 文字を超えません。

## CI

| 作業 | 内容 |
| --- | --- |
| Repository hygiene | ライセンスヘッダー、禁止されているパス、テストマッピング、i18n、権限マニフェスト、ファイル形式、スクリプトとワークフローのチェック |
| Commit messages | コミットメッセージの形式 |
| CI script unit tests | CI スクリプト自身のテスト |
| Java 17 / 21 / 25 / latest | すべての Java モジュールのビルドとテスト、バイトコードバージョンのチェック |
| Java static analysis | カバレッジ、Checkstyle、SpotBugs、PMD |
| Frontend | API の型とコントラクトの一致、型チェックとビルド、ESLint、ユニットテスト、ブラウザテスト |
| JavaScript SDK | 型チェック、ビルド、ESLint、ユニットテスト |
| Database | H2、PostgreSQL 14/17、MySQL 8.0/8.4、MariaDB 10.11/11.4、Oracle 23、SQL Server 2022 でのマイグレーションと統合テスト |
| Plugin API compatibility | 前回リリースとのプラグインコントラクトの比較 |
| Full-stack acceptance | PostgreSQL 上でパッケージ化してサービスを起動し、フルスタックブラウザテストを実行 |
| Docs | ドキュメントサイトのチェック、テスト、ビルド |

性能ベンチマークは毎晩別途実行され、セキュリティワークフローが依存関係とシークレットをスキャンします。

## リリース

バージョン番号は `年.マイナー.パッチ`（例：`2026.0.0`）形式で、リリース候補には `-rc.N` を付けます。すべての pom、npm パッケージ、Helm Chart の appVersion、コンソールのサイドバー、README のバージョンは一致している必要があり、CI が `check_versions.py` でチェックします。

`dev` ブランチで 1 つのコマンドでリリースします。

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # チェックとリリースノートのプレビューのみを行い、変更はしません
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # バージョンを設定し v2026.1.0 タグを打ってプッシュしたあと、dev を次のバージョンに切り替えます
```

スクリプトは作業ツリーがクリーンで、ローカルブランチがリモートより遅れていないこと、タグが存在しないことを要求します。確認後、`chore(release): prepare <バージョン>` をコミットし、注釈付きタグを作成してプッシュします。タグが `release.yml` をトリガーします。

- `script/ci/release.sh` がリリースパッケージ、リリース依存のみを含む CycloneDX SBOM、`SHA256SUMS` をビルドします。
- マルチアーキテクチャのイメージを `ghcr.io/devlive-community/grantforge` にプッシュします。
- Maven アーティファクト（ソースパッケージと Javadoc を含む）を GitHub Packages に公開します。リポジトリに `CENTRAL_USERNAME`、`CENTRAL_PASSWORD`（Central Portal のトークン）、`GPG_PRIVATE_KEY`、`GPG_PASSPHRASE` が設定されている場合は、署名して Maven Central に公開します。
- GitHub Release を作成します。本文には前回のリリース（`v*` や `1.0.6` のような数値タグ）以降のすべてのコミットを、新機能、バグ修正、性能などにグループ化してコミットリンクとともに掲載します。

リリース候補はプレリリースとしてマークされ、イメージの `latest` は更新しません。ローカルで `central` プロファイルを有効にしてもデフォルトでは公開しません（`central.skip=true`）。リリースワークフローだけが `-Dcentral.skip=false` を明示的に渡します。

## 権限マニフェスト

コンソールのページ、ボタンとそれらが必要とする API は `core/grantforge-web/src/permissions/` に宣言します。`check_permission_manifest.py` は、宣言された API がすべて存在すること、権限を必要とするインターフェースがすべてなんらかのボタンまたはページでカバーされていることを確認します（直接呼び出す例外は `script/ci/permission_direct_apis.txt` に登録）。

## ドキュメント

このサイトは `docs/` にあり、Next.js の静的エクスポートと Tailwind CSS を使います。

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # ページ、リンク、画像のチェック
pnpm build      # docs/out に出力
```

ページは `docs/content/` 配下の Markdown で、ナビゲーションは `docs/lib/navigation.ts` にあります。API リファレンスとエラーコードはビルド時にコントラクトとソースコードから生成します。スクリーンショットは `script/docs/screenshots.sh` が実際のサービスを起動してサンプルデータを書き込んだあと、Playwright で生成します。
