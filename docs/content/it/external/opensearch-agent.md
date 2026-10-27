---
title: Agente OpenSearch
description: Eseguire l’agente OpenSearch accanto al cluster, perché le policy di GrantForge diventino i ruoli propri del plug-in di sicurezza, con sicurezza a livello di campo, campi mascherati e sicurezza a livello di documento, i pattern di indice restino pattern così che i nuovi indici siano subito raggiungibili, e gli accessi siano segnalati dal log di audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente OpenSearch è un servizio accanto al cluster. Mantiene il controllo degli accessi proprio del plug-in di sicurezza allineato con le policy firmate di un servizio dati `opensearch`: un ruolo per utente che contiene esattamente ciò che le policy gli danno, associato a quel solo utente, con sicurezza a livello di campo, campi mascherati e sicurezza a livello di documento. OpenSearch verifica poi da sé ogni richiesta, e l’agente non si mette mai tra i client e il cluster. La distribuzione lo fornisce come JAR eseguibile, `agents/opensearch/grantforge-agent-opensearch-<version>.jar`, che richiede Java 17 o successivo.

I test di integrazione Testcontainers del repository eseguono l’agente, e il JAR così come distribuito, contro OpenSearch `2.19`, `3.3`, `3.6` e `3.9` con il plug-in di sicurezza. Ciò non certifica tutte le altre versioni; l’agente rifiuta cluster precedenti a `2.19` e cluster senza plug-in di sicurezza. Per il plug-in di servizio vedete [OpenSearch](/it/plugins/opensearch/).

## Funzionamento

- **Un ruolo per utente contiene ciò che GrantForge dà.** Per ogni utente a cui le policy danno qualcosa, l’agente tiene un ruolo con il suo nome, `grantforge_alice` per `alice`, associato a quel solo utente. La sua descrizione lo marca come dell’agente, e l’agente non prende alcun ruolo senza quel marchio per uno dei suoi, così non modifica nessun altro ruolo o associazione.
- **I pattern di indice restano pattern.** Un pattern di indice di una policy, come `logs-*`, è concesso come pattern dovunque tutte le policy decidono allo stesso modo su tutta la sua estensione: ogni policy di qualsiasi tipo i cui indici si sovrappongono al pattern lo copre per intero. L’agente lo decide esattamente, con automi finiti dei pattern. Gli indici creati dopo, come i log di domani, sono allora subito raggiungibili, anche quando il primo documento scritto li crea. Dove una policy decide diversamente dentro un pattern, come un diniego di `logs-secret` dentro `logs-*`, l’agente concede per nome ogni indice e flusso di dati del cluster, e quelli nuovi alla sincronizzazione successiva.
- **Sicurezza a livello di campo solo dove qualcosa sotto è deciso diversamente.** Un indice che l’utente può leggere per intero, senza policy che possa togliergli un campo, è letto senza sicurezza a livello di campo, così i campi aggiunti in seguito sono letti subito. Altrimenti l’utente legge i campi che una policy consente, tra quelli che i suoi documenti hanno e quelli che le policy nominano; i multi-campi come `name.keyword` seguono il loro campo.
- **Maschere e filtri delle righe sono quelli del plug-in di sicurezza.** Una maschera diventa un campo mascherato della voce, e un filtro delle righe la sua query di sicurezza a livello di documento. L’API validate di OpenSearch verifica ogni query prima di concederla; una query che OpenSearch non eseguirebbe non mostra alcun documento all’utente, e viene registrata. Una maschera che il plug-in di sicurezza non conosce nasconde invece il campo.
- **Una voce per insieme di permessi.** Indici e pattern con gli stessi permessi condividono una voce; le voci non si sovrappongono mai, poiché il plug-in di sicurezza combina la sicurezza a livello di campo e di documento delle voci sovrapposte.
- **Le operazioni composte del cluster arrivano con lettura e scrittura.** La lettura porta `cluster_composite_ops_ro`, per multi-search, multi-get e scorrimento, e la scrittura `cluster_composite_ops`, per bulk.
- **Le modifiche sono una patch.** L’agente scrive ruoli e associazioni come patch JSON, che il plug-in di sicurezza applica tutte insieme in tutto il cluster: i ruoli prima delle associazioni che li nominano, le associazioni prima dei ruoli che nominano e che vengono rimossi. Una patch che il plug-in di sicurezza rifiuta è applicata modifica per modifica, così un ruolo difettoso fallisce da solo.
- **Le policy del momento presente.** Ogni sincronizzazione pianifica secondo le policy come si applicano in quel momento, così i periodi di validità iniziano e finiscono in orario.
- **I ruoli di backend sono gruppi.** Una policy per un gruppo vale per gli utenti interni che hanno un ruolo di backend con quel nome, insieme ai gruppi in cui GrantForge mette gli utenti.
- **Le modifiche fatte a mano vengono annullate.** L’agente confronta a ogni sincronizzazione i suoi ruoli e associazioni con ciò che conserva il plug-in di sicurezza; ciò che differisce viene riscritto. Quando nulla è cambiato, l’agente non invia nulla.
- **I ruoli fuori da GrantForge vengono segnalati.** OpenSearch concede ciò che dà uno qualsiasi dei ruoli di un utente, quindi l’agente non può togliere ciò che un altro ruolo associato all’utente dà. Avvisa una volta di ogni ruolo di questo tipo che dà ciò che una policy nega, nominando ruolo, utente, accesso, indice e policy, e degli utenti associati a `all_access`, che nessun ruolo vincola.
- **Gli accessi vengono dal log di audit.** L’agente legge il log di audit del plug-in di sicurezza dagli indici in cui lo scrive, `security-auditlog-*`: ciò che ha rifiutato, come `MISSING_PRIVILEGES`, e ciò che ha consentito, come `GRANTED_PRIVILEGES` quando la configurazione di audit li registra. Ogni evento nomina l’utente, il tipo di accesso, ogni indice o flusso di dati raggiunto, o il cluster, e dice se lo ha deciso GrantForge o se lo ha consentito un ruolo fuori da GrantForge.

