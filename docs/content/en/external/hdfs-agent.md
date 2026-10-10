---
title: Apache Hadoop HDFS NameNode agent
description: Choose a versioned Hadoop NameNode agent to enforce GrantForge path policies and report access audits.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The server-side `grantforge-plugin-hdfs` provides one `hdfs` service type, its resources and connection configuration. NameNode agents are built separately for each Hadoop version, using that version's `INodeAttributeProvider` and `AccessControlEnforcer` while sharing policy signatures, caching, evaluation and auditing.

The service plugin lives in `plugins/grantforge-plugin-hdfs`, the numbered adapters in `agents/grantforge-agent-hdfs-<line>`, and Hadoop-free production logic in `agents/grantforge-agent-hdfs-common`. Shared native adapters live in the production Maven module `agents/grantforge-agent-hdfs-native`, built against the Java 8 / Hadoop 2.7.7 baseline. Each numbered module consumes its binary Maven dependency and retains its version-specific entry point and callbacks, without recompiling shared production sources. Protocol, snapshot cache and audit infrastructure remain in `core/grantforge-agent-core`.

The server plugin keeps one `hdfs` service type and a client version independent of the agents. Use `webhdfs://namenode:50070` for Hadoop 2.x connections and path lookup, or the corresponding `swebhdfs://` address with HTTPS. Hadoop 3.x can use RPC `hdfs://` or WebHDFS. Container tests cover WebHDFS on 2.x and both transports on 3.x; 2.x RPC is not certified, and the plugin never silently changes the configured transport.

With inode attribute extensions enabled, Apache Hadoop 2.7.7 throws a native null-pointer error when a regular user queries `/`, before the agent callback. Use actual data directories such as `/data` for data operations and the service `lookup.path` on this version. The container test explicitly records this root-path limitation.

## Choose a version

| Agent suffix | Tested Apache Hadoop | Container JVM | Permission SPI | Superuser path callback |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | Parameters | No |
| `2.10` | 2.10.2 | Java 8 | Parameters | No |
| `3.2` | 3.2.4 | Java 8 | Parameters | No |
| `3.3` | 3.3.6 | Java 8 | Context | No |
| `3.4` | 3.4.3 | Java 11 | Context, superuser and denial callbacks | Yes |
| `3.5` | 3.5.0 | Java 17 | Context, superuser and denial callbacks | Yes |

For Hadoop 2.10.2, install `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar`. Install exactly one adapter per NameNode and remove the old jar on upgrade; the configured provider class stays the same. Startup checks the Hadoop major/minor version and required callbacks and rejects a mismatched jar. Other patches and vendor distributions require separate verification.

The common module and 2.7 through 3.4 agents compile to Java 8 bytecode; 3.5 compiles to Java 17. The table lists the JVMs used in integration tests: Java 8 on Hadoop 3.4.3 has not been verified. Apache's 3.3.6 image is amd64 only and explicitly uses amd64 emulation on ARM hosts.

## Relationship to native permissions

The agent performs the HDFS native permission checks first, then the GrantForge policies: a user must satisfy both the native permissions and the policies. GrantForge allow policies do not bypass POSIX permissions, ACLs, owner checks or the sticky bit; deny policies always deny. Native permission settings are still maintained through Hadoop's administration tools.

The default `grantforge.hdfs.native.fallback=false` denies data access when there is no local policy snapshot, no matching policy, or the agent has not started yet. When set to `true`, access not decided by policies falls back to the native permissions; explicit deny policies still apply. While the server is temporarily unreachable, the agent keeps using the last local snapshot that passed signature verification.

The ancestors, the target, the subtrees and the snapshot path projections within one authorization callback all use the same version of the policy snapshot; refreshed policies take effect on the next callback, so allow rules from different versions are never combined. Access audits record the policy version actually used.

