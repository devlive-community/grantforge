---
title: Agente Elasticsearch
description: Eseguire l’agente Elasticsearch accanto al cluster, così che le policy GrantForge diventino i ruoli propri di Elasticsearch, dati agli utenti nativi e associati agli utenti degli altri realm, con sicurezza a livello di campo e di documento dove la licenza le offre, che i pattern di indice restino pattern perché i nuovi indici siano subito raggiungibili, e che gli accessi siano segnalati dai file del log di audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente Elasticsearch è un servizio accanto al cluster. Mantiene il controllo degli accessi proprio di Elasticsearch allineato con le policy firmate di un servizio dati `elasticsearch`: un ruolo per utente che contiene esattamente ciò che le policy gli danno, con sicurezza a livello di campo e di documento dove la licenza del cluster le offre. Un utente nativo ha il ruolo tra i propri ruoli; un utente di un altro realm, come LDAP, SAML o OpenID Connect, lo riceve tramite un’associazione di ruoli del suo nome. Elasticsearch verifica poi da sé ogni richiesta, e l’agente non si mette mai tra i client e il cluster. La distribuzione lo fornisce come JAR eseguibile, `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`, che richiede Java 17 o successivo.

I test di integrazione Testcontainers del repository eseguono l’agente, e il JAR così come distribuito, contro Elasticsearch `8.19`, `9.3`, `9.4` e `9.5` con la sicurezza attiva. Ciò non certifica tutte le altre versioni; l’agente rifiuta cluster precedenti a `8.19`, cluster senza sicurezza e cluster OpenSearch, che tiene l’[agente OpenSearch](/it/external/opensearch-agent/). Per il plug-in di servizio vedete [Elasticsearch](/it/plugins/elasticsearch/).

## Funzionamento

- **Un ruolo per utente contiene ciò che GrantForge dà.** Per ogni utente a cui le policy danno qualcosa, l’agente tiene un ruolo con il suo nome, `grantforge_alice` per `alice`. I suoi metadati nominano l’utente e lo marcano come dell’agente, e l’agente non prende come proprio alcun ruolo senza quel marchio, così non modifica nessun altro ruolo.
- **Gli utenti nativi hanno il ruolo; gli altri realm lo ricevono per nome.** L’agente aggiunge il ruolo ai ruoli di un utente nativo e mantiene tutti gli altri suoi ruoli. Le associazioni di ruoli valgono per tutti i realm tranne il nativo e quello dei file, quindi l’agente tiene anche un’associazione di ruoli con lo stesso nome che dà il ruolo al nome dell’utente, per gli utenti che accedono tramite LDAP, Active Directory, SAML, OpenID Connect, JWT, Kerberos o PKI. Il realm dei file non si può modificare tramite l’API di Elasticsearch; i suoi utenti non ricevono alcun ruolo.
- **I pattern di indice restano pattern.** Un pattern di indice di una policy, come `logs-*`, è concesso come pattern dovunque tutte le policy decidono allo stesso modo su tutta la sua estensione: ogni policy di qualsiasi tipo i cui indici si sovrappongono al pattern lo copre per intero. L’agente lo decide esattamente, con automi finiti dei pattern. Gli indici creati in seguito, come i log di domani, sono allora subito raggiungibili, anche quando il primo documento scritto li crea. Dove una policy decide diversamente all’interno di un pattern, come un diniego di `logs-secret` dentro `logs-*`, l’agente concede ogni indice e flusso di dati del cluster per nome, e quelli nuovi alla sincronizzazione successiva.
- **Sicurezza a livello di campo solo dove qualcosa sotto è deciso diversamente.** Un indice che l’utente può leggere per intero, senza policy che possa togliergli un campo, è letto senza sicurezza a livello di campo, così i campi aggiunti in seguito sono letti subito. Altrimenti l’utente legge i campi che una policy consente, tra quelli che i suoi documenti hanno e quelli che le policy nominano, ciascuno con i suoi multi-campi, come `name.keyword` con `name`.
- **I filtri delle righe sono sicurezza a livello di documento.** Un filtro delle righe diventa la query della voce. L’API validate di Elasticsearch verifica ogni query prima che sia concessa; una query che Elasticsearch non eseguirebbe non mostra all’utente alcun documento, e viene registrata.
- **Mai più di quanto le policy consentano sotto Platinum.** Elasticsearch offre la sicurezza a livello di documento e di campo a partire da una licenza Platinum, e al di sotto disattiva ogni ruolo che la usa. L’agente legge la licenza a ogni sincronizzazione. Sotto Platinum, una lettura che ha bisogno dell’una o dell’altra, perché una policy restringe campi o documenti dell’indice, non viene concessa affatto, e l’agente registra quale utente non può leggere quale indice e perché; la lettura di indici interi e tutto il resto sono concessi come al solito.
- **Una voce per insieme di privilegi.** Indici e pattern con gli stessi privilegi condividono una voce; le voci non si sovrappongono mai, poiché Elasticsearch combina la sicurezza a livello di campo e di documento delle voci sovrapposte.
- **Le policy del momento presente.** Ogni sincronizzazione pianifica secondo le policy come si applicano in quel momento, così i periodi di validità iniziano e finiscono in orario.
- **I ruoli degli utenti nativi sono gruppi.** Una policy per un gruppo vale per gli utenti nativi che hanno un ruolo con quel nome, insieme ai gruppi in cui GrantForge mette gli utenti.
- **Le modifiche fatte a mano vengono annullate.** L’agente confronta a ogni sincronizzazione i suoi ruoli, le sue associazioni di ruoli e i ruoli degli utenti nativi con ciò che conserva Elasticsearch; ciò che differisce viene riscritto, e il ruolo di un utente che ha un altro gli viene tolto. I ruoli sono scritti prima di essere dati, in una sola richiesta, e tolti prima di essere eliminati. Quando nulla è cambiato, l’agente non invia nulla; una modifica che Elasticsearch rifiuta viene registrata e non ferma le altre.
- **I ruoli fuori da GrantForge vengono segnalati.** Elasticsearch concede ciò che dà uno qualsiasi dei ruoli di un utente, quindi l’agente non può togliere ciò che un altro ruolo dà. Avvisa una volta di ogni ruolo di questo tipo, che un utente nativo ha o che un’associazione di ruoli del suo nome dà, che dà ciò che una policy nega, nominando il ruolo, l’utente, l’accesso, l’indice e la policy, e degli utenti con il ruolo `superuser`, che nessun ruolo limita.
- **Gli accessi vengono dai file del log di audit.** Elasticsearch scrive il suo log di audit solo in un file su ogni nodo. L’agente legge i file che gli vengono indicati, da dove è finita l’ultima lettura, li segue quando Elasticsearch li ruota e segnala gli eventi `access_granted` e `access_denied`. Ciascuno nomina l’utente, il tipo di accesso, ogni indice o flusso di dati raggiunto, o il cluster, e dice se l’ha deciso GrantForge o se l’ha consentito un ruolo fuori da GrantForge.

