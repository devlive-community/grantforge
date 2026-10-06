---
title: Autorisations sur les champs
description: Masquez, caviardez ou mettez en lecture seule les champs contrôlés selon le rôle, par exemple l’e-mail et la date de dernière connexion de l’utilisateur.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Les autorisations sur les champs déterminent comment les détenteurs du rôle **voient et modifient** chaque champ contrôlé. Cliquez sur **Autorisations sur les champs** dans la ligne du rôle pour les configurer.

![Autorisations sur les champs](/screenshots/role-fields.png)

## Mode de consultation

| Mode | Effet |
| --- | --- |
| Visible | affiche la valeur d’origine |
| Caviardé | masque partiellement selon le mode de caviardage : e-mail (conserve l’initiale et le domaine), téléphone mobile (138\*\*\*5678), numéro de pièce d’identité (conserve les 6 premiers et les 4 derniers caractères), conserve les premiers et derniers caractères, masque entièrement |
| Caché | ne renvoie pas ce champ ; la colonne n’est pas affichée dans les listes |

## Mode de modification

| Mode | Effet |
| --- | --- |
| Modifiable | peut être renseigné et modifié |
| Lecture seule | désactivé dans le formulaire ; en cas de modification directe via l’API, une erreur est renvoyée qui indique de quel champ il s’agit |

## Règles de combinaison

- Un champ non configuré est déterminé par les autres rôles du détenteur ; si aucun rôle ne le configure, le champ est visible et modifiable.
- Lorsque plusieurs rôles configurent le même champ, c’est le **plus permissif** qui s’applique (visible > caviardé > caché, modifiable > lecture seule).
- La recherche et l’export respectent également les autorisations sur les champs : un champ caché ne peut pas servir à la recherche, et à l’export il est caché ou caviardé selon la règle.

## Champs contrôlés

Les champs contrôlés sont déclarés dans le code du serveur (actuellement l’e-mail de l’utilisateur et sa date de dernière connexion) et **Administration de la plateforme → Catalogue de ressources** indique dans quelles API ils apparaissent. Le contrôle des champs des applications métier peut être implémenté par l’application elle-même, et les règles sont également récupérées via l’API ouverte.
