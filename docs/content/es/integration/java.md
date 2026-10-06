---
title: SDK de Java (Spring Boot)
description: Usa grantforge-spring-boot-starter para comprobar los permisos de API, determinar los recursos y convertir los permisos sobre los datos en condiciones de consulta de JPA.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` depende solo de la API abierta de GrantForge, no de ningún otro módulo de GrantForge.

## Dependencia y configuración

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

De forma predeterminada, el starter lee el token de usuario de la cabecera `Authorization: Bearer` de la petición en curso. Las aplicaciones que guardan el token en la sesión (por ejemplo, con el inicio de sesión OAuth 2.0 de Spring Security) proporcionan su propio bean `AccessTokenResolver`:

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge exige que todos los clientes usen PKCE; al iniciar sesión con Spring Security, actívalo con `OAuth2AuthorizationRequestCustomizers.withPkce()` (consulta `samples/notes`).

## Permisos de API

```java
@RequirePermission("orders.delete")      // en el método o en la clase; se necesitan todos los códigos
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

Si no hay token o el token no vale, devuelve 401; si falta el permiso, devuelve 403 (problem details de RFC 9457). Para comprobarlo en el código, usa `GrantForge`:

```java
UserAuthorization user = grantForge.current();   // accountId, tenantId, username, roles, resources, permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## Permisos sobre los datos

Se declara sobre la entidad JPA y, al arrancar, el starter declara la entidad a GrantForge con el cliente confidencial; después ya se pueden configurar políticas para `<código-de-aplicación>:order` en los permisos sobre los datos de un rol:

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // ID de la cuenta de GrantForge; el ámbito "solo yo" lo compara
    private Long unitId;           // ID del departamento de GrantForge; los ámbitos de departamento lo comparan
    private String tenantId;       // opcional: una vez mapeado, todas las reglas salvo "todos" exigen el mismo inquilino
    @GrantForgeField("Status") private Status status;   // campos que la condición puede evaluar: texto, número, booleano, enumerado (opciones) y fecha
    @GrantForgeField("Total")  private long total;
}
```

Al consultar, combina el alcance de datos del usuario como un `Specification` más:

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // una fila fuera del ámbito se trata como si no existiera
```

La semántica es la misma que la de la consola de GrantForge: una fila es utilizable si cumple una regla de permiso y no cumple ninguna regla de denegación; si no hay reglas, no se puede usar nada; "mi departamento y subordinados" lo despliega GrantForge en una lista de ID de departamentos. De momento no se admite "la fila pertenece al departamento de la cuenta propietaria".

Los ejemplos completos están en las [Aplicaciones de ejemplo](/es/integration/samples/): `samples/shop` (navegador + servidor de recursos) y `samples/notes` (inicio de sesión con Spring Security).
