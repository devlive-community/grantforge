---
title: Agente Trino
description: Installare il controllo degli accessi di sistema nel coordinator Trino per controllare le query con le policy GrantForge, elencare solo ciò che un utente può usare, filtrare le righe e mascherare le colonne, e sottoporre a policy l’impersonificazione e le query degli altri utenti.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente Trino è un controllo degli accessi di sistema chiamato `grantforge`: il coordinator lo interroga su ogni catalogo, schema, tabella, colonna, routine, proprietà di sessione e utente che una query tocca, ed esso risponde dalle policy firmate del servizio dati `trino`. Trino da 470 a 478 gira su Java 23 o 24 e Trino 479 e successivi su Java 25, quindi la distribuzione fornisce due JAR, ciascuno con le dipendenze rilocate al suo interno:

| Trino | JAR |
| --- | --- |
| Da 470 a 478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 e successivi | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

I test di integrazione Testcontainers del repository eseguono il primo JAR in Trino `470` e `476` e il secondo in `479` e `484`. Questo non certifica tutte le altre versioni. Vedete [Trino](/it/plugins/trino/) per il plug-in di servizio.

## Funzionamento

- **Ogni controllo è deciso in ogni ambito che nomina.** Un permesso su un catalogo, uno schema o una tabella copre ciò che contiene, quindi l’agente decide una colonna nel suo catalogo, nel suo schema, nella sua tabella e in sé stessa: un diniego in uno qualsiasi rifiuta, un consenso in uno qualsiasi concede.
- **Gli elenchi omettono ciò che non si può usare.** `SHOW CATALOGS`, `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW FUNCTIONS`, `information_schema` e i metadati del driver mostrano solo ciò che l’utente può usare, o in cui può usare qualcosa.
- **Ciò che descrive i dati resta aperto.** L’`information_schema` di ogni catalogo, gli schemi `jdbc` e `metadata` del catalogo `system`, letti dai driver JDBC e dagli strumenti di BI, e le funzioni integrate di Trino non richiedono policy; le loro righe sono filtrate come gli elenchi.
- **Le righe sono filtrate e le colonne mascherate.** Le policy di filtro delle righe si applicano a ogni query che legge la tabella, e le maschere sono adattate al tipo della colonna, perché una maschera non rompa mai una query: le maschere di testo si applicano a colonne `varchar` e `char` e sono riconvertite al tipo della colonna, la maschera dell’anno a date e timestamp, un’espressione personalizzata è convertita al tipo, e ogni altra colonna nominata da una maschera di testo si legge come un `NULL` del suo tipo.
- **L’impersonificazione e le query altrui sono sotto policy.** Eseguire una query come un altro utente richiede `impersonate` su quell’utente; vedere o terminare le query di un altro utente richiede `view_query` o `kill_query` su di lui. Chiunque può eseguire query, e vedere e terminare le proprie.
- **I rifiuti dicono perché.** Il client riceve `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`, oppure che nessuna policy lo consente, o che non sono ancora arrivate policy.
- **Le decisioni sono conservate e gli accessi raggruppati.** Le decisioni dello snapshot corrente sono conservate, per impostazione predefinita 100.000 per 10 secondi, e un nuovo snapshot riparte da zero. Gli accessi consentiti identici entro una finestra, per impostazione predefinita 10 secondi, sono segnalati come un unico evento di accesso che dice quanti erano; i rifiuti sono segnalati uno per uno, subito. Ogni evento nomina la query.
- **I privilegi sono di GrantForge.** Le istruzioni `GRANT`, `REVOKE`, `DENY` e sui ruoli di Trino sono rifiutate, perché le policy si gestiscono in GrantForge.

