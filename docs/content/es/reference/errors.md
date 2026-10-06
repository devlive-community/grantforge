---
title: Códigos de error
description: Todos los códigos de error estables, el estado HTTP y su significado, generados a partir del código fuente.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Todas las respuestas de error son problem details de RFC 9457:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "No tienes permiso para realizar esta operación",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code` es estable y se puede evaluar en el programa; `detail` se localiza según el `Accept-Language` de la solicitud y solo sirve para mostrarse. Los errores de validación incluyen además un array `errors` que indica los campos concretos. La tabla siguiente se genera al compilar la documentación a partir de la enumeración de códigos de error del código fuente.

{{generated:errors}}
