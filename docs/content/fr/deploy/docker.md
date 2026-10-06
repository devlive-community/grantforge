---
title: Docker, Compose et Helm
description: Exécuter GrantForge avec une image conteneur, l’essayer avec Compose accompagné de différentes bases de données, et le déployer sur Kubernetes avec Helm.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Image

Chaque version publiée est accompagnée d’une image `ghcr.io/devlive-community/grantforge:<version>` (linux/amd64 et linux/arm64) ; les versions stables mettent aussi à jour `latest` :

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

L’image est construite à partir de la version publiée, s’appuie sur `eclipse-temurin:21-jre`, s’exécute sous un utilisateur non privilégié (UID 10001), écrit sa journalisation sur la console et n’embarque aucune base de données. Vous pouvez aussi la construire vous-même depuis les sources :

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

Conventions de l’image :

| Chemin / variable | Description |
| --- | --- |
| `/opt/grantforge/data` | volume : fichiers de données de la base H2 intégrée |
| `/opt/grantforge/plugins` | volume : plug-ins de types de service |
| `/opt/grantforge/drivers` | pilotes JDBC supplémentaires (MySQL Connector/J se dépose ici) |
| `9999` | port du service |
| `HEALTHCHECK` | interroge `/actuator/health/readiness` |

## Exemple Compose

`deploy/compose/` fournit un exemple par base de données : `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, `oracle`.

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

Ouvrez ensuite http://127.0.0.1:9999/. Le mot de passe par défaut de la base utilisé par l’exemple ne convient que pour un essai : modifiez-le via `GRANTFORGE_DB_PASSWORD` avant tout usage réel. L’exemple MySQL exige de déposer au préalable `mysql-connector-j-<version>.jar` dans `deploy/compose/drivers/`.

## Helm

`deploy/helm/grantforge` est un chart Helm : un StatefulSet accompagné d’une base externe, chaque réplique recevant son propre numéro de nœud d’ID selon son rang de pod (Kubernetes 1.28 ou version ultérieure requis).

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

Paramètres courants :

| Paramètre | Valeur par défaut | Description |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / appVersion du chart | image |
| `replicaCount` | `1` | nombre de répliques, peut dépasser 1 |
| `database.url` / `username` / `password` | — | connexion à la base ; le mot de passe est de préférence placé dans `existingSecret` |
| `setupToken` | vide | jeton d’initialisation prédéfini ; lorsqu’il est vide, il est inscrit dans le journal |
| `encryptionKey` | vide | clé Base64 de 32 octets protégeant les secrets conservés chiffrés (mots de passe des sources d’identité, clés des vérificateurs, clés privées de signature, etc.) ; générée automatiquement et enregistrée en base lorsqu’elle est vide |
| `cookieSecure` | `false` | à mettre à `true` lorsque TLS se termine au niveau de l’entrée ; le cookie de session porte alors toujours l’attribut Secure |
| `ingress.*` | désactivé | expose la console et l’API |
| `plugins.persistence.enabled` | `false` | monte un volume persistant pour le répertoire des plug-ins |
| `podDisruptionBudget.enabled` | `false` | recommandé dès plusieurs répliques |

> [!IMPORTANT]
> En production, définissez impérativement `encryptionKey`. Sans elle, la clé est enregistrée dans la base de données : quiconque obtient une sauvegarde de la base peut déchiffrer les secrets qu’elle protège.
