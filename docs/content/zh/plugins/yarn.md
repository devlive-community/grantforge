---
title: Apache Hadoop YARN
description: 安装并配置 YARN 服务插件，连接一个 ResourceManager 或高可用的一组，以 simple 或 Kerberos 登录，查询队列与应用名称，并管理谁可以把哪些应用提交到哪里。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

YARN 插件查询队列及其中的应用名称，并针对 YARN 所决定的事情管理访问策略：向队列提交应用、管理队列以及管理集群。插件 ID 与服务类型均为 `yarn`。服务插件运行在 GrantForge 服务器中；安装在 ResourceManager 中的 [YARN 代理](/external/yarn-agent/)负责执行策略。

仓库的 Testcontainers 集成测试覆盖由经 SHA-512 验证的 Apache 发行包构建的 Hadoop `3.2.4`，以及 `3.3.6`、`3.4.3`、`3.5.0` 的 ResourceManager，还有一个 `3.4.3` 的 Kerberos ResourceManager；这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/yarn` 中包含该插件。在 **平台管理 → 插件** 中确认 `yarn` 已启用；更新插件目录后重新扫描。插件通过 Hadoop 自己的 YARN 客户端与 ResourceManager 通信，基于 Hadoop 的 shaded 客户端 3.5.0，位于插件自己的类加载器中。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 Apache Hadoop YARN（`yarn`）。
2. 输入 ResourceManager 的客户端地址，或高可用集群中每个 ResourceManager 的地址。
3. 选择查询用户的登录方式并填写，测试连接后保存服务。测试会登录并描述集群：有多少个 NodeManager、多少个队列。

设置项沿用 Hadoop 自己的名称，可以直接从 `yarn-site.xml` 复制。查询用户用于让 GrantForge 发现资源，与提交应用的用户相互独立。连接测试成功并不表示提交已经受策略约束。

| 设置 | 用途 |
| --- | --- |
| `yarn.resourcemanager.address` | 必填：ResourceManager，`host` 或 `host:port`，默认端口 `8032`；高可用集群填写多个并以逗号分隔，客户端会在它们之间故障转移 |
| `hadoop.security.authentication` | `simple`（默认）：用户名；`kerberos`：带 keytab 或密码的主体 |
| `username` | 必填的查询用户；Kerberos 下为完整主体，例如 `grantforge@EXAMPLE.COM` |
| `password` | Kerberos：主体的密码（未提供 keytab 时）；加密保存 |
| `keytab` | Kerberos：主体的 keytab 路径，需要在 **GrantForge 服务器** 上可读 |
| `kerberos.kdc` | Kerberos：主体所在 realm 的 KDC，`host[:port]`，以逗号分隔；为空时使用服务器的 `krb5.conf` |
| `yarn.resourcemanager.principal` | Kerberos：ResourceManager 的主体，默认 `yarn/_HOST@` 加查询主体的 realm |
| `hadoop.rpc.protection` | `authentication`（默认）、`integrity` 或 `privacy`，与集群一致 |
| `timeout` | 请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒；YARN 自己的客户端会重试一刻钟 |

服务完全由其设置决定：不会读取 GrantForge 服务器上的 `core-site.xml` 或 `yarn-site.xml`。simple 认证下会拒绝 Kerberos 设置；Kerberos 需要密码或 keytab。拒绝登录的 ResourceManager（例如要求 Kerberos 的）会报告为登录被拒绝。

### Kerberos

使用 `kerberos` 时，GrantForge 通过 Hadoop 自己的登录从 keytab 或用密码登录主体，保留这次登录并用 keytab 续期；GrantForge 服务器上的其他部分无需 Kerberos 设置。在 `kerberos.kdc` 中填写了 KDC 的 realm 会为整个 GrantForge 服务器声明，与 HDFS、Hive、Kafka、HBase 和 Trino 插件的做法相同，因此多个服务可以并行使用不同的 realm。

## 资源与访问类型

| 级别 | 匹配 | 访问类型 |
| --- | --- | --- |
| `queue` | 通配符，区分大小写；从根开始的队列路径，例如 `root.sales.etl` | `submit_app`、`admin_queue` |
| `application` | 通配符，区分大小写；位于队列下；应用的名称 | `submit_app` |
| `cluster` | 唯一的值 `*` | `admin`：YARN 的管理操作，例如刷新队列、节点与节点标签 |

`admin_queue` 隐含 `submit_app`，因为队列的管理员可以向其提交。

与 YARN 一样，队列上的权限覆盖其下的队列：`root.sales` 上的 `submit_app` 可以提交到 `root.sales.etl`。一次检查会在路径上的每个队列判定（从根开始），任一处拒绝都会拒绝它，因此一条策略可以允许 `root.sales`，同时拒绝 `root.sales.adhoc`。在队列之下，应用级别指明授权针对哪些应用：`root.hr` 上带应用 `payroll-*` 的 `submit_app` 只接受名称以 `payroll-` 开头的应用，作用于 `root.hr` 及其下的队列。

策略值中，`*` 匹配任意多个字符，`?` 匹配一个字符。无论集群运行哪种调度器，策略编辑器都会按路径（若输入的是队列自己的名字，则按名字）提示队列，并提示 ResourceManager 仍记得的、策略所列队列中的应用名称。

### 客户端地址

策略项可以只对部分客户端生效：其 **客户端地址** 条件（`ip-range`）接受地址、`10.1.0.0/16` 这样的网段以及主机名，与 Hadoop 自己的主机列表一致。在代理（例如 YARN 的 web proxy 或 router）之后，客户端是代理转发的第一个地址。地址与网段优于主机名，主机名需要对客户端做一次名称查询。YARN 询问管理权限时不带客户端地址，因此该条件在 `cluster` 上永不成立。

## 策略示例

假设集群运行队列 `root.sales.etl`、`root.sales.adhoc` 与 `root.hr`。在同一数据服务中创建以下已启用的访问策略：

| 资源 | 规则 |
| --- | --- |
| `queue=root.sales` | 为组 `analysts` 允许 `submit_app` |
| `queue=root.sales.adhoc` | 为用户 `alice` 拒绝 `submit_app` |
| `queue=root.hr`、`application=payroll-*` | 为用户 `bob` 允许 `submit_app` |
| `queue=root.sales` | 为用户 `carol` 允许 `admin_queue` |
| `queue=root` | 为组 `etl-robots` 允许 `submit_app`，限 `10.0.0.0/8` |
| `cluster=*` | 为组 `yarn-admins` 允许 `admin` |

代理应用新快照后，`analysts` 的成员可以提交到 `root.sales.etl` 与 `root.sales.adhoc`，但 `alice` 只能提交到 `root.sales.etl`；`bob` 只能向 `root.hr` 提交工资类应用；`carol` 可以向 sales 下的队列提交并终止其中的应用；`etl-robots` 的成员可以提交到任何队列，但只能从内网提交；`yarn-admins` 的成员可以刷新队列。

## 执行策略

服务插件负责管理策略与查询资源。执行策略需要在每个 ResourceManager 中安装 YARN 代理。部署方式与检查内容见 [YARN 代理](/external/yarn-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [YARN 代理](/external/yarn-agent/)
- [插件与服务类型](/develop/plugins/)
