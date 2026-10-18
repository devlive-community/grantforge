---
title: Apache Kafka
description: 安裝與設定 Kafka 服務外掛，透過明文或 TLS 以 PLAIN、SCRAM 或 Kerberos 登入，查詢主題、消費者群組、交易 ID 與使用者，並以 Kafka 自己的概念管理存取策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Kafka 外掛查詢主題、消費者群組、交易 ID、SCRAM 使用者與委派權杖，並以 Kafka 自己的概念管理存取策略：資源層級就是 Kafka 的資源類型，存取類型就是它的 ACL 操作。外掛 ID 與服務類型皆為 `kafka`。服務外掛執行於 GrantForge 伺服器中；安裝在 broker 與 controller 中的 [Kafka 代理](/zh-tw/external/kafka-agent/)負責執行策略。

儲存庫的 Testcontainers 整合測試涵蓋 Kafka `3.7.2`、`3.9.2`、`4.1.2` 與 `4.3.1`，使用 SASL PLAIN、SCRAM 登入與 Kafka 內建的授權器，並在 `4.3.1` 上涵蓋 Kerberos（keytab 與密碼）和 TLS；這並不代表對其他所有版本的認證。

## 安裝

發行套件在 `plugins/kafka` 中包含此外掛。請在 **平台管理 → 外掛** 中確認 `kafka` 已啟用；更新外掛目錄後請重新掃描。外掛在獨立的類別載入器中透過 Kafka 自己的 Admin 用戶端（`kafka-clients` 4.3.1）連線 broker。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Apache Kafka（`kafka`）。
2. 填寫啟動伺服器，以及 broker 監聽器的安全方式。
3. 填寫查詢使用者，測試連線後儲存服務。測試會登入並描述叢集：叢集 ID 與 broker 數量。

設定沿用 Kafka 用戶端自己的名稱，可以直接從用戶端設定中複製。查詢使用者只用於讓 GrantForge 探索資源，與生產和消費的用戶端無關。連線測試成功並不表示請求已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `bootstrap.servers` | 必填，起始 broker，`主機:連接埠` 以逗號分隔，例如 `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT`（預設）、`SSL`、`SASL_PLAINTEXT` 或 `SASL_SSL` |
| `sasl.mechanism` | 僅限 SASL 協定：使用密碼的 `PLAIN`、`SCRAM-SHA-256` 或 `SCRAM-SHA-512`，Kerberos 使用 `GSSAPI` |
| `username` | SASL：查詢使用者；Kerberos 下為完整主體，例如 `grantforge@EXAMPLE.COM` |
| `password` | 使用者的密碼；未提供 keytab 時為主體的 Kerberos 密碼；加密儲存 |
| `keytab` | Kerberos：主體 keytab 的路徑，需在 **GrantForge 伺服器** 上可讀取 |
| `kerberos.kdc` | Kerberos：主體所屬領域的 KDC，`主機[:連接埠]` 以逗號分隔；留空則使用伺服器的 `krb5.conf` |
| `sasl.kerberos.service.name` | Kerberos：broker 主體的第一部分，預設 `kafka` |
| `ssl.truststore.location`、`ssl.truststore.password`、`ssl.truststore.type` | TLS：驗證 broker 的信任庫（`JKS`、`PKCS12` 或 `PEM`）；留空則信任 Java 信任的憑證 |
| `ssl.keystore.location`、`ssl.keystore.password`、`ssl.key.password` | TLS：GrantForge 的憑證，供要求用戶端憑證的 broker 使用 |
| `ssl.verify.hostname` | TLS：broker 憑證是否必須包含所連線的主機名稱，預設 `true` |
| `request.timeout.ms` | 請求逾時，預設 `30000`，範圍 `1..600000` |

`PLAINTEXT` 與 `SSL` 不接受 SASL 設定，`PLAINTEXT` 與 `SASL_PLAINTEXT` 不接受 TLS 設定。`PLAIN` 與 SCRAM 需要密碼；`GSSAPI` 需要密碼或 keytab。

