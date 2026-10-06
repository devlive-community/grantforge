---
title: JavaScript SDK
description: Используйте @grantforge/client для входа пользователей и запроса прав в браузере, а также для управления кнопками с помощью Vue-директив.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` зависит только от стандартных браузерных API (fetch, WebCrypto, sessionStorage); поддержка Vue находится в `@grantforge/client/vue`.

## Вход (публичный клиент + PKCE)

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

Токены хранятся в sessionStorage и живут вместе со вкладкой. GrantForge не выдаёт refresh-токены публичным клиентам, поэтому после истечения access token пользователю приходится входить заново.

## Запрос прав

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

Ответы кэшируются на 30 секунд, затем повторно проверяются по ETag. Браузеры могут вызывать API кросс-доменно: GrantForge разрешает это для источников (origin), на которых размещены redirect URI включённого клиента, — без cookies.

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">删除</button>
<button v-resource="['shop.orders.btn.export']">导出</button>
<button v-permission.disable="'orders.approve'">审批</button>   ```

`useGrantForge()` предоставляет `authorization`, `error`, `refresh()`, `can()` и `hasResource()`. Скрытие кнопок — лишь удобство; API приложения обязано проверять те же права.


Установите пакет командой `npm install @grantforge/client`. Исходники SDK лежат в `sdk/grantforge-js` репозитория.
