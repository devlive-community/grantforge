---
title: Configuration Reference
description: All configuration keys, their defaults, and the corresponding environment variables.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Configuration can be written in `configure/application.properties` or overridden with environment variables. Spring Boot's relaxed binding rules apply: `grantforge.security.mfa.step-up-window` can be expressed as the environment variable `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`. Durations are written in forms like `30m`, `12h`, and `90d`.

## Service and Database

| Key | Environment variable | Default | Description |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | HTTP port |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | Built-in H2 file database | JDBC URL, see [Databases](/en/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | Database user |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | empty | Database password |
| — | `GRANTFORGE_HOME` | Installation directory | Directory holding the H2 data and logs |
| — | `GRANTFORGE_ID_NODE` | auto | Node number unique per instance in a cluster (0–1023) |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | Size limit for CSV import files |

## Setup and Registration

| Key | Default | Description |
| --- | --- | --- |
| `grantforge.setup.token` | empty | Fixed setup token (`GRANTFORGE_SETUP_TOKEN`); when empty, one is generated randomly and printed to the log |
| `grantforge.security.registration-enabled` | `false` | Whether visitors can self-register |
| `grantforge.security.registration-tenant` | `default` | Tenant that self-registered accounts belong to |

## Passwords and Lockout

| Key | Default | Description |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | Minimum length, at least 8 |
| `grantforge.security.password.max-length` | `128` | Maximum length, at most 1024 |
| `grantforge.security.password.required-character-classes` | `1` | Number of character classes that must be mixed (lowercase, uppercase, digits, other), 1–4 |
| `grantforge.security.password.history-size` | `0` | New password must not repeat the last N passwords, 0–24 |
| `grantforge.security.password.max-age` | no expiry | Password lifetime; after expiry the password must be changed at login |
| `grantforge.security.lockout.max-attempts` | `5` | Number of consecutive failures before lockout |
| `grantforge.security.lockout.duration` | `15m` | Lockout duration |

Passwords must not contain the username.

## Sessions and Cookies

| Key | Default | Description |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | Session idle timeout (`GRANTFORGE_SESSION_TIMEOUT`) |
| `grantforge.security.sessions.max-per-account` | `0` | Maximum concurrent sessions per account; 0 means unlimited |
| `grantforge.security.sessions.activity-interval` | `1m` | Interval at which a session's latest activity is recorded |
| `grantforge.security.cookie-secure` | `false` | Set to `true` when TLS terminates at the proxy (`GRANTFORGE_COOKIE_SECURE`) |

## Two-Step Verification

| Key | Default | Description |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | How long one two-step verification covers sensitive operations, from 1 minute to 12 hours |
| `grantforge.security.mfa.required-for-sensitive` | `false` | Whether sensitive operations require the account to have two-step verification enabled |

## Encryption and the Authorization Server

| Key | Default | Description |
| --- | --- | --- |
| `grantforge.security.encryption-key` | generated automatically | 32-byte Base64 key used to encrypt stored secrets; always set it in production |
| `grantforge.oauth.issuer` | request URL | OIDC issuer, for example `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | Signing key auto-rotation period; 0 disables it |
| `grantforge.oauth.signing-key-retention` | `2d` | How long old keys remain published; must be longer than the lifetime of any token |

## Audit, Plugins, and Agents

| Key | Default | Description |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | How long audit logs are kept |
| `grantforge.audit.archive-directory` | empty | Directory where expired audit records are archived before deletion |
| `grantforge.access-audit.retention` | `90d` | How long access audits reported by agents are kept |
| `grantforge.plugins.directory` | `plugins` | Plugins directory |
| `grantforge.plugins.call-timeout` | `10s` | Timeout for plugin calls (connection tests, resource lookups) |
| `grantforge.agents.refresh-interval` | `30s` | Interval at which agents are advised to pull policies |

## Observability

| Key | Default | Description |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | Whether `/actuator/prometheus` is accessible without login (`GRANTFORGE_PROMETHEUS_PUBLIC`) |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Set to `ecs` or `logstash` to emit JSON logs |
