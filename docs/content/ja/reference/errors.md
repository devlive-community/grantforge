---
title: エラーコード
description: 安定したすべてのエラーコード、HTTP ステータスとその意味。ソースコードから生成されます。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

すべてのエラーレスポンスは RFC 9457 problem details 形式です。

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "この操作を実行する権限がありません",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code` は安定しているため、プログラム内で分岐できます。`detail` はリクエストの `Accept-Language` に応じてローカライズされ、表示専用です。検証エラーには具体的なフィールドを示す `errors` 配列も含まれます。下の表はソースコード内のエラーコード列挙型からドキュメントのビルド時に生成されます。

{{generated:errors}}
