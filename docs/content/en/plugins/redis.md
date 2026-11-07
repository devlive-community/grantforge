---
title: Redis
description: Install and configure the Redis service plugin for Redis and Valkey, connect to a server, its replicas or a cluster, sign in with an ACL user over TLS if wanted, look up keys, channels and nodes, and manage who may read and write which keys, use which channels and monitor which nodes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Redis plugin looks up keys, channels and nodes of Redis and Valkey deployments, and manages access policies in Redis's own terms: reading and writing keys by pattern, subscribing and publishing to channels, and monitoring each node. Plugin ID and service type are both `redis`. The service plugin runs in the GrantForge server; the [Redis agent](/en/external/redis-agent/), which runs beside the deployment, enforces the policies with Redis's own ACL on every node.

The repository's Testcontainers integration tests cover a primary with a replica of Redis `7.2`, `7.4`, `8.2` and `8.10` and Valkey `8.1` and `9.1`, and a Redis `8.10` cluster. This does not certify every other release.

## Installation

The distribution ships the plugin in `plugins/redis`. On **Platform administration → Plugins**, check that `redis` is enabled, and rescan after updating the plugin directory. The plugin talks to Redis through Jedis, in a class loader of its own.

## Add a data service

1. Open **Data authorization → Data services**, add a service and choose Redis (`redis`).
2. Enter one or more nodes. The rest of a cluster, and the replicas of a primary, are found from them.
3. Enter the lookup user, test the connection and save the service. The test signs in and describes the deployment: Redis or Valkey and its release, whether it is a single server, a primary with replicas or a cluster, and who signed in.

The lookup user lets GrantForge find resources and is not the user that reads or writes. A connection test that succeeds does not mean access already follows the policies.

| Setting | Purpose |
| --- | --- |
| `redis.nodes` | Required: the nodes as `host:port`, such as `redis1.example.com:6379`, separated by commas and tried in order; the port is `6379` unless given |
| `username` | The lookup user, an ACL user; empty signs in as the default user |
| `password` | Its password, stored encrypted |
| `tls.mode` | `disable` (default), `verify-ca` to check the servers' certificates, or `verify-full` to check their names too |
| `tls.ca.file` | TLS: path on the **GrantForge server** of the PEM CA certificates the servers are checked against. Empty uses the JVM's |
| `tls.certificate.file` | TLS, for servers that ask for client certificates: path on the **GrantForge server** of the PEM client certificate, which may hold its key too |
| `tls.key.file` | Path on the **GrantForge server** of the certificate's PEM key, when it has a file of its own |
| `tls.key.password` | The password of an encrypted key, stored encrypted |
| `timeout` | How long connecting and a command may take, `10000` by default, in the range `1..600000` milliseconds |

A refused sign-in, a lookup user that lacks a permission and nodes that cannot be reached are each reported as such.

### What the lookup user sees

Keys are found with `SCAN` on each primary, a few rounds at most so that a lookup stays quick however many keys there are; channels are those that have subscribers now. `SCAN` lists every key whatever the user's key patterns, so the lookup user needs no permission to read values:

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `key` | Wildcards, case-sensitive. A key or a pattern of keys | `read`, `write` |
| `channel` | Wildcards, case-sensitive. A channel or a pattern of channels | `subscribe`, `publish` |
| `node` | Wildcards, not case-sensitive. A node's address as `host:port` | `monitor` |

| Access type | What Redis allows |
| --- | --- |
| `read` | The commands of `@read` on the key, `%R~` on it |
| `write` | The commands of `@write` on the key, `%W~` on it, which delete it too |
| `subscribe` | `SUBSCRIBE`, `PSUBSCRIBE`, `SSUBSCRIBE` on the channel, `&` |
| `publish` | `PUBLISH`, `SPUBLISH` on the channel, `&` |
| `monitor` | `INFO`, `DBSIZE`, `LATENCY LATEST`, `LATENCY HISTORY`, `MEMORY STATS` |

In policy values `*` matches any text and `?` one character, as in Redis's own patterns. Redis grants keys and channels by pattern and cannot list them to the agent, so a policy's value is granted as a pattern of its own, where the policies decide the user alike across it. A pattern that policies decide differently within, such as `sales:*` when a policy denies `sales:card:*`, is withheld and said so; name the keys or patterns policies decide alike instead. Values with brackets, backslashes, parentheses or white space are never granted.

Commands that Redis counts as dangerous, such as `FLUSHALL` and `KEYS`, are never given, and `SCAN` and `RANDOMKEY`, which list keys whatever their patterns, only to a user that may read every key. Patterns name keys in every database of a node, as Redis's ACL has no databases.

Redis filters nothing within a value and masks nothing, so the service type offers neither.

## Policy examples

Create these enabled policies in one data service:

| Resource | Rule |
| --- | --- |
| `key=sales:*` | Allow `read` to group `analysts` |
| `key=sales:card:*` | Allow `read` and `write` to user `billing` |
| `key=cart:*` | Allow `read` and `write` to user `shop` |
| `channel=orders.*` | Allow `subscribe` to group `analysts`; allow `publish` to user `shop` |
| `node=*` | Allow `monitor` to user `dashboards` |

Once the agent has applied the snapshot, members of `analysts` read every `sales:` key and follow the order channels; `billing` reads and writes the card keys, a pattern no other policy decides within; `shop` keeps carts and publishes order events; and `dashboards` sees each node's state and no data.

## Enforcing the policies

The service plugin manages policies and looks up resources. Enforcing them takes one Redis agent per deployment; see [Redis agent](/en/external/redis-agent/) for deployment and checks.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Redis agent](/en/external/redis-agent/)
- [Plugins and service types](/en/develop/plugins/)
