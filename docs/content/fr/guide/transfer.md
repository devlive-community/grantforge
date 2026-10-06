---
title: Import et export en masse
description: Importer ou exporter en masse des utilisateurs et l’organigramme par fichier CSV, avec un contrôle préalable avant l’écriture.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Contrôle d’accès → Import et export** permet d’importer ou d’exporter en masse, par fichier CSV, les utilisateurs et l’organigramme.

![Import et export](/screenshots/transfer.png)

## Importer

1. Cliquez sur **Télécharger le modèle** et remplissez-le selon la « description des colonnes ». Les en-têtes ne tiennent pas compte de la casse et les colonnes superflues sont ignorées ; pour un utilisateur, le service et le poste se renseignent par code, plusieurs valeurs étant séparées par des points-virgules.
2. Téléversez le fichier et cliquez sur **Contrôle préalable** : GrantForge vérifie ligne par ligne et signale chaque problème (numéro de ligne, colonne et cause).
3. Une fois tout validé, cliquez sur **Confirmer l’import de N lignes**. Si une seule ligne pose problème, rien n’est écrit, ce qui évite un import fait à moitié.

Règles :

- L’encodage du fichier peut être UTF-8 ou GBK (la valeur par défaut des CSV chinois enregistrés avec Excel est GBK) ; il est détecté automatiquement.
- Un fichier contient au plus 1 000 utilisateurs ou 5 000 services, pour une taille maximale de 2 Mo (configurable).
- L’import des services est trié automatiquement selon les relations de rattachement : un supérieur peut figurer après son subordonné dans le fichier ; un cycle à l’intérieur du fichier est signalé.
- Le mot de passe initial d’un utilisateur doit satisfaire la politique de mot de passe ; un utilisateur importé doit modifier son mot de passe à sa première connexion.
- L’import ne peut cibler que les services et les postes visibles au titre de vos autorisations sur les données.

## Exporter

Exportez les utilisateurs correspondant au filtre courant ou l’ensemble des services ; le nom du fichier porte la date, par exemple `users-2026-10-05.csv`. L’export respecte lui aussi les autorisations sur les données et sur les champs : les lignes non visibles ne sont pas exportées et les champs restreints sont cachés ou caviardés selon leur règle. Une cellule commençant par `=`, `+`, `-` ou `@` reçoit un préfixe, afin d’empêcher le tableur de l’interpréter comme une formule.
