---
title: PostgreSQL-Agent
description: Den PostgreSQL-Agenten neben den Servern betreiben, sodass GrantForge-Richtlinien zu eigenen Rollen, Rechten und Zeilensicherheitsrichtlinien von PostgreSQL werden, bis auf Spalten und Zeilen, Datenbank für Datenbank in je einer Transaktion, und Zugriffe aus dem Serverlog gemeldet werden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der PostgreSQL-Agent ist ein Dienst neben den Servern. Er hält die eigene Zugriffskontrolle von PostgreSQL im Einklang mit den signierten Richtlinien eines `postgresql`-Datendienstes: eine Rolle je Benutzer, die in jeder Datenbank, die er führt, genau das hält, was die Richtlinien dem Benutzer geben, und eine Zeilensicherheitsrichtlinie je Zeilenfilter. PostgreSQL prüft dann jede Anweisung selbst, und der Agent steht nie zwischen Clients und Server. Die Distribution liefert ihn als ausführbares JAR, `agents/postgresql/grantforge-agent-postgresql-<version>.jar`, das Java 17 oder neuer erfordert.

Die Testcontainers-Integrationstests des Repositorys führen den Agenten, und das JAR wie ausgeliefert, gegen PostgreSQL `15`, `16`, `17` und `18` mit pgaudit aus. Das zertifiziert nicht jede andere Version; Server älter als `15` lehnt der Agent ab. Das Dienst-Plug-in beschreibt [PostgreSQL](/de/plugins/postgresql/).

## Funktionsweise

- **Eine Rolle je Benutzer hält, was GrantForge gibt.** Für jeden Benutzer, dem die Richtlinien etwas geben, führt der Agent eine nach ihm benannte Rolle, `grantforge_alice` für `alice`, und macht den Benutzer zu einem Mitglied, das die Rechte der Rolle ohne `SET ROLE` hat. Der Agent schreibt einen Kommentar an jede seiner Rollen und hält keine Rolle ohne ihn für eine eigene, daher ändert er keine andere Rolle und kein Recht, das er nicht selbst vergeben hat.
- **Jede Datenbank ändert sich in einer Transaktion.** Der Agent meldet sich an jeder Datenbank an, die er führt, und nimmt seine Änderungen dort in einer Transaktion vor, sodass Benutzer eine Richtlinienversion auf einer Datenbank ganz oder gar nicht angewendet sehen.
- **Ganze Tabellen, wo darunter nichts anders entschieden wird.** Eine Zugriffsart, die eine Richtlinie auf einer ganzen Tabelle erlaubt, ohne Richtlinie, die sie einer Spalte nehmen könnte, wird auf der Tabelle vergeben, sodass später hinzugefügte Spalten sie sofort haben. Sonst werden Lesen, Einfügen und Ändern Spalte für Spalte vergeben. `CONNECT` für die Datenbank, `USAGE` für das Schema und für die Sequenzen der Serial-Spalten, in die der Benutzer einfügen darf, kommen mit.
- **Zeilenfilter schränken nur ein.** Ein Zeilenfilter ist eine restriktive Zeilensicherheitsrichtlinie der Rolle des Benutzers auf der Tabelle, für `SELECT`. Wo die Zeilensicherheit aus ist, schaltet der Agent sie ein, zusammen mit einer permissiven Richtlinie, die allen jede Zeile zeigt und nach seinem Präfix benannt ist (`grantforge`), sodass sich für andere nichts ändert; er schaltet die Zeilensicherheit wieder aus, wenn die Tabelle keinen Filter und keine eigene Richtlinie mehr hat. Zeilensicherheit, die ein Eigentümer eingeschaltet hat, bleibt die des Eigentümers, und die Richtlinien des Agenten schränken nur ein, was sie zeigt. Ein Filter, den PostgreSQL ablehnt, einer, der ändern würde, wen die Richtlinie bindet, oder ein Text aus mehr als einer Anweisung wird durch eine Richtlinie ersetzt, die dem Benutzer keine Zeilen zeigt, und protokolliert.
- **Die Richtlinien des gegenwärtigen Augenblicks.** Jeder Abgleich plant nach den Richtlinien, wie sie in diesem Augenblick gelten, sodass Gültigkeitszeiträume pünktlich beginnen und enden, und nach den Benutzern, Schemas, Tabellen und Spalten, die PostgreSQL hat, sodass neue beim nächsten Abgleich erfasst sind.
- **Rollen sind Gruppen.** Eine Richtlinie für eine Gruppe gilt für Benutzer, die Mitglied einer PostgreSQL-Rolle dieses Namens sind, direkt oder über andere Rollen, zusammen mit den Gruppen, in die GrantForge sie setzt.
- **Änderungen von Hand werden rückgängig gemacht.** Der Agent vergleicht bei jedem Abgleich die Rechte seiner Rollen mit dem, was PostgreSQL zeigt, und seine Zeilensicherheitsrichtlinien mit dem, was er zuletzt angelegt hat; was abweicht, wird beim nächsten Abgleich wiederhergestellt. Hat sich nichts geändert, sendet der Agent nichts.
- **Rechte außerhalb von GrantForge werden gemeldet.** PostgreSQL addiert Rechte, daher kann der Agent nicht nehmen, was ein Benutzer selbst, über eine andere Rolle oder über PUBLIC hält, noch was er mit den Tabellen tun darf, die ihm gehören. Er warnt einmal vor jedem solchen Recht, das gibt, was eine Richtlinie verweigert, mit Benutzer, Recht, wie der Benutzer es hält, und der Richtlinie.
- **Zugriffe kommen aus dem Serverlog.** Der Agent liest das JSON-Serverlog von PostgreSQL: das Sitzungslog von pgaudit für das, was PostgreSQL erlaubt hat, ein Ereignis je Tabelle, die eine Anweisung gelesen oder geschrieben hat, und die Ablehnungen von PostgreSQL wegen eines fehlenden Rechts, ein abgelehntes Ereignis für das, was der Fehler nennt. Jedes Ereignis sagt, ob GrantForge es entschieden hat, ob also die Richtlinien so entscheiden wie PostgreSQL, oder ob ein Recht außerhalb von GrantForge es erlaubt hat.

