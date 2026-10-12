---
title: MySQL
description: Das MySQL-Dienst-Plug-in installieren und konfigurieren, Datenbanken, Tabellen und Spalten nachschlagen und Zugriffs-, Maskierungs- und Zeilenfilter-Richtlinien verwalten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das MySQL-Plug-in stellt in GrantForge das Nachschlagen von Datenbanken, Tabellen und Spalten sowie Zugriffs-, Maskierungs- und Zeilenfilter-Richtlinien bereit. Plug-in-ID und Diensttyp lauten beide `mysql`. Das Dienst-Plug-in läuft auf dem GrantForge-Server; ein eigenständiger [MySQL-Proxy-Agent](/de/external/mysql-agent/) prüft die SQL-Anweisungen der Anwendungen.

Die Verbindungsprüfung akzeptiert MySQL 8.0 oder neuer. Die Testcontainers-Integrationstests im Repository decken `mysql:8.0` und `mysql:8.4` ab; damit sind nicht automatisch alle anderen Versionen geprüft. MariaDB-Server und MySQL 5.7 gehören nicht zum unterstützten Umfang. `jdbc:mariadb://` ist ein URL-Format des Treibers und bedeutet keine Unterstützung für MariaDB-Server.

## Installation

Die Distribution enthält das Plug-in in `plugins/mysql`. Prüfe unter **Plattformverwaltung → Plug-ins**, dass `mysql` aktiviert ist; nach einer Aktualisierung das Plug-in-Verzeichnis neu einlesen. Das Plug-in spricht mit MySQL über MariaDB Connector/J (LGPL), der das MySQL-Protokoll einschließlich `caching_sha2_password` und TLS beherrscht, in einem eigenen Classloader. MySQLs eigener Connector/J steht unter der GPL und wird nicht mitgeliefert.

## Datendienst hinzufügen

1. Öffne **Datenberechtigungen → Datendienste**, füge einen Dienst hinzu und wähle MySQL (`mysql`).
2. Gib die JDBC-URL des Servers ein, etwa `jdbc:mysql://mysql.example.com:3306`. `jdbc:mariadb://` funktioniert genauso, ebenso die Failover-Formen des Treibers wie `jdbc:mysql:replication://primary:3306,replica:3306`.
3. Gib den Lookup-Benutzer und sein Passwort ein, teste die Verbindung und speichere den Dienst. Der Test meldet sich an, prüft die Serverversion und liest `information_schema` wie die Suche.

Der Lookup-Benutzer dient GrantForge zum Ermitteln von Ressourcen und ist von den MySQL-Benutzern der Anwendungen getrennt. Ein erfolgreicher Verbindungstest bedeutet noch nicht, dass Richtlinien auf die SQL-Anweisungen der Anwendungen angewendet werden.

| Einstellung | Zweck |
| --- | --- |
| `jdbc.url` | Erforderliche JDBC-URL: `jdbc:mysql://host[:port]` oder `jdbc:mariadb://…`, optional mit Datenbank und `?options`; Zugangsdaten gehören nicht in die URL |
| `username` | Erforderlicher Lookup-Benutzer |
| `password` | Passwort des Lookup-Benutzers; verschlüsselt gespeichert |
| `jdbc.properties` | Treiberoptionen, eine `key=value` pro Zeile, etwa `sslMode=verify-full` und `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | Verbindungs-Timeout, Standard `10000`, Bereich `1..600000` |
| `read.timeout.ms` | Lese-Timeout einer Suche, Standard `30000`, Bereich `1..600000` |

Optionen, die den Server lokale Dateien lesen lassen, beim Verbinden Anweisungen ausführen, mehrere Anweisungen auf einmal erlauben, Zugangsdaten tragen oder Klassen laden, werden in URL und `jdbc.properties` unabhängig von der Schreibweise abgelehnt: `user`, `password`, `allowLocalInfile`, `allowMultiQueries`, `initSql`, `createDatabaseIfNotExist`, `socketFactory`, `credentialType` und die Pool-Optionen. Lookup-Verbindungen sind schreibgeschützt.

Für TLS mit Prüfung der Serveridentität trage Folgendes in `jdbc.properties` ein:

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

Die Zertifikatsdatei muss auf dem **GrantForge-Server** lesbar sein, und der Hostname in der URL muss zum Zertifikat passen. Das Plug-in verlangt TLS nicht standardmäßig; konfiguriere es ausdrücklich. Die Optionsnamen stammen von MariaDB Connector/J.

`information_schema` zeigt nur Objekte, auf die der Benutzer Rechte hat. Um beispielsweise nur Ressourcen der Datenbank `sales` zu ermitteln, kann ein Datenbankadministrator folgende Rechte vergeben. Ersetze `grantforge-host` durch die Quelladresse des GrantForge-Servers:

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

Verwende `REFERENCES ON *.*` nur, wenn alle Datenbanken ermittelt werden sollen. Das Metadatenkonto des Proxys benötigt zusätzlich `SHOW_ROUTINE`; siehe Agentenleitfaden.

## Ressourcen und Zugriffstypen

| Ebene | Abgleich | Hinweise |
| --- | --- | --- |
| `database` | Platzhalter, mit Groß-/Kleinschreibung | Richtlinien dürfen hier enden |
| `table` | Platzhalter, mit Groß-/Kleinschreibung | Unter einer Datenbank; Richtlinien dürfen hier enden |
| `column` | Platzhalter, ohne Groß-/Kleinschreibung | Unter einer Tabelle; Zugriffstypen `select`, `insert` und `update` |

Datenbanken und Tabellen werden mit Groß-/Kleinschreibung verglichen, wie MySQL sie unter Linux speichert. Auf einem Server mit `lower_case_table_names` 1 oder 2 wandelt der Agent die geprüften Namen in Kleinbuchstaben um; solche Richtlinien daher kleingeschrieben anlegen. Spaltennamen werden wie in MySQL immer ohne Groß-/Kleinschreibung verglichen.

Die Zugriffstypen folgen MySQLs Rechten: `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `index` und `all`, das diese acht Typen einschließt. `all` entspricht nicht MySQLs `ALL PRIVILEGES` und hebt die Anweisungsbeschränkungen des Proxys nicht auf.

