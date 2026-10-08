---
title: SDK Java (Spring Boot)
description: Use o grantforge-spring-boot-starter para verificar permissões de API, determinar recursos e converter permissões de dados em condições de consulta JPA.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O `grantforge-spring-boot-starter` depende apenas da API aberta do GrantForge, não de nenhum outro módulo do GrantForge.

## Dependência e configuração

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

Por padrão, o starter lê o token de usuário do cabeçalho `Authorization: Bearer` da requisição atual. Aplicações que mantêm o token na sessão (por exemplo, com o login OAuth 2.0 do Spring Security) fornecem seu próprio bean `AccessTokenResolver`:

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

O GrantForge exige que todos os clientes usem PKCE; ao fazer login com o Spring Security, ative-o com `OAuth2AuthorizationRequestCustomizers.withPkce()` (ver `samples/notes`).

## Permissões de API

```java
@RequirePermission("orders.delete")      // no método ou na classe; todos os códigos são obrigatórios
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

Sem token ou com token inválido, o retorno é 401; sem a permissão necessária, o retorno é 403 (problem details do RFC 9457). Para verificar no código, use `GrantForge`:

```java
UserAuthorization user = grantForge.current();   // accountId, tenantId, username, roles, resources, permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## Permissões de dados

Elas são declaradas sobre a entidade JPA e, na inicialização, o starter declara a entidade ao GrantForge usando o cliente confidencial; depois disso, é possível configurar políticas para `<código-da-aplicação>:order` nas permissões de dados de um papel:

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // ID da conta GrantForge; o escopo "somente eu" o compara
    private Long unitId;           // ID do departamento GrantForge; os escopos de departamento o comparam
    private String tenantId;       // opcional: depois de mapeado, todas as regras exceto "todos" exigem o mesmo tenant
    @GrantForgeField("Status") private Status status;   // campos que a condição pode avaliar: texto, número, booleano, enum (opções), data
    @GrantForgeField("Total")  private long total;
}
```

Ao consultar, combine o escopo de dados do usuário como mais um `Specification`:

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // uma linha fora do escopo é tratada como inexistente
```

A semântica é a mesma do console GrantForge: uma linha é utilizável se cumprir uma regra de permissão e não cumprir nenhuma regra de negação; se não houver regras, nada é utilizável; "meu departamento e subordinados" é expandido pelo GrantForge em uma lista de IDs de departamento. Ainda não há suporte para "a linha pertence ao departamento do proprietário".


Os exemplos completos estão em [Aplicações de exemplo](/pt-br/integration/samples/): `samples/shop` (navegador + servidor de recursos) e `samples/notes` (login com Spring Security).
