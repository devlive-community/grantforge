---
title: Agente Apache Hive
description: Installa l’agente in HiveServer2 per controllare le istruzioni, filtrare SHOW, mascherare colonne e filtrare righe con le policy di GrantForge.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente Hive è un autorizzatore dell’API di autorizzazione di Hive, come il plug-in Hive di Apache Ranger: HiveServer2 lo interroga prima di ogni istruzione e l’agente risponde in base alle policy firmate del servizio dati `hive`. La distribuzione lo fornisce come un unico JAR compatibile con Java 8, `agents/hive/grantforge-agent-hive-<version>.jar`, con le dipendenze rilocate al suo interno.

I test di integrazione Testcontainers del repository eseguono il JAR dentro HiveServer2 `3.1.3`, `4.0.1` e `4.1.0`. Ciò non certifica le altre versioni. Il plug-in di servizio è descritto in [Apache Hive](/it/plugins/hive/).

## Funzionamento

- **Le istruzioni vengono controllate.** Per ogni oggetto che un’istruzione legge o scrive, l’agente chiede alle policy il tipo di accesso richiesto dall’operazione, come Ranger; il primo rifiuto fa fallire l’istruzione con `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`. Le query vengono controllate colonna per colonna.
- **Gli elenchi vengono filtrati.** `SHOW DATABASES` e `SHOW TABLES` elencano solo ciò che l’utente può usare: un accesso qualsiasi al database o alla tabella, o a qualcosa al loro interno. `USE` e `DESCRIBE DATABASE` richiedono lo stesso.
- **Le query vengono mascherate e filtrate.** Se l’utente ha colonne mascherate o un filtro delle righe su una tabella, HiveServer2 riscrive la query con le maschere e la condizione, così join, condizioni e funzioni vedono solo ciò che l’utente può vedere.
- **Le maschere non si aggirano.** Scrivere, modificare o esportare una tabella su cui l’utente ha una maschera o un filtro delle righe viene rifiutato, come Ranger fa per impostazione predefinita.
- **Permessi e ruoli sono di GrantForge.** `GRANT`, `REVOKE`, `SHOW GRANT`, `CREATE ROLE`, `SET ROLE` e le altre istruzioni sui ruoli di Hive vengono rifiutate; `SHOW CURRENT ROLES` elenca i ruoli che GrantForge assegna all’utente.
- **Le istruzioni non sicure vengono rifiutate.** I comandi `dfs`, che raggiungerebbero il file system con i diritti di HiveServer2, vengono rifiutati. Nelle sessioni di HiveServer2 gli script `TRANSFORM` sono vietati e gli utenti possono modificare con `SET` solo le impostazioni consentite dall’autorizzazione SQL standard di Hive.
- **Ogni decisione viene segnalata** a GrantForge con utente, indirizzo del client, risorsa, tipo di accesso, esito, operazione e istruzione.

