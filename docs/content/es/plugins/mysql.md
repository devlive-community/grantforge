---
title: MySQL
description: Instala y configura el plug-in de servicio MySQL, consulta bases de datos, tablas y columnas, y gestiona políticas de acceso, enmascaramiento y filtrado de filas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de MySQL ofrece en GrantForge la consulta de bases de datos, tablas y columnas, y políticas de acceso, enmascaramiento y filtrado de filas. Su ID de plug-in y su tipo de servicio son ambos `mysql`. El plug-in de servicio se ejecuta en el servidor de GrantForge; un [agente proxy de MySQL](/es/external/mysql-agent/) independiente comprueba el SQL de las aplicaciones.

La prueba de conexión acepta MySQL 8.0 y posteriores. Las pruebas de integración de Testcontainers del repositorio cubren `mysql:8.0` y `mysql:8.4`; esto no certifica todas las demás versiones. Los servidores MariaDB y MySQL 5.7 quedan fuera del alcance compatible. `jdbc:mariadb://` es un formato de URL del controlador y no implica compatibilidad con servidores MariaDB.

## Instalación

La distribución incluye el plug-in en `plugins/mysql`. Comprueba en **Administración de la plataforma → Plug-ins** que `mysql` está activado; tras actualizarlo, vuelve a analizar el directorio de plug-ins. El plug-in habla con MySQL mediante MariaDB Connector/J (LGPL), que entiende el protocolo de MySQL, incluidos `caching_sha2_password` y TLS, en su propio cargador de clases. El Connector/J propio de MySQL es GPL y no se distribuye.

## Añadir un servicio de datos

1. Abre **Permisos de datos → Servicios de datos**, añade un servicio y elige MySQL (`mysql`).
2. Escribe la URL JDBC del servidor, por ejemplo `jdbc:mysql://mysql.example.com:3306`. `jdbc:mariadb://` funciona igual, y también las formas de conmutación por error del controlador, como `jdbc:mysql:replication://primary:3306,replica:3306`.
3. Escribe el usuario de consulta y su contraseña, prueba la conexión y guarda el servicio. La prueba inicia sesión, comprueba la versión del servidor y lee `information_schema` como lo hacen las consultas.

El usuario de consulta permite a GrantForge descubrir recursos y es distinto de los usuarios con los que los clientes de negocio inician sesión en MySQL. Una prueba de conexión correcta no significa que el SQL de las aplicaciones ya esté sujeto a las políticas.

| Ajuste | Función |
| --- | --- |
| `jdbc.url` | URL JDBC obligatoria: `jdbc:mysql://host[:port]` o `jdbc:mariadb://…`, con base de datos y `?options` opcionales; las credenciales no van en la URL |
| `username` | Usuario de consulta obligatorio |
| `password` | Contraseña del usuario de consulta; se guarda cifrada |
| `jdbc.properties` | Opciones del controlador, una `key=value` por línea, como `sslMode=verify-full` y `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | Tiempo de espera de conexión, `10000` por defecto, rango `1..600000` |
| `read.timeout.ms` | Tiempo de espera de lectura de una consulta, `30000` por defecto, rango `1..600000` |

Las opciones que harían leer archivos locales al servidor, ejecutar sentencias al conectar, permitir varias sentencias a la vez, llevar credenciales o cargar clases se rechazan tanto en la URL como en `jdbc.properties`, sin importar mayúsculas: `user`, `password`, `allowLocalInfile`, `allowMultiQueries`, `initSql`, `createDatabaseIfNotExist`, `socketFactory`, `credentialType` y las opciones de pool. Las conexiones de consulta son de solo lectura.

Para usar TLS con verificación de la identidad del servidor, introduce lo siguiente en `jdbc.properties`:

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

El archivo de certificado debe poder leerse en el **servidor de GrantForge**, y el nombre de host de la URL debe coincidir con el certificado. El plug-in no exige TLS de forma predeterminada; configúralo explícitamente. Estos nombres de opciones son los de MariaDB Connector/J.

`information_schema` solo muestra los objetos sobre los que el usuario tiene privilegios. Por ejemplo, para descubrir recursos únicamente en `sales`, un administrador de base de datos puede conceder estos permisos. Sustituye `grantforge-host` por la dirección de origen del servidor de GrantForge:

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

Usa `REFERENCES ON *.*` solo si necesitas descubrir todas las bases de datos. La cuenta de metadatos del proxy también requiere `SHOW_ROUTINE`; consulta la guía del agente.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Notas |
| --- | --- | --- |
| `database` | Comodines, distingue mayúsculas | Las políticas pueden terminar aquí |
| `table` | Comodines, distingue mayúsculas | Bajo una base de datos; las políticas pueden terminar aquí |
| `column` | Comodines, sin distinguir mayúsculas | Bajo una tabla; tipos de acceso `select`, `insert` y `update` |

Bases de datos y tablas se comparan distinguiendo mayúsculas, como MySQL las guarda en Linux. En un servidor con `lower_case_table_names` en 1 o 2, el agente pasa a minúsculas los nombres que comprueba, así que escribe esas políticas en minúsculas. Los nombres de columna siempre se comparan sin distinguir mayúsculas, como en MySQL.

Los tipos de acceso siguen los privilegios de MySQL: `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `index` y `all`, que incluye estos ocho tipos. `all` no equivale a `ALL PRIVILEGES` de MySQL ni elimina las restricciones del proxy sobre las sentencias.

