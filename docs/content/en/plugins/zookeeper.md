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
| `connection.timeout.ms` | How long to wait for a server and the sign-in, default `10000`, range `1000..60000` |
| `lookup.path` | Starting znode for lookup and browsing, below the chroot, default `/`; for example `/brokers`, with browsing confined to it |
| `lookup.max.entries` | Most children read from one znode at once, default `10000`, range `1..100000` |

## Authentication

- **none**: no sign-in; only znodes whose ACL is open to `world:anyone` can be read.
- **digest**: signs in as `username:password`, which matches the `digest` identities of ZooKeeper's ACLs. A wrong password is not refused at sign-in: it is another identity, so protected znodes show as access denied.
- **kerberos**: signs in over SASL with a keytab; the server's `krb5.conf` must lead to the KDC. Each principal and keytab gets a JAAS section of its own beside the process's configuration, so one GrantForge reaches several ensembles as different principals. Signing in with a Kerberos password alone is not supported yet.

Configuration is validated on save: the connection string's form, a user and password for digest, a principal and keytab for Kerberos, a keytab and server principal only with Kerberos, and the ranges of the timeouts and the limit.

## Browse znodes

Select the service under **Data permissions → Policies** and use **Browse** beside `path`:

- Children are listed a page at a time, by name; a znode with children of its own can be opened further.
- Each znode shows its ACL (`scheme:id:cdrwa`, as ZooKeeper's own tools write it), the size of its data and when it last changed.
- A znode whose ACL the lookup user may not read is still listed by name; opening it says why not.

Typing a path offers suggestions; a parent that does not exist has none, and paths outside `lookup.path` are refused. A znode with more children than `lookup.max.entries` fails with an error rather than showing part of them.

## Enforce policies

There is one resource level, `path`, and the access types follow ZooKeeper's ACL permissions: `read`, `write`, `create`, `delete` and `admin`; path policies support recursion and excludes.

The server plugin manages, looks up and browses. The agent that enforces these policies inside ZooKeeper servers is not available yet; until it is, ZooKeeper's own ACLs decide access.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Plugin and service type development](/en/develop/plugins/)