| Istruzione | Accesso necessario |
| --- | --- |
| `SELECT` | `select` su ogni colonna letta; una query che non legge colonne, come `count(*)`, sulla tabella |
| `INSERT`, `DELETE`, `TRUNCATE` | `insert`; `delete` per le altre due |
| `UPDATE` | `update` su ogni colonna scritta |
| `CREATE SCHEMA`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW` | `create` su ciò che si crea |
| `DROP` di uno schema, una tabella, una vista o una vista materializzata | `drop` su di esso |
| `ALTER` di una tabella, vista o colonna, `COMMENT`, `SET PROPERTIES`, `SET AUTHORIZATION`, `ALTER TABLE ... EXECUTE` | `alter` su di esso |
| Rinominare uno schema, una tabella, una vista o una vista materializzata | `alter` su di esso e `create` sul nuovo nome |
| `REFRESH MATERIALIZED VIEW` | `insert` su di essa |
| Una vista eseguita dal proprietario | Il proprietario ha bisogno di `select` sulle colonne e di `execute` sulle funzioni che legge |
| `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW COLUMNS`, `SHOW FUNCTIONS`, `SHOW CREATE` | Un qualsiasi accesso all’oggetto o al suo contenuto |
| `CREATE CATALOG`, `DROP CATALOG` | `create` o `drop` sul catalogo |
| Chiamare una funzione; `CREATE FUNCTION`, `DROP FUNCTION` | `execute`; `create_function`, `drop_function` sulla funzione |
| `CALL` | `execute` sulla procedura |
| `SET SESSION` | `set` sulla proprietà di sessione del catalogo o del sistema |
| Eseguire come un altro utente, vedere o terminare query altrui | `impersonate`, `view_query`, `kill_query` sull’utente |
| Leggere o scrivere le informazioni di sistema: nodi, statistiche e dump dei thread dell’API REST e dell’interfaccia web di Trino | `read` o `write` su `system_information` |

Da Trino 479, il secondo JAR controlla anche i branch delle tabelle: elencarli richiede di vedere la tabella, e crearne, eliminarne o farne avanzare uno richiede `alter` su di essa; aggiornare una vista e cambiare proprietario o commento di una vista materializzata richiedono `alter` su di essa.

## Distribuzione

1. Aggiungete un servizio dati `trino` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, selezionate il servizio ed emettete un token dell’agente. Viene mostrato una sola volta; conservatelo in un file leggibile solo dall’account di Trino.
2. Copiate il JAR della versione del coordinator in una directory propria nella directory dei plug-in di Trino, come `/usr/lib/trino/plugin/grantforge/`. Solo il coordinator controlla gli accessi; i worker non hanno bisogno di nulla.
3. Create `etc/access-control.properties` sul coordinator, sostituendo l’indirizzo, i percorsi e il nome di istanza di esempio:

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   La directory della cache deve essere scrivibile dall’account di Trino. GrantForge sostituisce il controllo degli accessi basato su file o altro di Trino invece di affiancarlo.
4. Riavviate il coordinator. Finché l’agente non ha il primo snapshot, le query sono rifiutate, tranne ciò che descrive i dati.
5. **Migrate prima le regole esistenti:** le regole del controllo degli accessi basato su file di Trino non si applicano più. Create prima le policy corrispondenti.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token dell’agente, un nome per questo coordinator e una directory scrivibile per lo snapshot firmato e gli eventi in coda |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica Ed25519 X.509 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave viene ottenuta dal server al primo contatto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite dello spool (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo gli accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` segnala ciascuno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Il massimo di decisioni conservate, predefinito `100000`, fino a `10000000`, `0` non ne conserva; e per quanto tempo, predefinito `10000`, `1..3600000` millisecondi |

## Utenti, gruppi e ruoli

Le policy nominano l’utente di sessione della query, come Trino lo fornisce dopo l’autenticazione e la mappatura degli utenti. I gruppi sono quelli che il provider di gruppi di Trino dà all’utente, insieme ai gruppi in cui GrantForge lo inserisce; i ruoli sono quelli che GrantForge gli assegna.

## Decisioni e limiti

- Senza uno snapshot utilizzabile tutto è rifiutato tranne ciò che descrive i dati, l’esecuzione delle query e le proprie query. Un servizio non in uso lascia Trino senza controlli, come senza controllo degli accessi; le istruzioni su privilegi e ruoli di Trino sono comunque rifiutate.
- Un token legato a un servizio il cui tipo non è `trino` rifiuta tutto.
- Trino chiede prima di tutto se un utente può usare un catalogo: un utente senza permessi in un catalogo riceve il `Cannot access catalog` di Trino.
- Una `SELECT` che legge una colonna che l’utente non può leggere è rifiutata, nominando la prima di tali colonne; le colonne non vengono tolte dal risultato.
- Trino non scrive in alcuna tabella di cui maschera colonne per l’utente: tale `INSERT` fallisce finché la maschera si applica.
- Una maschera è convertita al tipo della colonna, quindi un `varchar` limitato mostra quanto ci sta del valore mascherato, come 3 caratteri di un hash in `varchar(3)`.
- Un filtro delle righe o una maschera che lo snapshot non riesce a leggere non mostra righe, o mostra la colonna come `NULL`, invece dei dati.
- Le decisioni sono conservate per `grantforge.decision.cache.ttl.ms`, quindi il periodo di validità di una policy inizia o finisce fino a quel tempo dopo.

## Guide correlate

- [Trino](/it/plugins/trino/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
