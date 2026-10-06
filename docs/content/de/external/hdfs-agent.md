---
title: HDFS-NameNode-Agent
description: GrantForge-Pfadrichtlinien im Inneren eines Hadoop-3.5.0-NameNode durchsetzen und Zugriffs-Audits melden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das serverseitige Plug-in `grantforge-plugin-hdfs` definiert die Ressourcen und die Verbindungskonfiguration; `grantforge-agent-hdfs` wird im Inneren des NameNode installiert, prüft Zugriffe über Hadoops `INodeAttributeProvider` und `AccessControlEnforcer` und nutzt den GrantForge-Agent-Core, um signierte Richtlinien herunterzuladen, lokale Snapshots vorzuhalten und Audits stapelweise zu melden. Der aktuelle Agent ist für **Hadoop 3.5.0, Java 17 und höher** gebaut; andere Hadoop-Versionen erfordern eine passende Adaptierung und Verifikation.

Im Quellcode liegt das Server-Plug-in unter `plugins/grantforge-plugin-hdfs`, der NameNode-Agent unter `agents/grantforge-agent-hdfs`; die gemeinsam genutzten Protokoll-, Snapshot-Cache- und Audit-Meldeinfrastruktur liegt unter `core/grantforge-agent-core`.

## Verhältnis zu den nativen Berechtigungen

Der Agent führt zuerst die nativen HDFS-Berechtigungsprüfungen aus, danach die GrantForge-Richtlinien: Ein Benutzer muss sowohl die nativen Berechtigungen als auch die Richtlinien erfüllen. Erlauben-Richtlinien von GrantForge umgehen weder POSIX-Berechtigungen, ACLs, Eigentümerprüfungen noch das Sticky-Bit; Ablehnen-Richtlinien lehnen immer ab. Native Berechtigungseinstellungen werden weiterhin über Hadoops Verwaltungswerkzeuge gepflegt.

Standard ist `grantforge.hdfs.native.fallback=false`: Ohne lokales Richtlinien-Snapshot, ohne passende Richtlinie oder bevor der Agent gestartet ist, wird der Datenzugriff abgelehnt. Mit `true` fallen Zugriffe, die keine Richtlinie entscheidet, auf die nativen Berechtigungen zurück; explizite Ablehnen-Richtlinien gelten weiterhin. Solange der Server vorübergehend nicht erreichbar ist, wird weiterhin das letzte lokale Snapshot verwendet, dessen Signaturprüfung erfolgreich war.

Vorfahren, Ziel, Teilbäume und Snapshot-Pfadprojektionen innerhalb eines einzigen Autorisierungs-Callbacks verwenden dieselbe Version des Richtlinien-Snapshots; aktualisierte Richtlinien wirken ab dem nächsten Callback, damit Erlauben-Regeln verschiedener Versionen nie kombiniert werden. Zugriffs-Audits protokollieren die tatsächlich verwendete Richtlinienversion.

Der Agent prüft die `read`-, `write`- und `execute`-Rechte, die ein normaler Benutzer für das Ziel benötigt, sowie die übergeordneten Verzeichnisse, die Vorfahrenverzeichnisse und die Unterverzeichnisse, die eine rekursive Prüfung erfordern. Vorgänge wie Erstellen, Löschen und Umbenennen betreffen mehrere Pfade, und Erlauben-Richtlinien müssen sie alle abdecken. Im strikten Modus reicht eine `read`-Richtlinie auf die Zieldatei allein nicht: Der Benutzer braucht auch `execute`-Richtlinien auf den Vorfahrenverzeichnissen; erlaube zum Beispiel `execute` auf `/` mit gesetzter Rekursion und konfiguriere dann Lese- und Schreibrechte auf den tatsächlichen Datenverzeichnissen.

Snapshot-Pfade werden sowohl als tatsächlich angefragter Pfad als auch als ursprünglicher Pfad ohne `.snapshot/<Snapshot-Name>` geprüft, zum Beispiel wird `/data/.snapshot/s1/secret` auch als `/data/secret` geprüft. Ablehnen-Richtlinien auf dem ursprünglichen Pfad gelten damit auch für Snapshots; für explizite Snapshot-Pfade können zusätzlich strengere Einschränkungen gesetzt werden. Metadaten-Abfragen folgen der Berechtigungssemantik der HDFS-Verzeichnisdurchquerung.

