---
title: Rundgang durch die Konsole
description: Aufbau der Konsole, Menügruppen und warum die Menüs von Person zu Person unterschiedlich sind.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

![Überblick über den Arbeitsbereich](/screenshots/dashboard.png)

## Aufbau

- **Das linke Menü** ist in Gruppen geordnet: Arbeitsbereich, Zugriffskontrolle, Datenberechtigungen und Plattformverwaltung.
- **Die Kopfzeile** bietet Schnellnavigation (⌘K oder Ctrl+K, Suche nach Seiten nach Namen), den Sprachumschalter, das helle/dunkle Design und das persönliche Menü.
- **Der Überblick** zeigt die Anzahl der Konten, Abteilungen, Gruppen und Stellen des aktuellen Mandanten sowie die Mitglieder des Arbeitsbereichs und die ersten Schritte.

## Menügruppen

| Gruppe | Menüs | Beschreibung |
| --- | --- | --- |
| Arbeitsbereich | Überblick, Meine Anträge | Für jeden angemeldeten Benutzer sichtbar |
| Zugriffskontrolle | Benutzerverwaltung, Organisationsstruktur, Gruppen, Stellen, Import/Export, Rollenverwaltung, Funktionstrennung, Berechtigungsfreigabe, Berechtigungsprüfung, Online-Sitzungen, Identitätsquellen, Audit-Protokolle | Identität und Berechtigungen dieses Mandanten |
| Datenberechtigungen | Datendienste, Richtlinien, Agenten, Zugriffs-Audits | Berechtigungen für externe Datensysteme (HDFS, Hive usw.), siehe [Datendienste, Richtlinien und Agenten](/de/external/data-services/) |
| Plattformverwaltung | Mandantenverwaltung, Ressourcenkatalog, API-Katalog, Autorisierungsserver, Katalogprüfung, Plug-ins | Nur im Plattform-Mandanten verfügbar |

## Warum sich mein Menü von dem anderer unterscheidet

Die Konsole selbst ist eine Anwendung, die von GrantForge verwaltet wird: Jede Seite und jede Schaltfläche ist eine Ressource im Ressourcenkatalog, und welche Menüs und Schaltflächen du siehst, bestimmen allein deine Rollen.

- Wer die Systemrolle **Mandantenadministrator** innehat, sieht alle Menüs des Bereichs „Zugriffskontrolle“ und „Datenberechtigungen“ dieses Mandanten.
- Wer die Systemrolle **Plattformadministrator** innehat, sieht zusätzlich die „Plattformverwaltung“.
- Alle anderen sehen nur die Seiten, die ihre Rollen freigegeben haben; wenn in einer Gruppe keine einzige Seite enthalten ist, wird die gesamte Gruppe ausgeblendet.

> [!NOTE]
> Ausgeblendete Menüs sind nur eine Bequemlichkeit. Der Server prüft bei jedem API-Aufruf die Berechtigungen erneut: Selbst wenn du eine Adresse direkt eingibst, wird eine Aktion ohne Berechtigung abgewiesen.

Nach einer Berechtigungsänderung ist keine erneute Anmeldung nötig: Jede Antwort enthält die Versionsnummer der aktuellen Berechtigungen, und wenn sich diese Version ändert, lädt die Konsole das Menü automatisch neu.
