---
title: Agente Apache Kafka
description: Installare l’agente nei broker e nei controller Kafka per autorizzare ogni richiesta con le policy di GrantForge, mostrare le policy come ACL di sola lettura e raggruppare gli accessi ripetuti in un unico evento di audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente Kafka è un autorizzatore dell’API `Authorizer` di Kafka, indicato in `authorizer.class.name`: broker e controller lo interrogano per ogni richiesta, e risponde in base alle policy firmate del servizio dati `kafka`. La distribuzione lo fornisce come un unico JAR compatibile con Java 8, `agents/kafka/grantforge-agent-kafka-<version>.jar`, con le dipendenze rilocate al suo interno.

I test di integrazione Testcontainers del repository eseguono il JAR in Kafka `3.7.2`, `3.9.2`, `4.1.2` e `4.3.1`, in nodi KRaft che sono insieme broker e controller. Ciò non certifica ogni altra versione. Per il plug-in di servizio vedi [Apache Kafka](/it/plugins/kafka/).

## Funzionamento

- **Ogni richiesta viene controllata.** Per ogni risorsa toccata da una richiesta, Kafka chiede un’operazione ACL; l’agente risponde in base alle policy, con i tipi di risorsa di Kafka come livelli di risorsa e le sue operazioni come tipi di accesso. Un rifiuto arriva al client come errore di Kafka stesso, ad esempio `TOPIC_AUTHORIZATION_FAILED`.
- **Ai producer idempotenti basta un topic.** Kafka lascia avviare un producer senza `idempotent_write` sul cluster se può scrivere in qualche topic, e lo chiede all’autorizzatore. L’agente risponde in base alle policy stesse: conta un `write` consentito su qualsiasi topic, mentre un divieto conta solo in una policy che copre tutti i topic.
- **Le policy appaiono come ACL.** `kafka-acls --list` e `describeAcls` dell’API Admin mostrano le policy che si applicano ora: un valore senza caratteri jolly come ACL letterale, un valore come `orders*` come ACL con prefisso e `*` come carattere jolly di Kafka; gli utenti come principal `User:`, i gruppi come `Group:`, i ruoli come `Role:` e il gruppo `public` come `User:*`. Ciò che le ACL non possono esprimere viene omesso: altri caratteri jolly, valori esclusi, elementi con condizioni e gli elementi di una policy che ha eccezioni. Creare o eliminare ACL viene rifiutato: *the ACLs of this cluster are GrantForge policies; change them in GrantForge*.
- **Le decisioni vengono conservate.** Un broker pone le stesse domande a ogni produzione e lettura; l’agente conserva le decisioni dello snapshot corrente, per impostazione predefinita 100.000 per 10 secondi. Un nuovo snapshot riparte da zero, così una modifica di policy si applica non appena l’agente la riceve.
- **Gli accessi ripetuti vengono raggruppati.** Gli accessi consentiti identici entro una finestra, predefinita 10 secondi, vengono segnalati come un unico evento di accesso che indica quanti sono stati e quando è avvenuto l’ultimo; la console mostra *N accessi identici*. Gli accessi sono identici se condividono utente, indirizzo del client, risorsa, tipo di accesso, tipo di richiesta e policy decisiva. I rifiuti vengono segnalati uno per uno, subito. Kafka sceglie cosa vale la pena segnalare: ad esempio non chiede di segnalare i topic che omette da un elenco.
- **I super utenti non vengono controllati.** I principal di `super.users` possono fare tutto, come con l’autorizzatore di Kafka, e non vengono sottoposti ad audit.

## Distribuzione

1. Aggiungi un servizio dati `kafka` e configura le sue policy. Apri **Permessi sui dati → Agenti**, seleziona il servizio ed emetti un token dell’agente. Viene mostrato una sola volta; salvalo in un file leggibile solo dall’account di Kafka.
2. Copia il JAR in `libs` di **ogni broker e controller**, ad esempio `/opt/kafka/libs`.
3. Aggiungi le impostazioni al `server.properties` di ciascuno, sostituendo principal, indirizzo, percorsi e nome dell’istanza di esempio:

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` deve indicare i principal che broker e controller usano tra loro, come quello del listener tra broker, oppure delle policy devono consentire loro ciò che fanno, come `cluster_action` sul cluster. Le connessioni a un listener `PLAINTEXT` sono `User:ANONYMOUS`; indicalo solo se nessun client può raggiungere un tale listener. La directory della cache deve essere scrivibile dall’account di Kafka.
4. Riavvia broker e controller uno alla volta. I listener diversi da quelli dei controller si aprono quando l’agente ha il primo snapshot, al più dopo `grantforge.start.timeout.ms`; fino ad allora, e ogni volta che manca uno snapshot, le richieste vengono rifiutate.
5. **Migra prima le ACL esistenti:** le ACL memorizzate dall’autorizzatore di Kafka non si applicano più una volta che l’agente lo sostituisce. Elencale prima con `kafka-acls --list` e crea le policy corrispondenti.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token dell’agente, un nome per questo broker o controller e una directory scrivibile per lo snapshot firmato e gli eventi accodati |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copia la chiave pubblica Ed25519 X.509 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave viene ottenuta dal server al primo contatto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo gli accessi consentiti identici vengono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` li segnala uno per uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Numero massimo di decisioni conservate, predefinito `100000`, fino a `10000000`, `0` non ne conserva; e per quanto tempo, predefinito `10000`, `1..3600000` millisecondi |
| `grantforge.start.timeout.ms` | Quanto i listener attendono il primo snapshot, predefinito `30000`, `0..600000` millisecondi |

L’agente legge le impostazioni dalla configurazione che Kafka passa al suo autorizzatore. Un nodo che è insieme broker e controller crea un autorizzatore per ruolo; entrambi condividono un solo agente.

## Utenti, gruppi e ruoli

Le policy nominano il principal che Kafka assegna alla connessione: il nome utente SASL, il nome breve di un principal Kerberos secondo `sasl.kerberos.principal.to.local.rules`, o il nome di un certificato TLS secondo `ssl.principal.mapping.rules`. Kafka non ha gruppi né ruoli; quelli delle policy sono quelli che GrantForge assegna all’utente.

## Decisioni e limiti

- Senza uno snapshot utilizzabile, tutto viene rifiutato tranne le richieste dei super utenti. Un servizio non in uso lascia Kafka senza controlli, come senza autorizzatore.
- Un token legato a un servizio il cui tipo non è `kafka` rifiuta tutto.
- Negare un tipo di accesso nega anche ciò che implica: negare `write` su un topic nega `describe`, così l’utente non lo vede. Le ACL di Kafka si comportano diversamente.
- La domanda di Kafka se un utente possa agire su una risorsa qualsiasi di un tipo non è un accesso e non viene segnalata.
- Le decisioni vengono conservate per `grantforge.decision.cache.ttl.ms`, quindi il periodo di validità di una policy inizia o finisce con un ritardo fino a tale durata.
- L’agente funziona nei broker e nei controller. L’API REST di Kafka Connect, i registri di schemi e gli altri servizi attorno a Kafka non sono coperti.

## Guide correlate

- [Apache Kafka](/it/plugins/kafka/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
