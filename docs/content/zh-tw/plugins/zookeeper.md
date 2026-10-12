---
title: Apache ZooKeeper
description: 安裝與設定 ZooKeeper 服務端外掛，瀏覽 znode 並為 znode 路徑管理存取策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache ZooKeeper 外掛為 GrantForge 提供 ensemble 連線、znode 路徑查詢與策略管理。外掛 ID 與服務類型都是 `zookeeper`。

## 安裝

發行包已在 `plugins/zookeeper` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `zookeeper` 已啟用；更新外掛目錄後重新掃描。外掛在自己的類別載入器中使用 ZooKeeper 3.9 用戶端，不依賴伺服器的函式庫。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 ZooKeeper（`zookeeper`）。
2. 填寫連線字串，即 ensemble 的伺服器清單，可帶 chroot，例如 `zk1:2181,zk2:2181,zk3:2181/kafka`。用戶端會在伺服器之間自動切換。
3. 選擇認證方式並設定查詢 znode，測試連線（確認 znode 存在且可列出子節點）後儲存服務。

| 設定 | 用途 |
| --- | --- |
| `zookeeper.connect` | 必填連線字串：`host[:port]` 以逗號分隔，可帶 `/chroot`；chroot 不能以 `/` 結尾 |
| `authentication` | `none`（預設）、`digest` 或 `kerberos` |
| `username` | digest：ACL 中的 digest 使用者（不能包含冒號）；Kerberos：principal，如 `grantforge@EXAMPLE.COM` |
| `password` | digest 使用者的密碼，加密儲存 |
| `keytab` | Kerberos：GrantForge 伺服器上 principal 的 keytab 路徑 |
| `zookeeper.sasl.client.username` | Kerberos：伺服器 principal 的第一段，預設 `zookeeper`，即 `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos：伺服器 principal 不是上述形式時填寫完整 principal，如 `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | 是否連線到伺服器的 TLS 用戶端連接埠，預設 `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | 驗證伺服器憑證的信任庫；留空則信任伺服器 Java 所信任的憑證；類型為 `jks`、`pkcs12` 或 `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | 伺服器要求用戶端憑證時使用的金鑰庫 |
| `session.timeout.ms` | 工作階段逾時，預設 `15000`，範圍 `2000..120000` |
| `connection.timeout.ms` | 僅用於初始連線與認證的等待上限，預設 `10000` ms，範圍 `1000..60000`；不限制連線後的同步請求 |
| `request.timeout.ms` | 單次同步請求的等待上限，預設 `8000` ms，範圍 `1000..60000` |
| `lookup.path` | 查詢與瀏覽的起始 znode（chroot 之下），預設 `/`，例如 `/brokers`；瀏覽限制在此 znode 內 |
| `lookup.max.entries` | 單一 znode 一次讀取的子節點上限，預設 `10000`，範圍 `1..100000` |

## 認證

- **none**：不登入，只能看到 ACL 對 `world:anyone` 開放的 znode。
- **digest**：以 `username:password` 登入，對應 ZooKeeper ACL 中的 `digest` 身分。密碼錯誤不會被拒絕登入，而是成為另一個身分，讀取受保護的 znode 時顯示無權存取。
- **kerberos**：使用 keytab 透過 SASL 登入，需要伺服器的 `krb5.conf` 能找到 KDC。每個 principal 與 keytab 都有自己的 JAAS 登入區段，與行程原有的設定並存，因此一個 GrantForge 可以用不同 principal 連線多個 ensemble。暫不支援僅使用密碼的 Kerberos 登入。

儲存時會檢查設定：連線字串格式、digest 需要使用者與密碼、Kerberos 需要 principal 與 keytab、keytab 與伺服器 principal 只用於 Kerberos，以及各項逾時與上限的範圍。

關閉 TLS 時，信任庫與金鑰庫的路徑和密碼必須留空；填寫庫密碼時，必須同時提供對應的庫路徑。兩種庫的類型僅支援 `jks`、`pkcs12` 和 `pem`。

TLS 庫密碼前後的空白會原樣保留。服務設定決定本次連線的 TLS 模式及使用的憑證庫，覆寫 JVM 中既有的 ZooKeeper TLS 選擇與庫設定；伺服器憑證和主機名稱仍會驗證。

## 瀏覽 znode

在 **資料權限 → 策略** 選擇服務，使用 `path` 旁的 **瀏覽** 按鈕：

- 依名稱排序分頁列出子節點；有子節點的 znode 可繼續開啟。
- 顯示每個 znode 的 ACL（`scheme:id:cdrwa`，與 ZooKeeper 內建工具一致）、資料大小與修改時間。
- 目前使用者無權讀取 ACL 的 znode 仍會列出名稱，開啟時顯示無權存取的原因。

