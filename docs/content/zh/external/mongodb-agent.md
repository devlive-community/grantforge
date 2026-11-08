---
title: MongoDB 代理
description: 在部署旁运行 MongoDB 代理，把 GrantForge 策略变成 MongoDB 自身的角色，一步替换角色的权限，读取 GrantForge 之外的内置和自定义角色实际给出了什么，并从审计日志或 Profiler 报告访问。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MongoDB 代理是运行在部署旁的服务。它让 MongoDB 自身的访问控制与 `mongodb` 数据服务的已签名策略保持一致：每个用户一个角色，角色持有的权限恰好等于策略给该用户的权限。之后每条命令都由 MongoDB 自己检查，代理不挡在任何客户端前面。发行包以可运行 JAR `agents/mongodb/grantforge-agent-mongodb-<version>.jar` 提供，需要 Java 17 或更高版本。

仓库的 Testcontainers 集成测试让代理和发行的 JAR 运行在 MongoDB `6.0`、`7.0`、`8.0`、`9.0` 副本集和一个 `9.0` 分片集群上，并读取 Percona Server for MongoDB `7.0`、`8.0` 的审计日志。这并不认证其他所有版本；代理会拒绝低于 `6.0` 的部署。服务插件见 [MongoDB](/plugins/mongodb/)。

## 工作方式

- **每个用户一个角色，持有 GrantForge 给的权限。** 对策略给了权限的每个用户，代理在 `admin` 中维护一个以其命名的角色，如 `alice` 的 `grantforge_alice`，并授予该名称的每个 MongoDB 用户，如 `alice@admin` 和 `alice@sales`。`rolesInfo` 能看到 GrantForge 给了 `alice` 什么。代理不改动其他角色。
- **变更一步完成。** `updateRole` 一次替换角色的全部权限，用户不会同时持有新旧权限，也不会两者都没有。
- **下层没有其他决定时按整个数据库授权。** 策略在整个数据库上允许、且下层没有策略能收回的访问类型，会授权在数据库上，之后创建的集合立刻拥有。否则逐集合授权，新集合在下次同步时覆盖。
- **绝不超过策略允许的范围。** MongoDB 删除数据库的权限就是删除其中每个集合的权限。只有策略也给了后者时代理才授予；否则不授予，并记录一次哪个用户不能做什么以及原因。
- **使用当前时刻的策略。** 每次同步都按当时生效的策略以及 MongoDB 现有的用户、数据库和集合来规划，所以有效期准时开始和结束，新增对象在下次同步时覆盖。
- **MongoDB 角色就是组。** 针对组的策略适用于持有同名 MongoDB 角色的用户，以及 GrantForge 放入该组的用户。
- **手工改动会被还原。** 每次同步时代理都比较自己的角色与规划，改回被手工修改的权限、被加上的继承角色、授给他人的角色，并删除不再需要的角色。没有变化时不发送任何命令。
- **读取 GrantForge 之外的角色实际给出了什么。** MongoDB 会累加用户所有角色给出的权限，所以代理无法收回用户通过其他角色持有的权限。MongoDB 能说明每个角色给出了什么，包括内置和继承的角色，所以当 `readWrite@hr` 这样的角色给出了策略拒绝的权限时，代理会警告一次，指明用户、访问和策略。代理还会警告能管理用户（因而能给自己任何权限）的用户、持有 `root` 或对所有资源拥有所有动作的用户，以及完全不检查授权的服务器。
- **访问来自审计日志或 Profiler。** 见下文“报告访问”。每个事件都说明是 GrantForge 决定的（即策略与 MongoDB 的决定一致），还是 GrantForge 之外的角色允许了命令。聚合会按其 `$lookup`、`$graphLookup`、`$unionWith` 阶段读取的每个集合，以及 `$out`、`$merge` 阶段写入的每个集合分别报告。事件只包含命令名和命名空间，绝不包含参数，因为参数可能含有文档和密码。

## 部署

