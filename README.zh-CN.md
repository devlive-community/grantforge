<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge logo" />

# GrantForge

统一权限平台 · 用户、角色、菜单、接口、数据行与字段授权 · 外部数据系统

语言：[English](README.md) · 中文 · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [Deutsch](README.de.md) · [法语](README.fr.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.0.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge（原 AuthX）是一个开源（MIT）的统一权限平台。它集中回答两个问题：**谁能做什么**（功能授权）和**谁能看到哪些数据**（数据与字段授权）。权限在控制台里定义、解释与审计，业务应用通过标准协议接入，外部数据系统（例如 HDFS）则通过插件与代理纳入同一套策略体系。

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="GrantForge 控制台" />
</p>

## 功能

| 领域 | 功能 |
| --- | --- |
| 身份与组织 | 多租户、部门树、用户组与岗位；CSV 批量导入导出；LDAP / Active Directory 登录与同步、OIDC 联合登录 |
| 账号安全 | 会话管理与强制下线、密码策略与失败锁定、TOTP 两步验证与恢复码、敏感操作二次验证 |
| 功能授权 | 资源目录（模块、菜单、页面、标签、按钮、API）、角色继承、授权矩阵、授权影响分析 |
| 数据权限 | 按条件限定可见的数据行（本人、本部门及下级、指定部门、自定义条件），读与写分开控制 |
| 字段权限 | 字段可隐藏、可脱敏（邮箱、手机号、证件号等）、可只读 |
| 可解释与审计 | 权限解释（每项权限从哪来）、授权模拟、审计日志查询与导出 |
| 治理 | 职责分离（SOD）约束、权限申请与审批、定期权限复核 |
| 应用接入 | OAuth 2.1 / OIDC 授权服务器、权限查询开放 API、Java（Spring Boot Starter）与 JavaScript SDK |
| 外部系统 | 插件化服务类型与策略引擎：数据服务、访问策略、代理与访问审计 |
| 交付 | 单一可执行发行包、Docker 镜像、Compose 示例、Helm Chart；H2、PostgreSQL、MySQL、MariaDB、Oracle、SQL Server |

外部系统支持当前提供插件框架、通用策略编辑器、策略签名分发、访问审计，以及 HDFS 服务类型插件与 Hadoop 3.5.0 NameNode 代理；Hive 插件与其他 Hadoop 版本的代理仍在开发中。

## 工作原理：两个平面

- **管理平面**：GrantForge 服务端（Spring Boot 4.1，Java 17 字节码）与 Vue 3 控制台，负责租户、账号、组织、角色、授权、审计，以及数据服务与策略的维护。
- **数据平面**：嵌入受保护系统的代理。代理凭令牌定期从服务端拉取带 Ed25519 签名的策略快照并缓存在本地，在每次访问之前判定允许或拒绝（策略不可达时默认拒绝），同时把访问事件回传服务端审计。

自己的系统不必照抄 HDFS 的做法：普通业务应用用开放 API 或 Spring Boot Starter 在进程内求值即可，只有需要在数据库、文件系统等存储系统内部拦截访问时，才需要用 `core/grantforge-agent-core` 编写嵌入目标系统的代理。

## 接入你的应用

- **OAuth 2.1 / OpenID Connect**：GrantForge 本身就是授权服务器，应用用它登录用户；已有的身份源（LDAP / AD / OIDC）也可以接进来。
- **Java 应用**：`sdk/grantforge-spring-boot-starter` 提供 `@RequirePermission` 接口鉴权、`@GrantForgeEntity` 声明数据实体，以及 `GrantForgeDataScopes.scope(...)` 把平台上的数据权限转成 JPA `Specification`。
- **前端应用**：`@grantforge/client` 用 OIDC + PKCE 从你自己的域名引导用户登录，并查询当前用户的权限。
- **开放 API**：`/api/v1/open/me/authorization`、`/api/v1/open/me/data-access`、`/api/v1/open/catalog/data-entities`。
- **可运行的例子**：`samples/` 下的 `shop` 与 `notes` 两个应用按第三方的方式接入。

## 快速开始

需要 Java 17 或更高版本。服务默认监听 `9999` 端口，首次启动会在日志里打印一次性的**初始化令牌**；用浏览器打开 <http://127.0.0.1:9999/>，填入令牌并创建第一个管理员即可。

```bash
# 使用发行包（或从源码 ./mvnw clean package 构建，产物在 dist/）
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# 或使用 Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# 或用 Compose 搭配数据库
docker compose -f deploy/compose/postgres.yml up -d

# 或部署到 Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

默认使用内嵌 H2 文件库，无需任何配置即可启动。MySQL 驱动因 GPL 许可不随发行包分发，需要自行放进 `drivers/`。安装、初始化与第一次授权的详细步骤见[文档站](https://grantforge.devlive.org)。

## 数据库

默认使用内嵌 H2 文件库（`${GRANTFORGE_HOME}/data`），无需任何配置即可启动。生产环境通过环境变量切换，库表结构由 Liquibase 统一管理：

| 数据库 | 版本（CI 验证） | `GRANTFORGE_DB_URL` 示例 |
| --- | --- | --- |
| PostgreSQL | 14、17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0、8.4 | `jdbc:mysql://host:3306/grantforge`（需自行将 `mysql-connector-j` 放入 `lib/`，其 GPL 许可不随发行包分发） |
| MariaDB | 10.11、11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

同时设置 `GRANTFORGE_DB_USER`、`GRANTFORGE_DB_PASSWORD`；集群部署时每个实例必须设置不同的 `GRANTFORGE_ID_NODE`（0-1023）。

## 项目结构

Maven 根坐标：`org.devlive.grantforge:grantforge:2026.0.0`。Java 包前缀：`org.devlive.grantforge`。启动类：`org.devlive.grantforge.server.GrantForge`。

`core/` 包含服务端与共享基础设施，`plugins/` 包含服务端加载的服务类型插件，`agents/` 包含部署在受保护系统内的具体代理。共享的 `grantforge-agent-core` 库保留在 `core/`，HDFS NameNode 代理位于 `agents/grantforge-agent-hdfs`。

| 模块 | 职责 |
| --- | --- |
| `core/grantforge-server` | Spring Boot 服务入口：REST API、安全配置、开放 API，并托管 Web 管理界面 |
| `core/grantforge-web` | Vue 3 / TypeScript / Tailwind CSS 管理界面 |
| `core/grantforge-common` | 错误码与 problem details 模型、CSV、接口访问注解 |
| `core/grantforge-persistence` | 实体、租户过滤、TSID、Liquibase、数据与字段权限 SPI |
| `core/grantforge-audit` | 审计事件的记录、查询、保留与归档 |
| `core/grantforge-identity` | 租户、账号、部门、用户组、岗位、登录与会话、两步验证、身份源 |
| `core/grantforge-authz` | 资源目录、角色、授权、分配与求值、数据与字段策略、职责分离、申请与复核 |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | 服务类型插件的契约，以及插件的加载、隔离与调用 |
| `core/grantforge-policy-engine` | 外部系统策略的求值引擎（Java 8 API，可嵌入代理） |
| `core/grantforge-agent-core` | 代理的公共代码：设置、签名快照、访问判定、审计上报 |
| `core/grantforge-service` | 数据服务、策略快照签名与分发、代理与访问审计 |
| `core/grantforge-oauth` | 基于 Spring Authorization Server 的 OAuth 2.1 / OIDC 服务器 |
| `plugins/grantforge-plugin-hdfs` | HDFS 服务类型插件：策略管理与资源查询 |
| `plugins/grantforge-plugin-example` | 自定义服务类型的示例插件 |
| `agents/grantforge-agent-hdfs` | Hadoop 3.5.0 NameNode 代理：覆盖式授权与访问审计 |
| `sdk/grantforge-spring-boot-starter`、`sdk/grantforge-js` | Java 与 JavaScript 应用接入 SDK |
| `script/ci`、`deploy/` | CI 检查脚本（本地与 CI 使用同一脚本）与部署资源（Dockerfile、Compose、Helm） |

## 运维与可观测性

- 健康探针：`/actuator/health/liveness`、`/actuator/health/readiness`（仅返回状态，不暴露细节；就绪探针在数据库可用且迁移完成后才返回 200）。
- 指标：`/actuator/prometheus`（带 `application="grantforge"` 标签，默认需要登录，可用 `GRANTFORGE_PROMETHEUS_PUBLIC=true` 对受信网络开放）。
- 日志：默认可读文本，每行带请求编号；设置 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`（或 `logstash`）输出 JSON 日志。
- 发行包脚本：`bin/` 下的 `startup.sh`、`shutdown.sh`、`restart.sh`、`debug.sh` 与 `import-legacy.sh`。

## 开发与验证

构建需要 JDK 17 或更高版本（产物目标为 Java 17 字节码，策略引擎为 Java 8）；在 JDK 21+ 上会自动启用 Error Prone + NullAway 空值检查。前端使用 Vue 3.5、Tailwind CSS 4、Node.js 22.12+ 和 pnpm 8.10.2。

```sh
# Java 构建与单元测试（跳过前端构建）
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# 在指定数据库上运行持久化集成测试（需要 Docker，h2 除外）
bash script/ci/db_integration.sh postgres:17

# 打包发布包（包含前端构建），输出到 dist/
./mvnw clean package

# 前端开发与检查
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# 示例应用与 SDK
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# API 契约：服务端接口变更后重新生成 openapi.json 与前端类型（CI 会校验两者一致）
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# 文档站（docs/，Next.js + Tailwind CSS）
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # 用真实服务与示例数据重新生成文档截图

# 仓库检查（与 CI 相同）
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## 项目链接

- [项目仓库](https://github.com/devlive-community/grantforge)
- [文档站](https://grantforge.devlive.org)：快速开始、使用指南、应用接入与技术文档，源文件在 [`docs/`](docs/)
- [贡献指南](CONTRIBUTING.md) · [行为准则](CODE_OF_CONDUCT.md) · [更新日志](CHANGELOG)
