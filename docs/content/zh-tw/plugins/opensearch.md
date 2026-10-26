---
title: OpenSearch
description: 安裝並設定 OpenSearch 服務外掛，連線一個或多個節點，以密碼或 TLS 用戶端憑證登入，查詢索引、資料串流、欄位與叢集，並管理誰可以讀取、寫入與管理什麼，細到欄位、遮罩的值與文件。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

OpenSearch 外掛查詢索引、資料串流、欄位與叢集，並依 OpenSearch 自己的方式管理存取策略：讀取欄位，寫入、刪除、建立與管理索引，以及叢集本身的操作，並支援欄位遮罩與索引的列篩選。外掛 ID 與服務類型皆為 `opensearch`。服務外掛執行於 GrantForge 伺服器中；執行在叢集旁的 [OpenSearch 代理](/zh-tw/external/opensearch-agent/)藉由安全外掛自己的角色、欄位層級安全、遮罩欄位與文件層級安全執行策略。

儲存庫的 Testcontainers 整合測試涵蓋帶有安全外掛的 OpenSearch `2.19`、`3.3`、`3.6` 與 `3.9`；這不代表認證了其他所有版本。

## 安裝

發行套件在 `plugins/opensearch` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `opensearch` 已啟用；更新外掛目錄後重新掃描。外掛透過 OpenSearch 官方的低階 REST 用戶端與節點通訊，位於外掛自己的類別載入器中。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 OpenSearch（`opensearch`）。
2. 以 URL 輸入一個節點或多個節點。
3. 選擇查詢使用者的登入方式，填寫憑證，測試連線後儲存服務。測試會登入並描述叢集：OpenSearch 版本與叢集名稱、登入所用的使用者，以及叢集有多少個索引與資料串流。沒有安全外掛的叢集測試會失敗，因為代理藉由安全外掛執行策略。

查詢使用者用於讓 GrantForge 探索資源，與讀寫資料的使用者彼此獨立。連線測試成功並不表示存取已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `opensearch.hosts` | 必填：以 URL 表示的節點，例如 `https://opensearch1.example.com:9200`；多個以逗號分隔，某個無法連線時依序嘗試下一個。未寫協定時為 `https`，連接埠預設為 `9200`；同一服務的節點要麼都是 `http`，要麼都是 `https` |
| `authentication` | `password`（預設），或 `certificate`：安全外掛對應到某個使用者的 TLS 用戶端憑證 |
| `username`、`password` | password：查詢使用者及其密碼，密碼加密儲存 |
| `ssl.verificationMode` | 與 OpenSearch Dashboards 的命名一致：`full`（預設）驗證節點的憑證與名稱，`certificate` 只驗證憑證，`none` 不驗證 |
| `ssl.certificateAuthorities` | **GrantForge 伺服器**上用來驗證節點的 PEM CA 憑證路徑；空白時使用 JVM 的 |
| `ssl.certificate`、`ssl.key` | certificate：GrantForge 伺服器上 PEM 用戶端憑證及其私密金鑰的路徑，私密金鑰可為 PKCS #8 或較舊的 OpenSSL 格式；需要 `https` 節點 |
| `ssl.keyPassphrase` | certificate：加密私密金鑰的密碼短語，加密儲存 |
| `timeout` | 連線與請求可用的時間，預設 `30000`，範圍 `1..600000` 毫秒 |

一種登入方式的設定在另一種方式下會被拒絕，因此服務會明確說明如何登入。被拒絕的登入、缺少的權限、無法連線的節點以及無法驗證憑證的節點，都會如實回報。

### 查詢使用者能看到什麼

查詢使用者需要能讀取它要列出的內容：索引與資料串流來自 OpenSearch 的 resolve API，欄位來自欄位能力（field capabilities），叢集名稱來自叢集的描述。內建的 `readall` 角色提供前兩項；沒有 `cluster:monitor/main` 的使用者仍能通過連線測試，只是結果不寫版本。名稱以點開頭的索引與資料串流，即隱藏與系統索引，只在輸入以點開頭時列出；資料串流的後備索引以其資料串流列出。欄位是文件的欄位：OpenSearch 自己的中繼資料欄位、物件以及再次索引同一欄位的多重欄位（例如 `name.keyword`）不會列出，因為欄位層級安全會隨欄位一同顯示或隱藏它們。

## 資源與存取類型

| 層級 | 比對 | 存取類型 |
| --- | --- | --- |
| `index` | 萬用字元，區分大小寫；索引或資料串流的名稱 | `index`、`delete`、`create_index`、`manage_aliases`、`monitor`、`manage` |
| `field` | 萬用字元，區分大小寫；位於索引之下，欄位的路徑，例如 `address.city` | `read` |
| `cluster` | 萬用字元，區分大小寫；叢集的名稱 | `cluster_monitor`、`manage_snapshots`、`manage_templates`、`manage_pipelines` |

