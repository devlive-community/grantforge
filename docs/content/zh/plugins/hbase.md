---
title: Apache HBase
description: 安装和配置 HBase 服务插件，通过 ZooKeeper 或引导服务器找到集群，以 simple 或 Kerberos 登录，查询命名空间、表与列族，并按 HBase 自己的权限范围管理访问策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

HBase 插件查询命名空间、表与列族，并按 HBase 自己的权限范围管理访问策略：全局、命名空间、表、列族与列。插件 ID 与服务类型均为 `hbase`。服务插件运行在 GrantForge 服务器中；安装在 master 与 region server 中的 [HBase 代理](/external/hbase-agent/)负责执行策略。

仓库的 Testcontainers 集成测试覆盖由经 SHA-512 校验的 Apache 发行包构建的 HBase `2.5.16`、`2.6.7` 与 `3.0.0`，以及一个 `2.6.7` 的 Kerberos 集群；这并不代表对其他所有版本的认证。

## 安装

发行包在 `plugins/hbase` 中包含该插件。请在 **平台管理 → 插件** 中确认 `hbase` 已启用；更新插件目录后请重新扫描。插件在独立的类加载器中，通过 HBase 自己的客户端（`hbase-shaded-client-byo-hadoop` 2.6.7）和 Hadoop 的 shaded 客户端 3.5.0 连接集群，因此同样适用于 HBase 2.x 与 3.x。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，新增服务并选择 Apache HBase（`hbase`）。
2. 填写 ZooKeeper quorum，或 HBase 2.5 及以上集群的引导服务器。
3. 选择查询用户的登录方式并填写该用户，测试连接后保存服务。测试会登录并描述集群：集群 ID、版本与在线的 region server 数量。

配置项沿用 HBase 客户端自己的名称，可以直接从 `hbase-site.xml` 中复制。查询用户只用于让 GrantForge 发现资源，与读写数据的客户端无关。连接测试成功并不表示请求已经受策略约束。

| 配置项 | 用途 |
| --- | --- |
| `hbase.zookeeper.quorum` | ZooKeeper 主机，`主机` 或 `主机:端口` 以逗号分隔，例如 `zk1,zk2,zk3:2181`；未填写引导服务器时必填 |
| `hbase.zookeeper.property.clientPort` | 未指定端口的 quorum 主机所用端口，默认 `2181` |
| `zookeeper.znode.parent` | 集群在 ZooKeeper 中的根节点，默认 `/hbase` |
| `hbase.client.bootstrap.servers` | 代替 ZooKeeper，HBase 2.5 起可用：master 或 region server，`主机:端口` 以逗号分隔 |
| `hbase.security.authentication` | `simple`（默认）：用户名；`kerberos`：带 keytab 或密码的主体 |
| `username` | 必填的查询用户；Kerberos 下为完整主体，例如 `grantforge@EXAMPLE.COM` |
| `password` | Kerberos：主体的密码，未提供 keytab 时使用；加密保存 |
| `keytab` | Kerberos：主体 keytab 的路径，需在 **GrantForge 服务器** 上可读 |
| `kerberos.kdc` | Kerberos：主体所在领域的 KDC，`主机[:端口]` 以逗号分隔；留空则使用服务器的 `krb5.conf` |
| `hbase.master.kerberos.principal` | Kerberos：master 的主体，默认 `hbase/_HOST@` 加查询主体的领域 |
| `hbase.regionserver.kerberos.principal` | Kerberos：region server 的主体，默认与 master 相同 |
| `hbase.rpc.protection` | `authentication`（默认）、`integrity` 或 `privacy`，需与集群一致 |
| `hbase.rpc.timeout` | RPC 超时，默认 `30000`，范围 `1..600000` |

服务完全由其配置决定：不会读取 GrantForge 服务器上的任何 `hbase-site.xml`。simple 认证不接受 Kerberos 配置；Kerberos 需要密码或 keytab。查询在数次尝试后即放弃，而不是像 HBase 默认那样等待数分钟。

