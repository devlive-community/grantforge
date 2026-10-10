---
title: Apache Hadoop HDFS
description: 安裝與設定 HDFS 服務端外掛，瀏覽目錄並為檔案與目錄設定存取策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Hadoop HDFS 外掛在 GrantForge 中提供 HDFS 叢集連線、路徑查詢與策略管理。外掛 ID 與服務類型均為 `hdfs`。

## 安裝

發行包已將外掛放在 `plugins/hdfs`。在 **平台管理 → 外掛** 確認 `hdfs` 已啟用；更新外掛後可重新掃描外掛目錄。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 HDFS（`hdfs`）。
2. 填寫叢集 URI 與查詢使用者。Hadoop 2.x 使用 `webhdfs://namenode:50070`；3.x 可使用 `hdfs://namenode:8020` 或 `webhdfs://namenode:9870`。HTTPS 使用 `swebhdfs://` 與叢集實際連接埠。
3. 設定查詢目錄並測試連線；測試會確認目錄存在且可列出內容，然後儲存服務。

| 設定 | 用途 |
| --- | --- |
| `fs.default.name` | 必填叢集 URI，不包含子目錄、憑證或查詢參數；HA 可用 `hdfs://nameservice1` 並補齊附加設定 |
| `username` | 必填查詢使用者；Kerberos 時填 principal，例如 `grantforge@EXAMPLE.COM` |
| `hadoop.security.authentication` | 預設 `simple`；Kerberos 叢集選擇 `kerberos` |
| `hadoop.security.authorization` | 是否讓 Hadoop 檢查權限，預設 `false`；與叢集的 core-site.xml 一致 |
| `hadoop.security.auth_to_local` | Kerberos principal 對應使用者名稱的規則，與叢集的 core-site.xml 一致 |
| `password` / `keytab` | Kerberos 密碼或 GrantForge 伺服器上的 keytab 檔案路徑 |
| `kerberos.kdc` | 查詢 principal 所在 realm 的 KDC，`host[:port]`，多個以逗號分隔；留空則使用伺服器的 `krb5.conf` |
| `dfs.namenode.kerberos.principal`、`dfs.datanode.kerberos.principal`、`dfs.secondary.namenode.kerberos.principal` | Kerberos 叢集各元件的 principal，如 `nn/_HOST@EXAMPLE.COM`，與叢集設定一致 |
| `lookup.path` | 查詢與瀏覽的起始目錄，預設 `/`；例如 `/data`，瀏覽限制在該目錄內 |
| `lookup.max.entries` | 完整目錄掃描的上限，預設 `10000`，範圍 `1..100000` |
| `hadoop.config` | 每行一個 `key=value`，用於 HA 等 Hadoop 設定；覆寫同名連線設定 |
| `hadoop.rpc.protection` | `authentication`、`integrity` 或 `privacy`，需與叢集一致 |
| `ssl.client.truststore.location` | GrantForge 伺服器上信任庫的路徑，用於驗證 `swebhdfs://` NameNode 的憑證；留空則信任伺服器 Java 預設信任的憑證 |
| `ssl.client.truststore.password` | 信任庫密碼；信任庫受保護時填寫，加密儲存 |
| `ssl.client.truststore.type` | `jks`（預設）或 `pkcs12` |

Hadoop 2.7.7 啟用 NameNode 屬性擴充後，一般使用者查詢根路徑 `/` 會觸發已確認的上游 `NullPointerException`；請將 `lookup.path` 設為 `/data` 等實際目錄，詳見 [代理指南](/zh-tw/external/hdfs-agent/)。

Kerberos 還需可存取的 KDC、伺服器的 `krb5.conf`，以及與叢集一致的 `hadoop.security.auth_to_local` 與服務 principal。查詢帳號用於取得目錄中繼資料。已在 Hadoop 3.5.0 的 Kerberos 叢集上實測：經 RPC（keytab 或密碼）與 swebhdfs（服務自己的信任庫與 SPNEGO）查詢、瀏覽路徑，憑證錯誤、缺少信任庫與 simple 用戶端都會失敗；其他版本尚未驗證。

使用 Kerberos 時，GrantForge 會在多次查詢之間重複使用同一次登入，不必每次都存取 KDC：keytab 登入在票證接近到期時由 Hadoop 自動續期；密碼登入在票證剩餘壽命不足五分之一（至少一分鐘）時重新登入；更換密碼或 keytab 檔案更新後會重新登入。每個服務使用自己的信任庫，伺服器類別路徑上的 `ssl-client.xml` 不會覆蓋它。

一個 GrantForge 可同時連線多個 Kerberos realm 的叢集：在各服務的 `kerberos.kdc` 中填寫其 realm 的 KDC，realm 取自 `username` 中的 principal（如 `grantforge@BETA.EXAMPLE`）。GrantForge 會產生一份包含伺服器原有 `krb5.conf` 的設定，並將各服務的 realm 寫入其中，下一次登入即生效，無需重新啟動。`hadoop.security.auth_to_local` 留空時，會為該 realm 的 principal 產生短名規則。伺服器設定了 `java.security.krb5.realm` 與 `.kdc` 時無法使用此項。兩個服務對同一 realm 填寫不同 KDC 時以最近一次查詢為準，應保持一致；跨 realm 信任仍需在伺服器的 `krb5.conf` 中設定。

儲存時會驗證設定：`hadoop.config` 中的 `fs.defaultFS` 與 `fs.default.name` 是別名，只能設置其中一個；叢集 URI 不得包含憑證、路徑、查詢或片段；選擇 `kerberos` 時必須提供 `password` 或 `keytab`；`lookup.path` 必須是不含 `..` 的絕對路徑；`lookup.max.entries` 需在 `1..100000` 之間。附加設定會覆寫同名連線設定，驗證與登入均使用覆寫後的值。

## 瀏覽路徑

在 **資料權限 → 策略** 選擇服務，使用 `path` 旁的 **瀏覽** 按鈕。

- 逐級開啟目錄，使用路徑導覽或返回上層，並依需要載入後續頁面。
- 查看檔案與目錄標記、擁有者、群組、權限、檔案大小與修改時間。
- 多選檔案或目錄，或選擇目前目錄，加入策略的路徑清單；也可繼續輸入路徑取得候選。

RPC 與支援批次列舉的 WebHDFS 使用原生分頁；舊端點需在掃描上限內讀取目錄，超限會報錯。權限、驗證或連線失敗會顯示原因，並可重試。

輸入中可省略開頭的 `/`，允許重複的 `/` 與 `.`，拒絕 `..` 與 `lookup.path` 範圍之外的絕對路徑；不存在的目錄回傳空候選。瀏覽不取代 HDFS 自身的存取控制，符號連結與 ViewFS 掛載仍遵循叢集設定。

## 讓策略生效

資源只有一級 `path`，存取類型為 `read`、`write`、`execute`；路徑策略支援遞迴與排除。

服務端外掛負責管理與查詢。實際約束資料存取還需部署符合 Hadoop 版本的 NameNode 代理；使用者必須同時滿足 HDFS 原生權限與 GrantForge 策略。編號代理涵蓋 2.7、2.10、3.2、3.3、3.4、3.5，具體已驗證組合、驗證與 HA 範圍及超級使用者限制見代理指南。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Apache Hadoop HDFS NameNode 代理](/zh-tw/external/hdfs-agent/)
- [外掛與服務類型開發](/zh-tw/develop/plugins/)
