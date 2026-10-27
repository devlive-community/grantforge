---
title: OpenSearch
description: 安装并配置 OpenSearch 服务插件，连接一个或多个节点，以密码或 TLS 客户端证书登录，查询索引、数据流、字段与集群，并管理谁可以读取、写入与管理什么，细到字段、脱敏的值与文档。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

OpenSearch 插件查询索引、数据流、字段与集群，并按 OpenSearch 自己的方式管理访问策略：读取字段，写入、删除、创建与管理索引，以及集群自身的操作，并支持字段脱敏与索引的行过滤。插件 ID 与服务类型均为 `opensearch`。服务插件运行在 GrantForge 服务器中；运行在集群旁的 [OpenSearch 代理](/external/opensearch-agent/)借助安全插件自己的角色、字段级安全、脱敏字段与文档级安全执行策略。

仓库的 Testcontainers 集成测试覆盖带安全插件的 OpenSearch `2.19`、`3.3`、`3.6` 与 `3.9`；这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/opensearch` 中包含此插件。在 **平台管理 → 插件** 中确认 `opensearch` 已启用；更新插件目录后重新扫描。插件通过 OpenSearch 官方的低层 REST 客户端与节点通信，位于插件自己的类加载器中。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 OpenSearch（`opensearch`）。
2. 以 URL 输入一个节点或多个节点。
3. 选择查询用户的登录方式，填写凭据，测试连接后保存服务。测试会登录并描述集群：OpenSearch 版本与集群名称、登录所用的用户，以及集群有多少个索引与数据流。没有安全插件的集群测试失败，因为代理借助安全插件执行策略。

查询用户用于让 GrantForge 发现资源，与读写数据的用户相互独立。连接测试成功并不表示访问已受策略约束。

| 设置 | 用途 |
| --- | --- |
| `opensearch.hosts` | 必填：以 URL 表示的节点，例如 `https://opensearch1.example.com:9200`；多个以逗号分隔，某个无法访问时依次尝试下一个。不写协议时为 `https`，端口默认为 `9200`；同一服务的节点要么都是 `http`，要么都是 `https` |
| `authentication` | `password`（默认），或 `certificate`：安全插件映射为某个用户的 TLS 客户端证书 |
| `username`、`password` | password：查询用户及其密码，密码加密保存 |
| `ssl.verificationMode` | 与 OpenSearch Dashboards 的命名一致：`full`（默认）校验节点的证书与名称，`certificate` 只校验证书，`none` 不校验 |
| `ssl.certificateAuthorities` | **GrantForge 服务器**上用来校验节点的 PEM CA 证书路径；为空时使用 JVM 的 |
| `ssl.certificate`、`ssl.key` | certificate：GrantForge 服务器上 PEM 客户端证书及其私钥的路径，私钥可为 PKCS #8 或较早的 OpenSSL 格式；需要 `https` 节点 |
| `ssl.keyPassphrase` | certificate：加密私钥的口令，加密保存 |
| `timeout` | 连接与请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |

一种登录方式的设置在另一种方式下会被拒绝，因此服务会明确说明如何登录。被拒绝的登录、缺少的权限、无法访问的节点以及无法校验证书的节点，都会如实报告。

### 查询用户能看到什么

查询用户需要能读取它要列出的内容：索引与数据流来自 OpenSearch 的 resolve API，字段来自字段能力（field capabilities），集群名称来自集群的描述。内置的 `readall` 角色给出前两项；没有 `cluster:monitor/main` 的用户仍能通过连接测试，只是测试结果不写版本。名称以点开头的索引与数据流，即隐藏与系统索引，只在输入以点开头时列出；数据流的后备索引以其数据流列出。字段是文档的字段：OpenSearch 自己的元数据字段、对象以及再次索引同一字段的多字段（例如 `name.keyword`）不会列出，因为字段级安全会随字段一同显示或隐藏它们。

## 资源与访问类型

| 层级 | 匹配 | 访问类型 |
| --- | --- | --- |
| `index` | 通配符，区分大小写；索引或数据流的名称 | `index`、`delete`、`create_index`、`manage_aliases`、`monitor`、`manage` |
| `field` | 通配符，区分大小写；位于索引之下，字段的路径，例如 `address.city` | `read` |
| `cluster` | 通配符，区分大小写；集群的名称 | `cluster_monitor`、`manage_snapshots`、`manage_templates`、`manage_pipelines` |

