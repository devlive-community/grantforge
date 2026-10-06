---
title: Installing the Distribution Package
description: Install, start, stop, and upgrade the GrantForge distribution package on a physical or virtual machine.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Requirements

| Item | Requirement |
| --- | --- |
| Java | 17 or later (the distribution is compiled for Java 17; 21 recommended) |
| Memory | At least 1 GB; 2 GB or more recommended for production |
| Database | Built-in H2 for evaluation; PostgreSQL, MySQL, MariaDB, Oracle, or SQL Server for production, see [Databases](/en/start/databases/) |
| Browser | The last two major versions of Chrome, Edge, Firefox, and Safari |

## Directory Layout

Extracting `grantforge-release.tar.gz` yields a `grantforge/` directory:

| Directory | Contents |
| --- | --- |
| `bin/` | `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh`, and `import-legacy.sh` |
| `configure/` | `application.properties`; override the default configuration here |
| `lib/` | Server and dependency jars |
| `drivers/` | Additional JDBC drivers (MySQL must be added manually) |
| `plugins/` | Service type plugins, see [Plugins and service types](/en/architecture/plugins/) |
| `agents/` | Agent jars to deploy on target systems, such as the [HDFS NameNode agent](/en/guide/hdfs-agent/) |
| `data/` | Built-in H2 database files (created on first start) |
| `logs/` | `grantforge.log`; `console.out` captures output produced before the logging system starts |

## Start and Stop

```bash
bin/startup.sh     # 后台启动，进程号写入 pid 文件
bin/shutdown.sh    # 按 pid 文件优雅停止
bin/restart.sh     # 停止后再启动
bin/debug.sh       # 前台运行，日志同时输出到控制台，Ctrl+C 停止
```

The scripts can be run from any directory; the installation directory is the parent of the directory containing the script, or you can set it explicitly with the `GRANTFORGE_HOME` environment variable.

## Choosing a Database

By default GrantForge uses the H2 file database under `data/grantforge`, which is fine for evaluation. For production, specify the database in `configure/application.properties` or through environment variables:

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

On the first connection, GrantForge creates all tables automatically with Liquibase; every later start applies any migrations that have not run yet.

## First-Run Setup

The first start prints a one-time setup token to the log. Open the console, enter the token, and create the first administrator; the process is described in [Get started in five minutes](/en/start/quick-start/).

## Health Checks and Monitoring

| Endpoint | Purpose |
| --- | --- |
| `/actuator/health/liveness` | Liveness probe |
| `/actuator/health/readiness` | Readiness probe: returns 200 once the database is reachable and migrations have finished |
| `/actuator/prometheus` | Prometheus metrics; requires login by default, `GRANTFORGE_PROMETHEUS_PUBLIC=true` opens it up to a trusted network |

For structured logs, set `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (or `logstash`). Every log line carries a request ID that matches the `requestId` in API error responses.

## Cluster Deployment

Multiple instances can share one database and serve traffic at the same time: sessions are stored in the database, so any instance can handle any request. Each instance needs a distinct `GRANTFORGE_ID_NODE` (0–1023), which determines the node number used when generating IDs. The load balancer does not need sticky sessions.

## Upgrading

Stop the service, replace the old `lib/` with the new version's (keeping `configure/`, `data/`, `drivers/`, `plugins/`), and start again; database migrations run automatically. Back up the database before upgrading. For upgrading from 1.x, see [Upgrading and legacy migration](/en/start/upgrade/).
