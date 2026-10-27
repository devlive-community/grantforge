---
title: PostgreSQL
description: Das PostgreSQL-Dienst-Plug-in installieren und konfigurieren, einen Server oder mehrere erreichen, sich mit einem Passwort oder einem TLS-Client-Zertifikat anmelden, Datenbanken, Schemas, Tabellen und Spalten nachschlagen und verwalten, wer was lesen, schreiben und anlegen darf, bis auf Spalten und Zeilen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das PostgreSQL-Plug-in schlägt Datenbanken, Schemas, Tabellen und Spalten nach und verwaltet Zugriffsrichtlinien in den eigenen Begriffen von PostgreSQL: Spalten lesen, einfügen und ändern, Tabellen löschen, leeren und mit Triggern versehen, Objekte in einem Schema anlegen sowie Schemas und temporäre Tabellen in einer Datenbank anlegen, mit Zeilenfiltern für Tabellen. Plug-in-ID und Diensttyp sind beide `postgresql`. Das Dienst-Plug-in läuft im GrantForge-Server; der [PostgreSQL-Agent](/de/external/postgresql-agent/), ein Dienst neben den Servern, setzt die Richtlinien mit den eigenen Rechten und Zeilensicherheitsrichtlinien von PostgreSQL durch.

Die Testcontainers-Integrationstests des Repositorys decken PostgreSQL `15`, `16`, `17` und `18` ab, die Versionen, die PostgreSQL pflegt; das zertifiziert nicht jede andere Version.

## Installation

Die Distribution enthält das Plug-in in `plugins/postgresql`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `postgresql` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit den Servern über den eigenen JDBC-Treiber von PostgreSQL, in einem eigenen Class Loader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie PostgreSQL (`postgresql`).
2. Geben Sie einen Server als `host:port` an, oder mehrere Server.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie seinen Benutzernamen und seine Zugangsdaten ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Server: seine PostgreSQL-Version, den angemeldeten Benutzer und wie viele Datenbanken der Server hat.

Der Lookup-Benutzer lässt GrantForge Ressourcen entdecken; er ist von den Benutzern getrennt, die lesen und schreiben. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits Richtlinien unterliegt.

| Einstellung | Zweck |
| --- | --- |
| `postgresql.hosts` | Pflicht: ein Server als `host:port`, etwa `postgres1.example.com:5432`; mehrere durch Kommas getrennt, nacheinander versucht, wenn einer nicht erreichbar ist. Der Port ist standardmäßig `5432` |
| `postgresql.database` | Die Datenbank, an der sich der Lookup-Benutzer zuerst anmeldet, um die Datenbanken aufzulisten; Standard `postgres` |
| `authentication` | `password` (Standard) oder `certificate`: ein TLS-Client-Zertifikat, das die `cert`-Authentifizierung von PostgreSQL dem Benutzer zuordnet |
| `username` | Pflicht: der Lookup-Benutzer |
| `password` | password: das Passwort des Lookup-Benutzers; verschlüsselt gespeichert |
| `ssl.mode` | TLS, wie es `sslmode` von libpq nennt: `disable`, `allow`, `prefer` (Standard), `require`, `verify-ca` oder `verify-full`, das auch den Namen des Servers prüft |
| `ssl.root.certificate` | verify-ca und verify-full: das PEM-CA-Zertifikat, gegen das die Server geprüft werden, auf dem **GrantForge-Server**; leer verwendet das der JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate: das PEM-Client-Zertifikat und sein privater PKCS-#8-Schlüssel, PEM oder DER, auf dem GrantForge-Server; die Anmeldung mit Zertifikat braucht `require` oder einen Prüfmodus, und ein Prüfmodus braucht dazu `ssl.root.certificate` |
| `timeout` | Wie lange Verbinden und eine Abfrage dauern dürfen, Standard `30000`, Bereich `1..600000` Millisekunden |

Die Einstellungen einer Anmeldeart werden bei einer anderen abgelehnt, sodass ein Dienst genau angibt, wie er sich anmeldet. Der Treiber von PostgreSQL lehnt eine Schlüsseldatei ab, die andere als ihr Eigentümer lesen dürfen. Eine abgelehnte Anmeldung, eine fehlende Datenbank, ein nicht erreichbarer Server oder einer, dessen Zertifikat sich nicht prüfen lässt, werden als solche gemeldet.

### Was der Lookup-Benutzer sieht

Die Kataloge von PostgreSQL zeigen jedem Benutzer die Namen der Datenbanken und in jeder Datenbank, mit der er sich verbinden darf, die Namen der Schemas, Tabellen und Spalten, ohne Daten zu lesen. Der Lookup-Benutzer braucht nur `CONNECT` für die Datenbanken, für die Richtlinien geschrieben werden sollen; PUBLIC hat es, sofern es nicht entzogen wurde. Ein Lookup von Schemas, Tabellen oder Spalten meldet sich an jeder Datenbank an, die die Richtlinie nennt, höchstens 20, und überspringt die, mit denen sich der Lookup-Benutzer nicht verbinden darf. Die eigenen Schemas von PostgreSQL, die mit `pg_` beginnenden und `information_schema`, sind ausgenommen: Richtlinien entscheiden sie nicht.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `database` | Platzhalter, mit Groß-/Kleinschreibung; der Name der Datenbank | `create_schema`, `temporary` |
| `schema` | Platzhalter, mit Groß-/Kleinschreibung; unter einer Datenbank | `create` |
| `table` | Platzhalter, mit Groß-/Kleinschreibung; Tabellen, partitionierte Tabellen, Sichten, materialisierte Sichten und Fremdtabellen unter einem Schema | `delete`, `truncate`, `trigger` |
| `column` | Platzhalter, mit Groß-/Kleinschreibung; unter einer Tabelle | `select`, `insert`, `update` |

