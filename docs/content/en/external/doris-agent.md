---
title: Apache Doris agent
description: Run the Doris agent beside the cluster, so that GrantForge policies become Doris's own roles, column grants, workload group grants and row policies, given to every identity of a user, granted on whole databases and catalogs only where Doris would give no more, and accesses are reported from Doris's audit log table.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Apache Doris agent is a service beside the cluster. It keeps Doris's own access control in step with the signed policies of a `doris` data service: one role per user that holds exactly what the policies give the user, down to columns and workload groups, and one row policy per row filter. Doris then checks every statement itself, and the agent never stands between clients and the frontends. The distribution ships it as a runnable JAR, `agents/doris/grantforge-agent-doris-<version>.jar`, which requires Java 17 or later.

The repository's Testcontainers integration tests run the agent, and the JAR as shipped, against Apache Doris `2.1`, `3.1`, `4.0` and `4.1`. This does not certify every other release; the agent refuses clusters older than `2.1`. See [Apache Doris](/en/plugins/doris/) for the service plugin.

## How it works

- **One role per user holds what GrantForge gives.** For each user the policies give something, the agent keeps a role named after it, `grantforge_alice` for `alice`, with a comment that names the user, and grants it to every identity of the user, such as `'alice'@'%'` and `'alice'@'10.%'`. `SHOW ROLES` shows what GrantForge gives `alice`; the agent changes no other role and no grant it did not make.
- **Whole databases and catalogs where nothing below is decided otherwise.** Reading, loading and showing views that a policy allows on all of a database or catalog, with no policy that could take them away below it, are granted on the database or catalog, so tables created later have them at once. Otherwise they are granted table by table, and reading column by column. Altering is granted table by table, as Doris's privilege on a database would also rename it.
- **Never more than the policies allow.** Doris's privilege to create databases in a catalog is the privilege to create tables in all of them, and its privilege to drop a database the privilege to drop all its tables. The agent grants each only where the policies give the wider privilege too; otherwise it withholds it and logs once which user cannot do what and why.
- **Workload groups are granted by name.** A policy on workload groups gives `USAGE_PRIV` on each group it allows, so a user may run queries in it; groups created later are covered at the next sync.
- **Row filters become row policies.** A row filter is a permissive row policy of the user's role on the table. A new filter is in place before the privileges it narrows; a filter that changes is replaced behind a restrictive policy that shows no rows, so the user never sees more than either filter shows. Each filter is first run on its table; one Doris cannot run there or refuses, such as one naming a column the table does not have, is replaced by one that shows the user no rows, and logged.
- **The policies of the present moment.** Each sync plans from the policies as they apply at that moment, so validity periods start and end on time, and from the users, catalogs, databases, tables and columns Doris has, so new ones are covered at the next sync.
- **Doris roles are groups.** A policy for a group applies to users that hold a Doris role of that name, together with the groups GrantForge puts them in.
- **Changes made by hand are undone.** The agent compares its roles' privileges, as `SHOW ROLES` shows them, and its row policies with what the plans say at each sync, and puts back what differs. Doris shows column privileges only per user, merged over all of a user's grants: the agent grants the columns a plan names wherever a user cannot read them all, and revokes the others from its role once; a column that stays readable comes from a grant outside GrantForge. When nothing changed, the agent sends nothing.
- **Grants outside GrantForge are reported.** Doris adds up privileges and joins permissive row policies with OR, so the agent cannot take away what a user holds directly or through another role. It warns once of each such grant, column grant or row policy that gives what a policy denies, naming the user, the grant and where it comes from, and of users with `ADMIN_PRIV` or `NODE_PRIV`, whom no role binds.
- **Accesses come from the audit log.** The agent reads `__internal_schema.audit_log`, which every frontend writes, and reports each statement as one event per table, database or catalog it touched; it reads the tables a statement touched from the statement itself with Druid's Doris parser, as the log names none. A refusal is one denied event for the privilege and object Doris's error names. Each event says whether GrantForge decided it, that is whether the policies decide as Doris did, or a grant outside GrantForge allowed it. The log is read behind the present by the frontends' batch interval, `audit_plugin_max_batch_interval_sec`, and five seconds more.

| Statement | Access event |
| --- | --- |
| `SELECT`, the read side of `INSERT … SELECT` | `select` on each table read |
| `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` | `load` on the table written |
| `ALTER TABLE`, `DROP TABLE`, `SHOW CREATE VIEW` | `alter`, `drop`, `show_view` on the table |
| `CREATE TABLE`, `CREATE VIEW` | `create` on the database |
| `DROP DATABASE` | `drop_database` on the database |
| `CREATE DATABASE` | `create_database` on the catalog |

