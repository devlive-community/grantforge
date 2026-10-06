---
title: Multi-locataires et isolation des données
description: Comment les locataires isolent leurs données, quelles données sont partagées au niveau de la plateforme et quelles contraintes s’appliquent aux opérations entre locataires.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Mode d’isolation

À l’exception des données partagées au niveau de la plateforme, chaque table métier comporte une colonne `tenant_id`. À l’entrée d’une requête, la chaîne de filtres lie au thread courant le locataire du compte connecté ; le filtre de locataire d’Hibernate ajoute automatiquement la condition de locataire aux requêtes et remplit `tenant_id` à l’écriture. Le code métier ne peut pas « oublier » la condition de locataire.

Les rares scénarios qui doivent franchir la frontière entre locataires (par exemple rechercher un compte par nom d’utilisateur lors de la connexion, compter les comptes de chaque locataire, les tâches planifiées en arrière-plan) doivent entrer explicitement en « contexte système » ; une revue de code peut les repérer immédiatement.

## Données partagées et données isolées

| Partagé au niveau de la plateforme | Propre à chaque locataire |
| --- | --- |
| Applications et catalogue de ressources, catalogue d’API, clients OAuth, plug-ins | Comptes, services, groupes, postes, rôles, autorisations, affectations, politiques de données et de champs, séparation des tâches, demandes et revues, sources d’identité, services de données, audit |

Les noms d’utilisateur sont uniques sur l’ensemble de la plateforme : il n’est donc pas nécessaire de choisir un locataire lors de la connexion.

## Locataire plateforme

Le locataire plateforme est créé lors de l’initialisation et ne peut pas être désactivé. Ses administrateurs gèrent les données partagées au niveau de la plateforme ainsi que les autres locataires ; seuls les rôles du locataire plateforme peuvent utiliser la portée de données « tous les locataires ».

## ID

Toutes les clés primaires sont des TSID : 64 bits, ordonnées dans le temps et uniques dans le cluster grâce au numéro de nœud. Elles dépassent ce que JavaScript peut représenter exactement et sont donc toujours transmises sous forme de chaîne en JSON. Chaque instance du cluster a besoin d’un `GRANTFORGE_ID_NODE` distinct.
