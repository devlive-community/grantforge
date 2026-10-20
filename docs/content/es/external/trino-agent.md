---
title: Agente de Trino
description: Instalar el control de acceso del sistema en el coordinator de Trino para comprobar consultas con las políticas de GrantForge, listar solo lo que un usuario puede usar, filtrar filas y enmascarar columnas, y someter la suplantación y las consultas de otros usuarios a políticas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de Trino es un control de acceso del sistema llamado `grantforge`: el coordinator le pregunta por cada catálogo, esquema, tabla, columna, rutina, propiedad de sesión y usuario que toca una consulta, y responde a partir de las políticas firmadas del servicio de datos `trino`. Trino 470 a 478 se ejecutan sobre Java 23 o 24 y Trino 479 y posteriores sobre Java 25, por lo que la distribución incluye dos JAR, cada uno con sus dependencias reubicadas dentro:

| Trino | JAR |
| --- | --- |
| 470 a 478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 y posteriores | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

Las pruebas de integración con Testcontainers del repositorio ejecutan el primer JAR dentro de Trino `470` y `476` y el segundo dentro de `479` y `484`. Esto no certifica todas las demás versiones. Vea [Trino](/es/plugins/trino/) para el plug-in de servicio.

## Funcionamiento

- **Cada comprobación se decide en cada ámbito que nombra.** Un permiso sobre un catálogo, esquema o tabla cubre lo que contiene, así que el agente decide una columna en su catálogo, su esquema, su tabla y ella misma: una denegación en cualquiera de ellos rechaza, una autorización en cualquiera de ellos concede.
- **Los listados omiten lo que no se puede usar.** `SHOW CATALOGS`, `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW FUNCTIONS`, `information_schema` y los metadatos del controlador muestran solo lo que el usuario puede usar, o dentro de lo cual puede usar algo.
- **Lo que describe los datos queda abierto.** El `information_schema` de cada catálogo, los esquemas `jdbc` y `metadata` del catálogo `system`, que leen los controladores JDBC y las herramientas de BI, y las funciones integradas de Trino no necesitan política; sus filas se filtran como los listados.
- **Se filtran filas y se enmascaran columnas.** Las políticas de filtrado de filas se aplican a cada consulta que lee la tabla, y las máscaras se ajustan al tipo de la columna, para que una máscara nunca rompa una consulta: las máscaras de texto se aplican a columnas `varchar` y `char` y se convierten de vuelta al tipo de la columna, la máscara de año a fechas y marcas de tiempo, una expresión personalizada se convierte al tipo, y cualquier otra columna que nombre una máscara de texto se lee como un `NULL` de su tipo.
- **La suplantación y las consultas ajenas están sujetas a políticas.** Ejecutar una consulta como otro usuario requiere `impersonate` sobre ese usuario; ver o terminar las consultas de otro usuario requiere `view_query` o `kill_query` sobre él. Cualquiera puede ejecutar consultas, y ver y terminar las suyas.
- **Los rechazos dicen por qué.** El cliente recibe `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`, o que ninguna política lo permite, o que aún no llegaron políticas.
- **Las decisiones se conservan y los accesos se agrupan.** Las decisiones de la instantánea actual se conservan, por defecto 100 000 durante 10 segundos, y una nueva instantánea empieza de cero. Los accesos permitidos idénticos dentro de una ventana, por defecto 10 segundos, se informan como un único evento de acceso que indica cuántos hubo; los rechazos se informan uno a uno y de inmediato. Cada evento nombra la consulta.
- **Los privilegios son de GrantForge.** Las sentencias `GRANT`, `REVOKE`, `DENY` y de roles de Trino se rechazan, ya que las políticas se gestionan en GrantForge.

