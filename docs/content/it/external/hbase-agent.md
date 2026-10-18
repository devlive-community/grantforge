---
title: Agente Apache HBase
description: Installare il coprocessore nei master e nei region server HBase per controllare operazioni, letture e scritture con le policy di GrantForge, restituire solo le colonne che un utente può leggere e nascondere ciò che non può usare.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente HBase è un coprocessore di master, regioni e region server: HBase lo interroga per ogni operazione, lettura e scrittura, e risponde in base alle policy firmate del servizio dati `hbase`. La distribuzione lo fornisce come un unico JAR compatibile con Java 8, `agents/hbase/grantforge-agent-hbase-<version>.jar`, con le dipendenze rilocate al suo interno.

I test di integrazione Testcontainers del repository eseguono il JAR in HBase `2.5.16`, `2.6.7` e `3.0.0`. Ciò non certifica ogni altra versione. Per il plug-in di servizio vedi [Apache HBase](/it/plugins/hbase/).

## Funzionamento

- **Ogni richiesta viene controllata in ogni ambito che nomina.** In HBase un permesso copre tutto ciò che rientra nel suo ambito, quindi l’agente decide una richiesta nel suo namespace, nella sua tabella, nella sua famiglia di colonne e nella sua colonna: un divieto in uno qualsiasi la rifiuta, un permesso in uno qualsiasi la concede.
- **I proprietari di un namespace ne hanno le tabelle.** `create` e `admin` su un namespace consentono di crearlo, di crearvi tabelle e di amministrarle, senza una policy per tabella.
- **Le letture restituiscono ciò che può essere letto.** Quando un utente può leggere solo una parte di una tabella, come una famiglia di colonne, un get o uno scan restituisce quelle colonne invece di fallire; un utente che non può leggere nulla della tabella viene rifiutato.
- **Gli elenchi omettono ciò che non può essere usato.** Elencare namespace e tabelle mostra solo quelli che l’utente può usare, o in cui può usare qualcosa, ed elencare gli snapshot solo quelli delle tabelle che amministra; descrivere un namespace che non può usare viene rifiutato.
- **I rifiuti dicono perché.** Il client riceve `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)`, oppure la policy che l’ha negato.
- **Le decisioni vengono conservate e gli accessi raggruppati.** Le decisioni dello snapshot corrente vengono conservate, per impostazione predefinita 100.000 per 10 secondi, e un nuovo snapshot riparte da zero. Gli accessi consentiti identici entro una finestra, predefinita 10 secondi, vengono segnalati come un unico evento di accesso che indica quanti sono stati; i rifiuti vengono segnalati uno per uno, subito.
- **I server non vengono controllati.** I superutenti di HBase, indicati in `hbase.superuser`, e il lavoro dei server stessi non vengono controllati né sottoposti ad audit. Le tabelle del namespace `hbase`, come `hbase:meta`, sono leggibili da tutti, come serve ai client.

| Operazione | Accesso necessario |
| --- | --- |
| Get, scan | `read` sulla tabella, o sulle colonne restituite |
| Put, increment, append, delete di colonne | `write` su ogni colonna scritta |
| Delete di una famiglia di colonne o di una riga | `write` su ogni famiglia di colonne, poiché HBase elimina una riga famiglia per famiglia |
| Check-and-mutate | `read` sulla colonna confrontata, o sulla tabella con un filtro, e `write` su ciò che viene scritto |
| Creare una tabella | `create` sul namespace |
| Eliminare, troncare, modificare, abilitare, disabilitare, flush, split, merge, compattare | `admin` o `create` sulla tabella |
| Spostare, assegnare, revocare l’assegnazione, mettere offline | `admin` sulla tabella |
| Creare, ripristinare, eliminare o elencare uno snapshot | `admin` sulla tabella; la clonazione richiede anche `create` sul namespace di destinazione |
| Caricamento massivo | `create` sulla tabella |
| Creare, modificare, eliminare un namespace | `admin` sul namespace |
| Descrivere un namespace; elencare namespace e tabelle | Qualsiasi accesso a esso o al suo interno |
| Bilanciatore, arresto, quote, peer di replica, gruppi di region server, procedure, dismissione, arresto dei region server, rotazione dei WAL | `admin` su `global` |

## Distribuzione

1. Aggiungi un servizio dati `hbase` e configura le sue policy. Apri **Permessi sui dati → Agenti**, seleziona il servizio ed emetti un token dell’agente. Viene mostrato una sola volta; salvalo in un file leggibile solo dall’account di HBase.
2. Copia il JAR in `lib` di **ogni master e region server**, ad esempio `/opt/hbase/lib`.
3. Aggiungi le impostazioni all’`hbase-site.xml` di ciascuno, sostituendo indirizzo, percorsi e nome dell’istanza di esempio:

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   La directory della cache deve essere scrivibile dall’account di HBase. Sostituisci l’`AccessController` di HBase negli elenchi dei coprocessori invece di caricarli entrambi.
4. Riavvia master e region server uno alla volta. Finché l’agente non ha il primo snapshot, le richieste diverse da quelle dei superutenti vengono rifiutate.
5. **Migra prima i permessi esistenti:** i permessi che l’`AccessController` di HBase ha memorizzato in `hbase:acl` non si applicano più. Elencali prima con `user_permission` e crea le policy corrispondenti.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token dell’agente, un nome per questo master o region server e una directory scrivibile per lo snapshot firmato e gli eventi accodati |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copia la chiave pubblica Ed25519 X.509 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave viene ottenuta dal server al primo contatto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo gli accessi consentiti identici vengono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` li segnala uno per uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Numero massimo di decisioni conservate, predefinito `100000`, fino a `10000000`, `0` non ne conserva; e per quanto tempo, predefinito `10000`, `1..3600000` millisecondi |

HBase avvia un coprocessore per il master, per il region server e per ogni regione che apre; tutti condividono un solo agente, una cache delle decisioni e il raggruppamento degli eventi.

## Utenti, gruppi e ruoli

Le policy nominano il nome breve che HBase assegna all’utente della connessione: il nome utente dell’autenticazione simple, o il nome breve di un principal Kerberos secondo `hadoop.security.auth_to_local`. I gruppi sono quelli che la mappatura dei gruppi di Hadoop assegna all’utente sul server, insieme ai gruppi in cui GrantForge lo inserisce; i ruoli sono quelli che GrantForge gli assegna.

## Decisioni e limiti

- Senza uno snapshot utilizzabile, tutto viene rifiutato tranne le richieste dei superutenti e le letture del namespace `hbase`. Un servizio non in uso lascia HBase senza controlli, come senza autorizzatore.
- Un token legato a un servizio il cui tipo non è `hbase` rifiuta tutto.
- I comandi `grant`, `revoke` e `user_permission` di HBase falliscono con *AccessController is not loaded*: i permessi sono policy di GrantForge.
- Le chiamate agli endpoint dei coprocessori non vengono controllate; carica solo endpoint di cui ti fidi.
- Qui HBase non dà al creatore di una tabella alcun permesso proprio; una policy di namespace, come sopra, dà ai proprietari le loro tabelle.
- HBase registra alcuni rifiuti invece di restituirli, come quello dell’interruttore del bilanciatore: l’interruttore resta com’era, ma il client non viene avvisato.
- Le decisioni vengono conservate per `grantforge.decision.cache.ttl.ms`, quindi il periodo di validità di una policy inizia o finisce con un ritardo fino a tale durata.
- I permessi a livello di cella e le etichette di visibilità di HBase non sono coperti. Le colonne vengono confrontate come testo UTF-8.

## Guide correlate

- [Apache HBase](/it/plugins/hbase/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
