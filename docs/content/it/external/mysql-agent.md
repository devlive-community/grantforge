---
title: Agente proxy MySQL
description: Distribuisci il proxy di rete MySQL, configura TLS e la sincronizzazione delle policy e verifica permessi sulle colonne, mascheramento, filtri delle righe ed errori di accesso negato.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente MySQL è un proxy di rete indipendente: i client si collegano al proxy al posto del server, e il proxy verifica le istruzioni supportate rispetto alle policy firmate del servizio di dati `mysql` prima che raggiungano MySQL. Non richiede un’installazione all’interno di MySQL. La distribuzione include il JAR eseguibile `agents/mysql/grantforge-agent-mysql-<version>.jar`, che richiede Java 17 o successivo.

I test di integrazione Testcontainers del repository usano `mysql:8.0` e `mysql:8.4` con client MariaDB Connector/J e MySQL Connector/J. Questo non certifica altre versioni del server né tutte le funzionalità del protocollo MySQL. Consulta [MySQL](/it/plugins/mysql/) per i requisiti di versione e connessione del plug-in di servizio.

## Funzionamento

- **L’autenticazione viene inoltrata.** Il messaggio iniziale del server raggiunge il client e le risposte del client raggiungono il server, quindi è MySQL a verificare le password (`caching_sha2_password` e `mysql_native_password`); il proxy apprende l’utente accettato da MySQL. Le policy indicano questo nome utente, senza la parte host.
- **Le risorse SQL vengono verificate.** Il proxy analizza COM_QUERY e le istruzioni preparate lato server e controlla i tipi di accesso richiesti su database, tabelle e colonne. Un’istruzione rifiutata non raggiunge MySQL; il proxy genera un errore con un codice compatibile con MySQL, come 1142 o 1143.
- **Le letture vengono mascherate e filtrate.** Quando un’istruzione legge una tabella con colonne mascherate o un filtro delle righe per l’utente, il proxy la sostituisce con una tabella derivata dello stesso nome che mostra i valori mascherati e solo le righe filtrate. Join, condizioni e funzioni vedono così ciò che l’utente può vedere.
- Gli **eventi di autorizzazione delle risorse** vengono inviati a GrantForge con utente, indirizzo del client, risorsa, tipo di accesso, decisione e istruzione. L’ambito dell’audit è descritto di seguito.
- I risultati dell’esecuzione MySQL vengono inoltrati al client; il proxy non modifica i valori originali memorizzati nel database.

## Distribuzione

1. Aggiungi un servizio di dati `mysql` e configura le sue policy. Apri **Permessi sui dati → Agenti**, seleziona il servizio ed emetti un token di agente. Viene mostrato una sola volta; conservalo in un file leggibile solo dall’account del proxy.
2. Crea un utente di metadati per il proxy. Legge le colonne delle tabelle e le funzioni memorizzate da `information_schema` e non ha bisogno dei dati: `REFERENCES` rende visibili tutte le colonne, `SHOW_ROUTINE` tutte le funzioni memorizzate. Concedi questi privilegi direttamente all’utente, non tramite un ruolo; senza `SHOW_ROUTINE` il proxy non si avvia.

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. Scrivi il file di configurazione, sostituendo indirizzi, percorsi e nomi di esempio:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   Questo esempio attiva TLS dai client al proxy e dal proxy a MySQL. `mysql-proxy.crt` deve corrispondere al nome host del proxy usato dai client; il certificato MySQL deve corrispondere a `mysql.internal`. I percorsi sono sulla macchina del proxy e la directory della cache deve essere scrivibile. I file delle password vengono letti in UTF-8 e gli spazi iniziali e finali vengono rimossi; le password che richiedono questi spazi non possono essere memorizzate in questo modo.

