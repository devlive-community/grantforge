---
title: Apache ZooKeeper agent
description: Deploy the agent on every ZooKeeper node to enforce GrantForge policies through marker ACLs.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The server-side `grantforge-plugin-zookeeper` provides one `zookeeper` service type, its resources and connection configuration. Enforcing policies needs a second piece, running inside ZooKeeper itself: the agent, which takes part in the servers' ACL decisions as a SASL authentication provider. It lives in `agents/grantforge-agent-zookeeper`; the command `mvn -pl agents/grantforge-agent-zookeeper -am package` produces a single Java 8-compatible shaded JAR with its dependencies, which the distribution ships as `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. The implementation targets the ZooKeeper 3.6–3.9 APIs; compatibility with the individual versions still needs to be validated, so neither a complete version matrix nor acceptance for production can be claimed yet.

## Deployment

1. Install the JAR in the `lib` directory of **every ZooKeeper server**, including all nodes that can accept client requests. Create **Agent credentials** in the corresponding GrantForge `zookeeper` data service. Store the token in a separate file readable only by the ZooKeeper runtime account; do not write the token itself into `zoo.cfg`.
2. Add these settings to each server's `zoo.cfg`, replacing the example address, paths and instance identifier. `grantforge.server.url` (`http[s]://GrantForge`), `grantforge.token.file`, a local `grantforge.cache.dir` and `grantforge.instance` are all required. The cache directory must be writable by the ZooKeeper runtime account.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. This provider **replaces the native SASL provider**; do not keep another provider registered for the same `sasl` scheme in any other `authProvider.N` entry. `enableEagerACLCheck=false` is mandatory: when that check is enabled, the provider registration is rejected and the marker does not allow access. Keep the normal ZooKeeper SASL, JAAS and Kerberos authentication configured and working; the agent does not replace authentication. Restart the servers following the ensemble's maintenance procedure.
4. Optionally set `grantforge.signing.key.file` to the file holding the signing public key pinned as trusted. `grantforge.principal.mapping=short` is the default and maps `kafka/host@REALM` to `kafka`; `full` keeps the whole principal. Users in policies must match the mapped identities.

## ACLs and limitations

- Only the marker ACL `sasl:grantforge` delegates the decision to GrantForge policies, for example `sasl:grantforge:cdrwa`, where `c=create`, `d=delete`, `r=read`, `w=write` and `a=admin`. Other native ACLs, including other SASL identities, keep their normal behavior.
- The marker refuses access if the agent is not running, there is no usable policy snapshot, its `serviceType` is not `zookeeper`, or no policy matches/allows the access. **This is not a global deny**: other ACLs, `world:anyone` or a `super` identity may still allow the request.
- Policies match the server's absolute target path. For `create`/`delete`, this is the znode being created/deleted, not the parent supplying the ACL. Requests using chroot need the actual chroot prefix in the policy path: `/brokers` under chroot `/kafka` becomes `/kafka/brokers`. Sequential `create` checks the server's absolute request prefix before the sequence number is appended; subsequent `read`/`delete` checks the full generated node name.
- Native ACLs are not migrated automatically, and ACLs are not inherited recursively; new znodes need an explicit marker ACL. Before using `setACL`, verify allows and refusals on a small scope and retain an independent administrative access channel. Do not apply the marker to the root without careful validation, as this can lock out access.
- A server without the agent treats `sasl:grantforge` as the native SASL identity `grantforge` rather than evaluating policies. Deployment on every node is therefore required; partial deployment does not protect the ensemble.
- Writes forwarded from a follower/observer to the leader may have no client connection at this provider interface, so the `clientAddress` context may be empty. End-to-end ensemble policies based on client IP cannot be guaranteed.
- This provider cannot observe the final request result, so it does not emit final `AccessEvent` audit events and does not provide complete access auditing.
