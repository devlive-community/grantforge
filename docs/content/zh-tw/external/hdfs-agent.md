---
title: HDFS NameNode 代理
description: 在 Hadoop 3.5.0 NameNode 內執行 GrantForge 路徑策略，並回報存取稽核。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

服務端的 `grantforge-plugin-hdfs` 定義資源與連線設定；`grantforge-agent-hdfs` 安裝在 NameNode 內，透過 Hadoop 的 `INodeAttributeProvider` 與 `AccessControlEnforcer` 檢查存取，重用 GrantForge 代理核心下載簽章策略、儲存本機快照與批次回報稽核。目前代理針對 **Hadoop 3.5.0、Java 17 及以上**建置，其他 Hadoop 版本需要對應版本的調適與驗證。

原始碼中的服務端外掛位於 `plugins/grantforge-plugin-hdfs`，NameNode 代理位於 `agents/grantforge-agent-hdfs`；共用協定、快照快取與稽核回報基礎設施位於 `core/grantforge-agent-core`。

## 權限關係

代理先執行 HDFS 原生權限檢查，再執行 GrantForge 策略：使用者需要同時滿足原生權限與策略要求。GrantForge 的允許策略不會繞過 POSIX 權限、ACL、擁有者檢查或 sticky bit；拒絕策略始終拒絕。原生權限設定仍透過 Hadoop 的管理工具維護。

預設 `grantforge.hdfs.native.fallback=false`：沒有本機策略快照、沒有相符策略或代理尚未啟動時拒絕資料存取。設為 `true` 後，未被策略決定的存取使用原生權限；明確拒絕策略仍然有效。服務端暫時不可連線時繼續使用最後一份通過簽章驗證的本機快照。

一次授權回呼中的祖先、目標、子樹與快照路徑投影使用同一版策略快照；重新整理後的策略在下一次回呼生效，避免組合不同版本的允許規則。存取稽核記錄實際使用的策略版本。

代理檢查一般使用者存取目標所需的 `read`、`write`、`execute`，也檢查父目錄、祖先目錄與需要遞迴驗證的子目錄。建立、刪除、重新命名等操作涉及多個路徑，允許策略必須涵蓋它們。嚴格模式下，只有目標檔案的 `read` 策略還不夠，需要為使用者設定祖先目錄的 `execute` 策略，例如允許 `/` 上的 `execute` 並勾選遞迴，再為實際資料目錄設定讀寫權限。

快照路徑同時檢查實際請求路徑與去掉 `.snapshot/<快照名>` 後的原本路徑，例如 `/data/.snapshot/s1/secret` 同時檢查 `/data/secret`。原本路徑上的拒絕策略因此也約束快照；可以再為明確快照路徑設定更嚴格的限制。中介資料查詢沿用 HDFS 的目錄走訪權限語意。

一次遞迴授權最多檢查 `100000` 個 inode，超過上限會拒絕操作，避免在 NameNode 內無限配置記憶體。超長路徑使用完整路徑判定策略；稽核資源展示限制為 `1000` 字元，並在請求詳情中記錄原本長度與 SHA-256 摘要。

HDFS 超級使用者仍由 Hadoop 管理。帶路徑的超級使用者回呼先透過 Hadoop 的超級使用者檢查，再依 Hadoop 3.5.0 提供的操作名稱檢查策略：檔案讀取與中介資料查詢要求 `read`，目錄列舉要求 `read` + `execute`，已知修改操作要求 `write`。未知、缺少或無法準確推斷的操作（例如 `checkAccess` 和 `concat`）保守地要求三種權限。

超級使用者回呼沒有完整 inode 與子樹上下文，無路徑的叢集管理呼叫保留原生檢查；無法用子目錄策略限制超級使用者的所有遞迴操作。資料使用者應使用一般 Hadoop 使用者。

## 指標

代理透過 Hadoop 的 Metrics2 體系回報指標，與 NameNode 自身的 dfs 指標使用同一套 sink，在 NameNode 的 JMX 裡是 `Hadoop:service=NameNode,name=GrantForgeHdfsAgent`（Prometheus 的 JMX 匯出器可以直接抓取）。每個指標帶有 `instance`（資料服務執行個體名）與 `agentVersion` 標籤，HA 的兩個 NameNode 因此可以分別查看。指標註冊失敗只損失指標本身：代理會記錄警告並在沒有指標的情況下繼續執行授權。

| 指標 | 說明 |
| --- | --- |
| `Callbacks` | 代理執行的授權回呼數 |
| `SuperuserCallbacks` | 代理執行的超級使用者回呼數 |
| `NativeDenies` | Hadoop 在代理之前就拒絕的存取次數 |
| `EvaluationFailures` | 因策略求值例外而失敗關閉的回呼次數 |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | 策略判定為允許 / 拒絕 / 未定的權限次數；未定在嚴格模式下同樣拒絕 |
| `MissingSnapshots` | 沒有已驗證策略快照時服務的回呼次數 |
| `SnapshotVersion` | 目前使用的策略快照版本，0 表示沒有 |
| `QueuedEvents` / `DroppedEvents` | 記憶體中待回報的稽核事件數 / 因佇列或磁碟緩衝滿而丟棄的事件數 |
| `ServerReachable` | 最後一次連線策略伺服器是否成功（1/0） |

## 部署

