---
title: Trino
description: 安裝並設定 Trino 服務外掛，透過 TLS 以密碼、JSON Web Token 或 Kerberos 登入，查詢目錄、結構描述、資料表、欄位與工作階段屬性，並依 Trino 自己的概念管理存取、列篩選與遮罩策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Trino 外掛查詢目錄（catalog）、結構描述（schema）、資料表、欄位與工作階段屬性，並針對 Trino 所檢查的對象管理存取、列篩選與遮罩策略：資料、常式、工作階段屬性、使用者與系統資訊。外掛 ID 與服務類型皆為 `trino`。服務外掛執行於 GrantForge 伺服器中；安裝在 coordinator 中的 [Trino 代理](/zh-tw/external/trino-agent/)負責執行策略。

儲存庫的 Testcontainers 整合測試涵蓋 Trino `470`、`476`、`479` 與 `484`，並在 `470` 與 `484` 上涵蓋使用密碼檔與 JSON Web Token 的 HTTPS，以及 Kerberos。這並不代表對其他所有版本的認證。

## 安裝

發行套件在 `plugins/trino` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `trino` 已啟用；更新外掛目錄後重新掃描。外掛透過 Trino 自己的 JDBC 驅動程式（`trino-jdbc` 484）與 coordinator 通訊，驅動程式位於外掛自己的類別載入器中，可連線上述所有 Trino 版本。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Trino（`trino`）。
2. 輸入 coordinator 的 JDBC URL，例如 `jdbc:trino://trino.example.com:8443`。
3. 選擇查詢使用者的登入方式並填寫，測試連線後儲存服務。測試會登入並描述 coordinator：它的版本，以及該使用者能看到多少個目錄。

設定沿用驅動程式自己的名稱，與 Trino 的 JDBC 文件一致。查詢使用者用於讓 GrantForge 探索資源，與執行查詢的使用者彼此獨立。連線測試成功並不表示查詢已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `jdbc.url` | 必填：`jdbc:trino://host:port`，可帶目錄與結構描述，以及憑證以外的驅動程式屬性，例如 `?SSL=true` |
| `authentication` | `none`（預設）：只用使用者名稱；`password`；`jwt`：JSON Web Token；`kerberos`：帶 keytab 或密碼的主體 |
| `user` | 必填的查詢使用者；Kerberos 下為完整主體，例如 `grantforge@EXAMPLE.COM` |
| `password` | 使用者的密碼；Kerberos 下為主體的密碼（未提供 keytab 時）；加密儲存 |
| `accessToken` | `jwt`：coordinator 接受的權杖；加密儲存 |
| `keytab` | Kerberos：主體的 keytab 路徑，須在 **GrantForge 伺服器** 上可讀 |
| `kerberos.kdc` | Kerberos：主體所在 realm 的 KDC，`host[:port]`，以逗號分隔；空白時使用伺服器的 `krb5.conf` |
| `KerberosRemoteServiceName` | Kerberos：coordinator 主體的第一部分，預設 `trino` |
| `KerberosUseCanonicalHostname` | Kerberos：coordinator 的主體是否使用 URL 主機的標準名稱，預設 `true`；位於負載平衡器或別名之後且主體帶有該名稱時關閉 |
| `SSL` | 是否使用 TLS，預設 `false`；除 `none` 外的任何登入方式都需要它，因為 Trino 只透過 TLS 登入使用者 |
| `SSLTrustStorePath`、`SSLTrustStorePassword`、`SSLTrustStoreType` | TLS：用於驗證 coordinator 的信任庫、其密碼與類型（`JKS` 或 `PKCS12`）；空白時信任 Java 信任的憑證 |
| `SSLVerification` | TLS：`FULL`（預設）檢查憑證與主機名稱，`CA` 只檢查憑證，`NONE` 什麼都不檢查且不接受信任庫 |
| `timeout` | 請求逾時（毫秒），預設 `30000`，範圍 `1..600000` |

服務完全由其欄位決定。URL 中不能攜帶憑證或其他登入方式：`user`、`password`、`accessToken`、`sessionUser`、`extraCredentials`、外部與 OAuth 2.0 驗證、Kerberos 檔案與委派，以及金鑰庫與信任庫密碼都會被拒絕。其他登入方式的欄位也會被拒絕，例如在密碼驗證下填寫權杖。

無法驗證憑證的 coordinator 不會被連線：測試會回報其無法連線。登入被拒、權限不足與 coordinator 無法連線都會如實回報。

### Kerberos

使用 `kerberos` 時，GrantForge 從 keytab 或以密碼登入主體，並把這次登入的憑證交給驅動程式；GrantForge 伺服器上的其他部分無需 Kerberos 設定。在 `kerberos.kdc` 中填寫了 KDC 的 realm 會為整個 GrantForge 伺服器宣告，與 HDFS、Hive、Kafka 和 HBase 外掛的做法相同，因此多個服務可以並行使用不同的 realm。工作階段使用者為主體中 realm 之前的名稱，與 Trino 預設的主體對應一致。

### 查詢使用者能看到什麼

