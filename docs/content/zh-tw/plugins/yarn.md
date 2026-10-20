---
title: Apache Hadoop YARN
description: 安裝並設定 YARN 服務外掛，連線一個 ResourceManager 或高可用的一組，以 simple 或 Kerberos 登入，查詢佇列與應用程式名稱，並管理誰可以把哪些應用程式提交到哪裡。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

YARN 外掛查詢佇列及其中的應用程式名稱，並針對 YARN 所決定的事情管理存取策略：向佇列提交應用程式、管理佇列以及管理叢集。外掛 ID 與服務類型皆為 `yarn`。服務外掛執行於 GrantForge 伺服器中；安裝在 ResourceManager 中的 [YARN 代理](/zh-tw/external/yarn-agent/)負責執行策略。

儲存庫的 Testcontainers 整合測試涵蓋由經 SHA-512 驗證的 Apache 發行套件建置的 Hadoop `3.2.4`，以及 `3.3.6`、`3.4.3`、`3.5.0` 的 ResourceManager，還有一個 `3.4.3` 的 Kerberos ResourceManager；這並不代表對其他所有版本的認證。

## 安裝

發行套件在 `plugins/yarn` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `yarn` 已啟用；更新外掛目錄後重新掃描。外掛透過 Hadoop 自己的 YARN 用戶端與 ResourceManager 通訊，基於 Hadoop 的 shaded 用戶端 3.5.0，位於外掛自己的類別載入器中。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Apache Hadoop YARN（`yarn`）。
2. 輸入 ResourceManager 的用戶端位址，或高可用叢集中每個 ResourceManager 的位址。
3. 選擇查詢使用者的登入方式並填寫，測試連線後儲存服務。測試會登入並描述叢集：有多少個 NodeManager、多少個佇列。

設定沿用 Hadoop 自己的名稱，可以直接從 `yarn-site.xml` 複製。查詢使用者用於讓 GrantForge 探索資源，與提交應用程式的使用者彼此獨立。連線測試成功並不表示提交已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `yarn.resourcemanager.address` | 必填：ResourceManager，`host` 或 `host:port`，預設連接埠 `8032`；高可用叢集填寫多個並以逗號分隔，用戶端會在它們之間容錯移轉 |
| `hadoop.security.authentication` | `simple`（預設）：使用者名稱；`kerberos`：帶 keytab 或密碼的主體 |
| `username` | 必填的查詢使用者；Kerberos 下為完整主體，例如 `grantforge@EXAMPLE.COM` |
| `password` | Kerberos：主體的密碼（未提供 keytab 時）；加密儲存 |
| `keytab` | Kerberos：主體的 keytab 路徑，須在 **GrantForge 伺服器** 上可讀 |
| `kerberos.kdc` | Kerberos：主體所在 realm 的 KDC，`host[:port]`，以逗號分隔；空白時使用伺服器的 `krb5.conf` |
| `yarn.resourcemanager.principal` | Kerberos：ResourceManager 的主體，預設 `yarn/_HOST@` 加查詢主體的 realm |
| `hadoop.rpc.protection` | `authentication`（預設）、`integrity` 或 `privacy`，與叢集一致 |
| `timeout` | 請求可用的時長，預設 `30000`，範圍 `1..600000` 毫秒；YARN 自己的用戶端會重試十五分鐘 |

服務完全由其設定決定：不會讀取 GrantForge 伺服器上的 `core-site.xml` 或 `yarn-site.xml`。simple 驗證下會拒絕 Kerberos 設定；Kerberos 需要密碼或 keytab。拒絕登入的 ResourceManager（例如要求 Kerberos 的）會回報為登入被拒。

### Kerberos