| Zugriffsart | Rechte von PostgreSQL |
| --- | --- |
| `select`, `insert`, `update` | `SELECT`, `INSERT`, `UPDATE`, auf Spalten oder auf der ganzen Tabelle |
| `delete`, `truncate`, `trigger` | `DELETE`, `TRUNCATE`, `TRIGGER` auf der Tabelle |
| `create` | `CREATE` auf dem Schema: Tabellen, Sichten, Sequenzen, Funktionen und andere Objekte darin |
| `create_schema` | `CREATE` auf der Datenbank: Schemas darin |
| `temporary` | `TEMPORARY` auf der Datenbank |

Lesen, Einfügen und Ändern werden je Spalte entschieden, wie PostgreSQL sie vergibt: Eine Richtlinie für `select`, `insert` oder `update` nennt Spalten, `*` für alle. So kann eine Richtlinie eine Tabelle erlauben und eine Spalte davon verweigern, und der Benutzer darf jede andere Spalte lesen. In Richtlinienwerten passt `*` auf beliebig viele Zeichen und `?` auf eines. Der Richtlinieneditor schlägt die Datenbanken vor, die Schemas der Datenbanken, die eine Richtlinie nennt, ihre Tabellen und Spalten.

Was eine Richtlinie erlaubt, zu erreichen, braucht mehr von PostgreSQL: `CONNECT` für die Datenbank, `USAGE` für das Schema und, um in eine Serial-Spalte einzufügen, `USAGE` für deren Sequenz. Sie sind keine eigenen Zugriffsarten: Der Agent vergibt sie mit dem, was sie braucht. PostgreSQL hat keine Spaltenmaskierung, daher bietet dieser Diensttyp keine.

### Zeilenfilter

Eine Zeilenfilterrichtlinie auf einer Tabelle zeigt ihren Benutzern die Zeilen, für die eine Bedingung gilt. Die Bedingung ist ein PostgreSQL-Ausdruck, wie ihn das `USING` einer Zeilensicherheitsrichtlinie nimmt, etwa `region = 'EU'` oder `owner = current_user`. Ein Benutzer sieht den Filter der ersten Richtlinie, die für ihn gilt, wie bei den anderen Diensten. PostgreSQL filtert nur die Zeilen von Tabellen und partitionierten Tabellen: Eine Sicht oder Ähnliches, deren Zeilen eine Richtlinie für einen Benutzer filtert, kann dieser Benutzer gar nicht lesen.

## Richtlinienbeispiel

Angenommen, der Server hat die Datenbanken `sales` und `hr`, und `sales` hat das Schema `crm`. Legen Sie diese aktivierten Richtlinien im selben Datendienst an:

| Ressourcen | Regel |
| --- | --- |
| `database=sales`, `schema=crm`, `table=*`, `column=*` | `select` für Gruppe `analysts` erlauben |
| `database=sales`, `schema=crm`, `table=orders`, `column=card` | `select` für Gruppe `analysts` verweigern |
| `database=sales`, `schema=crm`, `table=orders`, `column=*` | `insert` für Benutzer `ingest` erlauben |
| `database=sales`, `schema=team_*` | `create` für Gruppe `data-team` erlauben |
| `database=sales`, `schema=team_*`, `table=*` | `delete` und `truncate` für Gruppe `data-team` erlauben |
| `database=sales`, `schema=team_*`, `table=*`, `column=*` | `select`, `insert` und `update` für Gruppe `data-team` erlauben |
| `database=sales` | `create_schema` für Gruppe `data-team` erlauben |
| `database=sales`, `schema=crm`, `table=orders` (Zeilenfilter) | Benutzer `emea` die Zeilen `region = 'EMEA'` zeigen |

Sobald der Agent den neuen Snapshot angewendet hat, lesen Mitglieder von `analysts` jede Tabelle von `crm` außer den Kartennummern von `orders`; `ingest` fügt in `orders` ein, seine Serial-IDs eingeschlossen, und liest nichts; Mitglieder von `data-team` legen Schemas an und in denen, deren Name mit `team_` beginnt, Tabellen, die sie lesen, ändern und leeren; und `emea` sieht, wenn eine Richtlinie es `orders` lesen lässt, nur die Zeilen der eigenen Region.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Sie durchzusetzen braucht den PostgreSQL-Agenten, einen je Server oder Primärserver. Bereitstellung und Prüfungen stehen unter [PostgreSQL-Agent](/de/external/postgresql-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [PostgreSQL-Agent](/de/external/postgresql-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
