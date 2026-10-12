---
title: Apache ZooKeeper
description: Install and configure the ZooKeeper service plugin, browse znodes and manage access policies for znode paths.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Apache ZooKeeper plugin provides ensemble connections, znode path lookup and policy management in GrantForge. Its plugin ID and service type are both `zookeeper`.

## Installation

The distribution includes the plugin in `plugins/zookeeper`. Confirm that `zookeeper` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin uses the ZooKeeper 3.9 client in a class loader of its own, apart from the server's libraries.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select ZooKeeper (`zookeeper`).
2. Enter the connection string: the ensemble's servers with an optional chroot, such as `zk1:2181,zk2:2181,zk3:2181/kafka`. The client fails over between the servers on its own.
3. Choose the authentication, set the lookup znode and test the connection, which verifies that the znode exists and its children can be listed, then save the service.

| Setting | Purpose |
| --- | --- |
| `zookeeper.connect` | Required connection string: `host[:port]` separated by commas, optionally followed by `/chroot`; the chroot cannot end with `/` |
| `authentication` | `none` (default), `digest` or `kerberos` |
| `username` | Digest: the digest user of the ACLs (no colon); Kerberos: the principal, such as `grantforge@EXAMPLE.COM` |
| `password` | The digest user's password; stored encrypted |
| `keytab` | Kerberos: path of the principal's keytab on the GrantForge server |
| `zookeeper.sasl.client.username` | Kerberos: the first part of the servers' principals, default `zookeeper`, as in `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos: the servers' whole principal where it does not have that form, such as `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | Whether to connect to the servers' TLS client port, default `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | Truststore that verifies the servers; empty trusts what the server's Java trusts; type `jks`, `pkcs12` or `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | Keystore with the client certificate, for servers that require one |
| `session.timeout.ms` | Session timeout, default `15000`, range `2000..120000` |
| `connection.timeout.ms` | Initial connection and authentication wait only, default `10000` ms, range `1000..60000`; does not limit later synchronous requests |
| `request.timeout.ms` | Maximum wait for one synchronous request, default `8000` ms, range `1000..60000` |
| `lookup.path` | Starting znode for lookup and browsing, below the chroot, default `/`; for example `/brokers`, with browsing confined to it |
| `lookup.max.entries` | Most children read from one znode at once, default `10000`, range `1..100000` |

## Authentication

- **none**: no sign-in; only znodes whose ACL is open to `world:anyone` can be read.
- **digest**: signs in as `username:password`, which matches the `digest` identities of ZooKeeper's ACLs. A wrong password is not refused at sign-in: it is another identity, so protected znodes show as access denied.
- **kerberos**: signs in over SASL with a keytab; the server's `krb5.conf` must lead to the KDC. Each principal and keytab gets a JAAS section of its own beside the process's configuration, so one GrantForge reaches several ensembles as different principals. Signing in with a Kerberos password alone is not supported yet.

Configuration is validated on save: the connection string's form, a user and password for digest, a principal and keytab for Kerberos, a keytab and server principal only with Kerberos, and the ranges of the timeouts and the limit.

With TLS disabled, leave both truststore and keystore locations and passwords empty. A store password requires its corresponding store location. Both store types accept only `jks`, `pkcs12` or `pem`.

Leading and trailing spaces in TLS store passwords are preserved. The service configuration determines the TLS mode and stores used for this connection, overriding pre-existing ZooKeeper TLS and store settings in the JVM; server certificates and hostnames are still verified.

## Browse znodes

Select the service under **Data permissions → Policies** and use **Browse** beside `path`:

