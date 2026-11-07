---
title: MongoDB agent
description: Run the MongoDB agent beside a deployment to turn GrantForge policies into MongoDB's own roles, replace a role's privileges in one step, read built-in and custom roles outside GrantForge for what they give, and report accesses from the audit log or the profiler.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The MongoDB agent is a service beside the deployment. It keeps MongoDB's own access control in step with the signed policies of a `mongodb` data service: one role per user that holds exactly what the policies give the user. MongoDB then checks every command itself, and the agent stands in no client's way. The distribution ships it as the runnable JAR `agents/mongodb/grantforge-agent-mongodb-<version>.jar`; it needs Java 17 or later.

The repository's Testcontainers integration tests run the agent, and the JAR as shipped, against replica sets of MongoDB `6.0`, `7.0`, `8.0` and `9.0` and a `9.0` sharded cluster, and read the audit log of Percona Server for MongoDB `7.0` and `8.0`. This does not certify every other release; the agent refuses deployments older than `6.0`. For the service plugin see [MongoDB](/en/plugins/mongodb/).

## How it works

- **One role per user holds what GrantForge gives.** For each user the policies give something, the agent keeps a role named after it in `admin`, `grantforge_alice` for `alice`, and grants it to every MongoDB user of that name, such as `alice@admin` and `alice@sales`. `rolesInfo` shows what GrantForge gives `alice`. The agent changes no other role.
- **A change happens in one step.** `updateRole` replaces a role's privileges at once, so a user never holds both the old and the new, or neither.
- **Whole databases where nothing below is decided otherwise.** An access type a policy allows on all of a database, with no policy that could take it away below it, is granted on the database, so collections created later have it at once. Otherwise it is granted collection by collection, and new collections are covered at the next sync.
- **Never more than the policies allow.** MongoDB's privilege to drop a database is the privilege to drop every collection in it. The agent grants it only where the policies give that too; otherwise it withholds it and logs, once, which user cannot do what and why.
- **The policies of the present moment.** Each sync plans from the policies as they apply at that moment, so validity periods start and end on time, and from the users, databases and collections MongoDB has, so new ones are covered at the next sync.
- **MongoDB roles are groups.** A policy for a group applies to users that hold a MongoDB role of that name, together with the groups GrantForge puts them in.
- **Changes by hand are undone.** At every sync the agent compares its roles with the plans and sets right privileges changed by hand, roles they were made to inherit, grants of a role to someone else, and roles nobody needs. When nothing changed, it sends nothing.
- **Roles outside GrantForge are read for what they give.** MongoDB adds up what all of a user's roles give, so the agent cannot take away what a user holds through another role. MongoDB says what every role gives, built-in and inherited ones too, so the agent warns, once each, of a role such as `readWrite@hr` that gives what a policy denies, naming the user, the access and the policy. It also warns of users who can administer users, and so give themselves anything, of users with `root` or every action on every resource, and of a server that checks no authorization at all.
- **Accesses come from the audit log or the profiler.** See Reporting accesses below. Each event says whether GrantForge decided, that is whether the policies decide as MongoDB did, or a role outside it allowed the command. An aggregation is reported for each collection its `$lookup`, `$graphLookup` and `$unionWith` stages read and its `$out` and `$merge` stages write. Events name the command and its namespace, never its arguments, which can hold documents and passwords.

## Deployment