Eine einzige rekursive Autorisierung prüft höchstens `100000` Inodes; jenseits dieser Grenze wird der Vorgang abgelehnt, um unbegrenzte Speicherzuweisung im Inneren des NameNode zu vermeiden. Überlange Pfade werden mit dem vollständigen Pfad bewertet; die Anzeige der Audit-Ressource ist auf `1000` Zeichen begrenzt, und die ursprüngliche Länge sowie ein SHA-256-Digest werden in den Anfragedetails protokolliert.

HDFS-Superuser werden weiterhin von Hadoop verwaltet. Superuser-Callbacks mit Pfad bestehen zuerst Hadoops Superuser-Prüfung und werden dann anhand der von Hadoop 3.5.0 bereitgestellten Vorgangsnamen gegen die Richtlinien geprüft: Dateilesen und Metadaten-Abfragen erfordern `read`, Verzeichnisaufzählung erfordert `read` + `execute`, bekannte Änderungsvorgänge erfordern `write`. Unbekannte, fehlende oder nicht zuverlässig ableitbare Vorgänge (zum Beispiel `checkAccess` und `concat`) erfordern sicherheitshalber alle drei.

Superuser-Callbacks haben keinen vollständigen Inode- und Teilbaumkontext, und pfadlose Clusterverwaltungsaufrufe behalten die native Prüfung; keiner der rekursiven Vorgänge eines Superusers lässt sich mit Unterverzeichnisrichtlinien einschränken. Datennutzer sollten reguläre Hadoop-Benutzer verwenden.

## Metriken

