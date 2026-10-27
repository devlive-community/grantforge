---
title: Elasticsearch-Agent
description: Den Elasticsearch-Agenten neben dem Cluster betreiben, damit GrantForge-Richtlinien zu den eigenen Rollen von Elasticsearch werden, nativen Benutzern gegeben und Benutzern anderer Realms zugeordnet, mit Field und Document Level Security, wo die Lizenz sie hat; Indexmuster bleiben Muster, sodass neue Indizes sofort erreichbar sind, und Zugriffe werden aus den Audit-Log-Dateien gemeldet.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der Elasticsearch-Agent ist ein Dienst neben dem Cluster. Er hält die eigene Zugriffskontrolle von Elasticsearch mit den signierten Richtlinien eines `elasticsearch`-Datendienstes im Einklang: eine Rolle pro Benutzer mit genau dem, was die Richtlinien dem Benutzer geben, mit Field und Document Level Security, wo die Lizenz des Clusters sie hat. Ein nativer Benutzer hält die Rolle unter seinen eigenen Rollen; ein Benutzer eines anderen Realms, etwa LDAP, SAML oder OpenID Connect, erhält sie über ein Role Mapping seines Namens. Elasticsearch prüft dann jede Anfrage selbst, und der Agent steht nie zwischen Clients und Cluster. Das Distributionspaket liefert ihn als ausführbares JAR, `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`, das Java 17 oder neuer braucht.

Die Testcontainers-Integrationstests des Repositorys führen den Agenten und das ausgelieferte JAR gegen Elasticsearch `8.19`, `9.3`, `9.4` und `9.5` mit aktivierter Sicherheit aus. Das zertifiziert nicht jede andere Version; der Agent lehnt Cluster älter als `8.19`, Cluster ohne Sicherheit und OpenSearch-Cluster ab, die der [OpenSearch-Agent](/de/external/opensearch-agent/) betreut. Das Service-Plug-in beschreibt [Elasticsearch](/de/plugins/elasticsearch/).

## Funktionsweise

