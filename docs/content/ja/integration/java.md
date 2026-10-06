---
title: Java SDK（Spring Boot）
description: grantforge-spring-boot-starter で API 権限を検証し、リソースを判定し、データ権限を JPA のクエリ条件に変換します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` は GrantForge のオープン API にのみ依存し、GrantForge の他のモジュールには依存しません。

## 依存と設定

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

starter はデフォルトで現在のリクエストの `Authorization: Bearer` ヘッダーからユーザートークンを読み取ります。トークンをセッションに保存するアプリケーション（例：Spring Security OAuth 2.0 ログイン）では、独自の `AccessTokenResolver` Bean を提供してください。

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge はすべてのクライアントに PKCE の使用を求めます。Spring Security ログイン時は `OAuth2AuthorizationRequestCustomizers.withPkce()` で有効にしてください（`samples/notes` 参照）。

## API 権限

```java
@RequirePermission("orders.delete")      // メソッドまたはクラスに付与。列挙したコードがすべて必要
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

トークンがないか失効しているときは 401、権限がないときは 403 を返します（RFC 9457 problem details）。コード内で直接判定するときは `GrantForge` を使います。

```java
UserAuthorization user = grantForge.current();   // accountId, tenantId, username, roles, resources, permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## データ権限

JPA エンティティに宣言すると、starter は起動時に機密クライアントでエンティティを GrantForge に宣言します。その後はロールのデータ権限で `<アプリケーションコード>:order` にポリシーを設定できます。

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // GrantForge のアカウント ID。「本人のみ」範囲がこの値を比較します
    private Long unitId;           // GrantForge の部門 ID。部門範囲がこの値を比較します
    private String tenantId;       // 任意：マッピングすると、「全テナント」を除くルールはすべて同じテナントを求めます
    @GrantForgeField("Status") private Status status;   // 条件でテストできるフィールド：テキスト、数値、ブール、列挙（選択肢）、日時
    @GrantForgeField("Total")  private long total;
}
```

クエリするときは、ユーザーのデータ範囲を通常の `Specification` と同じように組み合わせます。

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // 範囲外の行は存在しないものとして扱います
```

意味は GrantForge コンソールと同じです。許可ルールがあり、かつ拒否ルールに一致しない行は使えます。ルールがなければどの行も使えません。「自部門および配下の部門」は GrantForge が部門 ID のリストに展開します。行が所有者の所属部門に属するケースはまだ対応していません。


`samples/shop`（ブラウザ + リソースサーバー）と `samples/notes`（Spring Security ログイン）の完全なサンプルは [サンプルアプリケーション](/ja/integration/samples/) を参照してください。
