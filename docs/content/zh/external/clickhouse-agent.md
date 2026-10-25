---
title: ClickHouse 代理
description: 在服务器旁运行 ClickHouse 代理，让 GrantForge 策略成为 ClickHouse 自己的角色、授权与行策略，细到列与行，并从查询日志上报访问。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

ClickHouse 代理是运行在服务器旁的一个服务。它让 ClickHouse 自己的访问控制与 `clickhouse` 数据服务的签名策略保持一致：每个用户一个角色，恰好拥有策略给该用户的权限，每条行过滤对应一条行策略。之后由 ClickHouse 自己在其每种协议上检查每个查询，代理从不挡在客户端与服务器之间。发行包以一个可运行的 JAR 提供它：`agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`，需要 Java 17 或更高版本。

仓库的 Testcontainers 集成测试在 ClickHouse `24.8`、`25.3`、`25.8`、`26.3` 与 `26.8` 上运行代理以及发行的 JAR。这并不代表认证了其他所有版本；代理拒绝早于 `24.8` 的服务器。服务插件见 [ClickHouse](/plugins/clickhouse/)。

## 工作方式

- **每个用户一个角色，拥有 GrantForge 给的一切。** 对策略给了权限的每个用户，代理维护一个以其命名的角色，例如 `alice` 的 `grantforge_alice`，并把它作为默认角色授予该用户。`SHOW GRANTS FOR grantforge_alice` 列出 GrantForge 给 `alice` 的全部权限；代理不改动其他角色，也不改动不是它做出的授权。
- **下层没有不同判定时整库授权。** 策略在整个数据库上允许、且没有策略能从其中某张表或某列收回的访问类型，会在数据库上授予，因此之后新建的表立即拥有它。否则逐表授予，读写则逐列授予。
- **行过滤成为行策略。** 一条行过滤是用户角色在该表上的一条宽松（permissive）行策略。新的或修改后的过滤会先于它所收窄的权限就位。ClickHouse 拒绝的过滤，或其文本会让行策略作用到别人的过滤，会被替换为让该用户看不到任何行的过滤，并记入日志。
- **采用当前时刻的策略。** 每次同步都按当时生效的策略规划，因此有效期按时开始与结束；并按 ClickHouse 当下的用户、数据库、表与列规划，新出现的会在下次同步时覆盖。
- **ClickHouse 角色就是组。** 针对某个组的策略适用于直接或经由其他角色持有同名 ClickHouse 角色的用户，与 GrantForge 中用户所在的组一起使用。
- **撤销手工改动。** 代理记得它给每个角色与行策略的内容；与之不同的会在下次同步时恢复。没有任何变化时，代理什么也不发送。
- **报告 GrantForge 之外的授权。** ClickHouse 会把权限累加，因此代理无法收回用户直接或经由其他角色持有的权限。对每个给出了策略所拒绝内容的此类授权，代理会警告一次，写明用户、授权、来自哪个角色以及哪条策略。
- **访问来自查询日志。** 代理读取 `system.query_log`，把每条语句按它涉及的每张表、每个数据库或服务器上报为一条事件，被拒绝的语句按 ClickHouse 错误中写明的内容上报为一条拒绝事件。每个事件都写明是否由 GrantForge 决定，即策略是否与 ClickHouse 判定一致，还是由 GrantForge 之外的授权允许。

| 语句 | 访问事件 |
| --- | --- |
| `SELECT`、`INSERT … SELECT` 的读取部分 | 每张被读取的表上的 `select` |
| `INSERT` | 被写入的表上的 `insert` |
| `ALTER`、`ALTER UPDATE`、`ALTER DELETE`、`DELETE`、`UPDATE` | 表上的 `alter` |
| `DROP TABLE`、`TRUNCATE`、`OPTIMIZE` | 表上的 `drop`、`truncate`、`optimize` |
| `CREATE TABLE`、`CREATE VIEW`、`CREATE DICTIONARY` | 数据库上的 `create` |
| `DROP DATABASE` | 数据库上的 `drop_database` |
| `CREATE DATABASE`、`KILL QUERY`、`SYSTEM …` | 服务器上的 `create_database`、`kill_query`、`system` |

## 部署

