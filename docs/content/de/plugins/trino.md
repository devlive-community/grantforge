---
title: Trino
description: Das Trino-Dienst-Plug-in installieren und konfigurieren, sich über TLS mit Passwort, JSON Web Token oder Kerberos anmelden, Kataloge, Schemas, Tabellen, Spalten und Sitzungseigenschaften nachschlagen und Zugriffs-, Zeilenfilter- und Maskierungsrichtlinien in Trinos eigenen Begriffen verwalten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Trino-Plug-in schlägt Kataloge, Schemas, Tabellen, Spalten und Sitzungseigenschaften nach und verwaltet Zugriffs-, Zeilenfilter- und Maskierungsrichtlinien für das, was Trino prüft: Daten, Routinen, Sitzungseigenschaften, Benutzer und die Systeminformationen. Plug-in-ID und Diensttyp sind beide `trino`. Das Dienst-Plug-in läuft im GrantForge-Server; der im Coordinator installierte [Trino-Agent](/de/external/trino-agent/) setzt die Richtlinien durch.

Die Testcontainers-Integrationstests des Repositorys decken Trino `470`, `476`, `479` und `484` ab, auf `470` und `484` außerdem HTTPS mit Passwortdatei und JSON Web Tokens sowie Kerberos. Das zertifiziert nicht jede andere Version.

## Installation

Die Distribution enthält das Plug-in in `plugins/trino`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `trino` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit dem Coordinator über Trinos eigenen JDBC-Treiber (`trino-jdbc` 484) in einem eigenen Klassenlader, der mit allen oben genannten Trino-Versionen spricht.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Trino (`trino`).
2. Geben Sie die JDBC-URL des Coordinators ein, etwa `jdbc:trino://trino.example.com:8443`.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie ihn ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Coordinator: seine Version und wie viele Kataloge der Benutzer sieht.

Die Einstellungen tragen die Namen des Treibers, sodass sie sich wie Trinos JDBC-Dokumentation lesen. Der Lookup-Benutzer lässt GrantForge Ressourcen entdecken; er ist von den Benutzern getrennt, die Abfragen ausführen. Ein erfolgreicher Verbindungstest bedeutet nicht, dass Abfragen bereits Richtlinien unterliegen.

| Einstellung | Zweck |
| --- | --- |
| `jdbc.url` | Erforderlich: `jdbc:trino://host:port`, optional mit Katalog und Schema sowie Treibereigenschaften außer Anmeldedaten, etwa `?SSL=true` |
| `authentication` | `none` (Standard): nur der Benutzername; `password`; `jwt`: ein JSON Web Token; `kerberos`: ein Prinzipal mit Keytab oder Passwort |
| `user` | Erforderlicher Lookup-Benutzer; mit Kerberos der vollständige Prinzipal, etwa `grantforge@EXAMPLE.COM` |
| `password` | Das Passwort des Benutzers, mit Kerberos das des Prinzipals, sofern kein Keytab angegeben ist; verschlüsselt gespeichert |
| `accessToken` | `jwt`: das Token, das der Coordinator akzeptiert; verschlüsselt gespeichert |
| `keytab` | Kerberos: Pfad zum Keytab des Prinzipals, lesbar auf dem **GrantForge-Server** |
| `kerberos.kdc` | Kerberos: KDCs des Realms des Prinzipals, `host[:port]`, durch Kommas getrennt; leer verwendet die `krb5.conf` des Servers |
| `KerberosRemoteServiceName` | Kerberos: der erste Teil des Prinzipals des Coordinators, Standard `trino` |
| `KerberosUseCanonicalHostname` | Kerberos: ob der Prinzipal des Coordinators den kanonischen Namen des URL-Hosts trägt, Standard `true`; hinter einem Load Balancer oder Alias, dessen Namen der Prinzipal trägt, ausschalten |
| `SSL` | Ob TLS gesprochen wird, Standard `false`; jede Anmeldung außer `none` braucht es, da Trino Benutzer nur über TLS anmeldet |
| `SSLTrustStorePath`, `SSLTrustStorePassword`, `SSLTrustStoreType` | TLS: der Truststore, der den Coordinator prüft, sein Passwort und Typ (`JKS` oder `PKCS12`); leer vertraut, was Java vertraut |
| `SSLVerification` | TLS: `FULL` (Standard) prüft Zertifikat und Hostnamen, `CA` nur das Zertifikat, `NONE` nichts und nimmt keinen Truststore |
| `timeout` | Anfrage-Timeout in Millisekunden, Standard `30000`, Bereich `1..600000` |

