---
title: 租户
description: 创建、编辑、停用与启用租户。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**平台管理 → 租户管理**：每个租户是一个相互隔离的组织，拥有自己的用户与权限。平台租户承载平台管理员，不能停用。

![租户管理](/screenshots/tenants.png)

## 创建租户

填写租户编码、名称，以及该租户首个管理员的用户名、显示名称和密码。首个管理员持有该租户的“租户管理员”系统角色，首次登录时必须修改密码。用户名在整个平台内唯一。

## 停用与启用

停用租户后，该租户的所有账号立即退出且无法登录，已签发的应用令牌不再续期。数据不会删除，重新启用即可恢复。

## 平台租户

平台租户是初始化时创建的第一个租户，它的管理员可以管理全部租户、[资源目录与 API 目录](/guide/catalog/)、授权服务器与插件。业务应用和它们的资源是全平台共享的，各租户在自己的角色里授权这些资源。
