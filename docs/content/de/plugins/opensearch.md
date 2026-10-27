---
title: OpenSearch
description: Das OpenSearch-Dienst-Plug-in installieren und konfigurieren, einen oder mehrere Knoten erreichen, sich mit Passwort oder TLS-Client-Zertifikat anmelden, Indizes, Datenströme, Felder und den Cluster nachschlagen und verwalten, wer was lesen, schreiben und verwalten darf, bis hin zu Feldern, maskierten Werten und Dokumenten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Das OpenSearch-Plug-in schlägt Indizes, Datenströme, Felder und den Cluster nach und verwaltet Zugriffsrichtlinien in den Begriffen von OpenSearch: Felder lesen, Indizes beschreiben, löschen, anlegen und verwalten sowie die Operationen des Clusters selbst, mit Masken für Felder und Zeilenfiltern für Indizes. Plug-in-ID und Diensttyp sind beide `opensearch`. Das Dienst-Plug-in läuft im GrantForge-Server; der [OpenSearch-Agent](/de/external/opensearch-agent/), ein Dienst neben dem Cluster, setzt die Richtlinien mit den eigenen Rollen, der Sicherheit auf Feldebene, den maskierten Feldern und der Sicherheit auf Dokumentebene des Security-Plug-ins durch.

Die Testcontainers-Integrationstests des Repositorys decken OpenSearch `2.19`, `3.3`, `3.6` und `3.9` mit dem Security-Plug-in ab; damit ist nicht jede andere Version zertifiziert.

## Installation

Die Distribution enthält das Plug-in in `plugins/opensearch`. Prüfen Sie unter **Plattformverwaltung → Plug-ins**, dass `opensearch` aktiviert ist; durchsuchen Sie das Plug-in-Verzeichnis nach einer Aktualisierung erneut. Das Plug-in spricht mit den Knoten über den eigenen Low-Level-REST-Client von OpenSearch, in einem eigenen Klassenlader.

## Datendienst hinzufügen

1. Öffnen Sie **Datenberechtigungen → Datendienste**, fügen Sie einen Dienst hinzu und wählen Sie OpenSearch (`opensearch`).
2. Geben Sie einen Knoten als URL ein, oder mehrere Knoten.
3. Wählen Sie, wie sich der Lookup-Benutzer anmeldet, geben Sie seine Anmeldedaten ein, testen Sie die Verbindung und speichern Sie den Dienst. Der Test meldet sich an und beschreibt den Cluster: seine OpenSearch-Version und seinen Namen, den angemeldeten Benutzer und wie viele Indizes und Datenströme er hat. Ein Cluster ohne Security-Plug-in besteht den Test nicht, da der Agent die Richtlinien damit durchsetzt.

Der Lookup-Benutzer lässt GrantForge Ressourcen finden; er ist von den Benutzern getrennt, die lesen und schreiben. Ein erfolgreicher Verbindungstest bedeutet nicht, dass der Zugriff bereits Richtlinien unterliegt.

| Einstellung | Zweck |
| --- | --- |
| `opensearch.hosts` | Pflicht: ein Knoten als URL, etwa `https://opensearch1.example.com:9200`; mehrere durch Kommas getrennt, nacheinander versucht, wenn einer nicht erreichbar ist. Ohne Schema ist ein Knoten `https`, der Port ist standardmäßig `9200`; die Knoten eines Dienstes sind alle `http` oder alle `https` |
| `authentication` | `password` (Standard) oder `certificate`: ein TLS-Client-Zertifikat, das das Security-Plug-in einem Benutzer zuordnet |
| `username`, `password` | password: der Lookup-Benutzer und sein Passwort, verschlüsselt gespeichert |
| `ssl.verificationMode` | Wie OpenSearch Dashboards es nennt: `full` (Standard) prüft Zertifikate und Namen der Knoten, `certificate` nur ihre Zertifikate, `none` nichts |
| `ssl.certificateAuthorities` | Pfad auf dem **GrantForge-Server** zu den PEM-CA-Zertifikaten, gegen die die Knoten geprüft werden; leer verwendet die der JVM |
| `ssl.certificate`, `ssl.key` | certificate: Pfade auf dem GrantForge-Server zum PEM-Client-Zertifikat und seinem privaten Schlüssel, PKCS #8 oder die ältere OpenSSL-Form; sie brauchen `https`-Knoten |
| `ssl.keyPassphrase` | certificate: die Passphrase eines verschlüsselten privaten Schlüssels, verschlüsselt gespeichert |
| `timeout` | Wie lange Verbinden und eine Anfrage dauern dürfen, Standard `30000`, Bereich `1..600000` Millisekunden |

