---
title: Rollen und Berechtigungen
description: Rollen anlegen, Seiten, Schaltflächen und Schnittstellen freigeben, Vererbung einrichten, Rollen Personen, Gruppen, Abteilungen oder Stellen zuweisen und die Auswirkungen vor der Änderung prüfen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Zugriffskontrolle → Rollenverwaltung**: Eine Rolle ist eine Sammlung von Berechtigungen; sie wirkt, sobald sie einem Benutzer, einer Gruppe, einer Abteilung oder einer Stelle zugewiesen ist.

![Rollenverwaltung](/screenshots/roles.png)

## Systemrollen und benutzerdefinierte Rollen

Jeder Mandant hat die Systemrolle **Mandantenadministrator**, der Plattform-Mandant zusätzlich **Plattformadministrator**. Systemrollen besitzen automatisch alle Ressourcen ihres Moduls und können nicht geändert werden; wenn ähnliche, aber kleinere Berechtigungen nötig sind, **kopiere** die Systemrolle und ändere die Kopie.

Benutzerdefinierte Rollen haben eine Kodierung (Kleinbuchstaben, Ziffern, Punkt, Bindestrich oder Unterstrich) und einen Namen und können deaktiviert werden: Eine deaktivierte Rolle gewährt keine Berechtigungen und gibt auch über Vererbung keine weiter.

## Berechtigungen

Klicke in der Zeile der Rolle auf **Berechtigungen**, um die Berechtigungsmatrix zu öffnen:

![Berechtigungsmatrix](/screenshots/role-grants.png)

- Wechsle je nach Anwendung, die Ressourcen werden als Katalogbaum aufgeklappt, und für jede Ressource kann „Erlauben“ oder „Verweigern“ gewählt werden.
- **Das Erlauben einer Schaltfläche leitet automatisch die Seite ab, auf der sie liegt, und die Schnittstellen, die sie braucht**; du musst sie nicht einzeln anhaken. Abgeleitete Ressourcen sind in der Matrix markiert.
- **Verweigern hat Vorrang** und wirkt auf die untergeordneten Ressourcen: Wenn du eine Seite verweigerst, sind die Schaltflächen darunter auch dann nicht verwendbar, wenn sie in einer anderen Rolle erlaubt sind.
- Du kannst nur Berechtigungen vergeben, die du selbst besitzt, damit es nicht zu einer Rechteausweitung kommt. Wer eine Systemrolle innehat, kann jede Ressource der Geschäftsanwendungen vergeben.

Vor dem Speichern zeigt GrantForge die **Auswirkungen** dieser Änderung an: welche Ressourcen verfügbar bzw. nicht mehr verfügbar werden und wie viele Benutzer diese Rolle innehaben.

## Vererbung

Klicke auf **Vererbung** und wähle die Rollen, die diese Rolle erbt: Sie erhält alles, was die geerbten Rollen erlauben und verweigern, sowie die Rollen, die diese wiederum erben. Vererbung darf keine Zyklen bilden, und du kannst nur Berechtigungen erben, die du selbst besitst. Geeignet für gestapelte Verhältnisse wie „Manager = Mitarbeiter + Freigabe“.

## Zuweisung

Klicke auf **Zuweisung**, um die Rolle zuzuweisen an:

| Objekt | Beschreibung |
| --- | --- |
| Benutzer | Direkt für ein bestimmtes Konto |
| Gruppe | Alle Mitglieder der Gruppe erhalten die Rolle |
| Abteilung | Die Mitglieder der Abteilung erhalten die Rolle, optional „inklusive untergeordneter Abteilungen“ |
| Stelle | Wer die Stelle innehat, erhält die Rolle |

Jede Zuweisung kann ein **Startdatum** und ein **Enddatum** erhalten und läuft danach automatisch ab, passend für befristete Berechtigungen. Über [Antrag und Freigabe](/de/guide/access-requests/) kannst du Benutzer auch zeitlich begrenzte Rollen selbst beantragen lassen.

Zuweisungen und Berechtigungen unterliegen der [Funktionstrennung](/de/guide/sod/): Eine Zuweisung, die jemanden gleichzeitig Inhaber unvereinbarer Rollen machen würde, wird abgewiesen.

## Datenberechtigungen und Feldberechtigungen

In der Zeile der Rolle entscheiden **Datenberechtigungen** und **Feldberechtigungen** darüber, welche Zeilen der Inhaber sehen kann und wie er welche Felder sehen und ändern kann, siehe [Datenberechtigungen](/de/guide/data-permissions/) und [Feldberechtigungen](/de/guide/field-permissions/).

## Kopieren und Löschen

**Kopieren** kopiert die Rolle samt Berechtigungen, Datenberechtigungen und Feldberechtigungen. Das Löschen einer Rolle entfernt zugleich ihre Zuweisungen, Berechtigungen und Richtlinien und kann nicht rückgängig gemacht werden.
