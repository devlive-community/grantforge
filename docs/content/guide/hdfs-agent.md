---
title: HDFS NameNode 代理
description: 在 Hadoop 3.5.0 NameNode 内执行 GrantForge 路径策略，并上报访问审计。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

服务端的 `grantforge-plugin-hdfs` 定义资源和连接配置；`grantforge-agent-hdfs` 安装在 NameNode 内，通过 Hadoop 的 `INodeAttributeProvider` 与 `AccessControlEnforcer` 检查访问，复用 GrantForge 代理核心下载签名策略、保存本地快照和批量上报审计。当前代理针对 **Hadoop 3.5.0、Java 17 及以上**构建，其他 Hadoop 版本需要对应版本的适配和验证。

## 权限关系

代理先执行 HDFS 原生权限检查，再执行 GrantForge 策略：用户需要同时满足原生权限与策略要求。GrantForge 的允许策略不会绕过 POSIX 权限、ACL、所有者检查或 sticky bit；拒绝策略始终拒绝。原生权限设置仍通过 Hadoop 的管理工具维护。

默认 `grantforge.hdfs.native.fallback=false`：没有本地策略快照、没有匹配策略或代理尚未启动时拒绝数据访问。设为 `true` 后，未被策略决定的访问使用原生权限；显式拒绝策略仍然有效。服务端暂时不可达时继续使用最后一份通过签名验证的本地快照。

一次授权回调中的祖先、目标、子树和快照路径投影使用同一版策略快照；刷新后的策略在下一次回调生效，避免组合不同版本的允许规则。访问审计记录实际使用的策略版本。

代理检查普通用户访问目标所需的 `read`、`write`、`execute`，也检查父目录、祖先目录与需要递归校验的子目录。创建、删除、重命名等操作涉及多个路径，允许策略必须覆盖它们。严格模式下，只有目标文件的 `read` 策略还不够，需要给用户配置祖先目录的 `execute` 策略，例如允许 `/` 上的 `execute` 并勾选递归，再为实际数据目录配置读写权限。

快照路径同时检查实际请求路径和去掉 `.snapshot/<快照名>` 后的原路径，例如 `/data/.snapshot/s1/secret` 同时检查 `/data/secret`。原路径上的拒绝策略因此也约束快照；可以再为显式快照路径设置更严格的限制。元数据查询沿用 HDFS 的目录遍历权限语义。

一次递归授权最多检查 `100000` 个 inode，超过上限会拒绝操作，避免在 NameNode 内无限分配内存。超长路径使用完整路径判定策略；审计资源展示限制为 `1000` 字符，并在请求详情中记录原长度和 SHA-256 摘要。

HDFS 超级用户仍由 Hadoop 管理。带路径的超级用户回调先通过 Hadoop 的超级用户检查，再按 Hadoop 3.5.0 提供的操作名检查策略：文件读取与元数据查询要求 `read`，目录枚举要求 `read` + `execute`，已知修改操作要求 `write`。未知、缺失或无法准确推断的操作（例如 `checkAccess` 和 `concat`）保守地要求三种权限。

超级用户回调没有完整 inode 与子树上下文，无路径的集群管理调用保留原生检查；无法用子目录策略限制超级用户的所有递归操作。数据使用者应使用普通 Hadoop 用户。

## 部署

1. 在 GrantForge 的数据服务中添加 `hdfs` 服务，保存配置并测试连接；为实际 Hadoop 短用户名、用户组或角色配置路径策略。
2. 在“数据权限 → 代理”中为这个服务签发令牌。将令牌原文写到每个 NameNode 的本地文件，例如 `/etc/hadoop/grantforge/token`，由 NameNode 运行用户读取。
3. 将发行包 `agents/hdfs/` 下与当前发行版本对应的 `grantforge-agent-hdfs-<版本>.jar` 放入 NameNode 的类路径，例如 `$HADOOP_HOME/share/hadoop/hdfs/lib/`。代理 jar 已包含自己的策略引擎、Jackson 和签名库，Hadoop 类由 NameNode 提供；心跳中的代理版本由构建元数据生成。
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
./mvnw -pl plugins/grantforge-agent-hdfs -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,plugins/grantforge-agent-hdfs,core/grantforge-plugin-host -am test
```

代理产物位于 `plugins/grantforge-agent-hdfs/target/grantforge-agent-hdfs-<版本>.jar`。单元测试覆盖 NameNode 授权回调、配置、版本元数据和策略决策；WebHDFS 与 Kerberos 测试启动本机临时服务。上线前还需在目标集群验证读写、创建、重命名、递归删除、HA 切换和断网后的缓存行为。

集成验证通过独立的 `hdfs-it` profile 和 Testcontainers 运行，默认单元测试不启动集群：

```sh
./mvnw -Phdfs-it -pl plugins/grantforge-agent-hdfs -am verify
# 与 nightly 使用同一入口
bash script/ci/hdfs_integration.sh
```

测试使用固定版本的 Apache Hadoop 容器镜像，将真实打包的代理 jar 放入 NameNode 类路径。Testcontainers 创建隔离网络并管理 NameNode、DataNode 的生命周期，验证读写、创建、追加、重命名、删除、递归与快照拒绝、原生权限及审计、策略刷新、断开策略服务器后重启 NameNode 使用签名缓存，以及无快照时的严格模式与原生权限回退。

需要运行中的 Docker daemon，并允许下载测试镜像。Docker 不可用时测试失败，不会静默跳过。文件系统客户端在 Hadoop 容器内部执行，策略 HTTP 服务使用 Testcontainers 的主机端口转发；无需外部 Hadoop 集群。测试结束后清理容器和测试网络，日志保存到 `plugins/grantforge-agent-hdfs/target/hdfs-testcontainers`。

HA 测试启动两个 NameNode、一个 DataNode 和一个 JournalNode，为两个代理配置独立实例名和缓存目录。它使用逻辑 HDFS 客户端手动切换活动节点，并验证切换后的读写和拒绝策略；单 JournalNode 仅用于测试，不验证多数派容错，也不涉及 ZooKeeper 自动故障转移。

测试源码和依赖直接放在现有 `plugins/grantforge-agent-hdfs` 的 `src/test` 和 test scope 中，不单独建立 Maven 测试项目；Testcontainers 不会进入代理发行包。nightly 在 Java 17 和 21 上运行同一测试入口，并保存报告与容器日志。

Hadoop 扩展入口与权限语义见 [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) 和 [HDFS 权限指南](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html)。