Einstellungen einer Anmeldeart werden für die andere abgelehnt, sodass der Dienst genau angibt, wie er sich anmeldet. Abgelehnte Anmeldungen, fehlende Berechtigungen, nicht erreichbare Knoten und nicht prüfbare Zertifikate werden als solche gemeldet.

### Was der Lookup-Benutzer sieht

Der Lookup-Benutzer muss lesen dürfen, was er auflistet: die Resolve-API von OpenSearch für Indizes und Datenströme, die Feldfähigkeiten (field capabilities) für Felder und die Beschreibung des Clusters für dessen Namen. Die eingebaute Rolle `readall` gibt die ersten beiden; ein Benutzer ohne `cluster:monitor/main` besteht den Verbindungstest trotzdem, der dann die Version nicht nennt. Indizes und Datenströme, deren Namen mit einem Punkt beginnen, also versteckte und Systemindizes, werden nur aufgelistet, wenn das Eingegebene mit einem Punkt beginnt; die Backing-Indizes von Datenströmen werden über ihren Datenstrom aufgelistet. Felder sind die Felder der Dokumente: die eigenen Metadatenfelder von OpenSearch, Objekte und Multi-Felder, die ein Feld noch einmal indizieren, etwa `name.keyword`, werden ausgelassen, da die Sicherheit auf Feldebene sie mit ihrem Feld zeigt oder verbirgt.

## Ressourcen und Zugriffsarten

| Ebene | Abgleich | Zugriffsarten |
| --- | --- | --- |
| `index` | Platzhalter, Groß-/Kleinschreibung beachtet; der Name eines Index oder Datenstroms | `index`, `delete`, `create_index`, `manage_aliases`, `monitor`, `manage` |
| `field` | Platzhalter, Groß-/Kleinschreibung beachtet; unter einem Index, der Pfad eines Feldes wie `address.city` | `read` |
| `cluster` | Platzhalter, Groß-/Kleinschreibung beachtet; der Name des Clusters | `cluster_monitor`, `manage_snapshots`, `manage_templates`, `manage_pipelines` |

| Zugriffsart | Aktionsgruppe des Security-Plug-ins |
| --- | --- |
| `read` | `read`: Suchen, Abrufen, Zählen und Ähnliches |
| `index` | `index`: Dokumente indizieren und aktualisieren, auch per Bulk |
| `delete` | `delete`: Dokumente löschen |
| `create_index` | `create_index`: Indizes anlegen, auch wenn der erste Schreibvorgang einen anlegt |
| `manage_aliases`, `monitor` | `manage_aliases`, `indices_monitor` |
| `manage` | `manage`: jede Verwaltungsaktion eines Index, auch sein Löschen; sie schließt `create_index`, `manage_aliases` und `monitor` ein |
| `cluster_monitor`, `manage_snapshots` | `cluster_monitor`, `manage_snapshots` |
| `manage_templates`, `manage_pipelines` | `cluster_manage_index_templates`, `cluster_manage_pipelines` |

Lesen wird je Feld entschieden, so wie die Sicherheit auf Feldebene die Felder der Dokumente zeigt: Eine `read`-Richtlinie nennt Felder, und `*` steht für jedes Feld. Eine Richtlinie kann so einen Index erlauben und eines seiner Felder verweigern, und der Benutzer liest jedes andere Feld. In Richtlinienwerten steht `*` für eine beliebige Zeichenfolge und `?` für ein Zeichen. Der Richtlinieneditor schlägt Indizes und Datenströme, die Felder der Indizes, die die Richtlinie nennt, und den Cluster vor.

