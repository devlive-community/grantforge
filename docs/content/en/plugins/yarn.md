---
title: Apache Hadoop YARN
description: Install and configure the YARN service plugin, reach one ResourceManager or a highly available pair, sign in simply or with Kerberos, look up queues and application names, and manage who may submit which applications where.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The YARN plugin looks up queues and the names of applications in them, and manages access policies on what YARN decides: submitting applications to queues, administering queues, and administering the cluster. Its plugin ID and service type are both `yarn`. The service plugin runs in the GrantForge server; the [YARN agent](/en/external/yarn-agent/), installed in the ResourceManagers, enforces the policies.

The repository's Testcontainers integration tests cover the ResourceManagers of Hadoop `3.2.4`, built from the SHA-512-verified Apache release, `3.3.6`, `3.4.3` and `3.5.0`, and a Kerberos ResourceManager of `3.4.3`; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/yarn`. Confirm that `yarn` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the ResourceManager through Hadoop's own YARN client, on Hadoop's shaded client 3.5.0, in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Apache Hadoop YARN (`yarn`).
2. Enter the ResourceManager's client address, or the addresses of every ResourceManager of a highly available cluster.
3. Choose how the lookup user signs in, enter it, test the connection and save the service. The test signs in and describes the cluster: how many NodeManagers it has and how many queues.

The settings take Hadoop's own names, so they can be copied from a `yarn-site.xml`. The lookup user lets GrantForge discover resources; it is separate from the users who submit applications. A successful connection test does not mean submissions are already subject to policies.

| Setting | Purpose |
| --- | --- |
| `yarn.resourcemanager.address` | Required: the ResourceManager, `host` or `host:port`, default port `8032`; several separated by commas for a highly available cluster, between which the client fails over |
| `hadoop.security.authentication` | `simple` (default): a user name; `kerberos`: a principal with a keytab or password |
| `username` | Required lookup user; with Kerberos the full principal, such as `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: the principal's password, unless a keytab is given; stored encrypted |
| `keytab` | Kerberos: path of the principal's keytab, readable on the **GrantForge server** |
| `kerberos.kdc` | Kerberos: KDCs of the principal's realm, `host[:port]` separated by commas; empty uses the server's `krb5.conf` |
| `yarn.resourcemanager.principal` | Kerberos: the ResourceManagers' principal, default `yarn/_HOST@` the lookup principal's realm |
| `hadoop.rpc.protection` | `authentication` (default), `integrity` or `privacy`, as the cluster's |
| `timeout` | How long a request may take, default `30000`, range `1..600000` milliseconds; YARN's own client would retry for a quarter of an hour |

The service is exactly what its settings say: no `core-site.xml` or `yarn-site.xml` of the GrantForge server is read. The Kerberos settings are refused with simple authentication; Kerberos needs a password or a keytab. A ResourceManager that refuses the sign-in, such as one that expects Kerberos, is reported as a refused sign-in.

### Kerberos

With `kerberos`, GrantForge signs the principal in through Hadoop's own login, from the keytab or with the password, keeps the sign-in and renews it from the keytab; nothing else on the GrantForge server needs Kerberos settings. Realms whose KDCs are entered in `kerberos.kdc` are declared for the whole GrantForge server, the same way the HDFS, Hive, Kafka, HBase and Trino plugins do, so several services can use different realms side by side.

## Resources and access types

| Level | Matches | Access types |
| --- | --- | --- |
| `queue` | Wildcards, with case; the queue's path from the root, such as `root.sales.etl` | `submit_app`, `admin_queue` |
| `application` | Wildcards, with case; below a queue; the application's name | `submit_app` |
| `cluster` | Its one value, `*` | `admin`: YARN's administration, such as refreshing queues, nodes and node labels |

`admin_queue` implies `submit_app`, as a queue's administrators may submit to it.

As in YARN, a permission on a queue covers the queues below it: `submit_app` on `root.sales` submits to `root.sales.etl`. A check is decided at every queue of the path, from the root, and a denial at any of them refuses it, so a policy can allow `root.sales` and deny `root.sales.adhoc`. Below a queue, an application level names which applications a grant is for: `submit_app` on `root.hr` with application `payroll-*` admits only applications whose name starts with `payroll-`, in `root.hr` and the queues below it.

In policy values, `*` matches any number of characters and `?` matches one. The policy editor suggests queues by their path, or by their own name when that is what was typed, whichever scheduler the cluster runs, and the names of the applications the ResourceManager still knows in the queues a policy names.

### Client addresses

A policy item may hold for some clients only: its **Client addresses** condition (`ip-range`) takes addresses, ranges such as `10.1.0.0/16`, and host names, as Hadoop's own host lists do. Behind a proxy, such as YARN's web proxy or a router, the client is the first address the proxies forwarded. Addresses and ranges are better than host names, which cost a name lookup of the client. YARN asks about its administration without the client's address, so the condition never holds on `cluster`.

## Policy example

Suppose the cluster runs the queues `root.sales.etl`, `root.sales.adhoc` and `root.hr`. Create these enabled access policies in the same data service:

| Resources | Rule |
| --- | --- |
| `queue=root.sales` | Allow `submit_app` for group `analysts` |
| `queue=root.sales.adhoc` | Deny `submit_app` for user `alice` |
| `queue=root.hr`, `application=payroll-*` | Allow `submit_app` for user `bob` |
| `queue=root.sales` | Allow `admin_queue` for user `carol` |
| `queue=root` | Allow `submit_app` for group `etl-robots`, from `10.0.0.0/8` |
| `cluster=*` | Allow `admin` for group `yarn-admins` |

Once the agent applies the new snapshot, members of `analysts` submit to `root.sales.etl` and `root.sales.adhoc`, but `alice` only to `root.sales.etl`; `bob` submits payroll applications to `root.hr` and nothing else; `carol` submits to the sales queues and kills their applications; members of `etl-robots` submit anywhere, but only from the internal network; and members of `yarn-admins` refresh the queues.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the YARN agent in every ResourceManager. See the [YARN agent](/en/external/yarn-agent/) for its deployment and what it checks.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [YARN agent](/en/external/yarn-agent/)
- [Plugin and service type development](/en/develop/plugins/)
