---
title: Elasticsearch
description: 安装并配置 Elasticsearch 服务插件，连接一个或多个节点，使用密码、API 密钥或 TLS 客户端证书登录，查询索引、数据流、字段与集群，并用 Elasticsearch 自己的权限管理谁能读取、写入与管理什么，细化到字段与文档。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Elasticsearch 插件查询索引、数据流、字段与集群，并按 Elasticsearch 自己的方式管理访问策略：读取字段，写入、删除文档，创建、删除与管理索引，以及集群自身的操作，并支持索引的行过滤。插件 ID 与服务类型均为 `elasticsearch`。服务插件运行在 GrantForge 服务器中；运行在集群旁的 [Elasticsearch 代理](/external/elasticsearch-agent/)借助 Elasticsearch 自己的角色、字段级安全与文档级安全执行策略。

仓库的 Testcontainers 集成测试覆盖开启安全功能的 Elasticsearch `8.19`、`9.3`、`9.4` 与 `9.5`；这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/elasticsearch` 中包含此插件。在 **平台管理 → 插件** 中确认 `elasticsearch` 已启用；更新插件目录后重新扫描。插件通过 Elasticsearch 的低层 REST 客户端与节点通信，位于插件自己的类加载器中。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 Elasticsearch（`elasticsearch`）。
2. 以 URL 输入一个节点或多个节点。
3. 选择查询用户的登录方式，填写凭据，测试连接后保存服务。测试会登录并描述集群：Elasticsearch 版本与集群名称、许可证及其是否包含文档级与字段级安全、登录所用的用户，以及集群有多少个索引与数据流。没有开启安全功能的集群测试失败，因为代理借助 Elasticsearch 的角色执行策略。

查询用户用于让 GrantForge 发现资源，与读写数据的用户相互独立。连接测试成功并不表示访问已受策略约束。

| 设置 | 用途 |
| --- | --- |
| `elasticsearch.hosts` | 必填：以 URL 表示的节点，例如 `https://es1.example.com:9200`；多个以逗号分隔，某个无法访问时依次尝试下一个。不写协议时为 `https`，端口默认为 `9200`；同一服务的节点要么都是 `http`，要么都是 `https` |
| `authentication` | `password`（默认）、`api_key`，或 `certificate`：PKI realm 映射为某个用户的 TLS 客户端证书 |
| `username`、`password` | password：查询用户及其密码，密码加密保存 |
| `apiKey` | api_key：编码后的 API 密钥，即 Elasticsearch 创建它时返回的值，加密保存 |
| `ssl.verificationMode` | 与 Kibana 的命名一致：`full`（默认）校验节点的证书与名称，`certificate` 只校验证书，`none` 不校验 |
| `ssl.certificateAuthorities` | **GrantForge 服务器**上用来校验节点的 PEM CA 证书路径，例如 Elasticsearch 的 `http_ca.crt`；为空时使用 JVM 的 |
| `ssl.certificate`、`ssl.key` | certificate：GrantForge 服务器上 PEM 客户端证书及其私钥的路径，私钥可为 PKCS #8 或较早的 OpenSSL 格式；需要 `https` 节点 |
| `ssl.keyPassphrase` | certificate：加密私钥的口令，加密保存 |
| `timeout` | 连接与请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |

一种登录方式的设置在其他方式下会被拒绝，因此服务会明确说明如何登录。被拒绝的登录、已吊销的 API 密钥、缺少的权限、无法访问的节点以及无法校验证书的节点，都会如实报告。

### 查询用户能看到什么

查询用户需要能读取它列出的内容：索引与数据流来自 Elasticsearch 的 resolve API，字段来自字段能力（field capabilities），集群名称来自集群描述。对索引拥有 `read` 与 `view_index_metadata` 的角色即可满足前两项；没有 `monitor` 集群权限的用户也能通过连接测试，只是测试不会写出版本与许可证。名称以点开头的索引与数据流（隐藏与系统索引）只在输入以点开头时列出；数据流的后备索引以其数据流列出。字段指文档中的字段：Elasticsearch 自己的元数据字段、对象，以及再次索引某个字段的多字段（例如 `name.keyword`）不会列出，代理会把它们与原字段一同授予。

