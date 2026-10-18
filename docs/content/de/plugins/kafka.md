---
title: Apache Kafka
description: Das Kafka-Dienst-Plug-in installieren und konfigurieren, mit PLAIN, SCRAM oder Kerberos über Klartext oder TLS anmelden, Topics, Gruppen, Transaktions-IDs und Benutzer nachschlagen und Zugriffsrichtlinien in Kafkas eigenen Begriffen verwalten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Kafka-Plug-in schlägt Topics, Consumer-Gruppen, Transaktions-IDs, SCRAM-Benutzer und Delegation-Tokens nach und verwaltet Zugriffsrichtlinien in Kafkas eigenen Begriffen: Seine Ressourcentypen sind die Ressourcenebenen und seine ACL-Operationen die Zugriffsarten. Plug-in-ID und Diensttyp lauten beide `kafka`. Das Dienst-Plug-in läuft im GrantForge-Server; der [Kafka-Agent](/de/external/kafka-agent/), installiert in den Brokern und Controllern, setzt die Richtlinien durch.

Die Testcontainers-Integrationstests des Repositorys decken Kafka `3.7.2`, `3.9.2`, `4.1.2` und `4.3.1` mit Anmeldungen über SASL PLAIN und SCRAM und Kafkas eigenem Autorisierer ab, dazu Kerberos (Keytab und Passwort) und TLS gegen `4.3.1`; das zertifiziert nicht jede andere Version.

## Installation

Die Distribution enthält das Plug-in in `plugins/kafka`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `kafka` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit den Brokern über Kafkas eigenen Admin-Client (`kafka-clients` 4.3.1) in einem eigenen Class Loader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Apache Kafka (`kafka`).
2. Geben Sie die Bootstrap-Server an und wie der Listener der Broker gesichert ist.
3. Geben Sie den Lookup-Benutzer ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Cluster: seine ID und wie viele Broker er hat.

Die Einstellungen tragen Kafkas eigene Client-Namen und lassen sich aus einer Client-Konfiguration übernehmen. Der Lookup-Benutzer lässt GrantForge Ressourcen entdecken; er ist von den Clients getrennt, die produzieren und konsumieren. Ein erfolgreicher Verbindungstest bedeutet nicht, dass Anfragen bereits Richtlinien unterliegen.

| Einstellung | Zweck |
| --- | --- |
| `bootstrap.servers` | Erforderliche Start-Broker, `host:port` durch Kommas getrennt, etwa `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT` (Standard), `SSL`, `SASL_PLAINTEXT` oder `SASL_SSL` |
| `sasl.mechanism` | Nur SASL-Protokolle: `PLAIN`, `SCRAM-SHA-256` oder `SCRAM-SHA-512` mit Passwort, `GSSAPI` für Kerberos |
| `username` | SASL: der Lookup-Benutzer; mit Kerberos der vollständige Principal, etwa `grantforge@EXAMPLE.COM` |
| `password` | Das Passwort des Benutzers oder, ohne Keytab, das Kerberos-Passwort des Principals; verschlüsselt gespeichert |
| `keytab` | Kerberos: Pfad zur Keytab des Principals, lesbar auf dem **GrantForge-Server** |
| `kerberos.kdc` | Kerberos: KDCs des Realms des Principals, `host[:port]` durch Kommas getrennt; leer verwendet die `krb5.conf` des Servers |
| `sasl.kerberos.service.name` | Kerberos: der erste Teil der Principals der Broker, Standard `kafka` |
| `ssl.truststore.location`, `ssl.truststore.password`, `ssl.truststore.type` | TLS: der Truststore, der die Broker prüft (`JKS`, `PKCS12` oder `PEM`); leer vertraut dem, was Java vertraut |
| `ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password` | TLS: GrantForges Zertifikat, für Broker, die von Clients eines verlangen |
| `ssl.verify.hostname` | TLS: ob die Zertifikate der Broker die verbundenen Hosts nennen müssen, Standard `true` |
| `request.timeout.ms` | Anfrage-Timeout, Standard `30000`, Bereich `1..600000` |

Die SASL-Einstellungen werden mit `PLAINTEXT` und `SSL` abgelehnt, die TLS-Einstellungen mit `PLAINTEXT` und `SASL_PLAINTEXT`. `PLAIN` und SCRAM brauchen ein Passwort; `GSSAPI` braucht ein Passwort oder eine Keytab.

### Kerberos

