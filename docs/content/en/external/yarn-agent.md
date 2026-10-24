---
title: Apache Hadoop YARN agent
description: Install the authorization provider in YARN ResourceManagers to decide who may submit which applications to which queue, administer queues and the cluster, from which networks, with GrantForge policies.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The YARN agent is an authorization provider of the ResourceManager: YARN asks it who may submit to a queue, administer a queue and administer the cluster, and it answers from the `yarn` data service's signed policies. The distribution ships it as one Java 8-compatible JAR, `agents/yarn/grantforge-agent-yarn-<version>.jar`, with its dependencies shaded inside.

The repository's Testcontainers integration tests run the JAR inside the ResourceManagers of Hadoop `3.2.4`, `3.3.6`, `3.4.3` and `3.5.0`. This does not certify every other release. See [Apache Hadoop YARN](/en/plugins/yarn/) for the service plugin.

## How it works

- **Every check is decided at every queue of the path.** As in YARN, a permission on a queue covers the queues below it, so the agent decides a submission to `root.sales.etl` at `root`, `root.sales` and `root.sales.etl`: a denial at any of them refuses it, an allowance at any of them grants it.
- **Submissions are decided by the application's name too.** At each of those queues the agent also asks about the queue with the application's name, so a policy can admit only some applications, such as `payroll-*`, to a queue and the queues below it.
- **Policies can hold for some networks only.** Every check carries the client's address, the first forwarded one behind a proxy, and the agent evaluates the **Client addresses** condition with Hadoop's own host lists.
- **The cluster's administration is under policy.** YARN's administrative operations, which `yarn.admin.acl` grants otherwise, need `admin` on the cluster.
- **Refusals are logged with their reason.** YARN words the refusal the client gets; the ResourceManager log says which GrantForge policy denied it, or that none allowed it.
- **Decisions are kept and accesses folded.** The decisions of the current snapshot are kept, by default 100,000 for 10 seconds, and a new snapshot starts afresh. Identical allowed accesses within a window, by default 10 seconds, are reported as one access event that says how many there were; refusals are reported one by one at once. Each event names the application, the queue and the client's address.
- **The ResourceManager is not checked.** The ResourceManager's own user, such as `yarn`, is neither checked nor audited.

| Operation | Access needed |
| --- | --- |
| Submit an application | `submit_app` on its queue, or on its queue with the application's name; `admin_queue` on the queue does too |
| Kill, move or change the priority of another user's application, or see all of its details | `admin_queue` on its queue, and moving it also `submit_app` on the new queue; the application's owner may always, as may what its own ACLs allow |
| `yarn rmadmin`: refresh queues, nodes, user groups and ACLs, change node labels and attributes, and the other administrative operations | `admin` on the cluster |

## Deployment

1. Add a `yarn` data service and configure its policies. Open **Data permissions → Agents**, select the service and issue an agent token. It is shown only once; store it in a file only YARN's account can read.
2. Copy the JAR into `share/hadoop/yarn/lib` of **every ResourceManager**, such as `/opt/hadoop/share/hadoop/yarn/lib`.
3. Add the settings to `yarn-site.xml` of each, replacing the example address, paths, user and instance name:

   ```xml
   <configuration>
     <property>
       <name>yarn.acl.enable</name>
       <value>true</value>
     </property>
     <property>
       <name>yarn.authorization-provider</name>
       <value>org.devlive.grantforge.yarn.agent.GrantForgeYarnAuthorizer</value>
     </property>
     <property>
       <name>yarn.admin.acl</name>
       <value>yarn</value>
     </property>
     <property>
       <name>grantforge.server.url</name>
       <value>https://grantforge.example.com</value>
     </property>
     <property>
       <name>grantforge.token.file</name>
       <value>/etc/hadoop/grantforge.token</value>
     </property>
     <property>
       <name>grantforge.instance</name>
       <value>resourcemanager-1</value>
     </property>
     <property>
       <name>grantforge.cache.dir</name>
       <value>/var/lib/hadoop-yarn/grantforge-cache</value>
     </property>
   </configuration>
   ```

   `yarn.admin.acl` must name only the ResourceManager's own user: YARN lets its administrators do anything to every application before it asks the agent about the queue. The cache directory must be writable by YARN's account.
4. Restart the ResourceManagers one at a time. Until the agent has its first snapshot, requests other than the ResourceManager's own are refused.
5. **Move existing permissions first:** the queue ACLs of the scheduler's configuration, such as `acl_submit_applications` and `acl_administer_queue`, no longer apply. Create matching policies beforehand.

| Setting | Purpose |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Required: the GrantForge server, the agent token's file, a name for this ResourceManager and a writable directory for the signed snapshot and spooled events |
| `grantforge.signing.key.file` | Optional file pinning the snapshot signing key; copy the Base64-encoded X.509 Ed25519 public key from the Agents page. If omitted, the key is fetched from the server on first contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge connection and read timeouts, default `5000` / `8000` milliseconds |
| `grantforge.refresh.interval.ms` | How often the agent asks for new policies, default `30000`, at least `1000` milliseconds |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Access events: batch size (default `500`), queue size (default `10000`), sending interval (default `5000` milliseconds) and spool limit (default 64 MiB) |
| `grantforge.audit.fold.window.ms` | How long identical allowed accesses are folded into one event, default `10000`, up to `3600000` milliseconds; `0` reports each |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | The most decisions kept, default `100000`, up to `10000000`, `0` keeps none; and for how long, default `10000`, `1..3600000` milliseconds |

## Users, groups and roles

Policies name the short name YARN gives the caller: the user name of simple authentication, or the short name of a Kerberos principal by `hadoop.security.auth_to_local`. Groups are those Hadoop's group mapping gives the user on the ResourceManager, together with the groups GrantForge puts the user in; roles are those GrantForge gives the user.

## Decisions and limitations

- Without a usable snapshot, everything but the ResourceManager's own requests is refused. A service that is not in use leaves YARN unchecked, as it is with `yarn.acl.enable` off.
- A token bound to a service whose type is not `yarn` refuses everything.
- YARN asks about its administration without the client's address, so a Client addresses condition never holds on `cluster`.
- An application's owner, and the users its own ACLs name, manage it whatever the policies say, as in YARN.
- Checks of other access types, such as application priority ACLs, are refused.
- A host name in a Client addresses condition costs a name lookup of the client whenever a decision is made rather than kept.
- Decisions are kept for `grantforge.decision.cache.ttl.ms`, so a policy's validity period starts or ends up to that much late.

## Related guides

- [Apache Hadoop YARN](/en/plugins/yarn/)
- [Data services, policies and agents](/en/external/data-services/)
