---
title: Apache HBase
description: Das HBase-Dienst-Plug-in installieren und konfigurieren, den Cluster über ZooKeeper oder Bootstrap-Server finden, einfach oder mit Kerberos anmelden, Namespaces, Tabellen und Spaltenfamilien nachschlagen und Zugriffsrichtlinien in HBases eigenen Geltungsbereichen verwalten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das HBase-Plug-in schlägt Namespaces, Tabellen und Spaltenfamilien nach und verwaltet Zugriffsrichtlinien in den Geltungsbereichen von HBases eigenen Berechtigungen: global, Namespaces, Tabellen, Spaltenfamilien und Spalten. Plug-in-ID und Diensttyp lauten beide `hbase`. Das Dienst-Plug-in läuft im GrantForge-Server; der [HBase-Agent](/de/external/hbase-agent/), installiert in den Mastern und Region-Servern, setzt die Richtlinien durch.

Die Testcontainers-Integrationstests des Repositorys decken HBase `2.5.16`, `2.6.7` und `3.0.0` ab, gebaut aus den per SHA-512 geprüften Apache-Releases, dazu einen Kerberos-Cluster mit `2.6.7`; das zertifiziert nicht jede andere Version.

## Installation

Die Distribution enthält das Plug-in in `plugins/hbase`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `hbase` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit dem Cluster über HBases eigenen Client (`hbase-shaded-client-byo-hadoop` 2.6.7) auf Hadoops shaded Client 3.5.0, in einem eigenen Class Loader, und funktioniert so mit HBase 2.x und 3.x.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Apache HBase (`hbase`).
2. Geben Sie das ZooKeeper-Quorum an oder die Bootstrap-Server eines Clusters ab HBase 2.5.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie ihn ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Cluster: seine ID, sein Release und wie viele Region-Server laufen.

Die Einstellungen tragen HBases eigene Client-Namen und lassen sich aus einer `hbase-site.xml` übernehmen. Der Lookup-Benutzer lässt GrantForge Ressourcen entdecken; er ist von den Clients getrennt, die lesen und schreiben. Ein erfolgreicher Verbindungstest bedeutet nicht, dass Anfragen bereits Richtlinien unterliegen.

