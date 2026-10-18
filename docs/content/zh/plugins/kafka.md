---
title: Apache Kafka
description: 安装和配置 Kafka 服务插件，通过明文或 TLS 以 PLAIN、SCRAM 或 Kerberos 登录，查询主题、消费组、事务 ID 与用户，并以 Kafka 自己的概念管理访问策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Kafka 插件查询主题、消费组、事务 ID、SCRAM 用户与委托令牌，并以 Kafka 自己的概念管理访问策略：资源层级就是 Kafka 的资源类型，访问类型就是它的 ACL 操作。插件 ID 与服务类型均为 `kafka`。服务插件运行在 GrantForge 服务器中；安装在 broker 与 controller 中的 [Kafka 代理](/external/kafka-agent/)负责执行策略。

仓库的 Testcontainers 集成测试覆盖 Kafka `3.7.2`、`3.9.2`、`4.1.2` 与 `4.3.1`，使用 SASL PLAIN、SCRAM 登录与 Kafka 自带的授权器，并在 `4.3.1` 上覆盖 Kerberos（keytab 与密码）和 TLS；这并不代表对其他所有版本的认证。

## 安装

发行包在 `plugins/kafka` 中包含该插件。请在 **平台管理 → 插件** 中确认 `kafka` 已启用；更新插件目录后请重新扫描。插件在独立的类加载器中通过 Kafka 自己的 Admin 客户端（`kafka-clients` 4.3.1）连接 broker。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，新增服务并选择 Apache Kafka（`kafka`）。
2. 填写引导服务器，以及 broker 监听器的安全方式。
3. 填写查询用户，测试连接后保存服务。测试会登录并描述集群：集群 ID 与 broker 数量。

配置项沿用 Kafka 客户端自己的名称，可以直接从客户端配置中复制。查询用户只用于让 GrantForge 发现资源，与生产和消费的客户端无关。连接测试成功并不表示请求已经受策略约束。

| 配置项 | 用途 |
| --- | --- |
| `bootstrap.servers` | 必填，起始 broker，`主机:端口` 以逗号分隔，例如 `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT`（默认）、`SSL`、`SASL_PLAINTEXT` 或 `SASL_SSL` |
| `sasl.mechanism` | 仅限 SASL 协议：使用密码的 `PLAIN`、`SCRAM-SHA-256` 或 `SCRAM-SHA-512`，Kerberos 使用 `GSSAPI` |
| `username` | SASL：查询用户；Kerberos 下为完整主体，例如 `grantforge@EXAMPLE.COM` |
| `password` | 用户的密码；未提供 keytab 时为主体的 Kerberos 密码；加密保存 |
| `keytab` | Kerberos：主体 keytab 的路径，需在 **GrantForge 服务器** 上可读 |
| `kerberos.kdc` | Kerberos：主体所在领域的 KDC，`主机[:端口]` 以逗号分隔；留空则使用服务器的 `krb5.conf` |
| `sasl.kerberos.service.name` | Kerberos：broker 主体的第一部分，默认 `kafka` |
| `ssl.truststore.location`、`ssl.truststore.password`、`ssl.truststore.type` | TLS：验证 broker 的信任库（`JKS`、`PKCS12` 或 `PEM`）；留空则信任 Java 信任的证书 |
| `ssl.keystore.location`、`ssl.keystore.password`、`ssl.key.password` | TLS：GrantForge 的证书，供要求客户端证书的 broker 使用 |
| `ssl.verify.hostname` | TLS：broker 证书是否必须包含所连接的主机名，默认 `true` |
| `request.timeout.ms` | 请求超时，默认 `30000`，范围 `1..600000` |

`PLAINTEXT` 与 `SSL` 不接受 SASL 配置，`PLAINTEXT` 与 `SASL_PLAINTEXT` 不接受 TLS 配置。`PLAIN` 与 SCRAM 需要密码；`GSSAPI` 需要密码或 keytab。

### Kerberos

使用 `GSSAPI` 时，GrantForge 通过 Kafka 自己的 Kerberos 登录让主体登录，凭 keytab，或凭 GrantForge 亲自交给登录过程的密码；GrantForge 服务器上无需其他 Kerberos 配置。在 `kerberos.kdc` 中填写了 KDC 的领域会对整个 GrantForge 服务器声明，方式与 HDFS、Hive 插件相同，因此多个服务可以同时使用不同的领域。

### 查询用户能看到什么

Kafka 只向用户展示其可描述的内容，因此查询用户需要对要提示的主题、消费组与事务 ID 拥有 `describe`，查询 SCRAM 用户则需要集群上的 `describe`。Kafka 会略去无权描述的主题与消费组，所以提示为空，而不是报错。输入以 `_` 开头时才会提示内部主题，例如 `__consumer_offsets`。未启用委托令牌或 SCRAM 的集群不提示相应内容。

[Kafka 代理](/external/kafka-agent/)执行策略后，查询用户能描述什么同样由策略决定：为它配置允许 `describe` 的策略，或在 broker 的 `super.users` 中列出它。

## 资源与访问类型

| 层级 | Kafka 资源 | 访问类型 |
| --- | --- | --- |
| `topic` | 主题 | `read`、`write`、`create`、`delete`、`alter`、`describe`、`describe_configs`、`alter_configs`、`all` |
| `group` | 消费组 | `read`、`delete`、`describe`、`all` |
| `transactional_id` | 事务 ID | `write`、`describe`、`all` |
| `cluster` | 集群，名称固定为 `kafka-cluster` | `create`、`alter`、`describe`、`cluster_action`、`describe_configs`、`alter_configs`、`idempotent_write`、`all` |
| `delegation_token` | 委托令牌，按令牌 ID | `describe`、`all` |
| `user` | 创建或描述其委托令牌的用户 | `create_tokens`、`describe_tokens`、`all` |

每个层级各自独立，一条策略指定一个层级。值区分大小写：`*` 匹配任意数量的字符，`?` 匹配一个字符。访问类型之间的蕴含与 Kafka 的 ACL 操作相同：`read`、`write`、`delete` 与 `alter` 蕴含 `describe`，`alter_configs` 蕴含 `describe_configs`，`all` 蕴含其他全部类型。

蕴含同样作用于拒绝：拒绝主题上 `write` 的策略也会拒绝 `write` 所蕴含的 `describe`，因此用户将看不到该主题。Kafka 自己的 ACL 只对允许的操作应用蕴含。

策略编辑器按前缀从集群提示名称；`cluster` 提示 `kafka-cluster`。

## 策略示例

假设用户 `alice` 的生产者写入名称以 `orders` 开头的主题，组 `analysts` 以消费组 `billing` 读取这些主题。在同一数据服务中创建三条已启用的访问策略：

| 资源 | 规则 |
| --- | --- |
| `topic=orders*` | 允许用户 `alice` 的 `write` |
| `topic=orders*` | 允许组 `analysts` 的 `read` |
| `group=billing` | 允许组 `analysts` 的 `read` |

代理应用新快照后，`alice` 可以向 `orders-eu` 生产消息，幂等生产者也可以，且无需集群上的 `idempotent_write`；`analysts` 的成员以 `billing` 消费；其他主题与消费组对两者均拒绝。`kafka-acls --list` 会把这三条策略显示为前缀 ACL 与字面 ACL。

## 执行策略

服务插件负责管理策略与查询资源。执行策略需要在每个 broker 与 controller 中安装 Kafka 代理。部署方式与检查内容见 [Kafka 代理](/external/kafka-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Kafka 代理](/external/kafka-agent/)
- [插件与服务类型](/develop/plugins/)
