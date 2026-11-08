---
title: Apache Hadoop HDFS NameNode 代理
description: 以對應 Hadoop 版本的 NameNode 代理執行 GrantForge 路徑策略，並回報存取稽核。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

服務端的 `grantforge-plugin-hdfs` 統一提供 `hdfs` 服務類型、資源與連線設定。NameNode 代理依 Hadoop 版本分別建置，透過對應版本的 `INodeAttributeProvider` 與 `AccessControlEnforcer` 檢查存取，共用簽章策略、快取、策略求值與稽核邏輯。

原始碼中的服務端外掛位於 `plugins/grantforge-plugin-hdfs`，版本代理位於 `agents/grantforge-agent-hdfs-<line>`，不依賴 Hadoop 的共用生產邏輯位於 `agents/grantforge-agent-hdfs-common`。共用原生調適位於正式 Maven 生產模組 `agents/grantforge-agent-hdfs-native`，採用 Java 8 / Hadoop 2.7.7 基線；各編號模組透過 Maven 二進位相依引用它，保留各版入口與特有回呼，不再重複編譯共用生產原始碼。協定、快照快取與稽核基礎設施仍位於 `core/grantforge-agent-core`。

服務端外掛維持一個 `hdfs` 類型，用戶端版本獨立於代理版本。Hadoop 2.x 的連線與路徑查詢使用 `webhdfs://namenode:50070`（啟用 HTTPS 時使用對應的 `swebhdfs://` 位址）；Hadoop 3.x 可使用 RPC `hdfs://` 或 WebHDFS。容器矩陣驗證 2.x 的 WebHDFS，以及 3.x 的 RPC 與 WebHDFS；未認證 2.x RPC，不會自動切換連線協定。

Apache Hadoop 2.7.7 在啟用 inode 屬性擴充後，一般使用者直接查詢根路徑 `/` 會在原生程式碼中觸發空指標，發生在代理回呼之前。此版本的資料操作與服務的 `lookup.path` 應使用實際資料目錄，例如 `/data`；測試保留了根路徑失敗的獨立斷言。

## 選擇版本

| 代理模組後綴 | 驗證的 Apache Hadoop | 容器 JVM | 授權介面 | 超級使用者路徑回呼 |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | 參數式 | 無 |
| `2.10` | 2.10.2 | Java 8 | 參數式 | 無 |
| `3.2` | 3.2.4 | Java 8 | 參數式 | 無 |
| `3.3` | 3.3.6 | Java 8 | 上下文式 | 無 |
| `3.4` | 3.4.3 | Java 11 | 上下文式、超級使用者及拒絕回呼 | 有 |
| `3.5` | 3.5.0 | Java 17 | 上下文式、超級使用者及拒絕回呼 | 有 |

例如 Hadoop 2.10.2 安裝 `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar`。每台 NameNode 只安裝一個版本的代理，升級時移除舊 jar，設定中的 provider 類別名稱保持不變。代理啟動時核對 Hadoop 主次版本及必要回呼，裝錯版本會停止啟動；同一主次版本的其他修補版本及廠商分支仍需另行驗證。

`common` 與 `2.7` 至 `3.4` 的代理主程式碼使用 Java 8 位元組碼，`3.5` 使用 Java 17。上表列出實際整合測試的 JVM，不代表 3.4.3 已驗證 Java 8。3.3.6 官方測試映像僅提供 amd64，在 ARM 主機上明確以 amd64 模擬執行。

## 權限關係

代理先執行 HDFS 原生權限檢查，再執行 GrantForge 策略：使用者必須同時滿足原生權限與策略要求。GrantForge 的允許策略不會繞過 POSIX 權限、ACL、擁有者檢查或 sticky bit；拒絕策略一律拒絕。原生權限設定仍透過 Hadoop 的管理工具維護。

預設 `grantforge.hdfs.native.fallback=false`：沒有本機策略快照、沒有符合的策略或代理尚未啟動時拒絕資料存取。設為 `true` 後，未被策略決定的存取使用原生權限；明確的拒絕策略仍然有效。服務端暫時無法連線時，繼續使用最後一份通過簽章驗證的本機快照。

