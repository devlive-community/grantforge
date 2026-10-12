---
title: Apache ZooKeeper-Agent
description: Den Agenten auf jedem ZooKeeper-Knoten bereitstellen und GrantForge-Richtlinien über Marker-ACLs durchsetzen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Server-Plug-in `grantforge-plugin-zookeeper` definiert den Diensttyp `zookeeper`, seine Ressourcen und die Verbindungskonfiguration. Zum Durchsetzen der Richtlinien gehört ein zweiter Teil, der innerhalb von ZooKeeper läuft: der Agent, der als SASL-Authentifizierungsprovider an den ACL-Entscheidungen der Server mitwirkt. Er liegt in `agents/grantforge-agent-zookeeper`; der Befehl `mvn -pl agents/grantforge-agent-zookeeper -am package` erzeugt eine einzelne Java-8-kompatible Shaded-JAR mit ihren Abhängigkeiten, die in der Distribution unter `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar` ausgeliefert wird. Die Implementierung ist auf die APIs von ZooKeeper 3.6–3.9 ausgelegt; die Kompatibilität mit den einzelnen Versionen muss noch praktisch validiert werden. Weder die vollständige Versionsmatrix noch eine Produktionsabnahme gelten damit als abgeschlossen.

## Bereitstellung

1. Installiere die JAR im Verzeichnis `lib` **jedes ZooKeeper-Servers**, einschließlich aller Knoten, die Client-Anfragen annehmen können. Erstelle **Agent-Zugangsdaten** im zugehörigen GrantForge-Datendienst vom Typ `zookeeper`. Speichere das Token in einer separaten Datei, die nur das ZooKeeper-Betriebskonto lesen darf; schreibe das Token selbst nicht in `zoo.cfg`.
2. Ergänze auf jedem Server die folgenden Einstellungen in `zoo.cfg` und ersetze Beispieladresse, Pfade und Instanzkennung. `grantforge.server.url` (`http[s]://GrantForge`), `grantforge.token.file`, ein lokales `grantforge.cache.dir` und `grantforge.instance` sind Pflichtangaben. Das Cache-Verzeichnis muss für das ZooKeeper-Betriebskonto beschreibbar sein.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. Dieser Provider **ersetzt den nativen SASL-Provider**. In keinem anderen Eintrag `authProvider.N` darf ein weiterer Provider für dasselbe Scheme `sasl` registriert bleiben. `enableEagerACLCheck=false` ist zwingend: Bei aktivierter Prüfung wird die Provider-Registrierung abgelehnt und der Marker lässt keinen Zugriff zu. Behalte die normale ZooKeeper-Authentifizierung mit SASL, JAAS und Kerberos bei und prüfe ihre Funktion; der Agent ersetzt keine Authentifizierung. Starte die Server nach dem Wartungsverfahren des Ensembles neu.
4. Optional verweist `grantforge.signing.key.file` auf eine Datei mit dem fest hinterlegten öffentlichen Signaturschlüssel. `grantforge.principal.mapping=short` ist der Standard und bildet `kafka/host@REALM` auf `kafka` ab; `full` behält den vollständigen Principal. Benutzer in Richtlinien müssen den abgebildeten Identitäten entsprechen.

## ACLs und Einschränkungen

- Nur die Marker-ACL `sasl:grantforge` wird von GrantForge-Richtlinien entschieden, etwa `sasl:grantforge:cdrwa` mit `c=create`, `d=delete`, `r=read`, `w=write` und `a=admin`. Andere native ACLs, einschließlich anderer SASL-Identitäten, funktionieren unverändert.
- Läuft der Agent nicht, fehlt ein nutzbarer Richtlinien-Snapshot, ist dessen `serviceType` nicht `zookeeper` oder passt/erlaubt keine Richtlinie den Zugriff, lehnt der Marker ab. **Das ist kein globales Deny**: Andere ACLs, `world:anyone` oder eine `super`-Identität können die Anfrage weiterhin zulassen.
- Richtlinien prüfen den absoluten Zielpfad auf dem Server. Bei `create`/`delete` ist das der zu erstellende/löschende Znode, nicht der Elternknoten, dessen ACL geprüft wird. Bei Chroot-Anfragen muss der tatsächliche Chroot-Präfix im Richtlinienpfad stehen: `/brokers` unter Chroot `/kafka` entspricht `/kafka/brokers`. Sequenzielles `create` prüft den absoluten Anfragepräfix auf dem Server vor dem Anhängen der Sequenznummer; spätere `read`/`delete`-Prüfungen verwenden den vollständigen erzeugten Knotennamen.
- Native ACLs werden nicht automatisch migriert, und ACLs werden nicht rekursiv vererbt; neue Znodes brauchen eine ausdrücklich gesetzte Marker-ACL. Prüfe vor `setACL` erlaubte und abgelehnte Zugriffe in einem kleinen Bereich und behalte einen unabhängigen administrativen Zugang. Setze den Marker nicht unüberlegt am Wurzelknoten, um dich nicht auszusperren.
- Ein Server ohne Agent gleicht `sasl:grantforge` mit der nativen SASL-Identität `grantforge` ab, statt Richtlinien auszuwerten. Deshalb ist die Bereitstellung auf allen Knoten erforderlich; eine teilweise Bereitstellung schützt das Ensemble nicht.
- Bei Schreibanfragen, die ein Follower/Observer an den Leader weiterleitet, kann an dieser Provider-Schnittstelle die Client-Verbindung fehlen; der `clientAddress`-Kontext kann daher leer sein. Durchgängige Ensemble-Richtlinien auf Basis der Client-IP sind nicht garantiert.
- Dieser Provider kennt das endgültige Ergebnis einer Anfrage nicht. Er sendet daher keine abschließenden `AccessEvent`-Auditereignisse und bietet kein vollständiges Zugriffsaudit.
