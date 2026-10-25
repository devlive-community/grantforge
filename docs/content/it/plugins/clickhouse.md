---
title: ClickHouse
description: Installare e configurare il plug-in di servizio ClickHouse, raggiungere uno o più server, accedere con una password o un certificato client TLS, cercare database, tabelle e colonne, e gestire chi può leggere, scrivere e amministrare cosa, fino a colonne e righe.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in ClickHouse cerca database, tabelle e colonne, e gestisce policy di accesso nei termini di ClickHouse: leggere e scrivere colonne, modificare, eliminare, svuotare e ottimizzare tabelle, creare tabelle in un database ed eliminarlo, e sul server la creazione di database, `KILL QUERY` e le istruzioni `SYSTEM`, con filtri delle righe sulle tabelle. L’ID del plug-in e il tipo di servizio sono entrambi `clickhouse`. Il plug-in di servizio è eseguito nel server GrantForge; l’[agente ClickHouse](/it/external/clickhouse-agent/), un servizio accanto ai server, applica le policy con i privilegi e le row policy di ClickHouse stesso.

I test di integrazione Testcontainers del repository coprono ClickHouse `24.8`, `25.3`, `25.8`, `26.3` e `26.8`, le versioni con supporto a lungo termine degli ultimi due anni; ciò non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/clickhouse`. Verificate in **Gestione della piattaforma → Plug-in** che `clickhouse` sia abilitato; dopo un aggiornamento, ripetete la scansione della directory dei plug-in. Il plug-in comunica con i server via HTTP tramite il client Java di ClickHouse stesso, nel proprio class loader.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e scegliete ClickHouse (`clickhouse`).
2. Inserite l’URL dell’interfaccia HTTP di un server, o di più server.
3. Scegliete come accede l’utente di ricerca, inserite il nome utente e le credenziali, provate la connessione e salvate il servizio. La prova accede e descrive il server: la sua versione di ClickHouse, l’utente con cui ha effettuato l’accesso e quanti database vede quell’utente.

L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dagli utenti che leggono e scrivono. Una prova di connessione riuscita non significa che l’accesso sia già soggetto alle policy.

| Impostazione | Scopo |
| --- | --- |
| `clickhouse.urls` | Obbligatoria: l’interfaccia HTTP di un server, come `https://clickhouse1.example.com:8443`; più valori separati da virgole, provati a turno quando uno non è raggiungibile |
| `authentication` | `password` (predefinito) o `certificate`: un certificato client TLS che ClickHouse associa all’utente (`IDENTIFIED WITH ssl_certificate`) |
| `username` | Obbligatoria: l’utente di ricerca |
| `password` | password: la password dell’utente di ricerca; salvata cifrata |
| `ssl.root.certificate` | https: il certificato CA in PEM, sul **server GrantForge**, con cui si verificano i server; vuoto usa quello della JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate: il certificato client in PEM e la sua chiave privata, sul server GrantForge; l’accesso con certificato richiede server `https://` |
| `timeout` | Quanto può durare una richiesta, predefinito `30000`, intervallo `1..600000` millisecondi |

Le impostazioni di un tipo di accesso sono rifiutate con un altro, così un servizio dice esattamente come accede. Un accesso rifiutato, un utente in sola lettura, un server non raggiungibile o il cui certificato non può essere verificato, e qualcosa che non è ClickHouse a rispondere al suo posto, come un proxy, sono segnalati come tali.

### Cosa vede l’utente di ricerca

ClickHouse mostra a un utente solo i database, le tabelle e le colonne su cui ha qualche privilegio. Date all’utente di ricerca `SHOW` su ciò per cui si scriveranno policy, come `GRANT SHOW ON *.* TO grantforge_lookup`, che gli fa vedere i nomi senza leggere dati. I database di ClickHouse stesso, `system` e `information_schema`, restano fuori: le policy non li decidono.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `database` | Carattere jolly, distingue maiuscole e minuscole; il nome del database | `create`, `drop_database` |
| `table` | Carattere jolly, distingue maiuscole e minuscole; sotto un database | `alter`, `drop`, `truncate`, `optimize` |
| `column` | Carattere jolly, distingue maiuscole e minuscole; sotto una tabella | `select`, `insert` |
| `server` | Il suo unico valore, `*` | `create_database`, `kill_query`, `system` |

