---
title: Référence de l’API REST
description: Toutes les interfaces REST et leurs conditions d’accès, générée à partir du contrat OpenAPI.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Cette page est générée lors de la construction de la documentation à partir du contrat OpenAPI du dépôt (`core/grantforge-web/src/api/openapi.json`), et reste cohérente avec le serveur. Un service en cours d’exécution expose en plus ce même contrat à l’adresse `/v3/api-docs`.

- **Public** : aucune connexion requise.
- **Connexion suffisante** : tout compte connecté.
- Les autres interfaces listent les codes d’autorisation requis ; les codes d’autorisation sont déclarés comme ressources d’API dans le catalogue de ressources.

Les interfaces de la console utilisent la session et le jeton CSRF, voir [Conception de la sécurité](/fr/architecture/security/) ; l’API ouverte utilisée par les applications métier est décrite dans [API ouverte](/fr/integration/open-api/).

{{generated:api}}
