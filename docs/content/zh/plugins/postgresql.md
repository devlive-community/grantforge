---
title: PostgreSQL
description: 安装并配置 PostgreSQL 服务插件，连接一台或多台服务器，以密码或 TLS 客户端证书登录，查询数据库、模式、表与列，并管理谁可以读取、写入与创建什么，细到列与行。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

PostgreSQL 插件查询数据库、模式、表与列，并按 PostgreSQL 自己的方式管理访问策略：读取、插入与更新列，删除、清空表与在表上建触发器，在模式中创建对象，以及在数据库中创建模式与临时表，并支持表的行过滤。插件 ID 与服务类型均为 `postgresql`。服务插件运行在 GrantForge 服务器中；运行在服务器旁的 [PostgreSQL 代理](/external/postgresql-agent/)借助 PostgreSQL 自己的权限与行安全策略执行策略。

仓库的 Testcontainers 集成测试覆盖 PostgreSQL `15`、`16`、`17` 与 `18`，即 PostgreSQL 仍在维护的版本；这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/postgresql` 中包含此插件。在 **平台管理 → 插件** 中确认 `postgresql` 已启用；更新插件目录后重新扫描。插件通过 PostgreSQL 官方的 JDBC 驱动与服务器通信，位于插件自己的类加载器中。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 PostgreSQL（`postgresql`）。
2. 以 `host:port` 输入一台服务器或多台服务器。
3. 选择查询用户的登录方式，填写用户名与凭据，测试连接后保存服务。测试会登录并描述服务器：PostgreSQL 版本、登录所用的用户，以及服务器上有多少个数据库。

查询用户用于让 GrantForge 发现资源，与读写数据的用户相互独立。连接测试成功并不表示访问已受策略约束。

| 设置 | 用途 |
| --- | --- |
| `postgresql.hosts` | 必填：以 `host:port` 表示的服务器，例如 `postgres1.example.com:5432`；多个以逗号分隔，某台无法访问时依次尝试下一台。端口默认为 `5432` |
| `postgresql.database` | 查询用户首先登录的数据库，用于列出数据库；默认 `postgres` |
| `authentication` | `password`（默认）或 `certificate`：由 PostgreSQL 的 `cert` 认证映射到该用户的 TLS 客户端证书 |
| `username` | 必填：查询用户 |
| `password` | password：查询用户的密码；加密存储 |
| `ssl.mode` | 按 libpq 的 `sslmode` 命名的 TLS 方式：`disable`、`allow`、`prefer`（默认）、`require`、`verify-ca` 或同时校验服务器名称的 `verify-full` |
| `ssl.root.certificate` | verify-ca 与 verify-full：**GrantForge 服务器** 上用于校验服务器证书的 PEM CA 证书；为空时使用 JVM 自带的 |
| `ssl.client.certificate`、`ssl.client.key` | certificate：GrantForge 服务器上的 PEM 客户端证书及其 PKCS #8 私钥（PEM 或 DER）；证书登录需要 `require` 或某种校验方式，与校验方式一同使用时还需要 `ssl.root.certificate` |
| `timeout` | 连接与查询可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |

一种登录方式的设置在另一种方式下会被拒绝，因此服务明确写出它如何登录。PostgreSQL 的驱动会拒绝除所有者之外他人可读的私钥文件。登录被拒、数据库不存在、服务器无法访问或其证书无法校验，都会如实报告。

### 查询用户能看到什么

PostgreSQL 的系统目录向每个用户展示所有数据库的名称，并在它可以连接的每个数据库中展示模式、表与列的名称，而无需读取数据。查询用户只需对要编写策略的数据库拥有 `CONNECT` 权限，除非被撤销，PUBLIC 默认拥有该权限。查询模式、表或列时，会登录策略所指的每个数据库（最多 20 个），并跳过查询用户无权连接的数据库。PostgreSQL 自己的模式，即以 `pg_` 开头的模式与 `information_schema`，不会列出：策略不决定它们。

## 资源与访问类型

| 层级 | 匹配 | 访问类型 |
| --- | --- | --- |
| `database` | 通配符，区分大小写；数据库名称 | `create_schema`、`temporary` |
| `schema` | 通配符，区分大小写；位于数据库之下 | `create` |
| `table` | 通配符，区分大小写；模式下的表、分区表、视图、物化视图与外部表 | `delete`、`truncate`、`trigger` |
| `column` | 通配符，区分大小写；位于表之下 | `select`、`insert`、`update` |

| 访问类型 | PostgreSQL 的权限 |
| --- | --- |
| `select`、`insert`、`update` | 列上或整张表上的 `SELECT`、`INSERT`、`UPDATE` |
| `delete`、`truncate`、`trigger` | 表上的 `DELETE`、`TRUNCATE`、`TRIGGER` |
| `create` | 模式上的 `CREATE`：在其中创建表、视图、序列、函数与其他对象 |
| `create_schema` | 数据库上的 `CREATE`：在其中创建模式 |
| `temporary` | 数据库上的 `TEMPORARY` |

读取、插入与更新按列决定，与 PostgreSQL 授予它们的方式一致：`select`、`insert` 或 `update` 的策略会指定列，`*` 表示全部列。因此策略可以允许一张表而拒绝其中一列，用户可以读取其余各列。在策略值中，`*` 匹配任意一串字符，`?` 匹配一个字符。策略编辑器会提示数据库、策略所指数据库中的模式，以及其中的表与列。

要用到策略允许的内容，PostgreSQL 还需要更多权限：数据库的 `CONNECT`、模式的 `USAGE`，以及向自增列插入时其序列的 `USAGE`。它们不是单独的访问类型：代理会随需要它们的权限一并授予。PostgreSQL 没有列脱敏，因此此服务类型不提供脱敏。

### 行过滤

表上的行过滤策略只向其用户显示条件成立的行。条件是 PostgreSQL 表达式，与行安全策略的 `USING` 所接受的一致，例如 `region = 'EU'` 或 `owner = current_user`。与其他服务一样，用户看到的是第一条适用于他的策略的过滤。PostgreSQL 只能过滤表与分区表的行：策略为某个用户过滤其行的视图等对象，对该用户完全不可读。

## 策略示例

假设服务器有数据库 `sales` 与 `hr`，`sales` 中有模式 `crm`。在同一个数据服务中创建以下已启用的策略：

| 资源 | 规则 |
| --- | --- |
| `database=sales`、`schema=crm`、`table=*`、`column=*` | 允许组 `analysts` 进行 `select` |
| `database=sales`、`schema=crm`、`table=orders`、`column=card` | 拒绝组 `analysts` 进行 `select` |
| `database=sales`、`schema=crm`、`table=orders`、`column=*` | 允许用户 `ingest` 进行 `insert` |
| `database=sales`、`schema=team_*` | 允许组 `data-team` 进行 `create` |
| `database=sales`、`schema=team_*`、`table=*` | 允许组 `data-team` 进行 `delete` 与 `truncate` |
| `database=sales`、`schema=team_*`、`table=*`、`column=*` | 允许组 `data-team` 进行 `select`、`insert` 与 `update` |
| `database=sales` | 允许组 `data-team` 进行 `create_schema` |
| `database=sales`、`schema=crm`、`table=orders`（行过滤） | 向用户 `emea` 只显示 `region = 'EMEA'` 的行 |

代理应用新快照后，`analysts` 的成员可以读取 `crm` 中的每张表，但读不到 `orders` 的卡号；`ingest` 可以向 `orders` 插入数据（包括其自增 id），但什么也读不到；`data-team` 的成员可以创建模式，并在名称以 `team_` 开头的模式中创建、读取、修改与清空表；而 `emea` 若有策略允许其读取 `orders`，只能看到自己区域的行。

## 执行策略

服务插件管理策略并查询资源。执行策略需要 PostgreSQL 代理，每台服务器或每个主库一个。部署与检查见 [PostgreSQL 代理](/external/postgresql-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [PostgreSQL 代理](/external/postgresql-agent/)
- [插件与服务类型](/develop/plugins/)
