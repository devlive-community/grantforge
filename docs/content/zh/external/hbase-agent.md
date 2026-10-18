---
title: Apache HBase 代理
description: 在 HBase 的 master 与 region server 中安装协处理器，以 GrantForge 策略检查操作与读写，只返回用户可读的列，并隐藏用户无权使用的内容。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

HBase 代理是 master、region 与 region server 的协处理器：HBase 对每个操作与每次读写都询问它，它依据 `hbase` 数据服务的签名策略作答。发行包以单个兼容 Java 8 的 JAR 提供，即 `agents/hbase/grantforge-agent-hbase-<version>.jar`，依赖已重定位打包在内。

仓库的 Testcontainers 集成测试在 HBase `2.5.16`、`2.6.7` 与 `3.0.0` 中运行该 JAR。这并不代表对其他所有版本的认证。服务插件见 [Apache HBase](/plugins/hbase/)。

## 工作方式

- **每个请求都在其涉及的每个范围上检查。** HBase 中权限覆盖其范围内的一切，因此代理在请求的命名空间、表、列族与列上分别决策：任一范围的拒绝都会拒绝请求，任一范围的允许都会放行。
- **命名空间的所有者拥有其中的表。** 命名空间上的 `create` 与 `admin` 让用户可以创建该命名空间、在其中建表并管理这些表，无需逐表配置策略。
- **读取返回可读的部分。** 用户只能读表的一部分（例如一个列族）时，get 与 scan 返回这些列，而不是失败；对表中任何内容都无权读取的用户会被拒绝。
- **列表略去无权使用的内容。** 列出命名空间和表时只显示用户可以使用、或可以使用其中某些内容的项，列出快照时只显示用户管理的表的快照；描述无权使用的命名空间会被拒绝。
- **拒绝会说明原因。** 客户端收到 `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)`，或拒绝它的策略。
- **决策会被缓存，访问会被合并。** 当前快照的决策会被缓存，默认 100,000 条、10 秒，新快照会重新开始。一个时间窗口（默认 10 秒）内相同的允许访问会作为一条注明次数的访问事件上报；拒绝立即逐条上报。
- **服务器本身不受检查。** HBase 的超级用户（`hbase.superuser` 中列出）以及服务器自身的工作不受检查也不被审计。`hbase` 命名空间中的表（例如 `hbase:meta`）所有人都可读取，这是客户端必需的。

| 操作 | 所需访问 |
| --- | --- |
| Get、scan | 表上的 `read`，或返回的列上的 `read` |
| 列的 put、increment、append、delete | 所写每一列上的 `write` |
| 删除列族或整行 | 每个列族上的 `write`，因为 HBase 按列族逐个删除整行 |
| Check-and-mutate | 所比较列上的 `read`（使用过滤器时为表上的 `read`），以及所写内容上的 `write` |
| 创建表 | 命名空间上的 `create` |
| 删除、清空、修改、启用、禁用、刷写、拆分、合并、压缩 | 表上的 `admin` 或 `create` |
| 移动、分配、取消分配、下线 region | 表上的 `admin` |
| 创建、恢复、删除或列出快照 | 表上的 `admin`；克隆还需要目标命名空间上的 `create` |
| 批量导入 | 表上的 `create` |
| 创建、修改、删除命名空间 | 命名空间上的 `admin` |
| 描述命名空间；列出命名空间与表 | 对其本身或其中内容的任意访问 |
| 均衡器、关闭集群、配额、复制对端、region server 组、过程、下线 region server、停止 region server、滚动 WAL | `global` 上的 `admin` |

## 部署

1. 添加 `hbase` 数据服务并配置策略。打开 **数据权限 → 代理**，选择该服务并签发代理令牌。令牌只显示一次；请存入只有 HBase 账户可读的文件。
2. 将 JAR 复制到 **每个 master 与 region server** 的 `lib` 目录，例如 `/opt/hbase/lib`。
3. 在每个节点的 `hbase-site.xml` 中加入以下配置，替换示例地址、路径与实例名：

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   缓存目录必须对 HBase 账户可写。请在协处理器列表中替换 HBase 自带的 `AccessController`，不要同时加载两者。
4. 逐个重启 master 与 region server。代理拿到第一个快照之前，除超级用户外的请求都会被拒绝。
5. **先迁移现有权限：** HBase 的 `AccessController` 存在 `hbase:acl` 中的授权不再生效。请事先用 `user_permission` 列出它们，并创建对应的策略。

| 配置项 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 服务器、代理令牌文件、此 master 或 region server 的名称，以及存放签名快照与暂存事件的可写目录 |
| `grantforge.signing.key.file` | 可选，固定快照签名密钥的文件；从“代理”页面复制 Base64 编码的 X.509 Ed25519 公钥。省略时在首次连接时从服务器获取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 的连接与读超时，默认 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理获取新策略的间隔，默认 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 访问事件：批大小（默认 `500`）、队列容量（默认 `10000`）、发送间隔（默认 `5000` 毫秒）和暂存上限（默认 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允许访问合并为一条事件的时长，默认 `10000`，最多 `3600000` 毫秒；`0` 表示逐条上报 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 缓存的决策数上限，默认 `100000`，最多 `10000000`，`0` 表示不缓存；以及缓存时长，默认 `10000`，`1..3600000` 毫秒 |

HBase 会为 master、region server 以及它打开的每个 region 各创建一个协处理器；它们共用一个代理、决策缓存与事件合并。

## 用户、组与角色

策略中的名称是 HBase 赋予连接用户的短名：simple 认证的用户名，或按 `hadoop.security.auth_to_local` 得到的 Kerberos 主体短名。组是 Hadoop 组映射在服务器上为用户给出的组，加上 GrantForge 为用户指定的组；角色是 GrantForge 赋予用户的角色。

## 决策与限制

- 没有可用快照时，除超级用户的请求和对 `hbase` 命名空间的读取外，一律拒绝。未启用的服务不检查 HBase，与未安装授权器时相同。
- 令牌绑定的服务类型不是 `hbase` 时，一律拒绝。
- HBase 的 `grant`、`revoke` 与 `user_permission` 会以 *AccessController is not loaded* 失败：权限是 GrantForge 的策略。
- 协处理器端点调用不受检查；只加载可信的端点。
- 在这里，HBase 不会给表的创建者额外权限；如上所述，命名空间策略会让其所有者拥有其中的表。
- HBase 会把某些拒绝只记入日志而不返回给客户端，例如均衡器开关：开关保持原状，但客户端不会得到提示。
- 决策会缓存 `grantforge.decision.cache.ttl.ms`，因此策略有效期的开始或结束最多会延迟这么久。
- 不覆盖 HBase 的单元格级权限与可见性标签。列按 UTF-8 文本比较。

## 相关指南

- [Apache HBase](/plugins/hbase/)
- [数据服务、策略与代理](/external/data-services/)
