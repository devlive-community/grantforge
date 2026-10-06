---
title: Visite de la console
description: Disposition de la console, groupes de menus, et pourquoi les menus varient d’une personne à l’autre.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

![Vue d’ensemble de l’espace de travail](/screenshots/dashboard.png)

## Disposition

- **Le menu de gauche** est organisé par groupes : Espace de travail, Contrôle d’accès, Autorisations sur les données, Administration de la plateforme.
- **La barre supérieure** offre un accès rapide (⌘K ou Ctrl+K, recherche de pages par nom), le sélecteur de langue, le thème clair/sombre et le menu personnel.
- **La page Vue d’ensemble** affiche le nombre de comptes, de services, de groupes d’utilisateurs et de postes du locataire actuel, ainsi que les membres de l’espace de travail et les étapes de prise en main.

## Groupes de menus

| Groupe | Menus | Description |
| --- | --- | --- |
| Espace de travail | Vue d’ensemble, Mes demandes | Visible par tout utilisateur connecté |
| Contrôle d’accès | Gestion des utilisateurs, Organigramme, Groupes d’utilisateurs, Postes, Import et export, Gestion des rôles, Séparation des tâches, Validation des accès, Revue des accès, Sessions en ligne, Sources d’identité, Journaux d’audit | Identité et autorisations de ce locataire |
| Autorisations sur les données | Services de données, Politiques, Agents, Audit des accès | Autorisations sur les systèmes de données externes (HDFS, Hive, etc.) ; voir [Services de données, politiques et agents](/fr/external/data-services/) |
| Administration de la plateforme | Gestion des locataires, Catalogue de ressources, Catalogue d’API, Serveur d’autorisation, Examen du catalogue, Plug-ins | Disponible uniquement dans le locataire plateforme |

## Pourquoi mon menu diffère-t-il de celui des autres

La console est elle-même une application gérée par GrantForge : chaque page et chaque bouton est une ressource du catalogue de ressources, et ce sont uniquement vos rôles qui déterminent les menus et les boutons que vous voyez.

- Quiconque détient le rôle système **administrateur du locataire** voit tous les menus « Contrôle d’accès » et « Autorisations sur les données » de ce locataire.
- Quiconque détient le rôle système **administrateur de la plateforme** voit en plus « Administration de la plateforme ».
- Les autres ne voient que les pages autorisées par leurs rôles ; lorsqu’un groupe ne contient aucune page, le groupe entier ne s’affiche pas.

> [!NOTE]
> Les menus masqués ne sont qu’un confort. Le serveur revérifie les autorisations à chaque appel d’API : même en saisissant directement l’adresse, une opération pour laquelle vous n’avez pas l’autorisation est refusée.

Après un changement d’autorisation, aucune reconnexion n’est nécessaire : chaque réponse porte le numéro de version des autorisations en vigueur et, lorsque cette version change, la console recharge automatiquement le menu.
