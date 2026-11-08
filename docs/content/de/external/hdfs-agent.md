---
title: Apache Hadoop HDFS-NameNode-Agent
description: GrantForge-Pfadrichtlinien mit dem zur Hadoop-Version passenden NameNode-Agenten durchsetzen und Zugriffs-Audits melden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Server-Plug-in `grantforge-plugin-hdfs` stellt den Diensttyp `hdfs`, seine Ressourcen und die Verbindungseinstellungen einheitlich bereit. Die NameNode-Agenten werden für jede Hadoop-Version eigens gebaut, prüfen Zugriffe über `INodeAttributeProvider` und `AccessControlEnforcer` der jeweiligen Version und teilen sich die Logik für signierte Richtlinien, Cache, Richtlinienauswertung und Audit.

Im Quellcode liegt das Server-Plug-in in `plugins/grantforge-plugin-hdfs`, die Versions-Agenten liegen in `agents/grantforge-agent-hdfs-<line>` und die gemeinsame Produktionslogik ohne Hadoop-Abhängigkeit in `agents/grantforge-agent-hdfs-common`. Die gemeinsame native Anbindung liegt im regulären Maven-Produktionsmodul `agents/grantforge-agent-hdfs-native` und richtet sich nach Java 8 / Hadoop 2.7.7; die nummerierten Module binden es als binäre Maven-Abhängigkeit ein, behalten ihre versionsspezifischen Einstiegspunkte und Callbacks und kompilieren den gemeinsamen Produktionscode nicht noch einmal. Protokoll, Snapshot-Cache und Audit-Infrastruktur liegen weiterhin in `core/grantforge-agent-core`.

Das Server-Plug-in behält einen einzigen `hdfs`-Typ, und die Client-Version ist unabhängig von der Agent-Version. Verbindung und Pfadsuche nutzen bei Hadoop 2.x `webhdfs://namenode:50070` (mit HTTPS die entsprechende `swebhdfs://`-Adresse); bei Hadoop 3.x sind RPC über `hdfs://` und WebHDFS möglich. Die Container-Matrix prüft WebHDFS für 2.x sowie RPC und WebHDFS für 3.x; RPC für 2.x ist nicht zertifiziert, und das Verbindungsprotokoll wird nicht automatisch gewechselt.

Mit aktivierter Inode-Attributerweiterung löst Apache Hadoop 2.7.7 bei einer direkten Abfrage des Wurzelpfads `/` durch einen normalen Benutzer im nativen Code einen Null-Pointer-Fehler aus, noch bevor der Agent aufgerufen wird. Nutze in dieser Version für Datenoperationen und den `lookup.path` des Dienstes echte Datenverzeichnisse wie `/data`; die Tests behalten eine eigene Prüfung für den Fehler am Wurzelpfad.

## Version wählen

| Suffix des Agent-Moduls | Geprüftes Apache Hadoop | JVM im Container | Autorisierungsschnittstelle | Callback für Superuser-Pfade |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | Parameter | nein |
| `2.10` | 2.10.2 | Java 8 | Parameter | nein |
| `3.2` | 3.2.4 | Java 8 | Parameter | nein |
| `3.3` | 3.3.6 | Java 8 | Kontext | nein |
| `3.4` | 3.4.3 | Java 11 | Kontext, Superuser- und Ablehnungs-Callbacks | ja |
| `3.5` | 3.5.0 | Java 17 | Kontext, Superuser- und Ablehnungs-Callbacks | ja |

Für Hadoop 2.10.2 etwa wird `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar` installiert. Auf jedem NameNode wird genau ein versionsspezifischer Agent installiert; bei einem Upgrade wird das alte Jar entfernt, der Provider-Klassenname in der Konfiguration bleibt gleich. Beim Start prüft der Agent die Haupt- und Nebenversion von Hadoop sowie die benötigten Callbacks und bricht bei einer falschen Version ab; andere Patches derselben Haupt- und Nebenversion und Herstellerdistributionen müssen weiterhin eigens geprüft werden.

Der Hauptcode der Agenten `common` und `2.7` bis `3.4` verwendet Java-8-Bytecode, `3.5` Java 17. Die Tabelle nennt die JVMs, mit denen die Integrationstests tatsächlich laufen; sie besagt nicht, dass 3.4.3 mit Java 8 geprüft ist. Das offizielle Testimage für 3.3.6 gibt es nur für amd64, daher läuft es auf ARM-Hosts ausdrücklich in amd64-Emulation.

## Verhältnis zu den nativen Berechtigungen

