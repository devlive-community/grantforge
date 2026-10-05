---
title: 资源目录与 API 目录
description: 维护应用与资源树、资源依赖、OAuth 客户端，查看自动登记的 API，并用目录体检找出失效配置。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 资源目录

**平台管理 → 资源目录** 维护各应用的资源树。控制台自身（`grantforge-console`）是内置应用，它的页面、按钮与 API 在启动时自动登记，不能删除。

![资源目录](/screenshots/resources.png)

- **应用**：新建、编辑、删除业务应用；有客户端或资源的应用不能删除。
- **资源**：模块、菜单、页面、标签、按钮、API、数据实体与字段。类型决定可以放在哪里：按钮只能在页面或标签下，字段只能在数据实体下。资源可以拖动调整位置，最多 15 层。
- **状态**：可以隐藏或停用资源；无权限时可以选择“隐藏”或“禁用”按钮。
- **依赖**：按钮“需要”它调用的 API，页面“需要”它加载数据的 API；授权时依赖会被一起推导出来，详情页用图展示依赖关系。
- **OAuth 客户端**：为业务应用注册客户端，见 [OAuth 2.1 与 OpenID Connect](/integration/oauth/)。
- **字段**：选中字段时显示它出现在哪些接口中（返回或接收）。

## API 目录

**平台管理 → API 目录** 列出服务端启动时自动登记的全部接口及其访问要求：公开、登录即可，或需要某个权限码。需授权的接口按权限码归入资源目录，角色授权时引用这些权限。接口新增、下线或权限码变化时会列为“待确认的变更”，确认后更新目录。

![API 目录](/screenshots/apis.png)

## 目录体检

**平台管理 → 目录体检** 找出悄悄失效的配置：不生效的授权、无法工作的按钮（缺少需要的 API）、没人能调用的 API 与断开的依赖。体检只读取数据，不做任何修改。

![目录体检](/screenshots/health.png)
