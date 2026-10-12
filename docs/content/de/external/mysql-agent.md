---
title: MySQL-Proxy-Agent
description: Den MySQL-Netzwerk-Proxy bereitstellen, TLS und Richtlinienabgleich konfigurieren sowie Spaltenrechte, Maskierung, Zeilenfilter und Zugriffsfehler prüfen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der MySQL-Agent ist ein eigenständiger Netzwerk-Proxy: Clients verbinden sich mit ihm statt mit dem Server. Bevor unterstützte Anweisungen MySQL erreichen, prüft er sie anhand der signierten Richtlinien des Datendienstes `mysql`. Er muss nicht in MySQL installiert werden. Die Distribution enthält die ausführbare JAR-Datei `agents/mysql/grantforge-agent-mysql-<version>.jar`, die Java 17 oder höher benötigt.

Die Testcontainers-Integrationstests im Repository verwenden `mysql:8.0` und `mysql:8.4` mit MariaDB Connector/J und MySQL Connector/J als Clients. Damit werden weder andere Serverversionen noch sämtliche Funktionen des MySQL-Protokolls bestätigt. Versions- und Verbindungsanforderungen des Dienst-Plug-ins stehen unter [MySQL](/de/plugins/mysql/).

## Funktionsweise

- **Die Anmeldung läuft durch.** Die Begrüßung des Servers erreicht den Client und die Antworten des Clients den Server; MySQL prüft Passwörter also selbst (`caching_sha2_password` und `mysql_native_password`), und der Proxy erfährt den Benutzer, den MySQL angenommen hat. Richtlinien nennen diesen Benutzer ohne Host.
- **SQL-Ressourcen werden geprüft.** Der Proxy analysiert COM_QUERY und serverseitige Prepared Statements und prüft die erforderlichen Zugriffstypen auf Datenbanken, Tabellen und Spalten. Eine abgelehnte Anweisung erreicht MySQL nicht; der Proxy erzeugt einen Fehler mit einem MySQL-kompatiblen Code wie 1142 oder 1143.
- **Lesezugriffe werden maskiert und gefiltert.** Liest eine Anweisung eine Tabelle mit maskierten Spalten oder einem Zeilenfilter für den Benutzer, setzt der Proxy eine gleichnamige abgeleitete Tabelle an ihre Stelle, die die maskierten Werte und nur die gefilterten Zeilen zeigt; Joins, Bedingungen und Funktionen sehen so, was der Benutzer sehen darf.
- **Ereignisse zur Ressourcenautorisierung** werden mit Benutzer, Client-Adresse, Ressource, Zugriffstyp, Entscheidung und Anweisung an GrantForge gesendet. Der Umfang des Audits wird unten beschrieben.
- Die Ausführungsergebnisse von MySQL werden an den Client weitergeleitet; der Proxy verändert die ursprünglichen Werte in der Datenbank nicht.

## Bereitstellung

1. Lege einen Datendienst vom Typ `mysql` an und konfiguriere seine Richtlinien. Öffne **Datenberechtigungen → Agenten**, wähle den Dienst und stelle ein Agenten-Token aus. Es wird nur einmal angezeigt; speichere es in einer Datei, die nur das Konto des Proxys lesen kann.
2. Lege einen Metadaten-Benutzer für den Proxy an. Er liest Spalten und gespeicherte Funktionen aus `information_schema` und braucht keine Daten: `REFERENCES` zeigt alle Spalten, `SHOW_ROUTINE` alle gespeicherten Funktionen. Vergib beides direkt an den Benutzer, nicht über eine Rolle; ohne `SHOW_ROUTINE` startet der Proxy nicht.

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. Schreibe die Einstellungsdatei und ersetze die Beispieladressen, -pfade und -namen:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   Dieses Beispiel aktiviert TLS vom Client zum Proxy und vom Proxy zu MySQL. `mysql-proxy.crt` muss zum Proxy-Hostnamen passen, den Clients verwenden; das MySQL-Zertifikat muss zu `mysql.internal` passen. Die Pfade beziehen sich auf den Proxy-Rechner, und das Cache-Verzeichnis muss beschreibbar sein. Passwortdateien werden als UTF-8 gelesen und an beiden Enden von Leerraum bereinigt; Passwörter mit führendem oder abschließendem Leerraum lassen sich so nicht speichern.

