<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="Logo GrantForge" />

# GrantForge

Plataforma unificada de permisos · usuarios, roles, menús, API, filas y campos de datos · sistemas externos

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · Español

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.1.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge (antes AuthX) es una plataforma unificada de permisos de código abierto (MIT). Responde en un solo lugar a dos preguntas: **quién puede hacer qué** (permisos funcionales) y **quién puede ver qué datos** (permisos sobre los datos y los campos). Los permisos se definen, se explican y se auditan en la consola, las aplicaciones de negocio se conectan mediante protocolos estándar, y los sistemas de datos externos (HDFS, por ejemplo) se integran en el mismo modelo de políticas a través de plug-ins y agentes.

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="Consola GrantForge" />
</p>

## Funcionalidades

| Dominio | Funcionalidades |
| --- | --- |
| Identidad y organización | Multi-inquilinos, árbol de departamentos, grupos y puestos; importación y exportación masiva en CSV; inicio de sesión y sincronización con LDAP / Active Directory, federación OIDC |
| Seguridad de las cuentas | Gestión de sesiones y cierre forzado, política de contraseñas con bloqueo por intentos fallidos, verificación en dos pasos con TOTP y códigos de recuperación, segunda validación de las operaciones sensibles |
| Permisos funcionales | Catálogo de recursos (módulos, menús, páginas, pestañas, botones, API), herencia entre roles, matriz de permisos, análisis de impacto antes de concederlos |
| Permisos sobre los datos | Filas visibles acotadas por condiciones (uno mismo, el propio departamento y sus subdepartamentos, departamentos designados, condiciones personalizadas), con la lectura y la escritura controladas por separado |
| Permisos sobre los campos | Un campo puede ocultarse, enmascararse (correo electrónico, número de teléfono, número de documento) o quedar en solo lectura |
| Explicabilidad y auditoría | Explicación de los permisos (de dónde viene cada uno), simulación de una concesión, consulta y exportación del registro de auditoría |
| Gobernanza | Restricciones de separación de funciones (SOD), solicitud de acceso con aprobación, revisión periódica de los permisos |
| Integración de aplicaciones | Servidor de autorización OAuth 2.1 / OIDC, API abierta de consulta de permisos, SDK de Java (Spring Boot Starter) y de JavaScript |
| Sistemas externos | Tipos de servicio por plug-ins y motor de políticas: servicios de datos, políticas de acceso, agentes y auditoría de accesos |
| Entrega | Una sola versión publicada ejecutable, imagen Docker, ejemplos de Compose, chart de Helm; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

El soporte de sistemas externos incluye el marco de plug-ins, el editor de políticas, la distribución firmada, la auditoría de accesos, el tipo de servicio HDFS y agentes NameNode numerados para Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 y 3.5. El plug-in de Hive sigue en desarrollo.

## Versiones objetivo de los agentes HDFS

| Base de Hadoop | Java del contenedor | Directorio del agente |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8 (imagen amd64) | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

Selecciona `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` de `agents/hdfs/<line>/` para la línea Hadoop del clúster. La lógica compartida apunta a Java 8 y el adaptador 3.5 a Java 17.

Hadoop 2.7, 2.10, 3.2 y 3.3 no ofrecen el callback de autorización de superusuario que usa el agente. Hadoop conserva el control de esos accesos; utiliza usuarios normales para acceder a datos sujetos a políticas de GrantForge.

## Cómo funciona: dos planos

- **Plano de administración**: el servidor de GrantForge (Spring Boot 4.1, bytecode de Java 17) y la consola Vue 3 gestionan los inquilinos, las cuentas, la organización, los roles, los permisos y la auditoría, además de los servicios de datos y las políticas.
- **Plano de datos**: agentes integrados en el sistema protegido. Un agente obtiene periódicamente, con su token, instantáneas de políticas firmadas con Ed25519 y las guarda en caché localmente; decide antes de cada acceso si lo permite o lo deniega (denegación por defecto cuando ninguna política es alcanzable) y remite los eventos de acceso al servidor para auditoría.

Tu sistema no tiene que copiar el enfoque de HDFS: una aplicación de negocio ordinaria evalúa los permisos dentro de su propio proceso mediante la API abierta o el Spring Boot Starter; solo los sistemas que deben interceptar el acceso dentro de una base de datos, un sistema de archivos o un almacenamiento comparable necesitan un agente escrito contra `core/grantforge-agent-core` e integrado en el sistema de destino.

