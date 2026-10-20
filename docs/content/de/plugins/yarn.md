---
title: Apache Hadoop YARN
description: Das YARN-Dienst-Plug-in installieren und konfigurieren, einen ResourceManager oder ein hochverfügbares Paar erreichen, sich einfach oder mit Kerberos anmelden, Warteschlangen und Anwendungsnamen nachschlagen und verwalten, wer welche Anwendungen wohin übermitteln darf.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das YARN-Plug-in schlägt Warteschlangen und die Namen der Anwendungen darin nach und verwaltet Zugriffsrichtlinien für das, was YARN entscheidet: Anwendungen an Warteschlangen übermitteln, Warteschlangen verwalten und den Cluster verwalten. Plug-in-ID und Diensttyp sind beide `yarn`. Das Dienst-Plug-in läuft im GrantForge-Server; der in den ResourceManagern installierte [YARN-Agent](/de/external/yarn-agent/) setzt die Richtlinien durch.

Die Testcontainers-Integrationstests des Repositorys decken die ResourceManager von Hadoop `3.2.4`, gebaut aus der SHA-512-geprüften Apache-Version, `3.3.6`, `3.4.3` und `3.5.0` ab sowie einen Kerberos-ResourceManager von `3.4.3`; das zertifiziert nicht jede andere Version.

## Installation

Die Distribution enthält das Plug-in in `plugins/yarn`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `yarn` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit dem ResourceManager über Hadoops eigenen YARN-Client auf Hadoops geshadetem Client 3.5.0, in einem eigenen Klassenlader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Apache Hadoop YARN (`yarn`).
2. Geben Sie die Client-Adresse des ResourceManagers ein, oder die jedes ResourceManagers eines hochverfügbaren Clusters.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie ihn ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Cluster: wie viele NodeManager und wie viele Warteschlangen er hat.

Die Einstellungen tragen Hadoops eigene Namen und lassen sich aus einer `yarn-site.xml` übernehmen. Der Lookup-Benutzer lässt GrantForge Ressourcen entdecken; er ist von den Benutzern getrennt, die Anwendungen übermitteln. Ein erfolgreicher Verbindungstest bedeutet nicht, dass Übermittlungen bereits Richtlinien unterliegen.

