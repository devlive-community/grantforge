---
title: MySQL proxy agent
description: Deploy the MySQL network proxy, configure TLS and policy sync, and verify column permissions, masking, row filters and denial errors.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The MySQL agent is an independent network proxy: clients connect to it in place of the server, and it checks supported statements against the `mysql` data service's signed policies before they reach MySQL. It needs no installation inside MySQL. The distribution ships a runnable JAR, `agents/mysql/grantforge-agent-mysql-<version>.jar`, which requires Java 17 or later.

The repository's Testcontainers integration tests target `mysql:8.0` and `mysql:8.4` with MariaDB Connector/J and MySQL Connector/J clients. This does not certify other server versions or every MySQL protocol feature. See [MySQL](/en/plugins/mysql/) for the service plugin's version and connection requirements.

## How it works

- **Sign-in passes through.** The server's greeting reaches the client and the client's answers reach the server, so MySQL itself checks passwords (`caching_sha2_password` and `mysql_native_password`); the proxy learns the user MySQL accepted. Policies name that user, without its host.
- **SQL resources are checked.** The proxy parses COM_QUERY and server-side prepared statements and checks the access types required on their databases, tables and columns. A refused statement never reaches MySQL; the proxy generates an error with a MySQL-compatible code, such as 1142 or 1143.
- **Reads are masked and filtered.** Where a statement reads a table with masked columns or a row filter for the user, the proxy puts a derived table of the same name in its place, which shows the masked values and only the filtered rows, so joins, conditions and functions all see what the user may see.
- **Resource authorization events** go to GrantForge with the user, client address, resource, access type, decision and statement. The audit scope is described below.
- MySQL's execution results are relayed to the client; the proxy does not change the original values stored in the database.

## Deployment

1. Add a `mysql` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only the proxy's account can read.
2. Create a metadata user for the proxy. It reads table columns and stored functions from `information_schema` and needs no data: `REFERENCES` shows every column, `SHOW_ROUTINE` every stored function. Grant them directly to the user, not through a role; the proxy does not start without `SHOW_ROUTINE`.

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. Write the settings file, replacing the example addresses, paths and names:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   This example enables TLS from clients to the proxy and from the proxy to MySQL. `mysql-proxy.crt` must match the proxy hostname clients use; MySQL's certificate must match `mysql.internal`. These paths are on the proxy machine, and the cache directory must be writable. Password files are read as UTF-8 and trimmed, so passwords with leading or trailing whitespace cannot be stored this way.

4. Start the proxy, for example as a systemd service:

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   Replace `<version>` with the distribution version. Settings supplied as `java -Dgrantforge.…=… -jar …` override the file. The proxy stops cleanly on SIGTERM and sends or spools pending events.
5. Point clients at the proxy's address and port. MySQL now sees the proxy's address, so the accounts' host part must allow it.
6. **Close the way around the proxy:** clients that can reach MySQL directly are not checked. Let only the proxy and administrators reach the server's port.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this proxy and a writable directory for the signed snapshot and spooled events |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.proxy.host`, `grantforge.proxy.port` | Listen address; all addresses and port `3307` by default. Port range `0..65535`; `0` assigns a port automatically |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | PEM certificate and private key, both required together to offer TLS to clients |
| `grantforge.proxy.tls.required` | Default `false`; `true` refuses clients without TLS (error 3159) and requires the certificate and private key |
| `grantforge.mysql.host`, `grantforge.mysql.port` | Required MySQL host; port defaults to `3306`, range `1..65535` |
| `grantforge.mysql.tls` | TLS to MySQL: `disabled` (default), `required` (encrypted, unverified), `verify-ca` or `verify-identity` |
| `grantforge.mysql.tls.ca` | PEM CA that verifies MySQL; empty trusts what Java trusts |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | Required metadata user; the password file is optional, and omitting it passes no password to the driver |
| `grantforge.mysql.metadata.cache.seconds` | Column and stored-function cache lifetime, default `30`, range `0..86400` seconds; `0` disables cache reuse |
| `grantforge.mysql.connect.timeout.ms` | MySQL connection timeout, default `10000`, range `1..600000` milliseconds; metadata reads have a fixed `30000` millisecond timeout |
| `grantforge.proxy.check.threads` | Statement-checking threads, default `8`, range `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds, range `1..2147483647` |
| `grantforge.refresh.interval.ms` | Initial or fallback refresh interval while the server is unreachable, default `30000` milliseconds, minimum `1000`; normal heartbeats use the interval returned by the server |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | Default batch `500`, queue `10000` events; batch range `1..1000`, queue at least the batch size and at most `1000000` |
| `grantforge.audit.flush.interval.ms` | Audit shipping interval, default `5000` milliseconds, range `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | Audit spool limit, default `67108864` (64 MiB), minimum `0` |

