---
title: Beispielanwendungen
description: "Zwei vollständige Beispiele im Repository: ein Shop mit Direktanmeldung im Browser und eine Notiz-App mit Anmeldung über den Server."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Unter `samples/` liegen zwei lauffähige Anwendungen. Sie werden zusammen mit GrantForge von den End-to-End-Tests abgedeckt und sind die beste Referenz für eine Integration.

| Beispiel | Port | Gezeigte Integrationsweise |
| --- | --- | --- |
| `samples/shop` | 19081 | Der Browser meldet sich mit einem öffentlichen Client + PKCE über Ursprungsgrenzen hinweg an; Schaltflächen werden nach Ressource angezeigt; das Backend prüft die API mit `@RequirePermission`; `@GrantForgeEntity` deklariert die Entität „Bestellung“ und fragt sie im Datenbereich „eigene“ bzw. „eigener Mandant“ ab |
| `samples/notes` | 19082 | Der Server meldet sich mit Spring Security `oauth2Login` (vertraulicher Client + PKCE) an; ein eigenes `AccessTokenResolver` holt das Token aus der Sitzung; „Notiz schreiben“ wird nur für Berechtigte angezeigt |

## Ausführen

Die Beispiele sind eigenständige Maven-Builds und hängen vom Starter und vom JavaScript SDK dieses Repositorys ab:

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

Die Konfiguration der Beispiele kommt vollständig aus Umgebungsvariablen:

| Variable | Beschreibung |
| --- | --- |
| `GRANTFORGE_URL` | Die Adresse von GrantForge |
| `SHOP_BROWSER_CLIENT_ID` | Der öffentliche Client des Shops im Browser |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | Der vertrauliche Client, mit dem das Shop-Backend die Datenentität deklariert (`catalog` Scope) |
| `SHOP_SDK_DIRECTORY` | Das Verzeichnis mit den Build-Artefakten des JavaScript SDK (`sdk/grantforge-js/dist`) |

In GrantForge müssen für beide Anwendungen Ressourcen, Clients, Rollen und Datenrichtlinien angelegt sein. Das Vorbereitungsskript der End-to-End-Tests, `core/grantforge-web/tests/samples/setup.ts`, zeigt diese Schritte vollständig und kann direkt als Vorlage dienen.

## End-to-End-Tests

`script/ci/e2e_fullstack.sh` baut und startet die beiden Beispiele nach den Fullstack-Tests und prüft: die PKCE-Anmeldung über Ursprungsgrenzen hinweg und CORS, das Ein- und Ausblenden von Schaltflächen, 403 an Schnittstellen, die Datenbereiche „eigene“ und „eigener Mandant“, das Löschen und das Abmelden sowie die Server-Anmeldung der Notiz-App. Mit `GRANTFORGE_E2E_SKIP_SAMPLES=1` wird das übersprungen.
