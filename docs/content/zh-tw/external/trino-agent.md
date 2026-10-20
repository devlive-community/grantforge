---
title: Trino 代理
description: 在 Trino coordinator 中安裝系統存取控制，以 GrantForge 策略檢查查詢，只列出使用者可使用的內容，篩選列並遮罩欄位，並讓模擬使用者與他人的查詢受策略約束。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Trino 代理是名為 `grantforge` 的系統存取控制：coordinator 會就查詢涉及的每個目錄、結構描述、資料表、欄位、常式、工作階段屬性與使用者詢問它，它依據 `trino` 資料服務的簽章策略作答。Trino 470 至 478 執行於 Java 23 或 24，Trino 479 及之後執行於 Java 25，因此發行套件提供兩個 JAR，各自把相依套件遮蔽打包在內：

| Trino | JAR |
| --- | --- |
| 470 至 478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 及之後 | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

儲存庫的 Testcontainers 整合測試在 Trino `470` 與 `476` 中執行第一個 JAR，在 `479` 與 `484` 中執行第二個。這並不代表對其他所有版本的認證。服務外掛請參閱 [Trino](/zh-tw/plugins/trino/)。

## 運作方式

- **每次檢查都在它涉及的每個範圍上判定。** 目錄、結構描述或資料表上的權限涵蓋其中的內容，因此代理會在欄位所在的目錄、結構描述、資料表以及欄位本身上判定：任一範圍上的拒絕都會拒絕，任一範圍上的允許都會授予。
- **清單會省略無權使用的內容。** `SHOW CATALOGS`、`SHOW SCHEMAS`、`SHOW TABLES`、`SHOW FUNCTIONS`、`information_schema` 以及驅動程式的中繼資料只顯示使用者可使用（或可使用其中某些內容）的對象。
- **描述資料的內容始終開放。** 每個目錄的 `information_schema`、`system` 目錄中供 JDBC 驅動程式與 BI 工具讀取的 `jdbc` 與 `metadata` 結構描述，以及 Trino 的內建函式不需要策略；它們的列會像清單一樣被篩選。
- **篩選列並遮罩欄位。** 列篩選策略作用於讀取該資料表的每個查詢；遮罩會配合欄位的類型，使其不會破壞查詢：文字遮罩作用於 `varchar` 與 `char` 欄位並轉換回該欄位的類型，年份遮罩作用於日期與時間戳記，自訂運算式會轉換為該類型，文字遮罩涉及的其他欄位顯示為該類型的 `NULL`。
- **模擬使用者與他人的查詢受策略約束。** 以其他使用者身分執行查詢需要該使用者上的 `impersonate`；檢視或終止其他使用者的查詢需要該使用者上的 `view_query` 或 `kill_query`。每個人都可以執行查詢，並檢視與終止自己的查詢。
- **拒絕會說明原因。** 用戶端會收到 `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`，或者沒有策略允許，或者尚未收到策略。
- **快取決策並合併存取。** 目前快照的決策會被快取，預設 100,000 筆、10 秒，新快照會重新開始。時間窗內（預設 10 秒）相同的允許存取會以一筆存取事件回報，並註明次數；拒絕會立即逐筆回報。每個事件都註明所屬的查詢。
- **權限歸 GrantForge 管理。** Trino 的 `GRANT`、`REVOKE`、`DENY` 與角色陳述式會被拒絕，因為策略在 GrantForge 中管理。

