---
title: Offene API für Berechtigungsabfragen
description: Anwendungen fragen mit einem Zugriffstoken die Rollen, Ressourcen, API-Berechtigungen und Datenbereiche eines Benutzers ab und geben eigene Datenentitäten bekannt.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Die offene API liegt unter `/api/v1/open/`; sie akzeptiert ausschließlich Bearer-Token, die vom Autorisierungsserver ausgestellt wurden, und verwendet weder Cookies noch CSRF-Token. Sobald ein Token widerrufen oder seine Berechtigung entzogen wird, funktioniert es nicht mehr.

## Schnittstellen

| Schnittstelle | Token | Beschreibung |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | Benutzertoken, `permissions` | Die Rollen, Ressourcen und API-Berechtigungen des Benutzers in dieser Anwendung; unterstützt `If-None-Match` (304) |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | Benutzertoken, `permissions` | Beantwortet der Reihe nach, ob die Berechtigung jeweils vorliegt (1–100 auf einmal) |
| `GET /api/v1/open/me/data-access` | Benutzertoken, `permissions` | Die Regeln der Rollen für die Datenentitäten dieser Anwendung: Bereiche, Bedingungen, Abteilungen sowie die Abteilungen des Benutzers (inklusive Unterabteilungen), Gruppen und Stellen; unterstützt ETag |
| `PUT /api/v1/open/catalog/data-entities` | Eigenes Client-Token, `catalog` | Deklariert alle Datenentitäten dieser Anwendung und ersetzt die vorherige Deklaration |

## Beispiel

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

Konten, Mandanten und andere IDs kommen als Zeichenketten zurück, weil sie den Bereich ganzer Zahlen überschreiten, den JavaScript genau darstellen kann; `version` ist die Versionsnummer der Berechtigungen.

## Caching und Versionierung

Die Antworten auf `authorization` und `data-access` tragen einen `ETag`. Cached die Antwort und schickt beim nächsten Mal `If-None-Match` mit: Haben sich die Berechtigungen nicht geändert, kommt ein `304` und es entsteht fast kein Aufwand. Jede Änderung an Berechtigungen, Zuweisungen, Ressourcenkatalog oder Datenrichtlinien verändert die Versionsnummer. Die SDKs cachen standardmäßig 30 Sekunden und prüfen danach mit dem ETag erneut.

## Datenentitäten deklarieren

Die Anwendung deklariert ihre Datenentitäten mit ihrem eigenen vertraulichen Client (Client-Zugangsdaten plus `catalog`-Scope):

```json
{
  "entities": [
    {
      "code": "order",
      "name": "Bestellung",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "Status", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "Betrag", "type": "NUMBER" }
      ]
    }
  ]
}
```

Nach der Deklaration erscheinen die Entitäten als `<Anwendungscode>:<Entitätscode>` (zum Beispiel `shop:order`) im Editor für Datenberechtigungen der Rollen. `owned` bedeutet, dass eine Zeile ein Besitzerkonto hat (damit ist der Bereich „eigene Einträge“ verfügbar), `unitBased` bedeutet, dass eine Zeile zu einer Abteilung gehört (damit sind die Abteilungsbereiche verfügbar). Java-Anwendungen müssen diesen Aufruf nicht selbst schreiben; der Starter leitet die Entitäten automatisch aus `@GrantForgeEntity` ab.

## Fehler

Alle Fehler sind RFC 9457 problem details und tragen `code` sowie `requestId`:

| Status / Fehlercode | Bedeutung |
| --- | --- |
| 401 | Kein Token oder das Token ist nicht mehr gültig; melde dich erneut an |
| `GF-SECURITY-003` | Ein Benutzertoken ist nötig, geschickt wurde aber das eigene Token des Clients |
| `GF-SECURITY-004` | Dem Token fehlt ein erforderlicher Scope |
| `GF-SECURITY-005` | Das eigene Token des Clients ist nötig, geschickt wurde aber ein Benutzertoken |
| `GF-AUTHZ-052` | Die deklarierten Datenentitäten sind fehlerhaft; `errors` weist auf jede Stelle hin |