## Integrar tu aplicación

- **OAuth 2.1 / OpenID Connect**: GrantForge es en sí mismo un servidor de autorización, las aplicaciones conectan ahí a sus usuarios; las fuentes de identidad existentes (LDAP / AD / OIDC) también pueden integrarse.
- **Aplicaciones Java**: `sdk/grantforge-spring-boot-starter` proporciona `@RequirePermission` para proteger los puntos de entrada, `@GrantForgeEntity` para declarar una entidad de datos, y `GrantForgeDataScopes.scope(...)` para traducir los permisos sobre los datos de la plataforma a una `Specification` JPA.
- **Aplicaciones frontend**: `@grantforge/client` conecta al usuario desde tu propio origen con OIDC + PKCE y consulta sus permisos.
- **API abierta**: `/api/v1/open/me/authorization`, `/api/v1/open/me/data-access`, `/api/v1/open/catalog/data-entities`.
- **Ejemplos ejecutables**: las aplicaciones `shop` y `notes` de `samples/` se integran exactamente como lo haría un tercero.

## Inicio rápido

Se requiere Java 17 o una versión más reciente. El servicio escucha por defecto en el puerto `9999` y muestra en el primer arranque un **token de inicialización** de un solo uso; abre <http://127.0.0.1:9999/> en un navegador, introduce ese token y crea el primer administrador.

```bash
# Con la versión publicada (o compila desde las fuentes con ./mvnw clean package, artefacto en dist/)
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# O con Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# O con Compose y una base de datos
docker compose -f deploy/compose/postgres.yml up -d

# O despliega en Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

La base de datos de archivo H2 integrada se usa por defecto y no requiere ninguna configuración para arrancar. El controlador de MySQL no se distribuye con la versión publicada por su licencia GPL; colócalo tú mismo en `drivers/`. Los pasos detallados de instalación, inicialización y primera concesión de permisos están en la [documentación](https://grantforge.devlive.org).

## Bases de datos

La base de datos de archivo H2 integrada (`${GRANTFORGE_HOME}/data`) se usa por defecto y no requiere ninguna configuración para arrancar. En producción el cambio se hace mediante variables de entorno, y el esquema de tablas lo gestiona Liquibase:

| Base de datos | Versiones (verificadas en CI) | Ejemplo de `GRANTFORGE_DB_URL` |
| --- | --- | --- |
| PostgreSQL | 14, 17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0, 8.4 | `jdbc:mysql://host:3306/grantforge` (coloca tú mismo `mysql-connector-j` en `lib/`; su licencia GPL lo excluye de la versión publicada) |
| MariaDB | 10.11, 11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

Define también `GRANTFORGE_DB_USER` y `GRANTFORGE_DB_PASSWORD`; en una instalación en clúster cada instancia debe recibir su propio `GRANTFORGE_ID_NODE` (0-1023).

## Estructura del proyecto

Coordenada Maven raíz: `org.devlive.grantforge:grantforge:2026.0.0`. Prefijo de los paquetes Java: `org.devlive.grantforge`. Clase de arranque: `org.devlive.grantforge.server.GrantForge`.

`core/` contiene el servidor y la infraestructura compartida, `plugins/` contiene los plug-ins de tipos de servicio que carga el servidor, y `agents/` contiene los agentes desplegados dentro de los sistemas protegidos. La biblioteca compartida `grantforge-agent-core` permanece en `core/`, y el agente NameNode de HDFS está en `agents/grantforge-agent-hdfs-*`.

