---
title: Agente de Apache Doris
description: Ejecutar el agente de Apache Doris junto al clúster, para que las políticas de GrantForge se conviertan en los roles, privilegios de columnas, privilegios de grupos de carga de trabajo y políticas de filas propios de Doris, dados a cada identidad de un usuario, concedidos sobre bases de datos y catálogos completos solo donde Doris no daría más, y los accesos se informen desde la tabla del registro de auditoría de Doris.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de Apache Doris es un servicio junto al clúster. Mantiene el control de acceso propio de Doris de acuerdo con las políticas firmadas de un servicio de datos `doris`: un rol por usuario que contiene exactamente lo que las políticas le dan, hasta columnas y grupos de carga de trabajo, y una política de filas por filtro de filas. Doris comprueba entonces cada sentencia por sí mismo, y el agente nunca se interpone entre los clientes y los frontends. La distribución lo entrega como un JAR ejecutable, `agents/doris/grantforge-agent-doris-<version>.jar`, que requiere Java 17 o posterior.

Las pruebas de integración con Testcontainers del repositorio ejecutan el agente, y el JAR tal como se entrega, contra Apache Doris `2.1`, `3.1`, `4.0` y `4.1`. Esto no certifica todas las demás versiones; el agente rechaza clústeres anteriores a `2.1`. El plug-in de servicio se describe en [Apache Doris](/es/plugins/doris/).

## Funcionamiento

- **Un rol por usuario contiene lo que da GrantForge.** Para cada usuario al que las políticas dan algo, el agente mantiene un rol con su nombre, `grantforge_alice` para `alice`, con un comentario que nombra al usuario, y lo concede a cada identidad del usuario, como `'alice'@'%'` y `'alice'@'10.%'`. `SHOW ROLES` muestra lo que GrantForge da a `alice`; el agente no cambia ningún otro rol ni ningún privilegio que no concedió.
- **Bases de datos y catálogos completos donde nada inferior se decide de otra forma.** Leer, cargar y mostrar vistas que una política permite en toda una base de datos o catálogo, sin ninguna política que pudiera quitarlos por debajo, se conceden en la base de datos o el catálogo, así que las tablas creadas después los tienen al instante. Si no, se conceden tabla por tabla, y la lectura columna por columna. Alterar se concede tabla por tabla, ya que el privilegio de Doris sobre una base de datos también la renombraría.
- **Nunca más de lo que permiten las políticas.** El privilegio de Doris de crear bases de datos en un catálogo es el de crear tablas en todas ellas, y el de eliminar una base de datos el de eliminar todas sus tablas. El agente concede cada uno solo donde las políticas dan también el privilegio más amplio; si no, lo retiene y registra una vez qué usuario no puede hacer qué y por qué.
- **Los grupos de carga de trabajo se conceden por nombre.** Una política sobre grupos de carga de trabajo da `USAGE_PRIV` sobre cada grupo que permite, así que un usuario puede ejecutar consultas en él; los grupos creados después se cubren en la siguiente sincronización.
- **Los filtros de filas se convierten en políticas de filas.** Un filtro de filas es una política de filas permisiva del rol del usuario sobre la tabla. Un filtro nuevo está en su sitio antes que los privilegios que restringe; un filtro que cambia se sustituye detrás de una política restrictiva que no muestra filas, así que el usuario nunca ve más de lo que muestra cualquiera de los dos. Cada filtro se ejecuta antes sobre su tabla; uno que Doris no puede ejecutar ahí o rechaza, como uno que nombra una columna que la tabla no tiene, se sustituye por uno que no muestra ninguna fila al usuario, y se registra.
- **Las políticas del momento presente.** Cada sincronización planifica con las políticas tal como se aplican en ese momento, de modo que los periodos de validez empiezan y terminan a tiempo, y con los usuarios, catálogos, bases de datos, tablas y columnas que tiene Doris, de modo que los nuevos se cubren en la siguiente sincronización.
- **Los roles de Doris son grupos.** Una política para un grupo se aplica a los usuarios que tienen un rol de Doris con ese nombre, junto con los grupos en los que GrantForge los pone.
- **Los cambios hechos a mano se deshacen.** En cada sincronización el agente compara los privilegios de sus roles, tal como los muestra `SHOW ROLES`, y sus políticas de filas con los planes, y restablece lo que difiere. Doris muestra los privilegios de columnas solo por usuario, unidos sobre todos sus privilegios: el agente concede las columnas que nombra un plan donde un usuario no puede leerlas todas, y retira las demás de su rol una vez; una columna que sigue legible viene de un privilegio ajeno a GrantForge. Cuando nada cambió, el agente no envía nada.
- **Se informan los privilegios ajenos a GrantForge.** Doris suma los privilegios y une las políticas de filas permisivas con OR, así que el agente no puede quitar lo que un usuario tiene directamente o mediante otro rol. Avisa una vez de cada privilegio, privilegio de columna o política de filas así que dé lo que una política deniega, nombrando el usuario, el privilegio y su origen, y de los usuarios con `ADMIN_PRIV` o `NODE_PRIV`, a los que ningún rol limita.
- **Los accesos vienen del registro de auditoría.** El agente lee `__internal_schema.audit_log`, que escribe cada frontend, e informa cada sentencia como un evento por tabla, base de datos o catálogo que tocó; qué tablas son lo lee de la sentencia misma con el analizador de Doris de Druid, ya que el registro no nombra ninguna. Un rechazo es un evento denegado del privilegio y el objeto que nombra el error de Doris. Cada evento dice si lo decidió GrantForge, es decir, si las políticas deciden como Doris, o si lo permitió un privilegio ajeno a GrantForge. El registro se lee por detrás del presente el intervalo de lotes de los frontends, `audit_plugin_max_batch_interval_sec`, y cinco segundos más.

