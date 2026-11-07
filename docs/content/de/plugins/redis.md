---
title: Redis
description: Das Redis-Service-Plug-in für Redis und Valkey installieren und konfigurieren, sich mit einem Server, seinen Replikaten oder einem Cluster verbinden, mit einem ACL-Benutzer anmelden, auf Wunsch über TLS, Schlüssel, Kanäle und Knoten nachschlagen und verwalten, wer welche Schlüssel lesen und schreiben, welche Kanäle nutzen und welche Knoten überwachen darf.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Redis-Plug-in schlägt Schlüssel, Kanäle und Knoten von Redis- und Valkey-Deployments nach und verwaltet Zugriffsrichtlinien in den Begriffen von Redis selbst: Schlüssel nach Muster lesen und schreiben, Kanäle abonnieren und in sie veröffentlichen, und jeden Knoten überwachen. Plug-in-ID und Diensttyp sind beide `redis`. Das Service-Plug-in läuft im GrantForge-Server; der [Redis-Agent](/de/external/redis-agent/), der neben dem Deployment läuft, setzt die Richtlinien mit der eigenen ACL von Redis auf jedem Knoten durch.

Die Testcontainers-Integrationstests des Repositorys decken einen Primary mit Replikat von Redis `7.2`, `7.4`, `8.2` und `8.10` und Valkey `8.1` und `9.1` ab, außerdem einen Redis-`8.10`-Cluster. Das zertifiziert nicht jede andere Version.

## Installation

Das Distributionspaket enthält das Plug-in in `plugins/redis`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `redis` aktiviert ist; scannen Sie nach einer Aktualisierung des Plug-in-Verzeichnisses erneut. Das Plug-in spricht über Jedis in einem eigenen Class Loader mit Redis.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Redis (`redis`).
2. Geben Sie einen oder mehrere Knoten an. Der Rest eines Clusters und die Replikate eines Primary werden von ihnen aus gefunden.
3. Geben Sie den Lookup-Benutzer an, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt das Deployment: Redis oder Valkey und die Version, ob es ein einzelner Server, ein Primary mit Replikaten oder ein Cluster ist, und wer angemeldet ist.

Der Lookup-Benutzer lässt GrantForge Ressourcen finden und ist nicht der Benutzer, der liest oder schreibt. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits den Richtlinien folgt.

| Einstellung | Zweck |
| --- | --- |
| `redis.nodes` | Erforderlich: die Knoten als `host:port`, etwa `redis1.example.com:6379`, durch Kommas getrennt und der Reihe nach versucht; ohne Port `6379` |
| `username` | Der Lookup-Benutzer, ein ACL-Benutzer; leer meldet als Standardbenutzer an |
| `password` | Sein Passwort, verschlüsselt gespeichert |
| `tls.mode` | `disable` (Standard), `verify-ca` prüft die Zertifikate der Server, `verify-full` auch ihre Namen |
| `tls.ca.file` | TLS: Pfad auf dem **GrantForge-Server** der PEM-CA-Zertifikate, gegen die die Server geprüft werden. Leer verwendet die der JVM |
| `tls.certificate.file` | TLS, für Server, die Client-Zertifikate verlangen: Pfad auf dem **GrantForge-Server** des PEM-Client-Zertifikats, das auch seinen Schlüssel enthalten kann |
| `tls.key.file` | Pfad auf dem **GrantForge-Server** des PEM-Schlüssels des Zertifikats, wenn er eine eigene Datei hat |
| `tls.key.password` | Das Passwort eines verschlüsselten Schlüssels, verschlüsselt gespeichert |
| `timeout` | Wie lange das Verbinden und ein Befehl dauern dürfen, standardmäßig `10000`, im Bereich `1..600000` Millisekunden |

Eine abgelehnte Anmeldung, ein Lookup-Benutzer ohne nötige Rechte und nicht erreichbare Knoten werden jeweils als solche gemeldet.

### Was der Lookup-Benutzer sieht

