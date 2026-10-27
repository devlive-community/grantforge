---
title: Elasticsearch 代理
description: 在叢集旁執行 Elasticsearch 代理，讓 GrantForge 策略成為 Elasticsearch 自己的角色：賦予原生使用者，並透過角色對應給其他 realm 的使用者，授權允許時帶有欄位層級與文件層級安全；索引模式維持為模式，新索引立即可用；存取從稽核日誌檔回報。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Elasticsearch 代理是執行在叢集旁的服務。它讓 Elasticsearch 自己的存取控制與 `elasticsearch` 資料服務的簽章策略保持一致：為每個使用者維護一個角色，恰好持有策略給該使用者的權限，叢集授權允許時帶有欄位層級與文件層級安全。原生使用者在自己的角色中持有它；其他 realm（例如 LDAP、SAML 或 OpenID Connect）的使用者透過依使用者名稱的角色對應取得它。之後由 Elasticsearch 自己檢查每個請求，代理從不位於用戶端與叢集之間。發行套件以可執行的 JAR 提供它，即 `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`，需要 Java 17 或更新版本。

儲存庫的 Testcontainers 整合測試讓代理以及發行的 JAR 執行在啟用安全功能的 Elasticsearch `8.19`、`9.3`、`9.4` 與 `9.5` 上。這不代表認證了其他所有版本；代理會拒絕早於 `8.19` 的叢集、沒有啟用安全功能的叢集，以及由 [OpenSearch 代理](/zh-tw/external/opensearch-agent/)負責的 OpenSearch 叢集。服務外掛見 [Elasticsearch](/zh-tw/plugins/elasticsearch/)。

## 運作方式

- **每個使用者一個角色，持有 GrantForge 給出的權限。** 對於策略給出權限的每個使用者，代理維護一個以其命名的角色，例如 `alice` 對應 `grantforge_alice`。角色的中繼資料寫明使用者並標明它屬於代理，沒有該標記的角色絕不會被當作代理自己的，因此代理不會改動其他角色。
- **原生使用者持有角色，其他 realm 依名稱取得。** 代理把角色加入原生使用者的角色清單，並保留該使用者的其他所有角色。角色對應適用於原生與檔案 realm 以外的所有 realm，因此代理還維護一個同名的角色對應，依使用者名稱給出角色，供透過 LDAP、Active Directory、SAML、OpenID Connect、JWT、Kerberos 或 PKI 登入的使用者使用。檔案 realm 無法透過 Elasticsearch 的 API 修改，其使用者不會取得角色。
- **索引模式維持為模式。** 策略的索引模式（例如 `logs-*`）只要在其範圍內所有策略的決定都相同，就以模式授予：任何類型的策略，只要其索引與該模式有交集，就必須完整涵蓋該模式。代理藉由模式的有限自動機精確地判斷這一點。之後新建的索引（例如明天的日誌）立即可用，第一份文件寫入時自動建立索引也不例外。若某條策略在模式內做出不同的決定，例如在 `logs-*` 內拒絕 `logs-secret`，代理就依名稱授予叢集現有的每個索引與資料串流，新建的則在下次同步時授予。
- **只在下層另有決定時使用欄位層級安全。** 使用者可整體讀取、且沒有任何策略可能收回某個欄位的索引，不使用欄位層級安全，因此之後新增的欄位立即可讀。否則使用者讀取策略允許的欄位，範圍是文件已有的欄位與策略中寫出的欄位，每個欄位連同其多欄位一起，例如 `name` 與 `name.keyword`。
- **列篩選就是文件層級安全。** 列篩選成為條目的查詢。每個查詢在授予前都由 Elasticsearch 的 validate API 檢查；Elasticsearch 不會執行的查詢讓使用者看不到任何文件，並記錄日誌。
- **低於 Platinum 時絕不超出策略。** Elasticsearch 從 Platinum 授權起提供文件層級與欄位層級安全，低於此等級時會停用所有使用它們的角色。代理在每次同步時讀取授權。低於 Platinum 時，因策略限制了索引的欄位或文件而需要這兩項功能的讀取不會被授予，代理會記錄哪個使用者不能讀取哪個索引及原因；讀取整個索引與其他權限照常授予。
- **每組權限一個條目。** 權限相同的索引與模式共用一個條目；條目之間從不重疊，因為 Elasticsearch 會合併重疊條目的欄位層級與文件層級安全。
- **當下時刻的策略。** 每次同步都依當時適用的策略制定計畫，因此有效期間會準時開始與結束。
- **原生使用者的角色就是群組。** 針對某個群組的策略適用於持有同名角色的原生使用者，以及 GrantForge 中所在群組的使用者。
- **手動變更會被還原。** 每次同步時，代理都會把它的角色、角色對應與原生使用者的角色與 Elasticsearch 中保存的內容比較；不同之處會重新寫入，被其他使用者持有的某個使用者的角色會被收回。角色先以一次請求寫入，再賦予使用者；先從使用者收回，再刪除。沒有變化時，代理什麼也不傳送；Elasticsearch 拒絕的變更會記錄日誌，不影響其他變更。
- **回報 GrantForge 以外的角色。** Elasticsearch 授予使用者任一角色給出的權限，因此代理無法收回其他角色給出的權限。對於原生使用者持有、或依使用者名稱的角色對應給出、且給出了策略所拒絕內容的每個這類角色，代理只警告一次，寫明角色、使用者、存取、索引與策略；擁有 `superuser` 角色的使用者不受任何角色約束，也會被警告。
- **存取來自稽核日誌檔。** Elasticsearch 只把稽核日誌寫入每個節點上的檔案。代理讀取指定的檔案，從上次讀到的位置繼續，在 Elasticsearch 輪替檔案時跟隨，並回報 `access_granted` 與 `access_denied` 事件。每個事件寫明使用者、存取類型、所存取的每個索引或資料串流（或叢集），並說明是由 GrantForge 決定，還是由 GrantForge 以外的角色放行。

