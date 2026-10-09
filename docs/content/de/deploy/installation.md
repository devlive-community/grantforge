---
title: Release-Paket installieren
description: Das GrantForge-Release-Paket auf physischen oder virtuellen Maschinen installieren, starten, stoppen und aktualisieren.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Systemanforderungen

| Punkt | Anforderung |
| --- | --- |
| Java | 17 oder höher (das Release-Paket ist für Java 17 kompiliert, empfohlen wird 21) |
| Arbeitsspeicher | Mindestens 1 GB, für Produktivbetrieb besser 2 GB oder mehr |
| Datenbank | Für Tests die eingebaute H2; in Produktion PostgreSQL, MySQL, MariaDB, Oracle oder SQL Server, siehe [Datenbanken](/de/deploy/databases/) |
| Browser | Die letzten zwei Hauptversionen von Chrome, Edge, Firefox, Safari |

## Verzeichnisstruktur

Nach dem Entpacken von `grantforge-release.tar.gz` entsteht das Verzeichnis `grantforge/`:

| Verzeichnis | Inhalt |
| --- | --- |
| `bin/` | `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` und `import-legacy.sh` |
| `configure/` | `application.properties`, hier werden Standardwerte überschrieben |
| `lib/` | Die JARs des Servers und seiner Abhängigkeiten |
| `drivers/` | Zusätzliche JDBC-Treiber (für MySQL musst du sie selbst ablegen) |
| `plugins/` | Diensttyp-Plug-ins, siehe [Plug-ins und Diensttypen](/de/develop/plugins/) |
| `agents/` | Agent-JARs für die Bereitstellung auf Zielsystemen, zum Beispiel [Apache Hadoop HDFS-NameNode-Agent](/de/external/hdfs-agent/) |
| `data/` | Die Dateien der eingebauten H2-Datenbank (beim ersten Start angelegt) |
| `logs/` | `grantforge.log`; `console.out` enthält die Ausgaben vor dem Start des Protokollsystems |

## Start und Stopp

```bash
bin/startup.sh     # startet im Hintergrund, die Prozess-ID landet in der PID-Datei
bin/shutdown.sh    # stoppt sauber anhand der PID-Datei
bin/restart.sh     # stoppt und startet neu
bin/debug.sh       # läuft im Vordergrund, Protokolle gehen zusätzlich auf die Konsole, Stopp mit Ctrl+C
```

Die Skripte lassen sich aus jedem Verzeichnis ausführen; das Installationsverzeichnis ist das übergeordnete Verzeichnis des Skripts und kann auch über die Umgebungsvariable `GRANTFORGE_HOME` angegeben werden.

## Datenbank auswählen

Standardmäßig wird die H2-Dateidatenbank unter `data/grantforge` verwendet, geeignet für Tests. In der Produktion gibst du die Datenbank in `configure/application.properties` oder über Umgebungsvariablen an:

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

Bei der ersten Verbindung legt GrantForge alle Tabellen automatisch mit Liquibase an; bei jedem weiteren Start werden noch nicht ausgeführte Migrationen nachgezogen.

## Ersteinrichtung

Beim ersten Start wird ein einmaliges Initialisierungstoken in das Protokoll geschrieben. Öffne die Konsole, gib das Token ein und lege den ersten Administrator an; der Ablauf ist unter [In fünf Minuten loslegen](/de/start/quick-start/) beschrieben.

## Health-Check und Monitoring

| Adresse | Zweck |
| --- | --- |
| `/actuator/health/liveness` | Liveness-Probe |
| `/actuator/health/readiness` | Readiness-Probe: gibt 200 zurück, sobald die Datenbank erreichbar und die Migrationen abgeschlossen sind |
| `/actuator/prometheus` | Prometheus-Metriken; standardmäßig nur nach Anmeldung, kann mit `GRANTFORGE_PROMETHEUS_PUBLIC=true` für vertrauenswürdige Netzwerke geöffnet werden |

Wenn du strukturierte Protokolle brauchst, setze `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (oder `logstash`). Jede Protokollzeile trägt eine Request-ID, die zur `requestId` in den Fehlerantworten der Schnittstelle passt.

## Cluster-Betrieb

Mehrere Instanzen können gleichzeitig eine gemeinsame Datenbank bedienen: Die Sitzungen liegen in der Datenbank, jede Instanz kann jede Anfrage bearbeiten. Jede Instanz braucht ein eigenes `GRANTFORGE_ID_NODE` (0–1023); es bestimmt die Knotennummer, die bei der ID-Erzeugung verwendet wird. Der Load Balancer braucht keine Sitzungspersistenz.

## Aktualisieren

Stoppe den Dienst, ersetze `lib/` durch die neue Version (und behalte `configure/`, `data/`, `drivers/`, `plugins/`) und starte neu; die Datenbankmigrationen laufen automatisch. Sichere die Datenbank vor dem Aktualisieren. Für den Wechsel von 1.x siehe [Aktualisierung und Migration von Altversionen](/de/deploy/upgrade/).
