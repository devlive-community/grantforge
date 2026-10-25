---
title: ClickHouse
description: Install and configure the ClickHouse service plugin, reach one server or several, sign in with a password or a TLS client certificate, look up databases, tables and columns, and manage who may read, write and administer what, down to columns and rows.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The ClickHouse plugin looks up databases, tables and columns, and manages access policies in ClickHouse's own terms: reading and writing columns, altering, dropping, truncating and optimizing tables, creating tables in a database and dropping it, and the server's database creation, `KILL QUERY` and `SYSTEM` statements, with row filters of tables. Its plugin ID and service type are both `clickhouse`. The service plugin runs in the GrantForge server; the [ClickHouse agent](/en/external/clickhouse-agent/), a service beside the servers, enforces the policies with ClickHouse's own grants and row policies.

The repository's Testcontainers integration tests cover ClickHouse `24.8`, `25.3`, `25.8`, `26.3` and `26.8`, the long-term support releases of the last two years; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/clickhouse`. Confirm that `clickhouse` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the servers over HTTP through ClickHouse's own Java client, in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select ClickHouse (`clickhouse`).
2. Enter the URL of a server's HTTP interface, or of several servers.
3. Choose how the lookup user signs in, enter its user name and credentials, test the connection and save the service. The test signs in and describes the server: its ClickHouse release, the user it signed in as and how many databases that user sees.

The lookup user lets GrantForge discover resources; it is separate from the users who read and write. A successful connection test does not mean access is already subject to policies.

| Setting | Purpose |
| --- | --- |
| `clickhouse.urls` | Required: a server's HTTP interface, such as `https://clickhouse1.example.com:8443`; several separated by commas, tried in turn when one cannot be reached |
| `authentication` | `password` (default) or `certificate`: a TLS client certificate that ClickHouse maps to the user (`IDENTIFIED WITH ssl_certificate`) |
| `username` | Required: the lookup user |
| `password` | password: the lookup user's password; stored encrypted |
| `ssl.root.certificate` | https: the PEM CA certificate the servers are checked against, on the **GrantForge server**; empty uses the JVM's |
| `ssl.client.certificate`, `ssl.client.key` | certificate: the PEM client certificate and its private key, on the GrantForge server; certificate sign-in needs `https://` servers |
| `timeout` | How long a request may take, default `30000`, range `1..600000` milliseconds |

The settings of one sign-in are refused with another, so a service says exactly how it signs in. A refused sign-in, a read-only user, a server that cannot be reached or whose certificate cannot be verified, and something other than ClickHouse answering in its place, such as a proxy, are reported as such.

### What the lookup user may see

ClickHouse shows a user only the databases, tables and columns it holds some privilege on. Give the lookup user `SHOW` on what policies should be written for, such as `GRANT SHOW ON *.* TO grantforge_lookup`, which lets it see names without reading data. ClickHouse's own databases, `system` and `information_schema`, are left out: policies do not decide them.

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `database` | Wildcard, case-sensitive; the database's name | `create`, `drop_database` |
| `table` | Wildcard, case-sensitive; below a database | `alter`, `drop`, `truncate`, `optimize` |
| `column` | Wildcard, case-sensitive; below a table | `select`, `insert` |
| `server` | Its one value, `*` | `create_database`, `kill_query`, `system` |

| Access type | ClickHouse's privileges |
| --- | --- |
| `select`, `insert` | `SELECT`, `INSERT`, on columns |
| `alter` | `ALTER`: every alteration of a table, `ALTER UPDATE` and `ALTER DELETE` included |
| `drop` | `DROP TABLE`, `DROP VIEW`, `DROP DICTIONARY` |
| `truncate`, `optimize` | `TRUNCATE`, `OPTIMIZE` |
| `create` | `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` in the database |
| `drop_database` | `DROP DATABASE` |
| `create_database`, `kill_query`, `system` | `CREATE DATABASE`, `KILL QUERY`, `SYSTEM`: every `SYSTEM` statement |

Reading and writing are decided by column, as ClickHouse grants them: a policy for `select` or `insert` names columns, `*` for all of them. So a policy can allow a table and deny one column of it, and the user may read every other column. In policy values, `*` matches any run of characters and `?` one. The policy editor suggests the databases, the tables of the databases a policy names and their columns.

The access types are none of ClickHouse's privilege groups such as `ALL`: each is what it says, so a policy never gives more than it names. ClickHouse has no column masking outside ClickHouse Cloud, so this service type offers none.

### Row filters

A row filtering policy on a table shows its users the rows a condition holds for. The condition is a ClickHouse expression, as row policies take it, such as `region = 'EU'` or `owner = currentUser()`. A user sees the filter of the first policy that applies to them, as with the other services.

## Policy example

Suppose the server has databases `sales` and `hr`. Create these enabled policies in the same data service:

| Resources | Rule |
| --- | --- |
| `database=sales`, `table=*`, `column=*` | Allow `select` to group `analysts` |
| `database=sales`, `table=orders`, `column=card` | Deny `select` to group `analysts` |
| `database=sales`, `table=orders`, `column=*` | Allow `insert` to user `ingest` |
| `database=team_*` | Allow `create` to group `data-team` |
| `database=team_*`, `table=*` | Allow `alter`, `drop`, `truncate` and `optimize` to group `data-team` |
| `database=team_*`, `table=*`, `column=*` | Allow `select` and `insert` to group `data-team` |
| `server=*` | Allow `create_database` to group `data-team` |
| `database=sales`, `table=orders` (row filter) | Show user `emea` the rows `region = 'EMEA'` |

Once the agent applies the new snapshot, members of `analysts` read every table of `sales` but the card numbers of `orders`; `ingest` writes `orders` and reads nothing; members of `data-team` create databases, and tables in those whose name starts with `team_`, and read, write and change them; and `emea`, if a policy lets it read `orders`, sees only its own region's rows.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the ClickHouse agent, one per server or cluster. Deployment and checks are in the [ClickHouse agent](/en/external/clickhouse-agent/).

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [ClickHouse agent](/en/external/clickhouse-agent/)
- [Plugins and service types](/en/develop/plugins/)
