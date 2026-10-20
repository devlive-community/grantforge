---
title: Trino 代理
description: 在 Trino coordinator 中安装系统访问控制，用 GrantForge 策略检查查询，只列出用户可使用的内容，过滤行并对列脱敏，并让模拟用户与他人的查询受策略约束。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Trino 代理是名为 `grantforge` 的系统访问控制：coordinator 会就查询涉及的每个目录、模式、表、列、例程、会话属性与用户询问它，它依据 `trino` 数据服务的签名策略作答。Trino 470 至 478 运行在 Java 23 或 24 上，Trino 479 及以后运行在 Java 25 上，因此发行包提供两个 JAR，各自把依赖遮蔽打包在内：

| Trino | JAR |
| --- | --- |
| 470 至 478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 及以后 | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

仓库的 Testcontainers 集成测试在 Trino `470` 与 `476` 中运行第一个 JAR，在 `479` 与 `484` 中运行第二个。这并不代表认证了其他所有版本。服务插件见 [Trino](/plugins/trino/)。

## 工作方式

- **每次检查都在它涉及的每个范围上判定。** 目录、模式或表上的权限覆盖其中的内容，因此代理会在列所在的目录、模式、表以及列本身上判定：任一范围上的拒绝都会拒绝，任一范围上的允许都会授予。
- **列表会省略无权使用的内容。** `SHOW CATALOGS`、`SHOW SCHEMAS`、`SHOW TABLES`、`SHOW FUNCTIONS`、`information_schema` 以及驱动的元数据只显示用户可使用（或可使用其中某些内容）的对象。
- **描述数据的内容始终开放。** 每个目录的 `information_schema`、`system` 目录中供 JDBC 驱动和 BI 工具读取的 `jdbc` 与 `metadata` 模式，以及 Trino 的内置函数不需要策略；它们的行会像列表一样被过滤。
- **过滤行并对列脱敏。** 行过滤策略作用于读取该表的每个查询；脱敏会适配列的类型，使其不会破坏查询：文本脱敏作用于 `varchar` 与 `char` 列并转换回该列的类型，年份脱敏作用于日期与时间戳，自定义表达式会转换为该类型，文本脱敏涉及的其他列显示为该类型的 `NULL`。
- **模拟用户与他人的查询受策略约束。** 以其他用户身份运行查询需要该用户上的 `impersonate`；查看或终止其他用户的查询需要该用户上的 `view_query` 或 `kill_query`。每个人都可以运行查询，并查看和终止自己的查询。
- **拒绝会说明原因。** 客户端会收到 `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`，或者没有策略允许，或者尚未收到策略。
- **缓存决策并合并访问。** 当前快照的决策会被缓存，默认 100,000 条、10 秒，新快照会重新开始。窗口内（默认 10 秒）相同的允许访问会作为一条访问事件上报，并注明次数；拒绝会立即逐条上报。每个事件都注明所属的查询。
- **权限归 GrantForge 管理。** Trino 的 `GRANT`、`REVOKE`、`DENY` 与角色语句会被拒绝，因为策略在 GrantForge 中管理。

