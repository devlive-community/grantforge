---
title: Apache Hive
description: 安装和配置 Hive 服务插件，用密码或 Kerberos 登录，查询数据库、表、列与 UDF，并管理访问、脱敏与行过滤策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Hive 插件为 HiveServer2 提供数据库、表、列与永久 UDF 的查询，以及访问、脱敏和行过滤策略，模型参照 Apache Ranger 的 Hive 服务。插件 ID 与服务类型均为 `hive`。服务插件运行在 GrantForge 服务器中；安装在 HiveServer2 中的 [Hive 代理](/external/hive-agent/)负责执行策略。

仓库的 Testcontainers 集成测试覆盖 HiveServer2 `3.1.3`、`4.0.1` 与 `4.1.0`，其中包括对 `4.0.1` 的 Kerberos 登录；这并不代表对其他所有版本的认证。

## 安装

发行包在 `plugins/hive` 中包含该插件。请在 **平台管理 → 插件** 中确认 `hive` 已启用；更新插件目录后请重新扫描。插件在独立的类加载器中通过 Hive 自己的 JDBC 驱动（`hive-jdbc` 4.0.1 standalone）连接 HiveServer2，因此同样适用于 HiveServer2 3.1 与 4.x。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，新增服务并选择 Apache Hive（`hive`）。
2. 填写 HiveServer2 的 JDBC URL，例如 `jdbc:hive2://hiveserver2.example.com:10000/default`。也支持 ZooKeeper 服务发现：`jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`。
3. 选择查询用户的登录方式并填写该用户，测试连接后保存服务。测试会登录并列出数据库，与查询时的方式相同。

查询用户只用于让 GrantForge 发现资源，与执行查询的用户无关。连接测试成功并不表示查询已经受策略约束。

| 配置项 | 用途 |
| --- | --- |
| `jdbc.url` | 必填的 HiveServer2 URL `jdbc:hive2://主机/数据库;会话变量`，可带 TLS（`;ssl=true;sslTrustStore=…`）、HTTP 传输，或在 Kerberos 下带服务器主体（`;principal=hive/_HOST@EXAMPLE.COM`） |
| `authentication` | `password`（默认）：用户与密码，适用于 LDAP、PAM 或无认证；`kerberos`：主体加 keytab 或密码 |
| `username` | 必填的查询用户；Kerberos 下为完整主体，如 `grantforge@EXAMPLE.COM` |
| `password` | 用户密码；未提供 keytab 时为主体的 Kerberos 密码；加密保存 |
| `keytab` | Kerberos：主体 keytab 的路径，须在 **GrantForge 服务器** 上可读 |
| `kerberos.kdc` | Kerberos：主体所在域的 KDC，`host[:port]`，以逗号分隔；为空时使用服务器的 `krb5.conf` |
| `read.timeout.ms` | 读超时，默认 `30000`，范围 `1..600000` |

URL 中不得携带凭据，也不得包含会在 GrantForge 服务器上读取文件或令牌的内容：`user`、`password`、`initFile`、`kerberosAuthType`、`delegationToken`、`storePasswordPath`、`jwt` 以及 `browser…` 变量无论大小写一律拒绝。`auth` 只能是 `noSasl`。Kerberos 服务必须在 URL 中提供 `principal`；密码服务则不得提供。

### Kerberos

选择 `kerberos` 时，GrantForge 自己用 keytab 或密码登录该主体，并以该主体打开连接；GrantForge 服务器上不需要其他 Kerberos 配置。`kerberos.kdc` 中填写了 KDC 的域会对整个 GrantForge 服务器声明，方式与 HDFS 插件相同，因此多个 Hive 与 HDFS 服务可以同时使用不同的域。

## 资源与访问类型

| 层级 | 匹配方式 | 说明 |
| --- | --- | --- |
| `database` | 通配符，不区分大小写 | 策略可止于此层 |
| `table` | 通配符，不区分大小写 | 位于数据库之下；策略可止于此层 |
| `column` | 通配符，不区分大小写 | 位于表之下 |
| `udf` | 通配符，不区分大小写 | 永久函数，位于数据库之下 |
| `url` | 路径，区分大小写，可递归 | 语句读写的数据位置，如 `hdfs://nn:8020/landing`；访问类型 `read` 与 `write` |
| `hiveservice` | 通配符 | HiveServer2 本身；访问类型 `serviceadmin` |
| `global` | 通配符 | 临时函数与添加的文件；访问类型 `tempudfadmin` |

访问类型与 Hive 一致：`select`、`update`、`create`、`drop`、`alter`、`index`、`lock`、`read`、`write`、`repladmin`、`serviceadmin`、`tempudfadmin`、`refresh` 和 `all`。与 Ranger 相同，`all` 包含除 `tempudfadmin` 以外的所有类型，因为 `tempudfadmin` 允许用户在 HiveServer2 内运行任意代码。

策略值中 `*` 匹配任意个字符，`?` 匹配一个字符。策略编辑器按前缀提示名称：数据库、策略所列数据库中的表与 UDF，以及其表中的列。

## 脱敏

脱敏策略作用于列。每种脱敏类型都是 Hive 的一个脱敏函数，其中 `{col}` 代表该列：

| 脱敏类型 | 显示 |
| --- | --- |
| `mask` | 大写字母显示为 `X`，小写字母为 `x`，数字为 `n` |
| `mask_show_last_4` | 只显示最后四个字符 |
| `mask_show_first_4` | 只显示前四个字符 |
| `mask_hash` | SHA-256 哈希 |
| `mask_null` | `NULL` |
| `mask_none` | 原值不变 |
| `mask_date_show_year` | 该日期所在年份的 1 月 1 日 |
| `custom` | 自定义 Hive 表达式，用 `{col}` 表示列，如 `concat('****', substr({col}, -4))` |

例如，`mask_show_last_4` 将 `4111111111111111` 显示为 `xxxxxxxxxxxx1111`。

## 行过滤

行过滤策略作用于表。过滤条件是针对表中列的 Hive 条件，例如 `region = 'EU'`：查询只能看到满足条件的行。

脱敏与行过滤不授予访问权限，必须另有访问策略允许该查询。

## 策略示例

假设 Hive 中有 `sales.orders` 表，包含 `id`、`region` 和 `card` 列。在同一数据服务中为用户 `alice` 创建三条启用的策略：

| 策略类型 | 资源 | 规则 |
| --- | --- | --- |
| 访问 | `database=sales`、`table=orders`、`column=*` | 允许用户 `alice` 执行 `select` |
| 脱敏 | `database=sales`、`table=orders`、`column=card` | 对用户 `alice` 使用 `mask_show_last_4` |
| 行过滤 | `database=sales`、`table=orders` | 对用户 `alice` 使用 `region = 'EU'` |

代理应用新快照后，`alice` 只能看到 `sales.orders` 中 EU 的行，卡号除最后四位外全部脱敏；`SHOW DATABASES` 对她只列出 `sales`，其他数据库拒绝她访问。

## 执行策略

服务插件负责管理策略与查询资源。要执行策略（包括脱敏与行过滤），每个 HiveServer2 都需要安装 Hive 代理。部署方式与检查内容见 [Hive 代理](/external/hive-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Hive 代理](/external/hive-agent/)
- [插件与服务类型](/develop/plugins/)
