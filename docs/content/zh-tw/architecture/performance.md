---
title: 效能與基準
description: 百萬帳號規模的效能目標、基準測試的資料與方法，以及如何在本機執行。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge 的效能目標針對一個大型組織：**一百萬個帳號、一萬個角色、十萬個資源**。每晚在 PostgreSQL 17 上執行系統基準，任何指標超過上限都會讓建置失敗。

## 目標

| 指標 | 意義 | 上限 |
| --- | --- | --- |
| `authz.snapshot.hit` | 已快取的權限快照（每次介面呼叫都會用到） | p99 1 ms |
| `authz.snapshot.build` | 首次計算某個帳號的主控台快照 | p95 50 ms |
| `authz.snapshot.app` | 在具有大型資源樹的應用程式中計算快照 | p95 50 ms |
| `api.users.page` | 使用者清單的某一頁（前 500 頁內） | p95 200 ms |
| `api.users.search` | 依名稱搜尋使用者 | p95 200 ms |
| `api.groups.page` | 使用者群組清單的某一頁 | p95 200 ms |
| `api.roles.list` | 角色清單（不分頁） | p95 200 ms |
| `api.me.authorization` | 目前帳號的權限 | p95 200 ms |
| `api.member.groups` | 僅具有一個頁面權限的帳號存取受保護清單 | p95 200 ms |
| `jmh.derivation.*` | 為大型資源樹準備推導、推導二十條授權（JMH） | 平均 50 / 5 ms |

上限只允許收緊；放寬任何一項都需要有記錄可查的決策。

## 資料

基準會啟動真實的服務、完成初始化，再透過應用程式自己的實體寫入：

- 100 個部門、每千個帳號一個使用者群組、100 個職位；
- 每個帳號屬於一個部門與一個使用者群組，每十個帳號有一個職位，每二十個帳號有直接指派的角色；
- 每十個角色中有一個繼承自前一百個角色之一；每個使用者群組三個角色、每個部門兩個、每個職位一個；每個角色授權五個主控台頁面；
- 一個應用程式：多個模組下一百個頁面，每個頁面九個操作；十分之一的角色被授權其中二十個頁面與操作。

每個指標預熱後逐一呼叫測量。

## 在本機執行

```bash
bash script/ci/perf_benchmark.sh full            # 目標規模，postgres:17（需要 Docker），檢查上限
bash script/ci/perf_benchmark.sh smoke           # H2 上的小規模，只確認基準能運行
bash script/ci/perf_benchmark.sh full mysql:8.4  # 其他資料庫
```

報告寫入 `perf/target/perf-report.json`，日誌中同時輸出表格。可以透過 `PERF_OPTS` 傳入額外參數：

| 參數 | 預設值 | 說明 |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2` 或 `<引擎>:<版本>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | 資料規模 |
| `perf.samples` / `perf.warmup` | 1000 / 200 | 每個指標測量與預熱的次數 |
| `perf.jmh` | `true` | 同時執行 JMH 基準 |
| `perf.enforce` | `true` | 超過上限時以 1 結束 |

## 為什麼能快

- 權限快照按帳號快取，並依目錄與租戶的版本號整體失效，命中時只是一次記憶體讀取。
- 推導在記憶體中進行：資源樹、繼承關係與指派預先載入為緊湊的結構，避免逐筆查詢。
- 清單介面全部分頁，排序與篩選都落在有索引的欄位上；延遲關聯以 64 個一批載入，寫入以 50 筆一批提交（ID 由應用程式產生，因此可以批次插入）。
