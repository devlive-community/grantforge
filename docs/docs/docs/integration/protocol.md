---
title: 协议参考
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 授权服务器

发现文档：`<GrantForge>/.well-known/openid-configuration`（平台管理 → 授权服务器页面可复制）。

| 端点 | 说明 |
| --- | --- |
| `/oauth2/authorize` | 授权码流程；所有客户端必须使用 PKCE（S256）。 |
| `/oauth2/token` | 授权码、刷新令牌、客户端凭据。刷新令牌每次使用都会更换，旧令牌再次出现会吊销整个授权。 |
| `/oauth2/revoke` | 撤销令牌。 |
| `/oauth2/jwks` | 签名公钥（RS256）；密钥轮换后旧钥保留发布 2 天。 |
| `/userinfo` | `sub`、`tid`、`preferred_username`，profile 范围有 `name`，email 范围有 `email`。 |

访问令牌与 ID 令牌包含 `tid`（租户 ID）与 `preferred_username`。令牌在客户端被停用或删除、账号停用、锁定或需要改密、租户停用后不再续期。

## 开放 API

只接受授权服务器签发的 Bearer 令牌；令牌被撤销或其授权被吊销后立即失效。

| 接口 | 令牌 | 说明 |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | 用户令牌，`permissions` | 用户在本应用的角色、资源与 API 权限；支持 `If-None-Match`（304）。 |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | 用户令牌，`permissions` | 逐个回答是否拥有（1–100 个）。 |
| `GET /api/v1/open/me/data-access` | 用户令牌，`permissions` | 角色对本应用数据实体的规则：范围、条件、部门，以及用户的部门（含下级）、组、岗位；支持 ETag。 |
| `PUT /api/v1/open/catalog/data-entities` | 客户端自身令牌，`catalog` | 声明本应用的全部数据实体，替换之前的声明。 |

| 错误码 | 含义 |
| --- | --- |
| 401 | 没有令牌或令牌不再有效：重新登录。 |
| `GF-SECURITY-003` | 需要用户令牌，却是客户端自身的令牌。 |
| `GF-SECURITY-004` | 令牌缺少所需 scope。 |
| `GF-SECURITY-005` | 需要客户端自身的令牌，却是用户令牌。 |
| `GF-AUTHZ-052` | 声明的数据实体不正确，`errors` 指出每一处。 |
