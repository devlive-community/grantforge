---
title: Коды ошибок
description: Все стабильные коды ошибок, статусы HTTP и их китайские формулировки, сгенерированные из исходного кода.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Все ответы об ошибках оформлены как problem details по RFC 9457:

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

`code` стабилен и по нему можно безопасно ветвиться в коде; `detail` локализуется в зависимости от `Accept-Language` запроса и предназначен только для отображения. Ошибки валидации также содержат массив `errors`, указывающий на конкретные поля. Таблица ниже генерируется при сборке документации из перечисления кодов ошибок в исходном коде.

{{generated:errors}}
