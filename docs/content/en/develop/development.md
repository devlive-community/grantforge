---
title: Development, testing, and CI
description: Local builds, tests, coding standards, and CI checks.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Environment

- JDK 21 (artifacts are Java 17 bytecode; the policy engine is Java 8)
- Node.js 22 and pnpm 8.10.2
- Docker (database integration tests, performance benchmarks, and images)
- Python 3.12 (CI scripts)

## Common commands

```bash
./mvnw verify                          # 构建并测试全部 Java 模块（含控制台）
./mvnw verify -DskipFrontend           # 跳过控制台构建
bash script/ci/web.sh test             # 控制台单元测试
bash script/ci/web.sh e2e              # 控制台浏览器测试（模拟后端）
bash script/ci/e2e_fullstack.sh        # 打包、启动真实服务并运行全栈测试
bash script/ci/db_integration.sh postgres:17   # 在指定数据库上运行集成测试
bash script/ci/perf_benchmark.sh smoke # 小规模性能基准
```

For console development, run `pnpm dev` in `core/grantforge-web`; Vite proxies `/api` and other requests to the server at `localhost:9999`.

## Starting from an IDE

Run `org.devlive.grantforge.server.GrantForge` (module `grantforge-server`) directly; it uses the H2 database by default. The server automatically loads plugin modules built under the repository's `plugins/` directory (see [Plugins and service types](/en/develop/plugins/)); before using a plugin module for the first time, run `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` once to copy its dependencies.

## Coding standards

- Backend: Error Prone + NullAway (non-null by default, JSpecify `@Nullable` where nullable), Checkstyle, PMD, SpotBugs; ArchUnit guards shared conventions (no field injection, no native SQL, no entities in the API, no `Optional.get()`, and so on).
- Frontend: TypeScript strict mode, ESLint with zero warnings; all copy goes through i18n, keys must be literals, and the Chinese and English key sets are identical.
- Every source file carries the MIT license header; every main-source class must have a corresponding test class (exceptions are listed in `script/ci/test_mapping_exclusions.txt`).
- Coverage thresholds are set per module (`script/ci/coverage_thresholds.txt`).
- Database migrations are Liquibase YAML, one file per change, append-only (existing files are never modified); types use cross-database properties such as `${text}`.
- Commit messages follow Conventional Commits, with subjects no longer than 72 characters.

## CI

| Job | What it does |
| --- | --- |
| Repository hygiene | License headers, forbidden paths, test mapping, i18n, the permission manifest, file formatting, and script and workflow checks |
| Commit messages | Commit message format |
| CI script unit tests | Tests for the CI scripts themselves |
| Java 21 (tests and static analysis) | The gate: build and unit tests of all Java modules, coverage, NullAway, Checkstyle, SpotBugs, PMD, bytecode version checks |
| Java 17 / 25 / latest | The same unit tests on the other supported JDKs |
| Frontend | API types consistent with the contract, type checking and build, ESLint, unit tests, browser tests |
| JavaScript SDK | Type checking, build, ESLint, unit tests |
| Database | Migrations and integration tests on H2, PostgreSQL 14/17, MySQL 8.0/8.4, MariaDB 10.11/11.4, Oracle 23, and SQL Server 2022 |
| Plugin API compatibility | Compares the plugin contract against the previous release |
| Full-stack acceptance | Packages, starts the server, and runs full-stack browser tests on PostgreSQL |
| Docs | Checks, tests, and build for the documentation site |

Each job runs only when the change touches its area (`script/ci/changed_areas.py`): a documentation change builds no Java, a console change runs no database tests. The database, plugin API and full-stack jobs wait for the Java 21 gate and do not repeat what it checked: no unit tests, no JaCoCo, no enforcer and no Error Prone (`GRANTFORGE_CHECKED=1`, `script/ci/maven.sh`). The **CI result** job is the one check for branch protection. The nightly workflow runs the Testcontainers suites, the image smoke test and the performance benchmark for what changed in the last day, and all of them on Sundays; a release tag gets a complete CI run of its own, which the release waits for instead of testing again, and a release that failed half-way is finished by running it again. Security workflows scan dependencies and secrets.

## Releases

Versions are `year.minor.patch` (for example `2026.0.0`), with release candidates suffixed `-rc.N`. Versions in all poms, npm packages, the Helm Chart's appVersion, the console sidebar, and the README must match, and CI verifies them with `check_versions.py`.

Release from the `dev` branch with a single command:

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # 只检查并预览发布说明，不做任何修改
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # 设置版本、打标签 v2026.1.0 并推送，再把 dev 切到下一个版本
```

The script requires a clean working tree, a local branch not behind the remote, and a nonexistent tag; after confirmation it commits `chore(release): prepare <version>`, creates an annotated tag, and pushes. The tag triggers `release.yml`:

- `script/ci/release.sh` builds the distribution package, a CycloneDX SBOM containing only release dependencies, and `SHA256SUMS`;
- Multi-architecture images are pushed to `ghcr.io/devlive-community/grantforge`;
- Maven artifacts (including sources and Javadoc) are published to GitHub Packages; when the repository is configured with `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (a Central Portal token), `GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`, they are signed and published to Maven Central;
- A GitHub Release is created whose body lists all commits since the previous release (`v*` or a numeric tag such as `1.0.6`), grouped into features, fixes, performance, and so on, with links to the commits.

Release candidates are marked as pre-release and do not update the images' `latest` tag. Locally, the `central` profile does not publish by default (`central.skip=true`); only the release workflow explicitly passes `-Dcentral.skip=false`.

Before tagging, write the release log `docs/content/<language>/changelog/<version>.md` in every language and list it in `docs/lib/navigation.ts`; `tag.sh` refuses a release without it (release candidates excepted). The npm package publishes through trusted publishing: npm verifies a job-scoped OIDC token against the publisher configured for the package, so no `NPM_TOKEN` secret is needed. The release publishes what can be replaced (GitHub Packages, the image) before Maven Central and npm, and the GitHub release last; a release that failed half-way is finished by re-running the workflow, which skips what is published already.

## Permission manifest

The console's pages, buttons, and the APIs they need are declared in `core/grantforge-web/src/permissions/`. `check_permission_manifest.py` ensures that every declared API exists and that every endpoint requiring permissions is covered by some button or page (exceptions for direct calls are listed in `script/ci/permission_direct_apis.txt`).

## Documentation

This site lives in `docs/` and uses Next.js static export with Tailwind CSS:

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # 页面、链接与图片检查
pnpm build      # 输出到 docs/out
```

Pages are Markdown under `docs/content/`, and navigation is defined in `docs/lib/navigation.ts`. The API reference and error codes are generated at build time from the contract and the source code. Screenshots are produced by `script/docs/screenshots.sh`, which starts the real server, seeds sample data, and drives Playwright.
