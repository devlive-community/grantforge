---
title: Elasticsearch
description: Installare e configurare il plug-in di servizio Elasticsearch, raggiungere uno o più nodi, accedere con una password, una chiave API o un certificato client TLS, cercare indici, flussi di dati, campi e il cluster, e gestire chi può leggere, scrivere e amministrare cosa con i privilegi propri di Elasticsearch, fino a campi e documenti.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Elasticsearch cerca indici, flussi di dati, campi e il cluster, e gestisce le policy di accesso nei termini di Elasticsearch: leggere campi, indicizzare ed eliminare documenti, creare, eliminare e amministrare indici, e le operazioni del cluster stesso, con filtri delle righe degli indici. L’ID del plug-in e il tipo di servizio sono entrambi `elasticsearch`. Il plug-in di servizio gira nel server GrantForge; l’[agente Elasticsearch](/it/external/elasticsearch-agent/), un servizio accanto al cluster, applica le policy con i ruoli propri di Elasticsearch, la sicurezza a livello di campo e la sicurezza a livello di documento.

I test di integrazione Testcontainers del repository coprono Elasticsearch `8.19`, `9.3`, `9.4` e `9.5` con la sicurezza attiva; ciò non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/elasticsearch`. Verificate che `elasticsearch` sia attivato in **Gestione della piattaforma → Plug-in**; ripetete la scansione della directory dei plug-in dopo averla aggiornata. Il plug-in comunica con i nodi tramite il client REST di basso livello di Elasticsearch, in un class loader proprio.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e selezionate Elasticsearch (`elasticsearch`).
2. Inserite un nodo come URL, o più nodi.
3. Scegliete come accede l’utente di ricerca, inserite le sue credenziali, provate la connessione e salvate il servizio. La prova accede e descrive il cluster: la sua versione di Elasticsearch e il suo nome, la licenza e se offre la sicurezza a livello di documento e di campo, l’utente con cui ha acceduto e quanti indici e flussi di dati ha. Un cluster senza sicurezza non supera la prova, poiché l’agente applica le policy con i ruoli di Elasticsearch.

L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dagli utenti che leggono e scrivono. Una prova di connessione riuscita non significa che l’accesso sia già soggetto alle policy.

| Impostazione | Scopo |
| --- | --- |
| `elasticsearch.hosts` | Obbligatoria: un nodo come URL, ad esempio `https://es1.example.com:9200`; più nodi separati da virgole, provati a turno quando uno non è raggiungibile. Senza schema un nodo è `https`, e la porta predefinita è `9200`; i nodi di un servizio sono tutti `http` o tutti `https` |
| `authentication` | `password` (predefinito), `api_key` oppure `certificate`: un certificato client TLS che un realm PKI associa a un utente |
| `username`, `password` | password: l’utente di ricerca e la sua password, salvata cifrata |
| `apiKey` | api_key: la chiave API codificata, così come Elasticsearch la restituisce alla creazione, salvata cifrata |
| `ssl.verificationMode` | Come lo chiama Kibana: `full` (predefinito) verifica certificati e nomi dei nodi, `certificate` solo i certificati, `none` niente |
| `ssl.certificateAuthorities` | Percorso sul **server GrantForge** dei certificati CA PEM con cui si verificano i nodi, come l’`http_ca.crt` di Elasticsearch; vuoto usa quelli della JVM |
| `ssl.certificate`, `ssl.key` | certificate: percorsi sul server GrantForge del certificato client PEM e della sua chiave privata, PKCS #8 o la vecchia forma OpenSSL; richiedono nodi `https` |
| `ssl.keyPassphrase` | certificate: la passphrase di una chiave privata cifrata, salvata cifrata |
| `timeout` | Quanto possono durare la connessione e una richiesta, predefinito `30000`, intervallo `1..600000` millisecondi |

Le impostazioni di un metodo di accesso sono rifiutate per gli altri, così il servizio dice esattamente come accede. Accessi rifiutati, chiavi API revocate, privilegi mancanti, nodi irraggiungibili e certificati non verificabili sono segnalati come tali.

### Cosa vede l’utente di ricerca

