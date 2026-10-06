---
title: サンプルアプリケーション
description: リポジトリにある 2 つの完全なサンプル、ブラウザから直接接続するショップとサーバー側でログインするノート。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`samples/` には実行可能な 2 つのアプリケーションがあります。GrantForge とともにエンドツーエンドテストで検証されており、連携する際に最も参考になる資料です。

| サンプル | ポート | 示している連携方式 |
| --- | --- | --- |
| `samples/shop` | 19081 | ブラウザは公開クライアント + PKCE でクロスオリジンログインし、リソースごとにボタンを表示します。バックエンドは `@RequirePermission` で API を検証し、`@GrantForgeEntity` で注文エンティティを宣言して、「本人のみ」「自テナント全体」のデータ範囲で検索します |
| `samples/notes` | 19082 | サーバーは Spring Security `oauth2Login`（機密クライアント + PKCE）でログインし、カスタム `AccessTokenResolver` でセッションからトークンを取得します。権限を持つ人にだけ「ノートの作成」を表示します |

## 実行

サンプルは独立した Maven ビルドであり、このリポジトリの starter と JavaScript SDK に依存します。

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

サンプルの設定はすべて環境変数から取得します。

| 変数 | 説明 |
| --- | --- |
| `GRANTFORGE_URL` | GrantForge のアドレス |
| `SHOP_BROWSER_CLIENT_ID` | ショップのブラウザ側の公開クライアント |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | ショップのバックエンドがデータエンティティの宣言に使う機密クライアント（`catalog` scope） |
| `SHOP_SDK_DIRECTORY` | JavaScript SDK のビルド成果物ディレクトリ（`sdk/grantforge-js/dist`） |

GrantForge 側では、2 つのアプリケーションのリソース、クライアント、ロール、データポリシーを用意しておく必要があります。エンドツーエンドテストの準備スクリプト `core/grantforge-web/tests/samples/setup.ts` がこれらの手順をすべて示しているため、そのまま参照できます。

## エンドツーエンドテスト

`script/ci/e2e_fullstack.sh` はフルスタックテストの後に 2 つのサンプルをビルドして起動し、クロスオリジン PKCE ログインと CORS、ボタンの表示と非表示、インターフェースの 403、「本人のみ」と「自テナント全体」のデータ範囲、削除とログアウト、ノートアプリケーションのサーバー側ログインを検証します。`GRANTFORGE_E2E_SKIP_SAMPLES=1` を設定するとスキップできます。
