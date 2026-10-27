---
title: Apache Doris
description: Installare e configurare il plug-in di servizio Apache Doris, raggiungere uno o più frontend, accedere con una password, cercare cataloghi, database, tabelle, colonne e workload group, e gestire chi può leggere, caricare e amministrare cosa, fino a colonne e righe, e chi può eseguire query in quale workload group.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Apache Doris cerca cataloghi, database, tabelle, colonne e workload group, e gestisce le policy di accesso nei termini di Doris: leggere colonne, caricare, modificare ed eliminare tabelle e mostrare la definizione delle loro viste, creare tabelle in un database ed eliminarlo, creare database in un catalogo ed eseguire query in un workload group, con filtri delle righe delle tabelle. L’ID del plug-in e il tipo di servizio sono entrambi `doris`. Il plug-in di servizio gira nel server GrantForge; l’[agente Apache Doris](/it/external/doris-agent/), un servizio accanto al cluster, applica le policy con i ruoli, i privilegi e le policy di riga di Doris stesso.

I test di integrazione Testcontainers del repository coprono Apache Doris `2.1`, `3.1`, `4.0` e `4.1`, ciascuno su un cluster di un frontend e un backend; ciò non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/doris`. Verificate che `doris` sia attivato in **Gestione della piattaforma → Plug-in**; ripetete la scansione della directory dei plug-in dopo averla aggiornata. Il plug-in comunica con il protocollo MySQL dei frontend tramite MariaDB Connector/J, in un class loader proprio.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e selezionate Apache Doris (`doris`).
2. Inserite l’indirizzo del protocollo MySQL di un frontend, o di più frontend.
3. Inserite nome e password dell’utente di ricerca, provate la connessione e salvate il servizio. La prova accede e descrive il cluster: la versione di Doris, l’utente con cui ha acceduto, quanti cataloghi vede e quanti database del catalogo interno. Interroga solo il frontend, quindi risponde anche quando nessun backend è attivo.

L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dagli utenti che leggono e caricano. Una prova di connessione riuscita non significa che l’accesso sia già soggetto alle policy.

| Impostazione | Scopo |
| --- | --- |
| `doris.hosts` | Obbligatoria: l’indirizzo del protocollo MySQL di un frontend, ad esempio `fe1.example.com:9030`; più indirizzi separati da virgole, provati a turno quando uno non è raggiungibile. Senza porta, è `9030` |
| `username` | Obbligatoria: l’utente di ricerca |
| `password` | La password dell’utente di ricerca; salvata cifrata |
| `ssl.mode` | `disable` (predefinito), `trust` per cifrare senza verificare il frontend, `verify-ca` per verificarne il certificato o `verify-full` per verificarne anche il nome; per frontend che offrono TLS sul protocollo MySQL |
| `ssl.root.certificate` | verify-ca e verify-full: il certificato CA PEM con cui si verificano i frontend, sul **server GrantForge**; vuoto usa quelli della JVM |
| `timeout` | Quanto possono durare la connessione e un’istruzione, predefinito `30000`, intervallo `1..600000` millisecondi |

Accessi rifiutati, un utente di ricerca a cui manca un privilegio e frontend irraggiungibili sono segnalati come tali.

### Cosa vede l’utente di ricerca

Le istruzioni `SHOW` di Doris elencano solo i cataloghi, i database, le tabelle e le colonne su cui un utente ha qualche privilegio, quindi una ricerca mostra ciò che l’utente di ricerca può vedere. Dategli `SELECT_PRIV` su ciò per cui vanno scritte policy, ad esempio `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`; `SHOW WORKLOAD GROUPS` elenca i gruppi che può vedere. I database propri di Doris, `information_schema`, `mysql` e `__internal_schema`, sono omessi: le policy non li decidono.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `catalog` | Caratteri jolly, sensibile alle maiuscole; il catalogo interno o uno esterno | `create_database` |
| `database` | Caratteri jolly, sensibile alle maiuscole; sotto un catalogo | `create`, `drop_database` |
| `table` | Caratteri jolly, sensibile alle maiuscole; sotto un database, una tabella o vista | `load`, `alter`, `drop`, `show_view` |
| `column` | Caratteri jolly, non sensibile alle maiuscole, come Doris confronta i nomi delle colonne; sotto una tabella | `select` |
| `workload_group` | Caratteri jolly, sensibile alle maiuscole; il nome del gruppo | `usage` |

| Tipo di accesso | Privilegio di Doris |
| --- | --- |
| `select` | `SELECT_PRIV`, su colonne, o su una tabella, un database o un catalogo interi |
| `load` | `LOAD_PRIV`: caricamenti, `INSERT`, `UPDATE`, `DELETE` e `TRUNCATE` |
| `alter`, `drop` | `ALTER_PRIV`, `DROP_PRIV` sulla tabella |
| `show_view` | `SHOW_VIEW_PRIV`: `SHOW CREATE VIEW` |
| `create` | `CREATE_PRIV` sul database: crearlo, e crearvi tabelle e viste |
| `drop_database` | `DROP_PRIV` sul database |
| `create_database` | `CREATE_PRIV` sul catalogo |
| `usage` | `USAGE_PRIV` sul workload group |

La lettura si decide per colonna, come Doris la concede: una policy `select` nomina colonne, `*` per tutte. Una policy può così consentire una tabella e negarne una colonna, e l’utente legge tutte le altre. Nei valori delle policy `*` corrisponde a qualsiasi sequenza di caratteri e `?` a un carattere. L’editor delle policy suggerisce i cataloghi, i database dei cataloghi nominati dalla policy, le loro tabelle e colonne, e i workload group.

Il privilegio di Doris su un database raggiunge anche ogni sua tabella, e quello su un catalogo ogni suo database. L’agente concede quindi `create_database` solo dove le policy lasciano anche creare tabelle in ogni database del catalogo, e `drop_database` solo dove lasciano anche eliminare ogni tabella del database; altrimenti trattiene il privilegio e lo segnala, invece di concedere più di quanto le policy consentano. `ADMIN_PRIV`, `GRANT_PRIV` e `NODE_PRIV` non sono tipi di accesso: nessuna policy li dà.

Doris maschera i valori solo tramite Apache Ranger, quindi questo tipo di servizio non offre mascheramento.

### Filtri delle righe

Una policy di filtro delle righe di una tabella mostra ai suoi utenti le righe per cui vale una condizione. La condizione è un predicato Doris, come lo prendono le policy di riga, ad esempio `region = 'EU'`; l’agente lo esegue prima sulla tabella, e un filtro che non può esservi eseguito non mostra alcuna riga. Come per gli altri servizi, un utente riceve il filtro della prima policy che gli si applica.

## Esempio di policy

Supponiamo che il cluster abbia i database `sales` e `hr` nel suo catalogo interno e un workload group `etl`. Create queste policy attive nello stesso servizio dati:

| Risorse | Regola |
| --- | --- |
| `catalog=internal`, `database=sales`, `table=*`, `column=*` | Consentire `select` al gruppo `analysts` |
| `catalog=internal`, `database=sales`, `table=orders`, `column=card` | Negare `select` al gruppo `analysts` |
| `catalog=internal`, `database=sales`, `table=orders` | Consentire `load` all’utente `ingest` |
| `catalog=internal`, `database=team_*` | Consentire `create` al gruppo `data-team` |
| `catalog=internal`, `database=team_*`, `table=*` | Consentire `load`, `alter` e `drop` al gruppo `data-team` |
| `workload_group=etl` | Consentire `usage` all’utente `ingest` e al gruppo `data-team` |
| `catalog=internal`, `database=sales`, `table=orders` (filtro delle righe) | Mostrare all’utente `emea` le righe `region = 'EMEA'` |

Quando l’agente applica il nuovo snapshot, i membri di `analysts` leggono ogni tabella di `sales` tranne i numeri di carta di `orders`; `ingest` carica `orders` e può eseguire i suoi caricamenti in `etl`; i membri di `data-team` creano tabelle nei database `team_` creati da un amministratore, e le caricano, modificano ed eliminano; ed `emea`, se una policy gli lascia leggere `orders`, vede solo le righe della sua regione.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Applicarle richiede l’agente Apache Doris, uno per cluster. Distribuzione e verifiche sono descritte in [Agente Apache Doris](/it/external/doris-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente Apache Doris](/it/external/doris-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
