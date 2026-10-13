---
title: Apache Hive
description: 安裝與設定 Hive 服務外掛，以密碼或 Kerberos 登入，查詢資料庫、資料表、欄位與 UDF，並管理存取、遮罩與列篩選策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Hive 外掛為 HiveServer2 提供資料庫、資料表、欄位與永久 UDF 的查詢，以及存取、遮罩與列篩選策略，模型參照 Apache Ranger 的 Hive 服務。外掛 ID 與服務類型皆為 `hive`。服務外掛執行於 GrantForge 伺服器中；安裝在 HiveServer2 中的 [Hive 代理](/zh-tw/external/hive-agent/)負責執行策略。

儲存庫的 Testcontainers 整合測試涵蓋 HiveServer2 `3.1.3`、`4.0.1` 與 `4.1.0`，其中包含對 `4.0.1` 的 Kerberos 登入；這並不代表對其他所有版本的認證。

## 安裝

發行套件在 `plugins/hive` 中包含此外掛。請在 **平台管理 → 外掛** 中確認 `hive` 已啟用；更新外掛目錄後請重新掃描。外掛在獨立的類別載入器中透過 Hive 自己的 JDBC 驅動程式（`hive-jdbc` 4.0.1 standalone）連線 HiveServer2，因此同樣適用於 HiveServer2 3.1 與 4.x。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Apache Hive（`hive`）。
2. 填寫 HiveServer2 的 JDBC URL，例如 `jdbc:hive2://hiveserver2.example.com:10000/default`。也支援 ZooKeeper 服務探索：`jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`。
3. 選擇查詢使用者的登入方式並填寫該使用者，測試連線後儲存服務。測試會登入並列出資料庫，與查詢時的方式相同。

查詢使用者只用於讓 GrantForge 探索資源，與執行查詢的使用者無關。連線測試成功並不代表查詢已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `jdbc.url` | 必填的 HiveServer2 URL `jdbc:hive2://主機/資料庫;工作階段變數`，可帶 TLS（`;ssl=true;sslTrustStore=…`）、HTTP 傳輸，或在 Kerberos 下帶伺服器主體（`;principal=hive/_HOST@EXAMPLE.COM`） |
| `authentication` | `password`（預設）：使用者與密碼，適用於 LDAP、PAM 或無驗證；`kerberos`：主體加 keytab 或密碼 |
| `username` | 必填的查詢使用者；Kerberos 下為完整主體，如 `grantforge@EXAMPLE.COM` |
| `password` | 使用者密碼；未提供 keytab 時為主體的 Kerberos 密碼；加密儲存 |
| `keytab` | Kerberos：主體 keytab 的路徑，須在 **GrantForge 伺服器** 上可讀 |
| `kerberos.kdc` | Kerberos：主體所屬領域的 KDC，`host[:port]`，以逗號分隔；空白時使用伺服器的 `krb5.conf` |
| `read.timeout.ms` | 讀取逾時，預設 `30000`，範圍 `1..600000` |

URL 中不得攜帶憑證，也不得包含會在 GrantForge 伺服器上讀取檔案或權杖的內容：`user`、`password`、`initFile`、`kerberosAuthType`、`delegationToken`、`storePasswordPath`、`jwt` 以及 `browser…` 變數不論大小寫一律拒絕。`auth` 只能是 `noSasl`。Kerberos 服務必須在 URL 中提供 `principal`；密碼服務則不得提供。

### Kerberos

選擇 `kerberos` 時，GrantForge 自行以 keytab 或密碼登入該主體，並以該主體開啟連線；GrantForge 伺服器上不需要其他 Kerberos 設定。`kerberos.kdc` 中填寫了 KDC 的領域會對整個 GrantForge 伺服器宣告，方式與 HDFS 外掛相同，因此多個 Hive 與 HDFS 服務可同時使用不同的領域。

## 資源與存取類型

| 層級 | 比對方式 | 說明 |
| --- | --- | --- |
| `database` | 萬用字元，不分大小寫 | 策略可止於此層 |
| `table` | 萬用字元，不分大小寫 | 位於資料庫之下；策略可止於此層 |
| `column` | 萬用字元，不分大小寫 | 位於資料表之下 |
| `udf` | 萬用字元，不分大小寫 | 永久函式，位於資料庫之下 |
| `url` | 路徑，區分大小寫，可遞迴 | 陳述式讀寫的資料位置，如 `hdfs://nn:8020/landing`；存取類型 `read` 與 `write` |
| `hiveservice` | 萬用字元 | HiveServer2 本身；存取類型 `serviceadmin` |
| `global` | 萬用字元 | 暫時函式與新增的檔案；存取類型 `tempudfadmin` |

存取類型與 Hive 一致：`select`、`update`、`create`、`drop`、`alter`、`index`、`lock`、`read`、`write`、`repladmin`、`serviceadmin`、`tempudfadmin`、`refresh` 與 `all`。與 Ranger 相同，`all` 包含 `tempudfadmin` 以外的所有類型，因為 `tempudfadmin` 允許使用者在 HiveServer2 內執行任意程式碼。

策略值中 `*` 比對任意個字元，`?` 比對一個字元。策略編輯器依前綴提示名稱：資料庫、策略所列資料庫中的資料表與 UDF，以及其資料表中的欄位。

## 遮罩

遮罩策略作用於欄位。每種遮罩類型都是 Hive 的一個遮罩函式，其中 `{col}` 代表該欄位：

| 遮罩類型 | 顯示 |
| --- | --- |
| `mask` | 大寫字母顯示為 `X`，小寫字母為 `x`，數字為 `n` |
| `mask_show_last_4` | 只顯示最後四個字元 |
| `mask_show_first_4` | 只顯示前四個字元 |
| `mask_hash` | SHA-256 雜湊 |
| `mask_null` | `NULL` |
| `mask_none` | 原值不變 |
| `mask_date_show_year` | 該日期所在年份的 1 月 1 日 |
| `custom` | 自訂 Hive 運算式，以 `{col}` 表示欄位，如 `concat('****', substr({col}, -4))` |

例如，`mask_show_last_4` 會將 `4111111111111111` 顯示為 `xxxxxxxxxxxx1111`。

## 列篩選

列篩選策略作用於資料表。篩選條件是針對資料表欄位的 Hive 條件，例如 `region = 'EU'`：查詢只能看到符合條件的列。

遮罩與列篩選不授予存取權限，必須另有存取策略允許該查詢。

## 策略範例

假設 Hive 中有 `sales.orders` 資料表，包含 `id`、`region` 與 `card` 欄位。在同一資料服務中為使用者 `alice` 建立三條啟用的策略：

| 策略類型 | 資源 | 規則 |
| --- | --- | --- |
| 存取 | `database=sales`、`table=orders`、`column=*` | 允許使用者 `alice` 執行 `select` |
| 遮罩 | `database=sales`、`table=orders`、`column=card` | 對使用者 `alice` 使用 `mask_show_last_4` |
| 列篩選 | `database=sales`、`table=orders` | 對使用者 `alice` 使用 `region = 'EU'` |

代理套用新快照後，`alice` 只能看到 `sales.orders` 中 EU 的列，卡號除最後四碼外全部遮罩；`SHOW DATABASES` 對她只列出 `sales`，其他資料庫拒絕她存取。

## 執行策略

服務外掛負責管理策略與查詢資源。要執行策略（包括遮罩與列篩選），每個 HiveServer2 都需要安裝 Hive 代理。部署方式與檢查內容請見 [Hive 代理](/zh-tw/external/hive-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Hive 代理](/zh-tw/external/hive-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
