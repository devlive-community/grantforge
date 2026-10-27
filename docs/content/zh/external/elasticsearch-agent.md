---
title: Elasticsearch 代理
description: 在集群旁运行 Elasticsearch 代理，让 GrantForge 策略成为 Elasticsearch 自己的角色：赋予原生用户，并通过角色映射给其他 realm 的用户，许可证允许时带有字段级与文档级安全；索引模式保持为模式，新索引立即可用；访问从审计日志文件上报。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Elasticsearch 代理是运行在集群旁的服务。它让 Elasticsearch 自己的访问控制与 `elasticsearch` 数据服务的签名策略保持一致：为每个用户维护一个角色，恰好持有策略给该用户的权限，集群许可证允许时带有字段级与文档级安全。原生用户在自己的角色中持有它；其他 realm（例如 LDAP、SAML 或 OpenID Connect）的用户通过按用户名的角色映射获得它。之后由 Elasticsearch 自己检查每个请求，代理从不位于客户端与集群之间。发行包以可运行的 JAR 提供它，即 `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`，需要 Java 17 或更高版本。

仓库的 Testcontainers 集成测试让代理以及发行的 JAR 运行在开启安全功能的 Elasticsearch `8.19`、`9.3`、`9.4` 与 `9.5` 上。这并不代表认证了其他所有版本；代理会拒绝早于 `8.19` 的集群、没有开启安全功能的集群，以及由 [OpenSearch 代理](/external/opensearch-agent/)负责的 OpenSearch 集群。服务插件见 [Elasticsearch](/plugins/elasticsearch/)。

## 工作方式

- **每个用户一个角色，持有 GrantForge 给出的权限。** 对于策略给出了权限的每个用户，代理维护一个以其命名的角色，例如 `alice` 对应 `grantforge_alice`。角色的元数据写明用户并标明它属于代理，没有该标记的角色绝不会被当作代理自己的，因此代理不会改动其他角色。
- **原生用户持有角色，其他 realm 按用户名获得。** 代理把角色加入原生用户的角色列表，并保留该用户的其他所有角色。角色映射适用于除原生与文件 realm 之外的所有 realm，因此代理还维护一个同名的角色映射，按用户名把角色给出，供通过 LDAP、Active Directory、SAML、OpenID Connect、JWT、Kerberos 或 PKI 登录的用户使用。文件 realm 无法通过 Elasticsearch 的 API 修改，其用户不会获得角色。
- **索引模式保持为模式。** 策略的索引模式（例如 `logs-*`）只要在其范围内所有策略的决定都相同，就以模式授予：任何类型的策略，只要其索引与该模式有交集，就必须完整覆盖该模式。代理借助模式的有限自动机精确地判断这一点。之后新建的索引（例如明天的日志）立即可用，第一条文档写入时自动创建索引也不例外。若某条策略在模式内做出不同的决定，例如在 `logs-*` 内拒绝 `logs-secret`，代理就按名称授予集群现有的每个索引与数据流，新建的则在下次同步时授予。
- **只在下层另有决定时使用字段级安全。** 用户可以整体读取、且没有任何策略可能收回某个字段的索引，不使用字段级安全，因此之后新增的字段立即可读。否则用户读取策略允许的字段，范围是文档已有的字段与策略中写出的字段，每个字段连同其多字段一起，例如 `name` 与 `name.keyword`。
- **行过滤就是文档级安全。** 行过滤成为条目的查询。每个查询在授予之前都由 Elasticsearch 的 validate API 检查；Elasticsearch 不会执行的查询让用户看不到任何文档，并记录日志。
- **低于 Platinum 时绝不超出策略。** Elasticsearch 从 Platinum 许可证起提供文档级与字段级安全，低于该级别时会停用所有使用它们的角色。代理在每次同步时读取许可证。低于 Platinum 时，因策略限制了索引的字段或文档而需要这两项功能的读取不会被授予，代理会记录哪个用户不能读取哪个索引及原因；读取整个索引与其他权限照常授予。
- **每组权限一个条目。** 权限相同的索引与模式共用一个条目；条目之间从不重叠，因为 Elasticsearch 会合并重叠条目的字段级与文档级安全。
- **当前时刻的策略。** 每次同步都按当时适用的策略制定计划，因此有效期会准时开始与结束。
- **原生用户的角色就是组。** 针对某个组的策略适用于持有同名角色的原生用户，以及 GrantForge 中所在组的用户。
- **手工改动会被还原。** 每次同步时，代理都会把它的角色、角色映射与原生用户的角色与 Elasticsearch 中保存的内容比较；不同之处会重新写入，被其他用户持有的某个用户的角色会被收回。角色先以一次请求写入，再赋予用户；先从用户收回，再删除。没有变化时，代理什么也不发送；Elasticsearch 拒绝的变更会记录日志，不影响其他变更。
- **上报 GrantForge 之外的角色。** Elasticsearch 授予用户任一角色给出的权限，因此代理无法收回其他角色给出的权限。对于原生用户持有、或按用户名的角色映射给出的、给出了策略所拒绝内容的每个这样的角色，代理只警告一次，写明角色、用户、访问、索引与策略；拥有 `superuser` 角色的用户不受任何角色约束，也会被警告。
- **访问来自审计日志文件。** Elasticsearch 只把审计日志写入每个节点上的文件。代理读取指定的文件，从上次读到的位置继续，在 Elasticsearch 滚动文件时跟随，并上报 `access_granted` 与 `access_denied` 事件。每条事件写明用户、访问类型、所访问的每个索引或数据流（或集群），并说明是由 GrantForge 决定，还是由 GrantForge 之外的角色放行。

