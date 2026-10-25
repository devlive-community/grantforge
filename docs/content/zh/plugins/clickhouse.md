---
title: ClickHouse
description: 安装并配置 ClickHouse 服务插件，连接一台或多台服务器，以密码或 TLS 客户端证书登录，查询数据库、表与列，并管理谁可以读取、写入与管理什么，细到列与行。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

ClickHouse 插件查询数据库、表与列，并按 ClickHouse 自己的方式管理访问策略：读写列，修改、删除、清空与优化表，在数据库中建表与删除数据库，以及服务器上的建库、`KILL QUERY` 与 `SYSTEM` 语句，并支持表的行过滤。插件 ID 与服务类型均为 `clickhouse`。服务插件运行在 GrantForge 服务器中；运行在服务器旁的 [ClickHouse 代理](/external/clickhouse-agent/)借助 ClickHouse 自己的授权与行策略执行策略。

仓库的 Testcontainers 集成测试覆盖 ClickHouse `24.8`、`25.3`、`25.8`、`26.3` 与 `26.8`，即近两年的长期支持版本；这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/clickhouse` 中包含此插件。在 **平台管理 → 插件** 中确认 `clickhouse` 已启用；更新插件目录后重新扫描。插件通过 ClickHouse 官方的 Java 客户端以 HTTP 与服务器通信，位于插件自己的类加载器中。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 ClickHouse（`clickhouse`）。
2. 输入一台服务器或多台服务器的 HTTP 接口 URL。
3. 选择查询用户的登录方式，填写用户名与凭据，测试连接后保存服务。测试会登录并描述服务器：ClickHouse 版本、登录所用的用户，以及该用户能看到多少个数据库。

查询用户用于让 GrantForge 发现资源，与读写数据的用户相互独立。连接测试成功并不表示访问已受策略约束。

| 设置 | 用途 |
| --- | --- |
| `clickhouse.urls` | 必填：服务器的 HTTP 接口，例如 `https://clickhouse1.example.com:8443`；多个以逗号分隔，某台无法访问时依次尝试下一台 |
| `authentication` | `password`（默认）或 `certificate`：由 ClickHouse 映射到该用户的 TLS 客户端证书（`IDENTIFIED WITH ssl_certificate`） |
| `username` | 必填：查询用户 |
| `password` | password：查询用户的密码；加密存储 |
| `ssl.root.certificate` | https：**GrantForge 服务器** 上用于校验服务器证书的 PEM CA 证书；为空时使用 JVM 自带的 |
| `ssl.client.certificate`、`ssl.client.key` | certificate：GrantForge 服务器上的 PEM 客户端证书及其私钥；证书登录需要 `https://` 服务器 |
| `timeout` | 请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |

一种登录方式的设置在另一种方式下会被拒绝，因此服务明确写出它如何登录。登录被拒、只读用户、服务器无法访问或其证书无法校验，以及代替服务器应答的并非 ClickHouse（例如代理），都会如实报告。

### 查询用户能看到什么

ClickHouse 只向用户显示其拥有某种权限的数据库、表与列。请在需要编写策略的范围上授予查询用户 `SHOW`，例如 `GRANT SHOW ON *.* TO grantforge_lookup`，它只能看到名称，读不到数据。ClickHouse 自己的数据库 `system` 与 `information_schema` 不在其列：策略不决定它们。

## 资源与访问类型

| 层级 | 匹配 | 访问类型 |
| --- | --- | --- |
| `database` | 通配符，区分大小写；数据库名 | `create`、`drop_database` |
| `table` | 通配符，区分大小写；位于数据库之下 | `alter`、`drop`、`truncate`、`optimize` |
| `column` | 通配符，区分大小写；位于表之下 | `select`、`insert` |
| `server` | 唯一取值 `*` | `create_database`、`kill_query`、`system` |

| 访问类型 | ClickHouse 的权限 |
| --- | --- |
| `select`、`insert` | 列上的 `SELECT`、`INSERT` |
| `alter` | `ALTER`：表的一切修改，包括 `ALTER UPDATE` 与 `ALTER DELETE` |
| `drop` | `DROP TABLE`、`DROP VIEW`、`DROP DICTIONARY` |
| `truncate`、`optimize` | `TRUNCATE`、`OPTIMIZE` |
| `create` | 在该数据库中的 `CREATE TABLE`、`CREATE VIEW`、`CREATE DICTIONARY` |
| `drop_database` | `DROP DATABASE` |
| `create_database`、`kill_query`、`system` | `CREATE DATABASE`、`KILL QUERY`、`SYSTEM`：所有 `SYSTEM` 语句 |

读取与写入按列判定，与 ClickHouse 的授权方式一致：`select` 或 `insert` 的策略要指明列，`*` 表示全部列。因此一条策略可以允许一张表并拒绝其中一列，用户仍可读取其余各列。策略取值中，`*` 匹配任意一串字符，`?` 匹配一个字符。策略编辑器会提示数据库、策略所指数据库中的表及其列。

这些访问类型都不是 ClickHouse 的权限组（如 `ALL`）：每一项就是它所写的意思，因此一条策略给出的永远不会超出它所写的。ClickHouse 只在 ClickHouse Cloud 中提供列脱敏，因此此服务类型不提供脱敏。

### 行过滤

表上的行过滤策略只让其用户看到某个条件成立的行。条件是 ClickHouse 表达式，与行策略的写法相同，例如 `region = 'EU'` 或 `owner = currentUser()`。与其他服务一样，用户看到第一条适用于他的策略的过滤条件。

## 策略示例

假设服务器上有数据库 `sales` 与 `hr`。在同一数据服务中创建以下已启用的策略：

| 资源 | 规则 |
| --- | --- |
| `database=sales`、`table=*`、`column=*` | 允许组 `analysts` `select` |
| `database=sales`、`table=orders`、`column=card` | 拒绝组 `analysts` `select` |
| `database=sales`、`table=orders`、`column=*` | 允许用户 `ingest` `insert` |
| `database=team_*` | 允许组 `data-team` `create` |
| `database=team_*`、`table=*` | 允许组 `data-team` `alter`、`drop`、`truncate` 与 `optimize` |
| `database=team_*`、`table=*`、`column=*` | 允许组 `data-team` `select` 与 `insert` |
| `server=*` | 允许组 `data-team` `create_database` |
| `database=sales`、`table=orders`（行过滤） | 用户 `emea` 只看到 `region = 'EMEA'` 的行 |

代理应用新快照后，`analysts` 的成员可以读取 `sales` 的每张表，但读不到 `orders` 的卡号；`ingest` 可以写入 `orders`，但什么都读不到；`data-team` 的成员可以建库，并在名称以 `team_` 开头的数据库中建表，读写与修改它们；而 `emea` 若有策略允许读取 `orders`，只能看到本区域的行。

## 执行策略

服务插件管理策略并查询资源。执行策略需要 ClickHouse 代理，每台服务器或每个集群一个。部署与检查见 [ClickHouse 代理](/external/clickhouse-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [ClickHouse 代理](/external/clickhouse-agent/)
- [插件与服务类型](/develop/plugins/)
