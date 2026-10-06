---
title: Catalogue des ressources et des API
description: Maintenir les applications et l’arborescence des ressources, les dépendances entre ressources et les clients OAuth, consulter les API enregistrées automatiquement et repérer les configurations devenues inopérantes grâce au bilan du catalogue.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Catalogue de ressources

**Administration de la plateforme → Catalogue de ressources** entretient l’arborescence des ressources de chaque application. La console elle-même (`grantforge-console`) est l’application intégrée : ses pages, ses boutons et ses API sont enregistrés automatiquement au démarrage et ne peuvent pas être supprimés.

![Catalogue de ressources](/screenshots/resources.png)

- **Applications** : créez, modifiez et supprimez des applications métier ; une application qui a des clients ou des ressources ne peut pas être supprimée.
- **Ressources** : modules, menus, pages, onglets, boutons, API, entités de données et champs. Le type détermine où un élément peut se trouver : un bouton ne peut être que sous une page ou un onglet, et un champ que sous une entité de données. Les ressources peuvent être réordonnées par glisser-déposer, sur 15 niveaux au maximum.
- **État** : une ressource peut être masquée ou désactivée ; en l’absence d’autorisation, le bouton peut être « masqué » ou « désactivé ».
- **Dépendances** : un bouton « a besoin » de l’API qu’il appelle et une page « a besoin » de l’API dont elle charge les données ; lors de l’attribution des autorisations, les dépendances sont déduites en même temps, et la page de détail les représente sous forme de graphe.
- **Clients OAuth** : enregistrez des clients pour les applications métier ; voir [OAuth 2.1 et OpenID Connect](/fr/integration/oauth/).
- **Champs** : lorsqu’un champ est sélectionné, les interfaces dans lesquelles il apparaît (en retour ou en réception) sont affichées.

## Catalogue d’API

**Administration de la plateforme → Catalogue d’API** liste toutes les interfaces enregistrées automatiquement au démarrage du serveur et leurs exigences d’accès : public, simple compte connecté, ou code de permission requis. Les interfaces nécessitant une autorisation sont rattachées au catalogue de ressources selon leur code de permission, et les rôles y font référence lors de l’attribution des autorisations. Lorsqu’une interface est ajoutée, retirée, ou que son code de permission change, elle apparaît dans les « modifications à confirmer » ; le catalogue est mis à jour après confirmation.

![Catalogue d’API](/screenshots/apis.png)

## Bilan du catalogue

**Administration de la plateforme → Bilan du catalogue** repère les configurations devenues silencieusement inopérantes : autorisations sans effet, boutons inutilisables (il leur manque l’API requise), API que personne ne peut appeler et dépendances rompues. Le bilan se contente de lire les données : il ne modifie rien.

![Bilan du catalogue](/screenshots/health.png)
