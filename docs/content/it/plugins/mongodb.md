---
title: MongoDB
description: Installare e configurare il plug-in di servizio MongoDB, collegarsi a un replica set o cluster partizionato, accedere con SCRAM, certificato client, LDAP o Kerberos, cercare database e collezioni, e gestire chi può leggere, scrivere e amministrare cosa, collezione per collezione.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in MongoDB cerca database e collezioni e gestisce le policy di accesso nei termini di MongoDB stesso: lettura, change stream, inserimento, aggiornamento e rimozione, indici, opzioni di collezione, l’aggiramento della validazione dei documenti ed eliminazione, per collezione; creazione di collezioni ed eliminazione del database, per database; elenco di tutti i database e monitoraggio, per il deployment nel suo insieme. L’ID del plug-in e il tipo di servizio sono entrambi `mongodb`. Il plug-in di servizio viene eseguito nel server GrantForge; l’[agente MongoDB](/it/external/mongodb-agent/), eseguito accanto al deployment, applica le policy con i ruoli di MongoDB stesso.

I test di integrazione Testcontainers del repository coprono replica set di MongoDB `6.0`, `7.0`, `8.0` e `9.0`, e Kerberos su Percona Server for MongoDB `8.0`. Questo non certifica ogni altra versione.

## Installazione

La distribuzione include il plug-in in `plugins/mongodb`. Verificate che `mongodb` sia attivato in **Gestione della piattaforma → Plug-in**; ripetete la scansione dopo aver aggiornato la directory dei plug-in. Il plug-in parla con MongoDB tramite il driver Java ufficiale di MongoDB, in un proprio class loader.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e selezionate MongoDB (`mongodb`).
2. Inserite la stringa di connessione del deployment, come la accetta qualsiasi client MongoDB.
3. Indicate come accede l’utente di ricerca, provate la connessione e salvate il servizio. La prova accede e descrive il deployment: la versione di MongoDB, se è un replica set, un cluster partizionato o un singolo server, chi ha effettuato l’accesso e quanti database vede quell’utente.

L’utente di ricerca permette a GrantForge di trovare le risorse e non è l’utente che legge o scrive. Una prova di connessione riuscita non significa che l’accesso segua già le policy.

| Impostazione | Scopo |
| --- | --- |
| `mongodb.uri` | Obbligatoria: la stringa di connessione, come `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` o `mongodb+srv://cluster.example.com/`. Le opzioni come `tls=true` vanno qui; le credenziali no, e una stringa che ne contiene viene rifiutata |
| `auth.mechanism` | `SCRAM-SHA-256` (predefinito) o `SCRAM-SHA-1` per utenti con password, `MONGODB-X509` per un certificato client, `PLAIN` per LDAP, `GSSAPI` per Kerberos |
| `username` | L’utente di ricerca. Con `GSSAPI` il principal; con `MONGODB-X509` vuoto lo prende dal certificato |
| `password` | La password dell’utente di ricerca, salvata cifrata |
| `auth.source` | SCRAM: il database in cui è definito l’utente, `admin` per impostazione predefinita. Gli altri meccanismi usano `$external` |
| `tls.ca.file` | Con `tls=true`: percorso sul **server GrantForge** dei certificati CA PEM con cui vengono verificati i server. Vuoto usa quelli della JVM |
| `tls.certificate.key.file` | Con `tls=true`: percorso sul **server GrantForge** di un file PEM con il certificato client e la sua chiave, come lo accetta `mongosh` |
| `tls.certificate.key.password` | La password di una chiave cifrata, salvata cifrata |
| `kerberos.keytab` | `GSSAPI`: percorso sul **server GrantForge** del keytab del principal. Vuoto usa la password |
| `kerberos.kdc` | `GSSAPI`: i KDC del realm, separati da virgole. Vuoto usa il `krb5.conf` del server |
| `kerberos.service.name` | `GSSAPI`: il nome di servizio del principal dei server, `mongodb` per impostazione predefinita |
| `timeout` | Quanto possono durare la ricerca di un server, la connessione e un comando, `30000` per impostazione predefinita, nell’intervallo `1..600000` millisecondi |

