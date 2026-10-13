---
title: Apache Hive
description: Das Hive-Dienst-Plug-in installieren und konfigurieren, mit Passwort oder Kerberos anmelden, Datenbanken, Tabellen, Spalten und UDFs nachschlagen und Zugriffs-, Maskierungs- und Zeilenfilterrichtlinien verwalten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Hive-Plug-in bietet für HiveServer2 das Nachschlagen von Datenbanken, Tabellen, Spalten und permanenten UDFs sowie Zugriffs-, Maskierungs- und Zeilenfilterrichtlinien, nach dem Vorbild des Hive-Dienstes von Apache Ranger. Plug-in-ID und Diensttyp lauten beide `hive`. Das Dienst-Plug-in läuft im GrantForge-Server; der in HiveServer2 installierte [Hive-Agent](/de/external/hive-agent/) setzt die Richtlinien durch.

Die Testcontainers-Integrationstests des Repositorys decken HiveServer2 `3.1.3`, `4.0.1` und `4.1.0` ab, einschließlich einer Kerberos-Anmeldung an `4.0.1`; andere Versionen sind damit nicht zertifiziert.

## Installation

Die Distribution enthält das Plug-in in `plugins/hive`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `hive` aktiviert ist; durchsuchen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht über Hives eigenen JDBC-Treiber (`hive-jdbc` 4.0.1, standalone) in einem eigenen Klassenlader mit HiveServer2 und funktioniert daher mit HiveServer2 3.1 und 4.x gleichermaßen.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Apache Hive (`hive`).
2. Geben Sie die JDBC-URL von HiveServer2 ein, etwa `jdbc:hive2://hiveserver2.example.com:10000/default`. ZooKeeper-Erkennung funktioniert ebenfalls: `jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie ihn ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und listet die Datenbanken, wie es Lookups tun.

Der Lookup-Benutzer dient GrantForge nur zum Erkennen von Ressourcen; er ist unabhängig von den Benutzern, die Abfragen ausführen. Ein erfolgreicher Verbindungstest bedeutet nicht, dass Abfragen bereits Richtlinien unterliegen.

| Einstellung | Zweck |
| --- | --- |
| `jdbc.url` | Erforderliche HiveServer2-URL `jdbc:hive2://Hosts/Datenbank;Sitzungsvariablen`, mit TLS (`;ssl=true;sslTrustStore=…`), dem HTTP-Transport oder bei Kerberos dem Prinzipal des Servers (`;principal=hive/_HOST@EXAMPLE.COM`) |
| `authentication` | `password` (Standard): ein Benutzer mit Passwort, für LDAP, PAM oder ohne Authentifizierung; `kerberos`: ein Prinzipal mit Keytab oder Passwort |
| `username` | Erforderlicher Lookup-Benutzer; bei Kerberos der vollständige Prinzipal, etwa `grantforge@EXAMPLE.COM` |
| `password` | Das Passwort des Benutzers oder, ohne Keytab, das Kerberos-Passwort des Prinzipals; verschlüsselt gespeichert |
| `keytab` | Kerberos: Pfad der Keytab des Prinzipals, lesbar auf dem **GrantForge-Server** |
| `kerberos.kdc` | Kerberos: KDCs des Realms des Prinzipals, `host[:port]` durch Kommas getrennt; leer verwendet die `krb5.conf` des Servers |
| `read.timeout.ms` | Lese-Timeout, Standard `30000`, Bereich `1..600000` |

Die URL darf keine Zugangsdaten enthalten und nichts, was auf dem GrantForge-Server Dateien oder Tokens liest: `user`, `password`, `initFile`, `kerberosAuthType`, `delegationToken`, `storePasswordPath`, `jwt` und die `browser…`-Variablen werden unabhängig von der Schreibweise abgelehnt. `auth` darf nur `noSasl` sein. Ein Kerberos-Dienst braucht `principal` in der URL; ein Passwort-Dienst darf keinen haben.

### Kerberos