TLS on the two sides is independent. When only one side uses it and MySQL asks for the whole password (`caching_sha2_password` after a cache miss), the proxy carries it across: it answers a plain client's request for the server's key with an RSA key of its own, and encrypts a TLS client's password with MySQL's key for a plain server. Clients must not be configured with MySQL's public key file through the proxy.

## What statements need

| Statement | Access |
| --- | --- |
| `SELECT`, subqueries, `WITH`, `EXPLAIN`, `SET @x = (SELECT …)` | `select` on every column used; a table read without a column, as `COUNT(*)` or `EXISTS` do, needs `select` on the whole table |
| `INSERT`, `LOAD DATA` | `insert` on the columns written (all columns when none are listed); `ON DUPLICATE KEY UPDATE` adds `update` |
| `REPLACE` | `insert` on the columns and `delete` on the table |
| `UPDATE` | `update` on the columns set and `select` on the columns read |
| `DELETE` | `delete` on the table and `select` on the columns read |
| `CREATE TABLE`, `CREATE VIEW` / `DROP TABLE`, `DROP VIEW`, `TRUNCATE` / `ALTER TABLE`, `ALTER VIEW`, `OPTIMIZE` | `create` / `drop` / `alter` on the table or view |
| `RENAME TABLE` | `alter` and `drop` on the old name, `create` and `insert` on the new one |
| `CREATE INDEX`, `DROP INDEX` | `index` on the table |
| `CREATE DATABASE`, `DROP DATABASE` | `create`, `drop` on the database |
| `LOCK TABLES`, `ANALYZE`, `CHECK`, `CHECKSUM TABLE` | `select` on the table |

Statements that use no data pass on to MySQL's own grants: transactions, `SET`, `USE`, `SHOW`, `DESCRIBE`, `KILL` and account statements such as `GRANT` and `CREATE USER`. `information_schema` and `performance_schema` are databases like any other and need policies.

The proxy refuses statements it cannot fully check: executable comments (`/*! … */`), stored functions and `CALL`, `CREATE PROCEDURE`, `FUNCTION`, `TRIGGER` and `EVENT`, SQL-level `PREPARE`/`EXECUTE`, `HANDLER`, replication and clone commands, `USE` or `SET NAMES` together with other statements in one batch, and statements it cannot parse.

## Masks, row filters and their limits

- Masks and filters apply wherever a statement reads the table, including subqueries, joins, `INSERT … SELECT` and `CREATE TABLE … SELECT` (whose copy holds the masked values).
- A single-table `UPDATE` or `DELETE` changes only the rows the filter shows. Multi-table `UPDATE` or `DELETE` of a filtered table is refused, as is a write that uses a masked column of the table it changes, since it could copy or probe the hidden values.
- A view over a masked or filtered table cannot be created or altered: MySQL runs the view later, without the proxy.
- Prepared statements reuse their check and rewrite while the policy version is unchanged. A version change triggers another check: execution continues if access is still allowed and the rewritten text is unchanged; otherwise error 1615 requires the client to prepare again.
- A column named without its table counts for every table in sight of it that has such a column; qualify columns to avoid denials caused by a restricted column of the same name.
- Columns read through `*` come from the metadata user's view of `information_schema`, cached for `grantforge.mysql.metadata.cache.seconds`. Successful DDL sent through the proxy as a regular SQL command invalidates the cache, so the next lookup reads it again. External or prepared DDL is not guaranteed to invalidate it on execution; wait for cache expiry or restart the proxy.