4. Avvia il proxy, per esempio come servizio systemd:

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   Sostituisci `<version>` con la versione della distribuzione. Le impostazioni passate come `java -Dgrantforge.…=… -jar …` prevalgono sul file. Il proxy termina correttamente su SIGTERM e invia o conserva su disco gli eventi in attesa.
5. Indirizza i client all’indirizzo e alla porta del proxy. MySQL ora vede l’indirizzo del proxy, quindi la parte host degli account deve consentirlo.
6. **Chiudi il percorso che aggira il proxy:** i client che raggiungono direttamente MySQL non vengono verificati. Consenti solo al proxy e agli amministratori di raggiungere la porta del server.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatori: server GrantForge, file del token di agente, nome del proxy e una directory scrivibile per lo snapshot firmato e gli eventi conservati su disco |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copia dalla pagina Agenti la chiave pubblica Ed25519 X.509 codificata in Base64. Se omesso, la chiave viene recuperata dal server al primo contatto |
| `grantforge.proxy.host`, `grantforge.proxy.port` | Indirizzo di ascolto; tutti gli indirizzi e porta `3307` per impostazione predefinita. Intervallo `0..65535`; `0` assegna automaticamente una porta |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | Certificato e chiave privata PEM, entrambi necessari insieme per offrire TLS ai client |
| `grantforge.proxy.tls.required` | Valore predefinito `false`; `true` rifiuta i client senza TLS (errore 3159) e richiede certificato e chiave privata |
| `grantforge.mysql.host`, `grantforge.mysql.port` | Host MySQL obbligatorio; porta predefinita `3306`, intervallo `1..65535` |
| `grantforge.mysql.tls` | TLS verso MySQL: `disabled` (predefinito), `required` (cifrato, non verificato), `verify-ca` o `verify-identity` |
| `grantforge.mysql.tls.ca` | CA PEM che verifica MySQL; vuoto usa le autorità considerate attendibili da Java |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | Utente di metadati obbligatorio; il file della password è facoltativo e, se omesso, al driver non viene passata una password |
| `grantforge.mysql.metadata.cache.seconds` | Durata della cache di colonne e funzioni memorizzate: valore predefinito `30`, intervallo `0..86400` secondi; `0` ne disattiva il riutilizzo |
| `grantforge.mysql.connect.timeout.ms` | Timeout di connessione MySQL: valore predefinito `10000`, intervallo `1..600000` millisecondi; le letture dei metadati hanno un timeout fisso di `30000` millisecondi |
| `grantforge.proxy.check.threads` | Thread di verifica delle istruzioni: valore predefinito `8`, intervallo `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura GrantForge: valori predefiniti `5000` / `8000` millisecondi, intervallo `1..2147483647` |
| `grantforge.refresh.interval.ms` | Intervallo iniziale o di ripiego quando il server è irraggiungibile: valore predefinito `30000` millisecondi, minimo `1000`; gli heartbeat normali usano l’intervallo restituito dal server |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | Batch predefinito `500`, coda `10000` eventi; intervallo del batch `1..1000`, coda almeno pari al batch e al massimo `1000000` |
| `grantforge.audit.flush.interval.ms` | Intervallo di invio dell’audit: valore predefinito `5000` millisecondi, intervallo `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | Limite di archiviazione su disco degli eventi di audit: valore predefinito `67108864` (64 MiB), minimo `0` |

TLS sui due lati è indipendente. Quando solo un lato lo usa e MySQL richiede la password completa (`caching_sha2_password` dopo un mancato riscontro nella cache), il proxy la trasferisce: risponde a un client senza TLS che richiede la chiave del server con una propria chiave RSA e cifra la password di un client TLS con la chiave MySQL per un server senza TLS. I client non devono essere configurati con il file della chiave pubblica MySQL quando passano attraverso il proxy.

## Accessi richiesti dalle istruzioni

| Istruzione | Accesso |
| --- | --- |
| `SELECT`, sottoquery, `WITH`, `EXPLAIN`, `SET @x = (SELECT …)` | `select` su ogni colonna usata; una lettura senza colonne, come `COUNT(*)` o `EXISTS`, richiede `select` sull’intera tabella |
| `INSERT`, `LOAD DATA` | `insert` sulle colonne scritte (tutte se nessuna è elencata); `ON DUPLICATE KEY UPDATE` aggiunge `update` |
| `REPLACE` | `insert` sulle colonne e `delete` sulla tabella |
| `UPDATE` | `update` sulle colonne impostate e `select` sulle colonne lette |
| `DELETE` | `delete` sulla tabella e `select` sulle colonne lette |
| `CREATE TABLE`, `CREATE VIEW` / `DROP TABLE`, `DROP VIEW`, `TRUNCATE` / `ALTER TABLE`, `ALTER VIEW`, `OPTIMIZE` | `create` / `drop` / `alter` sulla tabella o vista |
| `RENAME TABLE` | `alter` e `drop` sul vecchio nome, `create` e `insert` sul nuovo |
| `CREATE INDEX`, `DROP INDEX` | `index` sulla tabella |
| `CREATE DATABASE`, `DROP DATABASE` | `create`, `drop` sul database |
| `LOCK TABLES`, `ANALYZE`, `CHECK`, `CHECKSUM TABLE` | `select` sulla tabella |

