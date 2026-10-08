---
title: Usuários
description: Criar, buscar, editar, desabilitar, bloquear e excluir contas, redefinir a senha e a autenticação de dois fatores e consultar as permissões vigentes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controle de acesso → Gestão de usuários** gerencia as contas da organização: a que departamento pertencem, se podem fazer login e a redefinição de senha.

![Gestão de usuários](/screenshots/users.png)

## Busca

Busque por nome de usuário, nome de exibição ou e-mail, filtre por estado (normal, desabilitada, bloqueada, aguardando troca de senha) e por departamento e escolha se deseja incluir os departamentos subordinados. A lista mostra apenas as contas que as suas permissões de dados permitem ver; campos como o e-mail podem ficar ocultos ou mascarados conforme as suas permissões de campo.

## Criação e edição

"Criar usuário" pede o nome de usuário (3–64 letras, dígitos ou `._@-`, único em toda a plataforma), a senha inicial, o nome de exibição, o e-mail, o departamento principal, os departamentos secundários e os cargos. A conta nova precisa trocar a senha no primeiro login.

## Operações sobre as contas

| Operação | Efeito |
| --- | --- |
| Desabilitar / Habilitar | Depois de desabilitada não consegue fazer login e as sessões abertas são encerradas imediatamente |
| Bloquear / Desbloquear | O bloqueio feito por um administrador não é removido automaticamente; no login aparece o aviso para contatar um administrador; use em caso de suspeita de roubo |
| Redefinir senha | Define uma nova senha inicial; o usuário precisa trocá-la no próximo login e as sessões abertas são encerradas |
| Redefinir autenticação de dois fatores | Para quando o autenticador é perdido: desativa a autenticação de dois fatores da conta e encerra suas sessões |
| Ver papéis | Os papéis que a conta possui e a origem de cada um (atribuição direta, grupo de usuários, departamento, cargo) |
| Ver permissões vigentes | Ver [Explicar, simular e auditar permissões](/pt-br/guide/explain/) |
| Excluir | Exclui a conta e suas relações com departamentos; os registros de auditoria são preservados |

A conta do sistema (o administrador criado na inicialização) e a sua própria conta não podem ser desabilitadas, bloqueadas nem excluídas.

## Autorregistro

Desativado por padrão. Depois de definir `grantforge.security.registration-enabled=true`, a página de login passa a exibir o acesso "Criar conta"; as contas registradas entram no tenant indicado em `grantforge.security.registration-tenant` e são contas comuns, sem nenhum papel.
