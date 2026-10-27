---
title: Apache Doris
description: Das Apache-Doris-Service-Plug-in installieren und konfigurieren, ein oder mehrere Frontends erreichen, sich mit einem Passwort anmelden, Kataloge, Datenbanken, Tabellen, Spalten und Workload-Gruppen nachschlagen und verwalten, wer was lesen, laden und verwalten darf, bis hinunter zu Spalten und Zeilen, und wer in welcher Workload-Gruppe Abfragen ausführen darf.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Apache-Doris-Plug-in schlägt Kataloge, Datenbanken, Tabellen, Spalten und Workload-Gruppen nach und verwaltet Zugriffsrichtlinien in den eigenen Begriffen von Doris: Spalten lesen, Tabellen laden, ändern, löschen und ihre Sichtdefinitionen zeigen, Tabellen in einer Datenbank anlegen und sie löschen, Datenbanken in einem Katalog anlegen und Abfragen in einer Workload-Gruppe ausführen, mit Zeilenfiltern von Tabellen. Plug-in-ID und Diensttyp sind beide `doris`. Das Service-Plug-in läuft im GrantForge-Server; der [Apache-Doris-Agent](/de/external/doris-agent/), ein Dienst neben dem Cluster, setzt die Richtlinien mit den eigenen Rollen, Rechten und Zeilenrichtlinien von Doris durch.

Die Testcontainers-Integrationstests des Repositorys decken Apache Doris `2.1`, `3.1`, `4.0` und `4.1` ab, jeweils auf einem Cluster aus einem Frontend und einem Backend; das zertifiziert nicht jede andere Version.

## Installation

Das Distributionspaket enthält das Plug-in in `plugins/doris`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `doris` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht über MariaDB Connector/J mit dem MySQL-Protokoll der Frontends, in einem eigenen Classloader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Apache Doris (`doris`).
2. Geben Sie die MySQL-Protokolladresse eines Frontends ein, oder mehrerer Frontends.
3. Geben Sie Namen und Passwort des Lookup-Benutzers ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Cluster: die Doris-Version, den angemeldeten Benutzer, wie viele Kataloge er sieht und wie viele Datenbanken des internen Katalogs. Er fragt nur das Frontend und antwortet daher auch, solange kein Backend läuft.

Der Lookup-Benutzer lässt GrantForge Ressourcen finden; er ist von den Benutzern getrennt, die lesen und laden. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits Richtlinien unterliegt.

| Einstellung | Zweck |
| --- | --- |
| `doris.hosts` | Pflicht: die MySQL-Protokolladresse eines Frontends, etwa `fe1.example.com:9030`; mehrere durch Kommas getrennt, nacheinander versucht, wenn eines nicht erreichbar ist. Ohne Angabe ist der Port `9030` |
| `username` | Pflicht: der Lookup-Benutzer |
| `password` | Das Passwort des Lookup-Benutzers; verschlüsselt gespeichert |
| `ssl.mode` | `disable` (Standard), `trust` zum Verschlüsseln ohne Prüfung des Frontends, `verify-ca` zum Prüfen seines Zertifikats oder `verify-full`, das auch seinen Namen prüft; für Frontends, die TLS im MySQL-Protokoll anbieten |
| `ssl.root.certificate` | verify-ca und verify-full: das PEM-CA-Zertifikat auf dem **GrantForge-Server**, gegen das die Frontends geprüft werden; leer nutzt das der JVM |
| `timeout` | Wie lange Verbindungsaufbau und eine Anweisung dauern dürfen, Standard `30000`, Bereich `1..600000` Millisekunden |

Abgelehnte Anmeldungen, ein Lookup-Benutzer ohne nötiges Recht und nicht erreichbare Frontends werden als solche gemeldet.

### Was der Lookup-Benutzer sieht

Die `SHOW`-Anweisungen von Doris listen nur die Kataloge, Datenbanken, Tabellen und Spalten, auf die ein Benutzer irgendein Recht hat; eine Suche zeigt also, was der Lookup-Benutzer sehen darf. Geben Sie ihm `SELECT_PRIV` auf das, wofür Richtlinien geschrieben werden sollen, etwa `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`; `SHOW WORKLOAD GROUPS` listet die Gruppen, die er sehen darf. Die eigenen Datenbanken von Doris, `information_schema`, `mysql` und `__internal_schema`, werden ausgelassen: Richtlinien entscheiden sie nicht.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `catalog` | Platzhalter, Groß-/Kleinschreibung beachtet; der interne Katalog oder ein externer | `create_database` |
| `database` | Platzhalter, Groß-/Kleinschreibung beachtet; unterhalb eines Katalogs | `create`, `drop_database` |
| `table` | Platzhalter, Groß-/Kleinschreibung beachtet; unterhalb einer Datenbank, eine Tabelle oder Sicht | `load`, `alter`, `drop`, `show_view` |
| `column` | Platzhalter, ohne Beachtung der Groß-/Kleinschreibung, wie Doris Spaltennamen vergleicht; unterhalb einer Tabelle | `select` |
| `workload_group` | Platzhalter, Groß-/Kleinschreibung beachtet; der Name der Gruppe | `usage` |

