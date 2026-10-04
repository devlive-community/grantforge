<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

# GrantForge samples

Two applications that integrate with GrantForge the way third parties do:

- `shop`: a Spring Boot back end with the Spring Boot starter (`@RequirePermission`, data scopes on a JPA entity declared
  with `@GrantForgeEntity`) serving a browser front end that signs users in with `@grantforge/client` (public client, PKCE)
  from its own origin.
- `notes`: a server-side application that signs users in with Spring Security's OAuth 2.0 login (confidential client, PKCE)
  and hands the user's token to the starter.

They are built apart from GrantForge, once the starter and the JavaScript client are built:

```bash
./mvnw -N install && ./mvnw -pl sdk/grantforge-spring-boot-starter install -DskipTests
./mvnw -f samples/pom.xml package -DskipTests
bash script/ci/sdk_js.sh install && bash script/ci/sdk_js.sh build
```

`script/ci/e2e_fullstack.sh` runs them against a GrantForge server: `core/grantforge-web/tests/samples/setup.ts` registers
the applications, their resources, clients and roles, starts both jars with the client credentials in the environment
(see each `application.properties`) and adds data policies once the shop has declared its order entity.
