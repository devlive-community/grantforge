---
title: 错误码
description: 所有稳定的错误码、HTTP 状态与中文含义，由源代码生成。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

所有错误响应都是 RFC 9457 problem details：

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

`code` 是稳定的，可以在程序里判断；`detail` 按请求的 `Accept-Language` 本地化，只用于显示。校验错误还带有 `errors` 数组，指出具体字段。下表由源代码中的错误码枚举在构建文档时生成。

{{generated:errors}}
