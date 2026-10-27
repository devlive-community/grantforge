---
title: Agente PostgreSQL
description: Eseguire l’agente PostgreSQL accanto ai server, perché le policy di GrantForge diventino ruoli, privilegi e policy di sicurezza delle righe di PostgreSQL, fino a colonne e righe, database per database in una transazione ciascuno, e gli accessi siano segnalati dal log del server.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente PostgreSQL è un servizio accanto ai server. Tiene il controllo degli accessi di PostgreSQL allineato alle policy firmate di un servizio dati `postgresql`: un ruolo per utente che detiene esattamente ciò che le policy danno all’utente, in ogni database che tiene, e una policy di sicurezza delle righe per filtro delle righe. PostgreSQL controlla poi da sé ogni istruzione, e l’agente non si mette mai tra i client e il server. La distribuzione lo fornisce come JAR eseguibile, `agents/postgresql/grantforge-agent-postgresql-<version>.jar`, che richiede Java 17 o successivo.

I test di integrazione Testcontainers del repository eseguono l’agente, e il JAR così come distribuito, contro PostgreSQL `15`, `16`, `17` e `18` con pgaudit. Ciò non certifica tutte le altre versioni; l’agente rifiuta i server precedenti a `15`. Per il plug-in di servizio vedete [PostgreSQL](/it/plugins/postgresql/).

## Funzionamento

- **Un ruolo per utente detiene ciò che GrantForge dà.** Per ogni utente a cui le policy danno qualcosa, l’agente tiene un ruolo con il suo nome, `grantforge_alice` per `alice`, e rende l’utente un membro che ha i privilegi del ruolo senza `SET ROLE`. L’agente scrive un commento su ciascuno dei suoi ruoli e non prende per suo alcun ruolo che ne sia privo, così non modifica nessun altro ruolo né alcun privilegio che non ha concesso.
- **Ogni database cambia in una transazione.** L’agente accede a ogni database che tiene e vi apporta le sue modifiche in una transazione, così gli utenti vedono una versione delle policy applicata a un database per intero oppure per niente.
- **Intere tabelle dove nulla al di sotto decide diversamente.** Un tipo di accesso che una policy consente su tutta una tabella, senza alcuna policy che possa toglierlo a una colonna, viene concesso sulla tabella, così le colonne aggiunte in seguito lo hanno subito. Altrimenti lettura, inserimento e aggiornamento vengono concessi colonna per colonna. `CONNECT` sul database, `USAGE` sullo schema e sulle sequenze delle colonne serial in cui l’utente può inserire arrivano insieme.
- **I filtri delle righe restringono soltanto.** Un filtro delle righe è una policy di sicurezza delle righe restrittiva del ruolo dell’utente sulla tabella, per `SELECT`. Dove la sicurezza delle righe è disattivata, l’agente la attiva insieme a una policy permissiva che mostra tutte le righe a tutti, con il nome del suo prefisso (`grantforge`), così per gli altri non cambia nulla; la disattiva di nuovo quando la tabella non ha più filtri né policy proprie. La sicurezza delle righe attivata da un proprietario resta del proprietario, e le policy dell’agente restringono soltanto ciò che mostra. Un filtro che PostgreSQL rifiuta, uno che cambierebbe chi la policy vincola, o un testo di più di un’istruzione viene sostituito da una policy che non mostra alcuna riga all’utente, e registrato.
- **Le policy del momento presente.** Ogni sincronizzazione pianifica secondo le policy come valgono in quel momento, così i periodi di validità iniziano e finiscono puntuali, e secondo gli utenti, gli schemi, le tabelle e le colonne che PostgreSQL ha, così quelli nuovi sono coperti alla sincronizzazione successiva.
- **I ruoli sono gruppi.** Una policy per un gruppo vale per gli utenti membri di un ruolo PostgreSQL con quel nome, direttamente o tramite altri ruoli, insieme ai gruppi in cui GrantForge li colloca.
- **Le modifiche fatte a mano vengono annullate.** A ogni sincronizzazione l’agente confronta i privilegi dei suoi ruoli con ciò che PostgreSQL mostra, e le sue policy di sicurezza delle righe con ciò che ha creato per ultimo; ciò che differisce viene ripristinato alla sincronizzazione successiva. Quando nulla è cambiato, l’agente non invia nulla.
- **I privilegi fuori da GrantForge vengono segnalati.** PostgreSQL somma i privilegi, quindi l’agente non può togliere ciò che un utente detiene da sé, tramite un altro ruolo o tramite PUBLIC, né ciò che può fare con le tabelle di cui è proprietario. Avvisa una volta di ciascuno di questi privilegi che dà ciò che una policy nega, indicando l’utente, il privilegio, come l’utente lo detiene e la policy.
- **Gli accessi vengono dal log del server.** L’agente legge il log JSON del server PostgreSQL: il log di sessione di pgaudit per ciò che PostgreSQL ha consentito, un evento per ogni tabella che un’istruzione ha letto o scritto, e i rifiuti di PostgreSQL per un privilegio mancante, un evento negato per ciò che l’errore nomina. Ogni evento dice se lo ha deciso GrantForge, cioè se le policy decidono come PostgreSQL, o se lo ha consentito un privilegio fuori da GrantForge.

