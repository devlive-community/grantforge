---
title: Apache Solr
description: 安裝並設定 Solr 服務外掛，連線一個或多個節點，以 basic 驗證或 JSON Web Token 登入，查詢集合及其請求路徑，並管理誰可以查詢、更新與管理什麼。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Solr 外掛查詢集合及其中的請求路徑，並針對 Solr 所決定的事情管理存取策略：查詢、更新、讀取與修改 schema 和 config、管理集合以及管理叢集。外掛 ID 與服務類型皆為 `solr`。服務外掛執行於 GrantForge 伺服器中；安裝在節點中的 [Solr 代理](/zh-tw/external/solr-agent/)負責執行策略。

儲存庫的 Testcontainers 整合測試涵蓋 Solr `9.4.1`、`9.8.1`、`9.11.0` 與 `10.0.0` 的 SolrCloud 節點；這不代表認證了其他所有版本。

## 安裝

發行套件在 `plugins/solr` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `solr` 已啟用；更新外掛目錄後重新掃描。外掛透過 SolrJ 9.11 的 JDK HTTP 用戶端與節點通訊，位於外掛自己的類別載入器中。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Apache Solr（`solr`）。
2. 輸入一個節點或多個節點的基礎 URL。
3. 選擇查詢使用者的登入方式並填寫憑證，測試連線後儲存服務。測試會登入並描述叢集：Solr 版本、是否執行 SolrCloud、有多少個集合。

查詢使用者用於讓 GrantForge 探索資源，與查詢和更新的使用者彼此獨立。連線測試成功並不表示存取已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `solr.urls` | 必填：節點的基礎 URL，例如 `https://solr1.example.com:8983/solr`；多個以逗號分隔，某個節點無法連線時依序嘗試下一個 |
| `authentication` | `none`（預設）、`basic` 或 `jwt` |
| `username`、`password` | basic：查詢使用者及其密碼；密碼加密儲存 |
| `token` | jwt：查詢使用者的 JSON Web Token；加密儲存 |
| `javax.net.ssl.trustStore`、`javax.net.ssl.trustStorePassword`、`javax.net.ssl.trustStoreType` | https：**GrantForge 伺服器** 上用於驗證節點憑證的信任庫、其密碼與類型，預設 `PKCS12`；留空時使用 JVM 內建的 |
| `timeout` | 請求可用的時長，預設 `30000`，範圍 `1..600000` 毫秒 |

一種登入方式的設定在另一種方式下會被拒絕，因此服務明確寫出它如何登入。登入被拒、使用者無權檢視，以及節點無法連線（包括其前方的代理替它回應時）都會如實回報。

### 查詢使用者能看到什麼

列出集合是 Solr 的 `collection-admin-read` 權限，集合的請求路徑來自其 config，即 `config-read`。請在 Solr 自己的規則中授予查詢使用者這些權限，或在安裝代理後授予它叢集上的 `admin_read` 與集合上的 `config_read`。查詢使用者無權檢視的集合的路徑不會出現在提示中。未執行 SolrCloud 的節點擁有的是 core 而非集合，外掛把它們當作集合列出。

## 資源與存取類型

| 層級 | 比對 | 存取類型 |
| --- | --- | --- |
| `collection` | 萬用字元，區分大小寫；集合的名稱 | `query`、`update`、`schema_read`、`schema_edit`、`config_read`、`config_edit`、`admin_collection` |
| `path` | 萬用字元，區分大小寫；位於集合之下；請求路徑，例如 `/select`、`/export` 或 `/update/json` | `query`、`update`、`schema_read`、`schema_edit`、`config_read`、`config_edit` |
| `cluster` | 唯一的值 `*` | `health`、`metrics`、`admin_read`、`security`、`admin` |

| 存取類型 | 對應 Solr 權限 | 隱含 |
| --- | --- | --- |
| `query` | `read`：搜尋、取得、匯出 | — |
| `update` | `update` | — |
| `schema_read`、`schema_edit` | `schema-read`、`schema-edit` | `schema_edit` 隱含 `schema_read` |
| `config_read`、`config_edit` | `config-read`、`config-edit` | `config_edit` 隱含 `config_read` |
| `admin_collection` | 指名該集合的 Collections API 請求的 `collection-admin-read` 與 `collection-admin-edit`：建立、重新載入、備份、刪除等 | `query`、`update`、`schema_edit`、`config_edit` |
| `health`、`metrics` | `health`、`metrics-read` | — |
| `admin_read` | 整個叢集的 `collection-admin-read`、`core-admin-read`、`zk-read`、`filestore-read`、`package-read`，以及讀取節點的 config 與 schema | `health`、`metrics` |
| `security` | `security-read`、`security-edit` | — |
| `admin` | 一切 | `admin_read`、`security`、`admin_collection` |

對集合的請求會在集合以及「集合加請求路徑」上判定，任一處拒絕都會拒絕它；因此一條策略可以允許一個集合而拒絕其 `/export` 處理器，或只允許透過 `/update*` 更新。涉及多個集合的請求需要每個集合都允許。在集合之下，請求路徑就是 Solr 把請求路由到的處理器，名稱與 Solr 一致。

透過 `name` 或 `collection` 參數指名集合的 Collections API 請求，需要每個被指名集合上的 `admin_collection`；因此 `team-*` 上的策略讓一個團隊可以建立名稱以 `team-` 開頭的集合，並重新載入、備份或刪除它們，但不能操作其他集合。不指名集合的請求（例如列出所有集合）屬於叢集。叢集上的 `admin` 授予一切，與 Solr 自己的 `all` 權限相同。策略值中，`*` 比對任意多個字元，`?` 比對一個字元。策略編輯器會提示集合，以及策略所列集合的請求路徑。

### 用戶端位址

策略項目可以只對部分用戶端生效：其 **用戶端位址** 條件（`ip-range`）接受位址以及 `10.1.0.0/16` 或 `2001:db8::/32` 這樣的網段。不會解析主機名稱。在反向代理之後，用戶端就是該反向代理，除非 Solr 代理信任它：此時用戶端是 `X-Forwarded-For` 中最後一個不是由受信任反向代理加入的位址，因此用戶端無法冒充任意位址。

## 策略範例

假設叢集有集合 `sales` 與 `hr`。在同一資料服務中建立以下已啟用的存取策略：

| 資源 | 規則 |
| --- | --- |
| `collection=sales` | 為群組 `analysts` 允許 `query` |
| `collection=sales`、`path=/export` | 為使用者 `alice` 拒絕 `query` |
| `collection=sales`、`path=/update*` | 為使用者 `ingest` 允許 `update` |
| `collection=team-*` | 為群組 `search-team` 允許 `admin_collection` |
| `cluster=*` | 為使用者 `monitoring` 允許 `metrics` 與 `health` |
| `collection=hr` | 為群組 `hr-staff` 允許 `query`，限 `10.0.0.0/8` |
| `cluster=*` | 為群組 `solr-admins` 允許 `admin` |

代理套用新快照後，`analysts` 的成員可以查詢 `sales`，`alice` 除匯出處理器外都可以；`ingest` 可以透過更新處理器更新 `sales`，但不能查詢；`search-team` 的成員可以建立並管理自己的 `team-` 集合；`monitoring` 可以讀取指標與健康狀態；`hr-staff` 的成員只能從內網查詢 `hr`；`solr-admins` 的成員可以做任何事。

## 執行策略

服務外掛負責管理策略與查詢資源。執行策略需要在每個節點中安裝 Solr 代理。部署方式與檢查內容見 [Solr 代理](/zh-tw/external/solr-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Solr 代理](/zh-tw/external/solr-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