Der Agent führt zuerst die nativen HDFS-Berechtigungsprüfungen aus und wendet danach die GrantForge-Richtlinien an: Ein Benutzer muss sowohl die nativen Berechtigungen als auch die Richtlinien erfüllen. Erlaubende Richtlinien von GrantForge umgehen weder POSIX-Rechte noch ACLs, Eigentümerprüfungen oder das Sticky Bit; verweigernde Richtlinien verweigern immer. Die nativen Berechtigungen werden weiterhin mit den Verwaltungswerkzeugen von Hadoop gepflegt.

Standard ist `grantforge.hdfs.native.fallback=false`: Ohne lokalen Richtlinien-Snapshot, ohne passende Richtlinie oder solange der Agent noch nicht gestartet ist, werden Datenzugriffe verweigert. Mit `true` entscheiden bei Zugriffen, die keine Richtlinie regelt, die nativen Berechtigungen; ausdrückliche Verweigerungen gelten weiterhin. Ist der Server vorübergehend nicht erreichbar, wird der letzte lokale Snapshot mit gültiger Signatur weiter verwendet.

Vorfahren, Ziel, Teilbäume und Snapshot-Pfadprojektionen innerhalb eines Autorisierungs-Callbacks verwenden dieselbe Version des Richtlinien-Snapshots; aktualisierte Richtlinien gelten ab dem nächsten Callback, sodass sich keine erlaubenden Regeln verschiedener Versionen mischen. Das Zugriffs-Audit vermerkt die tatsächlich verwendete Richtlinienversion.

Der Agent prüft die `read`-, `write`- und `execute`-Rechte, die ein normaler Benutzer für das Ziel braucht, sowie Elternverzeichnis, Vorfahrenverzeichnisse und rekursiv zu prüfende Unterverzeichnisse. Anlegen, Löschen, Umbenennen und ähnliche Operationen betreffen mehrere Pfade, und erlaubende Richtlinien müssen sie alle abdecken. Im strikten Modus genügt eine `read`-Richtlinie auf die Zieldatei nicht; der Benutzer braucht auch `execute`-Richtlinien auf den Vorfahrenverzeichnissen, etwa `execute` auf `/` mit Rekursion, und dann Lese- und Schreibrechte auf den eigentlichen Datenverzeichnissen.

Snapshot-Pfade werden sowohl als tatsächlich angefragter Pfad als auch als ursprünglicher Pfad ohne `.snapshot/<Snapshot-Name>` geprüft; für `/data/.snapshot/s1/secret` wird also auch `/data/secret` geprüft. Verweigernde Richtlinien auf dem ursprünglichen Pfad gelten daher auch für Snapshots; für ausdrückliche Snapshot-Pfade lassen sich zusätzlich strengere Einschränkungen setzen. Metadatenabfragen folgen der HDFS-Semantik für das Durchlaufen von Verzeichnissen.

Eine einzelne rekursive Autorisierung prüft höchstens `100000` Inodes; darüber wird die Operation verweigert, damit der NameNode nicht unbegrenzt Speicher belegt. Sehr lange Pfade werden anhand des vollständigen Pfads gegen die Richtlinien geprüft; die Anzeige der Ressource im Audit ist auf `1000` Zeichen begrenzt, und die Anfragedetails vermerken die ursprüngliche Länge und einen SHA-256-Digest.

Hadoop 2.7, 2.10, 3.2 und 3.3 lassen Superuser vor dem Aufruf des Agenten passieren; diese Zugriffe kann der Agent weder steuern noch protokollieren. Bei Hadoop 3.4 und 3.5 durchlaufen Superuser-Callbacks mit Pfad zuerst die native Prüfung, danach werden die Richtlinien nach dem Operationsnamen geprüft: Lesen von Dateien und Metadatenabfragen verlangen `read`, das Auflisten von Verzeichnissen `read` + `execute` und bekannte ändernde Operationen `write`. Unbekannte, fehlende oder nicht sicher bestimmbare Operationen (etwa `checkAccess` und `concat`) verlangen vorsichtshalber alle drei Rechte.

Superuser-Callbacks haben keinen vollständigen Inode- und Teilbaumkontext; Cluster-Verwaltungsaufrufe ohne Pfad bleiben bei der nativen Prüfung, und nicht alle rekursiven Operationen eines Superusers lassen sich mit Richtlinien auf Unterverzeichnissen einschränken. Wer Daten nutzt, sollte normale Hadoop-Benutzer verwenden.

## Metriken

