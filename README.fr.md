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

Le support des systèmes externes comprend le cadre de plug-ins, l’éditeur de politiques, la distribution signée, l’audit des accès et les plug-ins de service HDFS, ZooKeeper, MySQL, Hive, Kafka, HBase, Trino, YARN, Pulsar, Solr, ClickHouse et PostgreSQL. HDFS utilise des agents NameNode numérotés, ZooKeeper un agent embarqué dans le serveur, MySQL un proxy réseau autonome, Hive un autorisateur dans HiveServer2, Kafka un autorisateur dans les brokers et les contrôleurs, HBase un coprocesseur dans les masters et les region servers, Trino un contrôle d’accès système dans le coordinator, YARN un fournisseur d’autorisation dans les ResourceManagers, Pulsar un fournisseur d’autorisation dans les brokers, Solr un plug-in d’autorisation dans les nœuds et ClickHouse un service à côté des serveurs qui synchronise les rôles et politiques de lignes propres à ClickHouse. PostgreSQL utilise un service à côté des serveurs qui synchronise les rôles, droits et politiques de sécurité des lignes propres à PostgreSQL. La connexion, la recherche de bases, tables et colonnes et les fonctionnalités des politiques figurent dans le [guide du plug-in MySQL](https://grantforge.devlive.org/fr/plugins/mysql/) ; le déploiement et les limites d’application dans le [guide de l’agent proxy MySQL](https://grantforge.devlive.org/fr/external/mysql-agent/). Hive est décrit dans le [guide du plug-in Hive](https://grantforge.devlive.org/fr/plugins/hive/) et le [guide de l’agent Hive](https://grantforge.devlive.org/fr/external/hive-agent/). Kafka est décrit dans le [guide du plug-in Kafka](https://grantforge.devlive.org/fr/plugins/kafka/) et le [guide de l’agent Kafka](https://grantforge.devlive.org/fr/external/kafka-agent/). HBase est décrit dans le [guide du plug-in HBase](https://grantforge.devlive.org/fr/plugins/hbase/) et le [guide de l’agent HBase](https://grantforge.devlive.org/fr/external/hbase-agent/). Trino est décrit dans le [guide du plug-in Trino](https://grantforge.devlive.org/fr/plugins/trino/) et le [guide de l’agent Trino](https://grantforge.devlive.org/fr/external/trino-agent/). YARN est décrit dans le [guide du plug-in YARN](https://grantforge.devlive.org/fr/plugins/yarn/) et le [guide de l’agent YARN](https://grantforge.devlive.org/fr/external/yarn-agent/). Pulsar est décrit dans le [guide du plug-in Pulsar](https://grantforge.devlive.org/fr/plugins/pulsar/) et le [guide de l’agent Pulsar](https://grantforge.devlive.org/fr/external/pulsar-agent/). Solr est décrit dans le [guide du plug-in Solr](https://grantforge.devlive.org/fr/plugins/solr/) et le [guide de l’agent Solr](https://grantforge.devlive.org/fr/external/solr-agent/). ClickHouse est décrit dans le [guide du plug-in ClickHouse](https://grantforge.devlive.org/fr/plugins/clickhouse/) et le [guide de l’agent ClickHouse](https://grantforge.devlive.org/fr/external/clickhouse-agent/). PostgreSQL est décrit dans le [guide du plug-in PostgreSQL](https://grantforge.devlive.org/fr/plugins/postgresql/) et le [guide de l’agent PostgreSQL](https://grantforge.devlive.org/fr/external/postgresql-agent/).


## Composants et versions pris en charge

Les versions ci-dessous sont celles exécutées par les tests d'intégration Testcontainers du dépôt. Les autres versions ne sont pas certifiées.

| Composant | Plug-in de service | Versions vérifiées | Agent d'application | Versions de l'agent | Java de l'agent |
| --- | --- | --- | --- | --- | --- |
| Apache Hadoop HDFS | `plugins/grantforge-plugin-hdfs` | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 | Agent NameNode | 2.7, 2.10, 3.2, 3.3, 3.4, 3.5 | 8 (3.5 : 17) |
| Apache Hadoop YARN | `plugins/grantforge-plugin-yarn` | 3.2.4, 3.3.6, 3.4.3, 3.5.0 | Fournisseur d'autorisation des ResourceManagers | Testé sur les mêmes lignes Hadoop | 8 |
| Apache Hive | `plugins/grantforge-plugin-hive` | HiveServer2 3.1.3, 4.0.1, 4.1.0 | Autorisateur HiveServer2 | Testé sur les mêmes lignes HiveServer2 | 8 |
| Apache Kafka | `plugins/grantforge-plugin-kafka` | 3.7.2, 3.9.2, 4.1.2, 4.3.1 | Autorisateur des brokers et contrôleurs | Testé sur les mêmes lignes Kafka | 8 |
| Apache Pulsar | `plugins/grantforge-plugin-pulsar` | 3.0.17, 3.3.9, 4.0.14, 4.2.5 | Fournisseur d'autorisation des brokers | Testé sur les mêmes lignes Pulsar | 17 |
| Apache Solr | `plugins/grantforge-plugin-solr` | 9.8.1, 9.10.1, 9.11.0, 10.0.0 | Plug-in d'autorisation des nœuds | Testé sur les mêmes lignes Solr | 11 |
| ClickHouse | `plugins/grantforge-plugin-clickhouse` | 24.8, 25.3, 25.8, 26.3, 26.8 | Service qui synchronise rôles et politiques de lignes | Testé sur les mêmes lignes ClickHouse | 17 |
| PostgreSQL | `plugins/grantforge-plugin-postgresql` | 15, 16, 17, 18 | Service qui synchronise rôles, droits et politiques de sécurité des lignes | Testé sur les mêmes versions de PostgreSQL | 17 |
| Apache HBase | `plugins/grantforge-plugin-hbase` | 2.5.16, 2.6.7, 3.0.0 | Coprocesseur des masters et region servers | Testé sur les mêmes lignes HBase | 8 |
| Trino | `plugins/grantforge-plugin-trino` | 470, 476, 479, 484 | Contrôle d'accès système | 470 (470 à 478), 479 (479 et ultérieures) | 23 / 25 |
| MySQL | `plugins/grantforge-plugin-mysql` | 8.0, 8.4 (MySQL 5.7 et les serveurs MariaDB ne sont pas pris en charge) | Proxy réseau autonome | JAR de proxy unique | 17 |
| Apache ZooKeeper | `plugins/grantforge-plugin-zookeeper` | Non répertorié dans les tests du dépôt | Agent dans le serveur | 3.6.4 (cible de compilation) | 8 |

La colonne **Java de l'agent** indique la cible de bytecode de chaque agent. Les agents HDFS se choisissent selon la ligne Hadoop (par exemple `agents/hdfs/2.10/` pour Hadoop 2.10.2) ; voir le [guide de l'agent HDFS](https://grantforge.devlive.org/fr/external/hdfs-agent/). Hadoop 2.7, 2.10, 3.2 et 3.3 ne fournissent pas le callback d'autorisation super-utilisateur, donc sur ces lignes l'accès super-utilisateur reste contrôlé par Hadoop.

## Comment ça marche : deux plans

- **Plan d’administration** : le serveur GrantForge (Spring Boot 4.1, bytecode Java 17) et la console Vue 3 gèrent les locataires, les comptes, l’organisation, les rôles, les autorisations, l’audit, ainsi que les services de données et les politiques.
- **Plan de données** : des agents embarqués dans le système protégé ou déployés comme proxys réseau autonomes. Ils récupèrent avec leur jeton des instantanés de politiques signés en Ed25519, les mettent en cache localement, autorisent l’accès aux ressources et remontent les événements d’audit. Si le serveur est temporairement injoignable, ils conservent l’instantané déjà appliqué ; chaque guide d’agent précise la portée des contrôles.

Votre système n’a pas à copier l’approche de HDFS : une application métier ordinaire évalue les autorisations dans son propre processus via l’API ouverte ou le Spring Boot Starter ; seuls les systèmes qui doivent intercepter l’accès à l’intérieur d’une base de données, d’un système de fichiers ou d’un stockage comparable, ou sur son chemin d’accès réseau ont besoin d’un agent écrit contre `core/grantforge-agent-core` qui s’exécute dans le système cible ou comme proxy réseau.

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