L’utente di ricerca deve poter leggere ciò che elenca: l’API resolve di Elasticsearch per indici e flussi di dati, le capacità dei campi per i campi e la descrizione del cluster per il suo nome. Un ruolo con `read` e `view_index_metadata` sugli indici dà i primi due; un utente senza il privilegio di cluster `monitor` supera comunque la prova di connessione, che allora non nomina versione né licenza. Indici e flussi di dati i cui nomi iniziano con un punto, nascosti e di sistema, sono elencati solo quando ciò che si digita inizia con un punto; gli indici sottostanti dei flussi di dati sono elencati come il loro flusso di dati. I campi sono quelli dei documenti: i campi di metadati propri di Elasticsearch, gli oggetti e i multi-campi che indicizzano di nuovo un campo, come `name.keyword`, sono omessi, poiché l’agente li concede con il loro campo.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `index` | Caratteri jolly, sensibile alle maiuscole; il nome di un indice o flusso di dati | `index`, `delete`, `create_index`, `delete_index`, `view_index_metadata`, `monitor`, `manage` |
| `field` | Caratteri jolly, sensibile alle maiuscole; sotto un indice, il percorso di un campo come `address.city` | `read` |
| `cluster` | Caratteri jolly, sensibile alle maiuscole; il nome del cluster | `cluster_monitor`, `create_snapshot`, `manage_slm`, `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` |

Ogni tipo di accesso è il privilegio di Elasticsearch con lo stesso nome, tranne `cluster_monitor`, che è il privilegio di cluster `monitor`:

| Tipo di accesso | Cosa consente |
| --- | --- |
| `read` | Ricerche, letture, conteggi, ricerche multiple e scrolling |
| `index`, `delete` | Indicizzare e aggiornare documenti, anche in bulk; eliminare documenti |
| `create_index`, `delete_index` | Creare indici, anche quando la prima scrittura ne crea uno; eliminare indici e flussi di dati |
| `view_index_metadata`, `monitor` | Leggere mapping, impostazioni e alias; leggere statistiche e recovery |
| `manage` | Ogni azione amministrativa di un indice; implica `create_index`, `delete_index`, `view_index_metadata` e `monitor` |
| `cluster_monitor` | Leggere salute, stato e statistiche del cluster |
| `create_snapshot`, `manage_slm` | Creare snapshot; gestire le policy del ciclo di vita degli snapshot, il che implica `create_snapshot` |
| `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` | Gestire template degli indici, pipeline di ingest e policy del ciclo di vita degli indici |

La lettura si decide per campo, così come la sicurezza a livello di campo mostra i campi dei documenti: una policy `read` nomina campi, e `*` indica ogni campo. Una policy può così consentire un indice e negarne un campo, e l’utente legge tutti gli altri. Nei valori delle policy `*` corrisponde a qualsiasi sequenza di caratteri e `?` a un carattere. L’editor delle policy suggerisce indici e flussi di dati, i campi degli indici nominati dalla policy e il cluster.

### Filtri delle righe

Una policy di filtro delle righe di un indice mostra all’utente solo i documenti che corrispondono a una query. La query è il Query DSL di Elasticsearch così come lo prende la sicurezza a livello di documento, ad esempio `{"term": {"region": "EU"}}`, e può essere un template, ad esempio con `{{_user.username}}`. Come per gli altri servizi, un utente riceve il filtro della prima policy che gli si applica. Un filtro delle righe può interrogare solo campi che l’utente può leggere: la sicurezza a livello di campo nasconde gli altri anche alla query.

Elasticsearch non maschera valori, quindi il tipo di servizio non offre mascheramento.

### La licenza

Elasticsearch offre la sicurezza a livello di documento e di campo a partire da una licenza Platinum. Al di sotto, una policy che lascia leggere a un utente solo alcuni campi o alcuni documenti di un indice non può essere applicata; l’agente quindi non concede a quell’utente la lettura dell’indice anziché più di quanto le policy consentano, e lo segnala. Le policy che consentono indici interi, la scrittura e l’amministrazione funzionano con ogni licenza.

## Esempio di policy

Supponiamo che il cluster, con licenza Platinum, abbia gli indici `logs-2026.10.26` e `hr`. Create queste policy attive nello stesso servizio dati:

| Risorsa | Regola |
| --- | --- |
| `index=logs-*`, `field=*` | Consentire `read` al gruppo `analysts` |
| `index=logs-*`, `field=client_ip` | Negare `read` al gruppo `analysts` |
| `index=logs-*` | Consentire `index` e `create_index` all’utente `logstash` |
| `index=hr`, `field=*` | Consentire `read` al gruppo `hr` |
| `index=hr` (filtro delle righe) | Mostrare all’utente `emea-hr` solo documenti di `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Consentire `cluster_monitor` al gruppo `ops` |

Quando l’agente applica il nuovo snapshot, i membri di `analysts` leggono ogni campo dei log tranne l’indirizzo del client; `logstash` scrive subito il `logs-2026.10.27` di domani e lo crea con il suo primo documento; i membri di `hr` leggono i dipendenti; `emea-hr` vede solo i dipendenti della regione; e i membri di `ops` leggono salute e statistiche del cluster.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse; applicarle richiede un agente Elasticsearch per cluster. Distribuzione e verifiche sono descritte in [Agente Elasticsearch](/it/external/elasticsearch-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente Elasticsearch](/it/external/elasticsearch-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
