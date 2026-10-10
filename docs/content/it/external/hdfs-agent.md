---
title: Agente NameNode Apache Hadoop HDFS
description: Scegliere l’agente NameNode corrispondente alla versione di Hadoop, applicare le policy sui percorsi di GrantForge e segnalare l’audit degli accessi.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in del server `grantforge-plugin-hdfs` fornisce in modo unificato il tipo di servizio `hdfs`, le risorse e la configurazione di connessione. Gli agenti NameNode vengono costruiti separatamente per ciascuna versione di Hadoop, verificano gli accessi tramite l’`INodeAttributeProvider` e l’`AccessControlEnforcer` della versione corrispondente e condividono la logica di policy firmate, cache, valutazione delle policy e audit.

Nel codice sorgente, il plug-in del server si trova in `plugins/grantforge-plugin-hdfs`, gli agenti per versione in `agents/grantforge-agent-hdfs-<line>` e la logica di produzione comune indipendente da Hadoop in `agents/grantforge-agent-hdfs-common`. Gli adattatori nativi condivisi si trovano nel modulo di produzione Maven `agents/grantforge-agent-hdfs-native`, con baseline Java 8 / Hadoop 2.7.7; i singoli moduli numerati lo referenziano tramite una dipendenza binaria Maven, conservando i punti di ingresso e i callback specifici di ciascuna versione, senza ricompilare il codice di produzione condiviso. Il protocollo, la cache delle istantanee e l’infrastruttura di audit restano in `core/grantforge-agent-core`.

Il plug-in del server mantiene un unico tipo `hdfs` e la versione del client è indipendente dalla versione degli agenti. Per Hadoop 2.x, la connessione e la consultazione dei percorsi usano `webhdfs://namenode:50070` (con HTTPS attivo, l’indirizzo `swebhdfs://` corrispondente); Hadoop 3.x può usare RPC `hdfs://` o WebHDFS. La matrice di container verifica WebHDFS su 2.x e sia RPC sia WebHDFS su 3.x; RPC su 2.x non è certificato e il protocollo di connessione configurato non viene commutato automaticamente.

Apache Hadoop 2.7.7, con le estensioni degli attributi inode attivate, genera un puntatore nullo nel codice nativo quando un utente normale consulta direttamente il percorso radice `/`, prima del callback dell’agente. Per questa versione, le operazioni sui dati e il `lookup.path` del servizio devono usare directory di dati reali, per esempio `/data`; il test conserva un’asserzione separata per il fallimento sul percorso radice.

## Scelta della versione

| Suffisso del modulo agente | Apache Hadoop validato | JVM del container | Interfacce di autorizzazione | Callback superutente con percorso |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | A parametri | No |
| `2.10` | 2.10.2 | Java 8 | A parametri | No |
| `3.2` | 3.2.4 | Java 8 | A parametri | No |
| `3.3` | 3.3.6 | Java 8 | A contesto | No |
| `3.4` | 3.4.3 | Java 11 | A contesto, con callback superutente e di diniego | Sì |
| `3.5` | 3.5.0 | Java 17 | A contesto, con callback superutente e di diniego | Sì |

Per esempio, per Hadoop 2.10.2 si installa `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar`. Su ogni NameNode si installa una sola versione dell’agente; in caso di aggiornamento si rimuove il jar precedente e il nome della classe provider nella configurazione resta invariato. All’avvio l’agente verifica la versione principale e secondaria di Hadoop e i callback obbligatori; se viene installata la versione sbagliata, l’avvio viene interrotto. Le altre patch della stessa versione principale/secondaria e i rami dei produttori richiedono comunque una validazione separata.

`common` e gli agenti da `2.7` a `3.4` usano bytecode Java 8 per il codice principale, mentre `3.5` usa Java 17. La tabella precedente elenca le JVM su cui sono state eseguite le integrazioni di test reali; non significa che 3.4.3 sia stato validato su Java 8. L’immagine di test ufficiale di 3.3.6 è disponibile solo per amd64 e su host ARM viene eseguita esplicitamente in emulazione amd64.