Der Agent meldet Metriken über Hadoops Metrics2-System, über dieselben Sinks wie die dfs-Metriken des NameNode selbst; im JMX des NameNode lauten sie `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (ein Prometheus-JMX-Exporter kann sie direkt abgreifen). Jede Metrik trägt die Labels `instance` (Name der Datendienstinstanz) und `agentVersion`, sodass die beiden NameNodes eines HA-Paars unterscheidbar sind. Eine fehlgeschlagene Registrierung kostet nur die Metriken selbst: Der Agent protokolliert eine Warnung und setzt die Autorisierung ohne sie fort.

| Metrik | Bedeutung |
| --- | --- |
| `Callbacks` | Autorisierungs-Callbacks, die der Agent ausgeführt hat |
| `SuperuserCallbacks` | Superuser-Autorisierungs-Callbacks, die der Agent ausgeführt hat |
| `NativeDenies` | Zugriffe, die Hadoop abgelehnt hat, bevor der Agent zum Zug kam |
| `EvaluationFailures` | Callbacks, die wegen einer Ausnahme bei der Richtlinienauswertung fehlschlugen und den Zugriff ablehnten (fail closed) |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Von einer Richtlinie erlaubte / abgelehnte / unentschiedene Rechte; unentschieden wird im strikten Modus ebenfalls abgelehnt |
| `MissingSnapshots` | Bediente Callbacks, während kein verifiziertes Richtlinien-Snapshot vorlag |
| `SnapshotVersion` | Richtlinienversion des aktuell verwendeten Snapshots, 0 bedeutet keine |
| `QueuedEvents` / `DroppedEvents` | Audit-Ereignisse, die im Speicher auf die Meldung warten / wegen voller Queue oder vollem Puffer verworfen wurden |
| `ServerReachable` | Ob der letzte Zugriff auf den Richtlinien-Server erfolgreich war (1/0) |

## Bereitstellung

1. Lege in GrantForge unter Datenberechtigungen → Datendienste einen `hdfs`-Dienst an, speichere die Konfiguration und teste die Verbindung; konfiguriere Pfadrichtlinien für die tatsächlichen Hadoop-Kurznamen, Benutzergruppen oder Rollen.
2. Stelle unter „Datenberechtigungen → Agenten“ für diesen Dienst ein Token aus. Schreibe das Token im Klartext in eine lokale Datei auf jedem NameNode, zum Beispiel `/etc/hadoop/grantforge/token`, lesbar für den Benutzer, unter dem der NameNode läuft.
3. Lege die zum aktuellen Release passende `grantforge-agent-hdfs-<Version>.jar` aus dem Release-Paket-Verzeichnis `agents/hdfs/` auf den Klassenpfad des NameNode, zum Beispiel `$HADOOP_HOME/share/hadoop/hdfs/lib/`. Das Agent-Jar bringt seine eigene Richtlinien-Engine, Jackson und die Signaturbibliothek bereits mit; die Hadoop-Klassen stellt der NameNode bereit; die im Heartbeat gemeldete Agent-Version wird aus den Build-Metadaten erzeugt.
4. Konfiguriere die folgenden Eigenschaften in der `hdfs-site.xml` jedes NameNode; die beiden NameNodes eines HA-Setups verwenden unterschiedliche `instance`-Werte und jeweils eigene lokale Cache-Verzeichnisse.

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. Bestätige, dass `dfs.permissions.enabled=true` ist und dass `dfs.namenode.inode.attributes.provider.bypass.users` leer ist; beim Start lehnt der Agent Konfigurationen ab, die Autorisierungs-Callbacks umgehen könnten. Starte den NameNode neu und prüfe dann Heartbeat und Richtlinienversion auf der Agent-Seite von GrantForge. Der Agent liest die vorhandene Konfiguration des NameNode; er ändert die nativen Attribute von Inodes nicht.

Bei der ersten Bereitstellung kannst du zunächst mit `native.fallback=true` arbeiten, prüfen, ob das Richtlinien-Snapshot synchronisiert ist und die Berechtigungen auf den Vorfahrenverzeichnissen vollständig sind, und dann in den strikten Modus wechseln. Der Diensttyp, an den ein Token gebunden ist, muss `hdfs` lauten; eine Fehlkonfiguration oder eine Bindung an einen anderen Diensttyp führt dazu, dass Zugriffe abgelehnt werden.

## Optionale Einstellungen

| Eigenschaft | Standardwert | Zweck |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Timeout für die Verbindung zu GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Timeout für das Lesen einer Antwort |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Intervall für die Richtlinienaktualisierung, solange der Server nicht erreichbar ist, mindestens `1000`; normale Heartbeats verwenden das vom Server vorgeschlagene Intervall |
| `grantforge.hdfs.signing.key.file` | nicht gesetzt | Optionale Datei mit dem öffentlichen Signaturschlüssel; Inhalt ist der von der Konsole bereitgestellte Base64-X.509-Schlüssel; nach der Konfiguration werden nur Signaturen dieses Schlüssels akzeptiert |
| `grantforge.hdfs.audit.batch.size` | `500` | Maximale Anzahl Ereignisse pro Meldung, Bereich `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | Kapazität der Audit-Queue im Speicher, Bereich `1..1000000`, muss mindestens einen Batch fassen; wenn die Queue voll ist, werden neue Ereignisse gezählt und verworfen |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Audit-Flush-Intervall, positive Ganzzahl, höchstens `2147483647`; ein kleinerer Wert senkt die Meldelatenz |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Grenze des Plattenpuffers, solange der Server nicht erreichbar ist, nicht negative Ganzzahl; `0` deaktiviert den Plattenpuffer |

Bei stark schwankendem Zugriffsaufkommen kannst du die Audit-Queue vergrößern, um Queue-Überläufe zu verringern; ein kürzeres Flush-Intervall senkt die Audit-Latenz, erhöht aber auch die Meldefrequenz. Die Grenze des Plattenpuffers begrenzt die Plattennutzung bei längeren Netzausfällen; wenn der Puffer deaktiviert oder erschöpft ist, können Ereignisse verloren gehen. Die Audit-Meldung läuft im Hintergrund und wartet nicht auf eine Antwort des Richtlinien-Servers.

