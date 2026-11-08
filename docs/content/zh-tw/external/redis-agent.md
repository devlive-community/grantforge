---
title: Redis 代理
description: 在 Redis 或 Valkey 部署旁運行 Redis 代理，讓每個節點上每個使用者的 ACL 選擇器與 GrantForge 策略保持一致並用一條命令替換，用 ACL DRYRUN 詢問 Redis GrantForge 之外的權限允許什麼，並從各節點的 ACL 日誌報告拒絕。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Redis 代理是運行在部署旁的服務。它讓每個節點上 Redis 自身的 ACL 與 `redis` 資料服務的已簽章策略保持一致，複本和叢集的每個節點都包括在內，因為 Redis 在每個節點上各自維護 ACL。之後每條命令都由 Redis 自己檢查，代理不擋在任何用戶端前面。發行套件以可運行 JAR `agents/redis/grantforge-agent-redis-<version>.jar` 提供，需要 Java 17 或更高版本。

儲存庫的 Testcontainers 整合測試讓代理和發行的 JAR 運行在 Redis `7.2`、`7.4`、`8.2`、`8.10` 和 Valkey `8.1`、`9.1` 的一主一複本以及一個 Redis `8.10` 叢集上。這並不認證其他所有版本；代理會拒絕低於 `7.2` 的 Redis，因為其 ACL 日誌的條目沒有編號。服務外掛見 [Redis](/zh-tw/plugins/redis/)。

## 運作方式

- **選擇器歸 GrantForge，根權限歸運維人員。** 使用者的根權限或任一選擇器允許時，Redis 就允許命令。代理維護它所管理使用者的全部選擇器，從不改動其根權限和密碼：一個選擇器存放使用者可讀、可寫或可讀寫的鍵，一個存放可訂閱的頻道，一個存放可發佈的頻道，在允許監控的節點上再加一個。
- **變更由一條命令完成。** `ACL SETUSER user clearselectors (…)` 在一個節點上一次替換使用者的全部選擇器，使用者不會同時持有新舊權限，也不會兩者都沒有；被手工改動的選擇器在下次同步時以同樣方式改回。
- **模式只在成立處授予。** 策略中的鍵或頻道模式，僅當策略對該使用者在整個模式範圍內決定一致時，才作為 Redis 自己的模式授予；代理會沿著與它重疊的每條策略把模式切分並逐塊判斷，所以其他使用者的策略不會拖累它。策略在其中決定不同的模式會被保留不授予，並記錄一次。
- **不給危險命令，不列出他人的鍵。** 選擇器從不授予 Redis 認為危險的命令，`SCAN` 和 `RANDOMKEY` 只授予能讀取所有鍵的使用者。
- **每個節點都下發，能儲存就儲存。** 代理從給定節點發現複本和叢集節點，並逐個同步。把 ACL 儲存在檔案中的節點用 `ACL SAVE` 儲存；不使用檔案的節點重啓後會丟失選擇器，並在下次同步時重新獲得。
- **直接問 Redis 允許什麼。** 根權限與代理的選擇器並列生效。代理不去解析其規則，而是用 `ACL DRYRUN` 問 Redis：某使用者能否對策略拒絕的鍵、頻道或節點執行命令，並各警告一次，指明使用者、存取和策略。代理還會警告沒有選擇器能約束的使用者（對所有鍵有所有命令），以及任何人都能無密碼登入的使用者。
- **拒絕來自每個節點的 ACL 日誌。** 見下文“報告拒絕”。
- **使用當前時刻的策略。** 每次同步都按當時生效的策略以及 Redis 現有的使用者和節點來規劃，所以有效期準時開始和結束。

## 部署

