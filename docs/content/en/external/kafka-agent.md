---
title: Apache Kafka agent
description: Install the agent in Kafka brokers and controllers to authorize every request with GrantForge policies, show the policies as read-only ACLs and fold repeated accesses into one audit event.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Kafka agent is an authorizer of Kafka's `Authorizer` API, named in `authorizer.class.name`: brokers and controllers ask it about every request, and it answers from the `kafka` data service's signed policies. The distribution ships it as one Java 8-compatible JAR, `agents/kafka/grantforge-agent-kafka-<version>.jar`, with its dependencies shaded inside.

The repository's Testcontainers integration tests run the JAR inside Kafka `3.7.2`, `3.9.2`, `4.1.2` and `4.3.1`, in KRaft nodes that are broker and controller at once. This does not certify every other release. See [Apache Kafka](/en/plugins/kafka/) for the service plugin.

## How it works

- **Every request is checked.** For each resource a request touches, Kafka asks for an ACL operation; the agent answers from the policies, with Kafka's resource types as the resource levels and its operations as the access types. A refusal reaches the client as Kafka's own error, such as `TOPIC_AUTHORIZATION_FAILED`.
- **Idempotent producers need only a topic.** Kafka lets a producer start without `idempotent_write` on the cluster when it may write to some topic, and asks the authorizer whether it may. The agent answers from the policies themselves: an allow of `write` on any topic counts, and a denial counts only in a policy that covers every topic.
- **The policies show as ACLs.** `kafka-acls --list` and the Admin API's `describeAcls` show the policies that apply now: a value without wildcards as a literal ACL, a value such as `orders*` as a prefixed one and `*` as Kafka's wildcard; users as `User:` principals, groups as `Group:`, roles as `Role:` and the `public` group as `User:*`. What ACLs cannot express is left out: other wildcards, excluded values, items with conditions and the items of a policy that has exceptions to them. Creating or deleting ACLs is refused: *the ACLs of this cluster are GrantForge policies; change them in GrantForge*.
- **Decisions are kept.** A broker asks the same questions on every produce and fetch; the agent keeps the decisions of the current snapshot, by default 100,000 of them for 10 seconds. A new snapshot starts afresh, so a policy change applies as soon as the agent has it.
- **Repeated accesses are folded.** Identical allowed accesses within a window, by default 10 seconds, are reported as one access event that says how many there were and when the last one happened; the console shows *N identical accesses*. Accesses are identical when they share the user, client address, resource, access type, request type and deciding policy. Refusals are reported one by one at once. Kafka chooses what is worth reporting: it does not ask to report, for example, the topics it leaves out of a listing.
- **Super users are not checked.** The principals of `super.users` may do everything, as with Kafka's own authorizer, and are not audited.

## Deployment

1. Add a `kafka` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only Kafka's account can read.
2. Copy the JAR into `libs` of **every broker and controller**, such as `/opt/kafka/libs`.
3. Add the settings to the `server.properties` of each, replacing the example principals, address, paths and instance name:

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` must name the principals brokers and controllers use among themselves, such as that of the inter-broker listener, or policies must allow them what they do, such as `cluster_action` on the cluster. Connections to a `PLAINTEXT` listener are `User:ANONYMOUS`; name it only when no client can reach such a listener. The cache directory must be writable by Kafka's account.
4. Restart the brokers and controllers one at a time. Listeners other than the controllers' open once the agent has its first snapshot, at most after `grantforge.start.timeout.ms`; until then, and whenever there is no snapshot, requests are refused.
5. **Move existing ACLs first:** ACLs Kafka's own authorizer stored no longer apply once the agent replaces it. List them with `kafka-acls --list` beforehand and create matching policies.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this broker or controller and a writable directory for the signed snapshot and spooled events |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), sending interval (default `5000` milliseconds) and spool limit (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` reports each |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | The most decisions kept, default `100000`, up to `10000000`, `0` keeps none; and for how long, default `10000`, `1..3600000` milliseconds |
| `grantforge.start.timeout.ms` | How long listeners wait for the first snapshot, default `30000`, `0..600000` milliseconds |

The agent reads its settings from the configuration Kafka hands its authorizer. A node that is broker and controller at once creates an authorizer for each role; both share one agent.

## Users, groups and roles

Policies name the principal Kafka gives the connection: the SASL user name, the short name of a Kerberos principal by `sasl.kerberos.principal.to.local.rules`, or a TLS certificate's name by `ssl.principal.mapping.rules`. Kafka has no groups or roles; those of the policies are the ones GrantForge gives the user.

## Decisions and limitations

- Without a usable snapshot, everything but the super users' requests is refused. A service that is not in use leaves Kafka unchecked, as it is without an authorizer.
- A token bound to a service whose type is not `kafka` refuses everything.
- A denial of an access type also denies what it implies: denying `write` on a topic denies `describe`, so the user does not see it. Kafka's own ACLs differ here.
- Kafka's question whether a user may act on some resource of a type is no access and is not reported.
- Decisions are kept for `grantforge.decision.cache.ttl.ms`, so a policy's validity period starts or ends up to that much late.
- The agent works in brokers and controllers. Kafka Connect's REST API, schema registries and other services around Kafka are not covered.

## Related guides

- [Apache Kafka](/en/plugins/kafka/)
- [Data services, policies and agents](/en/external/data-services/)
