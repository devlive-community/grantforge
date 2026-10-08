---
title: Explicar, simular e auditar permissões
description: Veja o que uma pessoa pode usar e por quê, simule mudanças de papéis e consulte e exporte os registros de auditoria.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Permissões vigentes

Em **Gestão de usuários**, clique em "Ver permissões vigentes" na linha de um usuário para ver tudo o que ele pode usar agora: papéis, menus e botões, API, escopo de dados e campos restritos.

![Permissões vigentes](/screenshots/user-permissions.png)

Clique em **Explicação** em qualquer um desses itens e o GrantForge responde "por que pode usar": de qual papel, por meio de quais atribuições (direta, grupo de usuários, departamento, cargo) e de quais recursos isso é deduzido; quando não pode, ele explica se é falta de permissão ou se há alguma negação bloqueando.

## Simular mudanças

Abra **Simular mudanças** dentro das permissões vigentes: suponha acrescentar ou remover alguns papéis daquele usuário e veja quais menus, botões e API ele ganha ou perde. A simulação apenas calcula, não salva nada, e serve para confirmar o efeito antes de ajustar as permissões.

## Registros de auditoria

**Controle de acesso → Registros de auditoria** registra quem fez o quê e quando: logins, mudanças em permissões, operações de administração e chamadas rejeitadas.

![Registros de auditoria](/screenshots/audit.png)

- Filtre por evento, resultado, autor, objeto e data; o resultado pode ser exportado em CSV.
- Cada evento traz o IP de origem, o navegador e o ID da requisição, que corresponde ao log do servidor.
- São exibidos apenas os eventos que as suas permissões de dados permitem ver.
- A auditoria é mantida por 365 dias por padrão (`grantforge.audit.retention`) e pode ser configurada para arquivar em um diretório antes da exclusão.

Os eventos registrados incluem: logins bem-sucedidos e com falha, bloqueios, saídas, encerramento de sessão e troca de senha; mudanças em tenants, departamentos, usuários, grupos de usuários e cargos; mudanças no catálogo de recursos e de API; mudanças em papéis, permissões, heranças e atribuições; mudanças nas políticas de dados e de campos; cada passo da separação de funções, das solicitações de acesso e das revisões; mudanças nas fontes de identidade e nos clientes OAuth; e chamadas à API rejeitadas.
