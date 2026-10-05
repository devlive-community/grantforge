---
title: 五分钟上手
description: 用发行包或 Docker 启动 GrantForge，完成初始化，创建用户并授予第一个角色。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

本文用默认的嵌入式 H2 数据库在本机启动 GrantForge。生产环境请参考 [安装发行包](/start/installation/) 与 [数据库](/start/databases/)。

## 1. 启动服务

需要 Java 17 或更高版本。从 [GitHub Releases](https://github.com/devlive-community/grantforge/releases) 下载发行包，或在源码目录执行 `./mvnw -DskipTests package` 自行构建（产物在 `dist/grantforge-release.tar.gz`），然后解压并启动：

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

也可以用 Docker：先从发行包构建镜像，再用 Compose 示例启动：

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

服务默认监听 `9999` 端口。首次启动会创建数据库表，并在日志中打印一次性的**初始化令牌**：

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

发行包的日志在 `logs/grantforge.log`，Docker 用 `docker compose logs` 查看。

## 2. 完成初始化

浏览器打开 http://127.0.0.1:9999/，控制台会自动进入初始化页面。填写日志中的令牌、组织名称以及第一个管理员的用户名和密码（至少 12 个字符）。

![首次初始化与登录页](/screenshots/login.png)

> [!TIP]
> 自动化安装时，可以用环境变量 `GRANTFORGE_SETUP_TOKEN` 预先指定令牌，见 [配置参考](/start/configuration/)。

初始化完成后，这个管理员同时持有**租户管理员**和**平台管理员**两个系统角色，可以使用控制台的全部功能。初始化页面此后永久关闭。

## 3. 创建用户

进入 **访问控制 → 用户管理**，点击“创建用户”，填写用户名、初始密码和主部门。新用户首次登录时必须修改密码。

## 4. 创建角色并授权

1. 进入 **访问控制 → 角色管理**，点击“新建角色”，例如“只读审计员”。
2. 在角色行上点击“授权”，在授权矩阵里勾选“审计日志”页面。矩阵会自动带出该页面需要的 API。
3. 点击“分配”，把角色分配给刚才的用户。

![角色管理](/screenshots/roles.png)

## 5. 验证效果

用新用户登录：左侧菜单只出现“审计日志”。回到管理员账号，在用户行上点击“查看有效权限”图标，可以看到他每一项权限的来源。

## 下一步

- 了解 [核心概念](/start/concepts/)。
- 按 [使用指南](/guide/console/) 熟悉每个菜单。
- 让你的应用 [接入 GrantForge](/integration/overview/)。
