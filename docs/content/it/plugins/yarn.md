---
title: Apache Hadoop YARN
description: Installare e configurare il plug-in di servizio YARN, raggiungere un ResourceManager o una coppia ad alta disponibilità, accedere in modo semplice o con Kerberos, cercare code e nomi di applicazioni e gestire chi può inviare quali applicazioni e dove.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in YARN cerca le code e i nomi delle applicazioni che contengono, e gestisce le policy di accesso su ciò che YARN decide: inviare applicazioni alle code, amministrare le code e amministrare il cluster. L’ID del plug-in e il tipo di servizio sono entrambi `yarn`. Il plug-in di servizio è eseguito nel server GrantForge; l’[agente YARN](/it/external/yarn-agent/) installato nei ResourceManager applica le policy.

I test di integrazione Testcontainers del repository coprono i ResourceManager di Hadoop `3.2.4`, costruito dalla versione Apache verificata con SHA-512, `3.3.6`, `3.4.3` e `3.5.0`, oltre a un ResourceManager Kerberos `3.4.3`; ciò non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/yarn`. Verificate in **Gestione della piattaforma → Plug-in** che `yarn` sia abilitato; dopo un aggiornamento, ripetete la scansione della directory dei plug-in. Il plug-in comunica con il ResourceManager tramite il client YARN di Hadoop stesso, sul client shaded di Hadoop 3.5.0, nel proprio class loader.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e scegliete Apache Hadoop YARN (`yarn`).
2. Inserite l’indirizzo client del ResourceManager, o quello di ogni ResourceManager di un cluster ad alta disponibilità.
3. Scegliete come accede l’utente di ricerca, inseritelo, testate la connessione e salvate il servizio. Il test accede e descrive il cluster: quanti NodeManager e quante code ha.

Le impostazioni portano i nomi di Hadoop stesso e si possono copiare da un `yarn-site.xml`. L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dagli utenti che inviano applicazioni. Un test di connessione riuscito non significa che gli invii siano già soggetti alle policy.

| Impostazione | Scopo |
| --- | --- |
| `yarn.resourcemanager.address` | Obbligatoria: il ResourceManager, `host` o `host:port`, porta predefinita `8032`; più indirizzi, separati da virgole, per un cluster ad alta disponibilità, tra cui il client commuta |
| `hadoop.security.authentication` | `simple` (predefinito): un nome utente; `kerberos`: un principal con keytab o password |
| `username` | Utente di ricerca obbligatorio; con Kerberos, il principal completo, per esempio `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: la password del principal, se non è indicato un keytab; memorizzata cifrata |
| `keytab` | Kerberos: percorso del keytab del principal, leggibile sul **server GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del realm del principal, `host[:port]`, separati da virgole; vuoto usa il `krb5.conf` del server |
| `yarn.resourcemanager.principal` | Kerberos: il principal dei ResourceManager, predefinito `yarn/_HOST@` seguito dal realm del principal di ricerca |
| `hadoop.rpc.protection` | `authentication` (predefinito), `integrity` o `privacy`, come nel cluster |
| `timeout` | Quanto può durare una richiesta, predefinito `30000`, intervallo `1..600000` millisecondi; il client YARN stesso riproverebbe per un quarto d’ora |

Il servizio è esattamente ciò che dicono le sue impostazioni: nessun `core-site.xml` o `yarn-site.xml` del server GrantForge viene letto. Le impostazioni Kerberos sono rifiutate con l’autenticazione semplice; Kerberos richiede una password o un keytab. Un ResourceManager che rifiuta l’accesso, per esempio perché si aspetta Kerberos, è segnalato come accesso rifiutato.

### Kerberos

Con `kerberos`, GrantForge autentica il principal tramite il login di Hadoop stesso, dal keytab o con la password, mantiene quel login e lo rinnova dal keytab; nient’altro sul server GrantForge richiede impostazioni Kerberos. I realm i cui KDC sono elencati in `kerberos.kdc` sono dichiarati per l’intero server GrantForge, come fanno i plug-in HDFS, Hive, Kafka, HBase e Trino, così più servizi possono usare realm diversi fianco a fianco.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `queue` | Carattere jolly, distingue le maiuscole; il percorso della coda dalla radice, per esempio `root.sales.etl` | `submit_app`, `admin_queue` |
| `application` | Carattere jolly, distingue le maiuscole; sotto una coda; il nome dell’applicazione | `submit_app` |
| `cluster` | Il suo unico valore, `*` | `admin`: l’amministrazione di YARN, per esempio ricaricare code, nodi ed etichette dei nodi |

`admin_queue` implica `submit_app`, poiché gli amministratori di una coda possono inviarvi applicazioni.

Come in YARN, un permesso su una coda copre le code sottostanti: `submit_app` su `root.sales` invia a `root.sales.etl`. Un controllo è deciso in ogni coda del percorso, dalla radice, e un diniego in una qualsiasi di esse lo rifiuta; così una policy può consentire `root.sales` e negare `root.sales.adhoc`. Sotto una coda, un livello applicazione indica a quali applicazioni si applica un permesso: `submit_app` su `root.hr` con applicazione `payroll-*` accetta solo applicazioni il cui nome inizia con `payroll-`, in `root.hr` e nelle code sottostanti.

Nei valori delle policy, `*` corrisponde a un numero qualsiasi di caratteri e `?` a uno solo. L’editor delle policy suggerisce le code per percorso, o per il loro nome se è ciò che si digita, qualunque sia lo scheduler del cluster, oltre ai nomi delle applicazioni che il ResourceManager ricorda ancora nelle code nominate da una policy.

### Indirizzi dei client

Un elemento di policy può valere solo per alcuni client: la sua condizione **Indirizzi dei client** (`ip-range`) accetta indirizzi, intervalli come `10.1.0.0/16` e nomi host, come gli elenchi di host di Hadoop stesso. Dietro un proxy, per esempio il web proxy di YARN o un router, il client è il primo indirizzo inoltrato dai proxy. Indirizzi e intervalli sono preferibili ai nomi host, che costano una risoluzione del nome del client. YARN chiede della propria amministrazione senza l’indirizzo del client, quindi la condizione non vale mai su `cluster`.

## Esempio di policy

Supponete che il cluster esegua le code `root.sales.etl`, `root.sales.adhoc` e `root.hr`. Create queste policy di accesso abilitate nello stesso servizio dati:

| Risorse | Regola |
| --- | --- |
| `queue=root.sales` | Consentire `submit_app` al gruppo `analysts` |
| `queue=root.sales.adhoc` | Negare `submit_app` all’utente `alice` |
| `queue=root.hr`, `application=payroll-*` | Consentire `submit_app` all’utente `bob` |
| `queue=root.sales` | Consentire `admin_queue` all’utente `carol` |
| `queue=root` | Consentire `submit_app` al gruppo `etl-robots`, da `10.0.0.0/8` |
| `cluster=*` | Consentire `admin` al gruppo `yarn-admins` |

Quando l’agente applica il nuovo snapshot, i membri di `analysts` inviano a `root.sales.etl` e `root.sales.adhoc`, ma `alice` solo a `root.sales.etl`; `bob` invia applicazioni per le paghe a `root.hr` e nient’altro; `carol` invia alle code delle vendite e ne termina le applicazioni; i membri di `etl-robots` inviano ovunque, ma solo dalla rete interna; e i membri di `yarn-admins` ricaricano le code.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Per applicarle serve l’agente YARN in ogni ResourceManager. La distribuzione e i controlli sono descritti nell’[agente YARN](/it/external/yarn-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente YARN](/it/external/yarn-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
