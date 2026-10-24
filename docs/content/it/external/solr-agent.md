---
title: Agente Apache Solr
description: Installare il plug-in di autorizzazione nei nodi Solr per decidere con le policy di GrantForge chi può interrogare e aggiornare quali collection, tramite quali percorsi di richiesta, amministrare quali collection e il cluster, e da quali reti.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente Solr è un plug-in di autorizzazione dei nodi: Solr lo interroga su ogni richiesta, e risponde dalle policy firmate del servizio dati `solr`. La distribuzione lo fornisce come un JAR Java 11, `agents/solr/grantforge-agent-solr-<version>.jar`, con le dipendenze incorporate e rilocate.

I test di integrazione Testcontainers del repository eseguono il JAR dentro nodi SolrCloud di Solr `9.4.1`, `9.8.1`, `9.11.0` e `10.0.0`. Ciò non certifica tutte le altre versioni. Il plug-in di servizio è descritto in [Apache Solr](/it/plugins/solr/).

## Funzionamento

- **Le richieste sono classificate come le classifica Solr.** Ogni handler di richiesta nomina il permesso di cui ha bisogno, come `read` o `collection-admin-edit`, come fa per il plug-in basato su regole di Solr; l’agente trasforma quel nome in un tipo di accesso, e un handler che non ne nomina nessuno è deciso in base al tipo di richiesta.
- **Ogni richiesta a una collection è decisa anche sul suo percorso di richiesta.** L’agente decide un’interrogazione di `sales` tramite `/select` sulla collection `sales` e su `sales` con il percorso `/select`: un rifiuto su uno dei due la respinge, così una policy può negare un handler di una collection.
- **I team amministrano le proprie collection.** Una richiesta della Collections API che nomina collection richiede `admin_collection` su ciascuna, così una policy può lasciare che un team crei, ricarichi o elimini solo le collection il cui nome inizia con il suo prefisso.
- **Gli amministratori del cluster possono fare tutto.** `admin` sul cluster concede ogni richiesta, come il permesso `all` di Solr.
- **Le policy possono valere solo per alcune reti.** Ogni verifica porta l’indirizzo del client; dietro i reverse proxy di cui l’agente si fida, il client è l’ultimo indirizzo di `X-Forwarded-For` che nessun proxy fidato ha aggiunto.
- **I ruoli verificati di un token web JSON contano come gruppi.** Con l’autenticazione JWT di Solr, i ruoli che la claim del token dà all’utente sono gruppi della decisione, accanto a quelli in cui GrantForge colloca l’utente.
- **I rifiuti sono registrati con il loro motivo.** Solr risponde a una richiesta rifiutata con il proprio 403; il log del nodo nomina la policy di GrantForge che l’ha negata, o indica che nessuna la consente.
- **Le decisioni sono conservate e gli accessi raggruppati.** Le decisioni dello snapshot corrente sono conservate, per impostazione predefinita 100.000 per 10 secondi, e un nuovo snapshot riparte da zero. Gli accessi consentiti identici entro una finestra, per impostazione predefinita 10 secondi, sono segnalati come un unico evento di accesso che indica quanti erano; i rifiuti sono segnalati uno per uno, subito. Ogni evento nomina la collection e il percorso, o il cluster, e l’indirizzo del client.
- **I nodi non sono verificati.** Le richieste tra i nodi, che Solr firma come il nodo stesso, passano; a una richiesta senza utente viene chiesto di accedere.

| Richiesta | Accesso necessario |
| --- | --- |
| Cercare, ottenere, esportare, interrogare tramite qualsiasi handler di lettura | `query` su collection e percorso |
| Aggiungere, aggiornare o eliminare documenti | `update` su collection e percorso |
| Leggere o modificare lo schema di una collection | `schema_read` o `schema_edit` |
| Leggere o modificare la config di una collection | `config_read` o `config_edit` |
| Richieste della Collections API che nominano collection: creare, ricaricare, fare il backup, ripristinare, eliminare, modificare, il loro stato nel cluster | `admin_collection` su ogni collection nominata |
| Controlli di salute; metriche | `health`; `metrics` sul cluster |
| Elencare collection, stato dei core, letture di ZooKeeper, del file store e dei pacchetti | `admin_read` sul cluster |
| Leggere o modificare autenticazione e autorizzazione | `security` sul cluster |
| Amministrazione dei core, modifiche al file store e ai pacchetti, e ciò che nessun handler nomina | `admin` sul cluster |