一次授權回呼中的祖先、目標、子樹與快照路徑投影使用同一版策略快照；重新整理後的策略在下一次回呼生效，避免組合不同版本的允許規則。存取稽核記錄實際使用的策略版本。

代理檢查一般使用者存取目標所需的 `read`、`write`、`execute`，也檢查父目錄、祖先目錄與需要遞迴驗證的子目錄。建立、刪除、重新命名等操作涉及多個路徑，允許策略必須涵蓋它們。嚴格模式下，只有目標檔案的 `read` 策略還不夠，需要為使用者設定祖先目錄的 `execute` 策略，例如允許 `/` 上的 `execute` 並勾選遞迴，再為實際資料目錄設定讀寫權限。

快照路徑同時檢查實際請求路徑與去掉 `.snapshot/<快照名稱>` 後的原路徑，例如 `/data/.snapshot/s1/secret` 同時檢查 `/data/secret`。原路徑上的拒絕策略因此也約束快照；可以再為明確的快照路徑設定更嚴格的限制。中繼資料查詢沿用 HDFS 的目錄走訪權限語意。

一次遞迴授權最多檢查 `100000` 個 inode，超過上限會拒絕操作，避免在 NameNode 內無限配置記憶體。超長路徑使用完整路徑判定策略；稽核資源的顯示限制為 `1000` 字元，並在請求詳情中記錄原長度與 SHA-256 摘要。

Hadoop 2.7、2.10、3.2、3.3 在呼叫代理前略過超級使用者，代理無法管控或稽核這些存取。Hadoop 3.4、3.5 的帶路徑超級使用者回呼先通過原生檢查，再依操作名稱檢查策略：檔案讀取與中繼資料查詢要求 `read`，目錄列舉要求 `read` + `execute`，已知的修改操作要求 `write`。未知、缺失或無法準確推斷的操作（例如 `checkAccess` 與 `concat`）保守地要求三種權限。

超級使用者回呼沒有完整的 inode 與子樹上下文，無路徑的叢集管理呼叫保留原生檢查；無法以子目錄策略限制超級使用者的所有遞迴操作。資料使用者應使用一般 Hadoop 使用者。

## 指標

代理透過 Hadoop 的 Metrics2 體系回報指標，與 NameNode 自身的 dfs 指標使用同一套 sink，在 NameNode 的 JMX 中是 `Hadoop:service=NameNode,name=GrantForgeHdfsAgent`（Prometheus 的 JMX 匯出器可以直接擷取）。每個指標帶有 `instance`（資料服務執行個體名稱）與 `agentVersion` 標籤，因此 HA 的兩個 NameNode 可以分別檢視。指標註冊失敗只損失指標本身：代理會記錄警告，並在沒有指標的情況下繼續執行授權。

| 指標 | 說明 |
| --- | --- |
| `Callbacks` | 代理執行的授權回呼數 |
| `SuperuserCallbacks` | 代理執行的超級使用者回呼數 |
| `NativeDenies` | Hadoop 在代理之前就拒絕的存取數 |
| `EvaluationFailures` | 因策略求值例外而失敗關閉的回呼數 |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | 策略判定為允許 / 拒絕 / 未決的權限數；未決在嚴格模式下同樣拒絕 |
| `MissingSnapshots` | 沒有已驗證策略快照時服務的回呼數 |
| `SnapshotVersion` | 目前使用的策略快照版本，0 表示沒有 |
| `QueuedEvents` / `DroppedEvents` | 記憶體中待回報的稽核事件數 / 因佇列或磁碟緩衝已滿而捨棄的事件數 |
| `ServerReachable` | 最後一次存取策略伺服器是否成功（1/0） |

## 部署

