---
title: Agente de ClickHouse
description: Ejecutar el agente de ClickHouse junto a los servidores, para que las políticas de GrantForge se conviertan en los roles, privilegios y políticas de filas propios de ClickHouse, hasta columnas y filas, y los accesos se notifiquen desde el registro de consultas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de ClickHouse es un servicio junto a los servidores. Mantiene el control de acceso propio de ClickHouse de acuerdo con las políticas firmadas de un servicio de datos `clickhouse`: un rol por usuario que tiene exactamente lo que las políticas le dan al usuario, y una política de filas por filtro de filas. ClickHouse comprueba entonces él mismo cada consulta, en cada uno de sus protocolos, y el agente nunca se interpone entre los clientes y el servidor. La distribución lo incluye como un JAR ejecutable, `agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`, que requiere Java 17 o posterior.

Las pruebas de integración con Testcontainers del repositorio ejecutan el agente, y el JAR tal como se distribuye, contra ClickHouse `24.8`, `25.3`, `25.8`, `26.3` y `26.8`. Esto no certifica todas las demás versiones; el agente rechaza servidores anteriores a `24.8`. El plug-in de servicio se describe en [ClickHouse](/es/plugins/clickhouse/).

## Funcionamiento

- **Un rol por usuario tiene lo que GrantForge da.** Para cada usuario al que las políticas dan algo, el agente mantiene un rol con su nombre, `grantforge_alice` para `alice`, y se lo concede como rol predeterminado. `SHOW GRANTS FOR grantforge_alice` muestra todo lo que GrantForge da a `alice`; el agente no cambia ningún otro rol ni ningún privilegio que no concedió él.
- **Bases de datos enteras donde nada por debajo se decide de otro modo.** Un tipo de acceso que una política permite en toda una base de datos, sin ninguna política que pudiera quitarlo a una tabla o columna de ella, se concede sobre la base, de modo que las tablas creadas después lo tienen al instante. Si no, se concede tabla a tabla, y la lectura y la escritura columna a columna.
- **Los filtros de filas se convierten en políticas de filas.** Un filtro de filas es una política de filas permisiva del rol del usuario sobre la tabla. Un filtro nuevo o cambiado está en su sitio antes que los privilegios que restringe. Un filtro que ClickHouse rechaza, o uno cuyo texto haría que la política de filas se aplicara a otros, se sustituye por uno que no muestra ninguna fila al usuario, y se registra.
- **Las políticas del momento presente.** Cada sincronización planifica según las políticas tal como se aplican en ese momento, de modo que los periodos de validez empiezan y terminan a tiempo, y según los usuarios, bases de datos, tablas y columnas que tiene ClickHouse, de modo que los nuevos quedan cubiertos en la siguiente sincronización.
- **Los roles de ClickHouse son grupos.** Una política para un grupo se aplica a los usuarios que tienen un rol de ClickHouse con ese nombre, directamente o a través de otros roles, junto con los grupos en los que GrantForge los pone.
- **Se deshacen los cambios hechos a mano.** El agente recuerda lo que dio a cada rol y política de filas; el que difiera se restablece en la siguiente sincronización. Cuando nada ha cambiado, el agente no envía nada.
- **Se notifican los privilegios fuera de GrantForge.** ClickHouse suma los privilegios, así que el agente no puede quitar lo que un usuario tiene directamente o a través de otro rol. Avisa una vez de cada privilegio así que dé lo que una política deniega, nombrando al usuario, el privilegio, el rol por el que llega y la política.
- **Los accesos vienen del registro de consultas.** El agente lee `system.query_log` y notifica cada sentencia como un evento por tabla, base de datos o servidor que tocó, y un rechazo como un evento denegado de lo que nombra el error de ClickHouse. Cada evento dice si lo decidió GrantForge, es decir, si las políticas deciden como ClickHouse, o si lo permitió un privilegio fuera de GrantForge.

| Sentencia | Evento de acceso |
| --- | --- |
| `SELECT`, la parte de lectura de `INSERT … SELECT` | `select` sobre cada tabla leída |
| `INSERT` | `insert` sobre la tabla escrita |
| `ALTER`, `ALTER UPDATE`, `ALTER DELETE`, `DELETE`, `UPDATE` | `alter` sobre la tabla |
| `DROP TABLE`, `TRUNCATE`, `OPTIMIZE` | `drop`, `truncate`, `optimize` sobre la tabla |
| `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` | `create` sobre la base de datos |
| `DROP DATABASE` | `drop_database` sobre la base de datos |
| `CREATE DATABASE`, `KILL QUERY`, `SYSTEM …` | `create_database`, `kill_query`, `system` sobre el servidor |

## Despliegue

1. Añada un servicio de datos `clickhouse` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, elija el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un archivo que solo pueda leer la cuenta del agente.
2. Cree el usuario de ClickHouse del agente. Concede lo que dan las políticas, así que necesita esos privilegios con la opción de concesión, y la gestión de accesos para mantener roles y políticas de filas:

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   Los usuarios para los que el agente mantiene roles deben estar guardados donde puedan cambiarse, en el directorio de usuarios `local_directory` o `replicated`, que es donde los guarda `CREATE USER`. Los usuarios de `users.xml` y de LDAP se dejan como están y se registran una vez. Un clúster debería guardar sus usuarios, roles y políticas de filas en un directorio de usuarios `replicated`: así un solo agente sirve a todo el clúster.
3. Escriba el archivo de ajustes, sustituyendo las direcciones, rutas y nombres del ejemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   Estas rutas están en la máquina del agente, y el directorio de caché debe admitir escritura. Los archivos de contraseña se leen como UTF-8 y se recortan.
4. Inicie el agente, por ejemplo como servicio de systemd:

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   Sustituya `<version>` por la versión de la distribución. Los ajustes indicados como `java -Dgrantforge.…=… -jar …` sustituyen a los del archivo. El agente se detiene limpiamente con SIGTERM y envía o pone en cola los eventos pendientes; ClickHouse conserva lo que se le dio.
5. **Pase los privilegios existentes a políticas:** los privilegios concedidos fuera de GrantForge siguen aplicándose junto a los roles del agente. Cree las políticas correspondientes y luego revoque esos privilegios; los avisos del agente nombran los que dan lo que una política deniega.

