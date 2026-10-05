<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge logo" />

# GrantForge

Unified permission platform · users, roles, menus, APIs, data rows and fields · external data systems

Language: English · [中文说明](README.zh-CN.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.0.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge (formerly AuthX) is an open-source (MIT) unified permission platform. It answers two questions in one place: **who can do what** (functional authorization) and **who can see which data** (data and field authorization). Permissions are defined, explained and audited in the console, applications integrate through standard protocols, and external data systems such as HDFS are brought into the same policy model through plug-ins and agents.

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="GrantForge console" />
</p>

## Features

| Area | What it does |
| --- | --- |
| Identity & organization | Multi-tenancy, department tree, user groups and positions; CSV bulk import/export; LDAP / Active Directory sign-in and sync, OIDC federation |
| Account security | Session management and forced sign-out, password policy with lockout, TOTP two-factor authentication with recovery codes, step-up verification for sensitive operations |
| Functional authorization | Resource catalog (modules, menus, pages, tabs, buttons, APIs), role inheritance, grant matrix, impact analysis before granting |
| Data permissions | Row-level conditions (self, own department and descendants, specified departments, custom conditions), with read and write controlled separately |
| Field permissions | Fields can be hidden, masked (email, phone number, ID number) or read-only |
| Explainability & audit | Permission explanation (where every grant comes from), grant simulation, audit log query and export |
| Governance | Separation of duty constraints, access requests with approval, periodic access reviews |
| Application integration | OAuth 2.1 / OIDC authorization server, permission open API, Java (Spring Boot starter) and JavaScript SDK |
| External systems | Plug-in service types and a policy engine: data services, access policies, agents and access auditing |
| Delivery | One executable release, Docker image, Compose examples, Helm chart; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

External-system support today ships the plug-in framework, the generic policy editor, signed policy distribution, access auditing, an HDFS service type and a Hadoop 3.5.0 NameNode agent; the Hive plug-in and agents for other Hadoop versions are still in progress.

## How it works: two planes

- **Management plane**: the GrantForge server (Spring Boot 4.1, Java 17 bytecode) and the Vue 3 console own tenants, accounts, organization, roles, grants, auditing, data services and policies.
- **Data plane**: agents embedded in the system being protected. An agent pulls Ed25519-signed policy snapshots with its token and caches them locally, decides every access before it happens (denying when no policy is available), and reports access events back for auditing.

Your own systems do not have to follow the HDFS pattern. Regular applications evaluate permissions in-process through the open API or the Spring Boot starter; only systems that must intercept access inside a database, file system or similar store need an agent written against `core/grantforge-agent-core`.

## Integrating your application

- **OAuth 2.1 / OpenID Connect**: GrantForge is an authorization server, so applications sign users in with it; existing identity sources (LDAP / AD / OIDC) can also be connected.
- **Java applications**: `sdk/grantforge-spring-boot-starter` adds `@RequirePermission` for endpoints, `@GrantForgeEntity` for data entities, and `GrantForgeDataScopes.scope(...)` to turn platform data permissions into JPA `Specification`s.
- **Front-end applications**: `@grantforge/client` signs users in with OIDC + PKCE from your own origin and queries their permissions.
- **Open API**: `/api/v1/open/me/authorization`, `/api/v1/open/me/data-access`, `/api/v1/open/catalog/data-entities`.
- **Runnable examples**: `samples/shop` and `samples/notes` integrate the way a third party would.

## Quick start

Java 17 or later is required. The service listens on port `9999` and prints a one-time **setup token** on first start; open <http://127.0.0.1:9999/> in a browser, enter the token and create the first administrator.

```bash
# From the release (or build it from source with ./mvnw clean package, output in dist/)
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# Or with Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# Or with Compose against a database
docker compose -f deploy/compose/postgres.yml up -d

# Or on Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

The embedded H2 file database is the default, so no configuration is needed to start. The MySQL driver is not distributed with the release because of its GPL license; put it into `drivers/`. Installation, first-run setup and your first grant are described in the [documentation](https://grantforge.devlive.org).

## Databases

The default is an embedded H2 file database (`${GRANTFORGE_HOME}/data`), so no configuration is needed to start. Production deployments switch through environment variables; the schema is managed by Liquibase:

| Database | Versions (verified in CI) | `GRANTFORGE_DB_URL` example |
| --- | --- | --- |
| PostgreSQL | 14, 17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0, 8.4 | `jdbc:mysql://host:3306/grantforge` (add `mysql-connector-j` to `lib/`; its GPL license keeps it out of the release) |
| MariaDB | 10.11, 11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

Also set `GRANTFORGE_DB_USER` and `GRANTFORGE_DB_PASSWORD`; every instance in a cluster must set its own `GRANTFORGE_ID_NODE` (0-1023).

## Project layout

Maven root coordinate: `org.devlive.grantforge:grantforge:2026.0.0`. Java package prefix: `org.devlive.grantforge`. Main class: `org.devlive.grantforge.server.GrantForge`.

| Module | Responsibility |
| --- | --- |
| `core/grantforge-server` | Spring Boot entry point: REST API, security configuration, open API, and it serves the web console |
| `core/grantforge-web` | Vue 3 / TypeScript / Tailwind CSS console |
| `core/grantforge-common` | Error codes and problem details, CSV, endpoint access annotations |
| `core/grantforge-persistence` | Entities, tenant filtering, TSID, Liquibase, data and field permission SPI |
| `core/grantforge-audit` | Audit event recording, querying, retention and archiving |
| `core/grantforge-identity` | Tenants, accounts, departments, groups, positions, sign-in and sessions, two-factor authentication, identity sources |
| `core/grantforge-authz` | Resource catalog, roles, grants, assignments and evaluation, data and field policies, separation of duty, requests and reviews |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | Service type plug-in contract plus loading, isolation and invocation of plug-ins |
| `core/grantforge-policy-engine` | Policy evaluation engine for external systems (Java 8 API, embeddable in agents) |
| `core/grantforge-agent-core` | Shared agent code: settings, signed snapshots, access decisions, audit shipping |
| `core/grantforge-service` | Data services, policy snapshot signing and distribution, agents and access auditing |
| `core/grantforge-oauth` | OAuth 2.1 / OIDC server built on Spring Authorization Server |
| `plugins/grantforge-plugin-hdfs` | HDFS service type plug-in: policy management and resource lookup |
| `plugins/grantforge-agent-hdfs` | Hadoop 3.5.0 NameNode agent: overlay authorization and access auditing |
| `plugins/grantforge-plugin-example` | Example plug-in for a custom service type |
| `sdk/grantforge-spring-boot-starter`, `sdk/grantforge-js` | Java and JavaScript SDKs for integrating applications |
| `script/ci`, `deploy/` | CI check scripts (the same ones locally and in CI) and deployment resources (Dockerfile, Compose, Helm) |

## Operations and observability

- Health probes: `/actuator/health/liveness`, `/actuator/health/readiness` (status only, no details; readiness returns 200 once the database is reachable and migrations have run).
- Metrics: `/actuator/prometheus` (labelled `application="grantforge"`, login required by default; open it to trusted networks with `GRANTFORGE_PROMETHEUS_PUBLIC=true`).
- Logging: readable text with a request ID per line by default; set `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (or `logstash`) for JSON logs.
- Release scripts: `bin/startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` and `import-legacy.sh`.

## Development and verification

Builds need JDK 17 or later (Java 17 bytecode; the policy engine targets Java 8); Error Prone + NullAway enable themselves on JDK 21+. The front end uses Vue 3.5, Tailwind CSS 4, Node.js 22.12+ and pnpm 8.10.2.

```sh
# Java build and unit tests (skipping the console build)
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# Persistence integration tests on a given database (needs Docker, except h2)
bash script/ci/db_integration.sh postgres:17

# Package the release (including the console build) into dist/
./mvnw clean package

# Front-end development and checks
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# Sample applications and SDKs
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# API contract: regenerate openapi.json and the front-end types after a server change (CI checks both)
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# Documentation site (docs/, Next.js + Tailwind CSS)
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # regenerate the screenshots with a real service and sample data

# Repository checks (the same ones CI runs)
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## Links

- [Repository](https://github.com/devlive-community/grantforge)
- [Documentation](https://grantforge.devlive.org): quick start, user guide, integration and technical references, sources in [`docs/`](docs/)
- [Contributing](CONTRIBUTING.md) · [Code of conduct](CODE_OF_CONDUCT.md) · [Changelog](CHANGELOG)
