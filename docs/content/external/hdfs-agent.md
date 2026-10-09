---
title: Apache Hadoop HDFS NameNode 代理
description: 选择与 Hadoop 版本对应的 NameNode 代理，执行 GrantForge 路径策略并上报访问审计。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

服务端的 `grantforge-plugin-hdfs` 统一提供 `hdfs` 服务类型、资源和连接配置。NameNode 代理按 Hadoop 版本分别构建，通过对应版本的 `INodeAttributeProvider` 与 `AccessControlEnforcer` 检查访问，共享签名策略、缓存、策略求值和审计逻辑。

源码中的服务端插件位于 `plugins/grantforge-plugin-hdfs`，版本代理位于 `agents/grantforge-agent-hdfs-<line>`，不依赖 Hadoop 的公共生产逻辑位于 `agents/grantforge-agent-hdfs-common`。共享原生适配位于正式 Maven 生产模块 `agents/grantforge-agent-hdfs-native`，采用 Java 8 / Hadoop 2.7.7 基线；各编号模块通过 Maven 二进制依赖引用它，保留各版入口与特有回调，不再重复编译共享生产源码。协议、快照缓存和审计基础设施仍位于 `core/grantforge-agent-core`。

服务端插件保持一个 `hdfs` 类型，客户端版本独立于代理版本。Hadoop 2.x 的连接与路径查询使用 `webhdfs://namenode:50070`（启用 HTTPS 时使用对应的 `swebhdfs://` 地址）；Hadoop 3.x 可使用 RPC `hdfs://` 或 WebHDFS。容器矩阵验证 2.x 的 WebHDFS，以及 3.x 的 RPC 和 WebHDFS；未认证 2.x RPC，不会自动切换连接协议。

Apache Hadoop 2.7.7 在启用 inode 属性扩展后，普通用户直接查询根路径 `/` 会在原生代码中触发空指针，发生在代理回调之前。此版本的数据操作与服务的 `lookup.path` 应使用实际数据目录，例如 `/data`；测试保留了根路径失败的独立断言。

## 选择版本

| 代理模块后缀 | 验证的 Apache Hadoop | 容器 JVM | 授权接口 | 超级用户路径回调 |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | 参数式 | 无 |
| `2.10` | 2.10.2 | Java 8 | 参数式 | 无 |
| `3.2` | 3.2.4 | Java 8 | 参数式 | 无 |
| `3.3` | 3.3.6 | Java 8 | 上下文式 | 无 |
| `3.4` | 3.4.3 | Java 11 | 上下文式、超级用户及拒绝回调 | 有 |
| `3.5` | 3.5.0 | Java 17 | 上下文式、超级用户及拒绝回调 | 有 |

例如 Hadoop 2.10.2 安装 `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar`。每台 NameNode 只安装一个版本代理，升级时移除旧 jar，配置中的 provider 类名保持不变。代理启动时核对 Hadoop 主次版本及必需回调，装错版本会停止启动；同一主次版本的其他补丁及厂商分支仍需要独立验证。

`common` 和 `2.7` 至 `3.4` 的代理主代码使用 Java 8 字节码，`3.5` 使用 Java 17。上表列出实际集成测试的 JVM，不代表 3.4.3 已验证 Java 8。3.3.6 官方测试镜像仅提供 amd64，在 ARM 宿主上明确使用 amd64 模拟运行。

## 权限关系

代理先执行 HDFS 原生权限检查，再执行 GrantForge 策略：用户需要同时满足原生权限与策略要求。GrantForge 的允许策略不会绕过 POSIX 权限、ACL、所有者检查或 sticky bit；拒绝策略始终拒绝。原生权限设置仍通过 Hadoop 的管理工具维护。

默认 `grantforge.hdfs.native.fallback=false`：没有本地策略快照、没有匹配策略或代理尚未启动时拒绝数据访问。设为 `true` 后，未被策略决定的访问使用原生权限；显式拒绝策略仍然有效。服务端暂时不可达时继续使用最后一份通过签名验证的本地快照。

一次授权回调中的祖先、目标、子树和快照路径投影使用同一版策略快照；刷新后的策略在下一次回调生效，避免组合不同版本的允许规则。访问审计记录实际使用的策略版本。