Der Agent meldet Metriken über das Metrics2-System von Hadoop und verwendet dieselben Sinks wie die dfs-Metriken des NameNodes; im JMX des NameNodes erscheinen sie als `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (der JMX-Exporter von Prometheus kann sie direkt abgreifen). Jede Metrik trägt die Labels `instance` (Name der Datendienst-Instanz) und `agentVersion`, sodass sich die beiden NameNodes eines HA-Paars getrennt betrachten lassen. Schlägt die Registrierung fehl, gehen nur die Metriken verloren: Der Agent protokolliert eine Warnung und autorisiert ohne Metriken weiter.

| Metrik | Bedeutung |
| --- | --- |
| `Callbacks` | Vom Agenten ausgeführte Autorisierungs-Callbacks |
| `SuperuserCallbacks` | Vom Agenten ausgeführte Superuser-Callbacks |
| `NativeDenies` | Zugriffe, die Hadoop schon vor dem Agenten verweigert hat |
| `EvaluationFailures` | Callbacks, die wegen eines Fehlers bei der Richtlinienauswertung verweigert wurden |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Berechtigungen, die Richtlinien erlaubt / verweigert / offen gelassen haben; offene werden im strikten Modus ebenfalls verweigert |
| `MissingSnapshots` | Callbacks, die ohne geprüften Richtlinien-Snapshot bedient wurden |
| `SnapshotVersion` | Version des verwendeten Richtlinien-Snapshots, 0 für keinen |
| `QueuedEvents` / `DroppedEvents` | Audit-Ereignisse, die im Speicher auf das Senden warten / die wegen voller Warteschlange oder vollem Plattenpuffer verworfen wurden |
| `ServerReachable` | Ob der letzte Zugriff auf den Richtlinienserver gelang (1/0) |

## Bereitstellung

1. Lege in den Datendiensten von GrantForge einen `hdfs`-Dienst an, speichere die Konfiguration und teste die Verbindung; richte Pfadrichtlinien für die tatsächlichen kurzen Hadoop-Benutzernamen, Gruppen oder Rollen ein.
2. Stelle unter „Datenberechtigungen → Agenten“ ein Token für diesen Dienst aus. Schreibe den Tokentext auf jedem NameNode in eine lokale Datei wie `/etc/hadoop/grantforge/token`, die der Benutzer des NameNode-Prozesses lesen kann.
3. Wähle nach der Tabelle das passende Agent-Jar aus `agents/hdfs/<Hadoop-Linie>/` des Release-Pakets und lege es in den Classpath des NameNodes, etwa `$HADOOP_HOME/share/hadoop/hdfs/lib/`. Das Agent-Jar enthält seine eigene Richtlinien-Engine, Jackson und die Signaturbibliothek; die Hadoop-Klassen stellt der NameNode bereit. Die Agent-Version im Heartbeat umfasst die Produktversion und die Hadoop-Version, gegen die kompiliert wurde.
4. Setze in der `hdfs-site.xml` jedes NameNodes die folgenden Eigenschaften; die beiden NameNodes eines HA-Paars verwenden unterschiedliche `instance`-Werte und je ein eigenes lokales Cache-Verzeichnis.

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

5. Stelle sicher, dass `dfs.permissions.enabled=true` gilt und `dfs.namenode.inode.attributes.provider.bypass.users` leer ist; mit einer Konfiguration, die den Autorisierungs-Callback umgehen könnte, startet der Agent nicht. Starte den NameNode neu und prüfe Heartbeat und Richtlinienversion auf der Agentenseite von GrantForge. Der Agent liest die bestehende Konfiguration des NameNodes und ändert keine nativen Inode-Attribute.

Bei der ersten Bereitstellung kannst du mit `native.fallback=true` beginnen, prüfen, dass der Richtlinien-Snapshot synchronisiert ist, die Rechte auf Vorfahrenverzeichnisse ergänzen und dann in den strikten Modus wechseln. Das Token muss an den Diensttyp `hdfs` gebunden sein; eine falsche Konfiguration oder die Bindung an einen anderen Diensttyp führt zur Verweigerung.

## Optionale Einstellungen

| Eigenschaft | Standardwert | Zweck |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Timeout für den Verbindungsaufbau zu GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Timeout für das Lesen der Antwort |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Aktualisierungsintervall der Richtlinien, solange der Server nicht erreichbar ist, mindestens `1000`; der normale Heartbeat nutzt das vom Server empfohlene Intervall |
| `grantforge.hdfs.signing.key.file` | nicht gesetzt | Optionale Datei mit dem öffentlichen Signaturschlüssel, dem Base64-X.509-Schlüssel aus der Konsole; ist sie gesetzt, werden nur Signaturen mit diesem Schlüssel akzeptiert |
| `grantforge.hdfs.audit.batch.size` | `500` | Höchstzahl der Ereignisse pro Sendung, Bereich `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | Kapazität der Audit-Warteschlange im Speicher, Bereich `1..1000000`, mindestens ein Batch; ist sie voll, werden neue Ereignisse gezählt und verworfen |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Intervall für das Leeren des Audits, positive Ganzzahl, höchstens `2147483647`; kleinere Werte senken die Verzögerung |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Obergrenze des Plattenpuffers, solange der Server nicht erreichbar ist, nicht negative Ganzzahl; `0` schaltet den Plattenpuffer ab |