## Enforcement scope

The proxy checks resources it can parse in client SQL. It does not recursively expand existing views, triggers or other operations executed internally by the server. To protect underlying tables, also restrict native MySQL grants and use of existing views and triggers. Do not give business users administrator privileges and expect the proxy to intercept administrative statements.

A row filter is not a general write constraint: `INSERT`, `REPLACE` and `LOAD DATA` check write permissions but do not automatically require new rows to satisfy the filter expression. The existing-row update branch of `INSERT … ON DUPLICATE KEY UPDATE` also has no row-filter condition added. Protocol compression, ZSTD, query attributes and optional result metadata are disabled during the handshake.

## Policies and failures

- Without a usable snapshot, or with a token bound to a service that is not `mysql`, every statement that uses data is refused.
- If GrantForge is temporarily unreachable or a new snapshot's signature is invalid, the proxy keeps the snapshot already applied. An initial startup without any usable snapshot refuses resource access.
- Disabling the service skips GrantForge resource authorization, masking and row filtering; SQL parsing and protocol restrictions still apply. Disabling the service does not block data access.
- MySQL's grants still apply after the proxy's checks; the proxy can only narrow them.
- `COM_CHANGE_USER` is followed, and refused when the client and the server sides differ in TLS; connect again as the other user instead.

## Verification example

In a separate test database, have a database administrator create the following data and account. Replace `proxy-host` with the proxy source address MySQL actually sees:

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

Create three GrantForge policies for `alice`: allow `select` on `database=grantforge_demo`, `table=orders`, `column=*`; mask `card` with `mask_show_last_4`; filter the table with `region = 'EU'`. Keep the service and all three policies enabled. Wait until **Data permissions → Agents** shows that the latest policy version has been applied.

Connect to the proxy using the MySQL command-line client. `-p` prompts for the password; the CA file must trust the proxy certificate:

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- One row only: 1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000: GrantForge does not grant update
```

Native MySQL grants already allow `UPDATE` in this example, so its refusal verifies that the proxy enforces the policy. Next, change the row filter to `region = 'US'`, wait for the proxy to apply the new version and query again. Only row 2 should appear, with card `xxxxxxxxxxxx4444`.

## Troubleshooting and audit

| Error code / SQLSTATE | Meaning and checks |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | Database, table or column access denied. For messages containing `by GrantForge`, check the policy user, resources, access type and snapshot version; also check native MySQL grants for other denials |
| `1046 / 3D000` | No database selected; use `USE`, a default database in the connection, or a qualified table name |
| `1227 / 42000` | An operation is prohibited or cannot be checked safely; use the message to check statement restrictions and metadata privileges |
| `1064 / 42000` | SQL cannot be parsed |
| `1615 / HY000` | Prepare the statement again after a policy change |
| `3159 / HY000` | The proxy requires TLS but the client connected without it |
| `1043 / 08S01` | Backend protocol or TLS handshake failure; check the TLS mode, certificates, hostname and server capabilities |
| `2003 / HY000` | The proxy cannot connect to MySQL; check the address, network and backend status |

Under **Data permissions → Access audit**, find the allow and deny events for `alice` and the corresponding resources. One SQL statement can produce multiple resource authorization events. They are recorded before backend execution, so `ALLOWED` does not mean MySQL executed successfully. Parse failures, statements without resource access and disabled services may produce no resource events; repeated executions of a prepared statement at the same policy version do not repeat authorization events. This is resource authorization auditing, not a complete SQL execution log.

## Related guides

- [MySQL](/en/plugins/mysql/)
- [Data services, policies and agents](/en/external/data-services/)
