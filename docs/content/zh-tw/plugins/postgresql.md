---
title: PostgreSQL
description: 安裝並設定 PostgreSQL 服務外掛，連線一台或多台伺服器，以密碼或 TLS 用戶端憑證登入，查詢資料庫、結構描述、資料表與欄位，並管理誰可以讀取、寫入與建立什麼，細到欄位與列。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

PostgreSQL 外掛查詢資料庫、結構描述、資料表與欄位，並依 PostgreSQL 自己的方式管理存取策略：讀取、插入與更新欄位，刪除、清空資料表與在資料表上建立觸發程序，在結構描述中建立物件，以及在資料庫中建立結構描述與暫存資料表，並支援資料表的列篩選。外掛 ID 與服務類型皆為 `postgresql`。服務外掛執行於 GrantForge 伺服器中；執行在伺服器旁的 [PostgreSQL 代理](/zh-tw/external/postgresql-agent/)藉由 PostgreSQL 自己的權限與列安全政策執行策略。

儲存庫的 Testcontainers 整合測試涵蓋 PostgreSQL `15`、`16`、`17` 與 `18`，即 PostgreSQL 仍在維護的版本；這不代表認證了其他所有版本。

## 安裝

發行套件在 `plugins/postgresql` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `postgresql` 已啟用；更新外掛目錄後重新掃描。外掛透過 PostgreSQL 官方的 JDBC 驅動程式與伺服器通訊，位於外掛自己的類別載入器中。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 PostgreSQL（`postgresql`）。
2. 以 `host:port` 輸入一台伺服器或多台伺服器。
3. 選擇查詢使用者的登入方式，填寫使用者名稱與憑證，測試連線後儲存服務。測試會登入並描述伺服器：PostgreSQL 版本、登入所用的使用者，以及伺服器上有多少個資料庫。

查詢使用者用於讓 GrantForge 發現資源，與讀寫資料的使用者彼此獨立。連線測試成功並不表示存取已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `postgresql.hosts` | 必填：以 `host:port` 表示的伺服器，例如 `postgres1.example.com:5432`；多個以逗號分隔，某台無法連線時依序嘗試下一台。連接埠預設為 `5432` |
| `postgresql.database` | 查詢使用者首先登入的資料庫，用於列出資料庫；預設 `postgres` |
| `authentication` | `password`（預設）或 `certificate`：由 PostgreSQL 的 `cert` 驗證對應到該使用者的 TLS 用戶端憑證 |
| `username` | 必填：查詢使用者 |
| `password` | password：查詢使用者的密碼；加密儲存 |
| `ssl.mode` | 依 libpq 的 `sslmode` 命名的 TLS 方式：`disable`、`allow`、`prefer`（預設）、`require`、`verify-ca` 或同時驗證伺服器名稱的 `verify-full` |
| `ssl.root.certificate` | verify-ca 與 verify-full：**GrantForge 伺服器** 上用於驗證伺服器憑證的 PEM CA 憑證；空白時使用 JVM 內建的 |
| `ssl.client.certificate`、`ssl.client.key` | certificate：GrantForge 伺服器上的 PEM 用戶端憑證及其 PKCS #8 私密金鑰（PEM 或 DER）；憑證登入需要 `require` 或某種驗證方式，與驗證方式一起使用時還需要 `ssl.root.certificate` |
| `timeout` | 連線與查詢可用的時長，預設 `30000`，範圍 `1..600000` 毫秒 |

一種登入方式的設定在另一種方式下會被拒絕，因此服務明確寫出它如何登入。PostgreSQL 的驅動程式會拒絕擁有者以外的人可讀的私密金鑰檔案。登入被拒、資料庫不存在、伺服器無法連線或其憑證無法驗證，都會如實回報。

### 查詢使用者能看到什麼

PostgreSQL 的系統目錄向每位使用者顯示所有資料庫的名稱，並在它可以連線的每個資料庫中顯示結構描述、資料表與欄位的名稱，而不需讀取資料。查詢使用者只需對要撰寫策略的資料庫擁有 `CONNECT` 權限，除非被撤銷，PUBLIC 預設即擁有。查詢結構描述、資料表或欄位時，會登入策略所指的每個資料庫（最多 20 個），並略過查詢使用者無權連線的資料庫。PostgreSQL 自己的結構描述，即以 `pg_` 開頭者與 `information_schema`，不在其列：策略不決定它們。

## 資源與存取類型

