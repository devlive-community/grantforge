---
title: ClickHouse 代理
description: 在伺服器旁執行 ClickHouse 代理，讓 GrantForge 策略成為 ClickHouse 自己的角色、授權與列政策，細到欄位與列，並從查詢日誌回報存取。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

ClickHouse 代理是執行在伺服器旁的一個服務。它讓 ClickHouse 自己的存取控制與 `clickhouse` 資料服務的簽章策略保持一致：每位使用者一個角色，恰好擁有策略給該使用者的權限，每條列篩選對應一條列政策。之後由 ClickHouse 自己在其每種通訊協定上檢查每個查詢，代理從不擋在用戶端與伺服器之間。發行套件以一個可執行的 JAR 提供它：`agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`，需要 Java 17 或更新版本。

儲存庫的 Testcontainers 整合測試在 ClickHouse `24.8`、`25.3`、`25.8`、`26.3` 與 `26.8` 上執行代理以及發行的 JAR。這不代表認證了其他所有版本；代理拒絕早於 `24.8` 的伺服器。服務外掛見 [ClickHouse](/zh-tw/plugins/clickhouse/)。

## 運作方式

- **每位使用者一個角色，擁有 GrantForge 給的一切。** 對策略給了權限的每位使用者，代理維護一個以其命名的角色，例如 `alice` 的 `grantforge_alice`，並把它作為預設角色授予該使用者。`SHOW GRANTS FOR grantforge_alice` 列出 GrantForge 給 `alice` 的全部權限；代理不更動其他角色，也不更動不是它做出的授權。
- **下層沒有不同判定時整個資料庫授權。** 策略在整個資料庫上允許、且沒有策略能從其中某張資料表或某個欄位收回的存取類型，會在資料庫上授予，因此之後新建的資料表立即擁有它。否則逐表授予，讀寫則逐欄授予。
- **列篩選成為列政策。** 一條列篩選是使用者角色在該資料表上的一條寬鬆（permissive）列政策。新的或修改後的篩選會先於它所收窄的權限就位。ClickHouse 拒絕的篩選，或其文字會讓列政策作用到別人的篩選，會被替換為讓該使用者看不到任何列的篩選，並記入日誌。
- **採用目前時刻的策略。** 每次同步都依當時生效的策略規劃，因此有效期間準時開始與結束；並依 ClickHouse 當下的使用者、資料庫、資料表與欄位規劃，新出現的會在下次同步時涵蓋。
- **ClickHouse 角色就是群組。** 針對某個群組的策略適用於直接或經由其他角色持有同名 ClickHouse 角色的使用者，與 GrantForge 中使用者所屬的群組一起使用。
- **復原手動變更。** 代理記得它給每個角色與列政策的內容；與之不同的會在下次同步時復原。沒有任何變化時，代理什麼也不傳送。
- **回報 GrantForge 之外的授權。** ClickHouse 會把權限累加，因此代理無法收回使用者直接或經由其他角色持有的權限。對每個給出了策略所拒絕內容的此類授權，代理會警告一次，寫明使用者、授權、來自哪個角色以及哪條策略。
- **存取來自查詢日誌。** 代理讀取 `system.query_log`，把每個陳述式依它涉及的每張資料表、每個資料庫或伺服器回報為一筆事件，被拒絕的陳述式依 ClickHouse 錯誤中寫明的內容回報為一筆拒絕事件。每筆事件都寫明是否由 GrantForge 決定，即策略是否與 ClickHouse 判定一致，還是由 GrantForge 之外的授權允許。

| 陳述式 | 存取事件 |
| --- | --- |
| `SELECT`、`INSERT … SELECT` 的讀取部分 | 每張被讀取的資料表上的 `select` |
| `INSERT` | 被寫入的資料表上的 `insert` |
| `ALTER`、`ALTER UPDATE`、`ALTER DELETE`、`DELETE`、`UPDATE` | 資料表上的 `alter` |
| `DROP TABLE`、`TRUNCATE`、`OPTIMIZE` | 資料表上的 `drop`、`truncate`、`optimize` |
| `CREATE TABLE`、`CREATE VIEW`、`CREATE DICTIONARY` | 資料庫上的 `create` |
| `DROP DATABASE` | 資料庫上的 `drop_database` |
| `CREATE DATABASE`、`KILL QUERY`、`SYSTEM …` | 伺服器上的 `create_database`、`kill_query`、`system` |

## 部署

