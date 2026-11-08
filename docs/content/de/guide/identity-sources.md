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
| Telefon-Claim | standardmäßig `phone` (Casdoor); bei anderen Providern lässt sich `phone_number` eintragen. Damit Telefonnummern synchronisiert werden, muss der Scope `phone` enthalten. Liefert der Provider bei der Anmeldung keine Nummer, bleibt die vorhandene erhalten |
| Avatar-Adresse | wird aus dem OIDC-Standard-Claim `picture` übernommen; der Scope muss `profile` enthalten. Fehlt der Claim, bleibt die vorhandene Avatar-Adresse erhalten |
| Zusätzliche Parameter der Autorisierungsanfrage | ein optionales JSON-Objekt, dessen Werte Zeichenketten sein müssen. Bei Casdoor wählt etwa `{"provider_hint":"github"}` einen konfigurierten Provider. Die Parameter stehen in der Adresszeile des Browsers; trage dort keine Geheimnisse ein |

Die tragenden Protokollparameter (darunter `state`, `nonce`, `redirect_uri` und die PKCE-Parameter) verwaltet GrantForge; zusätzliche Parameter können sie nicht überschreiben.

Nach dem Aktivieren erscheint auf der Anmeldeseite die Schaltfläche „Mit <Name> anmelden“. Meldet sich der Benutzer beim Provider an, kehrt er zu GrantForge zurück; Konten mit aktiver Zwei-Faktor-Authentifizierung müssen zusätzlich einen Code eingeben.

## Kontoregeln

- Die eindeutige Kennung eines Benutzers aus der Identitätsquelle (`entryUUID`/`objectGUID` bei LDAP, `sub` bei OIDC) entspricht genau einem GrantForge-Konto; eine Umbenennung ändert an dieser Zuordnung nichts.
- Gibt es lokal schon ein Konto mit gleichem Namen, werden die beiden **nicht automatisch verknüpft**; die Anmeldung wird abgewiesen, und ein Administrator muss zuerst das lokale Konto umbenennen oder löschen. Damit wird verhindert, dass ein gleichnamiger Benutzer aus dem Verzeichnis ein bestehendes Konto übernimmt.
- Bei einer Identitätsquelle ohne automatische Kontoerstellung können sich nur bereits verknüpfte Benutzer anmelden.
- Nach dem Deaktivieren einer Identitätsquelle können sich ihre Benutzer nicht mehr anmelden; eine Identitätsquelle, die noch von Konten genutzt wird, kann nicht gelöscht werden.
- Konten aus einer Identitätsquelle können wie gewohnt Rollen zugewiesen und Zwei-Faktor-Authentifizierung aktiviert bekommen.

Die Synchronisierung der Organisationsstruktur wird in einer eigenen Datenquelle eingerichtet; die OIDC-Anmeldung muss dafür nicht geändert werden.

## Datenquellen und Synchronisierung der Organisationsstruktur

Datenquellen synchronisieren die Organisationsstruktur, Identitätsquellen sind weiterhin für die Anmeldung über OIDC oder LDAP zuständig. Beide werden getrennt eingerichtet; eine deaktivierte Datenquelle beeinträchtigt die OIDC-Anmeldung nicht.

1. Richte unter „Zugriffskontrolle → Identitätsquellen“ die bestehende Anmeldung über OpenID Connect ein.
2. Lege unter „Zugriffskontrolle → Datenquellen“ eine DingTalk-Datenquelle an und wähle die zugehörige OIDC-Identitätsquelle.
3. Trage CorpId, AppKey, AppSecret und die ID der Startabteilung ein (meist `1`). Das AppSecret wird gesondert verschlüsselt gespeichert, die API gibt es nie im Klartext zurück, und ein beim Bearbeiten leer gelassenes Feld behält den bisherigen Wert.
4. Die Quelle lässt sich unter einer lokalen Abteilung einhängen. Das Synchronisierungsintervall beträgt standardmäßig 30 Minuten, erlaubt sind 15–10080 Minuten; ein leeres Feld bedeutet nur manuelle Synchronisierung.
5. Teste nach dem Speichern die Verbindung und starte die erste Synchronisierung. Die DingTalk-Anwendung braucht Leserechte auf Abteilungen und das Mitarbeiterverzeichnis, und ihr Berechtigungsumfang muss den gewählten Teilbaum abdecken.
6. Meldet sich ein Benutzer per SSO an, wird der Mitarbeiter über die vertrauenswürdige OIDC-E-Mail-Adresse gefunden, und Abteilungen sowie Hauptabteilung werden automatisch gesetzt. Die E-Mail-Adresse muss vom Administrator gepflegt werden, darf von Mitarbeitern nicht für fremde Identitäten genutzt werden können und muss mit der DingTalk-Adresse übereinstimmen. Fehlt die Mitarbeiteradresse, wird die Unternehmensadresse verwendet.

