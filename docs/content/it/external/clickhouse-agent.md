---
title: Agente ClickHouse
description: Eseguire l’agente ClickHouse accanto ai server, perché le policy di GrantForge diventino i ruoli, i privilegi e le row policy di ClickHouse stesso, fino a colonne e righe, e gli accessi siano segnalati dal registro delle query.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente ClickHouse è un servizio accanto ai server. Mantiene il controllo degli accessi di ClickHouse stesso in linea con le policy firmate di un servizio dati `clickhouse`: un ruolo per utente che detiene esattamente ciò che le policy danno all’utente, e una row policy per ogni filtro delle righe. ClickHouse controlla quindi da sé ogni query, su ciascuno dei suoi protocolli, e l’agente non si mette mai tra i client e il server. La distribuzione lo fornisce come JAR eseguibile, `agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`, che richiede Java 17 o successivo.

I test di integrazione Testcontainers del repository eseguono l’agente, e il JAR così come distribuito, su ClickHouse `24.8`, `25.3`, `25.8`, `26.3` e `26.8`. Ciò non certifica tutte le altre versioni; l’agente rifiuta i server precedenti a `24.8`. Il plug-in di servizio è descritto in [ClickHouse](/it/plugins/clickhouse/).

## Funzionamento

- **Un ruolo per utente detiene ciò che GrantForge dà.** Per ogni utente a cui le policy danno qualcosa, l’agente tiene un ruolo con il suo nome, `grantforge_alice` per `alice`, e glielo concede come ruolo predefinito. `SHOW GRANTS FOR grantforge_alice` mostra tutto ciò che GrantForge dà ad `alice`; l’agente non modifica nessun altro ruolo né alcun privilegio che non abbia concesso lui.
- **Interi database dove nulla al di sotto decide diversamente.** Un tipo di accesso che una policy consente su tutto un database, senza alcuna policy che possa toglierlo a una sua tabella o colonna, viene concesso sul database, così le tabelle create in seguito lo hanno subito. Altrimenti viene concesso tabella per tabella, e lettura e scrittura colonna per colonna.
- **I filtri delle righe diventano row policy.** Un filtro delle righe è una row policy permissiva del ruolo dell’utente sulla tabella. Un filtro nuovo o modificato è al suo posto prima dei privilegi che restringe. Un filtro che ClickHouse rifiuta, o il cui testo farebbe applicare la row policy ad altri, viene sostituito da uno che non mostra all’utente alcuna riga, e registrato.
- **Le policy del momento presente.** Ogni sincronizzazione pianifica secondo le policy come valgono in quel momento, così i periodi di validità iniziano e finiscono in orario, e secondo gli utenti, i database, le tabelle e le colonne che ClickHouse ha, così quelli nuovi sono coperti alla sincronizzazione successiva.
- **I ruoli ClickHouse sono gruppi.** Una policy per un gruppo vale per gli utenti che detengono un ruolo ClickHouse con quel nome, direttamente o tramite altri ruoli, insieme ai gruppi in cui GrantForge li inserisce.
- **Le modifiche fatte a mano vengono annullate.** L’agente ricorda ciò che ha dato a ogni ruolo e row policy; quello che differisce viene ripristinato alla sincronizzazione successiva. Quando nulla è cambiato, l’agente non invia nulla.
- **I privilegi esterni a GrantForge vengono segnalati.** ClickHouse somma i privilegi, quindi l’agente non può togliere ciò che un utente detiene direttamente o tramite un altro ruolo. Avvisa una volta per ogni privilegio di questo tipo che dà ciò che una policy nega, indicando l’utente, il privilegio, il ruolo da cui proviene e la policy.
- **Gli accessi vengono dal registro delle query.** L’agente legge `system.query_log` e segnala ogni istruzione come un evento per tabella, database o server che ha toccato, e un rifiuto come un evento negato per ciò che l’errore di ClickHouse nomina. Ogni evento dice se lo ha deciso GrantForge, cioè se le policy decidono come ClickHouse, o se lo ha consentito un privilegio esterno a GrantForge.

| Istruzione | Evento di accesso |
| --- | --- |
| `SELECT`, la parte di lettura di `INSERT … SELECT` | `select` su ogni tabella letta |
| `INSERT` | `insert` sulla tabella scritta |
| `ALTER`, `ALTER UPDATE`, `ALTER DELETE`, `DELETE`, `UPDATE` | `alter` sulla tabella |
| `DROP TABLE`, `TRUNCATE`, `OPTIMIZE` | `drop`, `truncate`, `optimize` sulla tabella |
| `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` | `create` sul database |
| `DROP DATABASE` | `drop_database` sul database |
| `CREATE DATABASE`, `KILL QUERY`, `SYSTEM …` | `create_database`, `kill_query`, `system` sul server |

## Distribuzione

