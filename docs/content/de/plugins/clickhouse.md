---
title: ClickHouse
description: Das ClickHouse-Dienst-Plug-in installieren und konfigurieren, einen Server oder mehrere erreichen, sich mit einem Passwort oder einem TLS-Client-Zertifikat anmelden, Datenbanken, Tabellen und Spalten suchen und verwalten, wer was lesen, schreiben und administrieren darf, bis hinunter zu Spalten und Zeilen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das ClickHouse-Plug-in sucht Datenbanken, Tabellen und Spalten und verwaltet Zugriffsrichtlinien in den Begriffen von ClickHouse: Spalten lesen und schreiben, Tabellen ändern, löschen, leeren und optimieren, Tabellen in einer Datenbank anlegen und sie löschen, sowie auf dem Server das Anlegen von Datenbanken, `KILL QUERY` und `SYSTEM`-Anweisungen, mit Zeilenfiltern für Tabellen. Plug-in-ID und Diensttyp sind beide `clickhouse`. Das Dienst-Plug-in läuft im GrantForge-Server; der [ClickHouse-Agent](/de/external/clickhouse-agent/), ein Dienst neben den Servern, setzt die Richtlinien mit den eigenen Berechtigungen und Zeilenrichtlinien von ClickHouse durch.

Die Testcontainers-Integrationstests des Repositorys decken ClickHouse `24.8`, `25.3`, `25.8`, `26.3` und `26.8` ab, die Versionen mit Langzeitunterstützung der letzten zwei Jahre; das zertifiziert nicht jede andere Version.

## Installation

Die Distribution enthält das Plug-in in `plugins/clickhouse`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `clickhouse` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit den Servern über HTTP mit dem eigenen Java-Client von ClickHouse, in einem eigenen Klassenlader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie ClickHouse (`clickhouse`).
2. Geben Sie die URL der HTTP-Schnittstelle eines Servers oder mehrerer Server ein.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie seinen Benutzernamen und seine Anmeldedaten ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Server: seine ClickHouse-Version, den Benutzer, als der er sich angemeldet hat, und wie viele Datenbanken dieser Benutzer sieht.

Der Lookup-Benutzer lässt GrantForge Ressourcen entdecken; er ist unabhängig von den Benutzern, die lesen und schreiben. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits Richtlinien unterliegt.

| Einstellung | Zweck |
| --- | --- |
| `clickhouse.urls` | Pflicht: die HTTP-Schnittstelle eines Servers, etwa `https://clickhouse1.example.com:8443`; mehrere durch Kommas getrennt, der Reihe nach versucht, wenn einer nicht erreichbar ist |
| `authentication` | `password` (Standard) oder `certificate`: ein TLS-Client-Zertifikat, das ClickHouse dem Benutzer zuordnet (`IDENTIFIED WITH ssl_certificate`) |
| `username` | Pflicht: der Lookup-Benutzer |
| `password` | password: das Passwort des Lookup-Benutzers; verschlüsselt gespeichert |
| `ssl.root.certificate` | https: das PEM-CA-Zertifikat auf dem **GrantForge-Server**, gegen das die Server geprüft werden; leer nutzt das der JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate: das PEM-Client-Zertifikat und sein privater Schlüssel auf dem GrantForge-Server; die Anmeldung mit Zertifikat braucht `https://`-Server |
| `timeout` | Wie lange eine Anfrage dauern darf, Standard `30000`, Bereich `1..600000` Millisekunden |

Die Einstellungen einer Anmeldeart werden bei einer anderen abgelehnt, sodass ein Dienst genau sagt, wie er sich anmeldet. Eine abgelehnte Anmeldung, ein Benutzer mit nur Leserechten, ein Server, der nicht erreichbar ist oder dessen Zertifikat sich nicht prüfen lässt, und etwas anderes als ClickHouse, das an seiner Stelle antwortet, etwa ein Proxy, werden als solche gemeldet.

### Was der Lookup-Benutzer sieht

ClickHouse zeigt einem Benutzer nur die Datenbanken, Tabellen und Spalten, auf die er irgendein Recht hat. Geben Sie dem Lookup-Benutzer `SHOW` auf das, wofür Richtlinien geschrieben werden sollen, etwa `GRANT SHOW ON *.* TO grantforge_lookup`; damit sieht er Namen, ohne Daten zu lesen. Die eigenen Datenbanken von ClickHouse, `system` und `information_schema`, bleiben außen vor: Richtlinien entscheiden sie nicht.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `database` | Platzhalter, Groß-/Kleinschreibung beachtet; der Name der Datenbank | `create`, `drop_database` |
| `table` | Platzhalter, Groß-/Kleinschreibung beachtet; unter einer Datenbank | `alter`, `drop`, `truncate`, `optimize` |
| `column` | Platzhalter, Groß-/Kleinschreibung beachtet; unter einer Tabelle | `select`, `insert` |
| `server` | Sein einziger Wert, `*` | `create_database`, `kill_query`, `system` |