| Istruzione | Evento di accesso |
| --- | --- |
| `SELECT`, `COPY … TO`, la parte di lettura di altre istruzioni | `select` su ogni tabella letta |
| `INSERT`, `COPY … FROM` | `insert` sulla tabella scritta |
| `UPDATE`, `MERGE`, `SELECT … FOR UPDATE` | `update` sulla tabella |
| `DELETE`, `TRUNCATE` | `delete`, `truncate` sulla tabella |
| `CREATE TRIGGER` | `trigger` sulla tabella |
| `CREATE TABLE`, `CREATE VIEW` e altri oggetti | `create` sullo schema |
| `CREATE SCHEMA`, `CREATE TEMPORARY TABLE` | `create_schema`, `temporary` sul database |
| Una connessione rifiutata, uno schema rifiutato per mancanza di `USAGE` | `connect` sul database, `usage` sullo schema |

## Distribuzione

1. Aggiungete un servizio dati `postgresql` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, scegliete il servizio ed emettete un token di agente. È mostrato una sola volta; salvatelo in un file leggibile solo dall’account dell’agente.
2. Create l’utente PostgreSQL dell’agente come superutente. Concede privilegi su tabelle di cui non è proprietario, attiva e disattiva la sicurezza delle righe, tiene ruoli e legge il log del server:

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   I test eseguono l’agente come superutente. I superutenti non sono vincolati né dai privilegi né dalla sicurezza delle righe, quindi l’agente non tiene ruoli per loro.
3. Per segnalare gli accessi, fate scrivere a PostgreSQL il log del server in JSON e caricate pgaudit, dai pacchetti di PGDG come `postgresql-17-pgaudit` o da quelli della piattaforma, in `postgresql.conf`, poi riavviate il server:

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   Senza pgaudit l’agente segnala ciò che PostgreSQL ha rifiutato, non ciò che ha consentito; senza il log JSON non segnala nulla e lo dice una volta.
4. Scrivete il file delle impostazioni, sostituendo indirizzi, percorsi e nomi d’esempio:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   Questi percorsi sono sulla macchina dell’agente, e la cartella della cache deve essere scrivibile. I file di password sono letti come UTF-8 e privati degli spazi iniziali e finali.
5. Avviate l’agente, ad esempio come servizio systemd:

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   Sostituite `<version>` con la versione della distribuzione. Le impostazioni passate come `java -Dgrantforge.…=… -jar …` sostituiscono quelle del file. L’agente si ferma in modo pulito con SIGTERM e invia o accoda gli eventi in sospeso; PostgreSQL conserva ciò che ha ricevuto.
6. **Portare i privilegi esistenti nelle policy:** i privilegi concessi fuori da GrantForge continuano a valere accanto ai ruoli dell’agente. Create le policy corrispondenti, poi revocate i privilegi; gli avvisi dell’agente nominano quelli che danno ciò che una policy nega.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatori: il server GrantForge, il file del token dell’agente, un nome per questo agente e una cartella scrivibile per lo snapshot firmato, gli eventi accodati e la posizione di lettura del log del server |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica Ed25519 X.509 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave viene presa dal server al primo contatto |
| `grantforge.postgresql.hosts`, `grantforge.postgresql.user` | Obbligatori: i server come `host:port`, provati a turno finché uno raggiungibile non è il primario, e l’utente PostgreSQL dell’agente |
| `grantforge.postgresql.database` | Il database a cui l’agente accede per primo, per leggere ruoli, database e log del server, predefinito `postgres` |
| `grantforge.postgresql.password.file` | La password dell’utente; se omesso, l’agente accede senza password |
| `grantforge.postgresql.ssl.mode` | TLS come lo chiama `sslmode` di libpq, predefinito `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca e verify-full: certificato CA PEM che verifica i server; vuoto si fida di ciò di cui si fida Java |
| `grantforge.postgresql.tls.cert`, `grantforge.postgresql.tls.key` | Certificato client PEM e la sua chiave privata PKCS #8, insieme, per accedere con un certificato (autenticazione `cert`) invece che con una password; richiedono `require` o una modalità di verifica, e una modalità di verifica richiede allora `grantforge.postgresql.tls.ca` |
| `grantforge.postgresql.timeout.ms` | Quanto possono durare la connessione e un’istruzione, predefinito `30000`, intervallo `1..600000` millisecondi |
| `grantforge.postgresql.databases` | I database che l’agente tiene, separati da virgole; vuoto tiene ogni database che accetta connessioni |
| `grantforge.postgresql.role.prefix` | L’inizio dei nomi dei ruoli dell’agente, predefinito `grantforge_`; lettere, cifre e trattini bassi, terminante con un trattino basso, al massimo 32 caratteri e senza iniziare con `pg_`. All’agente appartengono i ruoli che ha marcato il cui nome inizia così, e le policy di sicurezza delle righe con il loro nome o con quello del prefisso senza l’ultimo trattino basso |
| `grantforge.postgresql.unmanaged.users` | Utenti che l’agente lascia stare, separati da virgole, come gli amministratori; lascia sempre stare il proprio utente e i superutenti |
| `grantforge.sync.interval.ms` | Ogni quanto l’agente allinea PostgreSQL, predefinito `10000`, intervallo `1000..3600000` millisecondi |
| `grantforge.audit.server.log` | Predefinito `true`; `false` non legge il log del server e non segnala accessi |
| `grantforge.audit.server.log.interval.ms`, `grantforge.audit.server.log.max.bytes` | Ogni quanto si legge il log, predefinito `5000` millisecondi, e il massimo di byte di una lettura, predefinito 16 MiB, così un arretrato si legge in più turni |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinito `500`), dimensione della coda (predefinito `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` invia ciascuno |

## Utenti, gruppi e ruoli

Le policy nominano utenti PostgreSQL, i ruoli che possono accedere. I gruppi sono i ruoli di cui un utente è membro, direttamente o tramite altri ruoli, insieme ai gruppi in cui GrantForge colloca l’utente; i ruoli sono quelli che GrantForge dà all’utente.

## Decisioni e limiti

- Senza snapshot l’agente non cambia nulla. Un servizio non in uso, o un token legato a un servizio il cui tipo non è `postgresql`, non concede nulla: l’agente elimina i suoi ruoli e le sue policy di sicurezza delle righe, e valgono i privilegi propri di PostgreSQL.
- Finché l’agente non gira, PostgreSQL conserva ciò che ha ricevuto per ultimo; le modifiche alle policy lo aspettano. Una modifica raggiunge PostgreSQL entro l’intervallo di aggiornamento e quello di sincronizzazione.
- Le tabelle create dopo una sincronizzazione ricevono i loro privilegi alla sincronizzazione successiva.
- I superutenti e i proprietari di una tabella possono farci di tutto; superutenti, ruoli con `BYPASSRLS` e proprietari di una tabella non sono vincolati dalla sua sicurezza delle righe, a meno che il proprietario non la imponga.
- Una vista legge le sue tabelle con i privilegi del suo proprietario a meno che non sia `security_invoker`: il log del server segnala le tabelle lette da una vista come consentite da un privilegio fuori da GrantForge.
- La lettura diretta di una partizione non è filtrata dalla sicurezza delle righe della sua tabella partizionata.
- pgaudit registra un’istruzione prima che PostgreSQL la esegua, quindi un’istruzione che fallisce dopo, come un inserimento la cui sequenza viene rifiutata, è segnalata come consentita e il suo rifiuto come negato. Le istruzioni di una sessione che pgaudit non registra non vengono segnalate.
- I rifiuti di PostgreSQL nominano una tabella senza il suo schema: l’evento nomina la tabella che l’istruzione qualifica, l’unica tabella con quel nome o quella in `public`. Un oggetto creato senza schema è segnalato in `public`.
- L’agente legge il log del server a cui accede, il primario; i log delle repliche non vengono letti.
- PostgreSQL non ha mascheramento delle colonne, quindi le policy non possono mascherare valori.

## Guide correlate

- [PostgreSQL](/it/plugins/postgresql/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
