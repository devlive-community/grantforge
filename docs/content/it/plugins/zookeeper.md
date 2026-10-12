---
title: Apache ZooKeeper
description: Installare e configurare il plug-in ZooKeeper, esplorare gli znode e gestire le policy di accesso ai percorsi degli znode.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Apache ZooKeeper fornisce a GrantForge le connessioni agli ensemble, la ricerca dei percorsi degli znode e la gestione delle policy. ID del plug-in e tipo di servizio sono entrambi `zookeeper`.

## Installazione

La distribuzione include il plug-in in `plugins/zookeeper`. Verifica in **Gestione della piattaforma → Plug-in** che `zookeeper` sia attivo; dopo un aggiornamento, rianalizza la directory dei plug-in. Il plug-in usa il client di ZooKeeper 3.9 in un proprio class loader, separato dalle librerie del server.

## Aggiungere un servizio di dati

1. Apri **Permessi sui dati → Servizi di dati**, aggiungi un servizio e scegli ZooKeeper (`zookeeper`).
2. Inserisci la stringa di connessione: i server dell’ensemble con un chroot facoltativo, come `zk1:2181,zk2:2181,zk3:2181/kafka`. Il client passa da un server all’altro da solo.
3. Scegli l’autenticazione, imposta lo znode di ricerca e prova la connessione (verifica che lo znode esista e che i figli siano elencabili); poi salva il servizio.

| Impostazione | Scopo |
| --- | --- |
| `zookeeper.connect` | Stringa di connessione obbligatoria: `host[:port]` separati da virgole, seguiti facoltativamente da `/chroot`; il chroot non può terminare con `/` |
| `authentication` | `none` (predefinito), `digest` o `kerberos` |
| `username` | Digest: l’utente digest delle ACL (senza due punti); Kerberos: il principal, come `grantforge@EXAMPLE.COM` |
| `password` | Password dell’utente digest; salvata cifrata |
| `keytab` | Kerberos: percorso del keytab del principal sul server GrantForge |
| `zookeeper.sasl.client.username` | Kerberos: prima parte dei principal dei server, predefinita `zookeeper`, come in `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos: principal completo dei server quando non ha quella forma, come `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | Se usare la porta client TLS dei server, predefinito `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | Truststore che verifica i server; vuoto, si fida di ciò di cui si fida il Java del server; tipo `jks`, `pkcs12` o `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | Keystore con il certificato client, per i server che lo richiedono |
| `session.timeout.ms` | Timeout della sessione, predefinito `15000`, intervallo `2000..120000` |
| `connection.timeout.ms` | Attesa solo per la connessione iniziale e l’autenticazione, predefinito `10000` ms, intervallo `1000..60000`; non limita le successive richieste sincrone |
| `request.timeout.ms` | Attesa massima per una richiesta sincrona, predefinito `8000` ms, intervallo `1000..60000` |
| `lookup.path` | Znode iniziale per ricerca ed esplorazione, sotto il chroot, predefinito `/`; ad esempio `/brokers`, con l’esplorazione limitata a esso |
| `lookup.max.entries` | Massimo di figli letti da uno znode alla volta, predefinito `10000`, intervallo `1..100000` |

## Autenticazione

- **none**: nessun accesso; si leggono solo gli znode la cui ACL è aperta a `world:anyone`.
- **digest**: accede come `username:password`, che corrisponde alle identità `digest` delle ACL di ZooKeeper. Una password errata non viene rifiutata all’accesso: è un’altra identità, e gli znode protetti risultano accesso negato.
- **kerberos**: accede via SASL con un keytab; il `krb5.conf` del server deve portare al KDC. Ogni principal con il suo keytab riceve una propria sezione JAAS accanto alla configurazione del processo, così un solo GrantForge raggiunge più ensemble come principal diversi. L’accesso Kerberos con la sola password non è ancora supportato.

La configurazione viene controllata al salvataggio: la forma della stringa di connessione, utente e password per digest, principal e keytab per Kerberos, keytab e principal del server solo con Kerberos, e gli intervalli dei timeout e del limite.

Con TLS disabilitato, i percorsi e le password del truststore e del keystore devono restare vuoti. La password di un archivio richiede il percorso dell’archivio corrispondente. Per entrambi gli archivi sono ammessi solo i tipi `jks`, `pkcs12` e `pem`.

Gli spazi iniziali e finali nelle password degli archivi TLS vengono conservati. La configurazione del servizio determina la modalità TLS e gli archivi usati per questa connessione, sostituendo le impostazioni TLS e degli archivi ZooKeeper già presenti nella JVM; i certificati e i nomi host dei server vengono comunque verificati.

## Esplorare gli znode

Seleziona il servizio in **Permessi sui dati → Policy** e usa **Esplora** accanto a `path`:

- I figli sono elencati una pagina alla volta, per nome; uno znode con figli propri può essere aperto ancora.
- Ogni znode mostra la sua ACL (`scheme:id:cdrwa`, come la scrivono gli strumenti di ZooKeeper), la dimensione dei dati e l’ultima modifica.
- Uno znode di cui l’utente di ricerca non può leggere l’ACL resta elencato per nome; aprendolo viene indicato il motivo.

