---
title: ClickHouse-Agent
description: Den ClickHouse-Agenten neben den Servern betreiben, damit GrantForge-Richtlinien zu den eigenen Rollen, Berechtigungen und Zeilenrichtlinien von ClickHouse werden, bis hinunter zu Spalten und Zeilen, und Zugriffe aus dem Query-Log gemeldet werden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der ClickHouse-Agent ist ein Dienst neben den Servern. Er hält die eigene Zugriffskontrolle von ClickHouse im Einklang mit den signierten Richtlinien eines `clickhouse`-Datendienstes: eine Rolle je Benutzer, die genau das hält, was die Richtlinien dem Benutzer geben, und eine Zeilenrichtlinie je Zeilenfilter. ClickHouse prüft dann jede Abfrage selbst, über jedes seiner Protokolle, und der Agent steht nie zwischen Clients und Server. Die Distribution liefert ihn als ausführbares JAR, `agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`, das Java 17 oder neuer erfordert.

Die Testcontainers-Integrationstests des Repositorys führen den Agenten, und das JAR wie ausgeliefert, gegen ClickHouse `24.8`, `25.3`, `25.8`, `26.3` und `26.8` aus. Das zertifiziert nicht jede andere Version; Server älter als `24.8` lehnt der Agent ab. Das Dienst-Plug-in beschreibt [ClickHouse](/de/plugins/clickhouse/).

## Funktionsweise

- **Eine Rolle je Benutzer hält, was GrantForge gibt.** Für jeden Benutzer, dem die Richtlinien etwas geben, führt der Agent eine nach ihm benannte Rolle, `grantforge_alice` für `alice`, und vergibt sie dem Benutzer als Standardrolle. `SHOW GRANTS FOR grantforge_alice` zeigt alles, was GrantForge `alice` gibt; der Agent ändert keine andere Rolle und keine Berechtigung, die er nicht selbst vergeben hat.
- **Ganze Datenbanken, wo darunter nichts anders entschieden wird.** Eine Zugriffsart, die eine Richtlinie auf einer ganzen Datenbank erlaubt, ohne Richtlinie, die sie einer Tabelle oder Spalte darin nehmen könnte, wird auf der Datenbank vergeben, sodass später angelegte Tabellen sie sofort haben. Sonst wird sie Tabelle für Tabelle vergeben, Lesen und Schreiben Spalte für Spalte.
- **Zeilenfilter werden zu Zeilenrichtlinien.** Ein Zeilenfilter ist eine permissive Zeilenrichtlinie der Rolle des Benutzers auf der Tabelle. Ein neuer oder geänderter Filter steht, bevor die Rechte kommen, die er einschränkt. Ein Filter, den ClickHouse ablehnt, oder einer, dessen Text die Zeilenrichtlinie auf andere anwenden würde, wird durch einen ersetzt, der dem Benutzer keine Zeilen zeigt, und protokolliert.
- **Die Richtlinien des gegenwärtigen Augenblicks.** Jeder Abgleich plant nach den Richtlinien, wie sie in diesem Augenblick gelten, sodass Gültigkeitszeiträume pünktlich beginnen und enden, und nach den Benutzern, Datenbanken, Tabellen und Spalten, die ClickHouse hat, sodass neue beim nächsten Abgleich erfasst sind.
- **ClickHouse-Rollen sind Gruppen.** Eine Richtlinie für eine Gruppe gilt für Benutzer, die eine ClickHouse-Rolle dieses Namens halten, direkt oder über andere Rollen, zusammen mit den Gruppen, in die GrantForge sie setzt.
- **Änderungen von Hand werden rückgängig gemacht.** Der Agent merkt sich, was er jeder Rolle und Zeilenrichtlinie gegeben hat; weicht eine davon ab, wird sie beim nächsten Abgleich wiederhergestellt. Hat sich nichts geändert, sendet der Agent nichts.
- **Berechtigungen außerhalb von GrantForge werden gemeldet.** ClickHouse addiert Rechte, daher kann der Agent nicht nehmen, was ein Benutzer direkt oder über eine andere Rolle hält. Er warnt einmal vor jeder solchen Berechtigung, die gibt, was eine Richtlinie verweigert, mit Benutzer, Berechtigung, der Rolle, über die sie kommt, und der Richtlinie.
- **Zugriffe kommen aus dem Query-Log.** Der Agent liest `system.query_log` und meldet jede Anweisung als ein Ereignis je Tabelle, Datenbank oder Server, die sie berührt hat, eine Ablehnung als ein abgelehntes Ereignis für das, was der Fehler von ClickHouse nennt. Jedes Ereignis sagt, ob GrantForge es entschieden hat, ob also die Richtlinien so entscheiden wie ClickHouse, oder ob eine Berechtigung außerhalb von GrantForge es erlaubt hat.

