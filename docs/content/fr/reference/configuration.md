---
title: Référence de configuration
description: Toutes les clés de configuration, leurs valeurs par défaut et les variables d’environnement correspondantes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

La configuration peut être écrite dans `configure/application.properties` ou surchargée par des variables d’environnement. Les règles de liaison souple de Spring Boot s’appliquent également : `grantforge.security.mfa.step-up-window` peut s’écrire sous forme de variable d’environnement `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`. Les durées s’écrivent sous la forme `30m`, `12h`, `90d`.

## Services et base de données

| Clé de configuration | Variable d’environnement | Valeur par défaut | Description |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | Port HTTP |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | Base de fichiers H2 intégrée | Adresse JDBC, voir [Bases de données](/fr/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | Utilisateur de la base de données |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | vide | Mot de passe de la base de données |
| — | `GRANTFORGE_HOME` | Répertoire d’installation | Répertoire contenant les données et les journaux H2 |
| — | `GRANTFORGE_ID_NODE` | automatique | Numéro de nœud unique par instance dans le cluster (0–1023) |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | Taille maximale des fichiers d’import CSV |

## Initialisation et inscription

| Clé de configuration | Valeur par défaut | Description |
| --- | --- | --- |
| `grantforge.setup.token` | vide | Jeton d’initialisation fixe (`GRANTFORGE_SETUP_TOKEN`) ; lorsqu’il est vide, un jeton aléatoire est généré et affiché dans le journal |
| `grantforge.security.registration-enabled` | `false` | Si les visiteurs sont autorisés à s’inscrire eux-mêmes |
| `grantforge.security.registration-tenant` | `default` | Locataire auquel appartiennent les comptes auto-inscrits |

## Mots de passe et verrouillage

| Clé de configuration | Valeur par défaut | Description |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | Longueur minimale, au moins 8 |
| `grantforge.security.password.max-length` | `128` | Longueur maximale, au plus 1024 |
| `grantforge.security.password.required-character-classes` | `1` | Nombre de catégories de caractères à panacher (minuscules, majuscules, chiffres, autres), 1–4 |
| `grantforge.security.password.history-size` | `0` | Le nouveau mot de passe ne peut pas être identique aux précédents, 0–24 |
| `grantforge.security.password.max-age` | pas d’expiration | Durée de validité du mot de passe ; après expiration, il doit être modifié à la connexion |
| `grantforge.security.lockout.max-attempts` | `5` | Nombre d’échecs consécutifs avant verrouillage |
| `grantforge.security.lockout.duration` | `15m` | Durée du verrouillage |

Le mot de passe ne peut pas contenir le nom d’utilisateur.

## Sessions et cookies

| Clé de configuration | Valeur par défaut | Description |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | Délai d’inactivité des sessions (`GRANTFORGE_SESSION_TIMEOUT`) |
| `grantforge.security.sessions.max-per-account` | `0` | Nombre maximal de sessions simultanées par compte, 0 pour illimité |
| `grantforge.security.sessions.activity-interval` | `1m` | Intervalle d’enregistrement de l’activité récente des sessions |
| `grantforge.security.cookie-secure` | `false` | À définir sur `true` lorsque TLS se termine au niveau du proxy (`GRANTFORGE_COOKIE_SECURE`) |

## Authentification à deux facteurs

| Clé de configuration | Valeur par défaut | Description |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | Durée pendant laquelle une authentification à deux facteurs couvre les opérations sensibles, de 1 minute à 12 heures |
| `grantforge.security.mfa.required-for-sensitive` | `false` | Si les opérations sensibles exigent que le compte ait activé l’authentification à deux facteurs |

## Chiffrement et serveur d’autorisation

| Clé de configuration | Valeur par défaut | Description |
| --- | --- | --- |
| `grantforge.security.encryption-key` | générée automatiquement | Clé Base64 de 32 octets pour le chiffrement des éléments stockés, à définir impérativement en production |
| `grantforge.oauth.issuer` | adresse de la requête | Émetteur OIDC, par exemple `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | Cycle de rotation automatique des clés de signature, 0 pour désactiver |
| `grantforge.oauth.signing-key-retention` | `2d` | Durée pendant laquelle les anciennes clés restent publiées, doit dépasser la validité de tout jeton |

## Audit, plug-ins et agents

| Clé de configuration | Valeur par défaut | Description |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | Durée de conservation du journal d’audit |
| `grantforge.audit.archive-directory` | vide | Répertoire vers lequel les audits expirés sont archivés avant suppression |
| `grantforge.access-audit.retention` | `90d` | Durée de conservation des audits d’accès remontés par les agents |
| `grantforge.plugins.directory` | `plugins` | Répertoire des plug-ins |
| `grantforge.plugins.call-timeout` | `10s` | Délai d’attente des appels de plug-ins (test de connexion, recherche de ressources) |
| `grantforge.agents.refresh-interval` | `30s` | Intervalle conseillé de récupération des politiques par les agents |

## Observabilité

| Clé de configuration | Valeur par défaut | Description |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | Si `/actuator/prometheus` est accessible sans connexion (`GRANTFORGE_PROMETHEUS_PUBLIC`) |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | À définir sur `ecs` ou `logstash` pour produire des journaux JSON |
