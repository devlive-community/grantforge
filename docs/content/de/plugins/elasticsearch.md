---
title: Elasticsearch
description: Das Elasticsearch-Service-Plug-in installieren und konfigurieren, einen oder mehrere Knoten erreichen, sich mit einem Passwort, einem API-Schlüssel oder einem TLS-Clientzertifikat anmelden, Indizes, Data Streams, Felder und den Cluster nachschlagen und mit den eigenen Berechtigungen von Elasticsearch verwalten, wer was lesen, schreiben und verwalten darf, bis hinunter zu Feldern und Dokumenten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das Elasticsearch-Plug-in schlägt Indizes, Data Streams, Felder und den Cluster nach und verwaltet Zugriffsrichtlinien in den eigenen Begriffen von Elasticsearch: Felder lesen, Dokumente indizieren und löschen, Indizes anlegen, löschen und verwalten sowie die eigenen Operationen des Clusters, mit Zeilenfiltern von Indizes. Plug-in-ID und Diensttyp sind beide `elasticsearch`. Das Service-Plug-in läuft im GrantForge-Server; der [Elasticsearch-Agent](/de/external/elasticsearch-agent/), ein Dienst neben dem Cluster, setzt die Richtlinien mit den eigenen Rollen, der Field Level Security und der Document Level Security von Elasticsearch durch.

Die Testcontainers-Integrationstests des Repositorys decken Elasticsearch `8.19`, `9.3`, `9.4` und `9.5` mit aktivierter Sicherheit ab; das zertifiziert nicht jede andere Version.

## Installation

Das Distributionspaket enthält das Plug-in in `plugins/elasticsearch`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `elasticsearch` aktiviert ist; scannen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht über den Low-Level-REST-Client von Elasticsearch mit den Knoten, in einem eigenen Classloader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie Elasticsearch (`elasticsearch`).
2. Geben Sie einen Knoten als URL ein, oder mehrere Knoten.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie seine Zugangsdaten ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Cluster: Elasticsearch-Version und Name, die Lizenz und ob sie Document und Field Level Security hat, den angemeldeten Benutzer und wie viele Indizes und Data Streams er hat. Ein Cluster ohne Sicherheit besteht den Test nicht, da der Agent Richtlinien mit den Rollen von Elasticsearch durchsetzt.

Der Lookup-Benutzer lässt GrantForge Ressourcen finden; er ist von den Benutzern getrennt, die lesen und schreiben. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits Richtlinien unterliegt.

| Einstellung | Zweck |
| --- | --- |
| `elasticsearch.hosts` | Pflicht: ein Knoten als URL, etwa `https://es1.example.com:9200`; mehrere durch Kommas getrennt, nacheinander versucht, wenn einer nicht erreichbar ist. Ohne Schema ist ein Knoten `https`, der Port ist standardmäßig `9200`; die Knoten eines Dienstes sind alle `http` oder alle `https` |
| `authentication` | `password` (Standard), `api_key` oder `certificate`: ein TLS-Clientzertifikat, das ein PKI-Realm einem Benutzer zuordnet |
| `username`, `password` | password: der Lookup-Benutzer und sein Passwort, verschlüsselt gespeichert |
| `apiKey` | api_key: der kodierte API-Schlüssel, wie Elasticsearch ihn beim Anlegen zurückgibt, verschlüsselt gespeichert |
| `ssl.verificationMode` | Wie Kibana es nennt: `full` (Standard) prüft Zertifikate und Namen der Knoten, `certificate` nur die Zertifikate, `none` nichts |
| `ssl.certificateAuthorities` | Pfad auf dem **GrantForge-Server** zu den PEM-CA-Zertifikaten, gegen die die Knoten geprüft werden, etwa `http_ca.crt` von Elasticsearch; leer nutzt die der JVM |
| `ssl.certificate`, `ssl.key` | certificate: Pfade auf dem GrantForge-Server zum PEM-Clientzertifikat und seinem privaten Schlüssel, PKCS #8 oder das ältere OpenSSL-Format; sie brauchen `https`-Knoten |
| `ssl.keyPassphrase` | certificate: die Passphrase eines verschlüsselten privaten Schlüssels, verschlüsselt gespeichert |
| `timeout` | Wie lange Verbindungsaufbau und eine Anfrage dauern dürfen, Standard `30000`, Bereich `1..600000` Millisekunden |

Einstellungen einer Anmeldeart werden für die anderen abgelehnt, sodass der Dienst genau sagt, wie er sich anmeldet. Abgelehnte Anmeldungen, widerrufene API-Schlüssel, fehlende Berechtigungen, nicht erreichbare Knoten und nicht prüfbare Zertifikate werden als solche gemeldet.

### Was der Lookup-Benutzer sieht

