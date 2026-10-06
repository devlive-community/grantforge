---
title: JavaScript SDK
description: '`@grantforge/client` でブラウザからユーザーをログインさせ、権限を照会し、Vue ディレクティブでボタンを制御します。'
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` はブラウザ標準 API（fetch、WebCrypto、sessionStorage）にのみ依存します。Vue 対応は `@grantforge/client/vue` にあります。

## ログイン（公開クライアント + PKCE）

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // トークンを交換して nonce を検証
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // GrantForge のログインに遷移
const token = await auth.accessToken()                   // 有効期限前に自動で更新。null なら再度ログインが必要
```

トークンは sessionStorage に保存され、タブを閉じると失われます。GrantForge は公開クライアントにリフレッシュトークンを発行しないため、アクセストークンの有効期限が切れたら再度ログインが必要です。

## 権限照会

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

レスポンスは 30 秒間キャッシュしてから ETag で再検証します。ブラウザからクロスオリジンで呼べます。GrantForge は有効なクライアントのコールバックアドレスがあるオリジンを許可し、Cookie は送信しません。

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">削除</button>
<button v-resource="['shop.orders.btn.export']">エクスポート</button>
<button v-permission.disable="'orders.approve'">承認</button>   ```

`useGrantForge()` は `authorization`、`error`、`refresh()`、`can()`、`hasResource()` を提供します。ボタンを非表示にするのは利便性にすぎず、アプリケーションの API も同じ権限を必ず検証する必要があります。


インストール：`npm install @grantforge/client`。SDK のソースコードはリポジトリの `sdk/grantforge-js` にあります。