## Rapporto con i permessi

L’agente esegue prima il controllo dei permessi nativo di HDFS e poi le policy di GrantForge: l’utente deve soddisfare contemporaneamente i requisiti dei permessi nativi e quelli delle policy. Le policy di consenso di GrantForge non aggirano i permessi POSIX, le ACL, il controllo del proprietario né lo sticky bit; le policy di diniego negano sempre. Le impostazioni dei permessi nativi restano gestite tramite gli strumenti di amministrazione di Hadoop.

Per impostazione predefinita `grantforge.hdfs.native.fallback=false`: quando non c’è un’istantanea delle policy locale, non c’è una policy corrispondente o l’agente non è ancora avviato, l’accesso ai dati viene negato. Se si imposta `true`, gli accessi che nessuna policy decide usano i permessi nativi; le policy di diniego esplicite restano valide. Quando il server è temporaneamente non raggiungibile, si continua a usare l’ultima istantanea locale che ha superato la verifica della firma.

Le proiezioni di antenati, bersaglio, sottoalbero e percorsi di istantanea di una stessa callback di autorizzazione usano la stessa versione dell’istantanea delle policy; le policy aggiornate entrano in vigore dalla callback successiva, per evitare di combinare regole di consenso di versioni diverse. L’audit degli accessi registra la versione delle policy effettivamente usata.

L’agente verifica i permessi `read`, `write` ed `execute` di cui un utente normale ha bisogno per accedere al bersaglio, e verifica anche le directory padre, le directory antenate e le sottodirectory che richiedono una verifica ricorsiva. Operazioni come creare, eliminare e rinominare riguardano più percorsi, quindi le policy di consenso devono coprirli tutti. In modalità rigorosa, non basta la policy `read` sul file bersaglio: occorre configurare per l’utente la policy `execute` sulle directory antenate, per esempio consentire `execute` su `/` e spuntare la ricorsione, e poi configurare i permessi di lettura e scrittura sulla directory dei dati reale.

I percorsi di istantanea vengono verificati sia sul percorso effettivamente richiesto sia sul percorso originale senza `.snapshot/<nome-istantanea>`, per esempio `/data/.snapshot/s1/secret` verifica contemporaneamente `/data/secret`. La policy di diniego sul percorso originale vincola quindi anche l’istantanea; si possono inoltre impostare limitazioni più rigorose per il percorso di istantanea esplicito. Le consultazioni dei metadati seguono la semantica dei permessi di attraversamento delle directory di HDFS.

Un’autorizzazione ricorsiva verifica al massimo `100000` inode; superato il limite l’operazione viene negata, per evitare allocazioni di memoria illimitate dentro il NameNode. I percorsi molto lunghi vengono valutati con il percorso completo; la visualizzazione della risorsa nell’audit è limitata a `1000` caratteri e nel dettaglio della richiesta vengono registrati la lunghezza originale e il digest SHA-256.

Hadoop 2.7, 2.10, 3.2 e 3.3 saltano i superutenti prima di chiamare l’agente, quindi l’agente non può controllare né verificare questi accessi. I callback superutente con percorso di Hadoop 3.4 e 3.5 passano prima dal controllo nativo e poi verificano le policy in base al nome dell’operazione: lettura di file e consultazioni di metadati richiedono `read`, l’enumerazione delle directory richiede `read` + `execute` e le operazioni di modifica note richiedono `write`. Le operazioni sconosciute, mancanti o non deducibili con precisione (per esempio `checkAccess` e `concat`) richiedono in modo conservatore tutti e tre i permessi.

I callback superutente non hanno il contesto completo di inode e sottoalbero; le chiamate di amministrazione del cluster senza percorso mantengono il controllo nativo; non è possibile limitare tutte le operazioni ricorsive del superutente con policy su sottodirectory. Chi usa i dati deve farlo con utenti Hadoop normali.

