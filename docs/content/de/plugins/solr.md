---
title: Apache Solr
description: Das Solr-Dienst-Plug-in installieren und konfigurieren, einen oder mehrere Knoten erreichen, sich mit Basic-Authentifizierung oder einem JSON Web Token anmelden, Collections und ihre Anfragepfade suchen und verwalten, wer was abfragen, aktualisieren und administrieren darf.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Solr-Plug-in sucht Collections und die Anfragepfade in ihnen und verwaltet Zugriffsrichtlinien für das, was Solr entscheidet: Abfragen, Aktualisieren, Lesen und Ändern von Schemas und Configs, das Administrieren von Collections und des Clusters. Plug-in-ID und Diensttyp sind beide `solr`. Das Dienst-Plug-in läuft im GrantForge-Server; der [Solr-Agent](/de/external/solr-agent/), in den Knoten installiert, setzt die Richtlinien durch.

Die Testcontainers-Integrationstests des Repositorys decken SolrCloud-Knoten von Solr `9.4.1`, `9.8.1`, `9.11.0` und `10.0.0` ab; das zertifiziert nicht jede andere Version.

## Installation

Die Distribution enthält das Plug-in in `plugins/solr`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `solr` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit den Knoten über den JDK-HTTP-Client von SolrJ 9.11, in einem eigenen Klassenlader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Apache Solr (`solr`).
2. Geben Sie die Basis-URL eines Knotens oder mehrerer Knoten ein.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie seine Anmeldedaten ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Cluster: seine Solr-Version, ob er SolrCloud ausführt und wie viele Collections er hat.

Der Lookup-Benutzer lässt GrantForge Ressourcen entdecken; er ist unabhängig von den Benutzern, die abfragen und aktualisieren. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits Richtlinien unterliegt.

| Einstellung | Zweck |
| --- | --- |
| `solr.urls` | Pflicht: die Basis-URL eines Knotens, etwa `https://solr1.example.com:8983/solr`; mehrere durch Kommas getrennt, der Reihe nach versucht, wenn einer nicht erreichbar ist |
| `authentication` | `none` (Standard), `basic` oder `jwt` |
| `username`, `password` | basic: der Lookup-Benutzer und sein Passwort; das Passwort wird verschlüsselt gespeichert |
| `token` | jwt: das JSON Web Token des Lookup-Benutzers; verschlüsselt gespeichert |
| `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword`, `javax.net.ssl.trustStoreType` | https: der Truststore auf dem **GrantForge-Server**, gegen den die Zertifikate der Knoten geprüft werden, sein Passwort und sein Typ, Standard `PKCS12`; leer nutzt den der JVM |
| `timeout` | Wie lange eine Anfrage dauern darf, Standard `30000`, Bereich `1..600000` Millisekunden |

Die Einstellungen einer Anmeldeart werden bei einer anderen abgelehnt, sodass ein Dienst genau sagt, wie er sich anmeldet. Eine abgelehnte Anmeldung, ein Benutzer, der etwas nicht sehen darf, und ein nicht erreichbarer Knoten, auch hinter einem Proxy, der für ihn antwortet, werden als solche gemeldet.

### Was der Lookup-Benutzer sieht

Collections aufzulisten ist Solrs Berechtigung `collection-admin-read`, und die Anfragepfade einer Collection stammen aus ihrer Config, `config-read`. Geben Sie dem Lookup-Benutzer diese in Solrs eigenen Regeln oder, sobald der Agent installiert ist, `admin_read` auf dem Cluster und `config_read` auf den Collections. Pfade von Collections, die der Lookup-Benutzer nicht sehen darf, fehlen in den Vorschlägen. Ein Knoten ohne SolrCloud hat Cores statt Collections, und das Plug-in führt sie als Collections.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `collection` | Platzhalter, Groß-/Kleinschreibung beachtet; der Name der Collection | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit`, `admin_collection` |
| `path` | Platzhalter, Groß-/Kleinschreibung beachtet; unter einer Collection; der Anfragepfad, etwa `/select`, `/export` oder `/update/json` | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit` |
| `cluster` | Sein einziger Wert, `*` | `health`, `metrics`, `admin_read`, `security`, `admin` |

