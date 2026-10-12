---
title: Agente proxy de MySQL
description: Despliega el proxy de red MySQL, configura TLS y la sincronización de políticas, y verifica permisos de columnas, enmascaramiento, filtros de filas y errores de denegación.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente MySQL es un proxy de red independiente: los clientes se conectan a él en lugar de al servidor, y comprueba las sentencias compatibles con las políticas firmadas del servicio de datos `mysql` antes de que lleguen a MySQL. No necesita instalarse dentro de MySQL. La distribución incluye el JAR ejecutable `agents/mysql/grantforge-agent-mysql-<version>.jar`, que requiere Java 17 o posterior.

Las pruebas de integración con Testcontainers del repositorio utilizan `mysql:8.0` y `mysql:8.4` con clientes MariaDB Connector/J y MySQL Connector/J. Esto no certifica otras versiones del servidor ni todas las funciones del protocolo MySQL. Consulta [MySQL](/es/plugins/mysql/) para los requisitos de versión y conexión del plug-in de servicio.

## Cómo funciona

- **El inicio de sesión pasa tal cual.** El saludo del servidor llega al cliente y las respuestas del cliente llegan al servidor, así que MySQL comprueba las contraseñas (`caching_sha2_password` y `mysql_native_password`); el proxy conoce el usuario que MySQL aceptó. Las políticas nombran a ese usuario, sin su host.
- **Se comprueban los recursos SQL.** El proxy analiza COM_QUERY y las sentencias preparadas en el servidor y comprueba los tipos de acceso necesarios en sus bases de datos, tablas y columnas. Una sentencia rechazada no llega a MySQL; el proxy genera un error con un código compatible con MySQL, como 1142 o 1143.
- **Las lecturas se enmascaran y filtran.** Cuando una sentencia lee una tabla con columnas enmascaradas o un filtro de filas para el usuario, el proxy la sustituye por una tabla derivada del mismo nombre que muestra los valores enmascarados y solo las filas filtradas, de modo que uniones, condiciones y funciones ven lo que el usuario puede ver.
- Los **eventos de autorización de recursos** se envían a GrantForge con el usuario, la dirección del cliente, el recurso, el tipo de acceso, la decisión y la sentencia. El alcance de la auditoría se describe más adelante.
- Los resultados de ejecución de MySQL se transmiten al cliente; el proxy no modifica los valores originales almacenados en la base de datos.

## Despliegue

1. Añade un servicio de datos `mysql` y configura sus políticas. Abre **Permisos sobre los datos → Agentes**, selecciona el servicio y emite un token de agente. Se muestra una sola vez; guárdalo en un archivo que solo pueda leer la cuenta del proxy.
2. Crea un usuario de metadatos para el proxy. Lee las columnas de las tablas y las funciones almacenadas de `information_schema` y no necesita datos: `REFERENCES` muestra todas las columnas y `SHOW_ROUTINE` todas las funciones almacenadas. Concédelos directamente al usuario, no mediante un rol; sin `SHOW_ROUTINE` el proxy no arranca.

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. Escribe el archivo de ajustes y sustituye las direcciones, rutas y nombres de ejemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   Este ejemplo activa TLS de los clientes al proxy y del proxy a MySQL. `mysql-proxy.crt` debe coincidir con el nombre de host del proxy que usan los clientes; el certificado de MySQL debe coincidir con `mysql.internal`. Las rutas son de la máquina del proxy y el directorio de caché debe permitir escritura. Los archivos de contraseñas se leen como UTF-8 y se eliminan los espacios en blanco iniciales y finales, por lo que no pueden almacenar contraseñas que los necesiten.

4. Inicia el proxy, por ejemplo como servicio de systemd:

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   Sustituye `<version>` por la versión de la distribución. Los ajustes pasados como `java -Dgrantforge.…=… -jar …` prevalecen sobre el archivo. El proxy se detiene limpiamente con SIGTERM y envía o guarda en disco los eventos pendientes.
5. Apunta los clientes a la dirección y el puerto del proxy. MySQL ve ahora la dirección del proxy, así que la parte de host de las cuentas debe permitirla.
6. **Cierra el camino que rodea al proxy:** los clientes que llegan directamente a MySQL no se comprueban. Deja que solo el proxy y los administradores alcancen el puerto del servidor.

