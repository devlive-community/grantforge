---
title: Revue périodique des accès
description: Passez en revue périodiquement qui détient quels rôles ; les réviseurs conservent ou révoquent élément par élément, et les attributions révoquées sont retirées à la fin de chaque tour.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Contrôle d’accès → Revue des accès** : passez en revue périodiquement qui détient quels rôles. Les réviseurs décident élément par élément de conserver ou de révoquer, et les attributions révoquées sont retirées lorsque le tour se termine.

![Revue des accès](/screenshots/access-reviews.png)

## Plans de revue

| Réglage | Description |
| --- | --- |
| Nom, description | par exemple « Revue trimestrielle des rôles financiers » |
| Rôles | les rôles à passer en revue ; chaque tour liste l’ensemble de leurs attributions actuelles |
| Durée d’un tour | 1 à 90 jours ; le tour se termine automatiquement à l’échéance |
| Intervalle de répétition | laissez vide pour ne démarrer les tours que manuellement ; sinon le tour suivant démarre automatiquement à l’intervalle |
| Éléments non passés en revue | éléments sans décision à la fin du tour : **conserver** ou **révoquer** |
| Actif | contrôle uniquement si les tours démarrent automatiquement selon le calendrier |

## Un tour de revue

1. Cliquez sur **Démarrer maintenant**, ou attendez que le plan démarre un tour automatiquement. GrantForge crée un élément de revue par attribution (sauf pour les rôles système des comptes système).
2. Pendant le tour, les réviseurs choisissent **Conserver** ou **Révoquer** élément par élément ou par lot ; une révocation peut être accompagnée d’une note, et les décisions peuvent être retirées avant la fin du tour.
3. Vous ne pouvez pas passer en revue les rôles que vous détenez vous-même par attribution directe, via un groupe d’utilisateurs, un service ou un poste.
4. Un administrateur peut **Terminer le tour** (appliquer toutes les décisions, les éléments sans décision étant traités selon les réglages du plan) ou **Annuler le tour** (aucune modification n’est apportée). Les tours non terminés à l’échéance se terminent automatiquement.

Les attributions révoquées sont supprimées lorsque le tour se termine. Chaque étape est enregistrée dans le journal d’audit, ce qui fournit une traçabilité pour les audits de contrôle interne.
