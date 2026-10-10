---
title: Apache Hadoop HDFS-NameNode-Agent
description: GrantForge-Pfadrichtlinien mit dem zur Hadoop-Version passenden NameNode-Agenten durchsetzen und Zugriffs-Audits melden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Server-Plug-in `grantforge-plugin-hdfs` definiert Ressourcen und Verbindungen. Die nummerierten NameNode-Agenten für Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 und 3.5 prüfen Zugriffe über die jeweilige Hadoop-SPI und teilen signierte Snapshots, Richtlinienauswertung und Audit-Übertragung.

Die gemeinsame HDFS-Logik liegt in `agents/grantforge-agent-hdfs-common`, die versionsabhängigen Adapter in `agents/grantforge-agent-hdfs-<line>`. Die gemeinsamen nativen Adapter bilden das produktive Maven-Modul `agents/grantforge-agent-hdfs-native` mit Java 8 / Hadoop 2.7.7 als Basis. Die nummerierten Module nutzen dessen binäre Maven-Abhängigkeit und behalten ihre versionsabhängigen Einstiegspunkte und Callbacks, ohne die gemeinsamen Produktionsquellen erneut zu kompilieren. `core/grantforge-agent-core` bleibt die gemeinsame Protokoll- und Laufzeitbibliothek.

Das Server-Plug-in behält einen `hdfs`-Diensttyp und eine von den Agenten unabhängige Client-Version. Verwende für Hadoop 2.x `webhdfs://namenode:50070` oder die entsprechende `swebhdfs://`-Adresse mit HTTPS; Hadoop 3.x kann RPC `hdfs://` oder WebHDFS nutzen. Die Container-Tests prüfen WebHDFS auf 2.x und beide Protokolle auf 3.x. RPC auf 2.x ist nicht zertifiziert; das konfigurierte Protokoll wird nicht automatisch gewechselt.

Mit aktivierter Inode-Attributerweiterung löst Apache Hadoop 2.7.7 bei Abfragen von `/` durch normale Benutzer einen nativen Nullpointer-Fehler vor dem Agent-Callback aus. Verwende bei dieser Version tatsächliche Datenverzeichnisse wie `/data` für Datenoperationen und `lookup.path`. Der Containertest prüft diese Einschränkung ausdrücklich.

## Zielversionen der HDFS-Agenten

| Hadoop-Basis | Java im Container | Agent-Verzeichnis |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8 (amd64-Image) | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

Wähle für die Hadoop-Linie des Clusters `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` aus `agents/hdfs/<line>/`. Die gemeinsame Logik zielt auf Java 8, der Adapter für 3.5 auf Java 17.

Hadoop 2.7, 2.10, 3.2 und 3.3 bieten den vom Agenten verwendeten Superuser-Autorisierungs-Callback nicht. Diese Superuserzugriffe bleiben unter der Kontrolle von Hadoop; für Datenzugriffe mit GrantForge-Richtlinien sollten normale Benutzer verwendet werden.

## Verhältnis zu den nativen Berechtigungen

Das folgende Durchsetzungsverhalten beschreibt Zugriffe normaler Benutzer. Die Einschränkungen für Superuser sind oben und im Abschnitt zu den Superuser-Callbacks beschrieben.

Der Agent führt zuerst die nativen HDFS-Berechtigungsprüfungen aus, danach die GrantForge-Richtlinien: Ein Benutzer muss sowohl die nativen Berechtigungen als auch die Richtlinien erfüllen. Erlauben-Richtlinien von GrantForge umgehen weder POSIX-Berechtigungen, ACLs, Eigentümerprüfungen noch das Sticky-Bit; Ablehnen-Richtlinien lehnen immer ab. Native Berechtigungseinstellungen werden weiterhin über Hadoops Verwaltungswerkzeuge gepflegt.

