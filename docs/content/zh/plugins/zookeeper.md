---
title: Apache ZooKeeper
description: 安装和配置 ZooKeeper 服务插件，浏览 znode，并为 znode 路径管理访问策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache ZooKeeper 插件为 GrantForge 提供 ensemble 连接、znode 路径查询和策略管理。插件 ID 和服务类型都是 `zookeeper`。

## 安装

发行包已在 `plugins/zookeeper` 中包含该插件。在 **平台管理 → 插件** 中确认 `zookeeper` 已启用；更新插件目录后重新扫描。插件在自己的类加载器中使用 ZooKeeper 3.9 客户端，不依赖服务器的库。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 ZooKeeper（`zookeeper`）。
2. 填写连接串，即 ensemble 的服务器列表，可带 chroot，例如 `zk1:2181,zk2:2181,zk3:2181/kafka`。客户端会在服务器之间自动切换。
3. 选择认证方式并设置查询 znode，测试连接（确认 znode 存在且可列出子节点）后保存服务。

| 配置 | 用途 |
| --- | --- |
| `zookeeper.connect` | 必填连接串：`host[:port]` 用逗号分隔，可带 `/chroot`；chroot 不能以 `/` 结尾 |
| `authentication` | `none`（默认）、`digest` 或 `kerberos` |
| `username` | digest：ACL 中的 digest 用户（不能包含冒号）；Kerberos：principal，如 `grantforge@EXAMPLE.COM` |
| `password` | digest 用户的密码，加密保存 |
| `keytab` | Kerberos：GrantForge 服务器上 principal 的 keytab 路径 |
| `zookeeper.sasl.client.username` | Kerberos：服务器 principal 的第一段，默认 `zookeeper`，即 `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos：服务器 principal 不是上述形式时填写完整 principal，如 `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | 是否连接服务器的 TLS 客户端端口，默认 `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | 校验服务器证书的信任库；留空则信任服务器 Java 所信任的证书；类型为 `jks`、`pkcs12` 或 `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | 服务器要求客户端证书时使用的密钥库 |
| `session.timeout.ms` | 会话超时，默认 `15000`，范围 `2000..120000` |
| `connection.timeout.ms` | 仅用于初始连接和认证的等待上限，默认 `10000` ms，范围 `1000..60000`；不限制连接后的同步请求 |
| `request.timeout.ms` | 单次同步请求的等待上限，默认 `8000` ms，范围 `1000..60000` |
| `lookup.path` | 查询和浏览的起始 znode（chroot 之下），默认 `/`，例如 `/brokers`；浏览限制在该 znode 内 |
| `lookup.max.entries` | 单个 znode 一次读取的子节点上限，默认 `10000`，范围 `1..100000` |

## 认证

- **none**：不登录，只能看到 ACL 对 `world:anyone` 开放的 znode。
- **digest**：以 `username:password` 登录，与 ZooKeeper ACL 中的 `digest` 身份对应。密码错误不会被拒绝登录，而是成为另一个身份，读取受保护 znode 时显示无权访问。
- **kerberos**：使用 keytab 通过 SASL 登录，需要服务器的 `krb5.conf` 能找到 KDC。每个 principal 和 keytab 都有自己的 JAAS 登录段，与进程原有的配置并存，因此一个 GrantForge 可以以不同 principal 连接多个 ensemble。暂不支持仅使用密码的 Kerberos 登录。

保存时会检查配置：连接串格式、digest 需要用户和密码、Kerberos 需要 principal 和 keytab、keytab 与服务器 principal 只用于 Kerberos，以及各个超时和上限的范围。

关闭 TLS 时，信任库与密钥库的路径和密码必须留空；填写库密码时，必须同时提供对应的库路径。两种库的类型仅支持 `jks`、`pkcs12` 和 `pem`。

TLS 库密码的前后空格会原样保留。服务配置决定本次连接的 TLS 模式及使用的证书库，覆盖 JVM 中已有的 ZooKeeper TLS 选择与库配置；服务器证书和主机名仍会校验。

## 浏览 znode

在 **数据权限 → 策略** 中选择服务，使用 `path` 旁的 **浏览**：

- 按名称排序分页列出子节点；有子节点的 znode 可继续打开。
- 显示每个 znode 的 ACL（`scheme:id:cdrwa`，与 ZooKeeper 自带工具一致）、数据大小和修改时间。
- 当前用户无权读取 ACL 的 znode 仍会列出名称，打开时显示无权访问的原因。

分页期间即使 znode 被删除，游标仍按最后已扫描的子节点名称推进，不依赖该子节点继续存在。子节点列表不是快照；并发新增或删除遵循 ZooKeeper 的语义，可能改变后续页面的内容。

输入路径时会提示候选；不存在的父节点返回空候选，超出 `lookup.path` 的路径会被拒绝。子节点超过 `lookup.max.entries` 的 znode 会报错，而不会只显示部分结果。

## 让策略生效

资源只有一级 `path`，访问类型与 ZooKeeper 的 ACL 权限一致：`read`、`write`、`create`、`delete`、`admin`；路径策略支持递归和排除。

服务端插件负责管理、查询和浏览。在 ZooKeeper 服务器中执行这些策略的代理尚未提供；在此之前，访问仍由 ZooKeeper 自身的 ACL 决定。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [插件与服务类型开发](/develop/plugins/)
