---
title: ClickHouse
description: 安裝並設定 ClickHouse 服務外掛，連線一台或多台伺服器，以密碼或 TLS 用戶端憑證登入，查詢資料庫、資料表與欄位，並管理誰可以讀取、寫入與管理什麼，細到欄位與列。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

ClickHouse 外掛查詢資料庫、資料表與欄位，並依 ClickHouse 自己的方式管理存取策略：讀寫欄位，修改、刪除、清空與最佳化資料表，在資料庫中建立資料表與刪除資料庫，以及伺服器上的建立資料庫、`KILL QUERY` 與 `SYSTEM` 陳述式，並支援資料表的列篩選。外掛 ID 與服務類型皆為 `clickhouse`。服務外掛執行於 GrantForge 伺服器中；執行在伺服器旁的 [ClickHouse 代理](/zh-tw/external/clickhouse-agent/)藉由 ClickHouse 自己的授權與列政策執行策略。

儲存庫的 Testcontainers 整合測試涵蓋 ClickHouse `24.8`、`25.3`、`25.8`、`26.3` 與 `26.8`，即近兩年的長期支援版本；這不代表認證了其他所有版本。

## 安裝

發行套件在 `plugins/clickhouse` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `clickhouse` 已啟用；更新外掛目錄後重新掃描。外掛透過 ClickHouse 官方的 Java 用戶端以 HTTP 與伺服器通訊，位於外掛自己的類別載入器中。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 ClickHouse（`clickhouse`）。
2. 輸入一台或多台伺服器的 HTTP 介面 URL。
3. 選擇查詢使用者的登入方式，填寫使用者名稱與憑證，測試連線後儲存服務。測試會登入並描述伺服器：ClickHouse 版本、登入所用的使用者，以及該使用者能看到多少個資料庫。

查詢使用者用於讓 GrantForge 探索資源，與讀寫資料的使用者彼此獨立。連線測試成功不代表存取已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `clickhouse.urls` | 必填：伺服器的 HTTP 介面，例如 `https://clickhouse1.example.com:8443`；多個以逗號分隔，某台無法連線時依序嘗試下一台 |
| `authentication` | `password`（預設）或 `certificate`：由 ClickHouse 對應到該使用者的 TLS 用戶端憑證（`IDENTIFIED WITH ssl_certificate`） |
| `username` | 必填：查詢使用者 |
| `password` | password：查詢使用者的密碼；加密儲存 |
| `ssl.root.certificate` | https：**GrantForge 伺服器** 上用來驗證伺服器憑證的 PEM CA 憑證；空白時使用 JVM 內建的 |
| `ssl.client.certificate`、`ssl.client.key` | certificate：GrantForge 伺服器上的 PEM 用戶端憑證及其私密金鑰；憑證登入需要 `https://` 伺服器 |
| `timeout` | 請求可用的時長，預設 `30000`，範圍 `1..600000` 毫秒 |

一種登入方式的設定在另一種方式下會被拒絕，因此服務明確寫出它如何登入。登入被拒、唯讀使用者、伺服器無法連線或其憑證無法驗證，以及代替伺服器回應的並非 ClickHouse（例如代理伺服器），都會如實回報。

### 查詢使用者能看到什麼

ClickHouse 只向使用者顯示其擁有某種權限的資料庫、資料表與欄位。請在需要撰寫策略的範圍上授予查詢使用者 `SHOW`，例如 `GRANT SHOW ON *.* TO grantforge_lookup`，它只能看到名稱，讀不到資料。ClickHouse 自己的資料庫 `system` 與 `information_schema` 不在其列：策略不決定它們。

## 資源與存取類型

| 層級 | 比對 | 存取類型 |
| --- | --- | --- |
| `database` | 萬用字元，區分大小寫；資料庫名稱 | `create`、`drop_database` |
| `table` | 萬用字元，區分大小寫；位於資料庫之下 | `alter`、`drop`、`truncate`、`optimize` |
| `column` | 萬用字元，區分大小寫；位於資料表之下 | `select`、`insert` |
| `server` | 唯一值 `*` | `create_database`、`kill_query`、`system` |

| 存取類型 | ClickHouse 的權限 |
| --- | --- |
| `select`、`insert` | 欄位上的 `SELECT`、`INSERT` |
| `alter` | `ALTER`：資料表的一切修改，包括 `ALTER UPDATE` 與 `ALTER DELETE` |
| `drop` | `DROP TABLE`、`DROP VIEW`、`DROP DICTIONARY` |
| `truncate`、`optimize` | `TRUNCATE`、`OPTIMIZE` |
| `create` | 在該資料庫中的 `CREATE TABLE`、`CREATE VIEW`、`CREATE DICTIONARY` |
| `drop_database` | `DROP DATABASE` |
| `create_database`、`kill_query`、`system` | `CREATE DATABASE`、`KILL QUERY`、`SYSTEM`：所有 `SYSTEM` 陳述式 |

讀取與寫入依欄位判定，與 ClickHouse 的授權方式一致：`select` 或 `insert` 的策略要指明欄位，`*` 表示全部欄位。因此一條策略可以允許一張資料表並拒絕其中一個欄位，使用者仍可讀取其餘各欄位。策略值中，`*` 比對任意一串字元，`?` 比對一個字元。策略編輯器會提示資料庫、策略所指資料庫中的資料表及其欄位。

這些存取類型都不是 ClickHouse 的權限群組（如 `ALL`）：每一項就是它所寫的意思，因此一條策略給出的永遠不會超出它所寫的。ClickHouse 只在 ClickHouse Cloud 中提供欄位遮罩，因此此服務類型不提供遮罩。

### 列篩選

資料表上的列篩選策略只讓其使用者看到某個條件成立的列。條件是 ClickHouse 運算式，與列政策的寫法相同，例如 `region = 'EU'` 或 `owner = currentUser()`。與其他服務一樣，使用者看到第一條適用於他的策略的篩選條件。

## 策略範例

假設伺服器上有資料庫 `sales` 與 `hr`。在同一資料服務中建立下列已啟用的策略：

| 資源 | 規則 |
| --- | --- |
| `database=sales`、`table=*`、`column=*` | 允許群組 `analysts` `select` |
| `database=sales`、`table=orders`、`column=card` | 拒絕群組 `analysts` `select` |
| `database=sales`、`table=orders`、`column=*` | 允許使用者 `ingest` `insert` |
| `database=team_*` | 允許群組 `data-team` `create` |
| `database=team_*`、`table=*` | 允許群組 `data-team` `alter`、`drop`、`truncate` 與 `optimize` |
| `database=team_*`、`table=*`、`column=*` | 允許群組 `data-team` `select` 與 `insert` |
| `server=*` | 允許群組 `data-team` `create_database` |
| `database=sales`、`table=orders`（列篩選） | 使用者 `emea` 只看到 `region = 'EMEA'` 的列 |

代理套用新快照後，`analysts` 的成員可以讀取 `sales` 的每張資料表，但讀不到 `orders` 的卡號；`ingest` 可以寫入 `orders`，但什麼都讀不到；`data-team` 的成員可以建立資料庫，並在名稱以 `team_` 開頭的資料庫中建立資料表，讀寫與修改它們；而 `emea` 若有策略允許讀取 `orders`，只能看到本區域的列。

## 執行策略

服務外掛管理策略並查詢資源。執行策略需要 ClickHouse 代理，每台伺服器或每個叢集一個。部署與檢查見 [ClickHouse 代理](/zh-tw/external/clickhouse-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [ClickHouse 代理](/zh-tw/external/clickhouse-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