In Richtlinienwerten steht `*` für beliebig viele Zeichen und `?` für genau ein Zeichen. Der Richtlinieneditor schlägt Namen nach ihrem Präfix vor: Datenbanken, Tabellen der genannten Datenbanken und Spalten ihrer Tabellen. `%` und `_` werden in der Eingabe für Vorschläge als normale Zeichen behandelt.

## Maskierung

Maskierungsrichtlinien gelten für Spalten. Jeder Maskentyp ist ein MySQL-8-Ausdruck, in dem `{col}` für die Spalte steht:

| Maskentyp | Zeigt |
| --- | --- |
| `mask` | ASCII-Buchstaben als `X`/`x`, Ziffern als `n`; andere Zeichen bleiben erhalten |
| `mask_show_last_4` | Nur die letzten vier Zeichen |
| `mask_show_first_4` | Nur die ersten vier Zeichen |
| `mask_hash` | Einen SHA-256-Hash ohne Salt |
| `mask_null` | `NULL` |
| `mask_none` | Den unveränderten Wert |
| `mask_date_show_year` | Den 1. Januar des Jahres |
| `custom` | Einen eigenen MySQL-Ausdruck mit `{col}` für die Spalte, etwa `CONCAT('****', RIGHT({col}, 4))` |

Die Masken für die ersten oder letzten vier Zeichen erhalten Werte mit höchstens vier Zeichen vollständig. Beispielsweise zeigt `mask_show_last_4` den Wert `4111111111111111` als `xxxxxxxxxxxx1111` an.

## Zeilenfilter

Zeilenfilter-Richtlinien gelten für Tabellen. Ein Filter ist eine MySQL-Bedingung auf die Spalten der Tabelle, etwa `region = 'EU'`: Der Benutzer sieht nur passende Zeilen, und `UPDATE` und `DELETE` auf einer einzelnen Tabelle ändern nur diese Zeilen. Schreibanweisungen über mehrere Tabellen werden abgelehnt, wenn ein Zeilenfilter auf die Zieltabelle angewendet werden müsste. Weitere Einschränkungen stehen im Agentenleitfaden.

Masken und Zeilenfilter vergeben keine Zugriffsrechte. Eine separate Zugriffsrichtlinie muss die Operation erlauben.

## Richtlinienbeispiel

Angenommen, MySQL enthält die Tabelle `sales.orders` mit den Spalten `id`, `region` und `card`. Lege im selben Datendienst drei aktivierte Richtlinien für den MySQL-Anmeldebenutzer `alice` an:

| Richtlinienart | Ressourcen | Regel |
| --- | --- | --- |
| Zugriff | `database=sales`, `table=orders`, `column=*` | `select` für Benutzer `alice` erlauben |
| Maskierung | `database=sales`, `table=orders`, `column=card` | `mask_show_last_4` für Benutzer `alice` verwenden |
| Zeilenfilter | `database=sales`, `table=orders` | `region = 'EU'` für Benutzer `alice` verwenden |

Trage als Richtlinienbenutzer `alice` ein, nicht `alice@host`. Auch MySQL selbst muss diesem Konto das Lesen der Tabelle erlauben. Sobald der Proxy den neuen Snapshot anwendet, erhält `alice` über den Proxy nur EU-Zeilen; von jeder Kartennummer bleiben nur die letzten vier Zeichen sichtbar. Bedingungen und Join-Ausdrücke sehen ebenfalls die maskierten Werte. Bereitstellung und Prüfung beschreibt der [MySQL-Proxy-Agent](/de/external/mysql-agent/).

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Um sie einschließlich Masken und Zeilenfiltern durchzusetzen, braucht es den MySQL-Proxy-Agenten, mit dem sich Clients statt mit dem Server verbinden. Bereitstellung und Prüfungen stehen im Agentenleitfaden.

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [MySQL-Proxy-Agent](/de/external/mysql-agent/)
- [Plug-ins und Diensttypen entwickeln](/de/develop/plugins/)
