---
title: API abierta de consulta de permisos
description: La aplicación consulta con el token de acceso los roles, los recursos, los permisos de API y el alcance de datos del usuario, y declara sus propias entidades de datos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

La API abierta está bajo `/api/v1/open/`, solo acepta tokens Bearer emitidos por el servidor de autorización, no usa cookies ni necesita token CSRF; el token deja de valer en cuanto se revoca o se le retira la autorización.

## Interfaces

| Interfaz | Token | Explicación |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | token de usuario, `permissions` | los roles, los recursos y los permisos de API del usuario en esta aplicación; admite `If-None-Match` (304) |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | token de usuario, `permissions` | responde uno a uno si se tiene el permiso (de 1 a 100 permisos) |
| `GET /api/v1/open/me/data-access` | token de usuario, `permissions` | las reglas de los roles sobre las entidades de datos de esta aplicación: alcance, condiciones y departamentos, además del departamento del usuario (con sus subordinados), sus grupos y sus puestos; admite ETag |
| `PUT /api/v1/open/catalog/data-entities` | token del propio cliente, `catalog` | declara todas las entidades de datos de esta aplicación y sustituye la declaración anterior |

## Ejemplo

```bash
curl -H "Authorization: Bearer $TOKEN" https://grantforge.example.com/api/v1/open/me/authorization
```

```json
{
  "application": "shop",
  "accountId": "100203911213113344",
  "tenantId": "100203900000000000",
  "username": "sam",
  "version": 8121,
  "roles": ["shop-buyers"],
  "resources": ["shop.orders", "shop.orders.btn.create"],
  "permissions": ["orders.read", "orders.create"],
  "computedAt": "2026-10-05T03:12:45Z"
}
```

El ID de la cuenta, el del inquilino y los demás se devuelven como cadena, porque se salen del rango de enteros que JavaScript puede representar con exactitud; `version` es el número de versión de los permisos.

## Caché y versión

Las respuestas de `authorization` y `data-access` llevan `ETag`. Guarda la respuesta en caché y la próxima vez envía `If-None-Match`: si los permisos no han cambiado devuelve `304`, con un coste prácticamente nulo. Cualquier cambio en las autorizaciones, las asignaciones, el catálogo de recursos o las políticas de datos cambia el número de versión. El SDK guarda en caché 30 segundos por defecto y después lo vuelve a comprobar con el ETag.

## Declarar entidades de datos

La aplicación declara sus entidades de datos con su cliente confidencial (credenciales de cliente más el scope `catalog`):

```json
{
  "entities": [
    {
      "code": "order",
      "name": "Pedido",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "Estado", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "Importe", "type": "NUMBER" }
      ]
    }
  ]
}
```

Después de declararlas, las entidades aparecen en el editor de permisos sobre los datos de los roles como `<código-de-aplicación>:<código-de-entidad>` (por ejemplo `shop:order`). `owned` significa que la fila tiene una cuenta propietaria (permite usar el ámbito "solo yo") y `unitBased`, que la fila pertenece a un departamento (permite usar los ámbitos de departamento). Las aplicaciones Java no tienen que escribir esta petición a mano: el starter la declara sola a partir de `@GrantForgeEntity`.

## Errores

Todos los errores son problem details de RFC 9457, con `code` y `requestId`:

| Estado / código de error | Significado |
| --- | --- |
| 401 | no hay token o el token ya no vale; hay que iniciar sesión otra vez |
| `GF-SECURITY-003` | hace falta un token de usuario, pero se ha enviado un token del propio cliente |
| `GF-SECURITY-004` | al token le falta el scope necesario |
| `GF-SECURITY-005` | hace falta un token del propio cliente, pero se ha enviado un token de usuario |
| `GF-AUTHZ-052` | las entidades de datos declaradas no son correctas; `errors` indica cada punto |
