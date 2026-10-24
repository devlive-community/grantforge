---
title: Agente Apache Pulsar
description: Installare il provider di autorizzazione nei broker Pulsar per decidere con le policy GrantForge chi può produrre e consumare dove, tramite quali sottoscrizioni, eseguire functions, sources e sinks, amministrare topic, namespace, tenant e cluster, e da quali reti.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente Pulsar è un provider di autorizzazione dei broker: Pulsar gli chiede chi può produrre, consumare, eseguire functions, sources e sinks, e amministrare topic, namespace, tenant e cluster, e lui risponde dalle policy firmate del servizio dati `pulsar`. La distribuzione lo fornisce come JAR Java 17, `agents/pulsar/grantforge-agent-pulsar-<version>.jar`, con le dipendenze incluse e rilocate.

I test di integrazione Testcontainers del repository eseguono il JAR nei broker di Pulsar `3.0.17`, `3.3.9`, `4.0.14` e `4.2.5`. Ciò non certifica tutte le altre versioni. Il plug-in di servizio è descritto in [Apache Pulsar](/it/plugins/pulsar/).

## Funzionamento

- **Ogni controllo è deciso a ogni livello del nome del topic.** Come con i permessi di Pulsar, un permesso su un tenant o un namespace copre ciò che contiene, quindi l’agente decide una produzione su `persistent://sales/orders/payments` sul tenant `sales`, sul namespace `orders` e sul topic `payments`: un diniego su uno qualsiasi rifiuta, un consenso su uno qualsiasi concede.
- **Il consumo è deciso anche dalla sottoscrizione.** A ciascuno di quei livelli l’agente chiede anche del topic con la sottoscrizione, così una policy può lasciar consumare un ruolo solo tramite alcune sottoscrizioni, per esempio `billing-*`.
- **Gli amministratori del cluster sono i superutenti di Pulsar.** `admin` sul cluster del broker rende un ruolo uno di loro, e Pulsar lascia fare tutto ai suoi superutenti, quindi le policy sottostanti non li limitano. Le operazioni di broker e di cluster di Pulsar richiedono `admin` sul cluster che nominano.
- **Le policy possono valere solo per alcune reti.** Ogni controllo porta l’indirizzo del client, e l’agente valuta la condizione **Indirizzi dei client** con gli intervalli di indirizzi di Netty, che lo stack di rete del broker usa.
- **Concedere permessi tramite Pulsar è rifiutato.** I permessi di Pulsar stesso e i ruoli di amministrazione dei suoi tenant non sono usati, quindi `pulsar-admin namespaces grant-permission` e simili ricevono HTTP 405, con l’indicazione di concederli in GrantForge.
- **I rifiuti sono registrati con il motivo.** Pulsar formula il rifiuto che riceve il client; il log del broker nomina la policy GrantForge che ha negato, o che nessuna lo consente.
- **Le decisioni sono conservate e gli accessi raggruppati.** Le decisioni dello snapshot corrente sono conservate, per impostazione predefinita 100.000 per 10 secondi, e un nuovo snapshot ricomincia da zero. Gli accessi consentiti identici entro una finestra, per impostazione predefinita 10 secondi, sono segnalati come un unico evento di accesso che indica quanti erano; i rifiuti sono segnalati uno per uno e subito. Ogni evento nomina il topic, il namespace, il tenant o il cluster e l’indirizzo del client.
- **Un agente serve tutto il broker.** Un broker carica il suo provider di autorizzazione per il protocollo binario, il servizio web e il functions worker; condividono un agente, che si ferma con il broker.

| Operazione | Accesso richiesto |
| --- | --- |
| Produrre, o creare un topic producendovi | `produce` sul topic |
| Sottoscrivere, consumare, annullare la sottoscrizione, saltare, sbirciare, far scadere messaggi, reimpostare un cursore | `consume` sul topic con la sottoscrizione |
| Cercare un topic, leggerne statistiche, metadati e policy; elencare i topic di un namespace | `produce` o `consume` |
| Terminare, compattare, scaricare sullo storage a livelli o dal broker, o troncare un topic; cambiarne le policy | `admin_topic` sul topic |
| Creare ed eliminare topic; cambiare le policy di un namespace | `admin_namespace` sul namespace |
| Eseguire functions, sources, sinks; usare packages | `functions`, `sources`, `sinks`, `packages` sul namespace |
| Creare, elencare ed eliminare i namespace di un tenant | `admin_tenant` sul tenant |
| Creare, cambiare ed eliminare tenant e cluster; le operazioni di broker di Pulsar | `admin` sul cluster |

