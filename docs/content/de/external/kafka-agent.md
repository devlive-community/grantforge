---
title: Apache Kafka-Agent
description: Den Agenten in Kafka-Brokern und -Controllern installieren, um jede Anfrage mit GrantForge-Richtlinien zu autorisieren, die Richtlinien als schreibgeschützte ACLs zu zeigen und wiederholte Zugriffe zu einem Audit-Ereignis zusammenzufassen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der Kafka-Agent ist ein Autorisierer der `Authorizer`-API von Kafka, angegeben in `authorizer.class.name`: Broker und Controller fragen ihn zu jeder Anfrage, und er antwortet aus den signierten Richtlinien des `kafka`-Datendienstes. Die Distribution liefert ihn als ein Java-8-kompatibles JAR, `agents/kafka/grantforge-agent-kafka-<version>.jar`, mit eingebetteten, umbenannten Abhängigkeiten.

Die Testcontainers-Integrationstests des Repositorys führen das JAR in Kafka `3.7.2`, `3.9.2`, `4.1.2` und `4.3.1` aus, in KRaft-Knoten, die zugleich Broker und Controller sind. Das zertifiziert nicht jede andere Version. Das Dienst-Plug-in beschreibt [Apache Kafka](/de/plugins/kafka/).

## Funktionsweise

- **Jede Anfrage wird geprüft.** Für jede Ressource, die eine Anfrage berührt, fragt Kafka nach einer ACL-Operation; der Agent antwortet aus den Richtlinien, mit Kafkas Ressourcentypen als Ressourcenebenen und seinen Operationen als Zugriffsarten. Eine Ablehnung erreicht den Client als Kafkas eigener Fehler, etwa `TOPIC_AUTHORIZATION_FAILED`.
- **Idempotente Producer brauchen nur ein Topic.** Kafka lässt einen Producer ohne `idempotent_write` auf dem Cluster starten, wenn er in irgendein Topic schreiben darf, und fragt den Autorisierer danach. Der Agent antwortet aus den Richtlinien selbst: Ein erlaubtes `write` auf einem beliebigen Topic zählt, eine Verweigerung nur in einer Richtlinie, die jedes Topic abdeckt.
- **Die Richtlinien erscheinen als ACLs.** `kafka-acls --list` und `describeAcls` der Admin-API zeigen die jetzt geltenden Richtlinien: einen Wert ohne Platzhalter als Literal-ACL, einen Wert wie `orders*` als Präfix-ACL und `*` als Kafkas Platzhalter; Benutzer als `User:`-Principals, Gruppen als `Group:`, Rollen als `Role:` und die Gruppe `public` als `User:*`. Was ACLs nicht ausdrücken können, fehlt: andere Platzhalter, ausgeschlossene Werte, Einträge mit Bedingungen und die Einträge einer Richtlinie mit Ausnahmen davon. Das Anlegen oder Löschen von ACLs wird abgelehnt: *the ACLs of this cluster are GrantForge policies; change them in GrantForge*.
- **Entscheidungen werden behalten.** Ein Broker stellt bei jedem Produce und Fetch dieselben Fragen; der Agent behält die Entscheidungen des aktuellen Snapshots, standardmäßig 100.000 davon für 10 Sekunden. Ein neuer Snapshot beginnt von vorn, sodass eine Richtlinienänderung gilt, sobald der Agent sie hat.
- **Wiederholte Zugriffe werden zusammengefasst.** Gleiche erlaubte Zugriffe innerhalb eines Fensters, standardmäßig 10 Sekunden, werden als ein Zugriffsereignis gemeldet, das sagt, wie viele es waren und wann der letzte geschah; die Konsole zeigt *N gleiche Zugriffe*. Zugriffe sind gleich, wenn Benutzer, Client-Adresse, Ressource, Zugriffsart, Anfragetyp und entscheidende Richtlinie übereinstimmen. Ablehnungen werden sofort einzeln gemeldet. Kafka wählt, was zu melden ist: Es verlangt zum Beispiel keine Meldung für die Topics, die es aus einer Liste weglässt.
- **Superuser werden nicht geprüft.** Die Principals aus `super.users` dürfen alles, wie mit Kafkas eigenem Autorisierer, und werden nicht auditiert.

