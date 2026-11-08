---
title: Agente de MongoDB
description: Ejecutar el agente de MongoDB junto a un despliegue para convertir las políticas de GrantForge en los roles propios de MongoDB, sustituir los privilegios de un rol en un solo paso, leer los roles integrados y personalizados ajenos a GrantForge para saber qué dan, e informar de los accesos desde el registro de auditoría o el profiler.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de MongoDB es un servicio junto al despliegue. Mantiene el control de acceso de MongoDB en sintonía con las políticas firmadas de un servicio de datos `mongodb`: un rol por usuario que contiene exactamente lo que las políticas le dan. Después, MongoDB comprueba cada comando por sí mismo, y el agente no se interpone ante ningún cliente. La distribución lo incluye como JAR ejecutable `agents/mongodb/grantforge-agent-mongodb-<version>.jar`; necesita Java 17 o posterior.

Las pruebas de integración con Testcontainers del repositorio ejecutan el agente, y el JAR tal como se distribuye, contra conjuntos de réplicas de MongoDB `6.0`, `7.0`, `8.0` y `9.0` y un clúster fragmentado `9.0`, y leen el registro de auditoría de Percona Server for MongoDB `7.0` y `8.0`. Esto no certifica todas las demás versiones; el agente rechaza despliegues anteriores a `6.0`. Para el plug-in de servicio, consulte [MongoDB](/es/plugins/mongodb/).

## Funcionamiento

- **Un rol por usuario contiene lo que da GrantForge.** Para cada usuario al que las políticas dan algo, el agente mantiene en `admin` un rol con su nombre, `grantforge_alice` para `alice`, y lo concede a cada usuario de MongoDB con ese nombre, como `alice@admin` y `alice@sales`. `rolesInfo` muestra lo que GrantForge da a `alice`. El agente no cambia ningún otro rol.
- **Un cambio ocurre en un solo paso.** `updateRole` sustituye de una vez los privilegios de un rol, de modo que un usuario nunca tiene a la vez los antiguos y los nuevos, ni ninguno de los dos.
- **Bases de datos completas donde nada por debajo decide otra cosa.** Un tipo de acceso que una política permite en toda una base de datos, sin ninguna política que pueda quitarlo por debajo, se concede en la base de datos, de modo que las colecciones creadas después lo tienen de inmediato. En otro caso se concede colección por colección, y las nuevas se cubren en la siguiente sincronización.
- **Nunca más de lo que permiten las políticas.** El privilegio de MongoDB para borrar una base de datos es el privilegio de borrar cada colección que contiene. El agente lo concede solo donde las políticas también dan eso; si no, lo retiene y registra una vez qué usuario no puede hacer qué y por qué.
- **Las políticas del momento presente.** Cada sincronización planifica según las políticas tal como se aplican en ese momento, de modo que los periodos de validez empiezan y terminan a tiempo, y según los usuarios, bases de datos y colecciones que tiene MongoDB, de modo que los nuevos se cubren en la siguiente sincronización.
- **Los roles de MongoDB son grupos.** Una política para un grupo se aplica a los usuarios que tienen un rol de MongoDB con ese nombre, junto con los grupos en los que GrantForge los coloca.
- **Los cambios manuales se deshacen.** En cada sincronización el agente compara sus roles con los planes y corrige privilegios cambiados a mano, roles que se les hizo heredar, concesiones de un rol a otra persona y roles que nadie necesita. Si nada cambió, no envía nada.
- **Se lee qué dan los roles ajenos a GrantForge.** MongoDB suma lo que dan todos los roles de un usuario, así que el agente no puede quitar lo que un usuario tiene mediante otro rol. MongoDB dice qué da cada rol, también los integrados y heredados, así que el agente avisa, una vez cada uno, de un rol como `readWrite@hr` que da lo que una política deniega, nombrando al usuario, el acceso y la política. También avisa de los usuarios que pueden administrar usuarios, y así darse cualquier cosa, de los usuarios con `root` o toda acción sobre todo recurso, y de un servidor que no comprueba ninguna autorización.
- **Los accesos vienen del registro de auditoría o del profiler.** Consulte Informar de los accesos más abajo. Cada evento dice si decidió GrantForge, es decir, si las políticas deciden como MongoDB, o si un rol ajeno permitió el comando. Una agregación se informa para cada colección que leen sus etapas `$lookup`, `$graphLookup` y `$unionWith` y escriben `$out` y `$merge`. Los eventos nombran el comando y su espacio de nombres, nunca sus argumentos, que pueden contener documentos y contraseñas.

## Despliegue