## Deployment

1. Add a `doris` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only the agent's account can read.
2. Create the agent's Doris user. It creates roles and row policies, grants what the policies give and reads every user's grants and the audit log, which `ADMIN_PRIV` allows:

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. To report accesses, turn on Doris's audit log where it is off, as in Doris 2.1: `SET GLOBAL enable_audit_plugin = true`. A shorter `audit_plugin_max_batch_interval_sec` than its default of 60 seconds reports accesses sooner.
4. Write the settings file, replacing the example addresses, paths and names:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   These paths are on the agent's machine, and the cache directory must be writable. Password files are read as UTF-8 and trimmed.
5. Start the agent, for example as a systemd service:

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   Replace `<version>` with the distribution version. Settings supplied as `java -Dgrantforge.…=… -jar …` override the file. The agent stops cleanly on SIGTERM and sends or spools pending events; Doris keeps what it was given.
6. **Move existing grants into policies:** grants made outside GrantForge keep applying next to the agent's roles. Create the matching policies, then revoke the grants; the agent's warnings name those that give what a policy denies.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this agent and a writable directory for the signed snapshot, spooled events and the audit log's read position |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.doris.hosts`, `grantforge.doris.user` | Required: the frontends' MySQL protocol addresses, tried in turn when one cannot be reached, the port `9030` unless given, and the agent's Doris user |
| `grantforge.doris.password.file` | The user's password; omitted, the agent signs in without one |
| `grantforge.doris.ssl.mode`, `grantforge.doris.tls.ca` | `disable` (default), `trust`, `verify-ca` or `verify-full`, for frontends that offer TLS; and the PEM CA certificate that verifies them, empty for what Java trusts |
| `grantforge.doris.timeout.ms` | How long connecting and a statement may take, default `30000`, range `1..600000` milliseconds |
| `grantforge.doris.role.prefix` | The start of the agent's role names, default `grantforge_`; letters, digits and underscores, ending with an underscore, at most 32 characters. The agent owns every role and row policy whose name starts with it |
| `grantforge.doris.unmanaged.users` | Users the agent leaves alone, separated by commas, such as service accounts; it always leaves its own user, `root`, `admin` and users with `ADMIN_PRIV` or `NODE_PRIV` alone |
| `grantforge.doris.catalogs` | The catalogs the agent grants in by database, table and column, separated by commas; empty for every catalog. Others get only what can be granted on them as a whole, so an external catalog with many tables need not be listed at each sync |
| `grantforge.sync.interval.ms` | How often the agent brings Doris in step, default `10000`, range `1000..3600000` milliseconds |
| `grantforge.audit.log` | Default `true`; `false` reads no audit log and reports no accesses |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | How far behind the present the log is read, by default the cluster's batch interval and five seconds; how often, default `5000` milliseconds; and the most entries one read takes, default `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), send interval (default `5000` milliseconds) and the spool limit on disk (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` sends each |

## Users, groups and roles

Policies name Doris users by their user names; a user name with several hosts is one user, and the agent's role goes to each of its identities. Groups are the Doris roles a user holds, together with the groups GrantForge puts the user in; roles are the roles GrantForge gives the user.

## Decisions and limits

- Without a snapshot the agent changes nothing. A service that is not in use, or a token bound to a service whose type is not `doris`, grants nothing: the agent drops its roles and row policies, and Doris's own grants apply.
- While the agent does not run, Doris keeps what it was given last; policy changes wait for it. A change reaches Doris within the refresh interval and the sync interval.
- Tables created in a database the agent grants table by table, and databases created in a catalog it grants database by database, get their privileges at the next sync.
- Doris's row policies cannot bind `root` and `admin`. A user Doris shows no row policy for sees every row of a table; permissive row policies made outside GrantForge for the same user widen what its filter shows.
- Doris checks `USAGE_PRIV` on a workload group when a query runs on the backends; a query the frontend answers itself, such as a constant or a cached result, runs without the check. Every user may use the `normal` group, as Doris gives it to all.
- The users the agent keeps roles for must exist in Doris; users who sign in through LDAP without a Doris user of their own get no role.
- Doris masks values only through Apache Ranger, so policies cannot mask values.

## Related guides

- [Apache Doris](/en/plugins/doris/)
- [Data services, policies and agents](/en/external/data-services/)
