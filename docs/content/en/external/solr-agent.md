---
title: Apache Solr agent
description: Install the authorization plugin in Solr nodes to decide with GrantForge policies who may query and update which collections, through which request paths, administer which collections and the cluster, and from which networks.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Solr agent is an authorization plugin of the nodes: Solr asks it about every request, and it answers from the `solr` data service's signed policies. The distribution ships it as one Java 11 JAR, `agents/solr/grantforge-agent-solr-<version>.jar`, with its dependencies embedded and relocated.

The repository's Testcontainers integration tests run the JAR inside SolrCloud nodes of Solr `9.8.1`, `9.10.1`, `9.11.0` and `10.0.0`. This does not certify every other release. The service plugin is described in [Apache Solr](/en/plugins/solr/).

## How it works

- **Requests are classified as Solr classifies them.** Each request handler names the permission it needs, such as `read` or `collection-admin-edit`, as it does for Solr's own rule-based plugin; the agent turns that name into an access type, and a handler that names none is decided by the request's type.
- **Every request to a collection is decided at its request path too.** The agent decides a query of `sales` through `/select` at collection `sales` and at `sales` with path `/select`: a denial at either refuses it, so a policy can deny one handler of a collection.
- **Teams administer their own collections.** A Collections API request that names collections needs `admin_collection` on each of them, so a policy can let a team create, reload or delete only the collections whose name starts with its prefix.
- **The cluster's administrators may do everything.** `admin` on the cluster grants every request, as Solr's own `all` permission does.
- **Policies can hold for some networks only.** Every check carries the client's address; behind proxies the agent trusts, the client is the last address of `X-Forwarded-For` that no trusted proxy added.
- **A JSON web token's verified roles count as groups.** With Solr's JWT authentication, the roles the token's claim gives the user are groups of the decision, beside those GrantForge puts the user in.
- **Refusals are logged with their reason.** Solr answers a refused request with its own 403; the node's log names the GrantForge policy that denied it, or that none allows it.
- **Decisions are kept and accesses folded.** The decisions of the current snapshot are kept, by default 100,000 for 10 seconds, and a new snapshot starts afresh. Identical allowed accesses within a window, by default 10 seconds, are reported as one access event saying how many there were; refusals are reported one by one at once. Every event names the collection and path, or the cluster, and the client address.
- **The nodes are not checked.** Requests between the nodes, which Solr signs as the node itself, pass; a request without a user is asked to sign in.

| Request | Access needed |
| --- | --- |
| Search, get, export, query through any read handler | `query` on the collection and path |
| Add, update or delete documents | `update` on the collection and path |
| Read or change a collection's schema | `schema_read` or `schema_edit` |
| Read or change a collection's config | `config_read` or `config_edit` |
| Collections API requests naming collections: create, reload, back up, restore, delete, modify, cluster status of them | `admin_collection` on each named collection |
| Health checks; metrics | `health`; `metrics` on the cluster |
| List collections, core status, ZooKeeper, file store and package reads | `admin_read` on the cluster |
| Read or change authentication and authorization | `security` on the cluster |
| Core administration, file store and package changes, and what no handler names | `admin` on the cluster |

## Deployment

1. Add a `solr` data service and configure its policies, including `admin` on the cluster for Solr's administrators. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only Solr's account can read.
2. Copy the JAR into `server/solr-webapp/webapp/WEB-INF/lib` of **every node**, such as `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`, and restart the nodes one at a time.
3. Set the authorization section of the cluster's `security.json`, beside an authentication plugin, replacing the example address, paths and instance name:

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   Upload it with `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>`; the nodes take it without a restart. Authentication must be enabled, so that requests have users.
4. **On Solr 9, mind Java's security manager**, which Solr enables by default: keep the token, the pinned key and the cache directory under `SOLR_HOME`, such as `/var/solr/data`, and grant the agent what it needs beyond Solr's own policy, in `server/etc/security.policy` or a policy file of your own that `-Djava.security.properties` adds as `policy.url.3`:

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10 runs without the security manager.
5. **Move existing permissions first:** the permissions and user roles of Solr's rule-based plugin no longer apply. Create matching policies beforehand. Until the agent has its first snapshot, requests other than those between the nodes are refused.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for the cluster's nodes and a writable directory for the signed snapshot and spooled events |
| `grantforge.trusted.proxies` | Proxies whose `X-Forwarded-For` the agent trusts, addresses or ranges separated by commas; none by default |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), sending interval (default `5000` milliseconds) and spool limit (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` reports each |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | The most decisions kept, default `100000`, up to `10000000`, `0` keeps none; and for how long, default `10000`, `1..3600000` milliseconds |

## Users, groups and roles

Policies name the user Solr's authentication signs in, such as a basic authentication user or a JSON web token's principal. Groups are the roles a JSON web token verified for the user, together with the groups GrantForge puts the user in; roles are those GrantForge gives the user.

## Decisions and limits

- Without a usable snapshot everything is refused but the requests between the nodes. A service that is not in use leaves Solr unchecked, as without an authorization plugin.
- A token bound to a service whose type is not `solr` refuses everything.
- A Collections API request that names its collections only in a JSON body, as some v2 API requests do, names none the agent can see, and needs `admin` on the cluster.
- Solr resolves aliases to their collections before it asks, so policies name collections, not aliases.
- Solr's `/admin/authorization` API cannot edit the agent's rules: GrantForge holds the policies.
- Client addresses are addresses and ranges only; a host name matches nothing.
- Decisions are kept for `grantforge.decision.cache.ttl.ms`, so a policy starts or stops applying up to that much later.

## Related guides

- [Apache Solr](/en/plugins/solr/)
- [Data services, policies and agents](/en/external/data-services/)