| 语句 | 所需访问 |
| --- | --- |
| `SELECT` | 所读每一列上的 `select`；不读取任何列的查询（例如 `count(*)`）需要表上的 `select` |
| `INSERT`、`DELETE`、`TRUNCATE` | `insert`；后两者为 `delete` |
| `UPDATE` | 所写每一列上的 `update` |
| `CREATE SCHEMA`、`CREATE TABLE`、`CREATE VIEW`、`CREATE MATERIALIZED VIEW` | 所创建对象上的 `create` |
| 删除模式、表、视图或物化视图（`DROP`） | 其上的 `drop` |
| 对表、视图或列的 `ALTER`、`COMMENT`、`SET PROPERTIES`、`SET AUTHORIZATION`、`ALTER TABLE ... EXECUTE` | 其上的 `alter` |
| 重命名模式、表、视图或物化视图 | 其上的 `alter` 与新名称上的 `create` |
| `REFRESH MATERIALIZED VIEW` | 其上的 `insert` |
| 以所有者身份运行的视图 | 其所有者需要对所读列的 `select` 与对所用函数的 `execute` |
| `SHOW SCHEMAS`、`SHOW TABLES`、`SHOW COLUMNS`、`SHOW FUNCTIONS`、`SHOW CREATE` | 对它或其中内容的任意访问 |
| `CREATE CATALOG`、`DROP CATALOG` | 目录上的 `create` 或 `drop` |
| 调用函数；`CREATE FUNCTION`、`DROP FUNCTION` | `execute`；函数上的 `create_function`、`drop_function` |
| `CALL` | 过程上的 `execute` |
| `SET SESSION` | 目录或系统会话属性上的 `set` |
| 以其他用户身份运行、查看或终止他人的查询 | 该用户上的 `impersonate`、`view_query`、`kill_query` |
| 读取或写入系统信息：Trino REST API 与 Web UI 中的节点、统计与线程转储 | `system_information` 上的 `read` 或 `write` |

从 Trino 479 起，第二个 JAR 还会检查表分支：列出分支需要能看到该表，创建、删除或快进分支需要表上的 `alter`；刷新视图以及更改物化视图的所有者或注释需要其上的 `alter`。

## 部署

1. 添加 `trino` 数据服务并配置其策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有 Trino 运行账号可读的文件。
2. 把与 coordinator 版本对应的 JAR 复制到 Trino 插件目录下单独的目录中，例如 `/usr/lib/trino/plugin/grantforge/`。只有 coordinator 检查访问；worker 不需要任何改动。
3. 在 coordinator 上创建 `etc/access-control.properties`，替换示例中的地址、路径与实例名：

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   缓存目录必须可由 Trino 运行账号写入。GrantForge 取代 Trino 基于文件或其他的访问控制，而不是与之并行运行。
4. 重启 coordinator。在代理收到第一个快照之前，除描述数据的内容外，查询都会被拒绝。
5. **先迁移已有规则：** Trino 基于文件的访问控制规则将不再生效。请事先创建对应的策略。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、该 coordinator 的名称，以及存放签名快照与暂存事件的可写目录 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 连接与读取超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）与暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 缓存决策的最大数量，默认 `100000`，最多 `10000000`，`0` 表示不缓存；以及缓存时长，默认 `10000`，范围 `1..3600000` 毫秒 |

## 用户、组与角色

策略中的用户是查询的会话用户，即 Trino 经认证与用户映射后得到的用户。组为 Trino 的组提供者给出的组，加上 GrantForge 中该用户所在的组；角色为 GrantForge 授予该用户的角色。

## 决策与限制

- 没有可用快照时，除描述数据的内容、运行查询与自己的查询外，一切都会被拒绝。未启用的服务让 Trino 不受检查，与没有访问控制时一样；但 Trino 的权限与角色语句仍会被拒绝。
- 绑定到类型不是 `trino` 的服务的令牌会拒绝一切。
- Trino 会先询问用户能否使用某个目录：在目录中没有任何权限的用户会收到 Trino 自己的 `Cannot access catalog`。
- 读取了用户无权读取的列的 `SELECT` 会被拒绝，并指出第一个这样的列；不会从结果中去掉列。
- Trino 不会写入对该用户脱敏的表：脱敏生效时，此类 `INSERT` 会失败。
- 脱敏会转换为列的类型，因此有长度限制的 `varchar` 只显示能容纳的部分，例如 `varchar(3)` 中只显示哈希的 3 个字符。
- 快照中无法读取的行过滤或脱敏会显示为没有行，或把该列显示为 `NULL`，而不是显示数据。
- 决策会缓存 `grantforge.decision.cache.ttl.ms`，因此策略的有效期最多会延迟这么久开始或结束。

## 相关指南

- [Trino](/plugins/trino/)
- [数据服务、策略与代理](/external/data-services/)
