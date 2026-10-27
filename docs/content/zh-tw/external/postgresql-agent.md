---
title: PostgreSQL 代理
description: 在伺服器旁執行 PostgreSQL 代理，讓 GrantForge 策略成為 PostgreSQL 自己的角色、權限與列安全政策，細到欄位與列，每個資料庫在一個交易中變更，並從伺服器日誌回報存取。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

PostgreSQL 代理是執行在伺服器旁的一個服務。它讓 PostgreSQL 自己的存取控制與 `postgresql` 資料服務的簽章策略保持一致：每位使用者一個角色，在它所維護的每個資料庫中恰好擁有策略給該使用者的權限，每條列篩選對應一條列安全政策。之後由 PostgreSQL 自己檢查每個陳述式，代理從不擋在用戶端與伺服器之間。發行套件以一個可執行的 JAR 提供它：`agents/postgresql/grantforge-agent-postgresql-<version>.jar`，需要 Java 17 或更新版本。

儲存庫的 Testcontainers 整合測試在帶有 pgaudit 的 PostgreSQL `15`、`16`、`17` 與 `18` 上執行代理以及發行的 JAR。這不代表認證了其他所有版本；代理拒絕早於 `15` 的伺服器。服務外掛見 [PostgreSQL](/zh-tw/plugins/postgresql/)。

## 運作方式

- **每位使用者一個角色，擁有 GrantForge 給的一切。** 對策略給了權限的每位使用者，代理維護一個以其命名的角色，例如 `alice` 的 `grantforge_alice`，並讓該使用者成為不需 `SET ROLE` 即擁有該角色權限的成員。代理會在它的每個角色上寫入註解，沒有該註解的角色絕不會被當成它自己的，因此它不更動其他角色，也不更動不是它授予的權限。
- **每個資料庫在一個交易中變更。** 代理登入它維護的每個資料庫，並在其中以一個交易完成變更，因此使用者看到的是一個策略版本整體套用到某個資料庫，或完全沒有套用。
- **下層沒有不同判定時整張資料表授權。** 策略在整張資料表上允許、且沒有策略能從某個欄位收回的存取類型，會在資料表上授予，因此之後新增的欄位立即擁有它。否則讀取、插入與更新逐欄授予。資料庫的 `CONNECT`、結構描述的 `USAGE`，以及使用者可插入的自動遞增欄位所用序列的 `USAGE`，會隨之一併授予。
- **列篩選只會收窄。** 一條列篩選是使用者角色在該資料表上針對 `SELECT` 的限制性列安全政策。若列安全未開啟，代理會開啟它，並同時建立一條向所有人顯示所有列的寬鬆政策，以其前綴命名（`grantforge`），讓其他人看到的內容不變；當資料表上不再有篩選、也沒有資料表自己的政策時，代理會再次關閉列安全。擁有者開啟的列安全仍歸擁有者，代理的政策只會收窄其顯示的內容。PostgreSQL 拒絕的篩選、會改變政策約束對象的篩選，或包含多個陳述式的文字，都會被替換為讓該使用者看不到任何列的政策，並記入日誌。
- **採用目前時刻的策略。** 每次同步都依當時生效的策略，以及 PostgreSQL 當下的使用者、結構描述、資料表與欄位規劃，因此有效期間準時開始與結束，新出現的會在下次同步時涵蓋。
- **角色就是群組。** 針對某個群組的策略適用於直接或經由其他角色成為同名 PostgreSQL 角色成員的使用者，與 GrantForge 中使用者所屬的群組一起使用。
- **復原手動變更。** 每次同步時，代理會把它的角色的權限與 PostgreSQL 顯示的內容比較，把它的列安全政策與它上次建立的比較；不同之處會在下次同步時復原。沒有任何變化時，代理什麼也不傳送。
- **回報 GrantForge 之外的權限。** PostgreSQL 會把權限累加，因此代理無法收回使用者自己擁有、經由其他角色或經由 PUBLIC 擁有的權限，也無法收回使用者對自己擁有的資料表能做的事。對每個給出了策略所拒絕內容的此類權限，代理會警告一次，寫明使用者、權限、使用者如何擁有它以及哪條策略。
- **存取來自伺服器日誌。** 代理讀取 PostgreSQL 的 JSON 伺服器日誌：PostgreSQL 允許的存取來自 pgaudit 的工作階段日誌，一個陳述式讀取或寫入的每張資料表一筆事件；PostgreSQL 因缺少權限而拒絕的存取，依錯誤寫明的對象回報為一筆拒絕事件。每筆事件都寫明是否由 GrantForge 決定，即策略是否與 PostgreSQL 判定一致，還是由 GrantForge 之外的權限允許。

