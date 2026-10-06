---
title: Sicherheitsdesign
description: Das Design von Sitzungen, CSRF, Passwort und Sperre, Zwei-Faktor-Authentifizierung und erneuter Verifizierung, verschlüsselter Ablage, Tokens und Audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Sitzungen der Konsole

- Sitzungen werden in der Datenbank gespeichert (Spring Session JDBC); der Browser hält nur das Cookie `GRANTFORGE_SESSION`: HttpOnly, SameSite=Lax und unter HTTPS mit Secure (oder mit `grantforge.security.cookie-secure` erzwungen).
- Beim Anmelden, beim Abschluss der Zwei-Faktor-Authentifizierung und beim Abschluss der Verbundanmeldung werden Sitzungs-ID und CSRF-Token gewechselt, um Sitzungsfixierung zu verhindern.
- Anfragen, die den Zustand ändern, müssen den Header `X-XSRF-TOKEN` mitführen; der Wert kommt aus dem Cookie `XSRF-TOKEN`.
- Administratoren können jede Sitzung auflisten und beenden; Deaktivieren, Sperren und das Zurücksetzen des Passworts beenden sofort alle Sitzungen des Kontos; eine Passwortänderung beendet die Sitzungen auf anderen Geräten.

## Passwörter

- Neue Passwörter werden mit Argon2id gehasht; BCrypt- und aus 1.x importierte Hashes lassen sich weiterhin prüfen und werden bei der nächsten Anmeldung auf den aktuellen Algorithmus gehoben.
- Richtlinie: Länge, Zeichenklassen, Historie und Gültigkeitsdauer sind konfigurierbar, und das Passwort darf den Benutzernamen nicht enthalten.
- Nach der konfigurierten Anzahl fehlgeschlagener Versuche wird das Konto gesperrt; auch für unbekannte Benutzernamen wird ein Hash-Vergleich ausgeführt, und die Antwortzeit verrät nicht, welche Benutzernamen es gibt; deaktivierte Konten und deaktivierte Mandanten werden erst gemeldet, wenn das Passwort richtig war.

## Zwei-Faktor-Authentifizierung und erneute Verifizierung

- TOTP (RFC 6238, SHA-1, 6 Stellen, 30 Sekunden, vor und nach je ein Zeitschritt Toleranz); ein Code desselben Zeitschritts ist nur einmal verwendbar; 10 einmalige Wiederherstellungscodes werden mit SHA-256 gespeichert.
- Bei Konten mit aktiver Zwei-Faktor-Authentifizierung bleibt die Sitzung nach dem richtigen Passwort 5 Minuten im Zustand „ausstehend“; vor dem Abschluss des zweiten Schritts ist man nicht angemeldet.
- Sensible Schnittstellen sind mit `@RequireStepUp` annotiert: Konten mit aktiver Zwei-Faktor-Authentifizierung müssen sich innerhalb eines konfigurierbaren Zeitfensters verifiziert haben, sonst wird `GF-SECURITY-006` zurückgegeben; die Konsole zeigt einen Bestätigungsdialog und wiederholt den Aufruf danach.

## Verschlüsselte Ablage

Die gebundenen Passwörter und Client-Geheimnisse der Identitätsquellen, die Schlüssel der Verifizierer, die sensiblen Konfigurationen der Datendienste und die privaten Signaturschlüssel des Autorisierungsservers werden mit AES-GCM verschlüsselt gespeichert. Der Schlüssel kommt aus `grantforge.security.encryption-key`; ist er nicht gesetzt, wird er automatisch erzeugt und in der Datenbank abgelegt (nur für Testzwecke geeignet). Agent-Tokens und OAuth-Client-Geheimnisse werden nur als Hash gespeichert.

## Tokens

- Der Autorisierungsserver speichert den Hash des Tokens, nicht das Token selbst.
- Refresh-Tokens werden bei jeder Verwendung ausgetauscht; wird ein altes Token wiederverwendet, wird die gesamte Autorisierung widerrufen.
- Deaktivierung, Sperrung oder ein nötiger Passwortwechsel des Kontos, die Deaktivierung des Clients und die Deaktivierung des Mandanten verhindern eine Token-Verlängerung; die offene API bestätigt bei jedem Aufruf, dass das Token noch gültig ist.
- Richtlinien-Snapshots werden mit Ed25519 signiert; der Agent verwendet sie erst, nachdem er die Signatur geprüft hat.

## Schutz der Schnittstellen

- Jede Schnittstelle muss ihre Zugriffsweise deklarieren; eine Schnittstelle ohne Deklaration verhindert den Start. Schnittstellen mit Berechtigungsbedarf werden bei jedem Aufruf gegen den aktuellen Snapshot geprüft.
- Abgewiesene Aufrufe werden ins Audit geschrieben (ohne die Anfrage zu blockieren).
- Lokale Konten mit demselben Namen wie in einer externen Identitätsquelle werden nicht automatisch verknüpft, um Kontoübernahmen zu verhindern.
- Beim CSV-Export wird Zellen, die mit `=`, `+`, `-` oder `@` beginnen, ein Präfix vorangestellt, um Formelinjektion zu verhindern.

## Audit

Alle Verwaltungsaktionen, Berechtigungsänderungen, Anmeldeereignisse und abgewiesenen Aufrufe werden im Audit-Protokoll festgehalten. Berechtigungsänderungen und ihr Audit-Eintrag werden in derselben Transaktion committet; wird die Änderung zurückgerollt, bleibt auch kein Audit-Eintrag zurück.
