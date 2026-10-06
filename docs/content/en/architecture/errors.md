---
title: Error codes
description: All stable error codes, HTTP statuses, and their Chinese meanings, generated from the source code.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

All error responses are RFC 9457 problem details:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "你没有执行此操作的权限",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code` is stable and safe to branch on in code; `detail` is localized according to the request's `Accept-Language` and is for display only. Validation errors also carry an `errors` array identifying the specific fields. The table below is generated at docs build time from the error code enum in the source code.

{{generated:errors}}