| Azione | Evento di accesso |
| --- | --- |
| `indices:data/read/*`: ricerca, lettura, conteggio | `read` su ogni indice o flusso di dati |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/delete`, `data_stream/delete` | `delete_index` |
| `indices:admin/get`, `mappings/get`, `settings/get`, `resolve/*` | `view_index_metadata` |
| `indices:monitor/*`; altri `indices:admin/*` | `monitor`; `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` e `repository/*`, `slm/*` | `cluster_monitor`, `create_snapshot`, `manage_slm` sul cluster |
| Modelli di indice e di componenti, pipeline di ingestione, `cluster:admin/ilm/*` | `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` sul cluster |

## Distribuzione

1. Aggiungete un servizio dati `elasticsearch` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, selezionate il servizio ed emettete un token di agente. È mostrato una sola volta; conservatelo in un file che solo l’account dell’agente può leggere.
2. Date all’agente un utente con il privilegio di cluster `manage_security`, che gestisce ruoli, associazioni di ruoli e utenti, e il diritto di leggere i mapping di ogni indice e di validare query, come un utente con il ruolo `superuser`. Può accedere con una password, una chiave API creata da quell’utente o un certificato client che un realm PKI gli associa.
3. Per segnalare gli accessi, attivate il log di audit (`xpack.security.audit.enabled: true`), che richiede una licenza Gold o superiore, e lasciate che l’agente legga i file di audit dei nodi, `<nome del cluster>_audit.json` nella directory dei log di ogni nodo: eseguite l’agente su un nodo, o raccogliete i file in una directory che può leggere. `xpack.security.audit.logfile.events.include: access_granted, access_denied` limita il log a ciò che l’agente segnala.
4. Scrivete il file delle impostazioni, sostituendo indirizzi, percorsi e nomi di esempio:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   Questi percorsi sono sulla macchina dell’agente, e la directory della cache deve essere scrivibile. I file delle password e delle chiavi sono letti come UTF-8 e ripuliti dagli spazi.
5. Avviate l’agente, ad esempio come servizio systemd:

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   Sostituite `<version>` con la versione della distribuzione. Le impostazioni passate come `java -Dgrantforge.…=… -jar …` prevalgono sul file. L’agente si ferma in modo pulito con SIGTERM e invia o accoda gli eventi in sospeso; Elasticsearch conserva ciò che ha ricevuto.
6. **Portare i ruoli esistenti nelle policy:** i ruoli che gli utenti hanno fuori da GrantForge continuano a valere accanto a quelli dell’agente. Create le policy corrispondenti, poi togliete quei ruoli agli utenti; gli avvisi dell’agente nominano i ruoli che danno ciò che una policy nega.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per questo agente e una directory scrivibile per lo snapshot firmato, gli eventi accodati e le posizioni di lettura del log di audit |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave è ottenuta dal server al primo contatto |
| `grantforge.elasticsearch.hosts` | Obbligatoria: i nodi come URL, provati a turno quando uno non è raggiungibile; senza schema un nodo è `https`, e la porta predefinita è `9200` |
| `grantforge.elasticsearch.user`, `grantforge.elasticsearch.password.file` | L’utente dell’agente e il file della sua password |
| `grantforge.elasticsearch.api.key.file` | In alternativa: il file di una chiave API codificata, così come Elasticsearch la restituisce alla creazione |
| `grantforge.elasticsearch.tls.cert`, `grantforge.elasticsearch.tls.key` | In alternativa: certificato client PEM e la sua chiave privata, insieme, per accedere con un certificato; `grantforge.elasticsearch.tls.key.passphrase.file` contiene la passphrase di una chiave cifrata |
| `grantforge.elasticsearch.ssl.verification.mode` | `full` (predefinito) verifica certificati e nomi dei nodi, `certificate` solo i certificati, `none` niente |
| `grantforge.elasticsearch.tls.ca` | Certificati CA PEM che verificano i nodi, come l’`http_ca.crt` di Elasticsearch; vuoto si fida di ciò di cui si fida Java |
| `grantforge.elasticsearch.timeout.ms` | Quanto possono durare la connessione e una richiesta, predefinito `30000`, intervallo `1..600000` millisecondi |
| `grantforge.elasticsearch.role.prefix` | L’inizio dei nomi dei ruoli dell’agente, predefinito `grantforge_`; lettere minuscole, cifre, trattini e trattini bassi, termina con un trattino basso, al massimo 32 caratteri. Appartengono all’agente i ruoli da lui marcati il cui nome inizia così |
| `grantforge.elasticsearch.unmanaged.users` | Utenti che l’agente lascia stare, separati da virgole, come gli account di servizio; lascia sempre stare il proprio utente, gli utenti riservati di Elasticsearch e gli utenti con il ruolo `superuser` |
| `grantforge.sync.interval.ms` | Ogni quanto l’agente allinea Elasticsearch, predefinito `10000`, intervallo `1000..3600000` millisecondi |
| `grantforge.audit.log.files` | I file del log di audit da leggere, separati da virgole; senza, l’agente non legge alcun log di audit e non segnala accessi |
| `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.lines` | Ogni quanto i file vengono letti, predefinito `5000` millisecondi, e il massimo di righe che una lettura prende da ciascuno, predefinito `5000`, così che un arretrato sia letto in più giri |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` invia ciascuno |

## Utenti, gruppi e ruoli

Le policy nominano gli utenti di Elasticsearch con i loro nomi utente. L’agente tiene ruoli per gli utenti nativi, per gli utenti che le policy nominano, che possono accedere tramite un altro realm, e per gli utenti per cui teneva già ruoli; gli utenti riservati di Elasticsearch, come `kibana_system`, vengono lasciati stare. I gruppi sono gli altri ruoli di un utente nativo insieme ai gruppi in cui GrantForge mette l’utente; l’agente non conosce i gruppi che una directory dà all’accesso, quindi date a quegli utenti i loro gruppi in GrantForge. I ruoli sono quelli che GrantForge dà all’utente.

## Decisioni e limiti

- Senza snapshot l’agente non cambia nulla. Un servizio non in uso, o un token legato a un servizio il cui tipo non è `elasticsearch`, non concede nulla: l’agente toglie i suoi ruoli e li elimina con le loro associazioni di ruoli, e valgono i ruoli propri di Elasticsearch.
- Finché l’agente non gira, Elasticsearch conserva ciò che ha ricevuto per ultimo; le modifiche delle policy lo attendono. Una modifica raggiunge Elasticsearch entro l’intervallo di aggiornamento e quello di sincronizzazione.
- Gli indici concessi per nome, e non tramite un pattern, ricevono i loro privilegi alla sincronizzazione successiva.
- Sotto Platinum un utente la cui lettura di un indice è ristretta da una policy non ne legge nulla; sotto Gold l’agente non segnala accessi.
- Un’associazione di ruoli del nome di un utente vale per ogni realm con quel nome utente; le policy nominano utenti, non realm.
- Gli alias raggiungono gli indici dietro di essi, quindi le policy nominano indici e flussi di dati, non alias. Gli indici il cui nome inizia con un punto si raggiungono solo con pattern di policy che iniziano con un punto.
- L’agente non gestisce gli spazi né i privilegi di applicazione di Kibana.

## Guide correlate

- [Elasticsearch](/it/plugins/elasticsearch/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