1. Añada un servicio de datos `mongodb` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, seleccione el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un fichero que solo pueda leer la cuenta del agente.
2. Cree el usuario de MongoDB del agente. Crea y cambia roles y lee usuarios y roles con `userAdminAnyDatabase`; lista colecciones con un rol que solo tiene eso; y lee las opciones del servidor y el profiler con `clusterMonitor`. `userAdminAnyDatabase` puede darse cualquier cosa, así que proteja este usuario como a un administrador. El nombre del rol de listado no debe empezar por el prefijo de roles del agente.

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. Para informar de los accesos, configure una de las fuentes de Informar de los accesos más abajo.
4. Escriba el fichero de ajustes sustituyendo las direcciones, rutas y nombres de ejemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   Estas rutas están en la máquina del agente, y el directorio de caché debe poder escribirse. Los ficheros de contraseña se leen como UTF-8 y se recortan.
5. Inicie el agente, por ejemplo como servicio de systemd:

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   Sustituya `<version>` por la versión de la distribución. Los ajustes indicados como `java -Dgrantforge.…=… -jar …` sobrescriben el fichero. El agente se detiene limpiamente con SIGTERM y envía o guarda los eventos pendientes; MongoDB conserva lo que se le dio.
6. **Pasar los privilegios existentes a políticas:** los roles concedidos fuera de GrantForge siguen aplicándose junto a los del agente. Cree las políticas correspondientes y revoque después esos roles. Los avisos del agente señalan los que dan lo que una política deniega.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorio: el servidor de GrantForge, el fichero del token del agente, un nombre para este agente y un directorio escribible para la instantánea firmada, los eventos en cola y la posición de lectura del registro de auditoría |
| `grantforge.signing.key.file` | Fichero opcional que fija la clave de firma de las instantáneas; copie la clave pública Ed25519 X.509 codificada en Base64 de la página de agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.mongodb.uri` | Obligatorio: la cadena de conexión, sin credenciales |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256` (predeterminado), `SCRAM-SHA-1`, `MONGODB-X509`, `PLAIN` o `GSSAPI` |
| `grantforge.mongodb.user`, `grantforge.mongodb.password.file`, `grantforge.mongodb.auth.source` | El usuario del agente, con `GSSAPI` su principal y con `MONGODB-X509` opcional; su contraseña; y para SCRAM la base de datos en la que se define, `admin` de forma predeterminada |
| `grantforge.mongodb.tls.ca.file`, `grantforge.mongodb.tls.certificate.key.file`, `grantforge.mongodb.tls.certificate.key.password.file` | Con `tls=true` en la cadena de conexión: los certificados CA PEM que verifican los servidores, vacío para lo que Java confía; un archivo PEM con el certificado de cliente y su clave; y la contraseña de una clave cifrada |
| `grantforge.mongodb.kerberos.keytab`, `grantforge.mongodb.kerberos.service.name` | `GSSAPI`: el keytab del principal, si no se usa la contraseña, y el nombre de servicio del principal de los servidores, `mongodb` de forma predeterminada. El reino viene del `krb5.conf` de la máquina |
| `grantforge.mongodb.timeout.ms` | Cuánto pueden tardar encontrar un servidor, conectar y un comando; `30000` de forma predeterminada, en el rango `1..600000` milisegundos |
| `grantforge.mongodb.role.prefix` | Con qué empiezan los nombres de los roles del agente, `grantforge_` de forma predeterminada. Letras, dígitos y guiones bajos, terminado en guion bajo, 32 caracteres como máximo. Todo rol de `admin` cuyo nombre empiece así pertenece al agente |
| `grantforge.mongodb.unmanaged.users` | Usuarios que el agente deja en paz, separados por comas, como cuentas de servicio. Siempre deja en paz a su propio usuario y a los usuarios con `root`, `__system` o toda acción sobre todo recurso |
| `grantforge.mongodb.databases` | Las bases de datos en las que el agente concede por colección, separadas por comas, vacío para todas. Las demás reciben solo lo que se puede conceder en conjunto, para que una base de datos con muchísimas colecciones no se liste en cada sincronización |
| `grantforge.mongodb.audit.files` | Los archivos del registro de auditoría que se leen, separados por comas. Vacío lee el profiler |
| `grantforge.sync.interval.ms` | Con qué frecuencia el agente pone MongoDB de acuerdo, por defecto `10000`, rango `1000..3600000` milisegundos |
| `grantforge.audit.log` | `true` de forma predeterminada. `false` no lee ni informa de accesos |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Cuánto por detrás del presente se lee el profiler (`2000` de forma predeterminada), con qué frecuencia se leen los accesos (`5000` milisegundos de forma predeterminada) y el máximo de entradas que toma una lectura de un archivo o base de datos (`1000` de forma predeterminada) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura hacia GrantForge, por defecto `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide políticas nuevas, por defecto `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (por defecto `500`), tamaño de cola (por defecto `10000`), intervalo de envío (por defecto `5000` milisegundos) y el límite de la cola en disco (por defecto 64 MiB) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo se agrupan en un evento los accesos permitidos idénticos, por defecto `10000`, hasta `3600000` milisegundos; `0` envía cada uno |

