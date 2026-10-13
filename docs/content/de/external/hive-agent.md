---
title: Apache Hive-Agent
description: Den Agenten in HiveServer2 installieren, um Anweisungen zu prüfen, SHOW zu filtern, Spalten zu maskieren und Zeilen zu filtern – mit GrantForge-Richtlinien.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der Hive-Agent ist ein Autorisierer der Autorisierungs-API von Hive, wie das Hive-Plug-in von Apache Ranger: HiveServer2 fragt ihn vor jeder Anweisung, und er antwortet anhand der signierten Richtlinien des `hive`-Datendienstes. Die Distribution liefert ihn als eine Java-8-kompatible JAR, `agents/hive/grantforge-agent-hive-<version>.jar`, mit verschatteten Abhängigkeiten.

Die Testcontainers-Integrationstests des Repositorys führen die JAR in HiveServer2 `3.1.3`, `4.0.1` und `4.1.0` aus. Andere Versionen sind damit nicht zertifiziert. Das Dienst-Plug-in beschreibt [Apache Hive](/de/plugins/hive/).

## Funktionsweise

- **Anweisungen werden geprüft.** Für jedes Objekt, das eine Anweisung liest oder schreibt, fragt der Agent die Richtlinien nach der Zugriffsart, die die Operation braucht, wie Ranger; die erste Ablehnung lässt die Anweisung mit `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]` scheitern. Abfragen werden Spalte für Spalte geprüft.
- **Auflistungen werden gefiltert.** `SHOW DATABASES` und `SHOW TABLES` listen nur, was der Benutzer verwenden darf: irgendeinen Zugriff auf die Datenbank oder Tabelle oder auf etwas darin. `USE` und `DESCRIBE DATABASE` verlangen dasselbe.
- **Abfragen werden maskiert und gefiltert.** Hat der Benutzer maskierte Spalten oder einen Zeilenfilter auf einer Tabelle, schreibt HiveServer2 die Abfrage mit den Masken und der Bedingung um, sodass Joins, Bedingungen und Funktionen nur sehen, was der Benutzer sehen darf.
- **Masken lassen sich nicht umgehen.** Schreiben, Ändern oder Exportieren einer Tabelle, auf der der Benutzer eine Maske oder einen Zeilenfilter hat, wird abgelehnt, wie Ranger es standardmäßig tut.
- **Berechtigungen und Rollen gehören GrantForge.** Hives `GRANT`, `REVOKE`, `SHOW GRANT`, `CREATE ROLE`, `SET ROLE` und die übrigen Rollenanweisungen werden abgelehnt; `SHOW CURRENT ROLES` listet die Rollen, die GrantForge dem Benutzer gibt.
- **Unsichere Anweisungen werden abgelehnt.** `dfs`-Befehle, die das Dateisystem mit den Rechten von HiveServer2 erreichen würden, werden abgelehnt. In HiveServer2-Sitzungen sind `TRANSFORM`-Skripte untersagt, und Benutzer dürfen nur die Einstellungen per `SET` ändern, die Hives SQL-Standard-Autorisierung erlaubt.
- **Jede Entscheidung wird gemeldet** an GrantForge, mit Benutzer, Client-Adresse, Ressource, Zugriffsart, Ergebnis, Operation und Anweisung.