| 操作 | 存取事件 |
| --- | --- |
| `indices:data/read/*`：搜尋、取得、計數 | 每個索引或資料串流上的 `read` |
| `indices:data/write/index`、`update`、`bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`、`auto_create`、`data_stream/create` | `create_index` |
| `indices:admin/delete`、`data_stream/delete` | `delete_index` |
| `indices:admin/get`、`mappings/get`、`settings/get`、`resolve/*` | `view_index_metadata` |
| `indices:monitor/*`；其他 `indices:admin/*` | `monitor`；`manage` |
| `cluster:monitor/*`、`cluster:admin/snapshot/*` 與 `repository/*`、`slm/*` | 叢集上的 `cluster_monitor`、`create_snapshot`、`manage_slm` |
| 索引範本與元件範本、擷取管線、`cluster:admin/ilm/*` | 叢集上的 `manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` |

## 部署

1. 新增 `elasticsearch` 資料服務並設定策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請保存到只有代理帳號能讀取的檔案中。
2. 為代理準備一個擁有 `manage_security` 叢集權限的使用者，用於管理角色、角色對應與使用者，並能讀取所有索引的對應與驗證查詢，例如擁有 `superuser` 角色的使用者。它可以用密碼、該使用者建立的 API 金鑰，或 PKI realm 對應到它的用戶端憑證登入。
3. 若要回報存取，請啟用稽核日誌（`xpack.security.audit.enabled: true`），這需要 Gold 或更高的授權，並讓代理能讀取節點的稽核日誌檔，即每個節點日誌目錄下的 `<叢集名稱>_audit.json`：可以在某個節點上執行代理，或把這些檔案彙集到代理能讀取的目錄。`xpack.security.audit.logfile.events.include: access_granted, access_denied` 讓日誌只包含代理回報的內容。
4. 撰寫設定檔，替換範例中的位址、路徑與名稱：

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   這些路徑位於代理所在的機器上，快取目錄必須可寫入。密碼與金鑰檔以 UTF-8 讀取並去除前後空白。
5. 啟動代理，例如作為 systemd 服務：

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   將 `<version>` 替換為發行版本。以 `java -Dgrantforge.…=… -jar …` 提供的設定會覆寫檔案中的設定。代理收到 SIGTERM 時正常停止，並傳送或暫存待回報的事件；Elasticsearch 保留已授予的權限。
6. **把現有角色遷移為策略：** 使用者在 GrantForge 以外持有的角色會繼續與代理的角色一同生效。先建立對應的策略，再從使用者收回這些角色；代理的警告會寫明哪些角色給出了策略所拒絕的內容。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔、此代理的名稱，以及用於保存簽章快照、暫存事件與稽核日誌讀取位置的可寫入目錄 |
| `grantforge.signing.key.file` | 選填，固定快照簽章公鑰的檔案；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公鑰。省略時首次連線從伺服器取得 |
| `grantforge.elasticsearch.hosts` | 必填：以 URL 表示的節點，某個無法連線時依序嘗試下一個；不寫協定時為 `https`，連接埠預設為 `9200` |
| `grantforge.elasticsearch.user`、`grantforge.elasticsearch.password.file` | 代理的使用者及其密碼檔 |
| `grantforge.elasticsearch.api.key.file` | 或者：編碼後的 API 金鑰檔，即 Elasticsearch 建立它時傳回的值 |
| `grantforge.elasticsearch.tls.cert`、`grantforge.elasticsearch.tls.key` | 或者：PEM 用戶端憑證及其私鑰，需同時設定，以憑證登入；`grantforge.elasticsearch.tls.key.passphrase.file` 保存加密私鑰的通關密語 |
| `grantforge.elasticsearch.ssl.verification.mode` | `full`（預設）驗證節點的憑證與名稱，`certificate` 只驗證憑證，`none` 不驗證 |
| `grantforge.elasticsearch.tls.ca` | 驗證節點的 PEM CA 憑證，例如 Elasticsearch 的 `http_ca.crt`；空白時信任 Java 信任的憑證 |
| `grantforge.elasticsearch.timeout.ms` | 連線與請求可用的時間，預設 `30000`，範圍 `1..600000` 毫秒 |
| `grantforge.elasticsearch.role.prefix` | 代理角色名稱的前綴，預設 `grantforge_`；由小寫字母、數字、連字號與底線組成，以底線結尾，最多 32 個字元。代理擁有以它開頭且帶有標記的角色 |
| `grantforge.elasticsearch.unmanaged.users` | 代理不處理的使用者，以逗號分隔，例如服務帳號；代理一律不處理它自己的使用者、Elasticsearch 的保留使用者以及擁有 `superuser` 角色的使用者 |
| `grantforge.sync.interval.ms` | 代理讓 Elasticsearch 與策略保持一致的頻率，預設 `10000`，範圍 `1000..3600000` 毫秒 |
| `grantforge.audit.log.files` | 要讀取的稽核日誌檔，以逗號分隔；不設定時不讀取稽核日誌，也不回報存取 |
| `grantforge.audit.log.interval.ms`、`grantforge.audit.log.max.lines` | 讀取檔案的頻率，預設 `5000` 毫秒；每次讀取每個檔案最多的行數，預設 `5000`，積壓會分多輪讀取 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | 連線 GrantForge 與讀取的逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理請求新策略的頻率，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與磁碟暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一個事件的時間，預設 `10000`，最多 `3600000` 毫秒；`0` 逐筆傳送 |

