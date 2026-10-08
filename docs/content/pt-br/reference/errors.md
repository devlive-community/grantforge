---
title: Códigos de erro
description: Todos os códigos de erro estáveis, o status HTTP e seu significado, gerados a partir do código-fonte.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Todas as respostas de erro são problem details da RFC 9457:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Você não tem permissão para executar esta operação",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code` é estável e pode ser usado para ramificar o código do programa; `detail` é localizado conforme o `Accept-Language` da requisição e serve apenas para exibição. Os erros de validação também trazem um array `errors` que indica os campos concretos. A tabela abaixo é gerada ao compilar a documentação a partir da enumeração de códigos de erro existente no código-fonte.

{{generated:errors}}
