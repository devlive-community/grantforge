---
title: REST-API-Referenz
description: Alle REST-Schnittstellen und ihre Zugriffsvoraussetzungen, generiert aus dem OpenAPI-Vertrag.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Diese Seite wird beim Bau der Dokumentation aus dem OpenAPI-Vertrag im Repository (`core/grantforge-web/src/api/openapi.json`) erzeugt und bleibt konsistent zum Server. Ein laufender Server stellt denselben Vertrag zusätzlich unter `/v3/api-docs` bereit.

- **Öffentlich**: keine Anmeldung nötig.
- **Angemeldet genügt**: jedes angemeldete Konto.
- Die übrigen Schnittstellen listen die erforderlichen Berechtigungscodes; Berechtigungscodes sind im Ressourcenkatalog als API-Ressourcen hinterlegt.

Die Konsolenschnittstellen nutzen Sessions und CSRF-Token, siehe [Sicherheitsdesign](/de/architecture/security/); die offene API für Geschäftsanwendungen siehst du unter [Offene API](/de/integration/open-api/).

{{generated:api}}