## 使用者、群組與角色

策略依使用者名稱指定 Elasticsearch 使用者。代理為原生使用者、策略中寫出的使用者（可能透過其他 realm 登入），以及之前曾為其維護角色的使用者維護角色；Elasticsearch 的保留使用者（例如 `kibana_system`）不受影響。群組是原生使用者的其他角色，加上 GrantForge 中使用者所在的群組；目錄在登入時給出的群組代理無法得知，因此請在 GrantForge 中為這類使用者設定群組。角色是 GrantForge 給使用者的角色。

## 決策與限制

- 沒有快照時代理不做任何變更。服務未啟用，或權杖綁定的服務類型不是 `elasticsearch` 時，不授予任何權限：代理收回並刪除它的角色及其角色對應，Elasticsearch 自己的角色繼續生效。
- 代理未執行時，Elasticsearch 保留最後授予的權限；策略變更會等待代理。變更在重新整理間隔與同步間隔內到達 Elasticsearch。
- 依名稱而非透過模式授予的索引，在下次同步時取得權限。
- 低於 Platinum 時，策略限制其讀取的使用者無法讀取該索引的任何內容；低於 Gold 時，代理不回報存取。
- 依使用者名稱的角色對應適用於所有擁有該使用者名稱的 realm；策略指定的是使用者，而非 realm。
- 別名會存取其背後的索引，因此策略應寫索引與資料串流，而不是別名。名稱以點開頭的索引只能透過以點開頭的策略模式存取。
- 代理不管理 Kibana 的空間與應用程式權限。

## 相關指南

- [Elasticsearch](/zh-tw/plugins/elasticsearch/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