## Metriche

L’agente segnala le metriche tramite il sistema Metrics2 di Hadoop, usando gli stessi sink delle metriche dfs del NameNode stesso, e nel JMX del NameNode compare come `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (l’esportatore JMX di Prometheus può raccoglierlo direttamente). Ogni metrica porta le etichette `instance` (nome dell’istanza del servizio di dati) e `agentVersion`, così i due NameNode di una HA si possono consultare separatamente. Se la registrazione delle metriche fallisce si perdono solo le metriche: l’agente registra un avviso e continua a eseguire le autorizzazioni senza metriche.

| Metrica | Spiegazione |
| --- | --- |
| `Callbacks` | Numero di callback di autorizzazione eseguiti dall’agente |
| `SuperuserCallbacks` | Numero di callback superutente eseguiti dall’agente |
| `NativeDenies` | Numero di accessi negati da Hadoop prima dell’agente |
| `EvaluationFailures` | Numero di callback chiusi in modo sicuro per un’eccezione nella valutazione delle policy |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Numero di permessi che le policy decidono consentito / negato / non determinato; in modalità rigorosa anche il non determinato viene negato |
| `MissingSnapshots` | Numero di callback serviti senza un’istantanea delle policy verificata |
| `SnapshotVersion` | Versione dell’istantanea delle policy attualmente in uso; 0 significa che non ce n’è |
| `QueuedEvents` / `DroppedEvents` | Numero di eventi di audit in attesa di essere inviati in memoria / numero di eventi scartati perché la coda o il buffer su disco sono pieni |
| `ServerReachable` | Se l’ultimo accesso al server delle policy ha avuto successo (1/0) |

## Distribuzione

1. Aggiungi un servizio `hdfs` nei servizi di dati di GrantForge, salva la configurazione e prova la connessione; configura le policy sui percorsi per i nomi utente corti, i gruppi di utenti o i ruoli reali di Hadoop.
2. In “Permessi sui dati → Agenti”, emetti un token per questo servizio. Scrivi il token originale in un file locale su ogni NameNode, per esempio `/etc/hadoop/grantforge/token`, leggibile dall’utente con cui gira il NameNode.
3. Scegli dalla tabella precedente il jar dell’agente corrispondente nella directory `agents/hdfs/<linea-versione-Hadoop>/` del pacchetto di rilascio e mettilo nel classpath del NameNode, per esempio in `$HADOOP_HOME/share/hadoop/hdfs/lib/`. Il jar dell’agente contiene già il proprio motore delle policy, Jackson e la libreria di firma; le classi di Hadoop sono fornite dal NameNode. La versione dell’agente nel battito include la versione del prodotto e la versione di Hadoop con cui è stato compilato.
4. Configura le seguenti proprietà nell’`hdfs-site.xml` di ogni NameNode; i due NameNode di una HA usano `instance` diversi e le proprie directory di cache locali.

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. Verifica che `dfs.permissions.enabled=true` e che `dfs.namenode.inode.attributes.provider.bypass.users` sia vuoto; all’avvio l’agente rifiuta le configurazioni che permetterebbero di aggirare le callback di autorizzazione. Riavvia il NameNode e poi controlla il battito e la versione delle policy nella pagina degli agenti di GrantForge. L’agente legge la configurazione esistente del NameNode; non modifica gli attributi nativi degli inode.

Per una prima distribuzione si può iniziare con `native.fallback=true`, confermare che l’istantanea delle policy è sincronizzata e completare i permessi sulle directory antenate, per poi passare alla modalità rigorosa. Il tipo di servizio vincolato al token deve essere `hdfs`; una configurazione errata o vincolata a un altro tipo di servizio nega l’accesso.

## Impostazioni opzionali

| Proprietà | Valore predefinito | Uso |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Timeout di connessione a GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Timeout di lettura della risposta |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Intervallo di aggiornamento delle policy quando il server non è raggiungibile, minimo `1000`; il battito normale usa l’intervallo consigliato dal server |
| `grantforge.hdfs.signing.key.file` | non impostato | File opzionale con la chiave pubblica di firma, contenente la chiave pubblica X.509 Base64 fornita dalla console; una volta configurato vengono accettate solo le firme di quella chiave pubblica |
| `grantforge.hdfs.audit.batch.size` | `500` | Numero massimo di eventi per ogni invio, intervallo `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | Capacità della coda di audit in memoria, intervallo `1..1000000`, almeno sufficiente a contenere un batch; quando la coda è piena, conta e scarta i nuovi eventi |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Intervallo di flush dell’audit, intero positivo, massimo `2147483647`; ridurlo abbassa la latenza di invio |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Limite del buffer su disco quando il server non è raggiungibile, intero non negativo; `0` disabilita il buffer su disco |

