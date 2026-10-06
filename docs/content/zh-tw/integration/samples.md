---
title: 範例應用程式
description: 儲存庫裡的兩個完整範例：瀏覽器直連的商店，和伺服器端登入的筆記。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`samples/` 下有兩個可執行的應用程式，它們和 GrantForge 一起由端對端測試覆蓋，是接入時最好的參照。

| 範例 | 連接埠 | 展示的接入方式 |
| --- | --- | --- |
| `samples/shop` | 19081 | 瀏覽器用公開客戶端 + PKCE 跨源登入；依資源顯示按鈕；後端用 `@RequirePermission` 驗證 API；`@GrantForgeEntity` 宣告訂單實體，依「本人」「本租戶」資料範圍查詢 |
| `samples/notes` | 19082 | 伺服器端用 Spring Security `oauth2Login`（機密客戶端 + PKCE）登入；自訂 `AccessTokenResolver` 從工作階段裡取權杖；僅對有權限的人顯示「寫筆記」 |

## 執行

範例是獨立的 Maven 建置，依賴本儲存庫的 starter 與 JavaScript SDK：

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

範例的設定全部來自環境變數：

| 變數 | 說明 |
| --- | --- |
| `GRANTFORGE_URL` | GrantForge 的位址 |
| `SHOP_BROWSER_CLIENT_ID` | 商店瀏覽器端的公開客戶端 |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | 商店後端宣告資料實體用的機密客戶端（`catalog` scope） |
| `SHOP_SDK_DIRECTORY` | JavaScript SDK 的建置產出目錄（`sdk/grantforge-js/dist`） |

在 GrantForge 裡需要為兩個應用程式建好資源、客戶端、角色和資料策略，端對端測試的準備指令碼 `core/grantforge-web/tests/samples/setup.ts` 完整演示了這些步驟，可以直接參考。

## 端對端測試

`script/ci/e2e_fullstack.sh` 在全棧測試之後建置並啟動兩個範例，驗證：跨源 PKCE 登入與 CORS、按鈕顯隱、介面 403、「本人」和「本租戶」資料範圍、刪除與登出，以及筆記應用程式的伺服器端登入。設定 `GRANTFORGE_E2E_SKIP_SAMPLES=1` 可以跳過。
