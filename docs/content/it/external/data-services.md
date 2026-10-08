---
title: Servizi di dati, policy e agenti
description: Gestire con i plug-in i permessi di sistemi esterni come HDFS e Hive, tra cui servizi di dati, policy di accesso, agenti e audit degli accessi.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il gruppo “Permessi sui dati” gestisce i permessi dei sistemi di dati esterni a GrantForge, con un’architettura simile a quella di Apache Ranger: i plug-in definiscono i tipi di servizio, gli amministratori scrivono le policy nella console e gli agenti distribuiti nel sistema di destinazione scaricano le policy e decidono l’accesso localmente.

> [!NOTE]
> La versione attuale fornisce il framework dei plug-in, l’editor di policy generico, la distribuzione delle policy e l’audit degli accessi, il tipo di servizio `hdfs` e l’agente NameNode per Hadoop 3.5.0, oltre al plug-in di esempio (`example`). Il plug-in per Hive e gli agenti per le altre versioni di Hadoop sono ancora in sviluppo.

```mermaid
flowchart LR
  C[Console: servizi di dati e policy] --> S[Server GrantForge]
  S -->|istantanea delle policy firmata| A[Agente (in HDFS / Hive)]
  A -->|battito e audit degli accessi| S
  U[Utente che accede ai dati] --> A
```

## Plug-in

**Gestione della piattaforma → Plug-in** elenca i plug-in di tipi di servizio caricati. I plug-in integrati vengono forniti con il server; gli altri plug-in si mettono nella directory `plugins` e poi si fa clic su “Ripeti scansione”; ogni plug-in viene caricato in modo indipendente e, se fallisce, viene disattivato solo lui. Lo sviluppo dei plug-in è descritto in [Plug-in e tipi di servizio](/it/develop/plugins/).

![Plug-in](/screenshots/plugins.png)

## HDFS

Il pacchetto di rilascio include il plug-in HDFS (`plugins/hdfs`), il tipo di servizio `hdfs`, equivalente al servizio HDFS di Apache Ranger:

- Le risorse hanno un solo livello, `path`, che viene confrontato per percorso: `/data/sales` corrisponde a sé stesso e, se si spunta “Ricorsivo”, anche a tutti i file e alle directory che contiene; sono supportate le esclusioni.
- I tipi di accesso `read`, `write` ed `execute` corrispondono ai bit di permesso di HDFS.
- Il plug-in si connette al cluster con il client di Hadoop stesso; il test di connessione verifica che la directory di consultazione esista e che il suo contenuto si possa elencare; quando si scrive una policy, inserendo un percorso vengono elencate le sottodirectory e i file di quella directory, con le directory prima dei file.
- Il plug-in del server si occupa della gestione e delle consultazioni; perché le policy vincolino l’accesso a HDFS, occorre inoltre distribuire l’[Agente NameNode HDFS](/it/external/hdfs-agent/).

| Configurazione | Spiegazione |
| --- | --- |
| `username` | Utente con cui si consultano le directory; con Kerberos, il principal, per esempio `grantforge@EXAMPLE.COM` |
| `password` / `keytab` | Con Kerberos, uno dei due: la password del principal oppure il percorso del file keytab sul server di GrantForge |
| `fs.default.name` | `hdfs://namenode:8020`, `hdfs://nameservice1` in alta disponibilità, oppure `webhdfs://namenode:9870` |
| `hadoop.security.authentication` | `simple` o `kerberos` |
| `hadoop.security.authorization`, `hadoop.security.auth_to_local` | Uguale al core-site.xml del cluster |
| `dfs.namenode.kerberos.principal` e simili | I principal di NameNode, DataNode e Secondary NameNode, per esempio `nn/_HOST@EXAMPLE.COM` |
| `hadoop.rpc.protection` | `authentication`, `integrity` o `privacy`, uguale al cluster |
| Configurazione Hadoop aggiuntiva | Una coppia `key=value` per riga, per l’alta disponibilità e altre impostazioni, per esempio `dfs.nameservices=nameservice1`, `dfs.ha.namenodes.nameservice1=nn1,nn2`, `dfs.namenode.rpc-address.nameservice1.nn1=nn1:8020`, `dfs.client.failover.proxy.provider.nameservice1=org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider` |
| `lookup.path` | Directory di consultazione, `/` per impostazione predefinita; se per esempio si imposta `/data`, un inserimento vuoto elenca il contenuto di `/data` e gli inserimenti relativi vengono completati a partire da lì. Utile nei cluster in cui l’utente di consultazione non ha il permesso di elencare la directory radice |
| `lookup.max.entries` | Numero massimo di voci analizzate in una consultazione di directory, `10000` per impostazione predefinita, intervallo `1..100000`; superato il limite viene restituito un errore, per non omettere candidati in silenzio |

