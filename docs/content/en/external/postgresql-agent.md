---
title: PostgreSQL agent
description: Run the PostgreSQL agent beside the servers, so that GrantForge policies become PostgreSQL's own roles, privileges and row security policies, down to columns and rows, database by database in one transaction each, and accesses are reported from the server log.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The PostgreSQL agent is a service beside the servers. It keeps PostgreSQL's own access control in step with the signed policies of a `postgresql` data service: one role per user that holds exactly what the policies give the user, in every database it keeps, and one row security policy per row filter. PostgreSQL then checks every statement itself, and the agent never stands between clients and the server. The distribution ships it as a runnable JAR, `agents/postgresql/grantforge-agent-postgresql-<version>.jar`, which requires Java 17 or later.

The repository's Testcontainers integration tests run the agent, and the JAR as shipped, against PostgreSQL `15`, `16`, `17` and `18` with pgaudit. This does not certify every other release; the agent refuses servers older than `15`. See [PostgreSQL](/en/plugins/postgresql/) for the service plugin.

## How it works

- **One role per user holds what GrantForge gives.** For each user the policies give something, the agent keeps a role named after it, `grantforge_alice` for `alice`, and makes the user a member that has the role's privileges without `SET ROLE`. The agent writes a comment on each of its roles and takes no role without it for one of its own, so it changes no other role and no privilege it did not give.
- **Each database changes in one transaction.** The agent signs in to each database it keeps and makes its changes there in one transaction, so users see a policy version applied to a database all at once or not at all.
- **Whole tables where nothing below is decided otherwise.** An access type a policy allows on all of a table, with no policy that could take it away from a column, is granted on the table, so columns added later have it at once. Otherwise reading, inserting and updating are granted column by column. `CONNECT` to the database, `USAGE` of the schema and of the sequences of serial columns the user may insert into come with them.
- **Row filters only narrow.** A row filter is a restrictive row security policy of the user's role on the table, for `SELECT`. Where row security is off, the agent turns it on together with a permissive policy that shows everybody every row, named after its prefix (`grantforge`), so that nothing changes for others; it turns row security off again when the table has no filter and no policy of its own left. Row security an owner turned on stays the owner's, and the agent's policies only narrow what it shows. A filter PostgreSQL refuses, one that would change whom the policy binds, or a text of more than one statement is replaced by a policy that shows the user no rows, and logged.
- **The policies of the present moment.** Each sync plans from the policies as they apply at that moment, so validity periods start and end on time, and from the users, schemas, tables and columns PostgreSQL has, so new ones are covered at the next sync.
- **Roles are groups.** A policy for a group applies to users that are members of a PostgreSQL role of that name, directly or through other roles, together with the groups GrantForge puts them in.
- **Changes made by hand are undone.** The agent compares the privileges of its roles with what PostgreSQL shows at each sync, and its row security policies with what it created last; what differs is put back at the next sync. When nothing changed, the agent sends nothing.
- **Privileges outside GrantForge are reported.** PostgreSQL adds up privileges, so the agent cannot take away what a user holds itself, through another role or through PUBLIC, nor what it may do with the tables it owns. It warns once of each such privilege that gives what a policy denies, naming the user, the privilege, how the user holds it and the policy.
- **Accesses come from the server log.** The agent reads PostgreSQL's JSON server log: pgaudit's session log for what PostgreSQL allowed, one event per table a statement read or wrote, and PostgreSQL's refusals for a missing privilege, one denied event for what the error names. Each event says whether GrantForge decided it, that is whether the policies decide as PostgreSQL did, or a privilege outside GrantForge allowed it.

| Statement | Access event |
| --- | --- |
| `SELECT`, `COPY … TO`, the read side of other statements | `select` on each table read |
| `INSERT`, `COPY … FROM` | `insert` on the table written |
| `UPDATE`, `MERGE`, `SELECT … FOR UPDATE` | `update` on the table |
| `DELETE`, `TRUNCATE` | `delete`, `truncate` on the table |
| `CREATE TRIGGER` | `trigger` on the table |
| `CREATE TABLE`, `CREATE VIEW` and other objects | `create` on the schema |
| `CREATE SCHEMA`, `CREATE TEMPORARY TABLE` | `create_schema`, `temporary` on the database |
| A refused connection, a schema refused for lack of `USAGE` | `connect` on the database, `usage` on the schema |

## Deployment

