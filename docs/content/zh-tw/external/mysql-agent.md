---
title: MySQL 代理
description: 部署 MySQL 網路代理，設定 TLS 與策略同步，驗證欄位層級權限、遮罩、列篩選及拒絕錯誤。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MySQL Agent 是獨立的網路代理：用戶端連線代理而不是伺服器，代理在支援的陳述式到達 MySQL 之前，依 `mysql` 資料服務的簽章策略進行檢查。它無需安裝到 MySQL 伺服器內部。發行包提供可執行的 JAR：`agents/mysql/grantforge-agent-mysql-<version>.jar`，需要 Java 17 或更新版本。

儲存庫的 Testcontainers 整合測試以 `mysql:8.0`、`mysql:8.4` 為目標，使用 MariaDB Connector/J 與 MySQL Connector/J 用戶端；不代表其他伺服器版本或全部 MySQL 協定功能均已驗證。服務外掛的版本與連線要求請見 [MySQL](/zh-tw/plugins/mysql/)。

## 運作方式

- **登入直通。** 伺服器的交握封包送到用戶端，用戶端的回應送到伺服器，因此密碼仍由 MySQL 自己驗證（`caching_sha2_password` 與 `mysql_native_password`）；代理據此得知 MySQL 接受的使用者。策略中的使用者就是該使用者名稱，不含主機部分。
- **檢查 SQL 資源。** 代理剖析 COM_QUERY 與伺服器端預備陳述式，依陳述式涉及的資料庫、資料表與欄位檢查對應的存取類型。被拒絕的陳述式不會到達 MySQL，用戶端收到代理產生的、與 MySQL 相容的錯誤碼，例如 1142 或 1143。
- **讀取會被遮罩與篩選。** 當陳述式讀取的資料表對該使用者有遮罩欄位或列篩選時，代理會以一個同名的衍生資料表取代它，衍生資料表顯示遮罩後的值且只包含篩選後的列，因此聯結、條件與函式看到的都是使用者可見的內容。
- **資源授權事件**上報到 GrantForge，包括使用者、用戶端位址、資源、存取類型、決策與陳述式。稽核範圍見下文。
- MySQL 的執行結果轉送給用戶端，代理不會改寫資料庫中儲存的原始值。

## 部署

1. 先新增 `mysql` 資料服務並設定策略，再開啟 **資料權限 → 代理**，選取該服務並簽發代理權杖。權杖只顯示一次，請存入只有代理執行帳號可讀的檔案中。
2. 為代理建立中繼資料使用者。它從 `information_schema` 讀取資料表的欄位與預存函式，不需要讀取資料：`REFERENCES` 可見所有欄位，`SHOW_ROUTINE` 可見所有預存函式。請直接授予該使用者，不要透過角色授予；缺少 `SHOW_ROUTINE` 時代理不會啟動。

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. 撰寫設定檔，替換範例中的位址、路徑與名稱：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   上例同時啟用用戶端到代理、代理到 MySQL 的 TLS。`mysql-proxy.crt` 必須符合用戶端使用的代理主機名稱；MySQL 的憑證必須符合 `mysql.internal`。這些路徑在代理所在機器上解析，快取目錄需要可寫。密碼檔案以 UTF-8 讀取後會去掉首尾空白，不能儲存需要首尾空白的密碼。

4. 啟動代理，例如以 systemd 服務執行：

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   將 `<version>` 替換為發行包版本。以 `java -Dgrantforge.…=… -jar …` 指定的設定會覆寫檔案中的值。代理收到 SIGTERM 時正常停止，並傳送或暫存尚未送出的事件。
5. 讓用戶端連線代理的位址與連接埠。此後 MySQL 看到的是代理的位址，帳號的主機部分必須允許該位址。
6. **封住繞過代理的途徑：** 能直接連到 MySQL 的用戶端不受檢查。只允許代理與管理員存取伺服器的連接埠。

