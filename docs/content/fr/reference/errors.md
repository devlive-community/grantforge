---
title: Codes d’erreur
description: Tous les codes d’erreur stables, les statuts HTTP et leur signification, générés depuis le code source.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Toutes les réponses d’erreur respectent le format RFC 9457 problem details :

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Vous n’avez pas l’autorisation d’effectuer cette opération",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code` est stable et peut être évalué dans le code ; `detail` est localisé selon l’en-tête `Accept-Language` de la requête et sert uniquement à l’affichage. Les erreurs de validation portent en plus un tableau `errors` qui indique les champs concernés. Le tableau ci-dessous est généré lors de la construction de la documentation à partir de l’énumération des codes d’erreur du code source.

{{generated:errors}}