1. Aggiungete un servizio dati `clickhouse` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, scegliete il servizio ed emettete un token di agente. È mostrato una sola volta; salvatelo in un file leggibile solo dall’account dell’agente.
2. Create l’utente ClickHouse dell’agente. Concede ciò che danno le policy, quindi gli servono quei privilegi con l’opzione di concessione, e la gestione degli accessi per tenere ruoli e row policy:

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   Gli utenti per cui l’agente tiene ruoli devono essere memorizzati dove possono essere modificati, nella directory utenti `local_directory` o `replicated`, dove li memorizza `CREATE USER`. Gli utenti di `users.xml` e di LDAP restano come sono e vengono registrati una volta. Un cluster dovrebbe tenere utenti, ruoli e row policy in una directory utenti `replicated`: un solo agente serve allora l’intero cluster.
3. Scrivete il file delle impostazioni, sostituendo indirizzi, percorsi e nomi dell’esempio:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   Questi percorsi sono sulla macchina dell’agente, e la directory di cache deve essere scrivibile. I file delle password sono letti come UTF-8 e ripuliti dagli spazi.
4. Avviate l’agente, ad esempio come servizio systemd:

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   Sostituite `<version>` con la versione della distribuzione. Le impostazioni fornite come `java -Dgrantforge.…=… -jar …` sostituiscono quelle del file. L’agente si arresta in modo pulito con SIGTERM e invia o accoda gli eventi in sospeso; ClickHouse mantiene ciò che ha ricevuto.
5. **Portate i privilegi esistenti nelle policy:** i privilegi concessi al di fuori di GrantForge continuano a valere accanto ai ruoli dell’agente. Create le policy corrispondenti, poi revocate quei privilegi; gli avvisi dell’agente nominano quelli che danno ciò che una policy nega.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per questo agente e una directory scrivibile per lo snapshot firmato, gli eventi accodati e la posizione di lettura del registro delle query |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave viene ottenuta dal server al primo contatto |
| `grantforge.clickhouse.urls`, `grantforge.clickhouse.user` | Obbligatorie: le interfacce HTTP dei server, provate a turno quando una non è raggiungibile, e l’utente ClickHouse dell’agente |
| `grantforge.clickhouse.password.file` | La password dell’utente; se omessa, l’agente accede senza password |
| `grantforge.clickhouse.tls.ca` | Certificato CA in PEM che verifica i server `https://`; vuoto si fida di ciò di cui si fida Java |
| `grantforge.clickhouse.tls.cert`, `grantforge.clickhouse.tls.key` | Certificato client in PEM e chiave privata, insieme, per accedere con un certificato (`IDENTIFIED WITH ssl_certificate`) invece che con una password |
| `grantforge.clickhouse.timeout.ms` | Quanto può durare una richiesta a ClickHouse, predefinito `30000`, intervallo `1..600000` millisecondi |
| `grantforge.clickhouse.role.prefix` | L’inizio dei nomi dei ruoli dell’agente, predefinito `grantforge_`; lettere, cifre e trattini bassi, terminato da un trattino basso. All’agente appartengono tutti i ruoli e le row policy il cui nome inizia così |
| `grantforge.clickhouse.unmanaged.users` | Utenti che l’agente lascia stare, separati da virgole, come gli amministratori; lascia sempre stare il proprio utente |
| `grantforge.sync.interval.ms` | Ogni quanto l’agente allinea ClickHouse, predefinito `10000`, intervallo `1000..3600000` millisecondi |
| `grantforge.audit.query.log` | Predefinito `true`; `false` non legge alcun registro delle query e non segnala accessi |
| `grantforge.audit.query.log.cluster` | Un cluster i cui registri delle repliche sono letti tramite `clusterAllReplicas`; vuoto legge il server che l’agente interroga |
| `grantforge.audit.query.log.delay.ms`, `grantforge.audit.query.log.interval.ms`, `grantforge.audit.query.log.batch` | Quanto indietro rispetto al presente si legge il registro, predefinito `10000` millisecondi, poiché ClickHouse lo scrive ogni 7,5 secondi; ogni quanto, predefinito `5000` millisecondi; e il massimo di voci per lettura, predefinito `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e di lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinito `500`), dimensione della coda (predefinito `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo accessi consentiti identici vengono riuniti in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` li invia uno per uno |

## Utenti, gruppi e ruoli

Le policy nominano utenti ClickHouse. I gruppi sono i ruoli ClickHouse che un utente detiene, direttamente o tramite altri ruoli, insieme ai gruppi in cui GrantForge inserisce l’utente; i ruoli sono quelli che GrantForge dà all’utente.

## Decisioni e limiti

- Senza snapshot l’agente non cambia nulla. Un servizio non in uso, o un token legato a un servizio il cui tipo non è `clickhouse`, non concede nulla: l’agente elimina i suoi ruoli e le sue row policy, e valgono i privilegi di ClickHouse stesso.
- Finché l’agente non è in esecuzione, ClickHouse mantiene ciò che ha ricevuto per ultimo; le modifiche alle policy lo attendono. Una modifica raggiunge ClickHouse entro l’intervallo di aggiornamento e l’intervallo di sincronizzazione.
- Le tabelle create in un database che l’agente concede tabella per tabella ricevono i loro privilegi alla sincronizzazione successiva.
- Un utente per cui ClickHouse non mostra alcuna row policy vede ogni riga di una tabella, come consente per impostazione predefinita l’impostazione `users_without_row_policies_can_read_rows` di ClickHouse; se è disattivata, gli utenti senza filtro non vedono alcuna riga delle tabelle filtrate. Row policy permissive create al di fuori di GrantForge per lo stesso utente ampliano ciò che il suo filtro mostra.
- `system` concede ogni istruzione `SYSTEM`, compreso `SYSTEM SHUTDOWN`.
- Il registro delle query segnala ciò che ClickHouse registra: le istruzioni degli utenti con `log_queries` disattivato non vengono segnalate. In un rifiuto, ClickHouse può nominare la tabella quando all’utente manca una colonna; l’evento nomina allora la tabella.
- ClickHouse non ha mascheramento delle colonne al di fuori di ClickHouse Cloud, quindi le policy non possono mascherare valori.

## Guide correlate

- [ClickHouse](/it/plugins/clickhouse/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
