---
title: Apache HBase-Agent
description: Den Coprocessor in HBase-Mastern und -Region-Servern installieren, um Operationen, Lese- und Schreibzugriffe mit GrantForge-Richtlinien zu prüfen, nur die Spalten zurückzugeben, die ein Benutzer lesen darf, und zu verbergen, was er nicht nutzen darf.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der HBase-Agent ist ein Coprocessor von Mastern, Regionen und Region-Servern: HBase fragt ihn zu jeder Operation, jedem Lese- und Schreibzugriff, und er antwortet aus den signierten Richtlinien des `hbase`-Datendienstes. Die Distribution liefert ihn als ein Java-8-kompatibles JAR, `agents/hbase/grantforge-agent-hbase-<version>.jar`, mit eingebetteten, umbenannten Abhängigkeiten.

Die Testcontainers-Integrationstests des Repositorys führen das JAR in HBase `2.5.16`, `2.6.7` und `3.0.0` aus. Das zertifiziert nicht jede andere Version. Das Dienst-Plug-in beschreibt [Apache HBase](/de/plugins/hbase/).

## Funktionsweise

- **Jede Anfrage wird in jedem Geltungsbereich geprüft, den sie nennt.** In HBase deckt eine Berechtigung alles in ihrem Geltungsbereich ab, daher entscheidet der Agent eine Anfrage an ihrem Namespace, ihrer Tabelle, ihrer Spaltenfamilie und ihrer Spalte: Eine Verweigerung in irgendeinem lehnt sie ab, eine Erlaubnis in irgendeinem gewährt sie.
- **Die Eigentümer eines Namespace haben seine Tabellen.** `create` und `admin` auf einem Namespace lassen einen Benutzer den Namespace anlegen, Tabellen darin anlegen und sie verwalten, ohne Richtlinie je Tabelle.
- **Lesen liefert, was gelesen werden darf.** Darf ein Benutzer nur einen Teil einer Tabelle lesen, etwa eine Spaltenfamilie, liefern Get und Scan diese Spalten, statt zu scheitern; wer nichts von der Tabelle lesen darf, wird abgelehnt.
- **Listen lassen weg, was nicht genutzt werden darf.** Das Auflisten von Namespaces und Tabellen zeigt nur die, die der Benutzer nutzen darf oder in denen er etwas nutzen darf, und das Auflisten von Snapshots nur die der Tabellen, die er verwaltet; das Beschreiben eines Namespace, den er nicht nutzen darf, wird abgelehnt.
- **Ablehnungen sagen warum.** Der Client erhält `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)` oder die Richtlinie, die es verweigert hat.
- **Entscheidungen werden behalten und Zugriffe zusammengefasst.** Die Entscheidungen des aktuellen Snapshots werden behalten, standardmäßig 100.000 für 10 Sekunden, und ein neuer Snapshot beginnt von vorn. Gleiche erlaubte Zugriffe innerhalb eines Fensters, standardmäßig 10 Sekunden, werden als ein Zugriffsereignis gemeldet, das sagt, wie viele es waren; Ablehnungen werden sofort einzeln gemeldet.
- **Die Server werden nicht geprüft.** HBases Superuser, genannt in `hbase.superuser`, und die eigene Arbeit der Server werden weder geprüft noch auditiert. Tabellen des Namespace `hbase`, etwa `hbase:meta`, darf jeder lesen, wie Clients es müssen.

| Operation | Benötigter Zugriff |
| --- | --- |
| Get, Scan | `read` auf der Tabelle oder auf den gelieferten Spalten |
| Put, Increment, Append, Delete von Spalten | `write` auf jeder geschriebenen Spalte |
| Delete einer Spaltenfamilie oder Zeile | `write` auf jeder Spaltenfamilie, da HBase eine Zeile Familie für Familie löscht |
| Check-and-Mutate | `read` auf der verglichenen Spalte, bei einem Filter auf der Tabelle, und `write` auf dem Geschriebenen |
| Tabelle anlegen | `create` auf dem Namespace |
| Löschen, Leeren, Ändern, Aktivieren, Deaktivieren, Flush, Split, Merge, Compaction | `admin` oder `create` auf der Tabelle |
| Verschieben, Zuweisen, Freigeben, Offline nehmen | `admin` auf der Tabelle |
| Snapshot anlegen, wiederherstellen, löschen oder auflisten | `admin` auf der Tabelle; Klonen braucht zudem `create` auf dem Ziel-Namespace |
| Bulk Load | `create` auf der Tabelle |
| Namespace anlegen, ändern, löschen | `admin` auf dem Namespace |
| Namespace beschreiben; Namespaces und Tabellen auflisten | Irgendein Zugriff darauf oder darin |
| Balancer, Herunterfahren, Quotas, Replikations-Peers, Region-Server-Gruppen, Prozeduren, Außerbetriebnahme, Stoppen von Region-Servern, WAL-Rollen | `admin` auf `global` |

