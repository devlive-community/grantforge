---
title: Benutzer
description: Konten anlegen, suchen, bearbeiten, deaktivieren, sperren und löschen, Passwörter und Zwei-Faktor-Authentifizierung zurücksetzen sowie effektive Berechtigungen ansehen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Zugriffskontrolle → Benutzerverwaltung** verwaltet die Konten der Organisation: zugehörige Abteilung, Anmeldefähigkeit und das Zurücksetzen von Passwörtern.

![Benutzerverwaltung](/screenshots/users.png)

## Suche

Suche nach Benutzername, Anzeigename oder E-Mail-Adresse und filtere nach Status (normal, deaktiviert, gesperrt, Passwortänderung ausstehend) und Abteilung, wahlweise einschließlich der untergeordneten Abteilungen. Die Liste zeigt nur die Konten, die deine Datenberechtigungen sehen lassen; Felder wie die E-Mail-Adresse können nach deinen Feldberechtigungen ausgeblendet oder maskiert sein.

## Anlegen und Bearbeiten

„Benutzer anlegen“ erfordert einen Benutzernamen (3–64 Buchstaben, Ziffern oder `._@-`, plattformweit eindeutig), ein initiales Passwort, einen Anzeigenamen, eine E-Mail-Adresse, eine primäre Abteilung, weitere Abteilungen und Stellen. Neue Konten müssen ihr Passwort bei der ersten Anmeldung ändern.

## Kontoaktionen

| Aktion | Wirkung |
| --- | --- |
| Deaktivieren / Aktivieren | Nach dem Deaktivieren ist keine Anmeldung möglich, bestehende Sitzungen enden sofort |
| Sperren / Entsperren | Eine von einem Administrator gesetzte Sperre wird nicht automatisch aufgehoben; bei der Anmeldung erscheint der Hinweis, einen Administrator zu kontaktieren. Bei Verdacht auf Missbrauch verwenden |
| Passwort zurücksetzen | Setzt ein neues initiales Passwort, das der Benutzer bei der nächsten Anmeldung ändern muss; bestehende Sitzungen enden |
| Zwei-Faktor-Authentifizierung zurücksetzen | Bei verlorenem Authenticator: schaltet die Zwei-Faktor-Authentifizierung des Kontos ab und beendet seine Sitzungen |
| Rollen ansehen | Die Rollen, die das Konto innehat, und die Quelle jeder Rolle (direkte Zuweisung, Gruppe, Abteilung, Stelle) |
| Effektive Berechtigungen ansehen | Siehe [Berechtigungen erklären, simulieren und prüfen](/de/guide/explain/) |
| Löschen | Löscht das Konto und seine Abteilungszuordnungen; Audit-Einträge bleiben erhalten |

Systemkonten (der bei der Initialisierung erstellte Administrator) und dein eigenes Konto können nicht deaktiviert, gesperrt oder gelöscht werden.

## Selbstregistrierung

Standardmäßig deaktiviert. Nach dem Setzen von `grantforge.security.registration-enabled=true` erscheint auf der Anmeldeseite der Eintrag „Konto erstellen“. Registrierte Konten gelangen in den von `grantforge.security.registration-tenant` angegebenen Mandanten und sind normale Konten ohne Rollen.
