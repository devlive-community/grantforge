---
title: Java SDK (Spring Boot)
description: Используйте grantforge-spring-boot-starter для проверки прав API, контроля ресурсов и превращения прав на данные в условия запросов JPA.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` зависит только от открытого API GrantForge и не требует никаких других модулей GrantForge.

## Зависимость и конфигурация

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

По умолчанию стартовый пакет считывает пользовательский токен из заголовка `Authorization: Bearer` текущего запроса. Приложения, хранящие токен в сессии (например, при входе через Spring Security OAuth 2.0), предоставляют собственный бин `AccessTokenResolver`:

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge требует PKCE для всех клиентов; включите его для входа через Spring Security с помощью `OAuth2AuthorizationRequestCustomizers.withPkce()` (см. `samples/notes`).

## Права API

```java
@RequirePermission("orders.delete")      // на методе или классе; нужны все коды
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

Отсутствующий или недействительный токен даёт 401, отсутствие права — 403 (problem details по RFC 9457). Для проверок в коде используйте `GrantForge`:

```java
UserAuthorization user = grantForge.current();   // accountId、tenantId、username、roles、resources、permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## Права на данные

Объявите их на JPA-сущности; при старте стартовый пакет регистрирует сущность в GrantForge с помощью конфиденциального клиента, после чего в правах на данные роли можно настроить политики для `<код приложения>:order`:

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // ID учётной записи GrantForge, его сравнивает область «только свои»
    private Long unitId;           // ID подразделения GrantForge, его сравнивают области подразделений
    private String tenantId;       // необязательно: после сопоставления все правила, кроме «Все», требуют того же арендатора
    @GrantForgeField("Status") private Status status;   // поле, которое могут проверять условия: текст, число, логическое, перечисление (варианты), время
    @GrantForgeField("Total")  private long total;
}
```

При выполнении запроса объединяйте область данных пользователя как обычный `Specification`:

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // строки вне области считаются отсутствующими
```

Семантика совпадает с консолью GrantForge: строка доступна, когда действует правило «разрешить» и ни одно правило «запретить» не совпадает; если правил нет вовсе, ничего не доступно; «моё подразделение и ниже» разворачивается GrantForge в список идентификаторов подразделений. Правило «строка принадлежит подразделению владельца» пока не поддерживается.


Для полных примеров см. [Примеры приложений](/ru/integration/samples/): `samples/shop` (браузер + resource server) и `samples/notes` (вход через Spring Security).
