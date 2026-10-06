---
title: Entwicklung, Tests und CI
description: Lokale Builds, Tests, Code-Konventionen und CI-Prüfungen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Umgebung

- JDK 21 (Artefakte sind Java-17-Bytecode; die Policy-Engine ist Java 8)
- Node.js 22 und pnpm 8.10.2
- Docker (Datenbank-Integrationstests, Performance-Benchmarks und Images)
- Python 3.12 (CI-Skripte)

## Häufige Befehle

```bash
./mvnw verify                          # baut und testet alle Java-Module (einschließlich Konsole)
./mvnw verify -DskipFrontend           # überspringt den Build der Konsole
bash script/ci/web.sh test             # Unit-Tests der Konsole
bash script/ci/web.sh e2e              # Browsertests der Konsole (simuliertes Backend)
bash script/ci/e2e_fullstack.sh        # paketiert, startet einen echten Dienst und führt Full-Stack-Tests aus
bash script/ci/db_integration.sh postgres:17   # führt Integrationstests auf der angegebenen Datenbank aus
bash script/ci/perf_benchmark.sh smoke # kleinstes Performance-Benchmark
```

Für die Arbeit an der Konsole führe `pnpm dev` in `core/grantforge-web` aus; Vite proxyt Anfragen wie `/api` an den Dienst unter `localhost:9999`.

## Start aus der IDE

Starte direkt `org.devlive.grantforge.server.GrantForge` (Modul `grantforge-server`); standardmäßig wird H2 verwendet. Der Server lädt gebaute Plug-in-Module aus `plugins/` im Repository automatisch (siehe [Plug-ins und Diensttypen](/de/develop/plugins/)); vor der ersten Nutzung eines Plug-in-Moduls führe einmal `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` aus, um seine Abhängigkeiten zu kopieren.

## Code-Konventionen

- Backend: Error Prone + NullAway (standardmäßig non-null, an nullable Stellen JSpecify `@Nullable`), Checkstyle, PMD, SpotBugs; ArchUnit sichert die gemeinsamen Konventionen (keine Field Injection, keine nativen SQL-Abfragen, Entitäten erscheinen nicht in der API, kein `Optional.get()` usw.).
- Frontend: TypeScript Strict Mode, ESLint ohne Warnungen; alle Texte laufen über i18n, Schlüssel müssen Literale sein, und die chinesischen und englischen Schlüssel sind vollständig gleich.
- Jede Quelldatei hat einen MIT-Lizenzheader; zu jeder Hauptklasse im Code gibt es eine passende Testklasse (Ausnahmen stehen in `script/ci/test_mapping_exclusions.txt`).
- Die Abdeckungsschwellen sind je Modul gesetzt (`script/ci/coverage_thresholds.txt`).
- Datenbankmigrationen sind Liquibase-YAML, eine Datei je Änderung, nur hinzufügen und nie ändern; Typen verwenden datenbankübergreifende Eigenschaften wie `${text}`.
- Commit-Nachrichten folgen Conventional Commits, der Titel ist höchstens 72 Zeichen lang.

## CI

| Job | Inhalt |
| --- | --- |
| Repository hygiene | Lizenzheader, verbotene Pfade, Test-Mapping, i18n, das Berechtigungs-Manifest, Dateiformate sowie Prüfungen von Skripten und Workflows |
| Commit messages | Format der Commit-Nachrichten |
| CI script unit tests | Tests der CI-Skripte selbst |
| Java 17 / 21 / 25 / latest | Build und Test aller Java-Module, Prüfung der Bytecode-Version |
| Java static analysis | Abdeckung, Checkstyle, SpotBugs, PMD |
| Frontend | API-Typen konsistent mit dem Vertrag, Typprüfung und Build, ESLint, Unit-Tests, Browsertests |
| JavaScript SDK | Typprüfung, Build, ESLint, Unit-Tests |
| Database | Migrationen und Integrationstests auf H2, PostgreSQL 14/17, MySQL 8.0/8.4, MariaDB 10.11/11.4, Oracle 23 und SQL Server 2022 |
| Plugin API compatibility | Vergleicht den Plug-in-Vertrag mit der vorherigen Veröffentlichung |
| Full-stack acceptance | Paketiert auf PostgreSQL, startet den Dienst und führt Full-Stack-Browsertests aus |
| Docs | Prüfungen, Tests und Build der Dokumentationsseite |