| Sentencia | Evento de acceso |
| --- | --- |
| `SELECT`, la parte de lectura de `INSERT … SELECT` | `select` en cada tabla leída |
| `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` | `load` en la tabla escrita |
| `ALTER TABLE`, `DROP TABLE`, `SHOW CREATE VIEW` | `alter`, `drop`, `show_view` en la tabla |
| `CREATE TABLE`, `CREATE VIEW` | `create` en la base de datos |
| `DROP DATABASE` | `drop_database` en la base de datos |
| `CREATE DATABASE` | `create_database` en el catálogo |

## Despliegue

1. Añada un servicio de datos `doris` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, seleccione el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un fichero que solo pueda leer la cuenta del agente.
2. Cree el usuario de Doris del agente. Crea roles y políticas de filas, concede lo que dan las políticas y lee los privilegios de cada usuario y el registro de auditoría, lo que permite `ADMIN_PRIV`:

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. Para informar de los accesos, active el registro de auditoría de Doris donde esté desactivado, como en Doris 2.1: `SET GLOBAL enable_audit_plugin = true`. Un `audit_plugin_max_batch_interval_sec` más corto que sus 60 segundos por defecto informa antes de los accesos.
4. Escriba el fichero de ajustes sustituyendo las direcciones, rutas y nombres de ejemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   Estas rutas están en la máquina del agente, y el directorio de caché debe poder escribirse. Los ficheros de contraseña se leen como UTF-8 y se recortan.
5. Inicie el agente, por ejemplo como servicio de systemd:

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   Sustituya `<version>` por la versión de la distribución. Los ajustes indicados como `java -Dgrantforge.…=… -jar …` sobrescriben el fichero. El agente se detiene limpiamente con SIGTERM y envía o guarda los eventos pendientes; Doris conserva lo que se le dio.
6. **Pase los privilegios existentes a políticas:** los privilegios concedidos fuera de GrantForge siguen aplicándose junto a los roles del agente. Cree las políticas equivalentes y retire luego esos privilegios; los avisos del agente nombran los que dan lo que una política deniega.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorio: el servidor de GrantForge, el fichero del token del agente, un nombre para este agente y un directorio escribible para la instantánea firmada, los eventos en cola y la posición de lectura del registro de auditoría |
| `grantforge.signing.key.file` | Fichero opcional que fija la clave de firma de las instantáneas; copie la clave pública Ed25519 X.509 codificada en Base64 de la página de agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.doris.hosts`, `grantforge.doris.user` | Obligatorio: las direcciones del protocolo MySQL de los frontends, probadas por turno cuando una no responde, el puerto `9030` si no se indica, y el usuario de Doris del agente |
| `grantforge.doris.password.file` | La contraseña del usuario; si se omite, el agente inicia sesión sin ella |
| `grantforge.doris.ssl.mode`, `grantforge.doris.tls.ca` | `disable` (por defecto), `trust`, `verify-ca` o `verify-full`, para frontends que ofrecen TLS; y el certificado CA PEM que los verifica, vacío para lo que confía Java |
| `grantforge.doris.timeout.ms` | Cuánto pueden durar la conexión y una sentencia, por defecto `30000`, rango `1..600000` milisegundos |
| `grantforge.doris.role.prefix` | El inicio de los nombres de los roles del agente, por defecto `grantforge_`; letras, dígitos y guiones bajos, terminado en guion bajo, como mucho 32 caracteres. Al agente le pertenece cada rol y política de filas cuyo nombre empieza así |
| `grantforge.doris.unmanaged.users` | Usuarios que el agente deja en paz, separados por comas, como cuentas de servicio; siempre deja en paz a su propio usuario, `root`, `admin` y los usuarios con `ADMIN_PRIV` o `NODE_PRIV` |
| `grantforge.doris.catalogs` | Los catálogos en los que el agente concede por base de datos, tabla y columna, separados por comas; vacío para todos. Los demás reciben solo lo que puede concederse sobre ellos completos, así que un catálogo externo con muchas tablas no tiene que listarse en cada sincronización |
| `grantforge.sync.interval.ms` | Con qué frecuencia el agente pone Doris de acuerdo, por defecto `10000`, rango `1000..3600000` milisegundos |
| `grantforge.audit.log` | Por defecto `true`; `false` no lee el registro de auditoría ni informa de accesos |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Cuánto por detrás del presente se lee el registro, por defecto el intervalo de lotes del clúster y cinco segundos; con qué frecuencia, por defecto `5000` milisegundos; y cuántas entradas como máximo toma una lectura, por defecto `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura hacia GrantForge, por defecto `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide políticas nuevas, por defecto `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (por defecto `500`), tamaño de cola (por defecto `10000`), intervalo de envío (por defecto `5000` milisegundos) y el límite de la cola en disco (por defecto 64 MiB) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo se agrupan en un evento los accesos permitidos idénticos, por defecto `10000`, hasta `3600000` milisegundos; `0` envía cada uno |

