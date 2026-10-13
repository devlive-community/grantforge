---
title: Apache Hive
description: Installa e configura il plug-in Hive, accedi con password o Kerberos, cerca database, tabelle, colonne e UDF e gestisci policy di accesso, mascheramento e filtro delle righe.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Hive offre per HiveServer2 la ricerca di database, tabelle, colonne e UDF permanenti, e policy di accesso, mascheramento e filtro delle righe, sul modello del servizio Hive di Apache Ranger. ID del plug-in e tipo di servizio sono entrambi `hive`. Il plug-in di servizio gira nel server GrantForge; l’[agente Hive](/it/external/hive-agent/), installato in HiveServer2, applica le policy.

I test di integrazione Testcontainers del repository coprono HiveServer2 `3.1.3`, `4.0.1` e `4.1.0`, incluso un accesso Kerberos a `4.0.1`; ciò non certifica le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/hive`. Verifica in **Gestione della piattaforma → Plug-in** che `hive` sia abilitato; dopo un aggiornamento esegui di nuovo la scansione della directory dei plug-in. Il plug-in comunica con HiveServer2 tramite il driver JDBC di Hive stesso (`hive-jdbc` 4.0.1, standalone) in un proprio class loader, quindi funziona sia con HiveServer2 3.1 sia con 4.x.

## Aggiungere un servizio dati

1. Apri **Permessi sui dati → Servizi dati**, aggiungi un servizio e scegli Apache Hive (`hive`).
2. Inserisci l’URL JDBC di HiveServer2, per esempio `jdbc:hive2://hiveserver2.example.com:10000/default`. Funziona anche il rilevamento tramite ZooKeeper: `jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`.
3. Scegli come accede l’utente di ricerca, inseriscilo, prova la connessione e salva il servizio. La prova accede ed elenca i database, come fanno le ricerche.

L’utente di ricerca serve solo a GrantForge per scoprire le risorse; è distinto dagli utenti che eseguono le query. Una prova di connessione riuscita non significa che le query siano già soggette alle policy.

| Impostazione | Scopo |
| --- | --- |
| `jdbc.url` | URL HiveServer2 obbligatorio `jdbc:hive2://host/database;variabili di sessione`, con TLS (`;ssl=true;sslTrustStore=…`), il trasporto HTTP o, con Kerberos, il principal del server (`;principal=hive/_HOST@EXAMPLE.COM`) |
| `authentication` | `password` (predefinito): un utente con la sua password, per LDAP, PAM o senza autenticazione; `kerberos`: un principal con keytab o password |
| `username` | Utente di ricerca obbligatorio; con Kerberos il principal completo, per esempio `grantforge@EXAMPLE.COM` |
| `password` | La password dell’utente o, senza keytab, la password Kerberos del principal; memorizzata cifrata |
| `keytab` | Kerberos: percorso della keytab del principal, leggibile sul **server GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del realm del principal, `host[:port]` separati da virgole; vuoto usa il `krb5.conf` del server |
| `read.timeout.ms` | Timeout di lettura, predefinito `30000`, intervallo `1..600000` |

L’URL non può contenere credenziali né nulla che legga file o token sul server GrantForge: `user`, `password`, `initFile`, `kerberosAuthType`, `delegationToken`, `storePasswordPath`, `jwt` e le variabili `browser…` sono rifiutati, a prescindere da maiuscole e minuscole. `auth` può valere solo `noSasl`. Un servizio Kerberos richiede `principal` nell’URL; un servizio con password non può averlo.

### Kerberos

