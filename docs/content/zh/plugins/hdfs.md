---
title: Apache Hadoop HDFS
description: 安装和配置 HDFS 服务端插件，浏览目录并为文件与目录配置访问策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Hadoop HDFS 插件在 GrantForge 中提供 HDFS 集群连接、路径查询与策略管理。插件 ID 和服务类型均为 `hdfs`。

## 安装

发行包已将插件放在 `plugins/hdfs`。在 **平台管理 → 插件** 中确认 `hdfs` 已启用；更新插件后可重新扫描插件目录。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 HDFS（`hdfs`）。
2. 填写集群 URI 和查询用户。Hadoop 2.x 使用 `webhdfs://namenode:50070`；3.x 可使用 `hdfs://namenode:8020` 或 `webhdfs://namenode:9870`。HTTPS 使用 `swebhdfs://` 和集群实际端口。
3. 设置查询目录并测试连接；测试会检查目录存在且可列出内容，然后保存服务。

| 配置 | 用途 |
| --- | --- |
| `fs.default.name` | 必填集群 URI，不包含子目录、凭据或查询参数；HA 可用 `hdfs://nameservice1`，并补齐附加配置 |
| `username` | 必填查询用户；Kerberos 时填写 principal，如 `grantforge@EXAMPLE.COM` |
| `hadoop.security.authentication` | 默认 `simple`；Kerberos 集群选择 `kerberos` |
| `hadoop.security.authorization` | 是否让 Hadoop 检查权限，默认 `false`；与集群的 core-site.xml 一致 |
| `hadoop.security.auth_to_local` | Kerberos principal 到用户名的映射规则，与集群的 core-site.xml 一致 |
| `password` / `keytab` | Kerberos 密码或 GrantForge 服务器上的 keytab 文件路径 |
| `dfs.namenode.kerberos.principal`、`dfs.datanode.kerberos.principal`、`dfs.secondary.namenode.kerberos.principal` | Kerberos 集群各组件的 principal，如 `nn/_HOST@EXAMPLE.COM`，与集群配置一致 |
| `lookup.path` | 查询和浏览的起始目录，默认 `/`；例如 `/data`，浏览限制在该目录内 |
| `lookup.max.entries` | 完整目录扫描的上限，默认 `10000`，范围 `1..100000` |
| `hadoop.config` | 每行一个 `key=value`，用于 HA 等 Hadoop 配置；覆盖同名连接设置 |
| `hadoop.rpc.protection` | `authentication`、`integrity` 或 `privacy`，需与集群一致 |
| `ssl.client.truststore.location` | GrantForge 服务器上信任库的路径，用于校验 `swebhdfs://` NameNode 的证书；留空则信任服务器 Java 默认信任的证书 |
| `ssl.client.truststore.password` | 信任库密码；信任库有保护时填写，加密保存 |
| `ssl.client.truststore.type` | `jks`（默认）或 `pkcs12` |

Hadoop 2.7.7 启用 NameNode 属性扩展后，普通用户查询根路径 `/` 会触发已确认的上游 `NullPointerException`；请将 `lookup.path` 设为 `/data` 等实际目录，详见 [代理指南](/external/hdfs-agent/)。

Kerberos 还需配置可访问的 KDC、服务器的 `krb5.conf`，以及与集群一致的 `hadoop.security.auth_to_local` 和服务 principal。查询账号用于获取目录元数据。已在 Hadoop 3.5.0 的 Kerberos 集群上实测：经 RPC（keytab 或密码）和 swebhdfs（服务自己的信任库与 SPNEGO）查询、浏览路径，凭据错误、缺少信任库和 simple 客户端都会失败；其他版本尚未验证。

使用 Kerberos 时，GrantForge 会在多次查询之间复用同一次登录，不必每次都访问 KDC：keytab 登录在票据临近过期时由 Hadoop 自动续期；密码登录在票据剩余寿命不足五分之一（至少一分钟）时重新登录；更换密码或 keytab 文件更新后会重新登录。每个服务使用自己的信任库，服务器类路径上的 `ssl-client.xml` 不会覆盖它。

保存时校验配置：`hadoop.config` 中的 `fs.defaultFS` 与 `fs.default.name` 是别名，只能设置其中一个；集群 URI 不能包含凭据、路径、查询或片段；选择 `kerberos` 时必须提供 `password` 或 `keytab`；`lookup.path` 必须是绝对路径且不含 `..`；`lookup.max.entries` 需在 `1..100000` 之间。附加配置覆盖同名连接设置，校验与登录均使用覆盖后的值。

## 浏览路径

在 **数据权限 → 策略** 中选择服务，使用 `path` 旁的 **浏览** 按钮。

- 逐级打开目录，使用路径导航或返回上级，并按需加载后续页面。
- 查看文件与目录标记、所有者、组、权限、文件大小和修改时间。
- 多选文件或目录，或选择当前目录，加入策略的路径列表；也可继续输入路径获取候选。

RPC 和支持批量枚举的 WebHDFS 使用原生分页；旧端点需在扫描上限内读取目录，超限会报错。权限、认证或连接失败会显示原因，并可重试。

输入中可省略开头的 `/`，允许重复的 `/` 与 `.`，拒绝 `..` 和 `lookup.path` 范围之外的绝对路径；不存在的目录返回空候选。浏览不替代 HDFS 自身的访问控制，符号链接和 ViewFS 挂载仍遵循集群配置。

## 让策略生效

资源只有一级 `path`，访问类型为 `read`、`write`、`execute`；路径策略支持递归和排除。

服务端插件负责管理与查询。实际约束数据访问还需部署与 Hadoop 版本匹配的 NameNode 代理；用户必须同时满足 HDFS 原生权限与 GrantForge 策略。编号代理覆盖 2.7、2.10、3.2、3.3、3.4、3.5，具体已验证组合、认证与 HA 范围及超级用户限制见代理指南。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Apache Hadoop HDFS NameNode 代理](/external/hdfs-agent/)
- [插件与服务类型开发](/develop/plugins/)