## 资源与访问类型

| 层级 | 匹配方式 | 访问类型 |
| --- | --- | --- |
| `index` | 通配符，区分大小写；索引或数据流的名称 | `index`、`delete`、`create_index`、`delete_index`、`view_index_metadata`、`monitor`、`manage` |
| `field` | 通配符，区分大小写；位于索引之下，字段路径如 `address.city` | `read` |
| `cluster` | 通配符，区分大小写；集群名称 | `cluster_monitor`、`create_snapshot`、`manage_slm`、`manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` |

每种访问类型都是同名的 Elasticsearch 权限，只有 `cluster_monitor` 对应集群权限 `monitor`：

| 访问类型 | 允许的操作 |
| --- | --- |
| `read` | 搜索、获取、计数、多重搜索与滚动查询 |
| `index`、`delete` | 写入与更新文档（包括 bulk）；删除文档 |
| `create_index`、`delete_index` | 创建索引，包括首次写入时自动创建；删除索引与数据流 |
| `view_index_metadata`、`monitor` | 读取映射、设置与别名；读取统计与恢复信息 |
| `manage` | 索引的所有管理操作；隐含 `create_index`、`delete_index`、`view_index_metadata` 与 `monitor` |
| `cluster_monitor` | 读取集群的健康、状态与统计 |
| `create_snapshot`、`manage_slm` | 创建快照；管理快照生命周期策略，隐含 `create_snapshot` |
| `manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` | 管理索引模板、摄取管道与索引生命周期策略 |

读取按字段决定，正如字段级安全展示文档字段那样：`read` 策略写明字段，`*` 表示所有字段。因此一条策略可以允许某个索引，同时拒绝其中一个字段，用户仍能读取其余所有字段。策略值中，`*` 匹配任意多个字符，`?` 匹配一个字符。策略编辑器会建议索引与数据流、策略所写索引的字段，以及集群。

### 行过滤

索引的行过滤策略只让用户看到查询匹配的文档。查询采用文档级安全所接受的 Elasticsearch 查询 DSL，例如 `{"term": {"region": "EU"}}`，也可以是模板，例如使用 `{{_user.username}}`。与其他服务一样，用户得到第一条适用于它的策略的过滤条件。行过滤只能检查用户可读的字段：字段级安全同样会对查询隐藏其他字段。

Elasticsearch 不会对值做脱敏，因此该服务类型不提供脱敏。

### 许可证

Elasticsearch 从 Platinum 许可证起提供文档级与字段级安全。低于该级别时，只允许用户读取索引部分字段或部分文档的策略无法执行，因此代理不会授予该用户对该索引的读取，而不是给出超出策略的权限，并说明原因。允许读取整个索引、写入与管理的策略在任何许可证下都有效。

## 策略示例

假设集群持有 Platinum 许可证，并有索引 `logs-2026.10.26` 与 `hr`。在同一个数据服务中创建以下启用的策略：

| 资源 | 规则 |
| --- | --- |
| `index=logs-*`、`field=*` | 允许组 `analysts` `read` |
| `index=logs-*`、`field=client_ip` | 拒绝组 `analysts` `read` |
| `index=logs-*` | 允许用户 `logstash` `index` 与 `create_index` |
| `index=hr`、`field=*` | 允许组 `hr` `read` |
| `index=hr`（行过滤） | 只让用户 `emea-hr` 看到 `{"term": {"region": "EMEA"}}` 的文档 |
| `cluster=*` | 允许组 `ops` `cluster_monitor` |

代理应用新快照后，`analysts` 的成员能读取除客户端地址外的所有日志字段；`logstash` 能立即写入明天的 `logs-2026.10.27`，并以第一条文档创建它；`hr` 的成员能读取员工信息；`emea-hr` 只看到本区域的员工；`ops` 的成员能读取集群的健康与统计。

## 执行策略

服务插件管理策略并查询资源；执行策略需要为每个集群部署一个 Elasticsearch 代理。部署与检查见 [Elasticsearch 代理](/external/elasticsearch-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Elasticsearch 代理](/external/elasticsearch-agent/)
- [插件与服务类型](/develop/plugins/)