Quando l’accesso arriva a raffiche molto forti si può aumentare la coda di audit per ridurre gli overflow della coda; accorciare l’intervallo di flush abbassa la latenza dell’audit e aumenta anche la frequenza di invio. Il limite del buffer su disco controlla l’occupazione di disco durante disconnessioni prolungate; quando il buffer è disabilitato o esaurito, alcuni eventi possono andare persi. L’invio dell’audit viene eseguito in background e non attende la risposta del server delle policy.

Se la chiave pubblica di firma non è configurata, l’agente la ottiene dal server la prima volta e la conserva insieme all’istantanea. L’agente usa il nome utente corto e i gruppi passati da Hadoop; i ruoli e i gruppi aggiuntivi provengono dall’istantanea firmata. La mappatura del nome corto del principal Kerberos è decisa dal `hadoop.security.auth_to_local` del cluster.

## Compilazione e verifica dal codice sorgente

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

L’artefatto dell’agente si trova in `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`. I test unitari coprono le callback di autorizzazione del NameNode, la configurazione, i metadati di versione e le decisioni delle policy; i test WebHDFS e Kerberos avviano servizi temporanei locali. Prima della messa in produzione occorre inoltre verificare sul cluster di destinazione lettura e scrittura, creazione, rinomina, eliminazione ricorsiva, commutazione HA e comportamento della cache dopo la perdita di rete.

