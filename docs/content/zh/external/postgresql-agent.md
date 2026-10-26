---
title: PostgreSQL 代理
description: 在服务器旁运行 PostgreSQL 代理，让 GrantForge 策略成为 PostgreSQL 自己的角色、权限与行安全策略，细到列与行，每个数据库在一个事务中变更，并从服务器日志上报访问。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

PostgreSQL 代理是运行在服务器旁的服务。它让 PostgreSQL 自己的访问控制与 `postgresql` 数据服务的签名策略保持一致：为每个用户维护一个角色，在它所维护的每个数据库中恰好持有策略给该用户的权限，并为每个行过滤维护一条行安全策略。之后由 PostgreSQL 自己检查每条语句，代理从不位于客户端与服务器之间。发行包以可运行的 JAR 提供它，即 `agents/postgresql/grantforge-agent-postgresql-<version>.jar`，需要 Java 17 或更高版本。

仓库的 Testcontainers 集成测试让代理以及发行的 JAR 运行在带 pgaudit 的 PostgreSQL `15`、`16`、`17` 与 `18` 上。这并不代表认证了其他所有版本；代理会拒绝早于 `15` 的服务器。服务插件见 [PostgreSQL](/plugins/postgresql/)。

## 工作方式

- **每个用户一个角色，持有 GrantForge 给出的权限。** 对于策略给出了权限的每个用户，代理维护一个以其命名的角色，例如 `alice` 对应 `grantforge_alice`，并让该用户成为无需 `SET ROLE` 即拥有该角色权限的成员。代理会在它的每个角色上写注释，没有该注释的角色绝不会被当作它自己的，因此它不会改动其他角色，也不会改动它没有授予的权限。
- **每个数据库在一个事务中变更。** 代理登录它维护的每个数据库，并在其中以一个事务完成变更，因此用户看到的是一个策略版本整体应用到某个数据库，或完全没有应用。
- **下层没有其他决定时按整张表授权。** 策略在整张表上允许、且没有任何策略可能从某一列收回的访问类型，会授予在表上，因此之后新增的列立即拥有该权限。否则读取、插入与更新按列逐一授予。数据库的 `CONNECT`、模式的 `USAGE`，以及用户可以插入的自增列所用序列的 `USAGE`，会随之一并授予。
- **行过滤只会收窄。** 行过滤是该用户角色在表上针对 `SELECT` 的限制性行安全策略。若行安全未开启，代理会开启它，并同时创建一条向所有人显示所有行的宽松策略，以其前缀命名（`grantforge`），使其他人看到的内容不变；当表上不再有过滤、也没有表自己的策略时，代理会再次关闭行安全。所有者开启的行安全仍归所有者，代理的策略只会收窄其显示的内容。PostgreSQL 拒绝的过滤、会改变策略约束对象的过滤，或包含多条语句的文本，都会被替换为一条不向该用户显示任何行的策略，并记录日志。
- **当前时刻的策略。** 每次同步都按当前时刻适用的策略，以及 PostgreSQL 当前的用户、模式、表与列来规划，因此有效期按时开始与结束，新出现的对象会在下次同步时覆盖。
- **角色即组。** 面向组的策略适用于直接或经由其他角色成为同名 PostgreSQL 角色成员的用户，再加上 GrantForge 中该用户所在的组。
- **手工改动会被撤销。** 每次同步时，代理会把它的角色的权限与 PostgreSQL 显示的进行比较，把它的行安全策略与它上次创建的进行比较；不同之处会在下次同步时恢复。没有任何变化时，代理不发送任何语句。
- **会报告 GrantForge 之外的权限。** PostgreSQL 的权限是叠加的，因此代理无法收回用户自己持有、经由其他角色或经由 PUBLIC 持有的权限，也无法收回用户对自己所拥有的表可做的事。对于其中给出了策略所拒绝内容的每项权限，代理只警告一次，写明用户、权限、用户如何持有它以及对应的策略。
- **访问来自服务器日志。** 代理读取 PostgreSQL 的 JSON 服务器日志：PostgreSQL 允许的访问来自 pgaudit 的会话日志，一条语句读取或写入的每张表一条事件；PostgreSQL 因缺少权限而拒绝的访问，按错误所写明的对象产生一条拒绝事件。每条事件都会说明是否由 GrantForge 决定，即策略的决定是否与 PostgreSQL 一致，还是由 GrantForge 之外的权限放行。

| 语句 | 访问事件 |
| --- | --- |
| `SELECT`、`COPY … TO`、其他语句中读取的部分 | 每张被读取的表上的 `select` |
| `INSERT`、`COPY … FROM` | 被写入的表上的 `insert` |
| `UPDATE`、`MERGE`、`SELECT … FOR UPDATE` | 表上的 `update` |
| `DELETE`、`TRUNCATE` | 表上的 `delete`、`truncate` |
| `CREATE TRIGGER` | 表上的 `trigger` |
| `CREATE TABLE`、`CREATE VIEW` 与其他对象 | 模式上的 `create` |
| `CREATE SCHEMA`、`CREATE TEMPORARY TABLE` | 数据库上的 `create_schema`、`temporary` |
| 被拒绝的连接、因缺少 `USAGE` 而被拒绝的模式 | 数据库上的 `connect`、模式上的 `usage` |

## 部署