Was eine Richtlinie erlaubt, braucht in OpenSearch mehr: Multi-Search, Multi-Get und Scrollen zum Lesen und Bulk zum Schreiben. Das sind keine eigenen Zugriffsarten; der Agent gewährt die zusammengesetzten Operationen des Clusters damit.

### Masken

Eine Maskierungsrichtlinie eines Feldes zeigt dem Benutzer dessen Werte maskiert, wie die maskierten Felder des Security-Plug-ins, in Dokumenten wie in Aggregationen:

| Maske | Was der Benutzer sieht |
| --- | --- |
| Schwärzen (Redact) | Großbuchstaben als `X`, Kleinbuchstaben als `x` und Ziffern als `n` |
| Teilmaske: letzte 4 / erste 4 zeigen | Jedes andere Zeichen als `x` |
| Hash (SHA-256) | Der SHA-256-Hash des Wertes |
| Hash mit dem Salt des Clusters (BLAKE2b) | Der eigene Hash des Security-Plug-ins, mit dem Salt des Clusters verschlüsselt, sodass gleiche Werte gleich bleiben, aber nicht nachgeschlagen werden können |
| Benutzerdefiniert | Ein Hash-Algorithmus wie `SHA-512` oder reguläre Ausdrücke mit ihren Ersetzungen wie `/[0-9]/::*` |
| Unmaskiert | Der Wert, wie er ist |

### Zeilenfilter

Eine Zeilenfilterrichtlinie eines Index zeigt dem Benutzer nur die Dokumente, die eine Abfrage trifft. Die Abfrage ist die Query-DSL von OpenSearch, wie sie die Sicherheit auf Dokumentebene annimmt, etwa `{"term": {"region": "EU"}}`, und darf Ersetzungen des Security-Plug-ins wie `${user.name}` verwenden. Wie bei anderen Diensten erhält ein Benutzer den Filter der ersten Richtlinie, die für ihn gilt. Ein Zeilenfilter kann nur Felder prüfen, die der Benutzer lesen darf: Die Sicherheit auf Feldebene verbirgt die anderen auch vor der Abfrage.

## Richtlinienbeispiel

Angenommen, der Cluster hat die Indizes `logs-2026.10.26` und `hr`. Legen Sie im selben Datendienst diese aktivierten Richtlinien an:

| Ressource | Regel |
| --- | --- |
| `index=logs-*`, `field=*` | Gruppe `analysts` darf `read` |
| `index=logs-*`, `field=client_ip` | Gruppe `analysts` wird `read` verweigert |
| `index=logs-*` | Benutzer `logstash` darf `index` und `create_index` |
| `index=hr`, `field=*` | Gruppe `hr` darf `read` |
| `index=hr`, `field=salary` (Maskierung) | Gruppe `hr` sieht den Hash mit dem Salt des Clusters |
| `index=hr` (Zeilenfilter) | Benutzer `emea-hr` sieht nur Dokumente von `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Gruppe `ops` darf `cluster_monitor` |

Sobald der Agent den neuen Snapshot anwendet, lesen die Mitglieder von `analysts` jedes Protokollfeld außer der Adresse des Clients; `logstash` schreibt das morgige `logs-2026.10.27` sofort und legt es mit dem ersten Dokument an; Mitglieder von `hr` sehen Gehälter als Hash; `emea-hr` sieht nur die Beschäftigten der Region; und Mitglieder von `ops` lesen den Zustand und die Statistiken des Clusters.

## Richtlinien durchsetzen

Das Dienst-Plug-in verwaltet Richtlinien und schlägt Ressourcen nach; um sie durchzusetzen, braucht es einen OpenSearch-Agenten pro Cluster. Bereitstellung und Prüfungen stehen unter [OpenSearch-Agent](/de/external/opensearch-agent/).

## Weitere Anleitungen

- [Datendienste, Richtlinien und Agenten](/de/external/data-services/)
- [OpenSearch-Agent](/de/external/opensearch-agent/)
- [Plug-ins und Diensttypen](/de/develop/plugins/)
