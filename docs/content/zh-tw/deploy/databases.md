---
title: 資料庫
description: 支援的資料庫與版本、連線方式、驅動程式與多資料庫注意事項。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 支援範圍

| 資料庫 | 驗證的版本 | 驅動程式 |
| --- | --- | --- |
| H2 | 隨版本內建 | 內建，僅建議試用 |
| PostgreSQL | 14、17 | 內建 |
| MySQL | 8.0、8.4 | 需要自行放入 `drivers/`（Connector/J 採用 GPL 授權，不隨發行包分發） |
| MariaDB | 10.11、11.4 | 內建 |
| Oracle | Free 23 | 內建 |
| SQL Server | 2022 | 內建 |

每個版本都在 CI 中執行「空庫初始化 + 全部整合測試」。國產資料庫（達夢、金倉、openGauss、OceanBase 等）不在支援範圍內。

## 連線範例

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle（服務名稱）
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

使用者名稱與密碼分別用 `GRANTFORGE_DB_USER` 與 `GRANTFORGE_DB_PASSWORD` 設定。資料庫需要事先建立，帳號需要建立資料表的權限：首次啟動時 GrantForge 用 Liquibase 建立全部資料表，之後的版本升級也由 Liquibase 自動遷移。Hibernate 只校驗資料表結構，從不修改它。

在 PostgreSQL 上，GrantForge 會嘗試啟用 `pg_trgm` 擴充功能，並為使用者的登入名稱、顯示名稱與電子郵件建立三元組索引，讓百萬個帳號的「包含」搜尋保持在幾十毫秒。PostgreSQL 13 起它是可信擴充功能，資料庫擁有者即可啟用；如果帳號沒有這個權限，服務照常啟動，搜尋改為全資料表掃描，由管理員執行 `CREATE EXTENSION pg_trgm` 後下次啟動會自動補建索引。

## 字元集

- **MySQL / MariaDB**：建立資料庫時使用 `utf8mb4` 字元集，中文與表情符號才能完整儲存。
- **SQL Server、Oracle**：可能包含中文的文字欄位使用 `NVARCHAR`，長文字在 SQL Server 上是 `NVARCHAR(MAX)`、Oracle 上是 `CLOB`，與資料庫的預設字元集無關。
- **Oracle**：空字串會被視為 `NULL`，GrantForge 在領域層統一把空白值當作「未填寫」，行為與其他資料庫一致。

## 備份與還原

所有業務資料都在資料庫裡（工作階段也在），備份資料庫即可；如果使用外掛，同時備份 `plugins/`。未設定 `grantforge.security.encryption-key` 時，加密金鑰也儲存在資料庫中，還原備份即可解密；設定了金鑰則需要同時保管好這把金鑰。
