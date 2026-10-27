---
title: OpenSearch 代理
description: 在叢集旁執行 OpenSearch 代理，讓 GrantForge 策略成為安全外掛自己的角色，帶有欄位層級安全、遮罩欄位與文件層級安全；索引模式維持為模式，新索引立即可用；並從稽核日誌回報存取。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

OpenSearch 代理是執行在叢集旁的服務。它讓安全外掛自己的存取控制與 `opensearch` 資料服務的簽章策略保持一致：為每個使用者維護一個只對應給該使用者的角色，恰好持有策略給該使用者的權限，並帶有欄位層級安全、遮罩欄位與文件層級安全。之後由 OpenSearch 自己檢查每個請求，代理從不位於用戶端與叢集之間。發行套件以可執行的 JAR 提供它，即 `agents/opensearch/grantforge-agent-opensearch-<version>.jar`，需要 Java 17 或更新版本。

儲存庫的 Testcontainers 整合測試讓代理以及發行的 JAR 執行在帶有安全外掛的 OpenSearch `2.19`、`3.3`、`3.6` 與 `3.9` 上。這不代表認證了其他所有版本；代理會拒絕早於 `2.19` 的叢集以及沒有安全外掛的叢集。服務外掛見 [OpenSearch](/zh-tw/plugins/opensearch/)。

## 運作方式

- **每個使用者一個角色，持有 GrantForge 給出的權限。** 對於策略給出權限的每個使用者，代理維護一個以其命名的角色，例如 `alice` 對應 `grantforge_alice`，並只對應給該使用者。角色的描述標明它屬於代理，沒有該標記的角色絕不會被當作代理自己的，因此代理不會改動其他角色與對應。
- **索引模式維持為模式。** 策略的索引模式（例如 `logs-*`）只要在其範圍內所有策略的決定都相同，就以模式授予：任何類型的策略，只要其索引與該模式有交集，就必須完整涵蓋該模式。代理藉由模式的有限自動機精確地判斷這一點。之後新建的索引（例如明天的日誌）立即可用，第一份文件寫入時自動建立索引也不例外。若某條策略在模式內做出不同的決定，例如在 `logs-*` 內拒絕 `logs-secret`，代理就依名稱授予叢集現有的每個索引與資料串流，新建的則在下次同步時授予。
- **只在下層另有決定時使用欄位層級安全。** 使用者可以整體讀取、且沒有任何策略可能收回某個欄位的索引，不使用欄位層級安全，因此之後新增的欄位立即可讀。否則使用者讀取策略允許的欄位，範圍是文件已有的欄位與策略中寫出的欄位；`name.keyword` 這樣的多重欄位隨其欄位一同處理。
- **遮罩與列篩選使用安全外掛自己的機制。** 遮罩成為條目的遮罩欄位，列篩選成為其文件層級安全查詢。每個查詢在授予之前都由 OpenSearch 的 validate API 檢查；OpenSearch 不會執行的查詢讓使用者看不到任何文件，並記錄日誌。安全外掛不認識的遮罩方式會改為隱藏該欄位。
- **每組權限一個條目。** 權限相同的索引與模式共用一個條目；條目之間從不重疊，因為安全外掛會合併重疊條目的欄位層級與文件層級安全。
- **讀取與寫入附帶叢集的組合操作。** 讀取附帶 `cluster_composite_ops_ro`，用於多重搜尋、多重取得與捲動查詢；寫入附帶 `cluster_composite_ops`，用於 bulk。
- **變更是一個修補。** 代理以 JSON 修補寫入它的角色與對應，安全外掛會在整個叢集中一次套用：先寫角色，再寫引用這些角色的對應；先刪對應，再刪它們引用的角色。安全外掛拒絕的修補會逐項套用，因此一個有問題的角色只會讓它自己失敗。
- **當下時刻的策略。** 每次同步都依當時適用的策略制定計畫，因此有效期間會準時開始與結束。
- **後端角色就是群組。** 針對某個群組的策略適用於擁有同名後端角色的內部使用者，以及 GrantForge 中所在群組的使用者。
- **手動變更會被還原。** 每次同步時，代理都會把它的角色與對應與安全外掛中儲存的內容比較；不同之處會重新寫入。沒有變化時，代理不傳送任何內容。
- **回報 GrantForge 之外的角色。** OpenSearch 授予使用者任一角色給出的權限，因此代理無法收回對應給該使用者的其他角色所給出的權限。對於給出了策略所拒絕內容的每個這樣的角色，代理只警告一次，寫明角色、使用者、存取、索引與策略；對應到 `all_access` 的使用者不受任何角色約束，也會被警告。
- **存取來自稽核日誌。** 代理從安全外掛寫入的索引 `security-auditlog-*` 讀取稽核日誌：被拒絕的存取記為 `MISSING_PRIVILEGES`，稽核設定記錄時，被允許的存取記為 `GRANTED_PRIVILEGES`。每則事件寫明使用者、存取類型、所存取的每個索引或資料串流（或叢集），並說明是由 GrantForge 決定，還是由 GrantForge 之外的角色放行。

| 動作 | 存取事件 |
| --- | --- |
| `indices:data/read/*`：搜尋、取得、計數 | 每個索引或資料串流上的 `read` |
| `indices:data/write/index`、`update`、`bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`、`auto_create`、`data_stream/create` | `create_index` |
| `indices:admin/aliases*`、`indices:monitor/*` | `manage_aliases`、`monitor` |
| 其他 `indices:admin/*` | `manage` |
| `cluster:monitor/*`、`cluster:admin/snapshot/*` 與 `repository/*` | 叢集上的 `cluster_monitor`、`manage_snapshots` |
| 索引範本與元件範本、擷取管線 | 叢集上的 `manage_templates`、`manage_pipelines` |