| 陳述式 | 存取事件 |
| --- | --- |
| `SELECT`、`COPY … TO`、其他陳述式中讀取的部分 | 每張被讀取的資料表上的 `select` |
| `INSERT`、`COPY … FROM` | 被寫入的資料表上的 `insert` |
| `UPDATE`、`MERGE`、`SELECT … FOR UPDATE` | 資料表上的 `update` |
| `DELETE`、`TRUNCATE` | 資料表上的 `delete`、`truncate` |
| `CREATE TRIGGER` | 資料表上的 `trigger` |
| `CREATE TABLE`、`CREATE VIEW` 與其他物件 | 結構描述上的 `create` |
| `CREATE SCHEMA`、`CREATE TEMPORARY TABLE` | 資料庫上的 `create_schema`、`temporary` |
| 被拒絕的連線、因缺少 `USAGE` 而被拒絕的結構描述 | 資料庫上的 `connect`、結構描述上的 `usage` |

## 部署

1. 新增 `postgresql` 資料服務並設定其策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有代理執行帳號可讀的檔案。
2. 以超級使用者身分建立代理的 PostgreSQL 使用者。它要在不屬於它的資料表上授予權限、開啟與關閉列安全、維護角色並讀取伺服器日誌：

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   測試中代理以超級使用者執行。超級使用者既不受權限約束，也不受列安全約束，因此代理不為他們維護角色。
3. 若要回報存取，請讓 PostgreSQL 以 JSON 寫入伺服器日誌並載入 pgaudit（來自 PGDG 的套件，例如 `postgresql-17-pgaudit`，或所用平台提供的），在 `postgresql.conf` 中設定以下內容，然後重新啟動伺服器：

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   沒有 pgaudit 時，代理只回報 PostgreSQL 拒絕的存取，不回報它允許的存取；沒有 JSON 日誌時，代理不回報任何存取，並記錄一次日誌說明。
4. 撰寫設定檔，替換範例中的位址、路徑與名稱：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   這些路徑位於代理所在的機器上，快取目錄必須可寫入。密碼檔以 UTF-8 讀取並去除前後空白。
5. 啟動代理，例如作為 systemd 服務：

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   把 `<version>` 替換為發行版本。以 `java -Dgrantforge.…=… -jar …` 提供的設定會覆寫檔案中的設定。代理收到 SIGTERM 時會乾淨地停止，並傳送或暫存待送事件；PostgreSQL 保留它已取得的權限。
6. **把既有權限遷移為策略：** 在 GrantForge 之外授予的權限會與代理的角色一同繼續生效。請建立對應的策略，然後撤銷這些權限；代理的警告會寫出其中給出了策略所拒絕內容的那些。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔、此代理的名稱，以及存放簽章快照、暫存事件與伺服器日誌讀取位置的可寫入目錄 |
| `grantforge.signing.key.file` | 選用的固定快照簽章金鑰檔；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時在首次連線時從伺服器取得 |
| `grantforge.postgresql.hosts`、`grantforge.postgresql.user` | 必填：以 `host:port` 表示的伺服器（依序嘗試，直到某台可以連線且為主要伺服器），以及代理的 PostgreSQL 使用者 |
| `grantforge.postgresql.database` | 代理首先登入的資料庫，用於讀取角色、資料庫與伺服器日誌，預設 `postgres` |
| `grantforge.postgresql.password.file` | 該使用者的密碼；省略時代理不帶密碼登入 |
| `grantforge.postgresql.ssl.mode` | 依 libpq 的 `sslmode` 命名的 TLS 方式，預設 `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca 與 verify-full：驗證伺服器的 PEM CA 憑證；空白時信任 Java 所信任的 |
| `grantforge.postgresql.tls.cert`、`grantforge.postgresql.tls.key` | PEM 用戶端憑證及其 PKCS #8 私密金鑰，需同時設定，用於以憑證（`cert` 驗證）代替密碼登入；它們需要 `require` 或某種驗證方式，與驗證方式一起使用時還需要 `grantforge.postgresql.tls.ca` |
| `grantforge.postgresql.timeout.ms` | 連線與陳述式可用的時長，預設 `30000`，範圍 `1..600000` 毫秒 |
| `grantforge.postgresql.databases` | 代理維護的資料庫，以逗號分隔；空白時維護所有允許連線的資料庫 |
| `grantforge.postgresql.role.prefix` | 代理的角色名稱開頭，預設 `grantforge_`；由字母、數字與底線組成，以底線結尾，最多 32 個字元，且不以 `pg_` 開頭。名稱以它開頭且帶有代理註解的角色，以及以這些角色或去掉末尾底線的前綴命名的列安全政策，都歸代理所有 |
| `grantforge.postgresql.unmanaged.users` | 代理不處理的使用者，以逗號分隔，例如管理員；代理始終不處理它自己的使用者與超級使用者 |
| `grantforge.sync.interval.ms` | 代理讓 PostgreSQL 保持一致的間隔，預設 `10000`，範圍 `1000..3600000` 毫秒 |
| `grantforge.audit.server.log` | 預設 `true`；`false` 表示不讀取伺服器日誌，也不回報存取 |
| `grantforge.audit.server.log.interval.ms`、`grantforge.audit.server.log.max.bytes` | 讀取日誌的間隔，預設 `5000` 毫秒；以及每次讀取的最多位元組數，預設 16 MiB，積壓的日誌會分多次讀取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與磁碟暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆傳送 |

## 使用者、群組與角色

策略中的使用者是 PostgreSQL 使用者，即可以登入的角色。群組為該使用者直接或經由其他角色所屬的角色，加上 GrantForge 中該使用者所屬的群組；角色為 GrantForge 授予該使用者的角色。

## 決策與限制

- 沒有快照時代理不做任何變更。未啟用的服務，或綁定到類型不是 `postgresql` 的服務的權杖，不授予任何權限：代理刪除它的角色與列安全政策，由 PostgreSQL 自己的權限生效。
- 代理未執行期間，PostgreSQL 保留最後一次取得的權限；策略的變化要等代理執行後才生效。變化會在重新整理間隔加同步間隔之內到達 PostgreSQL。
- 同步之後新建的資料表，會在下次同步時取得權限。
- 超級使用者與資料表的擁有者可以對資料表做任何事；超級使用者、帶有 `BYPASSRLS` 的角色與資料表的擁有者不受資料表的列安全約束，除非擁有者強制啟用。
- 檢視表以其擁有者的權限讀取它的資料表，除非它是 `security_invoker`：伺服器日誌會把檢視表讀取的資料表回報為由 GrantForge 之外的權限允許。
- 直接讀取分割區時，不受其分割資料表的列安全篩選。
- pgaudit 在 PostgreSQL 執行陳述式之前就記錄它，因此之後才失敗的陳述式（例如其序列被拒絕的插入）會被回報為允許，其拒絕則回報為拒絕。pgaudit 不記錄的工作階段中的陳述式不會回報。
- PostgreSQL 的拒絕訊息只寫資料表名稱而不寫結構描述：事件寫明的是陳述式所限定的資料表、該名稱唯一的那張資料表，或 `public` 中的那張。未寫結構描述而建立的物件會以 `public` 回報。
- 代理讀取它所登入的伺服器（即主要伺服器）的伺服器日誌；不讀取複本的日誌。
- PostgreSQL 沒有欄位遮罩，因此策略不能對值遮罩。

## 相關指南

- [PostgreSQL](/zh-tw/plugins/postgresql/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
