---
title: Docker、Compose 與 Helm
description: 用容器映像執行 GrantForge，用 Compose 搭配各種資料庫試用，用 Helm 部署到 Kubernetes。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 映像

每個發行版都會發佈映像 `ghcr.io/devlive-community/grantforge:<版本>`（linux/amd64 與 linux/arm64），正式版同時更新 `latest`：

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

映像由發行包建置，基於 `eclipse-temurin:21-jre`，以非特權使用者（UID 10001）執行，日誌輸出到主控台，不內建資料庫。也可以從原始碼自行建置：

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

映像慣例：

| 路徑 / 變數 | 說明 |
| --- | --- |
| `/opt/grantforge/data` | 卷：內建 H2 的資料檔案 |
| `/opt/grantforge/plugins` | 卷：服務類型外掛 |
| `/opt/grantforge/drivers` | 額外 JDBC 驅動程式（MySQL Connector/J 放這裡） |
| `9999` | 服務連接埠 |
| `HEALTHCHECK` | 存取 `/actuator/health/readiness` |

## Compose 範例

`deploy/compose/` 為每種資料庫準備了一個範例：`h2`、`postgres`、`mariadb`、`mysql`、`sqlserver`、`oracle`。

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

然後開啟 http://127.0.0.1:9999/。範例使用的預設資料庫密碼只適合試用，正式使用前請透過 `GRANTFORGE_DB_PASSWORD` 修改。MySQL 範例需要先把 `mysql-connector-j-<版本>.jar` 放進 `deploy/compose/drivers/`。

## Helm

`deploy/helm/grantforge` 是一個 Helm Chart：一個 StatefulSet 加外部資料庫，每個副本按 Pod 序號取得自己的 ID 節點編號（需要 Kubernetes 1.28 及以上）。

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

常用參數：

| 參數 | 預設值 | 說明 |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / Chart 的 appVersion | 映像 |
| `replicaCount` | `1` | 副本數，可以大於 1 |
| `database.url` / `username` / `password` | — | 資料庫連線；密碼建議放在 `existingSecret` |
| `setupToken` | 空 | 預先指定初始化權杖，空時印在日誌裡 |
| `encryptionKey` | 空 | 加密儲存金鑰（身分來源密碼、驗證器金鑰、簽章私鑰等）的 32 位元組 Base64 金鑰；空時自動產生並存入資料庫 |
| `cookieSecure` | `false` | TLS 在入口處終止時設為 `true`，工作階段 Cookie 總是帶 Secure |
| `ingress.*` | 關閉 | 暴露主控台與 API |
| `plugins.persistence.enabled` | `false` | 為外掛目錄掛載持久卷 |
| `podDisruptionBudget.enabled` | `false` | 多副本時建議開啟 |

> [!IMPORTANT]
> 生產環境請務必設定 `encryptionKey`。未設定時金鑰儲存在資料庫裡，拿到資料庫備份的人就能解開其中加密儲存的金鑰。
