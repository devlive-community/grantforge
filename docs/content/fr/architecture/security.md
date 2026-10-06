---
title: Conception de la sécurité
description: Conception des sessions, de la protection CSRF, des mots de passe et du verrouillage, de l’authentification à deux facteurs et de la revérification, du chiffrement au repos, des jetons et de l’audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Sessions de la console

- Les sessions sont conservées en base de données (Spring Session JDBC) ; le navigateur ne détient que le cookie `GRANTFORGE_SESSION` : HttpOnly, SameSite=Lax, et Secure sous HTTPS (ou forcé via `grantforge.security.cookie-secure`).
- La connexion, la validation de l’authentification à deux facteurs et la connexion fédérée renouvellent l’identifiant de session et le jeton CSRF, afin d’éviter la fixation de session.
- Les requêtes qui modifient l’état doivent porter l’en-tête `X-XSRF-TOKEN`, dont la valeur provient du cookie `XSRF-TOKEN`.
- Les administrateurs peuvent lister et terminer n’importe quelle session ; la désactivation, le verrouillage et la réinitialisation du mot de passe mettent immédiatement fin à toutes les sessions du compte ; la modification du mot de passe termine les sessions sur les autres appareils.

## Mots de passe

- Les nouveaux mots de passe sont hachés avec Argon2id ; les hachages BCrypt et ceux importés depuis la version 1.x restent vérifiables et sont mis à niveau vers l’algorithme courant à la prochaine connexion.
- Politique : la longueur, les catégories de caractères, l’historique et la durée de validité sont tous configurables ; le mot de passe ne peut pas contenir le nom d’utilisateur.
- Après un nombre configurable d’échecs consécutifs, le compte est verrouillé ; une comparaison de hachage est également effectuée pour les noms d’utilisateur inconnus, et le temps de réponse ne révèle pas quels noms d’utilisateur existent ; les comptes désactivés et les locataires désactivés ne sont signalés qu’après vérification du mot de passe.

## Authentification à deux facteurs et revérification

- TOTP (RFC 6238, SHA-1, 6 chiffres, 30 secondes, avec une tolérance d’un pas de temps avant et après) ; un code d’un même pas de temps ne peut être utilisé qu’une seule fois ; 10 codes de récupération à usage unique sont conservés avec SHA-256.
- Pour les comptes qui ont activé l’authentification à deux facteurs, la session reste pendant 5 minutes à l’état « en attente » après validation du mot de passe ; l’utilisateur n’est pas connecté tant que la deuxième étape n’est pas achevée.
- Les interfaces sensibles sont annotées avec `@RequireStepUp` : les comptes qui ont activé l’authentification à deux facteurs doivent s’être vérifiés dans une fenêtre temporelle configurable, sinon `GF-SECURITY-006` est renvoyé ; la console affiche une fenêtre de confirmation, puis réessaie.

## Chiffrement au repos

Les mots de passe liés et les secrets client des sources d’identité, les clés des vérificateurs, les configurations sensibles des services de données et les clés privées de signature du serveur d’autorisation sont tous conservés chiffrés en AES-GCM. La clé provient de `grantforge.security.encryption-key` ; lorsqu’elle n’est pas configurée, elle est générée automatiquement et enregistrée en base de données (convient uniquement à un essai). Les jetons d’agent et les secrets des clients OAuth ne sont conservés que sous forme de hachage.

## Jetons

- Le serveur d’autorisation conserve le hachage des jetons, et non les jetons eux-mêmes.
- Les jetons de rafraîchissement sont renouvelés à chaque usage ; la relecture d’un ancien jeton entraîne la révocation de toute l’autorisation.
- La désactivation ou le verrouillage d’un compte, la nécessité de changer de mot de passe, la désactivation d’un client et celle d’un locataire empêchent tout renouvellement de jeton ; l’API ouverte confirme la validité du jeton à chaque appel.
- Les instantanés de politique sont signés avec Ed25519 ; l’agent ne les utilise qu’après avoir vérifié la signature.

## Protection des interfaces

- Chaque interface doit déclarer son mode d’accès ; une interface sans déclaration empêche le démarrage ; les interfaces qui exigent des autorisations sont vérifiées à chaque appel par rapport à l’instantané le plus récent.
- Les appels refusés sont écrits dans l’audit (sans bloquer la requête).
- Un compte local homonyme d’une source d’identité externe n’est pas associé automatiquement, afin d’éviter les prises de contrôle de compte.
- Lors de l’export CSV, les cellules qui commencent par `=`, `+`, `-` ou `@` reçoivent un préfixe, afin d’éviter l’injection de formules.

## Audit

Toutes les opérations d’administration, les changements d’autorisation, les événements de connexion et les appels refusés sont enregistrés dans le journal d’audit ; les changements liés aux autorisations et leur audit sont validés dans la même transaction : si le changement est annulé, aucun audit ne subsiste.
