---
title: Trino
description: Installare e configurare il plug-in di servizio Trino, accedere con password, token web JSON o Kerberos su TLS, cercare cataloghi, schemi, tabelle, colonne e proprietà di sessione, e gestire policy di accesso, filtro delle righe e mascheramento nei termini di Trino.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Trino cerca cataloghi, schemi, tabelle, colonne e proprietà di sessione, e gestisce policy di accesso, filtro delle righe e mascheramento su ciò che Trino controlla: dati, routine, proprietà di sessione, utenti e le informazioni di sistema. ID del plug-in e tipo di servizio sono entrambi `trino`. Il plug-in di servizio gira nel server GrantForge; l’[agente Trino](/it/external/trino-agent/), installato nel coordinator, applica le policy.

I test di integrazione Testcontainers del repository coprono Trino `470`, `476`, `479` e `484` e, su `470` e `484`, HTTPS con file di password e token web JSON, oltre a Kerberos. Questo non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/trino`. Verificate che `trino` sia abilitato in **Gestione della piattaforma → Plug-in**; riscansionate la directory dei plug-in dopo averla aggiornata. Il plug-in parla con il coordinator tramite il driver JDBC di Trino (`trino-jdbc` 484), in un class loader proprio, che parla con tutte le versioni di Trino sopra indicate.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e selezionate Trino (`trino`).
2. Inserite l’URL JDBC del coordinator, ad esempio `jdbc:trino://trino.example.com:8443`.
3. Scegliete come accede l’utente di ricerca, inseritelo, testate la connessione e salvate il servizio. Il test accede e descrive il coordinator: la sua versione e quanti cataloghi vede l’utente.

Le impostazioni usano i nomi del driver, quindi si leggono come la documentazione JDBC di Trino. L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dagli utenti che eseguono query. Un test di connessione riuscito non significa che le query siano già soggette alle policy.

| Impostazione | Scopo |
| --- | --- |
| `jdbc.url` | Obbligatoria: `jdbc:trino://host:port`, facoltativamente con catalogo e schema e con proprietà del driver diverse dalle credenziali, come `?SSL=true` |
| `authentication` | `none` (predefinito): solo il nome utente; `password`; `jwt`: un token web JSON; `kerberos`: un principal con keytab o password |
| `user` | Utente di ricerca obbligatorio; con Kerberos il principal completo, come `grantforge@EXAMPLE.COM` |
| `password` | La password dell’utente, o con Kerberos quella del principal se non è indicato un keytab; salvata cifrata |
| `accessToken` | `jwt`: il token che il coordinator accetta; salvato cifrato |
| `keytab` | Kerberos: percorso del keytab del principal, leggibile sul **server GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del realm del principal, `host[:port]` separati da virgole; vuoto usa il `krb5.conf` del server |
| `KerberosRemoteServiceName` | Kerberos: la prima parte del principal del coordinator, predefinito `trino` |
| `KerberosUseCanonicalHostname` | Kerberos: se il principal del coordinator prende il nome canonico dell’host dell’URL, predefinito `true`; disattivatelo dietro un bilanciatore o un alias il cui nome è nel principal |
| `SSL` | Se usare TLS, predefinito `false`; ogni accesso tranne `none` lo richiede, poiché Trino autentica gli utenti solo su TLS |
| `SSLTrustStorePath`, `SSLTrustStorePassword`, `SSLTrustStoreType` | TLS: il truststore che verifica il coordinator, la sua password e il tipo (`JKS` o `PKCS12`); vuoto si fida di ciò di cui si fida Java |
| `SSLVerification` | TLS: `FULL` (predefinito) verifica certificato e nome host, `CA` solo il certificato, `NONE` niente e non accetta truststore |
| `timeout` | Timeout delle richieste in millisecondi, predefinito `30000`, intervallo `1..600000` |

Il servizio è esattamente ciò che dicono i suoi campi. L’URL non può contenere credenziali né ciò che farebbe accedere in altro modo: `user`, `password`, `accessToken`, `sessionUser`, `extraCredentials`, autenticazione esterna e OAuth 2.0, file e delega Kerberos e le password di keystore e truststore vi sono rifiutati. Anche i campi di un altro tipo di accesso sono rifiutati, come un token con l’autenticazione a password.

Con un coordinator il cui certificato non si può verificare non si parla: il test lo segnala come irraggiungibile. Un accesso rifiutato, permessi mancanti e un coordinator irraggiungibile sono segnalati come tali.

### Kerberos

Con `kerberos`, GrantForge autentica il principal, dal keytab o con la password, e passa al driver la credenziale di quell’accesso; nient’altro sul server GrantForge richiede impostazioni Kerberos. I realm i cui KDC sono indicati in `kerberos.kdc` sono dichiarati per l’intero server GrantForge, come fanno i plug-in HDFS, Hive, Kafka e HBase, così più servizi possono usare realm diversi fianco a fianco. L’utente di sessione è il nome del principal prima del realm, come Trino mappa i principal per impostazione predefinita.

### Cosa vede l’utente di ricerca