Con `kerberos`, GrantForge autentica da sé il principal, con la keytab o la password, e apre la connessione come quel principal; sul server GrantForge non servono altre impostazioni Kerberos. I realm i cui KDC sono indicati in `kerberos.kdc` vengono dichiarati per tutto il server GrantForge, come fa il plug-in HDFS, così più servizi Hive e HDFS possono usare realm diversi contemporaneamente.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Note |
| --- | --- | --- |
| `database` | Caratteri jolly, senza maiuscole | Le policy possono terminare qui |
| `table` | Caratteri jolly, senza maiuscole | Sotto un database; le policy possono terminare qui |
| `column` | Caratteri jolly, senza maiuscole | Sotto una tabella |
| `udf` | Caratteri jolly, senza maiuscole | Funzioni permanenti, sotto un database |
| `url` | Percorsi, con maiuscole, ricorsivi | Dati che le istruzioni leggono o scrivono, per esempio `hdfs://nn:8020/landing`; tipi di accesso `read` e `write` |
| `hiveservice` | Caratteri jolly | HiveServer2 stesso; tipo di accesso `serviceadmin` |
| `global` | Caratteri jolly | Funzioni temporanee e file aggiunti; tipo di accesso `tempudfadmin` |

I tipi di accesso sono quelli di Hive: `select`, `update`, `create`, `drop`, `alter`, `index`, `lock`, `read`, `write`, `repladmin`, `serviceadmin`, `tempudfadmin`, `refresh` e `all`. Come in Ranger, `all` include ogni tipo tranne `tempudfadmin`, che permette a un utente di eseguire codice a sua scelta dentro HiveServer2.

Nei valori delle policy, `*` corrisponde a un numero qualsiasi di caratteri e `?` a un carattere. L’editor delle policy suggerisce nomi per prefisso: database, tabelle e UDF nei database indicati dalla policy e colonne delle sue tabelle.

## Mascheramento

Le policy di mascheramento si applicano alle colonne. Ogni tipo di maschera è una delle funzioni di mascheramento di Hive, in cui `{col}` indica la colonna:

| Tipo di maschera | Mostra |
| --- | --- |
| `mask` | Maiuscole come `X`, minuscole come `x` e cifre come `n` |
| `mask_show_last_4` | Solo gli ultimi quattro caratteri |
| `mask_show_first_4` | Solo i primi quattro caratteri |
| `mask_hash` | Un hash SHA-256 |
| `mask_null` | `NULL` |
| `mask_none` | Il valore invariato |
| `mask_date_show_year` | Il 1° gennaio dell’anno della data |
| `custom` | Un’espressione Hive a scelta, con `{col}` per la colonna, per esempio `concat('****', substr({col}, -4))` |

Per esempio, `mask_show_last_4` mostra `4111111111111111` come `xxxxxxxxxxxx1111`.

## Filtri delle righe

Le policy di filtro delle righe si applicano alle tabelle. Un filtro è una condizione Hive sulle colonne della tabella, per esempio `region = 'EU'`: le query vedono solo le righe che la soddisfano.

Maschere e filtri delle righe non concedono accesso. Una policy di accesso separata deve consentire la query.

## Esempio di policy

Supponiamo che Hive abbia una tabella `sales.orders` con le colonne `id`, `region` e `card`. Crea tre policy abilitate nello stesso servizio dati per l’utente `alice`:

| Tipo di policy | Risorse | Regola |
| --- | --- | --- |
| Accesso | `database=sales`, `table=orders`, `column=*` | Consentire `select` all’utente `alice` |
| Mascheramento | `database=sales`, `table=orders`, `column=card` | Usare `mask_show_last_4` per l’utente `alice` |
| Filtro delle righe | `database=sales`, `table=orders` | Usare `region = 'EU'` per l’utente `alice` |

Appena l’agente applica il nuovo snapshot, `alice` vede solo le righe EU di `sales.orders`, con ogni carta mascherata tranne gli ultimi quattro caratteri; `SHOW DATABASES` le elenca `sales` e gli altri database la rifiutano.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Per applicarle, maschere e filtri delle righe compresi, ogni HiveServer2 ha bisogno dell’agente Hive. Distribuzione e controlli sono descritti nell’[agente Hive](/it/external/hive-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente Hive](/it/external/hive-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
