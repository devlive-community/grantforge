---
title: Apache Doris 代理
description: 在集群旁运行 Apache Doris 代理，让 GrantForge 策略成为 Doris 自己的角色、列授权、工作负载组授权与行策略，授予用户的每个身份，只在 Doris 不会给出更多时才整库、整个 Catalog 授权，并从 Doris 的审计日志表上报访问。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Doris 代理是运行在集群旁的服务。它让 Doris 自己的访问控制与 `doris` 数据服务的签名策略保持一致：每个用户一个角色，恰好拥有策略给该用户的权限，细到列与工作负载组；每条行过滤一条行策略。之后由 Doris 自己检查每条语句，代理从不挡在客户端与 FE 之间。发行包以可运行的 JAR 提供它，即 `agents/doris/grantforge-agent-doris-<version>.jar`，需要 Java 17 或更高版本。

仓库的 Testcontainers 集成测试让代理及发行的 JAR 运行在 Apache Doris `2.1`、`3.1`、`4.0` 与 `4.1` 上。这并不代表认证了其他所有版本；代理拒绝早于 `2.1` 的集群。服务插件见 [Apache Doris](/plugins/doris/)。

## 工作方式

- **每个用户一个角色，拥有 GrantForge 给的一切。** 对策略给了权限的每个用户，代理维护一个以其命名的角色，例如 `alice` 的 `grantforge_alice`，其注释写明对应的用户，并把它授予该用户的每个身份，例如 `'alice'@'%'` 与 `'alice'@'10.%'`。`SHOW ROLES` 列出 GrantForge 给 `alice` 的权限；代理不改动其他角色，也不改动不是它做出的授权。
- **下层没有不同判定时整库、整个 Catalog 授权。** 策略在整个数据库或 Catalog 上允许、且下层没有策略能收回的读取、导入与查看视图，会在数据库或 Catalog 上授予，因此之后新建的表立即拥有它们。否则逐表授予，读取则逐列授予。修改逐表授予，因为 Doris 在数据库上的这项权限也能重命名数据库。
- **从不超出策略允许的范围。** Doris 在 Catalog 中建库的权限就是在其所有数据库中建表的权限，删除数据库的权限就是删除其所有表的权限。代理只在策略同时给出更宽的权限时才授予它们；否则扣下该权限，并在日志中写明一次哪个用户不能做什么以及原因。
- **工作负载组按名称授权。** 工作负载组上的策略在它允许的每个组上授予 `USAGE_PRIV`，用户因此能在该组中运行查询；之后新建的组会在下次同步时覆盖。
- **行过滤成为行策略。** 一条行过滤是用户角色在该表上的一条宽松（permissive）行策略。新的过滤先于它所收窄的权限就位；修改过滤时，先放上一条不显示任何行的限制性（restrictive）策略再替换，因此用户看到的永远不会多于新旧任何一个过滤。每个过滤会先在其表上试运行；Doris 无法在该表上运行或拒绝的过滤，例如引用了表中不存在的列，会被替换为让该用户看不到任何行的过滤，并记入日志。
- **采用当前时刻的策略。** 每次同步都按当时生效的策略规划，因此有效期按时开始与结束；并按 Doris 当下的用户、Catalog、数据库、表与列规划，新出现的会在下次同步时覆盖。
- **Doris 角色就是组。** 针对某个组的策略适用于持有同名 Doris 角色的用户，与 GrantForge 中用户所在的组一起使用。
- **撤销手工改动。** 每次同步时，代理把它的角色在 `SHOW ROLES` 中显示的权限以及它的行策略与计划比较，恢复不一致之处。Doris 只按用户显示列权限，且合并了用户的全部授权：用户无法读取计划中的全部列时，代理授予这些列；用户能读到的其他列，代理从自己的角色上撤销一次，之后仍能读取的列来自 GrantForge 之外的授权。没有任何变化时，代理什么也不发送。
- **报告 GrantForge 之外的授权。** Doris 会把权限累加，并用 OR 合并宽松行策略，因此代理无法收回用户直接或经由其他角色持有的权限。对每个给出了策略所拒绝内容的此类授权、列授权或行策略，代理会警告一次，写明用户、授权及其来源；持有 `ADMIN_PRIV` 或 `NODE_PRIV`、不受任何角色约束的用户也会被报告。
- **访问来自审计日志。** 代理读取每个 FE 都会写入的 `__internal_schema.audit_log`，把每条语句按它涉及的每张表、每个数据库或 Catalog 上报为一条事件；由于日志不写明表，代理用 Druid 的 Doris 解析器从语句本身读出它涉及的表。被拒绝的语句按 Doris 错误中写明的权限与对象上报为一条拒绝事件。每个事件都写明是否由 GrantForge 决定，即策略是否与 Doris 判定一致，还是由 GrantForge 之外的授权允许。日志的读取落后于当前时刻 FE 的批量写入间隔 `audit_plugin_max_batch_interval_sec` 再加五秒。

| 语句 | 访问事件 |
| --- | --- |
| `SELECT`、`INSERT … SELECT` 的读取部分 | 每张被读取的表上的 `select` |
| `INSERT`、`UPDATE`、`DELETE`、`TRUNCATE` | 被写入的表上的 `load` |
| `ALTER TABLE`、`DROP TABLE`、`SHOW CREATE VIEW` | 表上的 `alter`、`drop`、`show_view` |
| `CREATE TABLE`、`CREATE VIEW` | 数据库上的 `create` |
| `DROP DATABASE` | 数据库上的 `drop_database` |
| `CREATE DATABASE` | Catalog 上的 `create_database` |