- **Eine Rolle pro Benutzer hält, was GrantForge gibt.** Für jeden Benutzer, dem die Richtlinien etwas geben, hält der Agent eine nach ihm benannte Rolle, `grantforge_alice` für `alice`. Ihre Metadaten nennen den Benutzer und kennzeichnen sie als die des Agenten, und der Agent hält keine Rolle ohne diese Kennzeichnung für seine eigene, sodass er keine andere Rolle ändert.
- **Native Benutzer tragen die Rolle; andere Realms erhalten sie per Name.** Der Agent fügt die Rolle zu den Rollen eines nativen Benutzers hinzu und behält jede andere Rolle des Benutzers. Role Mappings gelten für jeden Realm außer dem nativen und dem File-Realm, daher hält der Agent auch ein gleichnamiges Role Mapping, das die Rolle dem Namen des Benutzers gibt, für Benutzer, die sich über LDAP, Active Directory, SAML, OpenID Connect, JWT, Kerberos oder PKI anmelden. Der File-Realm lässt sich über die API von Elasticsearch nicht ändern; seine Benutzer erhalten keine Rolle.
- **Indexmuster bleiben Muster.** Das Indexmuster einer Richtlinie, etwa `logs-*`, wird als Muster gewährt, wo jede Richtlinie darüber gleich entscheidet: Jede Richtlinie jeder Art, deren Indizes das Muster überlappen, deckt es ganz ab. Der Agent entscheidet das exakt, mit endlichen Automaten der Muster. Später angelegte Indizes, etwa die Logs von morgen, sind dann sofort erreichbar, auch wenn das erste geschriebene Dokument sie anlegt. Wo eine Richtlinie innerhalb eines Musters anders entscheidet, etwa ein Verweigern von `logs-secret` innerhalb von `logs-*`, gewährt der Agent jeden Index und Data Stream des Clusters beim Namen, neue beim nächsten Sync.
- **Field Level Security nur, wo darunter anders entschieden wird.** Ein Index, den der Benutzer ganz lesen darf, ohne Richtlinie, die ein Feld wegnehmen könnte, wird ohne Field Level Security gelesen, sodass später hinzugefügte Felder sofort gelesen werden. Sonst liest der Benutzer die Felder, die eine Richtlinie erlaubt, von denen, die seine Dokumente haben, und denen, die die Richtlinien nennen, jedes mit seinen Multi-Feldern, etwa `name.keyword` mit `name`.
- **Zeilenfilter sind Document Level Security.** Ein Zeilenfilter wird die Abfrage des Eintrags. Die Validate-API von Elasticsearch prüft jede Abfrage, bevor sie gewährt wird; eine Abfrage, die Elasticsearch nicht ausführen würde, zeigt dem Benutzer keine Dokumente und wird protokolliert.
- **Unter Platinum nie mehr, als die Richtlinien erlauben.** Elasticsearch hat Document und Field Level Security ab einer Platinum-Lizenz und schaltet darunter jede Rolle ab, die sie nutzt. Der Agent liest die Lizenz bei jedem Sync. Unter Platinum wird ein Lesen, das eines davon braucht, weil eine Richtlinie Felder oder Dokumente des Index einschränkt, gar nicht gewährt, und der Agent protokolliert, welcher Benutzer welchen Index nicht lesen kann und warum; das Lesen ganzer Indizes und alles andere wird wie üblich gewährt.
- **Ein Eintrag pro Satz von Berechtigungen.** Indizes und Muster mit denselben Berechtigungen teilen einen Eintrag; Einträge überlappen nie, da Elasticsearch die Field und Document Level Security überlappender Einträge vereint.
- **Die Richtlinien des gegenwärtigen Moments.** Jeder Sync plant aus den Richtlinien, wie sie in diesem Moment gelten, sodass Gültigkeitszeiträume pünktlich beginnen und enden.
- **Rollen nativer Benutzer sind Gruppen.** Eine Richtlinie für eine Gruppe gilt für native Benutzer mit einer Rolle dieses Namens, zusammen mit den Gruppen, in die GrantForge Benutzer setzt.
- **Änderungen von Hand werden rückgängig gemacht.** Der Agent vergleicht bei jedem Sync seine Rollen, Role Mappings und die Rollen nativer Benutzer mit dem, was Elasticsearch hält; was abweicht, wird neu geschrieben, und die Rolle eines Benutzers, die ein anderer hält, wird zurückgenommen. Rollen werden in einer Anfrage geschrieben, bevor sie gegeben werden, und zurückgenommen, bevor sie entfernt werden. Wenn sich nichts geändert hat, sendet der Agent nichts; eine Änderung, die Elasticsearch ablehnt, wird protokolliert und hält die übrigen nicht auf.
- **Rollen außerhalb von GrantForge werden gemeldet.** Elasticsearch gewährt, was eine der Rollen eines Benutzers gibt, daher kann der Agent nicht wegnehmen, was eine andere Rolle gibt. Er warnt einmal vor jeder solchen Rolle, die ein nativer Benutzer hält oder ein Role Mapping seines Namens gibt, wenn sie gibt, was eine Richtlinie verweigert, mit Rolle, Benutzer, Zugriff, Index und Richtlinie, und vor Benutzern mit der Rolle `superuser`, die keine Rolle bindet.
- **Zugriffe kommen aus den Audit-Log-Dateien.** Elasticsearch schreibt sein Audit-Log nur in eine Datei auf jedem Knoten. Der Agent liest die Dateien, die man ihm nennt, ab dem Ende des letzten Lesens, folgt ihnen, wenn Elasticsearch sie rotiert, und meldet `access_granted`- und `access_denied`-Ereignisse. Jedes nennt den Benutzer, die Zugriffsart, jeden erreichten Index oder Data Stream oder den Cluster und sagt, ob GrantForge entschieden hat oder eine Rolle außerhalb von GrantForge es erlaubt hat.

