---
title: MongoDB 代理
description: 在部署旁運行 MongoDB 代理，把 GrantForge 策略變成 MongoDB 自身的角色，一步替換角色的權限，讀取 GrantForge 之外的內建和自訂角色實際給出了什麼，並從稽核日誌或 Profiler 報告存取。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MongoDB 代理是運行在部署旁的服務。它讓 MongoDB 自身的存取控制與 `mongodb` 資料服務的已簽章策略保持一致：每個使用者一個角色，角色持有的權限恰好等於策略給該使用者的權限。之後每條命令都由 MongoDB 自己檢查，代理不擋在任何用戶端前面。發行套件以可運行 JAR `agents/mongodb/grantforge-agent-mongodb-<version>.jar` 提供，需要 Java 17 或更高版本。

儲存庫的 Testcontainers 整合測試讓代理和發行的 JAR 運行在 MongoDB `6.0`、`7.0`、`8.0`、`9.0` 複本集和一個 `9.0` 分片叢集上，並讀取 Percona Server for MongoDB `7.0`、`8.0` 的稽核日誌。這並不認證其他所有版本；代理會拒絕低於 `6.0` 的部署。服務外掛見 [MongoDB](/zh-tw/plugins/mongodb/)。

## 運作方式

- **每個使用者一個角色，持有 GrantForge 給的權限。** 對策略給了權限的每個使用者，代理在 `admin` 中維護一個以其命名的角色，如 `alice` 的 `grantforge_alice`，並授予該名稱的每個 MongoDB 使用者，如 `alice@admin` 和 `alice@sales`。`rolesInfo` 能看到 GrantForge 給了 `alice` 什麼。代理不改動其他角色。
- **變更一步完成。** `updateRole` 一次替換角色的全部權限，使用者不會同時持有新舊權限，也不會兩者都沒有。
- **下層沒有其他決定時按整個資料庫授權。** 策略在整個資料庫上允許、且下層沒有策略能收回的存取類型，會授權在資料庫上，之後創建的集合立刻擁有。否則逐集合授權，新集合在下次同步時涵蓋。
- **絕不超過策略允許的範圍。** MongoDB 刪除資料庫的權限就是刪除其中每個集合的權限。只有策略也給了後者時代理才授予；否則不授予，並記錄一次哪個使用者不能做什麼以及原因。
- **使用當前時刻的策略。** 每次同步都按當時生效的策略以及 MongoDB 現有的使用者、資料庫和集合來規劃，所以有效期準時開始和結束，新增對象在下次同步時涵蓋。
- **MongoDB 角色就是群組。** 針對群組的策略適用於持有同名 MongoDB 角色的使用者，以及 GrantForge 放入該群組的使用者。
- **手工改動會被還原。** 每次同步時代理都比較自己的角色與規劃，改回被手工修改的權限、被加上的繼承角色、授給他人的角色，並刪除不再需要的角色。沒有變化時不發送任何命令。
- **讀取 GrantForge 之外的角色實際給出了什麼。** MongoDB 會累加使用者所有角色給出的權限，所以代理無法收回使用者通過其他角色持有的權限。MongoDB 能說明每個角色給出了什麼，包括內建和繼承的角色，所以當 `readWrite@hr` 這樣的角色給出了策略拒絕的權限時，代理會警告一次，指明使用者、存取和策略。代理還會警告能管理使用者（因而能給自己任何權限）的使用者、持有 `root` 或對所有資源擁有所有動作的使用者，以及完全不檢查授權的伺服器。
- **存取來自稽核日誌或 Profiler。** 見下文“報告存取”。每個事件都說明是 GrantForge 決定的（即策略與 MongoDB 的決定一致），還是 GrantForge 之外的角色允許了命令。彙總會按其 `$lookup`、`$graphLookup`、`$unionWith` 階段讀取的每個集合，以及 `$out`、`$merge` 階段寫入的每個集合分別報告。事件只包含命令名和命名空間，絕不包含參數，因為參數可能含有文件和密碼。

## 部署

