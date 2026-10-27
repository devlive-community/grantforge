---
title: OpenSearch-Agent
description: Den OpenSearch-Agenten neben dem Cluster betreiben, damit GrantForge-Richtlinien zu den eigenen Rollen des Security-Plug-ins werden, mit Sicherheit auf Feldebene, maskierten Feldern und Sicherheit auf Dokumentebene, Indexmuster Muster bleiben, sodass neue Indizes sofort erreichbar sind, und Zugriffe aus dem Audit-Log gemeldet werden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der OpenSearch-Agent ist ein Dienst neben dem Cluster. Er hält die eigene Zugriffskontrolle des Security-Plug-ins im Einklang mit den signierten Richtlinien eines `opensearch`-Datendienstes: eine Rolle pro Benutzer, die genau das enthält, was die Richtlinien dem Benutzer geben, nur diesem Benutzer zugeordnet, mit Sicherheit auf Feldebene, maskierten Feldern und Sicherheit auf Dokumentebene. OpenSearch prüft dann jede Anfrage selbst, und der Agent steht nie zwischen Clients und Cluster. Die Distribution liefert ihn als ausführbares JAR, `agents/opensearch/grantforge-agent-opensearch-<version>.jar`, das Java 17 oder neuer benötigt.

Die Testcontainers-Integrationstests des Repositorys führen den Agenten und das ausgelieferte JAR gegen OpenSearch `2.19`, `3.3`, `3.6` und `3.9` mit dem Security-Plug-in aus. Damit ist nicht jede andere Version zertifiziert; der Agent lehnt Cluster älter als `2.19` und Cluster ohne Security-Plug-in ab. Zum Dienst-Plug-in siehe [OpenSearch](/de/plugins/opensearch/).

## Funktionsweise

- **Eine Rolle pro Benutzer enthält, was GrantForge gibt.** Für jeden Benutzer, dem die Richtlinien etwas geben, führt der Agent eine nach ihm benannte Rolle, `grantforge_alice` für `alice`, nur diesem Benutzer zugeordnet. Ihre Beschreibung kennzeichnet sie als die des Agenten, und der Agent hält keine Rolle ohne diese Kennzeichnung für eine eigene, sodass er keine andere Rolle und Zuordnung ändert.
- **Indexmuster bleiben Muster.** Ein Indexmuster einer Richtlinie wie `logs-*` wird als Muster gewährt, wo jede Richtlinie über es hinweg gleich entscheidet: Jede Richtlinie jeder Art, deren Indizes das Muster überschneiden, deckt es ganz ab. Der Agent entscheidet das exakt, mit endlichen Automaten der Muster. Später angelegte Indizes wie die Protokolle von morgen sind dann sofort erreichbar, auch wenn das erste geschriebene Dokument sie anlegt. Wo eine Richtlinie innerhalb eines Musters anders entscheidet, etwa eine Verweigerung von `logs-secret` innerhalb von `logs-*`, gewährt der Agent jeden Index und Datenstrom des Clusters namentlich, und neue bei der nächsten Synchronisierung.
- **Sicherheit auf Feldebene nur, wo darunter anders entschieden wird.** Ein Index, den der Benutzer als Ganzes lesen darf, ohne Richtlinie, die ein Feld wegnehmen könnte, wird ohne Sicherheit auf Feldebene gelesen, sodass später hinzugefügte Felder sofort gelesen werden. Sonst liest der Benutzer die Felder, die eine Richtlinie erlaubt, von denen, die seine Dokumente haben, und denen, die die Richtlinien nennen; Multi-Felder wie `name.keyword` folgen ihrem Feld.
- **Masken und Zeilenfilter sind die des Security-Plug-ins.** Eine Maske wird zu einem maskierten Feld des Eintrags und ein Zeilenfilter zu seiner Abfrage der Sicherheit auf Dokumentebene. Die Validate-API von OpenSearch prüft jede Abfrage, bevor sie gewährt wird; eine Abfrage, die OpenSearch nicht ausführen würde, zeigt dem Benutzer keine Dokumente und wird protokolliert. Eine Maske, die das Security-Plug-in nicht kennt, verbirgt stattdessen das Feld.
- **Ein Eintrag pro Satz von Berechtigungen.** Indizes und Muster mit denselben Berechtigungen teilen sich einen Eintrag; Einträge überschneiden sich nie, da das Security-Plug-in die Sicherheit auf Feld- und Dokumentebene überschneidender Einträge kombiniert.
- **Die zusammengesetzten Operationen des Clusters kommen mit Lesen und Schreiben.** Lesen bringt `cluster_composite_ops_ro` für Multi-Search, Multi-Get und Scrollen, Schreiben `cluster_composite_ops` für Bulk.
- **Änderungen sind ein Patch.** Der Agent schreibt seine Rollen und Zuordnungen als JSON-Patches, die das Security-Plug-in auf einmal im ganzen Cluster anwendet: Rollen vor den Zuordnungen, die sie nennen, Zuordnungen vor den Rollen, die sie nennen und die entfernt werden. Ein Patch, den das Security-Plug-in ablehnt, wird Änderung für Änderung angewendet, sodass eine fehlerhafte Rolle allein scheitert.
- **Die Richtlinien des gegenwärtigen Moments.** Jede Synchronisierung plant nach den Richtlinien, wie sie in diesem Moment gelten, sodass Gültigkeitszeiträume pünktlich beginnen und enden.
- **Backend-Rollen sind Gruppen.** Eine Richtlinie für eine Gruppe gilt für interne Benutzer mit einer Backend-Rolle dieses Namens, zusammen mit den Gruppen, in die GrantForge Benutzer setzt.
- **Manuelle Änderungen werden rückgängig gemacht.** Der Agent vergleicht bei jeder Synchronisierung seine Rollen und Zuordnungen mit dem, was das Security-Plug-in hält; was abweicht, wird neu geschrieben. Hat sich nichts geändert, sendet der Agent nichts.
- **Rollen außerhalb von GrantForge werden gemeldet.** OpenSearch gewährt, was irgendeine Rolle eines Benutzers gibt, daher kann der Agent nicht wegnehmen, was eine andere dem Benutzer zugeordnete Rolle gibt. Er warnt einmal vor jeder solchen Rolle, die gibt, was eine Richtlinie verweigert, mit Rolle, Benutzer, Zugriff, Index und Richtlinie, sowie vor Benutzern, die `all_access` zugeordnet sind und die keine Rolle bindet.
- **Zugriffe kommen aus dem Audit-Log.** Der Agent liest das Audit-Log des Security-Plug-ins aus den Indizes, in die es schreibt, `security-auditlog-*`: was es abgelehnt hat, als `MISSING_PRIVILEGES`, und was es erlaubt hat, als `GRANTED_PRIVILEGES`, wenn die Audit-Konfiguration diese protokolliert. Jedes Ereignis nennt den Benutzer, die Zugriffsart, jeden erreichten Index oder Datenstrom oder den Cluster und sagt, ob GrantForge entschieden hat oder eine Rolle außerhalb von GrantForge es erlaubt hat.

