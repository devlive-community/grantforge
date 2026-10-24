---
title: Apache Solr
description: Installare e configurare il plug-in di servizio Solr, raggiungere uno o più nodi, accedere con autenticazione basic o un token web JSON, cercare le collection e i loro percorsi di richiesta, e gestire chi può interrogare, aggiornare e amministrare cosa.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Solr cerca le collection e i percorsi di richiesta al loro interno, e gestisce policy di accesso su ciò che Solr decide: interrogare, aggiornare, leggere e modificare schemi e config, amministrare le collection e amministrare il cluster. L’ID del plug-in e il tipo di servizio sono entrambi `solr`. Il plug-in di servizio è eseguito nel server GrantForge; l’[agente Solr](/it/external/solr-agent/), installato nei nodi, applica le policy.

I test di integrazione Testcontainers del repository coprono nodi SolrCloud di Solr `9.4.1`, `9.8.1`, `9.11.0` e `10.0.0`; ciò non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/solr`. Verificate in **Gestione della piattaforma → Plug-in** che `solr` sia abilitato; dopo un aggiornamento, ripetete la scansione della directory dei plug-in. Il plug-in comunica con i nodi tramite il client HTTP del JDK di SolrJ 9.11, nel proprio class loader.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e scegliete Apache Solr (`solr`).
2. Inserite l’URL di base di un nodo, o di più nodi.
3. Scegliete come accede l’utente di ricerca, inserite le sue credenziali, testate la connessione e salvate il servizio. Il test accede e descrive il cluster: la sua versione di Solr, se esegue SolrCloud e quante collection ha.

L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dagli utenti che interrogano e aggiornano. Un test di connessione riuscito non significa che l’accesso sia già soggetto alle policy.

| Impostazione | Scopo |
| --- | --- |
| `solr.urls` | Obbligatoria: l’URL di base di un nodo, come `https://solr1.example.com:8983/solr`; più URL separati da virgole, provati a turno quando uno non è raggiungibile |
| `authentication` | `none` (predefinito), `basic` o `jwt` |
| `username`, `password` | basic: l’utente di ricerca e la sua password; la password è salvata cifrata |
| `token` | jwt: il token web JSON dell’utente di ricerca; salvato cifrato |
| `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword`, `javax.net.ssl.trustStoreType` | https: il truststore, sul **server GrantForge**, con cui si verificano i certificati dei nodi, la sua password e il suo tipo, predefinito `PKCS12`; vuoto usa quello della JVM |
| `timeout` | Quanto può durare una richiesta, predefinito `30000`, intervallo `1..600000` millisecondi |

Le impostazioni di un tipo di accesso sono rifiutate con un altro, così un servizio dice esattamente come accede. Un accesso rifiutato, un utente che non può vedere qualcosa e un nodo non raggiungibile, anche dietro un proxy che risponde al suo posto, sono segnalati come tali.

### Cosa vede l’utente di ricerca

Elencare le collection è il permesso `collection-admin-read` di Solr, e i percorsi di richiesta di una collection provengono dalla sua config, `config-read`. Concedeteli all’utente di ricerca nelle regole di Solr o, una volta installato l’agente, `admin_read` sul cluster e `config_read` sulle collection. I percorsi delle collection che l’utente di ricerca non può vedere sono esclusi dai suggerimenti. Un nodo eseguito senza SolrCloud ha core invece di collection, e il plug-in li presenta come collection.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `collection` | Carattere jolly, distingue maiuscole e minuscole; il nome della collection | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit`, `admin_collection` |
| `path` | Carattere jolly, distingue maiuscole e minuscole; sotto una collection; il percorso di richiesta, come `/select`, `/export` o `/update/json` | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit` |
| `cluster` | Il suo unico valore, `*` | `health`, `metrics`, `admin_read`, `security`, `admin` |

