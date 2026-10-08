---
title: Catálogo de recursos e de API
description: Mantenha a árvore de recursos e as dependências de cada aplicação e seus clientes OAuth, consulte as API registradas automaticamente e encontre as configurações que deixaram de funcionar com a verificação do catálogo.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Catálogo de recursos

**Gestão da plataforma → Catálogo de recursos** mantém a árvore de recursos de cada aplicação. O próprio console (`grantforge-console`) é a aplicação integrada: suas páginas, botões e API são registrados automaticamente na inicialização e não podem ser excluídos.

![Catálogo de recursos](/screenshots/resources.png)

- **Aplicações**: crie, edite e exclua aplicações de negócio; uma aplicação que tenha clientes ou recursos não pode ser excluída.
- **Recursos**: módulos, menus, páginas, abas, botões, API, entidades de dados e campos. O tipo determina onde cada item pode ficar: um botão apenas sob uma página ou aba, e um campo apenas sob uma entidade de dados. Os recursos podem ser reposicionados arrastando-os, com no máximo 15 níveis.
- **Estado**: um recurso pode ser ocultado ou desativado; quando falta uma permissão, é possível escolher entre ocultar ou desativar o botão.
- **Dependências**: um botão "precisa" da API que ele chama, e uma página "precisa" da API de que carrega os dados; ao conceder permissões, as dependências também são deduzidas, e a página de detalhes as exibe em um gráfico.
- **Clientes OAuth**: registre clientes para aplicações de negócio; ver [OAuth 2.1 e OpenID Connect](/pt-br/integration/oauth/).
- **Campos**: ao selecionar um campo, são exibidas as interfaces em que ele aparece (que o retornam ou o recebem).

## Catálogo de API

**Gestão da plataforma → Catálogo de API** lista todas as interfaces registradas automaticamente pelo servidor na inicialização e o que cada uma exige para acesso: ser pública, bastar o login ou exigir um código de permissão. As interfaces que exigem autorização são agrupadas no catálogo de recursos pelo seu código de permissão, e os papéis referenciam essas permissões ao conceder acesso. Quando uma interface é criada, desativada ou tem seu código de permissão alterado, ela aparece como "alteração pendente de confirmação" e o catálogo é atualizado após a confirmação.

![Catálogo de API](/screenshots/apis.png)

## Verificação do catálogo

**Gestão da plataforma → Verificação do catálogo** encontra as configurações que deixaram de funcionar silenciosamente: autorizações sem efeito, botões que não funcionam (falta a API de que precisam), API que ninguém pode chamar e dependências quebradas. A verificação apenas lê dados e não faz nenhuma modificação.

![Verificação do catálogo](/screenshots/health.png)
