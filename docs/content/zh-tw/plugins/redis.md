---
title: Redis
description: 為 Redis 和 Valkey 安裝並配置 Redis 服務外掛，連接單機、其複本或叢集，用 ACL 使用者登入（可選 TLS），查詢鍵、頻道和節點，並管理誰可以讀寫哪些鍵、使用哪些頻道、監控哪些節點。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Redis 外掛查詢 Redis 和 Valkey 部署的鍵、頻道和節點，並用 Redis 自己的概念管理存取策略：按模式讀寫鍵，訂閱和發佈頻道，以及監控每個節點。外掛 ID 與服務類型都是 `redis`。服務外掛運行在 GrantForge 伺服器中，運行在部署旁的 [Redis 代理](/zh-tw/external/redis-agent/) 在每個節點上用 Redis 自身的 ACL 實施策略。

儲存庫的 Testcontainers 整合測試涵蓋 Redis `7.2`、`7.4`、`8.2`、`8.10` 和 Valkey `8.1`、`9.1` 的一主一複本，以及一個 Redis `8.10` 叢集。這並不認證其他所有版本。

## 安裝

發行套件在 `plugins/redis` 中附帶此外掛。在 **平台管理 → 外掛** 中確認 `redis` 已啓用，更新外掛目錄後重新掃描。外掛在自己的類別載入器中通過 Jedis 與 Redis 通信。

## 新增資料服務

1. 開啟 **資料權限 → 資料服務**，新增服務並選擇 Redis（`redis`）。
2. 填寫一個或多個節點。叢集的其餘節點和主節點的複本會從這些節點自動發現。
3. 填寫查詢使用者，測試連接並儲存服務。測試會登入並描述部署：是 Redis 還是 Valkey 及其版本，是單機、帶複本的主節點還是叢集，以及誰登入了。

查詢使用者讓 GrantForge 能找到資源，它不是讀寫資料的使用者。連接測試成功並不代表存取已經遵循策略。

| 設定 | 用途 |
| --- | --- |
| `redis.nodes` | 必填：節點地址 `host:port`，如 `redis1.example.com:6379`，以逗號分隔並按順序嘗試；不寫端口時為 `6379` |
| `username` | 查詢使用者，一個 ACL 使用者；留空則以預設使用者登入 |
| `password` | 其密碼，加密存儲 |
| `tls.mode` | `disable`（預設），`verify-ca` 校驗伺服器憑證，`verify-full` 同時校驗名稱 |
| `tls.ca.file` | TLS：**GrantForge 伺服器**上用於校驗伺服器的 PEM CA 憑證路徑。留空使用 JVM 的 |
| `tls.certificate.file` | TLS，用於要求用戶端憑證的伺服器：**GrantForge 伺服器**上 PEM 用戶端憑證的路徑，可同時包含私密金鑰 |
| `tls.key.file` | 憑證私密金鑰單獨存放時，**GrantForge 伺服器**上該 PEM 私密金鑰的路徑 |
| `tls.key.password` | 加密私密金鑰的密碼，加密存儲 |
| `timeout` | 連接和單條命令允許的時長，預設 `10000`，範圍 `1..600000` 毫秒 |

登入被拒絕、查詢使用者缺少權限、節點無法連接都會如實報告。

### 查詢使用者能看到什麼

鍵通過在每個主節點上執行 `SCAN` 查找，最多掃描幾輪，無論鍵有多少查詢都很快；頻道是當前有訂閱者的那些。`SCAN` 無論使用者的鍵模式如何都會列出所有鍵，所以查詢使用者不需要讀取值的權限：

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## 資源與存取類型

| 層級 | 匹配方式 | 存取類型 |
| --- | --- | --- |
| `key` | 萬用字元，區分大小寫。一個鍵或鍵的模式 | `read`、`write` |
| `channel` | 萬用字元，區分大小寫。一個頻道或頻道的模式 | `subscribe`、`publish` |
| `node` | 萬用字元，不區分大小寫。節點地址 `host:port` | `monitor` |

| 存取類型 | Redis 允許什麼 |
| --- | --- |
| `read` | 對該鍵執行 `@read` 中的命令，即 `%R~` |
| `write` | 對該鍵執行 `@write` 中的命令，即 `%W~`，也包括刪除 |
| `subscribe` | `SUBSCRIBE`、`PSUBSCRIBE`、`SSUBSCRIBE` ，作用於頻道， `&` |
| `publish` | `PUBLISH`、`SPUBLISH` ，作用於頻道， `&` |
| `monitor` | `INFO`、`DBSIZE`、`LATENCY LATEST`、`LATENCY HISTORY`、`MEMORY STATS` |

策略值中 `*` 匹配任意文字，`?` 匹配一個字元，與 Redis 自己的模式相同。Redis 按模式授予鍵和頻道，也無法把它們列給代理，所以策略值會作為一個獨立模式授予，前提是策略對該使用者在整個模式範圍內的決定一致。策略在其中決定不同的模式，例如策略拒絕 `sales:card:*` 時的 `sales:*`，會被保留不授予並說明原因；請改為指定策略決定一致的鍵或模式。含方括號、反斜槓、圓括號或空白的值永遠不會授予。

Redis 認為危險的命令，如 `FLUSHALL` 和 `KEYS`，永遠不會授予；無論模式如何都會列出鍵的 `SCAN` 和 `RANDOMKEY`，只授予能讀取所有鍵的使用者。模式在節點的每個資料庫中都指同名的鍵，因為 Redis 的 ACL 沒有資料庫的概念。

Redis 不會在值內部過濾，也不做遮罩，所以此服務類型不提供這兩項。

## 策略範例

在同一資料服務中創建以下啓用的策略：

| 資源 | 規則 |
| --- | --- |
| `key=sales:*` | 允許群組 `analysts` `read` |
| `key=sales:card:*` | 允許使用者 `billing` `read` 和 `write` |
| `key=cart:*` | 允許使用者 `shop` `read` 和 `write` |
| `channel=orders.*` | 允許群組 `analysts` `subscribe`；允許使用者 `shop` `publish` |
| `node=*` | 允許使用者 `dashboards` `monitor` |

代理應用快照後，`analysts` 的成員可以讀取所有 `sales:` 鍵並關注訂單頻道；`billing` 讀寫卡號鍵，這個模式內沒有其他策略作出不同決定；`shop` 維護購物車併發布訂單事件；`dashboards` 能看到每個節點的狀態而看不到資料。

## 執行策略

服務外掛管理策略並查詢資源。實施策略需要每個部署一個 Redis 代理，部署和檢查見 [Redis 代理](/zh-tw/external/redis-agent/)。

## 相關指南

- [資料服務、策略與代理](/zh-tw/external/data-services/)
- [Redis 代理](/zh-tw/external/redis-agent/)
- [外掛程式與服務類型](/zh-tw/develop/plugins/)
