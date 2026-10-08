---
title: Permissões de dados
description: "Determina quais linhas de cada tipo de dados quem tem o papel pode ler, modificar, excluir e exportar: as próprias, as do departamento, as de departamentos indicados ou conforme uma condição."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

As permissões de dados determinam **quais linhas** de cada tipo de dados quem tem o papel pode ler, modificar, excluir e exportar. Clique em **Permissões de dados** na linha de um papel para configurá-las.

![Permissões de dados](/screenshots/role-data.png)

## Regras

Cada regra tem quatro partes:

| Parte | Opções |
| --- | --- |
| Entidade de dados | Usuários, departamentos, grupos de usuários, cargos, eventos de auditoria e as entidades declaradas pelas aplicações de negócio (como `shop:order`) |
| Ação | Ver, modificar, excluir, exportar |
| Escopo | Todos os tenants (apenas para papéis do tenant plataforma), todo o tenant atual, meu departamento e subordinados, meu departamento, departamentos indicados, somente eu, por condição |
| Efeito | Permitir ou negar |

Combinação das regras:

- **Sem nenhuma regra de permissão, não se vê dado algum.**
- **A negação tem prioridade sobre a permissão**: qualquer linha alcançada por uma regra de negação fica indisponível.
- As regras dos vários papéis de uma pessoa valem juntas: as permissões fazem união e as negações também fazem união.
- Os papéis de sistema trazem embutido o escopo correspondente (o administrador do tenant tem todo o tenant atual), exceto nas entidades das aplicações de negócio.

## Por condição

Quando o escopo é "Por condição", combine as condições no editor de condições:

- Compare campos da entidade, por exemplo "Estado igual a Normal" ou "Último login anterior a agora". Texto aceita contém e começa com; números e datas aceitam comparações de maior e menor; também há pertence, não pertence, está vazio e não está vazio.
- O valor pode ser fixo ou um **atributo do usuário atual**: o próprio ID, o nome de usuário, o departamento, os grupos de usuários, os cargos que ocupa e o momento atual.
- As condições podem ser agrupadas com "Todas atendidas" / "Qualquer uma atendida", podem ser negadas e o aninhamento chega a no máximo 4 níveis.

## Pré-visualização

No editor, escolha um usuário e clique em **Pré-visualizar** para ver quantas linhas, e exatamente quais, as regras deste papel permitem que ele veja.

## Onde vale

As listas e os detalhes de usuários, departamentos, grupos de usuários e cargos do console, a importação e exportação e os registros de auditoria respeitam as permissões de dados; as linhas que você não pode ver não aparecem na lista e, ao acessar diretamente pelo ID, a resposta é "Não existe". As aplicações de negócio obtêm as mesmas regras pelo [SDK Java](/pt-br/integration/java/) ou pela [API aberta](/pt-br/integration/open-api/).