| 陳述式 | 所需存取 |
| --- | --- |
| `SELECT` | 所讀每個欄位上的 `select`；不讀取任何欄位的查詢（例如 `count(*)`）需要資料表上的 `select` |
| `INSERT`、`DELETE`、`TRUNCATE` | `insert`；後兩者為 `delete` |
| `UPDATE` | 所寫每個欄位上的 `update` |
| `CREATE SCHEMA`、`CREATE TABLE`、`CREATE VIEW`、`CREATE MATERIALIZED VIEW` | 所建立對象上的 `create` |
| 刪除結構描述、資料表、檢視或具體化檢視（`DROP`） | 其上的 `drop` |
| 對資料表、檢視或欄位的 `ALTER`、`COMMENT`、`SET PROPERTIES`、`SET AUTHORIZATION`、`ALTER TABLE ... EXECUTE` | 其上的 `alter` |
| 重新命名結構描述、資料表、檢視或具體化檢視 | 其上的 `alter` 與新名稱上的 `create` |
| `REFRESH MATERIALIZED VIEW` | 其上的 `insert` |
| 以擁有者身分執行的檢視 | 其擁有者需要對所讀欄位的 `select` 與對所用函式的 `execute` |
| `SHOW SCHEMAS`、`SHOW TABLES`、`SHOW COLUMNS`、`SHOW FUNCTIONS`、`SHOW CREATE` | 對它或其中內容的任意存取 |
| `CREATE CATALOG`、`DROP CATALOG` | 目錄上的 `create` 或 `drop` |
| 呼叫函式；`CREATE FUNCTION`、`DROP FUNCTION` | `execute`；函式上的 `create_function`、`drop_function` |
| `CALL` | 程序上的 `execute` |
| `SET SESSION` | 目錄或系統工作階段屬性上的 `set` |
| 以其他使用者身分執行、檢視或終止他人的查詢 | 該使用者上的 `impersonate`、`view_query`、`kill_query` |
| 讀取或寫入系統資訊：Trino REST API 與 Web UI 中的節點、統計與執行緒傾印 | `system_information` 上的 `read` 或 `write` |

從 Trino 479 起，第二個 JAR 還會檢查資料表分支：列出分支需要能看到該資料表，建立、刪除或快轉分支需要資料表上的 `alter`；重新整理檢視以及變更具體化檢視的擁有者或註解需要其上的 `alter`。

## 部署

1. 新增 `trino` 資料服務並設定其策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有 Trino 執行帳號可讀的檔案。
2. 把與 coordinator 版本對應的 JAR 複製到 Trino 外掛目錄下獨立的目錄中，例如 `/usr/lib/trino/plugin/grantforge/`。只有 coordinator 檢查存取；worker 不需要任何變更。
3. 在 coordinator 上建立 `etc/access-control.properties`，替換範例中的位址、路徑與執行個體名稱：

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   快取目錄必須可由 Trino 執行帳號寫入。GrantForge 取代 Trino 以檔案為基礎或其他的存取控制，而不是與之並行執行。
4. 重新啟動 coordinator。在代理收到第一個快照之前，除描述資料的內容外，查詢都會被拒絕。
5. **先遷移既有規則：** Trino 以檔案為基礎的存取控制規則將不再生效。請事先建立對應的策略。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此 coordinator 的名稱，以及存放簽章快照與暫存事件的可寫目錄 |
| `grantforge.signing.key.file` | 選用的固定快照簽章金鑰檔；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時於首次連線時從伺服器取得 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理擷取新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆回報 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 快取決策的最大數量，預設 `100000`，最多 `10000000`，`0` 表示不快取；以及快取時長，預設 `10000`，範圍 `1..3600000` 毫秒 |

## 使用者、群組與角色

策略中的使用者是查詢的工作階段使用者，即 Trino 經驗證與使用者對應後得到的使用者。群組為 Trino 的群組提供者給出的群組，加上 GrantForge 中該使用者所屬的群組；角色為 GrantForge 授予該使用者的角色。

## 決策與限制

- 沒有可用快照時，除描述資料的內容、執行查詢與自己的查詢外，一切都會被拒絕。未啟用的服務讓 Trino 不受檢查，與沒有存取控制時一樣；但 Trino 的權限與角色陳述式仍會被拒絕。
- 繫結到類型不是 `trino` 之服務的權杖會拒絕一切。
- Trino 會先詢問使用者能否使用某個目錄：在目錄中沒有任何權限的使用者會收到 Trino 自己的 `Cannot access catalog`。
- 讀取了使用者無權讀取之欄位的 `SELECT` 會被拒絕，並指出第一個這樣的欄位；不會從結果中移除欄位。
- Trino 不會寫入對該使用者遮罩的資料表：遮罩生效時，此類 `INSERT` 會失敗。
- 遮罩會轉換為欄位的類型，因此有長度限制的 `varchar` 只顯示容納得下的部分，例如 `varchar(3)` 中只顯示雜湊的 3 個字元。
- 快照中無法讀取的列篩選或遮罩會顯示為沒有列，或把該欄位顯示為 `NULL`，而不是顯示資料。
- 決策會快取 `grantforge.decision.cache.ttl.ms`，因此策略的有效期最多會延遲這麼久開始或結束。

## 相關指南

- [Trino](/zh-tw/plugins/trino/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
