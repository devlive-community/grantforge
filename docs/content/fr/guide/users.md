---
title: Utilisateurs
description: Créer, rechercher, modifier, désactiver, verrouiller et supprimer des comptes, réinitialiser le mot de passe et l’authentification à deux facteurs, consulter les autorisations effectives.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Contrôle d’accès → Gestion des utilisateurs** gère les comptes de l’organisation : service de rattachement, possibilité de se connecter et réinitialisation du mot de passe.

![Gestion des utilisateurs](/screenshots/users.png)

## Rechercher

Recherchez par nom d’utilisateur, nom d’affichage ou adresse e-mail, filtrez par état (normal, désactivé, verrouillé, changement de mot de passe en attente) et par service, et choisissez d’inclure ou non les services subordonnés. La liste n’affiche que les comptes que vos autorisations sur les données vous permettent de voir ; certains champs, comme l’adresse e-mail, peuvent être cachés ou caviardés selon vos autorisations sur les champs.

## Créer et modifier

« Créer un utilisateur » exige de renseigner le nom d’utilisateur (3 à 64 lettres, chiffres ou signes `._@-`, unique sur toute la plateforme), le mot de passe initial, le nom d’affichage, l’adresse e-mail, le service principal, les services secondaires et les postes. Un nouveau compte doit modifier son mot de passe à sa première connexion.

## Opérations sur les comptes

| Opération | Effet |
| --- | --- |
| Désactiver / Activer | Après désactivation, la connexion est impossible et les sessions existantes prennent fin immédiatement |
| Verrouiller / Déverrouiller | Un verrouillage posé par un administrateur ne se lève pas automatiquement ; à la connexion, il est demandé de contacter l’administrateur. À utiliser en cas de suspicion de compromission |
| Réinitialiser le mot de passe | Définit un nouveau mot de passe initial ; l’utilisateur devra le modifier à sa prochaine connexion et ses sessions existantes prennent fin |
| Réinitialiser l’authentification à deux facteurs | En cas de perte de l’authentificateur : désactive l’authentification à deux facteurs du compte et met fin à ses sessions |
| Voir les rôles | Les rôles détenus par le compte et l’origine de chacun (affectation directe, groupe d’utilisateurs, service, poste) |
| Voir les autorisations effectives | Voir [Explication, simulation et audit des autorisations](/fr/guide/explain/) |
| Supprimer | Supprime le compte et ses rattachements aux services ; les enregistrements d’audit sont conservés |

Le compte système (l’administrateur créé à l’initialisation) et votre propre compte ne peuvent être ni désactivés, ni verrouillés, ni supprimés.

## Inscription libre

Désactivée par défaut. Après avoir défini `grantforge.security.registration-enabled=true`, la page de connexion propose une entrée « Créer un compte » ; les comptes ainsi créés rejoignent le locataire désigné par `grantforge.security.registration-tenant` et sont des comptes ordinaires sans aucun rôle.
