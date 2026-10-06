---
title: 오류 코드
description: 안정적인 모든 오류 코드와 HTTP 상태, 그리고 그 의미를 소스 코드에서 생성합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

모든 오류 응답은 RFC 9457 problem details 형식입니다.

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "이 작업을 수행할 권한이 없습니다",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code`는 안정적이므로 코드에서 분기할 수 있습니다. `detail`은 요청의 `Accept-Language`에 따라 지역화되며 표시용으로만 사용됩니다. 검증 오류에는 구체적인 필드를 가리키는 `errors` 배열도 포함됩니다. 아래 표는 소스 코드의 오류 코드 열거형에서 문서를 빌드할 때 생성됩니다.

{{generated:errors}}
