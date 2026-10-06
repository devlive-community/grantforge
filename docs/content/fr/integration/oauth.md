---
title: OAuth 2.1 et OpenID Connect
description: Points de terminaison du serveur d’autorisation, types de clients, règles sur les jetons et clés de signature.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge intègre un serveur d’autorisation bâti sur Spring Authorization Server, conforme aux exigences de sécurité d’OAuth 2.1 : seuls le code d’autorisation (PKCE obligatoire), le jeton de rafraîchissement et les identifiants client sont pris en charge ; les flux implicite et mot de passe ne le sont pas.

![Serveur d’autorisation](/screenshots/oauth.png)

## Document de découverte et points de terminaison

Document de découverte : `<GrantForge>/.well-known/openid-configuration`, directement copiable depuis la page **Administration de la plateforme → Serveur d’autorisation**. Par défaut, l’émetteur est l’adresse par laquelle la requête arrive ; en cas de déploiement derrière un proxy inverse, fixez-le avec `grantforge.oauth.issuer`.

| Point de terminaison | Description |
| --- | --- |
| `/oauth2/authorize` | flux du code d’autorisation ; tous les clients doivent utiliser PKCE (S256) |
| `/oauth2/token` | code d’autorisation, jeton de rafraîchissement, identifiants client. Le jeton de rafraîchissement est renouvelé à chaque utilisation ; la réapparition d’un ancien jeton révoque toute l’autorisation |
| `/oauth2/revoke` | révoque un jeton |
| `/oauth2/jwks` | clés publiques de signature (RS256) |
| `/userinfo` | `sub`, `tid`, `preferred_username` ; le scope `profile` comporte `name`, le scope `email` comporte `email` |

Le jeton d’accès et le jeton d’ID contiennent `tid` (identifiant de locataire) et `preferred_username` ; `auth_time` dans le jeton d’ID est l’heure à laquelle l’utilisateur s’est connecté à la console.

## Clients

Dans **Administration de la plateforme → Catalogue de ressources**, sélectionnez une application et cliquez sur « Clients OAuth » pour gérer ses clients.

| Réglage | Règle |
| --- | --- |
| Type | le **client public** convient aux applications qui ne peuvent pas conserver de secret, comme les applications navigateur ou mobile ; le **client confidentiel** est destiné aux applications serveur et dispose d’un secret |
| Adresses de rappel | 10 au maximum, adresses absolues, sans joker ni fragment ; https obligatoire, ou http en local (localhost, 127.0.0.1, [::1]), ou un protocole personnalisé pour une application native |
| scopes | `openid`, `profile`, `email`, `permissions` (consultation des autorisations), `catalog` (déclaration d’entités de données, réservé aux identifiants client) |
| Méthodes d’autorisation | code d’autorisation, jeton de rafraîchissement (suppose un code d’autorisation ; réservé aux clients confidentiels), identifiants client (clients confidentiels uniquement) |
| Durée de validité des jetons | jeton d’accès de 1 minute à 24 heures (15 minutes par défaut), jeton de rafraîchissement de 1 heure à 90 jours (30 jours par défaut) |

Le secret d’un client confidentiel n’est affiché qu’une seule fois, à l’enregistrement ou à la rotation ; GrantForge n’en conserve que le hachage. La rotation peut prévoir une période de grâce (7 jours au maximum), durant laquelle l’ancien et le nouveau secret sont tous deux valides, ce qui facilite les mises à jour progressives.

## Règles sur les jetons

- Les jetons sont conservés sous forme de hachage : même en cas de fuite de la base, aucun jeton utilisable n’est exposé.
- Dès que l’un des cas suivants se produit, les jetons déjà émis cessent d’être renouvelés : le client est désactivé ou supprimé, le compte est désactivé ou verrouillé, le compte doit modifier son mot de passe, ou le locataire est désactivé.
- Navigateur multi-origines : GrantForge autorise les origines des adresses de rappel des clients activés à appeler les points de terminaison de jetons et l’API ouverte de manière multi-origines, sans cookie.

## Clé de signature

La clé de signature est générée en RSA 2048 et la clé privée est conservée chiffrée. La rotation automatique a lieu par défaut tous les 90 jours (`grantforge.oauth.signing-key-rotation`), et l’ancienne clé publique continue d’être publiée dans le JWKS pendant 2 jours (`signing-key-retention`), afin que les jetons émis avant la rotation restent vérifiables. En cas de besoin, la rotation peut être déclenchée immédiatement depuis la page du serveur d’autorisation (opération sensible : les comptes ayant activé l’authentification à deux facteurs doivent se réauthentifier).

## Utiliser GrantForge pour connecter d’autres systèmes

Tout système compatible OpenID Connect (Grafana, GitLab, Jenkins, etc.) peut utiliser GrantForge comme IdP : créez-lui dans le catalogue de ressources une application et un client confidentiel, puis renseignez l’adresse du document de découverte, le `client_id` et le secret dans sa configuration OIDC. Inversement, GrantForge peut lui aussi s’appuyer sur un autre IdP pour la connexion ; voir [Sources d’identité](/fr/guide/identity-sources/).