| 操作 | 访问事件 |
| --- | --- |
| `indices:data/read/*`：搜索、获取、计数 | 对每个索引或数据流的 `read` |
| `indices:data/write/index`、`update`、`bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`、`auto_create`、`data_stream/create` | `create_index` |
| `indices:admin/delete`、`data_stream/delete` | `delete_index` |
| `indices:admin/get`、`mappings/get`、`settings/get`、`resolve/*` | `view_index_metadata` |
| `indices:monitor/*`；其他 `indices:admin/*` | `monitor`；`manage` |
| `cluster:monitor/*`、`cluster:admin/snapshot/*` 与 `repository/*`、`slm/*` | 对集群的 `cluster_monitor`、`create_snapshot`、`manage_slm` |
| 索引模板与组件模板、摄取管道、`cluster:admin/ilm/*` | 对集群的 `manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` |

## 部署

1. 添加 `elasticsearch` 数据服务并配置策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请保存到只有代理账号能读取的文件中。
2. 为代理准备一个拥有 `manage_security` 集群权限的用户，用于管理角色、角色映射与用户，并能读取所有索引的映射与校验查询，例如拥有 `superuser` 角色的用户。它可以用密码、该用户创建的 API 密钥，或 PKI realm 映射到它的客户端证书登录。
3. 若要上报访问，请开启审计日志（`xpack.security.audit.enabled: true`），这需要 Gold 或更高的许可证，并让代理能读取节点的审计日志文件，即每个节点日志目录下的 `<集群名>_audit.json`：可以在某个节点上运行代理，或把这些文件汇集到代理能读取的目录。`xpack.security.audit.logfile.events.include: access_granted, access_denied` 让日志只包含代理上报的内容。
4. 编写设置文件，替换示例中的地址、路径与名称：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   这些路径位于代理所在的机器上，缓存目录必须可写。密码与密钥文件按 UTF-8 读取并去除首尾空白。
5. 启动代理，例如作为 systemd 服务：

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   将 `<version>` 替换为发行版本。以 `java -Dgrantforge.…=… -jar …` 提供的设置会覆盖文件中的设置。代理收到 SIGTERM 时正常停止，并发送或暂存待上报的事件；Elasticsearch 保留已授予的权限。
6. **把现有角色迁移为策略：** 用户在 GrantForge 之外持有的角色会继续与代理的角色一同生效。先创建对应的策略，再从用户收回这些角色；代理的警告会写明哪些角色给出了策略所拒绝的内容。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此代理的名称，以及用于保存签名快照、暂存事件与审计日志读取位置的可写目录 |
| `grantforge.signing.key.file` | 可选，固定快照签名公钥的文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时首次连接从服务器获取 |
| `grantforge.elasticsearch.hosts` | 必填：以 URL 表示的节点，某个无法访问时依次尝试下一个；不写协议时为 `https`，端口默认为 `9200` |
| `grantforge.elasticsearch.user`、`grantforge.elasticsearch.password.file` | 代理的用户及其密码文件 |
| `grantforge.elasticsearch.api.key.file` | 或者：编码后的 API 密钥文件，即 Elasticsearch 创建它时返回的值 |
| `grantforge.elasticsearch.tls.cert`、`grantforge.elasticsearch.tls.key` | 或者：PEM 客户端证书及其私钥，需同时设置，用证书登录；`grantforge.elasticsearch.tls.key.passphrase.file` 保存加密私钥的口令 |
| `grantforge.elasticsearch.ssl.verification.mode` | `full`（默认）校验节点的证书与名称，`certificate` 只校验证书，`none` 不校验 |
| `grantforge.elasticsearch.tls.ca` | 校验节点的 PEM CA 证书，例如 Elasticsearch 的 `http_ca.crt`；为空时信任 Java 信任的证书 |
| `grantforge.elasticsearch.timeout.ms` | 连接与请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |
| `grantforge.elasticsearch.role.prefix` | 代理角色名称的前缀，默认 `grantforge_`；由小写字母、数字、连字符与下划线组成，以下划线结尾，最多 32 个字符。代理拥有以它开头且带有标记的角色 |
| `grantforge.elasticsearch.unmanaged.users` | 代理不处理的用户，以逗号分隔，例如服务账号；代理始终不处理它自己的用户、Elasticsearch 的保留用户以及拥有 `superuser` 角色的用户 |
| `grantforge.sync.interval.ms` | 代理让 Elasticsearch 与策略保持一致的频率，默认 `10000`，范围 `1000..3600000` 毫秒 |
| `grantforge.audit.log.files` | 要读取的审计日志文件，以逗号分隔；不设置时不读取审计日志，也不上报访问 |
| `grantforge.audit.log.interval.ms`、`grantforge.audit.log.max.lines` | 读取文件的频率，默认 `5000` 毫秒；每次读取每个文件最多的行数，默认 `5000`，积压会分多轮读取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | 连接 GrantForge 与读取的超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理请求新策略的频率，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批量大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）与磁盘暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 逐条发送 |

