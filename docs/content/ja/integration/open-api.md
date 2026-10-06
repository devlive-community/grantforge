---
title: 権限照会オープン API
description: アプリケーションがアクセストークンでユーザーのロール、リソース、API 権限とデータ範囲を照会し、自分のデータエンティティを宣言します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

オープン API は `/api/v1/open/` の配下にあり、権限付与サーバーが発行した Bearer トークンのみを受け付けます。Cookie は使わず、CSRF トークンも不要であり、トークンが取り消されるか、その権限付与が取り消されると即座に無効になります。

## インターフェース

| インターフェース | トークン | 説明 |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | ユーザートークン、`permissions` | このアプリケーションでのユーザーのロール、リソース、API 権限。`If-None-Match` 対応（304） |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | ユーザートークン、`permissions` | 各権限を持つかどうかを 1 件ずつ回答します（1 回に 1–100 件） |
| `GET /api/v1/open/me/data-access` | ユーザートークン、`permissions` | ロールに設定されたこのアプリケーションのデータエンティティのルール：範囲、条件、部門、およびユーザーの部門（配下の部門を含む）、ユーザーグループ、職位。ETag 対応 |
| `PUT /api/v1/open/catalog/data-entities` | クライアント自身のトークン、`catalog` | このアプリケーションのすべてのデータエンティティを宣言し、それまでの宣言を置き換えます |

## サンプル

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

アカウント、テナントなどの ID は、JavaScript が正確に表せる整数の範囲を超えるため文字列で返します。`version` は権限のバージョン番号です。

## キャッシュとバージョン

`authorization` と `data-access` のレスポンスには `ETag` が付きます。レスポンスをキャッシュし、次のリクエストで `If-None-Match` を送ると、権限が変わっていないときは `304` が返るため、コストはほとんどかかりません。権限付与、割り当て、リソースカタログ、データポリシーが少しでも変わるとバージョン番号が変わります。SDK はデフォルトで 30 秒間キャッシュしてから ETag で再検証します。

## データエンティティの宣言

アプリケーションは自分の機密クライアント（クライアントクレデンシャル + `catalog` scope）でデータエンティティを宣言します。

```json
{
  "entities": [
    {
      "code": "order",
      "name": "注文",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "状態", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "金額", "type": "NUMBER" }
      ]
    }
  ]
}
```

宣言すると、エンティティは `<アプリケーションコード>:<エンティティコード>`（例：`shop:order`）の形でロールのデータ権限エディターに現れます。`owned` は行に所有者アカウントがあることを意味するため「本人のみ」範囲を使え、`unitBased` は行がどの部門に属するかを意味するため部門範囲を使えます。Java アプリケーションがこのリクエストを手書きする必要はなく、starter が `@GrantForgeEntity` から自動で宣言します。

## エラー

すべてのエラーは RFC 9457 problem details 形式で、`code` と `requestId` を含みます。

| ステータス / エラーコード | 意味 |
| --- | --- |
| 401 | トークンがないか、もう有効ではないため、再度ログインする必要があります |
| `GF-SECURITY-003` | ユーザートークンが必要ですが、クライアント自身のトークンが渡されました |
| `GF-SECURITY-004` | トークンに必要な scope がありません |
| `GF-SECURITY-005` | クライアント自身のトークンが必要ですが、ユーザートークンが渡されました |
| `GF-AUTHZ-052` | 宣言したデータエンティティが正しくなく、`errors` が各問題の場所を知らせます |
