---
title: Apache ZooKeeper 代理
description: 在每个 ZooKeeper 节点部署代理，通过 marker ACL 执行 GrantForge 策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

服务端的 `grantforge-plugin-zookeeper` 统一提供 `zookeeper` 服务类型、资源和连接配置。让策略真正生效还需要另一部分：运行在 ZooKeeper 内部的 agent，它作为 SASL authentication provider 参与服务器的 ACL 判定。agent 位于 `agents/grantforge-agent-zookeeper`，构建命令 `mvn -pl agents/grantforge-agent-zookeeper -am package` 生成单个兼容 Java 8、内含依赖的 shaded JAR，发行包路径为 `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`。实现面向 ZooKeeper 3.6–3.9 API，实际版本兼容性仍待验证，不代表完整版本矩阵已通过或已完成生产验收。

## 部署

1. 将 JAR 安装到 **每个 ZooKeeper server** 的 `lib` 目录，包括所有可接收客户端请求的节点。在 GrantForge 对应的 `zookeeper` 数据服务中创建 **Agent 凭据**，把 token 存入独立文件，仅允许 ZooKeeper 运行账户读取；不要把 token 内容写入 `zoo.cfg`。
2. 在每台服务器的 `zoo.cfg` 中加入以下配置，替换示例地址、文件路径和实例标识。`grantforge.server.url`（`http[s]://GrantForge`）、`grantforge.token.file`、本机 `grantforge.cache.dir` 和 `grantforge.instance` 均为必填；缓存目录必须可由 ZooKeeper 运行账户写入。

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

## ACL 与限制

- 仅 `sasl:grantforge` marker ACL 交给 GrantForge 策略；例如 `sasl:grantforge:cdrwa`，其中 `c=create`、`d=delete`、`r=read`、`w=write`、`a=admin`。其他原生 ACL（包括其他 SASL 身份）照常判断。
- agent 未运行、没有可用的策略 snapshot、snapshot 的 `serviceType` 不是 `zookeeper`，或策略不匹配/不允许时，marker 拒绝。**这不是全局 deny**：其他 ACL、`world:anyone` 或 `super` 身份仍可能放行请求。
- 策略匹配服务器上的绝对目标路径。`create`/`delete` 匹配要创建/删除的 znode，而不是提供 ACL 的父节点；使用 chroot 的请求必须加上实际 chroot 前缀，例如 chroot `/kafka` 下的 `/brokers` 对应策略路径 `/kafka/brokers`。sequential `create` 检查追加序号前的 server 绝对请求前缀；后续 `read`/`delete` 检查生成的完整节点名。
- 原生 ACL 不会自动迁移，ACL 也不会递归继承；新建 znode 必须显式设置 marker ACL。使用 `setACL` 前先在小范围验证允许与拒绝，并保留独立管理通道；不要贸然将 marker 应用到根节点，以免锁死访问。
- 未安装 agent 的服务器会把 `sasl:grantforge` 当作原生 SASL 身份 `grantforge` 匹配，而不会执行策略，因此必须在所有节点部署，不能依靠部分部署保护 ensemble。
- follower/observer 转发到 leader 的写请求在此 provider 接口可能没有客户端连接，因此 `clientAddress` context 可能为空，不能保证基于客户端 IP 的 ensemble 全链路策略。
- 此 provider 无法获知请求的最终结果，因此不发送最终 `AccessEvent` 审计事件，也不提供完整访问审计。