| 設定項 | 用途 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、本代理的名稱，以及存放簽章快照與暫存事件的可寫目錄 |
| `grantforge.signing.key.file` | 選填，固定快照簽章公鑰的檔案；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公鑰。省略時會在首次連線時從伺服器取得公鑰 |
| `grantforge.proxy.host`, `grantforge.proxy.port` | 監聽位址；預設為所有位址、連接埠 `3307`。連接埠範圍 `0..65535`；`0` 自動分配連接埠 |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | PEM 憑證與私鑰，必須同時設定才能向用戶端提供 TLS |
| `grantforge.proxy.tls.required` | 預設 `false`；`true` 拒絕未使用 TLS 的用戶端（錯誤 3159），並要求設定憑證與私鑰 |
| `grantforge.mysql.host`, `grantforge.mysql.port` | MySQL 主機必填；連接埠預設 `3306`，範圍 `1..65535` |
| `grantforge.mysql.tls` | 到 MySQL 的 TLS：`disabled`（預設）、`required`（加密但不驗證）、`verify-ca` 或 `verify-identity` |
| `grantforge.mysql.tls.ca` | 驗證 MySQL 的 PEM CA；空白時信任 Java 信任的憑證 |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | 中繼資料使用者必填；密碼檔案選填，省略時不向驅動程式傳入密碼 |
| `grantforge.mysql.metadata.cache.seconds` | 欄位與預存函式的快取時間，預設 `30` 秒，範圍 `0..86400`；`0` 停用快取重用 |
| `grantforge.mysql.connect.timeout.ms` | MySQL 連線逾時，預設 `10000` 毫秒，範圍 `1..600000`；中繼資料讀取的逾時固定為 `30000` 毫秒 |
| `grantforge.proxy.check.threads` | 檢查陳述式的執行緒數，預設 `8`，範圍 `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒，範圍 `1..2147483647` |
| `grantforge.refresh.interval.ms` | 初始或伺服器無法連線時的備援更新間隔，預設 `30000` 毫秒，最小 `1000`；正常心跳使用伺服器傳回的間隔 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | 預設批次 `500`、佇列 `10000` 個事件；批次範圍 `1..1000`，佇列至少容納一個批次，最多 `1000000` |
| `grantforge.audit.flush.interval.ms` | 稽核傳送間隔，預設 `5000` 毫秒，範圍 `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | 稽核磁碟暫存上限，預設 `67108864`（64 MiB），最小 `0` |

兩側的 TLS 彼此獨立。當只有一側使用 TLS、而 MySQL 需要完整密碼（`caching_sha2_password` 快取未命中）時，代理會在兩側之間傳遞密碼：明文用戶端要求伺服器公鑰時，代理以自己的 RSA 公鑰回應；TLS 用戶端連到明文伺服器時，代理以 MySQL 的公鑰加密密碼。透過代理連線時，用戶端不要設定 MySQL 的公鑰檔案。

## 陳述式所需的存取

| 陳述式 | 存取 |
| --- | --- |
| `SELECT`、子查詢、`WITH`、`EXPLAIN`、`SET @x = (SELECT …)` | 所用每個欄位的 `select`；不讀取任何欄位的資料表讀取（如 `COUNT(*)`、`EXISTS`）需要整個資料表的 `select` |
| `INSERT`、`LOAD DATA` | 寫入欄位的 `insert`（未列出欄位時為所有欄位）；`ON DUPLICATE KEY UPDATE` 另需 `update` |
| `REPLACE` | 欄位的 `insert` 與資料表的 `delete` |
| `UPDATE` | 被修改欄位的 `update` 與被讀取欄位的 `select` |
| `DELETE` | 資料表的 `delete` 與被讀取欄位的 `select` |
| `CREATE TABLE`、`CREATE VIEW` / `DROP TABLE`、`DROP VIEW`、`TRUNCATE` / `ALTER TABLE`、`ALTER VIEW`、`OPTIMIZE` | 資料表或檢視表的 `create` / `drop` / `alter` |
| `RENAME TABLE` | 舊名稱的 `alter` 與 `drop`，新名稱的 `create` 與 `insert` |
| `CREATE INDEX`、`DROP INDEX` | 資料表的 `index` |
| `CREATE DATABASE`、`DROP DATABASE` | 資料庫的 `create`、`drop` |
| `LOCK TABLES`、`ANALYZE`、`CHECK`、`CHECKSUM TABLE` | 資料表的 `select` |

不涉及資料的陳述式交由 MySQL 自己的授權決定：交易、`SET`、`USE`、`SHOW`、`DESCRIBE`、`KILL`，以及 `GRANT`、`CREATE USER` 等帳號陳述式。`information_schema` 與 `performance_schema` 和其他資料庫一樣需要策略。

代理會拒絕無法完整檢查的陳述式：可執行註解（`/*! … */`）、預存函式與 `CALL`、`CREATE PROCEDURE`、`FUNCTION`、`TRIGGER`、`EVENT`、SQL 層級的 `PREPARE`/`EXECUTE`、`HANDLER`、複寫與 clone 命令、在同一批次中與其他陳述式一起出現的 `USE` 或 `SET NAMES`，以及無法剖析的陳述式。

## 遮罩、列篩選及其限制

