---
title: Apache-Doris-Agent
description: Den Apache-Doris-Agenten neben dem Cluster betreiben, damit GrantForge-Richtlinien zu den eigenen Rollen, Spaltenrechten, Workload-Gruppen-Rechten und Zeilenrichtlinien von Doris werden, jeder Identität eines Benutzers gegeben, auf ganze Datenbanken und Kataloge nur dort gewährt, wo Doris nicht mehr geben würde, und Zugriffe aus der Audit-Log-Tabelle von Doris gemeldet werden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der Apache-Doris-Agent ist ein Dienst neben dem Cluster. Er hält die eigene Zugriffskontrolle von Doris im Einklang mit den signierten Richtlinien eines `doris`-Datendienstes: eine Rolle pro Benutzer, die genau das enthält, was die Richtlinien dem Benutzer geben, bis hinunter zu Spalten und Workload-Gruppen, und eine Zeilenrichtlinie pro Zeilenfilter. Doris prüft dann jede Anweisung selbst, und der Agent steht nie zwischen Clients und Frontends. Das Distributionspaket liefert ihn als ausführbares JAR, `agents/doris/grantforge-agent-doris-<version>.jar`, das Java 17 oder neuer benötigt.

Die Testcontainers-Integrationstests des Repositorys führen den Agenten und das ausgelieferte JAR gegen Apache Doris `2.1`, `3.1`, `4.0` und `4.1` aus. Das zertifiziert nicht jede andere Version; der Agent lehnt Cluster älter als `2.1` ab. Das Service-Plug-in beschreibt [Apache Doris](/de/plugins/doris/).

## Funktionsweise

- **Eine Rolle pro Benutzer enthält, was GrantForge gibt.** Für jeden Benutzer, dem die Richtlinien etwas geben, hält der Agent eine nach ihm benannte Rolle, `grantforge_alice` für `alice`, mit einem Kommentar, der den Benutzer nennt, und gewährt sie jeder Identität des Benutzers, etwa `'alice'@'%'` und `'alice'@'10.%'`. `SHOW ROLES` zeigt, was GrantForge `alice` gibt; der Agent ändert keine andere Rolle und kein Recht, das er nicht vergeben hat.
- **Ganze Datenbanken und Kataloge, wo darunter nichts anders entschieden wird.** Lesen, Laden und Sichten-Zeigen, die eine Richtlinie auf einer ganzen Datenbank oder einem ganzen Katalog erlaubt, ohne dass eine Richtlinie sie darunter entziehen könnte, werden auf der Datenbank oder dem Katalog gewährt, sodass später angelegte Tabellen sie sofort haben. Sonst werden sie Tabelle für Tabelle gewährt, und Lesen Spalte für Spalte. Ändern wird Tabelle für Tabelle gewährt, da das Recht von Doris auf eine Datenbank sie auch umbenennen ließe.
- **Nie mehr, als die Richtlinien erlauben.** Das Recht von Doris, Datenbanken in einem Katalog anzulegen, ist das Recht, in allen davon Tabellen anzulegen, und das Recht, eine Datenbank zu löschen, das Recht, alle ihre Tabellen zu löschen. Der Agent gewährt jedes nur dort, wo die Richtlinien auch das weitere Recht geben; sonst hält er es zurück und protokolliert einmal, welcher Benutzer was nicht darf und warum.
- **Workload-Gruppen werden namentlich gewährt.** Eine Richtlinie auf Workload-Gruppen gibt `USAGE_PRIV` auf jede Gruppe, die sie erlaubt, sodass ein Benutzer darin Abfragen ausführen darf; später angelegte Gruppen folgen bei der nächsten Synchronisation.
- **Zeilenfilter werden zu Zeilenrichtlinien.** Ein Zeilenfilter ist eine permissive Zeilenrichtlinie der Rolle des Benutzers auf der Tabelle. Ein neuer Filter steht, bevor die Rechte kommen, die er einschränkt; ein Filter, der sich ändert, wird hinter einer restriktiven Richtlinie ersetzt, die keine Zeilen zeigt, sodass der Benutzer nie mehr sieht, als einer der beiden Filter zeigt. Ein Filter, den Doris auf seiner Tabelle nicht ausführen kann oder ablehnt, etwa weil er eine Spalte nennt, die die Tabelle nicht hat, wird durch einen ersetzt, der dem Benutzer keine Zeilen zeigt, und protokolliert.
- **Die Richtlinien des aktuellen Moments.** Jede Synchronisation plant mit den Richtlinien, wie sie in diesem Moment gelten, sodass Gültigkeitszeiträume pünktlich beginnen und enden, und mit den Benutzern, Katalogen, Datenbanken, Tabellen und Spalten, die Doris hat, sodass neue bei der nächsten Synchronisation erfasst werden.
- **Doris-Rollen sind Gruppen.** Eine Richtlinie für eine Gruppe gilt für Benutzer, die eine Doris-Rolle dieses Namens haben, zusammen mit den Gruppen, in die GrantForge sie einordnet.
- **Manuelle Änderungen werden rückgängig gemacht.** Der Agent vergleicht bei jeder Synchronisation die Rechte seiner Rollen, wie `SHOW ROLES` sie zeigt, und seine Zeilenrichtlinien mit den Plänen und stellt wieder her, was abweicht. Doris zeigt Spaltenrechte nur pro Benutzer, zusammengeführt über alle seine Rechte: Der Agent gewährt die Spalten, die ein Plan nennt, wo ein Benutzer nicht alle lesen kann, und entzieht seiner Rolle die übrigen einmal; eine Spalte, die lesbar bleibt, stammt aus einem Recht außerhalb von GrantForge. Wenn sich nichts geändert hat, sendet der Agent nichts.
- **Rechte außerhalb von GrantForge werden gemeldet.** Doris addiert Rechte und verbindet permissive Zeilenrichtlinien mit OR, daher kann der Agent nicht entziehen, was ein Benutzer direkt oder über eine andere Rolle hat. Er warnt einmal vor jedem solchen Recht, Spaltenrecht oder jeder Zeilenrichtlinie, die gibt, was eine Richtlinie verweigert, und nennt Benutzer, Recht und Herkunft, sowie vor Benutzern mit `ADMIN_PRIV` oder `NODE_PRIV`, die keine Rolle bindet.
- **Zugriffe kommen aus dem Audit-Log.** Der Agent liest `__internal_schema.audit_log`, das jedes Frontend schreibt, und meldet jede Anweisung als ein Ereignis pro Tabelle, Datenbank oder Katalog, den sie berührt hat; welche Tabellen das sind, liest er mit dem Doris-Parser von Druid aus der Anweisung selbst, da das Log keine nennt. Eine Ablehnung ist ein verweigertes Ereignis für das Recht und das Objekt, die der Fehler von Doris nennt. Jedes Ereignis sagt, ob GrantForge entschieden hat, also ob die Richtlinien wie Doris entscheiden, oder ein Recht außerhalb von GrantForge es erlaubt hat. Das Log wird um das Batch-Intervall der Frontends, `audit_plugin_max_batch_interval_sec`, und fünf Sekunden hinter der Gegenwart gelesen.