1. Add a `mongodb` data service and its policies. Open **Data authorization → Agents**, choose the service and issue an agent token. It is shown once, so save it in a file only the agent's account can read.
2. Create the agent's MongoDB user. It creates and changes roles and reads users and roles, with `userAdminAnyDatabase`; lists collections, with a role that holds only that; and reads the server's options and the profiler, with `clusterMonitor`. `userAdminAnyDatabase` can give itself anything, so guard this user as you guard an administrator. The name of the listing role must not start with the agent's role prefix.

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. To report accesses, set up one of the sources under Reporting accesses below.
4. Write a settings file, changing the example addresses, paths and names:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   These paths are on the agent's machine, and the cache directory must be writable. The password file is read as UTF-8, with surrounding white space removed.
5. Start the agent, for example as a systemd service:

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   Replace `<version>` with the distribution's version. Settings given as `java -Dgrantforge.…=… -jar …` take precedence over the file. On SIGTERM the agent stops cleanly and sends or spools pending events; MongoDB keeps what it was given.
6. **Move existing grants to policies:** roles granted outside GrantForge keep applying beside the agent's. Create the matching policies, then revoke those roles. The agent's warnings point out those that give what a policy denies.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token file, this agent's name, and a writable directory for signed snapshots, spooled events and how far accesses were read |
| `grantforge.signing.key.file` | Optional file that pins the snapshot signing key. Copy the Base64-encoded X.509 Ed25519 public key from the agents page. If omitted, it is fetched from the server at the first connection |
| `grantforge.mongodb.uri` | Required: the connection string, with no credentials in it |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256` (default), `SCRAM-SHA-1`, `MONGODB-X509`, `PLAIN` or `GSSAPI` |
| `grantforge.mongodb.user`, `grantforge.mongodb.password.file`, `grantforge.mongodb.auth.source` | The agent's user, with `GSSAPI` its principal and with `MONGODB-X509` optional; its password; and for SCRAM the database it is defined in, `admin` by default |
| `grantforge.mongodb.tls.ca.file`, `grantforge.mongodb.tls.certificate.key.file`, `grantforge.mongodb.tls.certificate.key.password.file` | With `tls=true` in the connection string: the PEM CA certificates that verify the servers, empty for what Java trusts; one PEM file with the client certificate and its key; and the password of an encrypted key |
| `grantforge.mongodb.kerberos.keytab`, `grantforge.mongodb.kerberos.service.name` | `GSSAPI`: the principal's keytab, otherwise the password is used, and the service name of the servers' principal, `mongodb` by default. The realm comes from the machine's `krb5.conf` |
| `grantforge.mongodb.timeout.ms` | How long finding a server, connecting and a command may take, `30000` by default, in the range `1..600000` milliseconds |
| `grantforge.mongodb.role.prefix` | What the agent's role names start with, `grantforge_` by default. Letters, digits and underscores, ending in an underscore, at most 32 characters. Every role of `admin` whose name starts with it belongs to the agent |
| `grantforge.mongodb.unmanaged.users` | Users the agent leaves alone, separated by commas, such as service accounts. It always leaves alone its own user and users with `root`, `__system` or every action on every resource |
| `grantforge.mongodb.databases` | The databases the agent grants by collection, separated by commas, empty for all. The others get only what can be granted on them as a whole, so that a database with very many collections is not listed at every sync |
| `grantforge.mongodb.audit.files` | The audit log files to read, separated by commas. Empty reads the profiler |
| `grantforge.sync.interval.ms` | How often the agent brings MongoDB in step, `10000` by default, in the range `1000..3600000` milliseconds |
| `grantforge.audit.log` | `true` by default. `false` reads no accesses and reports none |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | How far behind the present the profiler is read (`2000` by default), how often accesses are read (`5000` milliseconds by default), and the most entries one read takes of a file or database (`1000` by default) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connect and read timeouts, `5000` / `8000` milliseconds by default |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, `30000` by default, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (`500` by default), queue size (`10000` by default), send interval (`5000` milliseconds by default) and disk spool limit (64 MiB by default) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, `10000` by default, at most `3600000` milliseconds. `0` sends each |

## Reporting accesses

| Source | Where | What it tells | How to turn it on |
| --- | --- | --- | --- |
| Audit log | MongoDB Enterprise and Percona Server for MongoDB | What was allowed and what was refused, on every member whose file the agent can read | Start `mongod` with `--auditDestination file --auditFormat JSON --auditPath <path>` and `--setParameter auditAuthorizationSuccess=true`, and name the files in `grantforge.mongodb.audit.files` |
| Profiler | Every edition | What ran on the member the agent talks to, the primary of a replica set; no refusals | `db.setProfilingLevel(2)` in each database to report. It costs the server, so the agent never turns it on, and `system.profile` is a small capped collection |

The audit log is read from files, so run the agent on the node or give it a directory that collects them. The tests read Percona Server's audit log, which has the format of MongoDB Enterprise's. A `mongos` has no profiler, so a sharded cluster reports accesses only from the audit log.

| Command | Access event |
| --- | --- |
| `find`, `aggregate`, `count`, `distinct`, `listIndexes` | `read` on each collection read |
| `aggregate` opening with `$changeStream` | `change_stream` |
| `insert`, `update`, `delete`, `findAndModify`, `bulkWrite` | `insert`, `update` or `remove` on each collection written; `bypass_validation` too when asked for |
| `createIndexes`, `dropIndexes` | `create_index`, `drop_index` |
| `collMod`, `validate`, `convertToCapped` | `alter` |
| `drop`, `create`, `dropDatabase` | `drop`, `create`, `drop_database` |
| `listDatabases`; `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `currentOp` | `list_databases`; `monitor` |

MongoDB refuses a command as a whole without saying which of its collections was the reason, so a refusal is reported for those the policies deny, or for the first when they deny none.

## Users, groups and roles

Policies name MongoDB users by their user name. A name defined in several databases is one user, and the agent's role is granted to all of them. Groups are the MongoDB roles a user holds and the groups GrantForge puts it in. Roles are the roles GrantForge gives the user.

## Decisions and limits

- Without a snapshot the agent changes nothing. A token bound to a service not in use, or to a service whose type is not `mongodb`, grants nothing: the agent drops its roles, and MongoDB's own remain.
- While the agent is not running MongoDB keeps what it last received, and policy changes wait for it. A change reaches MongoDB within the refresh and sync intervals; a `mongos` applies it once its user cache has expired, by default within 30 seconds (`userCacheInvalidationIntervalSecs`).
- MongoDB's privileges name databases and collections exactly, so a wildcard in a policy is granted for what exists. A database exists once it has a collection: until then, `create` on a database that a policy names has nothing to be granted on.
- Users defined nowhere in MongoDB, such as those LDAP authorization maps to roles by itself, are not seen, and the agent keeps no role for them.
- A privilege on a database as a whole covers its collections but not its `system.` collections, and MongoDB's own databases are left to MongoDB.
- Renaming collections and MongoDB's other administrative actions are no access types: they stay with roles outside GrantForge.
- MongoDB filters no rows and masks no fields by role; a [view](/en/plugins/mongodb/) gives a restricted reading.

## Related guides

- [MongoDB](/en/plugins/mongodb/)
- [Data services, policies and agents](/en/external/data-services/)