Performance-Benchmarks laufen zusätzlich jede Nacht; Sicherheitsworkflows scannen Abhängigkeiten und Schlüssel.

## Veröffentlichungen

Die Versionsnummer ist `Jahr.Nebenversion.Revision` (zum Beispiel `2026.0.0`), Release-Kandidaten erhalten den Zusatz `-rc.N`. Die Versionen in allen poms, npm-Paketen, im appVersion des Helm Chart, in der Seitenleiste der Konsole und in der README müssen übereinstimmen; die CI prüft das mit `check_versions.py`.

Veröffentlichen geht vom `dev`-Zweig mit einem einzigen Befehl:

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # prüft nur und zeigt eine Vorschau der Release-Notes, nimmt keine Änderungen vor
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # setzt die Version, erstellt das Tag v2026.1.0 und pusht, danach wechselt dev auf die nächste Version
```

Das Skript verlangt einen sauberen Arbeitsstand, einen lokalen Zweig, der nicht hinter dem Remote liegt, und ein nicht existierendes Tag; nach der Bestätigung committet es `chore(release): prepare <Version>`, erzeugt ein kommentiertes Tag und pusht. Das Tag stößt `release.yml` an:

- `script/ci/release.sh` baut das Release-Paket, eine CycloneDX-SBOM nur mit Release-Abhängigkeiten und `SHA256SUMS`;
- Multi-Architektur-Images werden nach `ghcr.io/devlive-community/grantforge` gepusht;
- Maven-Artefakte (einschließlich Quellpaket und Javadoc) werden in GitHub Packages veröffentlicht; sind im Repository `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (Token des Central Portal), `GPG_PRIVATE_KEY` und `GPG_PASSPHRASE` gesetzt, werden sie signiert und nach Maven Central veröffentlicht;
- Ein GitHub Release wird erzeugt, dessen Text alle Commits seit der vorherigen Veröffentlichung (`v*` oder ein numerisches Tag wie `1.0.6`) enthält, gruppiert nach neuen Funktionen, Fehlerbehebungen, Performance usw., mit Links zu den Commits.

Release-Kandidaten werden als Vorabversion markiert und aktualisieren nicht das `latest`-Tag der Images. Lokal wird beim Aktivieren des `central`-Profils standardmäßig nichts veröffentlicht (`central.skip=true`); nur wenn der Release-Workflow `-Dcentral.skip=false` ausdrücklich übergibt, passiert es.

## Berechtigungs-Manifest

Die Seiten und Schaltflächen der Konsole und die APIs, die sie brauchen, sind in `core/grantforge-web/src/permissions/` deklariert. `check_permission_manifest.py` stellt sicher, dass jede deklarierte API existiert und dass jede berechtigungspflichtige Schnittstelle von einer Schaltfläche oder Seite abgedeckt ist (Ausnahmen für direkte Aufrufe stehen in `script/ci/permission_direct_apis.txt`).

## Dokumentation

Diese Seite liegt in `docs/` und nutzt den statischen Export von Next.js mit Tailwind CSS:

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # Prüfung von Seiten, Links und Bildern
pnpm build      # Ausgabe nach docs/out
```

Die Seiten sind Markdown unter `docs/content/`, die Navigation steht in `docs/lib/navigation.ts`. API-Referenz und Fehlercodes werden beim Bauen aus Vertrag und Quellcode erzeugt. Screenshots entstehen mit `script/docs/screenshots.sh`: echter Dienststart, Beispieldaten einfügen und dann Playwright.
