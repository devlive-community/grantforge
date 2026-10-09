---
title: Installer la version publiée
description: Installer, démarrer, arrêter et mettre à niveau la version publiée de GrantForge sur une machine physique ou une machine virtuelle.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Configuration requise

| Élément | Exigence |
| --- | --- |
| Java | 17 ou version ultérieure (la version publiée est compilée pour Java 17 ; Java 21 est recommandé) |
| Mémoire | 1 Go au minimum ; 2 Go ou plus recommandés en production |
| Base de données | H2 intégrée pour un essai ; PostgreSQL, MySQL, MariaDB, Oracle ou SQL Server en production, voir [Bases de données](/fr/deploy/databases/) |
| Navigateur | Chrome, Edge, Firefox ou Safari dans l’une des deux dernières versions majeures |

## Structure des répertoires

Après décompression de `grantforge-release.tar.gz`, on obtient le répertoire `grantforge/` :

| Répertoire | Contenu |
| --- | --- |
| `bin/` | `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` et `import-legacy.sh` |
| `configure/` | `application.properties`, qui permet de surcharger la configuration par défaut |
| `lib/` | les jars du serveur et de ses dépendances |
| `drivers/` | pilotes JDBC supplémentaires (à déposer soi-même pour MySQL) |
| `plugins/` | plug-ins de types de service, voir [Plug-ins et types de service](/fr/develop/plugins/) |
| `agents/` | les jars d’agent à déployer sur les systèmes cibles, par exemple l’[agent NameNode Apache Hadoop HDFS](/fr/external/hdfs-agent/) |
| `data/` | le fichier de la base H2 intégrée (créé au premier démarrage) |
| `logs/` | `grantforge.log` ; `console.out` conserve la sortie produite avant le démarrage du système de journalisation |

## Démarrer et arrêter

```bash
bin/startup.sh     # démarre en arrière-plan et inscrit le numéro de processus dans le fichier pid
bin/shutdown.sh    # arrêt gracieux selon le fichier pid
bin/restart.sh     # arrêt puis redémarrage
bin/debug.sh       # s’exécute au premier plan, la journalisation allant aussi à la console ; Ctrl+C pour arrêter
```

Les scripts peuvent être exécutés depuis n’importe quel répertoire : le répertoire d’installation est le répertoire parent de celui des scripts ; il peut aussi être désigné par la variable d’environnement `GRANTFORGE_HOME`.

## Choisir une base de données

Par défaut, la base fichier H2 située sous `data/grantforge` est utilisée, ce qui convient pour un essai. En production, indiquez la base de données dans `configure/application.properties` ou par variable d’environnement :

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

À la première connexion, GrantForge crée automatiquement toutes les tables avec Liquibase ; à chaque démarrage suivant, les migrations non encore exécutées sont jouées.

## Initialisation

Au premier démarrage, un jeton d’initialisation à usage unique est inscrit dans le journal : ouvrez la console, saisissez ce jeton et créez le premier administrateur. La procédure est décrite dans [Démarrage en cinq minutes](/fr/start/quick-start/).

## Contrôles de santé et supervision

| Adresse | Usage |
| --- | --- |
| `/actuator/health/liveness` | sonde de vivacité |
| `/actuator/health/readiness` | sonde de disponibilité : renvoie 200 lorsque la base de données est joignable et que les migrations sont terminées |
| `/actuator/prometheus` | métriques Prometheus ; connexion requise par défaut, ouverture au réseau de confiance possible avec `GRANTFORGE_PROMETHEUS_PUBLIC=true` |

Pour une journalisation structurée, définissez `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (ou `logstash`). Chaque ligne de journal porte un identifiant de requête, qui correspond au `requestId` des réponses d’erreur de l’API.

## Déploiement en cluster

Plusieurs instances peuvent partager une même base de données et servir en même temps : les sessions sont enregistrées en base, si bien que n’importe quelle instance peut traiter n’importe quelle requête. Chaque instance a besoin d’un `GRANTFORGE_ID_NODE` distinct (0–1023), qui détermine le numéro de nœud utilisé pour générer les identifiants. L’équilibreur de charge n’a pas besoin d’affinité de session.

## Mettre à niveau

Arrêtez le service, remplacez l’ancien `lib/` par celui de la nouvelle version (en conservant `configure/`, `data/`, `drivers/` et `plugins/`), puis redémarrez : les migrations de base de données sont exécutées automatiquement. Sauvegardez la base de données avant la mise à niveau. Pour une mise à niveau depuis la 1.x, voir [Mettre à niveau et migrer depuis d’anciennes versions](/fr/deploy/upgrade/).
