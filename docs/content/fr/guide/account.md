---
title: Connexion, comptes et authentification à deux facteurs
description: Connexion et sessions, profil personnel, modification du mot de passe, authentification à deux facteurs et codes de récupération, ainsi que la nouvelle vérification des opérations sensibles.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Connexion

![Page de connexion](/screenshots/login.png)

Le nom d’utilisateur ne distingue pas les majuscules des minuscules. Cinq erreurs de mot de passe consécutives (configurable) verrouillent le compte pendant 15 minutes ; un compte verrouillé par un administrateur doit être déverrouillé par un administrateur. Lorsque des sources d’identité sont activées, la page de connexion affiche aussi un bouton « Se connecter via X », voir [Sources d’identité](/fr/guide/identity-sources/).

Les sessions sont conservées côté serveur ; le navigateur ne détient qu’un cookie de session HttpOnly. Après 30 minutes d’inactivité (configurable), la session expire et une reconnexion est nécessaire.

## Profil personnel

Cliquez sur l’avatar en haut à droite pour accéder au **profil personnel** :

![Profil personnel](/screenshots/account.png)

- **Informations générales** : modifiez le nom d’affichage et l’adresse e-mail. Le nom d’utilisateur et l’organisation de rattachement sont maintenus par l’administrateur.
- **Modifier le mot de passe** : le mot de passe actuel est demandé. Après modification, toutes vos sessions sur les autres appareils prennent fin. Les comptes connectés via une source d’identité ne voient pas ici l’option de modification du mot de passe : celui-ci est géré par la source d’identité.
- **Authentification à deux facteurs** : voir ci-dessous.
- **Mes appareils de connexion** : la liste des navigateurs actuellement connectés ; vous pouvez y mettre fin aux sessions que vous ne reconnaissez pas.
- **Connexions récentes** : les 10 dernières connexions, déconnexions et tentatives échouées, y compris les tentatives de connexion effectuées par d’autres avec votre nom d’utilisateur.

Après une réinitialisation du mot de passe par un administrateur ou l’expiration du mot de passe, la prochaine connexion mène d’abord à la page « Modifier le mot de passe » ; aucune autre fonction n’est accessible tant que ce n’est pas fait.

## Authentification à deux facteurs

Une fois activée, la connexion exige, en plus du mot de passe, un code à 6 chiffres fourni par une application d’authentification (Google Authenticator, Microsoft Authenticator, 1Password, etc.).

1. Dans le profil personnel, cliquez sur **Configurer l’authentificateur** dans « Authentification à deux facteurs ».
2. Ajoutez un compte dans l’application d’authentification : saisissez la clé affichée sur la page, ou ouvrez le lien otpauth sur un appareil où l’application est installée.
3. Saisissez le code affiché par l’application, puis cliquez sur **Activer**.
4. La page affiche **10 codes de récupération**, une seule fois. Conservez-les précieusement : en cas de perte de l’authentificateur, chaque code de récupération permet de se connecter une fois à la place du code de vérification.

Après activation, vous pouvez régénérer les codes de récupération (les anciens sont immédiatement invalidés) ou désactiver l’authentification à deux facteurs ; ces deux opérations exigent la saisie d’un code de vérification. En cas de perte de l’authentificateur sans code de récupération, demandez à un administrateur de réinitialiser l’authentification à deux facteurs du compte dans la **Gestion des utilisateurs**.

> [!TIP]
> Chaque code de vérification ne peut être utilisé qu’une fois. Des erreurs répétées de code de vérification comptent dans le verrouillage au même titre que des erreurs de mot de passe.

## Nouvelle vérification des opérations sensibles

Pour les comptes dont l’authentification à deux facteurs est activée, les opérations suivantes exigent une vérification effectuée dans les 10 dernières minutes (configurable) : rotation de la clé de signature, création d’un client ou rotation de sa clé, création ou désactivation d’un locataire, réinitialisation du mot de passe ou de l’authentification à deux facteurs d’autrui, affectation de rôles, modification d’autorisations, ajout ou modification de sources d’identité, validation de demandes d’accès.

Au-delà de ce délai, la console ouvre une boîte de dialogue « Confirmer l’identité » ; après saisie du code de vérification, l’opération en cours reprend automatiquement. En définissant `grantforge.security.mfa.required-for-sensitive=true`, vous pouvez exiger que les comptes exécutant ces opérations aient activé l’authentification à deux facteurs.

## Sessions en ligne

Dans **Contrôle d’accès → Sessions en ligne**, l’administrateur voit tous les navigateurs connectés de ce locataire (compte, IP, navigateur, heure de connexion, dernière activité) et peut mettre fin aux sessions suspectes. Désactiver ou verrouiller un compte, ou réinitialiser son mot de passe, met immédiatement fin à toutes les sessions de ce compte.

![Sessions en ligne](/screenshots/sessions.png)
