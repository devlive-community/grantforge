---
title: Trino
description: 安装并配置 Trino 服务插件，通过 TLS 以密码、JSON Web Token 或 Kerberos 登录，查询目录、模式、表、列与会话属性，并按 Trino 自己的概念管理访问、行过滤与脱敏策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Trino 插件查询目录（catalog）、模式（schema）、表、列与会话属性，并针对 Trino 所检查的对象管理访问、行过滤与脱敏策略：数据、例程、会话属性、用户与系统信息。插件 ID 与服务类型均为 `trino`。服务插件运行在 GrantForge 服务器中；安装在 coordinator 中的 [Trino 代理](/external/trino-agent/)负责执行策略。

仓库的 Testcontainers 集成测试覆盖 Trino `470`、`476`、`479` 与 `484`，并在 `470` 与 `484` 上覆盖使用密码文件和 JSON Web Token 的 HTTPS，以及 Kerberos。这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/trino` 中包含该插件。在 **平台管理 → 插件** 中确认 `trino` 已启用；更新插件目录后重新扫描。插件通过 Trino 自己的 JDBC 驱动（`trino-jdbc` 484）与 coordinator 通信，驱动位于插件自己的类加载器中，可连接上述所有 Trino 版本。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 Trino（`trino`）。
2. 输入 coordinator 的 JDBC URL，例如 `jdbc:trino://trino.example.com:8443`。
3. 选择查询用户的登录方式并填写，测试连接后保存服务。测试会登录并描述 coordinator：它的版本，以及该用户能看到多少个目录。

设置项沿用驱动自己的名称，与 Trino 的 JDBC 文档一致。查询用户用于让 GrantForge 发现资源，与执行查询的用户相互独立。连接测试成功并不表示查询已经受策略约束。

| 设置 | 用途 |
| --- | --- |
| `jdbc.url` | 必填：`jdbc:trino://host:port`，可带目录与模式，以及除凭据以外的驱动属性，例如 `?SSL=true` |
| `authentication` | `none`（默认）：只用用户名；`password`；`jwt`：JSON Web Token；`kerberos`：带 keytab 或密码的主体 |
| `user` | 必填的查询用户；Kerberos 下为完整主体，例如 `grantforge@EXAMPLE.COM` |
| `password` | 用户的密码；Kerberos 下为主体的密码（未提供 keytab 时）；加密保存 |
| `accessToken` | `jwt`：coordinator 接受的令牌；加密保存 |
| `keytab` | Kerberos：主体的 keytab 路径，需要在 **GrantForge 服务器** 上可读 |
| `kerberos.kdc` | Kerberos：主体所在 realm 的 KDC，`host[:port]`，以逗号分隔；为空时使用服务器的 `krb5.conf` |
| `KerberosRemoteServiceName` | Kerberos：coordinator 主体的第一部分，默认 `trino` |
| `KerberosUseCanonicalHostname` | Kerberos：coordinator 的主体是否使用 URL 主机的规范名称，默认 `true`；位于负载均衡器或别名之后且主体带有该名称时关闭 |
| `SSL` | 是否使用 TLS，默认 `false`；除 `none` 外的任何登录方式都需要它，因为 Trino 只通过 TLS 登录用户 |
| `SSLTrustStorePath`、`SSLTrustStorePassword`、`SSLTrustStoreType` | TLS：用于验证 coordinator 的信任库、其密码与类型（`JKS` 或 `PKCS12`）；为空时信任 Java 信任的证书 |
| `SSLVerification` | TLS：`FULL`（默认）检查证书与主机名，`CA` 只检查证书，`NONE` 什么都不检查且不接受信任库 |
| `timeout` | 请求超时（毫秒），默认 `30000`，范围 `1..600000` |

服务完全由其字段决定。URL 中不能携带凭据或其他登录方式：`user`、`password`、`accessToken`、`sessionUser`、`extraCredentials`、外部与 OAuth 2.0 认证、Kerberos 文件与委托，以及密钥库和信任库密码都会被拒绝。其他登录方式的字段也会被拒绝，例如在密码认证下填写令牌。

无法验证证书的 coordinator 不会被连接：测试会报告其不可达。登录被拒绝、权限不足与 coordinator 不可达都会如实报告。

### Kerberos

使用 `kerberos` 时，GrantForge 从 keytab 或用密码登录主体，并把这次登录的凭据交给驱动；GrantForge 服务器上的其他部分无需 Kerberos 设置。在 `kerberos.kdc` 中填写了 KDC 的 realm 会为整个 GrantForge 服务器声明，与 HDFS、Hive、Kafka 和 HBase 插件的做法相同，因此多个服务可以并行使用不同的 realm。会话用户为主体中 realm 之前的名称，与 Trino 默认的主体映射一致。

### 查询用户能看到什么

