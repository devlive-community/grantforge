---
title: JavaScript SDK
description: 用 @grantforge/client 在瀏覽器裡登入使用者、查詢權限，並用 Vue 指令控制按鈕。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` 僅依賴瀏覽器標準 API（fetch、WebCrypto、sessionStorage），Vue 支援在 `@grantforge/client/vue`。

## 登入（公開客戶端 + PKCE）

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // 換取權杖並驗證 nonce
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // 跳轉 GrantForge 登入
const token = await auth.accessToken()                   // 過期前自動續期；null 表示需要重新登入
```

權杖儲存在 sessionStorage，隨標籤頁結束。GrantForge 不給公開客戶端簽發更新權杖，存取權杖過期後需要重新登入。

## 查詢權限

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

回應快取 30 秒，之後用 ETag 複驗。瀏覽器可以跨網域呼叫：GrantForge 允許已啟用客戶端回呼位址所在的來源，不攜帶 cookie。

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">刪除</button>
<button v-resource="['shop.orders.btn.export']">匯出</button>
<button v-permission.disable="'orders.approve'">審批</button>   ```

`useGrantForge()` 提供 `authorization`、`error`、`refresh()`、`can()`、`hasResource()`。隱藏按鈕僅是便利，應用程式的 API 必須同樣驗證權限。


安裝：`npm install @grantforge/client`。SDK 原始碼在儲存庫的 `sdk/grantforge-js`。
