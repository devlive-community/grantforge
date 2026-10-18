---
title: Apache HBase
description: Installare e configurare il plug-in di servizio HBase, trovare il cluster tramite ZooKeeper o server di bootstrap, accedere in modo simple o con Kerberos, cercare namespace, tabelle e famiglie di colonne e gestire policy di accesso negli ambiti propri di HBase.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in HBase cerca namespace, tabelle e famiglie di colonne, e gestisce policy di accesso negli ambiti dei permessi propri di HBase: l’ambito globale, i namespace, le tabelle, le famiglie di colonne e le colonne. ID del plug-in e tipo di servizio sono entrambi `hbase`. Il plug-in di servizio è eseguito nel server GrantForge; l’[agente HBase](/it/external/hbase-agent/), installato nei master e nei region server, applica le policy.

I test di integrazione Testcontainers del repository coprono HBase `2.5.16`, `2.6.7` e `3.0.0`, costruiti dalle release Apache verificate con SHA-512, e un cluster Kerberos `2.6.7`; ciò non certifica ogni altra versione.

## Installazione

La distribuzione include il plug-in in `plugins/hbase`. Verifica che `hbase` sia abilitato in **Gestione della piattaforma → Plug-in**; dopo un aggiornamento, ripeti la scansione della directory dei plug-in. Il plug-in comunica con il cluster tramite il client di HBase (`hbase-shaded-client-byo-hadoop` 2.6.7) sul client shaded di Hadoop 3.5.0, in un class loader proprio, e funziona quindi con HBase 2.x e 3.x.

## Aggiungere un servizio dati

1. Apri **Permessi sui dati → Servizi dati**, aggiungi un servizio e seleziona Apache HBase (`hbase`).
2. Inserisci il quorum ZooKeeper, oppure i server di bootstrap di un cluster HBase 2.5 o successivo.
3. Scegli come accede l’utente di ricerca, inseriscilo, prova la connessione e salva il servizio. La prova accede e descrive il cluster: il suo ID, la sua release e quanti region server sono attivi.

Le impostazioni usano i nomi dei client di HBase, quindi si possono copiare da un `hbase-site.xml`. L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dai client che leggono e scrivono. Una prova di connessione riuscita non significa che le richieste siano già soggette alle policy.

