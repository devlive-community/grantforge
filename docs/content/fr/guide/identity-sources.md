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
| Revendication du téléphone | lit `phone` par défaut (Casdoor) ; pour d’autres fournisseurs, indiquez `phone_number`. Pour synchroniser le numéro de téléphone, le scope doit inclure `phone`. Si le fournisseur ne renvoie aucun numéro à la connexion, le numéro existant est conservé |
| Adresse de l’avatar | synchronisée depuis la revendication OIDC standard `picture` ; le scope doit inclure `profile`. Sans valeur renvoyée, l’adresse existante est conservée |
| Paramètres supplémentaires de la requête d’autorisation | objet JSON facultatif dont les valeurs doivent être des chaînes. Avec Casdoor, par exemple, `{"provider_hint":"github"}` désigne un fournisseur configuré. Les paramètres apparaissent dans l’adresse du navigateur : n’y mettez jamais de secret |

Les paramètres essentiels du protocole (dont `state`, `nonce`, `redirect_uri` et ceux de PKCE) sont gérés par GrantForge ; les paramètres supplémentaires ne peuvent pas les remplacer.

Une fois l’option activée, la page de connexion affiche un bouton « Se connecter avec <nom> ». L’utilisateur se connecte chez le fournisseur puis revient sur GrantForge ; les comptes ayant activé l’authentification à deux facteurs doivent en plus saisir un code.

Avec `provider=dingtalk` dans `https://auth.example.com/#/auth/login?provider=dingtalk`, la connexion de la source OIDC activée s’ouvre directement. La valeur est le code de la source dans GrantForge. `redirect` indique une page de la console, par exemple `#/auth/login?provider=dingtalk&redirect=%2Fadmin%2Fusers`. En cas d’échec, la connexion normale réapparaît ; les sessions et la vérification en deux étapes restent inchangées.

Un client OAuth peut aussi ajouter `provider=dingtalk` à `/oauth2/authorize`. La requête complète, avec `state`, `nonce` et PKCE, est conservée. Le `provider` de la page de connexion est prioritaire.

## Règles relatives aux comptes

- L’identifiant unique d’un utilisateur issu d’une source d’identité (`entryUUID`/`objectGUID` pour LDAP, `sub` pour OIDC) correspond à un compte GrantForge ; un changement de nom ne modifie pas cette correspondance.
- Lorsqu’un compte local porte déjà le même nom, l’association **n’est pas effectuée automatiquement** et la connexion est refusée ; un administrateur doit d’abord renommer ou supprimer le compte local. Cela évite qu’un utilisateur homonyme de l’annuaire prenne le contrôle d’un compte existant.
- Une source d’identité qui ne crée pas automatiquement de comptes ne permet de se connecter qu’aux utilisateurs déjà associés.
- Après la désactivation d’une source d’identité, ses utilisateurs ne peuvent plus se connecter ; une source d’identité encore utilisée par des comptes ne peut pas être supprimée.
- Les comptes issus d’une source d’identité peuvent recevoir des rôles et activer l’authentification à deux facteurs comme les autres.

La synchronisation de l’organisation se configure dans une source de données distincte, sans modifier les réglages de connexion OIDC.

## Sources de données et synchronisation de l’organisation

Les sources de données synchronisent l’organisation, tandis que les sources d’identité continuent d’assurer la connexion OIDC ou LDAP. Les deux se configurent séparément : désactiver une source de données n’affecte pas la connexion OIDC.

1. Configurez la connexion OpenID Connect existante dans « Contrôle d’accès → Sources d’identité ».
2. Dans « Contrôle d’accès → Sources de données », ajoutez une source DingTalk et choisissez la source d’identité OIDC associée.
3. Saisissez CorpId, AppKey, AppSecret et l’ID du département racine (généralement `1`). L’AppSecret est chiffré à part, l’API ne le renvoie jamais en clair, et un champ laissé vide lors d’une modification conserve la valeur existante.
4. La source peut être montée sous un département local. L’intervalle de synchronisation est de 30 minutes par défaut et peut aller de 15 à 10080 minutes ; un champ vide signifie une synchronisation manuelle uniquement.
5. Après l’enregistrement, testez la connexion et lancez la première synchronisation. L’application DingTalk doit pouvoir lire les départements et l’annuaire des employés, et son périmètre d’autorisation doit couvrir le sous-arbre choisi.
6. Quand un utilisateur se connecte par SSO, l’employé est retrouvé par l’adresse e-mail OIDC de confiance, et ses départements ainsi que son département principal sont renseignés automatiquement. L’adresse doit être gérée par un administrateur, ne pas permettre à un employé d’usurper une autre identité et correspondre à l’adresse DingTalk. Sans adresse d’employé, l’adresse d’entreprise est utilisée.

La connexion lit uniquement l’instantané local de l’annuaire et n’interroge pas DingTalk. Avec la MFA, l’appartenance est mise à jour après la réussite de la seconde étape. Les comptes connectés avant la première synchronisation conservent l’adresse de confiance et sont associés une fois la synchronisation terminée ; l’adresse du profil, modifiable localement, ne sert jamais à la correspondance. Une adresse absente, en double ou déjà liée à un autre compte ne crée pas de nouvelle liaison.

La liaison à un employé repose sur son ID externe et ne passe pas à un autre employé quand l’adresse change. La synchronisation gère l’appartenance à plusieurs départements ; le département principal reste l’ancien s’il est toujours valide, sinon il est choisi dans l’ordre stable des ID de département. Les départements locaux, groupes, postes et attributions directes de rôles sont conservés. Si un rôle est attribué à un département synchronisé, les utilisateurs en héritent selon les règles existantes, et une mutation met leurs autorisations à jour.

Dès qu’un instantané de l’organisation ou des comptes en attente d’association existent, la source d’identité associée, l’entreprise, l’application, le département racine et le point de montage sont figés. Les départements et appartenances synchronisés sont en lecture seule et ne peuvent pas être remplacés à la main dans la gestion des utilisateurs ou de l’organisation. Les départements disparus sont conservés pour protéger les autorisations existantes ; quand un employé quitte l’annuaire autorisé, les appartenances gérées par cette source sont retirées sans que son compte soit désactivé automatiquement. Une source de données qui possède des départements ne peut pas être supprimée directement, mais elle peut être désactivée.

La synchronisation récupère toutes les pages avant d’appliquer le résultat de façon atomique. En cas d’échec de lecture, de hiérarchie anormale ou de changement du périmètre d’autorisation, le dernier instantané réussi est conservé ; les administrateurs vérifient le résultat grâce au résumé de synchronisation, à la liste des anomalies et au journal d’audit. Si le périmètre a changé, rétablissez d’abord le périmètre approuvé, puis relancez la synchronisation.

L’accès passe par le [SDK serveur officiel de DingTalk](https://open.dingtalk.com/document/development/download-the-server-side-sdk), sans appels HTTP écrits à la main. Les adaptateurs de fournisseur produisent des instantanés neutres des départements et des employés, si bien qu’un futur adaptateur Feishu pourra réutiliser la même logique de synchronisation, de liaison et de mise à jour des autorisations.
