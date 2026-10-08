---
title: Codici di errore
description: Tutti i codici di errore stabili, lo stato HTTP e il relativo significato, generati dal codice sorgente.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Tutte le risposte di errore rispettano il formato RFC 9457 problem details:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Non disponi dell’autorizzazione per eseguire questa operazione",
  "code": "GF-SECURITY-002",
  "requestId": "0f6c1a…"
}
```

`code` è stabile e può essere valutato nel codice; `detail` viene localizzato in base all’`Accept-Language` della richiesta e serve solo alla visualizzazione. Gli errori di validazione riportano inoltre un array `errors` che indica i campi interessati. La tabella seguente viene generata in fase di compilazione della documentazione a partire dall’enumerazione dei codici di errore presente nel codice sorgente.

{{generated:errors}}
