---
title: Elasticsearch agent
description: Run the Elasticsearch agent beside the cluster, so that GrantForge policies become Elasticsearch's own roles, given to native users and mapped to users of other realms, with field and document level security where the license has them, index patterns stay patterns so that new indices are reachable at once, and accesses are reported from the audit log files.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Elasticsearch agent is a service beside the cluster. It keeps Elasticsearch's own access control in step with the signed policies of an `elasticsearch` data service: one role per user that holds exactly what the policies give the user, with field and document level security where the cluster's license has them. A native user holds the role among its own roles; a user of another realm, such as LDAP, SAML or OpenID Connect, gets it through a role mapping of its name. Elasticsearch then checks every request itself, and the agent never stands between clients and the cluster. The distribution ships it as a runnable JAR, `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`, which requires Java 17 or later.

The repository's Testcontainers integration tests run the agent, and the JAR as shipped, against Elasticsearch `8.19`, `9.3`, `9.4` and `9.5` with security on. This does not certify every other release; the agent refuses clusters older than `8.19`, clusters without security and OpenSearch clusters, which the [OpenSearch agent](/en/external/opensearch-agent/) keeps. See [Elasticsearch](/en/plugins/elasticsearch/) for the service plugin.

## How it works

- **One role per user holds what GrantForge gives.** For each user the policies give something, the agent keeps a role named after it, `grantforge_alice` for `alice`. Its metadata names the user and marks it as the agent's, and the agent takes no role without that mark for one of its own, so it changes no other role.
- **Native users carry the role; other realms get it by name.** The agent adds the role to a native user's roles and keeps every other role the user has. Role mappings apply to every realm but the native and file realms, so the agent also keeps a role mapping of the same name that gives the role to the user's name, for users who sign in through LDAP, Active Directory, SAML, OpenID Connect, JWT, Kerberos or PKI. The file realm cannot be changed through Elasticsearch's API; its users get no role.
- **Index patterns stay patterns.** A policy's index pattern, such as `logs-*`, is granted as a pattern wherever every policy decides the same across it: every policy of any kind whose indices overlap the pattern covers it whole. The agent decides this exactly, with finite automata of the patterns. Indices created later, such as tomorrow's logs, are then reachable at once, also when the first document written creates them. Where a policy decides otherwise within a pattern, such as a deny of `logs-secret` within `logs-*`, the agent grants each index and data stream the cluster has by name, and new ones at the next sync.
- **Field level security only where something below is decided otherwise.** An index the user may read as a whole, with no policy that could take a field away, is read without field level security, so fields added later are read at once. Otherwise the user reads the fields a policy allows, of those its documents have and those the policies name, each with its multi-fields, such as `name.keyword` with `name`.
- **Row filters are document level security.** A row filter becomes the entry's query. Elasticsearch's validate API checks each query before it is granted; a query Elasticsearch would not run shows the user no documents, and is logged.
- **Never more than the policies allow below Platinum.** Elasticsearch has document and field level security from a Platinum license on, and turns off every role that uses them below it. The agent reads the license at each sync. Below Platinum, a read that needs either, because a policy restricts fields or documents of the index, is not granted at all, and the agent logs which user cannot read which index and why; reading whole indices and everything else is granted as usual.
- **One entry per set of privileges.** Indices and patterns with the same privileges share an entry; entries never overlap, as Elasticsearch joins the field and document level security of overlapping entries.
- **The policies of the present moment.** Each sync plans from the policies as they apply at that moment, so validity periods start and end on time.
- **Native users' roles are groups.** A policy for a group applies to native users that hold a role of that name, together with the groups GrantForge puts users in.
- **Changes made by hand are undone.** The agent compares its roles, role mappings and native users' roles with what Elasticsearch holds at each sync; what differs is written again, and one user's role held by another is taken back. Roles are written before they are given, in one request, and taken back before they are removed. When nothing changed, the agent sends nothing; a change Elasticsearch refuses is logged and does not stop the others.
- **Roles outside GrantForge are reported.** Elasticsearch grants what any of a user's roles gives, so the agent cannot take away what another role gives. It warns once of each such role, held by a native user or given by a role mapping of the user's name, that gives what a policy denies, naming the role, the user, the access, the index and the policy, and of users with the `superuser` role, whom no role binds.
- **Accesses come from the audit log files.** Elasticsearch writes its audit log to a file on each node only. The agent reads the files it is given, from where the last read ended, follows them as Elasticsearch rolls them over, and reports `access_granted` and `access_denied` events. Each names the user, the access type, each index or data stream reached, or the cluster, and says whether GrantForge decided it or a role outside GrantForge allowed it.