## Informar de los accesos

| Fuente | Dónde | Qué dice | Cómo activarla |
| --- | --- | --- | --- |
| Registro de auditoría | MongoDB Enterprise y Percona Server for MongoDB | Qué se permitió y qué se rechazó, en cada miembro cuyo archivo puede leer el agente | Inicie `mongod` con `--auditDestination file --auditFormat JSON --auditPath <path>` y `--setParameter auditAuthorizationSuccess=true`, y nombre los archivos en `grantforge.mongodb.audit.files` |
| Profiler | Todas las ediciones | Qué se ejecutó en el miembro con el que habla el agente, el primario de un conjunto de réplicas; ningún rechazo | `db.setProfilingLevel(2)` en cada base de datos que se informe. Tiene un coste para el servidor, así que el agente nunca lo activa, y `system.profile` es una pequeña colección limitada |

El registro de auditoría se lee de archivos, así que ejecute el agente en el nodo o dele un directorio que los reúna. Las pruebas leen el registro de auditoría de Percona Server, que tiene el formato de MongoDB Enterprise. Un `mongos` no tiene profiler, así que un clúster fragmentado informa de los accesos solo desde el registro de auditoría.

| Comando | Evento de acceso |
| --- | --- |
| `find`, `aggregate`, `count`, `distinct`, `listIndexes` | `read` en cada colección leída |
| `aggregate` que empieza con `$changeStream` | `change_stream` |
| `insert`, `update`, `delete`, `findAndModify`, `bulkWrite` | `insert`, `update` o `remove` en cada colección escrita; también `bypass_validation` si se solicita |
| `createIndexes`, `dropIndexes` | `create_index`, `drop_index` |
| `collMod`, `validate`, `convertToCapped` | `alter` |
| `drop`, `create`, `dropDatabase` | `drop`, `create`, `drop_database` |
| `listDatabases`; `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `currentOp` | `list_databases`; `monitor` |

MongoDB rechaza un comando en conjunto sin decir cuál de sus colecciones fue el motivo, así que un rechazo se informa para las que las políticas deniegan, o para la primera si no deniegan ninguna.

## Usuarios, grupos y roles

Las políticas nombran a los usuarios de MongoDB por su nombre de usuario. Un nombre definido en varias bases de datos es un solo usuario, y el rol del agente se concede a todos ellos. Los grupos son los roles de MongoDB de un usuario y los grupos en los que lo coloca GrantForge. Los roles son los que GrantForge da al usuario.

## Decisiones y limitaciones

- Sin una instantánea el agente no cambia nada. Un token vinculado a un servicio sin uso, o a un servicio cuyo tipo no es `mongodb`, no concede nada: el agente borra sus roles y los propios de MongoDB permanecen.
- Mientras el agente no se ejecuta, MongoDB conserva lo último que recibió y los cambios de políticas esperan. Un cambio llega a MongoDB dentro de los intervalos de actualización y sincronización; un `mongos` lo aplica cuando caduca su caché de usuarios, de forma predeterminada en 30 segundos (`userCacheInvalidationIntervalSecs`).
- Los privilegios de MongoDB nombran bases de datos y colecciones con exactitud, así que un comodín de una política se concede para lo que existe. Una base de datos existe cuando tiene una colección: hasta entonces, `create` en una base de datos que nombra una política no tiene nada sobre lo que concederse.
- Los usuarios no definidos en ningún lugar de MongoDB, como aquellos que la autorización LDAP asigna a roles por sí misma, no se ven, y el agente no mantiene ningún rol para ellos.
- Un privilegio sobre toda una base de datos cubre sus colecciones pero no sus colecciones `system.`, y las bases de datos propias de MongoDB quedan a cargo de MongoDB.
- Renombrar colecciones y las demás acciones administrativas de MongoDB no son tipos de acceso: siguen en manos de roles ajenos a GrantForge.
- MongoDB no filtra filas ni enmascara campos por rol; una [vista](/es/plugins/mongodb/) da una lectura restringida.

## Guías relacionadas

- [MongoDB](/es/plugins/mongodb/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