Le istruzioni che non usano dati passano ai privilegi nativi di MySQL: transazioni, `SET`, `USE`, `SHOW`, `DESCRIBE`, `KILL` e istruzioni sugli account come `GRANT` e `CREATE USER`. `information_schema` e `performance_schema` sono database come gli altri e richiedono policy.

Il proxy rifiuta le istruzioni che non può verificare completamente: commenti eseguibili (`/*! … */`), funzioni memorizzate e `CALL`, `CREATE PROCEDURE`, `FUNCTION`, `TRIGGER` e `EVENT`, `PREPARE`/`EXECUTE` a livello SQL, `HANDLER`, comandi di replica e clonazione, `USE` o `SET NAMES` insieme ad altre istruzioni nello stesso batch e istruzioni che non può analizzare.

## Maschere, filtri delle righe e relativi limiti

- Maschere e filtri si applicano ovunque un’istruzione legga la tabella, incluse sottoquery, join, `INSERT … SELECT` e `CREATE TABLE … SELECT` (la cui copia contiene i valori mascherati).
- Un `UPDATE` o `DELETE` su una singola tabella modifica solo le righe mostrate dal filtro. Sono rifiutati `UPDATE` o `DELETE` multitabella su una tabella filtrata, così come una scrittura che usa una colonna mascherata della tabella modificata, perché potrebbe copiare o sondare i valori nascosti.
- Non è possibile creare né modificare una vista su una tabella mascherata o filtrata: MySQL esegue la vista in seguito, senza il proxy.
- Le istruzioni preparate riutilizzano verifica e riscrittura finché la versione delle policy non cambia. Una nuova versione provoca un’altra verifica: l’esecuzione continua se l’accesso è ancora consentito e il testo riscritto non cambia; altrimenti l’errore 1615 richiede al client di preparare nuovamente l’istruzione.
- Una colonna indicata senza la tabella viene considerata per ogni tabella visibile che abbia una colonna con quel nome; qualifica le colonne per evitare rifiuti dovuti a una colonna limitata con lo stesso nome.
- Le colonne lette tramite `*` provengono dalla vista di `information_schema` dell’utente di metadati e restano in cache per `grantforge.mysql.metadata.cache.seconds`. Un DDL riuscito inviato attraverso il proxy come comando SQL ordinario invalida la cache: la ricerca successiva rilegge i metadati. Non è garantita l’invalidazione durante l’esecuzione di DDL esterno o preparato; attendi la scadenza della cache o riavvia il proxy.

## Ambito di applicazione delle policy

Il proxy verifica le risorse che può analizzare nel SQL del client. Non espande ricorsivamente viste esistenti, trigger o altre operazioni eseguite internamente dal server. Per proteggere le tabelle sottostanti, limita anche i privilegi nativi MySQL e l’uso di viste e trigger esistenti. Non concedere privilegi amministrativi agli utenti di business aspettandoti che il proxy intercetti le istruzioni amministrative.

Un filtro delle righe non è un vincolo generale sulle scritture: `INSERT`, `REPLACE` e `LOAD DATA` verificano i permessi di scrittura, ma non richiedono automaticamente che le nuove righe soddisfino l’espressione del filtro. Neppure al ramo di `INSERT … ON DUPLICATE KEY UPDATE` che aggiorna righe esistenti viene aggiunta una condizione di filtro delle righe. Compressione del protocollo, ZSTD, attributi delle query e metadati facoltativi dei risultati vengono disattivati durante la negoziazione.

## Policy e guasti