1. 添加 `mongodb` 数据服务及其策略。打开 **数据权限 → 代理**，选择服务并签发代理令牌。令牌只显示一次，请保存到只有代理账户能读取的文件。
2. 创建代理的 MongoDB 用户。它用 `userAdminAnyDatabase` 创建和修改角色、读取用户和角色；用一个只有列出集合权限的角色列出集合；用 `clusterMonitor` 读取服务器选项和 Profiler。`userAdminAnyDatabase` 能给自己任何权限，请像保护管理员一样保护此用户。列出集合的角色名不能以代理的角色前缀开头。

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. 如需报告访问，按下文“报告访问”配置一种来源。
4. 编写配置文件，替换示例中的地址、路径和名称：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   这些路径都在代理所在机器上，缓存目录必须可写。密码文件按 UTF-8 读取并去除首尾空白。
5. 启动代理，例如作为 systemd 服务：

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   将 `<version>` 替换为发行版本。`java -Dgrantforge.…=… -jar …` 给出的设置优先于文件。收到 SIGTERM 时代理干净退出，发送或落盘待发事件；MongoDB 保留已收到的内容。
6. **把现有授权迁移到策略：** GrantForge 之外授予的角色会继续与代理的角色一起生效。先创建对应的策略，再撤销这些角色。代理的警告会指出那些给出了策略拒绝权限的角色。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此代理的名称，以及存放已签名快照、落盘事件和访问读取位置的可写目录 |
| `grantforge.signing.key.file` | 可选，固定快照签名密钥的文件。从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.mongodb.uri` | 必填：连接字符串，不含凭据 |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256`（默认）、`SCRAM-SHA-1`、`MONGODB-X509`、`PLAIN` 或 `GSSAPI` |
| `grantforge.mongodb.user`、`grantforge.mongodb.password.file`、`grantforge.mongodb.auth.source` | 代理的用户，`GSSAPI` 时为主体，`MONGODB-X509` 时可选；其密码；SCRAM 时定义该用户的数据库，默认 `admin` |
| `grantforge.mongodb.tls.ca.file`、`grantforge.mongodb.tls.certificate.key.file`、`grantforge.mongodb.tls.certificate.key.password.file` | 连接字符串含 `tls=true` 时：校验服务器的 PEM CA 证书，留空使用 Java 信任的；同时包含客户端证书和私钥的 PEM 文件；加密私钥的密码 |
| `grantforge.mongodb.kerberos.keytab`、`grantforge.mongodb.kerberos.service.name` | `GSSAPI`：主体的 keytab，否则使用密码；服务器主体的服务名，默认 `mongodb`。realm 来自机器的 `krb5.conf` |
| `grantforge.mongodb.timeout.ms` | 查找服务器、连接和单条命令允许的时长，默认 `30000`，范围 `1..600000` 毫秒 |
| `grantforge.mongodb.role.prefix` | 代理角色名的开头，默认 `grantforge_`。由字母、数字和下划线组成，以下划线结尾，最多 32 个字符。`admin` 中名称以它开头的角色都属于代理 |
| `grantforge.mongodb.unmanaged.users` | 代理不处理的用户，以逗号分隔，如服务账号。代理始终不处理自己的用户，以及持有 `root`、`__system` 或对所有资源拥有所有动作的用户 |
| `grantforge.mongodb.databases` | 代理逐集合授权的数据库，以逗号分隔，留空为全部。其他数据库只获得能整体授予的权限，这样集合特别多的数据库不必在每次同步时列出 |
| `grantforge.mongodb.audit.files` | 要读取的审计日志文件，以逗号分隔。留空则读取 Profiler |
| `grantforge.sync.interval.ms` | 代理同步 MongoDB 的间隔，默认 `10000`，范围 `1000..3600000` 毫秒 |
| `grantforge.audit.log` | 默认 `true`。`false` 时不读取也不报告访问 |
| `grantforge.audit.log.delay.ms`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.batch` | 读取 Profiler 时落后当前时间多久（默认 `2000`）、读取访问的间隔（默认 `5000` 毫秒）、每次从一个文件或数据库读取的最多条目（默认 `1000`） |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 连接与读取超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理请求新策略的间隔，默认 `30000`，最少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）和磁盘落盘上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问折叠为一个事件的时长，默认 `10000`，最多 `3600000` 毫秒。`0` 表示逐个发送 |

## 报告访问

| 来源 | 适用于 | 能说明什么 | 如何开启 |
| --- | --- | --- | --- |
| 审计日志 | MongoDB Enterprise 和 Percona Server for MongoDB | 哪些被允许、哪些被拒绝，覆盖代理能读到其文件的每个成员 | 用 `--auditDestination file --auditFormat JSON --auditPath <path>` 和 `--setParameter auditAuthorizationSuccess=true` 启动 `mongod`，并在 `grantforge.mongodb.audit.files` 中列出文件 |
| Profiler | 所有版本 | 代理连接的成员（副本集的主节点）上运行过什么；没有拒绝记录 | 在每个要报告的数据库执行 `db.setProfilingLevel(2)`。它有服务器开销，所以代理从不自行开启；`system.profile` 是一个很小的固定集合 |

审计日志从文件读取，所以要把代理运行在节点上，或给它一个汇集这些文件的目录。测试读取的是 Percona Server 的审计日志，其格式与 MongoDB Enterprise 相同。`mongos` 没有 Profiler，所以分片集群只能从审计日志报告访问。

| 命令 | 访问事件 |
| --- | --- |
| `find`、`aggregate`、`count`、`distinct`、`listIndexes` | 读取的每个集合的 `read` |
| 以 `$changeStream` 开头的 `aggregate` | `change_stream` |
| `insert`、`update`、`delete`、`findAndModify`、`bulkWrite` | 写入的每个集合的 `insert`、`update` 或 `remove`；要求跳过校验时还有 `bypass_validation` |
| `createIndexes`、`dropIndexes` | `create_index`、`drop_index` |
| `collMod`、`validate`、`convertToCapped` | `alter` |
| `drop`、`create`、`dropDatabase` | `drop`、`create`、`drop_database` |
| `listDatabases`；`serverStatus`、`top`、`replSetGetStatus`、`connPoolStats`、`hostInfo`、`currentOp` | `list_databases`；`monitor` |

MongoDB 整体拒绝一条命令，不说明是其中哪个集合造成的，所以拒绝事件按策略拒绝的那些集合报告；策略都不拒绝时，按第一个集合报告。

## 用户、组与角色

策略按用户名指定 MongoDB 用户。在多个数据库中定义的同名用户算一个用户，代理的角色会授予所有这些用户。组是用户持有的 MongoDB 角色以及 GrantForge 放入的组。角色是 GrantForge 给用户的角色。

## 决策与限制

- 没有快照时代理不做任何改动。绑定到未使用的服务或类型不是 `mongodb` 的服务的令牌不授予任何权限：代理删除自己的角色，MongoDB 自身的角色保留。
- 代理未运行时 MongoDB 保留最后收到的内容，策略变更等待代理。变更在刷新和同步间隔内到达 MongoDB；`mongos` 在其用户缓存过期后应用，默认 30 秒内（`userCacheInvalidationIntervalSecs`）。
- MongoDB 的权限精确指定数据库和集合，所以策略中的通配符按现存对象授予。数据库有了集合才算存在：在此之前，策略指定数据库上的 `create` 没有可授予的对象。
- 没有在 MongoDB 中定义的用户，例如 LDAP 授权直接映射到角色的用户，代理看不到，也不为其维护角色。
- 整个数据库上的权限覆盖其集合但不覆盖 `system.` 集合，MongoDB 自己的数据库交由 MongoDB 管理。
- 重命名集合以及 MongoDB 的其他管理动作不是访问类型：它们仍由 GrantForge 之外的角色决定。
- MongoDB 不按角色过滤行，也不按角色脱敏字段；[视图](/plugins/mongodb/)可以提供受限的读取。

## 相关指南

- [MongoDB](/plugins/mongodb/)
- [数据服务、策略与代理](/external/data-services/)
