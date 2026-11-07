---
title: Redis 代理
description: 在 Redis 或 Valkey 部署旁运行 Redis 代理，让每个节点上每个用户的 ACL 选择器与 GrantForge 策略保持一致并用一条命令替换，用 ACL DRYRUN 询问 Redis GrantForge 之外的权限允许什么，并从各节点的 ACL 日志报告拒绝。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Redis 代理是运行在部署旁的服务。它让每个节点上 Redis 自身的 ACL 与 `redis` 数据服务的已签名策略保持一致，副本和集群的每个节点都包括在内，因为 Redis 在每个节点上各自维护 ACL。之后每条命令都由 Redis 自己检查，代理不挡在任何客户端前面。发行包以可运行 JAR `agents/redis/grantforge-agent-redis-<version>.jar` 提供，需要 Java 17 或更高版本。

仓库的 Testcontainers 集成测试让代理和发行的 JAR 运行在 Redis `7.2`、`7.4`、`8.2`、`8.10` 和 Valkey `8.1`、`9.1` 的一主一副本以及一个 Redis `8.10` 集群上。这并不认证其他所有版本；代理会拒绝低于 `7.2` 的 Redis，因为其 ACL 日志的条目没有编号。服务插件见 [Redis](/plugins/redis/)。

## 工作方式

- **选择器归 GrantForge，根权限归运维人员。** 用户的根权限或任一选择器允许时，Redis 就允许命令。代理维护它所管理用户的全部选择器，从不改动其根权限和密码：一个选择器存放用户可读、可写或可读写的键，一个存放可订阅的频道，一个存放可发布的频道，在允许监控的节点上再加一个。
- **变更由一条命令完成。** `ACL SETUSER user clearselectors (…)` 在一个节点上一次替换用户的全部选择器，用户不会同时持有新旧权限，也不会两者都没有；被手工改动的选择器在下次同步时以同样方式改回。
- **模式只在成立处授予。** 策略中的键或频道模式，仅当策略对该用户在整个模式范围内决定一致时，才作为 Redis 自己的模式授予；代理会沿着与它重叠的每条策略把模式切分并逐块判断，所以其他用户的策略不会拖累它。策略在其中决定不同的模式会被保留不授予，并记录一次。
- **不给危险命令，不列出他人的键。** 选择器从不授予 Redis 认为危险的命令，`SCAN` 和 `RANDOMKEY` 只授予能读取所有键的用户。
- **每个节点都下发，能保存就保存。** 代理从给定节点发现副本和集群节点，并逐个同步。把 ACL 保存在文件中的节点用 `ACL SAVE` 保存；不使用文件的节点重启后会丢失选择器，并在下次同步时重新获得。
- **直接问 Redis 允许什么。** 根权限与代理的选择器并列生效。代理不去解析其规则，而是用 `ACL DRYRUN` 问 Redis：某用户能否对策略拒绝的键、频道或节点执行命令，并各警告一次，指明用户、访问和策略。代理还会警告没有选择器能约束的用户（对所有键有所有命令），以及任何人都能无密码登录的用户。
- **拒绝来自每个节点的 ACL 日志。** 见下文“报告拒绝”。
- **使用当前时刻的策略。** 每次同步都按当时生效的策略以及 Redis 现有的用户和节点来规划，所以有效期准时开始和结束。

## 部署