Standard ist `grantforge.hdfs.native.fallback=false`: Ohne lokales Richtlinien-Snapshot, ohne passende Richtlinie oder bevor der Agent gestartet ist, wird der Datenzugriff abgelehnt. Mit `true` fallen Zugriffe, die keine Richtlinie entscheidet, auf die nativen Berechtigungen zurück; explizite Ablehnen-Richtlinien gelten weiterhin. Solange der Server vorübergehend nicht erreichbar ist, wird weiterhin das letzte lokale Snapshot verwendet, dessen Signaturprüfung erfolgreich war.

Vorfahren, Ziel, Teilbäume und Snapshot-Pfadprojektionen innerhalb eines einzigen Autorisierungs-Callbacks verwenden dieselbe Version des Richtlinien-Snapshots; aktualisierte Richtlinien wirken ab dem nächsten Callback, damit Erlauben-Regeln verschiedener Versionen nie kombiniert werden. Zugriffs-Audits protokollieren die tatsächlich verwendete Richtlinienversion.

Der Agent prüft die `read`-, `write`- und `execute`-Rechte, die ein normaler Benutzer für das Ziel benötigt, sowie die übergeordneten Verzeichnisse, die Vorfahrenverzeichnisse und die Unterverzeichnisse, die eine rekursive Prüfung erfordern. Vorgänge wie Erstellen, Löschen und Umbenennen betreffen mehrere Pfade, und Erlauben-Richtlinien müssen sie alle abdecken. Im strikten Modus reicht eine `read`-Richtlinie auf die Zieldatei allein nicht: Der Benutzer braucht auch `execute`-Richtlinien auf den Vorfahrenverzeichnissen; erlaube zum Beispiel `execute` auf `/` mit gesetzter Rekursion und konfiguriere dann Lese- und Schreibrechte auf den tatsächlichen Datenverzeichnissen.

Snapshot-Pfade werden sowohl als tatsächlich angefragter Pfad als auch als ursprünglicher Pfad ohne `.snapshot/<Snapshot-Name>` geprüft, zum Beispiel wird `/data/.snapshot/s1/secret` auch als `/data/secret` geprüft. Ablehnen-Richtlinien auf dem ursprünglichen Pfad gelten damit auch für Snapshots; für explizite Snapshot-Pfade können zusätzlich strengere Einschränkungen gesetzt werden. Metadaten-Abfragen folgen der Berechtigungssemantik der HDFS-Verzeichnisdurchquerung.

Eine einzige rekursive Autorisierung prüft höchstens `100000` Inodes; jenseits dieser Grenze wird der Vorgang abgelehnt, um unbegrenzte Speicherzuweisung im Inneren des NameNode zu vermeiden. Überlange Pfade werden mit dem vollständigen Pfad bewertet; die Anzeige der Audit-Ressource ist auf `1000` Zeichen begrenzt, und die ursprüngliche Länge sowie ein SHA-256-Digest werden in den Anfragedetails protokolliert.

Die folgenden Superuser-Callbacks gelten für Hadoop 3.4 und 3.5. HDFS-Superuser werden weiterhin von Hadoop verwaltet. Superuser-Callbacks mit Pfad bestehen zuerst Hadoops Superuser-Prüfung und werden dann anhand der von Hadoop 3.4 / 3.5 bereitgestellten Vorgangsnamen gegen die Richtlinien geprüft: Dateilesen und Metadaten-Abfragen erfordern `read`, Verzeichnisaufzählung erfordert `read` + `execute`, bekannte Änderungsvorgänge erfordern `write`. Unbekannte, fehlende oder nicht zuverlässig ableitbare Vorgänge (zum Beispiel `checkAccess` und `concat`) erfordern sicherheitshalber alle drei.

Superuser-Callbacks haben keinen vollständigen Inode- und Teilbaumkontext, und pfadlose Clusterverwaltungsaufrufe behalten die native Prüfung; keiner der rekursiven Vorgänge eines Superusers lässt sich mit Unterverzeichnisrichtlinien einschränken. Datennutzer sollten reguläre Hadoop-Benutzer verwenden.

## Metriken

