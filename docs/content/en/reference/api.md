---
title: REST API reference
description: All REST endpoints and their access requirements, generated from the OpenAPI contract.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

This page is generated at docs build time from the OpenAPI contract in the repository (`core/grantforge-web/src/api/openapi.json`) and stays consistent with the server. A running server also serves the same contract at `/v3/api-docs`.

- **Public**: no sign-in required.
- **Authenticated**: any signed-in account.
- The remaining endpoints list the permission codes they require; permission codes are registered as API resources in the resource catalog.

Console endpoints use sessions and CSRF tokens; see [Security design](/en/architecture/security/). For the Open API used by business applications, see [Open API](/en/integration/open-api/).

{{generated:api}}
