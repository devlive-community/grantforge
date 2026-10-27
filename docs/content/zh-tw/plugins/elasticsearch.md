---
title: Elasticsearch
description: 安裝並設定 Elasticsearch 服務外掛，連線一個或多個節點，使用密碼、API 金鑰或 TLS 用戶端憑證登入，查詢索引、資料串流、欄位與叢集，並以 Elasticsearch 自己的權限管理誰能讀取、寫入與管理什麼，細化到欄位與文件。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Elasticsearch 外掛查詢索引、資料串流、欄位與叢集，並依 Elasticsearch 自己的方式管理存取策略：讀取欄位，寫入、刪除文件，建立、刪除與管理索引，以及叢集本身的操作，並支援索引的列篩選。外掛 ID 與服務類型皆為 `elasticsearch`。服務外掛執行於 GrantForge 伺服器中；執行在叢集旁的 [Elasticsearch 代理](/zh-tw/external/elasticsearch-agent/)藉由 Elasticsearch 自己的角色、欄位層級安全與文件層級安全執行策略。

儲存庫的 Testcontainers 整合測試涵蓋啟用安全功能的 Elasticsearch `8.19`、`9.3`、`9.4` 與 `9.5`；這不代表認證了其他所有版本。

## 安裝

發行套件在 `plugins/elasticsearch` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `elasticsearch` 已啟用；更新外掛目錄後重新掃描。外掛透過 Elasticsearch 的低階 REST 用戶端與節點通訊，位於外掛自己的類別載入器中。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Elasticsearch（`elasticsearch`）。
2. 以 URL 輸入一個節點或多個節點。
3. 選擇查詢使用者的登入方式，填寫憑證，測試連線後儲存服務。測試會登入並描述叢集：Elasticsearch 版本與叢集名稱、授權及其是否包含文件層級與欄位層級安全、登入所用的使用者，以及叢集有多少個索引與資料串流。沒有啟用安全功能的叢集測試會失敗，因為代理藉由 Elasticsearch 的角色執行策略。

查詢使用者用於讓 GrantForge 探索資源，與讀寫資料的使用者相互獨立。連線測試成功不代表存取已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `elasticsearch.hosts` | 必填：以 URL 表示的節點，例如 `https://es1.example.com:9200`；多個以逗號分隔，某個無法連線時依序嘗試下一個。不寫協定時為 `https`，連接埠預設為 `9200`；同一服務的節點要嘛都是 `http`，要嘛都是 `https` |
| `authentication` | `password`（預設）、`api_key`，或 `certificate`：PKI realm 對應為某個使用者的 TLS 用戶端憑證 |
| `username`、`password` | password：查詢使用者及其密碼，密碼加密儲存 |
| `apiKey` | api_key：編碼後的 API 金鑰，即 Elasticsearch 建立它時傳回的值，加密儲存 |
| `ssl.verificationMode` | 與 Kibana 的命名一致：`full`（預設）驗證節點的憑證與名稱，`certificate` 只驗證憑證，`none` 不驗證 |
| `ssl.certificateAuthorities` | **GrantForge 伺服器**上用來驗證節點的 PEM CA 憑證路徑，例如 Elasticsearch 的 `http_ca.crt`；空白時使用 JVM 的 |
| `ssl.certificate`、`ssl.key` | certificate：GrantForge 伺服器上 PEM 用戶端憑證及其私鑰的路徑，私鑰可為 PKCS #8 或較早的 OpenSSL 格式；需要 `https` 節點 |
| `ssl.keyPassphrase` | certificate：加密私鑰的通關密語，加密儲存 |
| `timeout` | 連線與請求可用的時間，預設 `30000`，範圍 `1..600000` 毫秒 |

一種登入方式的設定在其他方式下會被拒絕，因此服務會明確說明如何登入。被拒絕的登入、已撤銷的 API 金鑰、缺少的權限、無法連線的節點以及無法驗證憑證的節點，都會如實回報。

### 查詢使用者能看到什麼

查詢使用者需要能讀取它列出的內容：索引與資料串流來自 Elasticsearch 的 resolve API，欄位來自欄位能力（field capabilities），叢集名稱來自叢集描述。對索引擁有 `read` 與 `view_index_metadata` 的角色即可滿足前兩項；沒有 `monitor` 叢集權限的使用者也能通過連線測試，只是測試不會寫出版本與授權。名稱以點開頭的索引與資料串流（隱藏與系統索引）只在輸入以點開頭時列出；資料串流的後備索引以其資料串流列出。欄位指文件中的欄位：Elasticsearch 自己的中繼資料欄位、物件，以及再次索引某個欄位的多欄位（例如 `name.keyword`）不會列出，代理會把它們與原欄位一同授予。

