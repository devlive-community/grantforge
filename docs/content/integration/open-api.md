---
title: 权限查询开放 API
description: 应用用访问令牌查询用户的角色、资源、API 权限与数据范围，以及声明自己的数据实体。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

开放 API 位于 `/api/v1/open/` 下，只接受授权服务器签发的 Bearer 令牌，不使用 Cookie、不需要 CSRF 令牌；令牌被撤销或其授权被吊销后立即失效。

## 接口

| 接口 | 令牌 | 说明 |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | 用户令牌，`permissions` | 用户在本应用的角色、资源与 API 权限；支持 `If-None-Match`（304） |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | 用户令牌，`permissions` | 逐个回答是否拥有（1–100 个） |
| `GET /api/v1/open/me/data-access` | 用户令牌，`permissions` | 角色对本应用数据实体的规则：范围、条件、部门，以及用户的部门（含下级）、组、岗位；支持 ETag |
| `PUT /api/v1/open/catalog/data-entities` | 客户端自身令牌，`catalog` | 声明本应用的全部数据实体，替换之前的声明 |

## 示例

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

账号、租户等 ID 以字符串返回，因为它们超出了 JavaScript 能精确表示的整数范围；`version` 是权限的版本号。

## 缓存与版本

`authorization` 与 `data-access` 的答复带有 `ETag`。缓存答复，下次带上 `If-None-Match`：权限没有变化时返回 `304`，几乎没有开销。授权、分配、资源目录或数据策略的任何变化都会改变版本号。SDK 默认缓存 30 秒再用 ETag 复验。

## 声明数据实体

应用用自己的机密客户端（客户端凭据 + `catalog` scope）声明数据实体：

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

声明后，实体以 `<应用编码>:<实体编码>`（如 `shop:order`）出现在角色的数据权限编辑器中。`owned` 表示行有归属账号（可用“本人”范围），`unitBased` 表示行属于某个部门（可用部门范围）。Java 应用不必手写这个请求，starter 会从 `@GrantForgeEntity` 自动声明。

## 错误

所有错误都是 RFC 9457 problem details，带 `code` 与 `requestId`：

| 状态 / 错误码 | 含义 |
| --- | --- |
| 401 | 没有令牌或令牌不再有效，需要重新登录 |
| `GF-SECURITY-003` | 需要用户令牌，却是客户端自身的令牌 |
| `GF-SECURITY-004` | 令牌缺少所需 scope |
| `GF-SECURITY-005` | 需要客户端自身的令牌，却是用户令牌 |
| `GF-AUTHZ-052` | 声明的数据实体不正确，`errors` 指出每一处 |
