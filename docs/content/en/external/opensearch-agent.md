---
title: OpenSearch agent
description: Run the OpenSearch agent beside the cluster, so that GrantForge policies become the security plugin's own roles, with field level security, masked fields and document level security, index patterns stay patterns so that new indices are reachable at once, and accesses are reported from the audit log.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The OpenSearch agent is a service beside the cluster. It keeps the security plugin's own access control in step with the signed policies of an `opensearch` data service: one role per user that holds exactly what the policies give the user, mapped to that user alone, with field level security, masked fields and document level security. OpenSearch then checks every request itself, and the agent never stands between clients and the cluster. The distribution ships it as a runnable JAR, `agents/opensearch/grantforge-agent-opensearch-<version>.jar`, which requires Java 17 or later.

The repository's Testcontainers integration tests run the agent, and the JAR as shipped, against OpenSearch `2.19`, `3.3`, `3.6` and `3.9` with the security plugin. This does not certify every other release; the agent refuses clusters older than `2.19` and clusters without the security plugin. See [OpenSearch](/en/plugins/opensearch/) for the service plugin.

## How it works

- **One role per user holds what GrantForge gives.** For each user the policies give something, the agent keeps a role named after it, `grantforge_alice` for `alice`, mapped to that user alone. Its description marks it as the agent's, and the agent takes no role without that mark for one of its own, so it changes no other role or mapping.
- **Index patterns stay patterns.** A policy's index pattern, such as `logs-*`, is granted as a pattern wherever every policy decides the same across it: every policy of any kind whose indices overlap the pattern covers it whole. The agent decides this exactly, with finite automata of the patterns. Indices created later, such as tomorrow's logs, are then reachable at once, also when the first document written creates them. Where a policy decides otherwise within a pattern, such as a deny of `logs-secret` within `logs-*`, the agent grants each index and data stream the cluster has by name, and new ones at the next sync.
- **Field level security only where something below is decided otherwise.** An index the user may read as a whole, with no policy that could take a field away, is read without field level security, so fields added later are read at once. Otherwise the user reads the fields a policy allows, of those its documents have and those the policies name; multi-fields such as `name.keyword` follow their field.
- **Masks and row filters are the security plugin's own.** A mask becomes a masked field of the entry, and a row filter its document level security query. OpenSearch's validate API checks each query before it is granted; a query OpenSearch would not run shows the user no documents, and is logged. A mask the security plugin does not know hides the field instead.
- **One entry per set of permissions.** Indices and patterns with the same permissions share an entry; entries never overlap, as the security plugin combines the field and document level security of overlapping entries.
- **The cluster's composite operations come with reading and writing.** Reading brings `cluster_composite_ops_ro`, for multi-search, multi-get and scrolling, and writing `cluster_composite_ops`, for bulk.
- **Changes are one patch.** The agent writes its roles and mappings as JSON patches, which the security plugin applies at once across the cluster: roles before the mappings that name them, mappings before the roles they name are removed. A patch the security plugin refuses is applied change by change, so that one bad role fails alone.
- **The policies of the present moment.** Each sync plans from the policies as they apply at that moment, so validity periods start and end on time.
- **Backend roles are groups.** A policy for a group applies to internal users that have a backend role of that name, together with the groups GrantForge puts users in.
- **Changes made by hand are undone.** The agent compares its roles and mappings with what the security plugin holds at each sync; what differs is written again. When nothing changed, the agent sends nothing.
- **Roles outside GrantForge are reported.** OpenSearch grants what any of a user's roles gives, so the agent cannot take away what another role mapped to the user gives. It warns once of each such role that gives what a policy denies, naming the role, the user, the access, the index and the policy, and of users mapped to `all_access`, whom no role binds.
- **Accesses come from the audit log.** The agent reads the security plugin's audit log from the indices it writes, `security-auditlog-*`: what it refused, as `MISSING_PRIVILEGES`, and what it allowed, as `GRANTED_PRIVILEGES` when the audit configuration logs those. Each event names the user, the access type, each index or data stream reached, or the cluster, and says whether GrantForge decided it or a role outside GrantForge allowed it.

