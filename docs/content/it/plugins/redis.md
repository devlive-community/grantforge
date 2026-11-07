---
title: Redis
description: Installare e configurare il plug-in di servizio Redis per Redis e Valkey, collegarsi a un server, alle sue repliche o a un cluster, accedere con un utente ACL, via TLS se serve, cercare chiavi, canali e nodi, e gestire chi può leggere e scrivere quali chiavi, usare quali canali e monitorare quali nodi.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Redis cerca chiavi, canali e nodi dei deployment Redis e Valkey e gestisce le policy di accesso nei termini di Redis stesso: leggere e scrivere chiavi per pattern, iscriversi e pubblicare sui canali, e monitorare ogni nodo. L’ID del plug-in e il tipo di servizio sono entrambi `redis`. Il plug-in di servizio viene eseguito nel server GrantForge; l’[agente Redis](/it/external/redis-agent/), eseguito accanto al deployment, applica le policy con l’ACL di Redis stesso su ogni nodo.

I test di integrazione Testcontainers del repository coprono un primario con una replica di Redis `7.2`, `7.4`, `8.2` e `8.10` e Valkey `8.1` e `9.1`, e un cluster Redis `8.10`. Questo non certifica ogni altra versione.

## Installazione

La distribuzione include il plug-in in `plugins/redis`. Verificate che `redis` sia attivato in **Gestione della piattaforma → Plug-in**; ripetete la scansione dopo aver aggiornato la directory dei plug-in. Il plug-in parla con Redis tramite Jedis, in un proprio class loader.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e selezionate Redis (`redis`).
2. Inserite uno o più nodi. Il resto di un cluster e le repliche di un primario vengono trovati a partire da essi.
3. Indicate l’utente di ricerca, provate la connessione e salvate il servizio. La prova accede e descrive il deployment: Redis o Valkey e la versione, se è un singolo server, un primario con repliche o un cluster, e chi ha effettuato l’accesso.

L’utente di ricerca permette a GrantForge di trovare le risorse e non è l’utente che legge o scrive. Una prova di connessione riuscita non significa che l’accesso segua già le policy.

| Impostazione | Scopo |
| --- | --- |
| `redis.nodes` | Obbligatoria: i nodi come `host:port`, come `redis1.example.com:6379`, separati da virgole e provati in ordine; la porta è `6379` se non indicata |
| `username` | L’utente di ricerca, un utente ACL; vuoto accede come utente predefinito |
| `password` | La sua password, salvata cifrata |
| `tls.mode` | `disable` (predefinito), `verify-ca` per verificare i certificati dei server, o `verify-full` per verificare anche i loro nomi |
| `tls.ca.file` | TLS: percorso sul **server GrantForge** dei certificati CA PEM con cui vengono verificati i server. Vuoto usa quelli della JVM |
| `tls.certificate.file` | TLS, per server che chiedono certificati client: percorso sul **server GrantForge** del certificato client PEM, che può contenere anche la sua chiave |
| `tls.key.file` | Percorso sul **server GrantForge** della chiave PEM del certificato, quando ha un file proprio |
| `tls.key.password` | La password di una chiave cifrata, salvata cifrata |
| `timeout` | Quanto possono durare la connessione e un comando, `10000` per impostazione predefinita, nell’intervallo `1..600000` millisecondi |

Un accesso rifiutato, un utente di ricerca privo di un permesso e nodi irraggiungibili vengono segnalati come tali.

### Cosa vede l’utente di ricerca

Le chiavi vengono trovate con `SCAN` su ogni primario, al massimo qualche giro, così una ricerca resta veloce comunque siano tante le chiavi; i canali sono quelli che hanno iscritti adesso. `SCAN` elenca ogni chiave indipendentemente dai pattern di chiavi dell’utente, quindi l’utente di ricerca non ha bisogno del permesso di leggere i valori:

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `key` | Caratteri jolly, distingue maiuscole e minuscole. Una chiave o un pattern di chiavi | `read`, `write` |
| `channel` | Caratteri jolly, distingue maiuscole e minuscole. Un canale o un pattern di canali | `subscribe`, `publish` |
| `node` | Caratteri jolly, senza distinguere maiuscole e minuscole. L’indirizzo di un nodo come `host:port` | `monitor` |

| Tipo di accesso | Cosa permette Redis |
| --- | --- |
| `read` | I comandi di `@read` sulla chiave, `%R~` su di essa |
| `write` | I comandi di `@write` sulla chiave, `%W~` su di essa, che la eliminano anche |
| `subscribe` | `SUBSCRIBE`, `PSUBSCRIBE`, `SSUBSCRIBE` sul canale, `&` |
| `publish` | `PUBLISH`, `SPUBLISH` sul canale, `&` |
| `monitor` | `INFO`, `DBSIZE`, `LATENCY LATEST`, `LATENCY HISTORY`, `MEMORY STATS` |

Nei valori delle policy, `*` corrisponde a qualsiasi testo e `?` a un carattere, come nei pattern di Redis. Redis concede chiavi e canali per pattern e non può elencarli all’agente, quindi il valore di una policy viene concesso come pattern a sé, dove le policy decidono l’utente allo stesso modo su tutto il pattern. Un pattern all’interno del quale le policy decidono diversamente, come `sales:*` quando una policy nega `sales:card:*`, viene trattenuto e segnalato; nominate invece chiavi o pattern che le policy decidono allo stesso modo. I valori con parentesi quadre, backslash, parentesi tonde o spazi non vengono mai concessi.

I comandi che Redis considera pericolosi, come `FLUSHALL` e `KEYS`, non vengono mai dati, e `SCAN` e `RANDOMKEY`, che elencano le chiavi indipendentemente dai loro pattern, solo a un utente che può leggere ogni chiave. I pattern nominano chiavi in ogni database di un nodo, perché l’ACL di Redis non ha database.

Redis non filtra nulla all’interno di un valore e non maschera nulla, quindi questo tipo di servizio non offre né l’una né l’altra cosa.

## Esempio di policy

Create queste policy attivate in un servizio dati:

| Risorsa | Regola |
| --- | --- |
| `key=sales:*` | Permettere `read` al gruppo `analysts` |
| `key=sales:card:*` | Permettere `read` e `write` all’utente `billing` |
| `key=cart:*` | Permettere `read` e `write` all’utente `shop` |
| `channel=orders.*` | Permettere `subscribe` al gruppo `analysts`; permettere `publish` all’utente `shop` |
| `node=*` | Permettere `monitor` all’utente `dashboards` |

Dopo che l’agente ha applicato lo snapshot, i membri di `analysts` leggono ogni chiave `sales:` e seguono i canali degli ordini; `billing` legge e scrive le chiavi delle carte, un pattern in cui nessun’altra policy decide diversamente; `shop` gestisce i carrelli e pubblica gli eventi degli ordini; e `dashboards` vede lo stato di ogni nodo e nessun dato.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Per applicarle serve un agente Redis per deployment; vedete [Agente Redis](/it/external/redis-agent/) per la distribuzione e le verifiche.

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente Redis](/it/external/redis-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