Ist kein öffentlicher Signaturschlüssel konfiguriert, holt ihn der Agent beim ersten Kontakt mit dem Server und speichert ihn zusammen mit dem Snapshot. Der Agent verwendet den von Hadoop übergebenen Kurznamen und die übergebenen Gruppen; Rollen und zusätzliche Gruppen kommen aus dem signierten Snapshot. Die Kurznamen-Abbildung für Kerberos-Principals wird durch `hadoop.security.auth_to_local` des Clusters bestimmt.

## Aus dem Quellcode bauen und verifizieren

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs,core/grantforge-plugin-host -am test
```

Das Agent-Artefakt liegt unter `agents/grantforge-agent-hdfs/target/grantforge-agent-hdfs-<Version>.jar`. Unit-Tests decken die Autorisierungs-Callbacks des NameNode, die Konfiguration, die Versionsmetadaten und die Richtlinienentscheidungen ab; die WebHDFS- und Kerberos-Tests starten temporäre lokale Dienste. Vor der Inbetriebnahme solltest du auf dem Zielcluster zusätzlich Lesen/Schreiben, Erstellen, Umbenennen, rekursives Löschen, HA-Failover und das Cache-Verhalten nach einem Netzausfall verifizieren.

Die Integrationsverifikation läuft in der `verify`-Phase mit Testcontainers (Unit-Tests starten keinen Cluster; `verify` benötigt einen verfügbaren Docker-Daemon):

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am verify
# Gleicher Einstieg wie bei nightly
bash script/ci/hdfs_integration.sh
```

Die Tests verwenden Container-Images von Apache Hadoop mit fixierten Versionen und legen das tatsächlich paketierte Agent-Jar auf den Klassenpfad des NameNode. Testcontainers erzeugt ein isoliertes Netzwerk und verwaltet den Lebenszyklus von NameNode und DataNodes; verifiziert werden Lesen/Schreiben, Erstellen, Anhängen, Umbenennen, Löschen, rekursive und Snapshot-Ablehnung, native Berechtigungen und Audit, Richtlinienaktualisierung, der Neustart des NameNode gegen den signierten Cache nach Abbruch der Verbindung zum Richtlinien-Server sowie der strikte Modus mit nativen Berechtigungsrückfall, wenn kein Snapshot existiert.

Ein laufender Docker-Daemon ist erforderlich, und das Herunterladen der Test-Images muss erlaubt sein. Steht Docker nicht zur Verfügung, schlagen die Tests fehl; sie werden nie stillschweigend übersprungen. Der Dateisystem-Client läuft innerhalb des Hadoop-Containers, und der HTTP-Dienst für Richtlinien verwendet die Host-Port-Weiterleitung von Testcontainers; ein externer Hadoop-Cluster ist nicht nötig. Nach den Tests werden Container und Test-Netzwerk aufgeräumt, und Logs werden unter `agents/grantforge-agent-hdfs/target/hdfs-testcontainers` gespeichert.

Der HA-Test startet zwei NameNodes, einen DataNode und einen JournalNode und konfiguriert für die beiden Agenten je einen eigenen Instanznamen und ein eigenes Cache-Verzeichnis. Er schaltet mit dem logischen HDFS-Client den aktiven Knoten manuell um und verifiziert Lese- und Schreibvorgänge sowie Ablehnen-Richtlinien nach der Umschaltung; der einzelne JournalNode dient nur dem Test, die Quorum-Fehlertoleranz wird nicht verifiziert, und auch das automatische Failover über ZooKeeper ist nicht Gegenstand des Tests.

Testquellen und Abhängigkeiten liegen direkt im vorhandenen `agents/grantforge-agent-hdfs` unter `src/test` und im Test-Scope; ein eigenes Maven-Testprojekt wird nicht angelegt, und Testcontainers landet nie im Agent-Release-Paket. Nightly lässt denselben Testeinstieg auf Java 17 und 21 laufen und speichert die Berichte und Container-Logs.

Zu den Hadoop-Erweiterungspunkten und der Berechtigungssemantik siehe [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) und [HDFS-Berechtigungsleitfaden](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
