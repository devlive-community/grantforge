---
title: MongoDB
description: 安裝並設定 MongoDB 服務外掛，連線複本集或分片叢集，用 SCRAM、用戶端憑證、LDAP 或 Kerberos 登入，查詢資料庫與集合，並按集合管理誰可以讀、寫和管理什麼。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MongoDB 外掛查詢資料庫與集合，並用 MongoDB 自己的概念管理存取策略：按集合決定讀取、變更串流、插入、更新與刪除、索引、集合選項、跳過文件驗證和刪除集合；按資料庫決定創建集合和刪除資料庫；按整個部署決定列出全部資料庫和監控。外掛 ID 與服務類型都是 `mongodb`。服務外掛運行在 GrantForge 伺服器中，運行在部署旁的 [MongoDB 代理](/zh-tw/external/mongodb-agent/) 用 MongoDB 自身的角色實施策略。

儲存庫的 Testcontainers 整合測試涵蓋 MongoDB `6.0`、`7.0`、`8.0`、`9.0` 複本集，以及 Percona Server for MongoDB `8.0` 上的 Kerberos。這並不認證其他所有版本。

## 安裝

發行套件在 `plugins/mongodb` 中附帶此外掛。在 **平台管理 → 外掛** 中確認 `mongodb` 已啟用，更新外掛目錄後重新掃描。外掛在自己的類別載入器中通過官方 MongoDB Java 驅動程式與 MongoDB 通信。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 MongoDB（`mongodb`）。
2. 填寫部署的連線字串，與任何 MongoDB 用戶端使用的相同。
3. 填寫查詢使用者的登入方式，測試連線並儲存服務。測試會登入並描述部署：MongoDB 版本，是複本集、分片叢集還是單機，誰登入了，以及該使用者能看到多少個資料庫。

查詢使用者讓 GrantForge 能找到資源，它不是讀寫資料的使用者。連線測試成功並不代表存取已經遵循策略。

| 設定 | 用途 |
| --- | --- |
| `mongodb.uri` | 必填：連線字串，如 `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` 或 `mongodb+srv://cluster.example.com/`。`tls=true` 等選項寫在這裡；憑據不寫在這裡，含憑據的字串會被拒絕 |
| `auth.mechanism` | 有密碼的使用者用 `SCRAM-SHA-256`（預設）或 `SCRAM-SHA-1`，用戶端憑證用 `MONGODB-X509`，LDAP 用 `PLAIN`，Kerberos 用 `GSSAPI` |
| `username` | 查詢使用者。`GSSAPI` 時為主體；`MONGODB-X509` 時留空則取自憑證 |
| `password` | 查詢使用者的密碼，加密存儲 |
| `auth.source` | SCRAM：定義該使用者的資料庫，預設 `admin`。其他機制使用 `$external` |
| `tls.ca.file` | `tls=true` 時：**GrantForge 伺服器**上用於驗證伺服器的 PEM CA 憑證路徑。留空使用 JVM 的 |
| `tls.certificate.key.file` | `tls=true` 時：**GrantForge 伺服器**上同時包含用戶端憑證和私密金鑰的 PEM 檔案路徑，與 `mongosh` 使用的相同 |
| `tls.certificate.key.password` | 加密私密金鑰的密碼，加密存儲 |
| `kerberos.keytab` | `GSSAPI`：**GrantForge 伺服器**上該主體的 keytab 路徑。留空則使用密碼 |
| `kerberos.kdc` | `GSSAPI`：該 realm 的 KDC，以逗號分隔。留空使用伺服器的 `krb5.conf` |
| `kerberos.service.name` | `GSSAPI`：伺服器主體的服務名，預設 `mongodb` |
| `timeout` | 查找伺服器、連線和單條命令允許的時長，預設 `30000`，範圍 `1..600000` 毫秒 |

登入被拒絕、查詢使用者缺少權限、部署無法連線都會如實報告。

