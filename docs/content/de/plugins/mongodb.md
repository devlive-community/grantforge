---
title: MongoDB
description: Das MongoDB-Service-Plug-in installieren und konfigurieren, sich mit einem Replica Set oder Sharded Cluster verbinden, mit SCRAM, Client-Zertifikat, LDAP oder Kerberos anmelden, Datenbanken und Collections nachschlagen und je Collection verwalten, wer was lesen, schreiben und administrieren darf.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das MongoDB-Plug-in schlägt Datenbanken und Collections nach und verwaltet Zugriffsrichtlinien in den Begriffen von MongoDB selbst: Lesen, Change Streams, Einfügen, Aktualisieren und Entfernen, Indizes, Collection-Optionen, das Überspringen der Dokumentvalidierung und Löschen je Collection; das Anlegen von Collections und Löschen der Datenbank je Datenbank; das Auflisten aller Datenbanken und das Überwachen für das Deployment als Ganzes. Plug-in-ID und Diensttyp sind beide `mongodb`. Das Service-Plug-in läuft im GrantForge-Server; der [MongoDB-Agent](/de/external/mongodb-agent/), der neben dem Deployment läuft, setzt die Richtlinien mit den eigenen Rollen von MongoDB durch.

Die Testcontainers-Integrationstests des Repositorys decken Replica Sets von MongoDB `6.0`, `7.0`, `8.0` und `9.0` ab, außerdem Kerberos auf Percona Server for MongoDB `8.0`. Das zertifiziert nicht jede andere Version.

## Installation

Das Distributionspaket enthält das Plug-in in `plugins/mongodb`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `mongodb` aktiviert ist; scannen Sie nach einer Aktualisierung des Plug-in-Verzeichnisses erneut. Das Plug-in spricht über den offiziellen MongoDB-Java-Treiber in einem eigenen Class Loader mit MongoDB.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie MongoDB (`mongodb`).
2. Geben Sie die Verbindungszeichenfolge des Deployments an, wie jeder MongoDB-Client sie nimmt.
3. Geben Sie an, wie sich der Lookup-Benutzer anmeldet, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt das Deployment: die MongoDB-Version, ob es ein Replica Set, ein Sharded Cluster oder ein einzelner Server ist, wer angemeldet ist und wie viele Datenbanken dieser Benutzer sieht.

Der Lookup-Benutzer lässt GrantForge Ressourcen finden und ist nicht der Benutzer, der liest oder schreibt. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits den Richtlinien folgt.

| Einstellung | Zweck |
| --- | --- |
| `mongodb.uri` | Erforderlich: die Verbindungszeichenfolge, etwa `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` oder `mongodb+srv://cluster.example.com/`. Optionen wie `tls=true` gehören hierher, Anmeldedaten nicht; eine Zeichenfolge mit Anmeldedaten wird abgelehnt |
| `auth.mechanism` | `SCRAM-SHA-256` (Standard) oder `SCRAM-SHA-1` für Benutzer mit Passwort, `MONGODB-X509` für ein Client-Zertifikat, `PLAIN` für LDAP, `GSSAPI` für Kerberos |
| `username` | Der Lookup-Benutzer. Bei `GSSAPI` der Principal; bei `MONGODB-X509` nimmt leer ihn aus dem Zertifikat |
| `password` | Das Passwort des Lookup-Benutzers, verschlüsselt gespeichert |
| `auth.source` | SCRAM: die Datenbank, in der der Benutzer definiert ist, standardmäßig `admin`. Die anderen Mechanismen verwenden `$external` |
| `tls.ca.file` | Mit `tls=true`: Pfad auf dem **GrantForge-Server** der PEM-CA-Zertifikate, gegen die die Server geprüft werden. Leer verwendet die der JVM |
| `tls.certificate.key.file` | Mit `tls=true`: Pfad auf dem **GrantForge-Server** einer PEM-Datei mit Client-Zertifikat und Schlüssel, wie `mongosh` sie nimmt |
| `tls.certificate.key.password` | Das Passwort eines verschlüsselten Schlüssels, verschlüsselt gespeichert |
| `kerberos.keytab` | `GSSAPI`: Pfad auf dem **GrantForge-Server** der Keytab des Principals. Leer verwendet das Passwort |
| `kerberos.kdc` | `GSSAPI`: die KDCs des Realms, durch Kommas getrennt. Leer verwendet die `krb5.conf` des Servers |
| `kerberos.service.name` | `GSSAPI`: der Dienstname des Server-Principals, standardmäßig `mongodb` |
| `timeout` | Wie lange das Finden eines Servers, das Verbinden und ein Befehl dauern dürfen, standardmäßig `30000`, im Bereich `1..600000` Millisekunden |

