---
title: Apache Pulsar-Agent
description: Den Autorisierungsanbieter in Pulsar-Brokern installieren, um mit GrantForge-Richtlinien zu entscheiden, wer wo produzieren und konsumieren darf, über welche Subscriptions, Functions, Sources und Sinks betreiben, Topics, Namespaces, Tenants und Cluster verwalten darf und aus welchen Netzen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der Pulsar-Agent ist ein Autorisierungsanbieter der Broker: Pulsar fragt ihn, wer produzieren, konsumieren, Functions, Sources und Sinks betreiben und Topics, Namespaces, Tenants und Cluster verwalten darf, und er antwortet aus den signierten Richtlinien des `pulsar`-Datendiensts. Die Distribution liefert ihn als ein Java-17-JAR, `agents/pulsar/grantforge-agent-pulsar-<version>.jar`, mit eingebetteten und verschobenen Abhängigkeiten.

Die Testcontainers-Integrationstests des Repositorys führen das JAR in den Brokern von Pulsar `3.0.17`, `3.3.9`, `4.0.14` und `4.2.5` aus. Das zertifiziert nicht jede andere Version. Das Dienst-Plug-in beschreibt [Apache Pulsar](/de/plugins/pulsar/).

## Funktionsweise

- **Jede Prüfung wird auf jeder Ebene des Topic-Namens entschieden.** Wie bei Pulsars eigenen Berechtigungen umfasst eine Berechtigung auf einem Tenant oder Namespace, was darin ist, daher entscheidet der Agent das Produzieren nach `persistent://sales/orders/payments` im Tenant `sales`, im Namespace `orders` und im Topic `payments`: Eine Verweigerung in einem davon lehnt ab, eine Erlaubnis in einem davon gewährt.
- **Konsumieren wird auch nach der Subscription entschieden.** Auf jeder dieser Ebenen fragt der Agent auch nach dem Topic mit der Subscription, sodass eine Richtlinie eine Rolle nur über einige Subscriptions konsumieren lassen kann, etwa `billing-*`.
- **Die Verwalter des Clusters sind Pulsars Superuser.** `admin` auf dem Cluster des Brokers macht eine Rolle zu einem davon, und Pulsar lässt seine Superuser alles tun, daher schränken die Richtlinien darunter sie nicht ein. Pulsars Broker- und Cluster-Operationen brauchen `admin` auf dem Cluster, den sie nennen.
- **Richtlinien können nur für einige Netze gelten.** Jede Prüfung trägt die Adresse des Clients, und der Agent wertet die Bedingung **Client-Adressen** mit Nettys eigenen Adressbereichen aus, die der Netzwerk-Stack des Brokers nutzt.
- **Berechtigen über Pulsar wird abgelehnt.** Pulsars eigene Berechtigungen und die Admin-Rollen seiner Tenants werden nicht verwendet, daher werden `pulsar-admin namespaces grant-permission` und Ähnliches mit HTTP 405 beantwortet, mit dem Hinweis, in GrantForge zu berechtigen.
- **Ablehnungen werden mit ihrem Grund protokolliert.** Pulsar formuliert die Ablehnung, die der Client erhält; das Protokoll des Brokers nennt die GrantForge-Richtlinie, die verweigert hat, oder dass keine es erlaubt.
- **Entscheidungen werden behalten und Zugriffe zusammengefasst.** Die Entscheidungen des aktuellen Snapshots werden behalten, standardmäßig 100.000 für 10 Sekunden, und ein neuer Snapshot beginnt von vorn. Gleiche erlaubte Zugriffe innerhalb eines Fensters, standardmäßig 10 Sekunden, werden als ein Zugriffsereignis gemeldet, das sagt, wie viele es waren; Ablehnungen werden einzeln und sofort gemeldet. Jedes Ereignis nennt Topic, Namespace, Tenant oder Cluster und die Client-Adresse.
- **Ein Agent dient dem ganzen Broker.** Ein Broker lädt seinen Autorisierungsanbieter für sein Binärprotokoll, seinen Webdienst und seinen Functions Worker; sie teilen einen Agenten, der mit dem Broker stoppt.

