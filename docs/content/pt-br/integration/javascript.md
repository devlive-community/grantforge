---
title: SDK JavaScript
description: Use o @grantforge/client para iniciar sessão dos usuários no navegador, consultar permissões e controlar botões com diretivas do Vue.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`@grantforge/client` depende apenas das API padrão do navegador (fetch, WebCrypto, sessionStorage), e o suporte ao Vue está em `@grantforge/client/vue`.

## Início de sessão (cliente público + PKCE)

```ts
import { GrantForgeAuth, GrantForgeClient } from '@grantforge/client'

const auth = new GrantForgeAuth({ issuer: 'https://grantforge.example.com', clientId: 'gf_xxx', redirectUri: `${location.origin}/` })

if (new URLSearchParams(location.search).has('code')) {
  const { returnTo } = await auth.handleRedirect()      // troca o code por tokens e valida o nonce
  history.replaceState(null, '', returnTo ?? '/')
}
if (!auth.signedIn) await auth.login(location.pathname)  // vai para o início de sessão do GrantForge
const token = await auth.accessToken()                   // renova sozinho antes de expirar; null significa que é preciso iniciar sessão de novo
```

O token é guardado em sessionStorage e desaparece ao fechar a aba. O GrantForge não emite tokens de atualização para clientes públicos, portanto, quando o token de acesso expira, é preciso iniciar sessão novamente.

## Consultar permissões

```ts
const grantForge = new GrantForgeClient({ baseUrl: 'https://grantforge.example.com', accessToken: () => auth.accessToken() })
await grantForge.can('orders.delete')
await grantForge.hasResource('shop.orders.btn.export')
```

A resposta fica em cache por 30 segundos e depois é revalidada com ETag. O navegador pode chamar de forma cruzada: o GrantForge permite a origem dos endereços de callback dos clientes habilitados, sem enviar cookies.

## Vue

```ts
import { createGrantForge, useGrantForge } from '@grantforge/client/vue'
app.use(createGrantForge(grantForge))
```

```vue
<button v-permission="'orders.delete'">Excluir</button>
<button v-resource="['shop.orders.btn.export']">Exportar</button>
<button v-permission.disable="'orders.approve'">Aprovar</button>   ```

`useGrantForge()` fornece `authorization`, `error`, `refresh()`, `can()` e `hasResource()`. Ocultar botões é apenas uma conveniência: a API da aplicação precisa verificar as permissões da mesma forma.


Instalação: `npm install @grantforge/client`. O código-fonte do SDK está em `sdk/grantforge-js` no repositório.