分頁期間即使 znode 被刪除，游標仍依最後已掃描的子節點名稱往前推進，不依賴該子節點繼續存在。子節點列表不是快照；並行新增或刪除遵循 ZooKeeper 的語意，可能改變後續頁面的內容。

輸入路徑時會提示候選；不存在的父節點回傳空候選，超出 `lookup.path` 的路徑會被拒絕。子節點超過 `lookup.max.entries` 的 znode 會回報錯誤，而不會只顯示部分結果。

## 讓策略生效

資源只有一級 `path`，存取類型與 ZooKeeper 的 ACL 權限一致：`read`、`write`、`create`、`delete`、`admin`；路徑策略支援遞迴與排除。

服務外掛負責管理、查詢與瀏覽；伺服器內的 agent 透過 SASL authentication provider 為 marker ACL 執行策略。建置命令 `mvn -pl agents/grantforge-agent-zookeeper -am package` 會產生單一相容 Java 8、內含相依套件的 shaded JAR；發行包路徑為 `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`。實作以 ZooKeeper 3.6–3.9 API 為目標，實際版本相容性仍待驗證，不代表完整版本矩陣已通過或已完成正式環境驗收。

### 部署

1. 將 JAR 安裝到 **每個 ZooKeeper server** 的 `lib` 目錄，包括所有可接收用戶端請求的節點。在 GrantForge 對應的 `zookeeper` 資料服務中建立 **Agent 憑證**，將 token 存入獨立檔案，僅允許 ZooKeeper 執行帳號讀取；不要將 token 內容寫入 `zoo.cfg`。
2. 在每台伺服器的 `zoo.cfg` 中加入以下設定，替換範例位址、檔案路徑與實例識別碼。`grantforge.server.url`（`http[s]://GrantForge`）、`grantforge.token.file`、本機 `grantforge.cache.dir` 與 `grantforge.instance` 均為必填；快取目錄必須可由 ZooKeeper 執行帳號寫入。

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. 此 provider **取代原生 SASL provider**；不可在其他 `authProvider.N` 中保留另一個註冊相同 `sasl` scheme 的 provider。必須設定 `enableEagerACLCheck=false`：啟用時會拒絕 provider 註冊，marker 也不允許。保留並驗證正常的 ZooKeeper SASL、JAAS 與 Kerberos 認證設定；agent 不取代身分認證。依 ensemble 的維護流程重新啟動伺服器。
4. 可選設定 `grantforge.signing.key.file`，指定用於固定信任的簽章公鑰檔案。`grantforge.principal.mapping=short` 為預設值，將 `kafka/host@REALM` 對應為 `kafka`；`full` 保留完整 principal。策略中的使用者必須與對應後的身分一致。

### ACL 與限制

- 僅 `sasl:grantforge` marker ACL 交由 GrantForge 策略判斷；例如 `sasl:grantforge:cdrwa`，其中 `c=create`、`d=delete`、`r=read`、`w=write`、`a=admin`。其他原生 ACL（包括其他 SASL 身分）照常判斷。
- agent 未執行、沒有可用的策略 snapshot、snapshot 的 `serviceType` 不是 `zookeeper`，或策略不符合／不允許時，marker 拒絕存取。**這不是全域 deny**：其他 ACL、`world:anyone` 或 `super` 身分仍可能放行請求。
- 策略比對伺服器上的絕對目標路徑。`create`／`delete` 比對要建立／刪除的 znode，而非提供 ACL 的父節點；使用 chroot 的請求必須加上實際 chroot 前綴，例如 chroot `/kafka` 下的 `/brokers` 對應策略路徑 `/kafka/brokers`。sequential `create` 檢查附加序號前的伺服器絕對請求前綴；後續 `read`／`delete` 檢查產生的完整節點名稱。
- 原生 ACL 不會自動遷移，ACL 也不會遞迴繼承；新建 znode 必須明確設定 marker ACL。使用 `setACL` 前，先在小範圍驗證允許與拒絕，並保留獨立管理管道；不要貿然將 marker 套用到根節點，以免鎖死存取。
- 未安裝 agent 的伺服器會將 `sasl:grantforge` 視為原生 SASL 身分 `grantforge` 進行比對，而不會執行策略，因此必須在所有節點部署，不能依靠部分部署保護 ensemble。
- follower/observer 轉送至 leader 的寫入請求在此 provider 介面可能沒有用戶端連線，因此 `clientAddress` context 可能為空，無法保證依據用戶端 IP 的 ensemble 全鏈路策略。
- 此 provider 無法得知請求的最終結果，因此不傳送最終 `AccessEvent` 稽核事件，也不提供完整存取稽核。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [外掛與服務類型開發](/zh-tw/develop/plugins/)
