---
title: PostgreSQL
description: Installare e configurare il plug-in di servizio PostgreSQL, raggiungere uno o più server, accedere con una password o un certificato client TLS, cercare database, schemi, tabelle e colonne, e gestire chi può leggere, scrivere e creare cosa, fino a colonne e righe.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in PostgreSQL cerca database, schemi, tabelle e colonne, e gestisce le policy di accesso nei termini di PostgreSQL: leggere, inserire e aggiornare colonne, eliminare righe, svuotare tabelle e crearvi trigger, creare oggetti in uno schema, e creare schemi e tabelle temporanee in un database, con filtri delle righe sulle tabelle. L’ID del plug-in e il tipo di servizio sono entrambi `postgresql`. Il plug-in di servizio gira nel server GrantForge; l’[agente PostgreSQL](/it/external/postgresql-agent/), un servizio accanto ai server, applica le policy con i privilegi e le policy di sicurezza delle righe di PostgreSQL.

I test di integrazione Testcontainers del repository coprono PostgreSQL `15`, `16`, `17` e `18`, le versioni che PostgreSQL mantiene; ciò non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/postgresql`. Verificate in **Gestione della piattaforma → Plug-in** che `postgresql` sia abilitato; dopo un aggiornamento ripetete la scansione della cartella dei plug-in. Il plug-in parla con i server tramite il driver JDBC di PostgreSQL, in un class loader tutto suo.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e scegliete PostgreSQL (`postgresql`).
2. Indicate un server come `host:port`, oppure più server.
3. Scegliete come accede l’utente di ricerca, inserite il suo nome e le sue credenziali, provate la connessione e salvate il servizio. La prova accede e descrive il server: la sua versione di PostgreSQL, l’utente con cui ha effettuato l’accesso e quanti database ha il server.

L’utente di ricerca permette a GrantForge di scoprire le risorse; è separato dagli utenti che leggono e scrivono. Una prova di connessione riuscita non significa che l’accesso sia già soggetto alle policy.

| Impostazione | Scopo |
| --- | --- |
| `postgresql.hosts` | Obbligatorio: un server come `host:port`, ad esempio `postgres1.example.com:5432`; più server separati da virgole, provati a turno quando uno non è raggiungibile. La porta predefinita è `5432` |
| `postgresql.database` | Il database a cui l’utente di ricerca accede per primo, per elencare i database; predefinito `postgres` |
| `authentication` | `password` (predefinito) o `certificate`: un certificato client TLS che l’autenticazione `cert` di PostgreSQL associa all’utente |
| `username` | Obbligatorio: l’utente di ricerca |
| `password` | password: la password dell’utente di ricerca; salvata cifrata |
| `ssl.mode` | TLS come lo chiama `sslmode` di libpq: `disable`, `allow`, `prefer` (predefinito), `require`, `verify-ca` o `verify-full`, che verifica anche il nome del server |
| `ssl.root.certificate` | verify-ca e verify-full: il certificato CA PEM con cui si verificano i server, sul **server GrantForge**; vuoto usa quello della JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate: il certificato client PEM e la sua chiave privata PKCS #8, PEM o DER, sul server GrantForge; l’accesso con certificato richiede `require` o una modalità di verifica, e una modalità di verifica richiede allora `ssl.root.certificate` |
| `timeout` | Quanto possono durare la connessione e una query, predefinito `30000`, intervallo `1..600000` millisecondi |

Le impostazioni di un tipo di accesso vengono rifiutate con un altro, così un servizio dice esattamente come accede. Il driver di PostgreSQL rifiuta un file di chiave privata leggibile da altri oltre al suo proprietario. Un accesso rifiutato, un database inesistente, un server irraggiungibile o il cui certificato non si può verificare vengono segnalati come tali.

### Cosa vede l’utente di ricerca

I cataloghi di PostgreSQL mostrano a ogni utente i nomi dei database e, in ogni database a cui può connettersi, i nomi di schemi, tabelle e colonne, senza leggere dati. All’utente di ricerca basta `CONNECT` sui database per cui scrivere le policy, che PUBLIC ha a meno che non sia stato revocato. Una ricerca di schemi, tabelle o colonne accede a ogni database che la policy nomina, al massimo 20, e salta i database a cui l’utente di ricerca non può connettersi. Gli schemi propri di PostgreSQL, quelli che iniziano con `pg_` e `information_schema`, sono esclusi: le policy non li decidono.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `database` | Caratteri jolly, sensibile alle maiuscole; il nome del database | `create_schema`, `temporary` |
| `schema` | Caratteri jolly, sensibile alle maiuscole; sotto un database | `create` |
| `table` | Caratteri jolly, sensibile alle maiuscole; tabelle, tabelle partizionate, viste, viste materializzate e tabelle esterne sotto uno schema | `delete`, `truncate`, `trigger` |
| `column` | Caratteri jolly, sensibile alle maiuscole; sotto una tabella | `select`, `insert`, `update` |

| Tipo di accesso | Privilegi di PostgreSQL |
| --- | --- |
| `select`, `insert`, `update` | `SELECT`, `INSERT`, `UPDATE`, su colonne o sull’intera tabella |
| `delete`, `truncate`, `trigger` | `DELETE`, `TRUNCATE`, `TRIGGER` sulla tabella |
| `create` | `CREATE` sullo schema: tabelle, viste, sequenze, funzioni e altri oggetti al suo interno |
| `create_schema` | `CREATE` sul database: schemi al suo interno |
| `temporary` | `TEMPORARY` sul database |

Lettura, inserimento e aggiornamento si decidono per colonna, come PostgreSQL li concede: una policy per `select`, `insert` o `update` nomina colonne, `*` per tutte. Così una policy può consentire una tabella e negarne una colonna, e l’utente può leggere tutte le altre. Nei valori delle policy, `*` corrisponde a qualsiasi sequenza di caratteri e `?` a uno solo. L’editor delle policy suggerisce i database, gli schemi dei database che una policy nomina, le loro tabelle e colonne.

Raggiungere ciò che una policy consente richiede altro a PostgreSQL: `CONNECT` sul database, `USAGE` sullo schema e, per inserire in una colonna serial, `USAGE` sulla sua sequenza. Non sono tipi di accesso a sé: l’agente li concede insieme a ciò che ne ha bisogno. PostgreSQL non ha mascheramento delle colonne, quindi questo tipo di servizio non ne offre.

### Filtri delle righe

Una policy di filtro delle righe su una tabella mostra ai suoi utenti le righe per cui vale una condizione. La condizione è un’espressione PostgreSQL, come la prende lo `USING` di una policy di sicurezza delle righe, ad esempio `region = 'EU'` o `owner = current_user`. Un utente vede il filtro della prima policy che gli si applica, come negli altri servizi. PostgreSQL filtra solo le righe di tabelle e tabelle partizionate: una vista o un oggetto simile di cui una policy filtra le righe per un utente non è affatto leggibile per quell’utente.

## Esempio di policy

Supponiamo che il server abbia i database `sales` e `hr`, e che `sales` abbia lo schema `crm`. Create queste policy abilitate nello stesso servizio dati:

| Risorse | Regola |
| --- | --- |
| `database=sales`, `schema=crm`, `table=*`, `column=*` | Consentire `select` al gruppo `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=card` | Negare `select` al gruppo `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=*` | Consentire `insert` all’utente `ingest` |
| `database=sales`, `schema=team_*` | Consentire `create` al gruppo `data-team` |
| `database=sales`, `schema=team_*`, `table=*` | Consentire `delete` e `truncate` al gruppo `data-team` |
| `database=sales`, `schema=team_*`, `table=*`, `column=*` | Consentire `select`, `insert` e `update` al gruppo `data-team` |
| `database=sales` | Consentire `create_schema` al gruppo `data-team` |
| `database=sales`, `schema=crm`, `table=orders` (filtro delle righe) | Mostrare all’utente `emea` le righe `region = 'EMEA'` |

Una volta che l’agente applica il nuovo snapshot, i membri di `analysts` leggono ogni tabella di `crm` tranne i numeri di carta di `orders`; `ingest` inserisce in `orders`, compresi i suoi id serial, e non legge nulla; i membri di `data-team` creano schemi e, in quelli il cui nome inizia con `team_`, creano, leggono, modificano e svuotano tabelle; ed `emea`, se una policy gli permette di leggere `orders`, vede solo le righe della propria regione.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Applicarle richiede l’agente PostgreSQL, uno per server o primario. Distribuzione e controlli sono nell’[agente PostgreSQL](/it/external/postgresql-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente PostgreSQL](/it/external/postgresql-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
