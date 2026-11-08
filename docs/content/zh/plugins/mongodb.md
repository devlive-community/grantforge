---
title: MongoDB
description: 安装并配置 MongoDB 服务插件，连接副本集或分片集群，用 SCRAM、客户端证书、LDAP 或 Kerberos 登录，查询数据库与集合，并按集合管理谁可以读、写和管理什么。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MongoDB 插件查询数据库与集合，并用 MongoDB 自己的概念管理访问策略：按集合决定读取、变更流、插入、更新与删除、索引、集合选项、跳过文档校验和删除集合；按数据库决定创建集合和删除数据库；按整个部署决定列出全部数据库和监控。插件 ID 与服务类型都是 `mongodb`。服务插件运行在 GrantForge 服务器中，运行在部署旁的 [MongoDB 代理](/external/mongodb-agent/) 用 MongoDB 自身的角色实施策略。

仓库的 Testcontainers 集成测试覆盖 MongoDB `6.0`、`7.0`、`8.0`、`9.0` 副本集，以及 Percona Server for MongoDB `8.0` 上的 Kerberos。这并不认证其他所有版本。

## 安装

发行包在 `plugins/mongodb` 中附带此插件。在 **平台管理 → 插件** 中确认 `mongodb` 已启用，更新插件目录后重新扫描。插件在自己的类加载器中通过官方 MongoDB Java 驱动与 MongoDB 通信。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 MongoDB（`mongodb`）。
2. 填写部署的连接字符串，与任何 MongoDB 客户端使用的相同。
3. 填写查询用户的登录方式，测试连接并保存服务。测试会登录并描述部署：MongoDB 版本，是副本集、分片集群还是单机，谁登录了，以及该用户能看到多少个数据库。

查询用户让 GrantForge 能找到资源，它不是读写数据的用户。连接测试成功并不代表访问已经遵循策略。

| 设置 | 用途 |
| --- | --- |
| `mongodb.uri` | 必填：连接字符串，如 `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` 或 `mongodb+srv://cluster.example.com/`。`tls=true` 等选项写在这里；凭据不写在这里，含凭据的字符串会被拒绝 |
| `auth.mechanism` | 有密码的用户用 `SCRAM-SHA-256`（默认）或 `SCRAM-SHA-1`，客户端证书用 `MONGODB-X509`，LDAP 用 `PLAIN`，Kerberos 用 `GSSAPI` |
| `username` | 查询用户。`GSSAPI` 时为主体；`MONGODB-X509` 时留空则取自证书 |
| `password` | 查询用户的密码，加密存储 |
| `auth.source` | SCRAM：定义该用户的数据库，默认 `admin`。其他机制使用 `$external` |
| `tls.ca.file` | `tls=true` 时：**GrantForge 服务器**上用于校验服务器的 PEM CA 证书路径。留空使用 JVM 的 |
| `tls.certificate.key.file` | `tls=true` 时：**GrantForge 服务器**上同时包含客户端证书和私钥的 PEM 文件路径，与 `mongosh` 使用的相同 |
| `tls.certificate.key.password` | 加密私钥的密码，加密存储 |
| `kerberos.keytab` | `GSSAPI`：**GrantForge 服务器**上该主体的 keytab 路径。留空则使用密码 |
| `kerberos.kdc` | `GSSAPI`：该 realm 的 KDC，以逗号分隔。留空使用服务器的 `krb5.conf` |
| `kerberos.service.name` | `GSSAPI`：服务器主体的服务名，默认 `mongodb` |
| `timeout` | 查找服务器、连接和单条命令允许的时长，默认 `30000`，范围 `1..600000` 毫秒 |

登录被拒绝、查询用户缺少权限、部署无法连接都会如实报告。

### 查询用户能看到什么

MongoDB 只列出用户拥有任一权限的数据库和集合，所以查询结果就是查询用户能看到的内容。请给它要编写策略的对象的 `read` 权限，或用 `readAnyDatabase` 覆盖全部。MongoDB 自己的数据库 `admin`、`config`、`local` 以及系统集合不会列出：策略不决定它们。

## 资源与访问类型

| 层级 | 匹配方式 | 访问类型 |
| --- | --- | --- |
| `deployment` | 通配符，区分大小写。副本集名称；分片集群为 `sharded`，单机为 `standalone` | `list_databases`、`monitor` |
| `database` | 通配符，区分大小写 | `create`、`drop_database` |
| `collection` | 通配符，区分大小写。数据库下的集合或视图 | `read`、`change_stream`、`insert`、`update`、`remove`、`create_index`、`drop_index`、`alter`、`bypass_validation`、`drop` |

| 访问类型 | MongoDB 的动作 |
| --- | --- |
| `read` | `find`、`listIndexes`、`collStats`、`killCursors`；整个数据库时还有 `listCollections` 和 `dbStats` |
| `change_stream` | `changeStream`；打开变更流时 MongoDB 还要求 `find` |
| `insert`、`update`、`remove` | `insert`、`update`、`remove` |
| `create_index`、`drop_index` | `createIndex`、`dropIndex` |
| `alter` | `collMod`、`validate`、`convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | 数据库上的 `createCollection`：创建集合和视图 |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`：所有数据库的名称，而不只是用户有权限的那些 |
| `monitor` | `serverStatus`、`top`、`replSetGetStatus`、`connPoolStats`、`hostInfo`、`inprog` |

策略值中 `*` 匹配任意文本，`?` 匹配一个字符。策略编辑器会提示部署、数据库，以及策略指定数据库下的集合。

MongoDB 删除数据库的权限就是删除其中每个集合的权限，所以只有当策略同时允许删除所有集合时，代理才授予 `drop_database`。否则代理不授予并说明原因，而不是给出超过策略允许的权限。用户和角色管理不是访问类型，任何策略都不会给出它们。

### 行与字段

MongoDB 不按角色过滤行，也不按角色脱敏字段，所以此服务类型不提供这两项。视图可以提供受限的读取：MongoDB 只按视图本身检查读取者，所以允许读取视图而不给源集合任何权限的策略，只会让用户看到视图展示的内容。

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## 策略示例

假设部署中有数据库 `sales` 和 `hr`。在同一数据服务中创建以下启用的策略：

| 资源 | 规则 |
| --- | --- |
| `database=sales`、`collection=*` | 允许组 `analysts` `read` |
| `database=sales`、`collection=orders_public` | 允许用户 `support` `read` |
| `database=sales`、`collection=orders` | 允许用户 `ingest` `insert` 和 `update`；拒绝组 `contractors` `read` |
| `database=team_*` | 允许组 `data-team` `create` |
| `database=team_*`、`collection=*` | 允许组 `data-team` `read`、`insert`、`update`、`remove`、`create_index` 和 `drop` |
| `deployment=*` | 允许用户 `dashboards` `monitor` |

代理应用快照后，`analysts` 的成员可以读取 `sales` 的全部内容，包括之后创建的集合；`support` 不需要源集合就能读取视图；`ingest` 可以写入 `orders` 但不能读取；`data-team` 的成员在 `team_` 数据库内自由工作；`dashboards` 能看到服务器状态而看不到数据。

## 实施策略

服务插件管理策略并查询资源。实施策略需要每个部署一个 MongoDB 代理，部署和检查见 [MongoDB 代理](/external/mongodb-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [MongoDB 代理](/external/mongodb-agent/)
- [插件与服务类型](/develop/plugins/)