| Ajuste | Función |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor GrantForge, el archivo del token del agente, un nombre para este proxy y un directorio escribible para la instantánea firmada y los eventos guardados en disco |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copia la clave pública Ed25519 X.509 codificada en Base64 de la página de agentes. Si se omite, se obtiene la clave del servidor en el primer contacto |
| `grantforge.proxy.host`, `grantforge.proxy.port` | Dirección de escucha; todas las direcciones y puerto `3307` por defecto. Rango `0..65535`; `0` asigna un puerto automáticamente |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | Certificado y clave privada PEM; ambos se requieren juntos para ofrecer TLS a los clientes |
| `grantforge.proxy.tls.required` | `false` por defecto; `true` rechaza clientes sin TLS (error 3159) y requiere el certificado y la clave privada |
| `grantforge.mysql.host`, `grantforge.mysql.port` | Host MySQL obligatorio; puerto `3306` por defecto, rango `1..65535` |
| `grantforge.mysql.tls` | TLS hacia MySQL: `disabled` (por defecto), `required` (cifrado, sin verificar), `verify-ca` o `verify-identity` |
| `grantforge.mysql.tls.ca` | CA en PEM para verificar MySQL; vacío utiliza las autoridades en las que confía Java |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | Usuario de metadatos obligatorio; el archivo de contraseña es opcional y, si se omite, no se pasa contraseña al controlador |
| `grantforge.mysql.metadata.cache.seconds` | Duración de la caché de columnas y funciones almacenadas: `30` por defecto, rango `0..86400` segundos; `0` desactiva su reutilización |
| `grantforge.mysql.connect.timeout.ms` | Tiempo de espera de conexión a MySQL: `10000` por defecto, rango `1..600000` milisegundos; las lecturas de metadatos tienen un tiempo fijo de `30000` milisegundos |
| `grantforge.proxy.check.threads` | Hilos para comprobar sentencias: `8` por defecto, rango `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura de GrantForge: `5000` / `8000` milisegundos por defecto, rango `1..2147483647` |
| `grantforge.refresh.interval.ms` | Intervalo inicial o de respaldo cuando el servidor no está disponible: `30000` milisegundos por defecto, mínimo `1000`; los latidos normales usan el intervalo devuelto por el servidor |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | Lote de `500` y cola de `10000` eventos por defecto; rango del lote `1..1000`, cola al menos del tamaño del lote y como máximo `1000000` |
| `grantforge.audit.flush.interval.ms` | Intervalo de envío de auditoría: `5000` milisegundos por defecto, rango `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | Límite de almacenamiento de auditoría en disco: `67108864` (64 MiB) por defecto, mínimo `0` |

TLS es independiente en cada lado. Cuando solo un lado lo usa y MySQL pide la contraseña completa (`caching_sha2_password` sin acierto en caché), el proxy la traslada: a un cliente sin TLS que pide la clave del servidor le responde con una clave RSA propia, y cifra la contraseña de un cliente con TLS con la clave de MySQL para un servidor sin TLS. Los clientes no deben configurarse con el archivo de clave pública de MySQL a través del proxy.

## Qué necesitan las sentencias

| Sentencia | Acceso |
| --- | --- |
| `SELECT`, subconsultas, `WITH`, `EXPLAIN`, `SET @x = (SELECT …)` | `select` en cada columna usada; una tabla leída sin columna, como con `COUNT(*)` o `EXISTS`, necesita `select` sobre toda la tabla |
| `INSERT`, `LOAD DATA` | `insert` en las columnas escritas (todas si no se listan); `ON DUPLICATE KEY UPDATE` añade `update` |
| `REPLACE` | `insert` en las columnas y `delete` en la tabla |
| `UPDATE` | `update` en las columnas asignadas y `select` en las leídas |
| `DELETE` | `delete` en la tabla y `select` en las columnas leídas |
| `CREATE TABLE`, `CREATE VIEW` / `DROP TABLE`, `DROP VIEW`, `TRUNCATE` / `ALTER TABLE`, `ALTER VIEW`, `OPTIMIZE` | `create` / `drop` / `alter` en la tabla o vista |
| `RENAME TABLE` | `alter` y `drop` en el nombre antiguo, `create` e `insert` en el nuevo |
| `CREATE INDEX`, `DROP INDEX` | `index` en la tabla |
| `CREATE DATABASE`, `DROP DATABASE` | `create`, `drop` en la base de datos |
| `LOCK TABLES`, `ANALYZE`, `CHECK`, `CHECKSUM TABLE` | `select` en la tabla |

