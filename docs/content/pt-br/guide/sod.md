---
title: Separação de funções
description: Configure papéis que uma mesma pessoa não pode possuir ao mesmo tempo, com recusa obrigatória ou apenas relato, e consulte os conflitos atuais.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controle de acesso → Separação de funções** configura os papéis que uma mesma pessoa não pode possuir ao mesmo tempo (como pagamento e aprovação) e permite consultar as contas que infringem as restrições no momento.

![Separação de funções](/screenshots/sod.png)

## Restrições

| Configuração | Descrição |
| --- | --- |
| Papéis mutuamente exclusivos | de 2 a 50 papéis |
| Máximo por conta | 1 por padrão; pode ser definido como "no máximo dois de três" |
| Modo | **Obrigatório**: recusa atribuições e heranças de papéis que criariam conflito; **Apenas relato**: permite a alteração e a exibe apenas na lista de conflitos |
| Ativa | uma restrição desativada não recusa nem relata |

"Possuir" um papel inclui todos os caminhos: atribuição direta, obtenção por meio de grupo, departamento ou cargo, e herança de papéis. Atribuições fora do período de validade e papéis desativados não contam.

## Quando a obrigatoriedade se aplica

No modo obrigatório, as seguintes alterações verificam, antes de salvar, cada conta que elas afetam:

- atribuir um papel, ou modificar o período de validade de uma atribuição ou se ela inclui departamentos subordinados;
- modificar as relações de herança de um papel;
- aprovar solicitações de acesso (ver [Solicitações de acesso e aprovações](/pt-br/guide/access-requests/)).

Apenas os conflitos **criados por esta alteração** são recusados, com indicação de quem é, quais papéis e qual restrição foi infringida; conflitos que já existiam antes de a restrição entrar em vigor não bloqueiam outras alterações sem relação com eles — eles permanecem na lista de conflitos e precisam de tratamento manual.

> [!WARNING]
> Esta verificação não é feita ao adicionar uma pessoa a um grupo, departamento ou cargo. Os conflitos surgidos por esses caminhos aparecem na lista de conflitos; consulte-a com regularidade.

## Lista de conflitos

À direita são listadas todas as contas que infringem alguma restrição ativada (em qualquer modo): conta, restrição, papéis possuídos e limite permitido.
