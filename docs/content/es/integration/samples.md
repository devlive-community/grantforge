---
title: Aplicaciones de ejemplo
description: "Los dos ejemplos completos del repositorio: una tienda a la que el navegador se conecta directamente y una libreta de notas con inicio de sesión en el servidor."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

En `samples/` hay dos aplicaciones que se pueden ejecutar; junto con GrantForge están cubiertas por pruebas de extremo a extremo y son la mejor referencia a la hora de integrar.

| Ejemplo | Puerto | Forma de integración que muestra |
| --- | --- | --- |
| `samples/shop` | 19081 | el navegador inicia sesión de forma cruzada con un cliente público + PKCE; los botones se muestran según los recursos; el backend comprueba la API con `@RequirePermission`; `@GrantForgeEntity` declara la entidad de pedido y las consultas se hacen con los ámbitos de datos "solo yo" y "todo el inquilino actual" |
| `samples/notes` | 19082 | el servidor inicia sesión con el `oauth2Login` de Spring Security (cliente confidencial + PKCE); un `AccessTokenResolver` personalizado saca el token de la sesión; "Escribir nota" solo se muestra a quien tiene el permiso |

## Ejecutar

Los ejemplos son compilaciones Maven independientes que dependen del starter y del SDK de JavaScript de este repositorio:

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

Toda la configuración de los ejemplos viene de variables de entorno:

| Variable | Explicación |
| --- | --- |
| `GRANTFORGE_URL` | la dirección de GrantForge |
| `SHOP_BROWSER_CLIENT_ID` | el cliente público del navegador de la tienda |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | el cliente confidencial que usa el backend de la tienda para declarar entidades de datos (el scope `catalog`) |
| `SHOP_SDK_DIRECTORY` | el directorio con el resultado de compilar el SDK de JavaScript (`sdk/grantforge-js/dist`) |

En GrantForge hay que crear los recursos, los clientes, los roles y las políticas de datos de las dos aplicaciones; el script de preparación de las pruebas de extremo a extremo `core/grantforge-web/tests/samples/setup.ts` muestra todos esos pasos y sirve de referencia directa.

## Pruebas de extremo a extremo

`script/ci/e2e_fullstack.sh` compila y arranca los dos ejemplos después de las pruebas full-stack, y comprueba: el inicio de sesión PKCE de origen cruzado y CORS, que los botones se muestren o se oculten, el 403 de las interfaces, los ámbitos de datos "solo yo" y "todo el inquilino actual", el borrado y el cierre de sesión, y el inicio de sesión en el servidor de la libreta de notas. Si defines `GRANTFORGE_E2E_SKIP_SAMPLES=1`, se saltan.