Der Agent meldet Metriken über Hadoops Metrics2-System, über dieselben Sinks wie die dfs-Metriken des NameNode selbst; im JMX des NameNode lauten sie `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (ein Prometheus-JMX-Exporter kann sie direkt abgreifen). Jede Metrik trägt die Labels `instance` (Name der Datendienstinstanz) und `agentVersion`, sodass die beiden NameNodes eines HA-Paars unterscheidbar sind. Eine fehlgeschlagene Registrierung kostet nur die Metriken selbst: Der Agent protokolliert eine Warnung und setzt die Autorisierung ohne sie fort.

| Metrik | Bedeutung |
| --- | --- |
| `Callbacks` | Autorisierungs-Callbacks, die der Agent ausgeführt hat |
| `SuperuserCallbacks` | Superuser-Autorisierungs-Callbacks, die der Agent ausgeführt hat (3.4 / 3.5) |
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
3. Wähle die Hadoop-Linie des Clusters und kopiere `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` aus `agents/hdfs/<line>/` auf den NameNode-Klassenpfad, zum Beispiel `$HADOOP_HOME/share/hadoop/hdfs/lib/`. Installiere dort genau einen passenden Adapter. Das Jar enthält die gemeinsame Agent-Logik, Jackson und die Signaturbibliothek; Hadoop kommt vom NameNode.
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

Die Befehle zeigen 3.5; ersetze die Linie für den gewünschten Adapter durch 2.7, 2.10, 3.2, 3.3 oder 3.4.

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

Das Agent-Artefakt liegt unter `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<Version>.jar`. Unit-Tests decken die Autorisierungs-Callbacks des NameNode, die Konfiguration, die Versionsmetadaten und die Richtlinienentscheidungen ab; die WebHDFS- und Kerberos-Tests starten temporäre lokale Dienste. Vor der Inbetriebnahme solltest du auf dem Zielcluster zusätzlich Lesen/Schreiben, Erstellen, Umbenennen, rekursives Löschen, HA-Failover und das Cache-Verhalten nach einem Netzausfall verifizieren.

Die Integrationsverifikation läuft in der `verify`-Phase mit Testcontainers (Unit-Tests starten keinen Cluster; `verify` benötigt einen verfügbaren Docker-Daemon):

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# Gleicher Einstieg wie bei nightly
bash script/ci/hdfs_integration.sh 3.5
# Einzelne Linie oder die vollständige Matrix
bash script/ci/hdfs_integration.sh all
```

Die Tests verwenden Container-Images von Apache Hadoop mit fixierten Versionen und legen das tatsächlich paketierte Agent-Jar auf den Klassenpfad des NameNode. Testcontainers erzeugt ein isoliertes Netzwerk und verwaltet den Lebenszyklus von NameNode und DataNodes; verifiziert werden Lesen/Schreiben, Erstellen, Anhängen, Umbenennen, Löschen, rekursive und Snapshot-Ablehnung, native Berechtigungen und Audit, Richtlinienaktualisierung, der Neustart des NameNode gegen den signierten Cache nach Abbruch der Verbindung zum Richtlinien-Server sowie der strikte Modus mit nativen Berechtigungsrückfall, wenn kein Snapshot existiert.

Ein laufender Docker-Daemon ist erforderlich, und das Herunterladen der Test-Images muss erlaubt sein. Steht Docker nicht zur Verfügung, schlagen die Tests fehl; sie werden nie stillschweigend übersprungen. Der Dateisystem-Client läuft innerhalb des Hadoop-Containers, und der HTTP-Dienst für Richtlinien verwendet die Host-Port-Weiterleitung von Testcontainers; ein externer Hadoop-Cluster ist nicht nötig. Nach den Tests werden Container und Test-Netzwerk aufgeräumt, und Logs werden unter `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers` gespeichert.

Der HA-Test startet zwei NameNodes, einen DataNode und einen JournalNode und konfiguriert für die beiden Agenten je einen eigenen Instanznamen und ein eigenes Cache-Verzeichnis. Er schaltet mit dem logischen HDFS-Client den aktiven Knoten manuell um und verifiziert Lese- und Schreibvorgänge sowie Ablehnen-Richtlinien nach der Umschaltung; der einzelne JournalNode dient nur dem Test, die Quorum-Fehlertoleranz wird nicht verifiziert, und auch das automatische Failover über ZooKeeper ist nicht Gegenstand des Tests.

