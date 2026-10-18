---
title: Apache HBase
description: 安裝與設定 HBase 服務外掛，透過 ZooKeeper 或啟動伺服器找到叢集，以 simple 或 Kerberos 登入，查詢命名空間、資料表與欄族，並依 HBase 自己的權限範圍管理存取策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

HBase 外掛查詢命名空間、資料表與欄族，並依 HBase 自己的權限範圍管理存取策略：全域、命名空間、資料表、欄族與欄。外掛 ID 與服務類型皆為 `hbase`。服務外掛執行於 GrantForge 伺服器中；安裝在 master 與 region server 中的 [HBase 代理](/zh-tw/external/hbase-agent/)負責執行策略。

儲存庫的 Testcontainers 整合測試涵蓋由經 SHA-512 驗證的 Apache 發行套件建置的 HBase `2.5.16`、`2.6.7` 與 `3.0.0`，以及一個 `2.6.7` 的 Kerberos 叢集；這並不代表對其他所有版本的認證。

## 安裝

發行套件在 `plugins/hbase` 中包含此外掛。請在 **平台管理 → 外掛** 中確認 `hbase` 已啟用；更新外掛目錄後請重新掃描。外掛在獨立的類別載入器中，透過 HBase 自己的用戶端（`hbase-shaded-client-byo-hadoop` 2.6.7）與 Hadoop 的 shaded 用戶端 3.5.0 連線叢集，因此同樣適用於 HBase 2.x 與 3.x。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Apache HBase（`hbase`）。
2. 填寫 ZooKeeper quorum，或 HBase 2.5 以上叢集的啟動伺服器。
3. 選擇查詢使用者的登入方式並填寫該使用者，測試連線後儲存服務。測試會登入並描述叢集：叢集 ID、版本與上線的 region server 數量。

設定沿用 HBase 用戶端自己的名稱，可以直接從 `hbase-site.xml` 中複製。查詢使用者只用於讓 GrantForge 探索資源，與讀寫資料的用戶端無關。連線測試成功並不表示請求已受策略約束。

| 設定 | 用途 |
| --- | --- |
| `hbase.zookeeper.quorum` | ZooKeeper 主機，`主機` 或 `主機:連接埠` 以逗號分隔，例如 `zk1,zk2,zk3:2181`；未填寫啟動伺服器時必填 |
| `hbase.zookeeper.property.clientPort` | 未指定連接埠的 quorum 主機所用連接埠，預設 `2181` |
| `zookeeper.znode.parent` | 叢集在 ZooKeeper 中的根節點，預設 `/hbase` |
| `hbase.client.bootstrap.servers` | 取代 ZooKeeper，HBase 2.5 起可用：master 或 region server，`主機:連接埠` 以逗號分隔 |
| `hbase.security.authentication` | `simple`（預設）：使用者名稱；`kerberos`：帶 keytab 或密碼的主體 |
| `username` | 必填的查詢使用者；Kerberos 下為完整主體，例如 `grantforge@EXAMPLE.COM` |
| `password` | Kerberos：主體的密碼，未提供 keytab 時使用；加密儲存 |
| `keytab` | Kerberos：主體 keytab 的路徑，需在 **GrantForge 伺服器** 上可讀取 |
| `kerberos.kdc` | Kerberos：主體所屬領域的 KDC，`主機[:連接埠]` 以逗號分隔；留空則使用伺服器的 `krb5.conf` |
| `hbase.master.kerberos.principal` | Kerberos：master 的主體，預設 `hbase/_HOST@` 加查詢主體的領域 |
| `hbase.regionserver.kerberos.principal` | Kerberos：region server 的主體，預設與 master 相同 |
| `hbase.rpc.protection` | `authentication`（預設）、`integrity` 或 `privacy`，需與叢集一致 |
| `hbase.rpc.timeout` | RPC 逾時，預設 `30000`，範圍 `1..600000` |

服務完全由其設定決定：不會讀取 GrantForge 伺服器上的任何 `hbase-site.xml`。simple 驗證不接受 Kerberos 設定；Kerberos 需要密碼或 keytab。查詢在數次嘗試後即放棄，而不是像 HBase 預設那樣等待數分鐘。

