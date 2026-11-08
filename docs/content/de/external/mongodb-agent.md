---
title: MongoDB-Agent
description: Den MongoDB-Agenten neben einem Deployment betreiben, um GrantForge-Richtlinien in die eigenen Rollen von MongoDB umzusetzen, die Rechte einer Rolle in einem Schritt zu ersetzen, eingebaute und eigene Rollen außerhalb von GrantForge darauf zu lesen, was sie geben, und Zugriffe aus dem Audit-Log oder dem Profiler zu melden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der MongoDB-Agent ist ein Dienst neben dem Deployment. Er hält die Zugriffskontrolle von MongoDB im Einklang mit den signierten Richtlinien eines `mongodb`-Datendienstes: eine Rolle je Benutzer, die genau das hält, was die Richtlinien dem Benutzer geben. MongoDB prüft dann jeden Befehl selbst, und der Agent steht keinem Client im Weg. Das Distributionspaket liefert ihn als ausführbares JAR `agents/mongodb/grantforge-agent-mongodb-<version>.jar`; er braucht Java 17 oder neuer.

Die Testcontainers-Integrationstests des Repositorys führen den Agenten und das ausgelieferte JAR gegen Replica Sets von MongoDB `6.0`, `7.0`, `8.0` und `9.0` und einen Sharded Cluster mit `9.0` aus und lesen das Audit-Log von Percona Server for MongoDB `7.0` und `8.0`. Das zertifiziert nicht jede andere Version; der Agent lehnt Deployments vor `6.0` ab. Zum Service-Plug-in siehe [MongoDB](/de/plugins/mongodb/).

## Funktionsweise

- **Eine Rolle je Benutzer hält, was GrantForge gibt.** Für jeden Benutzer, dem die Richtlinien etwas geben, hält der Agent in `admin` eine nach ihm benannte Rolle, `grantforge_alice` für `alice`, und gewährt sie jedem MongoDB-Benutzer dieses Namens, etwa `alice@admin` und `alice@sales`. `rolesInfo` zeigt, was GrantForge `alice` gibt. Der Agent ändert keine andere Rolle.
- **Eine Änderung geschieht in einem Schritt.** `updateRole` ersetzt die Rechte einer Rolle auf einmal, sodass ein Benutzer nie alte und neue zugleich hält und nie keine von beiden.
- **Ganze Datenbanken, wo darunter nichts anders entschieden wird.** Eine Zugriffsart, die eine Richtlinie auf einer ganzen Datenbank erlaubt, ohne dass eine Richtlinie sie darunter wegnehmen könnte, wird auf der Datenbank gewährt, sodass später angelegte Collections sie sofort haben. Sonst wird sie je Collection gewährt, und neue Collections folgen beim nächsten Abgleich.
- **Nie mehr, als die Richtlinien erlauben.** Das Recht von MongoDB, eine Datenbank zu löschen, ist das Recht, jede Collection darin zu löschen. Der Agent gewährt es nur, wo die Richtlinien auch das geben; sonst hält er es zurück und protokolliert einmal, welcher Benutzer was nicht kann und warum.
- **Die Richtlinien des gegenwärtigen Moments.** Jeder Abgleich plant nach den Richtlinien, wie sie in diesem Moment gelten, sodass Gültigkeitszeiträume pünktlich beginnen und enden, und nach den Benutzern, Datenbanken und Collections, die MongoDB hat, sodass neue beim nächsten Abgleich folgen.
- **MongoDB-Rollen sind Gruppen.** Eine Richtlinie für eine Gruppe gilt für Benutzer, die eine MongoDB-Rolle dieses Namens halten, und für die Gruppen, in die GrantForge sie setzt.
- **Änderungen von Hand werden rückgängig gemacht.** Bei jedem Abgleich vergleicht der Agent seine Rollen mit den Plänen und korrigiert von Hand geänderte Rechte, geerbte Rollen, Vergaben einer Rolle an andere und Rollen, die niemand braucht. Hat sich nichts geändert, sendet er nichts.
- **Rollen außerhalb von GrantForge werden darauf gelesen, was sie geben.** MongoDB addiert, was alle Rollen eines Benutzers geben, der Agent kann also nicht wegnehmen, was ein Benutzer über eine andere Rolle hält. MongoDB sagt, was jede Rolle gibt, auch eingebaute und geerbte, daher warnt der Agent je einmal vor einer Rolle wie `readWrite@hr`, die gibt, was eine Richtlinie verweigert, und nennt Benutzer, Zugriff und Richtlinie. Er warnt auch vor Benutzern, die Benutzer verwalten und sich so alles geben können, vor Benutzern mit `root` oder jeder Aktion auf jeder Ressource und vor einem Server, der gar keine Autorisierung prüft.
- **Zugriffe kommen aus dem Audit-Log oder dem Profiler.** Siehe unten unter Zugriffe melden. Jedes Ereignis sagt, ob GrantForge entschieden hat, die Richtlinien also wie MongoDB entscheiden, oder eine Rolle außerhalb den Befehl erlaubt hat. Eine Aggregation wird für jede Collection gemeldet, die ihre Stufen `$lookup`, `$graphLookup` und `$unionWith` lesen und `$out` und `$merge` schreiben. Ereignisse nennen den Befehl und seinen Namespace, nie seine Argumente, die Dokumente und Passwörter enthalten können.