En los valores de política, `*` coincide con cualquier cantidad de caracteres y `?` con un carácter. El editor sugiere nombres por prefijo: bases de datos, tablas de las bases que nombra la política y columnas de sus tablas. `%` y `_` en la entrada de sugerencias son caracteres literales.

## Enmascaramiento

Las políticas de enmascaramiento se aplican a columnas. Cada tipo de máscara es una expresión de MySQL 8 en la que `{col}` representa la columna:

| Tipo de máscara | Muestra |
| --- | --- |
| `mask` | Letras ASCII como `X`/`x` y dígitos como `n`; los demás caracteres se conservan |
| `mask_show_last_4` | Solo los últimos cuatro caracteres |
| `mask_show_first_4` | Solo los primeros cuatro caracteres |
| `mask_hash` | Un hash SHA-256 sin sal |
| `mask_null` | `NULL` |
| `mask_none` | El valor sin cambios |
| `mask_date_show_year` | El 1 de enero del año de la fecha |
| `custom` | Una expresión de MySQL propia, con `{col}` para la columna, como `CONCAT('****', RIGHT({col}, 4))` |

Las máscaras que muestran los primeros o los últimos cuatro caracteres conservan el valor completo si no supera los cuatro caracteres. Por ejemplo, `mask_show_last_4` muestra `4111111111111111` como `xxxxxxxxxxxx1111`.

## Filtros de filas

Las políticas de filtrado de filas se aplican a tablas. Un filtro es una condición de MySQL sobre las columnas de la tabla, como `region = 'EU'`: el usuario solo ve las filas que la cumplen, y las sentencias `UPDATE` y `DELETE` sobre una sola tabla solo modifican esas filas. Se rechazan las escrituras sobre varias tablas cuando el destino requiere un filtro de filas. Consulta la guía del agente para otras restricciones.

Las máscaras y los filtros de filas no conceden acceso. Una política de acceso independiente debe permitir la operación.

## Ejemplo de políticas

Supón que MySQL contiene una tabla `sales.orders` con las columnas `id`, `region` y `card`. Crea tres políticas activadas en el mismo servicio de datos para el usuario de inicio de sesión de MySQL `alice`:

| Tipo de política | Recursos | Regla |
| --- | --- | --- |
| Acceso | `database=sales`, `table=orders`, `column=*` | Permitir `select` al usuario `alice` |
| Enmascaramiento | `database=sales`, `table=orders`, `column=card` | Aplicar `mask_show_last_4` al usuario `alice` |
| Filtro de filas | `database=sales`, `table=orders` | Aplicar `region = 'EU'` al usuario `alice` |

Escribe `alice` como usuario de la política, no `alice@host`. MySQL también debe permitir que esa cuenta lea la tabla. Cuando el proxy aplica la nueva instantánea, las consultas de `alice` a través del proxy solo devuelven filas EU y muestran únicamente los últimos cuatro caracteres de cada tarjeta. Los predicados y las expresiones de unión también ven los valores enmascarados. Consulta el [agente proxy de MySQL](/es/external/mysql-agent/) para el despliegue y la verificación.

## Aplicar políticas

El plug-in de servicio gestiona las políticas y consulta los recursos. Para aplicarlas, máscaras y filtros incluidos, hace falta el agente proxy de MySQL, al que los clientes se conectan en lugar del servidor. Consulta la guía del agente para su despliegue y lo que comprueba.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente proxy de MySQL](/es/external/mysql-agent/)
- [Desarrollo de plug-ins y tipos de servicio](/es/develop/plugins/)
