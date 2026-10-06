---
title: API ouverte de consultation des autorisations
description: Les applications consultent avec un jeton d’accès les rôles, ressources, autorisations d’API et portées de données d’un utilisateur, et déclarent leurs propres entités de données.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’API ouverte se trouve sous `/api/v1/open/`. Elle n’accepte que les jetons Bearer émis par le serveur d’autorisation, sans cookie et sans jeton CSRF ; un jeton révoqué, ou dont l’autorisation est retirée, cesse immédiatement de fonctionner.

## Interfaces

| Interface | Jeton | Description |
| --- | --- | --- |
| `GET /api/v1/open/me/authorization` | jeton d’utilisateur, `permissions` | Rôles, ressources et autorisations d’API de l’utilisateur dans cette application ; prise en charge d’`If-None-Match` (304) |
| `GET /api/v1/open/me/permissions?permission=a&permission=b` | jeton d’utilisateur, `permissions` | Répond une par une à la question de savoir si la permission est détenue (1 à 100 à la fois) |
| `GET /api/v1/open/me/data-access` | jeton d’utilisateur, `permissions` | Règles des rôles sur les entités de données de cette application : portées, conditions, services, ainsi que les services de l’utilisateur (subordonnés inclus), groupes et postes ; prise en charge d’ETag |
| `PUT /api/v1/open/catalog/data-entities` | jeton propre au client, `catalog` | Déclare toutes les entités de données de cette application et remplace la déclaration précédente |

## Exemples

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

Les identifiants de compte, de locataire et autres sont renvoyés sous forme de chaînes, car ils dépassent la plage d’entiers que JavaScript représente exactement ; `version` est le numéro de version des autorisations.

## Cache et version

Les réponses d’`authorization` et de `data-access` portent un `ETag`. Mettez la réponse en cache et joignez `If-None-Match` à la demande suivante : si les autorisations n’ont pas changé, un `304` est renvoyé, avec un coût quasi nul. Tout changement d’autorisation, d’affectation, de catalogue de ressources ou de politique de données modifie le numéro de version. Les SDK mettent en cache 30 secondes par défaut, puis revérifient avec l’ETag.

## Déclarer des entités de données

L’application déclare ses entités de données avec son client confidentiel propre (identifiants du client + scope `catalog`) :

```json
{
  "entities": [
    {
      "code": "order",
      "name": "Commande",
      "owned": true,
      "unitBased": false,
      "fields": [
        { "code": "status", "name": "Statut", "type": "CHOICE", "choices": ["NEW", "PAID", "SHIPPED"] },
        { "code": "total", "name": "Montant", "type": "NUMBER" }
      ]
    }
  ]
}
```

Après la déclaration, les entités apparaissent sous la forme `<code d’application>:<code d’entité>` (par exemple `shop:order`) dans l’éditeur d’autorisations sur les données des rôles. `owned` indique qu’une ligne de données a un compte propriétaire (la portée « moi » est alors disponible), `unitBased` indique qu’une ligne de données appartient à un service (les portées de service sont alors disponibles). Les applications Java n’ont pas besoin d’écrire cette requête à la main : le starter la déclare automatiquement à partir d’`@GrantForgeEntity`.

## Erreurs

Toutes les erreurs sont des problem details RFC 9457, avec un `code` et un `requestId` :

| État / code d’erreur | Signification |
| --- | --- |
| 401 | Aucun jeton, ou jeton qui n’est plus valide ; vous devez vous reconnecter |
| `GF-SECURITY-003` | Un jeton d’utilisateur est requis, mais c’est un jeton propre au client qui a été envoyé |
| `GF-SECURITY-004` | Le jeton ne possède pas le scope requis |
| `GF-SECURITY-005` | Un jeton propre au client est requis, mais c’est un jeton d’utilisateur qui a été envoyé |
| `GF-AUTHZ-052` | Les entités de données déclarées sont incorrectes ; `errors` indique chaque endroit |