## 部署

1. 新增 `opensearch` 資料服務並設定其策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有代理執行帳號可讀的檔案。
2. 給代理一個可以修改安全外掛設定的使用者：一個對應到 `all_access`、且被 `plugins.security.restapi.roles_enabled` 列出的使用者，或 `plugins.security.authcz.admin_dn` 所指定的管理員憑證。測試以示範設定的 `admin` 以及其管理員憑證執行代理。
3. 要回報存取，請把稽核日誌保存在叢集中（`plugins.security.audit.type: internal_opensearch`）。要回報 OpenSearch 允許的存取，還需把 `GRANTED_PRIVILEGES` 從稽核設定的停用類別中移除；拒絕預設就會記錄。
4. 撰寫設定檔，替換範例中的位址、路徑與名稱：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   這些路徑位於代理所在的機器上，快取目錄必須可寫入。密碼檔以 UTF-8 讀取並去除前後空白。
5. 啟動代理，例如作為 systemd 服務：

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   把 `<version>` 替換為發行版本。以 `java -Dgrantforge.…=… -jar …` 提供的設定會覆寫檔案中的設定。代理收到 SIGTERM 時會乾淨地停止，並傳送或暫存待送事件；OpenSearch 保留它已獲得的權限。
6. **把既有角色移轉為策略：** 在 GrantForge 之外對應給使用者的角色會與代理的角色一同持續生效。請建立對應的策略，然後刪除這些對應；代理的警告會寫出其中給出了策略所拒絕內容的角色。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔、此代理的名稱，以及存放簽章快照、暫存事件與稽核日誌讀取位置的可寫入目錄 |
| `grantforge.signing.key.file` | 選填的固定快照簽章金鑰檔；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時在首次連線時從伺服器取得 |
| `grantforge.opensearch.hosts` | 必填：以 URL 表示的節點，某個無法連線時依序嘗試下一個；未寫協定時為 `https`，連接埠預設為 `9200` |
| `grantforge.opensearch.user`、`grantforge.opensearch.password.file` | 代理的使用者及其密碼檔 |
| `grantforge.opensearch.tls.cert`、`grantforge.opensearch.tls.key` | 或者：PEM 用戶端憑證及其私密金鑰，需同時設定，用於以憑證登入；`grantforge.opensearch.tls.key.passphrase.file` 存放加密私密金鑰的密碼短語 |
| `grantforge.opensearch.ssl.verification.mode` | `full`（預設）驗證節點的憑證與名稱，`certificate` 只驗證憑證，`none` 不驗證 |
| `grantforge.opensearch.tls.ca` | 驗證節點的 PEM CA 憑證；空白時信任 Java 所信任的 |
| `grantforge.opensearch.timeout.ms` | 連線與請求可用的時間，預設 `30000`，範圍 `1..600000` 毫秒 |
| `grantforge.opensearch.role.prefix` | 代理的角色名稱開頭，預設 `grantforge_`；由小寫字母、數字、連字號與底線組成，以底線結尾，最多 32 個字元。名稱以它開頭且帶有代理標記的角色歸代理所有 |
| `grantforge.opensearch.unmanaged.users` | 代理不處理的使用者，以逗號分隔，例如服務帳號；代理始終不處理它自己的使用者以及對應到 `all_access` 的使用者 |
| `grantforge.sync.interval.ms` | 代理讓 OpenSearch 保持一致的間隔，預設 `10000`，範圍 `1000..3600000` 毫秒 |
| `grantforge.audit.log` | 預設 `true`；`false` 表示不讀取稽核日誌，也不回報存取 |
| `grantforge.audit.log.index`、`grantforge.audit.log.interval.ms`、`grantforge.audit.log.max.documents` | 稽核日誌的索引，預設 `security-auditlog-*`；讀取間隔，預設 `5000` 毫秒；以及每次讀取的最多文件數，預設 `1000`，積壓的日誌會分多次讀取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理取得新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與磁碟暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一則事件的時間，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐則回報 |

## 使用者、群組與角色

策略中的使用者是安全外掛所認識的 OpenSearch 使用者。代理為內部使用者、策略中寫出的使用者（他們可能透過 LDAP 或 SAML 等目錄登入）以及先前已為其維護角色的使用者維護角色；安全外掛保留與隱藏的使用者（例如 `kibanaserver`）不做處理。群組為內部使用者的後端角色，加上 GrantForge 中該使用者所在的群組；目錄在登入時給出的後端角色代理無從得知，因此請在 GrantForge 中為這類使用者設定群組。角色為 GrantForge 授予該使用者的角色。

## 決策與限制

- 沒有快照時代理不做任何變更。未啟用的服務，或綁定到類型不是 `opensearch` 的服務的權杖，不授予任何權限：代理刪除它的角色與對應，由安全外掛自己的角色生效。
- 代理未執行期間，OpenSearch 保留最後一次獲得的權限；策略的變化要等代理執行後才生效。變化會在重新整理間隔加同步間隔之內到達 OpenSearch。
- 依名稱授予（而非經由模式授予）的索引，會在下次同步時獲得權限。
- 別名會存取到其背後的索引：搜尋別名需要對它包含的每個索引都有權限，因此策略寫的是索引與資料串流，而不是別名。
- 列篩選只能檢驗使用者可以讀取的欄位，遮罩只作用於 text 與 keyword 欄位的值。
- 名稱以點開頭的索引只能經由以點開頭的策略模式存取。
- 代理不管理安全外掛為 OpenSearch Dashboards 提供的租用戶。

## 相關指南

- [OpenSearch](/zh-tw/plugins/opensearch/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
