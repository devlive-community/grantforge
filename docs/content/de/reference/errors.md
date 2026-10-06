---
title: Fehlercodes
description: Alle stabilen Fehlercodes, HTTP-Statuswerte und ihre Bedeutungen, aus dem Quellcode generiert.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Alle Fehlerantworten folgen RFC 9457 problem details:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Du hast für diesen Vorgang keine Berechtigung",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code` ist stabil und kann im Code ausgewertet werden; `detail` wird anhand des `Accept-Language`-Headers der Anfrage lokalisiert und dient nur der Anzeige. Validierungsfehler tragen zusätzlich ein `errors`-Array, das die konkreten Felder nennt. Die folgende Tabelle wird beim Erzeugen der Dokumentation aus dem Fehlercode-Enum im Quellcode generiert.

{{generated:errors}}
