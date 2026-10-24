---
title: Apache Solr-Agent
description: Das Autorisierungs-Plug-in in Solr-Knoten installieren, um mit GrantForge-Richtlinien zu entscheiden, wer welche Collections abfragen und aktualisieren darf, über welche Anfragepfade, welche Collections und den Cluster administrieren darf und aus welchen Netzen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der Solr-Agent ist ein Autorisierungs-Plug-in der Knoten: Solr fragt ihn zu jeder Anfrage, und er antwortet aus den signierten Richtlinien des `solr`-Datendiensts. Die Distribution liefert ihn als ein Java-11-JAR, `agents/solr/grantforge-agent-solr-<version>.jar`, mit eingebetteten und verschobenen Abhängigkeiten.

Die Testcontainers-Integrationstests des Repositorys führen das JAR in SolrCloud-Knoten von Solr `9.4.1`, `9.8.1`, `9.11.0` und `10.0.0` aus. Das zertifiziert nicht jede andere Version. Das Dienst-Plug-in beschreibt [Apache Solr](/de/plugins/solr/).

## Funktionsweise

- **Anfragen werden so eingeordnet, wie Solr sie einordnet.** Jeder Anfrage-Handler nennt die Berechtigung, die er braucht, etwa `read` oder `collection-admin-edit`, wie für Solrs eigenes regelbasiertes Plug-in; der Agent macht aus diesem Namen eine Zugriffsart, und ein Handler, der keine nennt, wird nach der Art der Anfrage entschieden.
- **Jede Anfrage an eine Collection wird auch an ihrem Anfragepfad entschieden.** Der Agent entscheidet eine Abfrage von `sales` über `/select` an der Collection `sales` und an `sales` mit dem Pfad `/select`: eine Ablehnung an einer der beiden verweigert sie, sodass eine Richtlinie einen Handler einer Collection verweigern kann.
- **Teams administrieren ihre eigenen Collections.** Eine Collections-API-Anfrage, die Collections nennt, braucht `admin_collection` auf jeder von ihnen, sodass eine Richtlinie ein Team nur die Collections anlegen, neu laden oder löschen lassen kann, deren Name mit seinem Präfix beginnt.
- **Die Administratoren des Clusters dürfen alles.** `admin` auf dem Cluster gewährt jede Anfrage, wie Solrs eigene Berechtigung `all`.
- **Richtlinien können nur für einige Netze gelten.** Jede Prüfung trägt die Adresse des Clients; hinter Reverse-Proxys, denen der Agent vertraut, ist der Client die letzte Adresse in `X-Forwarded-For`, die kein vertrauenswürdiger Proxy hinzugefügt hat.
- **Die geprüften Rollen eines JSON Web Tokens zählen als Gruppen.** Mit Solrs JWT-Authentifizierung sind die Rollen, die der Claim des Tokens dem Benutzer gibt, Gruppen der Entscheidung, neben denen, in die GrantForge den Benutzer einordnet.
- **Ablehnungen werden mit ihrem Grund protokolliert.** Solr beantwortet eine abgelehnte Anfrage mit seinem eigenen 403; das Protokoll des Knotens nennt die GrantForge-Richtlinie, die sie verweigert hat, oder dass keine sie erlaubt.
- **Entscheidungen werden zwischengespeichert und Zugriffe zusammengefasst.** Die Entscheidungen des aktuellen Snapshots werden behalten, standardmäßig 100.000 für 10 Sekunden, und ein neuer Snapshot beginnt von vorn. Gleiche erlaubte Zugriffe innerhalb eines Fensters, standardmäßig 10 Sekunden, werden als ein Zugriffsereignis mit ihrer Anzahl gemeldet; Ablehnungen werden einzeln und sofort gemeldet. Jedes Ereignis nennt die Collection und den Pfad oder den Cluster sowie die Client-Adresse.
- **Die Knoten werden nicht geprüft.** Anfragen zwischen den Knoten, die Solr als Knoten selbst signiert, passieren; eine Anfrage ohne Benutzer wird zur Anmeldung aufgefordert.

| Anfrage | Benötigter Zugriff |
| --- | --- |
| Suchen, Abrufen, Exportieren, Abfragen über einen beliebigen Lese-Handler | `query` auf Collection und Pfad |
| Dokumente hinzufügen, aktualisieren oder löschen | `update` auf Collection und Pfad |
| Das Schema einer Collection lesen oder ändern | `schema_read` oder `schema_edit` |
| Die Config einer Collection lesen oder ändern | `config_read` oder `config_edit` |
| Collections-API-Anfragen, die Collections nennen: Anlegen, Neuladen, Sichern, Wiederherstellen, Löschen, Ändern, ihr Cluster-Status | `admin_collection` auf jeder genannten Collection |
| Health-Checks; Metriken | `health`; `metrics` auf dem Cluster |
| Collections auflisten, Core-Status, ZooKeeper, File Store und Pakete lesen | `admin_read` auf dem Cluster |
| Authentifizierung und Autorisierung lesen oder ändern | `security` auf dem Cluster |
| Core-Administration, Änderungen an File Store und Paketen und alles, was kein Handler nennt | `admin` auf dem Cluster |