- 只要陳述式讀取該資料表，遮罩與篩選就會生效，包括子查詢、聯結、`INSERT … SELECT` 與 `CREATE TABLE … SELECT`（複製出的資料表中是遮罩後的值）。
- 單一資料表的 `UPDATE` 或 `DELETE` 只修改篩選條件可見的列。對有列篩選的資料表執行多資料表 `UPDATE` 或 `DELETE` 會被拒絕；寫入時使用被修改資料表中的遮罩欄位也會被拒絕，因為這可能複製或探測被隱藏的值。
- 不能對有遮罩或列篩選的資料表建立或修改檢視表：MySQL 之後執行檢視表時不經過代理。
- 策略版本不變時，預備陳述式沿用原有的檢查與改寫結果。版本變更會觸發重新檢查：若存取仍被允許且改寫文字不變，便繼續執行；否則回傳錯誤 1615，要求用戶端重新預備。
- 未寫資料表名稱的欄位會計入其可見範圍內所有含同名欄位的資料表；請為欄位加上資料表限定，以免同名的受限欄位造成拒絕。
- 透過 `*` 讀取的欄位來自中繼資料使用者所見的 `information_schema`，快取 `grantforge.mysql.metadata.cache.seconds` 秒。透過代理以一般 SQL 命令成功執行 DDL 後，快取會失效，下次查詢時重新讀取。外部或預備陳述式 DDL 不保證在執行時使快取失效；請等待快取到期或重新啟動代理。

## 執行範圍

代理檢查能從用戶端 SQL 剖析出的資源，不會遞迴展開既有檢視表、觸發程序或其他伺服器內部執行的操作。要保護底層資料表，還需限制 MySQL 原生權限，以及既有檢視表與觸發程序的使用。不要將管理員權限授予業務使用者，然後期待代理攔截管理陳述式。

列篩選不是通用的寫入約束：`INSERT`、`REPLACE`、`LOAD DATA` 會檢查寫入權限，但不會自動要求新列符合篩選運算式。`INSERT … ON DUPLICATE KEY UPDATE` 更新既有列的分支，也不會自動加入列篩選條件。協定壓縮、ZSTD、查詢屬性與選用結果中繼資料會在交握時停用。

## 策略與失敗

- 沒有可用快照，或權杖繫結的服務類型不是 `mysql` 時，所有存取資料的陳述式都會被拒絕。
- GrantForge 暫時無法連線或新快照的簽章無效時，代理保留已套用的快照。首次啟動且沒有任何可用快照時，拒絕資源存取。
- 停用服務會略過 GrantForge 資源授權、遮罩與列篩選；SQL 剖析與協定限制仍有效。停用服務不會封鎖資料存取。
- 代理檢查之後 MySQL 的授權依然有效；代理只能進一步收緊權限。
- 會跟隨 `COM_CHANGE_USER`，但用戶端與伺服器兩側 TLS 不一致時會拒絕；請改以另一使用者重新連線。

## 驗收範例

請資料庫管理員在獨立測試資料庫中建立以下資料與帳號，並將 `proxy-host` 替換為 MySQL 實際看到的代理來源位址：

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

為 `alice` 建立三項 GrantForge 策略：允許 `database=grantforge_demo`、`table=orders`、`column=*` 的 `select`；使用 `mask_show_last_4` 遮罩 `card`；以 `region = 'EU'` 篩選資料表。保持服務與三項策略啟用，並等待 **資料權限 → 代理** 顯示已套用最新策略版本。

以 MySQL 命令列用戶端連線代理。`-p` 會提示輸入密碼；CA 檔案必須信任代理憑證：

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- 只有一列：1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000：GrantForge 未授予 update
```

範例中的 MySQL 原生權限已允許 `UPDATE`，所以拒絕結果可驗證代理確實執行策略。接著將列篩選改為 `region = 'US'`，等待代理套用新版本後再查詢。此時只應出現第 2 列，卡號為 `xxxxxxxxxxxx4444`。

## 疑難排解與稽核

| 錯誤碼 / SQLSTATE | 意義與檢查項目 |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | 資料庫、資料表或欄位存取被拒絕。訊息含 `by GrantForge` 時，檢查策略使用者、資源、存取類型及快照版本；其他拒絕也需檢查 MySQL 原生權限 |
| `1046 / 3D000` | 未選取資料庫；使用 `USE`、設定連線預設資料庫，或使用完整資料表名稱 |
| `1227 / 42000` | 操作被禁止或無法安全檢查；依訊息檢查陳述式限制與中繼資料權限 |
| `1064 / 42000` | SQL 無法剖析 |
| `1615 / HY000` | 策略變更後重新預備陳述式 |
| `3159 / HY000` | 代理要求 TLS，但用戶端未使用 TLS 連線 |
| `1043 / 08S01` | 後端協定或 TLS 交握失敗；檢查 TLS 模式、憑證、主機名稱及伺服器能力 |
| `2003 / HY000` | 代理無法連線 MySQL；檢查位址、網路及後端狀態 |

在 **資料權限 → 存取稽核** 中查找 `alice` 與對應資源的允許、拒絕事件。一項 SQL 可產生多個資源授權事件，事件在後端執行前記錄，因此 `ALLOWED` 不代表 MySQL 執行成功。剖析失敗、不存取資源的陳述式與停用的服務可能不產生資源事件；同一策略版本下重複執行預備陳述式，不會重複產生授權事件。這是資源授權稽核，不是完整的 SQL 執行記錄。

## 相關指南

- [MySQL](/zh-tw/plugins/mysql/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