Bei stark schwankendem Zugriffsaufkommen kannst du die Audit-Warteschlange vergrößern, damit sie seltener überläuft; ein kürzeres Intervall senkt die Audit-Verzögerung, erhöht aber die Sendehäufigkeit. Die Grenze des Plattenpuffers begrenzt den Plattenverbrauch bei langen Verbindungsausfällen; ist der Puffer abgeschaltet oder erschöpft, können Ereignisse verloren gehen. Das Audit wird im Hintergrund gesendet und wartet nicht auf den Richtlinienserver.

Ist kein öffentlicher Signaturschlüssel konfiguriert, holt ihn der Agent beim ersten Mal vom Server und speichert ihn mit dem Snapshot. Der Agent verwendet den kurzen Benutzernamen und die Gruppen, die Hadoop übergibt; Rollen und zusätzliche Gruppen stammen aus dem signierten Snapshot. Wie Kerberos-Principals auf kurze Namen abgebildet werden, bestimmt `hadoop.security.auth_to_local` des Clusters.

## Aus dem Quellcode bauen und verifizieren

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

Das Agent-Artefakt liegt unter `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`. Die Unit-Tests decken die Autorisierungs-Callbacks des NameNodes, die Konfiguration, die Versionsmetadaten und die Richtlinienentscheidungen ab; die WebHDFS- und Kerberos-Tests starten temporäre lokale Dienste. Vor dem Produktiveinsatz sind auf dem Zielcluster außerdem Lesen und Schreiben, Anlegen, Umbenennen, rekursives Löschen, HA-Umschaltung und das Cache-Verhalten nach einem Verbindungsausfall zu prüfen.

