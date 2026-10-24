---
title: Apache Pulsar agent
description: Install the authorization provider in Pulsar brokers to decide with GrantForge policies who may produce and consume where, through which subscriptions, run functions, sources and sinks, and administer topics, namespaces, tenants and clusters, and from which networks.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Pulsar agent is an authorization provider of the brokers: Pulsar asks it who may produce, consume, run functions, sources and sinks, and administer topics, namespaces, tenants and clusters, and it answers from the `pulsar` data service's signed policies. The distribution ships it as one Java 17 JAR, `agents/pulsar/grantforge-agent-pulsar-<version>.jar`, with its dependencies embedded and relocated.

The repository's Testcontainers integration tests run the JAR inside the brokers of Pulsar `3.0.17`, `3.3.9`, `4.0.14` and `4.2.5`. This does not certify every other release. The service plugin is described in [Apache Pulsar](/en/plugins/pulsar/).

## How it works

- **Every check is decided at every level of the topic's name.** As with Pulsar's own grants, a permission on a tenant or a namespace covers what is in it, so the agent decides producing to `persistent://sales/orders/payments` at tenant `sales`, namespace `orders` and topic `payments`: a denial at any of them refuses, an allowance at any of them grants.
- **Consuming is decided by the subscription too.** At each of those levels the agent also asks about the topic with the subscription, so that a policy can let a role consume only through some subscriptions, such as `billing-*`.
- **The cluster's administrators are Pulsar's superusers.** `admin` on the broker's cluster makes a role one of them, and Pulsar lets its superusers do everything, so the policies below do not restrict them. Pulsar's broker and cluster operations need `admin` on the cluster they name.
- **Policies can hold for some networks only.** Every check carries the client's address, and the agent evaluates the **Client addresses** condition with Netty's own address ranges, which the broker's network stack uses.
- **Granting through Pulsar is refused.** Pulsar's own grants and the admin roles of its tenants are not used, so `pulsar-admin namespaces grant-permission` and the like are answered with HTTP 405, saying to grant in GrantForge.
- **Refusals are logged with their reason.** Pulsar words the refusal the client gets; the broker log names the GrantForge policy that denied it, or that none allows it.
- **Decisions are kept and accesses folded.** The decisions of the current snapshot are kept, by default 100,000 for 10 seconds, and a new snapshot starts afresh. Identical allowed accesses within a window, by default 10 seconds, are reported as one access event saying how many there were; refusals are reported one by one at once. Every event names the topic, namespace, tenant or cluster and the client address.
- **One agent serves the whole broker.** A broker loads its authorization provider for its binary protocol, its web service and its functions worker; they share one agent, which stops with the broker.

| Operation | Access needed |
| --- | --- |
| Produce, or create a topic by producing to it | `produce` on the topic |
| Subscribe, consume, unsubscribe, skip, peek, expire messages, reset a cursor | `consume` on the topic with the subscription |
| Look up a topic, read its stats, metadata and policies; list the topics of a namespace | `produce` or `consume` |
| Terminate, compact, offload, unload or truncate a topic; change its policies | `admin_topic` on the topic |
| Create and delete topics; change a namespace's policies | `admin_namespace` on the namespace |
| Run functions, sources, sinks; use packages | `functions`, `sources`, `sinks`, `packages` on the namespace |
| Create, list and delete the namespaces of a tenant | `admin_tenant` on the tenant |
| Create, change and delete tenants and clusters; Pulsar's broker operations | `admin` on the cluster |

## Deployment

1. Add a `pulsar` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only Pulsar's account can read.
2. Copy the JAR into `lib` of **every broker**, such as `/pulsar/lib`.
3. Add the settings to `broker.conf` of each, or `standalone.conf`, replacing the example address, paths and instance name:

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   Authentication must be enabled too, so that clients have roles. `superUserRoles` and `proxyRoles` must name only the brokers' own role and those of the proxies: the agent does not check them, and Pulsar checks the original client of a proxy as well. Give administrators `admin` on the cluster instead. The cache directory must be writable by Pulsar's account. In a container, write the `grantforge.*` settings into the file: the shell of some Pulsar images drops environment variables whose names have dots, such as `PULSAR_PREFIX_grantforge.token.file`.
4. Restart the brokers one at a time. Until the agent has its first snapshot, requests other than those of the brokers and proxies are refused.
5. **Move existing permissions first:** Pulsar's own grants on namespaces, topics and subscriptions, and the admin roles of tenants, no longer apply. Create matching policies beforehand.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this broker and a writable directory for the signed snapshot and spooled events |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), sending interval (default `5000` milliseconds) and spool limit (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` reports each |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | The most decisions kept, default `100000`, up to `10000000`, `0` keeps none; and for how long, default `10000`, `1..3600000` milliseconds |

## Users, groups and roles

Policies name the role Pulsar's authentication gives the client, such as a token's subject or a certificate's common name, as a user. Pulsar has no groups of its own: groups are those GrantForge puts the role in, and roles are those GrantForge gives it.

## Decisions and limits

- Without a usable snapshot everything is refused but the requests of the brokers and proxies. A service that is not in use leaves Pulsar unchecked, as with `authorizationEnabled` off.
- A token bound to a service whose type is not `pulsar` refuses everything.
- Pulsar asks whether a role is a superuser or a tenant's admin before many checks; only the answers that let a request through are reported, and the request's own check is reported either way. A request Pulsar decides by that question alone, as Pulsar 3.0 does for tenants and clusters, is logged at debug level only when refused.
- `admin` on a cluster makes a role a superuser of the brokers of that cluster only.
- Pulsar's permission listings, such as `pulsar-admin namespaces permissions`, fail: GrantForge holds the policies.
- Client addresses are addresses and ranges only; a host name matches nothing.
- Decisions are kept for `grantforge.decision.cache.ttl.ms`, so a policy starts or stops applying up to that much later.

## Related guides

- [Apache Pulsar](/en/plugins/pulsar/)
- [Data services, policies and agents](/en/external/data-services/)
