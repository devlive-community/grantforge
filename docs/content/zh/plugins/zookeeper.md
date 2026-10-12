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

服务插件负责管理、查询和浏览；服务器内 agent 通过 SASL authentication provider 为 marker ACL 执行策略。构建命令 `mvn -pl agents/grantforge-agent-zookeeper -am package` 生成单个兼容 Java 8、内含依赖的 shaded JAR；发行包路径为 `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`。实现面向 ZooKeeper 3.6–3.9 API，实际版本兼容性仍待验证，不代表完整版本矩阵已通过或已完成生产验收。

### 部署

1. 将 JAR 安装到 **每个 ZooKeeper server** 的 `lib` 目录，包括所有可接收客户端请求的节点。在 GrantForge 对应的 `zookeeper` 数据服务中创建 **Agent 凭据**，把 token 存入独立文件，仅允许 ZooKeeper 运行账户读取；不要把 token 内容写入 `zoo.cfg`。
2. 在每台服务器的 `zoo.cfg` 中使用以下配置，替换示例地址、文件路径和实例标识。`grantforge.server.url`（`http[s]://GrantForge`）、`grantforge.token.file`、本机 `grantforge.cache.dir` 和 `grantforge.instance` 均为必填；缓存目录必须可由 ZooKeeper 运行账户写入。

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. 此 provider **替换原生 SASL provider**；不能在其他 `authProvider.N` 中保留另一个注册同一 `sasl` scheme 的 provider。必须设置 `enableEagerACLCheck=false`：开启时 provider 注册会被拒绝，marker 也不允许。保留并验证正常的 ZooKeeper SASL、JAAS 和 Kerberos 认证配置；agent 不替代身份认证。按 ensemble 的维护流程重启服务器。
4. 可选设置 `grantforge.signing.key.file`，指定用于固定信任的签名公钥文件。`grantforge.principal.mapping=short` 为默认值，将 `kafka/host@REALM` 映射为 `kafka`；`full` 保留完整 principal。策略中的用户必须与映射后的身份一致。

### ACL 与限制

- 仅 `sasl:grantforge` marker ACL 交给 GrantForge 策略；例如 `sasl:grantforge:cdrwa`，其中 `c=create`、`d=delete`、`r=read`、`w=write`、`a=admin`。其他原生 ACL（包括其他 SASL 身份）照常判断。
- agent 未运行、没有可用的策略 snapshot、snapshot 的 `serviceType` 不是 `zookeeper`，或策略不匹配/不允许时，marker 拒绝。**这不是全局 deny**：其他 ACL、`world:anyone` 或 `super` 身份仍可能放行请求。
- 策略匹配服务器上的绝对目标路径。`create`/`delete` 匹配要创建/删除的 znode，而不是提供 ACL 的父节点；使用 chroot 的请求必须加上实际 chroot 前缀，例如 chroot `/kafka` 下的 `/brokers` 对应策略路径 `/kafka/brokers`。sequential `create` 检查追加序号前的 server 绝对请求前缀；后续 `read`/`delete` 检查生成的完整节点名。
- 原生 ACL 不会自动迁移，ACL 也不会递归继承；新建 znode 必须显式设置 marker ACL。使用 `setACL` 前先在小范围验证允许与拒绝，并保留独立管理通道；不要贸然将 marker 应用到根节点，以免锁死访问。
- 未安装 agent 的服务器会把 `sasl:grantforge` 当作原生 SASL 身份 `grantforge` 匹配，而不会执行策略，因此必须在所有节点部署，不能依靠部分部署保护 ensemble。
- follower/observer 转发到 leader 的写请求在此 provider 接口可能没有客户端连接，因此 `clientAddress` context 可能为空，不能保证基于客户端 IP 的 ensemble 全链路策略。
- 此 provider 无法获知请求的最终结果，因此不发送最终 `AccessEvent` 审计事件，也不提供完整访问审计。

## 相关指南

- [数据服务、策略与代理](/external/data-services/)
- [插件与服务类型开发](/develop/plugins/)
