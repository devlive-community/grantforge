---
title: Open API for permission queries
description: Applications use access tokens to query a user's roles, resources, API permissions, and data scopes, and to declare their own data entities.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Open API lives under `/api/v1/open/` and accepts only Bearer tokens issued by the authorization server; it uses no cookies and needs no CSRF tokens. A token stops working immediately once it is revoked or its grant is revoked.

## Endpoints

| Endpoint | Token | Description |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | User token, `permissions` | The user's roles, resources, and API permissions in this application; supports `If-None-Match` (304) |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | User token, `permissions` | Answers one by one whether each is held (1–100 at a time) |
| `GET /api/v1/open/me/data-access` | User token, `permissions` | The role's rules for this application's data entities: scopes, conditions, departments, plus the user's departments (including sub-departments), groups, and positions; supports ETag |
| `PUT /api/v1/open/catalog/data-entities` | The client's own token, `catalog` | Declares all of this application's data entities, replacing the previous declaration |

## Example

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

IDs such as accounts and tenants are returned as strings because they exceed the integer range JavaScript can represent exactly; `version` is the version number of the permissions.

## Caching and versioning

Responses from `authorization` and `data-access` carry an `ETag`. Cache the response and send `If-None-Match` next time: when the permissions have not changed, the server returns `304` at almost no cost. Any change to grants, assignments, the resource catalog, or data policies changes the version number. SDKs cache for 30 seconds by default and then revalidate with the ETag.

## Declaring data entities

The application declares its data entities with its own confidential client (client credentials plus the `catalog` scope):

```json
{
  "entities": [
    {
      "code": "order",
      "name": "订单",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "状态", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "金额", "type": "NUMBER" }
      ]
    }
  ]
}
```

Once declared, the entities appear in the role's data-permission editor as `<应用编码>:<实体编码>` (for example `shop:order`). `owned` means each row has an owning account (enabling the "own records only" scope), and `unitBased` means each row belongs to a department (enabling the department scopes). Java applications do not have to write this request by hand; the starter declares entities automatically from `@GrantForgeEntity`.

## Errors

All errors are RFC 9457 problem details carrying `code` and `requestId`:

| Status / error code | Meaning |
| --- | --- |
| 401 | No token or the token is no longer valid; sign in again |
| `GF-SECURITY-003` | A user token is required, but the token is the client's own |
| `GF-SECURITY-004` | The token lacks a required scope |
| `GF-SECURITY-005` | The client's own token is required, but the token is a user token |
| `GF-AUTHZ-052` | The declared data entities are invalid; `errors` pinpoints each problem |