## Bereitstellung

1. Fügen Sie einen `solr`-Datendienst hinzu und konfigurieren Sie seine Richtlinien, einschließlich `admin` auf dem Cluster für die Administratoren von Solr. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agent-Token aus. Es wird nur einmal angezeigt; legen Sie es in einer Datei ab, die nur Solrs Konto lesen kann.
2. Kopieren Sie das JAR in `server/solr-webapp/webapp/WEB-INF/lib` **jedes Knotens**, etwa `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`, und starten Sie die Knoten nacheinander neu.
3. Setzen Sie den Autorisierungsabschnitt der `security.json` des Clusters neben einem Authentifizierungs-Plug-in und ersetzen Sie Beispieladresse, Pfade und Instanznamen:

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   Laden Sie sie mit `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>` hoch; die Knoten übernehmen sie ohne Neustart. Die Authentifizierung muss aktiviert sein, damit Anfragen Benutzer haben.
4. **Beachten Sie unter Solr 9 Javas Security Manager**, den Solr standardmäßig aktiviert: Legen Sie Token, angehefteten Schlüssel und Cache-Verzeichnis unter `SOLR_HOME` ab, etwa `/var/solr/data`, und gewähren Sie dem Agenten, was er über Solrs eigene Policy hinaus braucht, in `server/etc/security.policy` oder einer eigenen Policy-Datei, die `-Djava.security.properties` als `policy.url.3` hinzufügt:

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10 läuft ohne Security Manager.
5. **Übertragen Sie bestehende Berechtigungen zuerst:** Die Berechtigungen und Benutzerrollen von Solrs regelbasiertem Plug-in gelten nicht mehr. Legen Sie vorher passende Richtlinien an. Bis der Agent seinen ersten Snapshot hat, werden Anfragen außer denen zwischen den Knoten verweigert.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Pflicht: der GrantForge-Server, die Datei des Agent-Tokens, ein Name für die Knoten des Clusters und ein beschreibbares Verzeichnis für den signierten Snapshot und gepufferte Ereignisse |
| `grantforge.trusted.proxies` | Reverse-Proxys, deren `X-Forwarded-For` der Agent vertraut, Adressen oder Bereiche durch Kommas getrennt; standardmäßig keine |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots anheftet; kopieren Sie den Base64-codierten X.509-Ed25519-Public-Key von der Agenten-Seite. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeout zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batch-Größe (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Spool-Grenze (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange gleiche erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` meldet jeden |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Die meisten behaltenen Entscheidungen, Standard `100000`, bis `10000000`, `0` behält keine; und wie lange, Standard `10000`, `1..3600000` Millisekunden |

## Benutzer, Gruppen und Rollen

Richtlinien nennen den Benutzer, den Solrs Authentifizierung anmeldet, etwa einen Benutzer der Basic-Authentifizierung oder den Principal eines JSON Web Tokens. Gruppen sind die Rollen, die ein JSON Web Token für den Benutzer geprüft hat, zusammen mit den Gruppen, in die GrantForge den Benutzer einordnet; Rollen sind die, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne verwendbaren Snapshot wird alles außer den Anfragen zwischen den Knoten verweigert. Ein nicht verwendeter Dienst lässt Solr ungeprüft, wie ohne Autorisierungs-Plug-in.
- Ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `solr` ist, verweigert alles.
- Eine Collections-API-Anfrage, die ihre Collections nur in einem JSON-Body nennt, wie manche Anfragen der v2-API, nennt keine, die der Agent sehen kann, und braucht `admin` auf dem Cluster.
- Solr löst Aliasse in ihre Collections auf, bevor es fragt; Richtlinien nennen daher Collections, keine Aliasse.
- Solrs API `/admin/authorization` kann die Regeln des Agenten nicht bearbeiten: GrantForge hält die Richtlinien.
- Client-Adressen sind nur Adressen und Bereiche; ein Hostname passt auf nichts.
- Entscheidungen werden für `grantforge.decision.cache.ttl.ms` behalten, sodass eine Richtlinie bis zu so viel später zu gelten beginnt oder aufhört.

## Weitere Anleitungen

- [Apache Solr](/de/plugins/solr/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
