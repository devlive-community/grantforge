---
title: JavaScript SDK
description: Melde mit @grantforge/client Benutzer im Browser an, frage Berechtigungen ab und steuere Schaltflächen mit Vue-Direktiven.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` hängt nur von Browser-Standard-APIs ab (fetch, WebCrypto, sessionStorage); die Vue-Unterstützung liegt in `@grantforge/client/vue`.

## Anmeldung (öffentlicher Client + PKCE)

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // Token einlösen und Nonce prüfen
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // zur GrantForge-Anmeldung weiterleiten
const token = await auth.accessToken()                   // wird vor dem Ablauf automatisch verlängert; null bedeutet: erneut anmelden
```

Der Token liegt im sessionStorage und endet mit dem Tab. GrantForge stellt öffentlichen Clients kein Refresh-Token aus; nach dem Ablauf des Zugriffstokens ist eine erneute Anmeldung nötig.

## Berechtigungen abfragen

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

Antworten werden 30 Sekunden zwischengespeichert und danach per ETag erneut validiert. Der Browser kann domainübergreifend aufrufen: GrantForge erlaubt die Origins, denen aktivierte Client-Callback-Adressen zugeordnet sind; Cookies werden nicht mitgesendet.

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">Löschen</button>
<button v-resource="['shop.orders.btn.export']">Export</button>
<button v-permission.disable="'orders.approve'">Freigeben</button>   ```

`useGrantForge()` stellt `authorization`, `error`, `refresh()`, `can()` und `hasResource()` bereit. Das Ausblenden von Schaltflächen ist nur ein Komfort; die APIs der Anwendung müssen die Berechtigungen genauso prüfen.


Installation: `npm install @grantforge/client`. Der Quellcode des SDK liegt im Repository unter `sdk/grantforge-js`.
