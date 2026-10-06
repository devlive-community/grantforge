---
title: REST API リファレンス
description: すべての REST インターフェースとアクセス要件を、OpenAPI コントラクトから生成します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

このページはリポジトリ内の OpenAPI コントラクト（`core/grantforge-web/src/api/openapi.json`）からドキュメントのビルド時に生成され、サーバーと一致します。実行中のサービスは `/v3/api-docs` でも同じコントラクトを提供します。

- **公開**: ログインは不要です。
- **ログインのみで利用可能**: ログイン済みのアカウントならどれでも利用できます。
- それ以外のインターフェースは必要な権限コードを列挙します。権限コードはリソースカタログに API リソースとして登録されます。

コンソールインターフェースはセッションと CSRF トークンを使います。[セキュリティ設計](/ja/architecture/security/) を参照してください。業務アプリケーションが使うオープン API は [オープン API](/ja/integration/open-api/) を参照してください。

{{generated:api}}
