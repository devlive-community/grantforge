---
title: 开发、测试与 CI
description: 本地构建、测试、代码规范与 CI 检查。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 环境

- JDK 21（产物为 Java 17 字节码；策略引擎为 Java 8）
- Node.js 22 与 pnpm 8.10.2
- Docker（数据库集成测试、性能基准与镜像）
- Python 3.12（CI 脚本）

## 常用命令

```bash
./mvnw verify                          # 构建并测试全部 Java 模块（含控制台）
./mvnw verify -DskipFrontend           # 跳过控制台构建
bash script/ci/web.sh test             # 控制台单元测试
bash script/ci/web.sh e2e              # 控制台浏览器测试（模拟后端）
bash script/ci/e2e_fullstack.sh        # 打包、启动真实服务并运行全栈测试
bash script/ci/db_integration.sh postgres:17   # 在指定数据库上运行集成测试
bash script/ci/perf_benchmark.sh smoke # 小规模性能基准
```

控制台开发时运行 `pnpm dev`（`core/grantforge-web`），Vite 把 `/api` 等请求代理到 `localhost:9999` 的服务。

## 在 IDE 中启动

直接运行 `org.devlive.grantforge.server.GrantForge`（模块 `grantforge-server`），默认使用 H2 数据库。服务端会自动加载仓库 `plugins/` 下构建过的插件模块（见 [插件与服务类型](/develop/plugins/)）；插件模块第一次使用前执行一次 `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` 复制它的依赖。

## 代码规范

- 后端：Error Prone + NullAway（默认非空，可空处用 JSpecify `@Nullable`）、Checkstyle、PMD、SpotBugs；ArchUnit 守护共同的约定（不用字段注入、不写原生 SQL、实体不出现在 API 中、不允许 `Optional.get()` 等）。
- 前端：TypeScript 严格模式、ESLint 零警告；文案全部走 i18n，键必须是字面量，中英文键完全一致。
- 每个源文件都有 MIT 许可证头；每个主代码类都要有对应的测试类（例外登记在 `script/ci/test_mapping_exclusions.txt`）。
- 覆盖率按模块设置门槛（`script/ci/coverage_thresholds.txt`）。
- 数据库迁移是 Liquibase YAML，一个变更一个文件，只能新增不能修改；类型使用 `${text}` 等跨库属性。
- 提交信息遵循 Conventional Commits，标题不超过 72 个字符。

## CI

| 作业 | 内容 |
| --- | --- |
| Repository hygiene | 许可证头、禁止的路径、测试映射、i18n、权限清单、文件格式、脚本与工作流检查 |
| Commit messages | 提交信息格式 |
| CI script unit tests | CI 脚本自身的测试 |
| Java 17 / 21 / 25 / latest | 全部 Java 模块的构建与测试，字节码版本检查 |
| Java static analysis | 覆盖率、Checkstyle、SpotBugs、PMD |
| Frontend | API 类型与契约一致、类型检查与构建、ESLint、单元测试、浏览器测试 |
| JavaScript SDK | 类型检查、构建、ESLint、单元测试 |
| Database | H2、PostgreSQL 14/17、MySQL 8.0/8.4、MariaDB 10.11/11.4、Oracle 23、SQL Server 2022 上的迁移与集成测试 |
| Plugin API compatibility | 与上一个发行版比较插件契约 |
| Full-stack acceptance | 在 PostgreSQL 上打包、启动服务并运行全栈浏览器测试 |
| Docs | 文档站的检查、测试与构建 |

每晚另外运行性能基准；安全工作流扫描依赖与密钥。

## 发布

版本号为 `年.次版本.修订`（如 `2026.0.0`），候选版加 `-rc.N`。所有 pom、npm 包、Helm Chart 的 appVersion、控制台侧栏与 README 中的版本必须一致，CI 用 `check_versions.py` 检查。

在 `dev` 分支上用一条命令发布：

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # 只检查并预览发布说明，不做任何修改
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # 设置版本、打标签 v2026.1.0 并推送，再把 dev 切到下一个版本
```

脚本要求工作区干净、本地分支不落后于远端、标签不存在，确认后提交 `chore(release): prepare <版本>`、创建带注释的标签并推送。标签触发 `release.yml`：

- `script/ci/release.sh` 构建发行包、只含发行依赖的 CycloneDX SBOM 与 `SHA256SUMS`；
- 多架构镜像推送到 `ghcr.io/devlive-community/grantforge`；
- Maven 构件（含源码包与 Javadoc）发布到 GitHub Packages；仓库配置了 `CENTRAL_USERNAME`、`CENTRAL_PASSWORD`（Central Portal 令牌）、`GPG_PRIVATE_KEY` 与 `GPG_PASSPHRASE` 时，签名后发布到 Maven Central；
- 创建 GitHub Release，正文是上一个发布版本（`v*` 或 `1.0.6` 这样的数字标签）以来的全部提交，按新功能、问题修复、性能等分组，附提交链接。

候选版标记为预发布，不更新镜像的 `latest`。本地启用 `central` profile 默认不会发布（`central.skip=true`），只有发布工作流显式传入 `-Dcentral.skip=false`。

## 权限清单

控制台的页面、按钮与它们需要的 API 在 `core/grantforge-web/src/permissions/` 中声明。`check_permission_manifest.py` 确保每个声明的 API 都存在、每个需要权限的接口都被某个按钮或页面覆盖（直接调用的例外登记在 `script/ci/permission_direct_apis.txt`）。

## 文档

本站位于 `docs/`，使用 Next.js 静态导出与 Tailwind CSS：

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # 页面、链接与图片检查
pnpm build      # 输出到 docs/out
```

页面是 `docs/content/` 下的 Markdown，导航在 `docs/lib/navigation.ts`。API 参考与错误码在构建时从契约与源代码生成。截图由 `script/docs/screenshots.sh` 启动真实服务、写入示例数据后用 Playwright 生成。
