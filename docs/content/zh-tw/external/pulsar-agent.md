---
title: Apache Pulsar 代理
description: 在 Pulsar broker 中安裝授權提供者，以 GrantForge 策略決定誰可以在哪裡生產與消費、透過哪些訂閱、執行 Functions、Sources 與 Sinks、管理主題、命名空間、租戶與叢集，以及可以從哪些網路發起。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Pulsar 代理是 broker 的授權提供者：Pulsar 詢問它誰可以生產、消費、執行 Functions、Sources 與 Sinks，以及管理主題、命名空間、租戶與叢集，它依據 `pulsar` 資料服務的簽章策略作答。發行套件以一個 Java 17 的 JAR 提供它：`agents/pulsar/grantforge-agent-pulsar-<version>.jar`，相依套件已內嵌並重新定位。

儲存庫的 Testcontainers 整合測試在 Pulsar `3.0.17`、`3.3.9`、`4.0.14` 與 `4.2.5` 的 broker 中執行該 JAR。這並不代表對其他所有版本的認證。服務外掛請參閱 [Apache Pulsar](/zh-tw/plugins/pulsar/)。

## 運作方式

- **每次檢查都在主題名稱的每一級判定。** 與 Pulsar 自己的授權一樣，租戶或命名空間上的權限涵蓋其中的內容，因此代理在租戶 `sales`、命名空間 `orders` 與主題 `payments` 上判定一次向 `persistent://sales/orders/payments` 的生產：任一處拒絕都會拒絕，任一處允許都會授予。
- **消費還會依訂閱判定。** 在上述每一級，代理還會詢問「主題加訂閱」，因此一條策略可以讓角色只透過部分訂閱（例如 `billing-*`）消費。
- **叢集管理員就是 Pulsar 的超級使用者。** broker 所在叢集上的 `admin` 會讓角色成為超級使用者，而 Pulsar 允許超級使用者做任何事，因此下層策略不會限制他們。Pulsar 的 broker 與叢集操作需要所指叢集上的 `admin`。
- **策略可以只對部分網路生效。** 每次檢查都帶上用戶端位址，代理以 broker 網路堆疊所用的 Netty 位址範圍評估 **用戶端位址** 條件。
- **拒絕透過 Pulsar 授權。** Pulsar 自己的授權與租戶的管理員角色不再使用，因此 `pulsar-admin namespaces grant-permission` 之類的命令會得到 HTTP 405，並提示到 GrantForge 中授權。
- **拒絕會連同原因記入日誌。** 用戶端收到的拒絕由 Pulsar 措辭；broker 日誌會寫明是哪條 GrantForge 策略拒絕的，或者沒有策略允許。
- **快取決策並合併存取。** 目前快照的決策會被快取，預設 100,000 筆、10 秒，新快照會重新開始。時間窗內（預設 10 秒）相同的允許存取會以一筆存取事件回報，並註明次數；拒絕會立即逐筆回報。每個事件都註明主題、命名空間、租戶或叢集，以及用戶端位址。
- **一個代理服務整個 broker。** broker 會為二進位協定、Web 服務與 Functions Worker 分別載入授權提供者；它們共用一個代理，代理隨 broker 一起停止。

| 操作 | 所需存取 |
| --- | --- |
| 生產，或透過生產建立主題 | 主題上的 `produce` |
| 訂閱、消費、取消訂閱、略過、檢視、使訊息過期、重設游標 | 主題加訂閱上的 `consume` |
| 查找主題，讀取其統計、中繼資料與策略；列出命名空間的主題 | `produce` 或 `consume` |
| 終止、壓縮、卸載到分層儲存、unload 或截斷主題；修改其策略 | 主題上的 `admin_topic` |
| 建立與刪除主題；修改命名空間的策略 | 命名空間上的 `admin_namespace` |
| 執行 Functions、Sources、Sinks；使用 Packages | 命名空間上的 `functions`、`sources`、`sinks`、`packages` |
| 建立、列出與刪除租戶的命名空間 | 租戶上的 `admin_tenant` |
| 建立、修改與刪除租戶和叢集；Pulsar 的 broker 操作 | 叢集上的 `admin` |

## 部署

1. 新增 `pulsar` 資料服務並設定其策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有 Pulsar 執行帳號可讀的檔案。
2. 把 JAR 複製到 **每個 broker** 的 `lib`，例如 `/pulsar/lib`。
3. 在每個 broker 的 `broker.conf`（或 `standalone.conf`）中加入以下設定，替換範例中的位址、路徑與執行個體名稱：

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   驗證也必須啟用，用戶端才有角色。`superUserRoles` 與 `proxyRoles` 只能填 broker 自己的角色與代理伺服器的角色：代理不檢查它們，而 Pulsar 也會檢查經由代理伺服器的原始用戶端。管理員請改為授予叢集上的 `admin`。快取目錄必須可由 Pulsar 執行帳號寫入。在容器中請把 `grantforge.*` 設定寫進設定檔：部分 Pulsar 映像檔的 shell 會丟棄名稱中帶點的環境變數，例如 `PULSAR_PREFIX_grantforge.token.file`。
4. 逐一重新啟動 broker。在代理收到第一個快照之前，除 broker 與代理伺服器本身外的請求都會被拒絕。
5. **先遷移既有權限：** Pulsar 自己在命名空間、主題與訂閱上的授權，以及租戶的管理員角色，都將不再生效。請事先建立對應的策略。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此 broker 的名稱，以及存放簽章快照與暫存事件的可寫目錄 |
| `grantforge.signing.key.file` | 選用的固定快照簽章金鑰檔；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時於首次連線時從伺服器取得 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理擷取新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆回報 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 快取決策的最大數量，預設 `100000`，最多 `10000000`，`0` 表示不快取；以及快取時長，預設 `10000`，範圍 `1..3600000` 毫秒 |

## 使用者、群組與角色

策略把 Pulsar 驗證給用戶端的角色（例如 token 的 subject 或憑證的 common name）當作使用者。Pulsar 沒有自己的群組：群組為 GrantForge 中該角色所屬的群組，角色為 GrantForge 授予它的角色。

## 決策與限制

- 沒有可用快照時，除 broker 與代理伺服器的請求外一切都會被拒絕。未啟用的服務讓 Pulsar 不受檢查，與關閉 `authorizationEnabled` 時一樣。
- 繫結到類型不是 `pulsar` 之服務的權杖會拒絕一切。
- Pulsar 在許多檢查之前會先詢問角色是否為超級使用者或租戶管理員；只有讓請求通過的回答會被回報，而請求本身的檢查無論結果都會回報。若 Pulsar 只憑這一詢問決定某個請求（Pulsar 3.0 對租戶與叢集即是如此），被拒絕時只在 debug 層級記錄。
- 叢集上的 `admin` 只讓角色成為該叢集 broker 的超級使用者。
- Pulsar 的權限清單（例如 `pulsar-admin namespaces permissions`）會失敗：策略保存在 GrantForge 中。
- 用戶端位址只能是位址與網段；主機名稱不比對任何用戶端。
- 決策會快取 `grantforge.decision.cache.ttl.ms`，因此策略的生效或失效最多會延遲這麼久。

## 相關指南

- [Apache Pulsar](/zh-tw/plugins/pulsar/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
