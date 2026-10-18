---
title: Apache HBase
description: Install and configure the HBase service plugin, find the cluster through ZooKeeper or bootstrap servers, sign in simply or with Kerberos, look up namespaces, tables and column families, and manage access policies at HBase's own scopes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The HBase plugin looks up namespaces, tables and column families, and manages access policies at the scopes of HBase's own permissions: the global scope, namespaces, tables, column families and columns. Its plugin ID and service type are both `hbase`. The service plugin runs in the GrantForge server; the [HBase agent](/en/external/hbase-agent/), installed in the masters and region servers, enforces the policies.

The repository's Testcontainers integration tests cover HBase `2.5.16`, `2.6.7` and `3.0.0`, built from the SHA-512-verified Apache releases, and a Kerberos cluster of `2.6.7`; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/hbase`. Confirm that `hbase` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the cluster through HBase's own client (`hbase-shaded-client-byo-hadoop` 2.6.7) on Hadoop's shaded client 3.5.0, in a class loader of its own, so it works with HBase 2.x and 3.x alike.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Apache HBase (`hbase`).
2. Enter the ZooKeeper quorum, or the bootstrap servers of an HBase 2.5 or later cluster.
3. Choose how the lookup user signs in, enter it, test the connection and save the service. The test signs in and describes the cluster: its ID, its release and how many region servers are live.

The settings take HBase's own client names, so they can be copied from an `hbase-site.xml`. The lookup user lets GrantForge discover resources; it is separate from the clients that read and write. A successful connection test does not mean requests are already subject to policies.

| Setting | Purpose |
| --- | --- |
| `hbase.zookeeper.quorum` | ZooKeeper hosts, `host` or `host:port` separated by commas, such as `zk1,zk2,zk3:2181`; required unless bootstrap servers are given |
| `hbase.zookeeper.property.clientPort` | The port of quorum hosts that name none, default `2181` |
| `zookeeper.znode.parent` | The cluster's root in ZooKeeper, default `/hbase` |
| `hbase.client.bootstrap.servers` | Instead of ZooKeeper, from HBase 2.5 on: masters or region servers, `host:port` separated by commas |
| `hbase.security.authentication` | `simple` (default): a user name; `kerberos`: a principal with a keytab or password |
| `username` | Required lookup user; with Kerberos the full principal, such as `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: the principal's password, unless a keytab is given; stored encrypted |
| `keytab` | Kerberos: path of the principal's keytab, readable on the **GrantForge server** |
| `kerberos.kdc` | Kerberos: KDCs of the principal's realm, `host[:port]` separated by commas; empty uses the server's `krb5.conf` |
| `hbase.master.kerberos.principal` | Kerberos: the masters' principal, default `hbase/_HOST@` the lookup principal's realm |
| `hbase.regionserver.kerberos.principal` | Kerberos: the region servers' principal, default the masters' |
| `hbase.rpc.protection` | `authentication` (default), `integrity` or `privacy`, as the cluster's |
| `hbase.rpc.timeout` | RPC timeout, default `30000`, range `1..600000` |

The service is exactly what its settings say: no `hbase-site.xml` of the GrantForge server is read. The Kerberos settings are refused with simple authentication; Kerberos needs a password or a keytab. Lookups give up after a few attempts rather than HBase's minutes.

### Kerberos

With `kerberos`, GrantForge signs the principal in through Hadoop's own login, from the keytab or with the password, keeps the sign-in and renews it from the keytab; nothing else on the GrantForge server needs Kerberos settings. Realms whose KDCs are entered in `kerberos.kdc` are declared for the whole GrantForge server, the same way the HDFS and Hive plugins do, so several services can use different realms side by side.

A cluster closes the connection of a client that signs in other than it expects, without saying why. The plugin reports that as a refused sign-in and points to `hbase.security.authentication` and `hbase.rpc.protection`, which must match the cluster's.

### What the lookup user sees

Once the [HBase agent](/en/external/hbase-agent/) enforces the policies, listings leave out the namespaces and tables the lookup user may not use: give it a policy that allows `read` on namespace `*`, or name it in the cluster's `hbase.superuser`.

## Resources and access types

| Level | Matches | Access types |
| --- | --- | --- |
| `global` | Its one value, `*` | `admin`: operations on the whole cluster, such as the balancer, quotas and replication |
| `namespace` | Wildcards, with case | `read`, `write`, `create`, `admin` |
| `table` | Wildcards, with case; below a namespace, named without it | `read`, `write`, `create`, `admin` |
| `column_family` | Wildcards, with case; below a table | `read`, `write` |
| `column` | Wildcards, with case; below a column family; named as UTF-8 text | `read`, `write` |

The access types are HBase's: `read`, `write`, `create` and `admin`. HBase's `exec`, for coprocessor endpoints, is not offered, because the agent cannot check endpoint calls on every HBase release.

As in HBase, a permission covers everything within its scope: `read` on namespace `sales` reads every table in it, `write` on a column family writes all its columns. A request is decided at each scope it names, and a denial at any of them refuses it, so a policy can allow a table and deny one of its columns. The global scope is the cluster itself, not every table; policies on namespace `*` cover every table.

In policy values, `*` matches any number of characters and `?` matches one. The policy editor suggests namespaces, tables within the namespaces a policy names, and column families within its tables; columns hold data and are not suggested.

## Policy example

Suppose the namespace `sales` has a table `orders` with column families `d`, holding `name` and `ssn`, and `meta`. Create these enabled access policies in the same data service:

| Resources | Rule |
| --- | --- |
| `namespace=sales` | Allow `read` for user `alice` |
| `namespace=sales`, `table=orders`, `column_family=d` | Allow `read` for user `bob` |
| `namespace=sales`, `table=orders` | Allow `read` for group `auditors` |
| `namespace=sales`, `table=orders`, `column_family=d`, `column=ssn` | Deny `read` for group `auditors` |
| `namespace=analytics` | Allow `create` and `admin` for user `dave` |

Once the agent applies the new snapshot, `alice` reads every table in `sales`; `bob` reads `orders` and gets only the columns of `d`, and sees only `orders` when he lists tables; members of `auditors` read all of `orders` but `d:ssn`; `dave` creates the namespace `analytics` and its tables, administers them, and is refused tables anywhere else.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the HBase agent in every master and region server. See the [HBase agent](/en/external/hbase-agent/) for its deployment and what it checks.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [HBase agent](/en/external/hbase-agent/)
- [Plugin and service type development](/en/develop/plugins/)