| Zugriffsart | Solrs Berechtigungen | Impliziert |
| --- | --- | --- |
| `query` | `read`: Suchen, Abrufen, Exportieren | — |
| `update` | `update` | — |
| `schema_read`, `schema_edit` | `schema-read`, `schema-edit` | `schema_edit` impliziert `schema_read` |
| `config_read`, `config_edit` | `config-read`, `config-edit` | `config_edit` impliziert `config_read` |
| `admin_collection` | `collection-admin-read` und `collection-admin-edit` der Collections-API-Anfragen, die die Collection nennen: Anlegen, Neuladen, Sichern, Löschen und Ähnliches | `query`, `update`, `schema_edit`, `config_edit` |
| `health`, `metrics` | `health`, `metrics-read` | — |
| `admin_read` | `collection-admin-read` des ganzen Clusters, `core-admin-read`, `zk-read`, `filestore-read`, `package-read` und das Lesen von Config und Schema der Knoten | `health`, `metrics` |
| `security` | `security-read`, `security-edit` | — |
| `admin` | Alles | `admin_read`, `security`, `admin_collection` |

Eine Anfrage an eine Collection wird an der Collection und an der Collection mit ihrem Anfragepfad entschieden, und eine Ablehnung an einer der beiden verweigert sie; so kann eine Richtlinie eine Collection erlauben und ihren `/export`-Handler verweigern oder Aktualisieren nur über `/update*` erlauben. Eine Anfrage an mehrere Collections braucht jede von ihnen. Unter einer Collection ist der Anfragepfad der Handler, an den Solr die Anfrage leitet, so wie Solr ihn nennt.

Eine Collections-API-Anfrage, die Collections über ihren Parameter `name` oder `collection` nennt, braucht `admin_collection` auf jeder von ihnen; eine Richtlinie auf `team-*` lässt ein Team also Collections anlegen, deren Name mit `team-` beginnt, und sie neu laden, sichern oder löschen, aber keine andere. Eine Anfrage, die keine nennt, etwa das Auflisten aller Collections, gehört dem Cluster. `admin` auf dem Cluster gewährt alles, wie Solrs eigene Berechtigung `all`. In Richtlinienwerten passt `*` auf eine beliebige Zeichenfolge und `?` auf ein Zeichen. Der Richtlinien-Editor schlägt die Collections und die Anfragepfade der Collections vor, die eine Richtlinie nennt.

### Client-Adressen

Ein Richtlinieneintrag kann nur für einige Clients gelten: seine Bedingung **Client-Adressen** (`ip-range`) nimmt Adressen und Bereiche wie `10.1.0.0/16` oder `2001:db8::/32`. Hostnamen werden nicht aufgelöst. Hinter einem Reverse-Proxy ist der Client der Proxy, sofern der Agent ihm nicht vertraut: dann ist der Client die letzte Adresse in `X-Forwarded-For`, die kein vertrauenswürdiger Proxy hinzugefügt hat, sodass ein Client keine Adresse seiner Wahl vorgeben kann.

## Richtlinienbeispiel

Angenommen, der Cluster hat die Collections `sales` und `hr`. Legen Sie im selben Datendienst diese aktivierten Zugriffsrichtlinien an:

| Ressourcen | Regel |
| --- | --- |
| `collection=sales` | `query` für Gruppe `analysts` erlauben |
| `collection=sales`, `path=/export` | `query` für Benutzer `alice` verweigern |
| `collection=sales`, `path=/update*` | `update` für Benutzer `ingest` erlauben |
| `collection=team-*` | `admin_collection` für Gruppe `search-team` erlauben |
| `cluster=*` | `metrics` und `health` für Benutzer `monitoring` erlauben |
| `collection=hr` | `query` für Gruppe `hr-staff` erlauben, aus `10.0.0.0/8` |
| `cluster=*` | `admin` für Gruppe `solr-admins` erlauben |

Sobald der Agent den neuen Snapshot anwendet, fragen Mitglieder von `analysts` `sales` ab, `alice` alles außer seinem Export-Handler; `ingest` aktualisiert `sales` über seine Update-Handler und darf es nicht abfragen; Mitglieder von `search-team` legen ihre eigenen `team-`-Collections an und administrieren sie; `monitoring` liest Metriken und Health; Mitglieder von `hr-staff` fragen `hr` nur aus dem internen Netz ab; und Mitglieder von `solr-admins` dürfen alles.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und sucht Ressourcen. Sie durchzusetzen erfordert den Solr-Agenten in jedem Knoten. Bereitstellung und Prüfungen beschreibt der [Solr-Agent](/de/external/solr-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Solr-Agent](/de/external/solr-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
