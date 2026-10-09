---
title: 接入概览
description: GrantForge 既是授权服务器也是权限中心，业务应用用标准协议登录用户并查询权限。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge 对业务应用有两种身份：

- **授权服务器**（OAuth 2.1 / OpenID Connect）：用户在 GrantForge 登录，应用拿到访问令牌与 ID 令牌。
- **权限中心**：应用用同一个令牌询问 GrantForge，这个用户在本应用里有哪些角色、资源（菜单、页面、按钮）、API 权限以及数据范围。

## 概念对照

| 概念 | 在哪里配置 | 说明 |
| --- | --- | --- |
| 应用 | 平台管理 → 资源目录 | 一个业务系统，例如 `shop` |
| 资源 | 资源目录中应用的资源树 | 模块、菜单、页面、按钮、API。页面与按钮控制界面，API 资源就是 API 权限编码（例如 `orders.read`） |
| 客户端 | 资源目录 → 应用的“OAuth 客户端” | 应用登录用户、获取令牌的身份。浏览器应用用**公开客户端**，服务端应用用**机密客户端** |
| scope | 客户端设置 | `openid`、`profile`、`email` 用于登录；`permissions` 允许令牌查询权限；`catalog` 允许应用以自身身份声明数据实体 |
| 角色与授权 | 访问控制 → 角色管理 | 把应用的资源授给角色，再把角色分配给用户、组、部门或岗位 |
| 数据策略 | 角色 → 数据权限 | 应用声明的实体（`<应用编码>:<实体>`）与控制台自身的实体一样配置：全部、本租户、本人、部门、指定部门或条件 |

租户管理员（持有系统角色的人）可以把业务应用的任何资源授给本租户的角色；控制台自身的权限仍然只能授出自己拥有的部分。

## 流程

```mermaid
sequenceDiagram
  participant B as 浏览器
  participant A as 业务应用
  participant G as GrantForge
  B->>G: /oauth2/authorize（PKCE）
  G-->>B: 未登录时转到控制台登录页，登录后带回授权请求
  G-->>B: 回到应用的回调地址，带 code
  B->>G: /oauth2/token（code + code_verifier）
  G-->>B: 访问令牌、ID 令牌
  B->>G: /api/v1/open/me/authorization（Bearer）
  G-->>B: 角色、资源、API 权限（ETag）
  B->>A: 调用应用 API（Bearer）
  A->>G: /api/v1/open/me/authorization、/data-access（同一个令牌）
  A-->>B: 只返回用户可用的数据
```

## 接入步骤

1. 在 **平台管理 → 资源目录** 新建应用，建好页面、按钮与 API 资源。
2. 为应用注册客户端：浏览器应用选“公开”，服务端应用选“机密”；回调地址填应用的登录回调；scope 至少选 `openid` 与 `permissions`。机密客户端的密钥只显示一次。
3. 在 **访问控制 → 角色管理** 建角色、授权、分配给用户。
4. 应用接入 SDK：Java 见 [Java SDK](/integration/java/)，浏览器见 [JavaScript SDK](/integration/javascript/)，协议细节见 [OAuth 2.1 与 OpenID Connect](/integration/oauth/) 和 [权限查询开放 API](/integration/open-api/)。

仓库里的 `samples/` 有两个完整示例（商店与笔记），并由端到端测试覆盖，见 [示例应用](/integration/samples/)。