La configurazione Hadoop aggiuntiva prevale sulle impostazioni di connessione con lo stesso nome, e sia la validazione della configurazione sia l’accesso usano i valori prevalenti. `fs.defaultFS` e `fs.default.name` sono alias, quindi nella configurazione aggiuntiva se ne può impostare uno solo; chiavi duplicate, indirizzi che non sono del cluster e configurazioni Kerberos senza credenziali vengono rifiutate al salvataggio. Nell’indirizzo del cluster si indica solo l’URI del cluster; le sottodirectory da consultare vanno in `lookup.path`.

`lookup.path` limita l’ambito di esplorazione dei percorsi candidati; non sostituisce il controllo di accesso proprio di HDFS: i collegamenti simbolici e i montaggi ViewFS seguono la configurazione del cluster. Nell’inserimento si può omettere la `/` iniziale, `/` e `.` ripetuti sono consentiti, mentre `..` e i percorsi assoluti fuori dall’ambito vengono rifiutati. Una directory inesistente restituisce candidati vuoti; permessi insufficienti e connessioni non riuscite mostrano un errore.

Quando si usa Kerberos, il server di GrantForge deve essere in grado di trovare il KDC: configura `/etc/krb5.conf` oppure indicalo con `-Djava.security.krb5.conf=`.

## Servizi di dati

**Permessi sui dati → Servizi di dati**: un servizio è un’istanza di un sistema esterno i cui permessi GrantForge gestisce, per esempio un cluster HDFS. Quando si aggiunge un servizio si sceglie il tipo di servizio e si compilano i dati di connessione secondo le voci di configurazione definite dal plug-in; prima si può fare il **Test di connessione**. Le configurazioni sensibili, come le password, vengono salvate cifrate e non vengono più mostrate dopo il salvataggio.

![Servizi di dati](/screenshots/services.png)

## Policy

**Permessi sui dati → Policy** decidono chi può fare cosa sulle risorse di un servizio di dati:

- le **policy di accesso** consentono o negano l’accesso;
- le **policy di mascheramento** nascondono i campi;
- le **policy di filtro delle righe** lasciano passare solo alcune righe.

La gerarchia delle risorse (in Hive, database, tabelle e colonne, per esempio), i tipi di accesso (per esempio select, update) e le condizioni provengono tutti dal plug-in del tipo di servizio; quando si compila la risorsa si possono cercare le risorse che esistono realmente nel sistema di destinazione. Le policy hanno come oggetto utenti, gruppi di utenti o ruoli.

![Policy](/screenshots/policies.png)

## Agenti

**Permessi sui dati → Agenti**: gli agenti vengono distribuiti all’interno del sistema di destinazione; con un token inviano periodicamente un battito e scaricano un’istantanea delle policy firmata, e decidono l’accesso localmente. Qui si emettono i token degli agenti (mostrati una sola volta) e si verifica se ogni agente ha già adottato le policy più recenti.

![Agenti](/screenshots/agents.png)

## Audit degli accessi

**Permessi sui dati → Audit degli accessi**: ogni decisione di accesso segnalata da un agente: chi ha fatto cosa, quando, da dove e su quale risorsa, se l’accesso è stato consentito o negato e quale policy l’ha deciso. Le registrazioni vengono conservate per 90 giorni per impostazione predefinita.

![Audit degli accessi](/screenshots/access-audit.png)
