---
title: Referencia de la API REST
description: Todas las interfaces REST y sus requisitos de acceso, generada a partir del contrato OpenAPI.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Esta página se genera al compilar la documentación a partir del contrato OpenAPI del repositorio (`core/grantforge-web/src/api/openapi.json`) y se mantiene coherente con el servidor. El servicio en ejecución expone además ese mismo contrato en `/v3/api-docs`.

- **Público**: no hace falta iniciar sesión.
- **Con iniciar sesión basta**: cualquier cuenta con sesión iniciada.
- Las demás interfaces listan los códigos de permiso necesarios; los códigos de permiso se registran como recursos de API en el catálogo de recursos.

Las interfaces de la consola usan la sesión y el token CSRF, ver [Diseño de seguridad](/es/architecture/security/); la API abierta que usan las aplicaciones de negocio se describe en [API abierta](/es/integration/open-api/).

{{generated:api}}
