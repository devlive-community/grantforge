---
title: OAuth 2.1 与 OpenID Connect
description: 授权服务器的端点、客户端类型、令牌规则与签名密钥。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge 内置基于 Spring Authorization Server 的授权服务器，遵循 OAuth 2.1 的安全要求：只支持授权码流程（必须使用 PKCE）、刷新令牌和客户端凭据，不支持隐式流程与密码模式。

![授权服务器](/screenshots/oauth.png)

## 发现文档与端点

发现文档：`<GrantForge>/.well-known/openid-configuration`，在 **平台管理 → 授权服务器** 页面可以直接复制。签发者默认是请求到达的地址，在反向代理之后部署时用 `grantforge.oauth.issuer` 固定它。

| 端点 | 说明 |
| --- | --- |
| `/oauth2/authorize` | 授权码流程；所有客户端必须使用 PKCE（S256） |
| `/oauth2/token` | 授权码、刷新令牌、客户端凭据。刷新令牌每次使用都会更换，旧令牌再次出现会吊销整个授权 |
| `/oauth2/revoke` | 撤销令牌 |
| `/oauth2/jwks` | 签名公钥（RS256） |
| `/userinfo` | `sub`、`tid`、`preferred_username`；profile 范围有 `name`，email 范围有 `email` |

访问令牌与 ID 令牌包含 `tid`（租户 ID）与 `preferred_username`；ID 令牌的 `auth_time` 是用户登录控制台的时间。

## 客户端

在 **平台管理 → 资源目录** 选中应用，点击“OAuth 客户端”管理它的客户端。

| 设置 | 规则 |
| --- | --- |
| 类型 | **公开客户端**用于浏览器、移动端等无法保管密钥的应用；**机密客户端**用于服务端应用，有密钥 |
| 回调地址 | 最多 10 个，绝对地址，不允许通配符和 fragment；必须是 https，或本机的 http（localhost、127.0.0.1、[::1]），或原生应用的自定义协议 |
| scope | `openid`、`profile`、`email`、`permissions`（查询权限）、`catalog`（声明数据实体，仅客户端凭据） |
| 授权方式 | 授权码、刷新令牌（需要授权码，仅机密客户端会获得）、客户端凭据（仅机密客户端） |
| 令牌有效期 | 访问令牌 1 分钟–24 小时（默认 15 分钟），刷新令牌 1 小时–90 天（默认 30 天） |

机密客户端的密钥在注册或轮换时只显示一次，GrantForge 只保存它的哈希。轮换时可以设置宽限期（最长 7 天），宽限期内新旧密钥都有效，方便滚动更新。

## 令牌规则

- 令牌以哈希保存：数据库泄露也拿不到可用的令牌。
- 以下任一情况发生后，已签发的令牌不再续期：客户端被停用或删除、账号被停用或锁定、账号需要修改密码、租户被停用。
- 浏览器跨域：GrantForge 允许已启用客户端回调地址所在的源跨域调用令牌端点与开放 API，不携带 Cookie。

## 签名密钥

签名密钥用 RSA 2048 生成，私钥加密保存。默认每 90 天自动轮换（`grantforge.oauth.signing-key-rotation`），旧公钥继续在 JWKS 中发布 2 天（`signing-key-retention`），保证轮换前签发的令牌仍能验证。需要时可以在授权服务器页面立即轮换（这是敏感操作，开启两步验证的账号需要再次验证）。

## 用 GrantForge 登录控制台以外的系统

任何支持 OpenID Connect 的系统（Grafana、GitLab、Jenkins 等）都可以把 GrantForge 当作 IdP：在资源目录为它建应用和机密客户端，把发现文档地址、client_id 和密钥填进对方的 OIDC 配置即可。反过来，GrantForge 也可以用其他 IdP 登录，见 [身份源](/guide/identity-sources/)。
