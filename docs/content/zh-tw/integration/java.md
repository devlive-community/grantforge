---
title: Java SDK（Spring Boot）
description: 用 grantforge-spring-boot-starter 驗證 API 權限、識別資源，並把資料權限變成 JPA 查詢條件。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` 僅依賴 GrantForge 的開放 API，不依賴 GrantForge 的其他模組。

## 依賴與設定

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

starter 預設從目前請求的 `Authorization: Bearer` 標頭讀取使用者權杖。權杖儲存在工作階段裡的應用程式（例如 Spring Security OAuth 2.0 登入）提供自己的 `AccessTokenResolver` bean：

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge 要求所有客戶端使用 PKCE；Spring Security 登入時用 `OAuth2AuthorizationRequestCustomizers.withPkce()` 開啟它（見 `samples/notes`）。

## API 權限

```java
@RequirePermission("orders.delete")      // 方法或類別上；全部編碼都需要
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

沒有權杖或權杖失效傳回 401，缺少權限傳回 403（RFC 9457 problem details）。程式碼中檢查用 `GrantForge`：

```java
UserAuthorization user = grantForge.current();   // accountId、tenantId、username、roles、resources、permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## 資料權限

在 JPA 實體上宣告，starter 啟動時用機密客戶端把實體宣告給 GrantForge，之後即可在角色的資料權限中為 `<應用程式編碼>:order` 設定策略：

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // GrantForge 帳號 ID，「本人」範圍比較它
    private Long unitId;           // GrantForge 部門 ID，部門範圍比較它
    private String tenantId;       // 可選：對應後，除「全部」外的規則都要求相同租戶
    @GrantForgeField("Status") private Status status;   // 條件可測試的欄位：文字、數字、布林、列舉（選項）、時間
    @GrantForgeField("Total")  private long total;
}
```

查詢時把使用者的資料範圍當作一般 `Specification` 組合：

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // 範圍外的列視為不存在
```

語意與 GrantForge 主控台一致：有允許規則且沒有命中拒絕規則的列可用；沒有規則就什麼都不可用；「我的部門及以下」由 GrantForge 展開成部門 ID 清單。暫不支援「列屬於歸屬人所在部門」。


完整範例見 [範例應用程式](/zh-tw/integration/samples/) 中的 `samples/shop`（瀏覽器 + 資源伺服器）與 `samples/notes`（Spring Security 登入）。
