<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge logo" />

# GrantForge

統一權限平台 · 使用者、角色、選單、介面、資料列與欄位授權 · 外部資料系統

語言：[English](README.md) · [简体中文](README.zh-CN.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · 繁體中文 · [Deutsch](README.de.md) · [法文](README.fr.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.0.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge（原為 AuthX）是一個開源（MIT）的統一權限平台。它集中回答兩個問題：**誰能做什麼**（功能授權）與**誰能看到哪些資料**（資料與欄位授權）。權限在主控台中定義、解釋與稽核，業務應用程式透過標準協定接入，外部資料系統（例如 HDFS）則透過外掛程式與代理納入同一套策略體系。

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="GrantForge 主控台" />
</p>

## 功能

| 領域 | 功能 |
| --- | --- |
| 身分與組織 | 多租戶、部門樹、使用者群組與職位；CSV 批次匯入匯出；LDAP / Active Directory 登入與同步、OIDC 聯合登入 |
| 帳號安全 | 工作階段管理與強制登出、密碼策略與失敗鎖定、TOTP 兩步驟驗證與復原碼、敏感操作二次驗證 |
| 功能授權 | 資源目錄（模組、選單、頁面、標籤、按鈕、API）、角色繼承、授權矩陣、授權影響分析 |
| 資料權限 | 依條件限定可見的資料列（本人、所屬部門及下級、指定部門、自訂條件），讀取與寫入分開控制 |
| 欄位權限 | 欄位可隱藏、可遮罩（電子郵件、手機號碼、證件號碼等）、可唯讀 |
| 可解釋與稽核 | 權限解釋（每項權限的來源）、授權模擬、稽核日誌查詢與匯出 |
| 治理 | 職責分離（SOD）約束、權限申請與核准、定期權限複核 |
| 應用程式接入 | OAuth 2.1 / OIDC 授權伺服器、權限查詢開放 API、Java（Spring Boot Starter）與 JavaScript SDK |
| 外部系統 | 外掛程式化的服務類型與策略引擎：資料服務、存取策略、代理與存取稽核 |
| 交付 | 單一可執行的發行套件、Docker 映像、Compose 範例、Helm Chart；H2、PostgreSQL、MySQL、MariaDB、Oracle、SQL Server |

外部系統支援目前提供外掛程式框架、通用策略編輯器、策略簽章分發、存取稽核，以及 HDFS 服務類型外掛程式與 Hadoop 3.5.0 NameNode 代理；Hive 外掛程式與其他 Hadoop 版本的代理仍在開發中。

## 運作原理：兩個平面

- **管理平面**：GrantForge 伺服器端（Spring Boot 4.1，Java 17 位元組碼）與 Vue 3 主控台，負責租戶、帳號、組織、角色、授權、稽核，以及資料服務與策略的維護。
- **資料平面**：嵌入受保護系統中的代理。代理以權杖定期從伺服器端拉取帶有 Ed25519 簽章的策略快照並快取在本機，在每次存取之前判定允許或拒絕（策略無法連線時預設拒絕），同時將存取事件回傳伺服器端稽核。

自己的系統不必照抄 HDFS 的做法：一般業務應用程式使用開放 API 或 Spring Boot Starter 在行程內求值即可，只有需要在資料庫、檔案系統等儲存系統內部攔截存取時，才需要用 `core/grantforge-agent-core` 編寫嵌入目標系統的代理。

## 串接你的應用程式

- **OAuth 2.1 / OpenID Connect**：GrantForge 本身就是授權伺服器，應用程式用它登入使用者；既有的身分來源（LDAP / AD / OIDC）也可以接進來。
- **Java 應用程式**：`sdk/grantforge-spring-boot-starter` 提供 `@RequirePermission` 介面鑑權、`@GrantForgeEntity` 宣告資料實體，以及 `GrantForgeDataScopes.scope(...)` 把平台上的資料權限轉成 JPA `Specification`。
- **前端應用程式**：`@grantforge/client` 用 OIDC + PKCE 從你自己的網域引導使用者登入，並查詢該使用者的權限。
- **開放 API**：`/api/v1/open/me/authorization`、`/api/v1/open/me/data-access`、`/api/v1/open/catalog/data-entities`。
- **可執行的範例**：`samples/` 下的 `shop` 與 `notes` 兩個應用程式以第三方的方式串接。

## 快速開始

需要 Java 17 或更高版本。服務預設監聽 `9999` 埠，首次啟動會在日誌中列印一次性的**初始化權杖**；用瀏覽器開啟 <http://127.0.0.1:9999/>，填入權杖並建立第一個管理員即可。

```bash
# 使用發行套件（或從原始碼 ./mvnw clean package 建置，產物在 dist/）
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# 或使用 Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# 或用 Compose 搭配資料庫
docker compose -f deploy/compose/postgres.yml up -d

# 或部署到 Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

預設使用內嵌 H2 檔案庫，無需任何設定即可啟動。MySQL 驅動因 GPL 授權不隨發行套件分發，需要自行放進 `drivers/`。安裝、初始化與第一次授權的詳細步驟見[文件站](https://grantforge.devlive.org)。

## 資料庫

預設使用內嵌 H2 檔案庫（`${GRANTFORGE_HOME}/data`），無需任何設定即可啟動。正式環境透過環境變數切換，庫表結構由 Liquibase 統一管理：

| 資料庫 | 版本（CI 驗證） | `GRANTFORGE_DB_URL` 範例 |
| --- | --- | --- |
| PostgreSQL | 14、17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0、8.4 | `jdbc:mysql://host:3306/grantforge`（需自行將 `mysql-connector-j` 放入 `lib/`，其 GPL 授權不隨發行套件分發） |
| MariaDB | 10.11、11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

同時設定 `GRANTFORGE_DB_USER`、`GRANTFORGE_DB_PASSWORD`；叢集部署時每個執行個體必須設定不同的 `GRANTFORGE_ID_NODE`（0-1023）。

## 專案結構

Maven 根座標：`org.devlive.grantforge:grantforge:2026.0.0`。Java 套件前綴：`org.devlive.grantforge`。啟動類別：`org.devlive.grantforge.server.GrantForge`。

`core/` 包含服務端與共用基礎設施，`plugins/` 包含服務端載入的服務類型外掛程式，`agents/` 包含部署在受保護系統內的具體代理。共用的 `grantforge-agent-core` 函式庫保留在 `core/`，HDFS NameNode 代理位於 `agents/grantforge-agent-hdfs`。

| 模組 | 職責 |
| --- | --- |
| `core/grantforge-server` | Spring Boot 服務入口：REST API、安全設定、開放 API，並託管 Web 管理介面 |
| `core/grantforge-web` | Vue 3 / TypeScript / Tailwind CSS 管理介面 |
| `core/grantforge-common` | 錯誤碼與 problem details 模型、CSV、介面存取註解 |
| `core/grantforge-persistence` | 實體、租戶過濾、TSID、Liquibase、資料與欄位權限 SPI |
| `core/grantforge-audit` | 稽核事件的記錄、查詢、保留與歸檔 |
| `core/grantforge-identity` | 租戶、帳號、部門、使用者群組、職位、登入與工作階段、兩步驟驗證、身分來源 |
| `core/grantforge-authz` | 資源目錄、角色、授權、分配與求值、資料與欄位策略、職責分離、申請與複核 |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | 服務類型外掛程式的契約，以及外掛程式的載入、隔離與呼叫 |
| `core/grantforge-policy-engine` | 外部系統策略的求值引擎（Java 8 API，可嵌入代理） |
| `core/grantforge-agent-core` | 代理的共用程式碼：設定、簽章快照、存取判定、稽核回報 |
| `core/grantforge-service` | 資料服務、策略快照簽章與分發、代理與存取稽核 |
| `core/grantforge-oauth` | 基於 Spring Authorization Server 的 OAuth 2.1 / OIDC 伺服器 |
| `plugins/grantforge-plugin-hdfs` | HDFS 服務類型外掛程式：策略管理與資源查詢 |
| `plugins/grantforge-plugin-example` | 自訂服務類型的範例外掛程式 |
| `agents/grantforge-agent-hdfs` | Hadoop 3.5.0 NameNode 代理：覆蓋式授權與存取稽核 |
| `sdk/grantforge-spring-boot-starter`、`sdk/grantforge-js` | Java 與 JavaScript 應用程式串接 SDK |
| `script/ci`、`deploy/` | CI 檢查腳本（本機與 CI 使用同一腳本）與部署資源（Dockerfile、Compose、Helm） |

## 營運與可觀測性

- 健康探測：`/actuator/health/liveness`、`/actuator/health/readiness`（僅回傳狀態，不揭露細節；就緒探測在資料庫可用且遷移完成後才回傳 200）。
- 指標：`/actuator/prometheus`（帶有 `application="grantforge"` 標籤，預設需要登入，可用 `GRANTFORGE_PROMETHEUS_PUBLIC=true` 對受信任網路開放）。
- 日誌：預設為易讀的文字，每行帶有請求編號；設定 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`（或 `logstash`）輸出 JSON 日誌。
- 發行套件腳本：`bin/` 下的 `startup.sh`、`shutdown.sh`、`restart.sh`、`debug.sh` 與 `import-legacy.sh`。

## 開發與驗證

建置需要 JDK 17 或更高版本（產物目標為 Java 17 位元組碼，策略引擎為 Java 8）；在 JDK 21+ 上會自動啟用 Error Prone + NullAway 空值檢查。前端使用 Vue 3.5、Tailwind CSS 4、Node.js 22.12+ 和 pnpm 8.10.2。

```sh
# Java 建置與單元測試（跳過前端建置）
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# 在指定資料庫上執行持久化整合測試（需要 Docker，h2 除外）
bash script/ci/db_integration.sh postgres:17

# 打包發行套件（包含前端建置），輸出到 dist/
./mvnw clean package

# 前端開發與檢查
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# 範例應用程式與 SDK
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# API 契約：服務端介面變更後重新產生 openapi.json 與前端型別（CI 會驗證兩者一致）
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# 文件站（docs/，Next.js + Tailwind CSS）
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # 用真實服務與範例資料重新產生文件截圖

# 儲存庫檢查（與 CI 相同）
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## 專案連結

- [專案儲存庫](https://github.com/devlive-community/grantforge)
- [文件站](https://grantforge.devlive.org)：快速開始、使用指南、應用程式串接與技術文件，原始檔在 [`docs/`](docs/)
- [貢獻指南](CONTRIBUTING.md) · [行為準則](CODE_OF_CONDUCT.md) · [更新日誌](CHANGELOG)