## Distribuzione

1. Aggiungete un servizio dati `pulsar` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, scegliete il servizio ed emettete un token di agente. È mostrato una sola volta; salvatelo in un file leggibile solo dall’account di Pulsar.
2. Copiate il JAR in `lib` **di ogni broker**, per esempio `/pulsar/lib`.
3. Aggiungete le impostazioni al `broker.conf` di ogni broker, o allo `standalone.conf`, sostituendo l’indirizzo, i percorsi e il nome di istanza dell’esempio:

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   Anche l’autenticazione deve essere abilitata, perché i client abbiano dei ruoli. `superUserRoles` e `proxyRoles` devono nominare solo il ruolo dei broker stessi e quelli dei proxy: l’agente non li controlla, e Pulsar controlla anche il client originale di un proxy. Agli amministratori date invece `admin` sul cluster. La directory di cache deve essere scrivibile dall’account di Pulsar. In un container scrivete le impostazioni `grantforge.*` nel file: la shell di alcune immagini Pulsar scarta le variabili d’ambiente il cui nome contiene punti, come `PULSAR_PREFIX_grantforge.token.file`.
4. Riavviate i broker uno dopo l’altro. Finché l’agente non ha il primo snapshot, le richieste diverse da quelle dei broker e dei proxy sono rifiutate.
5. **Trasferite prima i permessi esistenti:** i permessi di Pulsar stesso su namespace, topic e sottoscrizioni, e i ruoli di amministrazione dei tenant, non si applicano più. Create prima le policy corrispondenti.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per questo broker e una directory scrivibile per lo snapshot firmato e gli eventi in attesa |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Senza di esso, la chiave è ottenuta dal server al primo contatto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite dello spool (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo gli accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` li segnala tutti |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Numero massimo di decisioni conservate, predefinito `100000`, fino a `10000000`, `0` non ne conserva; e per quanto tempo, predefinito `10000`, `1..3600000` millisecondi |

## Utenti, gruppi e ruoli

Le policy nominano come utente il ruolo che l’autenticazione di Pulsar dà al client, per esempio il soggetto di un token o il common name di un certificato. Pulsar non ha gruppi propri: i gruppi sono quelli in cui GrantForge colloca il ruolo, e i ruoli quelli che GrantForge gli assegna.

## Decisioni e limiti

- Senza uno snapshot utilizzabile tutto è rifiutato, tranne le richieste dei broker e dei proxy. Un servizio non usato lascia Pulsar senza controlli, come con `authorizationEnabled` disattivato.
- Un token legato a un servizio il cui tipo non è `pulsar` rifiuta tutto.
- Prima di molti controlli Pulsar chiede se un ruolo è superutente o amministratore di un tenant; sono segnalate solo le risposte che lasciano passare una richiesta, e il controllo della richiesta stessa è segnalato comunque. Una richiesta che Pulsar decide solo con quella domanda, come fa Pulsar 3.0 per tenant e cluster, è registrata solo a livello debug quando è rifiutata.
- `admin` su un cluster rende un ruolo superutente solo dei broker di quel cluster.
- Gli elenchi dei permessi di Pulsar, come `pulsar-admin namespaces permissions`, falliscono: le policy le custodisce GrantForge.
- Gli indirizzi dei client sono solo indirizzi e intervalli; un nome host non corrisponde ad alcun client.
- Le decisioni sono conservate per `grantforge.decision.cache.ttl.ms`, quindi una policy inizia o smette di valere fino a quel tempo dopo.

## Guide correlate

- [Apache Pulsar](/it/plugins/pulsar/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
