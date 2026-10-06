---
title: Upgrading and Legacy Migration
description: Upgrading between 2.x versions, and migrating accounts, roles, and menus from 1.x (AuthX / GrantForge 1.x).
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Upgrading Between 2.x Versions

1. Back up the database (as well as `plugins/` and `configure/`).
2. Stop the service: `bin/shutdown.sh`.
3. Replace the old `lib/` and `bin/` with those from the new version's distribution package.
4. Start the service: `bin/startup.sh`. Liquibase runs the new version's database migrations automatically, and the readiness probe returns 200 only after the migrations finish.

When upgrading a cluster, stop all instances before starting the new version so that old and new versions never read and write at the same time. Released migrations are never modified, and every version is verified on all supported databases for "upgrade from the previous version".

## Migrating from 1.x

1.x stores its data in a different set of tables, and 2.x does not read them. The migration path is: install 2.x on a **new database** and complete initialization, stop the service, then import the old database's accounts, roles, and menus into a tenant:

```bash
# 先预演：只生成报告，不写入
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
# 确认报告后正式导入
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

Both commands write `logs/legacy-import-report.json`, listing what was (or would be) created, what was skipped and why, and the new ID corresponding to each legacy object. Put the old database's JDBC driver into `drivers/` and supply the password via `GRANTFORGE_LEGACY_SOURCE_PASSWORD` (the script prompts for it if not provided). Re-running the import only fills in what is missing.

Import rules:

- **Accounts** keep their original passwords, which are automatically re-hashed with the new algorithm on first login. Accounts that violate the 2.x username rules (3–64 letters, digits, or `._@-`), have no password, or whose username is already taken by another tenant are skipped.
- **Roles** keep their names; codes are lowercased (`GLY` → `gly`).
- **Menus** become resources of the `legacy` application: menus with `#` addresses become groupings, other addresses become pages, and menus under a page become buttons. A menu address and its HTTP method become an API resource `api:<方法>:<路径>`; addresses ending in `*` become `<路径>/**`, matched by path segments instead of character prefixes — the report lists each one for review.
- Only **explicit grants** are migrated: 1.x let anyone access addresses registered as menus, and 2.x does not.

> [!WARNING]
> Before importing, review the skipped accounts and wildcard addresses in the report before running `--apply`.