| Operation | Zugriffsart |
| --- | --- |
| Abfragen, `SHOW CREATE TABLE`, `SHOW PARTITIONS`, `SHOW TBLPROPERTIES`, `DESCRIBE`, `SHOW COLUMNS`, `ANALYZE` | `select` |
| `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `LOAD` (Ziel) | `update` |
| `CREATE DATABASE`, `CREATE TABLE`, `CREATE VIEW`, `CREATE FUNCTION` | `create` auf das Erstellte, `select` auf das Gelesene |
| `ALTER …`, `MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`, `UNLOCK` | `lock` |
| `SHOW DATABASES`, `SHOW TABLES`, `USE`, `DESCRIBE DATABASE` | Irgendein Zugriff auf das Objekt oder etwas darin |
| `REPL DUMP`, `REPL LOAD`, `REPL STATUS` | `repladmin` |
| `KILL QUERY`, Ressourcenpläne, LLAP-Verwaltung | `serviceadmin` auf `hiveservice` |
| Temporäre Funktionen, `ADD JAR`, `COMPILE` | `tempudfadmin` auf `global` |
| Orte, die eine Anweisung liest oder schreibt | `read` oder `write` auf `url` |

## Bereitstellung

1. Fügen Sie einen `hive`-Datendienst hinzu und konfigurieren Sie seine Richtlinien, darunter eine, die den Benutzern, unter denen HiveServer2 seine eigene Arbeit ausführt, etwa `hive`, alles Nötige erlaubt. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agent-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur das Konto von HiveServer2 lesen kann.
2. Kopieren Sie die JAR in `lib` **jedes HiveServer2**, etwa `/opt/hive/lib`.
3. Ergänzen Sie die Einstellungen in `hiveserver2-site.xml` oder `hive-site.xml` jedes HiveServer2 und ersetzen Sie Beispieladresse, Pfade und Instanzname:

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   Das Cache-Verzeichnis muss für das Konto von HiveServer2 beschreibbar sein. Wie bei Ranger lässt `hive.server2.enable.doAs=false` HiveServer2 Daten unter seinem eigenen Konto lesen und schreiben, sodass die Richtlinien entscheiden, wer was sieht.
4. Starten Sie HiveServer2 neu. Der Agent startet mit der ersten Sitzung, stoppt mit dem Server und sendet oder puffert dabei ausstehende Ereignisse.
5. **Schließen Sie die Wege an HiveServer2 vorbei:** Der Agent prüft nur Anweisungen, die in HiveServer2 laufen. Clients, die den Metastore oder die Warehouse-Dateien direkt erreichen, etwa die Hive-CLI, Spark oder HDFS-Clients, werden nicht geprüft; schützen Sie sie mit den Berechtigungen des Metastores und von HDFS oder mit dem [HDFS-Agenten](/de/external/hdfs-agent/).

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Erforderlich: der GrantForge-Server, die Datei des Agent-Tokens, ein Name für diesen HiveServer2 und ein beschreibbares Verzeichnis für den signierten Snapshot und gepufferte Ereignisse |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten öffentlichen X.509-Ed25519-Schlüssel von der Agentenseite. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeout zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Puffergrenze (Standard 64 MiB) |

Der Agent liest seine Einstellungen aus den Konfigurationsdateien von HiveServer2 und aus Systemeigenschaften (`-Dgrantforge.…`, die Vorrang haben), nie aus den Einstellungen einer Sitzung, die ein Client wählen kann.

## Benutzer, Gruppen und Rollen

Richtlinien nennen den Benutzer, den HiveServer2 der Sitzung gibt: den Benutzernamen, mit dem sich ein Client anmeldet, oder den Kurznamen seines Kerberos-Prinzipals (`alice` für `alice@EXAMPLE.COM`). Gruppen sind die, die Hadoops Gruppenzuordnung dem Benutzer in HiveServer2 gibt, zusammen mit den Gruppen, in die GrantForge den Benutzer setzt; Rollen sind die, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne verwendbaren Snapshot oder wenn die Richtlinien nicht lesbar sind, wird jede Anweisung abgelehnt. Ein nicht genutzter Dienst lässt HiveServer2 ungeprüft, wie ohne Autorisierer.
- Ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `hive` ist, lehnt alles ab.
- `url`-Richtlinien entscheiden über die Orte, die Anweisungen lesen und schreiben; anders als Ranger greift der Agent nicht auf die Berechtigungen des Dateisystems zurück.
- Anweisungen, für die Hive kein Objekt nennt, werden auf jeder Datenbank geprüft: `REPL`-Anweisungen ohne Objekt brauchen `repladmin`, das Ändern des Besitzers einer Tabelle oder Datenbank ohne Objekt braucht `alter`, jeweils auf Datenbank `*`.
- `EXPLAIN`, `DESCRIBE FUNCTION`, `SHOW FUNCTIONS`, `SET` und die übrigen Anweisungen, die Ranger nicht prüft, werden ebenfalls nicht geprüft; Metadatenaufrufe von JDBC-Clients wie `getTables` werden nicht gefiltert.
- Der Agent arbeitet in HiveServer2. Die Autorisierung im Metastore ist nicht abgedeckt.

## Weitere Anleitungen

- [Apache Hive](/de/plugins/hive/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
