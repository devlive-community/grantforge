---
title: Agente Redis
description: Eseguire l’agente Redis accanto a un deployment Redis o Valkey per mantenere i selettori ACL di ogni utente su ogni nodo allineati alle policy di GrantForge, sostituiti con un comando, chiedere a Redis con ACL DRYRUN cosa permettono i permessi fuori da GrantForge, e segnalare i rifiuti dai log ACL dei nodi.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente Redis è un servizio accanto al deployment. Mantiene l’ACL di Redis stesso allineata alle policy firmate di un servizio dati `redis` su ogni nodo, anche sulle repliche e su ogni nodo di un cluster, perché Redis tiene un’ACL su ciascuno. Redis verifica poi ogni comando da sé, e l’agente non si frappone a nessun client. La distribuzione lo fornisce come JAR eseguibile `agents/redis/grantforge-agent-redis-<version>.jar`; richiede Java 17 o successivo.

I test di integrazione Testcontainers del repository eseguono l’agente, e il JAR così come viene distribuito, su un primario con una replica di Redis `7.2`, `7.4`, `8.2` e `8.10` e Valkey `8.1` e `9.1`, e su un cluster Redis `8.10`. Questo non certifica ogni altra versione; l’agente rifiuta Redis precedente alla `7.2`, il cui log ACL non numera le voci. Per il plug-in di servizio vedete [Redis](/it/plugins/redis/).

## Funzionamento

- **I selettori sono di GrantForge, i permessi root dell’operatore.** Redis permette un comando quando lo permettono i permessi root di un utente o uno dei suoi selettori. L’agente mantiene ogni selettore degli utenti di cui si occupa e non tocca mai i loro permessi root né le loro password: un selettore contiene le chiavi che l’utente può leggere, scrivere o entrambe, uno i canali a cui può iscriversi, uno quelli su cui può pubblicare, e sui nodi che può monitorare uno in più.
- **Una modifica avviene con un solo comando.** `ACL SETUSER user clearselectors (…)` sostituisce in una volta tutti i selettori di un utente su un nodo, così un utente non ha mai insieme i vecchi e i nuovi, né nessuno; i selettori cambiati a mano vengono corretti allo stesso modo alla sincronizzazione successiva.
- **Pattern, concessi solo dove valgono.** Un pattern di chiavi o canali di una policy viene concesso come pattern di Redis dove le policy decidono l’utente allo stesso modo su tutto il pattern; l’agente taglia il pattern lungo ogni policy che lo sovrappone e decide ogni parte, così la policy di un altro utente non lo trattiene. Un pattern all’interno del quale le policy decidono diversamente viene trattenuto e registrato una volta.
- **Niente di pericoloso, niente elenco delle chiavi altrui.** I selettori non danno mai ciò che Redis considera pericoloso, e danno `SCAN` e `RANDOMKEY` solo a un utente che può leggere ogni chiave.
- **Ogni nodo, salvato dove si può.** L’agente trova le repliche e i nodi del cluster a partire dai nodi indicati e allinea ciascuno. Un nodo che tiene la sua ACL in un file la salva con `ACL SAVE`; uno che non lo fa perde i selettori al riavvio e li riceve di nuovo alla sincronizzazione successiva.
- **Si chiede a Redis cosa permette.** I permessi root decidono accanto ai selettori dell’agente. Invece di leggerne le regole, l’agente chiede a Redis con `ACL DRYRUN` se un utente può eseguire un comando su una chiave, un canale o un nodo che una policy gli nega, e avvisa una volta per ciascun caso, nominando utente, accesso e policy. Avvisa anche degli utenti che nessun selettore vincola, con ogni comando su ogni chiave, e degli utenti come cui chiunque può accedere senza password.
- **I rifiuti vengono dal log ACL di ogni nodo.** Vedete Segnalare i rifiuti più sotto.
- **Le policy del momento presente.** Ogni sincronizzazione pianifica secondo le policy come si applicano in quel momento, così i periodi di validità iniziano e finiscono in orario, e secondo gli utenti e i nodi che Redis ha.

## Distribuzione

