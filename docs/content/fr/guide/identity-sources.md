---
title: Sources d’identité (LDAP et OIDC)
description: Permettre aux utilisateurs de se connecter avec l’annuaire d’entreprise (LDAP/AD) ou un fournisseur OpenID Connect, en créant et synchronisant automatiquement les comptes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Contrôle d’accès → Sources d’identité** : configurez les annuaires ou les fournisseurs d’identité avec lesquels les utilisateurs peuvent se connecter. Le mot de passe de ces comptes est enregistré dans la source d’identité ; GrantForge ne l’enregistre pas et ne peut pas le modifier.

![Sources d’identité](/screenshots/identity-sources.png)

## LDAP / Active Directory

Cliquez sur « Ajouter une source d’identité » et choisissez le type « Annuaire LDAP » :

| Réglage | Description |
| --- | --- |
| Adresse de l’annuaire | `ldap://` ou `ldaps://`, plusieurs adresses séparées par des espaces pour permettre le basculement en cas de panne |
| Base DN des utilisateurs | par exemple `ou=people,dc=example,dc=com` |
| Compte de recherche | compte servant à rechercher les utilisateurs (Bind DN) et son mot de passe ; laissez vide pour une recherche anonyme |
| Filtre des utilisateurs | `(&(objectClass=person)(uid={0}))` par défaut, où `{0}` est le nom saisi par l’utilisateur |
| Attributs | attributs du nom d’utilisateur, du nom d’affichage, de l’e-mail et de l’identifiant unique ; adapté par défaut à OpenLDAP (`uid`, `cn`, `mail`, `entryUUID`), pour Active Directory renseignez `sAMAccountName` et `objectGUID` |
| Création automatique d’un compte pour les nouveaux utilisateurs | si l’option est activée, un compte est créé dès la première connexion d’un utilisateur de l’annuaire |
| Intervalle de synchronisation | laissez vide pour une synchronisation manuelle uniquement, 15 minutes au minimum |
| Désactivation des comptes des utilisateurs ayant quitté l’annuaire | lors de la synchronisation, désactiver les utilisateurs qui n’existent plus dans l’annuaire |

Après l’enregistrement, cliquez sur **Tester la connexion** pour vérifier que la configuration est correcte.

À la connexion, GrantForge cherche d’abord l’utilisateur avec le compte de recherche, puis lie l’annuaire avec l’identité de cet utilisateur et le mot de passe saisi afin de vérifier ce mot de passe.

La **synchronisation** crée des comptes pour les nouveaux utilisateurs de l’annuaire, met à jour le nom et l’e-mail des comptes existants et désactive les utilisateurs partis conformément aux réglages (en mettant fin à leurs sessions). Le résultat de la synchronisation s’affiche sur la carte de la source d’identité.

## OpenID Connect

Choisissez le type « OpenID Connect » pour intégrer Keycloak, Azure AD, Okta ou une autre instance GrantForge :

| Réglage | Description |
| --- | --- |
| Adresse de l’émetteur | adresse de l’émetteur du fournisseur ; GrantForge récupère ses points de terminaison et ses clés via son document de découverte |
| ID et secret du client | client enregistré auprès du fournisseur ; sans secret, il fonctionne comme client public avec PKCE |
| Adresse de rappel | l’adresse `<GrantForge>/api/v1/auth/federated/callback/<encodé>` affichée sur la page, à enregistrer dans le client côté fournisseur |
| Scopes et revendications | `openid profile email` par défaut ; le nom d’utilisateur, le nom d’affichage et l’e-mail sont lus respectivement dans les revendications `preferred_username`, `name` et `email` |

Une fois l’option activée, la page de connexion affiche un bouton « Se connecter avec <nom> ». L’utilisateur se connecte chez le fournisseur puis revient sur GrantForge ; les comptes ayant activé l’authentification à deux facteurs doivent en plus saisir un code.

## Règles relatives aux comptes

- L’identifiant unique d’un utilisateur issu d’une source d’identité (`entryUUID`/`objectGUID` pour LDAP, `sub` pour OIDC) correspond à un compte GrantForge ; un changement de nom ne modifie pas cette correspondance.
- Lorsqu’un compte local porte déjà le même nom, l’association **n’est pas effectuée automatiquement** et la connexion est refusée ; un administrateur doit d’abord renommer ou supprimer le compte local. Cela évite qu’un utilisateur homonyme de l’annuaire prenne le contrôle d’un compte existant.
- Une source d’identité qui ne crée pas automatiquement de comptes ne permet de se connecter qu’aux utilisateurs déjà associés.
- Après la désactivation d’une source d’identité, ses utilisateurs ne peuvent plus se connecter ; une source d’identité encore utilisée par des comptes ne peut pas être supprimée.
- Les comptes issus d’une source d’identité peuvent recevoir des rôles et activer l’authentification à deux facteurs comme les autres.
