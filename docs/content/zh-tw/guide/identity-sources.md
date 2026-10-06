---
title: 身分來源（LDAP 與 OIDC）
description: 讓使用者以公司目錄（LDAP/AD）或 OpenID Connect 提供者登入，自動建立帳號並同步。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**存取控制 → 身分來源** 設定使用者可以用來登入的目錄或身分提供者。這類帳號的密碼儲存在身分來源裡，GrantForge 不儲存也不能修改它們。

![身分來源](/screenshots/identity-sources.png)

## LDAP / Active Directory

點擊「新增身分來源」，類型選「LDAP 目錄」：

| 設定 | 說明 |
| --- | --- |
| 目錄位址 | `ldap://` 或 `ldaps://`，多個位址以空格分開實作故障切換 |
| 使用者所在 Base DN | 例如 `ou=people,dc=example,dc=com` |
| 查詢帳號 | 用來尋找使用者的帳號（Bind DN）及其密碼；留空則匿名查詢 |
| 使用者過濾條件 | 預設 `(&(objectClass=person)(uid={0}))`，`{0}` 是使用者輸入的名字 |
| 屬性 | 使用者名稱、顯示名稱、電子郵件、唯一識別的屬性；預設相容 OpenLDAP（`uid`、`cn`、`mail`、`entryUUID`），Active Directory 請填 `sAMAccountName` 與 `objectGUID` |
| 為新使用者自動建立帳號 | 開啟後，目錄裡的使用者首次登入即建立帳號 |
| 同步間隔 | 留空則僅手動同步，最短 15 分鐘 |
| 停用已離開目錄的使用者帳號 | 同步時停用目錄中已不存在的使用者 |

儲存後點擊 **測試連線** 確認設定正確。

登入時，GrantForge 先用查詢帳號找到使用者，再用使用者輸入的密碼、以該使用者的身分繫結目錄來驗證密碼。

**同步**會為目錄中的新使用者建立帳號、更新既有帳號的姓名與電子郵件，並依設定停用已離開的使用者（同時結束他們的工作階段）。同步結果顯示在身分來源卡片上。

## OpenID Connect

類型選「OpenID Connect」，可以接入 Keycloak、Azure AD、Okta、另一套 GrantForge 等：

| 設定 | 說明 |
| --- | --- |
| Issuer 位址 | 提供者的簽發者位址，GrantForge 透過它的發現文件取得端點與金鑰 |
| 客戶端 ID / 金鑰 | 在提供者註冊的客戶端；沒有金鑰時作為公開客戶端，使用 PKCE |
| 回呼位址 | 頁面上顯示的 `<GrantForge>/api/v1/auth/federated/callback/<编码>`，需要註冊到提供者的客戶端裡 |
| 範圍與宣告 | 預設 `openid profile email`；使用者名稱、顯示名稱、電子郵件分別取 `preferred_username`、`name`、`email` 宣告 |

啟用後，登入頁出現「透過 <名稱> 登入」按鈕。使用者在提供者登入後回到 GrantForge；已開啟雙因素驗證的帳號還要輸入驗證碼。

## 帳號規則

- 身分來源使用者的唯一識別（LDAP 的 `entryUUID`/`objectGUID`、OIDC 的 `sub`）對應一個 GrantForge 帳號，改名不影響對應關係。
- 本地已有同名帳號時**不會自動關聯**，登入會被拒絕，需要管理員先改名或刪除本地帳號。這可避免目錄裡的同名使用者接管既有帳號。
- 不會自動建立帳號的身分來源僅能讓已關聯的使用者登入。
- 停用身分來源後，它的使用者無法登入；仍有帳號在使用的身分來源不能刪除。
- 身分來源帳號可以照常指派角色、開啟雙因素驗證。