在 [Trino 代理](/external/trino-agent/)执行策略后，列表会省略查询用户无权使用的内容：为它添加一条在目录 `*` 上允许 `select` 的策略，或在希望策略编辑器提示其资源的目录上允许。

## 资源与访问类型

| 级别 | 匹配 | 访问类型 |
| --- | --- | --- |
| `catalog` | 通配符，不区分大小写 | `select`、`insert`、`update`、`delete`、`create`、`drop`、`alter`、`all` |
| `schema` | 通配符，不区分大小写；位于目录下 | 同 `catalog` |
| `table` | 通配符，不区分大小写；位于模式下；包括表、视图与物化视图 | 同 `catalog` |
| `column` | 通配符，不区分大小写；位于表下 | `select`、`update` |
| `function` | 通配符，不区分大小写；位于模式下 | `execute`、`create_function`、`drop_function` |
| `procedure` | 通配符，不区分大小写；位于模式下 | `execute` |
| `session_property` | 通配符，不区分大小写；位于目录下，名称不含目录 | `set` |
| `system_session_property` | 通配符，不区分大小写 | `set` |
| `user` | 通配符，区分大小写 | `impersonate`、`view_query`、`kill_query` |
| `system_information` | 唯一的值 `*` | `read`、`write` |

`all` 隐含 `select`、`insert`、`update`、`delete`、`create`、`drop` 与 `alter`。

目录、模式或表上的权限覆盖其中的一切：模式 `sales` 上的 `select` 可读取其中每张表，目录上的 `create` 可在其中创建模式与表。一次检查会在它涉及的每个范围上判定，任一范围上的拒绝都会拒绝它，因此一条策略可以允许一个模式，同时拒绝其中一张表的某一列。函数、过程与会话属性按名称授权，用 `*` 表示模式或目录中的全部；函数有自己的访问类型，因此对例程的授权绝不会被当作对表的授权。

策略值中，`*` 匹配任意多个字符，`?` 匹配一个字符。策略编辑器会提示目录、策略所列目录中的模式、其模式中的表、其表中的列，以及其目录与系统的会话属性；函数、过程与用户不会被提示。

## 行过滤与脱敏

表上的行过滤策略与列上的脱敏策略都是 Trino SQL，coordinator 会将其应用于读取它们的每个查询。行过滤是表中列上的条件，例如 `region = 'EU'`；其中的名称在该表的目录与模式中解析。

| 脱敏类型 | 显示 |
| --- | --- |
| `mask` | 字母显示为 `X` 或 `x`，数字显示为 `n` |
| `mask_show_last_4`、`mask_show_first_4` | 只显示后四个或前四个字符 |
| `mask_hash` | 值的 SHA-256，十六进制 |
| `mask_null` | `NULL` |
| `mask_none` | 原值，不脱敏 |
| `mask_date_show_year` | 只显示日期或时间戳的年份 |
| `custom` | 策略中的 Trino SQL 表达式，用 `{col}` 表示该列 |

代理会让每种脱敏适配列的类型，使脱敏不会破坏读取该列的查询：见 [Trino 代理](/external/trino-agent/)。

## 策略示例

假设目录 `hive` 中有模式 `sales`，其中有表 `orders`（列包括 `region`、`customer` 与 `card`）和 `returns`。在同一数据服务中创建以下已启用的策略：

| 策略 | 资源 | 规则 |
| --- | --- | --- |
| 访问 | `catalog=hive`、`schema=sales` | 为组 `analysts` 允许 `select` |
| 访问 | `catalog=hive`、`schema=sales`、`table=orders`、`column=card` | 为组 `analysts` 拒绝 `select` |
| 访问 | `catalog=hive`、`schema=sales`、`table=returns` | 为用户 `alice` 允许 `insert` |
| 行过滤 | `catalog=hive`、`schema=sales`、`table=orders` | 为用户 `alice` 设置 `region = 'EU'` |
| 脱敏 | `catalog=hive`、`schema=sales`、`table=orders`、`column=customer` | 为组 `analysts` 设置 `mask_hash` |
| 访问 | `catalog=system`、`schema=runtime` | 为用户 `alice` 允许 `select` |
| 访问 | `user=bob` | 为用户 `alice` 允许 `view_query` |

代理应用新快照后，`analysts` 的成员可以读取 `sales` 中除 `orders.card` 外的每张表，看到的 `customer` 是哈希值；`alice` 只看到欧洲的订单，可以写入 `returns`，并且在 `system.runtime.queries` 中除了自己的查询还能看到 Bob 的查询。

## 执行策略

服务插件负责管理策略与查询资源。执行策略需要在 coordinator 中安装 Trino 代理。部署方式与检查内容见 [Trino 代理](/external/trino-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Trino 代理](/external/trino-agent/)
- [插件与服务类型](/develop/plugins/)
