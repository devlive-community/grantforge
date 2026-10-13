---
title: Agente de Apache Hive
description: Instala el agente en HiveServer2 para comprobar sentencias, filtrar SHOW, enmascarar columnas y filtrar filas con las políticas de GrantForge.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de Hive es un autorizador de la API de autorización de Hive, como el plug-in de Hive de Apache Ranger: HiveServer2 le pregunta antes de cada sentencia y él responde según las políticas firmadas del servicio de datos `hive`. La distribución lo incluye como un único JAR compatible con Java 8, `agents/hive/grantforge-agent-hive-<version>.jar`, con sus dependencias reubicadas dentro.

Las pruebas de integración con Testcontainers del repositorio ejecutan el JAR dentro de HiveServer2 `3.1.3`, `4.0.1` y `4.1.0`. Esto no certifica las demás versiones. El plug-in de servicio se describe en [Apache Hive](/es/plugins/hive/).

## Funcionamiento

- **Se comprueban las sentencias.** Para cada objeto que una sentencia lee o escribe, el agente pregunta a las políticas por el tipo de acceso que exige la operación, como Ranger; la primera denegación hace fallar la sentencia con `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`. Las consultas se comprueban columna a columna.
- **Se filtran los listados.** `SHOW DATABASES` y `SHOW TABLES` solo listan lo que el usuario puede usar: cualquier acceso a la base de datos o tabla, o a algo que contenga. `USE` y `DESCRIBE DATABASE` exigen lo mismo.
- **Se enmascaran y filtran las consultas.** Cuando el usuario tiene columnas enmascaradas o un filtro de filas en una tabla, HiveServer2 reescribe la consulta con las máscaras y la condición, de modo que uniones, condiciones y funciones solo ven lo que el usuario puede ver.
- **Las máscaras no se pueden eludir.** Escribir, modificar o exportar una tabla en la que el usuario tiene una máscara o un filtro de filas se deniega, como Ranger hace por defecto.
- **Los permisos y los roles son de GrantForge.** `GRANT`, `REVOKE`, `SHOW GRANT`, `CREATE ROLE`, `SET ROLE` y las demás sentencias de roles de Hive se deniegan; `SHOW CURRENT ROLES` lista los roles que GrantForge da al usuario.
- **Se deniegan las sentencias inseguras.** Los comandos `dfs`, que llegarían al sistema de archivos con los permisos propios de HiveServer2, se deniegan. En las sesiones de HiveServer2 se prohíben los scripts `TRANSFORM` y los usuarios solo pueden cambiar con `SET` los ajustes que permite la autorización estándar SQL de Hive.
- **Cada decisión se notifica** a GrantForge con el usuario, la dirección del cliente, el recurso, el tipo de acceso, el resultado, la operación y la sentencia.

