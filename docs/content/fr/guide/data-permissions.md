---
title: Autorisations sur les données
description: "Déterminez quelles lignes de chaque type de données les rôles peuvent lire, modifier, supprimer et exporter : celles de la personne concernée, de son service, de services désignés ou selon une condition."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Les autorisations sur les données déterminent **quelles lignes** de chaque type de données les détenteurs du rôle peuvent lire, modifier, supprimer et exporter. Cliquez sur **Autorisations sur les données** dans la ligne du rôle pour les configurer.

![Autorisations sur les données](/screenshots/role-data.png)

## Règles

Chaque règle se compose de quatre parties :

| Partie | Options |
| --- | --- |
| Entité de données | utilisateurs, services, groupes d’utilisateurs, postes, événements d’audit, ainsi que les entités déclarées par les applications métier (par exemple `shop:order`) |
| Action | consulter, modifier, supprimer, exporter |
| Portée | tous les locataires (rôles du locataire de plateforme uniquement), tout le locataire courant, le service courant et ses services subordonnés, le service courant, des services désignés, uniquement la personne concernée, selon une condition |
| Effet | autoriser ou refuser |

Combinaison des règles :

- **Sans aucune règle d’autorisation, aucune donnée n’est visible.**
- **Le refus est prioritaire sur l’autorisation** : toute ligne correspondant à une règle de refus est indisponible.
- Les règles des différents rôles d’une personne s’appliquent ensemble : les autorisations sont réunies, et les refus également.
- Les rôles système impliquent la portée correspondante (l’administrateur du locataire porte sur tout le locataire), sauf pour les entités des applications métier.

## Selon une condition

Lorsque la portée est « selon une condition », utilisez l’éditeur de conditions pour combiner des conditions :

- Comparez les champs de l’entité, par exemple « statut égal à normal » ou « dernière connexion antérieure à l’heure actuelle ». Le texte prend en charge « contient » et « commence par » ; les nombres et les dates prennent en charge les comparaisons de grandeur ; « appartient à », « n’appartient pas à », « est vide » et « n’est pas vide » sont également pris en charge.
- La valeur peut être une valeur fixe ou un **attribut de l’utilisateur courant** : son ID, son nom d’utilisateur, son service, ses groupes d’utilisateurs, les postes qu’il occupe, ainsi que l’heure actuelle.
- Les conditions peuvent être groupées avec « toutes satisfaites / au moins une satisfaite », peuvent être inversées et peuvent être imbriquées jusqu’à 4 niveaux.

## Aperçu

Dans l’éditeur, sélectionnez un utilisateur et cliquez sur **Aperçu** pour voir combien de lignes les règles de ce rôle lui permettent de voir, et précisément lesquelles.

## Où cela s’applique

La console respecte les autorisations sur les données pour les listes et les détails des utilisateurs, des services, des groupes d’utilisateurs et des postes, pour l’import et l’export, ainsi que pour les journaux d’audit ; les lignes non visibles n’apparaissent pas dans les listes et un accès direct par ID renvoie « inexistant ». Les applications métier obtiennent les mêmes règles via le [Java SDK](/fr/integration/java/) ou l’[API ouverte](/fr/integration/open-api/).
