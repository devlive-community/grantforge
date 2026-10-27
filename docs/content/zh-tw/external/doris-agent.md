---
title: Apache Doris 代理
description: 在叢集旁執行 Apache Doris 代理，讓 GrantForge 策略成為 Doris 自己的角色、欄授權、工作負載群組授權與列政策，授予使用者的每個身分，只在 Doris 不會給出更多時才整個資料庫、整個 Catalog 授權，並從 Doris 的稽核日誌資料表回報存取。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Doris 代理是執行於叢集旁的服務。它讓 Doris 自己的存取控制與 `doris` 資料服務的簽章策略保持一致：每個使用者一個角色，恰好擁有策略給該使用者的權限，細到欄與工作負載群組；每條列篩選一條列政策。之後由 Doris 自己檢查每條陳述式，代理從不擋在用戶端與 FE 之間。發行套件以可執行的 JAR 提供它，即 `agents/doris/grantforge-agent-doris-<version>.jar`，需要 Java 17 或更新版本。

儲存庫的 Testcontainers 整合測試讓代理及發行的 JAR 在 Apache Doris `2.1`、`3.1`、`4.0` 與 `4.1` 上執行。這不代表認證了其他所有版本；代理拒絕早於 `2.1` 的叢集。服務外掛見 [Apache Doris](/zh-tw/plugins/doris/)。

## 運作方式

- **每個使用者一個角色，擁有 GrantForge 給的一切。** 對策略給了權限的每個使用者，代理維護一個以其命名的角色，例如 `alice` 的 `grantforge_alice`，其註解寫明對應的使用者，並把它授予該使用者的每個身分，例如 `'alice'@'%'` 與 `'alice'@'10.%'`。`SHOW ROLES` 列出 GrantForge 給 `alice` 的權限；代理不改動其他角色，也不改動不是它做出的授權。
- **下層沒有不同判定時整個資料庫、整個 Catalog 授權。** 策略在整個資料庫或 Catalog 上允許、且下層沒有策略能收回的讀取、匯入與檢視檢視表，會在資料庫或 Catalog 上授予，因此之後新建的資料表立即擁有它們。否則逐表授予，讀取則逐欄授予。修改逐表授予，因為 Doris 在資料庫上的這項權限也能重新命名資料庫。
- **從不超出策略允許的範圍。** Doris 在 Catalog 中建立資料庫的權限就是在其所有資料庫中建立資料表的權限，刪除資料庫的權限就是刪除其所有資料表的權限。代理只在策略同時給出更寬的權限時才授予它們；否則扣下該權限，並在日誌中寫明一次哪個使用者不能做什麼以及原因。
- **工作負載群組依名稱授權。** 工作負載群組上的策略在它允許的每個群組上授予 `USAGE_PRIV`，使用者因此能在該群組中執行查詢；之後新建的群組會在下次同步時涵蓋。
- **列篩選成為列政策。** 一條列篩選是使用者角色在該資料表上的一條寬鬆（permissive）列政策。新的篩選先於它所收窄的權限就位；修改篩選時，先放上一條不顯示任何列的限制性（restrictive）政策再替換，因此使用者看到的永遠不會多於新舊任何一個篩選。每個篩選會先在其資料表上試執行；Doris 無法在該資料表上執行或拒絕的篩選，例如引用了資料表中不存在的欄，會被替換為讓該使用者看不到任何列的篩選，並記入日誌。
- **採用當下時刻的策略。** 每次同步都依當時生效的策略規劃，因此有效期間準時開始與結束；並依 Doris 當下的使用者、Catalog、資料庫、資料表與欄規劃，新出現的會在下次同步時涵蓋。
- **Doris 角色就是群組。** 針對某個群組的策略適用於持有同名 Doris 角色的使用者，與 GrantForge 中使用者所屬的群組一起使用。
- **復原手動改動。** 每次同步時，代理把它的角色在 `SHOW ROLES` 中顯示的權限以及它的列政策與計畫比較，復原不一致之處。Doris 只依使用者顯示欄權限，且合併了使用者的全部授權：使用者無法讀取計畫中的全部欄時，代理授予這些欄；使用者能讀到的其他欄，代理從自己的角色上撤銷一次，之後仍能讀取的欄來自 GrantForge 之外的授權。沒有任何變化時，代理什麼也不傳送。
- **回報 GrantForge 之外的授權。** Doris 會把權限累加，並以 OR 合併寬鬆列政策，因此代理無法收回使用者直接或經由其他角色持有的權限。對每個給出了策略所拒絕內容的此類授權、欄授權或列政策，代理會警告一次，寫明使用者、授權及其來源；持有 `ADMIN_PRIV` 或 `NODE_PRIV`、不受任何角色約束的使用者也會被回報。
- **存取來自稽核日誌。** 代理讀取每個 FE 都會寫入的 `__internal_schema.audit_log`，把每條陳述式依它涉及的每個資料表、每個資料庫或 Catalog 回報為一筆事件；由於日誌不寫明資料表，代理以 Druid 的 Doris 解析器從陳述式本身讀出它涉及的資料表。被拒絕的陳述式依 Doris 錯誤中寫明的權限與物件回報為一筆拒絕事件。每筆事件都寫明是否由 GrantForge 決定，也就是策略是否與 Doris 判定一致，還是由 GrantForge 之外的授權允許。日誌的讀取落後當下時刻 FE 的批次寫入間隔 `audit_plugin_max_batch_interval_sec` 再加五秒。