| Operación | Tipo de acceso |
| --- | --- |
| Consultas, `SHOW CREATE TABLE`, `SHOW PARTITIONS`, `SHOW TBLPROPERTIES`, `DESCRIBE`, `SHOW COLUMNS`, `ANALYZE` | `select` |
| `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `LOAD` (destino) | `update` |
| `CREATE DATABASE`, `CREATE TABLE`, `CREATE VIEW`, `CREATE FUNCTION` | `create` sobre lo que se crea, `select` sobre lo que se lee |
| `ALTER …`, `MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`, `UNLOCK` | `lock` |
| `SHOW DATABASES`, `SHOW TABLES`, `USE`, `DESCRIBE DATABASE` | Cualquier acceso al objeto o a algo que contenga |
| `REPL DUMP`, `REPL LOAD`, `REPL STATUS` | `repladmin` |
| `KILL QUERY`, planes de recursos, administración de LLAP | `serviceadmin` sobre `hiveservice` |
| Funciones temporales, `ADD JAR`, `COMPILE` | `tempudfadmin` sobre `global` |
| Ubicaciones que una sentencia lee o escribe | `read` o `write` sobre `url` |

## Despliegue

1. Añade un servicio de datos `hive` y configura sus políticas, incluida una que permita a los usuarios con los que HiveServer2 hace su propio trabajo, como `hive`, todo lo que necesitan. Abre **Permisos sobre los datos → Agentes**, selecciona el servicio y emite un token de agente. Solo se muestra una vez; guárdalo en un archivo que solo pueda leer la cuenta de HiveServer2.
2. Copia el JAR en `lib` de **cada HiveServer2**, como `/opt/hive/lib`.
3. Añade los ajustes a `hiveserver2-site.xml` o `hive-site.xml` de cada HiveServer2, sustituyendo la dirección, las rutas y el nombre de instancia de ejemplo:

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   La cuenta de HiveServer2 debe poder escribir en el directorio de caché. Como con Ranger, `hive.server2.enable.doAs=false` hace que HiveServer2 lea y escriba los datos con su propia cuenta, de modo que las políticas deciden quién ve qué.
4. Reinicia HiveServer2. El agente arranca con la primera sesión y se detiene con el servidor, enviando o encolando sus eventos pendientes.
5. **Cierra los caminos que evitan HiveServer2:** el agente solo comprueba las sentencias que se ejecutan en HiveServer2. Los clientes que llegan directamente al metastore o a los archivos del almacén, como la CLI de Hive, Spark o los clientes de HDFS, no se comprueban; protégelos con los permisos propios del metastore y de HDFS, o con el [agente de HDFS](/es/external/hdfs-agent/).

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor GrantForge, el archivo del token del agente, un nombre para este HiveServer2 y un directorio con escritura para la instantánea firmada y los eventos encolados |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copia la clave pública Ed25519 X.509 codificada en Base64 desde la página de agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura con GrantForge, `5000` / `8000` milisegundos por defecto |
| `grantforge.refresh.interval.ms` | Cada cuánto pide el agente nuevas políticas, `30000` por defecto, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (`500` por defecto), tamaño de la cola (`10000` por defecto), intervalo de envío (`5000` milisegundos por defecto) y límite de encolado (64 MiB por defecto) |

El agente lee sus ajustes de los archivos de configuración de HiveServer2 y de las propiedades del sistema (`-Dgrantforge.…`, que prevalecen), nunca de los ajustes de una sesión, que un cliente puede elegir.

## Usuarios, grupos y roles

Las políticas nombran al usuario que HiveServer2 asigna a la sesión: el nombre con el que inicia sesión un cliente, o el nombre corto de su principal Kerberos (`alice` para `alice@EXAMPLE.COM`). Los grupos son los que la asignación de grupos de Hadoop da al usuario en HiveServer2, junto con los grupos en los que GrantForge pone al usuario; los roles son los que GrantForge da al usuario.

## Decisiones y limitaciones

- Sin una instantánea utilizable, o si no se pueden leer las políticas, se deniega toda sentencia. Un servicio que no está en uso deja HiveServer2 sin comprobar, como sin autorizador.
- Un token vinculado a un servicio cuyo tipo no es `hive` lo deniega todo.
- Las políticas `url` deciden las ubicaciones que leen y escriben las sentencias; a diferencia de Ranger, el agente no recurre a los permisos del sistema de archivos.
- Las sentencias para las que Hive no nombra ningún objeto se comprueban sobre todas las bases de datos: las sentencias `REPL` sin objeto necesitan `repladmin` y cambiar el propietario de una tabla o base sin objeto necesita `alter`, sobre la base de datos `*`.
- `EXPLAIN`, `DESCRIBE FUNCTION`, `SHOW FUNCTIONS`, `SET` y las demás sentencias que Ranger no comprueba tampoco se comprueban; las llamadas de metadatos de los clientes JDBC, como `getTables`, no se filtran.
- El agente funciona en HiveServer2. La autorización dentro del metastore no está cubierta.

## Guías relacionadas

- [Apache Hive](/es/plugins/hive/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
