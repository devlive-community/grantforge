---
title: Trino
description: Install and configure the Trino service plugin, sign in with a password, a JSON web token or Kerberos over TLS, look up catalogs, schemas, tables, columns and session properties, and manage access, row filtering and masking policies in Trino's terms.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Trino plugin looks up catalogs, schemas, tables, columns and session properties, and manages access, row filtering and masking policies on what Trino checks: data, routines, session properties, users and the system information. Its plugin ID and service type are both `trino`. The service plugin runs in the GrantForge server; the [Trino agent](/en/external/trino-agent/), installed in the coordinator, enforces the policies.

The repository's Testcontainers integration tests cover Trino `470`, `476`, `479` and `484`; HTTPS with a password file and JSON web tokens, and Kerberos, on `470` and `484`. This does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/trino`. Confirm that `trino` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the coordinator through Trino's own JDBC driver (`trino-jdbc` 484), in a class loader of its own, which speaks to every Trino release above.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Trino (`trino`).
2. Enter the JDBC URL of the coordinator, such as `jdbc:trino://trino.example.com:8443`.
3. Choose how the lookup user signs in, enter it, test the connection and save the service. The test signs in and describes the coordinator: its release and how many catalogs the user sees.

The settings take the driver's own names, so they read as Trino's JDBC documentation does. The lookup user lets GrantForge discover resources; it is separate from the users who query. A successful connection test does not mean queries are already subject to policies.

| Setting | Purpose |
| --- | --- |
| `jdbc.url` | Required: `jdbc:trino://host:port`, optionally with a catalog and schema and with driver properties other than credentials, such as `?SSL=true` |
| `authentication` | `none` (default): the user name alone; `password`; `jwt`: a JSON web token; `kerberos`: a principal with a keytab or password |
| `user` | Required lookup user; with Kerberos the full principal, such as `grantforge@EXAMPLE.COM` |
| `password` | The user's password, or with Kerberos the principal's password unless a keytab is given; stored encrypted |
| `accessToken` | `jwt`: the token the coordinator accepts; stored encrypted |
| `keytab` | Kerberos: path of the principal's keytab, readable on the **GrantForge server** |
| `kerberos.kdc` | Kerberos: KDCs of the principal's realm, `host[:port]` separated by commas; empty uses the server's `krb5.conf` |
| `KerberosRemoteServiceName` | Kerberos: the first part of the coordinator's principal, default `trino` |
| `KerberosUseCanonicalHostname` | Kerberos: whether the coordinator's principal takes the canonical name of the URL's host, default `true`; turn it off behind a load balancer or alias whose name the principal carries |
| `SSL` | Whether to speak TLS, default `false`; any sign-in but `none` needs it, as Trino signs users in over TLS only |
| `SSLTrustStorePath`, `SSLTrustStorePassword`, `SSLTrustStoreType` | TLS: the truststore that verifies the coordinator, its password and type (`JKS` or `PKCS12`); empty trusts what Java trusts |
| `SSLVerification` | TLS: `FULL` (default) checks the certificate and host name, `CA` the certificate alone, `NONE` nothing and takes no truststore |
| `timeout` | Request timeout in milliseconds, default `30000`, range `1..600000` |

The service is exactly what its fields say. The URL may not carry credentials or what would sign in another way: `user`, `password`, `accessToken`, `sessionUser`, `extraCredentials`, external and OAuth 2.0 authentication, Kerberos files and delegation, and keystore and truststore passwords are refused there. Fields of another sign-in are refused too, such as a token with password authentication.

A coordinator whose certificate cannot be verified is not spoken to: the test reports it as unreachable. A refused sign-in, missing permissions and an unreachable coordinator are reported as such.

### Kerberos

With `kerberos`, GrantForge signs the principal in, from the keytab or with the password, and hands the driver the credential of that sign-in; nothing else on the GrantForge server needs Kerberos settings. Realms whose KDCs are entered in `kerberos.kdc` are declared for the whole GrantForge server, the same way the HDFS, Hive, Kafka and HBase plugins do, so several services can use different realms side by side. The session user is the principal's name before the realm, as Trino maps principals by default.