| 陳述式 | 存取事件 |
| --- | --- |
| `SELECT`、`INSERT … SELECT` 的讀取部分 | 每個被讀取的資料表上的 `select` |
| `INSERT`、`UPDATE`、`DELETE`、`TRUNCATE` | 被寫入的資料表上的 `load` |
| `ALTER TABLE`、`DROP TABLE`、`SHOW CREATE VIEW` | 資料表上的 `alter`、`drop`、`show_view` |
| `CREATE TABLE`、`CREATE VIEW` | 資料庫上的 `create` |
| `DROP DATABASE` | 資料庫上的 `drop_database` |
| `CREATE DATABASE` | Catalog 上的 `create_database` |

## 部署

1. 新增 `doris` 資料服務並設定其策略。開啟 **資料權限 → 代理**，選擇該服務並核發代理權杖。權杖只顯示一次；請存入只有代理執行帳號可讀的檔案。
2. 建立代理的 Doris 使用者。它要建立角色與列政策、授予策略給出的權限，並讀取每個使用者的授權與稽核日誌，`ADMIN_PRIV` 允許這些操作：

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. 要回報存取，請在稽核日誌關閉時（例如 Doris 2.1）將其開啟：`SET GLOBAL enable_audit_plugin = true`。把 `audit_plugin_max_batch_interval_sec` 調得比預設的 60 秒更短，存取會更快回報。
4. 撰寫設定檔，替換範例中的位址、路徑與名稱：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   這些路徑位於代理所在的機器上，快取目錄必須可寫入。密碼檔依 UTF-8 讀取並去除首尾空白。
5. 啟動代理，例如作為 systemd 服務：

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   把 `<version>` 替換為發行版本。以 `java -Dgrantforge.…=… -jar …` 提供的設定會覆寫檔案中的設定。代理收到 SIGTERM 時乾淨地停止，傳送或暫存待送的事件；Doris 保留已取得的授權。
6. **把現有授權移轉為策略：** 在 GrantForge 之外做出的授權會與代理的角色並存生效。建立對應的策略後再撤銷這些授權；代理的警告會寫明給出了策略所拒絕內容的授權。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔、此代理的名稱，以及存放簽章快照、暫存事件與稽核日誌讀取位置的可寫入目錄 |
| `grantforge.signing.key.file` | 選用的固定快照簽章金鑰檔；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公鑰。省略時在首次連線時從伺服器取得 |
| `grantforge.doris.hosts`、`grantforge.doris.user` | 必填：FE 的 MySQL 協定位址（某個無法連線時依序嘗試下一個，未寫連接埠時為 `9030`），以及代理的 Doris 使用者 |
| `grantforge.doris.password.file` | 該使用者的密碼；省略時代理不帶密碼登入 |
| `grantforge.doris.ssl.mode`、`grantforge.doris.tls.ca` | `disable`（預設）、`trust`、`verify-ca` 或 `verify-full`，用於提供 TLS 的 FE；以及驗證它們的 PEM CA 憑證，空白時信任 Java 信任的憑證 |
| `grantforge.doris.timeout.ms` | 連線與一條陳述式可用的時間，預設 `30000`，範圍 `1..600000` 毫秒 |
| `grantforge.doris.role.prefix` | 代理角色名稱的開頭，預設 `grantforge_`；由字母、數字與底線組成，以底線結尾，最多 32 個字元。名稱以它開頭的角色與列政策都歸代理所有 |
| `grantforge.doris.unmanaged.users` | 代理不管理的使用者，以逗號分隔，例如服務帳號；代理始終不管理它自己的使用者、`root`、`admin` 以及持有 `ADMIN_PRIV` 或 `NODE_PRIV` 的使用者 |
| `grantforge.doris.catalogs` | 代理依資料庫、資料表與欄授權的 Catalog，以逗號分隔；空白時為全部 Catalog。其餘 Catalog 只取得能整體授予的權限，因此資料表很多的外部 Catalog 不必在每次同步時列舉 |
| `grantforge.sync.interval.ms` | 代理讓 Doris 保持一致的頻率，預設 `10000`，範圍 `1000..3600000` 毫秒 |
| `grantforge.audit.log` | 預設 `true`；`false` 不讀取稽核日誌，也不回報存取 |
| `grantforge.audit.log.delay.ms`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.batch` | 日誌讀取落後當下時刻的時間，預設為叢集的批次寫入間隔加五秒；讀取頻率，預設 `5000` 毫秒；以及一次讀取最多取多少筆，預設 `1000` |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | 連線 GrantForge 與讀取的逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理請求新策略的頻率，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）以及磁碟暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時間，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆傳送 |

## 使用者、群組與角色

策略依使用者名稱指定 Doris 使用者；同一使用者名稱在多個主機上的身分是同一個使用者，代理的角色會授予它的每個身分。群組為該使用者持有的 Doris 角色，加上 GrantForge 中該使用者所屬的群組；角色為 GrantForge 授予該使用者的角色。

## 決策與限制

- 沒有快照時代理不做任何改動。未啟用的服務，或繫結到類型不是 `doris` 的服務的權杖，不授予任何權限：代理刪除它的角色與列政策，由 Doris 自己的授權生效。
- 代理未執行期間，Doris 保留最後一次取得的授權；策略的變化要等代理執行後才生效。變化會在重新整理間隔加同步間隔之內到達 Doris。
- 在代理逐表授權的資料庫中新建的資料表，以及在代理逐庫授權的 Catalog 中新建的資料庫，會在下次同步時取得權限。
- Doris 的列政策無法約束 `root` 與 `admin`。對於 Doris 中沒有任何列政策的使用者，他能看到資料表中的每一列；在 GrantForge 之外為同一使用者建立的寬鬆列政策會放寬其篩選所顯示的範圍。
- Doris 在查詢於 BE 上執行時檢查工作負載群組上的 `USAGE_PRIV`；FE 自己就能回應的查詢，例如常數或快取的結果，不經此檢查。每個使用者都能使用 `normal` 群組，因為 Doris 把它給了所有人。
- 代理為之維護角色的使用者必須存在於 Doris 中；透過 LDAP 登入、在 Doris 中沒有自己使用者的人不會取得角色。
- Doris 只能透過 Apache Ranger 遮罩值，因此策略無法遮罩值。

## 相關指南

- [Apache Doris](/zh-tw/plugins/doris/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
