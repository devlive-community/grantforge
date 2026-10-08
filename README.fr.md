<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="Logo GrantForge" />

# GrantForge

Plateforme unifiée d’autorisations · utilisateurs, rôles, menus, API, lignes et champs de données · systèmes externes

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [Deutsch](README.de.md) · Français · [Español](README.es.md) · [Português](README.pt-BR.md) · [Italiano](README.it.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.1.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge (anciennement AuthX) est une plateforme unifiée d’autorisations, open source (MIT). Elle répond à deux questions en un seul endroit : **qui peut faire quoi** (autorisations fonctionnelles) et **qui peut voir quelles données** (autorisations sur les données et les champs). Les autorisations se définissent, s’expliquent et s’auditent dans la console, les applications métier s’y connectent via des protocoles standard, et les systèmes de données externes (HDFS par exemple) sont intégrés au même modèle de politiques par des plug-ins et des agents.

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="Console GrantForge" />
</p>

## Fonctionnalités

| Domaine | Fonctionnalités |
| --- | --- |
| Identité et organisation | Multi-locataires, arborescence des services, groupes et postes ; import et export CSV en masse ; connexion et synchronisation LDAP / Active Directory, fédération OIDC |
| Sécurité des comptes | Gestion des sessions et déconnexion forcée, politique de mot de passe avec verrouillage, authentification à deux facteurs TOTP avec codes de récupération, seconde validation des opérations sensibles |
| Autorisations fonctionnelles | Catalogue de ressources (modules, menus, pages, onglets, boutons, API), héritage des rôles, matrice d’autorisations, analyse d’impact avant l’attribution |
| Autorisations sur les données | Lignes visibles bornées par des conditions (soi-même, son service et ses sous-services, des services désignés, conditions personnalisées), lecture et écriture contrôlées séparément |
| Autorisations sur les champs | Un champ peut être masqué, caviardé (adresse e-mail, numéro de téléphone, numéro de pièce d’identité) ou en lecture seule |
| Explicabilité et audit | Explication des autorisations (d’où vient chacune), simulation d’une attribution, interrogation et export du journal d’audit |
| Gouvernance | Contraintes de séparation des tâches (SOD), demande d’accès avec validation, revue périodique des autorisations |
| Intégration d’applications | Serveur d’autorisation OAuth 2.1 / OIDC, API ouverte de consultation des autorisations, SDK Java (Spring Boot Starter) et JavaScript |
| Systèmes externes | Types de service par plug-ins et moteur de politiques : services de données, politiques d’accès, agents et audit des accès |
| Livraison | Une seule version publiée exécutable, image Docker, exemples Compose, chart Helm ; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

Le support des systèmes externes comprend le cadre de plug-ins, l’éditeur de politiques, la distribution signée, l’audit des accès, le type de service HDFS et des agents NameNode numérotés pour Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 et 3.5. Le plug-in Hive reste en développement.

## Versions cibles des agents HDFS

| Base Hadoop | Java du conteneur | Répertoire de l’agent |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8 (image amd64) | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

Choisissez `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` dans `agents/hdfs/<line>/` pour la branche Hadoop du cluster. La logique commune cible Java 8, l’adaptateur 3.5 Java 17.

Hadoop 2.7, 2.10, 3.2 et 3.3 ne fournissent pas le rappel d’autorisation des superutilisateurs utilisé par l’agent. Hadoop garde le contrôle de ces accès ; utilisez des utilisateurs ordinaires pour les données soumises aux politiques GrantForge.

## Comment ça marche : deux plans

- **Plan d’administration** : le serveur GrantForge (Spring Boot 4.1, bytecode Java 17) et la console Vue 3 gèrent les locataires, les comptes, l’organisation, les rôles, les autorisations, l’audit, ainsi que les services de données et les politiques.
- **Plan de données** : des agents embarqués dans le système protégé. Un agent récupère périodiquement, avec son jeton, des instantanés de politiques signés en Ed25519 et les met en cache localement ; il décide avant chaque accès de l’autoriser ou de le refuser (refus par défaut lorsqu’aucune politique n’est joignable) et remonte les événements d’accès au serveur pour l’audit.

Votre système n’a pas à copier l’approche de HDFS : une application métier ordinaire évalue les autorisations dans son propre processus via l’API ouverte ou le Spring Boot Starter ; seuls les systèmes qui doivent intercepter l’accès à l’intérieur d’une base de données, d’un système de fichiers ou d’un stockage comparable ont besoin d’un agent écrit contre `core/grantforge-agent-core` et embarqué dans le système cible.

## Intégrer votre application

- **OAuth 2.1 / OpenID Connect** : GrantForge est lui-même un serveur d’autorisation, les applications y connectent leurs utilisateurs ; les sources d’identité existantes (LDAP / AD / OIDC) peuvent aussi y être raccordées.
- **Applications Java** : `sdk/grantforge-spring-boot-starter` fournit `@RequirePermission` pour sécuriser les points d’entrée, `@GrantForgeEntity` pour déclarer une entité de données, et `GrantForgeDataScopes.scope(...)` pour traduire les autorisations sur les données de la plateforme en `Specification` JPA.
- **Applications frontend** : `@grantforge/client` connecte l’utilisateur depuis votre propre origine avec OIDC + PKCE et interroge ses autorisations.
- **API ouverte** : `/api/v1/open/me/authorization`, `/api/v1/open/me/data-access`, `/api/v1/open/catalog/data-entities`.
- **Exemples exécutables** : les applications `shop` et `notes` sous `samples/` s’intègrent exactement comme le ferait un tiers.

## Démarrage rapide

Java 17 ou une version plus récente est requis. Le service écoute par défaut sur le port `9999` et affiche au premier démarrage un **jeton d’initialisation** à usage unique ; ouvrez <http://127.0.0.1:9999/> dans un navigateur, saisissez ce jeton et créez le premier administrateur.

```bash
# Avec la version publiée (ou compilez depuis les sources avec ./mvnw clean package, artefact dans dist/)
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# Ou avec Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# Ou avec Compose et une base de données
docker compose -f deploy/compose/postgres.yml up -d

# Ou déployez sur Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

La base fichier H2 embarquée est utilisée par défaut, aucun réglage n’est nécessaire pour démarrer. Le pilote MySQL n’est pas distribué avec la version publiée à cause de sa licence GPL ; placez-le vous-même dans `drivers/`. Les étapes détaillées de l’installation, de l’initialisation et de la première attribution sont décrites dans la [documentation](https://grantforge.devlive.org).

## Bases de données

La base fichier H2 embarquée (`${GRANTFORGE_HOME}/data`) est utilisée par défaut, aucun réglage n’est nécessaire pour démarrer. En production, le changement se fait par variables d’environnement et le schéma est géré par Liquibase :

| Base de données | Versions (vérifiées en CI) | Exemple de `GRANTFORGE_DB_URL` |
| --- | --- | --- |
| PostgreSQL | 14, 17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0, 8.4 | `jdbc:mysql://host:3306/grantforge` (placez vous-même `mysql-connector-j` dans `lib/` ; sa licence GPL l’exclut de la version publiée) |
| MariaDB | 10.11, 11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

Définissez également `GRANTFORGE_DB_USER` et `GRANTFORGE_DB_PASSWORD` ; en cluster, chaque instance doit recevoir son propre `GRANTFORGE_ID_NODE` (0-1023).

## Structure du projet

Coordonnée Maven racine : `org.devlive.grantforge:grantforge:2026.0.0`. Préfixe des paquets Java : `org.devlive.grantforge`. Classe de démarrage : `org.devlive.grantforge.server.GrantForge`.

`core/` contient le serveur et l’infrastructure partagée, `plugins/` contient les plug-ins de types de service chargés par le serveur, et `agents/` contient les agents déployés dans les systèmes protégés. La bibliothèque partagée `grantforge-agent-core` reste dans `core/`, et l’agent NameNode HDFS se trouve sous `agents/grantforge-agent-hdfs-*`.

| Module | Responsabilité |
| --- | --- |
| `core/grantforge-server` | Point d’entrée Spring Boot : API REST, configuration de sécurité, API ouverte, et hébergement de la console web |
| `core/grantforge-web` | Console d’administration en Vue 3 / TypeScript / Tailwind CSS |
| `core/grantforge-common` | Codes d’erreur et modèle problem details, CSV, annotations d’accès aux points d’entrée |
| `core/grantforge-persistence` | Entités, filtrage par locataire, TSID, Liquibase, SPI des autorisations sur les données et les champs |
| `core/grantforge-audit` | Enregistrement, interrogation, conservation et archivage des événements d’audit |
| `core/grantforge-identity` | Locataires, comptes, services, groupes, postes, connexion et sessions, authentification à deux facteurs, sources d’identité |
| `core/grantforge-authz` | Catalogue de ressources, rôles, autorisations, attributions et évaluation, politiques sur les données et les champs, séparation des tâches, demandes et revues |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | Contrat des plug-ins de types de service, ainsi que chargement, isolation et appel des plug-ins |
| `core/grantforge-policy-engine` | Moteur d’évaluation des politiques des systèmes externes (API Java 8, intégrable dans un agent) |
| `core/grantforge-agent-core` | Code commun des agents : réglages, instantanés signés, décisions d’accès, remontée d’audit |
| `core/grantforge-service` | Services de données, signature et distribution des instantanés de politiques, agents et audit des accès |
| `core/grantforge-oauth` | Serveur OAuth 2.1 / OIDC bâti sur Spring Authorization Server |
| `plugins/grantforge-plugin-hdfs` | Plug-in du type de service HDFS : gestion des politiques et recherche de ressources |
| `plugins/grantforge-plugin-example` | Plug-in d’exemple pour un type de service personnalisé |
| `agents/grantforge-agent-hdfs-common` | Logique commune d’autorisation HDFS, de configuration, de snapshots et d’audit (Java 8) |
| `agents/grantforge-agent-hdfs-*` | Agents NameNode numérotés pour Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 et 3.5 : autorisation et audit des accès |
| `sdk/grantforge-spring-boot-starter`, `sdk/grantforge-js` | SDK Java et JavaScript pour l’intégration des applications |
| `script/ci`, `deploy/` | Scripts de vérification CI (les mêmes en local et en CI) et ressources de déploiement (Dockerfile, Compose, Helm) |

## Exploitation et observabilité

- Sondes de santé : `/actuator/health/liveness`, `/actuator/health/readiness` (uniquement le statut, sans détail ; la sonde de disponibilité renvoie 200 dès que la base est joignable et les migrations achevées).
- Métriques : `/actuator/prometheus` (avec le label `application="grantforge"`, connexion requise par défaut ; `GRANTFORGE_PROMETHEUS_PUBLIC=true` l’ouvre aux réseaux de confiance).
- Journaux : par défaut du texte lisible avec un identifiant de requête par ligne ; définissez `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (ou `logstash`) pour des journaux JSON.
- Scripts de la version publiée : `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` et `import-legacy.sh` sous `bin/`.

## Développement et vérification

La compilation nécessite JDK 17 ou ultérieur. Le serveur et l’adaptateur Hadoop 3.5 ciblent Java 17 ; le moteur de politiques, le cœur d’agent et les adaptateurs Hadoop 2.7–3.4 ciblent Java 8. Error Prone + NullAway s’activent dès JDK 21. Le frontend utilise Vue 3.5, Tailwind CSS 4, Node.js 22.12+ et pnpm 8.10.2.

```sh
# Compilation Java et tests unitaires (la compilation du frontend est ignorée)
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# Tests d’intégration de la persistance sur une base de données donnée (Docker requis, sauf pour h2)
bash script/ci/db_integration.sh postgres:17

# Empaquetage de la version publiée (frontend inclus) vers dist/
./mvnw clean package

# Développement et vérifications du frontend
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# Applications d’exemple et SDK
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# Contrat d’API : régénérer openapi.json et les types du frontend après un changement du serveur (la CI vérifie les deux)
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# Site de documentation (docs/, Next.js + Tailwind CSS)
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # régénère les captures avec un vrai service et des données d’exemple

# Vérifications du dépôt (les mêmes que la CI)
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## Liens

- [Dépôt du projet](https://github.com/devlive-community/grantforge)
- [Documentation](https://grantforge.devlive.org) : démarrage rapide, guide d’utilisation, intégration et référence technique, sources dans [`docs/`](docs/)
- [Guide de contribution](CONTRIBUTING.md) · [Code de conduite](CODE_OF_CONDUCT.md) · [Journal des modifications](CHANGELOG)
