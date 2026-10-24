---
title: Apache Pulsar
description: Das Pulsar-Dienst-Plug-in installieren und konfigurieren, einen Broker oder mehrere erreichen, sich mit Token, Passwort, OAuth 2.0 oder Client-Zertifikat anmelden, Tenants, Namespaces, Topics und Subscriptions nachschlagen und verwalten, wer wo produzieren, konsumieren und verwalten darf.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Pulsar-Plug-in schlägt Tenants, Namespaces, Topics, Subscriptions und Cluster nach und verwaltet Zugriffsrichtlinien für das, was Pulsar entscheidet: Produzieren, Konsumieren, Functions, Sources, Sinks und Packages sowie die Verwaltung von Topics, Namespaces, Tenants und Clustern. Plug-in-ID und Diensttyp sind beide `pulsar`. Das Dienst-Plug-in läuft im GrantForge-Server; der in den Brokern installierte [Pulsar-Agent](/de/external/pulsar-agent/) setzt die Richtlinien durch.

Die Testcontainers-Integrationstests des Repositorys decken die Broker von Pulsar `3.0.17`, `3.3.9`, `4.0.14` und `4.2.5` ab; das zertifiziert nicht jede andere Version.

## Installation

Die Distribution enthält das Plug-in in `plugins/pulsar`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `pulsar` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit dem Webdienst der Broker über Pulsars eigenen Admin-Client 4.2.5, in einem eigenen Klassenlader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Apache Pulsar (`pulsar`).
2. Geben Sie die Webdienst-URL eines Brokers oder mehrerer Broker ein.
3. Wählen Sie, wie sich die Lookup-Rolle anmeldet, geben Sie ihre Zugangsdaten ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Cluster: seine Cluster und wie viele Tenants er hat.

Die Einstellungen tragen die Namen von Pulsars eigener `client.conf` und lassen sich daraus übernehmen. Die Lookup-Rolle lässt GrantForge Ressourcen entdecken; sie ist von den Rollen getrennt, die produzieren und konsumieren. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits Richtlinien unterliegt.

| Einstellung | Zweck |
| --- | --- |
| `webServiceUrl` | Erforderlich: der Webdienst eines Brokers, `http://host:port` oder `https://host:port`, Standardport `8080` oder `8443`; mehrere Broker durch Kommas getrennt, etwa `http://broker1:8080,broker2:8080` |
| `authentication` | `none` (Standard), `token`, `basic`, `oauth2` oder `tls` |
| `token` | token: das Token der Lookup-Rolle; verschlüsselt gespeichert |
| `username`, `password` | basic: Benutzername und Passwort der Lookup-Rolle; das Passwort wird verschlüsselt gespeichert |
| `issuerUrl`, `privateKey`, `audience`, `scope` | oauth2: der Autorisierungsserver, der Pfad der JSON-Zugangsdatei mit `client_id` und `client_secret` auf dem **GrantForge-Server**, sowie Audience und Scope, falls die Broker sie erwarten |
| `tlsCertificateFilePath`, `tlsKeyFilePath` | tls: Pfade des Client-Zertifikats und seines privaten Schlüssels, PEM, auf dem **GrantForge-Server** |
| `tlsTrustCertsFilePath` | https: die CA-Zertifikate, gegen die die Zertifikate der Broker geprüft werden, PEM; leer verwendet die der JVM |
| `tlsEnableHostnameVerification` | https: ob das Zertifikat eines Brokers seinen Host nennen muss, Standard `true` |
| `timeout` | Wie lange eine Anfrage dauern darf, Standard `30000`, Bereich `1..600000` Millisekunden |

Die Einstellungen einer Anmeldeart werden bei einer anderen abgelehnt, sodass ein Dienst genau sagt, wie er sich anmeldet. Eine abgelehnte Anmeldung, eine Rolle, die etwas nicht sehen darf, und ein Broker, der nicht erreichbar ist, auch hinter einem Proxy, der für ihn antwortet, werden als solche gemeldet.

### Was die Lookup-Rolle sehen darf

Pulsar lässt nur seine Superuser Cluster und Tenants auflisten. Nennen Sie die Lookup-Rolle in `superUserRoles` der Broker oder erlauben Sie ihr nach der Installation des Agenten `admin` auf dem Cluster, was sie ebenfalls zum Superuser macht. Namespaces, Topics und Subscriptions, die die Lookup-Rolle nicht sehen darf, fehlen in den Vorschlägen.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `tenant` | Platzhalter, mit Groß-/Kleinschreibung | Alle außer `admin` |
| `namespace` | Platzhalter, mit Groß-/Kleinschreibung; unter einem Tenant; der eigene Name des Namespace, etwa `orders` | `produce`, `consume`, `functions`, `sources`, `sinks`, `packages`, `admin_topic`, `admin_namespace` |
| `topic` | Platzhalter, mit Groß-/Kleinschreibung; unter einem Namespace; der eigene Name des Topics, etwa `payments` | `produce`, `consume`, `admin_topic` |
| `subscription` | Platzhalter, mit Groß-/Kleinschreibung; unter einem Topic; der Name der Subscription | `consume` |
| `cluster` | Platzhalter, mit Groß-/Kleinschreibung; der Name des Clusters, etwa `standalone` | `admin`: Pulsars Superuser-Zugriff |

