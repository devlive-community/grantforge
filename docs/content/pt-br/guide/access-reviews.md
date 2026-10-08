---
title: Revisão periódica de permissões
description: Revise periodicamente quem possui quais papéis; o revisor decide item por item manter ou revogar, e as atribuições revogadas são removidas ao concluir a rodada.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controle de acesso → Revisão de permissões**: revise periodicamente quem possui quais papéis; o revisor decide item por item mantê-los ou revogá-los, e as atribuições revogadas são removidas ao concluir a rodada.

![Revisão de permissões](/screenshots/access-reviews.png)

## Plano de revisão

| Configuração | Descrição |
| --- | --- |
| Nome, descrição | por exemplo, "Revisão trimestral dos papéis de finanças" |
| Papéis | os papéis a serem revisados; cada rodada lista todas as suas atribuições atuais |
| Duração de cada rodada | 1–90 dias; ao vencer o prazo, a rodada é concluída automaticamente |
| Intervalo de repetição | deixe em branco para iniciar as rodadas apenas manualmente; caso contrário, a rodada seguinte começa automaticamente no intervalo definido |
| Itens não revisados | os itens que ficarem sem decisão ao fim da rodada: **manter** ou **revogar** |
| Ativo | afeta apenas se as rodadas começam automaticamente conforme o plano |

## Uma rodada de revisão

1. Clique em **Iniciar agora** ou aguarde o plano iniciar a rodada. O GrantForge gera um item de revisão para cada atribuição (exceto os papéis de sistema das contas de sistema).
2. O revisor escolhe **Manter** ou **Revogar** item por item ou em lote; ao revogar é possível escrever uma observação, e a decisão pode ser desfeita antes do fim da rodada.
3. Não é possível revisar os papéis que você mesmo obteve por atribuição direta, grupo, departamento ou cargo.
4. Um administrador pode **Concluir a rodada** (aplicar todas as decisões e tratar as pendentes conforme a configuração do plano) ou **Cancelar a rodada** (sem fazer nenhuma alteração). Rodadas vencidas sem conclusão são concluídas automaticamente.

As atribuições revogadas são excluídas ao concluir a rodada. Cada passo fica registrado no log de auditoria, o que torna o processo adequado como rastro para auditorias de controle interno.
