---
title: Apache ZooKeeper
description: 安裝與設定 ZooKeeper 服務端外掛，瀏覽 znode 並為 znode 路徑管理存取策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache ZooKeeper 外掛為 GrantForge 提供 ensemble 連線、znode 路徑查詢與策略管理。外掛 ID 與服務類型都是 `zookeeper`。

## 安裝

發行包已在 `plugins/zookeeper` 中包含此外掛。在 **平台管理 → 外掛** 中確認 `zookeeper` 已啟用；更新外掛目錄後重新掃描。外掛在自己的類別載入器中使用 ZooKeeper 3.9 用戶端，不依賴伺服器的函式庫。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 ZooKeeper（`zookeeper`）。
2. 填寫連線字串，即 ensemble 的伺服器清單，可帶 chroot，例如 `zk1:2181,zk2:2181,zk3:2181/kafka`。用戶端會在伺服器之間自動切換。
3. 選擇認證方式並設定查詢 znode，測試連線（確認 znode 存在且可列出子節點）後儲存服務。

| 設定 | 用途 |
| --- | --- |
| `zookeeper.connect` | 必填連線字串：`host[:port]` 以逗號分隔，可帶 `/chroot`；chroot 不能以 `/` 結尾 |
| `authentication` | `none`（預設）、`digest` 或 `kerberos` |
| `username` | digest：ACL 中的 digest 使用者（不能包含冒號）；Kerberos：principal，如 `grantforge@EXAMPLE.COM` |
| `password` | digest 使用者的密碼，加密儲存 |
| `keytab` | Kerberos：GrantForge 伺服器上 principal 的 keytab 路徑 |
| `zookeeper.sasl.client.username` | Kerberos：伺服器 principal 的第一段，預設 `zookeeper`，即 `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos：伺服器 principal 不是上述形式時填寫完整 principal，如 `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | 是否連線到伺服器的 TLS 用戶端連接埠，預設 `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | 驗證伺服器憑證的信任庫；留空則信任伺服器 Java 所信任的憑證；類型為 `jks`、`pkcs12` 或 `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | 伺服器要求用戶端憑證時使用的金鑰庫 |
| `session.timeout.ms` | 工作階段逾時，預設 `15000`，範圍 `2000..120000` |
| `connection.timeout.ms` | 等待伺服器與登入完成的時間，預設 `10000`，範圍 `1000..60000` |
| `lookup.path` | 查詢與瀏覽的起始 znode（chroot 之下），預設 `/`，例如 `/brokers`；瀏覽限制在此 znode 內 |
| `lookup.max.entries` | 單一 znode 一次讀取的子節點上限，預設 `10000`，範圍 `1..100000` |

## 認證

- **none**：不登入，只能看到 ACL 對 `world:anyone` 開放的 znode。
- **digest**：以 `username:password` 登入，對應 ZooKeeper ACL 中的 `digest` 身分。密碼錯誤不會被拒絕登入，而是成為另一個身分，讀取受保護的 znode 時顯示無權存取。
- **kerberos**：使用 keytab 透過 SASL 登入，需要伺服器的 `krb5.conf` 能找到 KDC。每個 principal 與 keytab 都有自己的 JAAS 登入區段，與行程原有的設定並存，因此一個 GrantForge 可以用不同 principal 連線多個 ensemble。暫不支援僅使用密碼的 Kerberos 登入。

儲存時會檢查設定：連線字串格式、digest 需要使用者與密碼、Kerberos 需要 principal 與 keytab、keytab 與伺服器 principal 只用於 Kerberos，以及各項逾時與上限的範圍。

## 瀏覽 znode

在 **資料權限 → 策略** 選擇服務，使用 `path` 旁的 **瀏覽** 按鈕：

- 依名稱排序分頁列出子節點；有子節點的 znode 可繼續開啟。
- 顯示每個 znode 的 ACL（`scheme:id:cdrwa`，與 ZooKeeper 內建工具一致）、資料大小與修改時間。
- 目前使用者無權讀取 ACL 的 znode 仍會列出名稱，開啟時顯示無權存取的原因。

輸入路徑時會提示候選；不存在的父節點回傳空候選，超出 `lookup.path` 的路徑會被拒絕。子節點超過 `lookup.max.entries` 的 znode 會回報錯誤，而不會只顯示部分結果。

## 讓策略生效

資源只有一級 `path`，存取類型與 ZooKeeper 的 ACL 權限一致：`read`、`write`、`create`、`delete`、`admin`；路徑策略支援遞迴與排除。

服務端外掛負責管理、查詢與瀏覽。在 ZooKeeper 伺服器中執行這些策略的代理尚未提供；在此之前，存取仍由 ZooKeeper 自身的 ACL 決定。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [外掛與服務類型開發](/zh-tw/develop/plugins/)