1. 在 GrantForge 的資料服務中新增 `hdfs` 服務，儲存設定並測試連線；為實際的 Hadoop 短使用者名稱、使用者群組或角色設定路徑策略。
2. 在「資料權限 → 代理」中為此服務簽發權杖。將權杖原文寫到每個 NameNode 的本機檔案，例如 `/etc/hadoop/grantforge/token`，由 NameNode 執行使用者讀取。
3. 依上表從發行包 `agents/hdfs/<Hadoop 版本線>/` 選擇對應的代理 jar，放入 NameNode 的類別路徑，例如 `$HADOOP_HOME/share/hadoop/hdfs/lib/`。代理 jar 已包含自己的策略引擎、Jackson 與簽章函式庫，Hadoop 類別由 NameNode 提供；心跳中的代理版本包含產品版本與編譯所用的 Hadoop 版本。
4. 在每個 NameNode 的 `hdfs-site.xml` 中設定以下屬性；HA 的兩個 NameNode 使用不同的 `instance` 與各自本機的快取目錄。

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. 確認 `dfs.permissions.enabled=true`，且 `dfs.namenode.inode.attributes.provider.bypass.users` 為空；代理啟動時會拒絕可繞過授權回呼的設定。重新啟動 NameNode，然後在 GrantForge 代理頁面檢查心跳與策略版本。代理讀取 NameNode 既有設定；不修改 inode 的原生屬性。

首次部署可先使用 `native.fallback=true`，確認策略快照已同步並補齊祖先目錄權限，再切換到嚴格模式。權杖綁定的服務類型必須是 `hdfs`；設定錯誤或綁定到其他服務類型會拒絕存取。

## 選用設定

| 屬性 | 預設值 | 用途 |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | 連線 GrantForge 的逾時 |
| `grantforge.hdfs.read.timeout.ms` | `8000` | 讀取回應的逾時 |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | 伺服器無法連線時的策略重新整理間隔，至少 `1000`；正常心跳使用服務端建議的間隔 |
| `grantforge.hdfs.signing.key.file` | 未設定 | 選用的簽章公鑰檔案，內容為主控台提供的 Base64 X.509 公鑰；設定後只接受此公鑰的簽章 |
| `grantforge.hdfs.audit.batch.size` | `500` | 每次回報的最多事件數，範圍 `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | 記憶體稽核佇列容量，範圍 `1..1000000`，至少能容納一個批次；佇列滿時計數並捨棄新事件 |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | 稽核排清間隔，正整數，最大 `2147483647`；調小可降低回報延遲 |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | 伺服器無法連線時的磁碟緩衝上限，非負整數；`0` 停用磁碟緩衝 |

突發存取量較大時可加大稽核佇列，減少佇列溢位；縮短排清間隔可降低稽核延遲，也會增加回報頻率。磁碟緩衝限制用於控制長時間斷線時的磁碟用量，停用或耗盡緩衝時事件可能遺失。稽核回報在背景執行，不等待策略伺服器回應。

未設定簽章公鑰時，代理首次從伺服器取得公鑰並隨快照儲存。代理使用 Hadoop 傳入的短使用者名稱與使用者群組，角色及額外群組來自簽章快照；Kerberos principal 的短名稱對應由叢集的 `hadoop.security.auth_to_local` 決定。

## 從原始碼建置與驗證

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

代理產物位於 `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`。單元測試涵蓋 NameNode 授權回呼、設定、版本中繼資料與策略決策；WebHDFS 與 Kerberos 測試啟動本機暫時服務。上線前還需要在目標叢集驗證讀寫、建立、重新命名、遞迴刪除、HA 切換與斷線後的快取行為。

整合驗證隨 `verify` 階段以 Testcontainers 執行（單元測試不啟動叢集；`verify` 需要可用的 Docker daemon）：

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# 與 nightly 使用同一入口
bash script/ci/hdfs_integration.sh
# 只驗證指定的版本線
bash script/ci/hdfs_integration.sh 2.10
```

測試使用固定版本的 Apache Hadoop 映像；舊版映像以固定摘要的 Java 8 基礎映像與經 SHA-512 驗證的 Apache 發行包建置。將對應版本的實際代理 jar 放入 NameNode 類別路徑，並斷言實際的 Hadoop 與 JVM 版本。Testcontainers 建立隔離網路並管理 NameNode、DataNode 的生命週期，驗證讀寫、建立、附加、重新命名、刪除、遞迴與快照拒絕、原生權限及稽核、策略重新整理、與策略伺服器斷線後重新啟動 NameNode 使用簽章快取，以及沒有快照時的嚴格模式與原生權限回退。

