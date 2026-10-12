---
title: MySQL 代理
description: 部署 MySQL 网络代理，配置 TLS 和策略同步，验证列级权限、脱敏、行过滤及拒绝错误。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MySQL Agent 是独立的网络代理：客户端连接代理而不是服务器，代理在支持的语句到达 MySQL 之前，按 `mysql` 数据服务的签名策略进行检查。它无需安装到 MySQL 服务端内部。发行包提供可运行的 JAR：`agents/mysql/grantforge-agent-mysql-<version>.jar`，需要 Java 17 或更高版本。

仓库的 Testcontainers 集成测试以 `mysql:8.0`、`mysql:8.4` 为目标，使用 MariaDB Connector/J 和 MySQL Connector/J 客户端；不代表其他服务器版本或全部 MySQL 协议功能均已验证。服务插件的版本与连接要求见 [MySQL](/plugins/mysql/)。

## 工作方式

- **登录直通。** 服务器的握手包送到客户端，客户端的应答送到服务器，因此密码仍由 MySQL 自己校验（`caching_sha2_password` 和 `mysql_native_password`）；代理据此得知 MySQL 接受的用户。策略中的用户就是该用户名，不含主机部分。
- **检查 SQL 资源。** 代理解析 COM_QUERY 和服务器端预处理语句，按语句涉及的数据库、表和列检查相应的访问类型。被拒绝的语句不会到达 MySQL，客户端收到代理生成的、与 MySQL 兼容的错误码，例如 1142 或 1143。
- **读取会被脱敏和过滤。** 当语句读取的表对该用户有脱敏列或行过滤时，代理会用一个同名派生表替换它，派生表显示脱敏后的值且只包含过滤后的行，因此连接、条件和函数看到的都是用户可见的内容。
- **资源授权事件**上报到 GrantForge，包括用户、客户端地址、资源、访问类型、决策和语句。审计范围见下文。
- MySQL 的执行结果转发给客户端，代理不会改写数据库中存储的原始值。

## 部署

1. 先添加 `mysql` 数据服务并配置策略，再打开 **数据权限 → 代理**，选中该服务并签发代理令牌。令牌只显示一次，把它保存到只有代理运行账号可读的文件中。
2. 为代理创建元数据用户。它从 `information_schema` 读取表的列和存储函数，不需要读取数据：`REFERENCES` 可见所有列，`SHOW_ROUTINE` 可见所有存储函数。请直接授予该用户，不要通过角色授予；缺少 `SHOW_ROUTINE` 时代理不会启动。

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. 编写配置文件，替换示例中的地址、路径和名称：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   上例同时启用客户端到代理、代理到 MySQL 的 TLS。`mysql-proxy.crt` 必须匹配客户端使用的代理主机名；MySQL 的证书必须匹配 `mysql.internal`。这些路径在代理所在机器上解析，缓存目录需要可写。密码文件按 UTF-8 读取后会去掉首尾空白，不能保存需要首尾空白的密码。

4. 启动代理，例如作为 systemd 服务运行：

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   将 `<version>` 换成发行包版本。以 `java -Dgrantforge.…=… -jar …` 给出的配置会覆盖文件中的值。代理收到 SIGTERM 时正常停止，并发送或暂存仍未发送的事件。
5. 让客户端连接代理的地址和端口。此后 MySQL 看到的是代理的地址，账号的主机部分必须允许该地址。
6. **封住绕过代理的路径：** 能直接连到 MySQL 的客户端不受检查。只允许代理和管理员访问服务器端口。