Un accesso rifiutato, un utente di ricerca privo di un privilegio e un deployment irraggiungibile vengono segnalati come tali.

### Cosa vede l’utente di ricerca

MongoDB elenca i database e le collezioni su cui un utente ha un qualsiasi privilegio, quindi una ricerca mostra ciò che l’utente di ricerca può vedere. Dategli `read` su ciò per cui si scrivono le policy, o `readAnyDatabase` per tutto. I database di MongoDB stesso, `admin`, `config` e `local`, e le collezioni di sistema sono omessi: le policy non li decidono.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `deployment` | Caratteri jolly, distingue maiuscole e minuscole. Il nome del replica set, `sharded` per un cluster partizionato, `standalone` per un singolo server | `list_databases`, `monitor` |
| `database` | Caratteri jolly, distingue maiuscole e minuscole | `create`, `drop_database` |
| `collection` | Caratteri jolly, distingue maiuscole e minuscole. Una collezione o vista di un database | `read`, `change_stream`, `insert`, `update`, `remove`, `create_index`, `drop_index`, `alter`, `bypass_validation`, `drop` |

| Tipo di accesso | Azioni di MongoDB |
| --- | --- |
| `read` | `find`, `listIndexes`, `collStats`, `killCursors`; su un intero database anche `listCollections` e `dbStats` |
| `change_stream` | `changeStream`; per aprirne uno MongoDB richiede anche `find` |
| `insert`, `update`, `remove` | `insert`, `update`, `remove` |
| `create_index`, `drop_index` | `createIndex`, `dropIndex` |
| `alter` | `collMod`, `validate`, `convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | `createCollection` sul database: creare collezioni e viste |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`: il nome di ogni database, non solo di quelli su cui l’utente ha un privilegio |
| `monitor` | `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `inprog` |

Nei valori delle policy, `*` corrisponde a qualsiasi testo e `?` a un carattere. L’editor delle policy suggerisce il deployment, i database e le collezioni dei database che una policy nomina.

Il privilegio di MongoDB di eliminare un database è il privilegio di eliminare ogni sua collezione, quindi l’agente concede `drop_database` solo dove le policy permettono anche di eliminare ogni collezione. Altrimenti trattiene il privilegio e lo segnala, invece di dare più di quanto le policy permettano. L’amministrazione di utenti e ruoli non è un tipo di accesso, e nessuna policy la dà.

### Righe e campi

MongoDB non filtra righe né maschera campi per ruolo, quindi questo tipo di servizio non offre né l’una né l’altra cosa. Una vista dà una lettura ristretta: MongoDB verifica il lettore di una vista solo rispetto alla vista, così una policy che permette `read` su una vista, e nulla sulla sua origine, mostra all’utente ciò che la vista mostra e nient’altro.

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## Esempio di policy

Supponiamo che il deployment abbia i database `sales` e `hr`. Create queste policy attivate in un servizio dati:

| Risorsa | Regola |
| --- | --- |
| `database=sales`, `collection=*` | Permettere `read` al gruppo `analysts` |
| `database=sales`, `collection=orders_public` | Permettere `read` all’utente `support` |
| `database=sales`, `collection=orders` | Permettere `insert` e `update` all’utente `ingest`; negare `read` al gruppo `contractors` |
| `database=team_*` | Permettere `create` al gruppo `data-team` |
| `database=team_*`, `collection=*` | Permettere `read`, `insert`, `update`, `remove`, `create_index` e `drop` al gruppo `data-team` |
| `deployment=*` | Permettere `monitor` all’utente `dashboards` |

Dopo che l’agente ha applicato lo snapshot, i membri di `analysts` leggono tutto `sales`, anche le collezioni create in seguito; `support` legge la vista senza la sua origine; `ingest` scrive in `orders` senza leggerlo; i membri di `data-team` lavorano liberamente nei database `team_`; e `dashboards` vede lo stato del server e nessun dato.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Per applicarle serve un agente MongoDB per deployment; vedete [Agente MongoDB](/it/external/mongodb-agent/) per la distribuzione e le verifiche.

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente MongoDB](/it/external/mongodb-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
