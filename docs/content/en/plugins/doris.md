---
title: Apache Doris
description: Install and configure the Apache Doris service plugin, reach one frontend or several, sign in with a password, look up catalogs, databases, tables, columns and workload groups, and manage who may read, load and administer what, down to columns and rows, and who may run queries in which workload group.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Apache Doris plugin looks up catalogs, databases, tables, columns and workload groups, and manages access policies in Doris's own terms: reading columns, loading, altering, dropping and showing the views of tables, creating tables in a database and dropping it, creating databases in a catalog, and running queries in a workload group, with row filters of tables. Its plugin ID and service type are both `doris`. The service plugin runs in the GrantForge server; the [Apache Doris agent](/en/external/doris-agent/), a service beside the cluster, enforces the policies with Doris's own roles, grants and row policies.

The repository's Testcontainers integration tests cover Apache Doris `2.1`, `3.1`, `4.0` and `4.1`, each on a cluster of one frontend and one backend; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/doris`. Confirm that `doris` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the frontends over the MySQL protocol through MariaDB Connector/J, in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Apache Doris (`doris`).
2. Enter a frontend's MySQL protocol address, or several frontends.
3. Enter the lookup user's name and password, test the connection and save the service. The test signs in and describes the cluster: its Doris release, the user it signed in as, how many catalogs that user sees and how many databases of the internal catalog. It asks the frontend only, so it answers while no backend is up.

The lookup user lets GrantForge discover resources; it is separate from the users who read and load. A successful connection test does not mean access is already subject to policies.

| Setting | Purpose |
| --- | --- |
| `doris.hosts` | Required: a frontend's MySQL protocol address, such as `fe1.example.com:9030`; several separated by commas, tried in turn when one cannot be reached. The port is `9030` unless given |
| `username` | Required: the lookup user |
| `password` | The lookup user's password; stored encrypted |
| `ssl.mode` | `disable` (default), `trust` to encrypt without checking the frontend, `verify-ca` to check its certificate, or `verify-full` to check its name too; for frontends that offer TLS on the MySQL protocol |
| `ssl.root.certificate` | verify-ca and verify-full: the PEM CA certificate the frontends are checked against, on the **GrantForge server**; empty uses the JVM's |
| `timeout` | How long connecting and a statement may take, default `30000`, range `1..600000` milliseconds |

A refused sign-in, a lookup user that lacks a privilege and frontends that cannot be reached are reported as such.

### What the lookup user may see

Doris's `SHOW` statements list only the catalogs, databases, tables and columns a user holds some privilege on, so a lookup shows what the lookup user may see. Give it `SELECT_PRIV` on what policies should be written for, such as `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`; `SHOW WORKLOAD GROUPS` lists the groups it may see. Doris's own databases, `information_schema`, `mysql` and `__internal_schema`, are left out: policies do not decide them.

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `catalog` | Wildcard, case-sensitive; the internal catalog or an external one | `create_database` |
| `database` | Wildcard, case-sensitive; below a catalog | `create`, `drop_database` |
| `table` | Wildcard, case-sensitive; below a database, a table or a view | `load`, `alter`, `drop`, `show_view` |
| `column` | Wildcard, not case-sensitive, as Doris compares column names; below a table | `select` |
| `workload_group` | Wildcard, case-sensitive; the group's name | `usage` |

| Access type | Doris's privilege |
| --- | --- |
| `select` | `SELECT_PRIV`, on columns, or on a table, database or catalog as a whole |
| `load` | `LOAD_PRIV`: loading, `INSERT`, `UPDATE`, `DELETE` and `TRUNCATE` |
| `alter`, `drop` | `ALTER_PRIV`, `DROP_PRIV` on the table |
| `show_view` | `SHOW_VIEW_PRIV`: `SHOW CREATE VIEW` |
| `create` | `CREATE_PRIV` on the database: creating it, and tables and views in it |
| `drop_database` | `DROP_PRIV` on the database |
| `create_database` | `CREATE_PRIV` on the catalog |
| `usage` | `USAGE_PRIV` on the workload group |

Reading is decided by column, as Doris grants it: a policy for `select` names columns, `*` for all of them. So a policy can allow a table and deny one column of it, and the user may read every other column. In policy values, `*` matches any run of characters and `?` one. The policy editor suggests the catalogs, the databases of the catalogs a policy names, their tables and columns, and the workload groups.

Doris's privilege on a database also reaches every table in it, and on a catalog every database in it. So the agent grants `create_database` only where the policies also let the user create tables in every database of the catalog, and `drop_database` only where they also let it drop every table of the database; otherwise it withholds the privilege and says so, rather than grant more than the policies allow. `ADMIN_PRIV`, `GRANT_PRIV` and `NODE_PRIV` are no access types: no policy gives them.

Doris masks values only through Apache Ranger, so this service type offers no masking.

### Row filters

A row filtering policy on a table shows its users the rows a condition holds for. The condition is a Doris predicate, as row policies take it, such as `region = 'EU'`; the agent first runs it on the table, and a filter that cannot run there shows no rows. A user sees the filter of the first policy that applies to them, as with the other services.

## Policy example

Suppose the cluster has databases `sales` and `hr` in its internal catalog and a workload group `etl`. Create these enabled policies in the same data service:

| Resources | Rule |
| --- | --- |
| `catalog=internal`, `database=sales`, `table=*`, `column=*` | Allow `select` to group `analysts` |
| `catalog=internal`, `database=sales`, `table=orders`, `column=card` | Deny `select` to group `analysts` |
| `catalog=internal`, `database=sales`, `table=orders` | Allow `load` to user `ingest` |
| `catalog=internal`, `database=team_*` | Allow `create` to group `data-team` |
| `catalog=internal`, `database=team_*`, `table=*` | Allow `load`, `alter` and `drop` to group `data-team` |
| `workload_group=etl` | Allow `usage` to user `ingest` and group `data-team` |
| `catalog=internal`, `database=sales`, `table=orders` (row filter) | Show user `emea` the rows `region = 'EMEA'` |

Once the agent applies the new snapshot, members of `analysts` read every table of `sales` but the card numbers of `orders`; `ingest` loads `orders` and may run its loads in `etl`; members of `data-team` create tables in the `team_` databases an administrator created, and load, alter and drop them; and `emea`, if a policy lets it read `orders`, sees only its own region's rows.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the Doris agent, one per cluster. Deployment and checks are in the [Apache Doris agent](/en/external/doris-agent/).

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Apache Doris agent](/en/external/doris-agent/)
- [Plugins and service types](/en/develop/plugins/)