| 配置项 | 作用 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、agent token 文件、本代理的名称，以及存放签名快照和暂存事件的可写目录 |
| `grantforge.signing.key.file` | 可选，固定快照签名公钥的文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥，省略时首次从服务端获取 |
| `grantforge.proxy.host`、`grantforge.proxy.port` | 代理的监听地址；默认所有地址、端口 `3307`。端口范围 `0..65535`，`0` 表示自动分配 |
| `grantforge.proxy.tls.cert`、`grantforge.proxy.tls.key` | PEM 证书和私钥，必须同时配置，用于向客户端提供 TLS |
| `grantforge.proxy.tls.required` | 默认 `false`；为 `true` 时拒绝不使用 TLS 的客户端（错误 3159），需要配置证书和私钥 |
| `grantforge.mysql.host`、`grantforge.mysql.port` | 必填的 MySQL 服务器地址；端口默认 `3306`，范围 `1..65535` |
| `grantforge.mysql.tls` | 到 MySQL 的 TLS：`disabled`（默认）、`required`（加密但不校验）、`verify-ca` 或 `verify-identity` |
| `grantforge.mysql.tls.ca` | 校验 MySQL 的 PEM CA；为空时信任 Java 信任的证书 |
| `grantforge.mysql.metadata.user`、`grantforge.mysql.metadata.password.file` | 必填元数据用户；密码文件可选，未配置时不向驱动传入密码 |
| `grantforge.mysql.metadata.cache.seconds` | 列和存储函数的缓存时间，默认 `30`，范围 `0..86400` 秒；`0` 表示不复用缓存 |
| `grantforge.mysql.connect.timeout.ms` | 连接 MySQL 的超时，默认 `10000`，范围 `1..600000` 毫秒；元数据读取超时固定为 `30000` 毫秒 |
| `grantforge.proxy.check.threads` | 检查语句的线程数，默认 `8`，范围 `1..256` |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | 到 GrantForge 的连接与读取超时，默认 `5000` / `8000` 毫秒，范围 `1..2147483647` |
| `grantforge.refresh.interval.ms` | 初始或服务端不可达时的刷新间隔，默认 `30000` 毫秒，最小 `1000`；正常心跳按服务端返回的间隔执行 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity` | 默认每批 `500` 条、队列 `10000` 条；批大小范围 `1..1000`，队列不小于批大小，最大 `1000000` 条 |
| `grantforge.audit.flush.interval.ms` | 审计发送周期，默认 `5000` 毫秒，范围 `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | 审计暂存大小上限，默认 `67108864`（64 MiB），最小 `0` |

两侧的 TLS 相互独立。当只有一侧使用 TLS、而 MySQL 需要完整密码（`caching_sha2_password` 缓存未命中）时，代理会在两侧之间传递密码：对于明文客户端请求服务器公钥，代理用自己的 RSA 公钥应答；对于 TLS 客户端连到明文服务器，代理用 MySQL 的公钥加密密码。经由代理连接时，客户端不要配置 MySQL 的公钥文件。

## 语句所需的访问

| 语句 | 访问 |
| --- | --- |
| `SELECT`、子查询、`WITH`、`EXPLAIN`、`SET @x = (SELECT …)` | 所用每一列的 `select`；不读取任何列的表读取（如 `COUNT(*)`、`EXISTS`）需要整张表的 `select` |
| `INSERT`、`LOAD DATA` | 写入列的 `insert`（未列出列时为所有列）；`ON DUPLICATE KEY UPDATE` 另需 `update` |
| `REPLACE` | 列的 `insert` 和表的 `delete` |
| `UPDATE` | 被修改列的 `update` 和被读取列的 `select` |
| `DELETE` | 表的 `delete` 和被读取列的 `select` |
| `CREATE TABLE`、`CREATE VIEW` / `DROP TABLE`、`DROP VIEW`、`TRUNCATE` / `ALTER TABLE`、`ALTER VIEW`、`OPTIMIZE` | 表或视图的 `create` / `drop` / `alter` |
| `RENAME TABLE` | 旧名称的 `alter` 和 `drop`，新名称的 `create` 和 `insert` |
| `CREATE INDEX`、`DROP INDEX` | 表的 `index` |
| `CREATE DATABASE`、`DROP DATABASE` | 数据库的 `create`、`drop` |
| `LOCK TABLES`、`ANALYZE`、`CHECK`、`CHECKSUM TABLE` | 表的 `select` |

不涉及数据的语句交由 MySQL 自己的授权决定：事务、`SET`、`USE`、`SHOW`、`DESCRIBE`、`KILL`，以及 `GRANT`、`CREATE USER` 等账号语句。`information_schema` 和 `performance_schema` 与其他数据库一样需要策略。

代理会拒绝它无法完整检查的内容：可执行注释（`/*! … */`）、存储函数和 `CALL`、`CREATE PROCEDURE`、`FUNCTION`、`TRIGGER`、`EVENT`、SQL 级 `PREPARE`/`EXECUTE`、`HANDLER`、复制和克隆命令、在同一批语句中与其他语句一起出现的 `USE` 或 `SET NAMES`，以及无法解析的语句。

## 脱敏、行过滤及其限制