## Bereitstellung

1. Fügen Sie einen `mongodb`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agenten-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur das Konto des Agenten lesen kann.
2. Legen Sie den MongoDB-Benutzer des Agenten an. Er legt Rollen an, ändert sie und liest Benutzer und Rollen mit `userAdminAnyDatabase`; listet Collections mit einer Rolle, die nur das hält; und liest die Optionen des Servers und den Profiler mit `clusterMonitor`. `userAdminAnyDatabase` kann sich alles geben, schützen Sie diesen Benutzer also wie einen Administrator. Der Name der Listen-Rolle darf nicht mit dem Rollenpräfix des Agenten beginnen.

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. Um Zugriffe zu melden, richten Sie eine der Quellen unter Zugriffe melden ein.
4. Schreiben Sie die Einstellungsdatei und ersetzen Sie die Beispieladressen, -pfade und -namen:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   Diese Pfade liegen auf der Maschine des Agenten, und das Cache-Verzeichnis muss beschreibbar sein. Passwortdateien werden als UTF-8 gelesen und getrimmt.
5. Starten Sie den Agenten, zum Beispiel als systemd-Dienst:

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   Ersetzen Sie `<version>` durch die Version der Distribution. Einstellungen, die als `java -Dgrantforge.…=… -jar …` übergeben werden, überschreiben die Datei. Der Agent hält bei SIGTERM sauber an und sendet oder puffert ausstehende Ereignisse; MongoDB behält, was es bekommen hat.
6. **Bestehende Rechte in Richtlinien überführen:** außerhalb von GrantForge vergebene Rollen gelten neben denen des Agenten weiter. Legen Sie die passenden Richtlinien an und entziehen Sie dann diese Rollen. Die Warnungen des Agenten nennen die, die geben, was eine Richtlinie verweigert.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Pflicht: der GrantForge-Server, die Datei des Agenten-Tokens, ein Name für diesen Agenten und ein beschreibbares Verzeichnis für den signierten Snapshot, gepufferte Ereignisse und die Leseposition des Audit-Logs |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten öffentlichen X.509-Ed25519-Schlüssel von der Seite Agenten. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.mongodb.uri` | Erforderlich: die Verbindungszeichenfolge, ohne Anmeldedaten |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256` (Standard), `SCRAM-SHA-1`, `MONGODB-X509`, `PLAIN` oder `GSSAPI` |
| `grantforge.mongodb.user`, `grantforge.mongodb.password.file`, `grantforge.mongodb.auth.source` | Der Benutzer des Agenten, bei `GSSAPI` sein Principal und bei `MONGODB-X509` optional; sein Passwort; und bei SCRAM die Datenbank, in der er definiert ist, standardmäßig `admin` |
| `grantforge.mongodb.tls.ca.file`, `grantforge.mongodb.tls.certificate.key.file`, `grantforge.mongodb.tls.certificate.key.password.file` | Mit `tls=true` in der Verbindungszeichenfolge: die PEM-CA-Zertifikate, die die Server prüfen, leer für das, dem Java vertraut; eine PEM-Datei mit Client-Zertifikat und Schlüssel; und das Passwort eines verschlüsselten Schlüssels |
| `grantforge.mongodb.kerberos.keytab`, `grantforge.mongodb.kerberos.service.name` | `GSSAPI`: die Keytab des Principals, sonst wird das Passwort verwendet, und der Dienstname des Server-Principals, standardmäßig `mongodb`. Der Realm kommt aus der `krb5.conf` der Maschine |
| `grantforge.mongodb.timeout.ms` | Wie lange das Finden eines Servers, das Verbinden und ein Befehl dauern dürfen, standardmäßig `30000`, im Bereich `1..600000` Millisekunden |
| `grantforge.mongodb.role.prefix` | Womit die Rollennamen des Agenten beginnen, standardmäßig `grantforge_`. Buchstaben, Ziffern und Unterstriche, endend mit einem Unterstrich, höchstens 32 Zeichen. Jede Rolle in `admin`, deren Name damit beginnt, gehört dem Agenten |
| `grantforge.mongodb.unmanaged.users` | Benutzer, die der Agent in Ruhe lässt, durch Kommas getrennt, etwa Dienstkonten. Immer in Ruhe lässt er seinen eigenen Benutzer und Benutzer mit `root`, `__system` oder jeder Aktion auf jeder Ressource |
| `grantforge.mongodb.databases` | Die Datenbanken, in denen der Agent je Collection gewährt, durch Kommas getrennt, leer für alle. Die übrigen bekommen nur, was im Ganzen gewährt werden kann, damit eine Datenbank mit sehr vielen Collections nicht bei jedem Abgleich gelistet wird |
| `grantforge.mongodb.audit.files` | Die zu lesenden Audit-Log-Dateien, durch Kommas getrennt. Leer liest den Profiler |
| `grantforge.sync.interval.ms` | Wie oft der Agent MongoDB abgleicht, Standard `10000`, Bereich `1000..3600000` Millisekunden |
| `grantforge.audit.log` | Standardmäßig `true`. `false` liest und meldet keine Zugriffe |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Wie weit hinter der Gegenwart der Profiler gelesen wird (standardmäßig `2000`), wie oft Zugriffe gelesen werden (standardmäßig `5000` Millisekunden) und die meisten Einträge, die ein Lesevorgang aus einer Datei oder Datenbank nimmt (standardmäßig `1000`) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeouts zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batch-Größe (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und die Puffergrenze auf der Platte (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange identische erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` sendet jeden |

## Zugriffe melden

| Quelle | Wo | Was sie sagt | Wie einschalten |
| --- | --- | --- | --- |
| Audit-Log | MongoDB Enterprise und Percona Server for MongoDB | Was erlaubt und was abgelehnt wurde, auf jedem Mitglied, dessen Datei der Agent lesen kann | `mongod` mit `--auditDestination file --auditFormat JSON --auditPath <path>` und `--setParameter auditAuthorizationSuccess=true` starten und die Dateien in `grantforge.mongodb.audit.files` nennen |
| Profiler | Jede Edition | Was auf dem Mitglied lief, mit dem der Agent spricht, dem Primary eines Replica Sets; keine Ablehnungen | `db.setProfilingLevel(2)` in jeder zu meldenden Datenbank. Das kostet den Server, daher schaltet der Agent es nie selbst ein, und `system.profile` ist eine kleine Capped Collection |

Das Audit-Log wird aus Dateien gelesen; betreiben Sie den Agenten also auf dem Knoten oder geben Sie ihm ein Verzeichnis, das sie sammelt. Die Tests lesen das Audit-Log von Percona Server, das das Format von MongoDB Enterprise hat. Ein `mongos` hat keinen Profiler, ein Sharded Cluster meldet Zugriffe also nur aus dem Audit-Log.

| Befehl | Zugriffsereignis |
| --- | --- |
| `find`, `aggregate`, `count`, `distinct`, `listIndexes` | `read` auf jeder gelesenen Collection |
| `aggregate`, das mit `$changeStream` beginnt | `change_stream` |
| `insert`, `update`, `delete`, `findAndModify`, `bulkWrite` | `insert`, `update` oder `remove` auf jeder geschriebenen Collection; auf Wunsch auch `bypass_validation` |
| `createIndexes`, `dropIndexes` | `create_index`, `drop_index` |
| `collMod`, `validate`, `convertToCapped` | `alter` |
| `drop`, `create`, `dropDatabase` | `drop`, `create`, `drop_database` |
| `listDatabases`; `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `currentOp` | `list_databases`; `monitor` |

MongoDB lehnt einen Befehl als Ganzes ab, ohne zu sagen, welche seiner Collections der Grund war. Eine Ablehnung wird daher für die gemeldet, die die Richtlinien verweigern, oder für die erste, wenn sie keine verweigern.

## Benutzer, Gruppen und Rollen

Richtlinien nennen MongoDB-Benutzer beim Benutzernamen. Ein in mehreren Datenbanken definierter Name ist ein Benutzer, und die Rolle des Agenten wird allen gewährt. Gruppen sind die MongoDB-Rollen eines Benutzers und die Gruppen, in die GrantForge ihn setzt. Rollen sind die Rollen, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne Snapshot ändert der Agent nichts. Ein Token, das an einen nicht genutzten Dienst oder einen Dienst anderen Typs als `mongodb` gebunden ist, gewährt nichts: Der Agent löscht seine Rollen, und die eigenen von MongoDB bleiben.
- Solange der Agent nicht läuft, behält MongoDB, was es zuletzt erhalten hat, und Richtlinienänderungen warten. Eine Änderung erreicht MongoDB innerhalb der Aktualisierungs- und Abgleichintervalle; ein `mongos` wendet sie an, sobald sein Benutzer-Cache abgelaufen ist, standardmäßig innerhalb von 30 Sekunden (`userCacheInvalidationIntervalSecs`).
- Die Rechte von MongoDB nennen Datenbanken und Collections genau, ein Platzhalter in einer Richtlinie wird also für das gewährt, was existiert. Eine Datenbank existiert, sobald sie eine Collection hat: bis dahin gibt es für `create` auf einer von einer Richtlinie genannten Datenbank nichts zu gewähren.
- Benutzer, die nirgends in MongoDB definiert sind, etwa solche, die die LDAP-Autorisierung selbst auf Rollen abbildet, werden nicht gesehen, und der Agent hält keine Rolle für sie.
- Ein Recht auf eine ganze Datenbank deckt ihre Collections ab, aber nicht ihre `system.`-Collections, und die eigenen Datenbanken von MongoDB bleiben MongoDB überlassen.
- Das Umbenennen von Collections und die übrigen Verwaltungsaktionen von MongoDB sind keine Zugriffsarten: Sie bleiben bei Rollen außerhalb von GrantForge.
- MongoDB filtert keine Zeilen und maskiert keine Felder je Rolle; eine [View](/de/plugins/mongodb/) ergibt ein eingeschränktes Lesen.

## Weitere Anleitungen

- [MongoDB](/de/plugins/mongodb/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
