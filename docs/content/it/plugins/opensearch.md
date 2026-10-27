---
title: OpenSearch
description: Installare e configurare il plug-in di servizio OpenSearch, raggiungere uno o più nodi, accedere con password o certificato client TLS, cercare indici, flussi di dati, campi e il cluster, e gestire chi può leggere, scrivere e amministrare cosa, fino a campi, valori mascherati e documenti.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in OpenSearch cerca indici, flussi di dati, campi e il cluster, e gestisce le policy di accesso nei termini di OpenSearch: leggere campi, indicizzare, eliminare, creare e amministrare indici, e le operazioni del cluster stesso, con maschere dei campi e filtri delle righe degli indici. L’ID del plug-in e il tipo di servizio sono entrambi `opensearch`. Il plug-in di servizio gira nel server GrantForge; l’[agente OpenSearch](/it/external/opensearch-agent/), un servizio accanto al cluster, applica le policy con i ruoli, la sicurezza a livello di campo, i campi mascherati e la sicurezza a livello di documento del plug-in di sicurezza.

I test di integrazione Testcontainers del repository coprono OpenSearch `2.19`, `3.3`, `3.6` e `3.9` con il plug-in di sicurezza; ciò non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/opensearch`. Verificate che `opensearch` sia attivato in **Gestione della piattaforma → Plug-in**; ripetete la scansione della directory dei plug-in dopo averla aggiornata. Il plug-in comunica con i nodi tramite il client REST di basso livello di OpenSearch, in un class loader proprio.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e selezionate OpenSearch (`opensearch`).
2. Inserite un nodo come URL, o più nodi.
3. Scegliete come accede l’utente di ricerca, inserite le sue credenziali, provate la connessione e salvate il servizio. La prova accede e descrive il cluster: la sua versione di OpenSearch e il suo nome, l’utente con cui ha acceduto e quanti indici e flussi di dati ha. Un cluster senza plug-in di sicurezza non supera la prova, poiché l’agente applica le policy tramite esso.

L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dagli utenti che leggono e scrivono. Una prova di connessione riuscita non significa che l’accesso sia già soggetto alle policy.

| Impostazione | Scopo |
| --- | --- |
| `opensearch.hosts` | Obbligatoria: un nodo come URL, ad esempio `https://opensearch1.example.com:9200`; più nodi separati da virgole, provati a turno quando uno non è raggiungibile. Senza schema un nodo è `https`, e la porta predefinita è `9200`; i nodi di un servizio sono tutti `http` o tutti `https` |
| `authentication` | `password` (predefinito), oppure `certificate`: un certificato client TLS che il plug-in di sicurezza associa a un utente |
| `username`, `password` | password: l’utente di ricerca e la sua password, salvata cifrata |
| `ssl.verificationMode` | Come lo chiama OpenSearch Dashboards: `full` (predefinito) verifica certificati e nomi dei nodi, `certificate` solo i certificati, `none` niente |
| `ssl.certificateAuthorities` | Percorso sul **server GrantForge** dei certificati CA PEM con cui si verificano i nodi; vuoto usa quelli della JVM |
| `ssl.certificate`, `ssl.key` | certificate: percorsi sul server GrantForge del certificato client PEM e della sua chiave privata, PKCS #8 o la vecchia forma OpenSSL; richiedono nodi `https` |
| `ssl.keyPassphrase` | certificate: la passphrase di una chiave privata cifrata, salvata cifrata |
| `timeout` | Quanto possono durare la connessione e una richiesta, predefinito `30000`, intervallo `1..600000` millisecondi |

Le impostazioni di un metodo di accesso sono rifiutate per l’altro, così il servizio dice esattamente come accede. Accessi rifiutati, permessi mancanti, nodi irraggiungibili e certificati non verificabili sono segnalati come tali.

### Cosa vede l’utente di ricerca

L’utente di ricerca deve poter leggere ciò che elenca: l’API resolve di OpenSearch per indici e flussi di dati, le capacità dei campi (field capabilities) per i campi e la descrizione del cluster per il suo nome. Il ruolo integrato `readall` dà i primi due; un utente senza `cluster:monitor/main` supera comunque la prova di connessione, che allora non nomina la versione. Indici e flussi di dati il cui nome inizia con un punto, nascosti e di sistema, sono elencati solo quando ciò che si digita inizia con un punto; gli indici di supporto dei flussi di dati sono elencati tramite il loro flusso. I campi sono quelli dei documenti: i campi di metadati di OpenSearch, gli oggetti e i multi-campi che indicizzano un campo ancora una volta, come `name.keyword`, sono esclusi, poiché la sicurezza a livello di campo li mostra o li nasconde con il loro campo.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `index` | Caratteri jolly, distingue maiuscole; il nome di un indice o flusso di dati | `index`, `delete`, `create_index`, `manage_aliases`, `monitor`, `manage` |
| `field` | Caratteri jolly, distingue maiuscole; sotto un indice, il percorso di un campo come `address.city` | `read` |
| `cluster` | Caratteri jolly, distingue maiuscole; il nome del cluster | `cluster_monitor`, `manage_snapshots`, `manage_templates`, `manage_pipelines` |