在 [Trino 代理](/zh-tw/external/trino-agent/)執行策略後，清單會省略查詢使用者無權使用的內容：為它新增一條在目錄 `*` 上允許 `select` 的策略，或在希望策略編輯器提示其資源的目錄上允許。

## 資源與存取類型

| 層級 | 比對 | 存取類型 |
| --- | --- | --- |
| `catalog` | 萬用字元，不區分大小寫 | `select`、`insert`、`update`、`delete`、`create`、`drop`、`alter`、`all` |
| `schema` | 萬用字元，不區分大小寫；位於目錄之下 | 同 `catalog` |
| `table` | 萬用字元，不區分大小寫；位於結構描述之下；包括資料表、檢視與具體化檢視 | 同 `catalog` |
| `column` | 萬用字元，不區分大小寫；位於資料表之下 | `select`、`update` |
| `function` | 萬用字元，不區分大小寫；位於結構描述之下 | `execute`、`create_function`、`drop_function` |
| `procedure` | 萬用字元，不區分大小寫；位於結構描述之下 | `execute` |
| `session_property` | 萬用字元，不區分大小寫；位於目錄之下，名稱不含目錄 | `set` |
| `system_session_property` | 萬用字元，不區分大小寫 | `set` |
| `user` | 萬用字元，區分大小寫 | `impersonate`、`view_query`、`kill_query` |
| `system_information` | 唯一的值 `*` | `read`、`write` |

`all` 隱含 `select`、`insert`、`update`、`delete`、`create`、`drop` 與 `alter`。

目錄、結構描述或資料表上的權限涵蓋其中的一切：結構描述 `sales` 上的 `select` 可讀取其中每個資料表，目錄上的 `create` 可在其中建立結構描述與資料表。一次檢查會在它涉及的每個範圍上判定，任一範圍上的拒絕都會拒絕它，因此一條策略可以允許一個結構描述，同時拒絕其中某個資料表的某個欄位。函式、程序與工作階段屬性依名稱授權，以 `*` 表示結構描述或目錄中的全部；函式有自己的存取類型，因此對常式的授權絕不會被當作對資料表的授權。

策略值中，`*` 比對任意多個字元，`?` 比對一個字元。策略編輯器會提示目錄、策略所列目錄中的結構描述、其結構描述中的資料表、其資料表中的欄位，以及其目錄與系統的工作階段屬性；函式、程序與使用者不會被提示。

## 列篩選與遮罩

資料表上的列篩選策略與欄位上的遮罩策略都是 Trino SQL，coordinator 會將其套用到讀取它們的每個查詢。列篩選是資料表欄位上的條件，例如 `region = 'EU'`；其中的名稱在該資料表的目錄與結構描述中解析。

| 遮罩類型 | 顯示 |
| --- | --- |
| `mask` | 字母顯示為 `X` 或 `x`，數字顯示為 `n` |
| `mask_show_last_4`、`mask_show_first_4` | 只顯示最後或最前四個字元 |
| `mask_hash` | 值的 SHA-256，十六進位 |
| `mask_null` | `NULL` |
| `mask_none` | 原值，不遮罩 |
| `mask_date_show_year` | 只顯示日期或時間戳記的年份 |
| `custom` | 策略中的 Trino SQL 運算式，以 `{col}` 表示該欄位 |

代理會讓每種遮罩配合欄位的類型，使遮罩不會破壞讀取該欄位的查詢：請參閱 [Trino 代理](/zh-tw/external/trino-agent/)。

## 策略範例

假設目錄 `hive` 中有結構描述 `sales`，其中有資料表 `orders`（欄位包括 `region`、`customer` 與 `card`）和 `returns`。在同一資料服務中建立以下已啟用的策略：

| 策略 | 資源 | 規則 |
| --- | --- | --- |
| 存取 | `catalog=hive`、`schema=sales` | 為群組 `analysts` 允許 `select` |
| 存取 | `catalog=hive`、`schema=sales`、`table=orders`、`column=card` | 為群組 `analysts` 拒絕 `select` |
| 存取 | `catalog=hive`、`schema=sales`、`table=returns` | 為使用者 `alice` 允許 `insert` |
| 列篩選 | `catalog=hive`、`schema=sales`、`table=orders` | 為使用者 `alice` 設定 `region = 'EU'` |
| 遮罩 | `catalog=hive`、`schema=sales`、`table=orders`、`column=customer` | 為群組 `analysts` 設定 `mask_hash` |
| 存取 | `catalog=system`、`schema=runtime` | 為使用者 `alice` 允許 `select` |
| 存取 | `user=bob` | 為使用者 `alice` 允許 `view_query` |

代理套用新快照後，`analysts` 的成員可以讀取 `sales` 中除 `orders.card` 之外的每個資料表，看到的 `customer` 是雜湊值；`alice` 只看到歐洲的訂單，可以寫入 `returns`，並在 `system.runtime.queries` 中除了自己的查詢外還能看到 Bob 的查詢。

## 執行策略

服務外掛負責管理策略與查詢資源。執行策略需要在 coordinator 中安裝 Trino 代理。部署方式與檢查內容請參閱 [Trino 代理](/zh-tw/external/trino-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Trino 代理](/zh-tw/external/trino-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