## 部署

1. 添加 `doris` 数据服务并配置其策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有代理运行账号可读的文件。
2. 创建代理的 Doris 用户。它要创建角色与行策略、授予策略给出的权限，并读取每个用户的授权与审计日志，`ADMIN_PRIV` 允许这些操作：

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. 要上报访问，请在审计日志关闭时（例如 Doris 2.1）将其打开：`SET GLOBAL enable_audit_plugin = true`。把 `audit_plugin_max_batch_interval_sec` 调得比默认的 60 秒更短，访问会更快上报。
4. 编写设置文件，替换示例中的地址、路径与名称：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   这些路径位于代理所在的机器上，缓存目录必须可写。密码文件按 UTF-8 读取并去除首尾空白。
5. 启动代理，例如作为 systemd 服务：

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   把 `<version>` 替换为发行版本。以 `java -Dgrantforge.…=… -jar …` 提供的设置会覆盖文件中的设置。代理收到 SIGTERM 时干净地停止，发送或暂存待发的事件；Doris 保留已获得的授权。
6. **把现有授权迁移为策略：** 在 GrantForge 之外做出的授权会与代理的角色并存生效。创建对应的策略后再撤销这些授权；代理的警告会写明给出了策略所拒绝内容的授权。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此代理的名称，以及存放签名快照、暂存事件与审计日志读取位置的可写目录 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.doris.hosts`、`grantforge.doris.user` | 必填：FE 的 MySQL 协议地址（某个无法访问时依次尝试下一个，未写端口时为 `9030`），以及代理的 Doris 用户 |
| `grantforge.doris.password.file` | 该用户的密码；省略时代理不带密码登录 |
| `grantforge.doris.ssl.mode`、`grantforge.doris.tls.ca` | `disable`（默认）、`trust`、`verify-ca` 或 `verify-full`，用于提供 TLS 的 FE；以及校验它们的 PEM CA 证书，为空时信任 Java 信任的证书 |
| `grantforge.doris.timeout.ms` | 连接与一条语句可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |
| `grantforge.doris.role.prefix` | 代理角色名称的开头，默认 `grantforge_`；由字母、数字与下划线组成，以下划线结尾，最多 32 个字符。名称以它开头的角色与行策略都归代理所有 |
| `grantforge.doris.unmanaged.users` | 代理不管理的用户，以逗号分隔，例如服务账号；代理始终不管理它自己的用户、`root`、`admin` 以及持有 `ADMIN_PRIV` 或 `NODE_PRIV` 的用户 |
| `grantforge.doris.catalogs` | 代理按数据库、表与列授权的 Catalog，以逗号分隔；为空时为全部 Catalog。其余 Catalog 只获得能在整体上授予的权限，因此表很多的外部 Catalog 无需在每次同步时列举 |
| `grantforge.sync.interval.ms` | 代理让 Doris 保持一致的频率，默认 `10000`，范围 `1000..3600000` 毫秒 |
| `grantforge.audit.log` | 默认 `true`；`false` 不读取审计日志，也不上报访问 |
| `grantforge.audit.log.delay.ms`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.batch` | 日志读取落后于当前时刻的时长，默认为集群的批量写入间隔加五秒；读取频率，默认 `5000` 毫秒；以及一次读取最多取多少条，默认 `1000` |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | 连接 GrantForge 与读取的超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理请求新策略的频率，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）以及磁盘暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条发送 |

## 用户、组与角色

策略按用户名指定 Doris 用户；同一用户名在多个主机上的身份是同一个用户，代理的角色会授予它的每个身份。组为该用户持有的 Doris 角色，加上 GrantForge 中该用户所在的组；角色为 GrantForge 授予该用户的角色。

## 决策与限制

- 没有快照时代理不做任何改动。未启用的服务，或绑定到类型不是 `doris` 的服务的令牌，不授予任何权限：代理删除它的角色与行策略，由 Doris 自己的授权生效。
- 代理未运行期间，Doris 保留最后一次获得的授权；策略的变化要等代理运行后才生效。变化会在刷新间隔加同步间隔之内到达 Doris。
- 在代理逐表授权的数据库中新建的表，以及在代理逐库授权的 Catalog 中新建的数据库，会在下次同步时获得权限。
- Doris 的行策略不能约束 `root` 与 `admin`。对于 Doris 中没有任何行策略的用户，他能看到表中的每一行；在 GrantForge 之外为同一用户创建的宽松行策略会放宽其过滤所显示的范围。
- Doris 在查询于 BE 上运行时检查工作负载组上的 `USAGE_PRIV`；FE 自己就能应答的查询，例如常量或缓存的结果，不经此检查。每个用户都能使用 `normal` 组，因为 Doris 把它给了所有人。
- 代理为之维护角色的用户必须存在于 Doris 中；通过 LDAP 登录、在 Doris 中没有自己用户的人不会获得角色。
- Doris 只能借助 Apache Ranger 脱敏，因此策略不能对值脱敏。

## 相关指南

- [Apache Doris](/plugins/doris/)
- [数据服务、策略与代理](/external/data-services/)
