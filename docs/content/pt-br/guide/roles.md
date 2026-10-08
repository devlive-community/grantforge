---
title: Papéis e permissões
description: Crie papéis, conceda páginas, botões e interfaces, configure a herança, atribua papéis a pessoas, grupos, departamentos ou cargos e visualize o impacto antes de modificar.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controle de acesso → Gestão de papéis**: um papel é um conjunto de permissões que passa a valer quando é atribuído a usuários, grupos de usuários, departamentos ou cargos.

![Gestão de papéis](/screenshots/roles.png)

## Papéis de sistema e papéis personalizados

Cada tenant tem o papel de sistema **administrador do tenant** e o tenant plataforma tem também o papel **administrador da plataforma**. Os papéis de sistema têm automaticamente todos os recursos do seu módulo e não podem ser modificados; quando você precisar de permissões parecidas, mas menores, **copie** o papel de sistema e modifique a cópia.

Os papéis personalizados têm um código (letras minúsculas, dígitos, ponto, hífen ou underscore) e um nome e podem ser desativados: um papel desativado não concede nenhuma permissão e também não transmite permissões pela herança.

## Concessão

Clique em **Conceder** na linha de um papel para abrir a matriz de permissões:

![Matriz de permissões](/screenshots/role-grants.png)

- Troque de aplicação; os recursos são exibidos como uma árvore de catálogo e, em cada recurso, é possível escolher "Permitir" ou "Negar".
- **Ao permitir um botão, a página em que ele está e as interfaces de que ele precisa são deduzidas automaticamente**, sem precisar marcar uma a uma. Os recursos deduzidos aparecem marcados na matriz.
- **A negação tem prioridade** e vale para os recursos subordinados: ao negar uma página, os botões que ela contém ficam indisponíveis mesmo que outro papel os permita.
- Você só pode conceder as permissões que você mesmo tem, para evitar exceder seus próprios direitos. Quem tem um papel de sistema pode conceder qualquer recurso das aplicações de negócio.

Antes de salvar, o GrantForge mostra o **impacto** da modificação: quais recursos passam a ficar disponíveis ou deixam de ficar e quantos usuários têm este papel.

## Herança

Clique em **Herdança** e escolha os papéis que este papel herda: ele recebe tudo o que os papéis herdados permitem e negam, assim como os papéis que estes, por sua vez, herdam. A herança não pode formar ciclos e você só pode herdar permissões que você mesmo tem. Serve a relações de soma, como "gerente = funcionário + aprovação".

## Atribuição

Clique em **Atribuir** para atribuir o papel a:

| Alvo | Descrição |
| --- | --- |
| Usuário | Diretamente a uma conta |
| Grupo de usuários | Todos os membros do grupo recebem o papel |
| Departamento | Os membros do departamento recebem o papel, com a opção "Incluir departamentos subordinados" |
| Cargo | Recebe o papel quem ocupa aquele cargo |

Cada atribuição permite definir a **data de início** e a **data de fim**; ao chegar a data de fim, ela perde a validade automaticamente, o que serve a autorizações temporais. Também é possível deixar que os próprios usuários solicitem papéis por tempo limitado por meio de [Solicitações de acesso e aprovações](/pt-br/guide/access-requests/).

A atribuição e a concessão de permissões obedecem à [separação de funções](/pt-br/guide/sod/): uma atribuição que faria alguém ter, ao mesmo tempo, papéis mutualmente excludentes é rejeitada.

## Permissões de dados e permissões de campo

**Permissões de dados** e **Permissões de campo** na linha do papel determinam, respectivamente, quais linhas quem tem o papel pode ver e como pode ver e modificar quais campos; ver [Permissões de dados](/pt-br/guide/data-permissions/) e [Permissões de campo](/pt-br/guide/field-permissions/).

## Copiar e excluir

**Copiar** copia junto as permissões concedidas, as permissões de dados e as permissões de campo. Excluir um papel exclui ao mesmo tempo suas atribuições, suas permissões e suas políticas, sem possibilidade de desfazer.
