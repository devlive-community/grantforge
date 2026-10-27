---
title: Agente Apache Doris
description: Eseguire l’agente Apache Doris accanto al cluster, così che le policy GrantForge diventino i ruoli, i privilegi di colonna, i privilegi dei workload group e le policy di riga di Doris stesso, dati a ogni identità di un utente, concessi su database e cataloghi interi solo dove Doris non darebbe di più, e che gli accessi siano segnalati dalla tabella del log di audit di Doris.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente Apache Doris è un servizio accanto al cluster. Mantiene il controllo degli accessi di Doris stesso allineato con le policy firmate di un servizio dati `doris`: un ruolo per utente che contiene esattamente ciò che le policy gli danno, fino a colonne e workload group, e una policy di riga per filtro delle righe. Doris verifica poi da sé ogni istruzione, e l’agente non si mette mai tra i client e i frontend. La distribuzione lo fornisce come JAR eseguibile, `agents/doris/grantforge-agent-doris-<version>.jar`, che richiede Java 17 o successivo.

I test di integrazione Testcontainers del repository eseguono l’agente, e il JAR così come distribuito, contro Apache Doris `2.1`, `3.1`, `4.0` e `4.1`. Ciò non certifica tutte le altre versioni; l’agente rifiuta cluster precedenti a `2.1`. Per il plug-in di servizio vedete [Apache Doris](/it/plugins/doris/).

## Funzionamento

- **Un ruolo per utente contiene ciò che GrantForge dà.** Per ogni utente a cui le policy danno qualcosa, l’agente tiene un ruolo con il suo nome, `grantforge_alice` per `alice`, con un commento che nomina l’utente, e lo concede a ogni identità dell’utente, come `'alice'@'%'` e `'alice'@'10.%'`. `SHOW ROLES` mostra ciò che GrantForge dà ad `alice`; l’agente non modifica nessun altro ruolo né alcun privilegio che non ha concesso.
- **Database e cataloghi interi dove niente sotto è deciso diversamente.** Lettura, caricamento e visualizzazione delle viste che una policy consente su un intero database o catalogo, senza policy che possa toglierli più sotto, sono concessi sul database o sul catalogo, così le tabelle create in seguito li hanno subito. Altrimenti sono concessi tabella per tabella, e la lettura colonna per colonna. La modifica è concessa tabella per tabella, poiché il privilegio di Doris su un database lo farebbe anche rinominare.
- **Mai più di quanto le policy consentano.** Il privilegio di Doris di creare database in un catalogo è quello di creare tabelle in tutti, e quello di eliminare un database è quello di eliminarne tutte le tabelle. L’agente concede ciascuno solo dove le policy danno anche il privilegio più ampio; altrimenti lo trattiene e registra una volta quale utente non può fare cosa e perché.
- **I workload group sono concessi per nome.** Una policy sui workload group dà `USAGE_PRIV` su ogni gruppo che consente, così un utente può eseguirvi query; i gruppi creati in seguito sono coperti alla sincronizzazione successiva.
- **I filtri delle righe diventano policy di riga.** Un filtro delle righe è una policy di riga permissiva del ruolo dell’utente sulla tabella. Un nuovo filtro è in posizione prima dei privilegi che restringe; un filtro che cambia è sostituito dietro una policy restrittiva che non mostra righe, così l’utente non vede mai più di quanto mostri l’uno o l’altro. Ogni filtro è prima eseguito sulla sua tabella; uno che Doris non può eseguirvi o rifiuta, come uno che nomina una colonna che la tabella non ha, è sostituito da uno che non mostra righe all’utente, e registrato.
- **Le policy del momento presente.** Ogni sincronizzazione pianifica secondo le policy come si applicano in quel momento, così i periodi di validità iniziano e finiscono in orario, e secondo gli utenti, i cataloghi, i database, le tabelle e le colonne di Doris, così quelli nuovi sono coperti alla sincronizzazione successiva.
- **I ruoli di Doris sono gruppi.** Una policy per un gruppo vale per gli utenti che hanno un ruolo Doris con quel nome, insieme ai gruppi in cui GrantForge li mette.
- **Le modifiche fatte a mano vengono annullate.** L’agente confronta a ogni sincronizzazione i privilegi dei suoi ruoli, come li mostra `SHOW ROLES`, e le sue policy di riga con i piani, e ripristina ciò che differisce. Doris mostra i privilegi di colonna solo per utente, uniti su tutti i suoi privilegi: l’agente concede le colonne che un piano nomina dove un utente non può leggerle tutte, e toglie una volta le altre dal suo ruolo; una colonna che resta leggibile viene da un privilegio fuori da GrantForge. Quando nulla è cambiato, l’agente non invia nulla.
- **I privilegi fuori da GrantForge vengono segnalati.** Doris somma i privilegi e unisce le policy di riga permissive con OR, quindi l’agente non può togliere ciò che un utente ha direttamente o tramite un altro ruolo. Avvisa una volta di ogni privilegio, privilegio di colonna o policy di riga di questo tipo che dà ciò che una policy nega, nominando l’utente, il privilegio e la sua provenienza, e degli utenti con `ADMIN_PRIV` o `NODE_PRIV`, che nessun ruolo limita.
- **Gli accessi vengono dal log di audit.** L’agente legge `__internal_schema.audit_log`, che ogni frontend scrive, e segnala ogni istruzione come un evento per tabella, database o catalogo toccato; quali tabelle lo legge dall’istruzione stessa con il parser Doris di Druid, poiché il log non ne nomina. Un rifiuto è un evento negato per il privilegio e l’oggetto che l’errore di Doris nomina. Ogni evento dice se l’ha deciso GrantForge, cioè se le policy decidono come Doris, o se l’ha consentito un privilegio fuori da GrantForge. Il log è letto indietro rispetto al presente dell’intervallo di batch dei frontend, `audit_plugin_max_batch_interval_sec`, più cinque secondi.