| Sentencia | Acceso necesario |
| --- | --- |
| `SELECT` | `select` sobre cada columna leída; una consulta que no lee ninguna columna, como `count(*)`, sobre la tabla |
| `INSERT`, `DELETE`, `TRUNCATE` | `insert`; `delete` para las otras dos |
| `UPDATE` | `update` sobre cada columna escrita |
| `CREATE SCHEMA`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW` | `create` sobre lo que se crea |
| `DROP` de un esquema, tabla, vista o vista materializada | `drop` sobre él |
| `ALTER` de una tabla, vista o columna, `COMMENT`, `SET PROPERTIES`, `SET AUTHORIZATION`, `ALTER TABLE ... EXECUTE` | `alter` sobre él |
| Renombrar un esquema, tabla, vista o vista materializada | `alter` sobre él y `create` sobre el nuevo nombre |
| `REFRESH MATERIALIZED VIEW` | `insert` sobre ella |
| Una vista que ejecuta su propietario | Su propietario necesita `select` sobre las columnas y `execute` sobre las funciones que lee |
| `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW COLUMNS`, `SHOW FUNCTIONS`, `SHOW CREATE` | Cualquier acceso a él o dentro de él |
| `CREATE CATALOG`, `DROP CATALOG` | `create` o `drop` sobre el catálogo |
| Llamar a una función; `CREATE FUNCTION`, `DROP FUNCTION` | `execute`; `create_function`, `drop_function` sobre la función |
| `CALL` | `execute` sobre el procedimiento |
| `SET SESSION` | `set` sobre la propiedad de sesión del catálogo o del sistema |
| Ejecutar como otro usuario, ver o terminar consultas ajenas | `impersonate`, `view_query`, `kill_query` sobre el usuario |
| Leer o escribir la información del sistema: nodos, estadísticas y volcados de hilos de la API REST y la interfaz web de Trino | `read` o `write` sobre `system_information` |

Desde Trino 479, el segundo JAR también comprueba las ramas de tabla: listarlas requiere ver la tabla, y crear, eliminar o avanzar una requiere `alter` sobre ella; refrescar una vista y cambiar el propietario o el comentario de una vista materializada requieren `alter` sobre ella.

## Despliegue

1. Añada un servicio de datos `trino` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, seleccione el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un archivo que solo pueda leer la cuenta de Trino.
2. Copie el JAR de la versión del coordinator en un directorio propio dentro del directorio de plug-ins de Trino, como `/usr/lib/trino/plugin/grantforge/`. Solo el coordinator comprueba el acceso; los workers no necesitan nada.
3. Cree `etc/access-control.properties` en el coordinator, sustituyendo la dirección, las rutas y el nombre de instancia de ejemplo:

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   El directorio de caché debe poder escribirlo la cuenta de Trino. GrantForge sustituye el control de acceso basado en archivos u otro de Trino en lugar de ejecutarse junto a él.
4. Reinicie el coordinator. Hasta que el agente tenga su primera instantánea, las consultas se rechazan, salvo lo que describe los datos.
5. **Migre primero las reglas existentes:** las reglas del control de acceso basado en archivos de Trino dejan de aplicarse. Cree antes las políticas equivalentes.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor GrantForge, el archivo del token de agente, un nombre para este coordinator y un directorio escribible para la instantánea firmada y los eventos en cola |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copie la clave pública Ed25519 X.509 codificada en Base64 desde la página Agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura hacia GrantForge, por defecto `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide nuevas políticas, por defecto `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (por defecto `500`), tamaño de cola (por defecto `10000`), intervalo de envío (por defecto `5000` milisegundos) y límite del búfer en disco (por defecto 64 MiB) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo se agrupan en un evento los accesos permitidos idénticos, por defecto `10000`, hasta `3600000` milisegundos; `0` informa cada uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | El máximo de decisiones conservadas, por defecto `100000`, hasta `10000000`, `0` no conserva ninguna; y durante cuánto tiempo, por defecto `10000`, `1..3600000` milisegundos |

## Usuarios, grupos y roles

Las políticas nombran al usuario de sesión de la consulta, tal como Trino lo da tras la autenticación y la asignación de usuarios. Los grupos son los que el proveedor de grupos de Trino da al usuario, junto con los grupos en los que GrantForge lo coloca; los roles son los que GrantForge le da.

## Decisiones y limitaciones

- Sin una instantánea utilizable, todo se rechaza salvo lo que describe los datos, ejecutar consultas y las consultas propias. Un servicio que no está en uso deja Trino sin comprobar, como sin control de acceso; las sentencias de privilegios y roles de Trino se rechazan igualmente.
- Un token vinculado a un servicio cuyo tipo no es `trino` lo rechaza todo.
- Trino pregunta antes que nada si un usuario puede usar un catálogo: un usuario sin ningún permiso en un catálogo recibe el `Cannot access catalog` propio de Trino.
- Un `SELECT` que lee una columna que el usuario no puede leer se rechaza, nombrando la primera de esas columnas; las columnas no se quitan del resultado.
- Trino no escribe en ninguna tabla cuyas columnas enmascara para el usuario: un `INSERT` así falla mientras la máscara se aplique.
- Una máscara se convierte al tipo de la columna, así que un `varchar` acotado muestra lo que quepa del valor enmascarado, como 3 caracteres de un hash en `varchar(3)`.
- Un filtro de filas o una máscara que la instantánea no puede leer no muestra filas, o muestra la columna como `NULL`, en lugar de los datos.
- Las decisiones se conservan durante `grantforge.decision.cache.ttl.ms`, así que el periodo de validez de una política empieza o termina hasta ese tiempo después.

## Guías relacionadas

- [Trino](/es/plugins/trino/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