- Children are listed a page at a time, by name; a znode with children of its own can be opened further.
- Each znode shows its ACL (`scheme:id:cdrwa`, as ZooKeeper's own tools write it), the size of its data and when it last changed.
- A znode whose ACL the lookup user may not read is still listed by name; opening it says why not.

If znodes are deleted during pagination, the cursor still advances from the last child name scanned, even if that child no longer exists. Child listings are not snapshots: concurrent additions or deletions follow ZooKeeper semantics and may change subsequent pages.

Typing a path offers suggestions; a parent that does not exist has none, and paths outside `lookup.path` are refused. A znode with more children than `lookup.max.entries` fails with an error rather than showing part of them.

## Enforce policies

There is one resource level, `path`, and the access types follow ZooKeeper's ACL permissions: `read`, `write`, `create`, `delete` and `admin`; path policies support recursion and excludes.

The service plugin manages, looks up and browses; the in-server agent enforces policies for marker ACLs through a SASL authentication provider. Build with `mvn -pl agents/grantforge-agent-zookeeper -am package` to produce a single Java 8-compatible shaded JAR with its dependencies. The distribution ships it as `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. The implementation targets ZooKeeper 3.6–3.9 APIs; compatibility with actual versions still needs validation. This is not a claim that the full version matrix has passed or that production acceptance is complete.

### Deployment

1. Install the JAR in the `lib` directory of **every ZooKeeper server**, including all nodes that can accept client requests. Create **Agent credentials** in the corresponding GrantForge `zookeeper` data service. Store the token in a separate file readable only by the ZooKeeper runtime account; do not put the token itself in `zoo.cfg`.
2. Add the following to each server's `zoo.cfg`, replacing the example address, paths and instance identifier. `grantforge.server.url` (`http[s]://GrantForge`), `grantforge.token.file`, a local `grantforge.cache.dir` and `grantforge.instance` are all required. The cache directory must be writable by the ZooKeeper runtime account.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. This provider **replaces the native SASL provider**; do not keep another provider registered for the same `sasl` scheme in any other `authProvider.N` entry. `enableEagerACLCheck=false` is mandatory: when enabled, provider registration is rejected and the marker is not allowed. Keep normal ZooKeeper SASL, JAAS and Kerberos authentication configured and working; the agent does not replace authentication. Restart servers following the ensemble's maintenance procedure.
4. Optionally set `grantforge.signing.key.file` to a file containing the pinned signing public key. `grantforge.principal.mapping=short` is the default and maps `kafka/host@REALM` to `kafka`; `full` retains the entire principal. Policy users must match the mapped identities.

### ACLs and limitations

- Only the `sasl:grantforge` marker ACL delegates to GrantForge policies, for example `sasl:grantforge:cdrwa`, where `c=create`, `d=delete`, `r=read`, `w=write` and `a=admin`. Other native ACLs, including other SASL identities, keep their normal behavior.
- The marker refuses access if the agent is not running, there is no usable policy snapshot, the snapshot's `serviceType` is not `zookeeper`, or no policy matches/allows access. **This is not a global deny**: other ACLs, `world:anyone` or a `super` identity may still allow the request.
- Policies match the server's absolute target path. For `create`/`delete`, this is the znode being created/deleted, not the parent supplying the ACL. Requests using chroot need the actual chroot prefix in the policy path: `/brokers` under chroot `/kafka` becomes `/kafka/brokers`. Sequential `create` checks the server's absolute request prefix before the sequence number is appended; subsequent `read`/`delete` checks the full generated node name.
- Native ACLs are not migrated automatically, and ACLs are not inherited recursively; new znodes need an explicit marker ACL. Before using `setACL`, verify allows and refusals on a small scope and retain an independent administrative access channel. Do not apply the marker to the root without careful validation, as this can lock out access.
- A server without the agent treats `sasl:grantforge` as the native SASL identity `grantforge` rather than evaluating policies. Deployment on every node is therefore required; partial deployment does not protect the ensemble.
- Writes forwarded from a follower/observer to the leader may have no client connection at this provider interface, so the `clientAddress` context may be empty. End-to-end ensemble policies based on client IP cannot be guaranteed.
- This provider cannot observe the final request result, so it does not emit final `AccessEvent` audit events and does not provide complete access auditing.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Plugin and service type development](/en/develop/plugins/)