| Istruzione | Evento di accesso |
| --- | --- |
| `SELECT`, la parte di lettura di `INSERT … SELECT` | `select` su ogni tabella letta |
| `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` | `load` sulla tabella scritta |
| `ALTER TABLE`, `DROP TABLE`, `SHOW CREATE VIEW` | `alter`, `drop`, `show_view` sulla tabella |
| `CREATE TABLE`, `CREATE VIEW` | `create` sul database |
| `DROP DATABASE` | `drop_database` sul database |
| `CREATE DATABASE` | `create_database` sul catalogo |

## Distribuzione

1. Aggiungete un servizio dati `doris` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, selezionate il servizio ed emettete un token di agente. È mostrato una sola volta; conservatelo in un file che solo l’account dell’agente può leggere.
2. Create l’utente Doris dell’agente. Crea ruoli e policy di riga, concede ciò che danno le policy e legge i privilegi di ogni utente e il log di audit, il che consente `ADMIN_PRIV`:

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. Per segnalare gli accessi, attivate il log di audit di Doris dove è spento, come in Doris 2.1: `SET GLOBAL enable_audit_plugin = true`. Un `audit_plugin_max_batch_interval_sec` più breve dei suoi 60 secondi predefiniti segnala prima gli accessi.
4. Scrivete il file delle impostazioni, sostituendo indirizzi, percorsi e nomi di esempio:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   Questi percorsi sono sulla macchina dell’agente, e la directory della cache deve essere scrivibile. I file delle password sono letti come UTF-8 e ripuliti dagli spazi.
5. Avviate l’agente, ad esempio come servizio systemd:

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   Sostituite `<version>` con la versione della distribuzione. Le impostazioni passate come `java -Dgrantforge.…=… -jar …` prevalgono sul file. L’agente si ferma in modo pulito con SIGTERM e invia o accoda gli eventi in sospeso; Doris conserva ciò che ha ricevuto.
6. **Portare i privilegi esistenti nelle policy:** i privilegi concessi fuori da GrantForge continuano a valere accanto ai ruoli dell’agente. Create le policy corrispondenti, poi revocate quei privilegi; gli avvisi dell’agente nominano quelli che danno ciò che una policy nega.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per questo agente e una directory scrivibile per lo snapshot firmato, gli eventi accodati e la posizione di lettura del log di audit |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave è ottenuta dal server al primo contatto |
| `grantforge.doris.hosts`, `grantforge.doris.user` | Obbligatorie: gli indirizzi del protocollo MySQL dei frontend, provati a turno quando uno non è raggiungibile, la porta `9030` se non indicata, e l’utente Doris dell’agente |
| `grantforge.doris.password.file` | La password dell’utente; se omessa, l’agente accede senza |
| `grantforge.doris.ssl.mode`, `grantforge.doris.tls.ca` | `disable` (predefinito), `trust`, `verify-ca` o `verify-full`, per frontend che offrono TLS; e il certificato CA PEM che li verifica, vuoto per ciò di cui si fida Java |
| `grantforge.doris.timeout.ms` | Quanto possono durare la connessione e un’istruzione, predefinito `30000`, intervallo `1..600000` millisecondi |
| `grantforge.doris.role.prefix` | L’inizio dei nomi dei ruoli dell’agente, predefinito `grantforge_`; lettere, cifre e trattini bassi, termina con un trattino basso, al massimo 32 caratteri. Appartengono all’agente ogni ruolo e policy di riga il cui nome inizia così |
| `grantforge.doris.unmanaged.users` | Utenti che l’agente lascia stare, separati da virgole, come gli account di servizio; lascia sempre stare il proprio utente, `root`, `admin` e gli utenti con `ADMIN_PRIV` o `NODE_PRIV` |
| `grantforge.doris.catalogs` | I cataloghi in cui l’agente concede per database, tabella e colonna, separati da virgole; vuoto per tutti. Gli altri ricevono solo ciò che si può concedere su di essi interi, così un catalogo esterno con molte tabelle non va elencato a ogni sincronizzazione |
| `grantforge.sync.interval.ms` | Ogni quanto l’agente allinea Doris, predefinito `10000`, intervallo `1000..3600000` millisecondi |
| `grantforge.audit.log` | Predefinito `true`; `false` non legge alcun log di audit e non segnala accessi |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Quanto indietro rispetto al presente si legge il log, per impostazione predefinita l’intervallo di batch del cluster più cinque secondi; ogni quanto, predefinito `5000` millisecondi; e il massimo di voci per lettura, predefinito `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` invia ciascuno |

