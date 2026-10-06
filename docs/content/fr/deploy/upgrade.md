---
title: Mettre à niveau et migrer depuis d’anciennes versions
description: Mises à niveau entre versions 2.x et migration depuis la 1.x (AuthX / GrantForge 1.x) des comptes, des rôles et des menus.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Mise à niveau entre versions 2.x

1. Sauvegardez la base de données (ainsi que `plugins/` et `configure/`).
2. Arrêtez le service : `bin/shutdown.sh`.
3. Remplacez l’ancien `lib/` et `bin/` par ceux de la nouvelle version publiée.
4. Démarrez : `bin/startup.sh`. Liquibase exécute automatiquement les migrations de la nouvelle version, et la sonde de disponibilité ne renvoie 200 qu’une fois les migrations terminées.

Lors d’une mise à niveau en cluster, arrêtez d’abord toutes les instances avant de démarrer la nouvelle version, afin d’éviter que l’ancienne et la nouvelle n’écrivent simultanément. Une migration déjà publiée n’est jamais modifiée, et chaque version est validée sur l’ensemble des bases prises en charge pour le scénario « mise à niveau depuis la version précédente ».

## Migration depuis la 1.x

La 1.x conserve ses données dans un autre jeu de tables, que la 2.x ne lit pas. La migration se fait ainsi : installez la 2.x sur une **nouvelle base** et terminez l’initialisation, arrêtez le service, puis importez les comptes, les rôles et les menus de l’ancienne base dans un locataire :

```bash
# simulation préalable : génère uniquement le rapport, sans rien écrire
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
# import réel une fois le rapport validé
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

Les deux commandes produisent `logs/legacy-import-report.json`, qui liste ce qui a été (ou va être) créé, ce qui a été ignoré et pourquoi, ainsi que le nouvel identifiant correspondant à chaque ancien objet. Le pilote JDBC de l’ancienne base se dépose dans `drivers/` et le mot de passe est fourni via `GRANTFORGE_LEGACY_SOURCE_PASSWORD` (le script le demande s’il n’est pas fourni). Une nouvelle exécution de l’import ne fait que compléter ce qui manque.

Règles d’import :

- **Comptes** : le mot de passe d’origine est conservé et remplacé automatiquement par le nouvel algorithme de hachage à la première connexion. Sont ignorés les comptes dont le nom d’utilisateur ne respecte pas les règles de la 2.x (3 à 64 lettres, chiffres ou caractères `._@-`), qui n’ont pas de mot de passe, ou dont le nom d’utilisateur est déjà pris par un autre locataire.
- **Rôles** : le nom est conservé et le code est converti en minuscules (`GLY` → `gly`).
- **Menus** : ils deviennent des ressources de l’application `legacy` ; les menus d’adresse `#` servent de regroupement, les autres adresses deviennent des pages, et les menus situés sous une page deviennent des boutons. L’adresse du menu et sa méthode HTTP deviennent une ressource API `api:<méthode>:<chemin>` ; une adresse terminée par `*` devient `<chemin>/**`, appariée par segment de chemin et non par préfixe de caractères ; le rapport les énumère une par une pour vérification.
- Seules les **autorisations explicites** sont migrées : la 1.x autorise tout le monde à accéder aux adresses enregistrées comme menus, ce que la 2.x ne fait pas.

> [!WARNING]
> Avant l’import, vérifiez dans le rapport les comptes ignorés et les adresses avec joker, puis exécutez `--apply`.