| Tipo di accesso | Gruppo di azioni del plug-in di sicurezza |
| --- | --- |
| `read` | `read`: ricerche, letture, conteggi e simili |
| `index` | `index`: indicizzare e aggiornare documenti, anche via bulk |
| `delete` | `delete`: eliminare documenti |
| `create_index` | `create_index`: creare indici, anche quando la prima scrittura ne crea uno |
| `manage_aliases`, `monitor` | `manage_aliases`, `indices_monitor` |
| `manage` | `manage`: ogni azione amministrativa di un indice, compresa la sua eliminazione; implica `create_index`, `manage_aliases` e `monitor` |
| `cluster_monitor`, `manage_snapshots` | `cluster_monitor`, `manage_snapshots` |
| `manage_templates`, `manage_pipelines` | `cluster_manage_index_templates`, `cluster_manage_pipelines` |

La lettura si decide per campo, come la sicurezza a livello di campo mostra i campi dei documenti: una policy `read` nomina campi, e `*` è ogni campo. Così una policy può consentire un indice e negarne un campo, e l’utente legge tutti gli altri. Nei valori delle policy `*` corrisponde a qualsiasi sequenza di caratteri e `?` a un carattere. L’editor delle policy suggerisce indici e flussi di dati, i campi degli indici che la policy nomina e il cluster.

Usare ciò che una policy consente richiede di più in OpenSearch: multi-search, multi-get e scorrimento per leggere, e bulk per scrivere. Non sono tipi di accesso a sé; l’agente concede con essi le operazioni composte del cluster.

### Maschere

Una policy di mascheramento di un campo mostra all’utente i suoi valori mascherati, come i campi mascherati del plug-in di sicurezza, nei documenti come nelle aggregazioni:

| Maschera | Cosa vede l’utente |
| --- | --- |
| Oscurare (Redact) | Maiuscole come `X`, minuscole come `x` e cifre come `n` |
| Maschera parziale: mostra gli ultimi 4 / i primi 4 | Ogni altro carattere come `x` |
| Hash (SHA-256) | L’hash SHA-256 del valore |
| Hash con il sale del cluster (BLAKE2b) | L’hash proprio del plug-in di sicurezza, con il sale del cluster come chiave, così valori uguali restano uguali ma non si possono risalire |
| Personalizzata | Un algoritmo di hash come `SHA-512`, o espressioni regolari con le loro sostituzioni come `/[0-9]/::*` |
| Non mascherato | Il valore così com’è |

### Filtri delle righe

Una policy di filtro delle righe di un indice mostra all’utente solo i documenti che una query trova. La query è il DSL di query di OpenSearch come lo accetta la sicurezza a livello di documento, ad esempio `{"term": {"region": "EU"}}`, e può usare le sostituzioni del plug-in di sicurezza come `${user.name}`. Come per gli altri servizi, un utente riceve il filtro della prima policy che gli si applica. Un filtro delle righe può verificare solo campi che l’utente può leggere: la sicurezza a livello di campo nasconde gli altri anche alla query.

## Esempio di policy

Supponete che il cluster abbia gli indici `logs-2026.10.26` e `hr`. Create queste policy attivate nello stesso servizio dati:

| Risorsa | Regola |
| --- | --- |
| `index=logs-*`, `field=*` | Consentire al gruppo `analysts` `read` |
| `index=logs-*`, `field=client_ip` | Negare `read` al gruppo `analysts` |
| `index=logs-*` | Consentire all’utente `logstash` `index` e `create_index` |
| `index=hr`, `field=*` | Consentire al gruppo `hr` `read` |
| `index=hr`, `field=salary` (mascheramento) | Mostrare al gruppo `hr` l’hash con il sale del cluster |
| `index=hr` (filtro delle righe) | Mostrare all’utente `emea-hr` solo i documenti di `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Consentire al gruppo `ops` `cluster_monitor` |

Quando l’agente applica il nuovo snapshot, i membri di `analysts` leggono ogni campo dei log tranne l’indirizzo del client; `logstash` scrive subito il `logs-2026.10.27` di domani, creandolo con il primo documento; i membri di `hr` vedono gli stipendi come hash; `emea-hr` vede solo i dipendenti della regione; e i membri di `ops` leggono lo stato e le statistiche del cluster.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse; applicarle richiede un agente OpenSearch per cluster. Distribuzione e verifiche sono in [Agente OpenSearch](/it/external/opensearch-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente OpenSearch](/it/external/opensearch-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
