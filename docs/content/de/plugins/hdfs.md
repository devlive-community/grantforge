---
title: Apache Hadoop HDFS
description: Das HDFS-Dienst-Plug-in installieren, Verbindungen konfigurieren, Verzeichnisse durchsuchen und Pfadrichtlinien verwalten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Plug-in Apache Hadoop HDFS bietet Clusterverbindungen, Pfadsuche und Richtlinienverwaltung in GrantForge. Plug-in-ID und Diensttyp heißen beide `hdfs`.

## Installation

Die Distribution enthält das Plug-in unter `plugins/hdfs`. Prüfe unter **Plattformverwaltung → Plug-ins**, dass `hdfs` aktiviert ist; lies das Plug-in-Verzeichnis nach einem Update erneut ein.

## Datendienst hinzufügen

1. Öffne **Datenberechtigungen → Datendienste**, füge einen Dienst hinzu und wähle HDFS (`hdfs`).
2. Trage Cluster-URI und Suchbenutzer ein. Hadoop 2.x nutzt `webhdfs://namenode:50070`; 3.x kann `hdfs://namenode:8020` oder `webhdfs://namenode:9870` verwenden. Für HTTPS nutze `swebhdfs://` mit dem tatsächlichen Clusterport.
3. Lege das Suchverzeichnis fest und teste die Verbindung: Das Verzeichnis muss existieren und auflistbar sein. Speichere anschließend den Dienst.

| Einstellung | Zweck |
| --- | --- |
| `fs.default.name` | Erforderliche Cluster-URI ohne Unterverzeichnis, Zugangsdaten oder Abfrageparameter; HA kann `hdfs://nameservice1` mit passenden zusätzlichen Eigenschaften verwenden |
| `username` | Erforderlicher Suchbenutzer; bei Kerberos ein Principal wie `grantforge@EXAMPLE.COM` |
| `hadoop.security.authentication` | Standard `simple`; für Kerberos-Cluster `kerberos` wählen |
| `hadoop.security.authorization` | Ob Hadoop Berechtigungen prüft, Standard `false`; passend zur core-site.xml des Clusters |
| `hadoop.security.auth_to_local` | Regeln zur Abbildung von Kerberos-Principals auf Benutzernamen, passend zur core-site.xml des Clusters |
| `password` / `keytab` | Kerberos-Passwort oder Pfad einer Keytab-Datei auf dem GrantForge-Server |
| `dfs.namenode.kerberos.principal`, `dfs.datanode.kerberos.principal`, `dfs.secondary.namenode.kerberos.principal` | Principals der Cluster-Komponenten unter Kerberos, etwa `nn/_HOST@EXAMPLE.COM`, passend zur Cluster-Konfiguration |
| `lookup.path` | Startverzeichnis für Suche und Navigation, Standard `/`, etwa `/data`; die Navigation bleibt darunter |
| `lookup.max.entries` | Grenze vollständiger Verzeichnisscans, Standard `10000`, Bereich `1..100000` |
| `hadoop.config` | Eine Zeile `key=value` je Hadoop-Eigenschaft, etwa für HA; überschreibt gleichnamige Verbindungseinstellungen |
| `hadoop.rpc.protection` | `authentication`, `integrity` oder `privacy`, passend zum Cluster |

Wenn in Hadoop 2.7.7 die NameNode-Attributerweiterung aktiviert ist, lösen normale Benutzer beim Abfragen des Wurzelpfads `/` eine bestätigte Upstream-`NullPointerException` aus; setze `lookup.path` auf ein vorhandenes Verzeichnis wie `/data` (siehe [Agentenleitfaden](/de/external/hdfs-agent/)).

Kerberos benötigt außerdem einen erreichbaren KDC, die `krb5.conf` des Servers sowie passende `hadoop.security.auth_to_local`-Regeln und Dienst-Principals. Das Suchkonto liest Verzeichnismetadaten.

Die Konfiguration wird beim Speichern geprüft: In `hadoop.config` sind `fs.defaultFS` und `fs.default.name` Aliasse, deshalb nur eines von beiden setzen; die Cluster-URI darf keine Zugangsdaten, keinen Pfad, keine Abfrage und kein Fragment enthalten; `kerberos` erfordert ein `password` oder ein `keytab`; `lookup.path` muss ein absoluter Pfad ohne `..` sein; `lookup.max.entries` muss zwischen `1` und `100000` liegen. Zusätzliche Eigenschaften überschreiben gleichnamige Verbindungseinstellungen; Prüfung und Anmeldung verwenden die überschriebenen Werte.

## Pfade durchsuchen

Wähle unter **Datenberechtigungen → Richtlinien** den Dienst und nutze **Durchsuchen** neben `path`.

- Öffne Verzeichnisse, navigiere über den Pfad oder zum übergeordneten Verzeichnis und lade weitere Seiten nach Bedarf.
- Prüfe Datei-/Verzeichnismarkierungen, Eigentümer, Gruppe, Berechtigungen, Dateigröße und Änderungszeit.
- Übernimm mehrere Dateien oder Verzeichnisse oder das aktuelle Verzeichnis in die Richtlinie; getippte Pfade bieten weiterhin Vorschläge.

RPC und WebHDFS mit Stapelauflistung nutzen native Seitennavigation. Ältere Endpunkte lesen Verzeichnisse innerhalb der Scangrenze und melden darüber einen Fehler. Berechtigungs-, Anmelde- und Verbindungsfehler zeigen den Grund und lassen sich erneut versuchen.

Eingaben dürfen das führende `/` weglassen, wiederholte `/` und `.` sind erlaubt, `..` und absolute Pfade außerhalb von `lookup.path` werden abgewiesen; ein nicht vorhandenes Verzeichnis liefert keine Kandidaten. Das Durchsuchen ersetzt nicht die Zugriffskontrolle von HDFS selbst; symbolische Links und ViewFS-Mounts folgen weiterhin der Cluster-Konfiguration.

## Richtlinien durchsetzen

Die einzige Ressourcenebene ist `path`, mit den Zugriffstypen `read`, `write` und `execute`. Pfadrichtlinien unterstützen Rekursion und Ausschlüsse.

Das Server-Plug-in verwaltet und sucht Ressourcen. Zur Durchsetzung ist zusätzlich ein zur Hadoop-Version passender NameNode-Agent nötig; Benutzer müssen native HDFS-Berechtigungen und GrantForge-Richtlinien erfüllen. Nummerierte Agenten gibt es für 2.7, 2.10, 3.2, 3.3, 3.4 und 3.5. Verifizierte Kombinationen, Authentifizierungs- und HA-Abdeckung sowie Superuser-Grenzen stehen im Agentenleitfaden.

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Apache Hadoop HDFS-NameNode-Agent](/de/external/hdfs-agent/)
- [Plug-ins und Diensttypen entwickeln](/de/develop/plugins/)