| Anweisung | Zugriffsereignis |
| --- | --- |
| `SELECT`, `COPY … TO`, die Leseseite anderer Anweisungen | `select` auf jeder gelesenen Tabelle |
| `INSERT`, `COPY … FROM` | `insert` auf der geschriebenen Tabelle |
| `UPDATE`, `MERGE`, `SELECT … FOR UPDATE` | `update` auf der Tabelle |
| `DELETE`, `TRUNCATE` | `delete`, `truncate` auf der Tabelle |
| `CREATE TRIGGER` | `trigger` auf der Tabelle |
| `CREATE TABLE`, `CREATE VIEW` und andere Objekte | `create` auf dem Schema |
| `CREATE SCHEMA`, `CREATE TEMPORARY TABLE` | `create_schema`, `temporary` auf der Datenbank |
| Eine abgelehnte Verbindung, ein mangels `USAGE` abgelehntes Schema | `connect` auf der Datenbank, `usage` auf dem Schema |

## Bereitstellung

1. Fügen Sie einen `postgresql`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agenten-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur das Konto des Agenten lesen kann.
2. Legen Sie den PostgreSQL-Benutzer des Agenten als Superuser an. Er vergibt Rechte auf Tabellen, die ihm nicht gehören, schaltet die Zeilensicherheit ein und aus, führt Rollen und liest das Serverlog:

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   Die Tests führen den Agenten als Superuser aus. Superuser binden weder Rechte noch Zeilensicherheit, daher führt der Agent für sie keine Rolle.
3. Um Zugriffe zu melden, lassen Sie PostgreSQL sein Serverlog als JSON schreiben und pgaudit laden, aus den Paketen von PGDG wie `postgresql-17-pgaudit` oder denen der Plattform, in `postgresql.conf`, und starten Sie den Server neu:

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   Ohne pgaudit meldet der Agent, was PostgreSQL abgelehnt hat, nicht was es erlaubt hat; ohne das JSON-Log meldet er nichts und sagt es einmal.
4. Schreiben Sie die Einstellungsdatei und ersetzen Sie die Beispieladressen, -pfade und -namen:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   Diese Pfade liegen auf dem Rechner des Agenten, und das Cache-Verzeichnis muss beschreibbar sein. Passwortdateien werden als UTF-8 gelesen und getrimmt.
5. Starten Sie den Agenten, etwa als systemd-Dienst:

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   Ersetzen Sie `<version>` durch die Version der Distribution. Einstellungen als `java -Dgrantforge.…=… -jar …` überschreiben die Datei. Der Agent hält bei SIGTERM sauber an und sendet oder spoolt ausstehende Ereignisse; PostgreSQL behält, was es bekommen hat.
6. **Bestehende Rechte in Richtlinien überführen:** Rechte, die außerhalb von GrantForge vergeben wurden, gelten neben den Rollen des Agenten weiter. Legen Sie die passenden Richtlinien an und entziehen Sie dann die Rechte; die Warnungen des Agenten nennen die, die geben, was eine Richtlinie verweigert.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Pflicht: GrantForge-Server, Datei des Agenten-Tokens, Name dieses Agenten und ein beschreibbares Verzeichnis für den signierten Snapshot, gespoolte Ereignisse und die Leseposition des Serverlogs |
| `grantforge.signing.key.file` | Optionale Datei zur festen Bindung des Snapshot-Signaturschlüssels; kopieren Sie den Base64-kodierten X.509-Ed25519-Public-Key von der Agenten-Seite. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server abgerufen |
| `grantforge.postgresql.hosts`, `grantforge.postgresql.user` | Pflicht: die Server als `host:port`, der Reihe nach versucht, bis einer erreichbar und der Primärserver ist, und der PostgreSQL-Benutzer des Agenten |
| `grantforge.postgresql.database` | Die Datenbank, an der sich der Agent zuerst anmeldet, um Rollen, Datenbanken und das Serverlog zu lesen, Standard `postgres` |
| `grantforge.postgresql.password.file` | Das Passwort des Benutzers; fehlt sie, meldet sich der Agent ohne Passwort an |
| `grantforge.postgresql.ssl.mode` | TLS, wie es `sslmode` von libpq nennt, Standard `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca und verify-full: PEM-CA-Zertifikat, das die Server prüft; leer vertraut dem, dem Java vertraut |
| `grantforge.postgresql.tls.cert`, `grantforge.postgresql.tls.key` | PEM-Client-Zertifikat und sein privater PKCS-#8-Schlüssel, gemeinsam, um sich mit einem Zertifikat (`cert`-Authentifizierung) statt mit einem Passwort anzumelden; sie brauchen `require` oder einen Prüfmodus, und ein Prüfmodus braucht dazu `grantforge.postgresql.tls.ca` |
| `grantforge.postgresql.timeout.ms` | Wie lange Verbinden und eine Anweisung dauern dürfen, Standard `30000`, Bereich `1..600000` Millisekunden |
| `grantforge.postgresql.databases` | Die Datenbanken, die der Agent führt, durch Kommas getrennt; leer führt jede Datenbank, die Verbindungen erlaubt |
| `grantforge.postgresql.role.prefix` | Der Anfang der Rollennamen des Agenten, Standard `grantforge_`; Buchstaben, Ziffern und Unterstriche, endet mit einem Unterstrich, höchstens 32 Zeichen und nicht mit `pg_` beginnend. Dem Agenten gehören die von ihm markierten Rollen, deren Name damit beginnt, und die Zeilensicherheitsrichtlinien, die nach ihnen oder nach dem Präfix ohne seinen letzten Unterstrich benannt sind |
| `grantforge.postgresql.unmanaged.users` | Benutzer, die der Agent in Ruhe lässt, durch Kommas getrennt, etwa Administratoren; seinen eigenen Benutzer und Superuser lässt er immer in Ruhe |
| `grantforge.sync.interval.ms` | Wie oft der Agent PostgreSQL abgleicht, Standard `10000`, Bereich `1000..3600000` Millisekunden |
| `grantforge.audit.server.log` | Standard `true`; `false` liest kein Serverlog und meldet keine Zugriffe |
| `grantforge.audit.server.log.interval.ms`, `grantforge.audit.server.log.max.bytes` | Wie oft das Log gelesen wird, Standard `5000` Millisekunden, und die meisten Bytes, die ein Lesevorgang nimmt, Standard 16 MiB, sodass ein Rückstand über mehrere Runden gelesen wird |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeouts zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Spool-Grenze auf der Platte (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange identische erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` sendet jeden einzeln |