Der Dienst ist genau das, was seine Felder sagen. Die URL darf keine Anmeldedaten tragen und nichts, womit man sich anders anmelden würde: `user`, `password`, `accessToken`, `sessionUser`, `extraCredentials`, externe und OAuth-2.0-Authentifizierung, Kerberos-Dateien und -Delegierung sowie Keystore- und Truststore-Passwörter werden dort abgelehnt. Felder einer anderen Anmeldeart werden ebenfalls abgelehnt, etwa ein Token bei Passwortauthentifizierung.

Mit einem Coordinator, dessen Zertifikat sich nicht prüfen lässt, wird nicht gesprochen: Der Test meldet ihn als nicht erreichbar. Eine abgelehnte Anmeldung, fehlende Berechtigungen und ein nicht erreichbarer Coordinator werden als solche gemeldet.

### Kerberos

Mit `kerberos` meldet GrantForge den Prinzipal an, aus dem Keytab oder mit dem Passwort, und übergibt dem Treiber die Anmeldedaten dieser Anmeldung; sonst braucht nichts auf dem GrantForge-Server Kerberos-Einstellungen. Realms, deren KDCs in `kerberos.kdc` eingetragen sind, werden für den ganzen GrantForge-Server deklariert, wie es die HDFS-, Hive-, Kafka- und HBase-Plug-ins tun, sodass mehrere Dienste verschiedene Realms nebeneinander nutzen können. Der Sitzungsbenutzer ist der Name des Prinzipals vor dem Realm, wie Trino Prinzipale standardmäßig abbildet.

### Was der Lookup-Benutzer sieht

