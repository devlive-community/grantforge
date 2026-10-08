---
title: Visão geral da integração
description: O GrantForge é ao mesmo tempo servidor de autorização e central de permissões; aplicações de negócio fazem login de seus usuários e consultam suas permissões com protocolos padrão.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O GrantForge tem dois papéis diante das aplicações de negócio:

- **Servidor de autorização** (OAuth 2.1 / OpenID Connect): o usuário faz login no GrantForge e a aplicação recebe o token de acesso e o token de ID.
- **Central de permissões**: com o mesmo token, a aplicação pergunta ao GrantForge quais papéis, quais recursos (menus, páginas, botões), quais permissões de API e qual escopo de dados aquele usuário tem nesta aplicação.

## Correspondência de conceitos

| Conceito | Onde se configura | Descrição |
| --- | --- | --- |
| Aplicação | Administração da plataforma → Catálogo de recursos | um sistema de negócio, por exemplo `shop` |
| Recurso | a árvore de recursos da aplicação no catálogo de recursos | módulos, menus, páginas, botões, API. As páginas e os botões controlam a interface, e os recursos de API são os códigos de permissão de API (por exemplo `orders.read`) |
| Cliente | Catálogo de recursos → o "Cliente OAuth" da aplicação | a identidade com que a aplicação faz login dos usuários e obtém tokens. Aplicações de navegador usam um **cliente público** e aplicações de servidor usam um **cliente confidencial** |
| scope | as configurações do cliente | `openid`, `profile` e `email` servem ao login; `permissions` permite que o token consulte permissões; `catalog` permite que a aplicação declare entidades de dados em seu próprio nome |
| Papéis e permissões | Controle de acesso → Gestão de papéis | concede-se os recursos da aplicação a um papel e, depois, o papel é atribuído a usuários, grupos, departamentos ou cargos |
| Políticas de dados | Papel → Permissões de dados | as entidades declaradas pela aplicação (`<código-da-aplicação>:<entidade>`) são configuradas como as entidades do próprio console: todos, todo o tenant atual, somente eu, meu departamento, departamentos indicados ou por condição |

O administrador do tenant (quem tem o papel de sistema) pode conceder qualquer recurso das aplicações de negócio aos papéis do seu tenant; as permissões do próprio console continuam podendo ser concedidas apenas na parte que ele tem.

## Fluxo

```mermaid
sequenceDiagram
  participant B as Navegador
  participant A as Aplicação de negócio
  participant G as GrantForge
  B->>G: /oauth2/authorize (PKCE)
  G-->>B: sem login, vai para a página de login do console; depois de entrar, volta à requisição de autorização
  G-->>B: volta ao endereço de retorno da aplicação com code
  B->>G: /oauth2/token (code + code_verifier)
  G-->>B: token de acesso, token de ID
  B->>G: /api/v1/open/me/authorization (Bearer)
  G-->>B: papéis, recursos, permissões de API (ETag)
  B->>A: chama a API da aplicação (Bearer)
  A->>G: /api/v1/open/me/authorization, /data-access (o mesmo token)
  A-->>B: devolve apenas os dados que o usuário pode usar
```

## Passos da integração

1. Crie uma aplicação nova em **Administração da plataforma → Catálogo de recursos** e monte suas páginas, botões e recursos de API.
2. Registre um cliente para a aplicação: escolha "Público" se a aplicação for de navegador e "Confidencial" se for de servidor; informe como endereço de retorno o de login da aplicação; em scope, escolha pelo menos `openid` e `permissions`. A chave do cliente confidencial é exibida apenas uma vez.
3. Em **Controle de acesso → Gestão de papéis**, crie o papel, conceda as permissões e atribua-o a usuários.
4. Integre o SDK na aplicação: para Java, o [SDK Java](/pt-br/integration/java/); para o navegador, o [SDK JavaScript](/pt-br/integration/javascript/); os detalhes do protocolo estão em [OAuth 2.1 e OpenID Connect](/pt-br/integration/oauth/) e [API aberta de consulta de permissões](/pt-br/integration/open-api/).

No repositório, `samples/` traz dois exemplos completos (uma loja e um bloco de notas), cobertos por testes de ponta a ponta; ver [Aplicações de exemplo](/pt-br/integration/samples/).
