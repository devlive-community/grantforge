---
title: PostgreSQL
description: Install and configure the PostgreSQL service plugin, reach one server or several, sign in with a password or a TLS client certificate, look up databases, schemas, tables and columns, and manage who may read, write and create what, down to columns and rows.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The PostgreSQL plugin looks up databases, schemas, tables and columns, and manages access policies in PostgreSQL's own terms: reading, inserting and updating columns, deleting, truncating and triggers of tables, creating objects in a schema, and creating schemas and temporary tables in a database, with row filters of tables. Its plugin ID and service type are both `postgresql`. The service plugin runs in the GrantForge server; the [PostgreSQL agent](/en/external/postgresql-agent/), a service beside the servers, enforces the policies with PostgreSQL's own privileges and row security policies.

The repository's Testcontainers integration tests cover PostgreSQL `15`, `16`, `17` and `18`, the releases PostgreSQL supports; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/postgresql`. Confirm that `postgresql` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the servers through PostgreSQL's own JDBC driver, in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select PostgreSQL (`postgresql`).
2. Enter a server as `host:port`, or several servers.
3. Choose how the lookup user signs in, enter its user name and credentials, test the connection and save the service. The test signs in and describes the server: its PostgreSQL release, the user it signed in as and how many databases the server has.

The lookup user lets GrantForge discover resources; it is separate from the users who read and write. A successful connection test does not mean access is already subject to policies.

| Setting | Purpose |
| --- | --- |
| `postgresql.hosts` | Required: a server as `host:port`, such as `postgres1.example.com:5432`; several separated by commas, tried in turn when one cannot be reached. The port defaults to `5432` |
| `postgresql.database` | The database the lookup user signs in to first, to list the databases; default `postgres` |
| `authentication` | `password` (default) or `certificate`: a TLS client certificate that PostgreSQL's `cert` authentication maps to the user |
| `username` | Required: the lookup user |
| `password` | password: the lookup user's password; stored encrypted |
| `ssl.mode` | TLS as libpq's `sslmode` names it: `disable`, `allow`, `prefer` (default), `require`, `verify-ca` or `verify-full`, which also checks the server's name |
| `ssl.root.certificate` | verify-ca and verify-full: the PEM CA certificate the servers are checked against, on the **GrantForge server**; empty uses the JVM's |
| `ssl.client.certificate`, `ssl.client.key` | certificate: the PEM client certificate and its PKCS #8 private key, PEM or DER, on the GrantForge server; certificate sign-in needs `require` or a verify mode, and a verify mode with it needs `ssl.root.certificate` |
| `timeout` | How long connecting and a query may take, default `30000`, range `1..600000` milliseconds |

The settings of one sign-in are refused with another, so a service says exactly how it signs in. PostgreSQL's driver refuses a private key file that others than its owner may read. A refused sign-in, a missing database, a server that cannot be reached or whose certificate cannot be verified are reported as such.

### What the lookup user may see

PostgreSQL's catalogs show every user the names of the databases, and in each database it may connect to the names of the schemas, tables and columns, without reading data. The lookup user needs only `CONNECT` to the databases policies should be written for, which PUBLIC holds unless it was revoked. A lookup of schemas, tables or columns signs in to each database the policy names, up to 20 of them, and skips the databases the lookup user may not connect to. PostgreSQL's own schemas, those starting with `pg_` and `information_schema`, are left out: policies do not decide them.

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `database` | Wildcard, case-sensitive; the database's name | `create_schema`, `temporary` |
| `schema` | Wildcard, case-sensitive; below a database | `create` |
| `table` | Wildcard, case-sensitive; tables, partitioned tables, views, materialized views and foreign tables below a schema | `delete`, `truncate`, `trigger` |
| `column` | Wildcard, case-sensitive; below a table | `select`, `insert`, `update` |

| Access type | PostgreSQL's privileges |
| --- | --- |
| `select`, `insert`, `update` | `SELECT`, `INSERT`, `UPDATE`, on columns or on the table as a whole |
| `delete`, `truncate`, `trigger` | `DELETE`, `TRUNCATE`, `TRIGGER` on the table |
| `create` | `CREATE` on the schema: tables, views, sequences, functions and other objects in it |
| `create_schema` | `CREATE` on the database: schemas in it |
| `temporary` | `TEMPORARY` on the database |

Reading, inserting and updating are decided by column, as PostgreSQL grants them: a policy for `select`, `insert` or `update` names columns, `*` for all of them. So a policy can allow a table and deny one column of it, and the user may read every other column. In policy values, `*` matches any run of characters and `?` one. The policy editor suggests the databases, the schemas of the databases a policy names, their tables and columns.

Reaching what a policy allows needs more of PostgreSQL: `CONNECT` to the database, `USAGE` of the schema and, to insert into a serial column, `USAGE` of its sequence. They are no access types of their own: the agent grants them with what needs them. PostgreSQL has no column masking, so this service type offers none.

### Row filters

A row filtering policy on a table shows its users the rows a condition holds for. The condition is a PostgreSQL expression, as a row security policy's `USING` takes it, such as `region = 'EU'` or `owner = current_user`. A user sees the filter of the first policy that applies to them, as with the other services. PostgreSQL filters the rows of tables and partitioned tables only: a view or the like whose rows a policy filters for a user is not readable for that user at all.

## Policy example

Suppose the server has databases `sales` and `hr`, and `sales` has the schema `crm`. Create these enabled policies in the same data service:

| Resources | Rule |
| --- | --- |
| `database=sales`, `schema=crm`, `table=*`, `column=*` | Allow `select` to group `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=card` | Deny `select` to group `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=*` | Allow `insert` to user `ingest` |
| `database=sales`, `schema=team_*` | Allow `create` to group `data-team` |
| `database=sales`, `schema=team_*`, `table=*` | Allow `delete` and `truncate` to group `data-team` |
| `database=sales`, `schema=team_*`, `table=*`, `column=*` | Allow `select`, `insert` and `update` to group `data-team` |
| `database=sales` | Allow `create_schema` to group `data-team` |
| `database=sales`, `schema=crm`, `table=orders` (row filter) | Show user `emea` the rows `region = 'EMEA'` |

Once the agent applies the new snapshot, members of `analysts` read every table of `crm` but the card numbers of `orders`; `ingest` inserts into `orders`, its serial ids included, and reads nothing; members of `data-team` create schemas, and in those whose name starts with `team_` create, read, change and empty tables; and `emea`, if a policy lets it read `orders`, sees only its own region's rows.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the PostgreSQL agent, one per server or primary. Deployment and checks are in the [PostgreSQL agent](/en/external/postgresql-agent/).

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [PostgreSQL agent](/en/external/postgresql-agent/)
- [Plugins and service types](/en/develop/plugins/)
