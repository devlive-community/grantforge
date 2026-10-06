---
title: SDK Java (Spring Boot)
description: Avec grantforge-spring-boot-starter, vérifiez les autorisations d’API et les ressources, et traduisez les autorisations sur les données en critères de requête JPA.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` ne dépend que de l’API ouverte de GrantForge, et d’aucun autre module de GrantForge.

## Dépendances et configuration

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

Par défaut, le starter lit le jeton d’utilisateur dans l’en-tête `Authorization: Bearer` de la requête courante. Les applications qui conservent le jeton en session (par exemple avec la connexion Spring Security OAuth 2.0) fournissent leur propre bean `AccessTokenResolver` :

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge impose PKCE à tous les clients ; lors de la connexion Spring Security, activez-le avec `OAuth2AuthorizationRequestCustomizers.withPkce()` (voir `samples/notes`).

## Autorisations d’API

```java
@RequirePermission("orders.delete")      // sur la méthode ou la classe ; tous les codes sont requis
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

Sans jeton ou avec un jeton invalide, la réponse est 401 ; sans les autorisations requises, elle est 403 (problem details RFC 9457). Pour les vérifications dans le code, utilisez `GrantForge` :

```java
UserAuthorization user = grantForge.current();   // accountId, tenantId, username, roles, resources, permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## Autorisations sur les données

Déclarez-les sur les entités JPA. Au démarrage, le starter déclare les entités à GrantForge avec le client confidentiel ; vous pouvez ensuite configurer des politiques pour `<code d’application>:order` dans les autorisations sur les données des rôles :

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // identifiant de compte GrantForge, la portée « moi » le compare
    private Long unitId;           // identifiant de service GrantForge, les portées de service le comparent
    private String tenantId;       // facultatif : une fois mappé, toutes les règles autres que « tout » exigent le même locataire
    @GrantForgeField("Status") private Status status;   // champs testables par condition : texte, nombre, booléen, énumération (choix), date
    @GrantForgeField("Total")  private long total;
}
```

Lors des requêtes, composez la portée de données de l’utilisateur comme une `Specification` ordinaire :

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // les lignes hors de la portée sont traitées comme inexistantes
```

La sémantique est identique à celle de la console GrantForge : une ligne est accessible si une règle d’autorisation s’applique et qu’aucune règle de refus n’est atteinte ; sans aucune règle, rien n’est accessible ; « mon service et ses subordonnés » est développé par GrantForge en une liste d’identifiants de service. Le cas « la ligne appartient au service du propriétaire » n’est pas encore pris en charge.


Pour un exemple complet, voir les [applications d’exemple](/fr/integration/samples/) : `samples/shop` (navigateur + serveur de ressources) et `samples/notes` (connexion Spring Security).
