---
title: Apache Kafka 代理
description: 在 Kafka 的 broker 與 controller 中安裝代理，以 GrantForge 策略授權每個請求，將策略顯示為唯讀 ACL，並將重複存取合併為一筆稽核事件。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Kafka 代理是 Kafka `Authorizer` API 的一個授權器，於 `authorizer.class.name` 中指定：broker 與 controller 對每個請求都詢問它，它依據 `kafka` 資料服務的簽章策略作答。發行套件以單一相容 Java 8 的 JAR 提供，即 `agents/kafka/grantforge-agent-kafka-<version>.jar`，相依套件已重新定位打包在內。

儲存庫的 Testcontainers 整合測試在 Kafka `3.7.2`、`3.9.2`、`4.1.2` 與 `4.3.1` 中執行該 JAR，節點以 KRaft 模式同時擔任 broker 與 controller。這並不代表對其他所有版本的認證。服務外掛請見 [Apache Kafka](/zh-tw/plugins/kafka/)。

## 運作方式

- **每個請求都會檢查。** 對請求涉及的每個資源，Kafka 詢問一個 ACL 操作；代理依據策略作答，以 Kafka 的資源類型為資源層級、以它的操作為存取類型。拒絕會以 Kafka 自己的錯誤回傳給用戶端，例如 `TOPIC_AUTHORIZATION_FAILED`。
- **冪等生產者只需一個主題。** 生產者可寫入某個主題時，Kafka 允許它在沒有叢集 `idempotent_write` 的情況下啟動，並就此詢問授權器。代理直接依據策略作答：任一主題上允許的 `write` 都算數，拒絕只在涵蓋所有主題的策略中算數。
- **策略顯示為 ACL。** `kafka-acls --list` 與 Admin API 的 `describeAcls` 顯示目前生效的策略：不含萬用字元的值顯示為字面 ACL，`orders*` 這樣的值顯示為前綴 ACL，`*` 顯示為 Kafka 的萬用字元；使用者為 `User:` 主體，群組為 `Group:`，角色為 `Role:`，`public` 群組為 `User:*`。ACL 無法表達的內容會略去：其他萬用字元、排除的值、帶條件的項目，以及帶例外的策略中受影響的項目。建立或刪除 ACL 會被拒絕：*the ACLs of this cluster are GrantForge policies; change them in GrantForge*。
- **決策會被快取。** broker 在每次生產與擷取時都會問相同的問題；代理快取目前快照的決策，預設 100,000 筆、10 秒。新快照會重新開始，因此代理取得策略變更後立即生效。
- **重複存取會被合併。** 一個時間窗口（預設 10 秒）內相同的允許存取會作為一筆存取事件回報，註明次數與最後一次的時間；主控台顯示 *N 次相同存取*。使用者、用戶端位址、資源、存取類型、請求類型與決定策略都相同的存取視為相同。拒絕會立即逐筆回報。回報哪些由 Kafka 決定：例如它從清單中略去的主題不會要求回報。
- **超級使用者不受檢查。** `super.users` 中的主體可以做任何事，與 Kafka 內建的授權器相同，且不會被稽核。

## 部署

1. 新增 `kafka` 資料服務並設定策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有 Kafka 帳戶可讀取的檔案。
2. 將 JAR 複製到 **每個 broker 與 controller** 的 `libs` 目錄，例如 `/opt/kafka/libs`。
3. 在每個節點的 `server.properties` 中加入下列設定，替換範例主體、位址、路徑與執行個體名稱：

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` 必須包含 broker 與 controller 之間互相通訊所用的主體，例如 broker 間監聽器的主體，否則需要策略允許它們所做的操作，例如叢集上的 `cluster_action`。連線至 `PLAINTEXT` 監聽器的主體是 `User:ANONYMOUS`；只有在用戶端無法存取這類監聽器時才可列出它。快取目錄必須對 Kafka 帳戶可寫入。
4. 逐一重新啟動 broker 與 controller。除 controller 的監聽器外，其他監聽器在代理取得第一個快照後開放，最多等待 `grantforge.start.timeout.ms`；在此之前，以及任何沒有快照的時候，請求都會被拒絕。
5. **先遷移現有 ACL：** 代理取代 Kafka 內建的授權器後，後者儲存的 ACL 不再生效。請事先以 `kafka-acls --list` 列出它們，並建立對應的策略。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此 broker 或 controller 的名稱，以及存放簽章快照與暫存事件的可寫目錄 |
| `grantforge.signing.key.file` | 選填，固定快照簽章金鑰的檔案；從「代理」頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時於首次連線時從伺服器取得 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 的連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理取得新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列容量（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆回報 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 快取的決策數上限，預設 `100000`，最多 `10000000`，`0` 表示不快取；以及快取時長，預設 `10000`，`1..3600000` 毫秒 |
| `grantforge.start.timeout.ms` | 監聽器等待第一個快照的時長，預設 `30000`，`0..600000` 毫秒 |

代理從 Kafka 交給授權器的設定中讀取設定值。同時擔任 broker 與 controller 的節點會為每個角色建立一個授權器；兩者共用一個代理。

## 使用者、群組與角色

策略中的名稱是 Kafka 賦予連線的主體：SASL 使用者名稱、依 `sasl.kerberos.principal.to.local.rules` 得到的 Kerberos 主體短名，或依 `ssl.principal.mapping.rules` 得到的 TLS 憑證名稱。Kafka 沒有群組與角色；策略中的群組與角色是 GrantForge 賦予使用者的。

## 決策與限制

- 沒有可用快照時，除超級使用者外的所有請求都會被拒絕。未啟用的服務不檢查 Kafka，與未安裝授權器時相同。
- 權杖綁定的服務類型不是 `kafka` 時，一律拒絕。
- 拒絕一個存取類型也會拒絕它所蘊含的類型：拒絕主題上的 `write` 會拒絕 `describe`，使用者因此看不到該主題。這一點與 Kafka 自己的 ACL 不同。
- Kafka 詢問使用者能否對某類資源中的任一資源執行操作，這不算存取，不會回報。
- 決策會快取 `grantforge.decision.cache.ttl.ms`，因此策略有效期間的開始或結束最多會延遲這麼久。
- 代理執行於 broker 與 controller 中，不涵蓋 Kafka Connect 的 REST API、Schema Registry 以及 Kafka 周邊的其他服務。

## 相關指南

- [Apache Kafka](/zh-tw/plugins/kafka/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