代理检查普通用户访问目标所需的 `read`、`write`、`execute`，也检查父目录、祖先目录与需要递归校验的子目录。创建、删除、重命名等操作涉及多个路径，允许策略必须覆盖它们。严格模式下，只有目标文件的 `read` 策略还不够，需要给用户配置祖先目录的 `execute` 策略，例如允许 `/` 上的 `execute` 并勾选递归，再为实际数据目录配置读写权限。

快照路径同时检查实际请求路径和去掉 `.snapshot/<快照名>` 后的原路径，例如 `/data/.snapshot/s1/secret` 同时检查 `/data/secret`。原路径上的拒绝策略因此也约束快照；可以再为显式快照路径设置更严格的限制。元数据查询沿用 HDFS 的目录遍历权限语义。

一次递归授权最多检查 `100000` 个 inode，超过上限会拒绝操作，避免在 NameNode 内无限分配内存。超长路径使用完整路径判定策略；审计资源展示限制为 `1000` 字符，并在请求详情中记录原长度和 SHA-256 摘要。

Hadoop 2.7、2.10、3.2、3.3 在调用代理前跳过超级用户，代理无法管控或审计这些访问。Hadoop 3.4、3.5 的带路径超级用户回调先通过原生检查，再按操作名检查策略：文件读取与元数据查询要求 `read`，目录枚举要求 `read` + `execute`，已知修改操作要求 `write`。未知、缺失或无法准确推断的操作（例如 `checkAccess` 和 `concat`）保守地要求三种权限。

超级用户回调没有完整 inode 与子树上下文，无路径的集群管理调用保留原生检查；无法用子目录策略限制超级用户的所有递归操作。数据使用者应使用普通 Hadoop 用户。

## 指标

代理通过 Hadoop 的 Metrics2 体系上报指标，与 NameNode 自身的 dfs 指标使用同一套 sink，在 NameNode 的 JMX 里是 `Hadoop:service=NameNode,name=GrantForgeHdfsAgent`（Prometheus 的 JMX 导出器可以直接抓取）。每个指标带有 `instance`（数据服务实例名）与 `agentVersion` 标签，HA 的两个 NameNode 因此可以分别查看。指标注册失败只损失指标本身：代理会记录警告并在没有指标的情况下继续执行授权。

| 指标 | 说明 |
| --- | --- |
| `Callbacks` | 代理执行的授权回调数 |
| `SuperuserCallbacks` | 代理执行的超级用户回调数 |
| `NativeDenies` | Hadoop 在代理之前就拒绝的访问数 |
| `EvaluationFailures` | 因策略求值异常而失败关闭的回调数 |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | 策略判定为允许 / 拒绝 / 未决的权限数；未决在严格模式下同样拒绝 |
| `MissingSnapshots` | 没有已验证策略快照时服务的回调数 |
| `SnapshotVersion` | 当前使用的策略快照版本，0 表示没有 |
| `QueuedEvents` / `DroppedEvents` | 内存中待上报的审计事件数 / 因队列或磁盘缓冲满而丢弃的事件数 |
| `ServerReachable` | 最后一次访问策略服务器是否成功（1/0） |

## 部署

1. 在 GrantForge 的数据服务中添加 `hdfs` 服务，保存配置并测试连接；为实际 Hadoop 短用户名、用户组或角色配置路径策略。
2. 在“数据权限 → 代理”中为这个服务签发令牌。将令牌原文写到每个 NameNode 的本地文件，例如 `/etc/hadoop/grantforge/token`，由 NameNode 运行用户读取。
3. 按上表选择发行包 `agents/hdfs/<Hadoop版本线>/` 下对应的代理 jar，放入 NameNode 的类路径，例如 `$HADOOP_HOME/share/hadoop/hdfs/lib/`。代理 jar 已包含自己的策略引擎、Jackson 和签名库，Hadoop 类由 NameNode 提供；心跳中的代理版本包含产品版本与编译 Hadoop 版本。
4. 在每个 NameNode 的 `hdfs-site.xml` 中配置以下属性；HA 的两个 NameNode 使用不同的 `instance` 和各自本地的缓存目录。

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

5. 确认 `dfs.permissions.enabled=true`，且 `dfs.namenode.inode.attributes.provider.bypass.users` 为空；代理启动时会拒绝可绕过授权回调的配置。重启 NameNode，然后检查 GrantForge 代理页面中的心跳与策略版本。代理读取 NameNode 现有配置；不修改 inode 的原生属性。

