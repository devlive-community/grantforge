---
title: Apache Hadoop YARN-Agent
description: Den Autorisierungsanbieter in YARN-ResourceManagern installieren, um mit GrantForge-Richtlinien zu entscheiden, wer welche Anwendungen an welche Warteschlange übermitteln, Warteschlangen und den Cluster verwalten darf und aus welchen Netzen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der YARN-Agent ist ein Autorisierungsanbieter des ResourceManagers: YARN fragt ihn, wer an eine Warteschlange übermitteln, eine Warteschlange verwalten und den Cluster verwalten darf, und er antwortet aus den signierten Richtlinien des `yarn`-Datendiensts. Die Distribution liefert ihn als ein Java-8-kompatibles JAR, `agents/yarn/grantforge-agent-yarn-<version>.jar`, mit eingebetteten und verschobenen Abhängigkeiten.

Die Testcontainers-Integrationstests des Repositorys führen das JAR in den ResourceManagern von Hadoop `3.2.4`, `3.3.6`, `3.4.3` und `3.5.0` aus. Das zertifiziert nicht jede andere Version. Das Dienst-Plug-in beschreibt [Apache Hadoop YARN](/de/plugins/yarn/).

## Funktionsweise

- **Jede Prüfung wird in jeder Warteschlange des Pfads entschieden.** Wie in YARN umfasst eine Berechtigung auf einer Warteschlange die darunter, daher entscheidet der Agent eine Übermittlung an `root.sales.etl` in `root`, `root.sales` und `root.sales.etl`: Eine Verweigerung in einer davon lehnt ab, eine Erlaubnis in einer davon gewährt.
- **Übermittlungen werden auch nach dem Namen der Anwendung entschieden.** In jeder dieser Warteschlangen fragt der Agent auch nach der Warteschlange mit dem Namen der Anwendung, sodass eine Richtlinie nur einige Anwendungen, etwa `payroll-*`, in eine Warteschlange und die darunter zulassen kann.
- **Richtlinien können nur für einige Netze gelten.** Jede Prüfung trägt die Adresse des Clients, hinter einem Proxy die erste weitergeleitete, und der Agent wertet die Bedingung **Client-Adressen** mit Hadoops eigenen Hostlisten aus.
- **Die Verwaltung des Clusters unterliegt Richtlinien.** YARNs Verwaltungsoperationen, die sonst `yarn.admin.acl` gewährt, brauchen `admin` auf dem Cluster.
- **Ablehnungen werden mit ihrem Grund protokolliert.** YARN formuliert die Ablehnung, die der Client erhält; das Protokoll des ResourceManagers nennt die GrantForge-Richtlinie, die verweigert hat, oder dass keine es erlaubt.
- **Entscheidungen werden behalten und Zugriffe zusammengefasst.** Die Entscheidungen des aktuellen Snapshots werden behalten, standardmäßig 100.000 für 10 Sekunden, und ein neuer Snapshot beginnt von vorn. Gleiche erlaubte Zugriffe innerhalb eines Fensters, standardmäßig 10 Sekunden, werden als ein Zugriffsereignis gemeldet, das sagt, wie viele es waren; Ablehnungen werden einzeln und sofort gemeldet. Jedes Ereignis nennt Anwendung, Warteschlange und Client-Adresse.
- **Der ResourceManager wird nicht geprüft.** Der eigene Benutzer des ResourceManagers, etwa `yarn`, wird weder geprüft noch auditiert.

| Operation | Benötigter Zugriff |
| --- | --- |
| Eine Anwendung übermitteln | `submit_app` auf ihrer Warteschlange, oder auf ihrer Warteschlange mit dem Namen der Anwendung; `admin_queue` auf der Warteschlange genügt auch |
| Die Anwendung eines anderen Benutzers abbrechen, verschieben, ihre Priorität ändern oder all ihre Details sehen | `admin_queue` auf ihrer Warteschlange, zum Verschieben auch `submit_app` auf der neuen; der Eigentümer der Anwendung darf es immer, ebenso, wen ihre eigenen ACLs erlauben |
| `yarn rmadmin`: Warteschlangen, Knoten, Benutzergruppen und ACLs neu laden, Knotenlabels und -attribute ändern und die übrigen Verwaltungsoperationen | `admin` auf dem Cluster |

## Bereitstellung

