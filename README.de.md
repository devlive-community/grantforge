<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge-Logo" />

# GrantForge

Einheitliche Berechtigungsplattform · Benutzer, Rollen, Menüs, APIs, Datenzeilen und Felder · externe Datensysteme

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · Deutsch · [Français](README.fr.md) · [Spanisch](README.es.md) · [Portugiesisch](README.pt-BR.md) · [Italienisch](README.it.md)

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

Die Unterstützung externer Systeme umfasst das Plug-in-Framework, den Richtlinien-Editor, signierte Verteilung, Zugriffs-Audit und Dienst-Plug-ins für HDFS, ZooKeeper, MySQL, Hive, Kafka, HBase, Trino und YARN. HDFS verwendet nummerierte NameNode-Agenten, ZooKeeper einen Agenten im Server, MySQL einen eigenständigen Netzwerk-Proxy, Hive einen Autorisierer in HiveServer2, Kafka einen Autorisierer in den Brokern und Controllern, HBase einen Coprocessor in den Mastern und Region-Servern Trino eine Systemzugriffskontrolle im Coordinator und YARN einen Autorisierungsanbieter in den ResourceManagern. Verbindungseinstellungen, Datenbank-/Tabellen-/Spaltensuche und Richtlinienfunktionen beschreibt der [MySQL-Plug-in-Leitfaden](https://grantforge.devlive.org/de/plugins/mysql/). Bereitstellung und Grenzen der Durchsetzung beschreibt der [MySQL-Proxy-Agent-Leitfaden](https://grantforge.devlive.org/de/external/mysql-agent/). Hive beschreiben der [Hive-Plug-in-Leitfaden](https://grantforge.devlive.org/de/plugins/hive/) und der [Hive-Agent-Leitfaden](https://grantforge.devlive.org/de/external/hive-agent/). Kafka beschreiben der [Kafka-Plug-in-Leitfaden](https://grantforge.devlive.org/de/plugins/kafka/) und der [Kafka-Agent-Leitfaden](https://grantforge.devlive.org/de/external/kafka-agent/). HBase beschreiben der [HBase-Plug-in-Leitfaden](https://grantforge.devlive.org/de/plugins/hbase/) und der [HBase-Agent-Leitfaden](https://grantforge.devlive.org/de/external/hbase-agent/). Trino beschreiben der [Trino-Plug-in-Leitfaden](https://grantforge.devlive.org/de/plugins/trino/) und der [Trino-Agent-Leitfaden](https://grantforge.devlive.org/de/external/trino-agent/). YARN beschreiben der [YARN-Plug-in-Leitfaden](https://grantforge.devlive.org/de/plugins/yarn/) und der [YARN-Agent-Leitfaden](https://grantforge.devlive.org/de/external/yarn-agent/).


## Unterstützte Komponenten und Versionen

Die folgenden Versionen werden durch die Testcontainers-Integrationstests des Repositorys ausgeführt. Andere Versionen sind nicht zertifiziert.

| Komponente | Service-Plugin | Geprüfte Versionen | Durchsetzungs-Agent | Agent-Versionen | Agent-Java |
| --- | --- | --- | --- | --- | --- |
| Apache Hadoop HDFS | `plugins/grantforge-plugin-hdfs` | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 | NameNode-Agent | 2.7, 2.10, 3.2, 3.3, 3.4, 3.5 | 8 (3.5: 17) |
| Apache Hadoop YARN | `plugins/grantforge-plugin-yarn` | 3.2.4, 3.3.6, 3.4.3, 3.5.0 | ResourceManager-Autorisierungsprovider | Auf denselben Hadoop-Linien getestet | 8 |
| Apache Hive | `plugins/grantforge-plugin-hive` | HiveServer2 3.1.3, 4.0.1, 4.1.0 | HiveServer2-Autorisierer | Auf denselben HiveServer2-Linien getestet | 8 |
| Apache Kafka | `plugins/grantforge-plugin-kafka` | 3.7.2, 3.9.2, 4.1.2, 4.3.1 | Broker- und Controller-Autorisierer | Auf denselben Kafka-Linien getestet | 8 |
| Apache HBase | `plugins/grantforge-plugin-hbase` | 2.5.16, 2.6.7, 3.0.0 | Master- und RegionServer-Koprozessor | Auf denselben HBase-Linien getestet | 8 |
| Trino | `plugins/grantforge-plugin-trino` | 470, 476, 479, 484 | Systemzugriffskontrolle | 470 (470 bis 478), 479 (479 und neuer) | 23 / 25 |
| MySQL | `plugins/grantforge-plugin-mysql` | 8.0, 8.4 (MySQL 5.7 und MariaDB-Server werden nicht unterstützt) | Eigenständiger Netzwerk-Proxy | Einzelne Proxy-JAR | 17 |
| Apache ZooKeeper | `plugins/grantforge-plugin-zookeeper` | Nicht in den Repository-Tests aufgeführt | Agent im Server | 3.6.4 (Build-Ziel) | 8 |

Die Spalte **Agent-Java** nennt das Bytecode-Ziel jedes Agenten. HDFS-Agenten werden nach Hadoop-Linie gewählt (z. B. `agents/hdfs/2.10/` für Hadoop 2.10.2); siehe den [HDFS-Agenten-Leitfaden](https://grantforge.devlive.org/de/external/hdfs-agent/). Hadoop 2.7, 2.10, 3.2 und 3.3 stellen keinen Superuser-Autorisierungs-Callback bereit, daher kontrolliert Hadoop dort weiterhin den Superuser-Zugriff.

## So funktioniert es: zwei Ebenen

- **Verwaltungsebene**: Der GrantForge-Server (Spring Boot 4.1, Java-17-Bytecode) und die Vue-3-Konsole verwalten Mandanten, Konten, Organisation, Rollen, Berechtigungen, Audit, Datendienste und Richtlinien.
- **Datenebene**: Agenten, die im geschützten System eingebettet sind oder als eigenständige Netzwerk-Proxys laufen. Sie rufen mit ihren Tokens Ed25519-signierte Richtlinien-Snapshots ab, speichern sie lokal, autorisieren Ressourcenzugriffe und melden Audit-Ereignisse. Ist der Server vorübergehend nicht erreichbar, verwenden sie den bereits angewendeten Snapshot weiter; der jeweilige Agentenleitfaden beschreibt den Prüfungsumfang.

Eigene Systeme müssen nicht dem Muster von HDFS folgen. Gewöhnliche Anwendungen prüfen Berechtigungen im eigenen Prozess über die offene API oder den Spring Boot Starter; nur Systeme, die Zugriffe innerhalb einer Datenbank, eines Dateisystems oder eines ähnlichen Speichers oder in dessen Netzwerkzugriffspfad abfangen müssen, brauchen einen Agenten gegen `core/grantforge-agent-core`.

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
