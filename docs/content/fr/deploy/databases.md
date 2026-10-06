---
title: Bases de données
description: Bases de données et versions prises en charge, modes de connexion, pilotes et points d’attention selon les bases.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Périmètre pris en charge

| Base de données | Versions vérifiées | Pilote |
| --- | --- | --- |
| H2 | embarquée avec chaque version | intégré, recommandé pour un essai uniquement |
| PostgreSQL | 14, 17 | intégré |
| MySQL | 8.0, 8.4 | à déposer soi-même dans `drivers/` (Connector/J est sous licence GPL et n’est pas distribué avec la version publiée) |
| MariaDB | 10.11, 11.4 | intégré |
| Oracle | Free 23 | intégré |
| SQL Server | 2022 | intégré |

Chaque version est validée en CI par une « initialisation sur base vide + exécution de tous les tests d’intégration ». Les bases de données chinoises (DM, Kingbase, openGauss, OceanBase, etc.) ne sont pas prises en charge.

## Exemples de connexion

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle (nom de service)
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

Le nom d’utilisateur et le mot de passe se définissent respectivement avec `GRANTFORGE_DB_USER` et `GRANTFORGE_DB_PASSWORD`. La base doit être créée au préalable et le compte doit disposer du droit de créer des tables : au premier démarrage, GrantForge crée toutes les tables avec Liquibase, et les mises à niveau suivantes sont migrées automatiquement par Liquibase. Hibernate se contente de vérifier la structure des tables et ne la modifie jamais.

Sur PostgreSQL, GrantForge tente d’activer l’extension `pg_trgm` et de créer un index trigramme sur le nom de connexion, le nom d’affichage et l’adresse e-mail des comptes, afin que la recherche « contient » sur un million de comptes reste dans les quelques dizaines de millisecondes. Depuis PostgreSQL 13, il s’agit d’une extension de confiance que le propriétaire de la base peut activer ; si le compte ne dispose pas de ce droit, le service démarre normalement, la recherche se faisant alors par balayage complet de la table, et l’index est reconstruit automatiquement au démarrage suivant après qu’un administrateur a exécuté `CREATE EXTENSION pg_trgm`.

## Jeux de caractères

- **MySQL / MariaDB** : utilisez le jeu de caractères `utf8mb4` à la création de la base, sans quoi les caractères chinois et les émoticônes ne sont pas conservés intégralement.
- **SQL Server, Oracle** : utilisez `NVARCHAR` pour les colonnes de texte susceptibles de contenir du chinois ; les textes longs sont en `NVARCHAR(MAX)` sur SQL Server et en `CLOB` sur Oracle, indépendamment du jeu de caractères par défaut de la base.
- **Oracle** : une chaîne vide est traitée comme `NULL` ; GrantForge traite uniformément les valeurs vides comme « non renseignées » dans la couche métier, ce qui donne un comportement identique aux autres bases.

## Sauvegarde et restauration

Toutes les données métier se trouvent en base (les sessions également) : il suffit de sauvegarder la base ; si vous utilisez des plug-ins, sauvegardez aussi `plugins/`. Lorsque `grantforge.security.encryption-key` n’est pas défini, la clé de chiffrement est elle aussi enregistrée en base et la restauration de la sauvegarde suffit à déchiffrer ; si la clé est définie, elle doit être conservée avec le même soin.