4. Starte den Proxy, beispielsweise als systemd-Dienst:

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   Ersetze `<version>` durch die Distributionsversion. Einstellungen in der Form `java -Dgrantforge.…=… -jar …` überschreiben die Datei. Bei SIGTERM beendet sich der Proxy sauber und sendet oder spoolt ausstehende Ereignisse.
5. Richte die Clients auf Adresse und Port des Proxys. MySQL sieht nun die Adresse des Proxys; der Host-Teil der Konten muss sie zulassen.
6. **Schließe den Weg am Proxy vorbei:** Clients, die MySQL direkt erreichen, werden nicht geprüft. Lass nur den Proxy und Administratoren an den Port des Servers.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Erforderlich: GrantForge-Server, Datei des Agenten-Tokens, Name dieses Proxys und ein beschreibbares Verzeichnis für den signierten Snapshot und gespoolte Ereignisse |
| `grantforge.signing.key.file` | Optionale Datei zur festen Bindung des Snapshot-Signaturschlüssels; kopiere den Base64-kodierten X.509-Ed25519-Public-Key von der Agenten-Seite. Ohne diese Datei wird der Schlüssel beim ersten Kontakt vom Server abgerufen |
| `grantforge.proxy.host`, `grantforge.proxy.port` | Lauschadresse; standardmäßig alle Adressen und Port `3307`. Portbereich `0..65535`; `0` weist automatisch einen Port zu |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | PEM-Zertifikat und privater Schlüssel; beide sind gemeinsam erforderlich, um Clients TLS anzubieten |
| `grantforge.proxy.tls.required` | Standard `false`; `true` lehnt Clients ohne TLS ab (Fehler 3159) und erfordert Zertifikat und privaten Schlüssel |
| `grantforge.mysql.host`, `grantforge.mysql.port` | Erforderlicher MySQL-Host; Port standardmäßig `3306`, Bereich `1..65535` |
| `grantforge.mysql.tls` | TLS zu MySQL: `disabled` (Standard), `required` (verschlüsselt, ungeprüft), `verify-ca` oder `verify-identity` |
| `grantforge.mysql.tls.ca` | PEM-CA zur Prüfung von MySQL; ein leerer Wert verwendet die Vertrauensanker von Java |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | Erforderlicher Metadaten-Benutzer; die Passwortdatei ist optional. Ohne Datei erhält der Treiber kein Passwort |
| `grantforge.mysql.metadata.cache.seconds` | Cache-Dauer für Spalten und gespeicherte Funktionen: Standard `30`, Bereich `0..86400` Sekunden; `0` deaktiviert die Wiederverwendung des Caches |
| `grantforge.mysql.connect.timeout.ms` | MySQL-Verbindungs-Timeout: Standard `10000`, Bereich `1..600000` Millisekunden; Metadatenabfragen haben einen festen Timeout von `30000` Millisekunden |
| `grantforge.proxy.check.threads` | Threads für die Anweisungsprüfung: Standard `8`, Bereich `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge-Verbindungs- und Lese-Timeouts: Standard `5000` / `8000` Millisekunden, Bereich `1..2147483647` |
| `grantforge.refresh.interval.ms` | Anfängliches oder Rückfallintervall bei nicht erreichbarem Server: Standard `30000` Millisekunden, mindestens `1000`; normale Heartbeats verwenden das vom Server zurückgegebene Intervall |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | Standard: Batch `500`, Warteschlange `10000` Ereignisse; Batch-Bereich `1..1000`, Warteschlange mindestens so groß wie der Batch und höchstens `1000000` |
| `grantforge.audit.flush.interval.ms` | Audit-Versandintervall: Standard `5000` Millisekunden, Bereich `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | Audit-Spool-Limit: Standard `67108864` (64 MiB), mindestens `0` |

TLS ist auf beiden Seiten unabhängig. Nutzt nur eine Seite TLS und verlangt MySQL das ganze Passwort (`caching_sha2_password` ohne Cache-Treffer), trägt der Proxy es hinüber: Einem Client ohne TLS, der den Schlüssel des Servers anfordert, antwortet er mit einem eigenen RSA-Schlüssel, und das Passwort eines TLS-Clients verschlüsselt er für einen Server ohne TLS mit MySQLs Schlüssel. Clients dürfen durch den Proxy nicht mit MySQLs öffentlicher Schlüsseldatei konfiguriert sein.

## Was Anweisungen brauchen

| Anweisung | Zugriff |
| --- | --- |
| `SELECT`, Unterabfragen, `WITH`, `EXPLAIN`, `SET @x = (SELECT …)` | `select` auf jede genutzte Spalte; eine Tabelle, die ohne Spalte gelesen wird wie bei `COUNT(*)` oder `EXISTS`, braucht `select` auf die ganze Tabelle |
| `INSERT`, `LOAD DATA` | `insert` auf die geschriebenen Spalten (alle, wenn keine genannt sind); `ON DUPLICATE KEY UPDATE` braucht zusätzlich `update` |
| `REPLACE` | `insert` auf die Spalten und `delete` auf die Tabelle |
| `UPDATE` | `update` auf die gesetzten Spalten und `select` auf die gelesenen |
| `DELETE` | `delete` auf die Tabelle und `select` auf die gelesenen Spalten |
| `CREATE TABLE`, `CREATE VIEW` / `DROP TABLE`, `DROP VIEW`, `TRUNCATE` / `ALTER TABLE`, `ALTER VIEW`, `OPTIMIZE` | `create` / `drop` / `alter` auf die Tabelle oder Sicht |
| `RENAME TABLE` | `alter` und `drop` auf den alten Namen, `create` und `insert` auf den neuen |
| `CREATE INDEX`, `DROP INDEX` | `index` auf die Tabelle |
| `CREATE DATABASE`, `DROP DATABASE` | `create`, `drop` auf die Datenbank |
| `LOCK TABLES`, `ANALYZE`, `CHECK`, `CHECKSUM TABLE` | `select` auf die Tabelle |

