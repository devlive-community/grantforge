---
title: 租戶
description: 建立、編輯、停用與啟用租戶。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**平台管理 → 租戶管理**：每個租戶是一個相互隔離的組織，擁有自己的使用者與權限。平台租戶承載平台管理員，不能停用。

![租戶管理](/screenshots/tenants.png)

## 建立租戶

填寫租戶代碼、名稱，以及該租戶首位管理員的使用者名稱、顯示名稱與密碼。首位管理員持有該租戶的「租戶管理員」系統角色，首次登入時必須變更密碼。使用者名稱在整個平台內唯一。

## 停用與啟用

停用租戶後，該租戶的所有帳號立即登出且無法登入，已簽發的應用程式權杖不再續期。資料不會刪除，重新啟用即可恢復。

## 平台租戶

平台租戶是初始化時建立的第一個租戶，它的管理員可以管理全部租戶、[資源目錄與 API 目錄](/zh-tw/guide/catalog/)、授權伺服器與外掛程式。業務應用程式和它們的資源是全平台共享的，各租戶在自己的角色裡授權這些資源。
