---
title: 安装发行包
description: 在物理机或虚拟机上安装、启动、停止和升级 GrantForge 发行包。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 环境要求

| 项目 | 要求 |
| --- | --- |
| Java | 17 或更高版本（发行包按 Java 17 编译，推荐 21） |
| 内存 | 至少 1 GB，生产建议 2 GB 以上 |
| 数据库 | 试用可用内置 H2；生产使用 PostgreSQL、MySQL、MariaDB、Oracle 或 SQL Server，见 [数据库](/deploy/databases/) |
| 浏览器 | 最近两个大版本的 Chrome、Edge、Firefox、Safari |

## 目录结构

解压 `grantforge-release.tar.gz` 后得到 `grantforge/` 目录：

| 目录 | 内容 |
| --- | --- |
| `bin/` | `startup.sh`、`shutdown.sh`、`restart.sh`、`debug.sh` 与 `import-legacy.sh` |
| `configure/` | `application.properties`，在这里覆盖默认配置 |
| `lib/` | 服务端与依赖的 jar |
| `drivers/` | 额外的 JDBC 驱动（MySQL 需要自行放入） |
| `plugins/` | 服务类型插件，见 [插件与服务类型](/develop/plugins/) |
| `agents/` | 部署到目标系统的代理 jar，如 [Apache Hadoop HDFS NameNode 代理](/external/hdfs-agent/) |
| `data/` | 内置 H2 数据库文件（首次启动时创建） |
| `logs/` | `grantforge.log`；`console.out` 记录日志系统启动前的输出 |

## 启动与停止

```bash
bin/startup.sh     # 后台启动，进程号写入 pid 文件
bin/shutdown.sh    # 按 pid 文件优雅停止
bin/restart.sh     # 停止后再启动
bin/debug.sh       # 前台运行，日志同时输出到控制台，Ctrl+C 停止
```

脚本可以在任意目录执行，安装目录是脚本所在目录的上一级；也可以通过环境变量 `GRANTFORGE_HOME` 指定。

## 选择数据库

默认使用 `data/grantforge` 下的 H2 文件数据库，适合试用。生产环境在 `configure/application.properties` 或环境变量里指定数据库：

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

首次连接时，GrantForge 用 Liquibase 自动创建全部表；之后每次启动都会执行尚未执行的迁移。

## 首次初始化

首次启动会在日志中打印一次性初始化令牌，打开控制台填入令牌并创建第一个管理员即可，过程见 [五分钟上手](/start/quick-start/)。

## 健康检查与监控

| 地址 | 用途 |
| --- | --- |
| `/actuator/health/liveness` | 存活探针 |
| `/actuator/health/readiness` | 就绪探针：数据库可用、迁移完成后返回 200 |
| `/actuator/prometheus` | Prometheus 指标；默认需要登录，可用 `GRANTFORGE_PROMETHEUS_PUBLIC=true` 对受信网络开放 |

需要结构化日志时设置 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`（或 `logstash`）。每行日志都带有请求 ID，与接口错误响应中的 `requestId` 对应。

## 集群部署

多个实例可以共用一个数据库同时提供服务：会话保存在数据库中，任何实例都能处理任何请求。每个实例需要不同的 `GRANTFORGE_ID_NODE`（0–1023），它决定生成 ID 时使用的节点号。负载均衡器无需会话保持。

## 升级

停止服务，用新版本的 `lib/` 替换旧版本（保留 `configure/`、`data/`、`drivers/`、`plugins/`），再启动即可，数据库迁移会自动执行。升级前请备份数据库。从 1.x 升级见 [升级与旧版本迁移](/deploy/upgrade/)。
