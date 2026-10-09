---
title: Java SDK（Spring Boot）
description: 用 grantforge-spring-boot-starter 校验 API 权限、判断资源，并把数据权限变成 JPA 查询条件。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` 只依赖 GrantForge 的开放 API，不依赖 GrantForge 的其他模块。

## 依赖与配置

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

starter 默认从当前请求的 `Authorization: Bearer` 头读取用户令牌。令牌保存在会话里的应用（例如 Spring Security OAuth 2.0 登录）提供自己的 `AccessTokenResolver` bean：

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge 要求所有客户端使用 PKCE；Spring Security 登录时用 `OAuth2AuthorizationRequestCustomizers.withPkce()` 打开它（见 `samples/notes`）。

## API 权限

```java
@RequirePermission("orders.delete")      // 方法或类上；全部编码都需要
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

没有令牌或令牌失效返回 401，缺权限返回 403（RFC 9457 problem details）。代码中检查用 `GrantForge`：

```java
UserAuthorization user = grantForge.current();   // accountId、tenantId、username、roles、resources、permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## 数据权限

在 JPA 实体上声明，starter 启动时用机密客户端把实体声明给 GrantForge，之后即可在角色的数据权限中为 `<应用编码>:order` 配置策略：

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

查询时把用户的数据范围当作普通 `Specification` 组合：

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // 范围外的行视为不存在
```

语义与 GrantForge 控制台一致：有允许规则且没有命中拒绝规则的行可用；没有规则就什么都不可用；“我的部门及以下”由 GrantForge 展开成部门 ID 列表。暂不支持“行属于归属人所在部门”。


完整示例见 [示例应用](/integration/samples/) 中的 `samples/shop`（浏览器 + 资源服务器）与 `samples/notes`（Spring Security 登录）。