| Action | Access event |
| --- | --- |
| `indices:data/read/*`: search, get, count | `read` on each index or data stream |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/delete`, `data_stream/delete` | `delete_index` |
| `indices:admin/get`, `mappings/get`, `settings/get`, `resolve/*` | `view_index_metadata` |
| `indices:monitor/*`; other `indices:admin/*` | `monitor`; `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` and `repository/*`, `slm/*` | `cluster_monitor`, `create_snapshot`, `manage_slm` on the cluster |
| Index and component templates, ingest pipelines, `cluster:admin/ilm/*` | `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` on the cluster |

## Deployment

1. Add an `elasticsearch` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only the agent's account can read.
2. Give the agent a user with the `manage_security` cluster privilege, which manages roles, role mappings and users, and the right to read every index's mappings and validate queries, such as a user with the `superuser` role. It may sign in with a password, an API key that user created, or a client certificate a PKI realm maps to it.
3. To report accesses, turn on audit logging (`xpack.security.audit.enabled: true`), which needs a Gold license or above, and let the agent read the nodes' audit log files, `<cluster name>_audit.json` in each node's log directory: run the agent on a node, or collect the files in a directory it can read. `xpack.security.audit.logfile.events.include: access_granted, access_denied` keeps the log to what the agent reports.
4. Write the settings file, replacing the example addresses, paths and names:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   These paths are on the agent's machine, and the cache directory must be writable. Password and key files are read as UTF-8 and trimmed.
5. Start the agent, for example as a systemd service:

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   Replace `<version>` with the distribution version. Settings supplied as `java -Dgrantforge.…=… -jar …` override the file. The agent stops cleanly on SIGTERM and sends or spools pending events; Elasticsearch keeps what it was given.
6. **Move existing roles into policies:** roles users hold outside GrantForge keep applying next to the agent's. Create the matching policies, then take those roles from the users; the agent's warnings name the roles that give what a policy denies.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this agent and a writable directory for the signed snapshot, spooled events and the audit log's read positions |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.elasticsearch.hosts` | Required: the nodes as URLs, tried in turn when one cannot be reached; without a scheme a node is `https`, and the port defaults to `9200` |
| `grantforge.elasticsearch.user`, `grantforge.elasticsearch.password.file` | The agent's user and its password's file |
| `grantforge.elasticsearch.api.key.file` | Instead: the file of an encoded API key, as Elasticsearch returns it when it creates one |
| `grantforge.elasticsearch.tls.cert`, `grantforge.elasticsearch.tls.key` | Instead: PEM client certificate and its private key, together, to sign in with a certificate; `grantforge.elasticsearch.tls.key.passphrase.file` holds the passphrase of an encrypted key |
| `grantforge.elasticsearch.ssl.verification.mode` | `full` (default) checks the nodes' certificates and names, `certificate` their certificates only, `none` nothing |
| `grantforge.elasticsearch.tls.ca` | PEM CA certificates that verify the nodes, such as Elasticsearch's `http_ca.crt`; empty trusts what Java trusts |
| `grantforge.elasticsearch.timeout.ms` | How long connecting and a request may take, default `30000`, range `1..600000` milliseconds |
| `grantforge.elasticsearch.role.prefix` | The start of the agent's role names, default `grantforge_`; lowercase letters, digits, hyphens and underscores, ending with an underscore, at most 32 characters. The agent owns the roles it marked whose names start with it |
| `grantforge.elasticsearch.unmanaged.users` | Users the agent leaves alone, separated by commas, such as service accounts; it always leaves its own user, Elasticsearch's reserved users and users with the `superuser` role alone |
| `grantforge.sync.interval.ms` | How often the agent brings Elasticsearch in step, default `10000`, range `1000..3600000` milliseconds |
| `grantforge.audit.log.files` | The audit log files to read, separated by commas; none reads no audit log and reports no accesses |
| `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.lines` | How often the files are read, default `5000` milliseconds, and the most lines one read takes of each, default `5000`, so that a backlog is read over several turns |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), send interval (default `5000` milliseconds) and the spool limit on disk (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` sends each |

## Users, groups and roles

Policies name Elasticsearch users by their user names. The agent keeps roles for the native users, for the users the policies name, who may sign in through another realm, and for the users it kept roles for before; Elasticsearch's reserved users, such as `kibana_system`, are left alone. Groups are a native user's other roles together with the groups GrantForge puts the user in; the groups a directory gives at sign-in are not known to the agent, so give such users their groups in GrantForge. Roles are the roles GrantForge gives the user.

## Decisions and limits

- Without a snapshot the agent changes nothing. A service that is not in use, or a token bound to a service whose type is not `elasticsearch`, grants nothing: the agent takes its roles back and removes them with their role mappings, and Elasticsearch's own roles apply.
- While the agent does not run, Elasticsearch keeps what it was given last; policy changes wait for it. A change reaches Elasticsearch within the refresh interval and the sync interval.
- Indices granted by name, rather than through a pattern, get their privileges at the next sync.
- Below Platinum a user whose read of an index a policy restricts reads none of it; below Gold the agent reports no accesses.
- A role mapping of a user's name applies to every realm with that user name; policies name users, not realms.
- Aliases reach the indices behind them, so policies name indices and data streams, not aliases. Indices whose names start with a dot are reached only through policy patterns that start with a dot.
- Kibana's spaces and application privileges are not managed by the agent.

## Related guides

- [Elasticsearch](/en/plugins/elasticsearch/)
- [Data services, policies and agents](/en/external/data-services/)