Las sentencias que no usan datos quedan en manos de los privilegios de MySQL: transacciones, `SET`, `USE`, `SHOW`, `DESCRIBE`, `KILL` y sentencias de cuentas como `GRANT` y `CREATE USER`. `information_schema` y `performance_schema` son bases de datos como las demás y necesitan políticas.

El proxy rechaza las sentencias que no puede comprobar por completo: comentarios ejecutables (`/*! … */`), funciones almacenadas y `CALL`, `CREATE PROCEDURE`, `FUNCTION`, `TRIGGER` y `EVENT`, `PREPARE`/`EXECUTE` a nivel de SQL, `HANDLER`, comandos de replicación y clonación, `USE` o `SET NAMES` junto con otras sentencias en un mismo lote y sentencias que no puede analizar.

## Máscaras, filtros de filas y sus límites

- Máscaras y filtros se aplican dondequiera que una sentencia lea la tabla, incluidas subconsultas, uniones, `INSERT … SELECT` y `CREATE TABLE … SELECT` (cuya copia contiene los valores enmascarados).
- Un `UPDATE` o `DELETE` de una sola tabla solo modifica las filas que muestra el filtro. Un `UPDATE` o `DELETE` multitabla de una tabla filtrada se rechaza, igual que una escritura que usa una columna enmascarada de la tabla que modifica, ya que podría copiar o sondear los valores ocultos.
- No se puede crear ni modificar una vista sobre una tabla enmascarada o filtrada: MySQL ejecuta la vista más tarde, sin el proxy.
- Las sentencias preparadas reutilizan su comprobación y reescritura mientras no cambie la versión de las políticas. Un cambio de versión provoca otra comprobación: la ejecución continúa si el acceso sigue permitido y el texto reescrito no cambia; de lo contrario, el error 1615 exige que el cliente prepare de nuevo la sentencia.
- Una columna nombrada sin su tabla cuenta para cada tabla a la vista que tenga una columna así; cualifica las columnas para evitar rechazos por una columna restringida del mismo nombre.
- Las columnas leídas mediante `*` proceden de lo que el usuario de metadatos ve en `information_schema` y se guardan en caché durante `grantforge.mysql.metadata.cache.seconds`. El DDL ejecutado correctamente a través del proxy como comando SQL ordinario invalida la caché, por lo que la siguiente búsqueda vuelve a leer los metadatos. El DDL externo o preparado no garantiza la invalidación durante su ejecución; espera a que caduque la caché o reinicia el proxy.

## Alcance de la aplicación de políticas

El proxy comprueba los recursos que puede analizar en el SQL del cliente. No expande recursivamente vistas existentes, disparadores u otras operaciones ejecutadas internamente por el servidor. Para proteger las tablas subyacentes, restringe también los privilegios nativos de MySQL y el uso de vistas y disparadores existentes. No concedas privilegios de administrador a usuarios de negocio esperando que el proxy intercepte sentencias administrativas.

Un filtro de filas no es una restricción general de escritura: `INSERT`, `REPLACE` y `LOAD DATA` comprueban permisos de escritura, pero no exigen automáticamente que las nuevas filas cumplan la expresión del filtro. Tampoco se añade una condición de filtro de filas a la rama de `INSERT … ON DUPLICATE KEY UPDATE` que actualiza una fila existente. La compresión del protocolo, ZSTD, los atributos de consulta y los metadatos de resultados opcionales se desactivan durante la negociación.