使用 `kerberos` 時，GrantForge 透過 Hadoop 自己的登入從 keytab 或以密碼登入主體，保留這次登入並以 keytab 續期；GrantForge 伺服器上的其他部分無需 Kerberos 設定。在 `kerberos.kdc` 中填寫了 KDC 的 realm 會為整個 GrantForge 伺服器宣告，與 HDFS、Hive、Kafka、HBase 和 Trino 外掛的做法相同，因此多個服務可以並行使用不同的 realm。

## 資源與存取類型

| 層級 | 比對 | 存取類型 |
| --- | --- | --- |
| `queue` | 萬用字元，區分大小寫；從根開始的佇列路徑，例如 `root.sales.etl` | `submit_app`、`admin_queue` |
| `application` | 萬用字元，區分大小寫；位於佇列之下；應用程式的名稱 | `submit_app` |
| `cluster` | 唯一的值 `*` | `admin`：YARN 的管理操作，例如重新整理佇列、節點與節點標籤 |

`admin_queue` 隱含 `submit_app`，因為佇列的管理員可以向其提交。

與 YARN 一樣，佇列上的權限涵蓋其下的佇列：`root.sales` 上的 `submit_app` 可以提交到 `root.sales.etl`。一次檢查會在路徑上的每個佇列判定（從根開始），任一處拒絕都會拒絕它，因此一條策略可以允許 `root.sales`，同時拒絕 `root.sales.adhoc`。在佇列之下，應用程式層級指明授權針對哪些應用程式：`root.hr` 上帶應用程式 `payroll-*` 的 `submit_app` 只接受名稱以 `payroll-` 開頭的應用程式，作用於 `root.hr` 及其下的佇列。

策略值中，`*` 比對任意多個字元，`?` 比對一個字元。無論叢集執行哪種排程器，策略編輯器都會依路徑（若輸入的是佇列自己的名稱，則依名稱）提示佇列，並提示 ResourceManager 仍記得的、策略所列佇列中的應用程式名稱。

### 用戶端位址

策略項目可以只對部分用戶端生效：其 **用戶端位址** 條件（`ip-range`）接受位址、`10.1.0.0/16` 這樣的網段以及主機名稱，與 Hadoop 自己的主機清單一致。在代理（例如 YARN 的 web proxy 或 router）之後，用戶端是代理轉發的第一個位址。位址與網段優於主機名稱，主機名稱需要對用戶端做一次名稱查詢。YARN 詢問管理權限時不帶用戶端位址，因此該條件在 `cluster` 上永不成立。

## 策略範例

假設叢集執行佇列 `root.sales.etl`、`root.sales.adhoc` 與 `root.hr`。在同一資料服務中建立以下已啟用的存取策略：

| 資源 | 規則 |
| --- | --- |
| `queue=root.sales` | 為群組 `analysts` 允許 `submit_app` |
| `queue=root.sales.adhoc` | 為使用者 `alice` 拒絕 `submit_app` |
| `queue=root.hr`、`application=payroll-*` | 為使用者 `bob` 允許 `submit_app` |
| `queue=root.sales` | 為使用者 `carol` 允許 `admin_queue` |
| `queue=root` | 為群組 `etl-robots` 允許 `submit_app`，限 `10.0.0.0/8` |
| `cluster=*` | 為群組 `yarn-admins` 允許 `admin` |

代理套用新快照後，`analysts` 的成員可以提交到 `root.sales.etl` 與 `root.sales.adhoc`，但 `alice` 只能提交到 `root.sales.etl`；`bob` 只能向 `root.hr` 提交薪資類應用程式；`carol` 可以向 sales 下的佇列提交並終止其中的應用程式；`etl-robots` 的成員可以提交到任何佇列，但只能從內網提交；`yarn-admins` 的成員可以重新整理佇列。

## 執行策略

服務外掛負責管理策略與查詢資源。執行策略需要在每個 ResourceManager 中安裝 YARN 代理。部署方式與檢查內容請參閱 [YARN 代理](/zh-tw/external/yarn-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [YARN 代理](/zh-tw/external/yarn-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
