---
title: Feldberechtigungen
description: Gesteuerte Felder je Rolle ausblenden, maskieren oder schreibgeschützt machen, zum Beispiel die E-Mail-Adresse und der letzte Anmeldezeitpunkt eines Benutzers.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Feldberechtigungen bestimmen, wie die Inhaber einer Rolle die einzelnen gesteuerten Felder **sehen und ändern**. Klicke in der Zeile der Rolle auf **Feldberechtigungen**, um sie einzustellen.

![Feldberechtigungen](/screenshots/role-fields.png)

## Darstellung

| Modus | Wirkung |
| --- | --- |
| Sichtbar | Zeigt den ursprünglichen Wert |
| Maskiert | Blendet den Wert je nach Maskierungsart teilweise aus: E-Mail-Adresse (erster Buchstabe und Domain bleiben erhalten), Handynummer (138\*\*\*5678), Ausweisnummer (erste 6 und letzte 4 Stellen bleiben erhalten), erstes und letztes Zeichen bleiben erhalten, alles ausgeblendet |
| Verborgen | Das Feld wird überhaupt nicht zurückgegeben und die Spalte in Listen nicht angezeigt |

## Änderung

| Modus | Wirkung |
| --- | --- |
| Änderbar | Kann ausgefüllt und geändert werden |
| Schreibgeschützt | Im Formular deaktiviert; beim direkten Ändern über die API wird ein Fehler zurückgegeben, der das Feld benennt |

## Zusammenführungsregeln

- Felder ohne Einstellung werden von den anderen Rollen des Inhabers bestimmt; wenn keine Rolle das Feld setzt, ist es sichtbar und änderbar.
- Wenn mehrere Rollen dasselbe Feld setzen, gewinnt die **lockerste** Einstellung (sichtbar > maskiert > verborgen, änderbar > schreibgeschützt).
- Suche und Export beachten die Feldberechtigungen ebenfalls: verborgene Felder können nicht zur Suche verwendet werden, und beim Export werden sie nach den Regeln maskiert oder verborgen.

## Gesteuerte Felder

Gesteuerte Felder werden im Server-Code deklariert (aktuell die E-Mail-Adresse und der letzte Anmeldezeitpunkt eines Benutzers), und **Plattformverwaltung → Ressourcenkatalog** listet, in welchen Schnittstellen sie vorkommen. Geschäftsanwendungen können die Feldsteuerung für ihre eigenen Datenentitäten selbst umsetzen; die Regeln werden ebenfalls über die offene API bezogen.