| Anweisung | Zugriffsereignis |
| --- | --- |
| `SELECT`, die Leseseite von `INSERT … SELECT` | `select` auf jeder gelesenen Tabelle |
| `INSERT` | `insert` auf der geschriebenen Tabelle |
| `ALTER`, `ALTER UPDATE`, `ALTER DELETE`, `DELETE`, `UPDATE` | `alter` auf der Tabelle |
| `DROP TABLE`, `TRUNCATE`, `OPTIMIZE` | `drop`, `truncate`, `optimize` auf der Tabelle |
| `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` | `create` auf der Datenbank |
| `DROP DATABASE` | `drop_database` auf der Datenbank |
| `CREATE DATABASE`, `KILL QUERY`, `SYSTEM …` | `create_database`, `kill_query`, `system` auf dem Server |

## Bereitstellung

1. Fügen Sie einen `clickhouse`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agenten-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur das Konto des Agenten lesen kann.
2. Legen Sie den ClickHouse-Benutzer des Agenten an. Er vergibt, was die Richtlinien geben, braucht diese Rechte also mit Grant-Option, und die Zugriffsverwaltung, um Rollen und Zeilenrichtlinien zu führen:

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   Die Benutzer, für die der Agent Rollen führt, müssen dort gespeichert sein, wo sie sich ändern lassen, im Benutzerverzeichnis `local_directory` oder `replicated`, wie `CREATE USER` sie speichert. Benutzer aus `users.xml` und LDAP bleiben unberührt und werden einmal protokolliert. Ein Cluster sollte seine Benutzer, Rollen und Zeilenrichtlinien in einem `replicated`-Benutzerverzeichnis halten: Dann bedient ein Agent den ganzen Cluster.
3. Schreiben Sie die Einstellungsdatei und ersetzen Sie die Beispieladressen, -pfade und -namen:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   Diese Pfade liegen auf dem Rechner des Agenten, und das Cache-Verzeichnis muss beschreibbar sein. Passwortdateien werden als UTF-8 gelesen und getrimmt.
4. Starten Sie den Agenten, etwa als systemd-Dienst:

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   Ersetzen Sie `<version>` durch die Distributionsversion. Einstellungen in der Form `java -Dgrantforge.…=… -jar …` überschreiben die Datei. Bei SIGTERM beendet sich der Agent sauber und sendet oder spoolt ausstehende Ereignisse; ClickHouse behält, was es bekommen hat.
5. **Bestehende Berechtigungen in Richtlinien überführen:** Berechtigungen, die außerhalb von GrantForge vergeben wurden, gelten neben den Rollen des Agenten weiter. Legen Sie die passenden Richtlinien an und entziehen Sie dann die Berechtigungen; die Warnungen des Agenten nennen jene, die geben, was eine Richtlinie verweigert.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Pflicht: GrantForge-Server, Datei des Agenten-Tokens, Name dieses Agenten und ein beschreibbares Verzeichnis für den signierten Snapshot, gespoolte Ereignisse und die Leseposition des Query-Logs |
| `grantforge.signing.key.file` | Optionale Datei zur festen Bindung des Snapshot-Signaturschlüssels; kopieren Sie den Base64-kodierten X.509-Ed25519-Public-Key von der Agenten-Seite. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server abgerufen |
| `grantforge.clickhouse.urls`, `grantforge.clickhouse.user` | Pflicht: die HTTP-Schnittstellen der Server, der Reihe nach versucht, wenn einer nicht erreichbar ist, und der ClickHouse-Benutzer des Agenten |
| `grantforge.clickhouse.password.file` | Das Passwort des Benutzers; fehlt sie, meldet sich der Agent ohne Passwort an |
| `grantforge.clickhouse.tls.ca` | PEM-CA-Zertifikat, das `https://`-Server prüft; leer vertraut dem, dem Java vertraut |
| `grantforge.clickhouse.tls.cert`, `grantforge.clickhouse.tls.key` | PEM-Client-Zertifikat und privater Schlüssel, gemeinsam, um sich mit einem Zertifikat (`IDENTIFIED WITH ssl_certificate`) statt mit einem Passwort anzumelden |
| `grantforge.clickhouse.timeout.ms` | Wie lange eine Anfrage an ClickHouse dauern darf, Standard `30000`, Bereich `1..600000` Millisekunden |
| `grantforge.clickhouse.role.prefix` | Der Anfang der Rollennamen des Agenten, Standard `grantforge_`; Buchstaben, Ziffern und Unterstriche, endet mit einem Unterstrich. Dem Agenten gehört jede Rolle und Zeilenrichtlinie, deren Name damit beginnt |
| `grantforge.clickhouse.unmanaged.users` | Benutzer, die der Agent in Ruhe lässt, durch Kommas getrennt, etwa Administratoren; seinen eigenen Benutzer lässt er immer in Ruhe |
| `grantforge.sync.interval.ms` | Wie oft der Agent ClickHouse abgleicht, Standard `10000`, Bereich `1000..3600000` Millisekunden |
| `grantforge.audit.query.log` | Standard `true`; `false` liest kein Query-Log und meldet keine Zugriffe |
| `grantforge.audit.query.log.cluster` | Ein Cluster, dessen Replikate ihr Query-Log über `clusterAllReplicas` liefern; leer liest den Server, den der Agent fragt |
| `grantforge.audit.query.log.delay.ms`, `grantforge.audit.query.log.interval.ms`, `grantforge.audit.query.log.batch` | Wie weit hinter der Gegenwart das Log gelesen wird, Standard `10000` Millisekunden, da ClickHouse es alle 7,5 Sekunden schreibt; wie oft, Standard `5000` Millisekunden; und die meisten Einträge, die ein Lesevorgang nimmt, Standard `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeouts zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Spool-Grenze auf der Platte (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange identische erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` sendet jeden einzeln |