| 访问类型 | 安全插件的动作组 |
| --- | --- |
| `read` | `read`：搜索、获取、计数等 |
| `index` | `index`：写入与更新文档，包括通过 bulk |
| `delete` | `delete`：删除文档 |
| `create_index` | `create_index`：创建索引，包括首次写入时自动创建 |
| `manage_aliases`、`monitor` | `manage_aliases`、`indices_monitor` |
| `manage` | `manage`：索引的所有管理操作，包括删除索引；它隐含 `create_index`、`manage_aliases` 与 `monitor` |
| `cluster_monitor`、`manage_snapshots` | `cluster_monitor`、`manage_snapshots` |
| `manage_templates`、`manage_pipelines` | `cluster_manage_index_templates`、`cluster_manage_pipelines` |

读取按字段决定，与字段级安全显示文档字段的方式一致：`read` 策略要写字段，`*` 表示所有字段。因此策略可以允许一个索引而拒绝其中一个字段，用户能读取其余所有字段。策略值中 `*` 匹配任意字符序列，`?` 匹配一个字符。策略编辑器会提示索引与数据流、策略所写索引的字段，以及集群。

在 OpenSearch 中使用策略允许的内容还需要更多权限：读取需要多重搜索、多重获取与滚动查询，写入需要 bulk。它们不是独立的访问类型，代理会随之授予集群的组合操作。

### 脱敏

字段的脱敏策略让用户看到脱敏后的值，与安全插件的脱敏字段一致，对文档与聚合都生效：

| 脱敏方式 | 用户看到的内容 |
| --- | --- |
| 遮盖（Redact） | 大写字母显示为 `X`，小写字母为 `x`，数字为 `n` |
| 部分遮盖：显示后 4 位 / 前 4 位 | 其余每个字符显示为 `x` |
| 哈希（SHA-256） | 值的 SHA-256 哈希 |
| 带集群盐值的哈希（BLAKE2b） | 安全插件自己的哈希，以集群的盐值作为密钥，相同的值仍然相同，但无法反查 |
| 自定义 | 一种哈希算法，例如 `SHA-512`，或正则表达式及其替换内容，例如 `/[0-9]/::*` |
| 不脱敏 | 原值 |

### 行过滤

索引的行过滤策略只让用户看到某个查询匹配的文档。查询是文档级安全所接受的 OpenSearch 查询 DSL，例如 `{"term": {"region": "EU"}}`，也可以使用安全插件的替换变量，例如 `${user.name}`。与其他服务一样，用户看到的是第一条适用于他的策略的过滤。行过滤只能检验用户可以读取的字段：字段级安全对查询同样隐藏其他字段。

## 策略示例

假设集群有索引 `logs-2026.10.26` 与 `hr`。在同一个数据服务中创建以下已启用的策略：

| 资源 | 规则 |
| --- | --- |
| `index=logs-*`、`field=*` | 允许组 `analysts` `read` |
| `index=logs-*`、`field=client_ip` | 拒绝组 `analysts` `read` |
| `index=logs-*` | 允许用户 `logstash` `index` 与 `create_index` |
| `index=hr`、`field=*` | 允许组 `hr` `read` |
| `index=hr`、`field=salary`（脱敏） | 让组 `hr` 看到带集群盐值的哈希 |
| `index=hr`（行过滤） | 让用户 `emea-hr` 只看到 `{"term": {"region": "EMEA"}}` 的文档 |
| `cluster=*` | 允许组 `ops` `cluster_monitor` |

代理应用新快照后，`analysts` 的成员可以读取除客户端地址以外的所有日志字段；`logstash` 可以立即写入明天的 `logs-2026.10.27`，并由第一条文档创建它；`hr` 的成员看到的工资是哈希值；`emea-hr` 只看到本地区的员工；`ops` 的成员可以读取集群的健康状况与统计信息。

## 执行策略

服务插件负责管理策略与查询资源；执行策略需要每个集群一个 OpenSearch 代理。部署与检查见 [OpenSearch 代理](/external/opensearch-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [OpenSearch 代理](/external/opensearch-agent/)
- [插件与服务类型](/develop/plugins/)