| Anweisung | Zugriffsereignis |
| --- | --- |
| `SELECT`, die lesende Seite von `INSERT … SELECT` | `select` auf jeder gelesenen Tabelle |
| `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` | `load` auf der geschriebenen Tabelle |
| `ALTER TABLE`, `DROP TABLE`, `SHOW CREATE VIEW` | `alter`, `drop`, `show_view` auf der Tabelle |
| `CREATE TABLE`, `CREATE VIEW` | `create` auf der Datenbank |
| `DROP DATABASE` | `drop_database` auf der Datenbank |
| `CREATE DATABASE` | `create_database` auf dem Katalog |

## Bereitstellung

1. Fügen Sie einen `doris`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agenten-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur das Konto des Agenten lesen kann.
2. Legen Sie den Doris-Benutzer des Agenten an. Er legt Rollen und Zeilenrichtlinien an, gewährt, was die Richtlinien geben, und liest die Rechte jedes Benutzers und das Audit-Log, was `ADMIN_PRIV` erlaubt:

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. Um Zugriffe zu melden, schalten Sie das Audit-Log von Doris ein, wo es aus ist, wie in Doris 2.1: `SET GLOBAL enable_audit_plugin = true`. Ein kürzeres `audit_plugin_max_batch_interval_sec` als der Standard von 60 Sekunden meldet Zugriffe früher.
4. Schreiben Sie die Einstellungsdatei und ersetzen Sie die Beispieladressen, -pfade und -namen:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   Diese Pfade liegen auf der Maschine des Agenten, und das Cache-Verzeichnis muss beschreibbar sein. Passwortdateien werden als UTF-8 gelesen und getrimmt.
5. Starten Sie den Agenten, zum Beispiel als systemd-Dienst:

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   Ersetzen Sie `<version>` durch die Version der Distribution. Einstellungen, die als `java -Dgrantforge.…=… -jar …` übergeben werden, überschreiben die Datei. Der Agent hält bei SIGTERM sauber an und sendet oder puffert ausstehende Ereignisse; Doris behält, was es bekommen hat.
6. **Bestehende Rechte in Richtlinien überführen:** Rechte, die außerhalb von GrantForge vergeben wurden, gelten neben den Rollen des Agenten weiter. Legen Sie die passenden Richtlinien an und entziehen Sie dann die Rechte; die Warnungen des Agenten nennen die, die geben, was eine Richtlinie verweigert.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Pflicht: der GrantForge-Server, die Datei des Agenten-Tokens, ein Name für diesen Agenten und ein beschreibbares Verzeichnis für den signierten Snapshot, gepufferte Ereignisse und die Leseposition des Audit-Logs |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten öffentlichen X.509-Ed25519-Schlüssel von der Seite Agenten. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.doris.hosts`, `grantforge.doris.user` | Pflicht: die MySQL-Protokolladressen der Frontends, nacheinander versucht, wenn eines nicht erreichbar ist, Port `9030` ohne Angabe, und der Doris-Benutzer des Agenten |
| `grantforge.doris.password.file` | Das Passwort des Benutzers; ohne sie meldet sich der Agent ohne Passwort an |
| `grantforge.doris.ssl.mode`, `grantforge.doris.tls.ca` | `disable` (Standard), `trust`, `verify-ca` oder `verify-full`, für Frontends, die TLS anbieten; und das PEM-CA-Zertifikat, das sie prüft, leer für das, dem Java vertraut |
| `grantforge.doris.timeout.ms` | Wie lange Verbindungsaufbau und eine Anweisung dauern dürfen, Standard `30000`, Bereich `1..600000` Millisekunden |
| `grantforge.doris.role.prefix` | Der Anfang der Rollennamen des Agenten, Standard `grantforge_`; Buchstaben, Ziffern und Unterstriche, mit einem Unterstrich am Ende, höchstens 32 Zeichen. Dem Agenten gehören alle Rollen und Zeilenrichtlinien, deren Name damit beginnt |
| `grantforge.doris.unmanaged.users` | Benutzer, die der Agent in Ruhe lässt, durch Kommas getrennt, etwa Dienstkonten; seinen eigenen Benutzer, `root`, `admin` und Benutzer mit `ADMIN_PRIV` oder `NODE_PRIV` lässt er immer in Ruhe |
| `grantforge.doris.catalogs` | Die Kataloge, in denen der Agent nach Datenbank, Tabelle und Spalte gewährt, durch Kommas getrennt; leer für alle. Die übrigen erhalten nur, was sich auf ihnen als Ganzes gewähren lässt, sodass ein externer Katalog mit vielen Tabellen nicht bei jeder Synchronisation aufgelistet werden muss |
| `grantforge.sync.interval.ms` | Wie oft der Agent Doris abgleicht, Standard `10000`, Bereich `1000..3600000` Millisekunden |
| `grantforge.audit.log` | Standard `true`; `false` liest kein Audit-Log und meldet keine Zugriffe |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Wie weit hinter der Gegenwart das Log gelesen wird, standardmäßig das Batch-Intervall des Clusters und fünf Sekunden; wie oft, Standard `5000` Millisekunden; und die meisten Einträge, die ein Lesevorgang nimmt, Standard `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeouts zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batch-Größe (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und die Puffergrenze auf der Platte (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange identische erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` sendet jeden |

## Benutzer, Gruppen und Rollen

Richtlinien nennen Doris-Benutzer bei ihrem Benutzernamen; ein Benutzername mit mehreren Hosts ist ein Benutzer, und die Rolle des Agenten geht an jede seiner Identitäten. Gruppen sind die Doris-Rollen eines Benutzers, zusammen mit den Gruppen, in die GrantForge ihn einordnet; Rollen sind die Rollen, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne Snapshot ändert der Agent nichts. Ein Dienst, der nicht in Gebrauch ist, oder ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `doris` ist, gewährt nichts: Der Agent löscht seine Rollen und Zeilenrichtlinien, und die eigenen Rechte von Doris gelten.
- Solange der Agent nicht läuft, behält Doris, was es zuletzt bekommen hat; Richtlinienänderungen warten auf ihn. Eine Änderung erreicht Doris innerhalb des Aktualisierungs- und des Synchronisationsintervalls.
- Tabellen, die in einer Datenbank angelegt werden, in der der Agent Tabelle für Tabelle gewährt, und Datenbanken in einem Katalog, in dem er Datenbank für Datenbank gewährt, erhalten ihre Rechte bei der nächsten Synchronisation.
- Die Zeilenrichtlinien von Doris können `root` und `admin` nicht binden. Ein Benutzer, für den Doris keine Zeilenrichtlinie zeigt, sieht jede Zeile einer Tabelle; permissive Zeilenrichtlinien, die außerhalb von GrantForge für denselben Benutzer angelegt wurden, erweitern, was sein Filter zeigt.
- Doris prüft `USAGE_PRIV` auf eine Workload-Gruppe, wenn eine Abfrage auf den Backends läuft; eine Abfrage, die das Frontend selbst beantwortet, etwa eine Konstante oder ein zwischengespeichertes Ergebnis, läuft ohne die Prüfung. Jeder Benutzer darf die Gruppe `normal` nutzen, da Doris sie allen gibt.
- Die Benutzer, für die der Agent Rollen hält, müssen in Doris existieren; Benutzer, die sich über LDAP ohne eigenen Doris-Benutzer anmelden, erhalten keine Rolle.
- Doris maskiert Werte nur über Apache Ranger, daher können Richtlinien keine Werte maskieren.

## Weitere Anleitungen

- [Apache Doris](/de/plugins/doris/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