Schlüssel werden mit `SCAN` auf jedem Primary gefunden, höchstens einige Runden, damit ein Lookup schnell bleibt, wie viele Schlüssel es auch gibt; Kanäle sind die, die gerade Abonnenten haben. `SCAN` listet jeden Schlüssel unabhängig von den Schlüsselmustern des Benutzers, daher braucht der Lookup-Benutzer kein Recht, Werte zu lesen:

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `key` | Platzhalter, Groß-/Kleinschreibung beachtet. Ein Schlüssel oder ein Muster von Schlüsseln | `read`, `write` |
| `channel` | Platzhalter, Groß-/Kleinschreibung beachtet. Ein Kanal oder ein Muster von Kanälen | `subscribe`, `publish` |
| `node` | Platzhalter, Groß-/Kleinschreibung egal. Die Adresse eines Knotens als `host:port` | `monitor` |

| Zugriffsart | Was Redis erlaubt |
| --- | --- |
| `read` | Die Befehle von `@read` auf dem Schlüssel, `%R~` darauf |
| `write` | Die Befehle von `@write` auf dem Schlüssel, `%W~` darauf, die ihn auch löschen |
| `subscribe` | `SUBSCRIBE`, `PSUBSCRIBE`, `SSUBSCRIBE` auf dem Kanal, `&` |
| `publish` | `PUBLISH`, `SPUBLISH` auf dem Kanal, `&` |
| `monitor` | `INFO`, `DBSIZE`, `LATENCY LATEST`, `LATENCY HISTORY`, `MEMORY STATS` |

In Richtlinienwerten passt `*` auf beliebigen Text und `?` auf ein Zeichen, wie in den eigenen Mustern von Redis. Redis gewährt Schlüssel und Kanäle nach Muster und kann sie dem Agenten nicht auflisten, daher wird der Wert einer Richtlinie als eigenes Muster gewährt, wo die Richtlinien den Benutzer darüber hinweg gleich entscheiden. Ein Muster, innerhalb dessen Richtlinien anders entscheiden, etwa `sales:*`, wenn eine Richtlinie `sales:card:*` verweigert, wird zurückgehalten und gemeldet; nennen Sie stattdessen Schlüssel oder Muster, die die Richtlinien gleich entscheiden. Werte mit eckigen Klammern, Backslashes, runden Klammern oder Leerraum werden nie gewährt.

Befehle, die Redis als gefährlich einstuft, etwa `FLUSHALL` und `KEYS`, werden nie gegeben, und `SCAN` und `RANDOMKEY`, die Schlüssel unabhängig von ihren Mustern auflisten, nur einem Benutzer, der jeden Schlüssel lesen darf. Muster nennen Schlüssel in jeder Datenbank eines Knotens, da die ACL von Redis keine Datenbanken kennt.

Redis filtert nichts innerhalb eines Werts und maskiert nichts, daher bietet dieser Diensttyp beides nicht.

## Richtlinienbeispiel

Legen Sie in einem Datendienst diese aktivierten Richtlinien an:

| Ressource | Regel |
| --- | --- |
| `key=sales:*` | `read` für Gruppe `analysts` erlauben |
| `key=sales:card:*` | `read` und `write` für Benutzer `billing` erlauben |
| `key=cart:*` | `read` und `write` für Benutzer `shop` erlauben |
| `channel=orders.*` | `subscribe` für Gruppe `analysts` erlauben; `publish` für Benutzer `shop` erlauben |
| `node=*` | `monitor` für Benutzer `dashboards` erlauben |

Sobald der Agent den Snapshot angewendet hat, lesen Mitglieder von `analysts` jeden `sales:`-Schlüssel und folgen den Bestellkanälen; `billing` liest und schreibt die Kartenschlüssel, ein Muster, in dem keine andere Richtlinie anders entscheidet; `shop` führt Warenkörbe und veröffentlicht Bestellereignisse; und `dashboards` sieht den Zustand jedes Knotens und keine Daten.

## Richtlinien durchsetzen

Das Service-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Zur Durchsetzung braucht es einen Redis-Agenten je Deployment; Bereitstellung und Prüfungen stehen unter [Redis-Agent](/de/external/redis-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Redis-Agent](/de/external/redis-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