| Aktion | Zugriffsereignis |
| --- | --- |
| `indices:data/read/*`: Suchen, Abrufen, Zählen | `read` auf jedem Index oder Data Stream |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/delete`, `data_stream/delete` | `delete_index` |
| `indices:admin/get`, `mappings/get`, `settings/get`, `resolve/*` | `view_index_metadata` |
| `indices:monitor/*`; andere `indices:admin/*` | `monitor`; `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` und `repository/*`, `slm/*` | `cluster_monitor`, `create_snapshot`, `manage_slm` auf dem Cluster |
| Index- und Component-Templates, Ingest-Pipelines, `cluster:admin/ilm/*` | `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` auf dem Cluster |

## Bereitstellung

1. Legen Sie einen `elasticsearch`-Datendienst an und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agenten-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur das Konto des Agenten lesen kann.
2. Geben Sie dem Agenten einen Benutzer mit der Cluster-Berechtigung `manage_security`, die Rollen, Role Mappings und Benutzer verwaltet, und dem Recht, die Mappings jedes Index zu lesen und Abfragen zu validieren, etwa einen Benutzer mit der Rolle `superuser`. Er kann sich mit einem Passwort, einem von diesem Benutzer angelegten API-Schlüssel oder einem Clientzertifikat anmelden, das ein PKI-Realm ihm zuordnet.
3. Um Zugriffe zu melden, schalten Sie das Audit-Logging ein (`xpack.security.audit.enabled: true`), was eine Gold-Lizenz oder höher braucht, und lassen Sie den Agenten die Audit-Log-Dateien der Knoten lesen, `<Clustername>_audit.json` im Log-Verzeichnis jedes Knotens: Betreiben Sie den Agenten auf einem Knoten oder sammeln Sie die Dateien in einem Verzeichnis, das er lesen kann. `xpack.security.audit.logfile.events.include: access_granted, access_denied` beschränkt das Log auf das, was der Agent meldet.
4. Schreiben Sie die Einstellungsdatei und ersetzen Sie die Beispieladressen, -pfade und -namen:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   Diese Pfade liegen auf dem Rechner des Agenten, und das Cache-Verzeichnis muss beschreibbar sein. Passwort- und Schlüsseldateien werden als UTF-8 gelesen und getrimmt.
5. Starten Sie den Agenten, etwa als systemd-Dienst:

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   Ersetzen Sie `<version>` durch die Version des Distributionspakets. Als `java -Dgrantforge.…=… -jar …` übergebene Einstellungen überschreiben die Datei. Der Agent stoppt bei SIGTERM sauber und sendet oder puffert ausstehende Ereignisse; Elasticsearch behält, was es bekommen hat.
6. **Bestehende Rollen in Richtlinien überführen:** Rollen, die Benutzer außerhalb von GrantForge halten, gelten neben denen des Agenten weiter. Legen Sie die passenden Richtlinien an und nehmen Sie den Benutzern dann diese Rollen; die Warnungen des Agenten nennen die Rollen, die geben, was eine Richtlinie verweigert.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Pflicht: der GrantForge-Server, die Datei des Agenten-Tokens, ein Name für diesen Agenten und ein beschreibbares Verzeichnis für den signierten Snapshot, gepufferte Ereignisse und die Lesepositionen des Audit-Logs |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten X.509-Ed25519-Public-Key von der Agentenseite. Fehlt sie, wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.elasticsearch.hosts` | Pflicht: die Knoten als URLs, nacheinander versucht, wenn einer nicht erreichbar ist; ohne Schema ist ein Knoten `https`, der Port ist standardmäßig `9200` |
| `grantforge.elasticsearch.user`, `grantforge.elasticsearch.password.file` | Der Benutzer des Agenten und die Datei seines Passworts |
| `grantforge.elasticsearch.api.key.file` | Stattdessen: die Datei eines kodierten API-Schlüssels, wie Elasticsearch ihn beim Anlegen zurückgibt |
| `grantforge.elasticsearch.tls.cert`, `grantforge.elasticsearch.tls.key` | Stattdessen: PEM-Clientzertifikat und sein privater Schlüssel, zusammen, um sich mit einem Zertifikat anzumelden; `grantforge.elasticsearch.tls.key.passphrase.file` enthält die Passphrase eines verschlüsselten Schlüssels |
| `grantforge.elasticsearch.ssl.verification.mode` | `full` (Standard) prüft Zertifikate und Namen der Knoten, `certificate` nur die Zertifikate, `none` nichts |
| `grantforge.elasticsearch.tls.ca` | PEM-CA-Zertifikate, die die Knoten prüfen, etwa `http_ca.crt` von Elasticsearch; leer vertraut dem, dem Java vertraut |
| `grantforge.elasticsearch.timeout.ms` | Wie lange Verbindungsaufbau und eine Anfrage dauern dürfen, Standard `30000`, Bereich `1..600000` Millisekunden |
| `grantforge.elasticsearch.role.prefix` | Der Anfang der Rollennamen des Agenten, Standard `grantforge_`; Kleinbuchstaben, Ziffern, Bindestriche und Unterstriche, endend mit einem Unterstrich, höchstens 32 Zeichen. Dem Agenten gehören die von ihm gekennzeichneten Rollen, deren Namen damit beginnen |
| `grantforge.elasticsearch.unmanaged.users` | Benutzer, die der Agent in Ruhe lässt, durch Kommas getrennt, etwa Dienstkonten; seinen eigenen Benutzer, die reservierten Benutzer von Elasticsearch und Benutzer mit der Rolle `superuser` lässt er immer in Ruhe |
| `grantforge.sync.interval.ms` | Wie oft der Agent Elasticsearch abgleicht, Standard `10000`, Bereich `1000..3600000` Millisekunden |
| `grantforge.audit.log.files` | Die zu lesenden Audit-Log-Dateien, durch Kommas getrennt; ohne sie liest der Agent kein Audit-Log und meldet keine Zugriffe |
| `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.lines` | Wie oft die Dateien gelesen werden, Standard `5000` Millisekunden, und die meisten Zeilen, die ein Lesen von jeder nimmt, Standard `5000`, sodass ein Rückstand über mehrere Runden gelesen wird |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeouts zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und das Pufferlimit auf der Festplatte (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange identische erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` sendet jedes einzeln |

## Benutzer, Gruppen und Rollen

Richtlinien nennen Elasticsearch-Benutzer bei ihren Benutzernamen. Der Agent hält Rollen für die nativen Benutzer, für die Benutzer, die die Richtlinien nennen und die sich über einen anderen Realm anmelden können, und für die Benutzer, für die er vorher Rollen hielt; die reservierten Benutzer von Elasticsearch, etwa `kibana_system`, lässt er in Ruhe. Gruppen sind die übrigen Rollen eines nativen Benutzers zusammen mit den Gruppen, in die GrantForge den Benutzer setzt; die Gruppen, die ein Verzeichnis bei der Anmeldung gibt, kennt der Agent nicht, geben Sie solchen Benutzern ihre Gruppen also in GrantForge. Rollen sind die Rollen, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne Snapshot ändert der Agent nichts. Ein Dienst, der nicht in Gebrauch ist, oder ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `elasticsearch` ist, gewährt nichts: Der Agent nimmt seine Rollen zurück und entfernt sie mit ihren Role Mappings, und die eigenen Rollen von Elasticsearch gelten.
- Solange der Agent nicht läuft, behält Elasticsearch, was es zuletzt bekommen hat; Richtlinienänderungen warten auf ihn. Eine Änderung erreicht Elasticsearch innerhalb des Aktualisierungs- und des Sync-Intervalls.
- Beim Namen statt über ein Muster gewährte Indizes erhalten ihre Berechtigungen beim nächsten Sync.
- Unter Platinum liest ein Benutzer, dessen Lesen eines Index eine Richtlinie einschränkt, nichts davon; unter Gold meldet der Agent keine Zugriffe.
- Ein Role Mapping des Namens eines Benutzers gilt für jeden Realm mit diesem Benutzernamen; Richtlinien nennen Benutzer, nicht Realms.
- Aliase erreichen die Indizes dahinter, daher nennen Richtlinien Indizes und Data Streams, keine Aliase. Indizes, deren Namen mit einem Punkt beginnen, werden nur über Richtlinienmuster erreicht, die mit einem Punkt beginnen.
- Kibanas Spaces und Anwendungsberechtigungen verwaltet der Agent nicht.

## Weitere Anleitungen

- [Elasticsearch](/de/plugins/elasticsearch/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
