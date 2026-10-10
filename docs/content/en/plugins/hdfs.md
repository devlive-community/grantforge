---
title: Apache Hadoop HDFS
description: Install and configure the HDFS service plugin, browse directories and manage access policies for paths.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Apache Hadoop HDFS plugin provides cluster connections, path lookup and policy management in GrantForge. Its plugin ID and service type are both `hdfs`.

## Installation

The distribution includes the plugin in `plugins/hdfs`. Confirm that `hdfs` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select HDFS (`hdfs`).
2. Enter the cluster URI and lookup user. Hadoop 2.x uses `webhdfs://namenode:50070`; 3.x can use `hdfs://namenode:8020` or `webhdfs://namenode:9870`. For HTTPS, use `swebhdfs://` with the cluster's actual port.
3. Set the lookup directory and test the connection, which verifies that the directory exists and can be listed, then save the service.

| Setting | Purpose |
| --- | --- |
| `fs.default.name` | Required cluster URI without a subdirectory, credentials or query parameters; HA can use `hdfs://nameservice1` with matching additional properties |
| `username` | Required lookup user; for Kerberos, a principal such as `grantforge@EXAMPLE.COM` |
| `hadoop.security.authentication` | Defaults to `simple`; choose `kerberos` for a Kerberos cluster |
| `hadoop.security.authorization` | Whether Hadoop checks permissions, default `false`; match the cluster's core-site.xml |
| `hadoop.security.auth_to_local` | Rules mapping Kerberos principals to user names, matching the cluster's core-site.xml |
| `password` / `keytab` | Kerberos password or path to a keytab file on the GrantForge server |
| `dfs.namenode.kerberos.principal`, `dfs.datanode.kerberos.principal`, `dfs.secondary.namenode.kerberos.principal` | Principals of the cluster's components under Kerberos, such as `nn/_HOST@EXAMPLE.COM`, matching the cluster configuration |
| `lookup.path` | Starting directory for lookup and browsing, default `/`; for example `/data`, with browsing confined to that directory |
| `lookup.max.entries` | Limit for full-directory scans, default `10000`, range `1..100000` |
| `hadoop.config` | One `key=value` per line for HA and other Hadoop properties; overrides connection settings with the same name |
| `hadoop.rpc.protection` | `authentication`, `integrity` or `privacy`, matching the cluster |
| `ssl.client.truststore.location` | Path on the GrantForge server of the truststore that verifies `swebhdfs://` NameNode certificates; empty trusts what the server's Java trusts |
| `ssl.client.truststore.password` | The truststore password, when it is protected; stored encrypted |
| `ssl.client.truststore.type` | `jks` (default) or `pkcs12` |

With the NameNode attribute extension enabled in Hadoop 2.7.7, ordinary users querying the root path `/` trigger a confirmed upstream `NullPointerException`; set `lookup.path` to an actual directory such as `/data` (see the [agent guide](/en/external/hdfs-agent/)).

Kerberos also requires a reachable KDC, the server's `krb5.conf`, and matching `hadoop.security.auth_to_local` and service principals. The lookup account retrieves directory metadata.

With Kerberos, GrantForge reuses a sign-in between lookups instead of asking the KDC every time: Hadoop renews a keytab sign-in when its ticket nears its end, and a password sign-in is repeated once less than a fifth of its ticket's life (and at least a minute) remains; a changed password or an updated keytab file signs in again. Each service uses its own truststore, which an `ssl-client.xml` on the server's class path does not replace.

Configuration is validated on save: in `hadoop.config`, `fs.defaultFS` and `fs.default.name` are aliases, so configure only one; the cluster URI must not contain credentials, a path, a query or a fragment; `kerberos` requires a `password` or a `keytab`; `lookup.path` must be an absolute path without `..`; `lookup.max.entries` must be between `1` and `100000`. Additional properties override connection settings with the same name, and both validation and sign-in use the overridden values.

## Browse paths

Select the service under **Data permissions → Policies** and use **Browse** beside `path`.

- Open directories, navigate the path or return to the parent, and load subsequent pages as needed.
- Inspect file/directory markers, owner, group, permissions, file size and modification time.
- Select multiple files or directories, or the current directory, to add to the policy; typing paths still offers suggestions.

RPC and WebHDFS endpoints supporting batched listings use native pagination. Older endpoints read directories within the scan limit and report an error above it. Permission, authentication and connection failures show their reason and can be retried.

Input may omit the leading `/`, repeated `/` and `.` are allowed, and `..` and absolute paths outside `lookup.path` are rejected; a non-existent directory returns no candidates. Browsing does not replace HDFS's own access control; symbolic links and ViewFS mounts still follow the cluster configuration.

## Enforce policies

The single resource level is `path`, with `read`, `write` and `execute` access types. Path policies support recursion and exclusions.

The server plugin handles management and lookups. Enforcing data access also requires a NameNode agent matching the Hadoop version; users must satisfy both native HDFS permissions and GrantForge policies. Numbered agents cover 2.7, 2.10, 3.2, 3.3, 3.4 and 3.5. See the agent guide for verified combinations, authentication and HA coverage, and superuser limitations.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Apache Hadoop HDFS NameNode agent](/en/external/hdfs-agent/)
- [Plugin and service type development](/en/develop/plugins/)