Der Test für automatisches HA (nur Hadoop 3.5.0, Java 17 und 21) startet ZooKeeper, drei JournalNodes, zwei NameNodes mit je eigenem ZKFC und einen DataNode. Geprüft wird, dass nach dem Beenden des aktiven NameNode ZooKeeper den anderen übernehmen lässt, der die Richtlinien weiter durchsetzt und Ablehnungen unter seiner eigenen Instanz meldet; dass Richtlinien, die veröffentlicht werden, während ein NameNode fehlt, gelten, sobald er als Standby zurückkehrt, und auch nach dem Zurückwechseln gelten, nie eine ältere Version; dass der übernehmende NameNode ohne Richtlinienserver mit seinem vorhandenen Snapshot weiter durchsetzt und sein Audit nachliefert, sobald der Server zurück ist; und dass bei einem von drei gestoppten JournalNodes Schreibvorgänge gelingen, bei zwei gestoppten der Schreibvorgang scheitert und der aktive NameNode anhält, statt ohne Quorum weiterzumachen. Eine Kerberos-Variante ergänzt JournalNodes, die sich per Keytab anmelden und nur HTTPS anbieten, ZKFCs, die sich per SASL bei ZooKeeper authentifizieren, und Wahl-Znodes, die nur der Principal der NameNodes nutzen darf (ein nicht authentifizierter ZooKeeper-Client kann sie nicht einmal lesen); geprüft wird, dass der andere NameNode übernimmt, wenn der aktive gestoppt wird, und für Kerberos-Benutzer weiter durchsetzt, dass ein Client ohne Ticket durchgehend abgewiesen wird und dass die Rolle zurückwechselt, sobald der erste NameNode wieder da ist. Die JournalNodes laufen unter einem Principal mit dem Kurznamen der NameNodes, weil ein JournalNode Edits nur an den vollständigen Principal eines NameNode oder an Anfragende mit seinem eigenen Kurznamen herausgibt und die HTTP-Authentifizierung ihm nur Kurznamen liefert.

Der Kerberos-Test läuft nur auf Hadoop 3.5.0 (Java 17 und 21): Das KDC läuft in der Test-JVM, NameNode und DataNode starten im sicheren Modus mit eigenen Keytabs, der DataNode überträgt Daten nur nach SASL und bietet nur HTTPS, WebHDFS nutzt SPNEGO. Geprüft wird, dass GrantForge-Richtlinien für Lesen und Schreiben gelten, sobald Principals auf Kurznamen abgebildet sind, dass ein Benutzer ohne Richtlinie abgewiesen wird, dass Ablehnungen unter dem Kurznamen protokolliert werden, dass ein Client ohne Ticket abgewiesen wird, ohne auf Simple-Authentifizierung zurückzufallen, und dass ein neu gestarteter NameNode sich erneut anmeldet und weiter durchsetzt. Kerberos auf den anderen Versionslinien ist noch nicht verifiziert.

Gemeinsame native Unit-Tests laufen in `agents/grantforge-agent-hdfs-native/src/test`, nicht mehr in allen sechs Adaptern. Versionsabhängige Callback-Tests bleiben in ihren nummerierten Modulen. Die gemeinsamen Containertest-Quellen in `agents/grantforge-agent-hdfs-common/src/test/shared` werden weiterhin in die nummerierten Produktionsmodule kompiliert. Es gibt kein eigenes Maven-Testprojekt; Testcontainers bleibt eine Testabhängigkeit. Nightly prüft alle sechs Hadoop-Versionen mit Java-17/21-Testhosts; die Java-Version innerhalb der Container folgt der obigen Matrix. Berichte und Container-Logs werden gespeichert.

Zu den Hadoop-Erweiterungspunkten und der Berechtigungssemantik siehe [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) und [HDFS-Berechtigungsleitfaden](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
