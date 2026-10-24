<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="Logo GrantForge" />

# GrantForge

Plataforma unificada de permisos · usuarios, roles, menús, API, filas y campos de datos · sistemas externos

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · Español · [Português](README.pt-BR.md) · [Italiano](README.it.md)

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

El soporte de sistemas externos incluye el marco de plug-ins, el editor de políticas, la distribución firmada, la auditoría de accesos y plug-ins de servicio para HDFS, ZooKeeper, MySQL, Hive, Kafka, HBase, Trino, YARN y Pulsar. HDFS utiliza agentes NameNode numerados, ZooKeeper un agente integrado en el servidor, MySQL un proxy de red independiente, Hive un autorizador dentro de HiveServer2, Kafka un autorizador dentro de los brokers y controladores, HBase un coprocesador dentro de los masters y region servers, Trino un control de acceso del sistema dentro del coordinator, YARN un proveedor de autorización dentro de los ResourceManagers y Pulsar un proveedor de autorización dentro de los brokers. La conexión, la búsqueda de bases de datos, tablas y columnas y las funciones de las políticas se describen en la [guía del plug-in MySQL](https://grantforge.devlive.org/es/plugins/mysql/); el despliegue y los límites de aplicación, en la [guía del agente proxy de MySQL](https://grantforge.devlive.org/es/external/mysql-agent/). Hive se describe en la [guía del plug-in de Hive](https://grantforge.devlive.org/es/plugins/hive/) y la [guía del agente de Hive](https://grantforge.devlive.org/es/external/hive-agent/). Kafka se describe en la [guía del plug-in de Kafka](https://grantforge.devlive.org/es/plugins/kafka/) y la [guía del agente de Kafka](https://grantforge.devlive.org/es/external/kafka-agent/). HBase se describe en la [guía del plug-in de HBase](https://grantforge.devlive.org/es/plugins/hbase/) y la [guía del agente de HBase](https://grantforge.devlive.org/es/external/hbase-agent/). Trino se describe en la [guía del plug-in de Trino](https://grantforge.devlive.org/es/plugins/trino/) y la [guía del agente de Trino](https://grantforge.devlive.org/es/external/trino-agent/). YARN se describe en la [guía del plug-in de YARN](https://grantforge.devlive.org/es/plugins/yarn/) y la [guía del agente de YARN](https://grantforge.devlive.org/es/external/yarn-agent/). Pulsar se describe en la [guía del plug-in de Pulsar](https://grantforge.devlive.org/es/plugins/pulsar/) y la [guía del agente de Pulsar](https://grantforge.devlive.org/es/external/pulsar-agent/).


## Componentes y versiones compatibles

Las versiones siguientes son las que ejecutan las pruebas de integración Testcontainers del repositorio. Otras versiones no están certificadas.

| Componente | Plugin de servicio | Versiones verificadas | Agente de aplicación | Versiones del agente | Java del agente |
| --- | --- | --- | --- | --- | --- |
| Apache Hadoop HDFS | `plugins/grantforge-plugin-hdfs` | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 | Agente NameNode | 2.7, 2.10, 3.2, 3.3, 3.4, 3.5 | 8 (3.5: 17) |
| Apache Hadoop YARN | `plugins/grantforge-plugin-yarn` | 3.2.4, 3.3.6, 3.4.3, 3.5.0 | Proveedor de autorización de ResourceManager | Probado en las mismas líneas de Hadoop | 8 |
| Apache Hive | `plugins/grantforge-plugin-hive` | HiveServer2 3.1.3, 4.0.1, 4.1.0 | Autorizador de HiveServer2 | Probado en las mismas líneas de HiveServer2 | 8 |
| Apache Kafka | `plugins/grantforge-plugin-kafka` | 3.7.2, 3.9.2, 4.1.2, 4.3.1 | Autorizador de brokers y controladores | Probado en las mismas líneas de Kafka | 8 |
| Apache Pulsar | `plugins/grantforge-plugin-pulsar` | 3.0.17, 3.3.9, 4.0.14, 4.2.5 | Proveedor de autorización de brokers | Probado en las mismas líneas de Pulsar | 17 |
| Apache HBase | `plugins/grantforge-plugin-hbase` | 2.5.16, 2.6.7, 3.0.0 | Coprocesador de masters y region servers | Probado en las mismas líneas de HBase | 8 |
| Trino | `plugins/grantforge-plugin-trino` | 470, 476, 479, 484 | Control de acceso del sistema | 470 (470 a 478), 479 (479 y posteriores) | 23 / 25 |
| MySQL | `plugins/grantforge-plugin-mysql` | 8.0, 8.4 (no se admiten MySQL 5.7 ni servidores MariaDB) | Proxy de red independiente | JAR de proxy único | 17 |
| Apache ZooKeeper | `plugins/grantforge-plugin-zookeeper` | No aparece en las pruebas del repositorio | Agente dentro del servidor | 3.6.4 (destino de compilación) | 8 |

La columna **Java del agente** indica el destino de bytecode de cada agente. Los agentes HDFS se eligen según la línea de Hadoop (por ejemplo, `agents/hdfs/2.10/` para Hadoop 2.10.2); consulta la [guía del agente HDFS](https://grantforge.devlive.org/es/external/hdfs-agent/). Hadoop 2.7, 2.10, 3.2 y 3.3 no proporcionan el callback de autorización de superusuario, así que en esas líneas el acceso de superusuario sigue controlado por Hadoop.

## Cómo funciona: dos planos

- **Plano de administración**: el servidor de GrantForge (Spring Boot 4.1, bytecode de Java 17) y la consola Vue 3 gestionan los inquilinos, las cuentas, la organización, los roles, los permisos y la auditoría, además de los servicios de datos y las políticas.
- **Plano de datos**: agentes integrados en el sistema protegido o desplegados como proxies de red independientes. Obtienen con sus tokens instantáneas de políticas firmadas con Ed25519, las guardan en caché localmente, autorizan el acceso a recursos y envían eventos de auditoría. Si el servidor no está disponible temporalmente, conservan la instantánea ya aplicada; cada guía de agente describe el alcance de los controles.

Tu sistema no tiene que copiar el enfoque de HDFS: una aplicación de negocio ordinaria evalúa los permisos dentro de su propio proceso mediante la API abierta o el Spring Boot Starter; solo los sistemas que deben interceptar el acceso dentro de una base de datos, un sistema de archivos o un almacenamiento comparable, o en su ruta de acceso de red necesitan un agente escrito contra `core/grantforge-agent-core` que se ejecute en el sistema de destino o como proxy de red.

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
