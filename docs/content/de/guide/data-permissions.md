---
title: Datenberechtigungen
description: "Entscheide, welche Zeilen jeder Datenart eine Rolle lesen, ändern, löschen und exportieren darf: nur eigene, die eigene Abteilung, bestimmte Abteilungen oder Zeilen nach Bedingung."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Datenberechtigungen entscheiden, **welche Zeilen** jeder Datenart die Inhaber einer Rolle lesen, ändern, löschen und exportieren dürfen. Klicke in der Rollenzeile auf **Datenberechtigungen**, um sie einzustellen.

![Datenberechtigungen](/screenshots/role-data.png)

## Regeln

Jede Regel besteht aus vier Teilen:

| Teil | Optionen |
| --- | --- |
| Datenentität | Benutzer, Abteilungen, Gruppen, Stellen, Audit-Ereignisse sowie von Geschäftsanwendungen deklarierte Entitäten (zum Beispiel `shop:order`) |
| Aktion | Ansehen, Ändern, Löschen, Exportieren |
| Bereich | Alle Mandanten (nur für Rollen des Plattform-Mandanten), alles in diesem Mandanten, diese Abteilung und darunter, diese Abteilung, bestimmte Abteilungen, nur eigene, nach Bedingung |
| Wirkung | Erlauben oder Verweigern |

Regeln werden so zusammengeführt:

- **Ohne eine einzige erlaubende Regel sind keine Daten sichtbar.**
- **Verweigern hat Vorrang vor Erlauben**: Jede von einer Verweigerungsregel getroffene Zeile ist nicht verfügbar.
- Die Regeln aller Rollen einer Person wirken zusammen: Erlaubnisse werden vereinigt, Verweigerungen ebenfalls.
- Systemrollen implizieren den entsprechenden Bereich (Mandanten-Administrator erhält alles in diesem Mandanten), außer bei Entitäten von Geschäftsanwendungen.

## Nach Bedingung

Wenn der Bereich „nach Bedingung“ gewählt ist, werden Bedingungen im Bedingungs-Editor kombiniert:

- Vergleiche Felder der Entität, zum Beispiel „Status gleich aktiv“ oder „letzte Anmeldung vor der aktuellen Zeit“. Text unterstützt enthält und beginnt mit; Zahlen und Zeitpunkte unterstützen Größer- und Kleiner-Vergleiche; außerdem werden in, nicht in, ist leer und ist nicht leer unterstützt.
- Ein Wert kann ein fester Wert oder ein **Attribut des aktuellen Benutzers** sein: eigene ID, Benutzername, Abteilung, Gruppen, bekleidete Stellen sowie die aktuelle Zeit.
- Bedingungen können mit „alle erfüllt / mindestens eine erfüllt“ gruppiert, negiert und bis zu 4 Ebenen tief verschachtelt werden.

## Vorschau

Wähle im Editor einen Benutzer aus und klicke auf **Vorschau**, um zu sehen, wie viele Zeilen dieser Benutzer mit den Regeln der Rolle sehen kann und um welche es sich genau handelt.

## Wo sie wirken

Die Listen und Details zu Benutzern, Abteilungen, Gruppen und Stellen in der Konsole, Import und Export sowie das Audit-Protokoll beachten Datenberechtigungen; nicht sichtbare Zeilen erscheinen nicht in Listen, und der direkte Zugriff per ID gibt „nicht vorhanden“ zurück. Geschäftsanwendungen erhalten dieselben Regeln über das [Java SDK](/de/integration/java/) oder die [offene API](/de/integration/open-api/).
