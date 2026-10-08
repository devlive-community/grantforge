---
title: Prestazioni e benchmark
description: Obiettivi di prestazione su scala di un milione di account, dati e metodo dei benchmark e come eseguirli in locale.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Gli obiettivi di prestazione di GrantForge puntano a una grande organizzazione: **un milione di account, diecimila ruoli e centomila risorse**. Ogni notte vengono eseguiti i benchmark di sistema su PostgreSQL 17 e qualsiasi metrica che superi la soglia corrispondente fa fallire la build.

## Obiettivi

| Metrica | Significato | Soglia |
| --- | --- | --- |
| `authz.snapshot.hit` | Snapshot dei permessi già in cache (si usa a ogni chiamata all’interfaccia) | p99 1 ms |
| `authz.snapshot.build` | Primo calcolo dello snapshot della console di un account | p95 50 ms |
| `authz.snapshot.app` | Calcolo dello snapshot in un’applicazione con un grande albero delle risorse | p95 50 ms |
| `api.users.page` | Una pagina dell’elenco utenti (entro le prime 500 pagine) | p95 200 ms |
| `api.users.search` | Ricerca di utenti per nome | p95 200 ms |
| `api.groups.page` | Una pagina dell’elenco dei gruppi di utenti | p95 200 ms |
| `api.roles.list` | Elenco dei ruoli (non paginato) | p95 200 ms |
| `api.me.authorization` | I permessi dell’account corrente | p95 200 ms |
| `api.member.groups` | Un account con il permesso su una sola pagina accede a un elenco protetto | p95 200 ms |
| `jmh.derivation.*` | Preparazione della derivazione per un grande albero delle risorse e derivazione di venti autorizzazioni (JMH) | media 50 / 5 ms |

Le soglie si possono solo stringere; allentare una qualsiasi di esse richiede una decisione documentata.

## Dati

I benchmark avviano un servizio reale, completano l’inizializzazione e poi scrivono passando per le entità dell’applicazione stessa:

- 100 dipartimenti, un gruppo di utenti ogni mille account, 100 posizioni;
- ogni account appartiene a un dipartimento e a un gruppo di utenti, un account su dieci ricopre una posizione e uno su venti ha un ruolo assegnato direttamente;
- un ruolo su dieci eredita da uno dei primi cento ruoli; ogni gruppo di utenti ha tre ruoli, ogni dipartimento due, ogni posizione uno; ogni ruolo autorizza cinque pagine della console;
- un’applicazione: cento pagine suddivise in vari moduli, con nove operazioni per pagina; a un decimo dei ruoli vengono autorizzate venti tra quelle pagine e quelle operazioni.

Ogni metrica viene misurata chiamandola una per una dopo la fase di riscaldamento.

## Eseguirli in locale

```bash
bash script/ci/perf_benchmark.sh full            # scala obiettivo, postgres:17 (richiede Docker), verifica le soglie
bash script/ci/perf_benchmark.sh smoke           # piccola scala su H2, conferma solo che i benchmark partono
bash script/ci/perf_benchmark.sh full mysql:8.4  # altri database
```

Il report viene scritto in `perf/target/perf-report.json` e nel log viene stampata anche una tabella. Si possono passare parametri aggiuntivi con `PERF_OPTS`:

| Parametro | Valore predefinito | Significato |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2` oppure `<motore>:<versione>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | Dimensione dei dati |
| `perf.samples` / `perf.warmup` | 1000 / 200 | Numero di misurazioni e di riscaldamenti per ogni metrica |
| `perf.jmh` | `true` | Esegue anche i benchmark JMH |
| `perf.enforce` | `true` | Esce con codice 1 quando viene superata una soglia |

## Perché è veloce

- Lo snapshot dei permessi viene messo in cache per account e invalidato in blocco in base al numero di versione del catalogo e del tenant; in caso di hit è una semplice lettura in memoria.
- La derivazione avviene in memoria: albero delle risorse, relazioni di ereditarietà e assegnazioni vengono precaricati come strutture compatte, evitando interrogazioni riga per riga.
- Le interfacce di elenco sono tutte paginate, ordinamento e filtri ricadono su colonne indicizzate; le associazazioni lazy vengono caricate a blocchi di 64 e le scritture confermate a blocchi di 50 (gli ID sono generati dall’applicazione, quindi si può fare l’insert in batch).
