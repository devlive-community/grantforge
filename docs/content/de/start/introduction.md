---
title: Produktübersicht
description: Was GrantForge ist, welche Probleme es löst und wie es sich von gängigen Alternativen unterscheidet.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge ist eine quelloffene (MIT) Plattform für einheitliche Berechtigungen. Sie zentralisiert, „wer was tun und welche Daten er sehen darf“: In der Konsole pflegst du Benutzer und Organisationen, definierst Rollen und vergibst Menüs, Schaltflächen, APIs, Datenzeilen und Felder an Rollen. Deine Anwendung meldet Benutzer über OAuth 2.1 / OpenID Connect an und prüft Berechtigungen anschließend über die offene API oder ein SDK.

![Überblick über die GrantForge-Konsole](/screenshots/dashboard.png)

## Welche Probleme es löst

Wächst ein System über eine bestimmte Größe hinaus, verteilen sich Berechtigungen schnell überall: Menüs stehen in der Frontend-Konfiguration, jede Schnittstelle prüft mit eigenen Annotations für sich, und der sichtbare Datenumfang hängt an handgeschriebenen Bedingungen im SQL. Wenn jemand das Unternehmen verlässt oder die Stelle wechselt, kann niemand mit Sicherheit sagen, was er noch darf. GrantForge führt all das in einem einzigen Modell zusammen:

- **Einmal definiert, überall wirksam**: Konsolenseiten, Schaltflächen, REST-Endpunkte, Datenentitäten und Felder sind „Ressourcen“. Rollen erhalten Berechtigungen auf Ressourcen, und dieselbe Berechtigung steuert gleichzeitig die Sichtbarkeit im Frontend und die Prüfung im Backend.
- **Sichtbare Berechtigungen**: „Warum kann diese Person jene Seite sehen?“ und „Wen betrifft die Änderung dieser Rolle?“ lassen sich jederzeit beantworten. Berechtigungsänderungen haben eine Vorschau und einen Audit-Trail.
- **Bereit für Governance**: Funktionstrennung, befristete Berechtigungsanträge, regelmäßige Prüfungen und Zwei-Faktor-Authentifizierung erfüllen die üblichen Anforderungen aus MLPS und internen Kontrollaudits.
- **Anbindung über Standardprotokolle**: Anwendungen brauchen kein eigenes Benutzersystem. Anmeldung über OIDC und Berechtigungsabfragen mit dem Zugriffstoken genügen.

## Funktionen im Überblick

| Bereich | Funktionen |
| --- | --- |
| Identität und Organisation | Mehrmandantenfähigkeit, Abteilungsbaum, Gruppen, Stellen; CSV-Import und -Export in großen Mengen; LDAP/AD-Anmeldung und -Synchronisation, OIDC-Föderation |
| Kontosicherheit | Sitzungsverwaltung, Passwortrichtlinien und Sperrung, TOTP-Zwei-Faktor-Authentifizierung und Wiederherstellungscodes, zweite Bestätigung für kritische Aktionen |
| Funktionsberechtigungen | Ressourcenkatalog (Module, Menüs, Seiten, Tabs, Schaltflächen, APIs), Rollenvererbung, Berechtigungsmatrix, Auswirkungsanalyse |
| Datenberechtigungen | Sichtbare Zeilen nach Bedingung einschränken (eigene Einträge, eigene Abteilung inklusive Unterabteilungen, bestimmte Abteilungen, eigene Bedingungen), Lesen und Schreiben getrennt steuerbar |
| Feldberechtigungen | Felder verstecken, maskieren (E-Mail, Telefonnummer, Ausweisnummer) oder schreibgeschützt setzen |
| Erklärbarkeit und Audit | Berechtigungserklärung, Berechtigungssimulation, Abfrage und Export des Audit-Protokolls |
| Governance | Funktionstrennungsregeln, Berechtigungsantrag und Freigabe, regelmäßige Berechtigungsprüfungen |
| Anwendungsintegration | OAuth 2.1 / OIDC-Autorisierungsserver, offene API für Berechtigungsabfragen, Java- (Spring Boot) und JavaScript-SDK |
| Externe Systeme | Diensttypen und Policy-Engine als Plug-ins (ähnlich Apache Ranger), Datendienste, Zugriffsrichtlinien und Agenten |
| Auslieferung | Ein einzelnes Release-Paket, Docker-Images, Compose-Beispiele, Helm-Chart; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

## Unterschiede zu gängigen Ansätzen

> [!NOTE]
> GrantForge ist keine Bibliothek, die nur RBAC abbildet, und kein IdP, der nur Single Sign-on beherrscht. Es legt Identität, Autorisierung und Governance in ein einziges Modell und macht jede Berechtigung erklärbar.

- **Verglichen mit handgeschriebenen Berechtigungen im Code**: Berechtigungsregeln werden in der Konsole gepflegt, eine Änderung an Berechtigungen erfordert kein Deployment der Anwendung; vor der Vergabe siehst du die Auswirkungen, danach protokolliert das Audit-Protokoll.
- **Verglichen mit einem reinen Authentifizierungs-IdP (Keycloak u. ä.)**: GrantForge bringt ein Berechtigungsmodell bis hin zu Schaltflächen, Datenzeilen und Feldern mit sowie Governance-Funktionen wie Funktionstrennung und Berechtigungsprüfungen. Es kann selbst als IdP auftreten oder vorhandenes LDAP und OIDC als Identitätsquellen anbinden.
- **Verglichen mit Apache Ranger**: GrantForge übernimmt von Ranger die Architektur aus Diensttypen, Richtlinien und Agenten zur Verwaltung von Berechtigungen externer Datensysteme; in erster Linie ist es jedoch eine Berechtigungsplattform für Geschäftsanwendungen.

## Nächste Schritte

- [In fünf Minuten loslegen](/de/start/quick-start/): herunterladen, starten, Ersteinrichtung abschließen und die erste Rolle vergeben.
- [Kernkonzepte](/de/start/concepts/): wie Ressourcen, Rollen, Berechtigungen, Zuweisungen und Auswertung zusammenspielen.
- [Überblick zur Anwendungsintegration](/de/integration/overview/): so setzt du GrantForge in deiner Anwendung ein.