| Einstellung | Zweck |
| --- | --- |
| `yarn.resourcemanager.address` | Erforderlich: der ResourceManager, `host` oder `host:port`, Standardport `8032`; mehrere, durch Kommas getrennt, für einen hochverfügbaren Cluster, zwischen denen der Client umschaltet |
| `hadoop.security.authentication` | `simple` (Standard): ein Benutzername; `kerberos`: ein Prinzipal mit Keytab oder Passwort |
| `username` | Erforderlicher Lookup-Benutzer; mit Kerberos der vollständige Prinzipal, etwa `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: das Passwort des Prinzipals, sofern kein Keytab angegeben ist; verschlüsselt gespeichert |
| `keytab` | Kerberos: Pfad zum Keytab des Prinzipals, lesbar auf dem **GrantForge-Server** |
| `kerberos.kdc` | Kerberos: KDCs des Realms des Prinzipals, `host[:port]`, durch Kommas getrennt; leer verwendet die `krb5.conf` des Servers |
| `yarn.resourcemanager.principal` | Kerberos: der Prinzipal der ResourceManager, Standard `yarn/_HOST@` und der Realm des Lookup-Prinzipals |
| `hadoop.rpc.protection` | `authentication` (Standard), `integrity` oder `privacy`, wie im Cluster |
| `timeout` | Wie lange eine Anfrage dauern darf, Standard `30000`, Bereich `1..600000` Millisekunden; YARNs eigener Client würde eine Viertelstunde lang wiederholen |

Der Dienst ist genau das, was seine Einstellungen sagen: Keine `core-site.xml` oder `yarn-site.xml` des GrantForge-Servers wird gelesen. Die Kerberos-Einstellungen werden bei einfacher Authentifizierung abgelehnt; Kerberos braucht ein Passwort oder ein Keytab. Ein ResourceManager, der die Anmeldung ablehnt, etwa weil er Kerberos erwartet, wird als abgelehnte Anmeldung gemeldet.

### Kerberos

Mit `kerberos` meldet GrantForge den Prinzipal über Hadoops eigene Anmeldung an, aus dem Keytab oder mit dem Passwort, behält die Anmeldung und erneuert sie aus dem Keytab; sonst braucht nichts auf dem GrantForge-Server Kerberos-Einstellungen. Realms, deren KDCs in `kerberos.kdc` eingetragen sind, werden für den ganzen GrantForge-Server deklariert, wie es die HDFS-, Hive-, Kafka-, HBase- und Trino-Plug-ins tun, sodass mehrere Dienste verschiedene Realms nebeneinander nutzen können.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `queue` | Platzhalter, mit Groß-/Kleinschreibung; der Pfad der Warteschlange von der Wurzel, etwa `root.sales.etl` | `submit_app`, `admin_queue` |
| `application` | Platzhalter, mit Groß-/Kleinschreibung; unter einer Warteschlange; der Name der Anwendung | `submit_app` |
| `cluster` | Sein einziger Wert, `*` | `admin`: YARNs Verwaltung, etwa Warteschlangen, Knoten und Knotenlabels neu laden |

`admin_queue` umfasst `submit_app`, da die Verwalter einer Warteschlange an sie übermitteln dürfen.

Wie in YARN umfasst eine Berechtigung auf einer Warteschlange die Warteschlangen darunter: `submit_app` auf `root.sales` übermittelt an `root.sales.etl`. Eine Prüfung wird in jeder Warteschlange des Pfads entschieden, von der Wurzel an, und eine Verweigerung in einer davon lehnt sie ab; so kann eine Richtlinie `root.sales` erlauben und `root.sales.adhoc` verweigern. Unter einer Warteschlange nennt eine Anwendungsebene, für welche Anwendungen eine Berechtigung gilt: `submit_app` auf `root.hr` mit Anwendung `payroll-*` lässt nur Anwendungen zu, deren Name mit `payroll-` beginnt, in `root.hr` und den Warteschlangen darunter.

In Richtlinienwerten passt `*` auf beliebig viele Zeichen und `?` auf eines. Der Richtlinieneditor schlägt Warteschlangen nach ihrem Pfad vor, oder nach ihrem eigenen Namen, wenn dieser getippt wurde, gleich welchen Scheduler der Cluster nutzt, sowie die Namen der Anwendungen, die der ResourceManager in den von einer Richtlinie genannten Warteschlangen noch kennt.

### Client-Adressen

Ein Richtlinieneintrag kann nur für einige Clients gelten: Seine Bedingung **Client-Adressen** (`ip-range`) nimmt Adressen, Bereiche wie `10.1.0.0/16` und Hostnamen, wie Hadoops eigene Hostlisten. Hinter einem Proxy, etwa YARNs Web-Proxy oder einem Router, ist der Client die erste Adresse, die die Proxys weitergeleitet haben. Adressen und Bereiche sind besser als Hostnamen, die eine Namensauflösung des Clients kosten. YARN fragt nach seiner Verwaltung ohne die Adresse des Clients, daher gilt die Bedingung auf `cluster` nie.

## Richtlinienbeispiel

Angenommen, der Cluster betreibt die Warteschlangen `root.sales.etl`, `root.sales.adhoc` und `root.hr`. Legen Sie im selben Datendienst diese aktivierten Zugriffsrichtlinien an:

| Ressourcen | Regel |
| --- | --- |
| `queue=root.sales` | `submit_app` für Gruppe `analysts` erlauben |
| `queue=root.sales.adhoc` | `submit_app` für Benutzer `alice` verweigern |
| `queue=root.hr`, `application=payroll-*` | `submit_app` für Benutzer `bob` erlauben |
| `queue=root.sales` | `admin_queue` für Benutzer `carol` erlauben |
| `queue=root` | `submit_app` für Gruppe `etl-robots` erlauben, aus `10.0.0.0/8` |
| `cluster=*` | `admin` für Gruppe `yarn-admins` erlauben |

Sobald der Agent den neuen Snapshot anwendet, übermitteln Mitglieder von `analysts` an `root.sales.etl` und `root.sales.adhoc`, `alice` aber nur an `root.sales.etl`; `bob` übermittelt Gehaltsanwendungen an `root.hr` und sonst nichts; `carol` übermittelt an die Vertriebswarteschlangen und bricht deren Anwendungen ab; Mitglieder von `etl-robots` übermitteln überallhin, aber nur aus dem internen Netz; und Mitglieder von `yarn-admins` laden die Warteschlangen neu.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Um sie durchzusetzen, braucht es den YARN-Agent in jedem ResourceManager. Bereitstellung und Prüfungen beschreibt der [YARN-Agent](/de/external/yarn-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [YARN-Agent](/de/external/yarn-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
