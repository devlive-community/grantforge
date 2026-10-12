---
title: MySQL
description: Installa e configura il plug-in MySQL, cerca database, tabelle e colonne e gestisci le policy di accesso, mascheramento e filtro delle righe.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in MySQL offre a GrantForge la ricerca di database, tabelle e colonne, oltre alle policy di accesso, mascheramento e filtro delle righe. L’ID del plug-in e il tipo di servizio sono entrambi `mysql`. Il plug-in di servizio viene eseguito sul server GrantForge; un [agente proxy MySQL](/it/external/mysql-agent/) indipendente controlla il SQL delle applicazioni.

La verifica della connessione accetta MySQL 8.0 e versioni successive. I test di integrazione Testcontainers del repository coprono `mysql:8.0` e `mysql:8.4`; questo non certifica tutte le altre versioni. I server MariaDB e MySQL 5.7 non rientrano nell’ambito supportato. `jdbc:mariadb://` è un formato URL del driver, non un’indicazione di supporto ai server MariaDB.

## Installazione

La distribuzione include il plug-in in `plugins/mysql`. Verifica che `mysql` sia abilitato in **Gestione della piattaforma → Plug-in**; dopo un aggiornamento, esegui di nuovo la scansione della directory dei plug-in. Il plug-in comunica con MySQL tramite MariaDB Connector/J (LGPL), che supporta il protocollo MySQL, inclusi `caching_sha2_password` e TLS, e viene eseguito in un class loader dedicato. Il Connector/J ufficiale di MySQL è distribuito con licenza GPL e non è incluso.

## Aggiungere un servizio dati

1. Apri **Permessi sui dati → Servizi dati**, aggiungi un servizio e seleziona MySQL (`mysql`).
2. Inserisci l’URL JDBC del server, ad esempio `jdbc:mysql://mysql.example.com:3306`. Funzionano anche `jdbc:mariadb://` e i formati di failover del driver, ad esempio `jdbc:mysql:replication://primary:3306,replica:3306`.
3. Inserisci l’utente di ricerca e la sua password, verifica la connessione e salva il servizio. La verifica esegue l’accesso, controlla la versione del server e legge `information_schema` come la ricerca delle risorse.

L’utente di ricerca consente a GrantForge di individuare le risorse ed è distinto dagli utenti con cui i client delle applicazioni accedono a MySQL. Una verifica della connessione riuscita non significa che il SQL delle applicazioni sia già soggetto alle policy.

| Impostazione | Scopo |
| --- | --- |
| `jdbc.url` | URL JDBC obbligatorio: `jdbc:mysql://host[:port]` o `jdbc:mariadb://…`, con database e `?options` facoltativi; le credenziali non vanno nell’URL |
| `username` | Utente di ricerca obbligatorio |
| `password` | Password dell’utente di ricerca; memorizzata in forma cifrata |
| `jdbc.properties` | Opzioni del driver, una `key=value` per riga, ad esempio `sslMode=verify-full` e `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | Timeout di connessione, predefinito `10000`, intervallo `1..600000` |
| `read.timeout.ms` | Timeout di lettura per una ricerca, predefinito `30000`, intervallo `1..600000` |

Le opzioni che consentirebbero al server di leggere file locali, eseguire istruzioni alla connessione, eseguire più istruzioni insieme, trasportare credenziali o caricare classi vengono rifiutate sia nell’URL sia in `jdbc.properties`, indipendentemente dalle maiuscole: `user`, `password`, `allowLocalInfile`, `allowMultiQueries`, `initSql`, `createDatabaseIfNotExist`, `socketFactory`, `credentialType` e le opzioni del pool. Le connessioni di ricerca sono di sola lettura.

Per TLS con verifica dell’identità del server, inserisci quanto segue in `jdbc.properties`:

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

Il file del certificato deve essere leggibile sul **server GrantForge** e il nome host nell’URL deve corrispondere al certificato. Il plug-in non richiede TLS per impostazione predefinita; configuralo esplicitamente. Questi sono i nomi delle opzioni di MariaDB Connector/J.

`information_schema` mostra a un utente solo gli oggetti sui quali ha privilegi. Ad esempio, per individuare le risorse del solo database `sales`, un amministratore del database può concedere questi permessi. Sostituisci `grantforge-host` con l’indirizzo di origine del server GrantForge:

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

Usa `REFERENCES ON *.*` solo quando è necessario individuare tutti i database. L’account dei metadati del proxy richiede anche `SHOW_ROUTINE`; consulta la guida dell’agente.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Note |
| --- | --- | --- |
| `database` | Caratteri jolly, distingue maiuscole e minuscole | Le policy possono terminare qui |
| `table` | Caratteri jolly, distingue maiuscole e minuscole | Sotto un database; le policy possono terminare qui |
| `column` | Caratteri jolly, non distingue maiuscole e minuscole | Sotto una tabella; tipi di accesso `select`, `insert` e `update` |

Database e tabelle vengono confrontati distinguendo maiuscole e minuscole, come MySQL li memorizza su Linux. Su un server con `lower_case_table_names` impostato a 1 o 2, l’agente converte in minuscolo i nomi controllati; scrivi quindi queste policy in minuscolo. I nomi delle colonne vengono sempre confrontati senza distinguere maiuscole e minuscole, come in MySQL.

I tipi di accesso seguono i privilegi di MySQL: `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `index` e `all`, che include questi otto tipi. `all` non equivale a `ALL PRIVILEGES` di MySQL e non elimina le restrizioni del proxy sulle istruzioni.

