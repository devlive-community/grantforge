---
title: Apache Solr
description: 安装并配置 Solr 服务插件，连接一个或多个节点，以 basic 认证或 JSON Web Token 登录，查询集合及其请求路径，并管理谁可以查询、更新与管理什么。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Solr 插件查询集合及其中的请求路径，并针对 Solr 所决定的事情管理访问策略：查询、更新、读取与修改 schema 和 config、管理集合以及管理集群。插件 ID 与服务类型均为 `solr`。服务插件运行在 GrantForge 服务器中；安装在节点中的 [Solr 代理](/external/solr-agent/)负责执行策略。

仓库的 Testcontainers 集成测试覆盖 Solr `9.8.1`、`9.10.1`、`9.11.0` 与 `10.0.0` 的 SolrCloud 节点；这并不代表认证了其他所有版本。

## 安装

发行包在 `plugins/solr` 中包含此插件。在 **平台管理 → 插件** 中确认 `solr` 已启用；更新插件目录后重新扫描。插件通过 SolrJ 9.11 的 JDK HTTP 客户端与节点通信，位于插件自己的类加载器中。

## 添加数据服务

1. 打开 **数据权限 → 数据服务**，添加服务并选择 Apache Solr（`solr`）。
2. 输入一个节点或多个节点的基础 URL。
3. 选择查询用户的登录方式并填写凭据，测试连接后保存服务。测试会登录并描述集群：Solr 版本、是否运行 SolrCloud、有多少个集合。

查询用户用于让 GrantForge 发现资源，与查询和更新的用户相互独立。连接测试成功并不表示访问已受策略约束。

| 设置 | 用途 |
| --- | --- |
| `solr.urls` | 必填：节点的基础 URL，例如 `https://solr1.example.com:8983/solr`；多个以逗号分隔，某个节点无法访问时依次尝试下一个 |
| `authentication` | `none`（默认）、`basic` 或 `jwt` |
| `username`、`password` | basic：查询用户及其密码；密码加密存储 |
| `token` | jwt：查询用户的 JSON Web Token；加密存储 |
| `javax.net.ssl.trustStore`、`javax.net.ssl.trustStorePassword`、`javax.net.ssl.trustStoreType` | https：**GrantForge 服务器** 上用于校验节点证书的信任库、其密码与类型，默认 `PKCS12`；为空时使用 JVM 自带的 |
| `timeout` | 请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |

一种登录方式的设置在另一种方式下会被拒绝，因此服务明确写出它如何登录。登录被拒、用户无权查看，以及节点无法访问（包括其前面的代理替它应答时）都会如实报告。

### 查询用户能看到什么

列出集合是 Solr 的 `collection-admin-read` 权限，集合的请求路径来自其 config，即 `config-read`。请在 Solr 自己的规则中授予查询用户这些权限，或在安装代理后为它授予集群上的 `admin_read` 与集合上的 `config_read`。查询用户无权查看的集合的路径不会出现在提示中。未运行 SolrCloud 的节点有的是 core 而不是集合，插件把它们当作集合列出。

## 资源与访问类型

| 层级 | 匹配 | 访问类型 |
| --- | --- | --- |
| `collection` | 通配符，区分大小写；集合的名称 | `query`、`update`、`schema_read`、`schema_edit`、`config_read`、`config_edit`、`admin_collection` |
| `path` | 通配符，区分大小写；位于集合之下；请求路径，例如 `/select`、`/export` 或 `/update/json` | `query`、`update`、`schema_read`、`schema_edit`、`config_read`、`config_edit` |
| `cluster` | 唯一的值 `*` | `health`、`metrics`、`admin_read`、`security`、`admin` |

| 访问类型 | 对应 Solr 权限 | 隐含 |
| --- | --- | --- |
| `query` | `read`：搜索、获取、导出 | — |
| `update` | `update` | — |
| `schema_read`、`schema_edit` | `schema-read`、`schema-edit` | `schema_edit` 隐含 `schema_read` |
| `config_read`、`config_edit` | `config-read`、`config-edit` | `config_edit` 隐含 `config_read` |
| `admin_collection` | 指名该集合的 Collections API 请求的 `collection-admin-read` 与 `collection-admin-edit`：创建、重新加载、备份、删除等 | `query`、`update`、`schema_edit`、`config_edit` |
| `health`、`metrics` | `health`、`metrics-read` | — |
| `admin_read` | 整个集群的 `collection-admin-read`、`core-admin-read`、`zk-read`、`filestore-read`、`package-read`，以及读取节点的 config 与 schema | `health`、`metrics` |
| `security` | `security-read`、`security-edit` | — |
| `admin` | 一切 | `admin_read`、`security`、`admin_collection` |

对集合的请求会在集合以及“集合加请求路径”上判定，任一处拒绝都会拒绝它；因此一条策略可以允许一个集合而拒绝其 `/export` 处理器，或只允许通过 `/update*` 更新。涉及多个集合的请求需要每个集合都允许。在集合之下，请求路径就是 Solr 把请求路由到的处理器，名称与 Solr 一致。

通过 `name` 或 `collection` 参数指名集合的 Collections API 请求，需要每个被指名集合上的 `admin_collection`；因此 `team-*` 上的策略让一个团队可以创建名称以 `team-` 开头的集合，并重新加载、备份或删除它们，但不能操作其他集合。不指名集合的请求（例如列出所有集合）属于集群。集群上的 `admin` 授予一切，与 Solr 自己的 `all` 权限相同。策略值中，`*` 匹配任意多个字符，`?` 匹配一个字符。策略编辑器会提示集合，以及策略所列集合的请求路径。

### 客户端地址

策略项可以只对部分客户端生效：其 **客户端地址** 条件（`ip-range`）接受地址以及 `10.1.0.0/16` 或 `2001:db8::/32` 这样的网段。不会解析主机名。在反向代理之后，客户端就是该反向代理，除非 Solr 代理信任它：此时客户端是 `X-Forwarded-For` 中最后一个不是由受信任代理添加的地址，因此客户端无法冒充任意地址。

## 策略示例

假设集群有集合 `sales` 与 `hr`。在同一数据服务中创建以下已启用的访问策略：

| 资源 | 规则 |
| --- | --- |
| `collection=sales` | 为组 `analysts` 允许 `query` |
| `collection=sales`、`path=/export` | 为用户 `alice` 拒绝 `query` |
| `collection=sales`、`path=/update*` | 为用户 `ingest` 允许 `update` |
| `collection=team-*` | 为组 `search-team` 允许 `admin_collection` |
| `cluster=*` | 为用户 `monitoring` 允许 `metrics` 与 `health` |
| `collection=hr` | 为组 `hr-staff` 允许 `query`，限 `10.0.0.0/8` |
| `cluster=*` | 为组 `solr-admins` 允许 `admin` |

代理应用新快照后，`analysts` 的成员可以查询 `sales`，`alice` 除导出处理器外都可以；`ingest` 可以通过更新处理器更新 `sales`，但不能查询；`search-team` 的成员可以创建并管理自己的 `team-` 集合；`monitoring` 可以读取指标与健康状态；`hr-staff` 的成员只能从内网查询 `hr`；`solr-admins` 的成员可以做任何事。

## 执行策略

服务插件负责管理策略与查询资源。执行策略需要在每个节点中安装 Solr 代理。部署方式与检查内容见 [Solr 代理](/external/solr-agent/)。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [Solr 代理](/external/solr-agent/)
- [插件与服务类型](/develop/plugins/)
