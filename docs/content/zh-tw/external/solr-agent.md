---
title: Apache Solr 代理
description: 在 Solr 節點中安裝授權外掛，以 GrantForge 策略決定誰可以查詢與更新哪些集合、透過哪些請求路徑、管理哪些集合與叢集，以及可以從哪些網路發起。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Solr 代理是節點的授權外掛：Solr 就每個請求詢問它，它依據 `solr` 資料服務的簽章策略作答。發行套件以一個 Java 11 的 JAR 提供它：`agents/solr/grantforge-agent-solr-<version>.jar`，相依套件已內嵌並重新定位。

儲存庫的 Testcontainers 整合測試在 Solr `9.4.1`、`9.8.1`、`9.11.0` 與 `10.0.0` 的 SolrCloud 節點中執行該 JAR。這不代表認證了其他所有版本。服務外掛見 [Apache Solr](/zh-tw/plugins/solr/)。

## 運作方式

- **請求依 Solr 自己的方式分類。** 每個請求處理器都會宣告它需要的權限，例如 `read` 或 `collection-admin-edit`，與 Solr 內建的規則授權外掛所用的一致；代理把這個名稱轉換為存取類型，沒有宣告的處理器依請求類型判定。
- **對集合的每個請求還會依請求路徑判定。** 代理在集合 `sales` 以及「`sales` 加路徑 `/select`」上判定透過 `/select` 對 `sales` 的查詢：任一處拒絕都會拒絕，因此一條策略可以拒絕集合的某個處理器。
- **團隊可以管理自己的集合。** 指名集合的 Collections API 請求需要每個被指名集合上的 `admin_collection`，因此一條策略可以讓團隊只建立、重新載入或刪除名稱以其前綴開頭的集合。
- **叢集管理員可以做任何事。** 叢集上的 `admin` 授予每個請求，與 Solr 自己的 `all` 權限相同。
- **策略可以只對部分網路生效。** 每次檢查都帶上用戶端位址；在代理所信任的反向代理之後，用戶端是 `X-Forwarded-For` 中最後一個不是由受信任反向代理加入的位址。
- **JSON Web Token 驗證過的角色視為群組。** 使用 Solr 的 JWT 驗證時，權杖宣告給使用者的角色會作為決策中的群組，與 GrantForge 中該使用者所屬的群組一起使用。
- **拒絕會連同原因記入日誌。** Solr 以自己的 403 回應被拒絕的請求；節點日誌會寫明是哪條 GrantForge 策略拒絕的，或者沒有策略允許。
- **快取決策並合併存取。** 目前快照的決策會被快取，預設 100,000 筆、10 秒，新快照會重新開始。視窗內（預設 10 秒）相同的允許存取會作為一筆存取事件回報，並註明次數；拒絕會立即逐筆回報。每個事件都註明集合與路徑（或叢集）以及用戶端位址。
- **不檢查節點本身。** 節點之間的請求（Solr 以節點自身簽署）直接放行；沒有使用者的請求會被要求登入。

| 請求 | 所需存取 |
| --- | --- |
| 透過任何讀取處理器搜尋、取得、匯出、查詢 | 集合與路徑上的 `query` |
| 新增、更新或刪除文件 | 集合與路徑上的 `update` |
| 讀取或修改集合的 schema | `schema_read` 或 `schema_edit` |
| 讀取或修改集合的 config | `config_read` 或 `config_edit` |
| 指名集合的 Collections API 請求：建立、重新載入、備份、還原、刪除、修改及其叢集狀態 | 每個被指名集合上的 `admin_collection` |
| 健康檢查；指標 | 叢集上的 `health`；`metrics` |
| 列出集合、core 狀態、讀取 ZooKeeper、檔案儲存與套件 | 叢集上的 `admin_read` |
| 讀取或修改驗證與授權設定 | 叢集上的 `security` |
| core 管理、修改檔案儲存與套件，以及沒有處理器宣告的操作 | 叢集上的 `admin` |

## 部署

1. 新增 `solr` 資料服務並設定其策略，包括為 Solr 管理員授予叢集上的 `admin`。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有 Solr 執行帳號可讀的檔案。
2. 把 JAR 複製到 **每個節點** 的 `server/solr-webapp/webapp/WEB-INF/lib`，例如 `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`，然後逐一重新啟動節點。
3. 在叢集的 `security.json` 中設定授權部分（與驗證外掛並列），替換範例中的位址、路徑與執行個體名稱：

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   以 `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>` 上傳；節點無需重新啟動即可生效。必須啟用驗證，請求才有使用者。
4. **在 Solr 9 上注意 Java 安全管理員**（Solr 預設啟用）：權杖、固定的公鑰與快取目錄請放在 `SOLR_HOME` 下，例如 `/var/solr/data`，並在 `server/etc/security.policy` 中，或在透過 `-Djava.security.properties` 以 `policy.url.3` 加入的自有策略檔中，為代理授予 Solr 策略之外所需的權限：

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10 不再使用安全管理員。
5. **先遷移既有權限：** Solr 規則授權外掛的權限與使用者角色將不再生效。請事先建立對應的策略。在代理收到第一個快照之前，除節點之間的請求外都會被拒絕。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、叢集節點的名稱，以及存放簽章快照與暫存事件的可寫入目錄 |
| `grantforge.trusted.proxies` | 代理信任其 `X-Forwarded-For` 的反向代理，位址或網段，以逗號分隔；預設沒有 |
| `grantforge.signing.key.file` | 選用的固定快照簽章金鑰檔；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公鑰。省略時於首次連線時從伺服器取得 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理拉取新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆回報 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 快取決策的最大數量，預設 `100000`，最多 `10000000`，`0` 表示不快取；以及快取時長，預設 `10000`，範圍 `1..3600000` 毫秒 |

## 使用者、群組與角色

策略中的使用者是 Solr 驗證登入的使用者，例如 basic 驗證的使用者或 JSON Web Token 的主體。群組為 JSON Web Token 為該使用者驗證過的角色，加上 GrantForge 中該使用者所屬的群組；角色為 GrantForge 授予該使用者的角色。

## 決策與限制

- 沒有可用快照時，除節點之間的請求外一切都會被拒絕。未啟用的服務讓 Solr 不受檢查，與沒有授權外掛時一樣。
- 綁定到類型不是 `solr` 的服務的權杖會拒絕一切。
- 只在 JSON 請求本文中指名集合的 Collections API 請求（部分 v2 API 請求如此）沒有代理能看到的集合，需要叢集上的 `admin`。
- Solr 在詢問之前會把別名解析為集合，因此策略中寫的是集合而非別名。
- Solr 的 `/admin/authorization` API 無法編輯代理的規則：策略保存在 GrantForge 中。
- 用戶端位址只能是位址與網段；主機名稱不比對任何用戶端。
- 決策會快取 `grantforge.decision.cache.ttl.ms`，因此策略的生效或失效最多會延遲這麼久。

## 相關指南

- [Apache Solr](/zh-tw/plugins/solr/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
