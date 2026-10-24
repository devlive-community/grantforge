---
title: Apache Pulsar
description: 安裝並設定 Pulsar 服務外掛，連線一個或多個 broker，以 token、密碼、OAuth 2.0 或用戶端憑證登入，查詢租戶、命名空間、主題與訂閱，並管理誰可以在哪裡生產、消費與管理。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Pulsar 外掛查詢租戶、命名空間、主題、訂閱與叢集，並針對 Pulsar 所決定的事情管理存取策略：生產、消費、Functions、Sources、Sinks 與 Packages，以及管理主題、命名空間、租戶與叢集。外掛 ID 與服務類型皆為 `pulsar`。服務外掛執行於 GrantForge 伺服器中；安裝在 broker 中的 [Pulsar 代理](/zh-tw/external/pulsar-agent/)負責執行策略。

儲存庫的 Testcontainers 整合測試涵蓋 Pulsar `3.0.17`、`3.3.9`、`4.0.14` 與 `4.2.5` 的 broker；這並不代表對其他所有版本的認證。

## 安裝

發行套件在 `plugins/pulsar` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `pulsar` 已啟用；更新外掛目錄後重新掃描。外掛透過 Pulsar 自己的 admin 用戶端 4.2.5 與 broker 的 Web 服務通訊，位於外掛自己的類別載入器中。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Apache Pulsar（`pulsar`）。
2. 輸入一個 broker 或多個 broker 的 Web 服務位址。
3. 選擇查詢角色的登入方式並填寫憑據，測試連線後儲存服務。測試會登入並描述叢集：有哪些叢集、多少個租戶。

設定沿用 Pulsar 自己 `client.conf` 中的名稱，可以直接從中複製。查詢角色用於讓 GrantForge 探索資源，與生產和消費的角色彼此獨立。連線測試成功並不表示存取已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `webServiceUrl` | 必填：broker 的 Web 服務，`http://host:port` 或 `https://host:port`，預設連接埠 `8080` 或 `8443`；多個 broker 以逗號分隔，例如 `http://broker1:8080,broker2:8080` |
| `authentication` | `none`（預設）、`token`、`basic`、`oauth2` 或 `tls` |
| `token` | token：查詢角色的 token；加密儲存 |
| `username`、`password` | basic：查詢角色的使用者名稱與密碼；密碼加密儲存 |
| `issuerUrl`、`privateKey`、`audience`、`scope` | oauth2：授權伺服器、**GrantForge 伺服器** 上含 `client_id` 與 `client_secret` 的 JSON 憑據檔案路徑，以及 broker 要求時的 audience 與 scope |
| `tlsCertificateFilePath`、`tlsKeyFilePath` | tls：**GrantForge 伺服器** 上用戶端憑證及其私密金鑰的路徑，PEM |
| `tlsTrustCertsFilePath` | https：驗證 broker 憑證所用的 CA 憑證，PEM；空白時使用 JVM 內建的 |
| `tlsEnableHostnameVerification` | https：broker 憑證是否必須包含其主機名稱，預設 `true` |
| `timeout` | 請求可用的時長，預設 `30000`，範圍 `1..600000` 毫秒 |

一種登入方式的設定在另一種方式下會被拒絕，因此服務明確寫出它如何登入。登入被拒、角色無權檢視，以及 broker 無法連線（包括其前方的代理替它回應時）都會如實回報。

### 查詢角色能看到什麼

Pulsar 只允許超級使用者列出叢集與租戶。請把查詢角色寫入 broker 的 `superUserRoles`，或在安裝代理後為它授予叢集上的 `admin`，這同樣會讓它成為超級使用者。查詢角色無權檢視的命名空間、主題與訂閱不會出現在提示中。

## 資源與存取類型

