---
title: OpenSearch 代理
description: 在集群旁运行 OpenSearch 代理，让 GrantForge 策略成为安全插件自己的角色，带字段级安全、脱敏字段与文档级安全；索引模式保持为模式，新索引立即可用；并从审计日志上报访问。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

OpenSearch 代理是运行在集群旁的服务。它让安全插件自己的访问控制与 `opensearch` 数据服务的签名策略保持一致：为每个用户维护一个只映射给该用户的角色，恰好持有策略给该用户的权限，并带有字段级安全、脱敏字段与文档级安全。之后由 OpenSearch 自己检查每个请求，代理从不位于客户端与集群之间。发行包以可运行的 JAR 提供它，即 `agents/opensearch/grantforge-agent-opensearch-<version>.jar`，需要 Java 17 或更高版本。

仓库的 Testcontainers 集成测试让代理以及发行的 JAR 运行在带安全插件的 OpenSearch `2.19`、`3.3`、`3.6` 与 `3.9` 上。这并不代表认证了其他所有版本；代理会拒绝早于 `2.19` 的集群以及没有安全插件的集群。服务插件见 [OpenSearch](/plugins/opensearch/)。

## 工作方式

- **每个用户一个角色，持有 GrantForge 给出的权限。** 对于策略给出了权限的每个用户，代理维护一个以其命名的角色，例如 `alice` 对应 `grantforge_alice`，并只映射给该用户。角色的描述标明它属于代理，没有该标记的角色绝不会被当作代理自己的，因此代理不会改动其他角色与映射。
- **索引模式保持为模式。** 策略的索引模式（例如 `logs-*`）只要在其范围内所有策略的决定都相同，就以模式授予：任何类型的策略，只要其索引与该模式有交集，就必须完整覆盖该模式。代理借助模式的有限自动机精确地判断这一点。之后新建的索引（例如明天的日志）立即可用，第一条文档写入时自动创建索引也不例外。若某条策略在模式内做出不同的决定，例如在 `logs-*` 内拒绝 `logs-secret`，代理就按名称授予集群现有的每个索引与数据流，新建的则在下次同步时授予。
- **只在下层另有决定时使用字段级安全。** 用户可以整体读取、且没有任何策略可能收回某个字段的索引，不使用字段级安全，因此之后新增的字段立即可读。否则用户读取策略允许的字段，范围是文档已有的字段与策略中写出的字段；`name.keyword` 这样的多字段随其字段一同处理。
- **脱敏与行过滤使用安全插件自己的机制。** 脱敏成为条目的脱敏字段，行过滤成为其文档级安全查询。每个查询在授予之前都由 OpenSearch 的 validate API 检查；OpenSearch 不会执行的查询让用户看不到任何文档，并记录日志。安全插件不认识的脱敏方式会改为隐藏该字段。
- **每组权限一个条目。** 权限相同的索引与模式共用一个条目；条目之间从不重叠，因为安全插件会合并重叠条目的字段级与文档级安全。
- **读取与写入附带集群的组合操作。** 读取附带 `cluster_composite_ops_ro`，用于多重搜索、多重获取与滚动查询；写入附带 `cluster_composite_ops`，用于 bulk。
- **变更是一个补丁。** 代理以 JSON 补丁写入它的角色与映射，安全插件会在整个集群中一次性应用：先写角色，再写引用这些角色的映射；先删映射，再删它们引用的角色。安全插件拒绝的补丁会逐项应用，因此一个有问题的角色只会让它自己失败。
- **当前时刻的策略。** 每次同步都按当时适用的策略制定计划，因此有效期会准时开始与结束。
- **后端角色就是组。** 针对某个组的策略适用于拥有同名后端角色的内部用户，以及 GrantForge 中所在组的用户。
- **手工改动会被还原。** 每次同步时，代理都会把它的角色与映射与安全插件中保存的内容比较；不同之处会重新写入。没有变化时，代理什么也不发送。
- **上报 GrantForge 之外的角色。** OpenSearch 授予用户任一角色给出的权限，因此代理无法收回映射给该用户的其他角色所给出的权限。对于给出了策略所拒绝内容的每个这样的角色，代理只警告一次，写明角色、用户、访问、索引与策略；映射到 `all_access` 的用户不受任何角色约束，也会被警告。
- **访问来自审计日志。** 代理从安全插件写入的索引 `security-auditlog-*` 中读取审计日志：被拒绝的访问记为 `MISSING_PRIVILEGES`，审计配置记录时，被允许的访问记为 `GRANTED_PRIVILEGES`。每条事件写明用户、访问类型、所访问的每个索引或数据流（或集群），并说明是由 GrantForge 决定，还是由 GrantForge 之外的角色放行。

| 动作 | 访问事件 |
| --- | --- |
| `indices:data/read/*`：搜索、获取、计数 | 每个索引或数据流上的 `read` |
| `indices:data/write/index`、`update`、`bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`、`auto_create`、`data_stream/create` | `create_index` |
| `indices:admin/aliases*`、`indices:monitor/*` | `manage_aliases`、`monitor` |
| 其他 `indices:admin/*` | `manage` |
| `cluster:monitor/*`、`cluster:admin/snapshot/*` 与 `repository/*` | 集群上的 `cluster_monitor`、`manage_snapshots` |
| 索引模板与组件模板、采集管道 | 集群上的 `manage_templates`、`manage_pipelines` |