| Operation | Benötigter Zugriff |
| --- | --- |
| Produzieren oder ein Topic durch Produzieren anlegen | `produce` auf dem Topic |
| Abonnieren, konsumieren, abbestellen, überspringen, einsehen, Nachrichten ablaufen lassen, einen Cursor zurücksetzen | `consume` auf dem Topic mit der Subscription |
| Ein Topic nachschlagen, seine Statistiken, Metadaten und Richtlinien lesen; die Topics eines Namespace auflisten | `produce` oder `consume` |
| Ein Topic beenden, kompaktieren, auslagern, entladen oder kürzen; seine Richtlinien ändern | `admin_topic` auf dem Topic |
| Topics anlegen und löschen; die Richtlinien eines Namespace ändern | `admin_namespace` auf dem Namespace |
| Functions, Sources, Sinks betreiben; Packages nutzen | `functions`, `sources`, `sinks`, `packages` auf dem Namespace |
| Die Namespaces eines Tenants anlegen, auflisten und löschen | `admin_tenant` auf dem Tenant |
| Tenants und Cluster anlegen, ändern und löschen; Pulsars Broker-Operationen | `admin` auf dem Cluster |

## Bereitstellung

1. Fügen Sie einen `pulsar`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agent-Token aus. Es wird nur einmal angezeigt; legen Sie es in einer Datei ab, die nur Pulsars Konto lesen kann.
2. Kopieren Sie das JAR nach `lib` **jedes Brokers**, etwa `/pulsar/lib`.
3. Ergänzen Sie die Einstellungen in der `broker.conf` jedes Brokers oder in der `standalone.conf` und ersetzen Sie Beispieladresse, Pfade und Instanznamen:

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   Auch die Authentifizierung muss aktiviert sein, damit Clients Rollen haben. `superUserRoles` und `proxyRoles` dürfen nur die eigene Rolle der Broker und die der Proxys nennen: Der Agent prüft sie nicht, und Pulsar prüft auch den ursprünglichen Client eines Proxys. Geben Sie Administratoren stattdessen `admin` auf dem Cluster. Das Cache-Verzeichnis muss für Pulsars Konto beschreibbar sein. Schreiben Sie in einem Container die `grantforge.*`-Einstellungen in die Datei: Die Shell mancher Pulsar-Images verwirft Umgebungsvariablen, deren Namen Punkte enthalten, etwa `PULSAR_PREFIX_grantforge.token.file`.
4. Starten Sie die Broker nacheinander neu. Bis der Agent seinen ersten Snapshot hat, werden Anfragen außer denen der Broker und Proxys abgelehnt.
5. **Vorhandene Berechtigungen zuerst übertragen:** Pulsars eigene Berechtigungen auf Namespaces, Topics und Subscriptions sowie die Admin-Rollen der Tenants gelten nicht mehr. Legen Sie vorher passende Richtlinien an.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Erforderlich: der GrantForge-Server, die Datei des Agent-Tokens, ein Name für diesen Broker und ein beschreibbares Verzeichnis für den signierten Snapshot und zwischengespeicherte Ereignisse |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten X.509-Ed25519-Public-Key von der Seite Agenten. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeout zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Spool-Grenze (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange gleiche erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` meldet jeden |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Die meisten behaltenen Entscheidungen, Standard `100000`, bis `10000000`, `0` behält keine; und wie lange, Standard `10000`, `1..3600000` Millisekunden |

## Benutzer, Gruppen und Rollen

Richtlinien nennen die Rolle, die Pulsars Authentifizierung dem Client gibt, etwa das Subject eines Tokens oder den Common Name eines Zertifikats, als Benutzer. Pulsar hat keine eigenen Gruppen: Gruppen sind die, in die GrantForge die Rolle stellt, und Rollen die, die GrantForge ihr gibt.

## Entscheidungen und Grenzen

- Ohne verwendbaren Snapshot wird alles außer den Anfragen der Broker und Proxys abgelehnt. Ein nicht verwendeter Dienst lässt Pulsar ungeprüft, wie mit ausgeschaltetem `authorizationEnabled`.
- Ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `pulsar` ist, lehnt alles ab.
- Pulsar fragt vor vielen Prüfungen, ob eine Rolle Superuser oder Admin eines Tenants ist; nur die Antworten, die eine Anfrage durchlassen, werden gemeldet, und die eigene Prüfung der Anfrage wird in jedem Fall gemeldet. Eine Anfrage, die Pulsar allein nach dieser Frage entscheidet, wie Pulsar 3.0 bei Tenants und Clustern, wird bei Ablehnung nur auf Debug-Ebene protokolliert.
- `admin` auf einem Cluster macht eine Rolle nur zum Superuser der Broker dieses Clusters.
- Pulsars Berechtigungslisten, etwa `pulsar-admin namespaces permissions`, schlagen fehl: GrantForge hält die Richtlinien.
- Client-Adressen sind nur Adressen und Bereiche; ein Hostname passt auf keinen Client.
- Entscheidungen werden für `grantforge.decision.cache.ttl.ms` behalten, daher beginnt oder endet die Gültigkeit einer Richtlinie bis zu so viel später.

## Weitere Anleitungen

- [Apache Pulsar](/de/plugins/pulsar/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
