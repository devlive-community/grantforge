---
title: 身份源（LDAP 与 OIDC）
description: 让用户用公司目录（LDAP/AD）或 OpenID Connect 提供方登录，自动创建账号并同步。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**访问控制 → 身份源** 配置用户可以用来登录的目录或身份提供方。这类账号的密码保存在身份源里，GrantForge 不保存也不能修改它们。

![身份源](/screenshots/identity-sources.png)

## LDAP / Active Directory

点击“添加身份源”，类型选“LDAP 目录”：

| 设置 | 说明 |
| --- | --- |
| 目录地址 | `ldap://` 或 `ldaps://`，多个地址用空格分开实现故障切换 |
| 用户所在 Base DN | 例如 `ou=people,dc=example,dc=com` |
| 查询账号 | 用来查找用户的账号（Bind DN）及其密码；留空则匿名查询 |
| 用户过滤条件 | 默认 `(&(objectClass=person)(uid={0}))`，`{0}` 是用户输入的名字 |
| 属性 | 用户名、显示名称、邮箱、唯一标识的属性；默认适配 OpenLDAP（`uid`、`cn`、`mail`、`entryUUID`），Active Directory 请填 `sAMAccountName` 与 `objectGUID` |
| 为新用户自动创建账号 | 开启后，目录里的用户首次登录即创建账号 |
| 同步间隔 | 留空则只手动同步，最短 15 分钟 |
| 停用已离开目录的用户账号 | 同步时停用目录中已不存在的用户 |

保存后点击 **测试连接** 确认配置正确。

登录时，GrantForge 先用查询账号找到用户，再用用户输入的密码以该用户身份绑定目录来校验密码。

**同步**会为目录中的新用户创建账号、更新已有账号的姓名与邮箱，并按设置停用已离开的用户（同时结束他们的会话）。同步结果显示在身份源卡片上。

## OpenID Connect

类型选“OpenID Connect”，可以接入 Keycloak、Azure AD、Okta、另一套 GrantForge 等：

| 设置 | 说明 |
| --- | --- |
| Issuer 地址 | 提供方的签发者地址，GrantForge 通过它的发现文档获取端点和密钥 |
| 客户端 ID / 密钥 | 在提供方注册的客户端；没有密钥时作为公开客户端，使用 PKCE |
| 回调地址 | 页面上显示的 `<GrantForge>/api/v1/auth/federated/callback/<编码>`，需要登记到提供方的客户端里 |
| 范围与声明 | 默认 `openid profile email`；用户名、显示名称、邮箱分别取 `preferred_username`、`name`、`email` 声明 |

启用后，登录页出现“通过 <名称> 登录”按钮。用户在提供方登录后回到 GrantForge；开启了两步验证的账号还要输入验证码。

## 账号规则

- 身份源用户的唯一标识（LDAP 的 `entryUUID`/`objectGUID`，OIDC 的 `sub`）对应一个 GrantForge 账号，改名不影响对应关系。
- 本地已有同名账号时**不会自动关联**，登录会被拒绝，需要管理员先改名或删除本地账号。这避免了目录里的同名用户接管现有账号。
- 不自动创建账号的身份源只能让已关联的用户登录。
- 停用身份源后，它的用户无法登录；仍有账号使用的身份源不能删除。
- 身份源账号可以照常分配角色、开启两步验证。