Se vengono eliminati znode durante la paginazione, il cursore continua ad avanzare dall’ultimo nome figlio esaminato, anche se quel nodo non esiste più. L’elenco dei figli non è un’istantanea: aggiunte ed eliminazioni simultanee seguono la semantica di ZooKeeper e possono modificare le pagine successive.

Digitando un percorso compaiono suggerimenti; un padre inesistente non ne ha, e i percorsi fuori da `lookup.path` vengono rifiutati. Uno znode con più figli di `lookup.max.entries` dà un errore invece di mostrarne solo una parte.

## Applicare le policy

C’è un solo livello di risorsa, `path`, e i tipi di accesso seguono i permessi ACL di ZooKeeper: `read`, `write`, `create`, `delete` e `admin`; le policy sui percorsi supportano ricorsione ed esclusioni.

Il plug-in del servizio gestisce, cerca ed esplora; l’agente nel server applica le policy alle ACL marcatore tramite un provider di autenticazione SASL. Il comando `mvn -pl agents/grantforge-agent-zookeeper -am package` produce un singolo JAR shaded compatibile con Java 8, completo delle dipendenze. La distribuzione lo include in `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. L’implementazione è destinata alle API di ZooKeeper 3.6–3.9; la compatibilità con le versioni effettive deve ancora essere verificata. Non si dichiara quindi superata l’intera matrice delle versioni né concluso il collaudo per la produzione.

### Distribuzione

1. Installa il JAR nella directory `lib` di **ogni server ZooKeeper**, inclusi tutti i nodi che possono ricevere richieste dai client. Crea le **credenziali Agent** nel servizio di dati `zookeeper` corrispondente in GrantForge. Salva il token in un file separato, leggibile solo dall’account che esegue ZooKeeper; non scrivere il token stesso in `zoo.cfg`.
2. Aggiungi queste impostazioni al `zoo.cfg` di ogni server, sostituendo indirizzo, percorsi e identificativo dell’istanza dell’esempio. `grantforge.server.url` (`http[s]://GrantForge`), `grantforge.token.file`, una directory locale `grantforge.cache.dir` e `grantforge.instance` sono obbligatori. L’account che esegue ZooKeeper deve poter scrivere nella directory della cache.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. Questo provider **sostituisce il provider SASL nativo**; non mantenere un altro provider registrato per lo stesso schema `sasl` in altre voci `authProvider.N`. `enableEagerACLCheck=false` è obbligatorio: con il controllo attivo, la registrazione del provider viene rifiutata e il marcatore non consente l’accesso. Mantieni e verifica la normale autenticazione ZooKeeper con SASL, JAAS e Kerberos; l’agente non sostituisce l’autenticazione. Riavvia i server secondo la procedura di manutenzione dell’ensemble.
4. Puoi impostare `grantforge.signing.key.file` sul file della chiave pubblica di firma fissata come attendibile. `grantforge.principal.mapping=short` è il valore predefinito e converte `kafka/host@REALM` in `kafka`; `full` mantiene il principal completo. Gli utenti delle policy devono corrispondere alle identità risultanti.

### ACL e limitazioni

- Solo l’ACL marcatore `sasl:grantforge` delega alle policy GrantForge, ad esempio `sasl:grantforge:cdrwa`, dove `c=create`, `d=delete`, `r=read`, `w=write` e `a=admin`. Le altre ACL native, comprese le altre identità SASL, continuano a funzionare normalmente.
- Il marcatore nega l’accesso se l’agente non è in esecuzione, manca uno snapshot utilizzabile delle policy, il suo `serviceType` non è `zookeeper` o nessuna policy corrisponde/consente l’accesso. **Non è un diniego globale**: altre ACL, `world:anyone` o un’identità `super` possono ancora autorizzare la richiesta.
- Le policy confrontano il percorso assoluto di destinazione sul server. Per `create`/`delete` è lo znode da creare/eliminare, non il padre che fornisce l’ACL. Con chroot, il percorso della policy deve includere il prefisso chroot effettivo: `/brokers` sotto il chroot `/kafka` corrisponde a `/kafka/brokers`. Il `create` sequenziale verifica il prefisso assoluto della richiesta sul server prima di aggiungere il numero di sequenza; i successivi `read`/`delete` verificano il nome completo del nodo generato.
- Le ACL native non vengono migrate automaticamente e le ACL non vengono ereditate ricorsivamente; i nuovi znode richiedono un’ACL marcatore esplicita. Prima di usare `setACL`, verifica accessi consentiti e negati su un ambito ristretto e conserva un canale di amministrazione indipendente. Non applicare il marcatore al nodo radice senza un’attenta verifica, per evitare di bloccare l’accesso.
- Un server senza agente tratta `sasl:grantforge` come l’identità SASL nativa `grantforge`, anziché valutare le policy. La distribuzione su tutti i nodi è quindi necessaria; una distribuzione parziale non protegge l’ensemble.
- Le scritture inoltrate da un follower/observer al leader possono non avere una connessione client in questa interfaccia del provider, quindi il contesto `clientAddress` può essere vuoto. Non sono garantite policy end-to-end nell’ensemble basate sull’IP del client.
- Questo provider non può conoscere il risultato finale della richiesta: non invia quindi eventi di audit finali `AccessEvent` e non offre un audit completo degli accessi.

## Guide correlate

- [Servizi di dati, policy e agenti](/it/external/data-services/)
- [Sviluppo di plug-in e tipi di servizio](/it/develop/plugins/)