## Políticas y fallos

- Sin una instantánea utilizable, o con un token vinculado a un servicio que no es `mysql`, se rechaza toda sentencia que use datos.
- Si GrantForge no está disponible temporalmente o la firma de una nueva instantánea no es válida, el proxy conserva la instantánea ya aplicada. Un arranque inicial sin ninguna instantánea utilizable rechaza el acceso a recursos.
- Desactivar el servicio omite la autorización de recursos, el enmascaramiento y el filtrado de filas de GrantForge; el análisis SQL y las restricciones del protocolo siguen aplicándose. Desactivar el servicio no bloquea el acceso a datos.
- Los privilegios de MySQL siguen aplicándose tras las comprobaciones del proxy; el proxy solo puede restringirlos.
- `COM_CHANGE_USER` se sigue, y se rechaza cuando los lados del cliente y del servidor difieren en TLS; en su lugar, vuelve a conectarte como el otro usuario.

## Ejemplo de verificación

En una base de datos de prueba separada, pide a un administrador que cree los siguientes datos y cuenta. Sustituye `proxy-host` por la dirección de origen del proxy que realmente ve MySQL:

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

Crea tres políticas de GrantForge para `alice`: permite `select` sobre `database=grantforge_demo`, `table=orders`, `column=*`; enmascara `card` con `mask_show_last_4`; filtra la tabla con `region = 'EU'`. Mantén activados el servicio y las tres políticas. Espera a que **Permisos sobre los datos → Agentes** muestre que se ha aplicado la última versión de las políticas.

Conéctate al proxy con el cliente MySQL de línea de comandos. `-p` solicita la contraseña; el archivo CA debe confiar en el certificado del proxy:

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- One row only: 1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000: GrantForge does not grant update
```

Los privilegios nativos de MySQL ya permiten `UPDATE` en este ejemplo, así que su rechazo verifica que el proxy aplica la política. Después, cambia el filtro de filas a `region = 'US'`, espera a que el proxy aplique la nueva versión y consulta de nuevo. Solo debe aparecer la fila 2, con la tarjeta `xxxxxxxxxxxx4444`.

## Solución de problemas y auditoría

| Código de error / SQLSTATE | Significado y comprobaciones |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | Acceso a base de datos, tabla o columna denegado. En mensajes que contengan `by GrantForge`, comprueba el usuario de la política, los recursos, el tipo de acceso y la versión de la instantánea; comprueba también los privilegios nativos de MySQL para otras denegaciones |
| `1046 / 3D000` | No hay base de datos seleccionada; usa `USE`, una base de datos predeterminada en la conexión o un nombre de tabla cualificado |
| `1227 / 42000` | Una operación está prohibida o no puede comprobarse de forma segura; usa el mensaje para revisar restricciones de sentencias y privilegios de metadatos |
| `1064 / 42000` | El SQL no puede analizarse |
| `1615 / HY000` | Prepara la sentencia de nuevo después de un cambio de políticas |
| `3159 / HY000` | El proxy requiere TLS, pero el cliente se ha conectado sin él |
| `1043 / 08S01` | Fallo de protocolo o negociación TLS con el servidor de destino; comprueba el modo TLS, los certificados, el nombre de host y las capacidades del servidor |
| `2003 / HY000` | El proxy no puede conectarse a MySQL; comprueba la dirección, la red y el estado del servidor de destino |

En **Permisos sobre los datos → Auditoría de accesos**, busca los eventos permitidos y denegados de `alice` y sus recursos. Una sentencia SQL puede producir varios eventos de autorización de recursos. Se registran antes de la ejecución en el servidor, por lo que `ALLOWED` no significa que MySQL ejecutara correctamente. Los errores de análisis, las sentencias sin acceso a recursos y los servicios desactivados pueden no producir eventos de recursos; las ejecuciones repetidas de una sentencia preparada con la misma versión de políticas no repiten eventos de autorización. Esta es una auditoría de autorización de recursos, no un registro completo de ejecución SQL.

## Guías relacionadas

- [MySQL](/es/plugins/mysql/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
