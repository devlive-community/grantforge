---
title: Redis-Agent
description: Den Redis-Agenten neben einem Redis- oder Valkey-Deployment betreiben, um die ACL-Selektoren jedes Benutzers auf jedem Knoten im Einklang mit den GrantForge-Richtlinien zu halten, in einem Befehl ersetzt, Redis mit ACL DRYRUN zu fragen, was Rechte außerhalb von GrantForge erlauben, und Ablehnungen aus den ACL-Logs der Knoten zu melden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Der Redis-Agent ist ein Dienst neben dem Deployment. Er hält die eigene ACL von Redis auf jedem Knoten im Einklang mit den signierten Richtlinien eines `redis`-Datendienstes, auch auf den Replikaten und jedem Knoten eines Clusters, da Redis auf jedem eine eigene ACL führt. Redis prüft dann jeden Befehl selbst, und der Agent steht keinem Client im Weg. Das Distributionspaket liefert ihn als ausführbares JAR `agents/redis/grantforge-agent-redis-<version>.jar`; er braucht Java 17 oder neuer.

Die Testcontainers-Integrationstests des Repositorys führen den Agenten und das ausgelieferte JAR gegen einen Primary mit Replikat von Redis `7.2`, `7.4`, `8.2` und `8.10` und Valkey `8.1` und `9.1` sowie einen Redis-`8.10`-Cluster aus. Das zertifiziert nicht jede andere Version; der Agent lehnt Redis vor `7.2` ab, dessen ACL-Log keine Einträge nummeriert. Zum Service-Plug-in siehe [Redis](/de/plugins/redis/).

## Funktionsweise

- **Die Selektoren gehören GrantForge, die Root-Rechte dem Betrieb.** Redis erlaubt einen Befehl, wenn die Root-Rechte eines Benutzers oder einer seiner Selektoren ihn erlauben. Der Agent hält jeden Selektor der Benutzer, die er betreut, und berührt nie ihre Root-Rechte oder Passwörter: ein Selektor hält die Schlüssel, die der Benutzer lesen, schreiben oder beides darf, einer die Kanäle, die er abonnieren, einer die, in die er veröffentlichen darf, und auf den Knoten, die er überwachen darf, einer mehr.
- **Eine Änderung geschieht in einem Befehl.** `ACL SETUSER user clearselectors (…)` ersetzt alle Selektoren eines Benutzers auf einem Knoten auf einmal, sodass ein Benutzer nie alte und neue zugleich hält und nie keine von beiden; von Hand geänderte Selektoren werden beim nächsten Abgleich ebenso korrigiert.
- **Muster, nur gewährt, wo sie gelten.** Ein Schlüssel- oder Kanalmuster einer Richtlinie wird als eigenes Muster von Redis gewährt, wo die Richtlinien den Benutzer darüber hinweg gleich entscheiden; der Agent zerlegt das Muster entlang jeder überlappenden Richtlinie und entscheidet jeden Teil, sodass die Richtlinie eines anderen Benutzers es nicht zurückhält. Ein Muster, innerhalb dessen Richtlinien anders entscheiden, wird zurückgehalten und einmal protokolliert.
- **Nichts Gefährliches, kein Auflisten fremder Schlüssel.** Die Selektoren geben nie, was Redis als gefährlich einstuft, und `SCAN` und `RANDOMKEY` nur einem Benutzer, der jeden Schlüssel lesen darf.
- **Jeder Knoten, gespeichert, wo es geht.** Der Agent findet die Replikate und die Knoten des Clusters von den angegebenen Knoten aus und gleicht jeden ab. Ein Knoten, der seine ACL in einer Datei führt, speichert sie mit `ACL SAVE`; einer, der das nicht tut, verliert die Selektoren beim Neustart und bekommt sie beim nächsten Abgleich wieder.
- **Redis wird gefragt, was es erlaubt.** Root-Rechte entscheiden neben den Selektoren des Agenten. Statt ihre Regeln zu lesen, fragt der Agent Redis mit `ACL DRYRUN`, ob ein Benutzer einen Befehl auf einem Schlüssel, Kanal oder Knoten ausführen darf, den eine Richtlinie verweigert, und warnt je einmal mit Benutzer, Zugriff und Richtlinie. Er warnt auch vor Benutzern, die kein Selektor bindet, mit jedem Befehl auf jedem Schlüssel, und vor Benutzern, als die sich jeder ohne Passwort anmelden kann.
- **Ablehnungen kommen aus dem ACL-Log jedes Knotens.** Siehe unten unter Ablehnungen melden.
- **Die Richtlinien des gegenwärtigen Moments.** Jeder Abgleich plant nach den Richtlinien, wie sie in diesem Moment gelten, sodass Gültigkeitszeiträume pünktlich beginnen und enden, und nach den Benutzern und Knoten, die Redis hat.

