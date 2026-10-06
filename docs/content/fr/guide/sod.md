---
title: Séparation des tâches
description: Configurez les rôles qui ne peuvent pas être détenus simultanément par la même personne, imposez un refus ou un simple signalement, et consultez les conflits actuels.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Contrôle d’accès → Séparation des tâches** : configurez les rôles qui ne peuvent pas être détenus simultanément par la même personne (par exemple paiement et approbation) et consultez les comptes qui violent actuellement les contraintes.

![Séparation des tâches](/screenshots/sod.png)

## Contraintes

| Réglage | Description |
| --- | --- |
| Rôles mutuellement exclusifs | 2 à 50 rôles |
| Détention maximale par compte | 1 par défaut, peut être défini sur « au plus deux sur trois » |
| Mode | **Forcé** : refuse les attributions et les héritages de rôles qui créeraient un conflit ; **signalement seul** : autorise le changement et l’affiche seulement dans la liste des conflits |
| Actif | une contrainte désactivée ne refuse ni ne signale rien |

La « détention » recouvre toutes les voies : attribution directe, obtention via un groupe d’utilisateurs, un service ou un poste, et obtention par héritage de rôles. Les attributions hors de leur période de validité et les rôles désactivés ne comptent pas.

## Moment du contrôle

En mode forcé, les changements suivants vérifient, avant l’enregistrement, chaque compte qu’ils touchent :

- attribution d’un rôle, ou modification de la période de validité d’une attribution ou de l’inclusion des services subordonnés ;
- modification des relations d’héritage d’un rôle ;
- validation des demandes d’accès (voir [demandes d’accès et validations](/fr/guide/access-requests/)).

Seuls les conflits **créés par ce changement** sont refusés, avec indication de la personne concernée, des rôles et de la contrainte violée ; les conflits qui existaient déjà avant l’entrée en vigueur de la contrainte ne bloquent pas d’autres changements sans lien avec eux : ils restent affichés dans la liste des conflits et nécessitent un traitement manuel.

> [!WARNING]
> Ce contrôle n’est pas effectué lorsqu’une personne est ajoutée à un groupe d’utilisateurs, à un service ou à un poste. Les conflits créés par ces voies apparaissent dans la liste des conflits ; consultez-la régulièrement.

## Liste des conflits

La liste de droite répertorie tous les comptes qui violent des contraintes actives (quel que soit le mode) : compte, contrainte, rôles détenus et limite autorisée.