The agent checks the `read`, `write` and `execute` a regular user needs for the target, and also checks parent directories, ancestor directories and subdirectories that require recursive validation. Operations such as create, delete and rename involve multiple paths, and allow policies must cover all of them. In strict mode, a `read` policy on the target file alone is not enough: the user also needs `execute` policies on the ancestor directories — for example, allow `execute` on `/` with recursion checked, then configure read and write on the actual data directories.

Snapshot paths are checked both as the actually requested path and as the original path with `.snapshot/<snapshot name>` removed — for example, `/data/.snapshot/s1/secret` is also checked as `/data/secret`. Deny policies on the original path therefore also constrain snapshots; stricter restrictions can additionally be configured for explicit snapshot paths. Metadata queries follow HDFS's directory traversal permission semantics.

A single recursive authorization checks at most `100000` inodes; beyond that limit the operation is denied to avoid unbounded memory allocation inside the NameNode. Overly long paths are evaluated with the full path; the audit resource display is limited to `1000` characters, and the original length and a SHA-256 digest are recorded in the request details.

Hadoop 2.7, 2.10, 3.2 and 3.3 skip superusers before calling the agent, so it cannot enforce or audit those accesses. On Hadoop 3.4 and 3.5, superuser path callbacks first pass the native check, then apply policies by operation name: file reads and metadata queries require `read`, directory enumeration requires `read` + `execute`, and known modifications require `write`. Unknown, missing or ambiguous operations such as `checkAccess` and `concat` require all three.

Superuser callbacks have no full inode or subtree context, and pathless cluster administration calls keep the native checks; none of a superuser's recursive operations can be restricted with subdirectory policies. Data consumers should use regular Hadoop users.

## Metrics

The agent reports its metrics through Hadoop's Metrics2 system, over the same sinks as the NameNode's own dfs metrics: in the NameNode's JMX they are `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (a Prometheus JMX exporter can scrape them directly). Every metric carries the `instance` (the data service instance) and `agentVersion` tags, so the two NameNodes of an HA pair can be told apart. A failed registration only costs the metrics: the agent logs a warning and keeps enforcing without them.

| Metric | Meaning |
| --- | --- |
| `Callbacks` | Authorization callbacks the agent enforced |
| `SuperuserCallbacks` | Superuser authorization callbacks the agent enforced |
| `NativeDenies` | Access attempts Hadoop rejected before the agent ran |
| `EvaluationFailures` | Callbacks that failed closed because policy evaluation threw |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Permissions a policy allowed / denied / left undecided; undecided is denied too in strict mode |
| `MissingSnapshots` | Callbacks served while no verified policy snapshot existed |
| `SnapshotVersion` | Policy version of the snapshot in use, 0 when there is none |
| `QueuedEvents` / `DroppedEvents` | Access events waiting in memory / dropped because the queue or the spool was full |
| `ServerReachable` | Whether the last call to the policy server succeeded (1/0) |

## Deployment

1. Add an `hdfs` service under GrantForge's data services, save the configuration and test the connection; configure path policies for the actual Hadoop short usernames, groups or roles.
2. In "Data permissions → Agents", issue a token for this service. Write the raw token to a local file on each NameNode, for example `/etc/hadoop/grantforge/token`, readable by the user the NameNode runs as.
3. Choose the matching jar from `agents/hdfs/<Hadoop-line>/` using the table above and put it on the NameNode classpath, for example `$HADOOP_HOME/share/hadoop/hdfs/lib/`. The jar bundles its policy engine, Jackson and signature library; the NameNode supplies Hadoop classes. Heartbeats include both the product and compiled Hadoop versions.
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
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

The agent artifact ends up in `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`. Unit tests cover NameNode authorization callbacks, configuration, version metadata and policy decisions; the WebHDFS and Kerberos tests start temporary local services. Before going to production, also verify read/write, create, rename, recursive delete, HA failover and cached behavior after a network outage on the target cluster.