| Tipo di accesso | Privilegi di ClickHouse |
| --- | --- |
| `select`, `insert` | `SELECT`, `INSERT`, sulle colonne |
| `alter` | `ALTER`: ogni modifica di una tabella, compresi `ALTER UPDATE` e `ALTER DELETE` |
| `drop` | `DROP TABLE`, `DROP VIEW`, `DROP DICTIONARY` |
| `truncate`, `optimize` | `TRUNCATE`, `OPTIMIZE` |
| `create` | `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` nel database |
| `drop_database` | `DROP DATABASE` |
| `create_database`, `kill_query`, `system` | `CREATE DATABASE`, `KILL QUERY`, `SYSTEM`: ogni istruzione `SYSTEM` |

Lettura e scrittura si decidono per colonna, come le concede ClickHouse: una policy per `select` o `insert` nomina colonne, `*` per tutte. Così una policy può consentire una tabella e negarne una colonna, e l’utente può leggere tutte le altre. Nei valori delle policy, `*` corrisponde a qualsiasi sequenza di caratteri e `?` a uno. L’editor delle policy suggerisce i database, le tabelle dei database che una policy nomina e le loro colonne.

I tipi di accesso non sono nessuno dei gruppi di privilegi di ClickHouse come `ALL`: ciascuno è ciò che dice, così una policy non dà mai più di quanto nomina. ClickHouse non ha mascheramento delle colonne al di fuori di ClickHouse Cloud, quindi questo tipo di servizio non ne offre.

### Filtri delle righe

Una policy di filtro delle righe su una tabella mostra ai suoi utenti le righe per cui vale una condizione. La condizione è un’espressione ClickHouse, come la prendono le row policy, ad esempio `region = 'EU'` o `owner = currentUser()`. Un utente vede il filtro della prima policy che gli si applica, come per gli altri servizi.

## Esempio di policy

Supponiamo che il server abbia i database `sales` e `hr`. Create queste policy abilitate nello stesso servizio dati:

| Risorse | Regola |
| --- | --- |
| `database=sales`, `table=*`, `column=*` | Consentire `select` al gruppo `analysts` |
| `database=sales`, `table=orders`, `column=card` | Negare `select` al gruppo `analysts` |
| `database=sales`, `table=orders`, `column=*` | Consentire `insert` all’utente `ingest` |
| `database=team_*` | Consentire `create` al gruppo `data-team` |
| `database=team_*`, `table=*` | Consentire `alter`, `drop`, `truncate` e `optimize` al gruppo `data-team` |
| `database=team_*`, `table=*`, `column=*` | Consentire `select` e `insert` al gruppo `data-team` |
| `server=*` | Consentire `create_database` al gruppo `data-team` |
| `database=sales`, `table=orders` (filtro delle righe) | Mostrare all’utente `emea` le righe `region = 'EMEA'` |

Quando l’agente applica il nuovo snapshot, i membri di `analysts` leggono tutte le tabelle di `sales` tranne i numeri di carta di `orders`; `ingest` scrive `orders` e non legge nulla; i membri di `data-team` creano database e, in quelli il cui nome inizia con `team_`, tabelle, che leggono, scrivono e modificano; ed `emea`, se una policy gli permette di leggere `orders`, vede solo le righe della propria regione.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Applicarle richiede l’agente ClickHouse, uno per server o cluster. Distribuzione e verifiche sono descritte nell’[agente ClickHouse](/it/external/clickhouse-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente ClickHouse](/it/external/clickhouse-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
