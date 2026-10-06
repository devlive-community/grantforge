---
title: 2026.0.0 (reconstruction)
description: "GrantForge réécrit à partir de zéro : identité multi-locataires, autorisations sur les ressources et les rôles, autorisations sur les données et les champs, intégration par protocoles standards, gouvernance d’entreprise et autorisations sur les systèmes externes par plug-in."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

2026.0.0 est une réécriture complète qui succède à 1.x (AuthX). Ce n’est plus un gabarit d’administration, mais une plateforme d’identité et d’autorisations autonome. Les comptes, rôles et menus de 1.x peuvent être importés, voir [Mise à niveau et migration depuis les anciennes versions](/fr/deploy/upgrade/).

## Plateforme

- Spring Boot 4 et environnement d’exécution Java 17 ; une seule version publiée contient le serveur et la console, des images Docker, Compose et un Helm Chart étant fournis en complément.
- Prise en charge de H2, PostgreSQL, MySQL, MariaDB, Oracle et SQL Server ; les migrations sont gérées par Liquibase et chaque commit est testé sur neuf versions de bases de données.
- Assistant d’initialisation au premier démarrage, qui crée l’administrateur de la plateforme à l’aide d’un jeton à usage unique figurant dans les journaux du service.
- Déploiement en grappe : les sessions sont stockées dans la base de données et les identifiants sont des TSID ordonnés dans le temps.
- Console entièrement nouvelle : Vue 3 et Tailwind CSS, thèmes clair et sombre, chinois et anglais.

## Identité et organisation

- Multi-locataires : les comptes, l’organisation et les autorisations de chaque locataire sont entièrement isolés.
- Utilisateurs, arborescence des services, groupes et postes, avec import et export CSV en lot.
- Mots de passe Argon2id, politique de mot de passe et verrouillage configurables, gestion des sessions, authentification à deux facteurs TOTP avec codes de récupération et revérification des opérations sensibles.
- Sources d’identité LDAP / Active Directory et OIDC, avec synchronisation et connexion fédérée.

## Autorisations

- Catalogue de ressources : modules, menus, pages, onglets, boutons, API, entités et champs de données, ainsi que les dépendances entre eux. Les pages, boutons et API de la console elle-même figurent également dans le catalogue et sont soumis aux mêmes autorisations.
- Rôles et autorisations : autoriser et refuser, héritage, attribution par utilisateur / groupe / service / poste, durée de validité.
- Autorisations sur les données : limiter les lignes visibles selon le périmètre de l’organisation ou une condition structurée.
- Autorisations sur les champs : masquer, caviarder ou mettre en lecture seule les champs selon le rôle.
- Explication des autorisations, simulation par utilisateur, journal d’audit complet et vérification des configurations invalides.

## Gouvernance

- Séparation des tâches : les rôles mutuellement exclusifs sont refusés lors de l’attribution, de l’héritage et des demandes, et les conflits déjà existants peuvent être découverts.
- Demandes d’accès : les utilisateurs demandent les rôles pouvant faire l’objet d’une demande ; après validation par l’approbateur, ceux-ci prennent effet pour une durée limitée et sont révoqués automatiquement à l’expiration.
- Revue périodique des accès.

## Intégration des applications

- Serveur d’autorisation OAuth 2.1 / OpenID Connect intégré : code d’autorisation + PKCE, identifiants client, rotation des jetons de rafraîchissement et des clés de signature.
- API ouverte de consultation des autorisations, avec versionnage et ETag.
- Spring Boot Starter et SDK JavaScript, accompagnés d’applications d’exemple exécutables.

## Autorisations sur les systèmes externes

- Types de service par plug-in : les plug-ins définissent la hiérarchie des ressources, les types d’accès, le caviardage et le filtrage des lignes ; chaque plug-in est chargé indépendamment.
- Éditeur de politiques universel, instantanés de politique signés avec Ed25519, heartbeats des agents et audit des accès.
- Plug-in d’exemple, type de service HDFS et agent NameNode Hadoop 3.5.0 ; le plug-in Hive et les agents pour d’autres versions de Hadoop sont en cours de développement.

## Qualité

- Des benchmarks de performance à l’échelle d’un million de comptes s’exécutent chaque nuit et échouent dès qu’une limite est dépassée.
- Analyse statique (NullAway, Error Prone, Checkstyle, PMD, SpotBugs, ArchUnit), seuils de couverture et tests navigateur de bout en bout.
- Le site de documentation a été reconstruit avec Next.js et Tailwind CSS.
