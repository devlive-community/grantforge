---
title: Agente de PostgreSQL
description: Ejecutar el agente de PostgreSQL junto a los servidores, para que las políticas de GrantForge se conviertan en roles, privilegios y políticas de seguridad de filas propios de PostgreSQL, hasta columnas y filas, base de datos por base de datos en una transacción cada una, y los accesos se informen desde el registro del servidor.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de PostgreSQL es un servicio junto a los servidores. Mantiene el control de acceso propio de PostgreSQL de acuerdo con las políticas firmadas de un servicio de datos `postgresql`: un rol por usuario que tiene exactamente lo que las políticas dan al usuario, en cada base de datos que mantiene, y una política de seguridad de filas por filtro de filas. PostgreSQL comprueba entonces cada sentencia por sí mismo, y el agente nunca se interpone entre los clientes y el servidor. La distribución lo incluye como un JAR ejecutable, `agents/postgresql/grantforge-agent-postgresql-<version>.jar`, que requiere Java 17 o posterior.

Las pruebas de integración con Testcontainers del repositorio ejecutan el agente, y el JAR tal como se distribuye, contra PostgreSQL `15`, `16`, `17` y `18` con pgaudit. Esto no certifica todas las demás versiones; el agente rechaza servidores anteriores a `15`. Consulte [PostgreSQL](/es/plugins/postgresql/) para el plug-in de servicio.

## Funcionamiento

- **Un rol por usuario tiene lo que GrantForge da.** Para cada usuario al que las políticas dan algo, el agente mantiene un rol con su nombre, `grantforge_alice` para `alice`, y hace al usuario miembro con los privilegios del rol sin `SET ROLE`. El agente escribe un comentario en cada uno de sus roles y no toma ningún rol sin él por suyo, de modo que no cambia ningún otro rol ni ningún privilegio que no concedió él.
- **Cada base de datos cambia en una transacción.** El agente inicia sesión en cada base de datos que mantiene y hace allí sus cambios en una transacción, de modo que los usuarios ven una versión de las políticas aplicada a una base de datos entera o en absoluto.
- **Tablas enteras donde nada por debajo se decide de otro modo.** Un tipo de acceso que una política permite en toda una tabla, sin ninguna política que pudiera quitarlo a una columna, se concede sobre la tabla, de modo que las columnas añadidas después lo tienen al instante. Si no, la lectura, la inserción y la actualización se conceden columna a columna. `CONNECT` en la base de datos, `USAGE` en el esquema y en las secuencias de las columnas serial en las que el usuario puede insertar vienen con ellas.
- **Los filtros de filas solo restringen.** Un filtro de filas es una política de seguridad de filas restrictiva del rol del usuario sobre la tabla, para `SELECT`. Donde la seguridad de filas está desactivada, el agente la activa junto con una política permisiva que muestra todas las filas a todos, con el nombre de su prefijo (`grantforge`), de modo que nada cambia para los demás; vuelve a desactivarla cuando la tabla ya no tiene ningún filtro ni ninguna política propia. La seguridad de filas que activó un propietario sigue siendo del propietario, y las políticas del agente solo restringen lo que muestra. Un filtro que PostgreSQL rechaza, uno que cambiaría a quién vincula la política, o un texto de más de una sentencia se sustituye por una política que no muestra ninguna fila al usuario, y se registra.
- **Las políticas del momento presente.** Cada sincronización planifica según las políticas tal como se aplican en ese momento, de modo que los periodos de validez empiezan y terminan a tiempo, y según los usuarios, esquemas, tablas y columnas que tiene PostgreSQL, de modo que los nuevos se cubren en la siguiente sincronización.
- **Los roles son grupos.** Una política para un grupo se aplica a los usuarios miembros de un rol de PostgreSQL con ese nombre, directamente o mediante otros roles, junto con los grupos en los que GrantForge los coloca.
- **Los cambios hechos a mano se deshacen.** En cada sincronización el agente compara los privilegios de sus roles con lo que muestra PostgreSQL, y sus políticas de seguridad de filas con lo que creó por última vez; lo que difiere se restablece en la siguiente sincronización. Cuando nada cambió, el agente no envía nada.
- **Se informan los privilegios fuera de GrantForge.** PostgreSQL suma privilegios, así que el agente no puede quitar lo que un usuario tiene por sí mismo, mediante otro rol o mediante PUBLIC, ni lo que puede hacer con las tablas de su propiedad. Avisa una vez de cada uno de esos privilegios que da lo que una política deniega, nombrando al usuario, el privilegio, cómo lo tiene el usuario y la política.
- **Los accesos vienen del registro del servidor.** El agente lee el registro JSON del servidor PostgreSQL: el registro de sesión de pgaudit para lo que PostgreSQL permitió, un evento por cada tabla que una sentencia leyó o escribió, y los rechazos de PostgreSQL por falta de un privilegio, un evento denegado por lo que nombra el error. Cada evento dice si lo decidió GrantForge, es decir, si las políticas deciden como PostgreSQL, o si lo permitió un privilegio fuera de GrantForge.

