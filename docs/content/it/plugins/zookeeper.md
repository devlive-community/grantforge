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
| `connection.timeout.ms` | Attesa di un server e dell’accesso, predefinito `10000`, intervallo `1000..60000` |
| `lookup.path` | Znode iniziale per ricerca ed esplorazione, sotto il chroot, predefinito `/`; ad esempio `/brokers`, con l’esplorazione limitata a esso |
| `lookup.max.entries` | Massimo di figli letti da uno znode alla volta, predefinito `10000`, intervallo `1..100000` |

## Autenticazione

- **none**: nessun accesso; si leggono solo gli znode la cui ACL è aperta a `world:anyone`.
- **digest**: accede come `username:password`, che corrisponde alle identità `digest` delle ACL di ZooKeeper. Una password errata non viene rifiutata all’accesso: è un’altra identità, e gli znode protetti risultano accesso negato.
- **kerberos**: accede via SASL con un keytab; il `krb5.conf` del server deve portare al KDC. Ogni principal con il suo keytab riceve una propria sezione JAAS accanto alla configurazione del processo, così un solo GrantForge raggiunge più ensemble come principal diversi. L’accesso Kerberos con la sola password non è ancora supportato.

La configurazione viene controllata al salvataggio: la forma della stringa di connessione, utente e password per digest, principal e keytab per Kerberos, keytab e principal del server solo con Kerberos, e gli intervalli dei timeout e del limite.

## Esplorare gli znode

Seleziona il servizio in **Permessi sui dati → Policy** e usa **Esplora** accanto a `path`:

- I figli sono elencati una pagina alla volta, per nome; uno znode con figli propri può essere aperto ancora.
- Ogni znode mostra la sua ACL (`scheme:id:cdrwa`, come la scrivono gli strumenti di ZooKeeper), la dimensione dei dati e l’ultima modifica.
- Uno znode di cui l’utente di ricerca non può leggere l’ACL resta elencato per nome; aprendolo viene indicato il motivo.

Digitando un percorso compaiono suggerimenti; un padre inesistente non ne ha, e i percorsi fuori da `lookup.path` vengono rifiutati. Uno znode con più figli di `lookup.max.entries` dà un errore invece di mostrarne solo una parte.

## Applicare le policy

C’è un solo livello di risorsa, `path`, e i tipi di accesso seguono i permessi ACL di ZooKeeper: `read`, `write`, `create`, `delete` e `admin`; le policy sui percorsi supportano ricorsione ed esclusioni.

Il plug-in del server gestisce, cerca ed esplora. L’agente che applica queste policy nei server ZooKeeper non è ancora disponibile; fino ad allora decidono le ACL di ZooKeeper.

## Guide correlate

- [Servizi di dati, policy e agenti](/it/external/data-services/)
- [Sviluppo di plug-in e tipi di servizio](/it/develop/plugins/)
