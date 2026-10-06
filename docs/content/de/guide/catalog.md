---
title: Ressourcen- und API-Katalog
description: Anwendungen und Ressourcenbäume, Ressourcenabhängigkeiten und OAuth-Clients pflegen, automatisch registrierte Schnittstellen prüfen und mit der Katalogprüfung ungültige Konfigurationen finden.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Ressourcenkatalog

**Plattformverwaltung → Ressourcenkatalog** pflegt den Ressourcenbaum jeder Anwendung. Die Konsole selbst (`grantforge-console`) ist eine eingebaute Anwendung; ihre Seiten, Schaltflächen und Schnittstellen werden beim Start automatisch registriert und können nicht gelöscht werden.

![Ressourcenkatalog](/screenshots/resources.png)

- **Anwendungen**: Geschäftsanwendungen anlegen, bearbeiten und löschen; eine Anwendung mit Clients oder Ressourcen kann nicht gelöscht werden.
- **Ressourcen**: Module, Menüs, Seiten, Tabs, Schaltflächen, APIs, Datenentitäten und Felder. Der Typ bestimmt, wo eine Ressource liegen darf: Schaltflächen nur unter Seiten oder Tabs, Felder nur unter Datenentitäten. Ressourcen lassen sich per Ziehen umsortieren, maximal 15 Ebenen tief.
- **Status**: Ressourcen können ausgeblendet oder deaktiviert werden; wenn eine Berechtigung fehlt, kannst du wählen, ob Schaltflächen „ausgeblendet“ oder „deaktiviert“ werden.
- **Abhängigkeiten**: Eine Schaltfläche „benötigt“ die Schnittstellen, die sie aufruft, und eine Seite „benötigt“ die Schnittstellen, aus denen sie Daten lädt; bei der Autorisierung werden die Abhängigkeiten mit abgeleitet, und die Detailseite stellt die Abhängigkeiten als Graph dar.
- **OAuth-Clients**: Registriere Clients für Geschäftsanwendungen, siehe [OAuth 2.1 und OpenID Connect](/de/integration/oauth/).
- **Felder**: Wenn ein Feld ausgewählt ist, wird angezeigt, in welchen Schnittstellen es vorkommt (zurückgegeben oder empfangen).

## API-Katalog

**Plattformverwaltung → API-Katalog** listet alle Schnittstellen, die beim Start des Servers automatisch registriert werden, mit ihrer Zugriffsvoraussetzung: öffentlich, jeder angemeldete Benutzer oder ein bestimmter Berechtigungscode. Schnittstellen, die eine Autorisierung erfordern, werden nach Berechtigungscode in den Ressourcenkatalog eingeordnet, und Rollenautorisierungen verweisen auf diese Berechtigungen. Wird eine Schnittstelle hinzugefügt, außer Betrieb genommen oder ändert sich ihr Berechtigungscode, wird sie als „ausstehende Änderung“ geführt; nach der Bestätigung wird der Katalog aktualisiert.

![API-Katalog](/screenshots/apis.png)

## Katalogprüfung

**Plattformverwaltung → Katalogprüfung** findet Konfigurationen, die stillschweigend unwirksam geworden sind: Autorisierungen, die nicht mehr greifen, Schaltflächen, die nicht funktionieren können (fehlende benötigte Schnittstellen), Schnittstellen, die niemand aufrufen kann, und unterbrochene Abhängigkeiten. Die Prüfung liest nur Daten und ändert nichts.

![Katalogprüfung](/screenshots/health.png)