| Operazione | Tipo di accesso |
| --- | --- |
| Query, `SHOW CREATE TABLE`, `SHOW PARTITIONS`, `SHOW TBLPROPERTIES`, `DESCRIBE`, `SHOW COLUMNS`, `ANALYZE` | `select` |
| `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `LOAD` (destinazione) | `update` |
| `CREATE DATABASE`, `CREATE TABLE`, `CREATE VIEW`, `CREATE FUNCTION` | `create` su ciò che viene creato, `select` su ciò che viene letto |
| `ALTER …`, `MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`, `UNLOCK` | `lock` |
| `SHOW DATABASES`, `SHOW TABLES`, `USE`, `DESCRIBE DATABASE` | Un accesso qualsiasi all’oggetto o a ciò che contiene |
| `REPL DUMP`, `REPL LOAD`, `REPL STATUS` | `repladmin` |
| `KILL QUERY`, piani delle risorse, amministrazione LLAP | `serviceadmin` su `hiveservice` |
| Funzioni temporanee, `ADD JAR`, `COMPILE` | `tempudfadmin` su `global` |
| Posizioni che un’istruzione legge o scrive | `read` o `write` su `url` |

## Distribuzione

1. Aggiungi un servizio dati `hive` e configura le sue policy, tra cui una che consenta agli utenti con cui HiveServer2 svolge il proprio lavoro, come `hive`, tutto ciò di cui hanno bisogno. Apri **Permessi sui dati → Agenti**, seleziona il servizio ed emetti un token per l’agente. Viene mostrato una sola volta; salvalo in un file leggibile solo dall’account di HiveServer2.
2. Copia il JAR in `lib` di **ogni HiveServer2**, per esempio `/opt/hive/lib`.
3. Aggiungi le impostazioni a `hiveserver2-site.xml` o `hive-site.xml` di ogni HiveServer2, sostituendo indirizzo, percorsi e nome dell’istanza di esempio:

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   La directory della cache deve essere scrivibile dall’account di HiveServer2. Come con Ranger, `hive.server2.enable.doAs=false` fa leggere e scrivere i dati a HiveServer2 con il proprio account, così sono le policy a decidere chi vede cosa.
4. Riavvia HiveServer2. L’agente parte con la prima sessione e si ferma con il server, inviando o accodando gli eventi in sospeso.
5. **Chiudi le vie che aggirano HiveServer2:** l’agente controlla solo le istruzioni eseguite in HiveServer2. I client che raggiungono direttamente il metastore o i file del warehouse, come la CLI di Hive, Spark o i client HDFS, non vengono controllati; proteggili con i permessi propri del metastore e di HDFS, o con l’[agente HDFS](/it/external/hdfs-agent/).

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token dell’agente, un nome per questo HiveServer2 e una directory scrivibile per lo snapshot firmato e gli eventi accodati |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copia la chiave pubblica Ed25519 X.509 codificata in Base64 dalla pagina Agenti. Se omesso, la chiave viene ottenuta dal server al primo contatto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite della coda su disco (predefinito 64 MiB) |

L’agente legge le impostazioni dai file di configurazione di HiveServer2 e dalle proprietà di sistema (`-Dgrantforge.…`, che prevalgono), mai dalle impostazioni di una sessione, che un client può scegliere.

## Utenti, gruppi e ruoli

Le policy nominano l’utente che HiveServer2 assegna alla sessione: il nome con cui accede un client, o il nome breve del suo principal Kerberos (`alice` per `alice@EXAMPLE.COM`). I gruppi sono quelli che la mappatura dei gruppi di Hadoop assegna all’utente in HiveServer2, insieme ai gruppi in cui GrantForge inserisce l’utente; i ruoli sono quelli che GrantForge assegna all’utente.

## Decisioni e limiti

- Senza uno snapshot utilizzabile, o se le policy non possono essere lette, ogni istruzione viene rifiutata. Un servizio non in uso lascia HiveServer2 senza controlli, come senza autorizzatore.
- Un token legato a un servizio il cui tipo non è `hive` rifiuta tutto.
- Le policy `url` decidono le posizioni che le istruzioni leggono e scrivono; a differenza di Ranger, l’agente non ricorre ai permessi del file system.
- Le istruzioni per cui Hive non nomina alcun oggetto vengono controllate su ogni database: le istruzioni `REPL` senza oggetto richiedono `repladmin` e il cambio di proprietario di una tabella o di un database senza oggetto richiede `alter`, sul database `*`.
- `EXPLAIN`, `DESCRIBE FUNCTION`, `SHOW FUNCTIONS`, `SET` e le altre istruzioni che Ranger non controlla non vengono controllate nemmeno qui; le chiamate di metadati dei client JDBC, come `getTables`, non vengono filtrate.
- L’agente opera in HiveServer2. L’autorizzazione all’interno del metastore non è coperta.

## Guide correlate

- [Apache Hive](/it/plugins/hive/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