| Zugriffsart | Rechte von ClickHouse |
| --- | --- |
| `select`, `insert` | `SELECT`, `INSERT`, auf Spalten |
| `alter` | `ALTER`: jede Änderung einer Tabelle, `ALTER UPDATE` und `ALTER DELETE` eingeschlossen |
| `drop` | `DROP TABLE`, `DROP VIEW`, `DROP DICTIONARY` |
| `truncate`, `optimize` | `TRUNCATE`, `OPTIMIZE` |
| `create` | `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` in der Datenbank |
| `drop_database` | `DROP DATABASE` |
| `create_database`, `kill_query`, `system` | `CREATE DATABASE`, `KILL QUERY`, `SYSTEM`: jede `SYSTEM`-Anweisung |

Lesen und Schreiben werden pro Spalte entschieden, so wie ClickHouse sie vergibt: Eine Richtlinie für `select` oder `insert` nennt Spalten, `*` für alle. So kann eine Richtlinie eine Tabelle erlauben und eine ihrer Spalten verweigern, und der Benutzer darf jede andere Spalte lesen. In Richtlinienwerten steht `*` für eine beliebige Zeichenfolge und `?` für ein Zeichen. Der Richtlinieneditor schlägt die Datenbanken, die Tabellen der Datenbanken, die eine Richtlinie nennt, und deren Spalten vor.

Die Zugriffsarten sind keine der Rechtegruppen von ClickHouse wie `ALL`: Jede ist, was sie sagt, sodass eine Richtlinie nie mehr gibt, als sie nennt. ClickHouse hat außerhalb von ClickHouse Cloud keine Spaltenmaskierung, daher bietet dieser Diensttyp keine.

### Zeilenfilter

Eine Zeilenfilterrichtlinie auf einer Tabelle zeigt ihren Benutzern die Zeilen, für die eine Bedingung gilt. Die Bedingung ist ein ClickHouse-Ausdruck, wie Zeilenrichtlinien ihn nehmen, etwa `region = 'EU'` oder `owner = currentUser()`. Ein Benutzer sieht den Filter der ersten Richtlinie, die für ihn gilt, wie bei den anderen Diensten.

## Richtlinienbeispiel

Angenommen, der Server hat die Datenbanken `sales` und `hr`. Legen Sie im selben Datendienst diese aktivierten Richtlinien an:

| Ressourcen | Regel |
| --- | --- |
| `database=sales`, `table=*`, `column=*` | `select` für Gruppe `analysts` erlauben |
| `database=sales`, `table=orders`, `column=card` | `select` für Gruppe `analysts` verweigern |
| `database=sales`, `table=orders`, `column=*` | `insert` für Benutzer `ingest` erlauben |
| `database=team_*` | `create` für Gruppe `data-team` erlauben |
| `database=team_*`, `table=*` | `alter`, `drop`, `truncate` und `optimize` für Gruppe `data-team` erlauben |
| `database=team_*`, `table=*`, `column=*` | `select` und `insert` für Gruppe `data-team` erlauben |
| `server=*` | `create_database` für Gruppe `data-team` erlauben |
| `database=sales`, `table=orders` (Zeilenfilter) | Benutzer `emea` die Zeilen `region = 'EMEA'` zeigen |

Sobald der Agent den neuen Snapshot anwendet, lesen Mitglieder von `analysts` jede Tabelle von `sales` außer den Kartennummern von `orders`; `ingest` schreibt `orders` und liest nichts; Mitglieder von `data-team` legen Datenbanken an und darin, wenn ihr Name mit `team_` beginnt, Tabellen, und lesen, schreiben und ändern sie; und `emea` sieht, sofern eine Richtlinie es `orders` lesen lässt, nur die Zeilen seiner eigenen Region.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und sucht Ressourcen. Sie durchzusetzen erfordert den ClickHouse-Agenten, einen je Server oder Cluster. Bereitstellung und Prüfungen beschreibt der [ClickHouse-Agent](/de/external/clickhouse-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [ClickHouse-Agent](/de/external/clickhouse-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
