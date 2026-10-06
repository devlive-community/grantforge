---
title: JavaScript SDK
description: '@grantforge/client로 브라우저에서 사용자를 로그인시키고 권한을 조회하며, Vue 디렉티브로 버튼을 제어합니다.'
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client`는 브라우저 표준 API(fetch, WebCrypto, sessionStorage)에만 의존합니다. Vue 지원은 `@grantforge/client/vue`에 있습니다.

## 로그인(공개 클라이언트 + PKCE)

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // 토큰을 교환하고 nonce를 검증
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // GrantForge 로그인으로 이동
const token = await auth.accessToken()                   // 만료 전에 자동 갱신; null이면 다시 로그인해야 함
```

토큰은 sessionStorage에 저장되어 탭이 닫히면 사라집니다. GrantForge는 공개 클라이언트에 리프레시 토큰을 발급하지 않으므로, 액세스 토큰이 만료된 뒤에는 다시 로그인해야 합니다.

## 권한 조회

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

응답은 30초간 캐시한 뒤 ETag로 재검증합니다. 브라우저에서 교차 출처 호출이 가능합니다. GrantForge는 활성화된 클라이언트의 콜백 주소가 있는 출처를 허용하며, 쿠키는 보내지 않습니다.

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">삭제</button>
<button v-resource="['shop.orders.btn.export']">내보내기</button>
<button v-permission.disable="'orders.approve'">승인</button>   ```

`useGrantForge()`는 `authorization`, `error`, `refresh()`, `can()`, `hasResource()`를 제공합니다. 버튼을 숨기는 것은 편의일 뿐이며, 애플리케이션의 API도 같은 권한을 반드시 검증해야 합니다.


설치: `npm install @grantforge/client`. SDK 소스 코드는 저장소의 `sdk/grantforge-js`에 있습니다.