## Usuarios, grupos y roles

Las políticas nombran a los usuarios de Doris por sus nombres de usuario; un nombre de usuario con varios hosts es un usuario, y el rol del agente va a cada una de sus identidades. Los grupos son los roles de Doris de un usuario junto con los grupos en los que GrantForge lo pone; los roles son los roles que GrantForge da al usuario.

## Decisiones y limitaciones

- Sin instantánea el agente no cambia nada. Un servicio que no está en uso, o un token vinculado a un servicio cuyo tipo no es `doris`, no concede nada: el agente elimina sus roles y políticas de filas, y se aplican los privilegios propios de Doris.
- Mientras el agente no se ejecuta, Doris conserva lo último que se le dio; los cambios de políticas lo esperan. Un cambio llega a Doris dentro del intervalo de actualización y del de sincronización.
- Las tablas creadas en una base de datos en la que el agente concede tabla por tabla, y las bases de datos de un catálogo en el que concede base de datos por base de datos, reciben sus privilegios en la siguiente sincronización.
- Las políticas de filas de Doris no pueden limitar a `root` ni a `admin`. Un usuario para el que Doris no muestra ninguna política de filas ve todas las filas de una tabla; las políticas de filas permisivas creadas fuera de GrantForge para el mismo usuario amplían lo que muestra su filtro.
- Doris comprueba `USAGE_PRIV` sobre un grupo de carga de trabajo cuando una consulta se ejecuta en los backends; una consulta que el frontend responde por sí mismo, como una constante o un resultado en caché, se ejecuta sin la comprobación. Todo usuario puede usar el grupo `normal`, ya que Doris lo da a todos.
- Los usuarios a los que el agente mantiene roles deben existir en Doris; los usuarios que inician sesión mediante LDAP sin un usuario propio en Doris no reciben ningún rol.
- Doris enmascara valores solo a través de Apache Ranger, por lo que las políticas no pueden enmascarar valores.

## Guías relacionadas

- [Apache Doris](/es/plugins/doris/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