### Kerberos

使用 `kerberos` 時，GrantForge 透過 Hadoop 自己的登入讓主體登入，憑 keytab 或密碼，保留登入並依 keytab 續期；GrantForge 伺服器上無需其他 Kerberos 設定。在 `kerberos.kdc` 中填寫了 KDC 的領域會對整個 GrantForge 伺服器宣告，方式與 HDFS、Hive 外掛相同，因此多個服務可以同時使用不同的領域。

用戶端的登入方式與叢集預期不符時，叢集會直接關閉連線，不說明原因。外掛會將這種情況回報為登入遭拒，並提示檢查 `hbase.security.authentication` 與 `hbase.rpc.protection`，兩者必須與叢集一致。

### 查詢使用者能看到什麼

[HBase 代理](/zh-tw/external/hbase-agent/)執行策略後，清單會略去查詢使用者無權使用的命名空間與資料表：為它設定允許命名空間 `*` 上 `read` 的策略，或在叢集的 `hbase.superuser` 中列出它。

## 資源與存取類型

| 層級 | 比對 | 存取類型 |
| --- | --- | --- |
| `global` | 唯一的值 `*` | `admin`：針對整個叢集的操作，例如平衡器、配額與複寫 |
| `namespace` | 萬用字元，區分大小寫 | `read`、`write`、`create`、`admin` |
| `table` | 萬用字元，區分大小寫；位於命名空間之下，名稱不含命名空間 | `read`、`write`、`create`、`admin` |
| `column_family` | 萬用字元，區分大小寫；位於資料表之下 | `read`、`write` |
| `column` | 萬用字元，區分大小寫；位於欄族之下；以 UTF-8 文字命名 | `read`、`write` |

存取類型就是 HBase 的 `read`、`write`、`create` 與 `admin`。HBase 用於協同處理器端點的 `exec` 不提供，因為代理無法在所有 HBase 版本上檢查端點呼叫。

與 HBase 相同，權限涵蓋其範圍內的一切：命名空間 `sales` 上的 `read` 可讀其中所有資料表，欄族上的 `write` 可寫其所有欄。請求會在其涉及的每個範圍上分別決策，任一範圍的拒絕都會拒絕該請求，因此策略可以允許一張資料表而拒絕其中一欄。全域範圍是叢集本身，而不是所有資料表；命名空間 `*` 上的策略才涵蓋所有資料表。

策略值中，`*` 比對任意數量的字元，`?` 比對一個字元。策略編輯器會提示命名空間、策略所指命名空間中的資料表，以及其中資料表的欄族；欄存放的是資料，不做提示。

## 策略範例

假設命名空間 `sales` 中有資料表 `orders`，包含欄族 `d`（含 `name` 與 `ssn`）和 `meta`。在同一資料服務中建立以下已啟用的存取策略：

| 資源 | 規則 |
| --- | --- |
| `namespace=sales` | 允許使用者 `alice` 的 `read` |
| `namespace=sales`、`table=orders`、`column_family=d` | 允許使用者 `bob` 的 `read` |
| `namespace=sales`、`table=orders` | 允許群組 `auditors` 的 `read` |
| `namespace=sales`、`table=orders`、`column_family=d`、`column=ssn` | 拒絕群組 `auditors` 的 `read` |
| `namespace=analytics` | 允許使用者 `dave` 的 `create` 與 `admin` |

代理套用新快照後，`alice` 可讀 `sales` 中所有資料表；`bob` 讀取 `orders` 時只得到 `d` 的欄，列出資料表時也只看到 `orders`；`auditors` 的成員可讀 `orders` 中除 `d:ssn` 外的全部內容；`dave` 可以建立命名空間 `analytics` 及其中的資料表並管理它們，在其他地方建立資料表則遭拒。

## 執行策略

服務外掛負責管理策略與查詢資源。執行策略需要在每個 master 與 region server 中安裝 HBase 代理。部署方式與檢查內容請見 [HBase 代理](/zh-tw/external/hbase-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [HBase 代理](/zh-tw/external/hbase-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