1. 新增 `mongodb` 資料服務及其策略。開啟 **資料權限 → 代理**，選擇服務並簽發代理權杖。權杖只顯示一次，請儲存到只有代理帳戶能讀取的檔案。
2. 創建代理的 MongoDB 使用者。它用 `userAdminAnyDatabase` 創建和修改角色、讀取使用者和角色；用一個只有列出集合權限的角色列出集合；用 `clusterMonitor` 讀取伺服器選項和 Profiler。`userAdminAnyDatabase` 能給自己任何權限，請像保護管理員一樣保護此使用者。列出集合的角色名不能以代理的角色前綴開頭。

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. 如需報告存取，按下文“報告存取”設定一種來源。
4. 撰寫設定檔，替換示例中的地址、路徑和名稱：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   這些路徑都在代理所在機器上，快取目錄必須可寫。密碼檔案按 UTF-8 讀取並去除首尾空白。
5. 啓動代理，例如作為 systemd 服務：

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   將 `<version>` 替換為發行版本。`java -Dgrantforge.…=… -jar …` 給出的設定優先於檔案。收到 SIGTERM 時代理乾淨退出，發送或寫入磁碟待發事件；MongoDB 保留已收到的內容。
6. **把現有授權遷移到策略：** GrantForge 之外授予的角色會繼續與代理的角色一起生效。先創建對應的策略，再撤銷這些角色。代理的警告會指出那些給出了策略拒絕權限的角色。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此代理的名稱，以及存放已簽章快照、寫入磁碟事件和存取讀取位置的可寫目錄 |
| `grantforge.signing.key.file` | 可選，固定快照簽章金鑰的檔案。從代理頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時在首次連線時從伺服器獲取 |
| `grantforge.mongodb.uri` | 必填：連線字串，不含憑據 |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256`（預設）、`SCRAM-SHA-1`、`MONGODB-X509`、`PLAIN` 或 `GSSAPI` |
| `grantforge.mongodb.user`、`grantforge.mongodb.password.file`、`grantforge.mongodb.auth.source` | 代理的使用者，`GSSAPI` 時為主體，`MONGODB-X509` 時可選；其密碼；SCRAM 時定義該使用者的資料庫，預設 `admin` |
| `grantforge.mongodb.tls.ca.file`、`grantforge.mongodb.tls.certificate.key.file`、`grantforge.mongodb.tls.certificate.key.password.file` | 連線字串含 `tls=true` 時：驗證伺服器的 PEM CA 憑證，留空使用 Java 信任的；同時包含用戶端憑證和私密金鑰的 PEM 檔案；加密私密金鑰的密碼 |
| `grantforge.mongodb.kerberos.keytab`、`grantforge.mongodb.kerberos.service.name` | `GSSAPI`：主體的 keytab，否則使用密碼；伺服器主體的服務名，預設 `mongodb`。realm 來自機器的 `krb5.conf` |
| `grantforge.mongodb.timeout.ms` | 查找伺服器、連線和單條命令允許的時長，預設 `30000`，範圍 `1..600000` 毫秒 |
| `grantforge.mongodb.role.prefix` | 代理角色名的開頭，預設 `grantforge_`。由字母、數字和下划線群組成，以下划線結尾，最多 32 個字元。`admin` 中名稱以它開頭的角色都屬於代理 |
| `grantforge.mongodb.unmanaged.users` | 代理不處理的使用者，以逗號分隔，如服務帳號。代理始終不處理自己的使用者，以及持有 `root`、`__system` 或對所有資源擁有所有動作的使用者 |
| `grantforge.mongodb.databases` | 代理逐集合授權的資料庫，以逗號分隔，留空為全部。其他資料庫只獲得能整體授予的權限，這樣集合特別多的資料庫不必在每次同步時列出 |
| `grantforge.mongodb.audit.files` | 要讀取的稽核日誌檔案，以逗號分隔。留空則讀取 Profiler |
| `grantforge.sync.interval.ms` | 代理同步 MongoDB 的間隔，預設 `10000`，範圍 `1000..3600000` 毫秒 |
| `grantforge.audit.log` | 預設 `true`。`false` 時不讀取也不報告存取 |
| `grantforge.audit.log.delay.ms`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.batch` | 讀取 Profiler 時落後當前時間多久（預設 `2000`）、讀取存取的間隔（預設 `5000` 毫秒）、每次從一個檔案或資料庫讀取的最多條目（預設 `1000`） |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理請求新策略的間隔，預設 `30000`，最少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批大小（預設 `500`）、佇列大小（預設 `10000`）、發送間隔（預設 `5000` 毫秒）和磁盤寫入磁碟上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取折疊為一個事件的時長，預設 `10000`，最多 `3600000` 毫秒。`0` 表示逐個發送 |