需要執行中的 Docker daemon，並允許下載測試映像。Docker 無法使用時測試失敗，不會靜默略過。檔案系統用戶端在 Hadoop 容器內部執行，策略 HTTP 服務使用 Testcontainers 的主機連接埠轉送；不需要外部 Hadoop 叢集。測試結束後清理容器與測試網路，日誌儲存到 `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`。

HA 測試啟動兩個 NameNode、一個 DataNode 與一個 JournalNode，為兩個代理設定獨立的執行個體名稱與快取目錄。它使用邏輯 HDFS 用戶端手動切換作用中節點，並驗證切換後的讀寫與拒絕策略；單一 JournalNode 僅用於測試，不驗證多數派容錯，也不涉及 ZooKeeper 自動容錯移轉。

自動 HA 測試（Hadoop 2.10 至 3.5，Java 17 與 21）啟動 ZooKeeper、三個 JournalNode、兩個各帶 ZKFC 的 NameNode 與一個 DataNode。測試驗證：直接停止作用中 NameNode 後由 ZooKeeper 選出另一個節點接管，新的作用中節點繼續執行策略，拒絕事件歸屬於該節點的執行個體；節點離開期間發布的新策略在其以 Standby 恢復後生效，切回後仍執行新版本而不會回到舊版本；策略服務無法使用時，接管的節點以已載入的快照繼續執行，服務恢復後補送稽核；三個 JournalNode 停一個時寫入仍成功，停兩個時寫入失敗且作用中 NameNode 停止，而不是在沒有多數派的情況下繼續。另有一個 Kerberos 版本（僅 Hadoop 3.5.0）：JournalNode 也以 keytab 登入並只開放 HTTPS，ZKFC 透過 SASL 向 ZooKeeper 認證，選舉節點只允許 NameNode 的 principal 存取（未認證的 ZooKeeper 用戶端連讀取都會被拒）；測試驗證作用中 NameNode 被停止後另一個節點接管，並繼續對 Kerberos 使用者執行策略，沒有票證的用戶端一律被拒，原節點恢復後可以切回。JournalNode 以與 NameNode 相同短名稱的 principal 執行，因為 JournalNode 只把 edits 交給 NameNode 的完整 principal 或與自己短名稱相同的請求者，而 HTTP 認證只提供短名稱。Hadoop 2.7 的自動 HA 未驗證：它的用戶端只有在每個 NameNode 主機名稱都能解析時才會切換，測試中被停止的容器名稱不再能解析，用戶端會直接回報 UnknownHostException，而不嘗試另一個節點。

Kerberos 測試在全部六個版本線上執行（Java 17 與 21）：KDC 在測試 JVM 中執行，NameNode 與 DataNode 以安全模式啟動並各自使用 keytab，DataNode 的資料傳輸需要 SASL 且只開放 HTTPS，WebHDFS 使用 SPNEGO。測試驗證 principal 對應為短名稱後 GrantForge 策略對讀寫生效、沒有策略的使用者被拒、拒絕事件以短名稱稽核、沒有票證的用戶端被拒且不會回退到 simple 認證、NameNode 重新啟動後重新登入並繼續執行策略。

測試原始碼位於實際生產模組的 `src/test`。共用策略單元測試在 common 中執行，共用原生單元測試在 `agents/grantforge-agent-hdfs-native/src/test` 中執行，不再編譯進六個版本代理。各版回呼測試仍在對應的編號模組中，共用的容器測試原始碼 `agents/grantforge-agent-hdfs-common/src/test/shared` 仍編譯到各編號模組；沒有獨立的 Maven 測試專案。Testcontainers 僅為 test scope。nightly 在 Java 17 與 21 主機上驗證六個 Hadoop 版本，Hadoop 容器使用上表中的 JVM。目前容器涵蓋 Simple 認證、手動 HA、全部六個版本線的 Kerberos 與 HTTPS、2.10 至 3.5 的自動 HA，以及 Hadoop 3.5.0 上 Kerberos 下的自動 HA；2.7 的自動 HA、其他版本在 Kerberos 下的自動 HA 與廠商修補仍需要在目標環境驗證。

Hadoop 擴充入口與權限語意見 [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) 與 [HDFS 權限指南](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html)。