| Zugriffsart | Umfasst |
| --- | --- |
| `produce`, `consume`, `functions`, `sources`, `sinks`, `packages` | — |
| `admin_topic`: Topics beenden, kompaktieren, auslagern, entladen und kürzen und ihre Richtlinien ändern | `produce`, `consume` |
| `admin_namespace`: Topics anlegen und löschen und die Richtlinien des Namespace ändern | `admin_topic` |
| `admin_tenant`: Namespaces anlegen, auflisten und löschen | `admin_namespace`, `functions`, `sources`, `sinks`, `packages` |
| `admin`: alles, was Pulsars Superuser dürfen | `admin_tenant` |

Wie bei Pulsars eigenen Berechtigungen umfasst eine Berechtigung auf einem Tenant oder Namespace, was darin ist: `produce` auf dem Namespace `orders` des Tenants `sales` produziert in jedes Topic darin. Eine Prüfung wird nacheinander im Tenant, im Namespace, im Topic und in der Subscription entschieden, und eine Verweigerung in einem davon lehnt sie ab; so kann eine Richtlinie einen Namespace erlauben und ein Topic darin verweigern. Unter einem Topic nennt eine Subscription-Ebene, über welche Subscriptions eine Rolle konsumieren darf: `consume` auf Topic `*` mit Subscription `billing-*` lässt eine Rolle jedes Topic des Namespace konsumieren, aber nur über Subscriptions, deren Name mit `billing-` beginnt.

Ein Topic wird nach seinem eigenen Namen ohne Domain entschieden, sodass ein persistentes und ein nicht persistentes Topic gleichen Namens ihre Richtlinien teilen; eine Partition eines partitionierten Topics wird als das partitionierte Topic entschieden. In Richtlinienwerten passt `*` auf beliebig viele Zeichen und `?` auf eines. Der Richtlinieneditor schlägt die Cluster, die Tenants sowie die Namespaces, Topics und Subscriptions in dem vor, was eine Richtlinie nennt.

### Client-Adressen

Ein Richtlinieneintrag kann nur für einige Clients gelten: Seine Bedingung **Client-Adressen** (`ip-range`) nimmt Adressen und Bereiche wie `10.1.0.0/16` oder `2001:db8::/32`. Hostnamen werden nicht aufgelöst. Hinter einem Proxy ist der Client der Proxy, sofern er nicht die eigene Adresse des Clients weiterleitet: mit dem HAProxy-Protokoll für Producer und Consumer und mit `X-Forwarded-For` für den Webdienst, wenn die Broker ihm vertrauen.

## Richtlinienbeispiel

Angenommen, der Tenant `sales` hat die Namespaces `orders` und `returns`. Legen Sie im selben Datendienst diese aktivierten Zugriffsrichtlinien an:

| Ressourcen | Regel |
| --- | --- |
| `tenant=sales`, `namespace=orders` | `produce` für Gruppe `analysts` erlauben |
| `tenant=sales`, `namespace=orders`, `topic=audit` | `produce` für Benutzer `alice` verweigern |
| `tenant=sales`, `namespace=orders`, `topic=*`, `subscription=billing-*` | `consume` für Benutzer `alice` erlauben |
| `tenant=sales` | `admin_tenant` für Benutzer `bob` erlauben |
| `tenant=public` | `produce` für Gruppe `ingest` erlauben, aus `10.0.0.0/8` |
| `cluster=standalone` | `admin` für Gruppe `pulsar-admins` erlauben |

Sobald der Agent den neuen Snapshot anwendet, produzieren Mitglieder von `analysts` in jedes Topic von `sales/orders`, `alice` in alle außer `audit`; `alice` konsumiert dort nur über die `billing-`-Subscriptions; `bob` legt Namespaces und Topics in `sales` an und verwaltet sie; Mitglieder von `ingest` produzieren in den Tenant `public`, aber nur aus dem internen Netz; und Mitglieder von `pulsar-admins` sind Pulsars Superuser.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Um sie durchzusetzen, braucht es den Pulsar-Agent in jedem Broker. Bereitstellung und Prüfungen beschreibt der [Pulsar-Agent](/de/external/pulsar-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Pulsar-Agent](/de/external/pulsar-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