| Sentencia | Evento de acceso |
| --- | --- |
| `SELECT`, `COPY … TO`, la parte de lectura de otras sentencias | `select` en cada tabla leída |
| `INSERT`, `COPY … FROM` | `insert` en la tabla escrita |
| `UPDATE`, `MERGE`, `SELECT … FOR UPDATE` | `update` en la tabla |
| `DELETE`, `TRUNCATE` | `delete`, `truncate` en la tabla |
| `CREATE TRIGGER` | `trigger` en la tabla |
| `CREATE TABLE`, `CREATE VIEW` y otros objetos | `create` en el esquema |
| `CREATE SCHEMA`, `CREATE TEMPORARY TABLE` | `create_schema`, `temporary` en la base de datos |
| Una conexión rechazada, un esquema rechazado por falta de `USAGE` | `connect` en la base de datos, `usage` en el esquema |

## Despliegue

1. Añada un servicio de datos `postgresql` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, elija el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un archivo que solo pueda leer la cuenta del agente.
2. Cree el usuario de PostgreSQL del agente como superusuario. Concede privilegios sobre tablas que no son suyas, activa y desactiva la seguridad de filas, mantiene roles y lee el registro del servidor:

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   Las pruebas ejecutan el agente como superusuario. A los superusuarios no los vinculan ni los privilegios ni la seguridad de filas, así que el agente no mantiene ningún rol para ellos.
3. Para informar de los accesos, haga que PostgreSQL escriba su registro en JSON y cargue pgaudit, de los paquetes de PGDG como `postgresql-17-pgaudit` o los de la plataforma, en `postgresql.conf`, y reinicie el servidor:

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   Sin pgaudit el agente informa de lo que PostgreSQL rechazó, no de lo que permitió; sin el registro JSON no informa de nada y lo dice una vez.
4. Escriba el archivo de ajustes, sustituyendo las direcciones, rutas y nombres de ejemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   Estas rutas están en la máquina del agente, y el directorio de caché debe poder escribirse. Los archivos de contraseña se leen en UTF-8 y se recortan.
5. Inicie el agente, por ejemplo como servicio de systemd:

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   Sustituya `<version>` por la versión de la distribución. Los ajustes dados como `java -Dgrantforge.…=… -jar …` sustituyen a los del archivo. El agente se detiene limpiamente con SIGTERM y envía o pone en cola los eventos pendientes; PostgreSQL conserva lo que se le dio.
6. **Pasar los privilegios existentes a políticas:** los privilegios concedidos fuera de GrantForge siguen aplicándose junto a los roles del agente. Cree las políticas correspondientes y después revoque los privilegios; los avisos del agente nombran los que dan lo que una política deniega.