## 報告存取

| 來源 | 適用於 | 能說明什麼 | 如何開啓 |
| --- | --- | --- | --- |
| 稽核日誌 | MongoDB Enterprise 和 Percona Server for MongoDB | 哪些被允許、哪些被拒絕，涵蓋代理能讀到其檔案的每個成員 | 用 `--auditDestination file --auditFormat JSON --auditPath <path>` 和 `--setParameter auditAuthorizationSuccess=true` 啓動 `mongod`，並在 `grantforge.mongodb.audit.files` 中列出檔案 |
| Profiler | 所有版本 | 代理連線的成員（複本集的主節點）上運行過什麼；沒有拒絕記錄 | 在每個要報告的資料庫執行 `db.setProfilingLevel(2)`。它有伺服器成本，所以代理從不自行開啓；`system.profile` 是一個很小的固定大小集合 |

稽核日誌從檔案讀取，所以要把代理運行在節點上，或給它一個匯集這些檔案的目錄。測試讀取的是 Percona Server 的稽核日誌，其格式與 MongoDB Enterprise 相同。`mongos` 沒有 Profiler，所以分片叢集只能從稽核日誌報告存取。

| 命令 | 存取事件 |
| --- | --- |
| `find`、`aggregate`、`count`、`distinct`、`listIndexes` | 讀取的每個集合的 `read` |
| 以 `$changeStream` 開頭的 `aggregate` | `change_stream` |
| `insert`、`update`、`delete`、`findAndModify`、`bulkWrite` | 寫入的每個集合的 `insert`、`update` 或 `remove`；要求跳過驗證時還有 `bypass_validation` |
| `createIndexes`、`dropIndexes` | `create_index`、`drop_index` |
| `collMod`、`validate`、`convertToCapped` | `alter` |
| `drop`、`create`、`dropDatabase` | `drop`、`create`、`drop_database` |
| `listDatabases`；`serverStatus`、`top`、`replSetGetStatus`、`connPoolStats`、`hostInfo`、`currentOp` | `list_databases`；`monitor` |

MongoDB 整體拒絕一條命令，不說明是其中哪個集合造成的，所以拒絕事件按策略拒絕的那些集合報告；策略都不拒絕時，按第一個集合報告。

## 使用者、群組與角色

策略按使用者名指定 MongoDB 使用者。在多個資料庫中定義的同名使用者算一個使用者，代理的角色會授予所有這些使用者。群組是使用者持有的 MongoDB 角色以及 GrantForge 放入的群組。角色是 GrantForge 給使用者的角色。

## 決策與限制

- 沒有快照時代理不做任何改動。綁定到未使用的服務或類型不是 `mongodb` 的服務的權杖不授予任何權限：代理刪除自己的角色，MongoDB 自身的角色保留。
- 代理未運行時 MongoDB 保留最後收到的內容，策略變更等待代理。變更在刷新和同步間隔內到達 MongoDB；`mongos` 在其使用者快取過期後應用，預設 30 秒內（`userCacheInvalidationIntervalSecs`）。
- MongoDB 的權限精確指定資料庫和集合，所以策略中的萬用字元按現存對象授予。資料庫有了集合才算存在：在此之前，策略指定資料庫上的 `create` 沒有可授予的對象。
- 沒有在 MongoDB 中定義的使用者，例如 LDAP 授權直接映射到角色的使用者，代理看不到，也不為其維護角色。
- 整個資料庫上的權限涵蓋其集合但不涵蓋 `system.` 集合，MongoDB 自己的資料庫交由 MongoDB 管理。
- 重命名集合以及 MongoDB 的其他管理動作不是存取類型：它們仍由 GrantForge 之外的角色決定。
- MongoDB 不按角色篩選列，也不按角色遮罩欄位；[檢視](/zh-tw/plugins/mongodb/)可以提供受限的讀取。

## 相關指南

- [MongoDB](/zh-tw/plugins/mongodb/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
