---
title: SDK de JavaScript
description: Usa @grantforge/client para iniciar sesión a los usuarios en el navegador, consultar sus permisos y controlar los botones con directivas de Vue.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` depende solo de las API estándar del navegador (fetch, WebCrypto, sessionStorage), y la compatibilidad con Vue está en `@grantforge/client/vue`.

## Inicio de sesión (cliente público + PKCE)

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // canjea el code por tokens y comprueba el nonce
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // salta al inicio de sesión de GrantForge
const token = await auth.accessToken()                   // se renueva solo antes de caducar; null significa que hay que iniciar sesión otra vez
```

El token se guarda en sessionStorage y desaparece al cerrar la pestaña. GrantForge no emite tokens de actualización para los clientes públicos, así que cuando el token de acceso caduca hay que iniciar sesión otra vez.

## Consultar permisos

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

La respuesta se guarda en caché 30 segundos y después se vuelve a comprobar con ETag. El navegador puede llamar de forma cruzada: GrantForge permite el origen de las direcciones de callback de los clientes activados, sin enviar cookies.

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">Eliminar</button>
<button v-resource="['shop.orders.btn.export']">Exportar</button>
<button v-permission.disable="'orders.approve'">Aprobar</button>   ```

`useGrantForge()` proporciona `authorization`, `error`, `refresh()`, `can()` y `hasResource()`. Ocultar botones es solo una comodidad: la API de la aplicación debe comprobar los permisos igualmente.

Instalación: `npm install @grantforge/client`. El código fuente del SDK está en `sdk/grantforge-js` dentro del repositorio.