| Ajuste | Finalidad |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor GrantForge, el archivo del token del agente, un nombre para este agente y un directorio con permiso de escritura para la instantánea firmada, los eventos en cola y la posición de lectura del registro del servidor |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copie la clave pública Ed25519 X.509 codificada en Base64 de la página Agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.postgresql.hosts`, `grantforge.postgresql.user` | Obligatorios: los servidores como `host:port`, probados por turno hasta que uno accesible sea el primario, y el usuario de PostgreSQL del agente |
| `grantforge.postgresql.database` | La base de datos en la que el agente inicia sesión primero, para leer los roles, las bases de datos y el registro del servidor, por defecto `postgres` |
| `grantforge.postgresql.password.file` | La contraseña del usuario; si se omite, el agente inicia sesión sin contraseña |
| `grantforge.postgresql.ssl.mode` | TLS como lo nombra `sslmode` de libpq, por defecto `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca y verify-full: certificado de CA PEM que comprueba los servidores; vacío confía en lo que confía Java |
| `grantforge.postgresql.tls.cert`, `grantforge.postgresql.tls.key` | Certificado de cliente PEM y su clave privada PKCS #8, juntos, para iniciar sesión con un certificado (autenticación `cert`) en lugar de una contraseña; requieren `require` o un modo de verificación, y un modo de verificación requiere entonces `grantforge.postgresql.tls.ca` |
| `grantforge.postgresql.timeout.ms` | Cuánto pueden durar la conexión y una sentencia, por defecto `30000`, rango `1..600000` milisegundos |
| `grantforge.postgresql.databases` | Las bases de datos que mantiene el agente, separadas por comas; vacío mantiene todas las bases que admiten conexiones |
| `grantforge.postgresql.role.prefix` | El comienzo de los nombres de rol del agente, por defecto `grantforge_`; letras, dígitos y guiones bajos, terminado en guion bajo, de 32 caracteres como máximo y sin empezar por `pg_`. Al agente le pertenecen los roles que marcó cuyo nombre empieza así, y las políticas de seguridad de filas con su nombre o con el del prefijo sin su último guion bajo |
| `grantforge.postgresql.unmanaged.users` | Usuarios que el agente deja en paz, separados por comas, como los administradores; siempre deja en paz a su propio usuario y a los superusuarios |
| `grantforge.sync.interval.ms` | Con qué frecuencia el agente pone de acuerdo a PostgreSQL, por defecto `10000`, rango `1000..3600000` milisegundos |
| `grantforge.audit.server.log` | Por defecto `true`; `false` no lee ningún registro del servidor ni informa de accesos |
| `grantforge.audit.server.log.interval.ms`, `grantforge.audit.server.log.max.bytes` | Con qué frecuencia se lee el registro, por defecto `5000` milisegundos, y el máximo de bytes de una lectura, por defecto 16 MiB, de modo que un atraso se lee en varias vueltas |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura de GrantForge, por defecto `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide nuevas políticas, por defecto `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (por defecto `500`), tamaño de la cola (por defecto `10000`), intervalo de envío (por defecto `5000` milisegundos) y límite de la cola en disco (por defecto 64 MiB) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo los accesos permitidos idénticos se agrupan en un evento, por defecto `10000`, hasta `3600000` milisegundos; `0` envía cada uno |

## Usuarios, grupos y roles

Las políticas nombran usuarios de PostgreSQL, los roles que pueden iniciar sesión. Los grupos son los roles de los que un usuario es miembro, directamente o mediante otros roles, junto con los grupos en los que GrantForge coloca al usuario; los roles son los que GrantForge da al usuario.

## Decisiones y limitaciones

- Sin instantánea el agente no cambia nada. Un servicio que no está en uso, o un token vinculado a un servicio cuyo tipo no es `postgresql`, no concede nada: el agente elimina sus roles y sus políticas de seguridad de filas, y se aplican los privilegios propios de PostgreSQL.
- Mientras el agente no se ejecuta, PostgreSQL conserva lo último que se le dio; los cambios de políticas lo esperan. Un cambio llega a PostgreSQL dentro del intervalo de actualización y el de sincronización.
- Las tablas creadas después de una sincronización reciben sus privilegios en la siguiente.
- Los superusuarios y los propietarios de una tabla pueden hacer de todo con ella; a los superusuarios, a los roles con `BYPASSRLS` y a los propietarios de una tabla no los vincula su seguridad de filas, salvo que el propietario la imponga.
- Una vista lee sus tablas con los privilegios de su propietario salvo que sea `security_invoker`: el registro del servidor informa de las tablas que leyó una vista como permitidas por un privilegio fuera de GrantForge.
- Leer una partición directamente no se filtra por la seguridad de filas de su tabla particionada.
- pgaudit registra una sentencia antes de que PostgreSQL la ejecute, así que una sentencia que falla después, como una inserción cuya secuencia se rechaza, se informa como permitida y su rechazo como denegado. No se informa de las sentencias de una sesión que pgaudit no registra.
- Los rechazos de PostgreSQL nombran una tabla sin su esquema: el evento nombra la tabla que califica la sentencia, la única tabla con ese nombre o la de `public`. Un objeto creado sin esquema se informa en `public`.
- El agente lee el registro del servidor en el que inicia sesión, el primario; no se leen los registros de las réplicas.
- PostgreSQL no tiene enmascaramiento de columnas, así que las políticas no pueden enmascarar valores.

## Guías relacionadas

- [PostgreSQL](/es/plugins/postgresql/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