## Distribuzione

1. Aggiungete un servizio dati `solr` e configuratene le policy, incluso `admin` sul cluster per gli amministratori di Solr. Aprite **Permessi sui dati → Agenti**, scegliete il servizio ed emettete un token di agente. È mostrato una sola volta; salvatelo in un file leggibile solo dall’account di Solr.
2. Copiate il JAR in `server/solr-webapp/webapp/WEB-INF/lib` di **ogni nodo**, come `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`, e riavviate i nodi uno alla volta.
3. Impostate la sezione di autorizzazione del `security.json` del cluster, accanto a un plug-in di autenticazione, sostituendo indirizzo, percorsi e nome dell’istanza dell’esempio:

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   Caricatelo con `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>`; i nodi lo adottano senza riavvio. L’autenticazione deve essere abilitata, così che le richieste abbiano utenti.
4. **Su Solr 9, tenete conto del security manager di Java**, che Solr abilita per impostazione predefinita: tenete il token, la chiave fissata e la directory della cache sotto `SOLR_HOME`, come `/var/solr/data`, e concedete all’agente ciò che gli serve oltre alla policy di Solr, in `server/etc/security.policy` o in un vostro file di policy che `-Djava.security.properties` aggiunge come `policy.url.3`:

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10 è eseguito senza security manager.
5. **Migrate prima i permessi esistenti:** i permessi e i ruoli utente del plug-in basato su regole di Solr non valgono più. Create prima le policy corrispondenti. Finché l’agente non ha il suo primo snapshot, le richieste diverse da quelle tra i nodi sono rifiutate.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per i nodi del cluster e una directory scrivibile per lo snapshot firmato e gli eventi in coda |
| `grantforge.trusted.proxies` | Reverse proxy di cui l’agente accetta `X-Forwarded-For`, indirizzi o intervalli separati da virgole; nessuno per impostazione predefinita |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave viene ottenuta dal server al primo contatto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite del buffer su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo gli accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` li segnala uno per uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Il numero massimo di decisioni conservate, predefinito `100000`, fino a `10000000`, `0` non ne conserva nessuna; e per quanto tempo, predefinito `10000`, `1..3600000` millisecondi |

## Utenti, gruppi e ruoli

Le policy nominano l’utente con cui l’autenticazione di Solr accede, come un utente dell’autenticazione basic o il principal di un token web JSON. I gruppi sono i ruoli che un token web JSON ha verificato per l’utente, insieme ai gruppi in cui GrantForge colloca l’utente; i ruoli sono quelli che GrantForge dà all’utente.

## Decisioni e limiti

- Senza uno snapshot utilizzabile tutto è rifiutato tranne le richieste tra i nodi. Un servizio non in uso lascia Solr senza controlli, come senza plug-in di autorizzazione.
- Un token associato a un servizio il cui tipo non è `solr` rifiuta tutto.
- Una richiesta della Collections API che nomina le proprie collection solo in un corpo JSON, come alcune richieste della API v2, non ne nomina nessuna che l’agente possa vedere, e richiede `admin` sul cluster.
- Solr risolve gli alias nelle loro collection prima di chiedere, quindi le policy nominano collection, non alias.
- L’API `/admin/authorization` di Solr non può modificare le regole dell’agente: le policy sono in GrantForge.
- Gli indirizzi dei client sono solo indirizzi e intervalli; un nome host non corrisponde a nulla.
- Le decisioni sono conservate per `grantforge.decision.cache.ttl.ms`, quindi una policy inizia o smette di valere fino a quel tempo dopo.

## Guide correlate

- [Apache Solr](/it/plugins/solr/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
