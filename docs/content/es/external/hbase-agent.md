---
title: Agente de Apache HBase
description: Instala el coprocesador en los masters y region servers de HBase para comprobar operaciones, lecturas y escrituras con las políticas de GrantForge, devolver solo las columnas que un usuario puede leer y ocultar lo que no puede usar.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de HBase es un coprocesador de masters, regiones y region servers: HBase le pregunta por cada operación, lectura y escritura, y responde a partir de las políticas firmadas del servicio de datos `hbase`. La distribución lo incluye como un único JAR compatible con Java 8, `agents/hbase/grantforge-agent-hbase-<version>.jar`, con sus dependencias reubicadas dentro.

Las pruebas de integración con Testcontainers del repositorio ejecutan el JAR dentro de HBase `2.5.16`, `2.6.7` y `3.0.0`. Esto no certifica todas las demás versiones. Consulta [Apache HBase](/es/plugins/hbase/) para el plug-in de servicio.

## Funcionamiento

- **Cada solicitud se comprueba en cada ámbito que nombra.** En HBase un permiso cubre todo lo que hay en su ámbito, así que el agente decide una solicitud en su espacio de nombres, su tabla, su familia de columnas y su columna: una denegación en cualquiera de ellos la rechaza, una autorización en cualquiera la concede.
- **Los propietarios de un espacio de nombres tienen sus tablas.** `create` y `admin` sobre un espacio de nombres permiten crearlo, crear tablas en él y administrarlas, sin una política por tabla.
- **Las lecturas devuelven lo que se puede leer.** Cuando un usuario solo puede leer una parte de una tabla, como una familia de columnas, un get o un scan devuelve esas columnas en lugar de fallar; un usuario que no puede leer nada de la tabla es rechazado.
- **Los listados omiten lo que no se puede usar.** Listar espacios de nombres y tablas muestra solo los que el usuario puede usar, o en los que puede usar algo, y listar instantáneas solo las de las tablas que administra; describir un espacio de nombres que no puede usar se rechaza.
- **Los rechazos dicen por qué.** El cliente recibe `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)`, o la política que lo denegó.
- **Se conservan las decisiones y se agrupan los accesos.** Las decisiones de la instantánea actual se conservan, por defecto 100 000 durante 10 segundos, y una instantánea nueva empieza de cero. Los accesos permitidos idénticos dentro de una ventana, de 10 segundos por defecto, se notifican como un único evento de acceso que indica cuántos hubo; los rechazos se notifican de uno en uno al instante.
- **Los servidores no se comprueban.** Los superusuarios de HBase, nombrados en `hbase.superuser`, y el trabajo propio de los servidores no se comprueban ni se auditan. Las tablas del espacio de nombres `hbase`, como `hbase:meta`, las puede leer cualquiera, como necesitan los clientes.

| Operación | Acceso necesario |
| --- | --- |
| Get, scan | `read` sobre la tabla, o sobre las columnas devueltas |
| Put, increment, append, delete de columnas | `write` sobre cada columna escrita |
| Delete de una familia de columnas o de una fila | `write` sobre cada familia de columnas, ya que HBase borra una fila familia por familia |
| Check-and-mutate | `read` sobre la columna comparada, o sobre la tabla con un filtro, y `write` sobre lo escrito |
| Crear tabla | `create` sobre el espacio de nombres |
| Borrar, truncar, modificar, habilitar, deshabilitar, flush, split, merge, compactar | `admin` o `create` sobre la tabla |
| Mover, asignar, desasignar, poner fuera de línea | `admin` sobre la tabla |
| Crear, restaurar, borrar o listar una instantánea | `admin` sobre la tabla; clonar también requiere `create` sobre el espacio de nombres de destino |
| Carga masiva | `create` sobre la tabla |
| Crear, modificar, borrar un espacio de nombres | `admin` sobre el espacio de nombres |
| Describir un espacio de nombres; listar espacios de nombres y tablas | Cualquier acceso a él o dentro de él |
| Balanceador, apagado, cuotas, pares de replicación, grupos de region servers, procedimientos, retirada, parada de region servers, rotación de WAL | `admin` sobre `global` |

