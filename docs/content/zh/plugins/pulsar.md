---
title: Apache Pulsar
description: 安装并配置 Pulsar 服务插件，连接一个或多个 broker，以 token、密码、OAuth 2.0 或客户端证书登录，查询租户、命名空间、主题与订阅，并管理谁可以在哪里生产、消费与管理。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Pulsar 插件查询租户、命名空间、主题、订阅与集群，并针对 Pulsar 所决定的事情管理访问策略：生产、消费、Functions、Sources、Sinks 与 Packages，以及管理主题、命名空间、租户与集群。插件 ID 与服务类型均为 `pulsar`。服务插件运行在 GrantForge 服务器中；安装在 broker 中的 [Pulsar 代理](/external/pulsar-agent/)负责执行策略。

仓库的 Testcontainers 集成测试覆盖 Pulsar `3.0.17`、`3.3.9`、`4.0.14` 与 `4.2.5` 的 broker；这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/pulsar` 中包含此插件。在 **平台管理 → 插件** 中确认 `pulsar` 已启用；更新插件目录后重新扫描。插件通过 Pulsar 自己的 admin 客户端 4.2.5 与 broker 的 Web 服务通信，位于插件自己的类加载器中。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 Apache Pulsar（`pulsar`）。
2. 输入一个 broker 或多个 broker 的 Web 服务地址。
3. 选择查询角色的登录方式并填写凭据，测试连接后保存服务。测试会登录并描述集群：有哪些集群、多少个租户。

设置沿用 Pulsar 自己 `client.conf` 中的名称，可以直接从中复制。查询角色用于让 GrantForge 发现资源，与生产和消费的角色相互独立。连接测试成功并不表示访问已受策略约束。

| 设置 | 用途 |
| --- | --- |
| `webServiceUrl` | 必填：broker 的 Web 服务，`http://host:port` 或 `https://host:port`，默认端口 `8080` 或 `8443`；多个 broker 以逗号分隔，例如 `http://broker1:8080,broker2:8080` |
| `authentication` | `none`（默认）、`token`、`basic`、`oauth2` 或 `tls` |
| `token` | token：查询角色的 token；加密存储 |
| `username`、`password` | basic：查询角色的用户名与密码；密码加密存储 |
| `issuerUrl`、`privateKey`、`audience`、`scope` | oauth2：授权服务器、**GrantForge 服务器** 上含 `client_id` 与 `client_secret` 的 JSON 凭据文件路径，以及 broker 要求时的 audience 与 scope |
| `tlsCertificateFilePath`、`tlsKeyFilePath` | tls：**GrantForge 服务器** 上客户端证书及其私钥的路径，PEM |
| `tlsTrustCertsFilePath` | https：校验 broker 证书所用的 CA 证书，PEM；为空时使用 JVM 自带的 |
| `tlsEnableHostnameVerification` | https：broker 证书是否必须包含其主机名，默认 `true` |
| `timeout` | 请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |

一种登录方式的设置在另一种方式下会被拒绝，因此服务明确写出它如何登录。登录被拒、角色无权查看，以及 broker 无法访问（包括其前面的代理替它应答时）都会如实报告。

### 查询角色能看到什么

Pulsar 只允许超级用户列出集群与租户。请把查询角色写入 broker 的 `superUserRoles`，或在安装代理后为它授予集群上的 `admin`，这同样会让它成为超级用户。查询角色无权查看的命名空间、主题与订阅不会出现在提示中。

## 资源与访问类型

| 层级 | 匹配 | 访问类型 |
| --- | --- | --- |
| `tenant` | 通配符，区分大小写 | 除 `admin` 外全部 |
| `namespace` | 通配符，区分大小写；位于租户之下；命名空间自己的名称，例如 `orders` | `produce`、`consume`、`functions`、`sources`、`sinks`、`packages`、`admin_topic`、`admin_namespace` |
| `topic` | 通配符，区分大小写；位于命名空间之下；主题自己的名称，例如 `payments` | `produce`、`consume`、`admin_topic` |
| `subscription` | 通配符，区分大小写；位于主题之下；订阅的名称 | `consume` |
| `cluster` | 通配符，区分大小写；集群的名称，例如 `standalone` | `admin`：Pulsar 的超级用户权限 |

| 访问类型 | 隐含 |
| --- | --- |
| `produce`、`consume`、`functions`、`sources`、`sinks`、`packages` | — |
| `admin_topic`：终止、压缩、卸载到分层存储、unload 与截断主题，修改其策略 | `produce`、`consume` |
| `admin_namespace`：创建与删除主题，修改命名空间的策略 | `admin_topic` |
| `admin_tenant`：创建、列出与删除命名空间 | `admin_namespace`、`functions`、`sources`、`sinks`、`packages` |
| `admin`：Pulsar 超级用户可做的一切 | `admin_tenant` |

与 Pulsar 自己的授权一样，租户或命名空间上的权限覆盖其中的内容：租户 `sales` 的命名空间 `orders` 上的 `produce` 可以向其中每个主题生产。一次检查会依次在租户、命名空间、主题与订阅上判定，任一处拒绝都会拒绝它；因此一条策略可以允许一个命名空间，同时拒绝其中的某个主题。在主题之下，订阅层级指明角色可以通过哪些订阅消费：主题 `*`、订阅 `billing-*` 上的 `consume` 允许角色消费该命名空间的每个主题，但只能通过名称以 `billing-` 开头的订阅。

主题按自己的名称判定，不含 domain，因此同名的持久化与非持久化主题共享策略；分区主题的分区按分区主题本身判定。策略值中，`*` 匹配任意多个字符，`?` 匹配一个字符。策略编辑器会提示集群、租户，以及策略所列范围内的命名空间、主题与订阅。

### 客户端地址

策略项可以只对部分客户端生效：其 **客户端地址** 条件（`ip-range`）接受地址以及 `10.1.0.0/16` 或 `2001:db8::/32` 这样的网段。不会解析主机名。在代理之后，客户端就是代理本身，除非代理转发客户端自己的地址：生产者与消费者使用 HAProxy 协议，Web 服务在 broker 信任时使用 `X-Forwarded-For`。

## 策略示例

假设租户 `sales` 下有命名空间 `orders` 与 `returns`。在同一数据服务中创建以下已启用的访问策略：

| 资源 | 规则 |
| --- | --- |
| `tenant=sales`、`namespace=orders` | 为组 `analysts` 允许 `produce` |
| `tenant=sales`、`namespace=orders`、`topic=audit` | 为用户 `alice` 拒绝 `produce` |
| `tenant=sales`、`namespace=orders`、`topic=*`、`subscription=billing-*` | 为用户 `alice` 允许 `consume` |
| `tenant=sales` | 为用户 `bob` 允许 `admin_tenant` |
| `tenant=public` | 为组 `ingest` 允许 `produce`，限 `10.0.0.0/8` |
| `cluster=standalone` | 为组 `pulsar-admins` 允许 `admin` |

代理应用新快照后，`analysts` 的成员可以向 `sales/orders` 的每个主题生产，`alice` 除 `audit` 之外都可以；`alice` 在那里只能通过 `billing-` 开头的订阅消费；`bob` 可以在 `sales` 中创建并管理命名空间与主题；`ingest` 的成员只能从内网向 `public` 租户生产；`pulsar-admins` 的成员是 Pulsar 的超级用户。

## 执行策略

服务插件负责管理策略与查询资源。执行策略需要在每个 broker 中安装 Pulsar 代理。部署方式与检查内容见 [Pulsar 代理](/external/pulsar-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Pulsar 代理](/external/pulsar-agent/)
- [插件与服务类型](/develop/plugins/)
