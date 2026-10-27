---
title: Apache Solr 代理
description: 在 Solr 节点中安装授权插件，用 GrantForge 策略决定谁可以查询与更新哪些集合、通过哪些请求路径、管理哪些集合与集群，以及可以从哪些网络发起。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Solr 代理是节点的授权插件：Solr 就每个请求询问它，它依据 `solr` 数据服务的签名策略作答。发行包以一个 Java 11 的 JAR 提供它：`agents/solr/grantforge-agent-solr-<version>.jar`，依赖已内嵌并重定位。

仓库的 Testcontainers 集成测试在 Solr `9.8.1`、`9.10.1`、`9.11.0` 与 `10.0.0` 的 SolrCloud 节点中运行该 JAR。这并不代表认证了其他所有版本。服务插件见 [Apache Solr](/plugins/solr/)。

## 工作方式

- **请求按 Solr 自己的方式分类。** 每个请求处理器都会声明它需要的权限，例如 `read` 或 `collection-admin-edit`，与 Solr 自带的规则授权插件所用的一致；代理把这个名称转换为访问类型，没有声明的处理器按请求类型判定。
- **对集合的每个请求还会按请求路径判定。** 代理在集合 `sales` 以及“`sales` 加路径 `/select`”上判定一次通过 `/select` 对 `sales` 的查询：任一处拒绝都会拒绝，因此一条策略可以拒绝集合的某个处理器。
- **团队可以管理自己的集合。** 指名集合的 Collections API 请求需要每个被指名集合上的 `admin_collection`，因此一条策略可以让团队只创建、重新加载或删除名称以其前缀开头的集合。
- **集群管理员可以做任何事。** 集群上的 `admin` 授予每个请求，与 Solr 自己的 `all` 权限相同。
- **策略可以只对部分网络生效。** 每次检查都带上客户端地址；在代理所信任的反向代理之后，客户端是 `X-Forwarded-For` 中最后一个不是由受信任代理添加的地址。
- **JSON Web Token 验证过的角色当作组。** 使用 Solr 的 JWT 认证时，token 声明给用户的角色会作为决策中的组，与 GrantForge 中该用户所在的组一起使用。
- **拒绝会连同原因记入日志。** Solr 以自己的 403 回应被拒绝的请求；节点日志会写明是哪条 GrantForge 策略拒绝的，或者没有策略允许。
- **缓存决策并合并访问。** 当前快照的决策会被缓存，默认 100,000 条、10 秒，新快照会重新开始。窗口内（默认 10 秒）相同的允许访问会作为一条访问事件上报，并注明次数；拒绝会立即逐条上报。每个事件都注明集合与路径（或集群）以及客户端地址。
- **不检查节点本身。** 节点之间的请求（Solr 以节点自身签名）直接放行；没有用户的请求会被要求登录。

| 请求 | 所需访问 |
| --- | --- |
| 通过任何读取处理器搜索、获取、导出、查询 | 集合与路径上的 `query` |
| 添加、更新或删除文档 | 集合与路径上的 `update` |
| 读取或修改集合的 schema | `schema_read` 或 `schema_edit` |
| 读取或修改集合的 config | `config_read` 或 `config_edit` |
| 指名集合的 Collections API 请求：创建、重新加载、备份、恢复、删除、修改及其集群状态 | 每个被指名集合上的 `admin_collection` |
| 健康检查；指标 | 集群上的 `health`；`metrics` |
| 列出集合、core 状态、读取 ZooKeeper、文件存储与包 | 集群上的 `admin_read` |
| 读取或修改认证与授权设置 | 集群上的 `security` |
| core 管理、修改文件存储与包，以及没有处理器声明的操作 | 集群上的 `admin` |

## 部署

1. 添加 `solr` 数据服务并配置其策略，包括为 Solr 管理员授予集群上的 `admin`。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有 Solr 运行账号可读的文件。
2. 把 JAR 复制到 **每个节点** 的 `server/solr-webapp/webapp/WEB-INF/lib`，例如 `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`，然后逐个重启节点。
3. 在集群的 `security.json` 中设置授权部分（与认证插件并列），替换示例中的地址、路径与实例名：

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   用 `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>` 上传；节点无需重启即可生效。必须启用认证，请求才有用户。
4. **在 Solr 9 上注意 Java 安全管理器**（Solr 默认启用）：令牌、固定的公钥与缓存目录请放在 `SOLR_HOME` 下，例如 `/var/solr/data`，并在 `server/etc/security.policy` 中，或在通过 `-Djava.security.properties` 以 `policy.url.3` 加入的自有策略文件中，为代理授予 Solr 策略之外所需的权限：

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10 不再使用安全管理器。
5. **先迁移已有权限：** Solr 规则授权插件的权限与用户角色将不再生效。请事先创建对应的策略。在代理收到第一个快照之前，除节点之间的请求外都会被拒绝。

| 设置 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、集群节点的名称，以及存放签名快照与暂存事件的可写目录 |
| `grantforge.trusted.proxies` | 代理信任其 `X-Forwarded-For` 的反向代理，地址或网段，以逗号分隔；默认没有 |
| `grantforge.signing.key.file` | 可选的固定快照签名密钥文件；从代理页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 连接与读取超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列大小（默认 `10000`）、发送间隔（默认 `5000` 毫秒）与暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 缓存决策的最大数量，默认 `100000`，最多 `10000000`，`0` 表示不缓存；以及缓存时长，默认 `10000`，范围 `1..3600000` 毫秒 |

## 用户、组与角色

策略中的用户是 Solr 认证登录的用户，例如 basic 认证的用户或 JSON Web Token 的主体。组为 JSON Web Token 为该用户验证过的角色，加上 GrantForge 中该用户所在的组；角色为 GrantForge 授予该用户的角色。

## 决策与限制

- 没有可用快照时，除节点之间的请求外一切都会被拒绝。未启用的服务让 Solr 不受检查，与没有授权插件时一样。
- 绑定到类型不是 `solr` 的服务的令牌会拒绝一切。
- 只在 JSON 请求体中指名集合的 Collections API 请求（部分 v2 API 请求如此）没有代理能看到的集合，需要集群上的 `admin`。
- Solr 在询问之前会把别名解析为集合，因此策略中写的是集合而不是别名。
- Solr 的 `/admin/authorization` API 无法编辑代理的规则：策略保存在 GrantForge 中。
- 客户端地址只能是地址与网段；主机名不匹配任何客户端。
- 决策会缓存 `grantforge.decision.cache.ttl.ms`，因此策略的生效或失效最多会延迟这么久。

## 相关指南

- [Apache Solr](/plugins/solr/)
- [数据服务、策略与代理](/external/data-services/)