| Azione | Evento di accesso |
| --- | --- |
| `indices:data/read/*`: ricerca, lettura, conteggio | `read` su ogni indice o flusso di dati |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/aliases*`, `indices:monitor/*` | `manage_aliases`, `monitor` |
| Altri `indices:admin/*` | `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` e `repository/*` | `cluster_monitor`, `manage_snapshots` sul cluster |
| Modelli di indice e di componenti, pipeline di ingestione | `manage_templates`, `manage_pipelines` sul cluster |

## Distribuzione

1. Aggiungete un servizio dati `opensearch` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, selezionate il servizio ed emettete un token di agente. È mostrato una sola volta; conservatelo in un file che solo l’account dell’agente può leggere.
2. Date all’agente un utente che possa modificare la configurazione del plug-in di sicurezza: un utente associato a `all_access` finché `plugins.security.restapi.roles_enabled` lo elenca, oppure il certificato di amministrazione indicato da `plugins.security.authcz.admin_dn`. I test eseguono l’agente come `admin` della configurazione di dimostrazione, e con il suo certificato di amministrazione.
3. Per segnalare gli accessi, tenete il log di audit nel cluster (`plugins.security.audit.type: internal_opensearch`). Per ciò che OpenSearch ha consentito, togliete anche `GRANTED_PRIVILEGES` dalle categorie disattivate della configurazione di audit; i dinieghi sono registrati per impostazione predefinita.
4. Scrivete il file delle impostazioni, sostituendo indirizzi, percorsi e nomi di esempio:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   Questi percorsi sono sulla macchina dell’agente, e la directory della cache deve essere scrivibile. I file delle password sono letti come UTF-8 e ripuliti dagli spazi.
5. Avviate l’agente, ad esempio come servizio systemd:

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   Sostituite `<version>` con la versione della distribuzione. Le impostazioni passate come `java -Dgrantforge.…=… -jar …` prevalgono sul file. L’agente si ferma in modo pulito con SIGTERM e invia o accoda gli eventi in sospeso; OpenSearch conserva ciò che ha ricevuto.
6. **Portare i ruoli esistenti nelle policy:** i ruoli associati agli utenti fuori da GrantForge continuano a valere accanto a quelli dell’agente. Create le policy corrispondenti, poi rimuovete quelle associazioni; gli avvisi dell’agente nominano i ruoli che danno ciò che una policy nega.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per questo agente e una directory scrivibile per lo snapshot firmato, gli eventi accodati e la posizione di lettura del log di audit |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave è ottenuta dal server al primo contatto |
| `grantforge.opensearch.hosts` | Obbligatoria: i nodi come URL, provati a turno quando uno non è raggiungibile; senza schema un nodo è `https`, e la porta predefinita è `9200` |
| `grantforge.opensearch.user`, `grantforge.opensearch.password.file` | L’utente dell’agente e il file della sua password |
| `grantforge.opensearch.tls.cert`, `grantforge.opensearch.tls.key` | In alternativa: certificato client PEM e la sua chiave privata, insieme, per accedere con un certificato; `grantforge.opensearch.tls.key.passphrase.file` contiene la passphrase di una chiave cifrata |
| `grantforge.opensearch.ssl.verification.mode` | `full` (predefinito) verifica certificati e nomi dei nodi, `certificate` solo i certificati, `none` niente |
| `grantforge.opensearch.tls.ca` | Certificati CA PEM che verificano i nodi; vuoto si fida di ciò di cui si fida Java |
| `grantforge.opensearch.timeout.ms` | Quanto possono durare la connessione e una richiesta, predefinito `30000`, intervallo `1..600000` millisecondi |
| `grantforge.opensearch.role.prefix` | L’inizio dei nomi dei ruoli dell’agente, predefinito `grantforge_`; lettere minuscole, cifre, trattini e trattini bassi, termina con un trattino basso, al massimo 32 caratteri. Appartengono all’agente i ruoli da lui marcati il cui nome inizia così |
| `grantforge.opensearch.unmanaged.users` | Utenti che l’agente lascia stare, separati da virgole, come gli account di servizio; lascia sempre stare il proprio utente e gli utenti associati a `all_access` |
| `grantforge.sync.interval.ms` | Ogni quanto l’agente allinea OpenSearch, predefinito `10000`, intervallo `1000..3600000` millisecondi |
| `grantforge.audit.log` | Predefinito `true`; `false` non legge alcun log di audit e non segnala accessi |
| `grantforge.audit.log.index`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.documents` | Gli indici del log di audit, predefinito `security-auditlog-*`; ogni quanto viene letto, predefinito `5000` millisecondi; e il massimo di documenti per lettura, predefinito `1000`, così un arretrato è letto in più giri |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` invia ciascuno |

## Utenti, gruppi e ruoli

Le policy nominano gli utenti di OpenSearch come li conosce il plug-in di sicurezza. L’agente tiene ruoli per gli utenti interni, per gli utenti che le policy nominano, che possono accedere tramite una directory come LDAP o SAML, e per gli utenti per cui teneva già ruoli; gli utenti riservati e nascosti del plug-in di sicurezza, come `kibanaserver`, sono lasciati stare. I gruppi sono i ruoli di backend di un utente interno insieme ai gruppi in cui GrantForge mette l’utente; l’agente non conosce i ruoli di backend che una directory dà all’accesso, quindi date a questi utenti i loro gruppi in GrantForge. I ruoli sono quelli che GrantForge dà all’utente.

## Decisioni e limiti

- Senza snapshot l’agente non cambia nulla. Un servizio non in uso, o un token legato a un servizio il cui tipo non è `opensearch`, non concede nulla: l’agente rimuove i suoi ruoli e associazioni, e valgono i ruoli propri del plug-in di sicurezza.
- Finché l’agente non gira, OpenSearch conserva ciò che ha ricevuto per ultimo; le modifiche delle policy lo attendono. Una modifica raggiunge OpenSearch entro l’intervallo di aggiornamento e quello di sincronizzazione.
- Gli indici concessi per nome, e non tramite un pattern, ricevono i loro permessi alla sincronizzazione successiva.
- Gli alias raggiungono gli indici dietro di essi: cercare in un alias richiede permessi su ogni indice che contiene, quindi le policy nominano indici e flussi di dati, non alias.
- Un filtro delle righe può verificare solo campi che l’utente può leggere, e una maschera si applica ai valori dei campi text e keyword.
- Gli indici il cui nome inizia con un punto si raggiungono solo con pattern di policy che iniziano con un punto.
- L’agente non gestisce i tenant del plug-in di sicurezza per OpenSearch Dashboards.

## Guide correlate

- [OpenSearch](/it/plugins/opensearch/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
