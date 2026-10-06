---
title: OAuth 2.1 und OpenID Connect
description: Endpunkte des Autorisierungsservers, Client-Typen, Token-Regeln und Signaturschlüssel.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge bringt einen auf Spring Authorization Server basierenden Autorisierungsserver mit, der die Sicherheitsanforderungen von OAuth 2.1 erfüllt: Unterstützt werden nur der Autorisierungscode-Fluss (PKCE ist Pflicht), Refresh-Tokens und Client-Anmeldedaten; der implizite Fluss und der Passwort-Grant werden nicht unterstützt.

![Autorisierungsserver](/screenshots/oauth.png)

## Discovery-Dokument und Endpunkte

Discovery-Dokument: `<GrantForge>/.well-known/openid-configuration`; auf der Seite **Plattformverwaltung → Autorisierungsserver** kann es direkt kopiert werden. Der Issuer ist standardmäßig die Adresse, unter der die Anfrage eintrifft; wird hinter einem Reverse-Proxy bereitgestellt, fixiere ihn mit `grantforge.oauth.issuer`.

| Endpunkt | Erläuterung |
| --- | --- |
| `/oauth2/authorize` | Autorisierungscode-Fluss; alle Clients müssen PKCE (S256) verwenden |
| `/oauth2/token` | Autorisierungscode, Refresh-Token, Client-Anmeldedaten. Refresh-Tokens werden bei jeder Verwendung rotiert; ein erneut auftauchendes altes Token widerruft die gesamte Autorisierung |
| `/oauth2/revoke` | Token widerrufen |
| `/oauth2/jwks` | Öffentliche Signaturschlüssel (RS256) |
| `/userinfo` | `sub`, `tid`, `preferred_username`; der Scope `profile` liefert `name`, der Scope `email` liefert `email` |

Zugriffs-Token und ID-Token enthalten `tid` (die Mandanten-ID) und `preferred_username`; `auth_time` im ID-Token ist der Zeitpunkt, an dem sich der Benutzer an der Konsole angemeldet hat.

## Clients

Wähle unter **Plattformverwaltung → Ressourcenkatalog** die Anwendung aus und klicke auf „OAuth-Clients“, um ihre Clients zu verwalten.

| Einstellung | Regel |
| --- | --- |
| Typ | Der **öffentliche Client** ist für Browser, Mobilgeräte und andere Anwendungen gedacht, die kein Geheimnis verwahren können; der **vertrauliche Client** ist für Serveranwendungen bestimmt und hat ein Geheimnis |
| Callback-Adressen | Höchstens 10, absolute Adressen; Platzhalter und Fragmente sind nicht erlaubt. Es muss https sein, oder lokales http (localhost, 127.0.0.1, [::1]), oder ein eigenes Protokoll einer nativen Anwendung |
| scope | `openid`, `profile`, `email`, `permissions` (Berechtigungen abfragen), `catalog` (Datenentitäten deklarieren, nur mit Client-Anmeldedaten) |
| Autorisierungstypen | Autorisierungscode, Refresh-Token (erfordert den Autorisierungscode; nur vertrauliche Clients erhalten ihn), Client-Anmeldedaten (nur vertrauliche Clients) |
| Token-Gültigkeit | Zugriffs-Token 1 Minute–24 Stunden (Standard 15 Minuten), Refresh-Tokens 1 Stunde–90 Tage (Standard 30 Tage) |

Das Geheimnis eines vertraulichen Clients wird nur einmal angezeigt, bei der Registrierung oder bei der Rotation; GrantForge speichert nur seinen Hash. Bei der Rotation kann eine Übergangsfrist gesetzt werden (maximal 7 Tage), in der altes und neues Geheimnis gültig sind, was rollende Aktualisierungen erleichtert.

## Token-Regeln

- Token werden gehasht gespeichert: Selbst bei einem Datenbankleck bleiben keine nutzbaren Token übrig.
- Nach jedem der folgenden Ereignisse werden bereits ausgestellte Token nicht mehr verlängert: der Client wird deaktiviert oder gelöscht, das Konto wird deaktiviert oder gesperrt, das Konto muss sein Passwort ändern oder der Mandant wird deaktiviert.
- Ursprungsübergreifende Aufrufe aus dem Browser: GrantForge erlaubt es Ursprüngen, die eine aktivierte Callback-Adresse eines Clients hosten, den Token-Endpunkt und die offene API ursprungsübergreifend aufzurufen, und zwar ohne Cookies.

## Signaturschlüssel

Signaturschlüssel werden mit RSA 2048 erzeugt, die privaten Schlüssel werden verschlüsselt gespeichert. Standardmäßig rotieren sie automatisch alle 90 Tage (`grantforge.oauth.signing-key-rotation`), und alte öffentliche Schlüssel bleiben noch 2 Tage im JWKS veröffentlicht (`signing-key-retention`), damit Token, die vor der Rotation ausgestellt wurden, weiterhin verifizierbar sind. Bei Bedarf kann auf der Seite des Autorisierungsservers sofort rotiert werden (ein sensibler Vorgang; Konten mit aktiver Zwei-Faktor-Authentifizierung müssen ihn erneut bestätigen).

## Andere Systeme mit GrantForge anmelden

Jedes System, das OpenID Connect unterstützt (Grafana, GitLab, Jenkins und weitere), kann GrantForge als IdP nutzen: Lege im Ressourcenkatalog eine Anwendung und einen vertraulichen Client dafür an und trage die Adresse des Discovery-Dokuments, die client_id und das Geheimnis in die OIDC-Konfiguration des anderen Systems ein. Umgekehrt kann sich GrantForge auch über andere IdPs anmelden, siehe [Identitätsquellen](/de/guide/identity-sources/).