Eine abgelehnte Anmeldung, ein Lookup-Benutzer ohne nötige Rechte und ein nicht erreichbares Deployment werden jeweils als solche gemeldet.

### Was der Lookup-Benutzer sieht

MongoDB listet die Datenbanken und Collections, auf die ein Benutzer irgendein Recht hat, ein Lookup zeigt also, was der Lookup-Benutzer sehen darf. Geben Sie ihm `read` auf das, wofür Richtlinien geschrieben werden, oder `readAnyDatabase` für alles. Die eigenen Datenbanken von MongoDB, `admin`, `config` und `local`, und System-Collections werden ausgelassen: Richtlinien entscheiden sie nicht.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `deployment` | Platzhalter, Groß-/Kleinschreibung beachtet. Der Name des Replica Sets, `sharded` für einen Sharded Cluster, `standalone` für einen einzelnen Server | `list_databases`, `monitor` |
| `database` | Platzhalter, Groß-/Kleinschreibung beachtet | `create`, `drop_database` |
| `collection` | Platzhalter, Groß-/Kleinschreibung beachtet. Eine Collection oder View einer Datenbank | `read`, `change_stream`, `insert`, `update`, `remove`, `create_index`, `drop_index`, `alter`, `bypass_validation`, `drop` |

| Zugriffsart | Aktionen von MongoDB |
| --- | --- |
| `read` | `find`, `listIndexes`, `collStats`, `killCursors`; auf einer ganzen Datenbank auch `listCollections` und `dbStats` |
| `change_stream` | `changeStream`; zum Öffnen will MongoDB außerdem `find` |
| `insert`, `update`, `remove` | `insert`, `update`, `remove` |
| `create_index`, `drop_index` | `createIndex`, `dropIndex` |
| `alter` | `collMod`, `validate`, `convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | `createCollection` auf der Datenbank: Collections und Views anlegen |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`: der Name jeder Datenbank, nicht nur derer, auf die der Benutzer ein Recht hat |
| `monitor` | `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `inprog` |

In Richtlinienwerten passt `*` auf beliebigen Text und `?` auf ein Zeichen. Der Richtlinien-Editor schlägt das Deployment, Datenbanken und die Collections der Datenbanken vor, die eine Richtlinie nennt.

Das Recht von MongoDB, eine Datenbank zu löschen, ist das Recht, jede Collection darin zu löschen. Der Agent gewährt `drop_database` daher nur, wo die Richtlinien auch das Löschen jeder Collection erlauben; andernfalls hält er das Recht zurück und sagt es, statt mehr zu geben, als die Richtlinien erlauben. Benutzer- und Rollenverwaltung sind keine Zugriffsarten, und keine Richtlinie gibt sie.

### Zeilen und Felder

MongoDB filtert keine Zeilen und maskiert keine Felder je Rolle, daher bietet dieser Diensttyp beides nicht. Eine View ergibt ein eingeschränktes Lesen: MongoDB prüft den Leser einer View nur gegen die View, sodass eine Richtlinie, die `read` auf einer View und nichts auf ihrer Quelle erlaubt, dem Benutzer zeigt, was die View zeigt, und nicht mehr.

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## Richtlinienbeispiel

Angenommen, das Deployment hat die Datenbanken `sales` und `hr`. Legen Sie in einem Datendienst diese aktivierten Richtlinien an:

| Ressource | Regel |
| --- | --- |
| `database=sales`, `collection=*` | `read` für Gruppe `analysts` erlauben |
| `database=sales`, `collection=orders_public` | `read` für Benutzer `support` erlauben |
| `database=sales`, `collection=orders` | `insert` und `update` für Benutzer `ingest` erlauben; `read` für Gruppe `contractors` verweigern |
| `database=team_*` | `create` für Gruppe `data-team` erlauben |
| `database=team_*`, `collection=*` | `read`, `insert`, `update`, `remove`, `create_index` und `drop` für Gruppe `data-team` erlauben |
| `deployment=*` | `monitor` für Benutzer `dashboards` erlauben |

Sobald der Agent den Snapshot angewendet hat, lesen Mitglieder von `analysts` ganz `sales`, auch später angelegte Collections; `support` liest die View ohne ihre Quelle; `ingest` schreibt in `orders`, ohne es zu lesen; Mitglieder von `data-team` arbeiten frei in `team_`-Datenbanken; und `dashboards` sieht den Zustand des Servers und keine Daten.

## Richtlinien durchsetzen

Das Service-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Zur Durchsetzung braucht es einen MongoDB-Agenten je Deployment; Bereitstellung und Prüfungen stehen unter [MongoDB-Agent](/de/external/mongodb-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [MongoDB-Agent](/de/external/mongodb-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