## Bereitstellung

1. Fügen Sie einen `hbase`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agent-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur HBases Konto lesen kann.
2. Kopieren Sie das JAR nach `lib` **jedes Masters und Region-Servers**, etwa `/opt/hbase/lib`.
3. Ergänzen Sie die Einstellungen in der `hbase-site.xml` jedes Knotens und ersetzen Sie Beispieladresse, Pfade und Instanznamen:

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   Das Cache-Verzeichnis muss für HBases Konto beschreibbar sein. Ersetzen Sie HBases eigenen `AccessController` in den Coprocessor-Listen, statt beide zu laden.
4. Starten Sie Master und Region-Server nacheinander neu. Bis der Agent seinen ersten Snapshot hat, werden alle Anfragen außer denen der Superuser abgelehnt.
5. **Vorhandene Berechtigungen zuerst übertragen:** Grants, die HBases `AccessController` in `hbase:acl` gespeichert hat, gelten nicht mehr. Listen Sie sie vorher mit `user_permission` auf und legen Sie passende Richtlinien an.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Erforderlich: der GrantForge-Server, die Datei des Agent-Tokens, ein Name für diesen Master oder Region-Server und ein beschreibbares Verzeichnis für den signierten Snapshot und gepufferte Ereignisse |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten öffentlichen X.509-Ed25519-Schlüssel von der Agentenseite. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeout zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Puffergrenze (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange gleiche erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, höchstens `3600000` Millisekunden; `0` meldet jeden einzeln |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Höchstzahl behaltener Entscheidungen, Standard `100000`, höchstens `10000000`, `0` behält keine; und wie lange, Standard `10000`, `1..3600000` Millisekunden |

HBase startet einen Coprocessor für den Master, für den Region-Server und für jede Region, die er öffnet; alle teilen sich einen Agenten, einen Entscheidungscache und die Zusammenfassung der Ereignisse.

## Benutzer, Gruppen und Rollen

Richtlinien nennen den Kurznamen, den HBase dem Benutzer der Verbindung gibt: den Benutzernamen der einfachen Authentifizierung oder den Kurznamen eines Kerberos-Principals nach `hadoop.security.auth_to_local`. Gruppen sind die, die Hadoops Gruppenzuordnung dem Benutzer auf dem Server gibt, zusammen mit den Gruppen, in die GrantForge ihn aufnimmt; Rollen sind die, die GrantForge ihm gibt.

## Entscheidungen und Grenzen

- Ohne verwendbaren Snapshot wird alles außer den Anfragen der Superuser und dem Lesen des Namespace `hbase` abgelehnt. Ein nicht genutzter Dienst lässt HBase ungeprüft, wie ohne Autorisierer.
- Ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `hbase` ist, lehnt alles ab.
- HBases `grant`, `revoke` und `user_permission` scheitern mit *AccessController is not loaded*: Berechtigungen sind GrantForge-Richtlinien.
- Aufrufe von Coprocessor-Endpunkten werden nicht geprüft; laden Sie nur Endpunkte, denen Sie vertrauen.
- HBase gibt dem Ersteller einer Tabelle hier keine eigene Berechtigung; eine Namespace-Richtlinie wie oben gibt seinen Eigentümern ihre Tabellen.
- HBase protokolliert manche Ablehnungen, statt sie zurückzugeben, etwa beim Balancer-Schalter: Der Schalter bleibt, wie er war, aber der Client erfährt es nicht.
- Entscheidungen werden `grantforge.decision.cache.ttl.ms` lang behalten, sodass der Gültigkeitszeitraum einer Richtlinie bis zu so viel später beginnt oder endet.
- HBases Berechtigungen auf Zellebene und Sichtbarkeits-Labels sind nicht abgedeckt. Spalten werden als UTF-8-Text verglichen.

## Weitere Anleitungen

- [Apache HBase](/de/plugins/hbase/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
