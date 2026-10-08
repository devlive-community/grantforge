---
title: 插件与服务类型
description: 用服务类型插件把 GrantForge 的策略管理扩展到外部数据系统：契约、打包、隔离与分发。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

服务类型插件描述一种外部系统：它有哪些资源层级、哪些访问类型、能否脱敏和行过滤、连接需要哪些配置。GrantForge 据此为这类系统提供数据服务、通用的策略编辑器、策略快照与访问审计（见 [数据服务与策略](/external/data-services/)）。

## 依赖

插件只依赖 `grantforge-plugin-api`（只依赖 JDK 与 JSpecify），以 `provided` 范围引入：

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## 实现 ServiceTypeProvider

```java
public final class ExampleProvider implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("example").label("Example warehouse")
                .resources(ResourceDefinition.builder("database").label("Database").lookupSupported(true).validLeaf(true)
                                .excludesSupported(false).build(),
                        ResourceDefinition.builder("table").label("Table").parent("database").lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder("column").label("Column").parent("table").accessTypes("select").build(),
                        ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "select", "update"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", "redact({col})"))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .configFields(ConfigField.builder("url").label("Address").type(ConfigFieldType.STRING).mandatory().pattern("example://.+").build(),
                        ConfigField.builder("timeout").label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return "example".equals(config.get("password")) ? ConnectionResult.succeeded()
                : ConnectionResult.failed("the example warehouse refused the password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        // 返回 request.resource() 层级下以 request.userInput() 开头的候选值，最多 request.limit() 个
        return List.of();
    }
}
```

以上摘自示例插件 `plugins/grantforge-plugin-example`，可以直接复制作为模板。

定义在构造时一次性校验并报告全部问题：父级未知或成环、重名、引用了未声明的访问类型或资源等。名称必须匹配 `[a-z][a-z0-9_-]{0,63}`。

| 部件 | 说明 |
| --- | --- |
| 资源 | 层级、匹配方式（精确、通配、路径、正则）、是否区分大小写、是否必填、是否支持排除与递归（仅路径）、是否支持查找、是否可作为叶子 |
| 访问类型 | 名称、显示名、蕴含的其他访问类型（如 `all` 蕴含 `select`），可限定资源 |
| 脱敏、行过滤 | 声明哪些资源支持、有哪些脱敏方式；执行在目标系统 |
| 条件 | 策略可附加的条件（如 IP 范围），由策略引擎的条件 SPI 求值 |
| 配置字段 | 字符串、长文本、整数、布尔、密钥、枚举；密钥字段加密保存，不能有默认值 |

提供者需要公开无参构造并且线程安全。`validateConfig`、`testConnection`、`lookup` 都有默认实现，按需覆盖。

## 描述符与打包

插件根目录放 `grantforge-plugin.yaml`：

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.0"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

插件可以是：

- 一个 jar，描述符在 jar 根目录；
- 一个目录或 zip：`grantforge-plugin.yaml`、`classes/` 与 `lib/*.jar`。

把它放进 `grantforge.plugins.directory`（默认 `plugins`），在控制台的“插件”页点击重新扫描即可，无需重启。

插件带有依赖时，像 `plugins/grantforge-plugin-hdfs` 那样用 assembly 打成 `plugin` 分类的 zip（描述符在顶层、`classes/`、`lib/`），并在 `generate-resources` 阶段把运行时依赖复制到 `target/plugin-lib`：

```xml
<plugin>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>plugin-lib</id>
      <phase>generate-resources</phase>
      <goals><goal>copy-dependencies</goal></goals>
      <configuration>
        <includeScope>runtime</includeScope>
        <outputDirectory>${project.build.directory}/plugin-lib</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## 从源码启动时

在 IDE 里直接启动 `org.devlive.grantforge.server.GrantForge` 时，服务端的类来自各模块的 `target/classes`，此时如果没有配置 `grantforge.plugins.directory`、工作目录下也没有 `plugins` 目录，就使用仓库的 `plugins/` 目录：其中构建过的插件模块（`target/classes` 里有描述符，且构建生成了 `target/plugin-lib`）直接作为插件加载，类来自 `target/classes`，依赖来自 `target/plugin-lib`；不生成 `plugin-lib` 的模块（如测试用的示例插件）不会加载。修改插件代码后由 IDE 重新编译，在控制台“插件”页重新扫描即可生效。插件模块第一次使用前，用 Maven 构建一次以复制依赖：

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## 兼容性

`apiVersion` 声明插件需要的契约版本。宿主当前提供 `1.0.0`，主版本相同且不低于所需版本的插件才会加载，否则标为“不兼容”。契约的每次变化都会提升版本，CI 用 japicmp 与上一个发行版比较（`script/ci/check_plugin_api_compat.py`），不兼容的改动必须提升主版本。

## 隔离

- 每个插件有自己的类加载器，父加载器是平台类加载器，只有 `org.devlive.grantforge.plugin.api.` 与 `org.jspecify.annotations.` 委托给宿主；插件看不到 Spring 和服务端的类，可以自带任意版本的依赖。
- 读取失败、版本不兼容、重复、构造异常或超时只会让这个插件被标为失败并记录原因，服务照常运行。
- 对插件的每次调用都有超时（`grantforge.plugins.call-timeout`，默认 10 秒）。

## 代理与快照

服务类型插件的源码位于 `plugins/`，由 GrantForge 服务端加载；具体代理的源码位于 `agents/`，打包后部署到目标系统中，例如 `agents/grantforge-agent-hdfs-*` 部署到 HDFS NameNode。共享的代理基础设施位于 `core/grantforge-agent-core`。

目标系统里的代理用代理令牌访问 `/api/v1/agent/**`：

| 接口 | 作用 |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | 报告代理的状态与当前快照版本 |
| `GET /api/v1/agent/policies` | 下载策略快照；未变化时返回 304；响应头带 Ed25519 签名 |
| `GET /api/v1/agent/signing-key` | 验证签名用的公钥 |
| `POST /api/v1/agent/access-events` | 批量上报访问事件，进入访问审计 |

代理用 `grantforge-policy-engine`（Java 8 API，可以嵌入较老的系统）在本地求值，不必每次访问都调用 GrantForge。

代理不必自己实现这些协议，`core/grantforge-agent-core`（Java 8）已经封装好：

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // 条件求值器，按名称

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- 按服务端要求的间隔发心跳；策略版本变化时下载快照（ETag 未变则 304），用服务端的 Ed25519 公钥验签（可在设置中固定公钥，否则首次从服务端获取并保留），校验通过才替换，并保存到缓存目录；服务端不可达时启动沿用最后一份快照。
- 快照把角色与组展开到用户，`decide` 会把用户在快照中的角色和组加到请求上。服务停用或尚无快照时结果为 `NOT_DETERMINED`，由代理决定回退到系统自身的检查还是拒绝。
- 访问事件进入有界队列（满了就丢弃并计数，绝不阻塞系统），按批发送；服务端不可达时写入缓存目录下的 `audit-spool/`，恢复后补发，超过上限丢弃最旧的。
- 依赖 Jackson 2 与 Bouncy Castle（JDK 15 之前没有 Ed25519）；目标系统自带这些库的其他版本，代理打包时需要用 shade 重定位。

## 示例

`plugins/grantforge-plugin-example` 是一个完整的插件：类型 `example`（database → table → column 与 path），访问类型 select、update、all，列脱敏、表行过滤、IP 范围条件，配置 url、timeout、password（密码为 `example` 时测试连接成功），并能查找示例库表。全栈端到端测试用它走完“添加服务 → 写策略 → 签发令牌 → 代理拉取 → 访问审计”。
