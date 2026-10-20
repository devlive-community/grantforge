---
title: Trino agent
description: Install the system access control in the Trino coordinator to check queries with GrantForge policies, list only what a user may use, filter rows and mask columns, and keep impersonation and other users' queries under policy.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Trino agent is a system access control named `grantforge`: the coordinator asks it about every catalog, schema, table, column, routine, session property and user a query touches, and it answers from the `trino` data service's signed policies. Trino 470 to 478 run on Java 23 or 24 and Trino 479 and later on Java 25, so the distribution ships two JARs, each with its dependencies shaded inside:

| Trino | JAR |
| --- | --- |
| 470 to 478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 and later | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

The repository's Testcontainers integration tests run the first JAR inside Trino `470` and `476` and the second inside `479` and `484`. This does not certify every other release. See [Trino](/en/plugins/trino/) for the service plugin.

## How it works

- **Every check is decided at each scope it names.** A permission on a catalog, schema or table covers what is within it, so the agent decides a column at its catalog, its schema, its table and itself: a denial at any of them refuses, an allowance at any of them grants.
- **Listings leave out what may not be used.** `SHOW CATALOGS`, `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW FUNCTIONS`, `information_schema` and the driver's metadata show only what the user may use, or use something within.
- **What describes data stays open.** Every catalog's `information_schema`, the `system` catalog's `jdbc` and `metadata` schemas, which JDBC drivers and BI tools read, and Trino's built-in functions need no policy; their rows are filtered as listings are.
- **Rows are filtered and columns masked.** Row filtering policies apply to every query that reads the table, and masks are fitted to the column's type, so that a mask never breaks a query: text masks apply to `varchar` and `char` columns and are cast back to the column's type, the year mask to dates and timestamps, a custom expression is cast to the type, and any other column a text mask names reads as a `NULL` of its type.
- **Impersonation and others' queries are under policy.** Running a query as another user needs `impersonate` on that user; seeing or killing another user's queries needs `view_query` or `kill_query` on the user. Everyone may run queries, and see and kill their own.
- **Refusals say why.** The client gets `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`, or that no policy allows it, or that no policies came yet.
- **Decisions are kept and accesses folded.** The decisions of the current snapshot are kept, by default 100,000 for 10 seconds, and a new snapshot starts afresh. Identical allowed accesses within a window, by default 10 seconds, are reported as one access event that says how many there were; refusals are reported one by one at once. Each event names the query.
- **Privileges are GrantForge's.** Trino's `GRANT`, `REVOKE`, `DENY` and role statements are refused, as policies are managed in GrantForge.

| Statement | Access needed |
| --- | --- |
| `SELECT` | `select` on each column read; a query that reads no column, such as `count(*)`, on the table |
| `INSERT`, `DELETE`, `TRUNCATE` | `insert`; `delete` for both others |
| `UPDATE` | `update` on each column written |
| `CREATE SCHEMA`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW` | `create` on what is created |
| `DROP` of a schema, table, view or materialized view | `drop` on it |
| `ALTER` of a table, view or column, `COMMENT`, `SET PROPERTIES`, `SET AUTHORIZATION`, `ALTER TABLE ... EXECUTE` | `alter` on it |
| Renaming a schema, table, view or materialized view | `alter` on it and `create` on the new name |
| `REFRESH MATERIALIZED VIEW` | `insert` on it |
| A view whose owner runs it | Its owner needs `select` on the columns and `execute` on the functions it reads |
| `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW COLUMNS`, `SHOW FUNCTIONS`, `SHOW CREATE` | Any access to it or within it |
| `CREATE CATALOG`, `DROP CATALOG` | `create` or `drop` on the catalog |
| Calling a function; `CREATE FUNCTION`, `DROP FUNCTION` | `execute`; `create_function`, `drop_function` on the function |
| `CALL` | `execute` on the procedure |
| `SET SESSION` | `set` on the catalog's or the system's session property |
| Running as another user, seeing or killing others' queries | `impersonate`, `view_query`, `kill_query` on the user |
| Reading or writing system information: the nodes, statistics and thread dumps of Trino's REST API and web UI | `read` or `write` on `system_information` |

From Trino 479, the second JAR also checks table branches: listing them needs to see the table, and creating, dropping or fast-forwarding one needs `alter` on it; refreshing a view, and changing a materialized view's owner or comment, need `alter` on it.

## Deployment

1. Add a `trino` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only Trino's account can read.
2. Copy the JAR of the coordinator's release into a directory of its own in Trino's plugin directory, such as `/usr/lib/trino/plugin/grantforge/`. Only the coordinator checks access; the workers need nothing.
3. Create `etc/access-control.properties` on the coordinator, replacing the example address, paths and instance name:

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   The cache directory must be writable by Trino's account. GrantForge replaces Trino's file-based or other access control rather than running beside it.
4. Restart the coordinator. Until the agent has its first snapshot, queries are refused but for what describes data.
5. **Move existing rules first:** the rules of Trino's file-based access control no longer apply. Create matching policies beforehand.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this coordinator and a writable directory for the signed snapshot and spooled events |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), sending interval (default `5000` milliseconds) and spool limit (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` reports each |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | The most decisions kept, default `100000`, up to `10000000`, `0` keeps none; and for how long, default `10000`, `1..3600000` milliseconds |

## Users, groups and roles

Policies name the query's session user, as Trino gives it after authentication and user mapping. Groups are those Trino's group provider gives the user, together with the groups GrantForge puts the user in; roles are those GrantForge gives the user.

## Decisions and limitations

- Without a usable snapshot, everything is refused but what describes data, running queries and one's own queries. A service that is not in use leaves Trino unchecked, as it is without an access control; Trino's privilege and role statements are refused all the same.
- A token bound to a service whose type is not `trino` refuses everything.
- Trino asks whether a user may use a catalog at all before anything else: a user with no permission in a catalog gets Trino's own `Cannot access catalog`.
- A `SELECT` that reads a column the user may not read is refused, naming the first such column; columns are not dropped from the result.
- Trino writes no table whose columns it masks for the user: such an `INSERT` fails while the mask applies.
- A mask is cast to the column's type, so a bounded `varchar` shows as much of the masked value as fits, such as 3 characters of a hash in `varchar(3)`.
- A row filter or mask the snapshot cannot read shows no rows, or the column as `NULL`, rather than the data.
- Decisions are kept for `grantforge.decision.cache.ttl.ms`, so a policy's validity period starts or ends up to that much late.

## Related guides

- [Trino](/en/plugins/trino/)
- [Data services, policies and agents](/en/external/data-services/)
