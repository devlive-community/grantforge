---
title: API aperta di consultazione dei permessi
description: L’applicazione consulta con il token di accesso i ruoli, le risorse, i permessi API e l’ambito dati dell’utente, e dichiara le proprie entità di dati.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’API aperta si trova sotto `/api/v1/open/`, accetta solo token Bearer emessi dal server di autorizzazione, non usa cookie e non richiede token CSRF; il token smette di valere non appena viene revocato o gli viene revocata l’autorizzazione.

## Interfacce

| Interfaccia | Token | Spiegazione |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | token utente, `permissions` | i ruoli, le risorse e i permessi API dell’utente in questa applicazione; supporta `If-None-Match` (304) |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | token utente, `permissions` | risponde uno a uno se il permesso è posseduto (da 1 a 100 permessi) |
| `GET /api/v1/open/me/data-access` | token utente, `permissions` | le regole dei ruoli sulle entità di dati di questa applicazione: ambito, condizioni e dipartimenti, oltre al dipartimento dell’utente (con i subordinati), ai gruppi e alle posizioni; supporta ETag |
| `PUT /api/v1/open/catalog/data-entities` | token del proprio client, `catalog` | dichiara tutte le entità di dati di questa applicazione, sostituendo la dichiarazione precedente |

## Esempio

```bash
curl -H "Authorization: Bearer $TOKEN" https://grantforge.example.com/api/v1/open/me/authorization
```

```json
{
  "application": "shop",
  "accountId": "100203911213113344",
  "tenantId": "100203900000000000",
  "username": "sam",
  "version": 8121,
  "roles": ["shop-buyers"],
  "resources": ["shop.orders", "shop.orders.btn.create"],
  "permissions": ["orders.read", "orders.create"],
  "computedAt": "2026-10-05T03:12:45Z"
}
```

L’ID dell’account, quello del tenant e gli altri vengono restituiti come stringa, perché escono dal range di interi che JavaScript può rappresentare con precisione; `version` è il numero di versione dei permessi.

## Cache e versione

Le risposte di `authorization` e `data-access` portano `ETag`. Metti in cache la risposta e la volta successiva invia `If-None-Match`: se i permessi non sono cambiati viene restituito `304`, con un costo praticamente nullo. Qualsiasi cambiamento nelle autorizzazioni, nelle assegnazioni, nel catalogo delle risorse o nelle policy sui dati modifica il numero di versione. L’SDK mette in cache per 30 secondi per impostazione predefinita e poi riverifica con ETag.

## Dichiarare entità di dati

L’applicazione dichiara le proprie entità di dati con il client confidenziale (credenziali client più lo scope `catalog`):

```json
{
  "entities": [
    {
      "code": "order",
      "name": "Ordine",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "Stato", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "Importo", "type": "NUMBER" }
      ]
    }
  ]
}
```

Dopo la dichiarazione, le entità compaiono nell’editor dei permessi sui dati dei ruoli come `<codice-applicazione>:<codice-entità>` (per esempio `shop:order`). `owned` indica che la riga ha un account proprietario (permette di usare l’ambito “solo io”) e `unitBased` indica che la riga appartiene a un dipartimento (permette di usare gli ambiti di dipartimento). Le applicazioni Java non devono scrivere questa richiesta a mano: lo starter la dichiara automaticamente a partire da `@GrantForgeEntity`.

## Errori

Tutti gli errori sono problem details RFC 9457, con `code` e `requestId`:

| Stato / codice di errore | Significato |
| --- | --- |
| 401 | token assente o non più valido; occorre accedere di nuovo |
| `GF-SECURITY-003` | serve un token utente, ma è stato inviato un token del proprio client |
| `GF-SECURITY-004` | al token manca lo scope richiesto |
| `GF-SECURITY-005` | serve un token del proprio client, ma è stato inviato un token utente |
| `GF-AUTHZ-052` | le entità di dati dichiarate non sono corrette; `errors` indica ogni punto |