首次部署可先使用 `native.fallback=true`，确认策略快照已同步并补齐祖先目录权限，再切换严格模式。令牌绑定的服务类型必须是 `hdfs`；配置错误或绑定到其他服务类型会拒绝访问。

## 可选设置

| 属性 | 默认值 | 用途 |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | 连接 GrantForge 的超时 |
| `grantforge.hdfs.read.timeout.ms` | `8000` | 读取响应的超时 |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | 服务器不可达时的策略刷新间隔，至少 `1000`；正常心跳使用服务端建议间隔 |
| `grantforge.hdfs.signing.key.file` | 未设置 | 可选签名公钥文件，内容为控制台提供的 Base64 X.509 公钥；配置后只接受该公钥的签名 |
| `grantforge.hdfs.audit.batch.size` | `500` | 每次上报最多事件数，范围 `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | 内存审计队列容量，范围 `1..1000000`，至少能容纳一个批次；队列满时计数并丢弃新事件 |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | 审计刷新间隔，正整数，最大 `2147483647`；调小可降低上报延迟 |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | 服务器不可达时磁盘缓冲上限，非负整数；`0` 禁用磁盘缓冲 |

突发访问量较大时可增大审计队列，减少队列溢出；缩短刷新间隔可降低审计延迟，也会增加上报频率。磁盘缓冲限制用于控制长时间断网的磁盘占用，禁用或耗尽缓冲时事件可能丢失。审计上报在后台执行，不等待策略服务器响应。

未配置签名公钥时，代理首次从服务器获取公钥并随快照保存。代理使用 Hadoop 传入的短用户名和用户组，角色及额外组来自签名快照；Kerberos principal 的短名映射由集群的 `hadoop.security.auth_to_local` 决定。

## 从源码构建与验证

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

代理产物位于 `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`。单元测试覆盖 NameNode 授权回调、配置、版本元数据和策略决策；WebHDFS 与 Kerberos 测试启动本机临时服务。上线前还需在目标集群验证读写、创建、重命名、递归删除、HA 切换和断网后的缓存行为。

集成验证随 `verify` 阶段用 Testcontainers 运行（单元测试不启动集群；`verify` 需要可用的 Docker daemon）：

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# 与 nightly 使用同一入口
bash script/ci/hdfs_integration.sh
# 只验证指定版本线
bash script/ci/hdfs_integration.sh 2.10
```

测试使用固定版本的 Apache Hadoop 镜像；旧版镜像使用固定摘要的 Java 8 基础镜像和经 SHA-512 校验的 Apache 发行包构建。将对应版本的真实代理 jar 放入 NameNode 类路径，并断言实际 Hadoop 与 JVM 版本。Testcontainers 创建隔离网络并管理 NameNode、DataNode 的生命周期，验证读写、创建、追加、重命名、删除、递归与快照拒绝、原生权限及审计、策略刷新、断开策略服务器后重启 NameNode 使用签名缓存，以及无快照时的严格模式与原生权限回退。

需要运行中的 Docker daemon，并允许下载测试镜像。Docker 不可用时测试失败，不会静默跳过。文件系统客户端在 Hadoop 容器内部执行，策略 HTTP 服务使用 Testcontainers 的主机端口转发；无需外部 Hadoop 集群。测试结束后清理容器和测试网络，日志保存到 `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`。

HA 测试启动两个 NameNode、一个 DataNode 和一个 JournalNode，为两个代理配置独立实例名和缓存目录。它使用逻辑 HDFS 客户端手动切换活动节点，并验证切换后的读写和拒绝策略；单 JournalNode 仅用于测试，不验证多数派容错，也不涉及 ZooKeeper 自动故障转移。

测试源码位于实际生产模块的 `src/test`。公共策略单元测试在 common 中运行，共享原生单元测试在 `agents/grantforge-agent-hdfs-native/src/test` 中运行，不再编译进六个版本代理。各版回调测试仍在对应的编号模块中，共用容器测试源码 `agents/grantforge-agent-hdfs-common/src/test/shared` 仍编译到各编号模块；没有单独的 Maven 测试项目。Testcontainers 仅为 test scope。nightly 在 Java 17 和 21 宿主上验证六个 Hadoop 版本，Hadoop 容器使用上表中的 JVM。当前容器覆盖 Simple 认证与手动 HA；Kerberos、TLS 和厂商补丁仍需目标环境验证。

Hadoop 扩展入口与权限语义见 [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) 和 [HDFS 权限指南](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html)。