| Zugriffsart | Recht von Doris |
| --- | --- |
| `select` | `SELECT_PRIV`, auf Spalten oder auf eine Tabelle, Datenbank oder einen Katalog als Ganzes |
| `load` | `LOAD_PRIV`: Laden, `INSERT`, `UPDATE`, `DELETE` und `TRUNCATE` |
| `alter`, `drop` | `ALTER_PRIV`, `DROP_PRIV` auf die Tabelle |
| `show_view` | `SHOW_VIEW_PRIV`: `SHOW CREATE VIEW` |
| `create` | `CREATE_PRIV` auf die Datenbank: sie anlegen und Tabellen und Sichten darin |
| `drop_database` | `DROP_PRIV` auf die Datenbank |
| `create_database` | `CREATE_PRIV` auf den Katalog |
| `usage` | `USAGE_PRIV` auf die Workload-Gruppe |

Lesen wird pro Spalte entschieden, so wie Doris es gewährt: Eine `select`-Richtlinie nennt Spalten, `*` steht für alle. Eine Richtlinie kann so eine Tabelle erlauben und eine Spalte davon verweigern, und der Benutzer liest jede andere Spalte. In Richtlinienwerten passt `*` auf beliebig viele Zeichen und `?` auf eines. Der Richtlinieneditor schlägt die Kataloge, die Datenbanken der von der Richtlinie genannten Kataloge, deren Tabellen und Spalten sowie die Workload-Gruppen vor.

Das Recht von Doris auf eine Datenbank reicht auch auf jede Tabelle darin, das auf einen Katalog auf jede Datenbank darin. Der Agent gewährt `create_database` daher nur, wo die Richtlinien dem Benutzer auch erlauben, in jeder Datenbank des Katalogs Tabellen anzulegen, und `drop_database` nur, wo sie ihm auch erlauben, jede Tabelle der Datenbank zu löschen; sonst hält er das Recht zurück und sagt es, statt mehr zu gewähren, als die Richtlinien erlauben. `ADMIN_PRIV`, `GRANT_PRIV` und `NODE_PRIV` sind keine Zugriffsarten: Keine Richtlinie vergibt sie.

Doris maskiert Werte nur über Apache Ranger, daher bietet der Diensttyp keine Maskierung.

### Zeilenfilter

Eine Zeilenfilter-Richtlinie einer Tabelle zeigt ihren Benutzern die Zeilen, für die eine Bedingung gilt. Die Bedingung ist ein Doris-Prädikat, wie Zeilenrichtlinien es nehmen, etwa `region = 'EU'`; der Agent führt ihn zuerst auf der Tabelle aus, und ein Filter, der dort nicht läuft, zeigt keine Zeilen. Wie bei anderen Diensten erhält ein Benutzer den Filter der ersten Richtlinie, die auf ihn zutrifft.

## Richtlinienbeispiel

Angenommen, der Cluster hat die Datenbanken `sales` und `hr` im internen Katalog und eine Workload-Gruppe `etl`. Legen Sie im selben Datendienst diese aktivierten Richtlinien an:

| Ressourcen | Regel |
| --- | --- |
| `catalog=internal`, `database=sales`, `table=*`, `column=*` | Gruppe `analysts` `select` erlauben |
| `catalog=internal`, `database=sales`, `table=orders`, `column=card` | Gruppe `analysts` `select` verweigern |
| `catalog=internal`, `database=sales`, `table=orders` | Benutzer `ingest` `load` erlauben |
| `catalog=internal`, `database=team_*` | Gruppe `data-team` `create` erlauben |
| `catalog=internal`, `database=team_*`, `table=*` | Gruppe `data-team` `load`, `alter` und `drop` erlauben |
| `workload_group=etl` | Benutzer `ingest` und Gruppe `data-team` `usage` erlauben |
| `catalog=internal`, `database=sales`, `table=orders` (Zeilenfilter) | Benutzer `emea` die Zeilen `region = 'EMEA'` zeigen |

Sobald der Agent den neuen Snapshot anwendet, lesen Mitglieder von `analysts` jede Tabelle von `sales` außer den Kartennummern von `orders`; `ingest` lädt `orders` und darf seine Ladevorgänge in `etl` ausführen; Mitglieder von `data-team` legen Tabellen in den `team_`-Datenbanken an, die ein Administrator angelegt hat, und laden, ändern und löschen sie; und `emea` sieht, wenn eine Richtlinie es `orders` lesen lässt, nur die Zeilen seiner Region.

## Richtlinien durchsetzen

Das Service-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Sie durchzusetzen braucht den Apache-Doris-Agenten, einen pro Cluster. Bereitstellung und Prüfungen stehen im [Apache-Doris-Agenten](/de/external/doris-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Apache-Doris-Agent](/de/external/doris-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
