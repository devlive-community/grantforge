---
title: MySQL
description: 安装和配置 MySQL 服务插件，查询数据库、表和列，并管理访问、脱敏和行过滤策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MySQL 插件为 GrantForge 提供数据库/表/列查询，以及访问、脱敏和行过滤策略。插件 ID 和服务类型都是 `mysql`。服务插件安装在 GrantForge 服务端；业务 SQL 的实际检查由独立的 [MySQL 代理](/external/mysql-agent/) 执行。

连接检查接受 MySQL 8.0 及以上版本，仓库的 Testcontainers 集成测试覆盖 `mysql:8.0` 和 `mysql:8.4`。这不代表其他版本均已验证。MariaDB 服务端和 MySQL 5.7 不在支持范围内；`jdbc:mariadb://` 只是连接驱动使用的 URL 格式。

## 安装

发行包已在 `plugins/mysql` 中包含该插件。请在 **平台管理 → 插件** 中确认 `mysql` 已启用；更新插件目录后重新扫描。插件通过 MariaDB Connector/J（LGPL）访问 MySQL，它支持 MySQL 协议，包括 `caching_sha2_password` 和 TLS，并在插件自己的类加载器中运行。MySQL 官方的 Connector/J 是 GPL 许可，不随发行包分发。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，新增服务并选择 MySQL（`mysql`）。
2. 填写服务器的 JDBC URL，例如 `jdbc:mysql://mysql.example.com:3306`。`jdbc:mariadb://` 同样可用，驱动的故障转移写法也可以，例如 `jdbc:mysql:replication://primary:3306,replica:3306`。
3. 填写查询用户及其密码，测试连接后保存。测试会登录、确认服务器版本，并像查询时一样读取 `information_schema`。

查询用户只供 GrantForge 查找资源，和业务客户端登录 MySQL 的用户是两回事。连接测试成功也不代表业务 SQL 已经受到策略控制。

| 配置项 | 作用 |
| --- | --- |
| `jdbc.url` | 必填 JDBC URL：`jdbc:mysql://host[:port]` 或 `jdbc:mariadb://…`，可带数据库和 `?options`；凭据不能写在 URL 中 |
| `username` | 必填的查询用户 |
| `password` | 查询用户的密码；加密保存 |
| `jdbc.properties` | 驱动参数，每行一个 `key=value`，例如 `sslMode=verify-full` 和 `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | 连接超时，默认 `10000`，范围 `1..600000` |
| `read.timeout.ms` | 单次查询的读取超时，默认 `30000`，范围 `1..600000` |

会让服务器读取本地文件、在连接时执行语句、一次执行多条语句、携带凭据或加载类的参数，无论在 URL 还是 `jdbc.properties` 中、无论大小写，一律拒绝：`user`、`password`、`allowLocalInfile`、`allowMultiQueries`、`initSql`、`createDatabaseIfNotExist`、`socketFactory`、`credentialType` 以及连接池参数。查询连接是只读的。

需要校验服务器身份的 TLS 连接时，在 `jdbc.properties` 中填写：

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

证书路径必须在 **GrantForge 服务端**可读，URL 中的主机名应与证书匹配。插件不强制启用 TLS，请显式配置；这些选项使用 MariaDB Connector/J 的名称。

`information_schema` 只向用户展示其有权限的对象。例如只查询 `sales` 数据库中的资源，可由数据库管理员执行以下授权，将 `grantforge-host` 替换为 GrantForge 服务端的来源地址：

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

需要发现全部数据库时才使用 `REFERENCES ON *.*`。代理使用的元数据账号还需要 `SHOW_ROUTINE`，具体见代理指南。

## 资源与访问类型

| 级别 | 匹配 | 说明 |
| --- | --- | --- |
| `database` | 通配符，区分大小写 | 策略可以止于此级 |
| `table` | 通配符，区分大小写 | 位于数据库之下；策略可以止于此级 |
| `column` | 通配符，不区分大小写 | 位于表之下；访问类型为 `select`、`insert`、`update` |

数据库和表按区分大小写比较，与 MySQL 在 Linux 上的存储方式一致。若服务器的 `lower_case_table_names` 为 1 或 2，代理会把所检查的名称转为小写，这类策略请用小写书写。列名始终不区分大小写，与 MySQL 一致。

访问类型对应 MySQL 的权限：`select`、`insert`、`update`、`delete`、`create`、`drop`、`alter`、`index`，以及包含以上八种类型的 `all`。`all` 不等于 MySQL 的 `ALL PRIVILEGES`，也不会取消代理的语句限制。

策略值中的 `*` 匹配任意长度字符，`?` 匹配一个字符。在策略编辑器中输入时，会按名称前缀提示数据库、策略所指数据库中的表，以及其表中的列；输入提示中的 `%` 和 `_` 是普通字符。

## 脱敏

脱敏策略作用于列。每种脱敏类型都是一个 MySQL 8 表达式，其中 `{col}` 代表该列：

| 脱敏类型 | 显示 |
| --- | --- |
| `mask` | ASCII 字母显示为 `X`/`x`，数字显示为 `n`；其他字符保留 |
| `mask_show_last_4` | 仅显示最后四个字符 |
| `mask_show_first_4` | 仅显示前四个字符 |
| `mask_hash` | 未加盐的 SHA-256 哈希 |
| `mask_null` | `NULL` |
| `mask_none` | 原值不变 |
| `mask_date_show_year` | 该日期所在年份的 1 月 1 日 |
| `custom` | 自定义的 MySQL 表达式，用 `{col}` 代表列，例如 `CONCAT('****', RIGHT({col}, 4))` |

首四位和末四位脱敏会完整保留长度不超过四个字符的值。例如 `mask_show_last_4` 将 `4111111111111111` 显示为 `xxxxxxxxxxxx1111`。

## 行过滤

行过滤策略作用于表。过滤条件是基于表中列的 MySQL 条件，例如 `region = 'EU'`：用户只能看到满足条件的行，单表 `UPDATE` 和 `DELETE` 也只会修改这些行。需要对目标表应用行过滤的多表写入会被拒绝；其他限制见代理指南。

脱敏和行过滤不会授予访问权限，仍需单独配置允许访问的策略。

## 策略示例

假设 MySQL 中有 `sales.orders` 表，包含 `id`、`region`、`card` 列。为 MySQL 登录用户 `alice` 在同一个数据服务下创建三条已启用的策略：

| 策略种类 | 资源 | 规则 |
| --- | --- | --- |
| 访问 | `database=sales`、`table=orders`、`column=*` | 允许用户 `alice` 的 `select` |
| 脱敏 | `database=sales`、`table=orders`、`column=card` | 用户 `alice` 使用 `mask_show_last_4` |
| 行过滤 | `database=sales`、`table=orders` | 用户 `alice` 使用 `region = 'EU'` |

策略用户填写 `alice`，不填写 `alice@host`。MySQL 自身也必须允许该账号读取此表。代理应用新快照后，`alice` 经代理查询只能看到 EU 行，卡号仅显示末四位；查询条件和连接表达式看到的也是脱敏后的值。完整的部署与验收步骤见 [MySQL 代理](/external/mysql-agent/)。

## 让策略生效

服务插件负责管理策略和查询资源。要让策略（包括脱敏和行过滤）真正生效，还需部署 MySQL 代理：客户端连接代理而不是直接连接服务器。部署方式与检查内容见代理指南。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [MySQL 代理](/external/mysql-agent/)
- [插件与服务类型开发](/develop/plugins/)
