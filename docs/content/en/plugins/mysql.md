---
title: MySQL
description: Install and configure the MySQL service plugin, look up databases, tables and columns, and manage access, masking and row filtering policies.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The MySQL plugin provides lookup of databases, tables and columns, and access, masking and row filtering policies in GrantForge. Its plugin ID and service type are both `mysql`. The service plugin runs in the GrantForge server; an independent [MySQL proxy agent](/en/external/mysql-agent/) checks business SQL.

Connection checks accept MySQL 8.0 or later. The repository's Testcontainers integration tests cover `mysql:8.0` and `mysql:8.4`; this does not certify every other version. MariaDB servers and MySQL 5.7 are outside the supported scope. `jdbc:mariadb://` is a driver URL format, not a claim of MariaDB server support.

## Installation

The distribution includes the plugin in `plugins/mysql`. Confirm that `mysql` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to MySQL through MariaDB Connector/J (LGPL), which speaks MySQL's protocol, including `caching_sha2_password` and TLS, in a class loader of its own. MySQL's own Connector/J is GPL and is not shipped.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select MySQL (`mysql`).
2. Enter the JDBC URL of the server, such as `jdbc:mysql://mysql.example.com:3306`. `jdbc:mariadb://` works the same, and so do the driver's failover forms, such as `jdbc:mysql:replication://primary:3306,replica:3306`.
3. Enter the lookup user and its password, test the connection and save the service. The test signs in, checks the server version and reads `information_schema` as lookups do.

The lookup user lets GrantForge discover resources; it is separate from the users that business clients sign in as. A successful connection test does not mean business SQL is already subject to policies.

| Setting | Purpose |
| --- | --- |
| `jdbc.url` | Required JDBC URL: `jdbc:mysql://host[:port]` or `jdbc:mariadb://…`, with an optional database and `?options`; credentials do not belong in the URL |
| `username` | Required lookup user |
| `password` | The lookup user's password; stored encrypted |
| `jdbc.properties` | Driver options, one `key=value` per line, such as `sslMode=verify-full` and `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | Connection timeout, default `10000`, range `1..600000` |
| `read.timeout.ms` | Read timeout of a lookup, default `30000`, range `1..600000` |

Options that would read local files for the server, run statements on connecting, allow several statements at once, carry credentials, or load classes are refused in both the URL and `jdbc.properties`, whatever their case: `user`, `password`, `allowLocalInfile`, `allowMultiQueries`, `initSql`, `createDatabaseIfNotExist`, `socketFactory`, `credentialType` and the pool options. Lookup connections are read-only.

For TLS that verifies the server's identity, put the following in `jdbc.properties`:

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

The certificate file must be readable on the **GrantForge server**, and the hostname in the URL must match the certificate. The plugin does not require TLS by default; configure it explicitly. These are MariaDB Connector/J option names.

`information_schema` shows a user only objects on which it has privileges. For example, a database administrator can grant discovery of resources in `sales` only, replacing `grantforge-host` with the GrantForge server's source address:

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

Use `REFERENCES ON *.*` only when discovery of all databases is needed. The proxy's metadata account also needs `SHOW_ROUTINE`; see the agent guide.

## Resources and access types

| Level | Matches | Notes |
| --- | --- | --- |
| `database` | Wildcards, case-sensitive | Policies may end here |
| `table` | Wildcards, case-sensitive | Below a database; policies may end here |
| `column` | Wildcards, without case | Below a table; access types `select`, `insert` and `update` |

Databases and tables compare case-sensitively, as MySQL stores them on Linux. On a server with `lower_case_table_names` set to 1 or 2, the agent folds the names it is asked about to lower case, so write such policies in lower case. Column names always compare without case, as MySQL compares them.

The access types follow MySQL's privileges: `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `index` and `all`, which implies these eight types. `all` is not MySQL's `ALL PRIVILEGES` and does not remove the proxy's statement restrictions.

In policy values, `*` matches any number of characters and `?` matches one character. The policy editor suggests names by prefix: databases, tables within the databases a policy names, and columns within its tables. `%` and `_` in suggestion input are literal characters.

## Masking

Masking policies apply to columns. Each mask type is a MySQL 8 expression in which `{col}` stands for the column:

| Mask type | Shows |
| --- | --- |
| `mask` | ASCII letters as `X`/`x` and digits as `n`; other characters stay unchanged |
| `mask_show_last_4` | Only the last four characters |
| `mask_show_first_4` | Only the first four characters |
| `mask_hash` | An unsalted SHA-256 hash |
| `mask_null` | `NULL` |
| `mask_none` | The value unchanged |
| `mask_date_show_year` | January 1 of the date's year |
| `custom` | A MySQL expression of your own, using `{col}` for the column, such as `CONCAT('****', RIGHT({col}, 4))` |

The first-four and last-four masks preserve the whole value when it is no longer than four characters. For example, `mask_show_last_4` displays `4111111111111111` as `xxxxxxxxxxxx1111`.

## Row filters

Row filtering policies apply to tables. A filter is a MySQL condition on the table's columns, such as `region = 'EU'`: the user sees only rows that meet it, and single-table `UPDATE` and `DELETE` change only those rows. Multi-table writes that require a row filter on the target are refused; see the agent guide for other restrictions.

Masks and row filters do not grant access. A separate access policy must allow the operation.

## Policy example

Suppose MySQL has a `sales.orders` table with `id`, `region` and `card` columns. Create three enabled policies in the same data service for the MySQL login user `alice`:

| Policy kind | Resources | Rule |
| --- | --- | --- |
| Access | `database=sales`, `table=orders`, `column=*` | Allow `select` for user `alice` |
| Masking | `database=sales`, `table=orders`, `column=card` | Use `mask_show_last_4` for user `alice` |
| Row filter | `database=sales`, `table=orders` | Use `region = 'EU'` for user `alice` |

Name the policy user `alice`, not `alice@host`. MySQL itself must also let that account read the table. Once the proxy applies the new snapshot, queries through it return only EU rows for `alice`, with all but the last four characters of each card masked. Predicates and joins also see the masked values. See the [MySQL proxy agent](/en/external/mysql-agent/) for deployment and verification.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them, including masks and row filters, needs the MySQL proxy agent, which clients connect to in place of the server. See the agent guide for its deployment and what it checks.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [MySQL proxy agent](/en/external/mysql-agent/)
- [Plugin and service type development](/en/develop/plugins/)