- Senza uno snapshot utilizzabile o con un token legato a un servizio che non è `mysql`, tutte le istruzioni che usano dati vengono rifiutate.
- Se GrantForge è temporaneamente irraggiungibile o la firma di un nuovo snapshot non è valida, il proxy conserva lo snapshot già applicato. Un primo avvio senza alcuno snapshot utilizzabile rifiuta l’accesso alle risorse.
- Disattivare il servizio salta l’autorizzazione delle risorse, il mascheramento e il filtro delle righe di GrantForge; l’analisi SQL e le restrizioni del protocollo continuano ad applicarsi. Disattivare il servizio non blocca l’accesso ai dati.
- I privilegi nativi MySQL continuano ad applicarsi dopo le verifiche del proxy; il proxy può solo restringerli.
- `COM_CHANGE_USER` viene seguito e rifiutato quando il lato client e il lato server differiscono nell’uso di TLS; collegati nuovamente con l’altro utente.

## Esempio di verifica

In un database di test separato, chiedi a un amministratore del database di creare i dati e l’account seguenti. Sostituisci `proxy-host` con l’indirizzo sorgente del proxy effettivamente visto da MySQL:

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

Crea tre policy GrantForge per `alice`: consenti `select` su `database=grantforge_demo`, `table=orders`, `column=*`; maschera `card` con `mask_show_last_4`; filtra la tabella con `region = 'EU'`. Mantieni attivi il servizio e tutte e tre le policy. Attendi che **Permessi sui dati → Agenti** mostri che è stata applicata l’ultima versione delle policy.

Collegati al proxy con il client MySQL da riga di comando. `-p` richiede la password; il file della CA deve considerare attendibile il certificato del proxy:

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- One row only: 1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000: GrantForge does not grant update
```

In questo esempio i privilegi nativi MySQL consentono già `UPDATE`, quindi il suo rifiuto verifica che il proxy applica la policy. Cambia poi il filtro delle righe in `region = 'US'`, attendi che il proxy applichi la nuova versione e ripeti la query. Deve apparire solo la riga 2, con il valore della carta `xxxxxxxxxxxx4444`.

## Risoluzione dei problemi e audit

| Codice di errore / SQLSTATE | Significato e verifiche |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | Accesso al database, alla tabella o alla colonna negato. Per i messaggi che contengono `by GrantForge`, verifica utente della policy, risorse, tipo di accesso e versione dello snapshot; per altri rifiuti verifica anche i privilegi nativi MySQL |
| `1046 / 3D000` | Nessun database selezionato; usa `USE`, un database predefinito nella connessione o un nome di tabella qualificato |
| `1227 / 42000` | Un’operazione è vietata o non può essere verificata in sicurezza; usa il messaggio per controllare restrizioni delle istruzioni e privilegi sui metadati |
| `1064 / 42000` | Il SQL non può essere analizzato |
| `1615 / HY000` | Prepara nuovamente l’istruzione dopo una modifica delle policy |
| `3159 / HY000` | Il proxy richiede TLS, ma il client si è collegato senza TLS |
| `1043 / 08S01` | Errore del protocollo o della negoziazione TLS con il server di backend; verifica modalità TLS, certificati, nome host e funzionalità del server |
| `2003 / HY000` | Il proxy non può collegarsi a MySQL; verifica indirizzo, rete e stato del backend |

In **Permessi sui dati → Audit degli accessi**, cerca gli eventi di accesso consentito e negato per `alice` e le risorse corrispondenti. Una singola istruzione SQL può produrre più eventi di autorizzazione delle risorse. Vengono registrati prima dell’esecuzione nel backend, quindi `ALLOWED` non significa che MySQL abbia eseguito correttamente. Errori di analisi, istruzioni senza accesso alle risorse e servizi disattivati potrebbero non produrre eventi di risorsa; le esecuzioni ripetute di un’istruzione preparata con la stessa versione delle policy non ripetono gli eventi di autorizzazione. Questo è un audit dell’autorizzazione delle risorse, non un registro completo dell’esecuzione SQL.

## Guide correlate

- [MySQL](/it/plugins/mysql/)
- [Servizi di dati, policy e agenti](/it/external/data-services/)
