---
title: Redis
description: 为 Redis 和 Valkey 安装并配置 Redis 服务插件，连接单机、其副本或集群，用 ACL 用户登录（可选 TLS），查询键、频道和节点，并管理谁可以读写哪些键、使用哪些频道、监控哪些节点。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Redis 插件查询 Redis 和 Valkey 部署的键、频道和节点，并用 Redis 自己的概念管理访问策略：按模式读写键，订阅和发布频道，以及监控每个节点。插件 ID 与服务类型都是 `redis`。服务插件运行在 GrantForge 服务器中，运行在部署旁的 [Redis 代理](/external/redis-agent/) 在每个节点上用 Redis 自身的 ACL 实施策略。

仓库的 Testcontainers 集成测试覆盖 Redis `7.2`、`7.4`、`8.2`、`8.10` 和 Valkey `8.1`、`9.1` 的一主一副本，以及一个 Redis `8.10` 集群。这并不认证其他所有版本。

## 安装

发行包在 `plugins/redis` 中附带此插件。在 **平台管理 → 插件** 中确认 `redis` 已启用，更新插件目录后重新扫描。插件在自己的类加载器中通过 Jedis 与 Redis 通信。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 Redis（`redis`）。
2. 填写一个或多个节点。集群的其余节点和主节点的副本会从这些节点自动发现。
3. 填写查询用户，测试连接并保存服务。测试会登录并描述部署：是 Redis 还是 Valkey 及其版本，是单机、带副本的主节点还是集群，以及谁登录了。

查询用户让 GrantForge 能找到资源，它不是读写数据的用户。连接测试成功并不代表访问已经遵循策略。

| 设置 | 用途 |
| --- | --- |
| `redis.nodes` | 必填：节点地址 `host:port`，如 `redis1.example.com:6379`，以逗号分隔并按顺序尝试；不写端口时为 `6379` |
| `username` | 查询用户，一个 ACL 用户；留空则以默认用户登录 |
| `password` | 其密码，加密存储 |
| `tls.mode` | `disable`（默认），`verify-ca` 校验服务器证书，`verify-full` 同时校验名称 |
| `tls.ca.file` | TLS：**GrantForge 服务器**上用于校验服务器的 PEM CA 证书路径。留空使用 JVM 的 |
| `tls.certificate.file` | TLS，用于要求客户端证书的服务器：**GrantForge 服务器**上 PEM 客户端证书的路径，可同时包含私钥 |
| `tls.key.file` | 证书私钥单独存放时，**GrantForge 服务器**上该 PEM 私钥的路径 |
| `tls.key.password` | 加密私钥的密码，加密存储 |
| `timeout` | 连接和单条命令允许的时长，默认 `10000`，范围 `1..600000` 毫秒 |

登录被拒绝、查询用户缺少权限、节点无法连接都会如实报告。

### 查询用户能看到什么

键通过在每个主节点上执行 `SCAN` 查找，最多扫描几轮，无论键有多少查询都很快；频道是当前有订阅者的那些。`SCAN` 无论用户的键模式如何都会列出所有键，所以查询用户不需要读取值的权限：

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## 资源与访问类型

| 层级 | 匹配方式 | 访问类型 |
| --- | --- | --- |
| `key` | 通配符，区分大小写。一个键或键的模式 | `read`、`write` |
| `channel` | 通配符，区分大小写。一个频道或频道的模式 | `subscribe`、`publish` |
| `node` | 通配符，不区分大小写。节点地址 `host:port` | `monitor` |

| 访问类型 | Redis 允许什么 |
| --- | --- |
| `read` | 对该键执行 `@read` 中的命令，即 `%R~` |
| `write` | 对该键执行 `@write` 中的命令，即 `%W~`，也包括删除 |
| `subscribe` | `SUBSCRIBE`、`PSUBSCRIBE`、`SSUBSCRIBE` ，作用于频道， `&` |
| `publish` | `PUBLISH`、`SPUBLISH` ，作用于频道， `&` |
| `monitor` | `INFO`、`DBSIZE`、`LATENCY LATEST`、`LATENCY HISTORY`、`MEMORY STATS` |

策略值中 `*` 匹配任意文本，`?` 匹配一个字符，与 Redis 自己的模式相同。Redis 按模式授予键和频道，也无法把它们列给代理，所以策略值会作为一个独立模式授予，前提是策略对该用户在整个模式范围内的决定一致。策略在其中决定不同的模式，例如策略拒绝 `sales:card:*` 时的 `sales:*`，会被保留不授予并说明原因；请改为指定策略决定一致的键或模式。含方括号、反斜杠、圆括号或空白的值永远不会授予。

Redis 认为危险的命令，如 `FLUSHALL` 和 `KEYS`，永远不会授予；无论模式如何都会列出键的 `SCAN` 和 `RANDOMKEY`，只授予能读取所有键的用户。模式在节点的每个数据库中都指同名的键，因为 Redis 的 ACL 没有数据库的概念。

Redis 不会在值内部过滤，也不做脱敏，所以此服务类型不提供这两项。

## 策略示例

在同一数据服务中创建以下启用的策略：

| 资源 | 规则 |
| --- | --- |
| `key=sales:*` | 允许组 `analysts` `read` |
| `key=sales:card:*` | 允许用户 `billing` `read` 和 `write` |
| `key=cart:*` | 允许用户 `shop` `read` 和 `write` |
| `channel=orders.*` | 允许组 `analysts` `subscribe`；允许用户 `shop` `publish` |
| `node=*` | 允许用户 `dashboards` `monitor` |

代理应用快照后，`analysts` 的成员可以读取所有 `sales:` 键并关注订单频道；`billing` 读写卡号键，这个模式内没有其他策略作出不同决定；`shop` 维护购物车并发布订单事件；`dashboards` 能看到每个节点的状态而看不到数据。

## 实施策略

服务插件管理策略并查询资源。实施策略需要每个部署一个 Redis 代理，部署和检查见 [Redis 代理](/external/redis-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Redis 代理](/external/redis-agent/)
- [插件与服务类型](/develop/plugins/)