1. 新增 `clickhouse` 資料服務並設定其策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有代理執行帳號可讀的檔案。
2. 建立代理的 ClickHouse 使用者。它要授予策略給出的權限，因此需要帶授權選項的這些權限，以及維護角色與列政策的存取管理權限：

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   代理為其維護角色的使用者必須存放在可以修改的地方，即 `local_directory` 或 `replicated` 使用者目錄中，`CREATE USER` 就存放在那裡。`users.xml` 與 LDAP 中的使用者不受影響，並只記錄一次日誌。叢集應把使用者、角色與列政策放在 `replicated` 使用者目錄中：如此一個代理即可服務整個叢集。
3. 撰寫設定檔，替換範例中的位址、路徑與名稱：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   這些路徑位於代理所在的機器上，快取目錄必須可寫入。密碼檔以 UTF-8 讀取並去除首尾空白。
4. 啟動代理，例如作為 systemd 服務：

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   把 `<version>` 替換為發行版本。以 `java -Dgrantforge.…=… -jar …` 提供的設定會覆寫檔案中的設定。代理收到 SIGTERM 時會乾淨地停止，並傳送或暫存待傳事件；ClickHouse 保留它已取得的授權。
5. **把既有授權遷移為策略：** 在 GrantForge 之外做出的授權會與代理的角色一同繼續生效。請建立對應的策略，然後撤銷這些授權；代理的警告會寫出其中給出了策略所拒絕內容的那些。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此代理的名稱，以及存放簽章快照、暫存事件與查詢日誌讀取位置的可寫入目錄 |
| `grantforge.signing.key.file` | 選用的固定快照簽章金鑰檔；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公鑰。省略時於首次連線時從伺服器取得 |
| `grantforge.clickhouse.urls`、`grantforge.clickhouse.user` | 必填：伺服器的 HTTP 介面（某台無法連線時依序嘗試下一台），以及代理的 ClickHouse 使用者 |
| `grantforge.clickhouse.password.file` | 該使用者的密碼；省略時代理不帶密碼登入 |
| `grantforge.clickhouse.tls.ca` | 驗證 `https://` 伺服器的 PEM CA 憑證；空白時信任 Java 所信任的 |
| `grantforge.clickhouse.tls.cert`、`grantforge.clickhouse.tls.key` | PEM 用戶端憑證與私密金鑰，需同時設定，用於以憑證（`IDENTIFIED WITH ssl_certificate`）代替密碼登入 |
| `grantforge.clickhouse.timeout.ms` | 傳往 ClickHouse 的請求可用的時長，預設 `30000`，範圍 `1..600000` 毫秒 |
| `grantforge.clickhouse.role.prefix` | 代理角色名稱的開頭，預設 `grantforge_`；由字母、數字與底線組成，以底線結尾。名稱以它開頭的角色與列政策都歸代理所有 |
| `grantforge.clickhouse.unmanaged.users` | 代理不處理的使用者，以逗號分隔，例如管理員；代理一律不處理它自己的使用者 |
| `grantforge.sync.interval.ms` | 代理讓 ClickHouse 保持一致的間隔，預設 `10000`，範圍 `1000..3600000` 毫秒 |
| `grantforge.audit.query.log` | 預設 `true`；`false` 表示不讀取查詢日誌，也不回報存取 |
| `grantforge.audit.query.log.cluster` | 透過 `clusterAllReplicas` 讀取其各副本查詢日誌的叢集；空白時讀取代理所連線的伺服器 |
| `grantforge.audit.query.log.delay.ms`、`grantforge.audit.query.log.interval.ms`、`grantforge.audit.query.log.batch` | 讀取日誌時落後於目前的時長，預設 `10000` 毫秒，因為 ClickHouse 每 7.5 秒寫入一次日誌；讀取間隔，預設 `5000` 毫秒；以及每次讀取的最多筆數，預設 `1000` |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與磁碟暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆回報 |

## 使用者、群組與角色

策略中的使用者是 ClickHouse 使用者。群組為該使用者直接或經由其他角色持有的 ClickHouse 角色，加上 GrantForge 中該使用者所屬的群組；角色為 GrantForge 授予該使用者的角色。

## 決策與限制

- 沒有快照時代理不做任何變更。未啟用的服務，或綁定到類型不是 `clickhouse` 的服務的權杖，不授予任何權限：代理刪除它的角色與列政策，由 ClickHouse 自己的授權生效。
- 代理未執行期間，ClickHouse 保留最後一次取得的授權；策略的變化要等代理執行後才生效。變化會在重新整理間隔加同步間隔之內到達 ClickHouse。
- 在代理逐表授權的資料庫中新建的資料表，會在下次同步時取得權限。
- 對於 ClickHouse 中沒有任何列政策的使用者，他能看到資料表中的每一列，這是 ClickHouse 的 `users_without_row_policies_can_read_rows` 設定的預設行為；關閉該設定後，沒有篩選的使用者看不到被篩選資料表中的任何列。在 GrantForge 之外為同一使用者建立的寬鬆列政策會放寬其篩選所顯示的範圍。
- `system` 授予所有 `SYSTEM` 陳述式，包括 `SYSTEM SHUTDOWN`。
- 查詢日誌只回報 ClickHouse 記錄的內容：關閉了 `log_queries` 的使用者的陳述式不會回報。對於被拒絕的陳述式，若使用者缺少某一欄位的權限，ClickHouse 可能只寫明資料表；此時事件寫明的就是資料表。
- ClickHouse 只在 ClickHouse Cloud 中提供欄位遮罩，因此策略不能對值遮罩。

## 相關指南

- [ClickHouse](/zh-tw/plugins/clickhouse/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
