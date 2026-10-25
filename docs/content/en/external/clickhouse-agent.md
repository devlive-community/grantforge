---
title: ClickHouse agent
description: Run the ClickHouse agent beside the servers, so that GrantForge policies become ClickHouse's own roles, grants and row policies, down to columns and rows, and accesses are reported from the query log.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The ClickHouse agent is a service beside the servers. It keeps ClickHouse's own access control in step with the signed policies of a `clickhouse` data service: one role per user that holds exactly what the policies give the user, and one row policy per row filter. ClickHouse then checks every query itself, over each of its protocols, and the agent never stands between clients and the server. The distribution ships it as a runnable JAR, `agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`, which requires Java 17 or later.

The repository's Testcontainers integration tests run the agent, and the JAR as shipped, against ClickHouse `24.8`, `25.3`, `25.8`, `26.3` and `26.8`. This does not certify every other release; the agent refuses servers older than `24.8`. See [ClickHouse](/en/plugins/clickhouse/) for the service plugin.

## How it works

- **One role per user holds what GrantForge gives.** For each user the policies give something, the agent keeps a role named after it, `grantforge_alice` for `alice`, and grants it to the user as a default role. `SHOW GRANTS FOR grantforge_alice` shows everything GrantForge gives `alice`; the agent changes no other role and no grant it did not make.
- **Whole databases where nothing below is decided otherwise.** An access type a policy allows on all of a database, with no policy that could take it away from a table or column in it, is granted on the database, so tables created later have it at once. Otherwise it is granted table by table, and reading and writing column by column.
- **Row filters become row policies.** A row filter is a permissive row policy of the user's role on the table. A new or changed filter is in place before the privileges it narrows. A filter ClickHouse refuses, or one whose text would make the row policy apply to others, is replaced by one that shows the user no rows, and logged.
- **The policies of the present moment.** Each sync plans from the policies as they apply at that moment, so validity periods start and end on time, and from the users, databases, tables and columns ClickHouse has, so new ones are covered at the next sync.
- **ClickHouse roles are groups.** A policy for a group applies to users that hold a ClickHouse role of that name, directly or through other roles, together with the groups GrantForge puts them in.
- **Changes made by hand are undone.** The agent remembers what it gave each role and row policy; one that differs is put back at the next sync. When nothing changed, the agent sends nothing.
- **Grants outside GrantForge are reported.** ClickHouse adds up privileges, so the agent cannot take away what a user holds directly or through another role. It warns once of each such grant that gives what a policy denies, naming the user, the grant, the role it comes through and the policy.
- **Accesses come from the query log.** The agent reads `system.query_log` and reports each statement as one event per table, database or the server it touched, a refusal as one denied event for what ClickHouse's error names. Each event says whether GrantForge decided it, that is whether the policies decide as ClickHouse did, or a grant outside GrantForge allowed it.

| Statement | Access event |
| --- | --- |
| `SELECT`, the read side of `INSERT … SELECT` | `select` on each table read |
| `INSERT` | `insert` on the table written |
| `ALTER`, `ALTER UPDATE`, `ALTER DELETE`, `DELETE`, `UPDATE` | `alter` on the table |
| `DROP TABLE`, `TRUNCATE`, `OPTIMIZE` | `drop`, `truncate`, `optimize` on the table |
| `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` | `create` on the database |
| `DROP DATABASE` | `drop_database` on the database |
| `CREATE DATABASE`, `KILL QUERY`, `SYSTEM …` | `create_database`, `kill_query`, `system` on the server |

## Deployment

