---
title: REST API 參考
description: 全部 REST 介面及其存取要求，由 OpenAPI 契約產生。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

本頁由儲存庫中的 OpenAPI 契約（`core/grantforge-web/src/api/openapi.json`）在建置文件時產生，與伺服器端保持一致。執行中的服務還在 `/v3/api-docs` 提供同一份契約。

- **公開**：無需登入。
- **已登入即可**：任何已登入的帳號。
- 其餘介面列出所需的權限碼，權限碼在資源目錄中登錄為 API 資源。

主控台介面使用工作階段與 CSRF 權杖，見 [安全設計](/zh-tw/architecture/security/)；業務應用程式使用的開放 API 見 [開放 API](/zh-tw/integration/open-api/)。

{{generated:api}}