1. 在 GrantForge 的資料服務中新增 `hdfs` 服務，儲存設定並測試連線；為實際 Hadoop 短使用者名稱、使用者群組或角色設定路徑策略。
2. 在「資料權限 → 代理」中為這個服務簽發權杖。將權杖原文寫到每個 NameNode 的本機檔案，例如 `/etc/hadoop/grantforge/token`，由 NameNode 執行使用者讀取。
3. 將發行包 `agents/hdfs/` 下與目前發行版本對應的 `grantforge-agent-hdfs-<版本>.jar` 放入 NameNode 的類別路徑，例如 `$HADOOP_HOME/share/hadoop/hdfs/lib/`。代理 jar 已包含自己的策略引擎、Jackson 與簽章庫，Hadoop 類別由 NameNode 提供；心跳中的代理版本由建置中介資料產生。
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

5. 確認 `dfs.permissions.enabled=true`，且 `dfs.namenode.inode.attributes.provider.bypass.users` 為空；代理啟動時會拒絕可繞過授權回呼的設定。重新啟動 NameNode，然後檢查 GrantForge 代理頁面中的心跳與策略版本。代理讀取 NameNode 現有設定；不修改 inode 的原生屬性。

首次部署可先使用 `native.fallback=true`，確認策略快照已同步並補齊祖先目錄權限，再切換嚴格模式。權杖綁定的服務類型必須是 `hdfs`；設定錯誤或綁定到其他服務類型會拒絕存取。

## 選用設定

| 屬性 | 預設值 | 用途 |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | 連線 GrantForge 的逾時 |
| `grantforge.hdfs.read.timeout.ms` | `8000` | 讀取回應的逾時 |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | 伺服器不可連線時的策略重新整理間隔，至少 `1000`；正常心跳使用服務端建議間隔 |
| `grantforge.hdfs.signing.key.file` | 未設定 | 選用的簽章公鑰檔案，內容為主控台提供的 Base64 X.509 公鑰；設定後只接受該公鑰的簽章 |
| `grantforge.hdfs.audit.batch.size` | `500` | 每次回報最多事件數，範圍 `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | 記憶體稽核佇列容量，範圍 `1..1000000`，至少能容納一個批次；佇列滿時計數並丟棄新事件 |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | 稽核重新整理間隔，正整數，最大 `2147483647`；調小可降低回報延遲 |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | 伺服器不可連線時磁碟緩衝上限，非負整數；`0` 停用磁碟緩衝 |

突發存取量較大時可增大稽核佇列，減少佇列溢位；縮短重新整理間隔可降低稽核延遲，也會增加回報頻率。磁碟緩衝限制用於控制長時間斷線的磁碟佔用，停用或耗盡緩衝時事件可能遺失。稽核回報在背景執行，不等待策略伺服器回應。

未設定簽章公鑰時，代理首次從伺服器取得公鑰並隨快照儲存。代理使用 Hadoop 傳入的短使用者名稱與使用者群組，角色與額外群組來自簽章快照；Kerberos principal 的短名對應由叢集的 `hadoop.security.auth_to_local` 決定。

## 從原始碼建置與驗證

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs,core/grantforge-plugin-host -am test
```

代理產物位於 `agents/grantforge-agent-hdfs/target/grantforge-agent-hdfs-<版本>.jar`。單元測試涵蓋 NameNode 授權回呼、設定、版本中介資料與策略決策；WebHDFS 與 Kerberos 測試啟動本機暫存服務。上線前還需在目標叢集驗證讀寫、建立、重新命名、遞迴刪除、HA 切換與斷線後的快取行為。

整合驗證隨 `verify` 階段以 Testcontainers 執行（單元測試不啟動叢集；`verify` 需要可用的 Docker daemon）：

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am verify
# 與 nightly 使用同一入口
bash script/ci/hdfs_integration.sh
```

測試使用固定版本的 Apache Hadoop 容器映像，將真實打包的代理 jar 放入 NameNode 類別路徑。Testcontainers 建立隔離網路並管理 NameNode、DataNode 的生命週期，驗證讀寫、建立、附加、重新命名、刪除、遞迴與快照拒絕、原生權限及稽核、策略重新整理、中斷策略伺服器後重新啟動 NameNode 使用簽章快取，以及無快照時的嚴格模式與原生權限回退。

需要執行中的 Docker daemon，並允許下載測試映像。Docker 不可用時測試失敗，不會靜默跳過。檔案系統客戶端在 Hadoop 容器內部執行，策略 HTTP 服務使用 Testcontainers 的主機連接埠轉送；無需外部 Hadoop 叢集。測試結束後清理容器與測試網路，日誌儲存到 `agents/grantforge-agent-hdfs/target/hdfs-testcontainers`。

HA 測試啟動兩個 NameNode、一個 DataNode 與一個 JournalNode，為兩個代理設定獨立執行個體名與快取目錄。它使用邏輯 HDFS 客戶端手動切換活動節點，並驗證切換後的讀寫與拒絕策略；單一 JournalNode 僅用於測試，不驗證多數決容錯，也不涉及 ZooKeeper 自動故障轉移。

測試原始碼與依賴直接放在現有 `agents/grantforge-agent-hdfs` 的 `src/test` 與 test scope 中，不單獨建立 Maven 測試專案；Testcontainers 不會進入代理發行包。nightly 在 Java 17 與 21 上執行同一測試入口，並儲存報告與容器日誌。

Hadoop 擴充入口與權限語意見 [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) 和 [HDFS 權限指南](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html)。
