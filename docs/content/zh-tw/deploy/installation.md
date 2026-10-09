---
title: 安裝發行包
description: 在實體機或虛擬機上安裝、啟動、停止和升級 GrantForge 發行包。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 環境需求

| 項目 | 要求 |
| --- | --- |
| Java | 17 或更高版本（發行包按 Java 17 編譯，推薦 21） |
| 記憶體 | 至少 1 GB，生產建議 2 GB 以上 |
| 資料庫 | 試用可用內建 H2；生產使用 PostgreSQL、MySQL、MariaDB、Oracle 或 SQL Server，見 [資料庫](/zh-tw/deploy/databases/) |
| 瀏覽器 | 最近兩個大版本的 Chrome、Edge、Firefox、Safari |

## 目錄結構

解壓縮 `grantforge-release.tar.gz` 後取得 `grantforge/` 目錄：

| 目錄 | 內容 |
| --- | --- |
| `bin/` | `startup.sh`、`shutdown.sh`、`restart.sh`、`debug.sh` 與 `import-legacy.sh` |
| `configure/` | `application.properties`，在這裡覆寫預設設定 |
| `lib/` | 服務端與依賴的 jar |
| `drivers/` | 額外的 JDBC 驅動程式（MySQL 需要自行放入） |
| `plugins/` | 服務類型外掛，見 [外掛與服務類型](/zh-tw/develop/plugins/) |
| `agents/` | 部署到目標系統的代理 jar，如 [Apache Hadoop HDFS NameNode 代理](/zh-tw/external/hdfs-agent/) |
| `data/` | 內建 H2 資料庫檔案（首次啟動時建立） |
| `logs/` | `grantforge.log`；`console.out` 記錄日誌系統啟動前的輸出 |

## 啟動與停止

```bash
bin/startup.sh     # 背景啟動，行程編號寫入 pid 檔案
bin/shutdown.sh    # 按 pid 檔案優雅停止
bin/restart.sh     # 停止後再啟動
bin/debug.sh       # 前景執行，日誌同時輸出到主控台，Ctrl+C 停止
```

指令碼可以在任意目錄執行，安裝目錄是指令碼所在目錄的上一層；也可以透過環境變數 `GRANTFORGE_HOME` 指定。

## 選擇資料庫

預設使用 `data/grantforge` 下的 H2 檔案資料庫，適合試用。生產環境在 `configure/application.properties` 或環境變數裡指定資料庫：

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

首次連線時，GrantForge 用 Liquibase 自動建立全部資料表；之後每次啟動都會執行尚未執行的遷移。

## 首次初始化

首次啟動會在日誌中列印一次性的初始化權杖，開啟主控台填入權杖並建立第一個管理員即可，過程見 [五分鐘上手](/zh-tw/start/quick-start/)。

## 健康檢查與監控

| 位址 | 用途 |
| --- | --- |
| `/actuator/health/liveness` | 存活探針 |
| `/actuator/health/readiness` | 就緒探針：資料庫可用、遷移完成後回傳 200 |
| `/actuator/prometheus` | Prometheus 指標；預設需要登入，可用 `GRANTFORGE_PROMETHEUS_PUBLIC=true` 對受信網路開放 |

需要結構化日誌時設定 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`（或 `logstash`）。每行日誌都帶有請求 ID，與介面錯誤回應中的 `requestId` 對應。

## 叢集部署

多個執行個體可以共用一個資料庫同時提供服務：工作階段儲存在資料庫中，任何執行個體都能處理任何請求。每個執行個體需要不同的 `GRANTFORGE_ID_NODE`（0–1023），它決定產生 ID 時使用的節點編號。負載平衡器無需維持工作階段。

## 升級

停止服務，用新版本的 `lib/` 替換舊版本（保留 `configure/`、`data/`、`drivers/`、`plugins/`），再啟動即可，資料庫遷移會自動執行。升級前請備份資料庫。從 1.x 升級見 [升級與舊版本遷移](/zh-tw/deploy/upgrade/)。