| 層級 | 比對 | 存取類型 |
| --- | --- | --- |
| `database` | 萬用字元，區分大小寫；資料庫名稱 | `create_schema`、`temporary` |
| `schema` | 萬用字元，區分大小寫；位於資料庫之下 | `create` |
| `table` | 萬用字元，區分大小寫；結構描述下的資料表、分割資料表、檢視表、具體化檢視表與外部資料表 | `delete`、`truncate`、`trigger` |
| `column` | 萬用字元，區分大小寫；位於資料表之下 | `select`、`insert`、`update` |

| 存取類型 | PostgreSQL 的權限 |
| --- | --- |
| `select`、`insert`、`update` | 欄位上或整張資料表上的 `SELECT`、`INSERT`、`UPDATE` |
| `delete`、`truncate`、`trigger` | 資料表上的 `DELETE`、`TRUNCATE`、`TRIGGER` |
| `create` | 結構描述上的 `CREATE`：在其中建立資料表、檢視表、序列、函式與其他物件 |
| `create_schema` | 資料庫上的 `CREATE`：在其中建立結構描述 |
| `temporary` | 資料庫上的 `TEMPORARY` |

讀取、插入與更新依欄位決定，與 PostgreSQL 授予它們的方式一致：`select`、`insert` 或 `update` 的策略會指定欄位，`*` 表示全部欄位。因此策略可以允許一張資料表而拒絕其中一個欄位，使用者可以讀取其餘各欄。在策略值中，`*` 比對任意一串字元，`?` 比對一個字元。策略編輯器會提示資料庫、策略所指資料庫中的結構描述，以及其中的資料表與欄位。

要用到策略允許的內容，PostgreSQL 還需要更多權限：資料庫的 `CONNECT`、結構描述的 `USAGE`，以及插入自動遞增欄位時其序列的 `USAGE`。它們不是單獨的存取類型：代理會隨需要它們的權限一併授予。PostgreSQL 沒有欄位遮罩，因此此服務類型不提供遮罩。

### 列篩選

資料表上的列篩選策略只向其使用者顯示條件成立的列。條件是 PostgreSQL 運算式，與列安全政策的 `USING` 所接受的一致，例如 `region = 'EU'` 或 `owner = current_user`。與其他服務一樣，使用者看到的是第一條適用於他的策略的篩選。PostgreSQL 只能篩選資料表與分割資料表的列：策略為某位使用者篩選其列的檢視表等物件，對該使用者完全不可讀。

## 策略範例

假設伺服器有資料庫 `sales` 與 `hr`，`sales` 中有結構描述 `crm`。在同一個資料服務中建立以下已啟用的策略：

| 資源 | 規則 |
| --- | --- |
| `database=sales`、`schema=crm`、`table=*`、`column=*` | 允許群組 `analysts` 進行 `select` |
| `database=sales`、`schema=crm`、`table=orders`、`column=card` | 拒絕群組 `analysts` 進行 `select` |
| `database=sales`、`schema=crm`、`table=orders`、`column=*` | 允許使用者 `ingest` 進行 `insert` |
| `database=sales`、`schema=team_*` | 允許群組 `data-team` 進行 `create` |
| `database=sales`、`schema=team_*`、`table=*` | 允許群組 `data-team` 進行 `delete` 與 `truncate` |
| `database=sales`、`schema=team_*`、`table=*`、`column=*` | 允許群組 `data-team` 進行 `select`、`insert` 與 `update` |
| `database=sales` | 允許群組 `data-team` 進行 `create_schema` |
| `database=sales`、`schema=crm`、`table=orders`（列篩選） | 只向使用者 `emea` 顯示 `region = 'EMEA'` 的列 |

代理套用新快照後，`analysts` 的成員可以讀取 `crm` 中的每張資料表，但讀不到 `orders` 的卡號；`ingest` 可以向 `orders` 插入資料（包括其自動遞增 id），但什麼也讀不到；`data-team` 的成員可以建立結構描述，並在名稱以 `team_` 開頭的結構描述中建立、讀取、修改與清空資料表；而 `emea` 若有策略允許其讀取 `orders`，只能看到自己區域的列。

## 執行策略

服務外掛管理策略並查詢資源。執行策略需要 PostgreSQL 代理，每台伺服器或每個主要伺服器一個。部署與檢查見 [PostgreSQL 代理](/zh-tw/external/postgresql-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [PostgreSQL 代理](/zh-tw/external/postgresql-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
