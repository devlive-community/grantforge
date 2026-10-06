---
title: Konfigurationsreferenz
description: Alle Konfigurationsschlüssel, ihre Standardwerte und die zugehörigen Umgebungsvariablen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Konfiguration kannst du in `configure/application.properties` schreiben oder mit Umgebungsvariablen überschreiben. Die Loose-Binding-Regeln von Spring Boot gelten ebenfalls: `grantforge.security.mfa.step-up-window` kannst du als Umgebungsvariable `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW` schreiben. Zeitdauern werden in Formen wie `30m`, `12h` oder `90d` angegeben.

## Dienste und Datenbank

| Schlüssel | Umgebungsvariable | Standardwert | Erläuterung |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | HTTP-Port |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | eingebaute H2-Dateidatenbank | JDBC-Adresse, siehe [Datenbanken](/de/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | Datenbankbenutzer |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | leer | Datenbankpasswort |
| — | `GRANTFORGE_HOME` | Installationsverzeichnis | Verzeichnis mit den H2-Daten und Logs |
| — | `GRANTFORGE_ID_NODE` | automatisch | Pro Instanz im Cluster eindeutige Knotennummer (0–1023) |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | Größenbegrenzung für CSV-Importdateien |

## Initialisierung und Registrierung

| Schlüssel | Standardwert | Erläuterung |
| --- | --- | --- |
| `grantforge.setup.token` | leer | Festes Initialisierungs-Token (`GRANTFORGE_SETUP_TOKEN`); wenn leer, wird zufällig eines erzeugt und ins Log gedruckt |
| `grantforge.security.registration-enabled` | `false` | Ob Gäste sich selbst registrieren dürfen |
| `grantforge.security.registration-tenant` | `default` | Mandant, zu dem selbst registrierte Konten gehören |

## Passwörter und Sperrung

| Schlüssel | Standardwert | Erläuterung |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | Mindestlänge, mindestens 8 |
| `grantforge.security.password.max-length` | `128` | Höchstlänge, höchstens 1024 |
| `grantforge.security.password.required-character-classes` | `1` | Anzahl der Zeichenklassen, die gemischt werden müssen (Kleinbuchstaben, Großbuchstaben, Ziffern, andere), 1–4 |
| `grantforge.security.password.history-size` | `0` | Neues Passwort darf nicht mit den letzten N Passwörtern übereinstimmen, 0–24 |
| `grantforge.security.password.max-age` | kein Ablauf | Passwortgültigkeit; nach dem Ablauf muss das Passwort bei der Anmeldung geändert werden |
| `grantforge.security.lockout.max-attempts` | `5` | Anzahl aufeinanderfolgender Fehlversuche bis zur Sperrung |
| `grantforge.security.lockout.duration` | `15m` | Sperrdauer |

Passwörter dürfen den Benutzernamen nicht enthalten.

## Sitzungen und Cookies

| Schlüssel | Standardwert | Erläuterung |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | Leerlauf-Timeout der Sitzung (`GRANTFORGE_SESSION_TIMEOUT`) |
| `grantforge.security.sessions.max-per-account` | `0` | Maximale Anzahl gleichzeitiger Sitzungen pro Konto; 0 bedeutet unbegrenzt |
| `grantforge.security.sessions.activity-interval` | `1m` | Intervall, in dem die letzte Aktivität einer Sitzung aufgezeichnet wird |
| `grantforge.security.cookie-secure` | `false` | Auf `true` setzen, wenn TLS am Proxy endet (`GRANTFORGE_COOKIE_SECURE`) |

## Zwei-Faktor-Authentifizierung

| Schlüssel | Standardwert | Erläuterung |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | Wie lange eine Zwei-Faktor-Authentifizierung sensible Vorgänge abdeckt, von 1 Minute bis 12 Stunden |
| `grantforge.security.mfa.required-for-sensitive` | `false` | Ob sensible Vorgänge verlangen, dass das Konto die Zwei-Faktor-Authentifizierung aktiviert hat |

## Verschlüsselung und Autorisierungsserver

| Schlüssel | Standardwert | Erläuterung |
| --- | --- | --- |
| `grantforge.security.encryption-key` | automatisch erzeugt | 32 Byte großer Base64-Schlüssel, mit dem gespeicherte Geheimnisse verschlüsselt werden; in der Produktion unbedingt setzen |
| `grantforge.oauth.issuer` | Anfrageadresse | OIDC-Issuer, zum Beispiel `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | Automatische Rotationsperiode der Signaturschlüssel; 0 deaktiviert sie |
| `grantforge.oauth.signing-key-retention` | `2d` | Wie lange alte Schlüssel weiterhin veröffentlicht bleiben; muss länger sein als die Gültigkeit jedes Tokens |

## Audit, Plug-ins und Agenten

| Schlüssel | Standardwert | Erläuterung |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | Aufbewahrungsdauer der Audit-Protokolle |
| `grantforge.audit.archive-directory` | leer | Verzeichnis, in dem abgelaufene Audit-Einträge vor dem Löschen archiviert werden |
| `grantforge.access-audit.retention` | `90d` | Aufbewahrungsdauer der von Agenten gemeldeten Zugriffs-Audits |
| `grantforge.plugins.directory` | `plugins` | Plug-in-Verzeichnis |
| `grantforge.plugins.call-timeout` | `10s` | Timeout für Plug-in-Aufrufe (Verbindungstests, Ressourcensuchen) |
| `grantforge.agents.refresh-interval` | `30s` | Empfohlenes Intervall, in dem Agenten Richtlinien abrufen |

## Beobachtbarkeit

| Schlüssel | Standardwert | Erläuterung |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | Ob `/actuator/prometheus` ohne Anmeldung erreichbar ist (`GRANTFORGE_PROMETHEUS_PUBLIC`) |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Auf `ecs` oder `logstash` setzen, um JSON-Logs auszugeben |