## Utenti, gruppi e ruoli

Le policy nominano gli utenti di Doris con i loro nomi utente; un nome utente con più host è un solo utente, e il ruolo dell’agente va a ognuna delle sue identità. I gruppi sono i ruoli Doris di un utente insieme ai gruppi in cui GrantForge lo mette; i ruoli sono quelli che GrantForge dà all’utente.

## Decisioni e limiti

- Senza snapshot l’agente non cambia nulla. Un servizio non in uso, o un token legato a un servizio il cui tipo non è `doris`, non concede nulla: l’agente elimina i suoi ruoli e le sue policy di riga, e valgono i privilegi di Doris stesso.
- Finché l’agente non gira, Doris conserva ciò che ha ricevuto per ultimo; le modifiche delle policy lo attendono. Una modifica raggiunge Doris entro l’intervallo di aggiornamento e quello di sincronizzazione.
- Le tabelle create in un database in cui l’agente concede tabella per tabella, e i database di un catalogo in cui concede database per database, ricevono i loro privilegi alla sincronizzazione successiva.
- Le policy di riga di Doris non possono vincolare `root` e `admin`. Un utente per cui Doris non mostra alcuna policy di riga vede ogni riga di una tabella; le policy di riga permissive create fuori da GrantForge per lo stesso utente allargano ciò che il suo filtro mostra.
- Doris verifica `USAGE_PRIV` su un workload group quando una query gira sui backend; una query a cui il frontend risponde da sé, come una costante o un risultato in cache, gira senza la verifica. Ogni utente può usare il gruppo `normal`, poiché Doris lo dà a tutti.
- Gli utenti per cui l’agente tiene i ruoli devono esistere in Doris; gli utenti che accedono tramite LDAP senza un proprio utente Doris non ricevono alcun ruolo.
- Doris maschera i valori solo tramite Apache Ranger, quindi le policy non possono mascherare valori.

## Guide correlate

- [Apache Doris](/it/plugins/doris/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