### What the lookup user sees

Once the [Trino agent](/en/external/trino-agent/) enforces the policies, listings leave out what the lookup user may not use: give it a policy that allows `select` on catalog `*`, or on the catalogs whose resources the policy editor should suggest.

## Resources and access types

| Level | Matches | Access types |
| --- | --- | --- |
| `catalog` | Wildcards, without case | `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `all` |
| `schema` | Wildcards, without case; below a catalog | As `catalog` |
| `table` | Wildcards, without case; below a schema; tables, views and materialized views | As `catalog` |
| `column` | Wildcards, without case; below a table | `select`, `update` |
| `function` | Wildcards, without case; below a schema | `execute`, `create_function`, `drop_function` |
| `procedure` | Wildcards, without case; below a schema | `execute` |
| `session_property` | Wildcards, without case; below a catalog, named without it | `set` |
| `system_session_property` | Wildcards, without case | `set` |
| `user` | Wildcards, with case | `impersonate`, `view_query`, `kill_query` |
| `system_information` | Its one value, `*` | `read`, `write` |

`all` implies `select`, `insert`, `update`, `delete`, `create`, `drop` and `alter`.

A permission on a catalog, schema or table covers everything within it: `select` on schema `sales` reads every table of it, `create` on a catalog creates schemas and tables in it. A check is decided at each scope it names, and a denial at any of them refuses it, so a policy can allow a schema and deny one column of one table. Functions, procedures and session properties are granted by naming them, with `*` for all of a schema or catalog, and functions have access types of their own, so a grant on routines never reads as one on tables.

In policy values, `*` matches any number of characters and `?` matches one. The policy editor suggests catalogs, schemas within the catalogs a policy names, tables within its schemas, columns within its tables, and session properties of its catalogs and of the system; functions, procedures and users are not suggested.

## Row filters and masks

Row filtering policies on a table, and masking policies on a column, are Trino SQL, which the coordinator applies to every query that reads them. A row filter is a condition on the table's columns, such as `region = 'EU'`; names in it resolve in the table's catalog and schema.

| Mask type | Shows |
| --- | --- |
| `mask` | Letters as `X` or `x`, digits as `n` |
| `mask_show_last_4`, `mask_show_first_4` | Only the last or the first four characters |
| `mask_hash` | The SHA-256 of the value, in hexadecimal |
| `mask_null` | `NULL` |
| `mask_none` | The value, unmasked |
| `mask_date_show_year` | Only the year of a date or timestamp |
| `custom` | A Trino SQL expression of the policy's, with `{col}` for the column |

The agent fits every mask to the column's type, so that a mask never breaks the query that reads the column: see the [Trino agent](/en/external/trino-agent/).

## Policy example

Suppose the catalog `hive` has a schema `sales` with tables `orders`, whose columns include `region`, `customer` and `card`, and `returns`. Create these enabled policies in the same data service:

| Policy | Resources | Rule |
| --- | --- | --- |
| Access | `catalog=hive`, `schema=sales` | Allow `select` for group `analysts` |
| Access | `catalog=hive`, `schema=sales`, `table=orders`, `column=card` | Deny `select` for group `analysts` |
| Access | `catalog=hive`, `schema=sales`, `table=returns` | Allow `insert` for user `alice` |
| Row filter | `catalog=hive`, `schema=sales`, `table=orders` | `region = 'EU'` for user `alice` |
| Masking | `catalog=hive`, `schema=sales`, `table=orders`, `column=customer` | `mask_hash` for group `analysts` |
| Access | `catalog=system`, `schema=runtime` | Allow `select` for user `alice` |
| Access | `user=bob` | Allow `view_query` for user `alice` |

Once the agent applies the new snapshot, members of `analysts` read every table of `sales` but `orders.card`, and see `customer` hashed; `alice` sees only the European orders, writes to `returns`, and sees Bob's queries in `system.runtime.queries` beside her own.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the Trino agent in the coordinator. See the [Trino agent](/en/external/trino-agent/) for its deployment and what it checks.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Trino agent](/en/external/trino-agent/)
- [Plugin and service type development](/en/develop/plugins/)