Quando l’[agente Trino](/it/external/trino-agent/) applica le policy, gli elenchi omettono ciò che l’utente di ricerca non può usare: dategli una policy che consenta `select` sul catalogo `*`, o sui cataloghi di cui l’editor delle policy deve suggerire le risorse.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `catalog` | Caratteri jolly, senza maiuscole | `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `all` |
| `schema` | Caratteri jolly, senza maiuscole; sotto un catalogo | Come `catalog` |
| `table` | Caratteri jolly, senza maiuscole; sotto uno schema; tabelle, viste e viste materializzate | Come `catalog` |
| `column` | Caratteri jolly, senza maiuscole; sotto una tabella | `select`, `update` |
| `function` | Caratteri jolly, senza maiuscole; sotto uno schema | `execute`, `create_function`, `drop_function` |
| `procedure` | Caratteri jolly, senza maiuscole; sotto uno schema | `execute` |
| `session_property` | Caratteri jolly, senza maiuscole; sotto un catalogo, nominata senza di esso | `set` |
| `system_session_property` | Caratteri jolly, senza maiuscole | `set` |
| `user` | Caratteri jolly, con maiuscole | `impersonate`, `view_query`, `kill_query` |
| `system_information` | Il suo unico valore, `*` | `read`, `write` |

`all` implica `select`, `insert`, `update`, `delete`, `create`, `drop` e `alter`.

Un permesso su un catalogo, uno schema o una tabella copre tutto ciò che contiene: `select` sullo schema `sales` legge ogni sua tabella, `create` su un catalogo vi crea schemi e tabelle. Un controllo è deciso in ogni ambito che nomina, e un diniego in uno qualsiasi lo rifiuta; così una policy può consentire uno schema e negare una colonna di una sua tabella. Funzioni, procedure e proprietà di sessione si concedono nominandole, con `*` per tutte quelle di uno schema o catalogo, e le funzioni hanno tipi di accesso propri, quindi una concessione su routine non vale mai come una su tabelle.

Nei valori delle policy, `*` corrisponde a un numero qualsiasi di caratteri e `?` a uno. L’editor delle policy suggerisce cataloghi, schemi nei cataloghi che una policy nomina, tabelle nei suoi schemi, colonne nelle sue tabelle e proprietà di sessione dei suoi cataloghi e del sistema; funzioni, procedure e utenti non sono suggeriti.

## Filtri delle righe e maschere

Le policy di filtro delle righe su una tabella, e di mascheramento su una colonna, sono SQL di Trino, che il coordinator applica a ogni query che le legge. Un filtro delle righe è una condizione sulle colonne della tabella, come `region = 'EU'`; i nomi al suo interno si risolvono nel catalogo e nello schema della tabella.

| Tipo di maschera | Mostra |
| --- | --- |
| `mask` | Le lettere come `X` o `x`, le cifre come `n` |
| `mask_show_last_4`, `mask_show_first_4` | Solo gli ultimi o i primi quattro caratteri |
| `mask_hash` | Lo SHA-256 del valore, in esadecimale |
| `mask_null` | `NULL` |
| `mask_none` | Il valore, non mascherato |
| `mask_date_show_year` | Solo l’anno di una data o di un timestamp |
| `custom` | Un’espressione SQL di Trino della policy, con `{col}` per la colonna |

L’agente adatta ogni maschera al tipo della colonna, perché una maschera non rompa mai la query che legge la colonna: vedete l’[agente Trino](/it/external/trino-agent/).

## Esempio di policy

Supponiamo che il catalogo `hive` abbia uno schema `sales` con le tabelle `orders`, le cui colonne includono `region`, `customer` e `card`, e `returns`. Create queste policy abilitate nello stesso servizio dati:

| Policy | Risorse | Regola |
| --- | --- | --- |
| Accesso | `catalog=hive`, `schema=sales` | Consentire `select` al gruppo `analysts` |
| Accesso | `catalog=hive`, `schema=sales`, `table=orders`, `column=card` | Negare `select` al gruppo `analysts` |
| Accesso | `catalog=hive`, `schema=sales`, `table=returns` | Consentire `insert` all’utente `alice` |
| Filtro delle righe | `catalog=hive`, `schema=sales`, `table=orders` | `region = 'EU'` per l’utente `alice` |
| Mascheramento | `catalog=hive`, `schema=sales`, `table=orders`, `column=customer` | `mask_hash` per il gruppo `analysts` |
| Accesso | `catalog=system`, `schema=runtime` | Consentire `select` all’utente `alice` |
| Accesso | `user=bob` | Consentire `view_query` all’utente `alice` |

Quando l’agente applica il nuovo snapshot, i membri di `analysts` leggono ogni tabella di `sales` tranne `orders.card` e vedono `customer` come hash; `alice` vede solo gli ordini europei, scrive in `returns` e vede le query di Bob in `system.runtime.queries` accanto alle proprie.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Applicarle richiede l’agente Trino nel coordinator. Vedete l’[agente Trino](/it/external/trino-agent/) per la distribuzione e ciò che controlla.

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente Trino](/it/external/trino-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
