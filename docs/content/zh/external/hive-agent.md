---
title: Apache Hive 代理
description: 在 HiveServer2 中安装代理，用 GrantForge 策略检查语句、过滤 SHOW 结果、脱敏列并过滤行。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Hive 代理是 Hive 授权 API 的一个授权器，与 Apache Ranger 的 Hive 插件相同：HiveServer2 在执行每条语句前询问它，它依据 `hive` 数据服务的签名策略作答。发行包以单个兼容 Java 8 的 JAR 提供，即 `agents/hive/grantforge-agent-hive-<version>.jar`，依赖已重定位打包在内。

仓库的 Testcontainers 集成测试在 HiveServer2 `3.1.3`、`4.0.1` 与 `4.1.0` 中运行该 JAR。这并不代表对其他所有版本的认证。服务插件见 [Apache Hive](/plugins/hive/)。

## 工作方式

- **检查语句。** 对语句读写的每个对象，代理按操作所需的访问类型询问策略，方式与 Ranger 相同；第一次拒绝即令语句失败，错误为 `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`。查询逐列检查。
- **过滤列表。** `SHOW DATABASES` 与 `SHOW TABLES` 只列出用户可以使用的内容：对该数据库或表、或其中任一对象有任意访问权限。`USE` 与 `DESCRIBE DATABASE` 的要求相同。
- **脱敏与行过滤。** 用户在某表上有脱敏列或行过滤时，HiveServer2 会用脱敏表达式和条件改写查询，因此连接、条件与函数看到的都只是用户可见的内容。
- **无法绕过脱敏。** 对用户有脱敏或行过滤的表执行写入、修改或导出会被拒绝，与 Ranger 的默认行为一致。
- **授权与角色由 GrantForge 管理。** Hive 的 `GRANT`、`REVOKE`、`SHOW GRANT`、`CREATE ROLE`、`SET ROLE` 等角色语句一律拒绝；`SHOW CURRENT ROLES` 列出 GrantForge 赋予用户的角色。
- **拒绝不安全的语句。** `dfs` 命令会以 HiveServer2 自身的权限访问文件系统，因此被拒绝。在 HiveServer2 会话中禁止 `TRANSFORM` 脚本，用户只能 `SET` Hive SQL 标准授权所允许的配置。
- **每次决策都会上报** 给 GrantForge，包括用户、客户端地址、资源、访问类型、结果、操作和语句。

| 操作 | 访问类型 |
| --- | --- |
| 查询、`SHOW CREATE TABLE`、`SHOW PARTITIONS`、`SHOW TBLPROPERTIES`、`DESCRIBE`、`SHOW COLUMNS`、`ANALYZE` | `select` |
| `INSERT`、`UPDATE`、`DELETE`、`MERGE`、`TRUNCATE`、`LOAD`（目标） | `update` |
| `CREATE DATABASE`、`CREATE TABLE`、`CREATE VIEW`、`CREATE FUNCTION` | 对创建的对象 `create`，对读取的对象 `select` |
| `ALTER …`、`MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`、`UNLOCK` | `lock` |
| `SHOW DATABASES`、`SHOW TABLES`、`USE`、`DESCRIBE DATABASE` | 对该对象或其中任一对象的任意访问 |
| `REPL DUMP`、`REPL LOAD`、`REPL STATUS` | `repladmin` |
| `KILL QUERY`、资源计划、LLAP 管理 | `hiveservice` 上的 `serviceadmin` |
| 临时函数、`ADD JAR`、`COMPILE` | `global` 上的 `tempudfadmin` |
| 语句读写的位置 | `url` 上的 `read` 或 `write` |

## 部署

1. 添加 `hive` 数据服务并配置策略，其中应包括一条策略，允许 HiveServer2 执行自身工作所用的用户（如 `hive`）拥有所需的全部权限。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有 HiveServer2 账户可读的文件。
2. 将 JAR 复制到 **每个 HiveServer2** 的 `lib` 目录，例如 `/opt/hive/lib`。
3. 在每个 HiveServer2 的 `hiveserver2-site.xml` 或 `hive-site.xml` 中加入以下配置，替换示例地址、路径与实例名：

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   缓存目录必须对 HiveServer2 账户可写。与 Ranger 相同，`hive.server2.enable.doAs=false` 让 HiveServer2 以自身账户读写数据，由策略决定谁能看到什么。
4. 重启 HiveServer2。代理随第一个会话启动，随服务器停止，并发送或暂存尚未发送的事件。
5. **关闭绕过 HiveServer2 的途径：** 代理只检查在 HiveServer2 中执行的语句。直接访问元数据库或仓库文件的客户端（如 Hive CLI、Spark 或 HDFS 客户端）不受检查；请用元数据库与 HDFS 自身的权限或 [HDFS 代理](/external/hdfs-agent/)保护它们。

| 配置项 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此 HiveServer2 的名称，以及存放签名快照与暂存事件的可写目录 |
| `grantforge.signing.key.file` | 可选，固定快照签名密钥的文件；从“代理”页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 的连接与读超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理获取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列容量（默认 `10000`）、发送间隔（默认 `5000` 毫秒）和暂存上限（默认 64 MiB） |

代理从 HiveServer2 的配置文件和系统属性（`-Dgrantforge.…`，优先）读取配置，从不读取会话配置，因为客户端可以设置会话配置。

## 用户、组与角色

策略中的用户是 HiveServer2 为会话确定的用户：客户端登录所用的用户名，或其 Kerberos 主体的短名（`alice@EXAMPLE.COM` 对应 `alice`）。组为 Hadoop 组映射在 HiveServer2 中为用户给出的组，加上 GrantForge 中用户所属的组；角色为 GrantForge 赋予用户的角色。

## 决策与限制

- 没有可用快照或无法读取策略时，所有语句都会被拒绝。未启用的服务不检查 HiveServer2，与未安装授权器时相同。
- 令牌绑定的服务类型不是 `hive` 时，一律拒绝。
- 语句读写的位置由 `url` 策略决定；与 Ranger 不同，代理不会回退到文件系统自身的权限。
- Hive 未给出对象的语句在所有数据库上检查：没有对象的 `REPL` 语句需要 `repladmin`，没有对象的修改表或数据库所有者需要 `alter`，均作用于数据库 `*`。
- `EXPLAIN`、`DESCRIBE FUNCTION`、`SHOW FUNCTIONS`、`SET` 以及 Ranger 不检查的其他语句同样不检查；JDBC 客户端的元数据调用（如 `getTables`）不做过滤。
- 代理运行在 HiveServer2 中，不覆盖元数据库内部的授权。

## 相关指南

- [Apache Hive](/plugins/hive/)
- [数据服务、策略与代理](/external/data-services/)
