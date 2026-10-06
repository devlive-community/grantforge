---
title: Mandanten
description: Mandanten erstellen, bearbeiten, deaktivieren und wieder aktivieren.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Plattformverwaltung → Mandantenverwaltung**: Jeder Mandant ist eine voneinander getrennte Organisation mit eigenen Benutzern und Berechtigungen. Der Plattform-Mandant trägt die Plattform-Administratoren und kann nicht deaktiviert werden.

![Mandantenverwaltung](/screenshots/tenants.png)

## Mandanten erstellen

Fülle Mandanten-Code, Name sowie Benutzername, Anzeigename und Passwort des ersten Administrators dieses Mandanten aus. Der erste Administrator hält die Systemrolle „Mandanten-Administrator“ dieses Mandanten und muss das Passwort bei der ersten Anmeldung ändern. Der Benutzername ist plattformweit eindeutig.

## Deaktivieren und Aktivieren

Nach dem Deaktivieren eines Mandanten werden sofort alle Konten dieses Mandanten abgemeldet und können sich nicht mehr anmelden; bereits ausgestellte Anwendungs-Token werden nicht mehr verlängert. Daten werden nicht gelöscht, mit dem erneuten Aktivieren ist alles wiederhergestellt.

## Plattform-Mandant

Der Plattform-Mandant ist der erste bei der Initialisierung erstellte Mandant. Seine Administratoren können alle Mandanten, den [Ressourcen- und API-Katalog](/de/guide/catalog/), den Autorisierungsserver und die Plug-ins verwalten. Geschäftsanwendungen und ihre Ressourcen werden plattformweit geteilt; jeder Mandant gibt diese Ressourcen in seinen eigenen Rollen frei.
