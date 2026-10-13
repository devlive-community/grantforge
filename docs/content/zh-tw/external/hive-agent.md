---
title: Apache Hive 代理
description: 在 HiveServer2 中安裝代理，以 GrantForge 策略檢查陳述式、篩選 SHOW 結果、遮罩欄位並篩選列。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Hive 代理是 Hive 授權 API 的一個授權器，與 Apache Ranger 的 Hive 外掛相同：HiveServer2 在執行每個陳述式前詢問它，它依據 `hive` 資料服務的簽章策略回答。發行套件以單一相容 Java 8 的 JAR 提供，即 `agents/hive/grantforge-agent-hive-<version>.jar`，相依套件已重新定位封裝在內。

儲存庫的 Testcontainers 整合測試在 HiveServer2 `3.1.3`、`4.0.1` 與 `4.1.0` 中執行此 JAR。這並不代表對其他所有版本的認證。服務外掛請見 [Apache Hive](/zh-tw/plugins/hive/)。

## 運作方式

- **檢查陳述式。** 對陳述式讀寫的每個物件，代理依操作所需的存取類型詢問策略，方式與 Ranger 相同；第一次拒絕即令陳述式失敗，錯誤為 `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`。查詢逐欄檢查。
- **篩選清單。** `SHOW DATABASES` 與 `SHOW TABLES` 只列出使用者可使用的內容：對該資料庫或資料表、或其中任一物件有任何存取權限。`USE` 與 `DESCRIBE DATABASE` 的要求相同。
- **遮罩與列篩選。** 使用者在某資料表上有遮罩欄位或列篩選時，HiveServer2 會以遮罩運算式與條件改寫查詢，因此聯結、條件與函式看到的都只是使用者可見的內容。
- **無法繞過遮罩。** 對使用者有遮罩或列篩選的資料表進行寫入、修改或匯出會被拒絕，與 Ranger 的預設行為一致。
- **授權與角色由 GrantForge 管理。** Hive 的 `GRANT`、`REVOKE`、`SHOW GRANT`、`CREATE ROLE`、`SET ROLE` 等角色陳述式一律拒絕；`SHOW CURRENT ROLES` 列出 GrantForge 給予使用者的角色。
- **拒絕不安全的陳述式。** `dfs` 指令會以 HiveServer2 自身的權限存取檔案系統，因此被拒絕。在 HiveServer2 工作階段中禁止 `TRANSFORM` 指令碼，使用者只能 `SET` Hive SQL 標準授權所允許的設定。
- **每次決策都會回報** 給 GrantForge，包括使用者、用戶端位址、資源、存取類型、結果、操作與陳述式。

| 操作 | 存取類型 |
| --- | --- |
| 查詢、`SHOW CREATE TABLE`、`SHOW PARTITIONS`、`SHOW TBLPROPERTIES`、`DESCRIBE`、`SHOW COLUMNS`、`ANALYZE` | `select` |
| `INSERT`、`UPDATE`、`DELETE`、`MERGE`、`TRUNCATE`、`LOAD`（目標） | `update` |
| `CREATE DATABASE`、`CREATE TABLE`、`CREATE VIEW`、`CREATE FUNCTION` | 對建立的物件 `create`，對讀取的物件 `select` |
| `ALTER …`、`MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`、`UNLOCK` | `lock` |
| `SHOW DATABASES`、`SHOW TABLES`、`USE`、`DESCRIBE DATABASE` | 對該物件或其中任一物件的任何存取 |
| `REPL DUMP`、`REPL LOAD`、`REPL STATUS` | `repladmin` |
| `KILL QUERY`、資源計畫、LLAP 管理 | `hiveservice` 上的 `serviceadmin` |
| 暫時函式、`ADD JAR`、`COMPILE` | `global` 上的 `tempudfadmin` |
| 陳述式讀寫的位置 | `url` 上的 `read` 或 `write` |

## 部署

1. 新增 `hive` 資料服務並設定策略，其中應包含一條策略，允許 HiveServer2 執行自身工作所用的使用者（如 `hive`）擁有所需的全部權限。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有 HiveServer2 帳戶可讀的檔案。
2. 將 JAR 複製到 **每個 HiveServer2** 的 `lib` 目錄，例如 `/opt/hive/lib`。
3. 在每個 HiveServer2 的 `hiveserver2-site.xml` 或 `hive-site.xml` 中加入以下設定，替換範例位址、路徑與執行個體名稱：

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   快取目錄必須對 HiveServer2 帳戶可寫。與 Ranger 相同，`hive.server2.enable.doAs=false` 讓 HiveServer2 以自身帳戶讀寫資料，由策略決定誰能看到什麼。
4. 重新啟動 HiveServer2。代理隨第一個工作階段啟動、隨伺服器停止，並傳送或暫存尚未傳送的事件。
5. **關閉繞過 HiveServer2 的途徑：** 代理只檢查在 HiveServer2 中執行的陳述式。直接存取中繼資料庫或倉儲檔案的用戶端（如 Hive CLI、Spark 或 HDFS 用戶端）不受檢查；請以中繼資料庫與 HDFS 自身的權限或 [HDFS 代理](/zh-tw/external/hdfs-agent/)保護它們。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此 HiveServer2 的名稱，以及存放簽章快照與暫存事件的可寫目錄 |
| `grantforge.signing.key.file` | 選填，固定快照簽章金鑰的檔案；從「代理」頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時於首次連線時從伺服器取得 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 的連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理取得新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列容量（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與暫存上限（預設 64 MiB） |

代理從 HiveServer2 的設定檔與系統屬性（`-Dgrantforge.…`，優先）讀取設定，從不讀取工作階段設定，因為用戶端可以設定工作階段設定。

## 使用者、群組與角色

策略中的使用者是 HiveServer2 為工作階段確定的使用者：用戶端登入所用的使用者名稱，或其 Kerberos 主體的短名（`alice@EXAMPLE.COM` 對應 `alice`）。群組為 Hadoop 群組對應在 HiveServer2 中為使用者給出的群組，加上 GrantForge 中使用者所屬的群組；角色為 GrantForge 給予使用者的角色。

## 決策與限制

- 沒有可用快照或無法讀取策略時，所有陳述式都會被拒絕。未啟用的服務不檢查 HiveServer2，與未安裝授權器時相同。
- 權杖綁定的服務類型不是 `hive` 時，一律拒絕。
- 陳述式讀寫的位置由 `url` 策略決定；與 Ranger 不同，代理不會退回檔案系統自身的權限。
- Hive 未給出物件的陳述式會在所有資料庫上檢查：沒有物件的 `REPL` 陳述式需要 `repladmin`，沒有物件的變更資料表或資料庫擁有者需要 `alter`，皆作用於資料庫 `*`。
- `EXPLAIN`、`DESCRIBE FUNCTION`、`SHOW FUNCTIONS`、`SET` 以及 Ranger 不檢查的其他陳述式同樣不檢查；JDBC 用戶端的中繼資料呼叫（如 `getTables`）不做篩選。
- 代理在 HiveServer2 中運作，不涵蓋中繼資料庫內部的授權。

## 相關指南

- [Apache Hive](/zh-tw/plugins/hive/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
