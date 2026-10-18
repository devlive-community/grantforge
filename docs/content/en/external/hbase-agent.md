---
title: Apache HBase agent
description: Install the coprocessor in HBase masters and region servers to check operations, reads and writes with GrantForge policies, return only the columns a user may read and hide what a user may not use.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The HBase agent is a coprocessor of masters, regions and region servers: HBase asks it about every operation, read and write, and it answers from the `hbase` data service's signed policies. The distribution ships it as one Java 8-compatible JAR, `agents/hbase/grantforge-agent-hbase-<version>.jar`, with its dependencies shaded inside.

The repository's Testcontainers integration tests run the JAR inside HBase `2.5.16`, `2.6.7` and `3.0.0`. This does not certify every other release. See [Apache HBase](/en/plugins/hbase/) for the service plugin.

## How it works

- **Every request is checked at each scope it names.** In HBase a permission covers everything within its scope, so the agent decides a request at its namespace, its table, its column family and its column: a denial at any of them refuses it, an allowance at any of them grants it.
- **A namespace's owners have its tables.** `create` and `admin` on a namespace let a user create the namespace, create tables in it and administer them, without a policy per table.
- **Reads return what may be read.** When a user may read only some of a table, such as one column family, a get or scan returns those columns instead of failing; a user who may read nothing of the table is refused.
- **Listings leave out what may not be used.** Listing namespaces and tables shows only those the user may use, or use something within, and listing snapshots only those of tables the user administers; describing a namespace the user may not use is refused.
- **Refusals say why.** The client gets `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)`, or the policy that denied it.
- **Decisions are kept and accesses folded.** The decisions of the current snapshot are kept, by default 100,000 for 10 seconds, and a new snapshot starts afresh. Identical allowed accesses within a window, by default 10 seconds, are reported as one access event that says how many there were; refusals are reported one by one at once.
- **The servers are not checked.** HBase's superusers, named in `hbase.superuser`, and the servers' own work are not checked or audited. Tables of the `hbase` namespace, such as `hbase:meta`, may be read by everyone, as clients must.

| Operation | Access needed |
| --- | --- |
| Get, scan | `read` on the table, or on the columns returned |
| Put, increment, append, delete of columns | `write` on each column written |
| Delete of a column family or a row | `write` on each column family, as HBase deletes a row family by family |
| Check-and-mutate | `read` on the column compared, or on the table for a filter, and `write` on what is written |
| Create table | `create` on the namespace |
| Delete, truncate, modify, enable, disable, flush, split, merge, compact | `admin` or `create` on the table |
| Move, assign, unassign, take offline | `admin` on the table |
| Snapshot, restore, delete or list a snapshot | `admin` on the table; cloning also needs `create` on the target namespace |
| Bulk load | `create` on the table |
| Create, modify, delete namespace | `admin` on the namespace |
| Describe a namespace; list namespaces and tables | Any access to it or within it |
| Balancer, shutdown, quotas, replication peers, region server groups, procedures, decommissioning, stopping region servers, rolling WALs | `admin` on `global` |

## Deployment

1. Add an `hbase` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only HBase's account can read.
2. Copy the JAR into `lib` of **every master and region server**, such as `/opt/hbase/lib`.
3. Add the settings to `hbase-site.xml` of each, replacing the example address, paths and instance name:

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   The cache directory must be writable by HBase's account. Replace HBase's own `AccessController` in the coprocessor lists rather than loading both.
4. Restart the masters and region servers one at a time. Until the agent has its first snapshot, requests other than the superusers' are refused.
5. **Move existing permissions first:** grants that HBase's `AccessController` stored in `hbase:acl` no longer apply. List them with `user_permission` beforehand and create matching policies.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this master or region server and a writable directory for the signed snapshot and spooled events |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), sending interval (default `5000` milliseconds) and spool limit (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` reports each |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | The most decisions kept, default `100000`, up to `10000000`, `0` keeps none; and for how long, default `10000`, `1..3600000` milliseconds |

HBase starts a coprocessor for the master, for the region server and for every region it opens; all of them share one agent, decision cache and folding of events.

## Users, groups and roles

Policies name the short name HBase gives the connection's user: the user name of simple authentication, or the short name of a Kerberos principal by `hadoop.security.auth_to_local`. Groups are those Hadoop's group mapping gives the user on the server, together with the groups GrantForge puts the user in; roles are those GrantForge gives the user.

## Decisions and limitations

- Without a usable snapshot, everything but the superusers' requests and reads of the `hbase` namespace is refused. A service that is not in use leaves HBase unchecked, as it is without an authorizer.
- A token bound to a service whose type is not `hbase` refuses everything.
- HBase's `grant`, `revoke` and `user_permission` fail with *AccessController is not loaded*: permissions are GrantForge policies.
- Coprocessor endpoint calls are not checked; load only endpoints you trust.
- HBase gives the creator of a table no permission of its own here; a namespace policy, as above, gives its owners their tables.
- HBase logs some refusals instead of returning them, such as of the balancer switch: the switch stays as it was, but the client is not told.
- Decisions are kept for `grantforge.decision.cache.ttl.ms`, so a policy's validity period starts or ends up to that much late.
- HBase's cell-level permissions and visibility labels are not covered. Columns are compared as UTF-8 text.

## Related guides

- [Apache HBase](/en/plugins/hbase/)
- [Data services, policies and agents](/en/external/data-services/)