## Bereitstellung

1. Fügen Sie einen `kafka`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agent-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur Kafkas Konto lesen kann.
2. Kopieren Sie das JAR nach `libs` **jedes Brokers und Controllers**, etwa `/opt/kafka/libs`.
3. Ergänzen Sie die Einstellungen in der `server.properties` jedes Knotens und ersetzen Sie die Beispiel-Principals, Adresse, Pfade und den Instanznamen:

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` muss die Principals nennen, die Broker und Controller untereinander verwenden, etwa den des Inter-Broker-Listeners, oder Richtlinien müssen ihnen erlauben, was sie tun, etwa `cluster_action` auf dem Cluster. Verbindungen zu einem `PLAINTEXT`-Listener sind `User:ANONYMOUS`; nennen Sie ihn nur, wenn kein Client einen solchen Listener erreicht. Das Cache-Verzeichnis muss für Kafkas Konto beschreibbar sein.
4. Starten Sie Broker und Controller nacheinander neu. Andere Listener als die der Controller öffnen, sobald der Agent seinen ersten Snapshot hat, spätestens nach `grantforge.start.timeout.ms`; bis dahin und immer, wenn kein Snapshot da ist, werden Anfragen abgelehnt.
5. **Vorhandene ACLs zuerst übertragen:** ACLs, die Kafkas eigener Autorisierer gespeichert hat, gelten nicht mehr, sobald der Agent ihn ersetzt. Listen Sie sie vorher mit `kafka-acls --list` auf und legen Sie passende Richtlinien an.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Erforderlich: der GrantForge-Server, die Datei des Agent-Tokens, ein Name für diesen Broker oder Controller und ein beschreibbares Verzeichnis für den signierten Snapshot und gepufferte Ereignisse |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten öffentlichen X.509-Ed25519-Schlüssel von der Agentenseite. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeout zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Puffergrenze (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange gleiche erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, höchstens `3600000` Millisekunden; `0` meldet jeden einzeln |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Höchstzahl behaltener Entscheidungen, Standard `100000`, höchstens `10000000`, `0` behält keine; und wie lange, Standard `10000`, `1..3600000` Millisekunden |
| `grantforge.start.timeout.ms` | Wie lange Listener auf den ersten Snapshot warten, Standard `30000`, `0..600000` Millisekunden |

Der Agent liest seine Einstellungen aus der Konfiguration, die Kafka seinem Autorisierer übergibt. Ein Knoten, der zugleich Broker und Controller ist, erstellt für jede Rolle einen Autorisierer; beide teilen sich einen Agenten.

## Benutzer, Gruppen und Rollen

Richtlinien nennen den Principal, den Kafka der Verbindung gibt: den SASL-Benutzernamen, den Kurznamen eines Kerberos-Principals nach `sasl.kerberos.principal.to.local.rules` oder den Namen eines TLS-Zertifikats nach `ssl.principal.mapping.rules`. Kafka kennt keine Gruppen oder Rollen; die der Richtlinien sind die, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne verwendbaren Snapshot wird alles außer den Anfragen der Superuser abgelehnt. Ein nicht genutzter Dienst lässt Kafka ungeprüft, wie ohne Autorisierer.
- Ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `kafka` ist, lehnt alles ab.
- Die Verweigerung einer Zugriffsart verweigert auch, was sie einschließt: `write` auf einem Topic zu verweigern verweigert `describe`, sodass der Benutzer es nicht sieht. Kafkas eigene ACLs verhalten sich hier anders.
- Kafkas Frage, ob ein Benutzer auf irgendeine Ressource eines Typs zugreifen darf, ist kein Zugriff und wird nicht gemeldet.
- Entscheidungen werden `grantforge.decision.cache.ttl.ms` lang behalten, sodass der Gültigkeitszeitraum einer Richtlinie bis zu so viel später beginnt oder endet.
- Der Agent arbeitet in Brokern und Controllern. Die REST-API von Kafka Connect, Schema-Registries und andere Dienste rund um Kafka sind nicht abgedeckt.

## Weitere Anleitungen

- [Apache Kafka](/de/plugins/kafka/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
