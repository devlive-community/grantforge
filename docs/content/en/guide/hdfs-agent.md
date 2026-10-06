---
title: HDFS NameNode agent
description: Enforce GrantForge path policies inside a Hadoop 3.5.0 NameNode and report access audits.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The server-side `grantforge-plugin-hdfs` defines the resources and connection configuration; `grantforge-agent-hdfs` is installed inside the NameNode, checks access through Hadoop's `INodeAttributeProvider` and `AccessControlEnforcer`, and reuses the GrantForge agent core to download signed policies, keep local snapshots and report audits in batches. The current agent is built for **Hadoop 3.5.0 and Java 17 or later**; other Hadoop versions require matching adaptation and verification.

In the source tree, the server-side plugin lives in `plugins/grantforge-plugin-hdfs`, the NameNode agent in `agents/grantforge-agent-hdfs`, and the shared protocol, snapshot cache and audit reporting infrastructure in `core/grantforge-agent-core`.

## Relationship to native permissions

The agent performs the HDFS native permission checks first, then the GrantForge policies: a user must satisfy both the native permissions and the policies. GrantForge allow policies do not bypass POSIX permissions, ACLs, owner checks or the sticky bit; deny policies always deny. Native permission settings are still maintained through Hadoop's administration tools.

The default `grantforge.hdfs.native.fallback=false` denies data access when there is no local policy snapshot, no matching policy, or the agent has not started yet. When set to `true`, access not decided by policies falls back to the native permissions; explicit deny policies still apply. While the server is temporarily unreachable, the agent keeps using the last local snapshot that passed signature verification.

The ancestors, the target, the subtrees and the snapshot path projections within one authorization callback all use the same version of the policy snapshot; refreshed policies take effect on the next callback, so allow rules from different versions are never combined. Access audits record the policy version actually used.

The agent checks the `read`, `write` and `execute` a regular user needs for the target, and also checks parent directories, ancestor directories and subdirectories that require recursive validation. Operations such as create, delete and rename involve multiple paths, and allow policies must cover all of them. In strict mode, a `read` policy on the target file alone is not enough: the user also needs `execute` policies on the ancestor directories — for example, allow `execute` on `/` with recursion checked, then configure read and write on the actual data directories.

Snapshot paths are checked both as the actually requested path and as the original path with `.snapshot/<snapshot name>` removed — for example, `/data/.snapshot/s1/secret` is also checked as `/data/secret`. Deny policies on the original path therefore also constrain snapshots; stricter restrictions can additionally be configured for explicit snapshot paths. Metadata queries follow HDFS's directory traversal permission semantics.

A single recursive authorization checks at most `100000` inodes; beyond that limit the operation is denied to avoid unbounded memory allocation inside the NameNode. Overly long paths are evaluated with the full path; the audit resource display is limited to `1000` characters, and the original length and a SHA-256 digest are recorded in the request details.

HDFS superusers remain managed by Hadoop. Superuser callbacks with a path first pass Hadoop's superuser check, then are checked against the policies by the operation names Hadoop 3.5.0 provides: file reads and metadata queries require `read`, directory enumeration requires `read` + `execute`, and known modification operations require `write`. Unknown, missing, or not reliably inferable operations (for example `checkAccess` and `concat`) conservatively require all three.

Superuser callbacks have no full inode or subtree context, and pathless cluster administration calls keep the native checks; none of a superuser's recursive operations can be restricted with subdirectory policies. Data consumers should use regular Hadoop users.

## Deployment

1. Add an `hdfs` service under GrantForge's data services, save the configuration and test the connection; configure path policies for the actual Hadoop short usernames, groups or roles.
2. In "Data permissions → Agents", issue a token for this service. Write the raw token to a local file on each NameNode, for example `/etc/hadoop/grantforge/token`, readable by the user the NameNode runs as.
3. Place the `grantforge-agent-hdfs-<版本>.jar` that matches the current release from `agents/hdfs/` in the distribution package onto the NameNode's classpath, for example `$HADOOP_HOME/share/hadoop/hdfs/lib/`. The agent jar already bundles its own policy engine, Jackson and signature library; Hadoop classes are provided by the NameNode. The agent version reported in heartbeats comes from build metadata.
4. Configure the following properties in each NameNode's `hdfs-site.xml`; the two NameNodes in an HA setup use different `instance` values and their own local cache directories.

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. Confirm that `dfs.permissions.enabled=true` and that `dfs.namenode.inode.attributes.provider.bypass.users` is empty; at startup the agent rejects configurations that would let authorization callbacks be bypassed. Restart the NameNode, then check the heartbeat and policy version on GrantForge's Agents page. The agent reads the NameNode's existing configuration; it does not modify the native attributes of inodes.

