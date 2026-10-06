---
title: Docker, Compose und Helm
description: Betreibe GrantForge mit Container-Images, probiere es mit Compose zusammen mit verschiedenen Datenbanken aus und stelle es mit Helm auf Kubernetes bereit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Images

Zu jeder Version wird das Image `ghcr.io/devlive-community/grantforge:<Version>` veröffentlicht (linux/amd64 und linux/arm64); bei stabilen Versionen wird außerdem `latest` aktualisiert:

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

Das Image wird aus dem Release-Paket gebaut, basiert auf `eclipse-temurin:21-jre`, läuft als nicht privilegierter Benutzer (UID 10001), schreibt Logmeldungen auf die Konsole und enthält keine eingebaute Datenbank. Du kannst es auch selbst aus dem Quellcode bauen:

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

Konventionen des Images:

| Pfad / Variable | Erläuterung |
| --- | --- |
| `/opt/grantforge/data` | Volume: Datendateien der eingebetteten H2-Datenbank |
| `/opt/grantforge/plugins` | Volume: Plug-ins für Diensttypen |
| `/opt/grantforge/drivers` | zusätzliche JDBC-Treiber (MySQL Connector/J kommt hierhin) |
| `9999` | Dienstport |
| `HEALTHCHECK` | fragt `/actuator/health/readiness` ab |

## Compose-Beispiele

Unter `deploy/compose/` liegt für jede Datenbank ein Beispiel bereit: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, `oracle`.

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

Öffne dann http://127.0.0.1:9999/. Das in den Beispielen verwendete Standard-Datenbankpasswort eignet sich nur zum Ausprobieren; ändere es vor dem produktiven Einsatz über `GRANTFORGE_DB_PASSWORD`. Das MySQL-Beispiel erfordert, vorher `mysql-connector-j-<Version>.jar` nach `deploy/compose/drivers/` zu legen.

## Helm

`deploy/helm/grantforge` ist ein Helm Chart: ein StatefulSet plus externe Datenbank; jeder Replikat erhält anhand der Pod-Nummer eine eigene Knoten-ID (Kubernetes 1.28 oder höher erforderlich).

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

Häufig genutzte Parameter:

| Parameter | Standardwert | Erläuterung |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / Chart-AppVersion | Image |
| `replicaCount` | `1` | Anzahl der Replikate, darf größer als 1 sein |
| `database.url` / `username` / `password` | keine | Datenbankverbindung; das Passwort gehört in `existingSecret` |
| `setupToken` | leer | legt das Initialisierungs-Token vorab fest; wenn leer, wird es in der Logdatei ausgegeben |
| `encryptionKey` | leer | 32 Byte langer Base64-Schlüssel für die Verschlüsselung gespeicherter Geheimnisse (Passwörter von Identitätsquellen, Authenticator-Schlüssel, private Signaturschlüssel usw.); wenn leer, wird er automatisch erzeugt und in der Datenbank gespeichert |
| `cookieSecure` | `false` | auf `true` setzen, wenn TLS bereits am Ingress endet; Sitzungs-Cookies erhalten dann immer das Secure-Attribut |
| `ingress.*` | aus | Konsole und API freilegen |
| `plugins.persistence.enabled` | `false` | ein persistentes Volume für das Plug-in-Verzeichnis einhängen |
| `podDisruptionBudget.enabled` | `false` | bei mehreren Replikaten empfohlen |

> [!IMPORTANT]
> Setze `encryptionKey` in Produktivumgebungen unbedingt. Ohne ihn liegt der Schlüssel in der Datenbank, und wer einen Datenbank-Backup in die Hände bekommt, kann alle darin verschlüsselt gespeicherten Geheimnisse entschlüsseln.
