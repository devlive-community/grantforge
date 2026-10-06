---
title: 升级与旧版本迁移
description: 2.x 版本之间的升级，以及从 1.x（AuthX / GrantForge 1.x）迁移账号、角色与菜单。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 2.x 版本之间升级

1. 备份数据库（以及 `plugins/`、`configure/`）。
2. 停止服务：`bin/shutdown.sh`。
3. 用新版本发行包的 `lib/` 与 `bin/` 替换旧的。
4. 启动：`bin/startup.sh`。Liquibase 会自动执行新版本的数据库迁移，就绪探针在迁移完成后才返回 200。

集群升级时先停止全部实例再启动新版本，避免新旧版本同时读写。已经发布的迁移不会被修改，每个版本都在全部支持的数据库上验证过“从上一版本升级”。

## 从 1.x 迁移

1.x 把数据保存在另一套表里，2.x 不会读取它们。迁移方式是：在**新的数据库**上安装 2.x 并完成初始化，停止服务，然后把旧库的账号、角色和菜单导入某个租户：

```bash
# 先预演：只生成报告，不写入
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
# 确认报告后正式导入
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

两条命令都会写出 `logs/legacy-import-report.json`，列出已经（或将要）创建的内容、被跳过的内容及原因，以及每个旧对象对应的新 ID。旧数据库的 JDBC 驱动放进 `drivers/`，密码通过 `GRANTFORGE_LEGACY_SOURCE_PASSWORD` 提供（不提供时脚本会询问）。重复执行导入只会补充缺少的内容。

导入规则：

- **账号**保留原密码，首次登录时自动换成新的哈希算法。不符合 2.x 用户名规则（3–64 个字母、数字或 `._@-`）、没有密码，或用户名已被其他租户占用的账号会被跳过。
- **角色**保留名称，编码转为小写（`GLY` → `gly`）。
- **菜单**成为 `legacy` 应用的资源：`#` 地址的菜单作为分组，其他地址作为页面，页面下的菜单作为按钮。菜单地址及其 HTTP 方法成为 API 资源 `api:<方法>:<路径>`；以 `*` 结尾的地址变为 `<路径>/**`，按路径段而不是字符前缀匹配，报告会逐条列出供核对。
- 只迁移**显式授权**：1.x 允许任何人访问登记为菜单的地址，2.x 不会这样做。

> [!WARNING]
> 导入前请先在报告里核对被跳过的账号和通配地址，再执行 `--apply`。
