---
title: Modelo de permissões
description: A semântica exata dos recursos, da derivação de permissões, da herança e das atribuições, das regras de dados e de campos, além do instantâneo de permissões e da versão.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Este artigo descreve as regras exatas da avaliação. Para uma primeira aproximação aos conceitos, consulte os [conceitos essenciais](/pt-br/start/concepts/).

## Árvore de recursos e tipos

Os recursos pertencem a uma aplicação, organizam-se em árvore e têm no máximo 15 níveis. O tipo determina onde podem ser colocados:

| Tipo | Pai permitido |
| --- | --- |
| Módulo | Nível superior, módulo |
| Menu, página | Nível superior, módulo, menu |
| Etiqueta | Página, etiqueta |
| Botão | Página, etiqueta |
| API, entidade de dados | Nível superior, módulo |
| Campo | Entidade de dados |

Entre os recursos é possível declarar dependências: "obrigatória" (ao conceder um recurso, concedem-se também os recursos de que ele depende) ou "opcional" (apenas avisa o administrador, não é concedida automaticamente); as dependências não podem formar ciclos. As páginas e os botões do console, e as API de que precisam, são declarados na lista de permissões do frontend, e o servidor os sincroniza como recursos integrados na inicialização.

## Derivação de permissões

Para uma conta, o avaliador determina primeiro os **papéis efetivos**:

1. os papéis atribuídos diretamente à conta;
2. os papéis atribuídos aos grupos e departamentos de que a conta participa (inclusive as atribuições de departamentos superiores marcadas com "subordinados incluídos"), bem como aos cargos que ela ocupa;
3. considera apenas as atribuições vigentes no momento atual e os papéis que estão habilitados;
4. expande a herança: todos os papéis ancestrais de um papel (um ancestral desabilitado não transmite suas permissões).

Em seguida, combinam-se as permissões desses papéis:

- Permitir um recurso equivale a permitir o próprio recurso, seus ancestrais na árvore (para que fique visível) e os recursos de que ele depende como "obrigatórios" (de forma recursiva).
- Negar um recurso atua sobre ele e sobre todos os seus subordinados, e **prevalece sobre qualquer permissão**.
- Um recurso desabilitado e seus subordinados não têm efeito.
- Um papel de sistema equivale a permitir toda a subárvore de seu módulo (por exemplo `system`, `data`, `platform`).

O resultado é o conjunto de recursos utilizáveis e os códigos de permissão que correspondem aos recursos de API entre eles.

## Proteção contra escalada de privilégios

- Ao conceder uma "permissão", quem concede deve conseguir usar ele mesmo esse recurso (quem detém um papel de sistema não está sujeito a esse limite frente às aplicações de negócio).
- Ao atribuir um papel ou definir uma herança, quem concede deve cobrir todos os recursos que esse papel cobre em cada aplicação.
- O papel de administrador da plataforma só pode ser atribuído, modificado ou removido por seus atuais detentores.
- A "negativa" não tem restrições: qualquer pessoa com permissão de concessão pode restringir permissões.

## Regras de dados

As políticas de dados pertencem aos papéis e definem seu escopo por "entidade × ação × efeito": `ALL` (apenas para o tenant plataforma), `TENANT`, `ORG_AND_CHILDREN`, `ORG`, `CUSTOM_ORGS`, `SELF`, `CONDITION`. Uma linha de dados é utilizável se, e somente se, satisfizer pelo menos uma regra de permissão e não satisfizer nenhuma regra de negativa.

As condições são JSON estruturado, não executável, que é validado antes de ser gravado no banco de dados:

```json
{ "and": [
  { "field": "status", "op": "eq", "value": "ACTIVE" },
  { "not": { "field": "orgUnitId", "op": "in", "value": { "var": "subject.orgUnitIds" } } }
] }
```

As variáveis são apenas `subject.id`, `subject.username`, `subject.orgUnitIds`, `subject.groupCodes`, `subject.positionCodes` e `now`; os operadores de comparação são limitados conforme o tipo do campo; o aninhamento não passa de 5 níveis e os nós não passam de 50. O servidor traduz as regras para `Specification` do JPA, e o SDK as traduz com a mesma semântica dentro da aplicação de negócio.

## Regras de campos

As políticas de campos definem a forma de leitura (visível, mascarado, oculto) e a forma de escrita (editável, somente leitura); quando há vários papéis, adota-se a configuração mais permissiva e, se não houver nenhuma configuração, o campo fica totalmente aberto. As regras de leitura são aplicadas ao serializar para JSON (um mesmo DTO se apresenta de forma distinta diante de pessoas distintas) e as regras de escrita são aplicadas na camada de serviço; modificar um campo de somente leitura retorna `GF-FIELD-001` e indica o campo.

## Instantâneo e versão

O resultado da avaliação é um **instantâneo de permissões**: os recursos utilizáveis, os códigos de permissão, as regras de dados e as regras de campos. O instantâneo é mantido em cache por conta; qualquer mudança em permissões, atribuições, herança, catálogo de recursos, políticas ou relações de pertencimento eleva o número de versão do escopo correspondente (o catálogo ou o tenant), o que invalida o cache. Cada interface que precisa de permissões devolve a versão atual no cabeçalho de resposta `X-Authorization-Version`, e o console decide com ela se deve recarregar o menu; a API aberta expressa essa mesma versão por meio de ETag.
