---
title: Permissões de campo
description: Oculte, mascare ou deixe em somente leitura campos controlados conforme o papel, por exemplo o e-mail e a data do último login dos usuários.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

As permissões de campo determinam como quem tem o papel **vê e modifica** cada campo controlado. Clique em **Permissões de campo** na linha de um papel para configurá-las.

![Permissões de campo](/screenshots/role-fields.png)

## Formas de visualização

| Forma | Efeito |
| --- | --- |
| Visível | Mostra o valor original |
| Mascarar | Oculta parcialmente conforme a forma de máscara: e-mail (mantém a primeira letra e o domínio), telefone (138\*\*\*5678), documento de identidade (mantém os 6 primeiros e os 4 últimos caracteres), mantém o primeiro e o último caractere, oculta tudo |
| Oculto | Não retorna este campo e a coluna não é exibida nas listas |

## Formas de modificação

| Forma | Efeito |
| --- | --- |
| Modificável | Pode ser preenchido e modificado |
| Somente leitura | Desabilitado no formulário; ao modificá-lo chamando a API diretamente, retorna erro indicando de qual campo se trata |

## Regras de combinação

- Os campos sem configuração são decididos pelos outros papéis de quem os tem; quando nenhum papel os configura, o campo fica visível e modificável.
- Quando vários papéis configuram o mesmo campo, vale o **mais permissivo** (visível > mascarado > oculto, modificável > somente leitura).
- A busca e a exportação também respeitam as permissões de campo: campos ocultos não podem ser usados na busca e, na exportação, ficam ocultos ou mascarados conforme a regra.

## Campos controlados

Os campos controlados são declarados no código do servidor (hoje, o e-mail do usuário e a data do último login) e, em **Administração da plataforma → Catálogo de recursos**, é listado em quais interfaces cada um aparece. O controle de campos das aplicações de negócio pode ser implementado pela própria aplicação, e as regras também são obtidas pela API aberta.