1. Add a `clickhouse` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only the agent's account can read.
2. Create the agent's ClickHouse user. It grants what the policies give, so it needs those privileges with the grant option, and access management to keep roles and row policies:

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   The users the agent keeps roles for must be stored where they can be changed, in the `local_directory` or `replicated` user directory, as `CREATE USER` stores them. Users of `users.xml` and LDAP are left alone and logged once. A cluster should keep its users, roles and row policies in a `replicated` user directory: one agent then serves the whole cluster.
3. Write the settings file, replacing the example addresses, paths and names:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   These paths are on the agent's machine, and the cache directory must be writable. Password files are read as UTF-8 and trimmed.
4. Start the agent, for example as a systemd service:

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   Replace `<version>` with the distribution version. Settings supplied as `java -Dgrantforge.…=… -jar …` override the file. The agent stops cleanly on SIGTERM and sends or spools pending events; ClickHouse keeps what it was given.
5. **Move existing grants into policies:** grants made outside GrantForge keep applying next to the agent's roles. Create the matching policies, then revoke the grants; the agent's warnings name those that give what a policy denies.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this agent and a writable directory for the signed snapshot, spooled events and the query log's read position |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.clickhouse.urls`, `grantforge.clickhouse.user` | Required: the servers' HTTP interfaces, tried in turn when one cannot be reached, and the agent's ClickHouse user |
| `grantforge.clickhouse.password.file` | The user's password; omitted, the agent signs in without one |
| `grantforge.clickhouse.tls.ca` | PEM CA certificate that verifies `https://` servers; empty trusts what Java trusts |
| `grantforge.clickhouse.tls.cert`, `grantforge.clickhouse.tls.key` | PEM client certificate and private key, together, to sign in with a certificate (`IDENTIFIED WITH ssl_certificate`) instead of a password |
| `grantforge.clickhouse.timeout.ms` | How long a request to ClickHouse may take, default `30000`, range `1..600000` milliseconds |
| `grantforge.clickhouse.role.prefix` | The start of the agent's role names, default `grantforge_`; letters, digits and underscores, ending with an underscore. The agent owns every role and row policy whose name starts with it |
| `grantforge.clickhouse.unmanaged.users` | Users the agent leaves alone, separated by commas, such as administrators; it always leaves its own user alone |
| `grantforge.sync.interval.ms` | How often the agent brings ClickHouse in step, default `10000`, range `1000..3600000` milliseconds |
| `grantforge.audit.query.log` | Default `true`; `false` reads no query log and reports no accesses |
| `grantforge.audit.query.log.cluster` | A cluster whose replicas' query logs are read through `clusterAllReplicas`; empty reads the server the agent asks |
| `grantforge.audit.query.log.delay.ms`, `grantforge.audit.query.log.interval.ms`, `grantforge.audit.query.log.batch` | How far behind the present the log is read, default `10000` milliseconds, as ClickHouse writes it every 7.5 seconds; how often, default `5000` milliseconds; and the most entries one read takes, default `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), send interval (default `5000` milliseconds) and the spool limit on disk (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` sends each |

## Users, groups and roles

Policies name ClickHouse users. Groups are the ClickHouse roles a user holds, directly or through other roles, together with the groups GrantForge puts the user in; roles are the roles GrantForge gives the user.

## Decisions and limits

- Without a snapshot the agent changes nothing. A service that is not in use, or a token bound to a service whose type is not `clickhouse`, grants nothing: the agent drops its roles and row policies, and ClickHouse's own grants apply.
- While the agent does not run, ClickHouse keeps what it was given last; policy changes wait for it. A change reaches ClickHouse within the refresh interval and the sync interval.
- Tables created in a database the agent grants table by table get their privileges at the next sync.
- A user ClickHouse shows no row policy for sees every row of a table, as ClickHouse's `users_without_row_policies_can_read_rows` setting allows by default; with it off, users without a filter see no rows of filtered tables. Permissive row policies made outside GrantForge for the same user widen what its filter shows.
- `system` grants every `SYSTEM` statement, `SYSTEM SHUTDOWN` included.
- The query log reports what ClickHouse logs: statements of users with `log_queries` off are not reported. For a refusal, ClickHouse may name the table where the user lacks one column; the event then names the table.
- ClickHouse has no column masking outside ClickHouse Cloud, so policies cannot mask values.

## Related guides

- [ClickHouse](/en/plugins/clickhouse/)
- [Data services, policies and agents](/en/external/data-services/)
