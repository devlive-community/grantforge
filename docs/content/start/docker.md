---
title: Docker、Compose 与 Helm
description: 用容器镜像运行 GrantForge，用 Compose 搭配各种数据库试用，用 Helm 部署到 Kubernetes。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 镜像

每个发行版都会发布镜像 `ghcr.io/devlive-community/grantforge:<版本>`（linux/amd64 与 linux/arm64），正式版同时更新 `latest`：

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

镜像由发行包构建，基于 `eclipse-temurin:21-jre`，以非特权用户（UID 10001）运行，日志输出到控制台，不内置数据库。也可以从源码自己构建：

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

镜像约定：

| 路径 / 变量 | 说明 |
| --- | --- |
| `/opt/grantforge/data` | 卷：内置 H2 的数据文件 |
| `/opt/grantforge/plugins` | 卷：服务类型插件 |
| `/opt/grantforge/drivers` | 额外 JDBC 驱动（MySQL Connector/J 放这里） |
| `9999` | 服务端口 |
| `HEALTHCHECK` | 访问 `/actuator/health/readiness` |

## Compose 示例

`deploy/compose/` 为每种数据库准备了一个示例：`h2`、`postgres`、`mariadb`、`mysql`、`sqlserver`、`oracle`。

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

然后打开 http://127.0.0.1:9999/。示例使用的默认数据库密码只适合试用，正式使用前请通过 `GRANTFORGE_DB_PASSWORD` 修改。MySQL 示例需要先把 `mysql-connector-j-<版本>.jar` 放进 `deploy/compose/drivers/`。

## Helm

`deploy/helm/grantforge` 是一个 Helm Chart：一个 StatefulSet 加外部数据库，每个副本按 Pod 序号获得自己的 ID 节点号（需要 Kubernetes 1.28 及以上）。

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

常用参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / Chart 的 appVersion | 镜像 |
| `replicaCount` | `1` | 副本数，可以大于 1 |
| `database.url` / `username` / `password` | — | 数据库连接；密码建议放在 `existingSecret` |
| `setupToken` | 空 | 预先指定初始化令牌，空时打印在日志里 |
| `encryptionKey` | 空 | 加密存储密钥（身份源密码、验证器密钥、签名私钥等）的 32 字节 Base64 密钥；空时自动生成并存入数据库 |
| `cookieSecure` | `false` | TLS 在入口处终止时设为 `true`，会话 Cookie 总是带 Secure |
| `ingress.*` | 关闭 | 暴露控制台与 API |
| `plugins.persistence.enabled` | `false` | 为插件目录挂载持久卷 |
| `podDisruptionBudget.enabled` | `false` | 多副本时建议开启 |

> [!IMPORTANT]
> 生产环境请务必设置 `encryptionKey`。未设置时密钥保存在数据库里，拿到数据库备份的人就能解开其中加密保存的密钥。
