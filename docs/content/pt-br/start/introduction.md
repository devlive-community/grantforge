---
title: Apresentação do produto
description: O que é o GrantForge, que problemas ele resolve e em que se diferencia das soluções habituais.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O GrantForge é uma plataforma de permissões unificada e de código aberto (MIT). Ele centraliza "quem pode fazer o quê e quais dados pode ver": no console você mantém os usuários e a organização, define papéis e concede a esses papéis menus, botões, API, linhas de dados e campos; por sua vez, sua aplicação faz o login dos usuários por OAuth 2.1 / OpenID Connect e verifica as permissões com a API aberta ou com um SDK.

![Visão geral do console do GrantForge](/screenshots/dashboard.png)

## Que problemas ele resolve

Quando um sistema alcança certo porte, as permissões costumam ficar espalhadas: os menus são escritos na configuração do front-end, cada interface faz sua própria verificação com anotações, o escopo dos dados depende de condições escritas à mão no SQL e, na saída ou na mudança de cargo de alguém, ninguém sabe dizer ao certo o que essa pessoa ainda pode fazer. O GrantForge unifica tudo isso em um único modelo:

- **Definir em um lugar, valer em todos**: páginas do console, botões, interfaces REST, entidades de dados e campos são todos "recursos"; os papéis recebem permissões sobre recursos, e uma mesma permissão comanda ao mesmo tempo a exibição no front-end e a interceptação no back-end.
- **Permissões visíveis**: a qualquer momento é possível responder "por que ele vê esta página" e "quem será afetado se eu alterar este papel"; cada mudança de permissão tem prévia e deixa auditoria.
- **Conforme às exigências de governança**: separação de funções, solicitações de acesso com prazo, revisão periódica e autenticação de dois fatores atendem aos requisitos habituais de conformidade normativa e de auditoria de controle interno.
- **Integração por protocolos padrão**: a aplicação não precisa embutir um sistema de usuários próprio; basta fazer login por OIDC e consultar as permissões com o token de acesso.

## Funções em resumo

| Área | Função |
| --- | --- |
| Identidade e organização | Multi-tenant, árvore de departamentos, grupos, cargos; importação e exportação em massa por CSV; login e sincronização com LDAP/AD, login federado por OIDC |
| Segurança das contas | Gerenciamento de sessões, política de senhas e bloqueio, autenticação de dois fatores TOTP e códigos de recuperação, verificação adicional em operações sensíveis |
| Permissões funcionais | Catálogo de recursos (módulos, menus, páginas, abas, botões, API), herança de papéis, matriz de permissões, análise de impacto |
| Permissões de dados | Limitação das linhas visíveis por condição (o próprio usuário, o departamento e seus subordinados, departamentos indicados, condição personalizada); leitura e escrita controladas em separado |
| Permissões de campo | Campos ocultos, mascarados (e-mail, número de telefone, número de documento etc.) ou somente leitura |
| Explicabilidade e auditoria | Explicação de permissões, simulação de permissões, consulta e exportação do registro de auditoria |
| Governança | Restrições de separação de funções, solicitações de acesso e aprovações, revisão periódica de permissões |
| Integração de aplicações | Servidor de autorização OAuth 2.1 / OIDC, API aberta de consulta de permissões, SDK para Java (Spring Boot) e JavaScript |
| Sistemas externos | Tipos de serviço como plug-ins e motor de políticas (semelhante ao Apache Ranger), serviços de dados, políticas de acesso e agentes |
| Entrega | Pacote de lançamento único, imagem Docker, exemplos de Compose, Helm Chart; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

## Diferenças em relação às soluções habituais

> [!NOTE]
> O GrantForge não é uma biblioteca que apenas faz RBAC, nem um IdP que apenas faz login único. Ele reúne identidade, autorização e governança em um mesmo modelo e faz com que cada permissão possa ser explicada.

- **Em comparação com escrever as permissões à mão no código**: as regras de permissão são mantidas no console, e alterar uma permissão não exige publicar a aplicação; antes de conceder você vê o alcance do impacto e, depois, resta a auditoria.
- **Em comparação com um IdP que só autentica (Keycloak, entre outros)**: o GrantForge traz um modelo de permissões que chega até os botões, as linhas de dados e os campos, além de funções de governança como separação de funções e revisão; ao mesmo tempo, ele também pode atuar como IdP ou incorporar um LDAP ou OIDC já existente como fonte de identidade.
- **Em comparação com o Apache Ranger**: o GrantForge se inspirou nos tipos de serviço, nas políticas e na arquitetura de agentes do Ranger para gerenciar permissões de sistemas de dados externos; mas, antes de tudo, é uma plataforma de permissões para aplicações de negócio.

## Próximos passos

- [Configuração em cinco minutos](/pt-br/start/quick-start/): baixar, iniciar, concluir a inicialização e conceder o primeiro papel.
- [Conceitos essenciais](/pt-br/start/concepts/): a relação entre recursos, papéis, permissões, atribuições e avaliação.
- [Visão geral da integração de aplicações](/pt-br/integration/overview/): faça sua aplicação usar o GrantForge.