1. 添加 `postgresql` 数据服务并配置其策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有代理运行账号可读的文件。
2. 以超级用户身份创建代理的 PostgreSQL 用户。它要在不属于它的表上授予权限、开启与关闭行安全、维护角色并读取服务器日志：

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   测试中代理以超级用户运行。超级用户既不受权限约束，也不受行安全约束，因此代理不为他们维护角色。
3. 要上报访问，请让 PostgreSQL 以 JSON 写服务器日志并加载 pgaudit（来自 PGDG 的软件包，例如 `postgresql-17-pgaudit`，或所用平台提供的），在 `postgresql.conf` 中设置以下内容，然后重启服务器：

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   没有 pgaudit 时，代理只上报 PostgreSQL 拒绝的访问，不上报它允许的访问；没有 JSON 日志时，代理不上报任何访问，并记录一次日志说明。
4. 编写设置文件，替换示例中的地址、路径与名称：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   这些路径位于代理所在的机器上，缓存目录必须可写。密码文件以 UTF-8 读取并去除首尾空白。
5. 启动代理，例如作为 systemd 服务：

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   把 `<version>` 替换为发行版本。以 `java -Dgrantforge.…=… -jar …` 提供的设置会覆盖文件中的设置。代理收到 SIGTERM 时会干净地停止，并发送或暂存待发事件；PostgreSQL 保留它已获得的权限。
6. **把已有权限迁移为策略：** 在 GrantForge 之外授予的权限会与代理的角色一同继续生效。请创建对应的策略，然后撤销这些权限；代理的警告会写出其中给出了策略所拒绝内容的那些。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此代理的名称，以及存放签名快照、暂存事件与服务器日志读取位置的可写目录 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.postgresql.hosts`、`grantforge.postgresql.user` | 必填：以 `host:port` 表示的服务器（依次尝试，直到某台可以访问且是主库），以及代理的 PostgreSQL 用户 |
| `grantforge.postgresql.database` | 代理首先登录的数据库，用于读取角色、数据库与服务器日志，默认 `postgres` |
| `grantforge.postgresql.password.file` | 该用户的密码；省略时代理不带密码登录 |
| `grantforge.postgresql.ssl.mode` | 按 libpq 的 `sslmode` 命名的 TLS 方式，默认 `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca 与 verify-full：校验服务器的 PEM CA 证书；为空时信任 Java 所信任的 |
| `grantforge.postgresql.tls.cert`、`grantforge.postgresql.tls.key` | PEM 客户端证书及其 PKCS #8 私钥，需同时设置，用于以证书（`cert` 认证）代替密码登录；它们需要 `require` 或某种校验方式，与校验方式一同使用时还需要 `grantforge.postgresql.tls.ca` |
| `grantforge.postgresql.timeout.ms` | 连接与语句可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |
| `grantforge.postgresql.databases` | 代理维护的数据库，以逗号分隔；为空时维护所有允许连接的数据库 |
| `grantforge.postgresql.role.prefix` | 代理的角色名的开头，默认 `grantforge_`；由字母、数字与下划线组成，以下划线结尾，最多 32 个字符，且不以 `pg_` 开头。名称以它开头且带有代理注释的角色，以及以这些角色或去掉末尾下划线的前缀命名的行安全策略，都归代理所有 |
| `grantforge.postgresql.unmanaged.users` | 代理不处理的用户，以逗号分隔，例如管理员；代理始终不处理它自己的用户与超级用户 |
| `grantforge.sync.interval.ms` | 代理让 PostgreSQL 保持一致的间隔，默认 `10000`，范围 `1000..3600000` 毫秒 |
| `grantforge.audit.server.log` | 默认 `true`；`false` 表示不读取服务器日志，也不上报访问 |
| `grantforge.audit.server.log.interval.ms`、`grantforge.audit.server.log.max.bytes` | 读取日志的间隔，默认 `5000` 毫秒；以及每次读取的最多字节数，默认 16 MiB，积压的日志会分多次读取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 连接与读取超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）与磁盘暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |

## 用户、组与角色

策略中的用户是 PostgreSQL 用户，即可以登录的角色。组为该用户直接或经由其他角色所属的角色，加上 GrantForge 中该用户所在的组；角色为 GrantForge 授予该用户的角色。

## 决策与限制

- 没有快照时代理不做任何改动。未启用的服务，或绑定到类型不是 `postgresql` 的服务的令牌，不授予任何权限：代理删除它的角色与行安全策略，由 PostgreSQL 自己的权限生效。
- 代理未运行期间，PostgreSQL 保留最后一次获得的权限；策略的变化要等代理运行后才生效。变化会在刷新间隔加同步间隔之内到达 PostgreSQL。
- 同步之后新建的表，会在下次同步时获得权限。
- 超级用户与表的所有者可以对表做任何事；超级用户、带 `BYPASSRLS` 的角色与表的所有者不受表的行安全约束，除非所有者强制启用。
- 视图以其所有者的权限读取它的表，除非它是 `security_invoker`：服务器日志会把视图读取的表上报为由 GrantForge 之外的权限放行。
- 直接读取分区时，不受其分区表的行安全过滤。
- pgaudit 在 PostgreSQL 执行语句之前就记录它，因此之后才失败的语句（例如其序列被拒绝的插入）会被上报为允许，其拒绝则上报为拒绝。pgaudit 不记录的会话中的语句不会上报。
- PostgreSQL 的拒绝信息只写表名而不写模式：事件写明的是语句所限定的表、该名称唯一的那张表，或 `public` 中的那张表。未写模式而创建的对象会以 `public` 上报。
- 代理读取它所登录的服务器（即主库）的服务器日志；不读取副本的日志。
- PostgreSQL 没有列脱敏，因此策略不能对值脱敏。

## 相关指南

- [PostgreSQL](/plugins/postgresql/)
- [数据服务、策略与代理](/external/data-services/)
