---
title: Apache ZooKeeper
description: Das ZooKeeper-Dienst-Plug-in installieren und konfigurieren, Znodes durchsuchen und Zugriffsrichtlinien für Znode-Pfade verwalten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Apache-ZooKeeper-Plug-in stellt in GrantForge Verbindungen zu Ensembles, die Suche nach Znode-Pfaden und die Verwaltung von Richtlinien bereit. Plug-in-ID und Diensttyp lauten beide `zookeeper`.

## Installation

Die Distribution enthält das Plug-in in `plugins/zookeeper`. Prüfe unter **Plattformverwaltung → Plug-ins**, dass `zookeeper` aktiviert ist; nach einer Aktualisierung das Plug-in-Verzeichnis neu einlesen. Das Plug-in nutzt den ZooKeeper-3.9-Client in einem eigenen Classloader, getrennt von den Bibliotheken des Servers.

## Datendienst hinzufügen

1. Öffne **Datenberechtigungen → Datendienste**, füge einen Dienst hinzu und wähle ZooKeeper (`zookeeper`).
2. Gib den Verbindungsstring ein: die Server des Ensembles mit optionalem Chroot, etwa `zk1:2181,zk2:2181,zk3:2181/kafka`. Der Client wechselt selbstständig zwischen den Servern.
3. Wähle die Authentifizierung, lege den Such-Znode fest und teste die Verbindung (prüft, dass der Znode existiert und seine Kinder gelistet werden können); speichere dann den Dienst.

| Einstellung | Zweck |
| --- | --- |
| `zookeeper.connect` | Pflichtangabe: `host[:port]` durch Kommas getrennt, optional gefolgt von `/chroot`; der Chroot darf nicht auf `/` enden |
| `authentication` | `none` (Standard), `digest` oder `kerberos` |
| `username` | Digest: der Digest-Benutzer der ACLs (ohne Doppelpunkt); Kerberos: der Principal, etwa `grantforge@EXAMPLE.COM` |
| `password` | Passwort des Digest-Benutzers; verschlüsselt gespeichert |
| `keytab` | Kerberos: Pfad zur Keytab des Principals auf dem GrantForge-Server |
| `zookeeper.sasl.client.username` | Kerberos: erster Teil der Server-Principals, Standard `zookeeper`, wie in `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos: vollständiger Server-Principal, wenn er diese Form nicht hat, etwa `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | Ob der TLS-Client-Port der Server verwendet wird, Standard `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | Truststore zur Prüfung der Server; leer vertraut dem, was die Java-Umgebung des Servers vertraut; Typ `jks`, `pkcs12` oder `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | Keystore mit Client-Zertifikat für Server, die eines verlangen |
| `session.timeout.ms` | Sitzungs-Timeout, Standard `15000`, Bereich `2000..120000` |
| `connection.timeout.ms` | Nur Wartezeit für die erste Verbindung und Authentifizierung, Standard `10000` ms, Bereich `1000..60000`; begrenzt keine späteren synchronen Anfragen |
| `request.timeout.ms` | Maximale Wartezeit pro synchroner Anfrage, Standard `8000` ms, Bereich `1000..60000` |
| `lookup.path` | Start-Znode für Suche und Durchsuchen unterhalb des Chroot, Standard `/`; etwa `/brokers`, das Durchsuchen bleibt darauf beschränkt |
| `lookup.max.entries` | Höchstzahl der Kinder, die von einem Znode auf einmal gelesen werden, Standard `10000`, Bereich `1..100000` |

## Authentifizierung

- **none**: keine Anmeldung; nur Znodes, deren ACL `world:anyone` offensteht, sind lesbar.
- **digest**: Anmeldung als `username:password`, passend zu den `digest`-Identitäten der ZooKeeper-ACLs. Ein falsches Passwort wird bei der Anmeldung nicht abgelehnt, sondern ergibt eine andere Identität; geschützte Znodes erscheinen dann als Zugriff verweigert.
- **kerberos**: Anmeldung über SASL mit einer Keytab; die `krb5.conf` des Servers muss zum KDC führen. Jeder Principal mit seiner Keytab erhält einen eigenen JAAS-Abschnitt neben der Konfiguration des Prozesses, sodass ein GrantForge mehrere Ensembles als verschiedene Principals erreicht. Eine Kerberos-Anmeldung nur mit Passwort wird noch nicht unterstützt.

Beim Speichern wird geprüft: die Form des Verbindungsstrings, Benutzer und Passwort für Digest, Principal und Keytab für Kerberos, Keytab und Server-Principal nur mit Kerberos sowie die Bereiche der Timeouts und des Limits.

Bei deaktiviertem TLS müssen die Pfade und Passwörter von Truststore und Keystore leer bleiben. Ein Store-Passwort erfordert den zugehörigen Store-Pfad. Für beide Stores sind nur die Typen `jks`, `pkcs12` und `pem` zulässig.

Führende und nachgestellte Leerzeichen in TLS-Store-Passwörtern bleiben erhalten. Die Dienstkonfiguration bestimmt den TLS-Modus und die für diese Verbindung verwendeten Stores und überschreibt bereits vorhandene ZooKeeper-TLS- und Store-Einstellungen der JVM; Serverzertifikate und Hostnamen werden weiterhin geprüft.

