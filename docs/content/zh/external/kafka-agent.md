---
title: Apache Kafka 代理
description: 在 Kafka 的 broker 与 controller 中安装代理，以 GrantForge 策略授权每个请求，把策略显示为只读 ACL，并把重复访问合并为一条审计事件。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Kafka 代理是 Kafka `Authorizer` API 的一个授权器，在 `authorizer.class.name` 中指定：broker 与 controller 对每个请求都询问它，它依据 `kafka` 数据服务的签名策略作答。发行包以单个兼容 Java 8 的 JAR 提供，即 `agents/kafka/grantforge-agent-kafka-<version>.jar`，依赖已重定位打包在内。

仓库的 Testcontainers 集成测试在 Kafka `3.7.2`、`3.9.2`、`4.1.2` 与 `4.3.1` 中运行该 JAR，节点以 KRaft 模式同时担任 broker 与 controller。这并不代表对其他所有版本的认证。服务插件见 [Apache Kafka](/plugins/kafka/)。

## 工作方式

- **每个请求都会检查。** 对请求涉及的每个资源，Kafka 询问一个 ACL 操作；代理依据策略作答，以 Kafka 的资源类型为资源层级、以它的操作为访问类型。拒绝以 Kafka 自己的错误返回给客户端，例如 `TOPIC_AUTHORIZATION_FAILED`。
- **幂等生产者只需一个主题。** 生产者可写入某个主题时，Kafka 允许它在没有集群 `idempotent_write` 的情况下启动，并就此询问授权器。代理直接依据策略作答：任一主题上允许的 `write` 都算数，拒绝只在覆盖所有主题的策略中算数。
- **策略显示为 ACL。** `kafka-acls --list` 与 Admin API 的 `describeAcls` 显示当前生效的策略：不含通配符的值显示为字面 ACL，`orders*` 这样的值显示为前缀 ACL，`*` 显示为 Kafka 的通配符；用户为 `User:` 主体，组为 `Group:`，角色为 `Role:`，`public` 组为 `User:*`。ACL 无法表达的内容会略去：其他通配符、排除的值、带条件的条目，以及带例外的策略中受影响的条目。创建或删除 ACL 会被拒绝：*the ACLs of this cluster are GrantForge policies; change them in GrantForge*。
- **决策会被缓存。** broker 在每次生产与拉取时都会问相同的问题；代理缓存当前快照的决策，默认 100,000 条、10 秒。新快照会重新开始，因此代理拿到策略变更后立即生效。
- **重复访问会被合并。** 一个时间窗口（默认 10 秒）内相同的允许访问会作为一条访问事件上报，注明次数与最后一次的时间；控制台显示 *N 次相同访问*。用户、客户端地址、资源、访问类型、请求类型与决定策略都相同的访问视为相同。拒绝立即逐条上报。上报哪些由 Kafka 决定：例如它从列表中略去的主题不会要求上报。
- **超级用户不受检查。** `super.users` 中的主体可以做任何事，与 Kafka 自带的授权器相同，且不会被审计。

## 部署

1. 添加 `kafka` 数据服务并配置策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有 Kafka 账户可读的文件。
2. 将 JAR 复制到 **每个 broker 与 controller** 的 `libs` 目录，例如 `/opt/kafka/libs`。
3. 在每个节点的 `server.properties` 中加入以下配置，替换示例主体、地址、路径与实例名：

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` 必须包含 broker 与 controller 之间互相通信所用的主体，例如 broker 间监听器的主体，否则需要策略允许它们所做的操作，例如集群上的 `cluster_action`。连接 `PLAINTEXT` 监听器的主体是 `User:ANONYMOUS`；只有在客户端无法访问这类监听器时才可列出它。缓存目录必须对 Kafka 账户可写。
4. 逐个重启 broker 与 controller。除 controller 的监听器外，其他监听器在代理拿到第一个快照后开放，最多等待 `grantforge.start.timeout.ms`；在此之前，以及任何没有快照的时候，请求都会被拒绝。
5. **先迁移现有 ACL：** 代理替换 Kafka 自带的授权器后，后者存储的 ACL 不再生效。请事先用 `kafka-acls --list` 列出它们，并创建对应的策略。

| 配置项 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此 broker 或 controller 的名称，以及存放签名快照与暂存事件的可写目录 |
| `grantforge.signing.key.file` | 可选，固定快照签名密钥的文件；从“代理”页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 的连接与读超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理获取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列容量（默认 `10000`）、发送间隔（默认 `5000` 毫秒）和暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 缓存的决策数上限，默认 `100000`，最多 `10000000`，`0` 表示不缓存；以及缓存时长，默认 `10000`，`1..3600000` 毫秒 |
| `grantforge.start.timeout.ms` | 监听器等待第一个快照的时长，默认 `30000`，`0..600000` 毫秒 |

代理从 Kafka 交给授权器的配置中读取设置。同时担任 broker 与 controller 的节点会为每个角色创建一个授权器；两者共用一个代理。

## 用户、组与角色

策略中的名称是 Kafka 赋予连接的主体：SASL 用户名、按 `sasl.kerberos.principal.to.local.rules` 得到的 Kerberos 主体短名，或按 `ssl.principal.mapping.rules` 得到的 TLS 证书名称。Kafka 没有组与角色；策略中的组与角色是 GrantForge 赋予用户的。

## 决策与限制

- 没有可用快照时，除超级用户外的所有请求都会被拒绝。未启用的服务不检查 Kafka，与未安装授权器时相同。
- 令牌绑定的服务类型不是 `kafka` 时，一律拒绝。
- 拒绝一个访问类型也会拒绝它所蕴含的类型：拒绝主题上的 `write` 会拒绝 `describe`，用户因此看不到该主题。这一点与 Kafka 自己的 ACL 不同。
- Kafka 询问用户能否对某类资源中的任一资源执行操作，这不算访问，不会上报。
- 决策会缓存 `grantforge.decision.cache.ttl.ms`，因此策略有效期的开始或结束最多会延迟这么久。
- 代理运行在 broker 与 controller 中，不覆盖 Kafka Connect 的 REST API、Schema Registry 以及 Kafka 周边的其他服务。

## 相关指南

- [Apache Kafka](/plugins/kafka/)
- [数据服务、策略与代理](/external/data-services/)