## 資源與存取類型

| 層級 | 比對方式 | 存取類型 |
| --- | --- | --- |
| `index` | 萬用字元，區分大小寫；索引或資料串流的名稱 | `index`、`delete`、`create_index`、`delete_index`、`view_index_metadata`、`monitor`、`manage` |
| `field` | 萬用字元，區分大小寫；位於索引之下，欄位路徑如 `address.city` | `read` |
| `cluster` | 萬用字元，區分大小寫；叢集名稱 | `cluster_monitor`、`create_snapshot`、`manage_slm`、`manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` |

每種存取類型都是同名的 Elasticsearch 權限，只有 `cluster_monitor` 對應叢集權限 `monitor`：

| 存取類型 | 允許的操作 |
| --- | --- |
| `read` | 搜尋、取得、計數、多重搜尋與捲動查詢 |
| `index`、`delete` | 寫入與更新文件（包括 bulk）；刪除文件 |
| `create_index`、`delete_index` | 建立索引，包括首次寫入時自動建立；刪除索引與資料串流 |
| `view_index_metadata`、`monitor` | 讀取對應、設定與別名；讀取統計與復原資訊 |
| `manage` | 索引的所有管理操作；隱含 `create_index`、`delete_index`、`view_index_metadata` 與 `monitor` |
| `cluster_monitor` | 讀取叢集的健康、狀態與統計 |
| `create_snapshot`、`manage_slm` | 建立快照；管理快照生命週期策略，隱含 `create_snapshot` |
| `manage_index_templates`、`manage_ingest_pipelines`、`manage_ilm` | 管理索引範本、擷取管線與索引生命週期策略 |

讀取依欄位決定，正如欄位層級安全呈現文件欄位那樣：`read` 策略寫明欄位，`*` 表示所有欄位。因此一條策略可以允許某個索引，同時拒絕其中一個欄位，使用者仍能讀取其餘所有欄位。策略值中，`*` 比對任意多個字元，`?` 比對一個字元。策略編輯器會建議索引與資料串流、策略所寫索引的欄位，以及叢集。

### 列篩選

索引的列篩選策略只讓使用者看到查詢符合的文件。查詢採用文件層級安全所接受的 Elasticsearch 查詢 DSL，例如 `{"term": {"region": "EU"}}`，也可以是範本，例如使用 `{{_user.username}}`。與其他服務一樣，使用者取得第一條適用於它的策略的篩選條件。列篩選只能檢查使用者可讀的欄位：欄位層級安全同樣會對查詢隱藏其他欄位。

Elasticsearch 不會遮罩值，因此此服務類型不提供遮罩。

### 授權

Elasticsearch 從 Platinum 授權起提供文件層級與欄位層級安全。低於此等級時，只允許使用者讀取索引部分欄位或部分文件的策略無法執行，因此代理不會授予該使用者對該索引的讀取，而不是給出超出策略的權限，並說明原因。允許讀取整個索引、寫入與管理的策略在任何授權下都有效。

## 策略範例

假設叢集持有 Platinum 授權，並有索引 `logs-2026.10.26` 與 `hr`。在同一個資料服務中建立以下啟用的策略：

| 資源 | 規則 |
| --- | --- |
| `index=logs-*`、`field=*` | 允許群組 `analysts` `read` |
| `index=logs-*`、`field=client_ip` | 拒絕群組 `analysts` `read` |
| `index=logs-*` | 允許使用者 `logstash` `index` 與 `create_index` |
| `index=hr`、`field=*` | 允許群組 `hr` `read` |
| `index=hr`（列篩選） | 只讓使用者 `emea-hr` 看到 `{"term": {"region": "EMEA"}}` 的文件 |
| `cluster=*` | 允許群組 `ops` `cluster_monitor` |

代理套用新快照後，`analysts` 的成員能讀取除用戶端位址外的所有日誌欄位；`logstash` 能立即寫入明天的 `logs-2026.10.27`，並以第一份文件建立它；`hr` 的成員能讀取員工資料；`emea-hr` 只看到本區域的員工；`ops` 的成員能讀取叢集的健康與統計。

## 執行策略

服務外掛管理策略並查詢資源；執行策略需要為每個叢集部署一個 Elasticsearch 代理。部署與檢查見 [Elasticsearch 代理](/zh-tw/external/elasticsearch-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Elasticsearch 代理](/zh-tw/external/elasticsearch-agent/)
- [外掛與服務類型](/zh-tw/develop/plugins/)
