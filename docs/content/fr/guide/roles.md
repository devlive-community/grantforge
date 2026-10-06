---
title: Rôles et autorisations
description: Créez des rôles, accordez des pages, des boutons et des API, configurez l’héritage, attribuez des rôles à des personnes, des groupes, des services ou des postes, et prévisualisez l’impact avant de modifier.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Contrôle d’accès → Gestion des rôles** : un rôle est un ensemble d’autorisations ; il prend effet dès qu’il est attribué à des utilisateurs, des groupes d’utilisateurs, des services ou des postes.

![Gestion des rôles](/screenshots/roles.png)

## Rôles système et rôles personnalisés

Chaque locataire dispose du rôle système **Administrateur du locataire**, et le locataire de plateforme dispose en plus du rôle **Administrateur de la plateforme**. Les rôles système possèdent automatiquement toutes les ressources de leur module et ne peuvent pas être modifiés ; lorsque vous avez besoin d’autorisations similaires mais plus restreintes, **dupliquez** le rôle système puis modifiez la copie.

Les rôles personnalisés ont un code (lettres minuscules, chiffres, points, traits d’union ou soulignements) et un nom, et peuvent être désactivés : un rôle désactivé n’accorde aucune autorisation et n’en transmet aucune par héritage.

## Autorisations

Cliquez sur **Autoriser** dans la ligne du rôle pour ouvrir la matrice d’autorisations :

![Matrice d’autorisations](/screenshots/role-grants.png)

- Basculez d’une application à l’autre et dépliez les ressources sous forme d’arborescence de répertoire ; chaque ressource peut être définie sur « Autoriser » ou « Refuser ».
- **Autoriser un bouton déduit automatiquement la page à laquelle il appartient et les API dont il a besoin** ; vous n’avez pas à les cocher une par une. Les ressources déduites sont marquées dans la matrice.
- **Le refus est prioritaire** et s’applique aux ressources subordonnées : refusez une page et ses boutons sont inutilisables, même si un autre rôle les autorise.
- Vous ne pouvez accorder que les autorisations que vous détenez vous-même, ce qui évite toute escalade de privilèges. Les détenteurs d’un rôle système peuvent accorder n’importe quelle ressource des applications métier.

Avant l’enregistrement, GrantForge affiche l’**impact** de cette modification : quelles ressources deviennent disponibles ou indisponibles, et combien d’utilisateurs détiennent ce rôle.

## Héritage

Cliquez sur **Hériter** et choisissez les rôles dont ce rôle hérite : il reçoit tout ce que les rôles hérités autorisent et refusent, ainsi que ce dont ils héritent à leur tour. L’héritage ne peut pas former de cycle, et vous ne pouvez hériter que des autorisations que vous détenez vous-même. Cela convient aux relations cumulatives du type « manager = employé + approbateur ».

## Attribution

Cliquez sur **Attribuer** pour attribuer le rôle à :

| Cible | Description |
| --- | --- |
| Utilisateur | donne le rôle directement à un compte |
| Groupe d’utilisateurs | tous les membres du groupe obtiennent le rôle |
| Service | les membres du service obtiennent le rôle, avec l’option « inclure les services subordonnés » |
| Poste | toute personne occupant ce poste obtient le rôle |

Chaque attribution peut avoir une **date de début** et une **date de fin** ; elle expire automatiquement à la date de fin, ce qui convient aux autorisations temporaires. Les utilisateurs peuvent aussi demander eux-mêmes des rôles limités dans le temps via [demande et validation](/fr/guide/access-requests/).

Les attributions et les autorisations sont soumises à la [séparation des tâches](/fr/guide/sod/) : une attribution qui donnerait à une personne des rôles mutuellement exclusifs est refusée.

## Autorisations sur les données et autorisations sur les champs

Les boutons **Autorisations sur les données** et **Autorisations sur les champs** dans la ligne du rôle déterminent quelles lignes les détenteurs peuvent voir, et comment ils voient et modifient quels champs ; voir [autorisations sur les données](/fr/guide/data-permissions/) et [autorisations sur les champs](/fr/guide/field-permissions/).

## Duplication et suppression

**Dupliquer** copie le rôle avec ses autorisations, ses autorisations sur les données et ses autorisations sur les champs. Supprimer un rôle supprime aussi ses attributions, ses autorisations et ses politiques ; cette action est irréversible.
