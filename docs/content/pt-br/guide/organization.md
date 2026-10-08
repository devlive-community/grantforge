---
title: Departamentos, grupos e cargos
description: Mantenha a árvore de departamentos, crie grupos de usuários conforme a necessidade e gerencie os cargos; todos podem servir de alvo para atribuição de papéis e de escopo de dados.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Estrutura da organização

**Controle de acesso → Estrutura da organização** mantém a hierarquia de departamentos.

![Estrutura da organização](/screenshots/org.png)

- Os departamentos têm no máximo 16 níveis; é possível arrastá-los ou usar "Mover para…" para mudar de posição, mas um departamento não pode ser movido para abaixo de seus próprios subordinados.
- Cada conta tem um departamento principal e pode pertencer a vários departamentos ao mesmo tempo.
- Os departamentos são uma base importante das permissões de dados: "Meu departamento", "Meu departamento e subordinados" e "Departamentos indicados" são calculados a partir desta estrutura.
- Só é possível excluir departamentos que não tenham departamentos subordinados.

## Grupos de usuários

**Controle de acesso → Grupos de usuários**: coloque no mesmo grupo as contas que precisam das mesmas permissões e, em seguida, atribua papéis ao grupo. Os grupos de usuários são independentes da estrutura da organização e servem a conjuntos que cruzam departamentos, como um "grupo de plantão" ou um "grupo de projeto". Os membros podem ser adicionados e removidos em lote, no máximo 500 pessoas por vez.

![Grupos de usuários](/screenshots/groups.png)

## Cargos

**Controle de acesso → Cargos**: mantenha os cargos da organização (por exemplo "Gerente financeiro") e atribua um ou mais cargos a cada pessoa ao editar o usuário. Aos cargos também podem ser atribuídos papéis: quando alguém muda de cargo, basta trocar o cargo da pessoa e suas permissões mudam junto.

![Cargos](/screenshots/positions.png)