1. Aggiungete un servizio dati `redis` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, selezionate il servizio ed emettete un token di agente. È mostrato una sola volta; conservatelo in un file che solo l’account dell’agente può leggere.
2. Create l’utente ACL dell’agente su ogni nodo. Legge gli utenti, imposta i loro selettori, salva il file ACL, legge il log ACL, esegue prove a secco e trova i nodi del cluster. `+acl|setuser` può darsi qualsiasi cosa, quindi proteggete questo utente come un amministratore.

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. Limitate i permessi root degli utenti di cui si occupa l’agente a ciò che serve a ogni utente, come accedere; il resto lo dà l’agente. I permessi root che danno di più vengono trovati e segnalati.

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. Scrivete il file delle impostazioni, sostituendo indirizzi, percorsi e nomi di esempio:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   Questi percorsi sono sulla macchina dell’agente, e la directory della cache deve essere scrivibile. I file delle password sono letti come UTF-8 e ripuliti dagli spazi.
5. Avviate l’agente, ad esempio come servizio systemd:

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   Sostituite `<version>` con la versione della distribuzione. Le impostazioni passate come `java -Dgrantforge.…=… -jar …` prevalgono sul file. L’agente si ferma in modo pulito con SIGTERM e invia o accoda gli eventi in sospeso; Redis conserva ciò che ha ricevuto.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per questo agente e una directory scrivibile per lo snapshot firmato, gli eventi accodati e la posizione di lettura del log di audit |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave è ottenuta dal server al primo contatto |
| `grantforge.redis.nodes` | Obbligatoria: i nodi come `host:port`, la porta `6379` se non indicata |
| `grantforge.redis.discover` | `true` per impostazione predefinita: il resto di un cluster e le repliche di un primario vengono trovati a partire dai nodi indicati. `false` prende i nodi indicati e nessun altro, quando gli indirizzi annunciati dai nodi non sono raggiungibili dall’agente |
| `grantforge.redis.user`, `grantforge.redis.password.file` | L’utente ACL dell’agente, vuoto per l’utente predefinito, e la sua password |
| `grantforge.redis.tls.mode`, `grantforge.redis.tls.ca.file`, `grantforge.redis.tls.certificate.file`, `grantforge.redis.tls.key.file`, `grantforge.redis.tls.key.password.file` | `disable` (predefinito), `verify-ca` o `verify-full`; i certificati CA PEM che verificano i server, vuoto per ciò di cui Java si fida; un certificato client PEM, la sua chiave se in un file proprio, e la password della chiave |
| `grantforge.redis.timeout.ms` | Quanto possono durare la connessione e un comando, `10000` per impostazione predefinita, nell’intervallo `1..600000` millisecondi |
| `grantforge.redis.unmanaged.users` | Gli utenti che l’agente lascia stare, separati da virgole, come gli account di servizio. Lascia sempre stare il proprio utente e gli utenti con ogni comando su ogni chiave |
| `grantforge.sync.interval.ms` | Ogni quanto l’agente allinea Redis, predefinito `10000`, intervallo `1000..3600000` millisecondi |
| `grantforge.audit.log`, `grantforge.audit.log.interval.ms` | `true` per impostazione predefinita; `false` non legge alcun log ACL. Ogni quanto si leggono i log ACL, `5000` millisecondi per impostazione predefinita |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` invia ciascuno |

## Segnalare i rifiuti

Redis non tiene traccia di ciò che ha permesso, quindi l’agente segnala ciò che ha rifiutato, dal log ACL di ogni nodo: a chi è stato rifiutato cosa, da dove e quando, e se le policy decidono come Redis. Redis accorpa un rifiuto ripetuto entro un minuto alla voce precedente; l’agente segnala di nuovo una voce ogni volta che viene aggiornata.

| Motivo registrato | Evento di accesso |
| --- | --- |
| `key` | `read` o `write` sulla chiave, secondo come Redis contrassegna il comando |
| `channel` | `subscribe` o `publish` sul canale |
| `command` | `monitor` sul nodo per i comandi di monitoraggio; gli altri comandi vengono registrati senza le loro chiavi e sono omessi |
| `auth` | Omesso: un accesso rifiutato non nomina alcun accesso ai dati |

## Utenti, gruppi e ruoli

Le policy nominano gli utenti ACL di Redis per nome. Redis non ha ruoli né gruppi propri, quindi i gruppi sono quelli in cui GrantForge mette gli utenti.

## Decisioni e limiti

- Senza snapshot l’agente non cambia nulla. Un token legato a un servizio non in uso, o a un servizio il cui tipo non è `redis`, non concede nulla: l’agente svuota i selettori degli utenti di cui si occupa.
- Finché l’agente non è in esecuzione, Redis conserva ciò che ha ricevuto per ultimo e le modifiche alle policy attendono. Una modifica raggiunge Redis entro gli intervalli di aggiornamento e sincronizzazione.
- L’agente mantiene ogni selettore degli utenti di cui si occupa: un selettore aggiunto a mano dall’operatore viene rimosso alla sincronizzazione successiva. Date i permessi aggiuntivi nei permessi root, o lasciate l’utente all’operatore.
- L’ACL di Redis nomina chiavi e canali, non database, quindi un pattern copre le sue chiavi in ogni database di un nodo.
- Un comando rifiutato che l’utente non può eseguire affatto viene registrato da Redis senza le sue chiavi e non viene segnalato.
- Redis non filtra nulla all’interno di un valore e non maschera nulla.

## Guide correlate

- [Redis](/it/plugins/redis/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