## Benutzer, Gruppen und Rollen

Richtlinien nennen ClickHouse-Benutzer. Gruppen sind die ClickHouse-Rollen, die ein Benutzer direkt oder über andere Rollen hält, zusammen mit den Gruppen, in die GrantForge den Benutzer setzt; Rollen sind die Rollen, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne Snapshot ändert der Agent nichts. Ein Dienst, der nicht in Gebrauch ist, oder ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `clickhouse` ist, gibt nichts: Der Agent löscht seine Rollen und Zeilenrichtlinien, und die eigenen Berechtigungen von ClickHouse gelten.
- Solange der Agent nicht läuft, behält ClickHouse, was es zuletzt bekommen hat; Richtlinienänderungen warten auf ihn. Eine Änderung erreicht ClickHouse innerhalb des Aktualisierungs- und des Abgleichsintervalls.
- Tabellen, die in einer Datenbank angelegt werden, für die der Agent Tabelle für Tabelle vergibt, erhalten ihre Rechte beim nächsten Abgleich.
- Ein Benutzer, für den ClickHouse keine Zeilenrichtlinie zeigt, sieht jede Zeile einer Tabelle, wie es die ClickHouse-Einstellung `users_without_row_policies_can_read_rows` standardmäßig erlaubt; ist sie aus, sehen Benutzer ohne Filter keine Zeilen gefilterter Tabellen. Permissive Zeilenrichtlinien, die außerhalb von GrantForge für denselben Benutzer angelegt wurden, erweitern, was sein Filter zeigt.
- `system` gibt jede `SYSTEM`-Anweisung, `SYSTEM SHUTDOWN` eingeschlossen.
- Das Query-Log meldet, was ClickHouse protokolliert: Anweisungen von Benutzern mit ausgeschaltetem `log_queries` werden nicht gemeldet. Bei einer Ablehnung nennt ClickHouse unter Umständen die Tabelle, wenn dem Benutzer eine Spalte fehlt; das Ereignis nennt dann die Tabelle.
- ClickHouse hat außerhalb von ClickHouse Cloud keine Spaltenmaskierung, daher können Richtlinien keine Werte maskieren.

## Weitere Anleitungen

- [ClickHouse](/de/plugins/clickhouse/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
