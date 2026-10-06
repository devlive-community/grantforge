---
title: Présentation du produit
description: Ce qu’est GrantForge, les problèmes qu’il résout et ses différences avec les solutions courantes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge est une plateforme d’autorisations unifiée open source (MIT). Elle centralise « qui peut faire quoi, et quelles données il peut voir » : vous gérez les utilisateurs et l’organisation dans la console, définissez des rôles et accordez à ces rôles des menus, des boutons, des API, des lignes de données et des champs ; votre application connecte, elle, les utilisateurs via OAuth 2.1 / OpenID Connect, puis détermine les autorisations via l’API ouverte ou un SDK.

![Vue d’ensemble de la console GrantForge](/screenshots/dashboard.png)

## Les problèmes qu’il résout

Quand un système atteint une certaine taille, les autorisations se dispersent de tous côtés : les menus vivent dans la configuration du frontend, chaque interface fait ses propres vérifications par annotations, et le périmètre des données repose sur des conditions écrites à la main dans le SQL. En cas de départ ou de changement de poste, personne ne sait dire exactement ce qu’une personne peut encore faire. GrantForge réunit tout cela dans un modèle unique :

- **Définir une fois, appliquer partout** : les pages de la console, les boutons, les interfaces REST, les entités de données et les champs sont autant de « ressources » ; les rôles reçoivent des autorisations sur ces ressources, et une même autorisation pilote à la fois l’affichage côté frontend et le blocage côté backend.
- **Des autorisations visibles** : à tout moment vous pouvez répondre à « pourquoi cette personne voit-elle cette page » et « qui sera affecté si je modifie ce rôle » ; chaque changement d’autorisation s’accompagne d’un aperçu et d’une trace d’audit.
- **Conforme aux exigences de gouvernance** : séparation des tâches, demandes d’accès limitées dans le temps, revue périodique et authentification à deux facteurs répondent aux exigences courantes de la MLPS (protection classifiée) et des audits de contrôle interne.
- **Intégration par protocoles standard** : l’application n’a pas besoin d’embarquer son propre système d’utilisateurs ; il suffit de connecter les utilisateurs par OIDC et d’interroger les autorisations avec le jeton d’accès.

## Aperçu des fonctionnalités

| Domaine | Fonctionnalités |
| --- | --- |
| Identité et organisation | Multi-locataires, arborescence des services, groupes d’utilisateurs, postes ; import et export en masse par CSV ; connexion et synchronisation LDAP/AD, connexion fédérée OIDC |
| Sécurité des comptes | Gestion des sessions, politique et verrouillage des mots de passe, authentification à deux facteurs TOTP et codes de récupération, seconde vérification des opérations sensibles |
| Autorisations fonctionnelles | Catalogue de ressources (modules, menus, pages, onglets, boutons, API), héritage des rôles, matrice d’autorisations, analyse d’impact |
| Autorisations sur les données | Restriction des lignes visibles par condition (ses propres enregistrements, son service et ses services subordonnés, des services désignés, une condition personnalisée), lecture et écriture étant contrôlées séparément |
| Autorisations sur les champs | Masquer, caviarder (adresse e-mail, numéro de téléphone, numéro de pièce d’identité, etc.) ou passer des champs en lecture seule |
| Explicabilité et audit | Explication des autorisations, simulation d’autorisations, consultation et export du journal d’audit |
| Gouvernance | Contraintes de séparation des tâches, demandes d’accès et validation, revue périodique des autorisations |
| Intégration applicative | Serveur d’autorisation OAuth 2.1 / OIDC, API ouverte d’interrogation des autorisations, SDK Java (Spring Boot) et JavaScript |
| Systèmes externes | Types de service en plug-in et moteur de politiques (semblable à Apache Ranger), services de données, politiques d’accès et agents |
| Livraison | Version publiée unique, image Docker, exemples Compose, Helm Chart ; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

## Différences avec les solutions courantes

> [!NOTE]
> GrantForge n’est ni une bibliothèque qui ne ferait que du RBAC, ni un IdP qui ne ferait que de l’authentification unique. Il réunit identité, autorisations et gouvernance dans un même modèle et rend chaque autorisation explicable.

- **Par rapport aux autorisations écrites à la main dans le code** : les règles d’autorisation se maintiennent dans la console, aussi modifier une autorisation ne demande pas de publier l’application ; vous voyez l’impact avant l’autorisation et disposez d’une trace d’audit après.
- **Par rapport aux IdP cantonnés à l’authentification (Keycloak et consorts)** : GrantForge intègre un modèle d’autorisations d’une granularité fine, jusqu’au bouton, à la ligne de données et au champ, ainsi que des fonctions de gouvernance comme la séparation des tâches et les revues périodiques ; il peut aussi servir lui-même d’IdP, ou fédérer un LDAP ou un OIDC existant comme source d’identité.
- **Par rapport à Apache Ranger** : GrantForge emprunte à Ranger l’architecture des types de service, des politiques et des agents pour gérer les autorisations des systèmes de données externes ; mais il est d’abord une plateforme d’autorisations pour les applications métier.

## Prochaines étapes

- [Démarrage en cinq minutes](/fr/start/quick-start/) : téléchargement, démarrage, initialisation et attribution du premier rôle.
- [Concepts de base](/fr/start/concepts/) : les relations entre ressources, rôles, autorisations, affectations et évaluation.
- [Vue d’ensemble de l’intégration applicative](/fr/integration/overview/) : faire utiliser GrantForge par votre application.
