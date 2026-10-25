---
title: ClickHouse
description: Instalar y configurar el plug-in de servicio de ClickHouse, llegar a uno o varios servidores, iniciar sesión con una contraseña o un certificado de cliente TLS, buscar bases de datos, tablas y columnas, y gestionar quién puede leer, escribir y administrar qué, hasta columnas y filas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de ClickHouse busca bases de datos, tablas y columnas, y gestiona políticas de acceso en los términos de ClickHouse: leer y escribir columnas, modificar, eliminar, vaciar y optimizar tablas, crear tablas en una base de datos y eliminarla, y en el servidor la creación de bases de datos, `KILL QUERY` y las sentencias `SYSTEM`, con filtros de filas en las tablas. Su ID de plug-in y su tipo de servicio son ambos `clickhouse`. El plug-in de servicio se ejecuta en el servidor GrantForge; el [agente de ClickHouse](/es/external/clickhouse-agent/), un servicio junto a los servidores, aplica las políticas con los privilegios y las políticas de filas propios de ClickHouse.

Las pruebas de integración con Testcontainers del repositorio cubren ClickHouse `24.8`, `25.3`, `25.8`, `26.3` y `26.8`, las versiones con soporte a largo plazo de los dos últimos años; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/clickhouse`. Compruebe en **Administración de la plataforma → Plug-ins** que `clickhouse` está habilitado; vuelva a escanear el directorio de plug-ins tras una actualización. El plug-in habla con los servidores por HTTP mediante el cliente Java propio de ClickHouse, en su propio cargador de clases.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y elija ClickHouse (`clickhouse`).
2. Introduzca la URL de la interfaz HTTP de un servidor, o de varios.
3. Elija cómo inicia sesión el usuario de búsqueda, introduzca su nombre de usuario y sus credenciales, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el servidor: su versión de ClickHouse, el usuario con el que inició sesión y cuántas bases de datos ve ese usuario.

El usuario de búsqueda permite a GrantForge descubrir recursos; es independiente de los usuarios que leen y escriben. Una prueba de conexión correcta no significa que el acceso ya esté sujeto a políticas.

| Ajuste | Finalidad |
| --- | --- |
| `clickhouse.urls` | Obligatorio: la interfaz HTTP de un servidor, como `https://clickhouse1.example.com:8443`; varias separadas por comas, probadas por turno cuando una no es accesible |
| `authentication` | `password` (predeterminado) o `certificate`: un certificado de cliente TLS que ClickHouse asocia al usuario (`IDENTIFIED WITH ssl_certificate`) |
| `username` | Obligatorio: el usuario de búsqueda |
| `password` | password: la contraseña del usuario de búsqueda; se guarda cifrada |
| `ssl.root.certificate` | https: el certificado de CA en PEM, en el **servidor GrantForge**, con el que se comprueban los servidores; vacío usa el de la JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate: el certificado de cliente en PEM y su clave privada, en el servidor GrantForge; el inicio de sesión con certificado necesita servidores `https://` |
| `timeout` | Cuánto puede tardar una solicitud, predeterminado `30000`, rango `1..600000` milisegundos |

Los ajustes de un modo de inicio de sesión se rechazan con otro, de modo que un servicio dice exactamente cómo inicia sesión. Un inicio de sesión rechazado, un usuario de solo lectura, un servidor inaccesible o cuyo certificado no puede comprobarse, y algo que no es ClickHouse respondiendo en su lugar, como un proxy, se notifican como tales.

### Qué ve el usuario de búsqueda