1. Add a `postgresql` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only the agent's account can read.
2. Create the agent's PostgreSQL user as a superuser. It grants privileges on tables it does not own, turns row security on and off, keeps roles and reads the server log:

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   The tests run the agent as a superuser. Superusers are bound neither by privileges nor by row security, so the agent keeps no role for them.
3. To report accesses, let PostgreSQL write its server log as JSON and load pgaudit, from PGDG's packages such as `postgresql-17-pgaudit` or the platform's, in `postgresql.conf`, then restart the server:

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   Without pgaudit the agent reports what PostgreSQL refused, not what it allowed; without the JSON log it reports nothing and says so once.
4. Write the settings file, replacing the example addresses, paths and names:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   These paths are on the agent's machine, and the cache directory must be writable. Password files are read as UTF-8 and trimmed.
5. Start the agent, for example as a systemd service:

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   Replace `<version>` with the distribution version. Settings supplied as `java -Dgrantforge.…=… -jar …` override the file. The agent stops cleanly on SIGTERM and sends or spools pending events; PostgreSQL keeps what it was given.
6. **Move existing privileges into policies:** privileges given outside GrantForge keep applying next to the agent's roles. Create the matching policies, then revoke the privileges; the agent's warnings name those that give what a policy denies.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this agent and a writable directory for the signed snapshot, spooled events and the server log's read position |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.postgresql.hosts`, `grantforge.postgresql.user` | Required: the servers as `host:port`, tried in turn until one that can be reached is the primary, and the agent's PostgreSQL user |
| `grantforge.postgresql.database` | The database the agent signs in to first, to read the roles, the databases and the server log, default `postgres` |
| `grantforge.postgresql.password.file` | The user's password; omitted, the agent signs in without one |
| `grantforge.postgresql.ssl.mode` | TLS as libpq's `sslmode` names it, default `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca and verify-full: PEM CA certificate that verifies the servers; empty trusts what Java trusts |
| `grantforge.postgresql.tls.cert`, `grantforge.postgresql.tls.key` | PEM client certificate and its PKCS #8 private key, together, to sign in with a certificate (`cert` authentication) instead of a password; they need `require` or a verify mode, and a verify mode with them needs `grantforge.postgresql.tls.ca` |
| `grantforge.postgresql.timeout.ms` | How long connecting and a statement may take, default `30000`, range `1..600000` milliseconds |
| `grantforge.postgresql.databases` | The databases the agent keeps, separated by commas; empty keeps every database that allows connections |
| `grantforge.postgresql.role.prefix` | The start of the agent's role names, default `grantforge_`; letters, digits and underscores, ending with an underscore, at most 32 characters and not starting with `pg_`. The agent owns the roles it marked whose names start with it, and the row security policies named after them or after the prefix without its last underscore |
| `grantforge.postgresql.unmanaged.users` | Users the agent leaves alone, separated by commas, such as administrators; it always leaves its own user and superusers alone |
| `grantforge.sync.interval.ms` | How often the agent brings PostgreSQL in step, default `10000`, range `1000..3600000` milliseconds |
| `grantforge.audit.server.log` | Default `true`; `false` reads no server log and reports no accesses |
| `grantforge.audit.server.log.interval.ms`, `grantforge.audit.server.log.max.bytes` | How often the log is read, default `5000` milliseconds, and the most bytes one read takes, default 16 MiB, so that a backlog is read over several turns |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), send interval (default `5000` milliseconds) and the spool limit on disk (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` sends each |

## Users, groups and roles

Policies name PostgreSQL users, the roles that may sign in. Groups are the roles a user is a member of, directly or through other roles, together with the groups GrantForge puts the user in; roles are the roles GrantForge gives the user.

## Decisions and limits

- Without a snapshot the agent changes nothing. A service that is not in use, or a token bound to a service whose type is not `postgresql`, grants nothing: the agent drops its roles and row security policies, and PostgreSQL's own privileges apply.
- While the agent does not run, PostgreSQL keeps what it was given last; policy changes wait for it. A change reaches PostgreSQL within the refresh interval and the sync interval.
- Tables created after a sync get their privileges at the next sync.
- Superusers and the owners of a table may do everything with it; superusers, roles with `BYPASSRLS` and the owners of a table are not bound by its row security unless the owner forces it.
- A view reads its tables with its owner's privileges unless it is `security_invoker`: the server log reports the tables a view read as allowed by a privilege outside GrantForge.
- Reading a partition directly is not filtered by the row security of its partitioned table.
- pgaudit logs a statement before PostgreSQL runs it, so a statement that fails later, such as an insert whose sequence is refused, is reported as allowed and its refusal as denied. The statements of a session pgaudit does not log are not reported.
- PostgreSQL's refusals name a table without its schema: the event names the table the statement qualifies, the one table of that name, or the one in `public`. An object created without a schema is reported in `public`.
- The agent reads the server log of the server it signs in to, the primary; the logs of replicas are not read.
- PostgreSQL has no column masking, so policies cannot mask values.

## Related guides

- [PostgreSQL](/en/plugins/postgresql/)
- [Data services, policies and agents](/en/external/data-services/)
