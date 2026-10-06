---
title: 錯誤碼
description: 所有穩定的錯誤碼、HTTP 狀態與中文含義，由原始碼產生。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

所有錯誤回應都是 RFC 9457 problem details：

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "你沒有執行此操作的權限",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code` 是穩定的，可以在程式中判斷；`detail` 依請求的 `Accept-Language` 本地化，僅供顯示。驗證錯誤還帶有 `errors` 陣列，指出具體欄位。下表由原始碼中的錯誤碼列舉在建置文件時產生。

{{generated:errors}}