### Kerberos

使用 `kerberos` 时，GrantForge 通过 Hadoop 自己的登录让主体登录，凭 keytab 或密码，保留登录并按 keytab 续期；GrantForge 服务器上无需其他 Kerberos 配置。在 `kerberos.kdc` 中填写了 KDC 的领域会对整个 GrantForge 服务器声明，方式与 HDFS、Hive 插件相同，因此多个服务可以同时使用不同的领域。

客户端的登录方式与集群预期不符时，集群会直接关闭连接，不说明原因。插件会把这种情况报告为登录被拒，并提示检查 `hbase.security.authentication` 与 `hbase.rpc.protection`，二者必须与集群一致。

### 查询用户能看到什么

[HBase 代理](/external/hbase-agent/)执行策略后，列表会略去查询用户无权使用的命名空间和表：为它配置允许命名空间 `*` 上 `read` 的策略，或在集群的 `hbase.superuser` 中列出它。

## 资源与访问类型

| 层级 | 匹配 | 访问类型 |
| --- | --- | --- |
| `global` | 唯一的值 `*` | `admin`：针对整个集群的操作，例如均衡器、配额与复制 |
| `namespace` | 通配符，区分大小写 | `read`、`write`、`create`、`admin` |
| `table` | 通配符，区分大小写；位于命名空间之下，名称不含命名空间 | `read`、`write`、`create`、`admin` |
| `column_family` | 通配符，区分大小写；位于表之下 | `read`、`write` |
| `column` | 通配符，区分大小写；位于列族之下；按 UTF-8 文本命名 | `read`、`write` |

访问类型就是 HBase 的 `read`、`write`、`create` 与 `admin`。HBase 用于协处理器端点的 `exec` 不提供，因为代理无法在所有 HBase 版本上检查端点调用。

与 HBase 相同，权限覆盖其范围内的一切：命名空间 `sales` 上的 `read` 可读其中所有表，列族上的 `write` 可写其所有列。请求会在其涉及的每个范围上分别决策，任一范围的拒绝都会拒绝该请求，因此策略可以允许一张表而拒绝其中一列。全局范围是集群本身，而不是所有表；命名空间 `*` 上的策略才覆盖所有表。

策略值中，`*` 匹配任意数量的字符，`?` 匹配一个字符。策略编辑器会提示命名空间、策略所指命名空间中的表，以及其中表的列族；列存放的是数据，不做提示。

## 策略示例

假设命名空间 `sales` 中有表 `orders`，包含列族 `d`（含 `name` 与 `ssn`）和 `meta`。在同一数据服务中创建以下已启用的访问策略：

| 资源 | 规则 |
| --- | --- |
| `namespace=sales` | 允许用户 `alice` 的 `read` |
| `namespace=sales`、`table=orders`、`column_family=d` | 允许用户 `bob` 的 `read` |
| `namespace=sales`、`table=orders` | 允许组 `auditors` 的 `read` |
| `namespace=sales`、`table=orders`、`column_family=d`、`column=ssn` | 拒绝组 `auditors` 的 `read` |
| `namespace=analytics` | 允许用户 `dave` 的 `create` 与 `admin` |

代理应用新快照后，`alice` 可读 `sales` 中所有表；`bob` 读取 `orders` 时只得到 `d` 的列，列出表时也只看到 `orders`；`auditors` 的成员可读 `orders` 中除 `d:ssn` 外的全部内容；`dave` 可以创建命名空间 `analytics` 及其中的表并管理它们，在其他地方建表则被拒绝。

## 执行策略

服务插件负责管理策略与查询资源。执行策略需要在每个 master 与 region server 中安装 HBase 代理。部署方式与检查内容见 [HBase 代理](/external/hbase-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [HBase 代理](/external/hbase-agent/)
- [插件与服务类型](/develop/plugins/)
