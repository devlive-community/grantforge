---
title: Database
description: Database e versioni supportati, modalità di connessione, driver e particolarità dei diversi database.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Ambito supportato

| Database | Versioni verificate | Driver |
| --- | --- | --- |
| H2 | inclusa nella versione | incluso; consigliato solo per le prove |
| PostgreSQL | 14, 17 | incluso |
| MySQL | 8.0, 8.4 | va inserito a mano in `drivers/` (Connector/J è sotto licenza GPL e non viene distribuito con il pacchetto di rilascio) |
| MariaDB | 10.11, 11.4 | incluso |
| Oracle | Free 23 | incluso |
| SQL Server | 2022 | incluso |

Per ogni versione la CI esegue “inizializzazione su database vuoto più tutti i test di integrazione”. I database cinesi (DM, KingbaseES, openGauss, OceanBase, ecc.) non rientrano nell’ambito supportato.

## Esempi di connessione

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle (nome del servizio)
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

Nome utente e password si impostano rispettivamente con `GRANTFORGE_DB_USER` e `GRANTFORGE_DB_PASSWORD`. Il database va creato in anticipo e l’account deve avere il permesso di creare tabelle: al primo avvio GrantForge crea tutte le tabelle con Liquibase e anche le migrazioni delle versioni successive vengono eseguite automaticamente da Liquibase. Hibernate si limita a validare la struttura delle tabelle e non la modifica mai.

Su PostgreSQL GrantForge tenta di abilitare l’estensione `pg_trgm` e crea un indice trigram sui nomi di accesso, sui nomi visualizzati e sulle email degli utenti, in modo che la ricerca “contiene” su account su scala del milione resti nell’ordine di poche decine di millisecondi. Da PostgreSQL 13 è un’estensione trusted e può essere abilitata dal proprietario del database; se l’account non dispone di questo permesso, il servizio si avvia comunque, la ricerca passa a una scansione completa della tabella e, dopo che un amministratore ha eseguito `CREATE EXTENSION pg_trgm`, l’indice viene creato automaticamente all’avvio successivo.

## Set di caratteri

- **MySQL / MariaDB**: al momento della creazione del database usa il set di caratteri `utf8mb4`, così che cinese ed emoji vengano salvati per intero.
- **SQL Server, Oracle**: le colonne di testo che possono contenere cinese usano `NVARCHAR`; il testo lungo è `NVARCHAR(MAX)` su SQL Server e `CLOB` su Oracle, indipendentemente dal set di caratteri predefinito del database.
- **Oracle**: la stringa vuota viene considerata `NULL`, quindi GrantForge tratta in modo uniforme i valori vuoti come “non compilati” nello strato di dominio; il comportamento è identico a quello degli altri database.

## Backup e ripristino

Tutti i dati di business si trovano nel database (anche le sessioni), quindi basta fare il backup del database; se usi i plug-in, fai il backup anche di `plugins/`. Se `grantforge.security.encryption-key` non è impostata, la chiave di cifratura viene salvata anch’essa nel database e per decifrare è sufficiente ripristinare il backup; se invece la chiave è impostata, dovrai conservare con cura anche quella.
