---
title: Locataires
description: Créer, modifier, désactiver et réactiver des locataires.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Administration de la plateforme → Gestion des locataires** : chaque locataire est une organisation isolée des autres, avec ses propres utilisateurs et ses propres autorisations. Le locataire plateforme porte les administrateurs de la plateforme et ne peut pas être désactivé.

![Gestion des locataires](/screenshots/tenants.png)

## Créer un locataire

Renseignez le code du locataire, son nom, ainsi que le nom d’utilisateur, le nom d’affichage et le mot de passe de son premier administrateur. Ce premier administrateur détient le rôle système « Administrateur du locataire » et doit modifier son mot de passe à sa première connexion. Le nom d’utilisateur est unique sur l’ensemble de la plateforme.

## Désactiver et réactiver

Après la désactivation d’un locataire, tous ses comptes sont immédiatement déconnectés et ne peuvent plus se connecter ; les jetons déjà émis pour les applications ne sont plus renouvelés. Les données ne sont pas supprimées : il suffit de réactiver le locataire pour les retrouver.

## Locataire plateforme

Le locataire plateforme est le premier locataire, créé lors de l’initialisation. Ses administrateurs peuvent gérer l’ensemble des locataires, le [catalogue des ressources et des API](/fr/guide/catalog/), le serveur d’autorisation et les plug-ins. Les applications métier et leurs ressources sont partagées par toute la plateforme ; chaque locataire les autorise dans ses propres rôles.
