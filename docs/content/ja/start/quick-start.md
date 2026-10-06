---
title: 5分でセットアップ
description: リリースパッケージまたは Docker で GrantForge を起動し、初期化を完了して、ユーザーを作成し最初のロールに権限を付与します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

このページでは、デフォルトの組み込み H2 データベースを使ってローカルで GrantForge を起動します。本番環境では [リリースパッケージのインストール](/ja/deploy/installation/) と [データベース](/ja/deploy/databases/) を参照してください。

## 1. サービスの起動

Java 17 以降が必要です。[GitHub Releases](https://github.com/devlive-community/grantforge/releases) からリリースパッケージをダウンロードするか、ソースディレクトリで `./mvnw -DskipTests package` を実行して自分でビルドし（成果物は `dist/grantforge-release.tar.gz`）、解凍して起動します。

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

Docker を使うこともできます。まずリリースパッケージからイメージをビルドし、Compose のサンプルで起動します。

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

サービスはデフォルトで `9999` ポートを待ち受けます。初回起動時にデータベースのテーブルを作成し、ログに 1 回限りの**初期化トークン**を出力します。

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

リリースパッケージのログは `logs/grantforge.log` に出力され、Docker では `docker compose logs` で確認できます。

## 2. 初期化の完了

ブラウザで http://127.0.0.1:9999/ を開くと、コンソールは自動的に初期化ページへ移動します。ログに出力されたトークン、組織名、最初の管理者のユーザー名とパスワード（12 文字以上）を入力します。

![初期設定とログインページ](/screenshots/login.png)

> [!TIP]
> 自動インストールのときは、環境変数 `GRANTFORGE_SETUP_TOKEN` でトークンを事前に指定できます。[設定リファレンス](/ja/reference/configuration/) を参照してください。

初期化が完了すると、この管理者は**テナント管理者**と**プラットフォーム管理者**の 2 つのシステムロールを保持し、コンソールのすべての機能を使えます。初期化ページはその後永久に閉じられます。

## 3. ユーザーの作成

**アクセス制御 → ユーザー管理**に移動し、「ユーザー作成」をクリックして、ユーザー名、初期パスワード、主部門を入力します。新しいユーザーは初回ログイン時にパスワードを変更する必要があります。

## 4. ロールの作成と権限付与

1. **アクセス制御 → ロール管理**に移動し、「新しいロール」をクリックして、例えば「読み取り専用の監査担当者」のように名前を指定します。
2. ロールの行で「権限付与」をクリックし、権限付与マトリックスで「監査ログ」ページにチェックを入れます。マトリックスはそのページに必要な API を自動的に含めます。
3. 「割り当て」をクリックして、いま作成したユーザーにロールを割り当てます。

![ロール管理](/screenshots/roles.png)

## 5. 動作の確認

新しいユーザーでログインすると、左側のメニューには「監査ログ」だけが表示されます。管理者アカウントに戻り、ユーザーの行にある「有効な権限を表示」アイコンをクリックすると、そのユーザーの各権限の出所を確認できます。

## 次のステップ

- [中心概念](/ja/start/concepts/) について学びましょう。
- [ユーザーガイド](/ja/guide/console/) を見ながら、各メニューに慣れましょう。
- アプリケーションを [GrantForge に連携](/ja/integration/overview/) させましょう。
