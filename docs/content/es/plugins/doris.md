---
title: Apache Doris
description: Instalar y configurar el plug-in de servicio de Apache Doris, llegar a uno o varios frontends, iniciar sesión con una contraseña, buscar catálogos, bases de datos, tablas, columnas y grupos de carga de trabajo, y gestionar quién puede leer, cargar y administrar qué, hasta columnas y filas, y quién puede ejecutar consultas en qué grupo de carga de trabajo.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Apache Doris busca catálogos, bases de datos, tablas, columnas y grupos de carga de trabajo, y gestiona las políticas de acceso en los términos propios de Doris: leer columnas, cargar, alterar y eliminar tablas y mostrar la definición de sus vistas, crear tablas en una base de datos y eliminarla, crear bases de datos en un catálogo y ejecutar consultas en un grupo de carga de trabajo, con filtros de filas de tablas. El ID del plug-in y el tipo de servicio son ambos `doris`. El plug-in de servicio se ejecuta en el servidor de GrantForge; el [agente de Apache Doris](/es/external/doris-agent/), un servicio junto al clúster, aplica las políticas con los roles, privilegios y políticas de filas propios de Doris.

Las pruebas de integración con Testcontainers del repositorio cubren Apache Doris `2.1`, `3.1`, `4.0` y `4.1`, cada uno en un clúster de un frontend y un backend; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/doris`. Confirme en **Administración de la plataforma → Plug-ins** que `doris` está activado; vuelva a explorar el directorio de plug-ins tras actualizarlo. El plug-in habla con el protocolo MySQL de los frontends mediante MariaDB Connector/J, en un cargador de clases propio.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y seleccione Apache Doris (`doris`).
2. Introduzca la dirección del protocolo MySQL de un frontend, o de varios.
3. Introduzca el nombre y la contraseña del usuario de búsqueda, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el clúster: la versión de Doris, el usuario con el que inició sesión, cuántos catálogos ve y cuántas bases de datos del catálogo interno. Solo pregunta al frontend, así que responde aunque no haya ningún backend en marcha.

El usuario de búsqueda permite a GrantForge descubrir recursos; es distinto de los usuarios que leen y cargan. Una prueba de conexión correcta no significa que el acceso ya esté sujeto a políticas.

| Ajuste | Propósito |
| --- | --- |
| `doris.hosts` | Obligatorio: la dirección del protocolo MySQL de un frontend, por ejemplo `fe1.example.com:9030`; varias separadas por comas, probadas por turno cuando una no responde. Sin puerto, es `9030` |
| `username` | Obligatorio: el usuario de búsqueda |
| `password` | La contraseña del usuario de búsqueda; guardada cifrada |
| `ssl.mode` | `disable` (por defecto), `trust` para cifrar sin verificar el frontend, `verify-ca` para verificar su certificado o `verify-full` para verificar también su nombre; para frontends que ofrecen TLS en el protocolo MySQL |
| `ssl.root.certificate` | verify-ca y verify-full: el certificado CA PEM con el que se verifican los frontends, en el **servidor de GrantForge**; vacío usa los de la JVM |
| `timeout` | Cuánto pueden durar la conexión y una sentencia, por defecto `30000`, rango `1..600000` milisegundos |

Los inicios de sesión rechazados, un usuario de búsqueda sin el privilegio necesario y los frontends que no responden se informan como tales.

### Qué ve el usuario de búsqueda

Las sentencias `SHOW` de Doris listan solo los catálogos, bases de datos, tablas y columnas sobre los que un usuario tiene algún privilegio, así que una búsqueda muestra lo que el usuario de búsqueda puede ver. Dele `SELECT_PRIV` sobre aquello para lo que se escriban políticas, por ejemplo `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`; `SHOW WORKLOAD GROUPS` lista los grupos que puede ver. Las bases de datos propias de Doris, `information_schema`, `mysql` y `__internal_schema`, se omiten: las políticas no las deciden.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `catalog` | Comodines, distingue mayúsculas; el catálogo interno o uno externo | `create_database` |
| `database` | Comodines, distingue mayúsculas; bajo un catálogo | `create`, `drop_database` |
| `table` | Comodines, distingue mayúsculas; bajo una base de datos, una tabla o vista | `load`, `alter`, `drop`, `show_view` |
| `column` | Comodines, sin distinguir mayúsculas, como Doris compara los nombres de columna; bajo una tabla | `select` |
| `workload_group` | Comodines, distingue mayúsculas; el nombre del grupo | `usage` |

| Tipo de acceso | Privilegio de Doris |
| --- | --- |
| `select` | `SELECT_PRIV`, sobre columnas, o sobre una tabla, base de datos o catálogo completos |
| `load` | `LOAD_PRIV`: cargas, `INSERT`, `UPDATE`, `DELETE` y `TRUNCATE` |
| `alter`, `drop` | `ALTER_PRIV`, `DROP_PRIV` sobre la tabla |
| `show_view` | `SHOW_VIEW_PRIV`: `SHOW CREATE VIEW` |
| `create` | `CREATE_PRIV` sobre la base de datos: crearla, y crear tablas y vistas en ella |
| `drop_database` | `DROP_PRIV` sobre la base de datos |
| `create_database` | `CREATE_PRIV` sobre el catálogo |
| `usage` | `USAGE_PRIV` sobre el grupo de carga de trabajo |

La lectura se decide por columna, como Doris la concede: una política `select` nombra columnas, `*` para todas. Así una política puede permitir una tabla y denegar una de sus columnas, y el usuario puede leer todas las demás. En los valores de las políticas, `*` coincide con cualquier secuencia de caracteres y `?` con uno. El editor de políticas sugiere los catálogos, las bases de datos de los catálogos que nombra la política, sus tablas y columnas, y los grupos de carga de trabajo.

El privilegio de Doris sobre una base de datos alcanza también a cada tabla de ella, y sobre un catálogo a cada base de datos de él. Por eso el agente concede `create_database` solo donde las políticas también dejan al usuario crear tablas en cada base de datos del catálogo, y `drop_database` solo donde también le dejan eliminar cada tabla de la base de datos; si no, retiene el privilegio y lo dice, en lugar de conceder más de lo que permiten las políticas. `ADMIN_PRIV`, `GRANT_PRIV` y `NODE_PRIV` no son tipos de acceso: ninguna política los da.

Doris enmascara valores solo a través de Apache Ranger, por lo que este tipo de servicio no ofrece enmascaramiento.

### Filtros de filas

Una política de filtro de filas de una tabla muestra a sus usuarios las filas para las que se cumple una condición. La condición es un predicado de Doris, tal como lo toman las políticas de filas, por ejemplo `region = 'EU'`; el agente lo ejecuta antes sobre la tabla, y un filtro que no puede ejecutarse allí no muestra ninguna fila. Como en otros servicios, un usuario recibe el filtro de la primera política que se le aplica.

## Ejemplo de políticas

Supongamos que el clúster tiene las bases de datos `sales` y `hr` en su catálogo interno y un grupo de carga de trabajo `etl`. Cree estas políticas habilitadas en el mismo servicio de datos:

| Recursos | Regla |
| --- | --- |
| `catalog=internal`, `database=sales`, `table=*`, `column=*` | Permitir `select` al grupo `analysts` |
| `catalog=internal`, `database=sales`, `table=orders`, `column=card` | Denegar `select` al grupo `analysts` |
| `catalog=internal`, `database=sales`, `table=orders` | Permitir `load` al usuario `ingest` |
| `catalog=internal`, `database=team_*` | Permitir `create` al grupo `data-team` |
| `catalog=internal`, `database=team_*`, `table=*` | Permitir `load`, `alter` y `drop` al grupo `data-team` |
| `workload_group=etl` | Permitir `usage` al usuario `ingest` y al grupo `data-team` |
| `catalog=internal`, `database=sales`, `table=orders` (filtro de filas) | Mostrar al usuario `emea` las filas `region = 'EMEA'` |

Una vez que el agente aplica la nueva instantánea, los miembros de `analysts` leen cada tabla de `sales` salvo los números de tarjeta de `orders`; `ingest` carga `orders` y puede ejecutar sus cargas en `etl`; los miembros de `data-team` crean tablas en las bases de datos `team_` que creó un administrador, y las cargan, alteran y eliminan; y `emea`, si una política le deja leer `orders`, ve solo las filas de su región.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Aplicarlas requiere el agente de Apache Doris, uno por clúster. El despliegue y las comprobaciones están en [Agente de Apache Doris](/es/external/doris-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de Apache Doris](/es/external/doris-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