## Bereitstellung

1. Fügen Sie einen `redis`-Datendienst hinzu und konfigurieren Sie seine Richtlinien. Öffnen Sie **Datenberechtigungen → Agenten**, wählen Sie den Dienst und stellen Sie ein Agenten-Token aus. Es wird nur einmal angezeigt; speichern Sie es in einer Datei, die nur das Konto des Agenten lesen kann.
2. Legen Sie den ACL-Benutzer des Agenten auf jedem Knoten an. Er liest Benutzer, setzt ihre Selektoren, speichert die ACL-Datei, liest das ACL-Log, führt Dry Runs aus und findet die Knoten des Clusters. `+acl|setuser` kann sich alles geben, schützen Sie diesen Benutzer also wie einen Administrator.

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. Beschränken Sie die Root-Rechte der Benutzer, die der Agent betreut, auf das, was jeder Benutzer braucht, etwa die Anmeldung; den Rest gibt der Agent. Root-Rechte, die mehr geben, werden gefunden und gemeldet.

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. Schreiben Sie die Einstellungsdatei und ersetzen Sie die Beispieladressen, -pfade und -namen:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   Diese Pfade liegen auf der Maschine des Agenten, und das Cache-Verzeichnis muss beschreibbar sein. Passwortdateien werden als UTF-8 gelesen und getrimmt.
5. Starten Sie den Agenten, zum Beispiel als systemd-Dienst:

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   Ersetzen Sie `<version>` durch die Version der Distribution. Einstellungen, die als `java -Dgrantforge.…=… -jar …` übergeben werden, überschreiben die Datei. Der Agent hält bei SIGTERM sauber an und sendet oder puffert ausstehende Ereignisse; Redis behält, was es bekommen hat.

| Einstellung | Zweck |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Pflicht: der GrantForge-Server, die Datei des Agenten-Tokens, ein Name für diesen Agenten und ein beschreibbares Verzeichnis für den signierten Snapshot, gepufferte Ereignisse und die Leseposition des Audit-Logs |
| `grantforge.signing.key.file` | Optionale Datei, die den Signaturschlüssel der Snapshots festlegt; kopieren Sie den Base64-kodierten öffentlichen X.509-Ed25519-Schlüssel von der Seite Agenten. Ohne sie wird der Schlüssel beim ersten Kontakt vom Server geholt |
| `grantforge.redis.nodes` | Erforderlich: die Knoten als `host:port`, ohne Port `6379` |
| `grantforge.redis.discover` | Standardmäßig `true`: der Rest eines Clusters und die Replikate eines Primary werden von den angegebenen Knoten aus gefunden. `false` nimmt die angegebenen Knoten und keine anderen, wo die Adressen, die Knoten bekannt geben, vom Agenten nicht erreichbar sind |
| `grantforge.redis.user`, `grantforge.redis.password.file` | Der ACL-Benutzer des Agenten, leer für den Standardbenutzer, und sein Passwort |
| `grantforge.redis.tls.mode`, `grantforge.redis.tls.ca.file`, `grantforge.redis.tls.certificate.file`, `grantforge.redis.tls.key.file`, `grantforge.redis.tls.key.password.file` | `disable` (Standard), `verify-ca` oder `verify-full`; die PEM-CA-Zertifikate, die die Server prüfen, leer für das, dem Java vertraut; ein PEM-Client-Zertifikat, sein Schlüssel, wenn in eigener Datei, und das Passwort des Schlüssels |
| `grantforge.redis.timeout.ms` | Wie lange das Verbinden und ein Befehl dauern dürfen, standardmäßig `10000`, im Bereich `1..600000` Millisekunden |
| `grantforge.redis.unmanaged.users` | Benutzer, die der Agent in Ruhe lässt, durch Kommas getrennt, etwa Dienstkonten. Immer in Ruhe lässt er seinen eigenen Benutzer und Benutzer mit jedem Befehl auf jedem Schlüssel |
| `grantforge.sync.interval.ms` | Wie oft der Agent Redis abgleicht, Standard `10000`, Bereich `1000..3600000` Millisekunden |
| `grantforge.audit.log`, `grantforge.audit.log.interval.ms` | Standardmäßig `true`; `false` liest kein ACL-Log. Wie oft die ACL-Logs gelesen werden, standardmäßig `5000` Millisekunden |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Verbindungs- und Lese-Timeouts zu GrantForge, Standard `5000` / `8000` Millisekunden |
| `grantforge.refresh.interval.ms` | Wie oft der Agent nach neuen Richtlinien fragt, Standard `30000`, mindestens `1000` Millisekunden |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Zugriffsereignisse: Batch-Größe (Standard `500`), Warteschlangengröße (Standard `10000`), Sendeintervall (Standard `5000` Millisekunden) und die Puffergrenze auf der Platte (Standard 64 MiB) |
| `grantforge.audit.fold.window.ms` | Wie lange identische erlaubte Zugriffe zu einem Ereignis zusammengefasst werden, Standard `10000`, bis `3600000` Millisekunden; `0` sendet jeden |

