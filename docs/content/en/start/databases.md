---
title: Databases
description: Supported databases and versions, connection settings, drivers, and database-specific notes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Supported Databases

| Database | Verified versions | Driver |
| --- | --- | --- |
| H2 | Bundled with each release | Built in; evaluation only |
| PostgreSQL | 14, 17 | Built in |
| MySQL | 8.0, 8.4 | Add to `drivers/` yourself (Connector/J is GPL licensed and not distributed with the package) |
| MariaDB | 10.11, 11.4 | Built in |
| Oracle | Free 23 | Built in |
| SQL Server | 2022 | Built in |

Every release runs "fresh-database initialization plus the full integration test suite" in CI. Chinese domestic databases (Dameng, KingbaseES, openGauss, OceanBase, etc.) are outside the supported scope.

## Connection Examples

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle（服务名）
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

The username and password are set with `GRANTFORGE_DB_USER` and `GRANTFORGE_DB_PASSWORD` respectively. The database must exist beforehand, and the account needs permission to create tables: on first start GrantForge creates all tables with Liquibase, and later version upgrades are migrated automatically by Liquibase as well. Hibernate only validates the schema and never modifies it.

On PostgreSQL, GrantForge tries to enable the `pg_trgm` extension and builds trigram indexes on users' login name, display name, and email, keeping "contains" searches over millions of accounts in the tens of milliseconds. From PostgreSQL 13 it is a trusted extension that the database owner can enable; if the account lacks that permission, the service still starts and searches fall back to full table scans — once an administrator runs `CREATE EXTENSION pg_trgm`, the indexes are built automatically on the next start.

## Character Sets

- **MySQL / MariaDB**: create the database with the `utf8mb4` character set so that Chinese text and emoji are stored in full.
- **SQL Server, Oracle**: text columns that may contain Chinese use `NVARCHAR`; long text is `NVARCHAR(MAX)` on SQL Server and `CLOB` on Oracle, regardless of the database's default character set.
- **Oracle**: empty strings are treated as `NULL`; GrantForge normalizes blank values to "not filled in" at the domain layer, keeping behavior consistent with the other databases.

## Backup and Recovery

All business data lives in the database (sessions included), so backing up the database is enough; if you use plugins, back up `plugins/` as well. When `grantforge.security.encryption-key` is not set, the encryption key is stored in the database too, so restoring the backup restores decryption; if you set the key, you must keep that key safe as well.