| Ajuste | Finalidad |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor GrantForge, el archivo del token de agente, un nombre para este agente y un directorio con escritura para la instantánea firmada, los eventos en cola y la posición de lectura del registro de consultas |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copie la clave pública X.509 Ed25519 codificada en Base64 desde la página de agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.clickhouse.urls`, `grantforge.clickhouse.user` | Obligatorios: las interfaces HTTP de los servidores, probadas por turno cuando una no es accesible, y el usuario de ClickHouse del agente |
| `grantforge.clickhouse.password.file` | La contraseña del usuario; si se omite, el agente inicia sesión sin contraseña |
| `grantforge.clickhouse.tls.ca` | Certificado de CA en PEM que comprueba los servidores `https://`; vacío confía en lo que confía Java |
| `grantforge.clickhouse.tls.cert`, `grantforge.clickhouse.tls.key` | Certificado de cliente en PEM y clave privada, juntos, para iniciar sesión con un certificado (`IDENTIFIED WITH ssl_certificate`) en lugar de una contraseña |
| `grantforge.clickhouse.timeout.ms` | Cuánto puede tardar una solicitud a ClickHouse, predeterminado `30000`, rango `1..600000` milisegundos |
| `grantforge.clickhouse.role.prefix` | El comienzo de los nombres de rol del agente, predeterminado `grantforge_`; letras, dígitos y guiones bajos, terminado en guion bajo. El agente es dueño de todo rol y toda política de filas cuyo nombre empiece así |
| `grantforge.clickhouse.unmanaged.users` | Usuarios que el agente deja en paz, separados por comas, como los administradores; siempre deja en paz a su propio usuario |
| `grantforge.sync.interval.ms` | Con qué frecuencia el agente pone ClickHouse de acuerdo, predeterminado `10000`, rango `1000..3600000` milisegundos |
| `grantforge.audit.query.log` | Predeterminado `true`; `false` no lee ningún registro de consultas ni notifica accesos |
| `grantforge.audit.query.log.cluster` | Un clúster cuyos registros de las réplicas se leen mediante `clusterAllReplicas`; vacío lee el servidor al que pregunta el agente |
| `grantforge.audit.query.log.delay.ms`, `grantforge.audit.query.log.interval.ms`, `grantforge.audit.query.log.batch` | Cuánto por detrás del presente se lee el registro, predeterminado `10000` milisegundos, ya que ClickHouse lo escribe cada 7,5 segundos; con qué frecuencia, predeterminado `5000` milisegundos; y el máximo de entradas por lectura, predeterminado `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura hacia GrantForge, predeterminados `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide nuevas políticas, predeterminado `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (predeterminado `500`), tamaño de cola (predeterminado `10000`), intervalo de envío (predeterminado `5000` milisegundos) y límite de la cola en disco (predeterminado 64 MiB) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo los accesos permitidos idénticos se agrupan en un evento, predeterminado `10000`, hasta `3600000` milisegundos; `0` envía cada uno |

## Usuarios, grupos y roles

Las políticas nombran usuarios de ClickHouse. Los grupos son los roles de ClickHouse que tiene un usuario, directamente o a través de otros roles, junto con los grupos en los que GrantForge pone al usuario; los roles son los que GrantForge le da al usuario.

## Decisiones y limitaciones

- Sin instantánea, el agente no cambia nada. Un servicio que no está en uso, o un token vinculado a un servicio cuyo tipo no es `clickhouse`, no concede nada: el agente elimina sus roles y políticas de filas, y se aplican los privilegios propios de ClickHouse.
- Mientras el agente no se ejecuta, ClickHouse conserva lo último que se le dio; los cambios de políticas lo esperan. Un cambio llega a ClickHouse dentro del intervalo de actualización y el intervalo de sincronización.
- Las tablas creadas en una base de datos que el agente concede tabla a tabla reciben sus privilegios en la siguiente sincronización.
- Un usuario para el que ClickHouse no muestra ninguna política de filas ve todas las filas de una tabla, como permite por defecto el ajuste `users_without_row_policies_can_read_rows` de ClickHouse; si está desactivado, los usuarios sin filtro no ven ninguna fila de las tablas filtradas. Las políticas de filas permisivas creadas fuera de GrantForge para el mismo usuario amplían lo que muestra su filtro.
- `system` concede toda sentencia `SYSTEM`, incluida `SYSTEM SHUTDOWN`.
- El registro de consultas notifica lo que ClickHouse registra: no se notifican las sentencias de usuarios con `log_queries` desactivado. En un rechazo, ClickHouse puede nombrar la tabla cuando al usuario le falta una columna; el evento nombra entonces la tabla.
- ClickHouse no tiene enmascaramiento de columnas fuera de ClickHouse Cloud, así que las políticas no pueden enmascarar valores.

## Guías relacionadas

- [ClickHouse](/es/plugins/clickhouse/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