Anweisungen ohne Daten gehen an MySQLs eigene Rechte: Transaktionen, `SET`, `USE`, `SHOW`, `DESCRIBE`, `KILL` und Kontoanweisungen wie `GRANT` und `CREATE USER`. `information_schema` und `performance_schema` sind Datenbanken wie alle anderen und brauchen Richtlinien.

Der Proxy lehnt Anweisungen ab, die er nicht vollständig prüfen kann: ausführbare Kommentare (`/*! … */`), gespeicherte Funktionen und `CALL`, `CREATE PROCEDURE`, `FUNCTION`, `TRIGGER` und `EVENT`, `PREPARE`/`EXECUTE` auf SQL-Ebene, `HANDLER`, Replikations- und Clone-Befehle, `USE` oder `SET NAMES` zusammen mit anderen Anweisungen in einem Stapel sowie Anweisungen, die er nicht analysieren kann.

## Masken, Zeilenfilter und ihre Grenzen

- Masken und Filter gelten überall, wo eine Anweisung die Tabelle liest, auch in Unterabfragen, Joins, `INSERT … SELECT` und `CREATE TABLE … SELECT` (dessen Kopie die maskierten Werte enthält).
- Ein `UPDATE` oder `DELETE` auf eine Tabelle ändert nur die Zeilen, die der Filter zeigt. Mehrtabellen-`UPDATE` oder -`DELETE` einer gefilterten Tabelle wird abgelehnt, ebenso ein Schreibzugriff, der eine maskierte Spalte der geänderten Tabelle nutzt, da er die verborgenen Werte kopieren oder ausforschen könnte.
- Eine Sicht über eine maskierte oder gefilterte Tabelle kann weder angelegt noch geändert werden: MySQL führt die Sicht später ohne den Proxy aus.
- Prepared Statements verwenden ihre Prüfung und Umschreibung erneut, solange die Richtlinienversion unverändert bleibt. Eine Versionsänderung löst eine erneute Prüfung aus: Die Ausführung geht weiter, wenn der Zugriff noch erlaubt und der umgeschriebene Text unverändert ist. Andernfalls verlangt Fehler 1615 eine erneute Vorbereitung durch den Client.
- Eine Spalte ohne Tabellenangabe zählt für jede Tabelle in Sichtweite, die eine solche Spalte hat; qualifiziere Spalten, um Ablehnungen wegen einer gesperrten gleichnamigen Spalte zu vermeiden.
- Spalten, die über `*` gelesen werden, stammen aus der Sicht des Metadaten-Benutzers auf `information_schema` und werden für `grantforge.mysql.metadata.cache.seconds` zwischengespeichert. Erfolgreiche DDL-Anweisungen, die als reguläre SQL-Befehle über den Proxy gesendet werden, machen den Cache ungültig; die nächste Abfrage liest die Metadaten erneut. Bei externem DDL oder Prepared-DDL ist die Invalidierung während der Ausführung nicht garantiert; warte auf den Cache-Ablauf oder starte den Proxy neu.

## Umfang der Durchsetzung

Der Proxy prüft die Ressourcen, die er im SQL des Clients analysieren kann. Bestehende Sichten, Trigger und andere serverintern ausgeführte Vorgänge werden nicht rekursiv aufgelöst. Um zugrunde liegende Tabellen zu schützen, beschränke zusätzlich native MySQL-Rechte und die Nutzung bestehender Sichten und Trigger. Gib Geschäftsanwendern keine Administratorrechte in der Erwartung, dass der Proxy administrative Anweisungen abfängt.

Ein Zeilenfilter ist keine allgemeine Schreibbeschränkung: `INSERT`, `REPLACE` und `LOAD DATA` prüfen Schreibrechte, verlangen aber nicht automatisch, dass neue Zeilen den Filterausdruck erfüllen. Auch dem Zweig von `INSERT … ON DUPLICATE KEY UPDATE`, der bestehende Zeilen aktualisiert, wird keine Zeilenfilterbedingung hinzugefügt. Protokollkomprimierung, ZSTD, Abfrageattribute und optionale Ergebnismetadaten werden beim Handshake deaktiviert.

