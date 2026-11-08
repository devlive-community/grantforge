---
title: Agente MongoDB
description: Eseguire l’agente MongoDB accanto a un deployment per trasformare le policy di GrantForge nei ruoli di MongoDB stesso, sostituire i privilegi di un ruolo in un solo passo, leggere cosa danno i ruoli integrati e personalizzati fuori da GrantForge, e segnalare gli accessi dal log di audit o dal profiler.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente MongoDB è un servizio accanto al deployment. Mantiene il controllo degli accessi di MongoDB allineato alle policy firmate di un servizio dati `mongodb`: un ruolo per utente che contiene esattamente ciò che le policy gli danno. MongoDB verifica poi ogni comando da sé, e l’agente non si frappone a nessun client. La distribuzione lo fornisce come JAR eseguibile `agents/mongodb/grantforge-agent-mongodb-<version>.jar`; richiede Java 17 o successivo.

I test di integrazione Testcontainers del repository eseguono l’agente, e il JAR così come viene distribuito, su replica set di MongoDB `6.0`, `7.0`, `8.0` e `9.0` e un cluster partizionato `9.0`, e leggono il log di audit di Percona Server for MongoDB `7.0` e `8.0`. Questo non certifica ogni altra versione; l’agente rifiuta deployment precedenti a `6.0`. Per il plug-in di servizio vedete [MongoDB](/it/plugins/mongodb/).

## Funzionamento

- **Un ruolo per utente contiene ciò che dà GrantForge.** Per ogni utente a cui le policy danno qualcosa, l’agente mantiene in `admin` un ruolo col suo nome, `grantforge_alice` per `alice`, e lo concede a ogni utente MongoDB con quel nome, come `alice@admin` e `alice@sales`. `rolesInfo` mostra ciò che GrantForge dà ad `alice`. L’agente non cambia nessun altro ruolo.
- **Una modifica avviene in un solo passo.** `updateRole` sostituisce in una volta i privilegi di un ruolo, così un utente non ha mai insieme i vecchi e i nuovi, né nessuno dei due.
- **Interi database dove nulla al di sotto decide altrimenti.** Un tipo di accesso che una policy permette su tutto un database, senza policy che possano toglierlo al di sotto, viene concesso sul database, così le collezioni create in seguito lo hanno subito. Altrimenti viene concesso collezione per collezione, e le nuove sono coperte alla sincronizzazione successiva.
- **Mai più di quanto le policy permettono.** Il privilegio di MongoDB di eliminare un database è il privilegio di eliminare ogni sua collezione. L’agente lo concede solo dove le policy danno anche quello; altrimenti lo trattiene e registra una volta quale utente non può fare cosa e perché.
- **Le policy del momento presente.** Ogni sincronizzazione pianifica secondo le policy come si applicano in quel momento, così i periodi di validità iniziano e finiscono in orario, e secondo gli utenti, i database e le collezioni che MongoDB ha, così quelli nuovi sono coperti alla sincronizzazione successiva.
- **I ruoli MongoDB sono gruppi.** Una policy per un gruppo si applica agli utenti che hanno un ruolo MongoDB con quel nome, insieme ai gruppi in cui GrantForge li mette.
- **Le modifiche manuali vengono annullate.** A ogni sincronizzazione l’agente confronta i suoi ruoli con i piani e corregge privilegi cambiati a mano, ruoli fatti ereditare, concessioni di un ruolo ad altri e ruoli che non servono a nessuno. Se nulla è cambiato, non invia nulla.
- **Dei ruoli fuori da GrantForge si legge cosa danno.** MongoDB somma ciò che danno tutti i ruoli di un utente, quindi l’agente non può togliere ciò che un utente ha tramite un altro ruolo. MongoDB dice cosa dà ogni ruolo, anche quelli integrati ed ereditati, quindi l’agente avvisa, una volta ciascuno, di un ruolo come `readWrite@hr` che dà ciò che una policy nega, nominando utente, accesso e policy. Avvisa anche degli utenti che possono amministrare gli utenti, e così darsi qualsiasi cosa, degli utenti con `root` o ogni azione su ogni risorsa, e di un server che non verifica alcuna autorizzazione.
- **Gli accessi vengono dal log di audit o dal profiler.** Vedete Segnalare gli accessi più sotto. Ogni evento dice se ha deciso GrantForge, cioè se le policy decidono come MongoDB, o se un ruolo esterno ha permesso il comando. Un’aggregazione viene segnalata per ogni collezione che le sue fasi `$lookup`, `$graphLookup` e `$unionWith` leggono e `$out` e `$merge` scrivono. Gli eventi nominano il comando e il suo namespace, mai i suoi argomenti, che possono contenere documenti e password.

