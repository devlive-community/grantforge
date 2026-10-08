---
title: Riferimento all’API REST
description: Tutti gli endpoint REST e i relativi requisiti di accesso, generati dal contratto OpenAPI.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Questa pagina viene generata in fase di compilazione della documentazione a partire dal contratto OpenAPI presente nel repository (`core/grantforge-web/src/api/openapi.json`) e resta coerente con il server. Il servizio in esecuzione espone inoltre lo stesso contratto su `/v3/api-docs`.

- **Pubblico**: non richiede l’accesso.
- **Basta l’accesso**: qualsiasi account con sessione attiva.
- Gli altri endpoint elencano i codici di permesso necessari; i codici di permesso sono registrati come risorse API nel catalogo delle risorse.

Gli endpoint della console utilizzano la sessione e il token CSRF, vedi [progetto di sicurezza](/it/architecture/security/); l’API aperta utilizzata dalle applicazioni di business è descritta in [API aperta](/it/integration/open-api/).

{{generated:api}}