## 用户、组与角色

策略按用户名指定 Elasticsearch 用户。代理为原生用户、策略中写出的用户（可能通过其他 realm 登录），以及之前曾为其维护角色的用户维护角色；Elasticsearch 的保留用户（例如 `kibana_system`）不受影响。组是原生用户的其他角色，加上 GrantForge 中用户所在的组；目录在登录时给出的组代理无法得知，因此请在 GrantForge 中为这类用户设置组。角色是 GrantForge 给用户的角色。

## 决策与限制

- 没有快照时代理不做任何改动。服务未启用，或令牌绑定的服务类型不是 `elasticsearch` 时，不授予任何权限：代理收回并删除它的角色及其角色映射，Elasticsearch 自己的角色继续生效。
- 代理未运行时，Elasticsearch 保留最后授予的权限；策略变更会等待代理。变更在刷新间隔与同步间隔内到达 Elasticsearch。
- 按名称而非通过模式授予的索引，在下次同步时获得权限。
- 低于 Platinum 时，策略限制其读取的用户无法读取该索引的任何内容；低于 Gold 时，代理不上报访问。
- 按用户名的角色映射适用于所有拥有该用户名的 realm；策略指定的是用户，而非 realm。
- 别名会访问其背后的索引，因此策略应写索引与数据流，而不是别名。名称以点开头的索引只能通过以点开头的策略模式访问。
- 代理不管理 Kibana 的空间与应用权限。

## 相关指南

- [Elasticsearch](/plugins/elasticsearch/)
- [数据服务、策略与代理](/external/data-services/)
