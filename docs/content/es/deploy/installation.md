---
title: Instalar la versión publicada
description: Instala, arranca, detén y actualiza la versión publicada de GrantForge en una máquina física o virtual.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Requisitos del entorno

| Elemento | Requisito |
| --- | --- |
| Java | 17 o superior (la versión publicada está compilada con Java 17; se recomienda 21) |
| Memoria | 1 GB como mínimo; para producción, 2 GB o más |
| Base de datos | H2 integrada para probar; en producción, PostgreSQL, MySQL, MariaDB, Oracle o SQL Server; consulta [Bases de datos](/es/deploy/databases/) |
| Navegadores | Chrome, Edge, Firefox o Safari en sus dos últimas versiones principales |

## Estructura de carpetas

Al descomprimir `grantforge-release.tar.gz` se obtiene la carpeta `grantforge/`:

| Carpeta | Contenido |
| --- | --- |
| `bin/` | `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` e `import-legacy.sh` |
| `configure/` | `application.properties`, donde se sobrescriben los valores por defecto |
| `lib/` | los jar del servidor y sus dependencias |
| `drivers/` | controladores JDBC adicionales (el de MySQL hay que añadirlo a mano) |
| `plugins/` | plug-ins de tipo de servicio; consulta [Plug-ins y tipos de servicio](/es/develop/plugins/) |
| `agents/` | los jar de agente que se despliegan en los sistemas de destino, como el [agente NameNode de HDFS](/es/external/hdfs-agent/) |
| `data/` | el archivo de la base de datos H2 integrada (se crea en el primer arranque) |
| `logs/` | `grantforge.log`; `console.out` guarda la salida anterior al arranque del sistema de registro |

## Arrancar y detener

```bash
bin/startup.sh     # arranca en segundo plano y escribe el pid en su archivo
bin/shutdown.sh    # detiene con elegancia usando el archivo pid
bin/restart.sh     # detiene y vuelve a arrancar
bin/debug.sh       # se ejecuta en primer plano, con los registros también en consola; Ctrl+C lo detiene
```

Los scripts se pueden ejecutar desde cualquier carpeta: la carpeta de instalación es la que está un nivel por encima de la del script, aunque también se puede indicar con la variable de entorno `GRANTFORGE_HOME`.

## Elegir la base de datos

Por defecto se usa la base de datos de archivo H2 en `data/grantforge`, que basta para probar. En producción, indica la base de datos en `configure/application.properties` o con variables de entorno:

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

En la primera conexión, GrantForge crea todas las tablas con Liquibase; después, cada arranque ejecuta las migraciones que falten.

## Primera inicialización

En el primer arranque se imprime en el registro un token de inicialización de un solo uso: abre la consola, introduce el token y crea el primer administrador. El proceso está en [Primeros cinco minutos](/es/start/quick-start/).

## Comprobación de salud y monitorización

| Dirección | Uso |
| --- | --- |
| `/actuator/health/liveness` | sonda de vida |
| `/actuator/health/readiness` | sonda de preparación: devuelve 200 cuando la base de datos está disponible y las migraciones se han aplicado |
| `/actuator/prometheus` | métricas de Prometheus; por defecto exige iniciar sesión, pero se puede abrir a una red de confianza con `GRANTFORGE_PROMETHEUS_PUBLIC=true` |

Si necesitas registros estructurados, define `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (o `logstash`). Cada línea lleva el ID de solicitud, que se corresponde con el `requestId` de las respuestas de error de las interfaces.

## Despliegue en clúster

Varias instancias pueden compartir una misma base de datos y dar servicio a la vez: las sesiones se guardan en la base de datos, así que cualquier instancia puede atender cualquier petición. Cada instancia necesita un `GRANTFORGE_ID_NODE` distinto (de 0 a 1023), que decide el número de nodo que se usa al generar los ID. El equilibrador de carga no necesita afinidad de sesión.

## Actualizar

Detén el servicio, sustituye el `lib/` antiguo por el de la nueva versión (conserva `configure/`, `data/`, `drivers/` y `plugins/`), vuelve a arrancar y las migraciones de la base de datos se ejecutan solas. Haz una copia de seguridad de la base de datos antes de actualizar. Para pasar de 1.x, consulta [Actualizar y migrar versiones antiguas](/es/deploy/upgrade/).