Die Anmeldung liest nur den lokalen Verzeichnis-Schnappschuss und fragt DingTalk nicht ab. Mit MFA wird die Zugehörigkeit erst nach dem erfolgreichen zweiten Schritt aktualisiert. Konten, die sich vor der ersten Synchronisierung anmelden, merken sich die vertrauenswürdige Adresse und werden nach Abschluss der Synchronisierung verknüpft; die lokal bearbeitbare Profiladresse zählt beim Abgleich nicht. Fehlende, doppelte oder bereits an ein anderes Konto gebundene Adressen erzeugen keine neue Bindung.

Die Bindung an einen Mitarbeiter hängt an dessen externer Mitarbeiter-ID und wandert bei einer geänderten E-Mail-Adresse nicht zu einem anderen Mitarbeiter. Die Synchronisierung bildet Zugehörigkeiten zu mehreren Abteilungen ab; als Hauptabteilung bleibt die bisherige erhalten, solange sie gültig ist, sonst wird sie in stabiler Reihenfolge der Abteilungs-IDs gewählt. Lokale Abteilungen, Benutzergruppen, Stellen und direkte Rollenzuweisungen bleiben bestehen. Wird einer synchronisierten Abteilung eine Rolle zugewiesen, erben Benutzer sie nach den bestehenden Regeln, und ein Abteilungswechsel aktualisiert die Berechtigungen.

Sobald ein Organisations-Schnappschuss oder auf Verknüpfung wartende Konten existieren, sind die verknüpfte Identitätsquelle, das Unternehmen, die Anwendung, die Startabteilung und der Einhängepunkt festgelegt. Synchronisierte Abteilungen und Zugehörigkeiten sind schreibgeschützt und lassen sich in der Benutzer- oder Organisationsverwaltung nicht von Hand überschreiben. Verschwundene Abteilungen bleiben erhalten, damit bestehende Berechtigungen nicht verloren gehen; verlässt ein Mitarbeiter das freigegebene Verzeichnis, entfallen die von dieser Quelle verwalteten Zugehörigkeiten, das Konto wird aber nicht automatisch deaktiviert. Eine Datenquelle, der Abteilungen gehören, lässt sich nicht direkt löschen, aber deaktivieren.

Die Synchronisierung holt zuerst alle Seiten und wendet das Ergebnis dann atomar an. Bei Lesefehlern, einer fehlerhaften Hierarchie oder einem geänderten Berechtigungsumfang bleibt der letzte erfolgreiche Schnappschuss bestehen; Administratoren prüfen das Ergebnis über die Synchronisierungsübersicht, die Liste der Auffälligkeiten und das Audit-Protokoll. Hat sich der Berechtigungsumfang geändert, stelle zuerst den freigegebenen Umfang wieder her und synchronisiere dann.

Verwendet wird das [offizielle Server-SDK von DingTalk](https://open.dingtalk.com/document/development/download-the-server-side-sdk); eigene HTTP-Aufrufe gibt es nicht. Die Anbieter-Adapter liefern einheitliche Schnappschüsse von Abteilungen und Mitarbeitern, sodass ein künftiger Feishu-Adapter dieselbe Logik für Synchronisierung, Bindung und Berechtigungsaktualisierung nutzen kann.