## Despliegue

1. Añade un servicio de datos `hbase` y configura sus políticas. Abre **Permisos sobre los datos → Agentes**, selecciona el servicio y emite un token de agente. Solo se muestra una vez; guárdalo en un archivo que solo pueda leer la cuenta de HBase.
2. Copia el JAR en `lib` de **cada master y region server**, como `/opt/hbase/lib`.
3. Añade los ajustes al `hbase-site.xml` de cada uno, sustituyendo la dirección, las rutas y el nombre de instancia de ejemplo:

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   El directorio de caché debe tener permiso de escritura para la cuenta de HBase. Sustituye el `AccessController` propio de HBase en las listas de coprocesadores en lugar de cargar ambos.
4. Reinicia los masters y region servers de uno en uno. Hasta que el agente tiene su primera instantánea, se deniegan las solicitudes que no sean de superusuarios.
5. **Migra primero los permisos existentes:** los permisos que el `AccessController` de HBase guardó en `hbase:acl` dejan de aplicarse. Lístalos antes con `user_permission` y crea las políticas equivalentes.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor GrantForge, el archivo del token del agente, un nombre para este master o region server y un directorio con escritura para la instantánea firmada y los eventos encolados |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copia la clave pública Ed25519 X.509 codificada en Base64 desde la página de agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura con GrantForge, `5000` / `8000` milisegundos por defecto |
| `grantforge.refresh.interval.ms` | Cada cuánto pide el agente nuevas políticas, `30000` por defecto, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (`500` por defecto), tamaño de la cola (`10000` por defecto), intervalo de envío (`5000` milisegundos por defecto) y límite de encolado (64 MiB por defecto) |
| `grantforge.audit.fold.window.ms` | Cuánto tiempo se agrupan los accesos permitidos idénticos en un evento, `10000` por defecto, hasta `3600000` milisegundos; `0` notifica cada uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Máximo de decisiones conservadas, `100000` por defecto, hasta `10000000`, `0` no conserva ninguna; y durante cuánto tiempo, `10000` por defecto, `1..3600000` milisegundos |

HBase arranca un coprocesador para el master, para el region server y para cada región que abre; todos comparten un mismo agente, una caché de decisiones y la agrupación de eventos.

## Usuarios, grupos y roles

Las políticas nombran el nombre corto que HBase da al usuario de la conexión: el nombre de usuario de la autenticación simple, o el nombre corto de un principal Kerberos según `hadoop.security.auth_to_local`. Los grupos son los que el mapeo de grupos de Hadoop asigna al usuario en el servidor, junto con los grupos en los que GrantForge lo pone; los roles son los que GrantForge le asigna.

## Decisiones y limitaciones

- Sin una instantánea utilizable, se deniega todo salvo las solicitudes de los superusuarios y las lecturas del espacio de nombres `hbase`. Un servicio que no está en uso deja HBase sin comprobar, como sin autorizador.
- Un token vinculado a un servicio cuyo tipo no es `hbase` lo deniega todo.
- Los comandos `grant`, `revoke` y `user_permission` de HBase fallan con *AccessController is not loaded*: los permisos son políticas de GrantForge.
- Las llamadas a endpoints de coprocesadores no se comprueban; carga solo endpoints de confianza.
- Aquí HBase no da al creador de una tabla ningún permiso propio; una política de espacio de nombres, como la anterior, da a sus propietarios sus tablas.
- HBase registra algunos rechazos en lugar de devolverlos, como el del interruptor del balanceador: el interruptor queda como estaba, pero no se avisa al cliente.
- Las decisiones se conservan durante `grantforge.decision.cache.ttl.ms`, así que el periodo de validez de una política empieza o termina con hasta ese retraso.
- Los permisos a nivel de celda y las etiquetas de visibilidad de HBase no están cubiertos. Las columnas se comparan como texto UTF-8.

## Guías relacionadas

- [Apache HBase](/es/plugins/hbase/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
