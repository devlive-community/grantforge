---
title: Datendienste, Richtlinien und Agenten
description: "Verwalte Berechtigungen für externe Systeme wie HDFS und Hive über Plug-ins: Datendienste, Zugriffsrichtlinien, Agenten und Zugriffs-Audit."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Die Gruppe „Datenberechtigungen“ verwaltet die Berechtigungen für Datensysteme außerhalb von GrantForge. Die Architektur ähnelt der von Apache Ranger: Plug-ins definieren Diensttypen, Administratoren schreiben Richtlinien in der Konsole, und die in den Zielsystemen bereitgestellten Agenten laden die Richtlinien herunter und entscheiden Zugriffe lokal.

> [!NOTE]
> Die aktuelle Version bietet das Plug-in-Framework, den allgemeinen Richtlinien-Editor, die Richtlinienverteilung und das Zugriffs-Audit, den Diensttyp HDFS mit einem Hadoop-3.5.0-NameNode-Agenten sowie ein Beispiel-Plug-in (`example`). Das Hive-Plug-in und Agenten für andere Hadoop-Versionen befinden sich noch in der Entwicklung.

```mermaid
flowchart LR
  C[Konsole: Datendienste und Richtlinien] --> S[GrantForge-Server]
  S -->|signiertes Richtlinien-Snapshot| A[Agent (in HDFS / Hive)]
  A -->|Heartbeat und Zugriffs-Audit| S
  U[Benutzer greift auf Daten zu] --> A
```

## Plug-ins

**Plattformverwaltung → Plug-ins** listet die geladenen Diensttyp-Plug-ins. Integrierte Plug-ins werden mit dem Server ausgeliefert; für alle anderen Plug-ins genügt es, sie in das Verzeichnis `plugins` zu legen und auf „Neu einlesen“ zu klicken. Jedes Plug-in wird unabhängig geladen, ein Fehler deaktiviert nur es selbst. Zur Plug-in-Entwicklung siehe [Plug-ins und Diensttypen](/de/develop/plugins/).

![Plug-ins](/screenshots/plugins.png)

## HDFS

Das Release-Paket enthält das HDFS-Plug-in (`plugins/hdfs`), Diensttyp `hdfs`, konsistent mit dem HDFS-Dienst von Apache Ranger:

- Es gibt nur eine Ressourcenebene `path`, abgeglichen wird nach Pfad: `/data/sales` trifft auf sich selbst zu, und wenn „Rekursiv“ aktiviert ist, auch auf alle Dateien und Verzeichnisse darunter; Ausschlüsse werden unterstützt.
- Die Zugriffsarten `read`, `write` und `execute` entsprechen den Berechtigungsbits von HDFS.
- Das Plug-in verbindet sich über den eigenen Client von Hadoop mit dem Cluster. Verbindung testen prüft, ob das Abfrageverzeichnis existiert und sein Inhalt aufgelistet werden kann; beim Schreiben einer Richtlinie listet die Eingabe eines Pfads die Unterverzeichnisse und Dateien unter dem entsprechenden Verzeichnis auf, Verzeichnisse stehen dabei vor den Dateien.
- Das serverseitige Plug-in ist für Verwaltung und Abfragen zuständig; damit die Richtlinien den HDFS-Zugriff tatsächlich beschränken, muss zusätzlich der [Apache Hadoop HDFS-NameNode-Agent](/de/external/hdfs-agent/) bereitgestellt werden.

| Einstellung | Erläuterung |
| --- | --- |
| `username` | Der Benutzer für Verzeichnisabfragen; bei Kerberos der Principal, zum Beispiel `grantforge@EXAMPLE.COM` |
| `password` / `keytab` | Bei Kerberos eines von beiden: das Passwort des Principals oder der Pfad zur keytab-Datei auf dem GrantForge-Server |
| `fs.default.name` | `hdfs://namenode:8020`, hochverfügbar `hdfs://nameservice1` oder `webhdfs://namenode:9870` |
| `hadoop.security.authentication` | `simple` oder `kerberos` |
| `hadoop.security.authorization`, `hadoop.security.auth_to_local` | Konsistent mit der core-site.xml des Clusters |
| `dfs.namenode.kerberos.principal` usw. | Die Principals von NameNode, DataNode und Secondary NameNode, zum Beispiel `nn/_HOST@EXAMPLE.COM` |
| `hadoop.rpc.protection` | `authentication`, `integrity` oder `privacy`, konsistent mit dem Cluster |
| Zusätzliche Hadoop-Konfiguration | Pro Zeile ein `key=value`, für Hochverfügbarkeit und andere Einstellungen, zum Beispiel `dfs.nameservices=nameservice1`, `dfs.ha.namenodes.nameservice1=nn1,nn2`, `dfs.namenode.rpc-address.nameservice1.nn1=nn1:8020`, `dfs.client.failover.proxy.provider.nameservice1=org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider` |
| `lookup.path` | Das Abfrageverzeichnis, Standard `/`; wenn es zum Beispiel auf `/data` gesetzt ist, listet eine leere Eingabe den Inhalt von `/data` und relative Eingaben werden von dort vervollständigt. Geeignet für Cluster, in denen der abfragende Benutzer keine Berechtigung zum Auflisten des Wurzelverzeichnisses hat |
| `lookup.max.entries` | Die maximale Anzahl der in einer Verzeichnisabfrage gescannten Einträge, Standard `10000`, Bereich `1..100000`; beim Überschreiten des Grenzwerts wird ein Fehler zurückgegeben, damit Kandidaten nicht stillschweigend fehlen |

