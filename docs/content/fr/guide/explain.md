---
title: Expliquer, simuler et auditer les autorisations
description: Consultez ce qu’une personne peut utiliser et pourquoi, simulez des changements de rôles, interrogez et exportez les journaux d’audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Autorisations effectives

Dans **Gestion des utilisateurs**, cliquez sur « Voir les autorisations effectives » dans la ligne d’un utilisateur pour voir tout ce que cet utilisateur peut utiliser actuellement : rôles, menus et boutons, API, portée des données et champs restreints.

![Autorisations effectives](/screenshots/user-permissions.png)

Cliquez sur **Expliquer** sur n’importe quel élément : GrantForge répond à la question « pourquoi cela fonctionne-t-il » en indiquant par quel rôle, à travers quelles attributions (directe, groupe d’utilisateurs, service, poste) et quelles ressources déduites il l’obtient ; lorsque cela ne fonctionne pas, il précise s’il n’y a pas d’autorisation ou si elle est bloquée par une règle de refus.

## Simuler des changements

Ouvrez **Simuler des changements** dans les autorisations effectives : supposez l’ajout ou le retrait de certains rôles pour cet utilisateur et voyez quels menus, boutons et API il gagnerait ou perdrait. Une simulation effectue uniquement un calcul et n’enregistre rien ; c’est un bon moyen de vérifier l’effet avant d’ajuster les autorisations.

## Journal d’audit

**Contrôle d’accès → Journal d’audit** enregistre qui a fait quoi et quand : connexions, changements d’autorisations, opérations d’administration et appels refusés.

![Journal d’audit](/screenshots/audit.png)

- Filtrez par événement, résultat, opérateur, objet et date ; les résultats peuvent être exportés en CSV.
- Chaque événement comporte l’IP source, le navigateur et l’ID de requête ; l’ID de requête correspond aux journaux côté serveur.
- Seuls les événements que vos autorisations sur les données vous permettent de voir sont affichés.
- L’audit est conservé 365 jours par défaut (`grantforge.audit.retention`) ; une archive vers un répertoire avant la suppression peut être configurée.

Les événements enregistrés comprennent : connexions réussies et échouées, verrouillage, déconnexion, fin de session, modification de mot de passe, changements sur les locataires, services, utilisateurs, groupes d’utilisateurs et postes, changements sur les catalogues des ressources et des API, changements sur les rôles, autorisations, héritages et attributions, changements sur les politiques de données et de champs, chaque étape de la séparation des tâches, des demandes d’accès et des revues d’accès, changements sur les sources d’identité et les clients OAuth, ainsi que les appels d’API refusés.
