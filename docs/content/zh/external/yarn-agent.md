---
title: Apache Hadoop YARN 代理
description: 在 YARN ResourceManager 中安装授权提供者，用 GrantForge 策略决定谁可以把哪些应用提交到哪个队列、谁可以管理队列与集群、可以从哪些网络发起。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

YARN 代理是 ResourceManager 的授权提供者：YARN 询问它谁可以向队列提交、管理队列以及管理集群，它依据 `yarn` 数据服务的签名策略作答。发行包以一个兼容 Java 8 的 JAR 提供它：`agents/yarn/grantforge-agent-yarn-<version>.jar`，依赖已遮蔽打包在内。

仓库的 Testcontainers 集成测试在 Hadoop `3.2.4`、`3.3.6`、`3.4.3` 与 `3.5.0` 的 ResourceManager 中运行该 JAR。这并不代表认证了其他所有版本。服务插件见 [Apache Hadoop YARN](/plugins/yarn/)。

## 工作方式

- **每次检查都在路径上的每个队列判定。** 与 YARN 一样，队列上的权限覆盖其下的队列，因此代理在 `root`、`root.sales` 与 `root.sales.etl` 上判定一次向 `root.sales.etl` 的提交：任一处拒绝都会拒绝，任一处允许都会授予。
- **提交还会按应用名称判定。** 在上述每个队列上，代理还会询问“队列加应用名称”，因此一条策略可以只接受部分应用（例如 `payroll-*`）进入某个队列及其下的队列。
- **策略可以只对部分网络生效。** 每次检查都带上客户端地址（在代理之后为第一个转发地址），代理用 Hadoop 自己的主机列表评估 **客户端地址** 条件。
- **集群管理受策略约束。** YARN 的管理操作（否则由 `yarn.admin.acl` 授予）需要集群上的 `admin`。
- **拒绝会连同原因记入日志。** 客户端收到的拒绝由 YARN 措辞；ResourceManager 日志会写明是哪条 GrantForge 策略拒绝的，或者没有策略允许。
- **缓存决策并合并访问。** 当前快照的决策会被缓存，默认 100,000 条、10 秒，新快照会重新开始。窗口内（默认 10 秒）相同的允许访问会作为一条访问事件上报，并注明次数；拒绝会立即逐条上报。每个事件都注明应用、队列与客户端地址。
- **不检查 ResourceManager 自身。** ResourceManager 自己的用户（例如 `yarn`）既不检查也不审计。

| 操作 | 所需访问 |
| --- | --- |
| 提交应用 | 所在队列上的 `submit_app`，或所在队列加应用名称上的 `submit_app`；队列上的 `admin_queue` 也可以 |
| 终止、移动或更改其他用户应用的优先级，或查看其全部详情 | 所在队列上的 `admin_queue`，移动时还需要新队列上的 `submit_app`；应用的所有者以及其自身 ACL 允许的人始终可以 |
| `yarn rmadmin`：刷新队列、节点、用户组与 ACL，修改节点标签与属性，以及其他管理操作 | 集群上的 `admin` |

## 部署

1. 添加 `yarn` 数据服务并配置其策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有 YARN 运行账号可读的文件。
2. 把 JAR 复制到 **每个 ResourceManager** 的 `share/hadoop/yarn/lib`，例如 `/opt/hadoop/share/hadoop/yarn/lib`。
3. 在每个 ResourceManager 的 `yarn-site.xml` 中加入以下设置，替换示例中的地址、路径、用户与实例名：

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

   `yarn.admin.acl` 只能写 ResourceManager 自己的用户：YARN 在向代理询问队列之前，会让它的管理员对所有应用为所欲为。缓存目录必须可由 YARN 运行账号写入。
4. 逐个重启 ResourceManager。在代理收到第一个快照之前，除 ResourceManager 自身外的请求都会被拒绝。
5. **先迁移已有权限：** 调度器配置中的队列 ACL（例如 `acl_submit_applications` 与 `acl_administer_queue`）将不再生效。请事先创建对应的策略。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、该 ResourceManager 的名称，以及存放签名快照与暂存事件的可写目录 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 连接与读取超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）与暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 缓存决策的最大数量，默认 `100000`，最多 `10000000`，`0` 表示不缓存；以及缓存时长，默认 `10000`，范围 `1..3600000` 毫秒 |

## 用户、组与角色

策略中的用户是 YARN 给调用者的短名称：simple 认证下的用户名，或 Kerberos 主体按 `hadoop.security.auth_to_local` 得到的短名称。组为 Hadoop 组映射在 ResourceManager 上给出的组，加上 GrantForge 中该用户所在的组；角色为 GrantForge 授予该用户的角色。

## 决策与限制

- 没有可用快照时，除 ResourceManager 自身的请求外一切都会被拒绝。未启用的服务让 YARN 不受检查，与关闭 `yarn.acl.enable` 时一样。
- 绑定到类型不是 `yarn` 的服务的令牌会拒绝一切。
- YARN 询问管理权限时不带客户端地址，因此客户端地址条件在 `cluster` 上永不成立。
- 应用的所有者以及其自身 ACL 指定的用户无论策略如何都可以管理该应用，与 YARN 一致。
- 其他访问类型的检查（例如应用优先级 ACL）会被拒绝。
- 客户端地址条件中的主机名，每次在不命中缓存时做决策都要对客户端做一次名称查询。
- 决策会缓存 `grantforge.decision.cache.ttl.ms`，因此策略的有效期最多会延迟这么久开始或结束。

## 相关指南

- [Apache Hadoop YARN](/plugins/yarn/)
- [数据服务、策略与代理](/external/data-services/)
