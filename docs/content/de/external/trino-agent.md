---
title: Trino-Agent
description: Die Systemzugriffskontrolle im Trino-Coordinator installieren, um Abfragen mit GrantForge-Richtlinien zu prüfen, nur aufzulisten, was ein Benutzer verwenden darf, Zeilen zu filtern und Spalten zu maskieren und Identitätswechsel sowie die Abfragen anderer Benutzer Richtlinien zu unterstellen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der Trino-Agent ist eine Systemzugriffskontrolle namens `grantforge`: Der Coordinator fragt ihn zu jedem Katalog, Schema, jeder Tabelle, Spalte, Routine, Sitzungseigenschaft und jedem Benutzer, den eine Abfrage berührt, und er antwortet aus den signierten Richtlinien des `trino`-Datendiensts. Trino 470 bis 478 laufen auf Java 23 oder 24 und Trino 479 und neuer auf Java 25, daher liefert die Distribution zwei JARs, jedes mit seinen Abhängigkeiten eingebettet und verschoben:

| Trino | JAR |
| --- | --- |
| 470 bis 478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 und neuer | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

Die Testcontainers-Integrationstests des Repositorys führen das erste JAR in Trino `470` und `476` aus und das zweite in `479` und `484`. Das zertifiziert nicht jede andere Version. Das Dienst-Plug-in beschreibt [Trino](/de/plugins/trino/).

## Funktionsweise

- **Jede Prüfung wird in jedem Geltungsbereich entschieden, den sie nennt.** Eine Berechtigung auf einem Katalog, Schema oder einer Tabelle umfasst, was darin liegt, daher entscheidet der Agent eine Spalte in ihrem Katalog, ihrem Schema, ihrer Tabelle und bei sich selbst: Eine Verweigerung in einem davon lehnt ab, eine Erlaubnis in einem davon gewährt.
- **Auflistungen lassen weg, was nicht verwendet werden darf.** `SHOW CATALOGS`, `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW FUNCTIONS`, `information_schema` und die Metadaten des Treibers zeigen nur, was der Benutzer verwenden darf oder worin er etwas verwenden darf.
- **Was Daten beschreibt, bleibt offen.** Das `information_schema` jedes Katalogs, die Schemas `jdbc` und `metadata` des Katalogs `system`, die JDBC-Treiber und BI-Werkzeuge lesen, und Trinos eingebaute Funktionen brauchen keine Richtlinie; ihre Zeilen werden wie Auflistungen gefiltert.
- **Zeilen werden gefiltert und Spalten maskiert.** Zeilenfilterrichtlinien gelten für jede Abfrage, die die Tabelle liest, und Masken werden an den Typ der Spalte angepasst, damit eine Maske nie eine Abfrage bricht: Textmasken gelten für `varchar`- und `char`-Spalten und werden zurück in den Typ der Spalte umgewandelt, die Jahresmaske für Datums- und Zeitstempelwerte, ein eigener Ausdruck wird in den Typ umgewandelt, und jede andere Spalte, die eine Textmaske nennt, liest sich als `NULL` ihres Typs.
- **Identitätswechsel und fremde Abfragen unterliegen Richtlinien.** Eine Abfrage als anderer Benutzer braucht `impersonate` auf diesem Benutzer; die Abfragen eines anderen Benutzers zu sehen oder abzubrechen braucht `view_query` oder `kill_query` auf ihm. Jeder darf Abfragen ausführen und seine eigenen sehen und abbrechen.
- **Ablehnungen sagen, warum.** Der Client erhält `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`, oder dass keine Richtlinie es erlaubt oder noch keine Richtlinien gekommen sind.
- **Entscheidungen werden behalten und Zugriffe zusammengefasst.** Die Entscheidungen des aktuellen Snapshots werden behalten, standardmäßig 100.000 für 10 Sekunden, und ein neuer Snapshot beginnt von vorn. Gleiche erlaubte Zugriffe innerhalb eines Fensters, standardmäßig 10 Sekunden, werden als ein Zugriffsereignis gemeldet, das sagt, wie viele es waren; Ablehnungen werden einzeln und sofort gemeldet. Jedes Ereignis nennt die Abfrage.
- **Berechtigungen gehören GrantForge.** Trinos `GRANT`, `REVOKE`, `DENY` und Rollenanweisungen werden abgelehnt, da Richtlinien in GrantForge verwaltet werden.

