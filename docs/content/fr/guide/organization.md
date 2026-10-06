---
title: Services, groupes et postes
description: Maintenir l’arborescence des services, créer des groupes d’utilisateurs selon les besoins et gérer les postes, qui peuvent tous servir de cible à l’affectation des rôles et aux périmètres de données.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Organigramme

**Contrôle d’accès → Organigramme** maintient la hiérarchie des services.

![Organigramme](/screenshots/org.png)

- Les services comptent au plus 16 niveaux ; vous pouvez les réorganiser par glisser-déposer ou via « Déplacer vers… », mais pas sous l’un de leurs propres subordonnés.
- Chaque compte a un service principal et peut exercer dans plusieurs services.
- Les services sont un référentiel important des autorisations sur les données : « Mon service », « Mon service et ses subordonnés » et « Services désignés » sont tous calculés d’après cette structure.
- Seuls les services sans service subordonné peuvent être supprimés.

## Groupes d’utilisateurs

**Contrôle d’accès → Groupes d’utilisateurs** : placez dans un même groupe les comptes qui ont besoin des mêmes autorisations, puis il suffit d’affecter un rôle au groupe. Les groupes d’utilisateurs sont indépendants de la structure organisationnelle et conviennent aux ensembles transversaux comme un « groupe d’astreinte » ou un « groupe projet ». Les membres peuvent être ajoutés et retirés par lot, à raison de 500 personnes au maximum par opération.

![Groupes d’utilisateurs](/screenshots/groups.png)

## Postes

**Contrôle d’accès → Postes** : maintenez les postes de l’organisation (par exemple « directeur financier ») et attribuez un ou plusieurs postes à un utilisateur lors de son édition. Les postes peuvent eux aussi se voir affecter des rôles : lors d’un changement de poste, il suffit de modifier le poste de la personne et ses autorisations suivent.

![Postes](/screenshots/positions.png)
