---
title: Apache HBase 代理
description: 在 HBase 的 master 與 region server 中安裝協同處理器，以 GrantForge 策略檢查操作與讀寫，只傳回使用者可讀的欄，並隱藏使用者無權使用的內容。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

HBase 代理是 master、region 與 region server 的協同處理器：HBase 對每個操作與每次讀寫都詢問它，它依據 `hbase` 資料服務的簽章策略作答。發行套件以單一相容 Java 8 的 JAR 提供，即 `agents/hbase/grantforge-agent-hbase-<version>.jar`，相依套件已重新定位打包在內。

儲存庫的 Testcontainers 整合測試在 HBase `2.5.16`、`2.6.7` 與 `3.0.0` 中執行該 JAR。這並不代表對其他所有版本的認證。服務外掛請見 [Apache HBase](/zh-tw/plugins/hbase/)。

## 運作方式

- **每個請求都在其涉及的每個範圍上檢查。** HBase 中權限涵蓋其範圍內的一切，因此代理在請求的命名空間、資料表、欄族與欄上分別決策：任一範圍的拒絕都會拒絕請求，任一範圍的允許都會放行。
- **命名空間的擁有者擁有其中的資料表。** 命名空間上的 `create` 與 `admin` 讓使用者可以建立該命名空間、在其中建立資料表並管理這些資料表，無需逐表設定策略。
- **讀取傳回可讀的部分。** 使用者只能讀取資料表的一部分（例如一個欄族）時，get 與 scan 傳回這些欄，而不是失敗；對資料表中任何內容都無權讀取的使用者會遭拒。
- **清單略去無權使用的內容。** 列出命名空間與資料表時只顯示使用者可以使用、或可以使用其中某些內容的項目，列出快照時只顯示使用者管理之資料表的快照；描述無權使用的命名空間會遭拒。
- **拒絕會說明原因。** 用戶端收到 `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)`，或拒絕它的策略。
- **決策會被快取，存取會被合併。** 目前快照的決策會被快取，預設 100,000 筆、10 秒，新快照會重新開始。一個時間窗口（預設 10 秒）內相同的允許存取會作為一筆註明次數的存取事件回報；拒絕會立即逐筆回報。
- **伺服器本身不受檢查。** HBase 的超級使用者（`hbase.superuser` 中列出）以及伺服器自身的工作不受檢查也不被稽核。`hbase` 命名空間中的資料表（例如 `hbase:meta`）所有人都可讀取，這是用戶端必需的。

| 操作 | 所需存取 |
| --- | --- |
| Get、scan | 資料表上的 `read`，或傳回之欄上的 `read` |
| 欄的 put、increment、append、delete | 所寫每一欄上的 `write` |
| 刪除欄族或整列 | 每個欄族上的 `write`，因為 HBase 依欄族逐一刪除整列 |
| Check-and-mutate | 所比較欄上的 `read`（使用篩選器時為資料表上的 `read`），以及所寫內容上的 `write` |
| 建立資料表 | 命名空間上的 `create` |
| 刪除、清空、修改、啟用、停用、排清、分割、合併、壓縮 | 資料表上的 `admin` 或 `create` |
| 移動、指派、取消指派、下線 region | 資料表上的 `admin` |
| 建立、還原、刪除或列出快照 | 資料表上的 `admin`；複製還需要目標命名空間上的 `create` |
| 大量匯入 | 資料表上的 `create` |
| 建立、修改、刪除命名空間 | 命名空間上的 `admin` |
| 描述命名空間；列出命名空間與資料表 | 對其本身或其中內容的任意存取 |
| 平衡器、關閉叢集、配額、複寫對端、region server 群組、程序、除役 region server、停止 region server、輪替 WAL | `global` 上的 `admin` |

## 部署

1. 新增 `hbase` 資料服務並設定策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有 HBase 帳戶可讀取的檔案。
2. 將 JAR 複製到 **每個 master 與 region server** 的 `lib` 目錄，例如 `/opt/hbase/lib`。
3. 在每個節點的 `hbase-site.xml` 中加入下列設定，替換範例位址、路徑與執行個體名稱：

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   快取目錄必須對 HBase 帳戶可寫入。請在協同處理器清單中取代 HBase 內建的 `AccessController`，不要同時載入兩者。
4. 逐一重新啟動 master 與 region server。代理取得第一個快照之前，除超級使用者外的請求都會遭拒。
5. **先遷移現有權限：** HBase 的 `AccessController` 存在 `hbase:acl` 中的授權不再生效。請事先以 `user_permission` 列出它們，並建立對應的策略。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此 master 或 region server 的名稱，以及存放簽章快照與暫存事件的可寫目錄 |
| `grantforge.signing.key.file` | 選填，固定快照簽章金鑰的檔案；從「代理」頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時於首次連線時從伺服器取得 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 的連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理取得新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列容量（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆回報 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 快取的決策數上限，預設 `100000`，最多 `10000000`，`0` 表示不快取；以及快取時長，預設 `10000`，`1..3600000` 毫秒 |

HBase 會為 master、region server 以及它開啟的每個 region 各建立一個協同處理器；它們共用一個代理、決策快取與事件合併。

## 使用者、群組與角色

策略中的名稱是 HBase 賦予連線使用者的短名：simple 驗證的使用者名稱，或依 `hadoop.security.auth_to_local` 得到的 Kerberos 主體短名。群組是 Hadoop 群組對應在伺服器上為使用者給出的群組，加上 GrantForge 為使用者指定的群組；角色是 GrantForge 賦予使用者的角色。

## 決策與限制

- 沒有可用快照時，除超級使用者的請求與對 `hbase` 命名空間的讀取外，一律拒絕。未啟用的服務不檢查 HBase，與未安裝授權器時相同。
- 權杖綁定的服務類型不是 `hbase` 時，一律拒絕。
- HBase 的 `grant`、`revoke` 與 `user_permission` 會以 *AccessController is not loaded* 失敗：權限是 GrantForge 的策略。
- 協同處理器端點呼叫不受檢查；只載入可信的端點。
- 在這裡，HBase 不會給資料表的建立者額外權限；如上所述，命名空間策略會讓其擁有者擁有其中的資料表。
- HBase 會將某些拒絕只記入記錄而不傳回用戶端，例如平衡器開關：開關維持原狀，但用戶端不會得到提示。
- 決策會快取 `grantforge.decision.cache.ttl.ms`，因此策略有效期間的開始或結束最多會延遲這麼久。
- 不涵蓋 HBase 的儲存格層級權限與可見性標籤。欄以 UTF-8 文字比較。

## 相關指南

- [Apache HBase](/zh-tw/plugins/hbase/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