| Anweisung | Benötigter Zugriff |
| --- | --- |
| `SELECT` | `select` auf jeder gelesenen Spalte; eine Abfrage, die keine Spalte liest, etwa `count(*)`, auf der Tabelle |
| `INSERT`, `DELETE`, `TRUNCATE` | `insert`; `delete` für die beiden anderen |
| `UPDATE` | `update` auf jeder geschriebenen Spalte |
| `CREATE SCHEMA`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW` | `create` auf dem, was angelegt wird |
| `DROP` eines Schemas, einer Tabelle, Sicht oder materialisierten Sicht | `drop` darauf |
| `ALTER` einer Tabelle, Sicht oder Spalte, `COMMENT`, `SET PROPERTIES`, `SET AUTHORIZATION`, `ALTER TABLE ... EXECUTE` | `alter` darauf |
| Umbenennen eines Schemas, einer Tabelle, Sicht oder materialisierten Sicht | `alter` darauf und `create` auf dem neuen Namen |
| `REFRESH MATERIALIZED VIEW` | `insert` darauf |
| Eine Sicht, die ihr Eigentümer ausführt | Ihr Eigentümer braucht `select` auf den Spalten und `execute` auf den Funktionen, die sie liest |
| `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW COLUMNS`, `SHOW FUNCTIONS`, `SHOW CREATE` | Irgendein Zugriff darauf oder darin |
| `CREATE CATALOG`, `DROP CATALOG` | `create` oder `drop` auf dem Katalog |
| Eine Funktion aufrufen; `CREATE FUNCTION`, `DROP FUNCTION` | `execute`; `create_function`, `drop_function` auf der Funktion |
| `CALL` | `execute` auf der Prozedur |
| `SET SESSION` | `set` auf der Sitzungseigenschaft des Katalogs oder des Systems |
| Als anderer Benutzer ausführen, fremde Abfragen sehen oder abbrechen | `impersonate`, `view_query`, `kill_query` auf dem Benutzer |
| Systeminformationen lesen oder schreiben: Knoten, Statistiken und Thread-Dumps der REST-API und Weboberfläche von Trino | `read` oder `write` auf `system_information` |

Ab Trino 479 prüft das zweite JAR auch Tabellen-Branches: Sie aufzulisten braucht, die Tabelle sehen zu dürfen, und einen anzulegen, zu löschen oder vorzuspulen braucht `alter` auf ihr; eine Sicht zu aktualisieren und Eigentümer oder Kommentar einer materialisierten Sicht zu ändern braucht `alter` darauf.

## Bereitstellung

1. Fügen Sie einen `trino`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agent-Token aus. Es wird nur einmal angezeigt; legen Sie es in einer Datei ab, die nur Trinos Konto lesen kann.
2. Kopieren Sie das JAR zur Version des Coordinators in ein eigenes Verzeichnis in Trinos Plug-in-Verzeichnis, etwa `/usr/lib/trino/plugin/grantforge/`. Nur der Coordinator prüft Zugriffe; die Worker brauchen nichts.
3. Legen Sie auf dem Coordinator `etc/access-control.properties` an und ersetzen Sie Beispieladresse, Pfade und Instanznamen:

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   Das Cache-Verzeichnis muss für Trinos Konto beschreibbar sein. GrantForge ersetzt Trinos dateibasierte oder andere Zugriffskontrolle, statt neben ihr zu laufen.
4. Starten Sie den Coordinator neu. Bis der Agent seinen ersten Snapshot hat, werden Abfragen abgelehnt, außer für das, was Daten beschreibt.
5. **Vorhandene Regeln zuerst übertragen:** Die Regeln von Trinos dateibasierter Zugriffskontrolle gelten nicht mehr. Legen Sie vorher passende Richtlinien an.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Erforderlich: der GrantForge-Server, die Datei des Agent-Tokens, ein Name für diesen Coordinator und ein beschreibbares Verzeichnis für den signierten Snapshot und zwischengespeicherte Ereignisse |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten X.509-Ed25519-Public-Key von der Seite Agenten. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeout zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Spool-Grenze (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange gleiche erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` meldet jeden |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Die meisten behaltenen Entscheidungen, Standard `100000`, bis `10000000`, `0` behält keine; und wie lange, Standard `10000`, `1..3600000` Millisekunden |

## Benutzer, Gruppen und Rollen

Richtlinien nennen den Sitzungsbenutzer der Abfrage, wie Trino ihn nach Authentifizierung und Benutzerzuordnung gibt. Gruppen sind die, die Trinos Gruppenanbieter dem Benutzer gibt, zusammen mit den Gruppen, in die GrantForge den Benutzer stellt; Rollen sind die, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne verwendbaren Snapshot wird alles abgelehnt außer dem, was Daten beschreibt, dem Ausführen von Abfragen und den eigenen Abfragen. Ein nicht verwendeter Dienst lässt Trino ungeprüft, wie ohne Zugriffskontrolle; Trinos Berechtigungs- und Rollenanweisungen werden trotzdem abgelehnt.
- Ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `trino` ist, lehnt alles ab.
- Trino fragt vor allem anderen, ob ein Benutzer einen Katalog überhaupt verwenden darf: Ein Benutzer ohne Berechtigung in einem Katalog erhält Trinos eigenes `Cannot access catalog`.
- Ein `SELECT`, das eine Spalte liest, die der Benutzer nicht lesen darf, wird abgelehnt und nennt die erste solche Spalte; Spalten werden nicht aus dem Ergebnis entfernt.
- Trino schreibt keine Tabelle, deren Spalten es für den Benutzer maskiert: Ein solches `INSERT` schlägt fehl, solange die Maske gilt.
- Eine Maske wird in den Typ der Spalte umgewandelt, daher zeigt ein begrenztes `varchar` so viel vom maskierten Wert, wie hineinpasst, etwa 3 Zeichen eines Hashes in `varchar(3)`.
- Ein Zeilenfilter oder eine Maske, die der Snapshot nicht lesen kann, zeigt keine Zeilen bzw. die Spalte als `NULL` statt der Daten.
- Entscheidungen werden für `grantforge.decision.cache.ttl.ms` behalten, daher beginnt oder endet die Gültigkeit einer Richtlinie bis zu so viel später.

## Weitere Anleitungen

- [Trino](/de/plugins/trino/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
