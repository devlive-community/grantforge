---
title: OAuth 2.1 與 OpenID Connect
description: 授權伺服器的端點、客戶端類型、權杖規則與簽章金鑰。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge 內建基於 Spring Authorization Server 的授權伺服器，遵循 OAuth 2.1 的安全要求：僅支援授權碼流程（必須使用 PKCE）、更新權杖與客戶端憑證，不支援隱式流程與密碼模式。

![授權伺服器](/screenshots/oauth.png)

## 發現文件與端點

發現文件：`<GrantForge>/.well-known/openid-configuration`，在 **平台管理 → 授權伺服器** 頁面可以直接複製。簽發者預設是請求送達的位址，在反向代理之後部署時用 `grantforge.oauth.issuer` 固定它。

| 端點 | 說明 |
| --- | --- |
| `/oauth2/authorize` | 授權碼流程；所有客戶端必須使用 PKCE（S256） |
| `/oauth2/token` | 授權碼、更新權杖、客戶端憑證。更新權杖每次使用都會更換，舊權杖再次出現會吊銷整個授權 |
| `/oauth2/revoke` | 撤銷權杖 |
| `/oauth2/jwks` | 簽章公鑰（RS256） |
| `/userinfo` | `sub`、`tid`、`preferred_username`；profile 範圍有 `name`，email 範圍有 `email` |

存取權杖與 ID 權杖包含 `tid`（租戶 ID）與 `preferred_username`；ID 權杖的 `auth_time` 是使用者登入主控台的時間。

## 客戶端

在 **平台管理 → 資源目錄** 選取應用程式，點擊「OAuth 客戶端」管理它的客戶端。

| 設定 | 規則 |
| --- | --- |
| 類型 | **公開客戶端**用於瀏覽器、行動裝置等無法保管金鑰的應用程式；**機密客戶端**用於伺服器端應用程式，持有金鑰 |
| 回呼位址 | 最多 10 個，必須是絕對位址，不允許萬用字元和 fragment；必須是 https，或本機的 http（localhost、127.0.0.1、[::1]），或原生應用程式的自訂協定 |
| scope | `openid`、`profile`、`email`、`permissions`（查詢權限）、`catalog`（宣告資料實體，僅限客戶端憑證） |
| 授權方式 | 授權碼、更新權杖（需要授權碼，僅機密客戶端會取得）、客戶端憑證（僅機密客戶端） |
| 權杖有效期 | 存取權杖 1 分鐘–24 小時（預設 15 分鐘），更新權杖 1 小時–90 天（預設 30 天） |

機密客戶端的金鑰在註冊或輪換時僅顯示一次，GrantForge 僅儲存其雜湊。輪換時可以設定寬限期（最長 7 天），寬限期內新舊金鑰都有效，方便滾動更新。

## 權杖規則

- 權杖以雜湊儲存：即使資料庫外洩也取得不了可用的權杖。
- 以下任一情況發生後，已簽發的權杖不再續期：客戶端被停用或刪除、帳號被停用或鎖定、帳號需要變更密碼、租戶被停用。
- 瀏覽器跨域：GrantForge 允許已啟用客戶端回呼位址所在的來源跨域呼叫權杖端點與開放 API，且不攜帶 Cookie。

## 簽章金鑰

簽章金鑰以 RSA 2048 產生，私鑰加密儲存。預設每 90 天自動輪換（`grantforge.oauth.signing-key-rotation`），舊公鑰會繼續在 JWKS 中發佈 2 天（`signing-key-retention`），確保輪換前簽發的權杖仍可驗證。需要時可以在授權伺服器頁面立即輪換（這是敏感操作，已開啟兩步驟驗證的帳號需要再次驗證）。

## 用 GrantForge 登入主控台以外的系統

任何支援 OpenID Connect 的系統（Grafana、GitLab、Jenkins 等）都可以把 GrantForge 當作 IdP：在資源目錄為它建立應用程式與機密客戶端，再把發現文件位址、client_id 和金鑰填入對方的 OIDC 設定即可。反過來，GrantForge 也可以用其他 IdP 登入，見 [身分來源](/zh-tw/guide/identity-sources/)。