## Richtlinien und Fehler

- Ohne verwendbaren Snapshot oder mit einem Token für einen Dienst, der nicht `mysql` ist, wird jede Anweisung abgelehnt, die Daten nutzt.
- Ist GrantForge vorübergehend nicht erreichbar oder die Signatur eines neuen Snapshots ungültig, behält der Proxy den bereits angewendeten Snapshot. Beim ersten Start ohne verwendbaren Snapshot wird der Ressourcenzugriff verweigert.
- Das Deaktivieren des Dienstes überspringt Ressourcenautorisierung, Maskierung und Zeilenfilterung durch GrantForge; SQL-Analyse und Protokollbeschränkungen gelten weiterhin. Ein deaktivierter Dienst blockiert den Datenzugriff nicht.
- MySQLs Rechte gelten nach den Prüfungen des Proxys weiter; der Proxy kann sie nur einschränken.
- `COM_CHANGE_USER` wird verfolgt und abgelehnt, wenn Client- und Serverseite sich in TLS unterscheiden; melde dich stattdessen neu als der andere Benutzer an.

## Beispiel zur Überprüfung

Lass einen Datenbankadministrator in einer separaten Testdatenbank folgende Daten und das Konto anlegen. Ersetze `proxy-host` durch die Quelladresse des Proxys, die MySQL tatsächlich sieht:

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

Lege drei GrantForge-Richtlinien für `alice` an: erlaube `select` auf `database=grantforge_demo`, `table=orders`, `column=*`; maskiere `card` mit `mask_show_last_4`; filtere die Tabelle mit `region = 'EU'`. Halte den Dienst und alle drei Richtlinien aktiviert. Warte, bis **Datenberechtigungen → Agenten** die Anwendung der neuesten Richtlinienversion anzeigt.

Verbinde dich mit dem MySQL-Kommandozeilenclient mit dem Proxy. `-p` fragt das Passwort ab; die CA-Datei muss dem Proxy-Zertifikat vertrauen:

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- One row only: 1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000: GrantForge does not grant update
```

Die nativen MySQL-Rechte erlauben in diesem Beispiel bereits `UPDATE`; seine Ablehnung bestätigt deshalb die Durchsetzung der Richtlinie durch den Proxy. Ändere anschließend den Zeilenfilter zu `region = 'US'`, warte auf die Anwendung der neuen Version und frage erneut ab. Nur Zeile 2 mit dem Kartenwert `xxxxxxxxxxxx4444` sollte erscheinen.

## Fehlerbehebung und Audit

| Fehlercode / SQLSTATE | Bedeutung und Prüfungen |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | Datenbank-, Tabellen- oder Spaltenzugriff verweigert. Enthält die Meldung `by GrantForge`, prüfe Richtlinienbenutzer, Ressourcen, Zugriffstyp und Snapshot-Version; bei anderen Ablehnungen auch die nativen MySQL-Rechte |
| `1046 / 3D000` | Keine Datenbank gewählt; verwende `USE`, eine Standarddatenbank der Verbindung oder einen qualifizierten Tabellennamen |
| `1227 / 42000` | Ein Vorgang ist verboten oder kann nicht sicher geprüft werden; prüfe anhand der Meldung Anweisungsbeschränkungen und Metadatenrechte |
| `1064 / 42000` | SQL kann nicht analysiert werden |
| `1615 / HY000` | Bereite die Anweisung nach einer Richtlinienänderung erneut vor |
| `3159 / HY000` | Der Proxy verlangt TLS, aber der Client hat sich ohne TLS verbunden |
| `1043 / 08S01` | Backend-Protokoll- oder TLS-Handshake-Fehler; prüfe TLS-Modus, Zertifikate, Hostnamen und Serverfähigkeiten |
| `2003 / HY000` | Der Proxy kann keine Verbindung zu MySQL herstellen; prüfe Adresse, Netzwerk und Backend-Status |

Suche unter **Datenberechtigungen → Zugriffs-Audit** die Erlaubnis- und Ablehnungsereignisse für `alice` und die zugehörigen Ressourcen. Eine SQL-Anweisung kann mehrere Ereignisse zur Ressourcenautorisierung auslösen. Sie werden vor der Backend-Ausführung aufgezeichnet; `ALLOWED` bedeutet daher nicht, dass MySQL erfolgreich ausgeführt hat. Analysefehler, Anweisungen ohne Ressourcenzugriff und deaktivierte Dienste erzeugen möglicherweise keine Ressourcenereignisse. Wiederholte Ausführungen eines Prepared Statements bei gleicher Richtlinienversion wiederholen die Autorisierungsereignisse nicht. Dies ist ein Audit der Ressourcenautorisierung, kein vollständiges SQL-Ausführungsprotokoll.

## Weitere Anleitungen

- [MySQL](/de/plugins/mysql/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