Nei valori delle policy, `*` corrisponde a un numero qualsiasi di caratteri e `?` a un solo carattere. L’editor suggerisce i nomi per prefisso: database, tabelle dei database indicati dalla policy e colonne delle relative tabelle. Nell’input dei suggerimenti, `%` e `_` sono caratteri letterali.

## Mascheramento

Le policy di mascheramento si applicano alle colonne. Ogni tipo di maschera è un’espressione MySQL 8 nella quale `{col}` rappresenta la colonna:

| Tipo di maschera | Risultato |
| --- | --- |
| `mask` | Lettere ASCII come `X`/`x` e cifre come `n`; gli altri caratteri restano invariati |
| `mask_show_last_4` | Solo gli ultimi quattro caratteri |
| `mask_show_first_4` | Solo i primi quattro caratteri |
| `mask_hash` | Un hash SHA-256 senza sale |
| `mask_null` | `NULL` |
| `mask_none` | Il valore invariato |
| `mask_date_show_year` | Il 1° gennaio dell’anno della data |
| `custom` | Un’espressione MySQL personalizzata, con `{col}` per la colonna, ad esempio `CONCAT('****', RIGHT({col}, 4))` |

Le maschere che mostrano i primi o gli ultimi quattro caratteri conservano il valore intero se non supera i quattro caratteri. Ad esempio, `mask_show_last_4` visualizza `4111111111111111` come `xxxxxxxxxxxx1111`.

## Filtri delle righe

Le policy di filtro delle righe si applicano alle tabelle. Un filtro è una condizione MySQL sulle colonne della tabella, ad esempio `region = 'EU'`: l’utente vede solo le righe che la soddisfano e le istruzioni `UPDATE` e `DELETE` su una singola tabella modificano solo quelle righe. Le scritture che coinvolgono più tabelle vengono rifiutate se alla tabella di destinazione deve essere applicato un filtro delle righe. Consulta la guida dell’agente per le altre restrizioni.

Maschere e filtri delle righe non concedono l’accesso. Una policy di accesso separata deve autorizzare l’operazione.

## Esempio di policy

Supponiamo che MySQL contenga una tabella `sales.orders` con le colonne `id`, `region` e `card`. Crea tre policy abilitate nello stesso servizio dati per l’utente di accesso MySQL `alice`:

| Tipo di policy | Risorse | Regola |
| --- | --- | --- |
| Accesso | `database=sales`, `table=orders`, `column=*` | Consentire `select` all’utente `alice` |
| Mascheramento | `database=sales`, `table=orders`, `column=card` | Applicare `mask_show_last_4` all’utente `alice` |
| Filtro delle righe | `database=sales`, `table=orders` | Applicare `region = 'EU'` all’utente `alice` |

Indica `alice` come utente della policy, non `alice@host`. Anche MySQL deve consentire a questo account di leggere la tabella. Quando il proxy applica il nuovo snapshot, le query di `alice` attraverso il proxy restituiscono solo le righe EU e mostrano soltanto gli ultimi quattro caratteri di ogni numero di carta. Anche i predicati e le espressioni di join vedono i valori mascherati. Consulta l’[agente proxy MySQL](/it/external/mysql-agent/) per il deployment e la verifica.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Per applicarle, incluse maschere e filtri delle righe, è necessario l’agente proxy MySQL, al quale i client si collegano al posto del server. Consulta la guida dell’agente per il deployment e le verifiche eseguite.

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente proxy MySQL](/it/external/mysql-agent/)
- [Sviluppo di plug-in e tipi di servizio](/it/develop/plugins/)
