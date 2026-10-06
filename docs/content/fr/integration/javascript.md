---
title: SDK JavaScript
description: Avec @grantforge/client, connectez l’utilisateur dans le navigateur, interrogez les autorisations et pilotez les boutons avec des directives Vue.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` ne dépend que des API standard du navigateur (fetch, WebCrypto, sessionStorage) ; la prise en charge de Vue se trouve dans `@grantforge/client/vue`.

## Connexion (client public + PKCE)

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // échange le code contre un jeton et vérifie le nonce
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // redirige vers la connexion GrantForge
const token = await auth.accessToken()                   // renouvellement automatique avant expiration ; null signifie qu’une reconnexion est nécessaire
```

Le jeton est conservé dans sessionStorage, jusqu’à la fin de l’onglet. GrantForge ne délivre pas de jeton de rafraîchissement aux clients publics : après l’expiration du jeton d’accès, une reconnexion est nécessaire.

## Interroger les autorisations

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

Les réponses sont mises en cache 30 secondes, puis revérifiées avec l’ETag. Le navigateur peut appeler l’API d’un autre domaine : GrantForge autorise l’origine où sont déclarées les URL de rappel du client, sans cookie.

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">Supprimer</button>
<button v-resource="['shop.orders.btn.export']">Exporter</button>
<button v-permission.disable="'orders.approve'">Approuver</button>   ```

`useGrantForge()` fournit `authorization`, `error`, `refresh()`, `can()` et `hasResource()`. Masquer un bouton n’est qu’un confort : l’API de l’application doit vérifier les autorisations de la même manière.


Installation : `npm install @grantforge/client`. Le code source du SDK se trouve dans `sdk/grantforge-js` du dépôt.