## Znodes durchsuchen

Wähle den Dienst unter **Datenberechtigungen → Richtlinien** und nutze **Durchsuchen** neben `path`:

- Kinder werden seitenweise nach Namen gelistet; ein Znode mit eigenen Kindern lässt sich weiter öffnen.
- Jeder Znode zeigt seine ACL (`scheme:id:cdrwa`, wie ZooKeepers eigene Werkzeuge sie schreiben), die Größe seiner Daten und die letzte Änderung.
- Ein Znode, dessen ACL der Suchbenutzer nicht lesen darf, wird trotzdem mit Namen gelistet; beim Öffnen wird der Grund genannt.

Werden während des Blätterns Znodes gelöscht, wird der Cursor dennoch anhand des zuletzt durchlaufenen untergeordneten Namens weitergesetzt, auch wenn der betreffende Knoten nicht mehr existiert. Die Liste der untergeordneten Knoten ist keine Momentaufnahme: Gleichzeitige Ergänzungen oder Löschungen folgen der ZooKeeper-Semantik und können spätere Seiten verändern.

Beim Tippen eines Pfads erscheinen Vorschläge; ein nicht vorhandener Elternknoten hat keine, und Pfade außerhalb von `lookup.path` werden abgelehnt. Ein Znode mit mehr Kindern als `lookup.max.entries` führt zu einem Fehler, statt nur einen Teil zu zeigen.

## Richtlinien durchsetzen

Es gibt eine Ressourcenebene, `path`; die Zugriffsarten folgen den ACL-Rechten von ZooKeeper: `read`, `write`, `create`, `delete` und `admin`. Pfadrichtlinien unterstützen Rekursion und Ausschlüsse.

Das Dienst-Plug-in übernimmt Verwaltung, Suche und Durchsuchen; der Agent im ZooKeeper-Server setzt Richtlinien für Marker-ACLs über einen SASL-Authentifizierungsprovider durch. `mvn -pl agents/grantforge-agent-zookeeper -am package` erzeugt eine einzelne Java-8-kompatible Shaded-JAR mit ihren Abhängigkeiten. In der Distribution liegt sie unter `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. Die Implementierung ist auf die APIs von ZooKeeper 3.6–3.9 ausgelegt; die Kompatibilität mit den einzelnen Versionen muss noch praktisch validiert werden. Weder die vollständige Versionsmatrix noch eine Produktionsabnahme gelten damit als abgeschlossen.

### Bereitstellung

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

### ACLs und Einschränkungen

- Nur die Marker-ACL `sasl:grantforge` wird von GrantForge-Richtlinien entschieden, etwa `sasl:grantforge:cdrwa` mit `c=create`, `d=delete`, `r=read`, `w=write` und `a=admin`. Andere native ACLs, einschließlich anderer SASL-Identitäten, funktionieren unverändert.
- Läuft der Agent nicht, fehlt ein nutzbarer Richtlinien-Snapshot, ist dessen `serviceType` nicht `zookeeper` oder passt/erlaubt keine Richtlinie den Zugriff, lehnt der Marker ab. **Das ist kein globales Deny**: Andere ACLs, `world:anyone` oder eine `super`-Identität können die Anfrage weiterhin zulassen.
- Richtlinien prüfen den absoluten Zielpfad auf dem Server. Bei `create`/`delete` ist das der zu erstellende/löschende Znode, nicht der Elternknoten, dessen ACL geprüft wird. Bei Chroot-Anfragen muss der tatsächliche Chroot-Präfix im Richtlinienpfad stehen: `/brokers` unter Chroot `/kafka` entspricht `/kafka/brokers`. Sequenzielles `create` prüft den absoluten Anfragepräfix auf dem Server vor dem Anhängen der Sequenznummer; spätere `read`/`delete`-Prüfungen verwenden den vollständigen erzeugten Knotennamen.
- Native ACLs werden nicht automatisch migriert, und ACLs werden nicht rekursiv vererbt; neue Znodes brauchen eine ausdrücklich gesetzte Marker-ACL. Prüfe vor `setACL` erlaubte und abgelehnte Zugriffe in einem kleinen Bereich und behalte einen unabhängigen administrativen Zugang. Setze den Marker nicht unüberlegt am Wurzelknoten, um dich nicht auszusperren.
- Ein Server ohne Agent gleicht `sasl:grantforge` mit der nativen SASL-Identität `grantforge` ab, statt Richtlinien auszuwerten. Deshalb ist die Bereitstellung auf allen Knoten erforderlich; eine teilweise Bereitstellung schützt das Ensemble nicht.
- Bei Schreibanfragen, die ein Follower/Observer an den Leader weiterleitet, kann an dieser Provider-Schnittstelle die Client-Verbindung fehlen; der `clientAddress`-Kontext kann daher leer sein. Durchgängige Ensemble-Richtlinien auf Basis der Client-IP sind nicht garantiert.
- Dieser Provider kennt das endgültige Ergebnis einer Anfrage nicht. Er sendet daher keine abschließenden `AccessEvent`-Auditereignisse und bietet kein vollständiges Zugriffsaudit.

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Plug-ins und Diensttypen entwickeln](/de/develop/plugins/)