## 部署

1. 添加 `opensearch` 数据服务并配置其策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有代理运行账号可读的文件。
2. 给代理一个可以修改安全插件配置的用户：一个映射到 `all_access`、且被 `plugins.security.restapi.roles_enabled` 列出的用户，或 `plugins.security.authcz.admin_dn` 所指定的管理员证书。测试以演示配置的 `admin` 以及其管理员证书运行代理。
3. 要上报访问，请把审计日志保存在集群中（`plugins.security.audit.type: internal_opensearch`）。要上报 OpenSearch 允许的访问，还需把 `GRANTED_PRIVILEGES` 从审计配置的禁用类别中移除；拒绝默认就会记录。
4. 编写设置文件，替换示例中的地址、路径与名称：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   这些路径位于代理所在的机器上，缓存目录必须可写。密码文件按 UTF-8 读取并去除首尾空白。
5. 启动代理，例如作为 systemd 服务：

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   把 `<version>` 替换为发行版本。以 `java -Dgrantforge.…=… -jar …` 提供的设置会覆盖文件中的设置。代理收到 SIGTERM 时会干净地停止，并发送或暂存待发事件；OpenSearch 保留它已获得的权限。
6. **把已有角色迁移为策略：** 在 GrantForge 之外映射给用户的角色会与代理的角色一同继续生效。请创建对应的策略，然后删除这些映射；代理的警告会写出其中给出了策略所拒绝内容的角色。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此代理的名称，以及存放签名快照、暂存事件与审计日志读取位置的可写目录 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.opensearch.hosts` | 必填：以 URL 表示的节点，某个无法访问时依次尝试下一个；不写协议时为 `https`，端口默认为 `9200` |
| `grantforge.opensearch.user`、`grantforge.opensearch.password.file` | 代理的用户及其密码文件 |
| `grantforge.opensearch.tls.cert`、`grantforge.opensearch.tls.key` | 或者：PEM 客户端证书及其私钥，需同时设置，用于以证书登录；`grantforge.opensearch.tls.key.passphrase.file` 存放加密私钥的口令 |
| `grantforge.opensearch.ssl.verification.mode` | `full`（默认）校验节点的证书与名称，`certificate` 只校验证书，`none` 不校验 |
| `grantforge.opensearch.tls.ca` | 校验节点的 PEM CA 证书；为空时信任 Java 所信任的 |
| `grantforge.opensearch.timeout.ms` | 连接与请求可用的时长，默认 `30000`，范围 `1..600000` 毫秒 |
| `grantforge.opensearch.role.prefix` | 代理的角色名的开头，默认 `grantforge_`；由小写字母、数字、连字符与下划线组成，以下划线结尾，最多 32 个字符。名称以它开头且带有代理标记的角色归代理所有 |
| `grantforge.opensearch.unmanaged.users` | 代理不处理的用户，以逗号分隔，例如服务账号；代理始终不处理它自己的用户以及映射到 `all_access` 的用户 |
| `grantforge.sync.interval.ms` | 代理让 OpenSearch 保持一致的间隔，默认 `10000`，范围 `1000..3600000` 毫秒 |
| `grantforge.audit.log` | 默认 `true`；`false` 表示不读取审计日志，也不上报访问 |
| `grantforge.audit.log.index`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.max.documents` | 审计日志的索引，默认 `security-auditlog-*`；读取间隔，默认 `5000` 毫秒；以及每次读取的最多文档数，默认 `1000`，积压的日志会分多次读取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 连接与读取超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）与磁盘暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |

## 用户、组与角色

策略中的用户是安全插件所认识的 OpenSearch 用户。代理为内部用户、策略中写出的用户（他们可能通过 LDAP 或 SAML 等目录登录）以及之前已为其维护角色的用户维护角色；安全插件保留与隐藏的用户（例如 `kibanaserver`）不做处理。组为内部用户的后端角色，加上 GrantForge 中该用户所在的组；目录在登录时给出的后端角色代理无法得知，因此请在 GrantForge 中为这类用户设置组。角色为 GrantForge 授予该用户的角色。

## 决策与限制

- 没有快照时代理不做任何改动。未启用的服务，或绑定到类型不是 `opensearch` 的服务的令牌，不授予任何权限：代理删除它的角色与映射，由安全插件自己的角色生效。
- 代理未运行期间，OpenSearch 保留最后一次获得的权限；策略的变化要等代理运行后才生效。变化会在刷新间隔加同步间隔之内到达 OpenSearch。
- 按名称授予（而非经由模式授予）的索引，会在下次同步时获得权限。
- 别名会访问到其背后的索引：搜索别名需要对它包含的每个索引都有权限，因此策略写的是索引与数据流，而不是别名。
- 行过滤只能检验用户可以读取的字段，脱敏只作用于 text 与 keyword 字段的值。
- 名称以点开头的索引只能经由以点开头的策略模式访问。
- 代理不管理安全插件为 OpenSearch Dashboards 提供的租户。

## 相关指南

- [OpenSearch](/plugins/opensearch/)
- [数据服务、策略与代理](/external/data-services/)
