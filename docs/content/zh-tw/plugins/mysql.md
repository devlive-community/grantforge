---
title: MySQL
description: 安裝與設定 MySQL 服務外掛，查詢資料庫、資料表與欄位，並管理存取、遮罩與列篩選策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MySQL 外掛為 GrantForge 提供資料庫／資料表／欄位查詢，以及存取、遮罩與列篩選策略。外掛 ID 與服務類型都是 `mysql`。服務外掛安裝於 GrantForge 伺服器；業務 SQL 的實際檢查由獨立的 [MySQL 代理](/zh-tw/external/mysql-agent/) 執行。

連線檢查接受 MySQL 8.0 及更新版本，儲存庫的 Testcontainers 整合測試涵蓋 `mysql:8.0` 與 `mysql:8.4`，不代表其他版本均已驗證。MariaDB 伺服器與 MySQL 5.7 不在支援範圍內；`jdbc:mariadb://` 只是驅動程式使用的 URL 格式。

## 安裝

發行包已在 `plugins/mysql` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `mysql` 已啟用；更新外掛目錄後重新掃描。外掛透過 MariaDB Connector/J（LGPL）存取 MySQL，它支援 MySQL 協定，包括 `caching_sha2_password` 與 TLS，並在外掛自己的類別載入器中執行。MySQL 官方的 Connector/J 採 GPL 授權，不隨發行包散布。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 MySQL（`mysql`）。
2. 填入伺服器的 JDBC URL，例如 `jdbc:mysql://mysql.example.com:3306`。`jdbc:mariadb://` 同樣可用，驅動程式的容錯移轉寫法也可以，例如 `jdbc:mysql:replication://primary:3306,replica:3306`。
3. 填入查詢使用者與其密碼，測試連線後儲存。測試會登入、確認伺服器版本，並像查詢時一樣讀取 `information_schema`。

查詢使用者只供 GrantForge 尋找資源，與業務用戶端登入 MySQL 的使用者不同。連線測試成功不代表業務 SQL 已受到策略控制。

