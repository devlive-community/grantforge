---
title: Bases de datos
description: Las bases de datos y versiones admitidas, la forma de conectar, los controladores y las particularidades de cada base de datos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Compatibilidad

| Base de datos | Versiones verificadas | Controlador |
| --- | --- | --- |
| H2 | incluida con la versión | incluido; solo se recomienda para probar |
| PostgreSQL | 14, 17 | incluido |
| MySQL | 8.0, 8.4 | hay que ponerlo a mano en `drivers/` (Connector/J tiene licencia GPL y no se distribuye con el paquete publicado) |
| MariaDB | 10.11, 11.4 | incluido |
| Oracle | Free 23 | incluido |
| SQL Server | 2022 | incluido |

Cada versión se somete en CI a una "inicialización sobre una base de datos vacía más todas las pruebas de integración". Las bases de datos chinas (DM, KingbaseES, openGauss, OceanBase, etc.) se quedan fuera del ámbito admitido.

## Ejemplos de conexión

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle (nombre de servicio)
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

El nombre de usuario y la contraseña se indican con `GRANTFORGE_DB_USER` y `GRANTFORGE_DB_PASSWORD`, respectivamente. La base de datos hay que crearla antes, y la cuenta necesita permiso para crear tablas: en el primer arranque GrantForge crea todas las tablas con Liquibase, y las migraciones de las versiones posteriores también las ejecuta Liquibase automáticamente. Hibernate solo comprueba la estructura de las tablas, nunca la modifica.

En PostgreSQL, GrantForge intenta activar la extensión `pg_trgm` y crea un índice de trigramas sobre el nombre de usuario, el nombre visible y el correo, para que la búsqueda "contiene" sobre millones de cuentas se mantenga en unas decenas de milisegundos. Desde PostgreSQL 13 es una extensión de confianza y la puede activar el propietario de la base de datos; si la cuenta no tiene ese permiso, el servicio arranca igual, la búsqueda pasa a ser un recorrido completo de la tabla y, una vez que un administrador ejecute `CREATE EXTENSION pg_trgm`, el índice se crea solo en el siguiente arranque.

## Juegos de caracteres

- **MySQL / MariaDB**: al crear la base de datos, usa el juego de caracteres `utf8mb4`, para que el chino y los emoji se guarden completos.
- **SQL Server, Oracle**: las columnas de texto que pueden contener chino usan `NVARCHAR`; el texto largo es `NVARCHAR(MAX)` en SQL Server y `CLOB` en Oracle, al margen del juego de caracteres predeterminado de la base de datos.
- **Oracle**: la cadena vacía se trata como `NULL`, así que GrantForge considera los valores en blanco como "no rellenados" de forma uniforme en la capa de dominio; el comportamiento es igual que en las demás bases de datos.

## Copias de seguridad y restauración

Todos los datos de negocio están en la base de datos (también las sesiones), así que basta con hacer una copia de la base de datos; si usas plug-ins, copia también `plugins/`. Si no has configurado `grantforge.security.encryption-key`, la clave de cifrado también está en la base de datos y con restaurar la copia ya se puede descifrar; si has configurado una clave, tendrás que guardar además esa clave.
