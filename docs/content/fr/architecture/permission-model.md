---
title: Modèle d’autorisations
description: Sémantique exacte des ressources, de la dérivation des autorisations, de l’héritage, des affectations, des règles de données et de champs, ainsi que de l’instantané d’autorisations et de la version.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Ce document décrit les règles exactes de l’évaluation. Pour une introduction aux concepts, voir [Concepts de base](/fr/start/concepts/).

## Arbre des ressources et types

Les ressources appartiennent à une application et sont organisées en arbre d’au plus 15 niveaux. Le type détermine où elles peuvent se trouver :

| Type | Parents autorisés |
| --- | --- |
| Module | Racine, module |
| Menu, page | Racine, module, menu |
| Étiquette | Page, étiquette |
| Bouton | Page, étiquette |
| API, entité de données | Racine, module |
| Champ | Entité de données |

Entre ressources, des dépendances peuvent être déclarées : « requis » (la ressource dépendante est accordée en même temps que la ressource) ou « facultative » (simple information pour l’administrateur, pas d’octroi automatique) ; les dépendances ne peuvent pas former de cycle. Les pages, boutons et API de la console, ainsi que les API dont ils ont besoin, sont déclarés par la liste de permissions du frontend et synchronisés en ressources intégrées au démarrage du serveur.

## Dérivation des autorisations

Pour un compte, l’évaluateur détermine d’abord les **rôles effectifs** :

1. les rôles affectés directement au compte ;
2. les rôles affectés aux groupes, services (y compris les affectations des services parents « subordonnés inclus ») et postes occupés par le compte ;
3. seules les affectations dont la période de validité couvre l’instant présent, et les rôles activés, sont retenues ;
4. développement de l’héritage : tous les rôles ancêtres d’un rôle (un ancêtre désactivé ne transmet pas).

Puis il fusionne les autorisations de ces rôles :

- Autoriser une ressource, c’est l’autoriser elle-même, autoriser ses ancêtres dans l’arbre (pour la rendre visible), ainsi que ses dépendances « requises » (récursivement).
- Refuser une ressource s’applique à elle et à tous ses descendants, et **prévaut sur toute autorisation**.
- Une ressource désactivée et ses descendants ne prennent pas effet.
- Un rôle système équivaut à autoriser tout le sous-arbre de son module (par exemple `system`, `data`, `platform`).

Le résultat est l’ensemble des ressources disponibles, ainsi que les codes de permission correspondant aux ressources d’API qu’il contient.

## Garde-fous contre l’élévation de privilèges

- Pour accorder une « autorisation », celui qui accorde doit pouvoir lui-même utiliser la ressource (les porteurs d’un rôle système échappent à cette limite pour les applications métier).
- Lors de l’affectation d’un rôle ou du réglage d’un héritage, celui qui accorde doit couvrir toutes les ressources que le rôle couvre dans chaque application.
- Le rôle d’administrateur de plateforme ne peut être affecté, modifié ou retiré que par son détenteur actuel.
- Le « refus » n’est soumis à aucune limite : toute personne disposant du droit d’autorisation peut restreindre les accès.

## Règles de données

Les politiques de données appartiennent aux rôles et définissent les portées selon « entité × action × effet » : `ALL` (locataire plateforme uniquement), `TENANT`, `ORG_AND_CHILDREN`, `ORG`, `CUSTOM_ORGS`, `SELF`, `CONDITION`. Une ligne de données est accessible si et seulement si elle satisfait au moins une règle d’autorisation et aucune règle de refus.

Les conditions sont du JSON structuré, non exécutable, validées avant enregistrement en base :

```json
{ "and": [
  { "field": "status", "op": "eq", "value": "ACTIVE" },
  { "not": { "field": "orgUnitId", "op": "in", "value": { "var": "subject.orgUnitIds" } } }
] }
```

Les variables sont limitées à `subject.id`, `subject.username`, `subject.orgUnitIds`, `subject.groupCodes`, `subject.positionCodes` et `now` ; les opérateurs de comparaison sont restreints selon le type du champ ; l’imbrication ne dépasse pas 5 niveaux et 50 nœuds. Le serveur traduit les règles en `Specification` JPA, et les SDK appliquent la même sémantique dans les applications métier.

## Règles de champs

Les politiques de champs définissent le mode de lecture (visible, caviardé, masqué) et le mode d’écriture (modifiable, lecture seule) ; lorsque plusieurs rôles s’appliquent, le réglage le plus permissif est retenu, et en l’absence de tout réglage le champ est entièrement ouvert. Les règles de lecture s’appliquent à la sérialisation JSON (un même DTO se présente différemment selon les personnes), les règles d’écriture à la couche de service : la modification d’un champ en lecture seule renvoie `GF-FIELD-001` et désigne le champ.

## Instantanés et version

Le résultat de l’évaluation est un **instantané d’autorisations** : ressources disponibles, codes de permission, règles de données et règles de champs. L’instantané est mis en cache par compte ; tout changement d’autorisation, d’affectation, d’héritage, de catalogue de ressources, de politique ou de relation d’appartenance élève le numéro de version de la portée concernée (catalogue ou locataire) et invalide le cache. Chaque point d’entrée qui exige une permission renvoie la version courante dans l’en-tête de réponse `X-Authorization-Version`, ce qui permet à la console de décider s’il faut recharger les menus ; l’API ouverte exprime la même version avec un ETag.
