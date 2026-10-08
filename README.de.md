<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge-Logo" />

# GrantForge

Einheitliche Berechtigungsplattform · Benutzer, Rollen, Menüs, APIs, Datenzeilen und Felder · externe Datensysteme

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · Deutsch · [Français](README.fr.md) · [Spanisch](README.es.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.1.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge (früher AuthX) ist eine quelloffene (MIT) Plattform für einheitliche Berechtigungen. Sie beantwortet zwei Fragen an einer Stelle: **wer was tun darf** (Funktionsberechtigung) und **wer welche Daten sehen darf** (Daten- und Feldberechtigung). Berechtigungen werden in der Konsole definiert, erklärt und geprüft, Anwendungen binden sich über Standardprotokolle an, und externe Datensysteme wie HDFS werden über Plug-ins und Agenten in dasselbe Richtlinienmodell eingebunden.

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="GrantForge-Konsole" />
</p>

## Funktionen

| Bereich | Funktionen |
| --- | --- |
| Identität und Organisation | Mehrmandantenfähigkeit, Abteilungsbaum, Gruppen und Stellen; CSV-Import und -Export in großen Mengen; LDAP- / Active-Directory-Anmeldung und -Synchronisation, OIDC-Föderation |
| Kontosicherheit | Sitzungsverwaltung und erzwungene Abmeldung, Passwortrichtlinie mit Sperrung, TOTP-Zwei-Faktor-Authentifizierung mit Wiederherstellungscodes, zweite Bestätigung für sensible Vorgänge |
| Funktionsberechtigungen | Ressourcenkatalog (Module, Menüs, Seiten, Registerkarten, Schaltflächen, APIs), Rollenvererbung, Berechtigungsmatrix, Auswirkungsanalyse vor der Vergabe |
| Datenberechtigungen | Zeilenweise Bedingungen (eigene Einträge, eigene Abteilung inklusive Unterabteilungen, bestimmte Abteilungen, eigene Bedingungen), Lesen und Schreiben getrennt steuerbar |
| Feldberechtigungen | Felder lassen sich verstecken, maskieren (E-Mail, Telefonnummer, Ausweisnummer) oder schreibgeschützt setzen |
| Erklärbarkeit und Audit | Berechtigungserklärung (woher jede einzelne Berechtigung stammt), Berechtigungssimulation, Abfrage und Export des Audit-Protokolls |
| Governance | Funktionstrennungsregeln, Berechtigungsantrag mit Freigabe, regelmäßige Berechtigungsprüfungen |
| Anwendungsintegration | OAuth-2.1- / OIDC-Autorisierungsserver, offene API für Berechtigungsabfragen, Java- (Spring Boot Starter) und JavaScript-SDK |
| Externe Systeme | Diensttyp-Plug-ins und eine Policy-Engine: Datendienste, Zugriffsrichtlinien, Agenten und Zugriffs-Audit |
| Auslieferung | Ein einzelnes ausführbares Release-Paket, Docker-Image, Compose-Beispiele, Helm-Chart; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

Die Unterstützung externer Systeme umfasst das Plug-in-Framework, den Richtlinien-Editor, signierte Verteilung, Zugriffs-Audit, den HDFS-Diensttyp und nummerierte NameNode-Agenten für Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 und 3.5. Das Hive-Plug-in ist noch in Entwicklung.

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

## So funktioniert es: zwei Ebenen

- **Verwaltungsebene**: Der GrantForge-Server (Spring Boot 4.1, Java-17-Bytecode) und die Vue-3-Konsole verwalten Mandanten, Konten, Organisation, Rollen, Berechtigungen, Audit, Datendienste und Richtlinien.
- **Datenebene**: Agenten, die im geschützten System eingebettet sind. Ein Agent ruft mit seinem Token Ed25519-signierte Richtlinien-Snapshots ab, speichert sie lokal zwischen, entscheidet jeden Zugriff bereits vor der Ausführung (und verweigert, wenn keine Richtlinie vorliegt) und meldet Zugriffsereignisse zur Prüfung zurück.

Eigene Systeme müssen nicht dem Muster von HDFS folgen. Gewöhnliche Anwendungen prüfen Berechtigungen im eigenen Prozess über die offene API oder den Spring Boot Starter; nur Systeme, die Zugriffe innerhalb einer Datenbank, eines Dateisystems oder eines ähnlichen Speichers abfangen müssen, brauchen einen Agenten gegen `core/grantforge-agent-core`.

## Anbindung der eigenen Anwendung

- **OAuth 2.1 / OpenID Connect**: GrantForge ist ein Autorisierungsserver, deshalb melden Anwendungen Benutzer darüber an; vorhandene Identitätsquellen (LDAP / AD / OIDC) lassen sich ebenfalls anbinden.
- **Java-Anwendungen**: `sdk/grantforge-spring-boot-starter` ergänzt `@RequirePermission` für Endpunkte, `@GrantForgeEntity` für Datenentitäten und `GrantForgeDataScopes.scope(...)`, um die Datenberechtigungen der Plattform in JPA-`Specification`s zu übersetzen.
- **Frontend-Anwendungen**: `@grantforge/client` meldet Benutzer von der eigenen Origin aus mit OIDC + PKCE an und fragt ihre Berechtigungen ab.
- **Offene API**: `/api/v1/open/me/authorization`, `/api/v1/open/me/data-access`, `/api/v1/open/catalog/data-entities`.
- **Lauffähige Beispiele**: `samples/shop` und `samples/notes` binden sich so an, wie es ein Dritter täte.

## Schnellstart

Java 17 oder höher ist erforderlich. Der Dienst hört auf Port `9999` und gibt beim ersten Start ein einmaliges **Initialisierungs-Token** aus; öffne <http://127.0.0.1:9999/> im Browser, trage das Token ein und lege den ersten Administrator an.

```bash
# Aus dem Release-Paket (oder selbst aus dem Quelltext bauen mit ./mvnw clean package, Ausgabe in dist/)
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# Oder mit Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# Oder mit Compose gegen eine Datenbank
docker compose -f deploy/compose/postgres.yml up -d

# Oder auf Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

Die eingebettete H2-Dateidatenbank ist der Standard, für den Start ist also keine Konfiguration nötig. Der MySQL-Treiber wird wegen seiner GPL-Lizenz nicht mit dem Release ausgeliefert; lege ihn unter `drivers/` ab. Installation, Ersteinrichtung und die erste Berechtigungsvergabe sind in der [Dokumentation](https://grantforge.devlive.org) beschrieben.

## Datenbanken

Die Standardeinstellung ist eine eingebettete H2-Dateidatenbank (`${GRANTFORGE_HOME}/data`), für den Start ist also keine Konfiguration nötig. Produktivumgebungen wechseln über Umgebungsvariablen; das Schema verwaltet Liquibase:

| Datenbank | Versionen (in CI geprüft) | Beispiel für `GRANTFORGE_DB_URL` |
| --- | --- | --- |
| PostgreSQL | 14, 17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0, 8.4 | `jdbc:mysql://host:3306/grantforge` (`mysql-connector-j` nach `lib/` legen; die GPL-Lizenz hält es aus dem Release heraus) |
| MariaDB | 10.11, 11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

Setze außerdem `GRANTFORGE_DB_USER` und `GRANTFORGE_DB_PASSWORD`; jede Instanz in einem Cluster muss ihre eigene `GRANTFORGE_ID_NODE` setzen (0-1023).

## Projektstruktur

Maven-Wurzelkoordinate: `org.devlive.grantforge:grantforge:2026.0.0`. Java-Paketpräfix: `org.devlive.grantforge`. Hauptklasse: `org.devlive.grantforge.server.GrantForge`.

`core/` enthält den Server und die gemeinsame Infrastruktur, `plugins/` enthält die vom Server geladenen Diensttyp-Plug-ins, und `agents/` enthält die in den geschützten Systemen bereitgestellten Agenten. Die gemeinsame Bibliothek `grantforge-agent-core` bleibt in `core/`; der HDFS-NameNode-Agent liegt unter `agents/grantforge-agent-hdfs-*`.

| Modul | Zuständigkeit |
| --- | --- |
| `core/grantforge-server` | Spring-Boot-Einstiegspunkt: REST-API, Sicherheitskonfiguration, offene API und die Auslieferung der Web-Konsole |
| `core/grantforge-web` | Konsole in Vue 3 / TypeScript / Tailwind CSS |
| `core/grantforge-common` | Fehlercodes und problem details, CSV, Annotationen für den Schnittstellenzugriff |
| `core/grantforge-persistence` | Entitäten, Mandantenfilter, TSID, Liquibase, SPI für Daten- und Feldberechtigungen |
| `core/grantforge-audit` | Aufzeichnung, Abfrage, Aufbewahrung und Archivierung von Audit-Ereignissen |
| `core/grantforge-identity` | Mandanten, Konten, Abteilungen, Gruppen, Stellen, Anmeldung und Sitzungen, Zwei-Faktor-Authentifizierung, Identitätsquellen |
| `core/grantforge-authz` | Ressourcenkatalog, Rollen, Berechtigungen, Zuweisungen und Auswertung, Daten- und Feldrichtlinien, Funktionstrennung, Anträge und Prüfungen |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | Vertrag für Diensttyp-Plug-ins sowie Laden, Isolation und Aufruf der Plug-ins |
| `core/grantforge-policy-engine` | Auswertungs-Engine für Richtlinien externer Systeme (Java-8-API, einbettbar in Agenten) |
| `core/grantforge-agent-core` | Gemeinsamer Agenten-Code: Einstellungen, signierte Snapshots, Zugriffsentscheidungen, Audit-Meldungen |
| `core/grantforge-service` | Datendienste, Signierung und Verteilung von Richtlinien-Snapshots, Agenten und Zugriffs-Audit |
| `core/grantforge-oauth` | OAuth-2.1- / OIDC-Server auf Basis von Spring Authorization Server |
| `plugins/grantforge-plugin-hdfs` | Plug-in für den HDFS-Diensttyp: Richtlinienverwaltung und Ressourcensuche |
| `plugins/grantforge-plugin-example` | Beispiel-Plug-in für einen eigenen Diensttyp |
| `agents/grantforge-agent-hdfs-common` | Gemeinsame HDFS-Autorisierung, Konfiguration, Snapshots und Audit-Logik (Java 8) |
| `agents/grantforge-agent-hdfs-*` | Nummerierte NameNode-Agenten für Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 und 3.5: Autorisierung und Zugriffs-Audit |
| `sdk/grantforge-spring-boot-starter`, `sdk/grantforge-js` | Java- und JavaScript-SDKs zur Anbindung von Anwendungen |
| `script/ci`, `deploy/` | CI-Prüfskripte (lokal und in CI dieselben) und Bereitstellungsressourcen (Dockerfile, Compose, Helm) |

## Betrieb und Beobachtbarkeit

- Liveness- und Readiness-Probe: `/actuator/health/liveness`, `/actuator/health/readiness` (nur Status, keine Details; Readiness gibt 200 zurück, sobald die Datenbank erreichbar und die Migrationen durchgelaufen sind).
- Metriken: `/actuator/prometheus` (mit dem Label `application="grantforge"`, standardmäßig mit Anmeldung; mit `GRANTFORGE_PROMETHEUS_PUBLIC=true` für vertrauenswürdige Netze öffnen).
- Protokollierung: standardmäßig lesbarer Text mit einer Request-ID je Zeile; für JSON-Protokolle `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (oder `logstash`) setzen.
- Skripte des Release-Pakets: `bin/startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` und `import-legacy.sh`.

## Entwicklung und Prüfung

Builds benötigen JDK 17 oder höher. Server und Hadoop-3.5-Adapter zielen auf Java 17; Policy-Engine, Agent-Core und Hadoop-2.7–3.4-Adapter auf Java 8. Error Prone + NullAway aktivieren sich ab JDK 21. Das Frontend nutzt Vue 3.5, Tailwind CSS 4, Node.js 22.12+ und pnpm 8.10.2.

```sh
# Java-Build und Unit-Tests (der Build der Konsole wird übersprungen)
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# Integrationstests der Persistenz auf einer bestimmten Datenbank (außer h2 wird Docker benötigt)
bash script/ci/db_integration.sh postgres:17

# Release verpacken (einschließlich des Konsolen-Builds) nach dist/
./mvnw clean package

# Frontend-Entwicklung und Prüfungen
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# Beispielanwendungen und SDKs
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# API-Vertrag: openapi.json und die Frontend-Typen nach einer Server-Änderung neu erzeugen (die CI prüft beides)
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# Dokumentationsseite (docs/, Next.js + Tailwind CSS)
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # die Screenshots mit echtem Dienst und Beispieldaten neu erzeugen

# Repository-Prüfungen (dieselben wie in der CI)
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## Links

- [Repository](https://github.com/devlive-community/grantforge)
- [Dokumentation](https://grantforge.devlive.org): Schnellstart, Benutzerhandbuch, Anbindung und technische Referenzen, Quellen in [`docs/`](docs/)
- [Mitwirken](CONTRIBUTING.md) · [Verhaltensregeln](CODE_OF_CONDUCT.md) · [Änderungsprotokoll](CHANGELOG)
