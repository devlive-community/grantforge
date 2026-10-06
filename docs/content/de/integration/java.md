---
title: Java SDK (Spring Boot)
description: Prüfe API-Berechtigungen und Ressourcen mit `grantforge-spring-boot-starter` und übersetze Datenberechtigungen in JPA-Abfragebedingungen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`grantforge-spring-boot-starter` hängt nur von der offenen API von GrantForge ab, nicht von anderen Modulen von GrantForge.

## Abhängigkeit und Konfiguration

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

Der Starter liest das Benutzer-Token standardmäßig aus dem `Authorization: Bearer`-Header der aktuellen Anfrage. Anwendungen, die das Token in der Sitzung halten (zum Beispiel bei einer Anmeldung mit Spring Security OAuth 2.0), stellen einen eigenen `AccessTokenResolver`-Bean bereit:

```java
@Bean
AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients) {
    return () -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken signedIn
            ? clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName())
                    .getAccessToken().getTokenValue()
            : null;
}
```

GrantForge verlangt von allen Clients PKCE; aktiviere es bei einer Spring-Security-Anmeldung mit `OAuth2AuthorizationRequestCustomizers.withPkce()` (siehe `samples/notes`).

## API-Berechtigungen

```java
@RequirePermission("orders.delete")      // an Methode oder Klasse; für alle Codes erforderlich
@DeleteMapping("/api/orders/{id}")
public void delete(@PathVariable long id) { ... }
```

Ohne Token oder mit abgelaufenem Token wird 401 zurückgegeben, bei fehlender Berechtigung 403 (RFC 9457 problem details). Für Prüfungen im Code dient `GrantForge`:

```java
UserAuthorization user = grantForge.current();   // accountId, tenantId, username, roles, resources, permissions
if (grantForge.hasResource("shop.orders.btn.export")) { ... }
grantForge.require("orders.read", "orders.export");
```

## Datenberechtigungen

Sie werden auf der JPA-Entität deklariert; der Starter meldet die Entität beim Start über den vertraulichen Client an GrantForge. Danach kannst du in den Datenberechtigungen einer Rolle Richtlinien für `<Anwendungscode>:order` festlegen:

```java
@Entity
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class ShopOrder {
    private long ownerId;          // GrantForge-Konto-ID; mit ihr vergleicht der Bereich „eigene Einträge“
    private Long unitId;           // GrantForge-Abteilungs-ID; mit ihr vergleicht der Abteilungsbereich
    private String tenantId;       // optional: nach der Zuordnung verlangen alle Regeln außer „Alle Mandanten“ denselben Mandanten
    @GrantForgeField("Status") private Status status;   // Felder, die Bedingungen testen können: Text, Zahl, Boolean, Enum (Optionen), Zeit
    @GrantForgeField("Total")  private long total;
}
```

Füge den Datenbereich des Benutzers bei Abfragen wie ein gewöhnliches `Specification` zusammen:

```java
orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ).and(myFilters));
orders.findOne(scopes.scope(ShopOrder.class, DataAction.DELETE).and(byId(id)));   // Zeilen außerhalb des Bereichs gelten als nicht existent
```

Die Semantik deckt sich mit der GrantForge-Konsole: eine Zeile ist verfügbar, wenn sie mindestens eine Erlauben-Regel erfüllt und keine Verweigern-Regel trifft; ohne Regeln ist nichts verfügbar; „Meine Abteilung und darunter“ entwickelt GrantForge zu einer Liste von Abteilungs-IDs. Noch nicht unterstützt wird „Zeile gehört zur Abteilung des Besitzers“.

Vollständige Beispiele findest du in den [Beispielanwendungen](/de/integration/samples/): `samples/shop` (Browser + Ressourcen-Server) und `samples/notes` (Spring-Security-Anmeldung).