### Kerberos

使用 `GSSAPI` 時，GrantForge 透過 Kafka 自己的 Kerberos 登入讓主體登入，憑 keytab，或憑 GrantForge 親自交給登入程序的密碼；GrantForge 伺服器上無需其他 Kerberos 設定。在 `kerberos.kdc` 中填寫了 KDC 的領域會對整個 GrantForge 伺服器宣告，方式與 HDFS、Hive 外掛相同，因此多個服務可以同時使用不同的領域。

### 查詢使用者能看到什麼

Kafka 只向使用者顯示其可描述的內容，因此查詢使用者需要對要提示的主題、消費者群組與交易 ID 擁有 `describe`，查詢 SCRAM 使用者則需要叢集上的 `describe`。Kafka 會略去無權描述的主題與消費者群組，所以提示為空，而不是回報錯誤。輸入以 `_` 開頭時才會提示內部主題，例如 `__consumer_offsets`。未啟用委派權杖或 SCRAM 的叢集不提示相應內容。

[Kafka 代理](/zh-tw/external/kafka-agent/)執行策略後，查詢使用者能描述什麼同樣由策略決定：為它設定允許 `describe` 的策略，或在 broker 的 `super.users` 中列出它。

## 資源與存取類型

| 層級 | Kafka 資源 | 存取類型 |
| --- | --- | --- |
| `topic` | 主題 | `read`、`write`、`create`、`delete`、`alter`、`describe`、`describe_configs`、`alter_configs`、`all` |
| `group` | 消費者群組 | `read`、`delete`、`describe`、`all` |
| `transactional_id` | 交易 ID | `write`、`describe`、`all` |
| `cluster` | 叢集，名稱固定為 `kafka-cluster` | `create`、`alter`、`describe`、`cluster_action`、`describe_configs`、`alter_configs`、`idempotent_write`、`all` |
| `delegation_token` | 委派權杖，依權杖 ID | `describe`、`all` |
| `user` | 建立或描述其委派權杖的使用者 | `create_tokens`、`describe_tokens`、`all` |

每個層級各自獨立，一條策略指定一個層級。值區分大小寫：`*` 比對任意數量的字元，`?` 比對一個字元。存取類型之間的蘊含與 Kafka 的 ACL 操作相同：`read`、`write`、`delete` 與 `alter` 蘊含 `describe`，`alter_configs` 蘊含 `describe_configs`，`all` 蘊含其他所有類型。

蘊含同樣作用於拒絕：拒絕主題上 `write` 的策略也會拒絕 `write` 所蘊含的 `describe`，因此使用者將看不到該主題。Kafka 自己的 ACL 只對允許的操作套用蘊含。

策略編輯器依前綴從叢集提示名稱；`cluster` 提示 `kafka-cluster`。

## 策略範例

假設使用者 `alice` 的生產者寫入名稱以 `orders` 開頭的主題，群組 `analysts` 以消費者群組 `billing` 讀取這些主題。在同一資料服務中建立三條已啟用的存取策略：

| 資源 | 規則 |
| --- | --- |
| `topic=orders*` | 允許使用者 `alice` 的 `write` |
| `topic=orders*` | 允許群組 `analysts` 的 `read` |
| `group=billing` | 允許群組 `analysts` 的 `read` |

代理套用新快照後，`alice` 可以向 `orders-eu` 生產訊息，冪等生產者也可以，且不需要叢集上的 `idempotent_write`；`analysts` 的成員以 `billing` 消費；其他主題與消費者群組對兩者皆拒絕。`kafka-acls --list` 會將這三條策略顯示為前綴 ACL 與字面 ACL。

## 執行策略

服務外掛負責管理策略與查詢資源。執行策略需要在每個 broker 與 controller 中安裝 Kafka 代理。部署方式與檢查內容請見 [Kafka 代理](/zh-tw/external/kafka-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Kafka 代理](/zh-tw/external/kafka-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
