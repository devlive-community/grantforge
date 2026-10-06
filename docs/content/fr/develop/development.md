---
title: Développement, tests et intégration continue
description: Builds locaux, tests, conventions de code et vérifications d’intégration continue.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Environnement

- JDK 21 (les artefacts sont en bytecode Java 17 ; le moteur de politiques est en Java 8)
- Node.js 22 et pnpm 8.10.2
- Docker (tests d’intégration en base de données, benchmarks de performance et images)
- Python 3.12 (scripts d’intégration continue)

## Commandes courantes

```bash
./mvnw verify                          # construit et teste tous les modules Java (console comprise)
./mvnw verify -DskipFrontend           # ignore la construction de la console
bash script/ci/web.sh test             # tests unitaires de la console
bash script/ci/web.sh e2e              # tests navigateur de la console (backend simulé)
bash script/ci/e2e_fullstack.sh        # empaquette, démarre un vrai service et exécute les tests full-stack
bash script/ci/db_integration.sh postgres:17   # exécute les tests d’intégration sur la base de données indiquée
bash script/ci/perf_benchmark.sh smoke # benchmark de performance à petite échelle
```

Pour développer la console, exécutez `pnpm dev` (dans `core/grantforge-web`) ; Vite relaie les requêtes comme `/api` vers le service à l’écoute sur `localhost:9999`.

## Lancement depuis l’IDE

Exécutez directement `org.devlive.grantforge.server.GrantForge` (module `grantforge-server`) ; la base H2 est utilisée par défaut. Le serveur charge automatiquement les modules de plug-ins construits présents dans le dossier `plugins/` du dépôt (voir [Plug-ins et types de service](/fr/develop/plugins/)) ; avant la première utilisation d’un module de plug-in, exécutez une fois `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` pour en copier les dépendances.

## Conventions de code

- Backend : Error Prone + NullAway (non-nul par défaut, avec JSpecify `@Nullable` aux endroits nullables), Checkstyle, PMD, SpotBugs ; ArchUnit protège les conventions communes (pas d’injection par champ, pas de SQL natif, les entités n’apparaissent pas dans l’API, `Optional.get()` interdit, etc.).
- Frontend : mode strict de TypeScript, ESLint sans aucun avertissement ; tous les textes passent par l’i18n, les clés doivent être des littéraux et les clés chinoises et anglaises sont parfaitement identiques.
- Chaque fichier source porte un en-tête de licence MIT ; chaque classe principale du code doit avoir une classe de test correspondante (les exceptions sont déclarées dans `script/ci/test_mapping_exclusions.txt`).
- Les seuils de couverture sont définis par module (`script/ci/coverage_thresholds.txt`).
- Les migrations de base de données sont en YAML Liquibase, un fichier par changement, uniquement en ajout et jamais en modification ; les types utilisent des propriétés transverses comme `${text}`.
- Les messages de commit suivent Conventional Commits, et le titre ne dépasse pas 72 caractères.

## Intégration continue

| Tâche | Contenu |
| --- | --- |
| Repository hygiene | en-têtes de licence, chemins interdits, correspondance des tests, i18n, manifeste des autorisations, formats de fichiers, vérifications des scripts et des workflows |
| Commit messages | format des messages de commit |
| CI script unit tests | tests des scripts d’intégration continue eux-mêmes |
| Java 17 / 21 / 25 / latest | construction et test de tous les modules Java, vérification de la version du bytecode |
| Java static analysis | couverture, Checkstyle, SpotBugs, PMD |
| Frontend | cohérence des types d’API avec le contrat, vérification des types et build, ESLint, tests unitaires, tests navigateur |
| JavaScript SDK | vérification des types, build, ESLint, tests unitaires |
| Database | migrations et tests d’intégration sur H2, PostgreSQL 14/17, MySQL 8.0/8.4, MariaDB 10.11/11.4, Oracle 23 et SQL Server 2022 |
| Plugin API compatibility | comparaison du contrat des plug-ins avec la version publiée précédente |
| Full-stack acceptance | empaquetage sur PostgreSQL, démarrage du service et exécution des tests navigateur full-stack |
| Docs | vérification, tests et construction du site de documentation |

Les benchmarks de performance sont en outre exécutés chaque nuit ; les workflows de sécurité analysent les dépendances et les clés.

## Publication

Le numéro de version est de la forme `année.mineure.correctif` (par exemple `2026.0.0`), et les versions candidates reçoivent le suffixe `-rc.N`. La version doit être identique dans tous les pom, les paquets npm, l’appVersion du Helm Chart, la barre latérale de la console et le README ; l’intégration continue le vérifie avec `check_versions.py`.

La publication se fait depuis la branche `dev` avec une seule commande :

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # vérifie seulement et prévisualise les notes de version, sans aucune modification
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # définit la version, crée et pousse le tag v2026.1.0, puis fait repartir dev sur la version suivante
```

Le script exige un espace de travail propre, une branche locale qui n’est pas en retard sur le dépôt distant et l’absence du tag ; après confirmation, il valide le commit `chore(release): prepare <version>`, crée un tag annoté et le pousse. Le tag déclenche `release.yml` :

- `script/ci/release.sh` construit la version publiée, un SBOM CycloneDX ne contenant que les dépendances de publication et `SHA256SUMS` ;
- les images multi-architectures sont poussées vers `ghcr.io/devlive-community/grantforge` ;
- les artefacts Maven (paquet de sources et Javadoc compris) sont publiés sur GitHub Packages ; lorsque le dépôt définit `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (jeton du Central Portal), `GPG_PRIVATE_KEY` et `GPG_PASSPHRASE`, ils sont signés puis publiés sur Maven Central ;
- une GitHub Release est créée, dont le corps reprend tous les commits depuis la version publiée précédente (`v*` ou un tag numérique comme `1.0.6`), regroupés par nouvelles fonctionnalités, corrections de bugs, performances, etc., avec des liens vers les commits.

Les versions candidates sont marquées comme pré-publication et ne mettent pas à jour le tag `latest` des images. En local, l’activation du profil `central` ne publie rien par défaut (`central.skip=true`) ; seule la transmission explicite de `-Dcentral.skip=false` par le workflow de publication le fait.

## Manifeste des autorisations

Les pages et les boutons de la console, ainsi que les API dont elles ont besoin, sont déclarés dans `core/grantforge-web/src/permissions/`. `check_permission_manifest.py` garantit que chaque API déclarée existe et que chaque interface exigeant des autorisations est couverte par un bouton ou une page (les exceptions des appels directs sont déclarées dans `script/ci/permission_direct_apis.txt`).

## Documentation

Ce site se trouve dans `docs/` et utilise l’export statique de Next.js avec Tailwind CSS :

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # vérification des pages, des liens et des images
pnpm build      # sortie vers docs/out
```

Les pages sont du Markdown situé sous `docs/content/`, et la navigation se trouve dans `docs/lib/navigation.ts`. La référence de l’API et les codes d’erreur sont générés à la construction à partir du contrat et du code source. Les captures d’écran sont produites par `script/docs/screenshots.sh`, qui démarre un vrai service, écrit des données d’exemple, puis fait appel à Playwright.
