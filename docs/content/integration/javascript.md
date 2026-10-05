---
title: JavaScript SDK
description: 用 @grantforge/client 在浏览器里登录用户、查询权限，并用 Vue 指令控制按钮。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` 只依赖浏览器标准 API（fetch、WebCrypto、sessionStorage），Vue 支持在 `@grantforge/client/vue`。

## 登录（公开客户端 + PKCE）

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // 换取令牌并校验 nonce
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // 跳转 GrantForge 登录
const token = await auth.accessToken()                   // 过期前自动续期；null 表示需要重新登录
```

令牌保存在 sessionStorage，随标签页结束。GrantForge 不给公开客户端签发刷新令牌，访问令牌过期后需要重新登录。

## 查询权限

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

答复缓存 30 秒，之后用 ETag 复验。浏览器可以跨域调用：GrantForge 允许已启用客户端回调地址所在的源，不携带 cookie。

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">删除</button>
<button v-resource="['shop.orders.btn.export']">导出</button>
<button v-permission.disable="'orders.approve'">审批</button>   ```

`useGrantForge()` 提供 `authorization`、`error`、`refresh()`、`can()`、`hasResource()`。隐藏按钮只是便利，应用的 API 必须同样校验权限。


安装：`npm install @grantforge/client`。SDK 源码在仓库的 `sdk/grantforge-js`。
