---
title: Desempenho e benchmarks
description: Os objetivos de desempenho em escala de um milhão de contas, os dados e o método dos benchmarks, e como executá-los localmente.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Os objetivos de desempenho do GrantForge miram uma organização grande: **um milhão de contas, dez mil papéis e cem mil recursos**. Todas as noites são executados os benchmarks do sistema sobre o PostgreSQL 17, e qualquer métrica que supere seu limite faz a compilação falhar.

## Objetivos

| Métrica | Significado | Limite |
| --- | --- | --- |
| `authz.snapshot.hit` | Instantâneo de permissões já em cache (é usado em cada chamada a uma interface) | p99 1 ms |
| `authz.snapshot.build` | Cálculo pela primeira vez do instantâneo de console de uma conta | p95 50 ms |
| `authz.snapshot.app` | Cálculo do instantâneo em uma aplicação com uma árvore de recursos grande | p95 50 ms |
| `api.users.page` | Uma página da lista de usuários (dentro das 500 primeiras páginas) | p95 200 ms |
| `api.users.search` | Busca de usuários por nome | p95 200 ms |
| `api.groups.page` | Uma página da lista de grupos | p95 200 ms |
| `api.roles.list` | Lista de papéis (sem paginação) | p95 200 ms |
| `api.me.authorization` | As permissões da conta atual | p95 200 ms |
| `api.member.groups` | Uma conta com permissão sobre uma única página acessa uma lista protegida | p95 200 ms |
| `jmh.derivation.*` | Preparar a derivação para uma árvore de recursos grande e derivar vinte permissões (JMH) | média de 50 / 5 ms |

Os limites só podem ser endurecidos; flexibilizar qualquer um deles exige uma decisão documentada.

## Dados

Os benchmarks iniciam um serviço real, concluem a inicialização e depois escrevem por meio das próprias entidades da aplicação:

- 100 departamentos, um grupo a cada mil contas e 100 cargos;
- cada conta pertence a um departamento e a um grupo, uma em cada dez contas ocupa um cargo e uma em cada vinte tem um papel atribuído diretamente;
- um em cada dez papéis herda de um entre os cem primeiros papéis; cada grupo tem três papéis, cada departamento dois e cada cargo um; cada papel concede cinco páginas do console;
- uma aplicação: cem páginas distribuídas por vários módulos, com nove operações por página; a um décimo dos papéis são concedidas vinte dessas páginas e dessas operações.

Cada métrica é medida chamando uma a uma, depois do aquecimento.

## Executar localmente

```bash
bash script/ci/perf_benchmark.sh full            # escala alvo, postgres:17 (precisa de Docker), verifica os limites
bash script/ci/perf_benchmark.sh smoke           # pequena escala sobre H2, apenas confirma que os benchmarks rodam
bash script/ci/perf_benchmark.sh full mysql:8.4  # outros bancos de dados
```

O relatório é escrito em `perf/target/perf-report.json` e, no log, também é impressa uma tabela. Parâmetros adicionais podem ser passados com `PERF_OPTS`:

| Parâmetro | Valor padrão | Significado |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2` ou `<mecanismo>:<versão>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | Tamanho dos dados |
| `perf.samples` / `perf.warmup` | 1000 / 200 | Medições e aquecimentos por métrica |
| `perf.jmh` | `true` | Executar também os benchmarks do JMH |
| `perf.enforce` | `true` | Sair com o código 1 quando um limite é superado |

## Por que é rápido

- O instantâneo de permissões é mantido em cache por conta e invalidado em bloco conforme o número de versão do catálogo e do tenant, de modo que um acerto não passa de uma leitura em memória.
- A derivação ocorre em memória: a árvore de recursos, as relações de herança e as atribuições são carregadas antecipadamente como estruturas compactas, o que evita consultar registro por registro.
- As interfaces de lista são inteiramente paginadas, e a ordenação e a filtragem recaem sobre colunas indexadas; as associações tardias são carregadas em lotes de 64 e as escritas são confirmadas em lotes de 50 (os identificadores são gerados pela aplicação, por isso podem ser inseridos em lotes).