| Módulo | Responsabilidad |
| --- | --- |
| `core/grantforge-server` | Punto de entrada de Spring Boot: API REST, configuración de seguridad, API abierta, y alojamiento de la consola web |
| `core/grantforge-web` | Consola de administración en Vue 3 / TypeScript / Tailwind CSS |
| `core/grantforge-common` | Códigos de error y modelo problem details, CSV, anotaciones de acceso a los puntos de entrada |
| `core/grantforge-persistence` | Entidades, filtrado por inquilino, TSID, Liquibase, SPI de permisos sobre los datos y los campos |
| `core/grantforge-audit` | Registro, consulta, conservación y archivo de los eventos de auditoría |
| `core/grantforge-identity` | Inquilinos, cuentas, departamentos, grupos, puestos, inicio de sesión y sesiones, verificación en dos pasos, fuentes de identidad |
| `core/grantforge-authz` | Catálogo de recursos, roles, permisos, concesiones y evaluación, políticas sobre los datos y los campos, separación de funciones, solicitudes y revisiones |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | Contrato de los plug-ins de tipos de servicio, además de la carga, el aislamiento y la invocación de los plug-ins |
| `core/grantforge-policy-engine` | Motor de evaluación de políticas de sistemas externos (API de Java 8, integrable en un agente) |
| `core/grantforge-agent-core` | Código común de los agentes: ajustes, instantáneas firmadas, decisiones de acceso, envío de auditoría |
| `core/grantforge-service` | Servicios de datos, firma y distribución de instantáneas de políticas, agentes y auditoría de accesos |
| `core/grantforge-oauth` | Servidor OAuth 2.1 / OIDC construido sobre Spring Authorization Server |
| `plugins/grantforge-plugin-hdfs` | Plug-in del tipo de servicio HDFS: gestión de políticas y consulta de recursos |
| `plugins/grantforge-plugin-example` | Plug-in de ejemplo para un tipo de servicio personalizado |
| `agents/grantforge-agent-hdfs-common` | Lógica compartida de autorización HDFS, configuración, instantáneas y auditoría (Java 8) |
| `agents/grantforge-agent-hdfs-*` | Agentes NameNode numerados para Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 y 3.5: autorización y auditoría de accesos |
| `sdk/grantforge-spring-boot-starter`, `sdk/grantforge-js` | SDK de Java y de JavaScript para la integración de aplicaciones |
| `script/ci`, `deploy/` | Scripts de verificación de CI (los mismos en local y en CI) y recursos de despliegue (Dockerfile, Compose, Helm) |

## Operación y observabilidad

- Sondas de salud: `/actuator/health/liveness`, `/actuator/health/readiness` (solo el estado, sin detalles; la sonda de disponibilidad devuelve 200 cuando la base de datos es alcanzable y las migraciones han terminado).
- Métricas: `/actuator/prometheus` (con la etiqueta `application="grantforge"`, requieren inicio de sesión por defecto; `GRANTFORGE_PROMETHEUS_PUBLIC=true` las abre a redes de confianza).
- Registros: por defecto texto legible, con un identificador de petición en cada línea; define `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (o `logstash`) para obtener registros JSON.
- Scripts de la versión publicada: `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` e `import-legacy.sh` en `bin/`.

## Desarrollo y verificación

La compilación requiere JDK 17 o posterior. El servidor y el adaptador Hadoop 3.5 apuntan a Java 17; el motor de políticas, el núcleo del agente y los adaptadores Hadoop 2.7–3.4 a Java 8. Error Prone + NullAway se activan desde JDK 21. El frontend usa Vue 3.5, Tailwind CSS 4, Node.js 22.12+ y pnpm 8.10.2.

```sh
# Compilación Java y pruebas unitarias (se omite la compilación del frontend)
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# Pruebas de integración de persistencia sobre una base de datos concreta (requiere Docker, salvo h2)
bash script/ci/db_integration.sh postgres:17

# Empaquetado de la versión publicada (incluida la compilación del frontend), salida en dist/
./mvnw clean package

# Desarrollo y verificaciones del frontend
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# Aplicaciones de ejemplo y SDK
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# Contrato de API: regenera openapi.json y los tipos del frontend tras un cambio en el servidor (la CI comprueba ambos)
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# Sitio de documentación (docs/, Next.js + Tailwind CSS)
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # regenera las capturas con un servicio real y datos de ejemplo

# Verificaciones del repositorio (las mismas que la CI)
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## Enlaces

- [Repositorio del proyecto](https://github.com/devlive-community/grantforge)
- [Documentación](https://grantforge.devlive.org): inicio rápido, guía de uso, integración y referencia técnica, con las fuentes en [`docs/`](docs/)
- [Guía de contribución](CONTRIBUTING.md) · [Código de conducta](CODE_OF_CONDUCT.md) · [Registro de cambios](CHANGELOG)
