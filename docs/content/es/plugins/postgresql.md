---
title: PostgreSQL
description: Instalar y configurar el plug-in de servicio de PostgreSQL, llegar a uno o varios servidores, iniciar sesión con una contraseña o un certificado de cliente TLS, buscar bases de datos, esquemas, tablas y columnas, y gestionar quién puede leer, escribir y crear qué, hasta columnas y filas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de PostgreSQL busca bases de datos, esquemas, tablas y columnas, y gestiona políticas de acceso en los términos de PostgreSQL: leer, insertar y actualizar columnas, eliminar filas, vaciar tablas y crear disparadores en ellas, crear objetos en un esquema, y crear esquemas y tablas temporales en una base de datos, con filtros de filas en las tablas. Su ID de plug-in y su tipo de servicio son ambos `postgresql`. El plug-in de servicio se ejecuta en el servidor GrantForge; el [agente de PostgreSQL](/es/external/postgresql-agent/), un servicio junto a los servidores, aplica las políticas con los privilegios y las políticas de seguridad de filas propios de PostgreSQL.

Las pruebas de integración con Testcontainers del repositorio cubren PostgreSQL `15`, `16`, `17` y `18`, las versiones que PostgreSQL mantiene; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/postgresql`. Confirme en **Administración de la plataforma → Plug-ins** que `postgresql` está habilitado; vuelva a examinar el directorio de plug-ins tras actualizarlo. El plug-in habla con los servidores mediante el controlador JDBC de PostgreSQL, en un cargador de clases propio.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y elija PostgreSQL (`postgresql`).
2. Introduzca un servidor como `host:port`, o varios servidores.
3. Elija cómo inicia sesión el usuario de búsqueda, introduzca su nombre y sus credenciales, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el servidor: su versión de PostgreSQL, el usuario con el que inició sesión y cuántas bases de datos tiene el servidor.

El usuario de búsqueda permite a GrantForge descubrir recursos; es distinto de los usuarios que leen y escriben. Una prueba de conexión correcta no significa que el acceso ya esté sujeto a políticas.

| Ajuste | Finalidad |
| --- | --- |
| `postgresql.hosts` | Obligatorio: un servidor como `host:port`, como `postgres1.example.com:5432`; varios separados por comas, probados por turno cuando uno no es accesible. El puerto es `5432` por defecto |
| `postgresql.database` | La base de datos en la que el usuario de búsqueda inicia sesión primero, para listar las bases de datos; por defecto `postgres` |
| `authentication` | `password` (por defecto) o `certificate`: un certificado de cliente TLS que la autenticación `cert` de PostgreSQL asigna al usuario |
| `username` | Obligatorio: el usuario de búsqueda |
| `password` | password: la contraseña del usuario de búsqueda; se guarda cifrada |
| `ssl.mode` | TLS como lo nombra `sslmode` de libpq: `disable`, `allow`, `prefer` (por defecto), `require`, `verify-ca` o `verify-full`, que también comprueba el nombre del servidor |
| `ssl.root.certificate` | verify-ca y verify-full: el certificado de CA PEM con el que se comprueban los servidores, en el **servidor GrantForge**; vacío usa el de la JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate: el certificado de cliente PEM y su clave privada PKCS #8, PEM o DER, en el servidor GrantForge; iniciar sesión con certificado requiere `require` o un modo de verificación, y un modo de verificación requiere entonces `ssl.root.certificate` |
| `timeout` | Cuánto pueden durar la conexión y una consulta, por defecto `30000`, rango `1..600000` milisegundos |

Los ajustes de un inicio de sesión se rechazan con otro, de modo que un servicio dice exactamente cómo inicia sesión. El controlador de PostgreSQL rechaza un archivo de clave privada que puedan leer otros además de su propietario. Un inicio de sesión rechazado, una base de datos que no existe, un servidor inaccesible o cuyo certificado no se puede comprobar se informan como tales.

### Qué ve el usuario de búsqueda

Los catálogos de PostgreSQL muestran a todo usuario los nombres de las bases de datos y, en cada base a la que puede conectarse, los nombres de los esquemas, las tablas y las columnas, sin leer datos. El usuario de búsqueda solo necesita `CONNECT` en las bases de datos para las que se escriban políticas, que PUBLIC tiene salvo que se haya revocado. Una búsqueda de esquemas, tablas o columnas inicia sesión en cada base de datos que nombra la política, 20 como máximo, y omite las bases a las que el usuario de búsqueda no puede conectarse. Los esquemas propios de PostgreSQL, los que empiezan por `pg_` e `information_schema`, quedan fuera: las políticas no deciden sobre ellos.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `database` | Comodines, distingue mayúsculas; el nombre de la base de datos | `create_schema`, `temporary` |
| `schema` | Comodines, distingue mayúsculas; bajo una base de datos | `create` |
| `table` | Comodines, distingue mayúsculas; tablas, tablas particionadas, vistas, vistas materializadas y tablas foráneas bajo un esquema | `delete`, `truncate`, `trigger` |
| `column` | Comodines, distingue mayúsculas; bajo una tabla | `select`, `insert`, `update` |

| Tipo de acceso | Privilegios de PostgreSQL |
| --- | --- |
| `select`, `insert`, `update` | `SELECT`, `INSERT`, `UPDATE`, en columnas o en la tabla entera |
| `delete`, `truncate`, `trigger` | `DELETE`, `TRUNCATE`, `TRIGGER` en la tabla |
| `create` | `CREATE` en el esquema: tablas, vistas, secuencias, funciones y otros objetos en él |
| `create_schema` | `CREATE` en la base de datos: esquemas en ella |
| `temporary` | `TEMPORARY` en la base de datos |

La lectura, la inserción y la actualización se deciden por columna, como PostgreSQL las concede: una política de `select`, `insert` o `update` nombra columnas, `*` para todas. Así una política puede permitir una tabla y denegar una de sus columnas, y el usuario puede leer todas las demás. En los valores de las políticas, `*` coincide con cualquier secuencia de caracteres y `?` con uno. El editor de políticas sugiere las bases de datos, los esquemas de las bases que nombra una política, sus tablas y sus columnas.

Llegar a lo que una política permite requiere más de PostgreSQL: `CONNECT` en la base de datos, `USAGE` en el esquema y, para insertar en una columna serial, `USAGE` en su secuencia. No son tipos de acceso propios: el agente los concede junto con lo que los necesita. PostgreSQL no tiene enmascaramiento de columnas, así que este tipo de servicio no lo ofrece.

### Filtros de filas

Una política de filtrado de filas sobre una tabla muestra a sus usuarios las filas para las que se cumple una condición. La condición es una expresión de PostgreSQL, tal como la toma el `USING` de una política de seguridad de filas, como `region = 'EU'` u `owner = current_user`. Un usuario ve el filtro de la primera política que se le aplica, como en los demás servicios. PostgreSQL solo filtra las filas de tablas y tablas particionadas: una vista u objeto similar cuyas filas filtra una política para un usuario no es legible en absoluto para ese usuario.

## Ejemplo de políticas

Suponga que el servidor tiene las bases de datos `sales` y `hr`, y que `sales` tiene el esquema `crm`. Cree estas políticas habilitadas en el mismo servicio de datos:

| Recursos | Regla |
| --- | --- |
| `database=sales`, `schema=crm`, `table=*`, `column=*` | Permitir `select` al grupo `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=card` | Denegar `select` al grupo `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=*` | Permitir `insert` al usuario `ingest` |
| `database=sales`, `schema=team_*` | Permitir `create` al grupo `data-team` |
| `database=sales`, `schema=team_*`, `table=*` | Permitir `delete` y `truncate` al grupo `data-team` |
| `database=sales`, `schema=team_*`, `table=*`, `column=*` | Permitir `select`, `insert` y `update` al grupo `data-team` |
| `database=sales` | Permitir `create_schema` al grupo `data-team` |
| `database=sales`, `schema=crm`, `table=orders` (filtro de filas) | Mostrar al usuario `emea` las filas `region = 'EMEA'` |

Una vez que el agente aplica la nueva instantánea, los miembros de `analysts` leen todas las tablas de `crm` salvo los números de tarjeta de `orders`; `ingest` inserta en `orders`, incluidos sus identificadores serial, y no lee nada; los miembros de `data-team` crean esquemas y, en los que empiezan por `team_`, crean, leen, cambian y vacían tablas; y `emea`, si una política le deja leer `orders`, solo ve las filas de su región.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Aplicarlas requiere el agente de PostgreSQL, uno por servidor o primario. El despliegue y las comprobaciones están en el [agente de PostgreSQL](/es/external/postgresql-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de PostgreSQL](/es/external/postgresql-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
