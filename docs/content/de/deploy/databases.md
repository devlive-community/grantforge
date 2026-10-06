---
title: Datenbanken
description: Unterstützte Datenbanken und Versionen, Verbindungswege, Treiber und Hinweise für den Einsatz mit mehreren Datenbanken.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Unterstützungsbereich

| Datenbank | geprüfte Versionen | Treiber |
| --- | --- | --- |
| H2 | wird mit der Version geliefert | eingebaut, nur zum Ausprobieren empfohlen |
| PostgreSQL | 14, 17 | eingebaut |
| MySQL | 8.0, 8.4 | muss selbst nach `drivers/` gelegt werden (Connector/J steht unter GPL und wird nicht im Release-Paket mitgeliefert) |
| MariaDB | 10.11, 11.4 | eingebaut |
| Oracle | Free 23 | eingebaut |
| SQL Server | 2022 | eingebaut |

Für jede Version führt die CI „Initialisierung einer leeren Datenbank plus alle Integrationstests“ aus. Chinesische Datenbanken (DM, Kingbase, openGauss, OceanBase und weitere) liegen nicht im Unterstützungsbereich.

## Verbindungsbeispiele

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle (Dienstname)
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

Benutzername und Passwort werden über `GRANTFORGE_DB_USER` bzw. `GRANTFORGE_DB_PASSWORD` gesetzt. Die Datenbank muss vorher angelegt werden, und das Konto braucht das Recht, Tabellen zu erzeugen: Beim ersten Start legt GrantForge mit Liquibase alle Tabellen an, spätere Versionsupdates werden ebenfalls von Liquibase automatisch migriert. Hibernate prüft die Tabellenstruktur nur, ändert sie nie.

Auf PostgreSQL versucht GrantForge, die Erweiterung `pg_trgm` zu aktivieren, und legt für den Anmeldenamen, den Anzeigenamen und die E-Mail-Adresse der Benutzer einen Trigramm-Index an, damit die „Enthält“-Suche bei Millionen von Konten im Bereich von einigen zehn Millisekunden bleibt. Seit PostgreSQL 13 ist sie eine vertrauenswürdige Erweiterung, die der Datenbankbesitzer aktivieren kann; hat das Konto dieses Recht nicht, startet der Dienst ganz normal, die Suche weicht auf einen vollständigen Tabellenscan aus. Führt ein Administrator `CREATE EXTENSION pg_trgm` aus, werden die fehlenden Indizes beim nächsten Start automatisch nachgezogen.

## Zeichensätze

- **MySQL / MariaDB**: Lege die Datenbank mit dem Zeichensatz `utf8mb4` an, damit chinesische Zeichen und Emojis vollständig gespeichert werden.
- **SQL Server, Oracle**: Textspalten, die chinesische Zeichen enthalten können, erhalten `NVARCHAR`; lange Texte sind auf SQL Server `NVARCHAR(MAX)` und auf Oracle `CLOB`, unabhängig vom Standardzeichensatz der Datenbank.
- **Oracle**: Leere Zeichenketten gelten als `NULL`; GrantForge behandelt Leerwerte in der Domänenschicht einheitlich als „nicht ausgefüllt“, das Verhalten entspricht damit den anderen Datenbanken.

## Sicherung und Wiederherstellung

Alle Geschäftsdaten liegen in der Datenbank (auch die Sitzungen); es genügt, die Datenbank zu sichern; werden Plug-ins genutzt, sichere zusätzlich `plugins/`. Ist `grantforge.security.encryption-key` nicht gesetzt, liegt auch der Verschlüsselungsschlüssel in der Datenbank, und das Wiederherstellen der Sicherung genügt zum Entschlüsseln; ist er gesetzt, muss dieser Schlüssel separat sicher verwahrt werden.