Der Lookup-Benutzer muss lesen können, was er auflistet: die Resolve-API von Elasticsearch für Indizes und Data Streams, Field Capabilities für Felder und die Beschreibung des Clusters für seinen Namen. Eine Rolle mit `read` und `view_index_metadata` auf den Indizes gibt die ersten beiden; ein Benutzer ohne die Cluster-Berechtigung `monitor` besteht den Verbindungstest trotzdem, der dann weder Version noch Lizenz nennt. Indizes und Data Streams, deren Namen mit einem Punkt beginnen, also versteckte und Systemindizes, werden nur aufgelistet, wenn die Eingabe mit einem Punkt beginnt; die Backing-Indizes von Data Streams erscheinen als ihr Data Stream. Felder sind die Felder der Dokumente: die eigenen Metadatenfelder von Elasticsearch, Objekte und Multi-Felder, die ein Feld erneut indizieren, wie `name.keyword`, werden ausgelassen, da der Agent sie mit ihrem Feld gewährt.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `index` | Platzhalter, Groß-/Kleinschreibung beachtet; der Name eines Index oder Data Streams | `index`, `delete`, `create_index`, `delete_index`, `view_index_metadata`, `monitor`, `manage` |
| `field` | Platzhalter, Groß-/Kleinschreibung beachtet; unterhalb eines Index der Pfad eines Feldes wie `address.city` | `read` |
| `cluster` | Platzhalter, Groß-/Kleinschreibung beachtet; der Name des Clusters | `cluster_monitor`, `create_snapshot`, `manage_slm`, `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` |

Jede Zugriffsart ist die gleichnamige Berechtigung von Elasticsearch, außer `cluster_monitor`, das die Cluster-Berechtigung `monitor` ist:

| Zugriffsart | Was sie erlaubt |
| --- | --- |
| `read` | Suchen, Abrufen, Zählen, Multi-Search und Scrolling |
| `index`, `delete` | Dokumente indizieren und aktualisieren, auch per Bulk; Dokumente löschen |
| `create_index`, `delete_index` | Indizes anlegen, auch wenn der erste Schreibvorgang einen anlegt; Indizes und Data Streams löschen |
| `view_index_metadata`, `monitor` | Mappings, Einstellungen und Aliase lesen; Statistiken und Recovery lesen |
| `manage` | Jede Verwaltungsaktion eines Index; schließt `create_index`, `delete_index`, `view_index_metadata` und `monitor` ein |
| `cluster_monitor` | Zustand, Status und Statistiken des Clusters lesen |
| `create_snapshot`, `manage_slm` | Snapshots erstellen; Snapshot-Lifecycle-Richtlinien verwalten, was `create_snapshot` einschließt |
| `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` | Index-Templates, Ingest-Pipelines und Index-Lifecycle-Richtlinien verwalten |

Lesen wird pro Feld entschieden, so wie Field Level Security die Felder der Dokumente zeigt: Eine `read`-Richtlinie nennt Felder, und `*` sind alle Felder. Eine Richtlinie kann so einen Index erlauben und ein Feld darin verweigern, und der Benutzer liest alle übrigen Felder. In Richtlinienwerten passt `*` auf beliebig viele Zeichen und `?` auf ein Zeichen. Der Richtlinieneditor schlägt Indizes und Data Streams, die Felder der von der Richtlinie genannten Indizes und den Cluster vor.

### Zeilenfilter

Eine Zeilenfilter-Richtlinie eines Index zeigt dem Benutzer nur die Dokumente, die eine Abfrage trifft. Die Abfrage ist die Query DSL von Elasticsearch, wie Document Level Security sie nimmt, etwa `{"term": {"region": "EU"}}`, und darf ein Template sein, etwa mit `{{_user.username}}`. Wie bei anderen Diensten erhält ein Benutzer den Filter der ersten Richtlinie, die auf ihn zutrifft. Ein Zeilenfilter kann nur Felder prüfen, die der Benutzer lesen darf: Field Level Security verbirgt die übrigen auch vor der Abfrage.

Elasticsearch maskiert keine Werte, daher bietet der Diensttyp keine Maskierung.

### Die Lizenz

Elasticsearch hat Document und Field Level Security ab einer Platinum-Lizenz. Darunter lässt sich eine Richtlinie, die einen Benutzer nur einige Felder oder einige Dokumente eines Index lesen lässt, nicht durchsetzen; der Agent gewährt diesem Benutzer daher kein Lesen des Index, statt mehr als die Richtlinien erlauben, und sagt es. Richtlinien, die ganze Indizes, Schreiben und Verwalten erlauben, funktionieren mit jeder Lizenz.

## Richtlinienbeispiel

Angenommen, der Cluster hat eine Platinum-Lizenz und die Indizes `logs-2026.10.26` und `hr`. Legen Sie im selben Datendienst diese aktivierten Richtlinien an:

| Ressource | Regel |
| --- | --- |
| `index=logs-*`, `field=*` | Gruppe `analysts` `read` erlauben |
| `index=logs-*`, `field=client_ip` | Gruppe `analysts` `read` verweigern |
| `index=logs-*` | Benutzer `logstash` `index` und `create_index` erlauben |
| `index=hr`, `field=*` | Gruppe `hr` `read` erlauben |
| `index=hr` (Zeilenfilter) | Benutzer `emea-hr` nur Dokumente von `{"term": {"region": "EMEA"}}` zeigen |
| `cluster=*` | Gruppe `ops` `cluster_monitor` erlauben |

Sobald der Agent den neuen Snapshot anwendet, lesen Mitglieder von `analysts` jedes Log-Feld außer der Adresse des Clients; `logstash` schreibt das morgige `logs-2026.10.27` sofort und legt es mit dem ersten Dokument an; Mitglieder von `hr` lesen die Mitarbeitenden; `emea-hr` sieht nur die Mitarbeitenden der Region; und Mitglieder von `ops` lesen Zustand und Statistiken des Clusters.

## Richtlinien durchsetzen

Das Service-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach; sie durchzusetzen braucht einen Elasticsearch-Agenten pro Cluster. Bereitstellung und Prüfungen stehen in [Elasticsearch-Agent](/de/external/elasticsearch-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [Elasticsearch-Agent](/de/external/elasticsearch-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