| 設定項 | 用途 |
| --- | --- |
| `jdbc.url` | 必填 JDBC URL：`jdbc:mysql://host[:port]` 或 `jdbc:mariadb://…`，可帶資料庫與 `?options`；憑證不能寫在 URL 中 |
| `username` | 必填的查詢使用者 |
| `password` | 查詢使用者的密碼；加密儲存 |
| `jdbc.properties` | 驅動程式參數，每行一個 `key=value`，例如 `sslMode=verify-full` 與 `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | 連線逾時，預設 `10000`，範圍 `1..600000` |
| `read.timeout.ms` | 單次查詢的讀取逾時，預設 `30000`，範圍 `1..600000` |

會讓伺服器讀取本機檔案、在連線時執行陳述式、一次執行多個陳述式、攜帶憑證或載入類別的參數，無論在 URL 或 `jdbc.properties` 中、無論大小寫，一律拒絕：`user`、`password`、`allowLocalInfile`、`allowMultiQueries`、`initSql`、`createDatabaseIfNotExist`、`socketFactory`、`credentialType` 以及連線池參數。查詢連線為唯讀。

需要驗證伺服器身分的 TLS 連線時，在 `jdbc.properties` 中填入：

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

憑證檔案必須在 **GrantForge 伺服器**上可讀，URL 中的主機名稱必須與憑證相符。外掛預設不強制使用 TLS，請明確設定；這些選項使用 MariaDB Connector/J 的名稱。

`information_schema` 只向使用者顯示其有權限的物件。例如只查詢 `sales` 資料庫中的資源，可由資料庫管理員執行以下授權，將 `grantforge-host` 替換為 GrantForge 伺服器的來源位址：

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

需要探索全部資料庫時才使用 `REFERENCES ON *.*`。代理的中繼資料帳號還需要 `SHOW_ROUTINE`，詳見代理指南。

## 資源與存取類型

| 層級 | 比對 | 說明 |
| --- | --- | --- |
| `database` | 萬用字元，區分大小寫 | 策略可以止於此層 |
| `table` | 萬用字元，區分大小寫 | 位於資料庫之下；策略可以止於此層 |
| `column` | 萬用字元，不區分大小寫 | 位於資料表之下；存取類型為 `select`、`insert`、`update` |

資料庫與資料表依區分大小寫比較，與 MySQL 在 Linux 上的儲存方式一致。若伺服器的 `lower_case_table_names` 為 1 或 2，代理會把所檢查的名稱轉為小寫，這類策略請以小寫撰寫。欄位名稱一律不區分大小寫，與 MySQL 一致。

存取類型對應 MySQL 的權限：`select`、`insert`、`update`、`delete`、`create`、`drop`、`alter`、`index`，以及包含以上八種類型的 `all`。`all` 不等於 MySQL 的 `ALL PRIVILEGES`，也不會取消代理的陳述式限制。

策略值中的 `*` 比對任意長度的字元，`?` 比對一個字元。策略編輯器依名稱前綴提示資料庫、策略所指資料庫中的資料表，以及其資料表中的欄位；提示輸入中的 `%` 與 `_` 是一般字元。

## 遮罩

遮罩策略作用於欄位。每種遮罩類型都是一個 MySQL 8 運算式，其中 `{col}` 代表該欄位：

| 遮罩類型 | 顯示 |
| --- | --- |
| `mask` | ASCII 字母顯示為 `X`/`x`，數字顯示為 `n`；其他字元保留 |
| `mask_show_last_4` | 只顯示最後四個字元 |
| `mask_show_first_4` | 只顯示前四個字元 |
| `mask_hash` | 未加鹽的 SHA-256 雜湊 |
| `mask_null` | `NULL` |
| `mask_none` | 原值不變 |
| `mask_date_show_year` | 該日期所在年份的 1 月 1 日 |
| `custom` | 自訂的 MySQL 運算式，以 `{col}` 代表欄位，例如 `CONCAT('****', RIGHT({col}, 4))` |

首四位與末四位遮罩會完整保留長度不超過四個字元的值。例如 `mask_show_last_4` 將 `4111111111111111` 顯示為 `xxxxxxxxxxxx1111`。

## 列篩選

列篩選策略作用於資料表。篩選條件是以資料表欄位撰寫的 MySQL 條件，例如 `region = 'EU'`：使用者只能看到符合條件的列，單表 `UPDATE` 與 `DELETE` 也只會修改這些列。需要對目標資料表套用列篩選的多表寫入會被拒絕；其他限制請見代理指南。

遮罩與列篩選不會授予存取權限，仍須另外設定允許存取的策略。

## 策略範例

假設 MySQL 中有 `sales.orders` 資料表，包含 `id`、`region`、`card` 欄位。為 MySQL 登入使用者 `alice` 在同一個資料服務下建立三條已啟用的策略：

| 策略種類 | 資源 | 規則 |
| --- | --- | --- |
| 存取 | `database=sales`、`table=orders`、`column=*` | 允許使用者 `alice` 的 `select` |
| 遮罩 | `database=sales`、`table=orders`、`column=card` | 使用者 `alice` 使用 `mask_show_last_4` |
| 列篩選 | `database=sales`、`table=orders` | 使用者 `alice` 使用 `region = 'EU'` |

策略使用者填寫 `alice`，而非 `alice@host`。MySQL 本身也必須允許該帳號讀取此表。代理套用新快照後，`alice` 經代理查詢只能看到 EU 列，卡號僅顯示末四位；查詢條件與連接運算式看到的也是遮罩後的值。完整部署與驗證步驟請見 [MySQL 代理](/zh-tw/external/mysql-agent/)。

## 讓策略生效

服務外掛負責管理策略與查詢資源。要讓策略（包括遮罩與列篩選）真正生效，還需部署 MySQL 代理：用戶端連線代理而不是直接連線伺服器。部署方式與檢查內容請見代理指南。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [MySQL 代理](/zh-tw/external/mysql-agent/)
- [外掛與服務類型開發](/zh-tw/develop/plugins/)