1. 添加 `redis` 数据服务及其策略。打开 **数据权限 → 代理**，选择服务并签发代理令牌。令牌只显示一次，请保存到只有代理账户能读取的文件。
2. 在每个节点上创建代理的 ACL 用户。它读取用户、设置其选择器、保存 ACL 文件、读取 ACL 日志、执行 dry run 并发现集群节点。`+acl|setuser` 能给自己任何权限，请像保护管理员一样保护此用户。

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. 把代理所管理用户的根权限限制在每个用户都需要的范围内，例如登录；其余由代理授予。给出更多的根权限会被发现并警告。

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. 编写配置文件，替换示例中的地址、路径和名称：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   这些路径都在代理所在机器上，缓存目录必须可写。密码文件按 UTF-8 读取并去除首尾空白。
5. 启动代理，例如作为 systemd 服务：

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   将 `<version>` 替换为发行版本。`java -Dgrantforge.…=… -jar …` 给出的设置优先于文件。收到 SIGTERM 时代理干净退出，发送或落盘待发事件；Redis 保留已收到的内容。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此代理的名称，以及存放签名快照、暂存事件与审计日志读取位置的可写目录 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.redis.nodes` | 必填：节点地址 `host:port`，不写端口时为 `6379` |
| `grantforge.redis.discover` | 默认 `true`：从给定节点发现集群其余节点和主节点的副本。`false` 只使用给定节点，用于节点公布的地址代理无法访问的情况 |
| `grantforge.redis.user`、`grantforge.redis.password.file` | 代理的 ACL 用户（留空为默认用户）及其密码 |
| `grantforge.redis.tls.mode`、`grantforge.redis.tls.ca.file`、`grantforge.redis.tls.certificate.file`、`grantforge.redis.tls.key.file`、`grantforge.redis.tls.key.password.file` | `disable`（默认）、`verify-ca` 或 `verify-full`；校验服务器的 PEM CA 证书，留空使用 Java 信任的；PEM 客户端证书、单独存放时的私钥以及私钥密码 |
| `grantforge.redis.timeout.ms` | 连接和单条命令允许的时长，默认 `10000`，范围 `1..600000` 毫秒 |
| `grantforge.redis.unmanaged.users` | 代理不处理的用户，以逗号分隔，如服务账号。代理始终不处理自己的用户，以及对所有键有所有命令的用户 |
| `grantforge.sync.interval.ms` | 代理让 Redis 保持一致的频率，默认 `10000`，范围 `1000..3600000` 毫秒 |
| `grantforge.audit.log`、`grantforge.audit.log.interval.ms` | 默认 `true`；`false` 时不读取 ACL 日志。读取 ACL 日志的间隔，默认 `5000` 毫秒 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | 连接 GrantForge 与读取的超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理请求新策略的频率，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）以及磁盘暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条发送 |

## 报告拒绝

Redis 不记录它允许了什么，所以代理从每个节点的 ACL 日志报告它拒绝了什么：谁在何时从何处被拒绝了什么，以及策略是否与 Redis 的决定一致。Redis 会把一分钟内重复的拒绝合并到前一条记录中；记录每被更新一次，代理就再报告一次。

| 日志原因 | 访问事件 |
| --- | --- |
| `key` | 对该键的 `read` 或 `write`，按 Redis 对命令的标记区分 |
| `channel` | 对该频道的 `subscribe` 或 `publish` |
| `command` | 监控命令记为对节点的 `monitor`；其他命令在日志中不带键，不报告 |
| `auth` | 不报告：登录被拒绝不涉及任何访问 |

## 用户、组与角色

策略按名称指定 Redis 的 ACL 用户。Redis 没有自己的角色或组，所以组是 GrantForge 放入的组。

## 决策与限制

- 没有快照时代理不做任何改动。绑定到未使用的服务或类型不是 `redis` 的服务的令牌不授予任何权限：代理会清除其所管理用户的选择器。
- 代理未运行时 Redis 保留最后收到的内容，策略变更等待代理。变更在刷新和同步间隔内到达 Redis。
- 代理维护其所管理用户的全部选择器：运维人员手工添加的选择器会在下次同步时被移除。额外的权限请放在根权限中，或把该用户交给运维人员管理。
- Redis 的 ACL 指定的是键和频道而不是数据库，所以一个模式在节点的每个数据库中都覆盖同名的键。
- 用户完全无权执行的命令被拒绝时，Redis 在日志中不记录其键，这类拒绝不报告。
- Redis 不会在值内部过滤，也不做脱敏。

## 相关指南

- [Redis](/plugins/redis/)
- [数据服务、策略与代理](/external/data-services/)
