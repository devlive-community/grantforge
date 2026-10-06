---
title: Sample applications
description: Two complete examples in the repository — a shop that connects directly from the browser, and a notes app that signs in server-side.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Under `samples/` there are two runnable applications; together with GrantForge they are covered by end-to-end tests and are the best reference when integrating.

| Sample | Port | What it demonstrates |
| --- | --- | --- |
| `samples/shop` | 19081 | The browser signs in cross-origin with a public client + PKCE; buttons shown per resource; the backend enforces APIs with `@RequirePermission`; `@GrantForgeEntity` declares the order entity, queried with the "own records only" and "this tenant" data scopes |
| `samples/notes` | 19082 | The server signs in with Spring Security `oauth2Login` (confidential client + PKCE); a custom `AccessTokenResolver` takes the token from the session; "Write note" is shown only to those with permission |

## Running

The samples are standalone Maven builds that depend on this repository's starter and JavaScript SDK:

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

All sample configuration comes from environment variables:

| Variable | Description |
| --- | --- |
| `GRANTFORGE_URL` | The GrantForge address |
| `SHOP_BROWSER_CLIENT_ID` | The shop's browser-side public client |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | The confidential client the shop backend uses to declare data entities (`catalog` scope) |
| `SHOP_SDK_DIRECTORY` | Build output directory of the JavaScript SDK (`sdk/grantforge-js/dist`) |

In GrantForge you need to set up resources, clients, roles, and data policies for the two applications; the end-to-end test setup script `core/grantforge-web/tests/samples/setup.ts` walks through every step and can be used as a direct reference.

## End-to-end tests

`script/ci/e2e_fullstack.sh` builds and starts both samples after the full-stack tests and verifies cross-origin PKCE sign-in and CORS, button visibility, API 403s, the "own records only" and "this tenant" data scopes, deletion and sign-out, and the notes app's server-side sign-in. Set `GRANTFORGE_E2E_SKIP_SAMPLES=1` to skip it.
