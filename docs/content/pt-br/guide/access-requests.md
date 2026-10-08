---
title: Solicitações de acesso e aprovações
description: Usuários solicitam papéis por tempo limitado; quem aprova concede, rejeita ou revoga antes do prazo, e as permissões expiram automaticamente no vencimento.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Administrador: liberar os papéis que podem ser solicitados

Em **Controle de acesso → Aprovação de permissões**, clique em **Papéis solicitáveis** e escolha quais papéis personalizados podem ser solicitados e por quantos dias, no máximo, cada um pode ser pedido (1–365). Papéis do sistema não podem ser solicitados.

## Usuário: solicitar

Todo usuário conectado vê em **Espaço de trabalho → Minhas solicitações** os papéis que pode solicitar. Escolha o papel, informe o motivo e o número de dias e envie a solicitação; ela pode ser retirada antes da aprovação. Não é possível solicitar de novo um papel que você já possui ou para o qual já existe uma solicitação pendente.

![Minhas solicitações](/screenshots/requests.png)

Um usuário recém-criado precisa primeiro alterar a senha inicial para usar esta página.

## Aprovador: conceder, rejeitar e revogar

**Controle de acesso → Aprovação de permissões** lista as solicitações pendentes, as já concedidas ou todas elas.

![Aprovação de permissões](/screenshots/access-approvals.png)

- **Conceder**: é possível reduzir o número de dias e escrever um parecer. Depois de concedida, o usuário recebe o papel imediatamente e ele expira no vencimento.
- **Rejeitar**: é possível escrever o motivo.
- **Revogar**: retira o papel antes do prazo em uma solicitação já concedida.

Conceder uma solicitação equivale à atribuição do papel pelo próprio aprovador, portanto valem as mesmas regras:

- não é possível conceder um papel que vá além das permissões do próprio aprovador;
- as restrições de [Separação de funções](/pt-br/guide/sod/) são respeitadas;
- ninguém pode aprovar a própria solicitação;
- aprovadores com autenticação de dois fatores ativada precisam ter se autenticado nos últimos 10 minutos.

## Expiração automática

O papel concedido deixa de vigorar no instante do prazo final. Em segundo plano, as atribuições expiradas são limpas a cada 5 minutos e a solicitação é marcada como "expirada". Todo o processo (solicitação, retirada, concessão, rejeição, revocação e expiração) fica registrado no log de auditoria.
