---
title: SDK JavaScript
description: Usa `@grantforge/client` per far accedere gli utenti dal browser, consultare i permessi e controllare i pulsanti con le direttive Vue.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` dipende solo dalle API standard del browser (fetch, WebCrypto, sessionStorage) e il supporto per Vue si trova in `@grantforge/client/vue`.

## Accesso (client pubblico + PKCE)

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // scambia il code con i token e verifica il nonce
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // salta all’accesso di GrantForge
const token = await auth.accessToken()                   // si rinnova da sé prima della scadenza; null significa che occorre accedere di nuovo
```

Il token viene conservato in sessionStorage e scompare alla chiusura della scheda. GrantForge non emette refresh token per i client pubblici, quindi quando l’access token scade occorre accedere di nuovo.

## Consultare i permessi

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

La risposta viene messa in cache per 30 secondi e poi riverificata con ETag. Il browser può effettuare chiamate cross-origin: GrantForge consente all’origine degli indirizzi di callback dei client abilitati, senza inviare cookie.

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">Elimina</button>
<button v-resource="['shop.orders.btn.export']">Esporta</button>
<button v-permission.disable="'orders.approve'">Approva</button>   ```

`useGrantForge()` fornisce `authorization`, `error`, `refresh()`, `can()` e `hasResource()`. Nascondere i pulsanti è solo una comodità: l’API dell’applicazione deve verificare i permessi allo stesso modo.


Installazione: `npm install @grantforge/client`. Il codice sorgente dell’SDK si trova in `sdk/grantforge-js` nel repository.
