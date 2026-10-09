---
title: 性能与基准
description: 百万账号规模的性能目标、基准测试的数据与方法，以及如何在本地运行。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge 的性能目标针对一个大型组织：**一百万账号、一万个角色、十万个资源**。每晚在 PostgreSQL 17 上运行系统基准，任何指标超过上限都会让构建失败。

## 目标

| 指标 | 含义 | 上限 |
| --- | --- | --- |
| `authz.snapshot.hit` | 已缓存的权限快照（每次接口调用都会用到） | p99 1 ms |
| `authz.snapshot.build` | 首次计算一个账号的控制台快照 | p95 50 ms |
| `authz.snapshot.app` | 在大型资源树的应用中计算快照 | p95 50 ms |
| `api.users.page` | 用户列表的一页（前 500 页内） | p95 200 ms |
| `api.users.search` | 按名称搜索用户 | p95 200 ms |
| `api.groups.page` | 用户组列表的一页 | p95 200 ms |
| `api.roles.list` | 角色列表（不分页） | p95 200 ms |
| `api.me.authorization` | 当前账号的权限 | p95 200 ms |
| `api.member.groups` | 只有一个页面权限的账号访问受保护列表 | p95 200 ms |
| `jmh.derivation.*` | 为大型资源树准备推导、推导二十条授权（JMH） | 平均 50 / 5 ms |

上限只允许收紧；放宽任何一项都需要记录在案的决策。

## 数据

基准启动真实的服务、完成初始化，再通过应用自己的实体写入：

- 100 个部门、每千个账号一个用户组、100 个岗位；
- 每个账号属于一个部门和一个用户组，每十个账号有一个岗位，每二十个账号有直接分配的角色；
- 每十个角色有一个继承自前一百个角色之一；每个用户组三个角色、每个部门两个、每个岗位一个；每个角色授权五个控制台页面；
- 一个应用：若干模块下一百个页面，每个页面九个操作；十分之一的角色被授权其中二十个页面与操作。

每个指标预热后逐个调用测量。

## 在本地运行

```bash
bash script/ci/perf_benchmark.sh full            # 目标规模，postgres:17（需要 Docker），检查上限
bash script/ci/perf_benchmark.sh smoke           # H2 上的小规模，只确认基准能运行
bash script/ci/perf_benchmark.sh full mysql:8.4  # 其他数据库
```

报告写入 `perf/target/perf-report.json`，日志中同时输出表格。可以用 `PERF_OPTS` 传入额外参数：

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2` 或 `<引擎>:<版本>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | 数据规模 |
| `perf.samples` / `perf.warmup` | 1000 / 200 | 每个指标测量与预热的次数 |
| `perf.jmh` | `true` | 同时运行 JMH 基准 |
| `perf.enforce` | `true` | 超过上限时以 1 退出 |

## 为什么能快

- 权限快照按账号缓存，并按目录与租户的版本号整体失效，命中时只是一次内存读取。
- 推导在内存中进行：资源树、继承关系与分配预先加载为紧凑的结构，避免逐条查询。
- 列表接口全部分页，排序与过滤都落在有索引的列上；延迟关联按 64 个一批加载，写入按 50 条一批提交（ID 由应用生成，因此可以批量插入）。