Integration verification runs with the `verify` phase using Testcontainers (unit tests do not start a cluster; `verify` requires a working Docker daemon):

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# Same entry point as nightly
bash script/ci/hdfs_integration.sh
# Verify one numbered line
bash script/ci/hdfs_integration.sh 2.10
```

Tests use pinned Apache Hadoop images. Older images are built from a pinned Java 8 base and Apache archives verified with SHA-512. They load the matching packaged agent and assert the actual Hadoop and JVM versions. Testcontainers manages isolated NameNode and DataNode containers and checks reads, writes, create, append, rename, delete, recursive and snapshot denies, native permissions and audits, policy refresh, restarting with the signed cache while offline, and strict mode versus native fallback without a snapshot.

A running Docker daemon is required, and downloading the test images must be allowed. If Docker is unavailable the tests fail; they are never silently skipped. The filesystem client runs inside the Hadoop container, and the policy HTTP service uses Testcontainers' host port forwarding; no external Hadoop cluster is needed. Containers and the test network are cleaned up after the tests, and logs are saved to `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`.

The HA test starts two NameNodes, one DataNode and one JournalNode, and configures an independent instance name and cache directory for each of the two agents. It uses the logical HDFS client to switch the active node manually and verifies reads, writes and deny policies after the switch. The single JournalNode exists only for testing: quorum fault tolerance is not verified, and ZooKeeper-based automatic failover is not involved.

The automatic HA test (Hadoop 3.5.0 only, Java 17 and 21) starts ZooKeeper, three JournalNodes, two NameNodes each with its ZKFC, and one DataNode. It verifies that when the active NameNode is killed, ZooKeeper has the other one take over, which keeps enforcing the policies, with denials attributed to its own instance; that policies published while a NameNode is away apply once it returns as standby, and still apply after the role moves back, never an older version; that without the policy server the NameNode taking over keeps enforcing the snapshot it holds and reports its audit once the server returns; and that with one of three JournalNodes stopped writes still succeed, while with two stopped the write fails and the active NameNode stops rather than going on without a quorum. A Kerberos variant adds JournalNodes that sign in from a keytab and serve HTTPS only, ZKFCs that authenticate to ZooKeeper with SASL, and election znodes only the NameNodes' principal may use (an unauthenticated ZooKeeper client cannot even read them); it verifies that the other NameNode takes over when the active one is stopped and keeps enforcing for Kerberos users, that a client without a ticket is refused throughout, and that the role moves back once the first NameNode returns. The JournalNodes run under a principal with the NameNodes' short name, because a JournalNode hands edits only to a NameNode's full principal or to a requestor with its own short name, and HTTP authentication gives it short names only.

The Kerberos test runs on Hadoop 3.5.0 only (Java 17 and 21): the KDC runs in the test JVM, and the NameNode and DataNode start in secure mode with their own keytabs, the DataNode transferring data only after SASL and serving HTTPS only, and WebHDFS using SPNEGO. It verifies that GrantForge policies apply to reads and writes once principals map to short names, that a user without a policy is refused, that denials are audited under the short name, that a client without a ticket is refused without falling back to simple authentication, and that a restarted NameNode signs in again and keeps enforcing. Kerberos on the other lines is not yet verified.

Tests live in production modules' `src/test`. Common policy tests run in the common module; shared native unit tests run in `agents/grantforge-agent-hdfs-native/src/test` instead of compiling into all six adapters. Version-specific callback tests remain in their numbered modules, and shared container sources in `agents/grantforge-agent-hdfs-common/src/test/shared` still compile into each numbered module. There is no separate Maven test project; Testcontainers is test scope only. Nightly runs all six Hadoop versions on Java 17 and 21 hosts while each container uses the JVM in the table. Container coverage currently includes Simple authentication, manual HA, and Kerberos, automatic HA and both together on Hadoop 3.5.0; Kerberos on other versions, TLS and vendor patches require target-environment verification.

For the Hadoop extension entry points and permission semantics, see the [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) and the [HDFS Permissions Guide](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
