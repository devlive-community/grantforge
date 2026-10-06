---
title: 權限查詢開放 API
description: 應用程式以存取權杖查詢使用者的角色、資源、API 權限與資料範圍，並宣告自己的資料實體。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

開放 API 位於 `/api/v1/open/` 下，僅接受授權伺服器簽發的 Bearer 權杖，不使用 Cookie、不需要 CSRF 權杖；權杖被撤銷或其授權被吊銷後立即失效。

## 介面

| 介面 | 權杖 | 說明 |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | 使用者權杖，`permissions` | 使用者在本應用程式的角色、資源與 API 權限；支援 `If-None-Match`（304） |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | 使用者權杖，`permissions` | 逐個回答是否擁有（1–100 個） |
| `GET /api/v1/open/me/data-access` | 使用者權杖，`permissions` | 角色對本應用程式資料實體的規則：範圍、條件、部門，以及使用者的部門（含下級）、群組、職位；支援 ETag |
| `PUT /api/v1/open/catalog/data-entities` | 客戶端自身權杖，`catalog` | 宣告本應用程式的全部資料實體，取代先前的宣告 |

## 範例

```bash
curl -H "Authorization: Bearer $TOKEN" https://grantforge.example.com/api/v1/open/me/authorization
```

```json
{
  "application": "shop",
  "accountId": "100203911213113344",
  "tenantId": "100203900000000000",
  "username": "sam",
  "version": 8121,
  "roles": ["shop-buyers"],
  "resources": ["shop.orders", "shop.orders.btn.create"],
  "permissions": ["orders.read", "orders.create"],
  "computedAt": "2026-10-05T03:12:45Z"
}
```

帳號、租戶等 ID 以字串回傳，因為它們超出了 JavaScript 能精確表示的整數範圍；`version` 是權限的版本編號。

## 快取與版本

`authorization` 與 `data-access` 的回應帶有 `ETag`。快取該回應，下次附上 `If-None-Match`：權限沒有變化時回傳 `304`，幾乎沒有開銷。授權、分配、資源目錄或資料策略的任何變化都會改變版本編號。SDK 預設快取 30 秒，再以 ETag 重新驗證。

## 宣告資料實體

應用程式以自己的機密客戶端（客戶端憑證 + `catalog` scope）宣告資料實體：

```json
{
  "entities": [
    {
      "code": "order",
      "name": "訂單",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "狀態", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "金額", "type": "NUMBER" }
      ]
    }
  ]
}
```

宣告後，實體會以 `<應用程式編碼>:<實體編碼>`（如 `shop:order`）出現在角色的資料權限編輯器中。`owned` 表示資料列有歸屬帳號（可使用「本人」範圍），`unitBased` 表示資料列屬於某個部門（可使用部門範圍）。Java 應用程式不必手寫這個請求，starter 會從 `@GrantForgeEntity` 自動宣告。

## 錯誤

所有錯誤都是 RFC 9457 problem details，帶有 `code` 與 `requestId`：

| 狀態 / 錯誤碼 | 含義 |
| --- | --- |
| 401 | 沒有權杖或權杖已失效，需要重新登入 |
| `GF-SECURITY-003` | 需要使用者權杖，但傳入的是客戶端自身的權杖 |
| `GF-SECURITY-004` | 權杖缺少所需的 scope |
| `GF-SECURITY-005` | 需要客戶端自身的權杖，但傳入的是使用者權杖 |
| `GF-AUTHZ-052` | 宣告的資料實體不正確，`errors` 指出每一處 |
