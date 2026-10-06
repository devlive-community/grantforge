---
title: 五分鐘上手
description: 以發行包或 Docker 啟動 GrantForge，完成初始化，建立使用者並授予第一個角色。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

本文以預設的內嵌式 H2 資料庫在本機啟動 GrantForge。正式環境請參考 [安裝發行包](/zh-tw/deploy/installation/) 與 [資料庫](/zh-tw/deploy/databases/)。

## 1. 啟動服務

需要 Java 17 或以上版本。從 [GitHub Releases](https://github.com/devlive-community/grantforge/releases) 下載發行包，或在原始碼目錄執行 `./mvnw -DskipTests package` 自行建置（產物在 `dist/grantforge-release.tar.gz`），然後解壓縮並啟動：

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

也可以用 Docker：先從發行包建置映像，再以 Compose 範例啟動：

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

服務預設監聽 `9999` 埠。首次啟動會建立資料表，並在日誌中列印一次性的**初始化權杖**：

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

發行包的日誌在 `logs/grantforge.log`，Docker 用 `docker compose logs` 查看。

## 2. 完成初始化

以瀏覽器開啟 http://127.0.0.1:9999/，主控台會自動進入初始化頁面。填寫日誌中的權杖、組織名稱以及第一位管理員的使用者名稱和密碼（至少 12 個字元）。

![首次初始化與登入頁面](/screenshots/login.png)

> [!TIP]
> 自動化安裝時，可以用環境變數 `GRANTFORGE_SETUP_TOKEN` 預先指定權杖，見 [設定參考](/zh-tw/reference/configuration/)。

初始化完成後，這位管理員同時持有**租戶管理員**和**平台管理員**兩個系統角色，可以使用主控台的全部功能。初始化頁面此後永久關閉。

## 3. 建立使用者

進入 **存取控制 → 使用者管理**，點擊「建立使用者」，填寫使用者名稱、初始密碼與主部門。新使用者首次登入時必須變更密碼。

## 4. 建立角色並授權

1. 進入 **存取控制 → 角色管理**，點擊「新增角色」，例如「唯讀稽核員」。
2. 在角色列上點擊「授權」，在授權矩陣中勾選「稽核日誌」頁面。矩陣會自動帶出該頁面所需的 API。
3. 點擊「分配」，把角色分配給剛才的使用者。

![角色管理](/screenshots/roles.png)

## 5. 驗證效果

用新使用者登入：左側選單僅出現「稽核日誌」。回到管理員帳號，在使用者列上點擊「檢視有效權限」圖示，可以看到他每一項權限的來源。

## 下一步

- 了解 [核心概念](/zh-tw/start/concepts/)。
- 依照 [使用指南](/zh-tw/guide/console/) 熟悉每一個選單。
- 讓你的應用程式 [串接 GrantForge](/zh-tw/integration/overview/)。
