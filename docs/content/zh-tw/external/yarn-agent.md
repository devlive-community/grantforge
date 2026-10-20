---
title: Apache Hadoop YARN 代理
description: 在 YARN ResourceManager 中安裝授權提供者，以 GrantForge 策略決定誰可以把哪些應用程式提交到哪個佇列、誰可以管理佇列與叢集、可以從哪些網路發起。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

YARN 代理是 ResourceManager 的授權提供者：YARN 詢問它誰可以向佇列提交、管理佇列以及管理叢集，它依據 `yarn` 資料服務的簽章策略作答。發行套件以一個相容 Java 8 的 JAR 提供它：`agents/yarn/grantforge-agent-yarn-<version>.jar`，相依套件已遮蔽打包在內。

儲存庫的 Testcontainers 整合測試在 Hadoop `3.2.4`、`3.3.6`、`3.4.3` 與 `3.5.0` 的 ResourceManager 中執行該 JAR。這並不代表對其他所有版本的認證。服務外掛請參閱 [Apache Hadoop YARN](/zh-tw/plugins/yarn/)。

## 運作方式

- **每次檢查都在路徑上的每個佇列判定。** 與 YARN 一樣，佇列上的權限涵蓋其下的佇列，因此代理在 `root`、`root.sales` 與 `root.sales.etl` 上判定一次向 `root.sales.etl` 的提交：任一處拒絕都會拒絕，任一處允許都會授予。
- **提交還會依應用程式名稱判定。** 在上述每個佇列上，代理還會詢問「佇列加應用程式名稱」，因此一條策略可以只接受部分應用程式（例如 `payroll-*`）進入某個佇列及其下的佇列。
- **策略可以只對部分網路生效。** 每次檢查都帶上用戶端位址（在代理之後為第一個轉發位址），代理以 Hadoop 自己的主機清單評估 **用戶端位址** 條件。
- **叢集管理受策略約束。** YARN 的管理操作（否則由 `yarn.admin.acl` 授予）需要叢集上的 `admin`。
- **拒絕會連同原因記入日誌。** 用戶端收到的拒絕由 YARN 措辭；ResourceManager 日誌會寫明是哪條 GrantForge 策略拒絕的，或者沒有策略允許。
- **快取決策並合併存取。** 目前快照的決策會被快取，預設 100,000 筆、10 秒，新快照會重新開始。時間窗內（預設 10 秒）相同的允許存取會以一筆存取事件回報，並註明次數；拒絕會立即逐筆回報。每個事件都註明應用程式、佇列與用戶端位址。
- **不檢查 ResourceManager 本身。** ResourceManager 自己的使用者（例如 `yarn`）既不檢查也不稽核。

| 操作 | 所需存取 |
| --- | --- |
| 提交應用程式 | 所在佇列上的 `submit_app`，或所在佇列加應用程式名稱上的 `submit_app`；佇列上的 `admin_queue` 也可以 |
| 終止、移動或變更其他使用者應用程式的優先順序，或檢視其全部詳細資料 | 所在佇列上的 `admin_queue`，移動時還需要新佇列上的 `submit_app`；應用程式的擁有者以及其自身 ACL 允許的人始終可以 |
| `yarn rmadmin`：重新整理佇列、節點、使用者群組與 ACL，修改節點標籤與屬性，以及其他管理操作 | 叢集上的 `admin` |

## 部署

1. 新增 `yarn` 資料服務並設定其策略。開啟 **資料權限 → 代理**，選擇該服務並簽發代理權杖。權杖只顯示一次；請存入只有 YARN 執行帳號可讀的檔案。
2. 把 JAR 複製到 **每個 ResourceManager** 的 `share/hadoop/yarn/lib`，例如 `/opt/hadoop/share/hadoop/yarn/lib`。
3. 在每個 ResourceManager 的 `yarn-site.xml` 中加入以下設定，替換範例中的位址、路徑、使用者與執行個體名稱：

   ```xml
   <property><name>yarn.acl.enable</name><value>true</value></property>
   <property><name>yarn.authorization-provider</name>
     <value>org.devlive.grantforge.yarn.agent.GrantForgeYarnAuthorizer</value></property>
   <property><name>yarn.admin.acl</name><value>yarn</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hadoop/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>resourcemanager-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hadoop-yarn/grantforge-cache</value></property>
   ```

   `yarn.admin.acl` 只能填 ResourceManager 自己的使用者：YARN 在向代理詢問佇列之前，會讓它的管理員對所有應用程式為所欲為。快取目錄必須可由 YARN 執行帳號寫入。
4. 逐一重新啟動 ResourceManager。在代理收到第一個快照之前，除 ResourceManager 本身外的請求都會被拒絕。
5. **先遷移既有權限：** 排程器設定中的佇列 ACL（例如 `acl_submit_applications` 與 `acl_administer_queue`）將不再生效。請事先建立對應的策略。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必填：GrantForge 伺服器、代理權杖檔案、此 ResourceManager 的名稱，以及存放簽章快照與暫存事件的可寫目錄 |
| `grantforge.signing.key.file` | 選用的固定快照簽章金鑰檔；從代理頁面複製 Base64 編碼的 X.509 Ed25519 公開金鑰。省略時於首次連線時從伺服器取得 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge 連線與讀取逾時，預設 `5000` / `8000` 毫秒 |
| `grantforge.refresh.interval.ms` | 代理擷取新策略的間隔，預設 `30000`，至少 `1000` 毫秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | 存取事件：批次大小（預設 `500`）、佇列大小（預設 `10000`）、傳送間隔（預設 `5000` 毫秒）與暫存上限（預設 64 MiB） |
| `grantforge.audit.fold.window.ms` | 相同的允許存取合併為一筆事件的時長，預設 `10000`，最多 `3600000` 毫秒；`0` 表示逐筆回報 |
| `grantforge.decision.cache.size`、`grantforge.decision.cache.ttl.ms` | 快取決策的最大數量，預設 `100000`，最多 `10000000`，`0` 表示不快取；以及快取時長，預設 `10000`，範圍 `1..3600000` 毫秒 |

## 使用者、群組與角色

策略中的使用者是 YARN 給呼叫者的短名稱：simple 驗證下的使用者名稱，或 Kerberos 主體依 `hadoop.security.auth_to_local` 得到的短名稱。群組為 Hadoop 群組對應在 ResourceManager 上給出的群組，加上 GrantForge 中該使用者所屬的群組；角色為 GrantForge 授予該使用者的角色。

## 決策與限制

- 沒有可用快照時，除 ResourceManager 本身的請求外一切都會被拒絕。未啟用的服務讓 YARN 不受檢查，與關閉 `yarn.acl.enable` 時一樣。
- 繫結到類型不是 `yarn` 之服務的權杖會拒絕一切。
- YARN 詢問管理權限時不帶用戶端位址，因此用戶端位址條件在 `cluster` 上永不成立。
- 應用程式的擁有者以及其自身 ACL 指定的使用者無論策略如何都可以管理該應用程式，與 YARN 一致。
- 其他存取類型的檢查（例如應用程式優先順序 ACL）會被拒絕。
- 用戶端位址條件中的主機名稱，每次在未命中快取而做決策時都要對用戶端做一次名稱查詢。
- 決策會快取 `grantforge.decision.cache.ttl.ms`，因此策略的有效期最多會延遲這麼久開始或結束。

## 相關指南

- [Apache Hadoop YARN](/zh-tw/plugins/yarn/)
- [資料服務、策略與代理](/zh-tw/external/data-services/)
