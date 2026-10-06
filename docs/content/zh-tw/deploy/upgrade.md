---
title: 升級與舊版本遷移
description: 2.x 版本之間的升級，以及從 1.x（AuthX / GrantForge 1.x）遷移帳號、角色與選單。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 2.x 版本之間升級

1. 備份資料庫（以及 `plugins/`、`configure/`）。
2. 停止服務：`bin/shutdown.sh`。
3. 用新版本發行包的 `lib/` 與 `bin/` 替換舊的。
4. 啟動：`bin/startup.sh`。Liquibase 會自動執行新版本的資料庫遷移，就緒探針在遷移完成後才回傳 200。

叢集升級時先停止全部執行個體再啟動新版本，避免新舊版本同時讀寫。已經發布的遷移不會被修改，每個版本都在全部支援的資料庫上驗證過「從上一版本升級」。

## 從 1.x 遷移

1.x 把資料儲存在另一套資料表裡，2.x 不會讀取它們。遷移方式是：在**新的資料庫**上安裝 2.x 並完成初始化，停止服務，然後把舊庫的帳號、角色和選單匯入某個租戶：

```bash
# 先預演：只產生報告，不寫入
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
# 確認報告後正式匯入
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

兩條命令都會寫出 `logs/legacy-import-report.json`，列出已經（或將要）建立的內容、被跳過的內容及原因，以及每個舊物件對應的新 ID。舊資料庫的 JDBC 驅動放進 `drivers/`，密碼透過 `GRANTFORGE_LEGACY_SOURCE_PASSWORD` 提供（不提供時指令碼會詢問）。重複執行匯入只會補齊缺少的內容。

匯入規則：

- **帳號**保留原密碼，首次登入時自動換成新的雜湊演算法。不符合 2.x 使用者名稱規則（3–64 個字母、數字或 `._@-`）、沒有密碼，或使用者名稱已被其他租戶佔用的帳號會被跳過。
- **角色**保留名稱，編碼轉為小寫（`GLY` → `gly`）。
- **選單**成為 `legacy` 應用程式的資源：`#` 位址的選單作為分組，其他位址作為頁面，頁面下的選單作為按鈕。選單位址及其 HTTP 方法成為 API 資源 `api:<方法>:<路徑>`；以 `*` 結尾的位址變為 `<路徑>/**`，按路徑段而不是字元前綴匹配，報告會逐條列出供核對。
- 只遷移**顯式授權**：1.x 允許任何人存取登記為選單的位址，2.x 不會這樣做。

> [!WARNING]
> 匯入前請先在報告裡核對被跳過的帳號和通配位址，再執行 `--apply`。