| Action | Access event |
| --- | --- |
| `indices:data/read/*`: search, get, count | `read` on each index or data stream |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/aliases*`, `indices:monitor/*` | `manage_aliases`, `monitor` |
| Other `indices:admin/*` | `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` and `repository/*` | `cluster_monitor`, `manage_snapshots` on the cluster |
| Index and component templates, ingest pipelines | `manage_templates`, `manage_pipelines` on the cluster |

## Deployment

1. Add an `opensearch` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only the agent's account can read.
2. Give the agent a user that may change the security plugin's configuration: a user mapped to `all_access` while `plugins.security.restapi.roles_enabled` lists it, or the admin certificate named by `plugins.security.authcz.admin_dn`. The tests run the agent as the demo configuration's `admin`, and with its admin certificate.
3. To report accesses, keep the audit log in the cluster (`plugins.security.audit.type: internal_opensearch`). For what OpenSearch allowed, also take `GRANTED_PRIVILEGES` out of the disabled categories of the audit configuration; denials are logged by default.
4. Write the settings file, replacing the example addresses, paths and names:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   These paths are on the agent's machine, and the cache directory must be writable. Password files are read as UTF-8 and trimmed.
5. Start the agent, for example as a systemd service:

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   Replace `<version>` with the distribution version. Settings supplied as `java -Dgrantforge.…=… -jar …` override the file. The agent stops cleanly on SIGTERM and sends or spools pending events; OpenSearch keeps what it was given.
6. **Move existing roles into policies:** roles mapped to users outside GrantForge keep applying next to the agent's. Create the matching policies, then remove those mappings; the agent's warnings name the roles that give what a policy denies.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this agent and a writable directory for the signed snapshot, spooled events and the audit log's read position |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.opensearch.hosts` | Required: the nodes as URLs, tried in turn when one cannot be reached; without a scheme a node is `https`, and the port defaults to `9200` |
| `grantforge.opensearch.user`, `grantforge.opensearch.password.file` | The agent's user and its password's file |
| `grantforge.opensearch.tls.cert`, `grantforge.opensearch.tls.key` | Instead: PEM client certificate and its private key, together, to sign in with a certificate; `grantforge.opensearch.tls.key.passphrase.file` holds the passphrase of an encrypted key |
| `grantforge.opensearch.ssl.verification.mode` | `full` (default) checks the nodes' certificates and names, `certificate` their certificates only, `none` nothing |
| `grantforge.opensearch.tls.ca` | PEM CA certificates that verify the nodes; empty trusts what Java trusts |
| `grantforge.opensearch.timeout.ms` | How long connecting and a request may take, default `30000`, range `1..600000` milliseconds |
| `grantforge.opensearch.role.prefix` | The start of the agent's role names, default `grantforge_`; lowercase letters, digits, hyphens and underscores, ending with an underscore, at most 32 characters. The agent owns the roles it marked whose names start with it |
| `grantforge.opensearch.unmanaged.users` | Users the agent leaves alone, separated by commas, such as service accounts; it always leaves its own user and users mapped to `all_access` alone |
| `grantforge.sync.interval.ms` | How often the agent brings OpenSearch in step, default `10000`, range `1000..3600000` milliseconds |
| `grantforge.audit.log` | Default `true`; `false` reads no audit log and reports no accesses |
| `grantforge.audit.log.index`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.documents` | The audit log's indices, default `security-auditlog-*`; how often it is read, default `5000` milliseconds; and the most documents one read takes, default `1000`, so that a backlog is read over several turns |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), send interval (default `5000` milliseconds) and the spool limit on disk (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` sends each |

## Users, groups and roles

Policies name OpenSearch users as the security plugin knows them. The agent keeps roles for the internal users, for the users the policies name, who may sign in through a directory such as LDAP or SAML, and for the users it kept roles for before; the security plugin's reserved and hidden users, such as `kibanaserver`, are left alone. Groups are an internal user's backend roles together with the groups GrantForge puts the user in; the backend roles a directory gives at sign-in are not known to the agent, so give such users their groups in GrantForge. Roles are the roles GrantForge gives the user.

## Decisions and limits

- Without a snapshot the agent changes nothing. A service that is not in use, or a token bound to a service whose type is not `opensearch`, grants nothing: the agent removes its roles and mappings, and the security plugin's own roles apply.
- While the agent does not run, OpenSearch keeps what it was given last; policy changes wait for it. A change reaches OpenSearch within the refresh interval and the sync interval.
- Indices granted by name, rather than through a pattern, get their permissions at the next sync.
- Aliases reach the indices behind them: searching an alias needs permissions on every index it holds, so policies name indices and data streams, not aliases.
- A row filter can test only fields the user may read, and a mask applies to values of text and keyword fields.
- Indices whose names start with a dot are reached only through policy patterns that start with a dot.
- The security plugin's tenants for OpenSearch Dashboards are not managed by the agent.

## Related guides

- [OpenSearch](/en/plugins/opensearch/)
- [Data services, policies and agents](/en/external/data-services/)
