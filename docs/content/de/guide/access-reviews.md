---
title: Regelmäßige Berechtigungsprüfung
description: Regelmäßig prüfen, wer welche Rollen innehat; die Prüfer behalten oder entziehen sie einzeln, und beim Abschluss der Runde werden die entzogenen Zuweisungen entfernt.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Zugriffskontrolle → Berechtigungsprüfung**: Regelmäßig prüfen, wer welche Rollen innehat; der Prüfer entscheidet einzeln über Behalten oder Entziehen, und beim Abschluss der Runde werden die entzogenen Zuweisungen entfernt.

![Berechtigungsprüfung](/screenshots/access-reviews.png)

## Prüfplan

| Einstellung | Beschreibung |
| --- | --- |
| Name, Beschreibung | Zum Beispiel „Finanzrollen-Quartalsprüfung“ |
| Rollen | Die zu prüfenden Rollen; jede Runde listet alle ihre aktuellen Zuweisungen auf |
| Rundendauer | 1–90 Tage; beim Ablauf wird die Runde automatisch abgeschlossen |
| Wiederholungsintervall | Leer lassen, um nur manuell zu starten; sonst startet die nächste Runde automatisch nach dem Intervall |
| Ungeprüfte Einträge | Einträge ohne Entscheidung beim Rundenende: **behalten** oder **entziehen** |
| Aktiviert | Beeinflusst nur, ob der Plan automatisch startet |

## Eine Prüfrunde

1. Klicke auf **Sofort starten** oder warte, bis der Plan automatisch startet. GrantForge erzeugt für jede Zuweisung einen Prüfeintrag (Systemrollen von Systemkonten ausgenommen).
2. Der Prüfer wählt in dieser Runde einzeln oder stapelweise **behalten** oder **entziehen**; beim Entziehen kann eine Begründung angegeben werden. Entscheidungen können vor dem Rundenende zurückgenommen werden.
3. Rollen, die du selbst über eine direkte Zuweisung, eine Gruppe, eine Abteilung oder eine Stelle erhalten hast, kannst du nicht prüfen.
4. Administratoren können die Runde **abschließen** (alle Entscheidungen anwenden, unentschiedene Einträge werden laut Planeinstellung behandelt) oder die Runde **abbrechen** (ohne jede Änderung). Runden, die beim Ablauf nicht abgeschlossen sind, werden automatisch abgeschlossen.

Entzogene Zuweisungen werden beim Abschluss der Runde gelöscht. Jeder Schritt wird im Audit-Protokoll festgehalten und eignet sich so als Nachweis für die interne Kontrollprüfung.