## Distribuzione

1. Aggiungete un servizio dati `mongodb` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, selezionate il servizio ed emettete un token di agente. È mostrato una sola volta; conservatelo in un file che solo l’account dell’agente può leggere.
2. Create l’utente MongoDB dell’agente. Crea e cambia ruoli e legge utenti e ruoli con `userAdminAnyDatabase`; elenca le collezioni con un ruolo che contiene solo questo; e legge le opzioni del server e il profiler con `clusterMonitor`. `userAdminAnyDatabase` può darsi qualsiasi cosa, quindi proteggete questo utente come un amministratore. Il nome del ruolo di elenco non deve iniziare con il prefisso dei ruoli dell’agente.

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. Per segnalare gli accessi, configurate una delle fonti di Segnalare gli accessi più sotto.
4. Scrivete il file delle impostazioni, sostituendo indirizzi, percorsi e nomi di esempio:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   Questi percorsi sono sulla macchina dell’agente, e la directory della cache deve essere scrivibile. I file delle password sono letti come UTF-8 e ripuliti dagli spazi.
5. Avviate l’agente, ad esempio come servizio systemd:

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   Sostituite `<version>` con la versione della distribuzione. Le impostazioni passate come `java -Dgrantforge.…=… -jar …` prevalgono sul file. L’agente si ferma in modo pulito con SIGTERM e invia o accoda gli eventi in sospeso; MongoDB conserva ciò che ha ricevuto.
6. **Portare i privilegi esistenti nelle policy:** i ruoli concessi fuori da GrantForge continuano ad applicarsi accanto a quelli dell’agente. Create le policy corrispondenti, poi revocate quei ruoli. Gli avvisi dell’agente indicano quelli che danno ciò che una policy nega.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per questo agente e una directory scrivibile per lo snapshot firmato, gli eventi accodati e la posizione di lettura del log di audit |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave è ottenuta dal server al primo contatto |
| `grantforge.mongodb.uri` | Obbligatoria: la stringa di connessione, senza credenziali |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256` (predefinito), `SCRAM-SHA-1`, `MONGODB-X509`, `PLAIN` o `GSSAPI` |
| `grantforge.mongodb.user`, `grantforge.mongodb.password.file`, `grantforge.mongodb.auth.source` | L’utente dell’agente, con `GSSAPI` il suo principal e con `MONGODB-X509` facoltativo; la sua password; e per SCRAM il database in cui è definito, `admin` per impostazione predefinita |
| `grantforge.mongodb.tls.ca.file`, `grantforge.mongodb.tls.certificate.key.file`, `grantforge.mongodb.tls.certificate.key.password.file` | Con `tls=true` nella stringa di connessione: i certificati CA PEM che verificano i server, vuoto per ciò di cui Java si fida; un file PEM con il certificato client e la sua chiave; e la password di una chiave cifrata |
| `grantforge.mongodb.kerberos.keytab`, `grantforge.mongodb.kerberos.service.name` | `GSSAPI`: il keytab del principal, altrimenti si usa la password, e il nome di servizio del principal dei server, `mongodb` per impostazione predefinita. Il realm viene dal `krb5.conf` della macchina |
| `grantforge.mongodb.timeout.ms` | Quanto possono durare la ricerca di un server, la connessione e un comando, `30000` per impostazione predefinita, nell’intervallo `1..600000` millisecondi |
| `grantforge.mongodb.role.prefix` | Come iniziano i nomi dei ruoli dell’agente, `grantforge_` per impostazione predefinita. Lettere, cifre e trattini bassi, con un trattino basso finale, al massimo 32 caratteri. Ogni ruolo di `admin` il cui nome inizia così appartiene all’agente |
| `grantforge.mongodb.unmanaged.users` | Gli utenti che l’agente lascia stare, separati da virgole, come gli account di servizio. Lascia sempre stare il proprio utente e gli utenti con `root`, `__system` o ogni azione su ogni risorsa |
| `grantforge.mongodb.databases` | I database in cui l’agente concede per collezione, separati da virgole, vuoto per tutti. Gli altri ricevono solo ciò che si può concedere nel complesso, così un database con moltissime collezioni non viene elencato a ogni sincronizzazione |
| `grantforge.mongodb.audit.files` | I file del log di audit da leggere, separati da virgole. Vuoto legge il profiler |
| `grantforge.sync.interval.ms` | Ogni quanto l’agente allinea MongoDB, predefinito `10000`, intervallo `1000..3600000` millisecondi |
| `grantforge.audit.log` | `true` per impostazione predefinita. `false` non legge né segnala accessi |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Di quanto indietro rispetto al presente si legge il profiler (`2000` per impostazione predefinita), ogni quanto si leggono gli accessi (`5000` millisecondi per impostazione predefinita) e il massimo di voci che una lettura prende da un file o database (`1000` per impostazione predefinita) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` invia ciascuno |

