---
title: REST API 参考
description: 全部 REST 接口及其访问要求，由 OpenAPI 契约生成。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

本页由仓库中的 OpenAPI 契约（`core/grantforge-web/src/api/openapi.json`）在构建文档时生成，与服务端保持一致。运行中的服务还在 `/v3/api-docs` 提供同一份契约。

- **公开**：无需登录。
- **登录即可**：任何已登录的账号。
- 其余接口列出所需的权限码，权限码在资源目录中登记为 API 资源。

控制台接口使用会话与 CSRF 令牌，见 [安全设计](/architecture/security/)；业务应用使用的开放 API 见 [开放 API](/integration/open-api/)。

{{generated:api}}
