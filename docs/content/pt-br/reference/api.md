---
title: Referência da API REST
description: Todas as interfaces REST e seus requisitos de acesso, gerada a partir do contrato OpenAPI.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Esta página é gerada ao compilar a documentação a partir do contrato OpenAPI do repositório (`core/grantforge-web/src/api/openapi.json`) e se mantém coerente com o servidor. O serviço em execução também expõe esse mesmo contrato em `/v3/api-docs`.

- **Público**: não é necessário fazer login.
- **Com o login basta**: qualquer conta com sessão iniciada.
- As demais interfaces listam os códigos de permissão necessários; os códigos de permissão são registrados como recursos de API no catálogo de recursos.

As interfaces do console usam a sessão e o token CSRF; consulte [projeto de segurança](/pt-br/architecture/security/); a API aberta usada pelas aplicações de negócio está descrita em [API aberta](/pt-br/integration/open-api/).

{{generated:api}}
