---
title: Redis agent
description: Run the Redis agent beside a Redis or Valkey deployment to keep each user's ACL selectors on every node in step with GrantForge policies, replaced in one command, ask Redis with ACL DRYRUN what permissions outside GrantForge allow, and report refusals from the nodes' ACL logs.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Redis agent is a service beside the deployment. It keeps Redis's own ACL in step with the signed policies of a `redis` data service on every node, the replicas and every node of a cluster too, as Redis keeps an ACL on each. Redis then checks every command itself, and the agent stands in no client's way. The distribution ships it as the runnable JAR `agents/redis/grantforge-agent-redis-<version>.jar`; it needs Java 17 or later.

The repository's Testcontainers integration tests run the agent, and the JAR as shipped, against a primary with a replica of Redis `7.2`, `7.4`, `8.2` and `8.10` and Valkey `8.1` and `9.1`, and a Redis `8.10` cluster. This does not certify every other release; the agent refuses Redis older than `7.2`, whose ACL log numbers no entries. For the service plugin see [Redis](/en/plugins/redis/).

## How it works

- **The selectors are GrantForge's, the root permissions the operator's.** Redis allows a command when a user's root permissions or one of its selectors allow it. The agent keeps every selector of the users it looks after and never touches their root permissions or passwords: one selector holds the keys the user may read, write or both, one the channels it may subscribe to, one those it may publish to, and on the nodes it may monitor one more.
- **A change happens in one command.** `ACL SETUSER user clearselectors (…)` replaces all of a user's selectors at once on a node, so a user never holds both the old and the new, or neither; selectors changed by hand are set right the same way at the next sync.
- **Patterns, granted only where they hold.** A policy's key or channel pattern is granted as Redis's own pattern where the policies decide the user alike across it; the agent cuts the pattern along every policy that overlaps it and decides each part, so another user's policy does not hold it back. A pattern policies decide differently within is withheld and logged, once.
- **Nothing dangerous, no listing of others' keys.** The selectors never give what Redis counts as dangerous, and give `SCAN` and `RANDOMKEY` only to a user that may read every key.
- **Every node, saved where it can be.** The agent finds the replicas and the cluster's nodes from the nodes given and brings each in step. A node that keeps its ACL in a file saves it with `ACL SAVE`; one that does not loses the selectors when it restarts and gets them again at the next sync.
- **Redis is asked what it allows.** Root permissions decide beside the agent's selectors. Rather than read their rules, the agent asks Redis with `ACL DRYRUN` whether a user may run a command on a key, channel or node a policy denies, and warns, once each, naming the user, the access and the policy. It also warns of users no selector binds, with every command on every key, and of users anyone may sign in as without a password.
- **Refusals come from each node's ACL log.** See Reporting refusals below.
- **The policies of the present moment.** Each sync plans from the policies as they apply at that moment, so validity periods start and end on time, and from the users and nodes Redis has.

## Deployment

1. Add a `redis` data service and its policies. Open **Data authorization → Agents**, choose the service and issue an agent token. It is shown once, so save it in a file only the agent's account can read.
2. Create the agent's ACL user on every node. It reads users, sets their selectors, saves the ACL file, reads the ACL log, asks dry runs and finds the cluster's nodes. `+acl|setuser` can give itself anything, so guard this user as you guard an administrator.

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. Keep the root permissions of the users the agent looks after to what every user needs, such as signing in; the agent gives the rest. Root permissions that give more are found and warned of.

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. Write a settings file, changing the example addresses, paths and names:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   These paths are on the agent's machine, and the cache directory must be writable. The password file is read as UTF-8, with surrounding white space removed.
5. Start the agent, for example as a systemd service:

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   Replace `<version>` with the distribution's version. Settings given as `java -Dgrantforge.…=… -jar …` take precedence over the file. On SIGTERM the agent stops cleanly and sends or spools pending events; Redis keeps what it was given.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this agent and a writable directory for the signed snapshot, spooled events and the audit log's read position |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.redis.nodes` | Required: the nodes as `host:port`, the port `6379` unless given |
| `grantforge.redis.discover` | `true` by default: the rest of a cluster and a primary's replicas are found from the nodes given. `false` takes the nodes given and no others, where the addresses nodes announce cannot be reached from the agent |
| `grantforge.redis.user`, `grantforge.redis.password.file` | The agent's ACL user, empty for the default user, and its password |
| `grantforge.redis.tls.mode`, `grantforge.redis.tls.ca.file`, `grantforge.redis.tls.certificate.file`, `grantforge.redis.tls.key.file`, `grantforge.redis.tls.key.password.file` | `disable` (default), `verify-ca` or `verify-full`; the PEM CA certificates that verify the servers, empty for what Java trusts; a PEM client certificate, its key when in a file of its own, and the key's password |
| `grantforge.redis.timeout.ms` | How long connecting and a command may take, `10000` by default, in the range `1..600000` milliseconds |
| `grantforge.redis.unmanaged.users` | Users the agent leaves alone, separated by commas, such as service accounts. It always leaves alone its own user and users with every command on every key |
| `grantforge.sync.interval.ms` | How often the agent brings Redis in step, default `10000`, range `1000..3600000` milliseconds |
| `grantforge.audit.log`, `grantforge.audit.log.interval.ms` | `true` by default; `false` reads no ACL log. How often the ACL logs are read, `5000` milliseconds by default |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), send interval (default `5000` milliseconds) and the spool limit on disk (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` sends each |

## Reporting refusals

Redis keeps no record of what it allowed, so the agent reports what it refused, from the ACL log of each node: who was refused what, from where and when, and whether the policies decide as Redis did. Redis folds a refusal repeated within a minute into the entry before it; the agent reports an entry again each time it is updated.

| Logged reason | Access event |
| --- | --- |
| `key` | `read` or `write` on the key, as Redis flags the command |
| `channel` | `subscribe` or `publish` on the channel |
| `command` | `monitor` on the node for the commands of monitoring; other commands are logged without their keys and are left out |
| `auth` | Left out: a refused sign-in names no access |

## Users, groups and roles

Policies name Redis's ACL users by name. Redis has no roles or groups of its own, so groups are those GrantForge puts users in.

## Decisions and limits

- Without a snapshot the agent changes nothing. A token bound to a service not in use, or to a service whose type is not `redis`, grants nothing: the agent clears the selectors of the users it looks after.
- While the agent is not running Redis keeps what it last received, and policy changes wait for it. A change reaches Redis within the refresh and sync intervals.
- The agent keeps every selector of the users it looks after: a selector the operator adds by hand is removed at the next sync. Give extra permissions in root permissions, or leave the user to the operator.
- Redis's ACL names keys and channels, not databases, so a pattern covers its keys in every database of a node.
- A refused command the user may not run at all is logged by Redis without its keys, and is not reported.
- Redis filters nothing within a value and masks nothing.

## Related guides

- [Redis](/en/plugins/redis/)
- [Data services, policies and agents](/en/external/data-services/)
