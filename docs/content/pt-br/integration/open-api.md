---
title: API aberta de consulta de permissões
description: A aplicação consulta, com o token de acesso, os papéis, os recursos, as permissões de API e o escopo de dados do usuário, além de declarar suas próprias entidades de dados.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

A API aberta está sob `/api/v1/open/`, aceita apenas tokens Bearer emitidos pelo servidor de autorização, não usa cookies nem precisa de token CSRF; o token deixa de valer assim que é revogado ou perde sua autorização.

## Interfaces

| Interface | Token | Explicação |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | token de usuário, `permissions` | os papéis, os recursos e as permissões de API do usuário nesta aplicação; aceita `If-None-Match` (304) |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | token de usuário, `permissions` | responde um a um se a permissão existe (de 1 a 100 permissões) |
| `GET /api/v1/open/me/data-access` | token de usuário, `permissions` | as regras dos papéis sobre as entidades de dados desta aplicação: escopo, condições e departamentos, além do departamento do usuário (com seus subordinados), seus grupos e seus cargos; aceita ETag |
| `PUT /api/v1/open/catalog/data-entities` | token do próprio cliente, `catalog` | declara todas as entidades de dados desta aplicação e substitui a declaração anterior |

## Exemplo

```bash
curl -H "Authorization: Bearer $TOKEN" https://grantforge.example.com/api/v1/open/me/authorization
```

```json
{
  "application": "shop",
  "accountId": "100203911213113344",
  "tenantId": "100203900000000000",
  "username": "sam",
  "version": 8121,
  "roles": ["shop-buyers"],
  "resources": ["shop.orders", "shop.orders.btn.create"],
  "permissions": ["orders.read", "orders.create"],
  "computedAt": "2026-10-05T03:12:45Z"
}
```

O ID da conta, o do tenant e os demais são devolvidos como texto, porque ficam fora da faixa de inteiros que o JavaScript consegue representar com exatidão; `version` é o número de versão das permissões.

## Cache e versão

As respostas de `authorization` e `data-access` levam `ETag`. Guarde a resposta em cache e, na próxima vez, envie `If-None-Match`: se as permissões não mudaram, é devolvido `304`, com custo praticamente nulo. Qualquer mudança em permissões, atribuições, catálogo de recursos ou políticas de dados altera o número de versão. O SDK guarda em cache por 30 segundos por padrão e depois revalida com o ETag.

## Declarar entidades de dados

A aplicação declara suas entidades de dados com seu cliente confidencial (credenciais de cliente mais o scope `catalog`):

```json
{
  "entities": [
    {
      "code": "order",
      "name": "Pedido",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "Estado", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "Valor", "type": "NUMBER" }
      ]
    }
  ]
}
```

Depois da declaração, as entidades aparecem no editor de permissões de dados dos papéis como `<código-da-aplicação>:<código-da-entidade>` (por exemplo `shop:order`). `owned` significa que a linha de dados tem uma conta proprietária (permite usar o escopo "somente eu") e `unitBased` que a linha pertence a um departamento (permite usar os escopos de departamento). As aplicações Java não precisam escrever essa requisição à mão: o starter a declara automaticamente a partir de `@GrantForgeEntity`.

## Erros

Todos os erros são problem details da RFC 9457, com `code` e `requestId`:

| Estado / código de erro | Significado |
| --- | --- |
| 401 | não há token ou o token já não vale; é preciso iniciar sessão novamente |
| `GF-SECURITY-003` | é necessário um token de usuário, mas foi enviado um token do próprio cliente |
| `GF-SECURITY-004` | falta ao token o scope necessário |
| `GF-SECURITY-005` | é necessário um token do próprio cliente, mas foi enviado um token de usuário |
| `GF-AUTHZ-052` | as entidades de dados declaradas não são corretas; `errors` indica cada ponto |