| 存取類型 | 安全外掛的動作群組 |
| --- | --- |
| `read` | `read`：搜尋、取得、計數等 |
| `index` | `index`：寫入與更新文件，包括透過 bulk |
| `delete` | `delete`：刪除文件 |
| `create_index` | `create_index`：建立索引，包括首次寫入時自動建立 |
| `manage_aliases`、`monitor` | `manage_aliases`、`indices_monitor` |
| `manage` | `manage`：索引的所有管理操作，包括刪除索引；它隱含 `create_index`、`manage_aliases` 與 `monitor` |
| `cluster_monitor`、`manage_snapshots` | `cluster_monitor`、`manage_snapshots` |
| `manage_templates`、`manage_pipelines` | `cluster_manage_index_templates`、`cluster_manage_pipelines` |

讀取依欄位決定，與欄位層級安全顯示文件欄位的方式一致：`read` 策略要寫欄位，`*` 表示所有欄位。因此策略可以允許一個索引而拒絕其中一個欄位，使用者能讀取其餘所有欄位。策略值中 `*` 比對任意字元序列，`?` 比對一個字元。策略編輯器會提示索引與資料串流、策略所寫索引的欄位，以及叢集。

在 OpenSearch 中使用策略允許的內容還需要更多權限：讀取需要多重搜尋、多重取得與捲動查詢，寫入需要 bulk。它們不是獨立的存取類型，代理會隨之授予叢集的組合操作。

### 遮罩

欄位的遮罩策略讓使用者看到遮罩後的值，與安全外掛的遮罩欄位一致，對文件與彙總都有效：

| 遮罩方式 | 使用者看到的內容 |
| --- | --- |
| 遮蔽（Redact） | 大寫字母顯示為 `X`，小寫字母為 `x`，數字為 `n` |
| 部分遮蔽：顯示後 4 位 / 前 4 位 | 其餘每個字元顯示為 `x` |
| 雜湊（SHA-256） | 值的 SHA-256 雜湊 |
| 帶叢集鹽值的雜湊（BLAKE2b） | 安全外掛自己的雜湊，以叢集的鹽值作為金鑰，相同的值仍然相同，但無法反查 |
| 自訂 | 一種雜湊演算法，例如 `SHA-512`，或正規表示式及其替換內容，例如 `/[0-9]/::*` |
| 不遮罩 | 原值 |

### 列篩選

索引的列篩選策略只讓使用者看到某個查詢比對到的文件。查詢是文件層級安全所接受的 OpenSearch 查詢 DSL，例如 `{"term": {"region": "EU"}}`，也可以使用安全外掛的替換變數，例如 `${user.name}`。與其他服務相同，使用者看到的是第一條適用於他的策略的篩選。列篩選只能檢驗使用者可以讀取的欄位：欄位層級安全對查詢同樣隱藏其他欄位。

## 策略範例

假設叢集有索引 `logs-2026.10.26` 與 `hr`。在同一個資料服務中建立以下已啟用的策略：

| 資源 | 規則 |
| --- | --- |
| `index=logs-*`、`field=*` | 允許群組 `analysts` `read` |
| `index=logs-*`、`field=client_ip` | 拒絕群組 `analysts` `read` |
| `index=logs-*` | 允許使用者 `logstash` `index` 與 `create_index` |
| `index=hr`、`field=*` | 允許群組 `hr` `read` |
| `index=hr`、`field=salary`（遮罩） | 讓群組 `hr` 看到帶叢集鹽值的雜湊 |
| `index=hr`（列篩選） | 讓使用者 `emea-hr` 只看到 `{"term": {"region": "EMEA"}}` 的文件 |
| `cluster=*` | 允許群組 `ops` `cluster_monitor` |

代理套用新快照後，`analysts` 的成員可以讀取除了用戶端位址以外的所有日誌欄位；`logstash` 可以立即寫入明天的 `logs-2026.10.27`，並由第一份文件建立它；`hr` 的成員看到的薪資是雜湊值；`emea-hr` 只看到本地區的員工；`ops` 的成員可以讀取叢集的健康狀態與統計資訊。

## 執行策略

服務外掛負責管理策略與查詢資源；執行策略需要每個叢集一個 OpenSearch 代理。部署與檢查見 [OpenSearch 代理](/zh-tw/external/opensearch-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [OpenSearch 代理](/zh-tw/external/opensearch-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