| Impostazione | Scopo |
| --- | --- |
| `hbase.zookeeper.quorum` | Host ZooKeeper, `host` o `host:porta` separati da virgole, come `zk1,zk2,zk3:2181`; obbligatorio senza server di bootstrap |
| `hbase.zookeeper.property.clientPort` | La porta degli host del quorum che non ne indicano una, predefinita `2181` |
| `zookeeper.znode.parent` | La radice del cluster in ZooKeeper, predefinita `/hbase` |
| `hbase.client.bootstrap.servers` | Al posto di ZooKeeper, da HBase 2.5: master o region server, `host:porta` separati da virgole |
| `hbase.security.authentication` | `simple` (predefinito): un nome utente; `kerberos`: un principal con keytab o password |
| `username` | Utente di ricerca obbligatorio; con Kerberos il principal completo, come `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: la password del principal, senza keytab; memorizzata cifrata |
| `keytab` | Kerberos: percorso della keytab del principal, leggibile sul **server GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del realm del principal, `host[:porta]` separati da virgole; vuoto usa il `krb5.conf` del server |
| `hbase.master.kerberos.principal` | Kerberos: il principal dei master, predefinito `hbase/_HOST@` e il realm del principal di ricerca |
| `hbase.regionserver.kerberos.principal` | Kerberos: il principal dei region server, predefinito quello dei master |
| `hbase.rpc.protection` | `authentication` (predefinito), `integrity` o `privacy`, come quello del cluster |
| `hbase.rpc.timeout` | Timeout RPC, predefinito `30000`, intervallo `1..600000` |

Un servizio è esattamente ciò che dicono le sue impostazioni: non viene letto alcun `hbase-site.xml` del server GrantForge. Le impostazioni Kerberos sono rifiutate con l’autenticazione simple; Kerberos richiede una password o una keytab. Le ricerche rinunciano dopo pochi tentativi anziché dopo i minuti di HBase.

### Kerberos

Con `kerberos`, GrantForge fa accedere il principal tramite il login di Hadoop stesso, con la keytab o la password, conserva l’accesso e lo rinnova dalla keytab; nient’altro sul server GrantForge richiede impostazioni Kerberos. I realm i cui KDC sono inseriti in `kerberos.kdc` vengono dichiarati per l’intero server GrantForge, come fanno i plug-in HDFS e Hive, così più servizi possono usare realm diversi fianco a fianco.

Un cluster chiude la connessione di un client che accede diversamente da come si aspetta, senza dire perché. Il plug-in lo segnala come accesso rifiutato e rimanda a `hbase.security.authentication` e `hbase.rpc.protection`, che devono corrispondere a quelli del cluster.

### Cosa vede l’utente di ricerca

Quando l’[agente HBase](/it/external/hbase-agent/) applica le policy, gli elenchi omettono i namespace e le tabelle che l’utente di ricerca non può usare: assegnagli una policy che consenta `read` sul namespace `*`, oppure indicalo in `hbase.superuser` del cluster.

## Risorse e tipi di accesso

| Livello | Corrisponde a | Tipi di accesso |
| --- | --- | --- |
| `global` | Il suo unico valore, `*` | `admin`: operazioni sull’intero cluster, come il bilanciatore, le quote e la replica |
| `namespace` | Caratteri jolly, con maiuscole e minuscole | `read`, `write`, `create`, `admin` |
| `table` | Caratteri jolly, con maiuscole e minuscole; sotto un namespace, nominata senza di esso | `read`, `write`, `create`, `admin` |
| `column_family` | Caratteri jolly, con maiuscole e minuscole; sotto una tabella | `read`, `write` |
| `column` | Caratteri jolly, con maiuscole e minuscole; sotto una famiglia di colonne; nominata come testo UTF-8 | `read`, `write` |

I tipi di accesso sono quelli di HBase: `read`, `write`, `create` e `admin`. L’`exec` di HBase, per gli endpoint dei coprocessori, non è offerto, perché l’agente non può controllare queste chiamate su ogni release di HBase.

Come in HBase, un permesso copre tutto ciò che rientra nel suo ambito: `read` sul namespace `sales` legge ogni sua tabella, `write` su una famiglia di colonne scrive tutte le sue colonne. Una richiesta viene decisa in ogni ambito che nomina, e un divieto in uno qualsiasi la rifiuta; una policy può così consentire una tabella e negarne una colonna. L’ambito globale è il cluster stesso, non ogni tabella; le policy sul namespace `*` coprono ogni tabella.

Nei valori delle policy, `*` corrisponde a un numero qualsiasi di caratteri e `?` a uno. L’editor delle policy suggerisce namespace, tabelle dei namespace che una policy nomina e famiglie di colonne delle sue tabelle; le colonne contengono dati e non vengono suggerite.

## Esempio di policy

Supponi che il namespace `sales` abbia una tabella `orders` con le famiglie di colonne `d`, che contiene `name` e `ssn`, e `meta`. Crea queste policy di accesso abilitate nello stesso servizio dati:

| Risorse | Regola |
| --- | --- |
| `namespace=sales` | Consentire `read` all’utente `alice` |
| `namespace=sales`, `table=orders`, `column_family=d` | Consentire `read` all’utente `bob` |
| `namespace=sales`, `table=orders` | Consentire `read` al gruppo `auditors` |
| `namespace=sales`, `table=orders`, `column_family=d`, `column=ssn` | Negare `read` al gruppo `auditors` |
| `namespace=analytics` | Consentire `create` e `admin` all’utente `dave` |

Quando l’agente applica il nuovo snapshot, `alice` legge ogni tabella di `sales`; `bob` legge `orders` ottenendo solo le colonne di `d`, e quando elenca le tabelle vede solo `orders`; i membri di `auditors` leggono tutto `orders` tranne `d:ssn`; `dave` crea il namespace `analytics` e le sue tabelle, le amministra, e altrove le tabelle gli vengono rifiutate.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Per applicarle serve l’agente HBase in ogni master e region server. Vedi l’[agente HBase](/it/external/hbase-agent/) per la distribuzione e i controlli.

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente HBase](/it/external/hbase-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
