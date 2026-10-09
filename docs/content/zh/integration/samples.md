---
title: 示例应用
description: 仓库里的两个完整示例：浏览器直连的商店，和服务端登录的笔记。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`samples/` 下有两个可运行的应用，它们和 GrantForge 一起由端到端测试覆盖，是接入时最好的参照。

| 示例 | 端口 | 展示的接入方式 |
| --- | --- | --- |
| `samples/shop` | 19081 | 浏览器用公开客户端 + PKCE 跨源登录；按资源显示按钮；后端用 `@RequirePermission` 校验 API；`@GrantForgeEntity` 声明订单实体，按“本人”“本租户”数据范围查询 |
| `samples/notes` | 19082 | 服务端用 Spring Security `oauth2Login`（机密客户端 + PKCE）登录；自定义 `AccessTokenResolver` 从会话里取令牌；只对有权限的人显示“写笔记” |

## 运行

示例是独立的 Maven 构建，依赖本仓库的 starter 与 JavaScript SDK：

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

示例的配置全部来自环境变量：

| 变量 | 说明 |
| --- | --- |
| `GRANTFORGE_URL` | GrantForge 的地址 |
| `SHOP_BROWSER_CLIENT_ID` | 商店浏览器端的公开客户端 |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | 商店后端声明数据实体用的机密客户端（`catalog` scope） |
| `SHOP_SDK_DIRECTORY` | JavaScript SDK 的构建产物目录（`sdk/grantforge-js/dist`） |

在 GrantForge 里需要为两个应用建好资源、客户端、角色和数据策略，端到端测试的准备脚本 `core/grantforge-web/tests/samples/setup.ts` 完整演示了这些步骤，可以直接参考。

## 端到端测试

`script/ci/e2e_fullstack.sh` 在全栈测试之后构建并启动两个示例，验证：跨源 PKCE 登录与 CORS、按钮显隐、接口 403、“本人”和“本租户”数据范围、删除与退出，以及笔记应用的服务端登录。设置 `GRANTFORGE_E2E_SKIP_SAMPLES=1` 可以跳过。