1. Fügen Sie einen `yarn`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agent-Token aus. Es wird nur einmal angezeigt; legen Sie es in einer Datei ab, die nur YARNs Konto lesen kann.
2. Kopieren Sie das JAR nach `share/hadoop/yarn/lib` **jedes ResourceManagers**, etwa `/opt/hadoop/share/hadoop/yarn/lib`.
3. Ergänzen Sie die Einstellungen in der `yarn-site.xml` jedes ResourceManagers und ersetzen Sie Beispieladresse, Pfade, Benutzer und Instanznamen:

   ```xml
   <configuration>
     <property>
       <name>yarn.acl.enable</name>
       <value>true</value>
     </property>
     <property>
       <name>yarn.authorization-provider</name>
       <value>org.devlive.grantforge.yarn.agent.GrantForgeYarnAuthorizer</value>
     </property>
     <property>
       <name>yarn.admin.acl</name>
       <value>yarn</value>
     </property>
     <property>
       <name>grantforge.server.url</name>
       <value>https://grantforge.example.com</value>
     </property>
     <property>
       <name>grantforge.token.file</name>
       <value>/etc/hadoop/grantforge.token</value>
     </property>
     <property>
       <name>grantforge.instance</name>
       <value>resourcemanager-1</value>
     </property>
     <property>
       <name>grantforge.cache.dir</name>
       <value>/var/lib/hadoop-yarn/grantforge-cache</value>
     </property>
   ```

   `yarn.admin.acl` darf nur den eigenen Benutzer des ResourceManagers nennen: YARN lässt seine Verwalter alles mit jeder Anwendung tun, bevor es den Agent nach der Warteschlange fragt. Das Cache-Verzeichnis muss für YARNs Konto beschreibbar sein.
4. Starten Sie die ResourceManager nacheinander neu. Bis der Agent seinen ersten Snapshot hat, werden Anfragen außer denen des ResourceManagers abgelehnt.
5. **Vorhandene Berechtigungen zuerst übertragen:** Die Warteschlangen-ACLs der Scheduler-Konfiguration, etwa `acl_submit_applications` und `acl_administer_queue`, gelten nicht mehr. Legen Sie vorher passende Richtlinien an.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Erforderlich: der GrantForge-Server, die Datei des Agent-Tokens, ein Name für diesen ResourceManager und ein beschreibbares Verzeichnis für den signierten Snapshot und zwischengespeicherte Ereignisse |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten X.509-Ed25519-Public-Key von der Seite Agenten. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeout zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batchgröße (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und Spool-Grenze (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange gleiche erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` meldet jeden |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Die meisten behaltenen Entscheidungen, Standard `100000`, bis `10000000`, `0` behält keine; und wie lange, Standard `10000`, `1..3600000` Millisekunden |

## Benutzer, Gruppen und Rollen

Richtlinien nennen den Kurznamen, den YARN dem Aufrufer gibt: den Benutzernamen der einfachen Authentifizierung oder den Kurznamen eines Kerberos-Prinzipals nach `hadoop.security.auth_to_local`. Gruppen sind die, die Hadoops Gruppenzuordnung dem Benutzer auf dem ResourceManager gibt, zusammen mit den Gruppen, in die GrantForge den Benutzer stellt; Rollen sind die, die GrantForge dem Benutzer gibt.

## Entscheidungen und Grenzen

- Ohne verwendbaren Snapshot wird alles außer den eigenen Anfragen des ResourceManagers abgelehnt. Ein nicht verwendeter Dienst lässt YARN ungeprüft, wie mit ausgeschaltetem `yarn.acl.enable`.
- Ein Token, das an einen Dienst gebunden ist, dessen Typ nicht `yarn` ist, lehnt alles ab.
- YARN fragt nach seiner Verwaltung ohne die Adresse des Clients, daher gilt eine Bedingung Client-Adressen auf `cluster` nie.
- Der Eigentümer einer Anwendung und die Benutzer, die ihre eigenen ACLs nennen, verwalten sie unabhängig von den Richtlinien, wie in YARN.
- Prüfungen anderer Zugriffsarten, etwa ACLs für Anwendungsprioritäten, werden abgelehnt.
- Ein Hostname in einer Bedingung Client-Adressen kostet eine Namensauflösung des Clients, wann immer eine Entscheidung getroffen statt behalten wird.
- Entscheidungen werden für `grantforge.decision.cache.ttl.ms` behalten, daher beginnt oder endet die Gültigkeit einer Richtlinie bis zu so viel später.

## Weitere Anleitungen

- [Apache Hadoop YARN](/de/plugins/yarn/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