La verifica dell’integrazione viene eseguita con Testcontainers insieme alla fase `verify` (i test unitari non avviano un cluster; `verify` richiede un Docker daemon disponibile):

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# usa lo stesso punto di ingresso del nightly
bash script/ci/hdfs_integration.sh
# verifica solo la linea di versione indicata
bash script/ci/hdfs_integration.sh 2.10
```

I test usano immagini Apache Hadoop a versione fissa; le immagini delle versioni precedenti sono costruite con un’immagine base Java 8 a digest fisso e un pacchetto Apache verificato con SHA-512. Il jar reale dell’agente della versione corrispondente viene messo nel classpath del NameNode e vengono asserite le versioni reali di Hadoop e JVM. Testcontainers crea una rete isolata e gestisce il ciclo di vita di NameNode e DataNode, verificando lettura e scrittura, creazione, append, rinomina, eliminazione, diniego ricorsivo e su istantanee, permessi nativi e audit, aggiornamento delle policy, riavvio del NameNode con la cache firmata dopo la disconnessione del server delle policy, e la modalità rigorosa con ripiego sui permessi nativi in assenza di istantanea.

Serve un Docker daemon in esecuzione e il permesso di scaricare le immagini di test. Se Docker non è disponibile i test falliscono, non vengono saltati in silenzio. Il client del file system viene eseguito dentro il container Hadoop e il servizio HTTP delle policy usa il port forwarding dell’host di Testcontainers; non serve un cluster Hadoop esterno. Al termine dei test, container e rete di test vengono ripuliti e i log salvati in `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`.

Il test HA avvia due NameNode, un DataNode e un JournalNode, configurando nomi di istanza e directory di cache indipendenti per i due agenti. Usa un client HDFS logico per commutare manualmente il nodo attivo e verifica lettura, scrittura e policy di diniego dopo la commutazione; il JournalNode singolo serve solo per il test: non verifica la tolleranza ai guasti per maggioranza e non riguarda il failover automatico con ZooKeeper.

Il test di HA automatica (solo Hadoop 3.5.0, Java 17 e 21) avvia ZooKeeper, tre JournalNode, due NameNode ciascuno con il proprio ZKFC e un DataNode. Verifica che, arrestato bruscamente il NameNode attivo, ZooKeeper faccia subentrare l’altro, che continua ad applicare le policy e attribuisce i rifiuti alla propria istanza; che le policy pubblicate mentre un NameNode è assente si applichino al suo ritorno come standby e restino applicate dopo il ritorno del ruolo, mai una versione precedente; che senza server delle policy il NameNode subentrante continui con lo snapshot che possiede e invii il proprio audit al ritorno del server; e che con un JournalNode su tre fermo le scritture riescano, mentre con due fermi la scrittura fallisca e il NameNode attivo si arresti invece di proseguire senza quorum. Una variante Kerberos aggiunge JournalNode che accedono con un keytab e servono solo HTTPS, ZKFC che si autenticano su ZooKeeper con SASL e znode di elezione riservati al principal dei NameNode (un client ZooKeeper non autenticato non può nemmeno leggerli); verifica che l’altro NameNode subentri quando quello attivo viene fermato e continui ad applicare le policy agli utenti Kerberos, che un client senza ticket sia sempre rifiutato e che il ruolo torni al primo NameNode al suo ritorno. I JournalNode girano con un principal che ha il nome breve dei NameNode, perché un JournalNode consegna gli edit solo al principal completo di un NameNode o a un richiedente con il suo stesso nome breve, e l’autenticazione HTTP gli fornisce solo nomi brevi.

Il test Kerberos viene eseguito solo su Hadoop 3.5.0 (Java 17 e 21): il KDC gira nella JVM di test, NameNode e DataNode partono in modalità sicura con i propri keytab, il DataNode trasferisce dati solo dopo SASL e serve solo HTTPS, e WebHDFS usa SPNEGO. Verifica che le policy di GrantForge si applichino a letture e scritture una volta mappati i principal sui nomi brevi, che un utente senza policy venga rifiutato, che i rifiuti siano registrati con il nome breve, che un client senza ticket venga rifiutato senza ricadere sull’autenticazione simple e che un NameNode riavviato si riautentichi e continui ad applicare le policy. Kerberos sulle altre linee non è ancora verificato.

Il codice di test si trova in `src/test` dei moduli di produzione reali. I test unitari delle policy comuni vengono eseguiti in common, quelli nativi condivisi in `agents/grantforge-agent-hdfs-native/src/test`, e non vengono più compilati dentro i sei agenti per versione. I test dei callback di ciascuna versione restano nei moduli numerati corrispondenti, mentre il codice di test dei container condiviso in `agents/grantforge-agent-hdfs-common/src/test/shared` continua a essere compilato nei moduli numerati; non esiste un progetto Maven di test separato. Testcontainers è solo in scope test. Il nightly verifica le sei versioni di Hadoop su host Java 17 e 21, mentre i container Hadoop usano le JVM della tabella precedente. L’attuale copertura dei container riguarda l’autenticazione Simple, l’HA manuale, e Kerberos, l’HA automatica ed entrambi insieme su Hadoop 3.5.0; Kerberos sulle altre versioni, TLS e le patch dei produttori richiedono comunque una verifica nell’ambiente di destinazione.

I punti di ingresso delle estensioni di Hadoop e la semantica dei permessi sono descritti in [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) e in [Guida ai permessi HDFS](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
