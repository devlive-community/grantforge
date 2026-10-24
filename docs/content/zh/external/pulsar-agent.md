---
title: Apache Pulsar 代理
description: 在 Pulsar broker 中安装授权提供者，用 GrantForge 策略决定谁可以在哪里生产与消费、通过哪些订阅、运行 Functions、Sources 与 Sinks、管理主题、命名空间、租户与集群，以及可以从哪些网络发起。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Pulsar 代理是 broker 的授权提供者：Pulsar 询问它谁可以生产、消费、运行 Functions、Sources 与 Sinks，以及管理主题、命名空间、租户与集群，它依据 `pulsar` 数据服务的签名策略作答。发行包以一个 Java 17 的 JAR 提供它：`agents/pulsar/grantforge-agent-pulsar-<version>.jar`，依赖已内嵌并重定位。

仓库的 Testcontainers 集成测试在 Pulsar `3.0.17`、`3.3.9`、`4.0.14` 与 `4.2.5` 的 broker 中运行该 JAR。这并不代表认证了其他所有版本。服务插件见 [Apache Pulsar](/plugins/pulsar/)。

## 工作方式

- **每次检查都在主题名称的每一级判定。** 与 Pulsar 自己的授权一样，租户或命名空间上的权限覆盖其中的内容，因此代理在租户 `sales`、命名空间 `orders` 与主题 `payments` 上判定一次向 `persistent://sales/orders/payments` 的生产：任一处拒绝都会拒绝，任一处允许都会授予。
- **消费还会按订阅判定。** 在上述每一级，代理还会询问“主题加订阅”，因此一条策略可以让角色只通过部分订阅（例如 `billing-*`）消费。
- **集群管理员就是 Pulsar 的超级用户。** broker 所在集群上的 `admin` 会让角色成为超级用户，而 Pulsar 允许超级用户做任何事，因此下层策略不会限制他们。Pulsar 的 broker 与集群操作需要所指集群上的 `admin`。
- **策略可以只对部分网络生效。** 每次检查都带上客户端地址，代理用 broker 网络栈所用的 Netty 地址范围评估 **客户端地址** 条件。
- **拒绝通过 Pulsar 授权。** Pulsar 自己的授权与租户的管理员角色不再使用，因此 `pulsar-admin namespaces grant-permission` 之类的命令会得到 HTTP 405，并提示到 GrantForge 中授权。
- **拒绝会连同原因记入日志。** 客户端收到的拒绝由 Pulsar 措辞；broker 日志会写明是哪条 GrantForge 策略拒绝的，或者没有策略允许。
- **缓存决策并合并访问。** 当前快照的决策会被缓存，默认 100,000 条、10 秒，新快照会重新开始。窗口内（默认 10 秒）相同的允许访问会作为一条访问事件上报，并注明次数；拒绝会立即逐条上报。每个事件都注明主题、命名空间、租户或集群，以及客户端地址。
- **一个代理服务整个 broker。** broker 会为二进制协议、Web 服务与 Functions Worker 分别加载授权提供者；它们共用一个代理，代理随 broker 一起停止。

| 操作 | 所需访问 |
| --- | --- |
| 生产，或通过生产创建主题 | 主题上的 `produce` |
| 订阅、消费、取消订阅、跳过、查看、过期消息、重置游标 | 主题加订阅上的 `consume` |
| 查找主题，读取其统计、元数据与策略；列出命名空间的主题 | `produce` 或 `consume` |
| 终止、压缩、卸载到分层存储、unload 或截断主题；修改其策略 | 主题上的 `admin_topic` |
| 创建与删除主题；修改命名空间的策略 | 命名空间上的 `admin_namespace` |
| 运行 Functions、Sources、Sinks；使用 Packages | 命名空间上的 `functions`、`sources`、`sinks`、`packages` |
| 创建、列出与删除租户的命名空间 | 租户上的 `admin_tenant` |
| 创建、修改与删除租户和集群；Pulsar 的 broker 操作 | 集群上的 `admin` |

## 部署

1. 添加 `pulsar` 数据服务并配置其策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有 Pulsar 运行账号可读的文件。
2. 把 JAR 复制到 **每个 broker** 的 `lib`，例如 `/pulsar/lib`。
3. 在每个 broker 的 `broker.conf`（或 `standalone.conf`）中加入以下设置，替换示例中的地址、路径与实例名：

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   认证也必须启用，客户端才有角色。`superUserRoles` 与 `proxyRoles` 只能写 broker 自己的角色与代理的角色：代理不检查它们，而 Pulsar 也会检查经由代理的原始客户端。管理员请改为授予集群上的 `admin`。缓存目录必须可由 Pulsar 运行账号写入。在容器中请把 `grantforge.*` 设置写进配置文件：部分 Pulsar 镜像的 shell 会丢弃名称中带点的环境变量，例如 `PULSAR_PREFIX_grantforge.token.file`。
4. 逐个重启 broker。在代理收到第一个快照之前，除 broker 与代理自身外的请求都会被拒绝。
5. **先迁移已有权限：** Pulsar 自己在命名空间、主题与订阅上的授权，以及租户的管理员角色，都将不再生效。请事先创建对应的策略。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、该 broker 的名称，以及存放签名快照与暂存事件的可写目录 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 连接与读取超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）与暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 缓存决策的最大数量，默认 `100000`，最多 `10000000`，`0` 表示不缓存；以及缓存时长，默认 `10000`，范围 `1..3600000` 毫秒 |

## 用户、组与角色

策略把 Pulsar 认证给客户端的角色（例如 token 的 subject 或证书的 common name）当作用户。Pulsar 没有自己的组：组为 GrantForge 中该角色所在的组，角色为 GrantForge 授予它的角色。

## 决策与限制

- 没有可用快照时，除 broker 与代理的请求外一切都会被拒绝。未启用的服务让 Pulsar 不受检查，与关闭 `authorizationEnabled` 时一样。
- 绑定到类型不是 `pulsar` 的服务的令牌会拒绝一切。
- Pulsar 在许多检查之前会先询问角色是否为超级用户或租户管理员；只有让请求通过的回答会被上报，而请求本身的检查无论结果都会上报。若 Pulsar 只凭这一询问决定某个请求（Pulsar 3.0 对租户与集群就是如此），被拒绝时只在 debug 级别记录。
- 集群上的 `admin` 只让角色成为该集群 broker 的超级用户。
- Pulsar 的权限列表（例如 `pulsar-admin namespaces permissions`）会失败：策略保存在 GrantForge 中。
- 客户端地址只能是地址与网段；主机名不匹配任何客户端。
- 决策会缓存 `grantforge.decision.cache.ttl.ms`，因此策略的生效或失效最多会延迟这么久。

## 相关指南

- [Apache Pulsar](/plugins/pulsar/)
- [数据服务、策略与代理](/external/data-services/)