Die Integrationsverifikation läuft in der `verify`-Phase mit Testcontainers (Unit-Tests starten keinen Cluster; `verify` braucht einen verfügbaren Docker-Daemon):

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# derselbe Einstieg wie in nightly
bash script/ci/hdfs_integration.sh
# nur die angegebene Versionslinie prüfen
bash script/ci/hdfs_integration.sh 2.10
```

Die Tests verwenden Apache-Hadoop-Images mit fester Version; ältere Images werden aus einem Java-8-Basisimage mit festem Digest und einer per SHA-512 geprüften Apache-Distribution gebaut. Das echte Agent-Jar der passenden Version liegt im Classpath des NameNodes, und die tatsächlichen Hadoop- und JVM-Versionen werden geprüft. Testcontainers legt ein isoliertes Netzwerk an und verwaltet den Lebenszyklus von NameNode und DataNode; geprüft werden Lesen und Schreiben, Anlegen, Anhängen, Umbenennen, Löschen, Verweigerungen bei Rekursion und Snapshots, native Berechtigungen und Audit, die Aktualisierung von Richtlinien, ein NameNode-Neustart mit signiertem Cache nach getrennter Verbindung zum Richtlinienserver sowie der strikte Modus und der Rückfall auf native Berechtigungen ohne Snapshot.

Ein laufender Docker-Daemon ist erforderlich, und das Herunterladen der Testimages muss erlaubt sein. Ist Docker nicht verfügbar, schlagen die Tests fehl, statt still übersprungen zu werden. Der Dateisystem-Client läuft im Hadoop-Container, und der HTTP-Dienst für Richtlinien wird über die Host-Portweiterleitung von Testcontainers erreicht; ein externer Hadoop-Cluster ist nicht nötig. Nach den Tests werden Container und Testnetzwerk entfernt, und die Logs liegen unter `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`.

Der HA-Test startet zwei NameNodes, einen DataNode und einen JournalNode und gibt den beiden Agenten eigene Instanznamen und Cache-Verzeichnisse. Er schaltet den aktiven Knoten mit einem logischen HDFS-Client von Hand um und prüft danach Lesen, Schreiben und verweigernde Richtlinien; ein einzelner JournalNode dient nur dem Test, sodass weder Mehrheitsfehlertoleranz noch automatisches Failover über ZooKeeper geprüft werden.

Der Test für automatisches HA (Hadoop 2.10 bis 3.5, Java 17 und 21) startet ZooKeeper, drei JournalNodes, zwei NameNodes mit je einem ZKFC und einen DataNode. Er prüft: Wird der aktive NameNode direkt angehalten, wählt ZooKeeper einen anderen Knoten, der neue aktive Knoten setzt die Richtlinien weiter durch, und Verweigerungsereignisse gehören zu seiner Instanz; neue Richtlinien, die während der Abwesenheit eines Knotens veröffentlicht wurden, gelten nach seiner Rückkehr als Standby, und nach dem Zurückschalten gilt die neue Version statt der alten; ist der Richtliniendienst nicht erreichbar, setzt der übernehmende Knoten den bereits geladenen Snapshot weiter durch und sendet das Audit nach, sobald der Dienst wieder da ist; hält einer der drei JournalNodes an, gelingen Schreibvorgänge weiter, halten zwei an, schlagen sie fehl und der aktive NameNode beendet sich, statt ohne Mehrheit weiterzulaufen. Dazu gibt es eine Kerberos-Variante (nur Hadoop 3.5.0): Auch die JournalNodes melden sich per Keytab an und bieten nur HTTPS, die ZKFCs authentifizieren sich per SASL bei ZooKeeper, und auf den Wahlknoten darf nur der Principal des NameNodes zugreifen (ein nicht authentifizierter ZooKeeper-Client darf nicht einmal lesen); der Test prüft, dass nach dem Anhalten des aktiven NameNodes ein anderer Knoten übernimmt und Richtlinien für Kerberos-Benutzer weiter durchsetzt, dass Clients ohne Ticket stets abgewiesen werden und dass nach der Rückkehr des ursprünglichen Knotens zurückgeschaltet werden kann. Die JournalNodes laufen mit einem Principal, der denselben kurzen Namen hat wie der des NameNodes, denn ein JournalNode gibt Edits nur an den vollständigen Principal des NameNodes oder an Anfragende mit seinem eigenen kurzen Namen heraus, und die HTTP-Authentifizierung liefert nur den kurzen Namen. Automatisches HA mit Hadoop 2.7 ist nicht geprüft: Dessen Client schaltet nur um, wenn sich alle NameNode-Hostnamen auflösen lassen; der Name des im Test angehaltenen Containers lässt sich nicht mehr auflösen, sodass der Client sofort eine UnknownHostException meldet, ohne den anderen Knoten zu versuchen.

Der Kerberos-Test läuft auf allen sechs Versionslinien (Java 17 und 21): Das KDC läuft in der Test-JVM, NameNode und DataNode starten im sicheren Modus mit je eigenem Keytab, die Datenübertragung des DataNodes verlangt SASL und bietet nur HTTPS, und WebHDFS verwendet SPNEGO. Geprüft wird, dass GrantForge-Richtlinien nach der Abbildung des Principals auf den kurzen Namen für Lesen und Schreiben gelten, dass Benutzer ohne Richtlinie abgewiesen werden, dass Verweigerungen unter dem kurzen Namen protokolliert werden, dass Clients ohne Ticket abgewiesen werden und nicht auf Simple-Authentifizierung zurückfallen und dass sich der NameNode nach einem Neustart erneut anmeldet und die Richtlinien weiter durchsetzt.

Die Testquellen liegen in `src/test` der eigentlichen Produktionsmodule. Gemeinsame Unit-Tests der Richtlinien laufen in common, gemeinsame native Unit-Tests in `agents/grantforge-agent-hdfs-native/src/test`; sie werden nicht mehr in die sechs Versions-Agenten kompiliert. Die Callback-Tests jeder Version bleiben im jeweiligen nummerierten Modul, und die gemeinsamen Container-Testquellen in `agents/grantforge-agent-hdfs-common/src/test/shared` werden weiterhin in jedes nummerierte Modul kompiliert; ein eigenes Maven-Testprojekt gibt es nicht. Testcontainers ist nur im Scope test eingebunden. Nightly prüft die sechs Hadoop-Versionen auf Hosts mit Java 17 und 21, die Hadoop-Container verwenden die JVMs aus der Tabelle. Derzeit decken die Container Simple-Authentifizierung, manuelles HA, Kerberos und HTTPS auf allen sechs Versionslinien, automatisches HA für 2.10 bis 3.5 und automatisches HA unter Kerberos auf Hadoop 3.5.0 ab; automatisches HA mit 2.7, automatisches HA unter Kerberos auf anderen Versionen und Hersteller-Patches müssen weiterhin in der Zielumgebung geprüft werden.

Erweiterungspunkte und Berechtigungssemantik von Hadoop beschreiben die [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) und der [HDFS-Berechtigungsleitfaden](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