Zusätzliche Hadoop-Konfiguration überschreibt gleichnamige Verbindungseinstellungen; sowohl die Konfigurationsprüfung als auch die Anmeldung nutzen die überschriebenen Werte. `fs.defaultFS` und `fs.default.name` sind Aliase, in der zusätzlichen Konfiguration darf nur eines von beiden gesetzt werden; doppelte Schlüssel, Adressen außerhalb des Clusters und Kerberos-Konfigurationen ohne Anmeldedaten werden beim Speichern abgelehnt. In der Cluster-Adresse steht nur die Cluster-URI; die abzufragenden Unterverzeichnisse gehören in `lookup.path`.

`lookup.path` beschränkt den Bereich, in dem Pfadkandidaten durchsucht werden; es ersetzt nicht die eigene Zugriffskontrolle von HDFS; symbolische Links und ViewFS-Einhängungen folgen weiterhin der Cluster-Konfiguration. In der Eingabe darf das führende `/` entfallen, wiederholte `/` und `.` sind erlaubt, `..` und absolute Pfade außerhalb des Bereichs werden abgelehnt. Nicht existierende Verzeichnisse liefern keine Kandidaten; unzureichende Berechtigungen und Verbindungsfehler zeigen einen Fehler an.

Bei Verwendung von Kerberos muss der GrantForge-Server das KDC finden können: konfiguriere `/etc/krb5.conf` oder gib es mit `-Djava.security.krb5.conf=` an.

## Datendienste

**Datenberechtigungen → Datendienste**: Ein Dienst ist eine Instanz eines externen Systems, dessen Berechtigungen GrantForge verwaltet, zum Beispiel ein HDFS-Cluster. Wähle beim Hinzufügen eines Dienstes den Diensttyp und fülle die Verbindungsdaten gemäß den vom Plug-in definierten Konfigurationspunkten aus; vorab kannst du **Verbindung testen**. Sensible Konfiguration wie Passwörter wird verschlüsselt gespeichert und nach dem Speichern nicht mehr angezeigt.

![Datendienste](/screenshots/services.png)

## Richtlinien

**Datenberechtigungen → Richtlinien** entscheiden, wer mit welchen Ressourcen in einem Datendienst was tun darf:

- **Zugriffsrichtlinien** erlauben oder verweigern den Zugriff;
- **Maskierungsrichtlinien** verdecken Felder;
- **Zeilenfilterrichtlinien** lassen nur einen Teil der Zeilen durch.

Die Ressourcenhierarchie (zum Beispiel Datenbanken, Tabellen und Spalten in Hive), die Zugriffsarten (zum Beispiel select und update) und die Bedingungen stammen alle aus dem Plug-in des Diensttyps; beim Ausfüllen einer Ressource kannst du nach Ressourcen suchen, die im Zielsystem tatsächlich existieren. Richtlinien gelten für Benutzer, Gruppen oder Rollen.

Ebenen, die sich durchsuchen lassen, etwa HDFS-Pfade, haben die Schaltfläche **Durchsuchen**: Öffnen Sie Verzeichnisse Ebene für Ebene, sehen Sie Besitzer, Gruppe und Berechtigungen und wählen Sie mehrere Dateien oder Verzeichnisse auf einmal. Schlägt eine Suche oder das Durchsuchen fehl, wird der Grund angezeigt (keine Berechtigung, nicht erreichbar oder zu großes Verzeichnis), und Sie können es erneut versuchen.

![Richtlinien](/screenshots/policies.png)

## Agenten

**Datenberechtigungen → Agenten**: Agenten werden im Inneren des Zielsystems bereitgestellt, melden mit ihrem Token regelmäßig einen Heartbeat und laden signierte Richtlinien-Snapshots herunter, um Zugriffe lokal zu entscheiden. Hier werden Agenten-Token ausgestellt (nur einmal angezeigt) und du siehst, ob jeder Agent die neuesten Richtlinien bereits übernommen hat.

![Agenten](/screenshots/agents.png)

## Zugriffs-Audit

**Datenberechtigungen → Zugriffs-Audit**: Jede von einem Agenten gemeldete Zugriffsentscheidung: wer hat wann von wo aus was mit welcher Ressource getan, wurde es erlaubt oder verweigert, und welche Richtlinie hat entschieden. Datensätze werden standardmäßig 90 Tage aufbewahrt.

![Zugriffs-Audit](/screenshots/access-audit.png)