Mit `GSSAPI` meldet GrantForge den Principal über Kafkas eigene Kerberos-Anmeldung an, mit der Keytab oder mit dem Passwort, das GrantForge der Anmeldung selbst übergibt; sonst braucht der GrantForge-Server keine Kerberos-Einstellungen. Realms, deren KDCs in `kerberos.kdc` eingetragen sind, werden für den ganzen GrantForge-Server deklariert, wie es die HDFS- und Hive-Plug-ins tun, sodass mehrere Dienste verschiedene Realms nebeneinander nutzen können.

### Was der Lookup-Benutzer sieht

Kafka zeigt einem Benutzer nur, was er beschreiben darf. Der Lookup-Benutzer braucht daher `describe` auf den Topics, Gruppen und Transaktions-IDs, die er vorschlagen soll, und `describe` auf dem Cluster für SCRAM-Benutzer. Kafka lässt Topics und Gruppen weg, die er nicht beschreiben darf; die Vorschläge sind dann leer statt abgelehnt. Interne Topics wie `__consumer_offsets` werden vorgeschlagen, sobald die Eingabe mit `_` beginnt. Ein Cluster ohne Delegation-Tokens oder ohne SCRAM schlägt keine vor.

Sobald der [Kafka-Agent](/de/external/kafka-agent/) die Richtlinien durchsetzt, entscheiden sie auch, was der Lookup-Benutzer beschreiben darf: Geben Sie ihm eine Richtlinie, die `describe` erlaubt, oder nennen Sie ihn in `super.users` der Broker.

## Ressourcen und Zugriffsarten

| Ebene | Kafka-Ressource | Zugriffsarten |
| --- | --- | --- |
| `topic` | Topics | `read`, `write`, `create`, `delete`, `alter`, `describe`, `describe_configs`, `alter_configs`, `all` |
| `group` | Consumer-Gruppen | `read`, `delete`, `describe`, `all` |
| `transactional_id` | Transaktions-IDs | `write`, `describe`, `all` |
| `cluster` | Der Cluster, stets `kafka-cluster` genannt | `create`, `alter`, `describe`, `cluster_action`, `describe_configs`, `alter_configs`, `idempotent_write`, `all` |
| `delegation_token` | Delegation-Tokens, nach Token-ID | `describe`, `all` |
| `user` | Benutzer, deren Delegation-Tokens erstellt oder beschrieben werden | `create_tokens`, `describe_tokens`, `all` |

Jede Ebene steht für sich; eine Richtlinie nennt eine. Werte unterscheiden Groß- und Kleinschreibung: `*` steht für beliebig viele Zeichen, `?` für genau eines. Die Zugriffsarten schließen andere ein wie Kafkas ACL-Operationen: `read`, `write`, `delete` und `alter` schließen `describe` ein, `alter_configs` schließt `describe_configs` ein, und `all` schließt alle anderen ein.

Einschlüsse gelten auch für Verweigerungen: Eine Richtlinie, die `write` auf einem Topic verweigert, verweigert auch das von `write` eingeschlossene `describe`, sodass der Benutzer das Topic nicht mehr sieht. Kafkas eigene ACLs wenden Einschlüsse nur auf erlaubte Operationen an.

Der Richtlinieneditor schlägt Namen aus dem Cluster per Präfix vor; für `cluster` schlägt er `kafka-cluster` vor.

## Richtlinienbeispiel

Angenommen, Producer des Benutzers `alice` schreiben in Topics, deren Namen mit `orders` beginnen, und die Gruppe `analysts` liest sie mit der Consumer-Gruppe `billing`. Legen Sie im selben Datendienst drei aktivierte Zugriffsrichtlinien an:

| Ressourcen | Regel |
| --- | --- |
| `topic=orders*` | `write` für Benutzer `alice` erlauben |
| `topic=orders*` | `read` für Gruppe `analysts` erlauben |
| `group=billing` | `read` für Gruppe `analysts` erlauben |

Sobald der Agent den neuen Snapshot anwendet, darf `alice` nach `orders-eu` produzieren, auch mit einem idempotenten Producer und ohne `idempotent_write` auf dem Cluster; Mitglieder von `analysts` konsumieren es als `billing`; andere Topics und Gruppen verweigern beiden den Zugriff. `kafka-acls --list` zeigt die drei Richtlinien als Präfix- und Literal-ACLs.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach. Um sie durchzusetzen, braucht jeder Broker und Controller den Kafka-Agenten. Bereitstellung und Prüfungen beschreibt der [Kafka-Agent](/de/external/kafka-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Kafka-Agent](/de/external/kafka-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