1. 新增 `redis` 資料服務及其策略。開啟 **資料權限 → 代理**，選擇服務並簽發代理權杖。權杖只顯示一次，請儲存到只有代理帳戶能讀取的檔案。
2. 在每個節點上創建代理的 ACL 使用者。它讀取使用者、設定其選擇器、儲存 ACL 檔案、讀取 ACL 日誌、執行 dry run 併發現叢集節點。`+acl|setuser` 能給自己任何權限，請像保護管理員一樣保護此使用者。

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. 把代理所管理使用者的根權限限制在每個使用者都需要的範圍內，例如登入；其餘由代理授予。給出更多的根權限會被發現並警告。

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. 編寫設定檔，替換示例中的地址、路徑和名稱：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   這些路徑都在代理所在機器上，快取目錄必須可寫。密碼檔案按 UTF-8 讀取並去除首尾空白。
5. 啓動代理，例如作為 systemd 服務：

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   將 `<version>` 替換為發行版本。`java -Dgrantforge.…=… -jar …` 給出的設定優先於檔案。收到 SIGTERM 時代理乾淨退出，發送或寫入磁碟待發事件；Redis 保留已收到的內容。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此代理的名稱，以及存放簽章快照、暫存事件與審計日誌讀取位置的可寫目錄 |
| `grantforge.signing.key.file` | 可選的固定快照簽章金鑰檔案；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公鑰。省略時在首次連接時從伺服器獲取 |
| `grantforge.redis.nodes` | 必填：節點地址 `host:port`，不寫端口時為 `6379` |
| `grantforge.redis.discover` | 預設 `true`：從給定節點發現叢集其餘節點和主節點的複本。`false` 只使用給定節點，用於節點公佈的地址代理無法存取的情況 |
| `grantforge.redis.user`、`grantforge.redis.password.file` | 代理的 ACL 使用者（留空為預設使用者）及其密碼 |
| `grantforge.redis.tls.mode`、`grantforge.redis.tls.ca.file`、`grantforge.redis.tls.certificate.file`、`grantforge.redis.tls.key.file`、`grantforge.redis.tls.key.password.file` | `disable`（預設）、`verify-ca` 或 `verify-full`；校驗伺服器的 PEM CA 憑證，留空使用 Java 信任的；PEM 用戶端憑證、單獨存放時的私密金鑰以及私密金鑰密碼 |
| `grantforge.redis.timeout.ms` | 連接和單條命令允許的時長，預設 `10000`，範圍 `1..600000` 毫秒 |
| `grantforge.redis.unmanaged.users` | 代理不處理的使用者，以逗號分隔，如服務帳號。代理始終不處理自己的使用者，以及對所有鍵有所有命令的使用者 |
| `grantforge.sync.interval.ms` | 代理讓 Redis 保持一致的頻率，預設 `10000`，範圍 `1000..3600000` 毫秒 |
| `grantforge.audit.log`、`grantforge.audit.log.interval.ms` | 預設 `true`；`false` 時不讀取 ACL 日誌。讀取 ACL 日誌的間隔，預設 `5000` 毫秒 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | 連接 GrantForge 與讀取的超時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理請求新策略的頻率，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批大小（預設 `500`）、佇列大小（預設 `10000`）、發送間隔（預設 `5000` 毫秒）以及磁盤暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一條事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐條發送 |

## 報告拒絕

Redis 不記錄它允許了什麼，所以代理從每個節點的 ACL 日誌報告它拒絕了什麼：誰在何時從何處被拒絕了什麼，以及策略是否與 Redis 的決定一致。Redis 會把一分鐘內重復的拒絕合併到前一條記錄中；記錄每被更新一次，代理就再報告一次。

| 日誌原因 | 存取事件 |
| --- | --- |
| `key` | 對該鍵的 `read` 或 `write`，按 Redis 對命令的標記區分 |
| `channel` | 對該頻道的 `subscribe` 或 `publish` |
| `command` | 監控命令記為對節點的 `monitor`；其他命令在日誌中不帶鍵，不報告 |
| `auth` | 不報告：登入被拒絕不涉及任何存取 |

## 使用者、群組與角色

策略按名稱指定 Redis 的 ACL 使用者。Redis 沒有自己的角色或群組，所以群組是 GrantForge 放入的群組。

## 決策與限制

- 沒有快照時代理不做任何改動。綁定到未使用的服務或類型不是 `redis` 的服務的權杖不授予任何權限：代理會清除其所管理使用者的選擇器。
- 代理未運行時 Redis 保留最後收到的內容，策略變更等待代理。變更在刷新和同步間隔內到達 Redis。
- 代理維護其所管理使用者的全部選擇器：運維人員手工新增的選擇器會在下次同步時被移除。額外的權限請放在根權限中，或把該使用者交給運維人員管理。
- Redis 的 ACL 指定的是鍵和頻道而不是資料庫，所以一個模式在節點的每個資料庫中都涵蓋同名的鍵。
- 使用者完全無權執行的命令被拒絕時，Redis 在日誌中不記錄其鍵，這類拒絕不報告。
- Redis 不會在值內部過濾，也不做遮罩。

## 相關指南

- [Redis](/zh-tw/plugins/redis/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