## Ablehnungen melden

Redis führt keine Aufzeichnung dessen, was es erlaubt hat, daher meldet der Agent, was es abgelehnt hat, aus dem ACL-Log jedes Knotens: wem was wann und von wo verweigert wurde und ob die Richtlinien wie Redis entscheiden. Redis fasst eine innerhalb einer Minute wiederholte Ablehnung mit dem Eintrag davor zusammen; der Agent meldet einen Eintrag jedes Mal erneut, wenn er aktualisiert wird.

| Protokollierter Grund | Zugriffsereignis |
| --- | --- |
| `key` | `read` oder `write` auf dem Schlüssel, je nachdem, wie Redis den Befehl kennzeichnet |
| `channel` | `subscribe` oder `publish` auf dem Kanal |
| `command` | `monitor` auf dem Knoten für die Befehle der Überwachung; andere Befehle werden ohne ihre Schlüssel protokolliert und ausgelassen |
| `auth` | Ausgelassen: eine abgelehnte Anmeldung nennt keinen Zugriff |

## Benutzer, Gruppen und Rollen

Richtlinien nennen die ACL-Benutzer von Redis beim Namen. Redis hat keine eigenen Rollen oder Gruppen, daher sind Gruppen die, in die GrantForge Benutzer setzt.

## Entscheidungen und Grenzen

- Ohne Snapshot ändert der Agent nichts. Ein Token, das an einen nicht genutzten Dienst oder einen Dienst anderen Typs als `redis` gebunden ist, gewährt nichts: Der Agent leert die Selektoren der Benutzer, die er betreut.
- Solange der Agent nicht läuft, behält Redis, was es zuletzt erhalten hat, und Richtlinienänderungen warten. Eine Änderung erreicht Redis innerhalb der Aktualisierungs- und Abgleichintervalle.
- Der Agent hält jeden Selektor der Benutzer, die er betreut: ein vom Betrieb von Hand hinzugefügter Selektor wird beim nächsten Abgleich entfernt. Geben Sie zusätzliche Rechte in den Root-Rechten, oder überlassen Sie den Benutzer dem Betrieb.
- Die ACL von Redis nennt Schlüssel und Kanäle, keine Datenbanken, daher deckt ein Muster seine Schlüssel in jeder Datenbank eines Knotens ab.
- Ein abgelehnter Befehl, den der Benutzer gar nicht ausführen darf, wird von Redis ohne seine Schlüssel protokolliert und nicht gemeldet.
- Redis filtert nichts innerhalb eines Werts und maskiert nichts.

## Weitere Anleitungen

- [Redis](/de/plugins/redis/)
- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