- 只要语句读取该表，脱敏和过滤就会生效，包括子查询、连接、`INSERT … SELECT` 和 `CREATE TABLE … SELECT`（复制出的表中是脱敏后的值）。
- 单表 `UPDATE` 或 `DELETE` 只修改过滤条件可见的行。对有行过滤的表执行多表 `UPDATE` 或 `DELETE` 会被拒绝；写入时使用被修改表中的脱敏列也会被拒绝，因为这可能复制或探测被隐藏的值。
- 不能对有脱敏或行过滤的表创建或修改视图：MySQL 之后执行视图时不经过代理。
- 预处理语句在策略版本不变时沿用已有检查和改写结果；版本变化后重新检查。仍被允许且改写文本不变时继续执行，否则返回错误 1615，客户端需重新预处理。
- 未写表名的列会计入其可见范围内所有含有同名列的表；请为列加上表名限定，以免同名的受限列导致拒绝。
- 通过 `*` 读取的列来自元数据用户看到的 `information_schema`，缓存 `grantforge.mysql.metadata.cache.seconds` 秒。以普通 SQL 命令经代理成功执行 DDL 后会使缓存失效，下次查询再读取；外部或预处理 DDL 不保证在执行时使缓存失效，需等待缓存过期或重启代理。

## 检查边界

代理检查客户端 SQL 中能解析到的资源，不会递归展开既有视图、触发器以及其他服务端内部执行的底层操作。需要保护底层表时，应同时限制 MySQL 原生授权与既有视图、触发器的使用；不要给普通业务用户管理员权限并依赖代理拦截管理语句。

行过滤不是通用的写入约束：`INSERT`、`REPLACE` 和 `LOAD DATA` 会检查写入权限，但不会自动要求新行满足过滤表达式；`INSERT … ON DUPLICATE KEY UPDATE` 的已有行更新分支也不追加行过滤条件。协议压缩、ZSTD、查询属性和可选结果元数据在握手时禁用。

## 策略与失败

- 没有可用快照，或 token 绑定的服务类型不是 `mysql` 时，所有涉及数据的语句都会被拒绝。
- GrantForge 暂时失联或新快照签名校验失败时，继续使用已经应用的快照；首次启动没有可用快照时拒绝资源访问。
- 服务未启用时跳过 GrantForge 的资源授权、脱敏和行过滤，仍执行 SQL 解析和协议限制。停用服务不等于禁止访问数据。
- 代理检查之后 MySQL 的授权依然生效；代理只能进一步收紧权限。
- 会跟随 `COM_CHANGE_USER`，但客户端与服务器两侧 TLS 不一致时会拒绝；请改为以另一用户重新连接。

## 验收示例

在独立测试库中，由数据库管理员准备以下数据和账号，将 `proxy-host` 换成 MySQL 实际看到的代理来源地址：

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

在 GrantForge 为 `alice` 创建三条策略：允许 `database=grantforge_demo`、`table=orders`、`column=*` 的 `select`；对 `card` 使用 `mask_show_last_4`；对表设置行过滤 `region = 'EU'`。保持数据服务和三条策略启用，等待 **数据权限 → 代理** 显示已应用最新策略版本。

使用 MySQL 命令行客户端连接代理，`-p` 会提示输入密码；CA 文件应信任代理证书：

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- 仅一行：1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000：未在 GrantForge 授予 update
```

MySQL 原生已允许此示例中的 `UPDATE`，因此拒绝可验证代理的策略确实生效。接着修改行过滤为 `region = 'US'`，等待代理应用新版本后重新查询，应只返回第 2 行且卡号为 `xxxxxxxxxxxx4444`。

## 错误排查与审计

| 错误码 / SQLSTATE | 含义与检查方向 |
| --- | --- |
| `1044 / 42000`、`1142 / 42000`、`1143 / 42000` | 数据库、表、列访问被拒绝；带 `by GrantForge` 的错误检查策略用户、资源、访问类型及快照版本，其他拒绝同时核对 MySQL 原生授权 |
| `1046 / 3D000` | 尚未选择数据库；使用 `USE`、连接默认库或完整表名 |
| `1227 / 42000` | 操作被禁止或无法安全检查；结合消息核对语句限制和元数据账号权限 |
| `1064 / 42000` | SQL 无法解析 |
| `1615 / HY000` | 策略变化后需要重新预处理语句 |
| `3159 / HY000` | 代理要求 TLS，客户端却使用明文连接 |
| `1043 / 08S01` | 后端协议或 TLS 握手失败；检查 TLS 模式、证书、主机名与服务器能力 |
| `2003 / HY000` | 代理无法连接 MySQL；检查地址、网络及后端状态 |

在 **数据权限 → 访问审计** 查看 `alice` 对应资源的允许和拒绝事件。一条 SQL 可能产生多个资源授权事件，记录发生在后端执行之前，因此 `ALLOWED` 不等于 MySQL 执行成功。解析失败、无资源访问、服务停用可能不产生资源事件；同版本预处理语句重复执行不会重复记录授权事件。这是资源授权审计，不是完整的 SQL 执行日志。

## 相关指南

- [MySQL](/plugins/mysql/)
- [数据服务、策略与代理](/external/data-services/)
