---
title: Démarrage en cinq minutes
description: Démarrer GrantForge avec la version publiée ou Docker, terminer l’initialisation, créer un utilisateur et accorder le premier rôle.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Cet article démarre GrantForge sur votre propre machine avec la base de données H2 embarquée par défaut. Pour un environnement de production, consultez [Installer la version publiée](/fr/deploy/installation/) et [Bases de données](/fr/deploy/databases/).

## 1. Démarrer le service

Java 17 ou une version ultérieure est requis. Téléchargez la version publiée depuis [GitHub Releases](https://github.com/devlive-community/grantforge/releases) ou construisez-la vous-même dans le répertoire des sources avec `./mvnw -DskipTests package` (le résultat se trouve dans `dist/grantforge-release.tar.gz`), puis décompressez-la et démarrez :

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

Vous pouvez aussi utiliser Docker : construisez d’abord une image à partir de la version publiée, puis démarrez-la avec l’exemple Compose :

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

Le service écoute par défaut sur le port `9999`. Au premier démarrage, les tables de la base de données sont créées et un **jeton d’initialisation** à usage unique est affiché dans le journal :

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

Le journal de la version publiée se trouve dans `logs/grantforge.log` ; avec Docker, consultez la sortie via `docker compose logs`.

## 2. Terminer l’initialisation

Ouvrez http://127.0.0.1:9999/ dans votre navigateur ; la console bascule automatiquement sur la page d’initialisation. Renseignez le jeton figurant dans le journal, le nom de l’organisation, ainsi que le nom d’utilisateur et le mot de passe du premier administrateur (au moins 12 caractères).

![Première initialisation et page de connexion](/screenshots/login.png)

> [!TIP]
> Lors d’une installation automatisée, vous pouvez prédéfinir le jeton via la variable d’environnement `GRANTFORGE_SETUP_TOKEN`, voir [Référence de configuration](/fr/reference/configuration/).

Une fois l’initialisation terminée, cet administrateur détient simultanément les deux rôles système **administrateur du locataire** et **administrateur de la plateforme**, et peut utiliser toutes les fonctions de la console. La page d’initialisation est ensuite définitivement fermée.

## 3. Créer un utilisateur

Allez dans **Contrôle d’accès → Gestion des utilisateurs**, cliquez sur « Créer un utilisateur » et renseignez le nom d’utilisateur, le mot de passe initial et le service principal. Un nouvel utilisateur doit modifier son mot de passe à sa première connexion.

## 4. Créer un rôle et l’autoriser

1. Allez dans **Contrôle d’accès → Gestion des rôles**, cliquez sur « Nouveau rôle », par exemple « Auditeur en lecture seule ».
2. Sur la ligne du rôle, cliquez sur « Autoriser » et cochez la page « Journal d’audit » dans la matrice d’autorisations. La matrice reprend automatiquement les API requises par cette page.
3. Cliquez sur « Affecter » et attribuez le rôle à l’utilisateur que vous venez de créer.

![Gestion des rôles](/screenshots/roles.png)

## 5. Vérifier le résultat

Connectez-vous avec le nouvel utilisateur : seul le menu « Journal d’audit » apparaît à gauche. Revenez sur le compte administrateur et cliquez sur l’icône « Voir les autorisations effectives » sur la ligne de l’utilisateur : vous voyez alors l’origine de chacune de ses autorisations.

## Prochaines étapes

- Découvrez les [concepts de base](/fr/start/concepts/).
- Familiarisez-vous avec chaque menu grâce au [guide d’utilisation](/fr/guide/console/).
- Faites [intégrer GrantForge](/fr/integration/overview/) à votre application.