| Einstellung | Zweck |
| --- | --- |
| `hbase.zookeeper.quorum` | ZooKeeper-Hosts, `host` oder `host:port` durch Kommas getrennt, etwa `zk1,zk2,zk3:2181`; erforderlich, wenn keine Bootstrap-Server angegeben sind |
| `hbase.zookeeper.property.clientPort` | Der Port von Quorum-Hosts ohne eigenen Port, Standard `2181` |
| `zookeeper.znode.parent` | Die Wurzel des Clusters in ZooKeeper, Standard `/hbase` |
| `hbase.client.bootstrap.servers` | Statt ZooKeeper, ab HBase 2.5: Master oder Region-Server, `host:port` durch Kommas getrennt |
| `hbase.security.authentication` | `simple` (Standard): ein Benutzername; `kerberos`: ein Principal mit Keytab oder Passwort |
| `username` | Erforderlicher Lookup-Benutzer; mit Kerberos der vollständige Principal, etwa `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: das Passwort des Principals, wenn keine Keytab angegeben ist; verschlüsselt gespeichert |
| `keytab` | Kerberos: Pfad zur Keytab des Principals, lesbar auf dem **GrantForge-Server** |
| `kerberos.kdc` | Kerberos: KDCs des Realms des Principals, `host[:port]` durch Kommas getrennt; leer verwendet die `krb5.conf` des Servers |
| `hbase.master.kerberos.principal` | Kerberos: der Principal der Master, Standard `hbase/_HOST@` und der Realm des Lookup-Principals |
| `hbase.regionserver.kerberos.principal` | Kerberos: der Principal der Region-Server, Standard der der Master |
| `hbase.rpc.protection` | `authentication` (Standard), `integrity` oder `privacy`, wie im Cluster |
| `hbase.rpc.timeout` | RPC-Timeout, Standard `30000`, Bereich `1..600000` |

Ein Dienst ist genau das, was seine Einstellungen sagen: Keine `hbase-site.xml` des GrantForge-Servers wird gelesen. Die Kerberos-Einstellungen werden bei einfacher Authentifizierung abgelehnt; Kerberos braucht ein Passwort oder eine Keytab. Lookups geben nach wenigen Versuchen auf statt nach HBases Minuten.

### Kerberos

Mit `kerberos` meldet GrantForge den Principal über Hadoops eigene Anmeldung an, mit der Keytab oder dem Passwort, behält die Anmeldung und erneuert sie aus der Keytab; sonst braucht der GrantForge-Server keine Kerberos-Einstellungen. Realms, deren KDCs in `kerberos.kdc` eingetragen sind, werden für den ganzen GrantForge-Server deklariert, wie es die HDFS- und Hive-Plug-ins tun, sodass mehrere Dienste verschiedene Realms nebeneinander nutzen können.

Ein Cluster schließt die Verbindung eines Clients, der sich anders anmeldet als erwartet, ohne zu sagen warum. Das Plug-in meldet das als abgelehnte Anmeldung und verweist auf `hbase.security.authentication` und `hbase.rpc.protection`, die zu denen des Clusters passen müssen.

### Was der Lookup-Benutzer sieht

Sobald der [HBase-Agent](/de/external/hbase-agent/) die Richtlinien durchsetzt, lassen Listen die Namespaces und Tabellen weg, die der Lookup-Benutzer nicht nutzen darf: Geben Sie ihm eine Richtlinie, die `read` auf Namespace `*` erlaubt, oder nennen Sie ihn in `hbase.superuser` des Clusters.

## Ressourcen und Zugriffsarten

| Ebene | Vergleicht | Zugriffsarten |
| --- | --- | --- |
| `global` | Ihren einen Wert, `*` | `admin`: Operationen auf dem ganzen Cluster, etwa Balancer, Quotas und Replikation |
| `namespace` | Platzhalter, mit Groß- und Kleinschreibung | `read`, `write`, `create`, `admin` |
| `table` | Platzhalter, mit Groß- und Kleinschreibung; unter einem Namespace, ohne ihn benannt | `read`, `write`, `create`, `admin` |
| `column_family` | Platzhalter, mit Groß- und Kleinschreibung; unter einer Tabelle | `read`, `write` |
| `column` | Platzhalter, mit Groß- und Kleinschreibung; unter einer Spaltenfamilie; als UTF-8-Text benannt | `read`, `write` |

Die Zugriffsarten sind HBases: `read`, `write`, `create` und `admin`. HBases `exec` für Coprocessor-Endpunkte wird nicht angeboten, weil der Agent Endpunkt-Aufrufe nicht in jedem HBase-Release prüfen kann.

Wie in HBase deckt eine Berechtigung alles in ihrem Geltungsbereich ab: `read` auf Namespace `sales` liest jede Tabelle darin, `write` auf einer Spaltenfamilie schreibt alle ihre Spalten. Eine Anfrage wird in jedem Geltungsbereich entschieden, den sie nennt, und eine Verweigerung in irgendeinem lehnt sie ab; so kann eine Richtlinie eine Tabelle erlauben und eine ihrer Spalten verweigern. Der globale Geltungsbereich ist der Cluster selbst, nicht jede Tabelle; Richtlinien auf Namespace `*` decken jede Tabelle ab.

In Richtlinienwerten steht `*` für beliebig viele Zeichen und `?` für genau eines. Der Richtlinieneditor schlägt Namespaces vor, Tabellen in den Namespaces, die eine Richtlinie nennt, und Spaltenfamilien ihrer Tabellen; Spalten enthalten Daten und werden nicht vorgeschlagen.

## Richtlinienbeispiel

Angenommen, der Namespace `sales` hat eine Tabelle `orders` mit den Spaltenfamilien `d`, die `name` und `ssn` enthält, und `meta`. Legen Sie im selben Datendienst diese aktivierten Zugriffsrichtlinien an:

| Ressourcen | Regel |
| --- | --- |
| `namespace=sales` | `read` für Benutzer `alice` erlauben |
| `namespace=sales`, `table=orders`, `column_family=d` | `read` für Benutzer `bob` erlauben |
| `namespace=sales`, `table=orders` | `read` für Gruppe `auditors` erlauben |
| `namespace=sales`, `table=orders`, `column_family=d`, `column=ssn` | `read` für Gruppe `auditors` verweigern |
| `namespace=analytics` | `create` und `admin` für Benutzer `dave` erlauben |

Sobald der Agent den neuen Snapshot anwendet, liest `alice` jede Tabelle in `sales`; `bob` liest `orders` und erhält nur die Spalten von `d` und sieht beim Auflisten der Tabellen nur `orders`; Mitglieder von `auditors` lesen ganz `orders` außer `d:ssn`; `dave` legt den Namespace `analytics` und seine Tabellen an, verwaltet sie, und Tabellen anderswo werden ihm verweigert.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Um sie durchzusetzen, braucht jeder Master und Region-Server den HBase-Agenten. Bereitstellung und Prüfungen beschreibt der [HBase-Agent](/de/external/hbase-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [HBase-Agent](/de/external/hbase-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
