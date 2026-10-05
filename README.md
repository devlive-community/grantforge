<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge logo" />

# GrantForge

开源权限管理平台 · 用户、角色、菜单与接口授权

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.0.0-4F46E5)

</div>

GrantForge（原 AuthX）是开源（MIT）的插件化细粒度权限平台，目标覆盖页面、按钮、API、数据行与字段级授权，并可通过插件接管外部系统（如 HDFS、Hive）的权限。当前版本号为 `2026.0.0`。

> `rebuild` 分支正在基于 Spring Boot 4.1 / Java 17 整体重建。重建期间旧版后端保留在 `dev` 分支与 `legacy-2026.0.0` 标签中；在新接口完成前，Web 管理界面的数据功能不可用。

## 项目结构

| 模块 | 职责 |
| --- | --- |
| `core/grantforge-server` | Spring Boot 服务入口，提供 REST API 并托管 Web 管理界面 |
| `core/grantforge-web` | Vue 3 / TypeScript / Tailwind CSS 管理界面 |
| `script/ci` | CI 检查脚本（许可证头、格式、测试映射、安全扫描等），本地与 CI 使用同一脚本 |

Maven 根坐标：`org.devlive.grantforge:grantforge:2026.0.0`。Java 包前缀：`org.devlive.grantforge`。启动类：`org.devlive.grantforge.server.GrantForge`。

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

## 运维与可观测性

- 健康探针：`/actuator/health/liveness`、`/actuator/health/readiness`（仅返回状态，不暴露细节）。
- 指标：`/actuator/prometheus`（带 `application="grantforge"` 标签）。
- 日志：默认可读文本，每行带请求编号；设置 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`（或 `logstash`）输出 JSON 日志。

## 开发与验证

构建需要 JDK 17 或更高版本（产物目标为 Java 17）；在 JDK 21+ 上会自动启用 Error Prone + NullAway 空值检查。前端使用 Vue 3.5、Tailwind CSS 4、Node.js 22.12+ 和 pnpm 8.10.2。

```sh
# Java 构建与单元测试（跳过前端构建）
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
- [文档站](https://authx.devlive.org)：快速开始、使用指南、应用接入与技术文档，源文件在 [`docs/`](docs/)
