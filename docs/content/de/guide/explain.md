---
title: Berechtigungen erklären, simulieren und prüfen
description: Ansehen, was eine Person darf und warum, Rollenänderungen simulieren, Audit-Protokolle abfragen und exportieren.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Effektive Berechtigungen

Klicke in der **Benutzerverwaltung** in der Zeile des Benutzers auf „Effektive Berechtigungen ansehen“, um alles zu sehen, was der Benutzer aktuell nutzen kann: Rollen, Menüs und Schaltflächen, APIs, Datenbereiche und eingeschränkte Felder.

![Effektive Berechtigungen](/screenshots/user-permissions.png)

Klicke bei einem beliebigen Eintrag auf **Erklärung**, dann beantwortet GrantForge, „warum es funktioniert“: welche Rolle es gewährt, über welche Zuweisungen (direkt, Gruppe, Abteilung, Stelle) und welche Herleitungen aus Ressourcen es zustande kommt. Funktioniert etwas nicht, steht dort, ob es nie gewährt wurde oder von einer Verweigerungsregel blockiert ist.

## Änderungen simulieren

Öffne in den effektiven Berechtigungen **Änderungen simulieren**: Nimm an, du fügst dem Benutzer bestimmte Rollen hinzu oder entfernst sie, und sieh, welche Menüs, Schaltflächen und APIs dazukommen oder wegfallen. Eine Simulation rechnet nur und speichert nichts; damit eignet sie sich gut, um vor einer Berechtigungsanpassung die Auswirkung zu bestätigen.

## Audit-Protokoll

**Zugriffskontrolle → Audit-Protokoll** zeichnet auf, wer wann was getan hat: Anmeldungen, Berechtigungsänderungen, Verwaltungsaktionen und abgewiesene Aufrufe.

![Audit-Protokoll](/screenshots/audit.png)

- Filtere nach Ereignis, Ergebnis, Bearbeiter, Objekt und Zeit; die Ergebnisse lassen sich als CSV exportieren.
- Jedes Ereignis trägt Quell-IP, Browser und Request-ID; die Request-ID passt zu den Protokollen auf dem Server.
- Es werden nur die Ereignisse angezeigt, die deine Datenberechtigungen sehen lassen.
- Audit-Einträge bleiben standardmäßig 365 Tage erhalten (`grantforge.audit.retention`); eine Archivierung in ein Verzeichnis vor dem Löschen lässt sich konfigurieren.

Zu den aufgezeichneten Ereignissen gehören: erfolgreiche und fehlgeschlagene Anmeldungen, Sperrungen, Abmeldungen, Sitzungsenden und Passwortänderungen; Änderungen an Mandanten, Abteilungen, Benutzern, Gruppen und Stellen; Änderungen am Ressourcen- und API-Katalog; Änderungen an Rollen, Berechtigungen, Vererbung und Zuweisungen; Änderungen an Daten- und Feldrichtlinien; jeder Schritt von Funktionstrennung, Berechtigungsanträgen und Prüfungen; Änderungen an Identitätsquellen und OAuth-Clients; sowie abgewiesene API-Aufrufe.