Sobald der [Trino-Agent](/de/external/trino-agent/) die Richtlinien durchsetzt, lassen Auflistungen weg, was der Lookup-Benutzer nicht verwenden darf: Geben Sie ihm eine Richtlinie, die `select` auf Katalog `*` erlaubt, oder auf den Katalogen, deren Ressourcen der Richtlinieneditor vorschlagen soll.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `catalog` | Platzhalter, ohne Groß-/Kleinschreibung | `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `all` |
| `schema` | Platzhalter, ohne Groß-/Kleinschreibung; unter einem Katalog | Wie `catalog` |
| `table` | Platzhalter, ohne Groß-/Kleinschreibung; unter einem Schema; Tabellen, Sichten und materialisierte Sichten | Wie `catalog` |
| `column` | Platzhalter, ohne Groß-/Kleinschreibung; unter einer Tabelle | `select`, `update` |
| `function` | Platzhalter, ohne Groß-/Kleinschreibung; unter einem Schema | `execute`, `create_function`, `drop_function` |
| `procedure` | Platzhalter, ohne Groß-/Kleinschreibung; unter einem Schema | `execute` |
| `session_property` | Platzhalter, ohne Groß-/Kleinschreibung; unter einem Katalog, ohne ihn benannt | `set` |
| `system_session_property` | Platzhalter, ohne Groß-/Kleinschreibung | `set` |
| `user` | Platzhalter, mit Groß-/Kleinschreibung | `impersonate`, `view_query`, `kill_query` |
| `system_information` | Sein einziger Wert, `*` | `read`, `write` |

`all` umfasst `select`, `insert`, `update`, `delete`, `create`, `drop` und `alter`.

Eine Berechtigung auf einem Katalog, Schema oder einer Tabelle umfasst alles darin: `select` auf Schema `sales` liest jede seiner Tabellen, `create` auf einem Katalog legt darin Schemas und Tabellen an. Eine Prüfung wird in jedem Geltungsbereich entschieden, den sie nennt, und eine Verweigerung in einem davon lehnt sie ab; so kann eine Richtlinie ein Schema erlauben und eine Spalte einer seiner Tabellen verweigern. Funktionen, Prozeduren und Sitzungseigenschaften werden beim Namen gewährt, mit `*` für alle eines Schemas oder Katalogs, und Funktionen haben eigene Zugriffsarten, sodass eine Berechtigung auf Routinen nie als eine auf Tabellen gilt.

In Richtlinienwerten passt `*` auf beliebig viele Zeichen und `?` auf eines. Der Richtlinieneditor schlägt Kataloge vor, Schemas in den Katalogen, die eine Richtlinie nennt, Tabellen in ihren Schemas, Spalten in ihren Tabellen sowie Sitzungseigenschaften ihrer Kataloge und des Systems; Funktionen, Prozeduren und Benutzer werden nicht vorgeschlagen.

## Zeilenfilter und Masken

Zeilenfilterrichtlinien auf einer Tabelle und Maskierungsrichtlinien auf einer Spalte sind Trino-SQL, das der Coordinator auf jede Abfrage anwendet, die sie liest. Ein Zeilenfilter ist eine Bedingung auf den Spalten der Tabelle, etwa `region = 'EU'`; Namen darin werden im Katalog und Schema der Tabelle aufgelöst.

| Maskentyp | Zeigt |
| --- | --- |
| `mask` | Buchstaben als `X` oder `x`, Ziffern als `n` |
| `mask_show_last_4`, `mask_show_first_4` | Nur die letzten oder ersten vier Zeichen |
| `mask_hash` | Den SHA-256 des Werts, hexadezimal |
| `mask_null` | `NULL` |
| `mask_none` | Den Wert, unmaskiert |
| `mask_date_show_year` | Nur das Jahr eines Datums oder Zeitstempels |
| `custom` | Einen Trino-SQL-Ausdruck der Richtlinie, mit `{col}` für die Spalte |

Der Agent passt jede Maske an den Typ der Spalte an, damit eine Maske nie die Abfrage bricht, die die Spalte liest: siehe den [Trino-Agent](/de/external/trino-agent/).

## Richtlinienbeispiel

Angenommen, der Katalog `hive` hat ein Schema `sales` mit den Tabellen `orders`, deren Spalten `region`, `customer` und `card` umfassen, und `returns`. Legen Sie im selben Datendienst diese aktivierten Richtlinien an:

| Richtlinie | Ressourcen | Regel |
| --- | --- | --- |
| Zugriff | `catalog=hive`, `schema=sales` | `select` für Gruppe `analysts` erlauben |
| Zugriff | `catalog=hive`, `schema=sales`, `table=orders`, `column=card` | `select` für Gruppe `analysts` verweigern |
| Zugriff | `catalog=hive`, `schema=sales`, `table=returns` | `insert` für Benutzer `alice` erlauben |
| Zeilenfilter | `catalog=hive`, `schema=sales`, `table=orders` | `region = 'EU'` für Benutzer `alice` |
| Maskierung | `catalog=hive`, `schema=sales`, `table=orders`, `column=customer` | `mask_hash` für Gruppe `analysts` |
| Zugriff | `catalog=system`, `schema=runtime` | `select` für Benutzer `alice` erlauben |
| Zugriff | `user=bob` | `view_query` für Benutzer `alice` erlauben |

Sobald der Agent den neuen Snapshot anwendet, lesen Mitglieder von `analysts` jede Tabelle von `sales` außer `orders.card` und sehen `customer` gehasht; `alice` sieht nur die europäischen Bestellungen, schreibt in `returns` und sieht in `system.runtime.queries` neben ihren eigenen auch Bobs Abfragen.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Um sie durchzusetzen, braucht es den Trino-Agent im Coordinator. Bereitstellung und Prüfungen beschreibt der [Trino-Agent](/de/external/trino-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Trino-Agent](/de/external/trino-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