### 查詢使用者能看到什麼

MongoDB 只列出使用者擁有任一權限的資料庫和集合，所以查詢結果就是查詢使用者能看到的內容。請給它要撰寫策略的對象的 `read` 權限，或用 `readAnyDatabase` 涵蓋全部。MongoDB 自己的資料庫 `admin`、`config`、`local` 以及系統集合不會列出：策略不決定它們。

## 資源與存取類型

| 層級 | 匹配方式 | 存取類型 |
| --- | --- | --- |
| `deployment` | 萬用字元，區分大小寫。複本集名稱；分片叢集為 `sharded`，單機為 `standalone` | `list_databases`、`monitor` |
| `database` | 萬用字元，區分大小寫 | `create`、`drop_database` |
| `collection` | 萬用字元，區分大小寫。資料庫下的集合或檢視 | `read`、`change_stream`、`insert`、`update`、`remove`、`create_index`、`drop_index`、`alter`、`bypass_validation`、`drop` |

| 存取類型 | MongoDB 的動作 |
| --- | --- |
| `read` | `find`、`listIndexes`、`collStats`、`killCursors`；整個資料庫時還有 `listCollections` 和 `dbStats` |
| `change_stream` | `changeStream`；開啟變更串流時 MongoDB 還要求 `find` |
| `insert`、`update`、`remove` | `insert`、`update`、`remove` |
| `create_index`、`drop_index` | `createIndex`、`dropIndex` |
| `alter` | `collMod`、`validate`、`convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | 資料庫上的 `createCollection`：創建集合和檢視 |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`：所有資料庫的名稱，而不只是使用者有權限的那些 |
| `monitor` | `serverStatus`、`top`、`replSetGetStatus`、`connPoolStats`、`hostInfo`、`inprog` |

策略值中 `*` 匹配任意文字，`?` 匹配一個字元。策略編輯器會提示部署、資料庫，以及策略指定資料庫下的集合。

MongoDB 刪除資料庫的權限就是刪除其中每個集合的權限，所以只有當策略同時允許刪除所有集合時，代理才授予 `drop_database`。否則代理不授予並說明原因，而不是給出超過策略允許的權限。使用者和角色管理不是存取類型，任何策略都不會給出它們。

### 列與欄位

MongoDB 不按角色篩選列，也不按角色遮罩欄位，所以此服務類型不提供這兩項。檢視可以提供受限的讀取：MongoDB 只按檢視本身檢查讀取者，所以允許讀取檢視而不給源集合任何權限的策略，只會讓使用者看到檢視展示的內容。

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## 策略範例

假設部署中有資料庫 `sales` 和 `hr`。在同一資料服務中創建以下啟用的策略：

| 資源 | 規則 |
| --- | --- |
| `database=sales`、`collection=*` | 允許群組 `analysts` `read` |
| `database=sales`、`collection=orders_public` | 允許使用者 `support` `read` |
| `database=sales`、`collection=orders` | 允許使用者 `ingest` `insert` 和 `update`；拒絕群組 `contractors` `read` |
| `database=team_*` | 允許群組 `data-team` `create` |
| `database=team_*`、`collection=*` | 允許群組 `data-team` `read`、`insert`、`update`、`remove`、`create_index` 和 `drop` |
| `deployment=*` | 允許使用者 `dashboards` `monitor` |

代理應用快照後，`analysts` 的成員可以讀取 `sales` 的全部內容，包括之後創建的集合；`support` 不需要源集合就能讀取檢視；`ingest` 可以寫入 `orders` 但不能讀取；`data-team` 的成員在 `team_` 資料庫內自由工作；`dashboards` 能看到伺服器狀態而看不到資料。

## 執行策略

服務外掛管理策略並查詢資源。實施策略需要每個部署一個 MongoDB 代理，部署和檢查見 [MongoDB 代理](/zh-tw/external/mongodb-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [MongoDB 代理](/zh-tw/external/mongodb-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