## Segnalare gli accessi

| Fonte | Dove | Cosa dice | Come attivarla |
| --- | --- | --- | --- |
| Log di audit | MongoDB Enterprise e Percona Server for MongoDB | Cosa è stato permesso e cosa rifiutato, su ogni membro di cui l’agente può leggere il file | Avviate `mongod` con `--auditDestination file --auditFormat JSON --auditPath <path>` e `--setParameter auditAuthorizationSuccess=true`, e nominate i file in `grantforge.mongodb.audit.files` |
| Profiler | Ogni edizione | Cosa è stato eseguito sul membro con cui parla l’agente, il primario di un replica set; nessun rifiuto | `db.setProfilingLevel(2)` in ogni database da segnalare. Ha un costo per il server, quindi l’agente non lo attiva mai, e `system.profile` è una piccola collezione limitata |

Il log di audit viene letto da file, quindi eseguite l’agente sul nodo o dategli una directory che li raccoglie. I test leggono il log di audit di Percona Server, che ha il formato di MongoDB Enterprise. Un `mongos` non ha profiler, quindi un cluster partizionato segnala gli accessi solo dal log di audit.

| Comando | Evento di accesso |
| --- | --- |
| `find`, `aggregate`, `count`, `distinct`, `listIndexes` | `read` su ogni collezione letta |
| `aggregate` che inizia con `$changeStream` | `change_stream` |
| `insert`, `update`, `delete`, `findAndModify`, `bulkWrite` | `insert`, `update` o `remove` su ogni collezione scritta; anche `bypass_validation` se richiesto |
| `createIndexes`, `dropIndexes` | `create_index`, `drop_index` |
| `collMod`, `validate`, `convertToCapped` | `alter` |
| `drop`, `create`, `dropDatabase` | `drop`, `create`, `drop_database` |
| `listDatabases`; `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `currentOp` | `list_databases`; `monitor` |

MongoDB rifiuta un comando nel suo insieme senza dire quale delle sue collezioni sia stata la causa, quindi un rifiuto viene segnalato per quelle che le policy negano, o per la prima se non ne negano nessuna.

## Utenti, gruppi e ruoli

Le policy nominano gli utenti MongoDB per nome utente. Un nome definito in più database è un solo utente, e il ruolo dell’agente viene concesso a tutti. I gruppi sono i ruoli MongoDB di un utente e i gruppi in cui GrantForge lo mette. I ruoli sono quelli che GrantForge dà all’utente.

## Decisioni e limiti

- Senza snapshot l’agente non cambia nulla. Un token legato a un servizio non in uso, o a un servizio il cui tipo non è `mongodb`, non concede nulla: l’agente elimina i suoi ruoli, e quelli di MongoDB restano.
- Finché l’agente non è in esecuzione, MongoDB conserva ciò che ha ricevuto per ultimo e le modifiche alle policy attendono. Una modifica raggiunge MongoDB entro gli intervalli di aggiornamento e sincronizzazione; un `mongos` la applica quando scade la sua cache degli utenti, per impostazione predefinita entro 30 secondi (`userCacheInvalidationIntervalSecs`).
- I privilegi di MongoDB nominano esattamente database e collezioni, quindi un carattere jolly in una policy viene concesso per ciò che esiste. Un database esiste quando ha una collezione: fino ad allora, `create` su un database nominato da una policy non ha nulla su cui essere concesso.
- Gli utenti non definiti da nessuna parte in MongoDB, come quelli che l’autorizzazione LDAP associa da sé ai ruoli, non vengono visti, e l’agente non mantiene ruoli per loro.
- Un privilegio su un intero database copre le sue collezioni ma non le collezioni `system.`, e i database di MongoDB stesso restano a MongoDB.
- Rinominare collezioni e le altre azioni amministrative di MongoDB non sono tipi di accesso: restano ai ruoli fuori da GrantForge.
- MongoDB non filtra righe né maschera campi per ruolo; una [vista](/it/plugins/mongodb/) dà una lettura ristretta.

## Guide correlate

- [MongoDB](/it/plugins/mongodb/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