For a first deployment you can start with `native.fallback=true`, confirm that the policy snapshot has synced and that ancestor directory permissions are complete, and then switch to strict mode. The service type a token is bound to must be `hdfs`; a misconfiguration or a binding to another service type results in access being denied.

## Optional settings

| Property | Default | Purpose |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Timeout for connecting to GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Timeout for reading a response |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Policy refresh interval while the server is unreachable, at least `1000`; normal heartbeats use the interval suggested by the server |
| `grantforge.hdfs.signing.key.file` | not set | Optional signing public key file containing the Base64 X.509 public key provided by the console; when configured, only signatures from that key are accepted |
| `grantforge.hdfs.audit.batch.size` | `500` | Maximum number of events per report, range `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | In-memory audit queue capacity, range `1..1000000`, must hold at least one batch; when the queue is full, new events are counted and dropped |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Audit flush interval, a positive integer, at most `2147483647`; lower it to reduce reporting latency |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Disk buffer limit while the server is unreachable, a non-negative integer; `0` disables the disk buffer |

Under bursty access loads, increase the audit queue to reduce queue overflow; shortening the flush interval lowers the audit latency but also increases the reporting frequency. The disk buffer limit controls disk usage during long outages; events may be lost when the buffer is disabled or exhausted. Audit reporting runs in the background and does not wait for the policy server's response.

When no signing public key is configured, the agent fetches the public key from the server on first contact and stores it together with the snapshot. The agent uses the short username and groups passed in by Hadoop; roles and additional groups come from the signed snapshot. The short-name mapping for Kerberos principals is determined by the cluster's `hadoop.security.auth_to_local`.

## Build and verify from source

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs,core/grantforge-plugin-host -am test
```

The agent artifact ends up in `agents/grantforge-agent-hdfs/target/grantforge-agent-hdfs-<版本>.jar`. Unit tests cover NameNode authorization callbacks, configuration, version metadata and policy decisions; the WebHDFS and Kerberos tests start temporary local services. Before going to production, also verify read/write, create, rename, recursive delete, HA failover and cached behavior after a network outage on the target cluster.

Integration verification runs with the `verify` phase using Testcontainers (unit tests do not start a cluster; `verify` requires a working Docker daemon):

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am verify
# 与 nightly 使用同一入口
bash script/ci/hdfs_integration.sh
```

The tests use Apache Hadoop container images with pinned versions and place the actually packaged agent jar onto the NameNode classpath. Testcontainers creates an isolated network and manages the lifecycle of the NameNode and DataNodes, verifying read/write, create, append, rename, delete, recursive and snapshot denial, native permissions and auditing, policy refresh, restarting the NameNode against the signed cache after the policy server is disconnected, and strict mode with native permission fallback when no snapshot exists.

A running Docker daemon is required, and downloading the test images must be allowed. If Docker is unavailable the tests fail; they are never silently skipped. The filesystem client runs inside the Hadoop container, and the policy HTTP service uses Testcontainers' host port forwarding; no external Hadoop cluster is needed. Containers and the test network are cleaned up after the tests, and logs are saved to `agents/grantforge-agent-hdfs/target/hdfs-testcontainers`.

The HA test starts two NameNodes, one DataNode and one JournalNode, and configures an independent instance name and cache directory for each of the two agents. It uses the logical HDFS client to switch the active node manually and verifies reads, writes and deny policies after the switch. The single JournalNode exists only for testing: quorum fault tolerance is not verified, and ZooKeeper-based automatic failover is not involved.

Test sources and dependencies live directly in the existing `agents/grantforge-agent-hdfs` under `src/test` and the test scope; no separate Maven test project is created, and Testcontainers never ends up in the agent distribution. Nightly runs the same test entry point on Java 17 and 21 and saves the reports and container logs.

For the Hadoop extension entry points and permission semantics, see the [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) and the [HDFS Permissions Guide](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