Mit `kerberos` meldet GrantForge den Prinzipal selbst mit Keytab oder Passwort an und öffnet die Verbindung als dieser Prinzipal; weitere Kerberos-Einstellungen auf dem GrantForge-Server sind nicht nötig. Realms, deren KDCs in `kerberos.kdc` eingetragen sind, werden für den ganzen GrantForge-Server deklariert, wie beim HDFS-Plug-in, sodass mehrere Hive- und HDFS-Dienste nebeneinander verschiedene Realms verwenden können.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Hinweise |
| --- | --- | --- |
| `database` | Platzhalter, ohne Groß-/Kleinschreibung | Richtlinien dürfen hier enden |
| `table` | Platzhalter, ohne Groß-/Kleinschreibung | Unter einer Datenbank; Richtlinien dürfen hier enden |
| `column` | Platzhalter, ohne Groß-/Kleinschreibung | Unter einer Tabelle |
| `udf` | Platzhalter, ohne Groß-/Kleinschreibung | Permanente Funktionen, unter einer Datenbank |
| `url` | Pfade, mit Groß-/Kleinschreibung, rekursiv | Daten, die Anweisungen lesen oder schreiben, etwa `hdfs://nn:8020/landing`; Zugriffsarten `read` und `write` |
| `hiveservice` | Platzhalter | HiveServer2 selbst; Zugriffsart `serviceadmin` |
| `global` | Platzhalter | Temporäre Funktionen und hinzugefügte Dateien; Zugriffsart `tempudfadmin` |

Die Zugriffsarten sind die von Hive: `select`, `update`, `create`, `drop`, `alter`, `index`, `lock`, `read`, `write`, `repladmin`, `serviceadmin`, `tempudfadmin`, `refresh` und `all`. Wie in Ranger umfasst `all` jede Art außer `tempudfadmin`, mit der ein Benutzer beliebigen Code in HiveServer2 ausführen kann.

In Richtlinienwerten steht `*` für beliebig viele Zeichen und `?` für ein Zeichen. Der Richtlinieneditor schlägt Namen nach Präfix vor: Datenbanken, Tabellen und UDFs in den Datenbanken der Richtlinie sowie Spalten in deren Tabellen.

## Maskierung

Maskierungsrichtlinien gelten für Spalten. Jeder Maskentyp ist eine der Maskierungsfunktionen von Hive, in der `{col}` für die Spalte steht:

| Maskentyp | Zeigt |
| --- | --- |
| `mask` | Großbuchstaben als `X`, Kleinbuchstaben als `x` und Ziffern als `n` |
| `mask_show_last_4` | Nur die letzten vier Zeichen |
| `mask_show_first_4` | Nur die ersten vier Zeichen |
| `mask_hash` | Einen SHA-256-Hash |
| `mask_null` | `NULL` |
| `mask_none` | Den unveränderten Wert |
| `mask_date_show_year` | Den 1. Januar des Jahres des Datums |
| `custom` | Einen eigenen Hive-Ausdruck mit `{col}` für die Spalte, etwa `concat('****', substr({col}, -4))` |

So zeigt `mask_show_last_4` den Wert `4111111111111111` als `xxxxxxxxxxxx1111`.

## Zeilenfilter

Zeilenfilterrichtlinien gelten für Tabellen. Ein Filter ist eine Hive-Bedingung über die Spalten der Tabelle, etwa `region = 'EU'`: Abfragen sehen nur die Zeilen, die sie erfüllen.

Masken und Zeilenfilter gewähren keinen Zugriff. Eine eigene Zugriffsrichtlinie muss die Abfrage erlauben.

## Richtlinienbeispiel

Angenommen, Hive hat eine Tabelle `sales.orders` mit den Spalten `id`, `region` und `card`. Legen Sie im selben Datendienst drei aktivierte Richtlinien für den Benutzer `alice` an:

| Richtlinienart | Ressourcen | Regel |
| --- | --- | --- |
| Zugriff | `database=sales`, `table=orders`, `column=*` | `select` für Benutzer `alice` erlauben |
| Maskierung | `database=sales`, `table=orders`, `column=card` | `mask_show_last_4` für Benutzer `alice` |
| Zeilenfilter | `database=sales`, `table=orders` | `region = 'EU'` für Benutzer `alice` |

Sobald der Agent den neuen Snapshot anwendet, sieht `alice` nur die EU-Zeilen von `sales.orders`, bei jeder Karte bis auf die letzten vier Zeichen maskiert; `SHOW DATABASES` listet für sie `sales`, und andere Datenbanken verweigern ihr den Zugriff.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Um sie einschließlich Masken und Zeilenfiltern durchzusetzen, braucht jeder HiveServer2 den Hive-Agenten. Bereitstellung und Prüfumfang beschreibt der [Hive-Agent](/de/external/hive-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Hive-Agent](/de/external/hive-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
