---
title: Java SDK(Spring Boot)
description: grantforge-spring-boot-starter로 API 권한을 검증하고 리소스를 확인하며, 데이터 권한을 JPA 조회 조건으로 바꿉니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter`는 GrantForge의 공개 API에만 의존하며, GrantForge의 다른 모듈에는 의존하지 않습니다.

## 의존성과 설정

```xml
<dependency>
    <groupId>org.devlive.grantforge</groupId>
    <artifactId>grantforge-spring-boot-starter</artifactId>
    <version>2026.0.0</version>
</dependency>
```

```properties

grantforge.client.base-url=https://grantforge.example.com

grantforge.client.cache-ttl=30s

grantforge.client.client-id=gf_xxx
grantforge.client.client-secret=***
```

starter는 기본적으로 현재 요청의 `Authorization: Bearer` 헤더에서 사용자 토큰을 읽습니다. 토큰을 세션에 보관하는 애플리케이션(예: Spring Security OAuth 2.0 로그인)은 자신의 `AccessTokenResolver` bean을 제공하세요.

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge는 모든 클라이언트에 PKCE 사용을 요구합니다. Spring Security 로그인 시에는 `OAuth2AuthorizationRequestCustomizers.withPkce()`로 켜세요(`samples/notes` 참고).

## API 권한

```java
@RequirePermission("orders.delete")      // 메서드 또는 클래스에 적용; 나열한 코드가 모두 필요
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

토큰이 없거나 만료되면 401, 권한이 없으면 403(RFC 9457 problem details)을 반환합니다. 코드에서 직접 확인할 때는 `GrantForge`를 사용하세요.

```java
UserAuthorization user = grantForge.current();   // accountId, tenantId, username, roles, resources, permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## 데이터 권한

JPA 엔터티에 선언하면, starter가 시작할 때 기밀 클라이언트로 GrantForge에 엔터티를 선언합니다. 그 뒤에는 역할의 데이터 권한에서 `<애플리케이션 코드>:order`에 정책을 설정할 수 있습니다.

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // GrantForge 계정 ID, "본인만" 범위가 이 값을 비교
    private Long unitId;           // GrantForge 부서 ID, 부서 범위가 이 값을 비교
    private String tenantId;       // 선택: 매핑하면 "전체 테넌트"를 제외한 규칙은 모두 같은 테넌트를 요구
    @GrantForgeField("Status") private Status status;   // 조건에서 테스트할 수 있는 필드: 텍스트, 숫자, 불리언, 열거(선택지), 시간
    @GrantForgeField("Total")  private long total;
}
```

조회할 때 사용자의 데이터 범위를 일반 `Specification`처럼 조합합니다.

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // 범위를 벗어난 행은 존재하지 않는 것으로 처리
```

의미는 GrantForge 콘솔과 같습니다. 허용 규칙이 있고 거부 규칙에 걸리지 않은 행을 사용할 수 있으며, 규칙이 없으면 어떤 행도 사용할 수 없습니다. "본 부서 및 하위 부서"는 GrantForge가 부서 ID 목록으로 펼칩니다. 행이 소유 계정이 속한 부서에 속하는 경우는 아직 지원하지 않습니다.


`samples/shop`(브라우저 + 리소스 서버)과 `samples/notes`(Spring Security 로그인)의 전체 예제는 [예제 애플리케이션](/ko/integration/samples/)을 참고하세요.
