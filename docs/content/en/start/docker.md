---
title: Docker, Compose, and Helm
description: Run GrantForge in a container image, try it against various databases with Compose, and deploy to Kubernetes with Helm.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Image

Every release publishes the image `ghcr.io/devlive-community/grantforge:<版本>` (linux/amd64 and linux/arm64); stable releases also update `latest`:

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

The image is built from the distribution package, based on `eclipse-temurin:21-jre`, runs as an unprivileged user (UID 10001), logs to the console, and ships without a database. You can also build it yourself from source:

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

Image conventions:

| Path / variable | Description |
| --- | --- |
| `/opt/grantforge/data` | Volume: built-in H2 data files |
| `/opt/grantforge/plugins` | Volume: service type plugins |
| `/opt/grantforge/drivers` | Additional JDBC drivers (put MySQL Connector/J here) |
| `9999` | Service port |
| `HEALTHCHECK` | Hits `/actuator/health/readiness` |

## Compose Examples

`deploy/compose/` contains one example per database: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, and `oracle`.

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

Then open http://127.0.0.1:9999/. The default database password used in the examples is for evaluation only; change it via `GRANTFORGE_DB_PASSWORD` before real use. The MySQL example requires putting `mysql-connector-j-<版本>.jar` into `deploy/compose/drivers/` first.

## Helm

`deploy/helm/grantforge` is a Helm chart: a StatefulSet plus an external database, where each replica gets its own ID node number based on the Pod ordinal (Kubernetes 1.28 or later required).

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

Common values:

| Value | Default | Description |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / the chart's appVersion | Image |
| `replicaCount` | `1` | Number of replicas; can be greater than 1 |
| `database.url` / `username` / `password` | — | Database connection; the password should go in `existingSecret` |
| `setupToken` | empty | Pre-set the setup token; when empty it is printed to the log |
| `encryptionKey` | empty | 32-byte Base64 key used to encrypt stored secrets (identity source passwords, authenticator secrets, signing private keys, etc.); when empty it is generated automatically and stored in the database |
| `cookieSecure` | `false` | Set to `true` when TLS terminates at the ingress; session cookies are always marked Secure |
| `ingress.*` | disabled | Exposes the console and API |
| `plugins.persistence.enabled` | `false` | Mount a persistent volume for the plugins directory |
| `podDisruptionBudget.enabled` | `false` | Recommended when running multiple replicas |

> [!IMPORTANT]
> Always set `encryptionKey` in production. When it is not set, the key is stored in the database, so anyone who obtains a database backup can decrypt the secrets encrypted within it.