1. 添加 `clickhouse` 数据服务并配置其策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有代理运行账号可读的文件。
2. 创建代理的 ClickHouse 用户。它要授予策略给出的权限，因此需要带授权选项的这些权限，以及维护角色与行策略的访问管理权限：

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   代理为之维护角色的用户必须存放在可以修改的地方，即 `local_directory` 或 `replicated` 用户目录中，`CREATE USER` 就存放在那里。`users.xml` 与 LDAP 中的用户不受影响，并只记录一次日志。集群应把用户、角色与行策略放在 `replicated` 用户目录中：这样一个代理即可服务整个集群。
3. 编写设置文件，替换示例中的地址、路径与名称：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   这些路径位于代理所在的机器上，缓存目录必须可写。密码文件以 UTF-8 读取并去除首尾空白。
4. 启动代理，例如作为 systemd 服务：

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   把 `<version>` 替换为发行版本。以 `java -Dgrantforge.…=… -jar …` 提供的设置会覆盖文件中的设置。代理收到 SIGTERM 时会干净地停止，并发送或暂存待发事件；ClickHouse 保留它已获得的授权。
5. **把已有授权迁移为策略：** 在 GrantForge 之外做出的授权会与代理的角色一同继续生效。请创建对应的策略，然后撤销这些授权；代理的警告会写出其中给出了策略所拒绝内容的那些。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此代理的名称，以及存放签名快照、暂存事件与查询日志读取位置的可写目录 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.clickhouse.urls`、`grantforge.clickhouse.user` | 必填：服务器的 HTTP 接口（某台无法访问时依次尝试下一台），以及代理的 ClickHouse 用户 |
| `grantforge.clickhouse.password.file` | 该用户的密码；省略时代理不带密码登录 |
| `grantforge.clickhouse.tls.ca` | 校验 `https://` 服务器的 PEM CA 证书；为空时信任 Java 所信任的 |
| `grantforge.clickhouse.tls.cert`、`grantforge.clickhouse.tls.key` | PEM 客户端证书与私钥，需同时设置，用于以证书（`IDENTIFIED WITH ssl_certificate`）代替密码登录 |
| `grantforge.clickhouse.timeout.ms` | 发往 ClickHouse 的请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |
| `grantforge.clickhouse.role.prefix` | 代理的角色名的开头，默认 `grantforge_`；由字母、数字与下划线组成，以下划线结尾。名称以它开头的角色与行策略都归代理所有 |
| `grantforge.clickhouse.unmanaged.users` | 代理不处理的用户，以逗号分隔，例如管理员；代理始终不处理它自己的用户 |
| `grantforge.sync.interval.ms` | 代理让 ClickHouse 保持一致的间隔，默认 `10000`，范围 `1000..3600000` 毫秒 |
| `grantforge.audit.query.log` | 默认 `true`；`false` 表示不读取查询日志，也不上报访问 |
| `grantforge.audit.query.log.cluster` | 通过 `clusterAllReplicas` 读取其各副本查询日志的集群；为空时读取代理所访问的服务器 |
| `grantforge.audit.query.log.delay.ms`、`grantforge.audit.query.log.interval.ms`、`grantforge.audit.query.log.batch` | 读取日志时落后于当前的时长，默认 `10000` 毫秒，因为 ClickHouse 每 7.5 秒写一次日志；读取间隔，默认 `5000` 毫秒；以及每次读取的最多条数，默认 `1000` |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 连接与读取超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）与磁盘暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |

## 用户、组与角色

策略中的用户是 ClickHouse 用户。组为该用户直接或经由其他角色持有的 ClickHouse 角色，加上 GrantForge 中该用户所在的组；角色为 GrantForge 授予该用户的角色。

## 决策与限制

- 没有快照时代理不做任何改动。未启用的服务，或绑定到类型不是 `clickhouse` 的服务的令牌，不授予任何权限：代理删除它的角色与行策略，由 ClickHouse 自己的授权生效。
- 代理未运行期间，ClickHouse 保留最后一次获得的授权；策略的变化要等代理运行后才生效。变化会在刷新间隔加同步间隔之内到达 ClickHouse。
- 在代理逐表授权的数据库中新建的表，会在下次同步时获得权限。
- 对于 ClickHouse 中没有任何行策略的用户，他能看到表中的每一行，这是 ClickHouse 的 `users_without_row_policies_can_read_rows` 设置的默认行为；关闭该设置后，没有过滤的用户看不到被过滤表中的任何行。在 GrantForge 之外为同一用户创建的宽松行策略会放宽其过滤所显示的范围。
- `system` 授予所有 `SYSTEM` 语句，包括 `SYSTEM SHUTDOWN`。
- 查询日志只上报 ClickHouse 记录的内容：关闭了 `log_queries` 的用户的语句不会上报。对于被拒绝的语句，若用户缺少某一列的权限，ClickHouse 可能只写明表；此时事件写明的就是表。
- ClickHouse 只在 ClickHouse Cloud 中提供列脱敏，因此策略不能对值脱敏。

## 相关指南

- [ClickHouse](/plugins/clickhouse/)
- [数据服务、策略与代理](/external/data-services/)
