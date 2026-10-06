---
title: Docker, Compose y Helm
description: Ejecuta GrantForge con imágenes de contenedor, pruébalo con Compose junto a distintas bases de datos y despliégalo en Kubernetes con Helm.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Imágenes

Cada versión publica una imagen `ghcr.io/devlive-community/grantforge:<versión>` (linux/amd64 y linux/arm64), y las versiones estables actualizan además `latest`:

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

La imagen se construye a partir del paquete publicado, se basa en `eclipse-temurin:21-jre`, se ejecuta con un usuario sin privilegios (UID 10001), escribe los registros en la consola y no lleva base de datos integrada. También puedes construirla tú mismo desde el código fuente:

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

Convenciones de la imagen:

| Ruta / variable | Explicación |
| --- | --- |
| `/opt/grantforge/data` | volumen: los archivos de datos de la H2 integrada |
| `/opt/grantforge/plugins` | volumen: plug-ins de tipo de servicio |
| `/opt/grantforge/drivers` | controladores JDBC adicionales (aquí va el MySQL Connector/J) |
| `9999` | puerto del servicio |
| `HEALTHCHECK` | consulta `/actuator/health/readiness` |

## Ejemplo de Compose

`deploy/compose/` incluye un ejemplo para cada base de datos: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, `oracle`.

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

Después abre http://127.0.0.1:9999/. La contraseña de base de datos predeterminada que usan los ejemplos solo sirve para probar; cámbiala con `GRANTFORGE_DB_PASSWORD` antes de pasar a producción. El ejemplo de MySQL necesita que antes pongas `mysql-connector-j-<versión>.jar` en `deploy/compose/drivers/`.

## Helm

`deploy/helm/grantforge` es un chart de Helm: un StatefulSet más una base de datos externa, y cada réplica obtiene su propio número de nodo de ID según el ordinal del Pod (necesita Kubernetes 1.28 o superior).

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

Parámetros habituales:

| Parámetro | Valor predeterminado | Explicación |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / el appVersion del chart | imagen |
| `replicaCount` | `1` | número de réplicas, puede ser mayor que 1 |
| `database.url` / `username` / `password` | — | conexión a la base de datos; se recomienda dejar la contraseña en `existingSecret` |
| `setupToken` | vacío | token de inicialización indicado por adelantado; si está vacío se imprime en el registro |
| `encryptionKey` | vacío | clave Base64 de 32 bytes con la que se cifran las claves guardadas (contraseñas de fuentes de identidad, claves de verificadores, claves privadas de firma, etc.); si está vacía se genera automáticamente y se guarda en la base de datos |
| `cookieSecure` | `false` | ponlo en `true` cuando TLS termine en el Ingress; la cookie de sesión lleva siempre Secure |
| `ingress.*` | desactivado | expone la consola y la API |
| `plugins.persistence.enabled` | `false` | monta un volumen persistente para el directorio de plug-ins |
| `podDisruptionBudget.enabled` | `false` | se recomienda activarlo con varias réplicas |

> [!IMPORTANT]
> En producción, configura siempre `encryptionKey`. Si no la configuras, la clave se guarda en la base de datos y quien consiga una copia de seguridad de la base de datos podrá descifrar las claves que hay cifradas dentro.
