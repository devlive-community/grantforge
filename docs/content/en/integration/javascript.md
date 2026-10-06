---
title: JavaScript SDK
description: Use @grantforge/client to sign users in and query permissions in the browser, and to control buttons with Vue directives.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` depends only on standard browser APIs (fetch, WebCrypto, sessionStorage); Vue support lives in `@grantforge/client/vue`.

## Sign-in (public client + PKCE)

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

Tokens are kept in sessionStorage and die with the tab. GrantForge does not issue refresh tokens to public clients, so once the access token expires the user must sign in again.

## Querying permissions

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

Responses are cached for 30 seconds and then revalidated with ETag. Browsers can call it cross-origin: GrantForge allows origins that host an enabled client's redirect URIs, without cookies.

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">删除</button>
<button v-resource="['shop.orders.btn.export']">导出</button>
<button v-permission.disable="'orders.approve'">审批</button>   ```

`useGrantForge()` provides `authorization`, `error`, `refresh()`, `can()`, and `hasResource()`. Hiding buttons is a convenience only; the application's API must enforce the same permissions.


Install with `npm install @grantforge/client`. The SDK source lives in `sdk/grantforge-js` in the repository.
