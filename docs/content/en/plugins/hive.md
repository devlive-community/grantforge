---
title: Apache Hive
description: Install and configure the Hive service plugin, sign in with a password or Kerberos, look up databases, tables, columns and UDFs, and manage access, masking and row filtering policies.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Hive plugin provides lookup of databases, tables, columns and permanent UDFs, and access, masking and row filtering policies for HiveServer2, modeled on Apache Ranger's Hive service. Its plugin ID and service type are both `hive`. The service plugin runs in the GrantForge server; the [Hive agent](/en/external/hive-agent/), installed in HiveServer2, enforces the policies.

The repository's Testcontainers integration tests cover HiveServer2 `3.1.3`, `4.0.1` and `4.1.0`, including a Kerberos sign-in against `4.0.1`; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/hive`. Confirm that `hive` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to HiveServer2 through Hive's own JDBC driver (`hive-jdbc` 4.0.1, standalone) in a class loader of its own, so it works with HiveServer2 3.1 and 4.x alike.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Apache Hive (`hive`).
2. Enter the HiveServer2 JDBC URL, such as `jdbc:hive2://hiveserver2.example.com:10000/default`. ZooKeeper discovery works too: `jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`.
3. Choose how the lookup user signs in, enter it, test the connection and save the service. The test signs in and lists the databases, as lookups do.

The lookup user lets GrantForge discover resources; it is separate from the users that run queries. A successful connection test does not mean queries are already subject to policies.

| Setting | Purpose |
| --- | --- |
| `jdbc.url` | Required HiveServer2 URL `jdbc:hive2://hosts/database;session variables`, with TLS (`;ssl=true;sslTrustStore=…`), the HTTP transport or, for Kerberos, the server's principal (`;principal=hive/_HOST@EXAMPLE.COM`) |
| `authentication` | `password` (default): a user with its password, for LDAP, PAM or no authentication; `kerberos`: a principal with a keytab or password |
| `username` | Required lookup user; with Kerberos the full principal, such as `grantforge@EXAMPLE.COM` |
| `password` | The user's password, or the principal's Kerberos password unless a keytab is given; stored encrypted |
| `keytab` | Kerberos: path of the principal's keytab, readable on the **GrantForge server** |
| `kerberos.kdc` | Kerberos: KDCs of the principal's realm, `host[:port]` separated by commas; empty uses the server's `krb5.conf` |
| `read.timeout.ms` | Read timeout, default `30000`, range `1..600000` |

The URL may not carry credentials or anything that reads files or tokens on the GrantForge server: `user`, `password`, `initFile`, `kerberosAuthType`, `delegationToken`, `storePasswordPath`, `jwt` and the `browser…` variables are refused, whatever their case. `auth` may only be `noSasl`. A Kerberos service needs `principal` in the URL; a password service may not have one.

### Kerberos

With `kerberos`, GrantForge signs the principal in itself, with the keytab or the password, and opens the connection as that principal; nothing else on the GrantForge server needs Kerberos settings. Realms whose KDCs are entered in `kerberos.kdc` are declared for the whole GrantForge server, the same way the HDFS plugin does, so several Hive and HDFS services can use different realms side by side.

## Resources and access types

| Level | Matches | Notes |
| --- | --- | --- |
| `database` | Wildcards, without case | Policies may end here |
| `table` | Wildcards, without case | Below a database; policies may end here |
| `column` | Wildcards, without case | Below a table |
| `udf` | Wildcards, without case | Permanent functions, below a database |
| `url` | Paths, case-sensitive, recursive | Data that statements read or write, such as `hdfs://nn:8020/landing`; access types `read` and `write` |
| `hiveservice` | Wildcards | HiveServer2 itself; access type `serviceadmin` |
| `global` | Wildcards | Temporary functions and added files; access type `tempudfadmin` |

The access types are Hive's: `select`, `update`, `create`, `drop`, `alter`, `index`, `lock`, `read`, `write`, `repladmin`, `serviceadmin`, `tempudfadmin`, `refresh` and `all`. As in Ranger, `all` implies every type except `tempudfadmin`, which lets a user run code of their choosing inside HiveServer2.

In policy values, `*` matches any number of characters and `?` matches one character. The policy editor suggests names by prefix: databases, tables and UDFs within the databases a policy names, and columns within its tables.

## Masking

Masking policies apply to columns. Each mask type is one of Hive's masking functions, in which `{col}` stands for the column:

| Mask type | Shows |
| --- | --- |
| `mask` | Upper-case letters as `X`, lower-case letters as `x` and digits as `n` |
| `mask_show_last_4` | Only the last four characters |
| `mask_show_first_4` | Only the first four characters |
| `mask_hash` | A SHA-256 hash |
| `mask_null` | `NULL` |
| `mask_none` | The value unchanged |
| `mask_date_show_year` | January 1 of the date's year |
| `custom` | A Hive expression of your own, using `{col}` for the column, such as `concat('****', substr({col}, -4))` |

For example, `mask_show_last_4` displays `4111111111111111` as `xxxxxxxxxxxx1111`.

## Row filters

Row filtering policies apply to tables. A filter is a Hive condition on the table's columns, such as `region = 'EU'`: queries see only the rows that meet it.

Masks and row filters do not grant access. A separate access policy must allow the query.

## Policy example

Suppose Hive has a `sales.orders` table with `id`, `region` and `card` columns. Create three enabled policies in the same data service for the user `alice`:

| Policy kind | Resources | Rule |
| --- | --- | --- |
| Access | `database=sales`, `table=orders`, `column=*` | Allow `select` for user `alice` |
| Masking | `database=sales`, `table=orders`, `column=card` | Use `mask_show_last_4` for user `alice` |
| Row filter | `database=sales`, `table=orders` | Use `region = 'EU'` for user `alice` |

Once the agent applies the new snapshot, `alice` sees only EU rows of `sales.orders`, with all but the last four characters of each card masked, `SHOW DATABASES` lists `sales` for her, and other databases refuse her.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them, including masks and row filters, needs the Hive agent in every HiveServer2. See the [Hive agent](/en/external/hive-agent/) for its deployment and what it checks.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Hive agent](/en/external/hive-agent/)
- [Plugin and service type development](/en/develop/plugins/)
