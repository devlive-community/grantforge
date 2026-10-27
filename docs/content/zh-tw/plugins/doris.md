---
title: Apache Doris
description: 安裝並設定 Apache Doris 服務外掛，連線一個或多個 FE，以密碼登入，查詢 Catalog、資料庫、資料表、欄與工作負載群組，並管理誰可以讀取、匯入與管理什麼，細到欄與列，以及誰可以在哪個工作負載群組中執行查詢。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Doris 外掛查詢 Catalog、資料庫、資料表、欄與工作負載群組，並依 Doris 自己的方式管理存取策略：讀取欄，匯入、修改、刪除資料表與檢視檢視表定義，在資料庫中建立資料表與刪除資料庫，在 Catalog 中建立資料庫，以及在工作負載群組中執行查詢，並支援資料表的列篩選。外掛 ID 與服務類型皆為 `doris`。服務外掛執行於 GrantForge 伺服器中；執行於叢集旁的 [Apache Doris 代理](/zh-tw/external/doris-agent/)藉由 Doris 自己的角色、授權與列政策執行策略。

儲存庫的 Testcontainers 整合測試涵蓋 Apache Doris `2.1`、`3.1`、`4.0` 與 `4.1`，每個版本一個 FE 加一個 BE 的叢集；這不代表認證了其他所有版本。

## 安裝

發行套件在 `plugins/doris` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `doris` 已啟用；更新外掛目錄後重新掃描。外掛透過 MariaDB Connector/J 以 MySQL 協定與 FE 通訊，位於外掛自己的類別載入器中。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Apache Doris（`doris`）。
2. 輸入一個或多個 FE 的 MySQL 協定位址。
3. 填寫查詢使用者的使用者名稱與密碼，測試連線後儲存服務。測試會登入並描述叢集：Doris 版本、登入所用的使用者、該使用者能看到多少個 Catalog，以及內部 Catalog 中的多少個資料庫。測試只詢問 FE，因此沒有 BE 運作時也能回應。

查詢使用者用於讓 GrantForge 探索資源，與讀取和匯入資料的使用者彼此獨立。連線測試成功不代表存取已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `doris.hosts` | 必填：FE 的 MySQL 協定位址，例如 `fe1.example.com:9030`；多個以逗號分隔，某個無法連線時依序嘗試下一個。未寫連接埠時為 `9030` |
| `username` | 必填：查詢使用者 |
| `password` | 查詢使用者的密碼；加密儲存 |
| `ssl.mode` | `disable`（預設）、`trust`（加密但不驗證 FE）、`verify-ca`（驗證其憑證）或 `verify-full`（同時驗證其名稱）；用於在 MySQL 協定上提供 TLS 的 FE |
| `ssl.root.certificate` | verify-ca 與 verify-full：**GrantForge 伺服器** 上用於驗證 FE 的 PEM CA 憑證；空白時使用 JVM 內建的 |
| `timeout` | 連線與一條陳述式可用的時間，預設 `30000`，範圍 `1..600000` 毫秒 |

登入被拒、查詢使用者缺少權限以及 FE 無法連線，都會如實回報。

### 查詢使用者能看到什麼

Doris 的 `SHOW` 陳述式只列出使用者持有某種權限的 Catalog、資料庫、資料表與欄，因此查詢結果就是查詢使用者能看到的內容。請在需要撰寫策略的物件上授予它 `SELECT_PRIV`，例如 `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`；`SHOW WORKLOAD GROUPS` 列出它能看到的工作負載群組。Doris 自己的資料庫 `information_schema`、`mysql` 與 `__internal_schema` 不在其列：策略不決定它們。

## 資源與存取類型

| 層級 | 比對方式 | 存取類型 |
| --- | --- | --- |
| `catalog` | 萬用字元，區分大小寫；內部 Catalog 或外部 Catalog | `create_database` |
| `database` | 萬用字元，區分大小寫；位於 Catalog 之下 | `create`、`drop_database` |
| `table` | 萬用字元，區分大小寫；位於資料庫之下，資料表或檢視表 | `load`、`alter`、`drop`、`show_view` |
| `column` | 萬用字元，不區分大小寫，與 Doris 比較欄名的方式一致；位於資料表之下 | `select` |
| `workload_group` | 萬用字元，區分大小寫；工作負載群組的名稱 | `usage` |

