---
title: Leistung und Benchmarks
description: Leistungsziele für eine Million Konten, Daten und Methodik der Benchmarks sowie die lokale Ausführung.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Die Leistungsziele von GrantForge richten sich an eine große Organisation: **eine Million Konten, zehntausend Rollen, hunderttausend Ressourcen**. Jede Nacht laufen die System-Benchmarks auf PostgreSQL 17; wird eine Kennzahl über ihrer Obergrenze, schlägt der Build fehl.

## Ziele

| Kennzahl | Bedeutung | Obergrenze |
| --- | --- | --- |
| `authz.snapshot.hit` | Bereits im Cache liegendes Berechtigungs-Snapshot (wird bei jedem API-Aufruf genutzt) | p99 1 ms |
| `authz.snapshot.build` | Erstberechnung des Konsolen-Snapshots eines Kontos | p95 50 ms |
| `authz.snapshot.app` | Snapshot-Berechnung in einer Anwendung mit großem Ressourcenbaum | p95 50 ms |
| `api.users.page` | Eine Seite der Benutzerliste (innerhalb der ersten 500 Seiten) | p95 200 ms |
| `api.users.search` | Benutzersuche nach Name | p95 200 ms |
| `api.groups.page` | Eine Seite der Gruppenliste | p95 200 ms |
| `api.roles.list` | Rollenliste (ohne Paginierung) | p95 200 ms |
| `api.me.authorization` | Berechtigungen des aktuellen Kontos | p95 200 ms |
| `api.member.groups` | Zugriff eines Kontos mit nur einer Seitenberechtigung auf eine geschützte Liste | p95 200 ms |
| `jmh.derivation.*` | Ableitung für einen großen Ressourcenbaum vorbereiten, zwanzig Berechtigungen ableiten (JMH) | im Mittel 50 / 5 ms |

Obergrenzen dürfen nur verschärft werden; jede Lockerung erfordert eine dokumentierte Entscheidung.

## Daten

Die Benchmarks starten den echten Dienst, führen die Initialisierung durch und schreiben dann über die Entitäten der Anwendung selbst:

- 100 Abteilungen, je tausend Konten eine Gruppe, 100 Stellen;
- jedes Konto gehört zu einer Abteilung und einer Gruppe, jedes zehnte Konto hat eine Stelle, jedes zwanzigste Konto hat direkt zugewiesene Rollen;
- jede zehnte Rolle erbt von einer der ersten hundert Rollen; jede Gruppe hat drei Rollen, jede Abteilung zwei, jede Stelle eine; jede Rolle gibt fünf Konsolen-Seiten frei;
- eine Anwendung: unter mehreren Modulen hundert Seiten, neun Aktionen pro Seite; ein Zehntel der Rollen erhält zwanzig dieser Seiten und Aktionen.

Jede Kennzahl wird nach dem Aufwärmen Aufruf für Aufruf gemessen.

## Lokale Ausführung

```bash
bash script/ci/perf_benchmark.sh full            # Zielgröße, postgres:17 (Docker erforderlich), Obergrenzen prüfen
bash script/ci/perf_benchmark.sh smoke           # kleine Skalierung auf H2, bestätigt nur, dass die Benchmarks laufen
bash script/ci/perf_benchmark.sh full mysql:8.4  # andere Datenbanken
```

Der Bericht wird nach `perf/target/perf-report.json` geschrieben, zusätzlich erscheint eine Tabelle im Log. Zusätzliche Parameter können über `PERF_OPTS` übergeben werden:

| Parameter | Standardwert | Bedeutung |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2` oder `<Engine>:<Version>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | Datenumfang |
| `perf.samples` / `perf.warmup` | 1000 / 200 | Messungen und Aufwärm-Läufe pro Kennzahl |
| `perf.jmh` | `true` | JMH-Benchmarks ebenfalls ausführen |
| `perf.enforce` | `true` | beim Überschreiten einer Obergrenze mit 1 beenden |

## Warum es schnell ist

- Berechtigungs-Snapshots werden pro Konto im Cache gehalten und über die Versionsnummern von Katalog und Mandant komplett invalidiert; ein Treffer ist nur ein Lesezugriff im Speicher.
- Die Ableitung läuft im Speicher: Ressourcenbaum, Vererbungsbeziehungen und Zuweisungen werden vorab in kompakte Strukturen geladen, sodass keine Abfrage je Eintrag nötig ist.
- Listen-Schnittstellen sind durchweg paginiert, Sortierung und Filterung liegen auf indizierten Spalten; verzögerte Joins laden in Blöcken von 64, Schreibvorgänge committen in Blöcken von 50 (die IDs werden von der Anwendung erzeugt, daher ist Batch-Insert möglich).