| Aktion | Zugriffsereignis |
| --- | --- |
| `indices:data/read/*`: Suchen, Abrufen, Zählen | `read` auf jedem Index oder Datenstrom |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/aliases*`, `indices:monitor/*` | `manage_aliases`, `monitor` |
| Andere `indices:admin/*` | `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` und `repository/*` | `cluster_monitor`, `manage_snapshots` auf dem Cluster |
| Index- und Komponentenvorlagen, Ingest-Pipelines | `manage_templates`, `manage_pipelines` auf dem Cluster |

## Bereitstellung

1. Fügen Sie einen `opensearch`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agenten-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur das Konto des Agenten lesen kann.
2. Geben Sie dem Agenten einen Benutzer, der die Konfiguration des Security-Plug-ins ändern darf: einen Benutzer, der `all_access` zugeordnet ist, solange `plugins.security.restapi.roles_enabled` ihn aufführt, oder das Admin-Zertifikat, das `plugins.security.authcz.admin_dn` nennt. Die Tests führen den Agenten als `admin` der Demo-Konfiguration und mit deren Admin-Zertifikat aus.
3. Um Zugriffe zu melden, behalten Sie das Audit-Log im Cluster (`plugins.security.audit.type: internal_opensearch`). Für das, was OpenSearch erlaubt hat, nehmen Sie außerdem `GRANTED_PRIVILEGES` aus den deaktivierten Kategorien der Audit-Konfiguration; Verweigerungen werden standardmäßig protokolliert.
4. Schreiben Sie die Einstellungsdatei und ersetzen Sie die Beispieladressen, -pfade und -namen:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   Diese Pfade liegen auf dem Rechner des Agenten, und das Cache-Verzeichnis muss beschreibbar sein. Passwortdateien werden als UTF-8 gelesen und getrimmt.
5. Starten Sie den Agenten, zum Beispiel als systemd-Dienst:

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   Ersetzen Sie `<version>` durch die Version der Distribution. Einstellungen, die als `java -Dgrantforge.…=… -jar …` angegeben werden, überschreiben die Datei. Der Agent hält bei SIGTERM sauber an und sendet oder puffert ausstehende Ereignisse; OpenSearch behält, was es bekommen hat.
6. **Bestehende Rollen in Richtlinien überführen:** Rollen, die Benutzern außerhalb von GrantForge zugeordnet sind, gelten neben denen des Agenten weiter. Legen Sie die passenden Richtlinien an und entfernen Sie dann diese Zuordnungen; die Warnungen des Agenten nennen die Rollen, die geben, was eine Richtlinie verweigert.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Pflicht: der GrantForge-Server, die Datei des Agenten-Tokens, ein Name für diesen Agenten und ein beschreibbares Verzeichnis für den signierten Snapshot, gepufferte Ereignisse und die Leseposition des Audit-Logs |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten öffentlichen X.509-Ed25519-Schlüssel von der Seite Agenten. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.opensearch.hosts` | Pflicht: die Knoten als URLs, nacheinander versucht, wenn einer nicht erreichbar ist; ohne Schema ist ein Knoten `https`, der Port ist standardmäßig `9200` |
| `grantforge.opensearch.user`, `grantforge.opensearch.password.file` | Der Benutzer des Agenten und die Datei seines Passworts |
| `grantforge.opensearch.tls.cert`, `grantforge.opensearch.tls.key` | Stattdessen: PEM-Client-Zertifikat und sein privater Schlüssel, gemeinsam, um sich mit einem Zertifikat anzumelden; `grantforge.opensearch.tls.key.passphrase.file` enthält die Passphrase eines verschlüsselten Schlüssels |
| `grantforge.opensearch.ssl.verification.mode` | `full` (Standard) prüft Zertifikate und Namen der Knoten, `certificate` nur ihre Zertifikate, `none` nichts |
| `grantforge.opensearch.tls.ca` | PEM-CA-Zertifikate, die die Knoten prüfen; leer vertraut dem, dem Java vertraut |
| `grantforge.opensearch.timeout.ms` | Wie lange Verbinden und eine Anfrage dauern dürfen, Standard `30000`, Bereich `1..600000` Millisekunden |
| `grantforge.opensearch.role.prefix` | Der Anfang der Rollennamen des Agenten, Standard `grantforge_`; Kleinbuchstaben, Ziffern, Bindestriche und Unterstriche, endet mit einem Unterstrich, höchstens 32 Zeichen. Dem Agenten gehören die von ihm gekennzeichneten Rollen, deren Namen damit beginnen |
| `grantforge.opensearch.unmanaged.users` | Benutzer, die der Agent in Ruhe lässt, durch Kommas getrennt, etwa Dienstkonten; seinen eigenen Benutzer und `all_access` zugeordnete Benutzer lässt er immer in Ruhe |
| `grantforge.sync.interval.ms` | Wie oft der Agent OpenSearch abgleicht, Standard `10000`, Bereich `1000..3600000` Millisekunden |
| `grantforge.audit.log` | Standard `true`; `false` liest kein Audit-Log und meldet keine Zugriffe |
| `grantforge.audit.log.index`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.documents` | Die Indizes des Audit-Logs, Standard `security-auditlog-*`; wie oft es gelesen wird, Standard `5000` Millisekunden; und die meisten Dokumente, die ein Lesevorgang nimmt, Standard `1000`, sodass ein Rückstand über mehrere Durchgänge gelesen wird |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeouts zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und das Pufferlimit auf der Festplatte (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange gleiche erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` sendet jeden |

## Benutzer, Gruppen und Rollen

Richtlinien nennen OpenSearch-Benutzer, wie das Security-Plug-in sie kennt. Der Agent führt Rollen für die internen Benutzer, für die Benutzer, die die Richtlinien nennen und die sich über ein Verzeichnis wie LDAP oder SAML anmelden können, und für die Benutzer, für die er bereits Rollen führte; die reservierten und versteckten Benutzer des Security-Plug-ins wie `kibanaserver` lässt er in Ruhe. Gruppen sind die Backend-Rollen eines internen Benutzers zusammen mit den Gruppen, in die GrantForge den Benutzer setzt; die Backend-Rollen, die ein Verzeichnis bei der Anmeldung gibt, kennt der Agent nicht, geben Sie solchen Benutzern ihre Gruppen also in GrantForge. Rollen sind die Rollen, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne Snapshot ändert der Agent nichts. Ein Dienst, der nicht in Gebrauch ist, oder ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `opensearch` ist, gewährt nichts: Der Agent entfernt seine Rollen und Zuordnungen, und die eigenen Rollen des Security-Plug-ins gelten.
- Solange der Agent nicht läuft, behält OpenSearch, was es zuletzt bekommen hat; Richtlinienänderungen warten auf ihn. Eine Änderung erreicht OpenSearch innerhalb des Aktualisierungs- und des Synchronisierungsintervalls.
- Namentlich statt über ein Muster gewährte Indizes erhalten ihre Berechtigungen bei der nächsten Synchronisierung.
- Aliase erreichen die Indizes dahinter: Das Durchsuchen eines Alias braucht Berechtigungen auf jedem Index, den er enthält, daher nennen Richtlinien Indizes und Datenströme, keine Aliase.
- Ein Zeilenfilter kann nur Felder prüfen, die der Benutzer lesen darf, und eine Maske gilt für Werte von Text- und Keyword-Feldern.
- Indizes, deren Namen mit einem Punkt beginnen, werden nur über Richtlinienmuster erreicht, die mit einem Punkt beginnen.
- Die Tenants des Security-Plug-ins für OpenSearch Dashboards verwaltet der Agent nicht.

## Weitere Anleitungen

- [OpenSearch](/de/plugins/opensearch/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