| 存取類型 | Doris 的權限 |
| --- | --- |
| `select` | `SELECT_PRIV`，授予在欄上，或整個資料表、資料庫、Catalog 上 |
| `load` | `LOAD_PRIV`：匯入、`INSERT`、`UPDATE`、`DELETE` 與 `TRUNCATE` |
| `alter`、`drop` | 資料表上的 `ALTER_PRIV`、`DROP_PRIV` |
| `show_view` | `SHOW_VIEW_PRIV`：`SHOW CREATE VIEW` |
| `create` | 資料庫上的 `CREATE_PRIV`：建立該資料庫，以及在其中建立資料表與檢視表 |
| `drop_database` | 資料庫上的 `DROP_PRIV` |
| `create_database` | Catalog 上的 `CREATE_PRIV` |
| `usage` | 工作負載群組上的 `USAGE_PRIV` |

讀取依欄判定，與 Doris 的授權方式一致：`select` 策略指定欄，`*` 表示所有欄。因此一條策略可以允許一個資料表而拒絕其中一欄，使用者能讀取其餘所有欄。在策略值中，`*` 比對任意長度的字元，`?` 比對一個字元。策略編輯器會提示 Catalog、策略所指 Catalog 中的資料庫、其中的資料表與欄，以及工作負載群組。

Doris 在資料庫上的權限也作用於其中的每個資料表，在 Catalog 上的權限也作用於其中的每個資料庫。因此代理只在策略同時允許使用者在 Catalog 的每個資料庫中建立資料表時才授予 `create_database`，只在策略同時允許使用者刪除資料庫中的每個資料表時才授予 `drop_database`；否則會扣下該權限並說明原因，而不是授予超出策略允許的內容。`ADMIN_PRIV`、`GRANT_PRIV` 與 `NODE_PRIV` 不是存取類型：任何策略都不會授予它們。

Doris 只能透過 Apache Ranger 遮罩值，因此此服務類型不提供遮罩。

### 列篩選

資料表上的列篩選策略只向其使用者顯示符合條件的列。條件是 Doris 列政策所接受的述詞，例如 `region = 'EU'`；代理會先在資料表上試執行它，無法執行的篩選不顯示任何列。與其他服務一樣，使用者適用第一條對其生效的策略的篩選。

## 策略範例

假設叢集的內部 Catalog 中有資料庫 `sales` 與 `hr`，以及工作負載群組 `etl`。在同一個資料服務中建立以下已啟用的策略：

| 資源 | 規則 |
| --- | --- |
| `catalog=internal`、`database=sales`、`table=*`、`column=*` | 允許群組 `analysts` `select` |
| `catalog=internal`、`database=sales`、`table=orders`、`column=card` | 拒絕群組 `analysts` `select` |
| `catalog=internal`、`database=sales`、`table=orders` | 允許使用者 `ingest` `load` |
| `catalog=internal`、`database=team_*` | 允許群組 `data-team` `create` |
| `catalog=internal`、`database=team_*`、`table=*` | 允許群組 `data-team` `load`、`alter` 與 `drop` |
| `workload_group=etl` | 允許使用者 `ingest` 與群組 `data-team` `usage` |
| `catalog=internal`、`database=sales`、`table=orders`（列篩選） | 只向使用者 `emea` 顯示 `region = 'EMEA'` 的列 |

代理套用新快照後，`analysts` 的成員能讀取 `sales` 的每個資料表，但讀不到 `orders` 的卡號；`ingest` 能向 `orders` 匯入，並能在 `etl` 中執行其匯入；`data-team` 的成員能在管理員建立的 `team_` 資料庫中建立資料表，並對其匯入、修改與刪除；`emea` 若有策略允許其讀取 `orders`，只能看到本地區的列。

## 執行策略

服務外掛管理策略並查詢資源。執行策略需要 Apache Doris 代理，每個叢集一個。部署與檢查見 [Apache Doris 代理](/zh-tw/external/doris-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Apache Doris 代理](/zh-tw/external/doris-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
