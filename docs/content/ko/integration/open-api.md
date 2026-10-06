---
title: 권한 조회 오픈 API
description: 애플리케이션이 액세스 토큰으로 사용자의 역할, 리소스, API 권한, 데이터 범위를 조회하고, 자신의 데이터 엔터티를 선언합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

공개 API는 `/api/v1/open/` 아래에 있으며, 권한 부여 서버가 발급한 Bearer 토큰만 받습니다. 쿠키를 사용하지 않고 CSRF 토큰도 필요하지 않으며, 토큰이 철회되거나 그랜트가 철회되면 즉시 효력을 잃습니다.

## 인터페이스

| 인터페이스 | 토큰 | 설명 |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | 사용자 토큰, `permissions` | 이 애플리케이션에서 사용자의 역할, 리소스, API 권한. `If-None-Match` 지원(304) |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | 사용자 토큰, `permissions` | 각 권한의 보유 여부를 하나씩 응답(한 번에 1–100개) |
| `GET /api/v1/open/me/data-access` | 사용자 토큰, `permissions` | 역할에 설정된 이 애플리케이션 데이터 엔터티의 규칙: 범위, 조건, 부서, 그리고 사용자의 부서(하위 부서 포함), 사용자 그룹, 직위. ETag 지원 |
| `PUT /api/v1/open/catalog/data-entities` | 클라이언트 자신의 토큰, `catalog` | 이 애플리케이션의 모든 데이터 엔터티를 선언하며, 이전 선언을 대체합니다 |

## 예제

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

계정, 테넌트 같은 ID는 JavaScript가 정확히 표현할 수 있는 정수 범위를 넘어서 문자열로 반환됩니다. `version`은 권한의 버전 번호입니다.

## 캐시와 버전

`authorization`와 `data-access`의 응답에는 `ETag`가 붙습니다. 응답을 캐시하고 다음 요청에 `If-None-Match`를 보내면, 권한이 변하지 않았을 때 `304`를 반환하므로 비용이 거의 없습니다. 권한 부여, 할당, 리소스 카탈로그, 데이터 정책이 조금이라도 바뀌면 버전 번호가 변경됩니다. SDK는 기본적으로 30초간 캐시한 뒤 ETag로 재검증합니다.

## 데이터 엔터티 선언

애플리케이션은 자신의 기밀 클라이언트(클라이언트 자격 증명 + `catalog` scope)로 데이터 엔터티를 선언합니다.

```json
{
  "entities": [
    {
      "code": "order",
      "name": "주문",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "상태", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "금액", "type": "NUMBER" }
      ]
    }
  ]
}
```

선언하면 엔터티가 `<애플리케이션 코드>:<엔터티 코드>`(예: `shop:order`) 형태로 역할의 데이터 권한 편집기에 나타납니다. `owned`는 데이터 행에 소유 계정이 있음을 뜻하므로 "본인만" 범위를 쓸 수 있고, `unitBased`는 데이터 행이 어떤 부서에 속함을 뜻하므로 부서 범위를 쓸 수 있습니다. Java 애플리케이션은 이 요청을 직접 작성할 필요가 없으며, starter가 `@GrantForgeEntity`에서 자동으로 선언합니다.

## 오류

모든 오류는 RFC 9457 problem details 형식이며, `code`와 `requestId`를 포함합니다.

| 상태 / 오류 코드 | 의미 |
| --- | --- |
| 401 | 토큰이 없거나 더 이상 유효하지 않아 다시 로그인해야 합니다 |
| `GF-SECURITY-003` | 사용자 토큰이 필요한데 클라이언트 자신의 토큰이 전달되었습니다 |
| `GF-SECURITY-004` | 토큰에 필요한 scope가 없습니다 |
| `GF-SECURITY-005` | 클라이언트 자신의 토큰이 필요한데 사용자 토큰이 전달되었습니다 |
| `GF-AUTHZ-052` | 선언한 데이터 엔터티가 올바르지 않으며, `errors`가 각 문제의 위치를 알려줍니다 |
