---
title: Anmeldung, Konten und Zwei-Faktor-Authentifizierung
description: Anmeldung und Sitzungen, persönliche Einstellungen, Passwortänderung, Zwei-Faktor-Authentifizierung und Wiederherstellungscodes sowie die erneute Bestätigung sensibler Vorgänge.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Anmeldung

![Anmeldeseite](/screenshots/login.png)

Benutzernamen unterscheiden nicht zwischen Groß- und Kleinschreibung. Nach 5 (konfigurierbar) aufeinanderfolgenden falschen Passworteingaben wird das Konto für 15 Minuten gesperrt; ein von einem Administrator gesperrtes Konto muss von einem Administrator entsperrt werden. Wenn eine Identitätsquelle aktiviert ist, erscheint auf der Anmeldeseite zusätzlich die Schaltfläche „Mit X anmelden“, siehe [Identitätsquellen](/de/guide/identity-sources/).

Sitzungen werden serverseitig gespeichert, der Browser hält nur ein HttpOnly-Sitzungs-Cookie. Nach 30 Minuten (konfigurierbar) ohne Aktivität läuft die Sitzung ab und du musst dich erneut anmelden.

## Persönliche Einstellungen

Klicke oben rechts auf den Avatar, um die **persönlichen Einstellungen** zu öffnen:

![Persönliche Einstellungen](/screenshots/account.png)

- **Grunddaten**: Anzeigenamen und E-Mail-Adresse ändern. Benutzername und zugehörige Organisation werden vom Administrator gepflegt.
- **Passwort ändern**: erfordert die Eingabe des aktuellen Passworts. Nach der Änderung enden alle deine Sitzungen auf anderen Geräten. Konten, die sich über eine Identitätsquelle anmelden, zeigen hier keine Passwortänderung; das Passwort wird von der Identitätsquelle verwaltet.
- **Zwei-Faktor-Authentifizierung**: siehe unten.
- **Meine Anmeldegeräte**: Liste der aktuell angemeldeten Browser; du kannst unbekannte Sitzungen beenden.
- **Letzte Anmeldeaufzeichnungen**: die letzten 10 Anmeldungen, Abmeldungen und fehlgeschlagenen Versuche, einschließlich der Versuche anderer, sich mit deinem Benutzernamen anzumelden.

Nachdem ein Administrator das Passwort zurückgesetzt hat oder das Passwort abgelaufen ist, öffnet die nächste Anmeldung zuerst „Passwort ändern“; bis zur Änderung sind alle anderen Funktionen nicht verfügbar.

## Zwei-Faktor-Authentifizierung

Nach dem Aktivieren ist bei der Anmeldung neben dem Passwort ein 6-stelliger Code aus einer Authenticator-App (Google Authenticator, Microsoft Authenticator, 1Password usw.) erforderlich.

1. Klicke in den persönlichen Einstellungen unter „Zwei-Faktor-Authentifizierung“ auf **Authenticator einrichten**.
2. Füge das Konto in der Authenticator-App hinzu: gib den auf der Seite angezeigten Schlüssel ein oder öffne auf dem Gerät mit der App den otpauth-Link.
3. Gib den von der App angezeigten Code ein und klicke auf **Aktivieren**.
4. Die Seite zeigt **10 Wiederherstellungscodes** an, und zwar nur dieses eine Mal. Bewahre sie gut auf: wenn der Authenticator verloren geht, kann jeder Wiederherstellungscode einmal anstelle eines Codes zur Anmeldung verwendet werden.

Nach dem Aktivieren kannst du die Wiederherstellungscodes neu erzeugen (die alten werden sofort ungültig) oder die Zwei-Faktor-Authentifizierung ausschalten; für beide Vorgänge ist je ein Code erforderlich. Wenn der Authenticator verloren geht und du keine Wiederherstellungscodes hast, bitte einen Administrator, die Zwei-Faktor-Authentifizierung des Kontos in der **Benutzerverwaltung** zurückzusetzen.

> [!TIP]
> Jeder Code kann nur einmal verwendet werden. Aufeinanderfolgende falsche Code-Eingaben werden wie falsche Passwörter auf die Sperrzählung angerechnet.

## Erneute Bestätigung sensibler Vorgänge

Für Konten mit aktiver Zwei-Faktor-Authentifizierung verlangen die folgenden Vorgänge eine Bestätigung innerhalb der letzten 10 Minuten (konfigurierbar): Rotieren des Signaturschlüssels, Erstellen eines Clients oder Rotieren seines Geheimnisses, Erstellen oder Deaktivieren eines Mandanten, Zurücksetzen des Passworts oder der Zwei-Faktor-Authentifizierung einer anderen Person, Zuweisen von Rollen, Ändern von Berechtigungen, Hinzufügen oder Ändern von Identitätsquellen, Freigeben von Berechtigungsanträgen.

Wenn du diese Vorgänge nach Ablauf der Frist ausführst, öffnet die Konsole den Dialog „Identität bestätigen“; nach der Eingabe des Codes wird der gerade begonnene Vorgang fortgesetzt. Mit `grantforge.security.mfa.required-for-sensitive=true` kannst du verlangen, dass Konten, die diese Vorgänge ausführen, die Zwei-Faktor-Authentifizierung aktiviert haben müssen.

## Online-Sitzungen

Administratoren sehen unter **Zugriffskontrolle → Online-Sitzungen** alle angemeldeten Browser dieses Mandanten (Konto, IP, Browser, Anmeldezeitpunkt, letzte Aktivität) und können verdächtige Sitzungen beenden. Deaktivieren, Sperren eines Kontos oder Zurücksetzen des Passworts beenden sofort alle Sitzungen des Kontos.

![Online-Sitzungen](/screenshots/sessions.png)
