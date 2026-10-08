---
title: Multi-tenant e isolamento de dados
description: Como os tenants isolam os dados, quais dados são compartilhados pela plataforma e quais são as restrições das operações entre tenants.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Forma de isolamento

Exceto pelos dados que a plataforma compartilha, todas as tabelas de negócio têm uma coluna `tenant_id`. Quando uma requisição entra, a cadeia de filtros vincula ao thread atual o tenant da conta que iniciou sessão, o filtro de tenants do Hibernate adiciona automaticamente a condição de tenant às consultas e, ao escrever, o `tenant_id` é preenchido automaticamente. O código de negócio não consegue "se esquecer" de adicionar a condição de tenant.

Os poucos cenários que precisam atravessar tenants (por exemplo, buscar uma conta pelo nome de usuário no login, contar as contas de cada tenant ou as tarefas agendadas em segundo plano) devem entrar de forma explícita no "contexto de sistema", de modo que na revisão de código é possível localizá-los com um olhar.

## Compartilhado e independente

| Compartilhado pela plataforma | Independente em cada tenant |
| --- | --- |
| Catálogo de aplicações e recursos, catálogo de API, clientes OAuth, plug-ins | Contas, departamentos, grupos, cargos, papéis, permissões, atribuições, políticas de dados e de campos, separação de funções, solicitações e revisões, fontes de identidade, serviços de dados, auditoria |

O nome de usuário é único em toda a plataforma, por isso, ao iniciar sessão, não é necessário escolher um tenant.

## O tenant plataforma

O tenant plataforma é criado pela inicialização e não pode ser desativado. Seus administradores gerenciam os dados compartilhados pela plataforma e os demais tenants; apenas os papéis do tenant plataforma podem usar o escopo de dados "todos os tenants".

## Identificadores

Todas as chaves primárias são TSID: 64 bits, ordenadas por tempo e com unicidade garantida no cluster pelo número do nó. Elas são maiores que os inteiros que o JavaScript consegue representar com exatidão, por isso, em JSON, são sempre transmitidas como texto. Cada instância do cluster precisa de um `GRANTFORGE_ID_NODE` diferente.
