---
title: Apache Doris
description: 安装并配置 Apache Doris 服务插件，连接一个或多个 FE，以密码登录，查询 Catalog、数据库、表、列与工作负载组，并管理谁可以读取、导入与管理什么，细到列与行，以及谁可以在哪个工作负载组中运行查询。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Doris 插件查询 Catalog、数据库、表、列与工作负载组，并按 Doris 自己的方式管理访问策略：读取列，导入、修改、删除表与查看视图定义，在数据库中建表与删除数据库，在 Catalog 中建库，以及在工作负载组中运行查询，并支持表的行过滤。插件 ID 与服务类型均为 `doris`。服务插件运行在 GrantForge 服务器中；运行在集群旁的 [Apache Doris 代理](/external/doris-agent/)借助 Doris 自己的角色、授权与行策略执行策略。

仓库的 Testcontainers 集成测试覆盖 Apache Doris `2.1`、`3.1`、`4.0` 与 `4.1`，每个版本一个 FE 加一个 BE 的集群；这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/doris` 中包含此插件。在 **平台管理 → 插件** 中确认 `doris` 已启用；更新插件目录后重新扫描。插件通过 MariaDB Connector/J 以 MySQL 协议与 FE 通信，位于插件自己的类加载器中。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 Apache Doris（`doris`）。
2. 输入一个 FE 或多个 FE 的 MySQL 协议地址。
3. 填写查询用户的用户名与密码，测试连接后保存服务。测试会登录并描述集群：Doris 版本、登录所用的用户、该用户能看到多少个 Catalog，以及内部 Catalog 中的多少个数据库。测试只询问 FE，因此没有 BE 运行时也能应答。

查询用户用于让 GrantForge 发现资源，与读取和导入数据的用户相互独立。连接测试成功并不表示访问已受策略约束。

| 设置 | 用途 |
| --- | --- |
| `doris.hosts` | 必填：FE 的 MySQL 协议地址，例如 `fe1.example.com:9030`；多个以逗号分隔，某个无法访问时依次尝试下一个。未写端口时为 `9030` |
| `username` | 必填：查询用户 |
| `password` | 查询用户的密码；加密存储 |
| `ssl.mode` | `disable`（默认）、`trust`（加密但不校验 FE）、`verify-ca`（校验其证书）或 `verify-full`（同时校验其名称）；用于在 MySQL 协议上提供 TLS 的 FE |
| `ssl.root.certificate` | verify-ca 与 verify-full：**GrantForge 服务器** 上用于校验 FE 的 PEM CA 证书；为空时使用 JVM 自带的 |
| `timeout` | 连接与一条语句可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |

登录被拒、查询用户缺少权限以及 FE 无法访问，都会如实报告。

### 查询用户能看到什么

Doris 的 `SHOW` 语句只列出用户持有某种权限的 Catalog、数据库、表与列，因此查询结果就是查询用户能看到的内容。请在需要编写策略的对象上授予它 `SELECT_PRIV`，例如 `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`；`SHOW WORKLOAD GROUPS` 列出它能看到的工作负载组。Doris 自己的数据库 `information_schema`、`mysql` 与 `__internal_schema` 不在其列：策略不决定它们。

## 资源与访问类型

| 层级 | 匹配方式 | 访问类型 |
| --- | --- | --- |
| `catalog` | 通配符，区分大小写；内部 Catalog 或外部 Catalog | `create_database` |
| `database` | 通配符，区分大小写；位于 Catalog 之下 | `create`、`drop_database` |
| `table` | 通配符，区分大小写；位于数据库之下，表或视图 | `load`、`alter`、`drop`、`show_view` |
| `column` | 通配符，不区分大小写，与 Doris 比较列名的方式一致；位于表之下 | `select` |
| `workload_group` | 通配符，区分大小写；工作负载组的名称 | `usage` |

| 访问类型 | Doris 的权限 |
| --- | --- |
| `select` | `SELECT_PRIV`，授予在列上，或整张表、整个数据库、整个 Catalog 上 |
| `load` | `LOAD_PRIV`：导入、`INSERT`、`UPDATE`、`DELETE` 与 `TRUNCATE` |
| `alter`、`drop` | 表上的 `ALTER_PRIV`、`DROP_PRIV` |
| `show_view` | `SHOW_VIEW_PRIV`：`SHOW CREATE VIEW` |
| `create` | 数据库上的 `CREATE_PRIV`：创建该数据库，以及在其中建表与建视图 |
| `drop_database` | 数据库上的 `DROP_PRIV` |
| `create_database` | Catalog 上的 `CREATE_PRIV` |
| `usage` | 工作负载组上的 `USAGE_PRIV` |

读取按列判定，与 Doris 的授权方式一致：`select` 策略指定列，`*` 表示全部列。因此一条策略可以允许一张表而拒绝其中一列，用户能读取其余所有列。在策略值中，`*` 匹配任意长度的字符，`?` 匹配一个字符。策略编辑器会提示 Catalog、策略所指 Catalog 中的数据库、其中的表与列，以及工作负载组。

Doris 在数据库上的权限也作用于其中的每张表，在 Catalog 上的权限也作用于其中的每个数据库。因此代理只在策略同时允许用户在 Catalog 的每个数据库中建表时才授予 `create_database`，只在策略同时允许用户删除数据库中的每张表时才授予 `drop_database`；否则会扣下该权限并说明原因，而不是授予超出策略允许的内容。`ADMIN_PRIV`、`GRANT_PRIV` 与 `NODE_PRIV` 不是访问类型：任何策略都不会授予它们。

Doris 只能借助 Apache Ranger 脱敏，因此此服务类型不提供脱敏。

### 行过滤

表上的行过滤策略只向其用户显示满足条件的行。条件是 Doris 行策略所接受的谓词，例如 `region = 'EU'`；代理会先在表上试运行它，无法运行的过滤不显示任何行。与其他服务一样，用户适用第一条对其生效的策略的过滤。

## 策略示例

假设集群的内部 Catalog 中有数据库 `sales` 与 `hr`，以及工作负载组 `etl`。在同一个数据服务中创建以下已启用的策略：

| 资源 | 规则 |
| --- | --- |
| `catalog=internal`、`database=sales`、`table=*`、`column=*` | 允许组 `analysts` `select` |
| `catalog=internal`、`database=sales`、`table=orders`、`column=card` | 拒绝组 `analysts` `select` |
| `catalog=internal`、`database=sales`、`table=orders` | 允许用户 `ingest` `load` |
| `catalog=internal`、`database=team_*` | 允许组 `data-team` `create` |
| `catalog=internal`、`database=team_*`、`table=*` | 允许组 `data-team` `load`、`alter` 与 `drop` |
| `workload_group=etl` | 允许用户 `ingest` 与组 `data-team` `usage` |
| `catalog=internal`、`database=sales`、`table=orders`（行过滤） | 只向用户 `emea` 显示 `region = 'EMEA'` 的行 |

代理应用新快照后，`analysts` 的成员能读取 `sales` 的每张表，但读不到 `orders` 的卡号；`ingest` 能向 `orders` 导入，并能在 `etl` 中运行其导入；`data-team` 的成员能在管理员创建的 `team_` 数据库中建表，并对其导入、修改与删除；`emea` 若有策略允许其读取 `orders`，只能看到本地区的行。

## 执行策略

服务插件管理策略并查询资源。执行策略需要 Apache Doris 代理，每个集群一个。部署与检查见 [Apache Doris 代理](/external/doris-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Apache Doris 代理](/external/doris-agent/)
- [插件与服务类型](/develop/plugins/)