| Tipo di accesso | Permessi di Solr | Implica |
| --- | --- | --- |
| `query` | `read`: cercare, ottenere, esportare | — |
| `update` | `update` | — |
| `schema_read`, `schema_edit` | `schema-read`, `schema-edit` | `schema_edit` implica `schema_read` |
| `config_read`, `config_edit` | `config-read`, `config-edit` | `config_edit` implica `config_read` |
| `admin_collection` | `collection-admin-read` e `collection-admin-edit` delle richieste della Collections API che nominano la collection: creare, ricaricare, fare il backup, eliminare e simili | `query`, `update`, `schema_edit`, `config_edit` |
| `health`, `metrics` | `health`, `metrics-read` | — |
| `admin_read` | `collection-admin-read` dell’intero cluster, `core-admin-read`, `zk-read`, `filestore-read`, `package-read`, e la lettura di config e schema dei nodi | `health`, `metrics` |
| `security` | `security-read`, `security-edit` | — |
| `admin` | Tutto | `admin_read`, `security`, `admin_collection` |

Una richiesta a una collection è decisa sulla collection e sulla collection con il suo percorso di richiesta, e un rifiuto su uno dei due la respinge; così una policy può consentire una collection e negare il suo handler `/export`, o consentire l’aggiornamento solo tramite `/update*`. Una richiesta a più collection richiede ciascuna di esse. Sotto una collection, il percorso di richiesta è l’handler a cui Solr instrada la richiesta, come lo nomina Solr.

Una richiesta della Collections API che nomina collection, tramite il parametro `name` o `collection`, richiede `admin_collection` su ciascuna; una policy su `team-*` permette quindi a un team di creare collection il cui nome inizia con `team-`, e di ricaricarle, farne il backup o eliminarle, ma nessun’altra. Una richiesta che non ne nomina nessuna, come elencare tutte le collection, appartiene al cluster. `admin` sul cluster concede tutto, come il permesso `all` di Solr. Nei valori delle policy, `*` corrisponde a qualsiasi sequenza di caratteri e `?` a uno solo. L’editor delle policy suggerisce le collection e i percorsi di richiesta delle collection che una policy nomina.

### Indirizzi dei client

Un elemento di policy può valere solo per alcuni client: la sua condizione **Indirizzi dei client** (`ip-range`) accetta indirizzi e intervalli come `10.1.0.0/16` o `2001:db8::/32`. I nomi host non vengono risolti. Dietro un reverse proxy, il client è il proxy, a meno che l’agente non si fidi di esso: allora il client è l’ultimo indirizzo di `X-Forwarded-For` che nessun proxy fidato ha aggiunto, così un client non può attribuirsi un indirizzo a sua scelta.

## Esempio di policy

Supponete che il cluster abbia le collection `sales` e `hr`. Create queste policy di accesso abilitate nello stesso servizio dati:

| Risorse | Regola |
| --- | --- |
| `collection=sales` | Consentire `query` al gruppo `analysts` |
| `collection=sales`, `path=/export` | Negare `query` all’utente `alice` |
| `collection=sales`, `path=/update*` | Consentire `update` all’utente `ingest` |
| `collection=team-*` | Consentire `admin_collection` al gruppo `search-team` |
| `cluster=*` | Consentire `metrics` e `health` all’utente `monitoring` |
| `collection=hr` | Consentire `query` al gruppo `hr-staff`, da `10.0.0.0/8` |
| `cluster=*` | Consentire `admin` al gruppo `solr-admins` |

Quando l’agente applica il nuovo snapshot, i membri di `analysts` interrogano `sales`, `alice` tutto tranne il suo handler di esportazione; `ingest` aggiorna `sales` tramite i suoi handler di aggiornamento e non può interrogarla; i membri di `search-team` creano e amministrano le proprie collection `team-`; `monitoring` legge metriche e stato di salute; i membri di `hr-staff` interrogano `hr` solo dalla rete interna; e i membri di `solr-admins` possono fare tutto.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Applicarle richiede l’agente Solr in ogni nodo. Distribuzione e verifiche sono nell’[agente Solr](/it/external/solr-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente Solr](/it/external/solr-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
