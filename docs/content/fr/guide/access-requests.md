---
title: Demandes d’accès et validations
description: Les utilisateurs demandent des rôles limités dans le temps ; les approbateurs les acceptent, les rejettent ou les révoquent par anticipation, et ils sont retirés automatiquement à l’échéance.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Administrateur : ouvrir les rôles pouvant être demandés

Dans **Contrôle d’accès → Validation des accès**, cliquez sur **Rôles demandables** et choisissez quels rôles personnalisés peuvent être demandés, ainsi que le nombre maximal de jours pour lesquels chaque rôle peut être demandé (1 à 365). Les rôles système ne peuvent pas être demandés.

## Utilisateur : demande

Chaque utilisateur connecté peut voir les rôles demandables dans **Espace de travail → Mes demandes**. Choisissez un rôle, renseignez le motif et le nombre de jours, puis soumettez ; vous pouvez retirer la demande avant la validation. Vous ne pouvez pas faire une nouvelle demande si vous détenez déjà ce rôle ou si une demande est déjà en attente de validation.

![Mes demandes](/screenshots/requests.png)

Un utilisateur nouvellement créé doit d’abord modifier son mot de passe initial pour utiliser cette page.

## Approbateur : accepter, rejeter, révoquer

**Contrôle d’accès → Validation des accès** répertorie les demandes en attente de validation, accordées ou l’ensemble des demandes.

![Validation des accès](/screenshots/access-approvals.png)

- **Accepter** : vous pouvez réduire le nombre de jours et saisir un commentaire. Après acceptation, l’utilisateur obtient immédiatement le rôle, qui expire automatiquement à l’échéance.
- **Rejeter** : vous pouvez saisir un motif.
- **Révoquer** : retire le rôle par anticipation à une demande déjà accordée.

Une demande acceptée équivaut à une attribution de rôle par l’approbateur ; elle suit donc les mêmes règles :

- on ne peut pas accorder un rôle au-delà des autorisations de l’approbateur lui-même ;
- la [séparation des tâches](/fr/guide/sod/) est respectée ;
- on ne peut pas valider sa propre demande ;
- pour un approbateur ayant activé l’authentification à deux facteurs, une vérification doit avoir été effectuée dans les 10 dernières minutes.

## Retrait à l’échéance

Les rôles accordés expirent immédiatement à la date de fin. En arrière-plan, les attributions expirées sont nettoyées toutes les 5 minutes et la demande est marquée comme « expirée ». L’ensemble du processus (demande, retrait, acceptation, rejet, révocation, expiration) est enregistré dans le journal d’audit.