| 層級 | 比對 | 存取類型 |
| --- | --- | --- |
| `tenant` | 萬用字元，區分大小寫 | 除 `admin` 外全部 |
| `namespace` | 萬用字元，區分大小寫；位於租戶之下；命名空間自己的名稱，例如 `orders` | `produce`、`consume`、`functions`、`sources`、`sinks`、`packages`、`admin_topic`、`admin_namespace` |
| `topic` | 萬用字元，區分大小寫；位於命名空間之下；主題自己的名稱，例如 `payments` | `produce`、`consume`、`admin_topic` |
| `subscription` | 萬用字元，區分大小寫；位於主題之下；訂閱的名稱 | `consume` |
| `cluster` | 萬用字元，區分大小寫；叢集的名稱，例如 `standalone` | `admin`：Pulsar 的超級使用者權限 |

| 存取類型 | 隱含 |
| --- | --- |
| `produce`、`consume`、`functions`、`sources`、`sinks`、`packages` | — |
| `admin_topic`：終止、壓縮、卸載到分層儲存、unload 與截斷主題，修改其策略 | `produce`、`consume` |
| `admin_namespace`：建立與刪除主題，修改命名空間的策略 | `admin_topic` |
| `admin_tenant`：建立、列出與刪除命名空間 | `admin_namespace`、`functions`、`sources`、`sinks`、`packages` |
| `admin`：Pulsar 超級使用者可做的一切 | `admin_tenant` |

與 Pulsar 自己的授權一樣，租戶或命名空間上的權限涵蓋其中的內容：租戶 `sales` 的命名空間 `orders` 上的 `produce` 可以向其中每個主題生產。一次檢查會依序在租戶、命名空間、主題與訂閱上判定，任一處拒絕都會拒絕它；因此一條策略可以允許一個命名空間，同時拒絕其中的某個主題。在主題之下，訂閱層級指明角色可以透過哪些訂閱消費：主題 `*`、訂閱 `billing-*` 上的 `consume` 允許角色消費該命名空間的每個主題，但只能透過名稱以 `billing-` 開頭的訂閱。

主題依自己的名稱判定，不含 domain，因此同名的持久化與非持久化主題共用策略；分區主題的分區依分區主題本身判定。策略值中，`*` 比對任意多個字元，`?` 比對一個字元。策略編輯器會提示叢集、租戶，以及策略所列範圍內的命名空間、主題與訂閱。

### 用戶端位址

策略項目可以只對部分用戶端生效：其 **用戶端位址** 條件（`ip-range`）接受位址以及 `10.1.0.0/16` 或 `2001:db8::/32` 這樣的網段。不會解析主機名稱。在代理之後，用戶端就是代理本身，除非代理轉發用戶端自己的位址：生產者與消費者使用 HAProxy 協定，Web 服務在 broker 信任時使用 `X-Forwarded-For`。

## 策略範例

假設租戶 `sales` 下有命名空間 `orders` 與 `returns`。在同一資料服務中建立以下已啟用的存取策略：

| 資源 | 規則 |
| --- | --- |
| `tenant=sales`、`namespace=orders` | 為群組 `analysts` 允許 `produce` |
| `tenant=sales`、`namespace=orders`、`topic=audit` | 為使用者 `alice` 拒絕 `produce` |
| `tenant=sales`、`namespace=orders`、`topic=*`、`subscription=billing-*` | 為使用者 `alice` 允許 `consume` |
| `tenant=sales` | 為使用者 `bob` 允許 `admin_tenant` |
| `tenant=public` | 為群組 `ingest` 允許 `produce`，限 `10.0.0.0/8` |
| `cluster=standalone` | 為群組 `pulsar-admins` 允許 `admin` |

代理套用新快照後，`analysts` 的成員可以向 `sales/orders` 的每個主題生產，`alice` 除 `audit` 之外都可以；`alice` 在那裡只能透過 `billing-` 開頭的訂閱消費；`bob` 可以在 `sales` 中建立並管理命名空間與主題；`ingest` 的成員只能從內網向 `public` 租戶生產；`pulsar-admins` 的成員是 Pulsar 的超級使用者。

## 執行策略

服務外掛負責管理策略與查詢資源。執行策略需要在每個 broker 中安裝 Pulsar 代理。部署方式與檢查內容請參閱 [Pulsar 代理](/zh-tw/external/pulsar-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Pulsar 代理](/zh-tw/external/pulsar-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
