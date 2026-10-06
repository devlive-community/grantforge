---
title: Java SDK (Spring Boot)
description: Use grantforge-spring-boot-starter to enforce API permissions, check resources, and turn data permissions into JPA query conditions.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` depends only on GrantForge's Open API, not on any other GrantForge module.

## Dependency and configuration

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

By default the starter reads the user token from the current request's `Authorization: Bearer` header. Applications that keep the token in the session (for example Spring Security OAuth 2.0 login) provide their own `AccessTokenResolver` bean:

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge requires PKCE for all clients; enable it for Spring Security login with `OAuth2AuthorizationRequestCustomizers.withPkce()` (see `samples/notes`).

## API permissions

```java
@RequirePermission("orders.delete")      // 方法或类上；全部编码都需要
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

A missing or invalid token returns 401; a missing permission returns 403 (RFC 9457 problem details). For checks in code, use `GrantForge`:

```java
UserAuthorization user = grantForge.current();   // accountId、tenantId、username、roles、resources、permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## Data permissions

Declare it on the JPA entity; at startup the starter registers the entity with GrantForge using the confidential client, after which you can configure policies for `<应用编码>:order` in the role's data permissions:

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // GrantForge 账号 ID，“本人”范围比较它
    private Long unitId;           // GrantForge 部门 ID，部门范围比较它
    private String tenantId;       // 可选：映射后，除“全部”外的规则都要求同租户
    @GrantForgeField("Status") private Status status;   // 条件可测试的字段：文本、数字、布尔、枚举（选项）、时间
    @GrantForgeField("Total")  private long total;
}
```

At query time, combine the user's data scope like an ordinary `Specification`:

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // 范围外的行视为不存在
```

The semantics match the GrantForge console: a row is available when an allow rule applies and no deny rule matches it; with no rules at all, nothing is available; "my department and below" is expanded by GrantForge into a list of department IDs. "Row belongs to the owner's department" is not supported yet.


For complete examples, see [Sample applications](/en/integration/samples/): `samples/shop` (browser + resource server) and `samples/notes` (Spring Security login).
