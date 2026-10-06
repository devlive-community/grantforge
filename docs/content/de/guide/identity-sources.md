---
title: Identitätsquellen (LDAP und OIDC)
description: Benutzer melden sich über das Firmenverzeichnis (LDAP/AD) oder einen OpenID Connect-Provider an; Konten werden automatisch angelegt und synchronisiert.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Unter **Zugriffskontrolle → Identitätsquellen** richtest du die Verzeichnisse oder Identity Provider ein, über die sich Benutzer anmelden können. Die Passwörter solcher Konten liegen in der Identitätsquelle; GrantForge speichert sie nicht und kann sie nicht ändern.

![Identitätsquellen](/screenshots/identity-sources.png)

## LDAP / Active Directory

Klicke auf „Identitätsquelle hinzufügen“ und wähle als Typ „LDAP-Verzeichnis“:

| Einstellung | Erläuterung |
| --- | --- |
| Verzeichnisadresse | `ldap://` oder `ldaps://`; mehrere Adressen werden mit Leerzeichen getrennt und ermöglichen Failover |
| Base DN der Benutzer | zum Beispiel `ou=people,dc=example,dc=com` |
| Suchkonto | das Konto (Bind DN) mit Passwort, mit dem Benutzer gesucht werden; leer bedeutet anonyme Suche |
| Benutzerfilter | standardmäßig `(&(objectClass=person)(uid={0}))`; `{0}` steht für den eingegebenen Namen |
| Attribute | Attribute für Benutzername, Anzeigename, E-Mail-Adresse und eindeutige Kennung; die Vorgaben passen zu OpenLDAP (`uid`, `cn`, `mail`, `entryUUID`), für Active Directory trage `sAMAccountName` und `objectGUID` ein |
| Konten für neue Benutzer automatisch anlegen | Wenn aktiviert, wird beim ersten Anmelden eines Benutzers aus dem Verzeichnis ein Konto erzeugt |
| Synchronisierungsintervall | leer bedeutet nur manuelle Synchronisierung; mindestens 15 Minuten |
| Konten von Benutzern deaktivieren, die das Verzeichnis verlassen haben | Bei der Synchronisierung werden Benutzer deaktiviert, die im Verzeichnis nicht mehr vorhanden sind |

Klicke nach dem Speichern auf **Verbindung testen**, um zu prüfen, ob die Konfiguration korrekt ist.

Bei der Anmeldung sucht GrantForge den Benutzer zuerst mit dem Suchkonto und prüft dann das Passwort, indem es sich mit dem eingegebenen Passwort als dieser Benutzer an das Verzeichnis bindet.

Die **Synchronisierung** legt für neue Benutzer im Verzeichnis Konten an, aktualisiert Namen und E-Mail-Adressen bestehender Konten und deaktiviert – je nach Einstellung – die Benutzer, die das Verzeichnis verlassen haben, und beendet dabei ihre Sitzungen. Das Ergebnis der Synchronisierung erscheint auf der Karte der Identitätsquelle.

## OpenID Connect

Wähle als Typ „OpenID Connect“, um Keycloak, Azure AD, Okta, ein weiteres GrantForge und andere anzubinden:

| Einstellung | Erläuterung |
| --- | --- |
| Issuer-Adresse | die Ausstelleradresse des Providers; GrantForge bezieht über deren Discovery-Dokument Endpunkte und Schlüssel |
| Client-ID / Client-Secret | der beim Provider registrierte Client; ohne Secret als öffentlicher Client mit PKCE |
| Callback-Adresse | die auf der Seite angezeigte Adresse `<GrantForge>/api/v1/auth/federated/callback/<Code>`; sie muss im Client beim Provider eingetragen werden |
| Scopes und Claims | standardmäßig `openid profile email`; Benutzername, Anzeigename und E-Mail-Adresse werden aus den Claims `preferred_username`, `name` bzw. `email` gelesen |

Nach dem Aktivieren erscheint auf der Anmeldeseite die Schaltfläche „Mit <Name> anmelden“. Meldet sich der Benutzer beim Provider an, kehrt er zu GrantForge zurück; Konten mit aktiver Zwei-Faktor-Authentifizierung müssen zusätzlich einen Code eingeben.

## Kontoregeln

- Die eindeutige Kennung eines Benutzers aus der Identitätsquelle (`entryUUID`/`objectGUID` bei LDAP, `sub` bei OIDC) entspricht genau einem GrantForge-Konto; eine Umbenennung ändert an dieser Zuordnung nichts.
- Gibt es lokal schon ein Konto mit gleichem Namen, werden die beiden **nicht automatisch verknüpft**; die Anmeldung wird abgewiesen, und ein Administrator muss zuerst das lokale Konto umbenennen oder löschen. Damit wird verhindert, dass ein gleichnamiger Benutzer aus dem Verzeichnis ein bestehendes Konto übernimmt.
- Bei einer Identitätsquelle ohne automatische Kontoerstellung können sich nur bereits verknüpfte Benutzer anmelden.
- Nach dem Deaktivieren einer Identitätsquelle können sich ihre Benutzer nicht mehr anmelden; eine Identitätsquelle, die noch von Konten genutzt wird, kann nicht gelöscht werden.
- Konten aus einer Identitätsquelle können wie gewohnt Rollen zugewiesen und Zwei-Faktor-Authentifizierung aktiviert bekommen.
