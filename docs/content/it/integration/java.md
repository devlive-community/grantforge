---
title: SDK Java (Spring Boot)
description: Usare grantforge-spring-boot-starter per verificare i permessi delle API, determinare le risorse e trasformare i permessi sui dati in condizioni di query JPA.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` dipende solo dall’API aperta di GrantForge e da nessun altro modulo di GrantForge.

## Dipendenza e configurazione

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

Per impostazione predefinita, lo starter legge il token utente dall’intestazione `Authorization: Bearer` della richiesta in corso. Le applicazioni che conservano il token nella sessione (per esempio con l’accesso OAuth 2.0 di Spring Security) forniscono il proprio bean `AccessTokenResolver`:

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge richiede che tutti i client usino PKCE; all’accesso con Spring Security, attivalo con `OAuth2AuthorizationRequestCustomizers.withPkce()` (vedi `samples/notes`).

## Permessi sulle API

```java
@RequirePermission("orders.delete")      // sul metodo o sulla classe; servono tutti i codici
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

Senza token o con token non valido restituisce 401, con permesso mancante restituisce 403 (problem details di RFC 9457). Per la verifica nel codice si usa `GrantForge`:

```java
UserAuthorization user = grantForge.current();   // accountId, tenantId, username, roles, resources, permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## Permessi sui dati

Si dichiara sull’entità JPA e, all’avvio, lo starter dichiara l’entità a GrantForge con il client confidenziale; dopo è possibile configurare le policy per `<codice-applicazione>:order` nei permessi sui dati di un ruolo:

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // ID dell’account GrantForge; l’ambito “solo io” lo confronta
    private Long unitId;           // ID del dipartimento GrantForge; gli ambiti di dipartimento lo confrontano
    private String tenantId;       // opzionale: una volta mappato, tutte le regole salvo “tutti” richiedono lo stesso tenant
    @GrantForgeField("Status") private Status status;   // campi che la condizione può valutare: testo, numero, booleano, enumerato (opzioni), data
    @GrantForgeField("Total")  private long total;
}
```

Al momento della query, combina l’ambito dati dell’utente come un `Specification` qualsiasi:

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // una riga fuori dall’ambito è trattata come inesistente
```

La semantica è uguale a quella della console di GrantForge: una riga è disponibile se soddisfa una regola di consenso e non soddisfa alcuna regola di diniego; in assenza di regole non è disponibile nulla; “il mio dipartimento e i subordinati” viene espanso da GrantForge in un elenco di ID di dipartimento. Per il momento non è supportato “la riga appartiene al dipartimento del proprietario”.


Gli esempi completi sono nelle [Applicazioni di esempio](/it/integration/samples/): `samples/shop` (browser + server di risorse) e `samples/notes` (accesso con Spring Security).
