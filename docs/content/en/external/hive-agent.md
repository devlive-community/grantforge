---
title: Apache Hive agent
description: Install the agent in HiveServer2 to check statements, filter SHOW, mask columns and filter rows with GrantForge policies.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Hive agent is an authorizer of Hive's authorization API, as Apache Ranger's Hive plugin is: HiveServer2 asks it about every statement before running it, and it answers from the `hive` data service's signed policies. The distribution ships it as one Java 8-compatible JAR, `agents/hive/grantforge-agent-hive-<version>.jar`, with its dependencies shaded inside.

The repository's Testcontainers integration tests run the JAR inside HiveServer2 `3.1.3`, `4.0.1` and `4.1.0`. This does not certify every other release. See [Apache Hive](/en/plugins/hive/) for the service plugin.

## How it works

- **Statements are checked.** For each object a statement reads or writes, the agent asks the policies for the access type the operation needs, as Ranger does; the first refusal fails the statement with `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`. Queries are checked column by column.
- **Listing is filtered.** `SHOW DATABASES` and `SHOW TABLES` list only what the user may use: any access to the database or table, or to something in it. `USE` and `DESCRIBE DATABASE` need the same.
- **Queries are masked and filtered.** Where the user has masked columns or a row filter on a table, HiveServer2 rewrites the query with the masks and the condition, so joins, conditions and functions all see what the user may see.
- **Masks cannot be written around.** Writing to, altering or exporting a table on which the user has a mask or a row filter is refused, as Ranger refuses it by default.
- **Grants and roles are GrantForge's.** Hive's `GRANT`, `REVOKE`, `SHOW GRANT`, `CREATE ROLE`, `SET ROLE` and the other role statements are refused; `SHOW CURRENT ROLES` lists the roles GrantForge gives the user.
- **Unsafe statements are refused.** `dfs` commands, which would reach the file system with HiveServer2's own rights, are refused. In HiveServer2 sessions, `TRANSFORM` scripts are disallowed and users may only `SET` the settings Hive's SQL standard authorization allows.
- **Each decision is reported** to GrantForge with the user, client address, resource, access type, outcome, operation and statement.

| Operation | Access type |
| --- | --- |
| Queries, `SHOW CREATE TABLE`, `SHOW PARTITIONS`, `SHOW TBLPROPERTIES`, `DESCRIBE`, `SHOW COLUMNS`, `ANALYZE` | `select` |
| `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `LOAD` (target) | `update` |
| `CREATE DATABASE`, `CREATE TABLE`, `CREATE VIEW`, `CREATE FUNCTION` | `create` on what is created, `select` on what it reads |
| `ALTER …`, `MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`, `UNLOCK` | `lock` |
| `SHOW DATABASES`, `SHOW TABLES`, `USE`, `DESCRIBE DATABASE` | Any access to the object or something in it |
| `REPL DUMP`, `REPL LOAD`, `REPL STATUS` | `repladmin` |
| `KILL QUERY`, resource plans, LLAP administration | `serviceadmin` on `hiveservice` |
| Temporary functions, `ADD JAR`, `COMPILE` | `tempudfadmin` on `global` |
| Locations a statement reads or writes | `read` or `write` on `url` |

## Deployment

1. Add a `hive` data service and configure its policies, including one that allows the users HiveServer2 runs its own work as, such as `hive`, everything they need. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only HiveServer2's account can read.
2. Copy the JAR into `lib` of **every HiveServer2**, such as `/opt/hive/lib`.
3. Add the settings to `hiveserver2-site.xml` or `hive-site.xml` of each HiveServer2, replacing the example address, paths and instance name:

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   The cache directory must be writable by HiveServer2's account. As with Ranger, `hive.server2.enable.doAs=false` lets HiveServer2 read and write data as its own account, so the policies decide who sees what.
4. Restart HiveServer2. The agent starts with the first session and stops with the server, sending or spooling its pending events.
5. **Close the ways around HiveServer2:** the agent checks statements that run in HiveServer2 only. Clients that reach the metastore or the warehouse files directly, such as the Hive CLI, Spark or HDFS clients, are not checked; protect them with the metastore's and HDFS's own permissions, or with the [HDFS agent](/en/external/hdfs-agent/).

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this HiveServer2 and a writable directory for the signed snapshot and spooled events |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), sending interval (default `5000` milliseconds) and spool limit (default 64 MiB) |

The agent reads its settings from HiveServer2's configuration files and from system properties (`-Dgrantforge.…`, which win), never from a session's settings, which a client may choose.

## Users, groups and roles

Policies name the user HiveServer2 gives the session: the user name a client signs in with, or the short name of its Kerberos principal (`alice` for `alice@EXAMPLE.COM`). Groups are those Hadoop's group mapping gives the user in HiveServer2, together with the groups GrantForge puts the user in; roles are those GrantForge gives the user.

## Decisions and limitations

- Without a usable snapshot, or when the policies cannot be read, every statement is refused. A service that is not in use leaves HiveServer2 unchecked, as it is without an authorizer.
- A token bound to a service whose type is not `hive` refuses everything.
- `url` policies decide the locations statements read and write; unlike Ranger, the agent does not fall back to the file system's own permissions.
- Statements Hive names no object for are checked on every database: `REPL` statements without one need `repladmin`, changing the owner of a table or database without one needs `alter`, on database `*`.
- `EXPLAIN`, `DESCRIBE FUNCTION`, `SHOW FUNCTIONS`, `SET` and the other statements Ranger leaves unchecked are not checked either; metadata calls of JDBC clients, such as `getTables`, are not filtered.
- The agent works in HiveServer2. Authorization inside the metastore is not covered.

## Related guides

- [Apache Hive](/en/plugins/hive/)
- [Data services, policies and agents](/en/external/data-services/)