ClickHouse solo muestra a un usuario las bases de datos, tablas y columnas sobre las que tiene algún privilegio. Dé al usuario de búsqueda `SHOW` sobre aquello para lo que se vayan a escribir políticas, como `GRANT SHOW ON *.* TO grantforge_lookup`, que le deja ver nombres sin leer datos. Las bases de datos propias de ClickHouse, `system` e `information_schema`, quedan fuera: las políticas no las deciden.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `database` | Comodín, distingue mayúsculas; el nombre de la base de datos | `create`, `drop_database` |
| `table` | Comodín, distingue mayúsculas; bajo una base de datos | `alter`, `drop`, `truncate`, `optimize` |
| `column` | Comodín, distingue mayúsculas; bajo una tabla | `select`, `insert` |
| `server` | Su único valor, `*` | `create_database`, `kill_query`, `system` |

| Tipo de acceso | Privilegios de ClickHouse |
| --- | --- |
| `select`, `insert` | `SELECT`, `INSERT`, sobre columnas |
| `alter` | `ALTER`: cualquier modificación de una tabla, incluidos `ALTER UPDATE` y `ALTER DELETE` |
| `drop` | `DROP TABLE`, `DROP VIEW`, `DROP DICTIONARY` |
| `truncate`, `optimize` | `TRUNCATE`, `OPTIMIZE` |
| `create` | `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` en la base de datos |
| `drop_database` | `DROP DATABASE` |
| `create_database`, `kill_query`, `system` | `CREATE DATABASE`, `KILL QUERY`, `SYSTEM`: cualquier sentencia `SYSTEM` |

La lectura y la escritura se deciden por columna, como las concede ClickHouse: una política de `select` o `insert` nombra columnas, `*` para todas. Así, una política puede permitir una tabla y denegar una de sus columnas, y el usuario puede leer todas las demás. En los valores de las políticas, `*` coincide con cualquier secuencia de caracteres y `?` con uno. El editor de políticas sugiere las bases de datos, las tablas de las bases que nombra una política y sus columnas.

Los tipos de acceso no son ninguno de los grupos de privilegios de ClickHouse como `ALL`: cada uno es lo que dice, de modo que una política nunca da más de lo que nombra. ClickHouse no tiene enmascaramiento de columnas fuera de ClickHouse Cloud, así que este tipo de servicio no lo ofrece.

### Filtros de filas

Una política de filtro de filas sobre una tabla muestra a sus usuarios las filas para las que se cumple una condición. La condición es una expresión de ClickHouse, tal como la toman las políticas de filas, como `region = 'EU'` u `owner = currentUser()`. Un usuario ve el filtro de la primera política que se le aplica, como en los demás servicios.

## Ejemplo de políticas

Supongamos que el servidor tiene las bases de datos `sales` y `hr`. Cree estas políticas habilitadas en el mismo servicio de datos:

| Recursos | Regla |
| --- | --- |
| `database=sales`, `table=*`, `column=*` | Permitir `select` al grupo `analysts` |
| `database=sales`, `table=orders`, `column=card` | Denegar `select` al grupo `analysts` |
| `database=sales`, `table=orders`, `column=*` | Permitir `insert` al usuario `ingest` |
| `database=team_*` | Permitir `create` al grupo `data-team` |
| `database=team_*`, `table=*` | Permitir `alter`, `drop`, `truncate` y `optimize` al grupo `data-team` |
| `database=team_*`, `table=*`, `column=*` | Permitir `select` e `insert` al grupo `data-team` |
| `server=*` | Permitir `create_database` al grupo `data-team` |
| `database=sales`, `table=orders` (filtro de filas) | Mostrar al usuario `emea` las filas `region = 'EMEA'` |

Una vez que el agente aplica la nueva instantánea, los miembros de `analysts` leen todas las tablas de `sales` salvo los números de tarjeta de `orders`; `ingest` escribe `orders` y no lee nada; los miembros de `data-team` crean bases de datos y, en las que empiezan por `team_`, tablas, que leen, escriben y modifican; y `emea`, si una política le deja leer `orders`, solo ve las filas de su propia región.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Aplicarlas requiere el agente de ClickHouse, uno por servidor o clúster. El despliegue y las comprobaciones están en el [agente de ClickHouse](/es/external/clickhouse-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de ClickHouse](/es/external/clickhouse-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