## Benutzer, Gruppen und Rollen

Richtlinien nennen PostgreSQL-Benutzer, die Rollen, die sich anmelden dürfen. Gruppen sind die Rollen, deren Mitglied ein Benutzer direkt oder über andere Rollen ist, zusammen mit den Gruppen, in die GrantForge den Benutzer setzt; Rollen sind die Rollen, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne Snapshot ändert der Agent nichts. Ein Dienst, der nicht in Gebrauch ist, oder ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `postgresql` ist, gibt nichts: Der Agent löscht seine Rollen und Zeilensicherheitsrichtlinien, und die eigenen Rechte von PostgreSQL gelten.
- Solange der Agent nicht läuft, behält PostgreSQL, was es zuletzt bekommen hat; Richtlinienänderungen warten auf ihn. Eine Änderung erreicht PostgreSQL innerhalb des Aktualisierungs- und des Abgleichsintervalls.
- Tabellen, die nach einem Abgleich angelegt werden, erhalten ihre Rechte beim nächsten Abgleich.
- Superuser und die Eigentümer einer Tabelle dürfen mit ihr alles; Superuser, Rollen mit `BYPASSRLS` und die Eigentümer einer Tabelle sind nicht an ihre Zeilensicherheit gebunden, es sei denn, der Eigentümer erzwingt sie.
- Eine Sicht liest ihre Tabellen mit den Rechten ihres Eigentümers, sofern sie nicht `security_invoker` ist: Das Serverlog meldet die Tabellen, die eine Sicht gelesen hat, als von einem Recht außerhalb von GrantForge erlaubt.
- Das direkte Lesen einer Partition wird nicht von der Zeilensicherheit ihrer partitionierten Tabelle gefiltert.
- pgaudit protokolliert eine Anweisung, bevor PostgreSQL sie ausführt, daher wird eine Anweisung, die später scheitert, etwa ein Einfügen, dessen Sequenz abgelehnt wird, als erlaubt gemeldet und ihre Ablehnung als abgelehnt. Die Anweisungen einer Sitzung, die pgaudit nicht protokolliert, werden nicht gemeldet.
- Die Ablehnungen von PostgreSQL nennen eine Tabelle ohne ihr Schema: Das Ereignis nennt die Tabelle, die die Anweisung qualifiziert, die eine Tabelle dieses Namens oder die in `public`. Ein ohne Schema angelegtes Objekt wird in `public` gemeldet.
- Der Agent liest das Serverlog des Servers, an dem er sich anmeldet, des Primärservers; die Logs von Replikaten werden nicht gelesen.
- PostgreSQL hat keine Spaltenmaskierung, daher können Richtlinien keine Werte maskieren.

## Weitere Anleitungen

- [PostgreSQL](/de/plugins/postgresql/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
