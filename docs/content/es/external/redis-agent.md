---
title: Agente de Redis
description: Ejecutar el agente de Redis junto a un despliegue de Redis o Valkey para mantener los selectores ACL de cada usuario en cada nodo en sintonía con las políticas de GrantForge, sustituidos en un comando, preguntar a Redis con ACL DRYRUN qué permiten los permisos ajenos a GrantForge, e informar de los rechazos desde los registros ACL de los nodos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de Redis es un servicio junto al despliegue. Mantiene la ACL propia de Redis en sintonía con las políticas firmadas de un servicio de datos `redis` en cada nodo, también en las réplicas y en cada nodo de un clúster, ya que Redis mantiene una ACL en cada uno. Después, Redis comprueba cada comando por sí mismo, y el agente no se interpone ante ningún cliente. La distribución lo incluye como JAR ejecutable `agents/redis/grantforge-agent-redis-<version>.jar`; necesita Java 17 o posterior.

Las pruebas de integración con Testcontainers del repositorio ejecutan el agente, y el JAR tal como se distribuye, contra un primario con una réplica de Redis `7.2`, `7.4`, `8.2` y `8.10` y Valkey `8.1` y `9.1`, y un clúster de Redis `8.10`. Esto no certifica todas las demás versiones; el agente rechaza Redis anterior a `7.2`, cuyo registro ACL no numera las entradas. Para el plug-in de servicio, consulte [Redis](/es/plugins/redis/).

## Funcionamiento

- **Los selectores son de GrantForge, los permisos raíz del operador.** Redis permite un comando cuando lo permiten los permisos raíz de un usuario o uno de sus selectores. El agente mantiene todos los selectores de los usuarios que atiende y nunca toca sus permisos raíz ni sus contraseñas: un selector contiene las claves que el usuario puede leer, escribir o ambas cosas, uno los canales a los que puede suscribirse, uno aquellos en los que puede publicar, y en los nodos que puede supervisar, uno más.
- **Un cambio ocurre en un solo comando.** `ACL SETUSER user clearselectors (…)` sustituye a la vez todos los selectores de un usuario en un nodo, de modo que un usuario nunca tiene los antiguos y los nuevos, ni ninguno; los selectores cambiados a mano se corrigen igual en la siguiente sincronización.
- **Patrones, concedidos solo donde se cumplen.** Un patrón de claves o canales de una política se concede como patrón propio de Redis donde las políticas deciden al usuario igual en todo él; el agente corta el patrón a lo largo de cada política que se solapa con él y decide cada parte, de modo que la política de otro usuario no lo retiene. Un patrón en el que las políticas deciden de otro modo se retiene y se registra una vez.
- **Nada peligroso, nada de listar claves ajenas.** Los selectores nunca dan lo que Redis considera peligroso, y dan `SCAN` y `RANDOMKEY` solo a un usuario que puede leer todas las claves.
- **Cada nodo, guardado donde se puede.** El agente encuentra las réplicas y los nodos del clúster a partir de los nodos indicados y sincroniza cada uno. Un nodo que guarda su ACL en un archivo la guarda con `ACL SAVE`; uno que no lo hace pierde los selectores al reiniciarse y los recupera en la siguiente sincronización.
- **Se pregunta a Redis qué permite.** Los permisos raíz deciden junto a los selectores del agente. En lugar de leer sus reglas, el agente pregunta a Redis con `ACL DRYRUN` si un usuario puede ejecutar un comando sobre una clave, canal o nodo que una política le deniega, y avisa una vez de cada caso, nombrando al usuario, el acceso y la política. También avisa de los usuarios que ningún selector limita, con todos los comandos sobre todas las claves, y de los usuarios como los que cualquiera puede iniciar sesión sin contraseña.
- **Los rechazos vienen del registro ACL de cada nodo.** Consulte Informar de los rechazos más abajo.
- **Las políticas del momento presente.** Cada sincronización planifica según las políticas tal como se aplican en ese momento, de modo que los periodos de validez empiezan y terminan a tiempo, y según los usuarios y nodos que tiene Redis.

## Despliegue

1. Añada un servicio de datos `redis` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, seleccione el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un fichero que solo pueda leer la cuenta del agente.
2. Cree el usuario ACL del agente en cada nodo. Lee usuarios, fija sus selectores, guarda el archivo ACL, lee el registro ACL, hace pruebas en seco y encuentra los nodos del clúster. `+acl|setuser` puede darse cualquier cosa, así que proteja este usuario como a un administrador.

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. Limite los permisos raíz de los usuarios que atiende el agente a lo que necesita cualquier usuario, como iniciar sesión; el resto lo da el agente. Los permisos raíz que dan más se detectan y se avisan.

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. Escriba el fichero de ajustes sustituyendo las direcciones, rutas y nombres de ejemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   Estas rutas están en la máquina del agente, y el directorio de caché debe poder escribirse. Los ficheros de contraseña se leen como UTF-8 y se recortan.
5. Inicie el agente, por ejemplo como servicio de systemd:

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   Sustituya `<version>` por la versión de la distribución. Los ajustes indicados como `java -Dgrantforge.…=… -jar …` sobrescriben el fichero. El agente se detiene limpiamente con SIGTERM y envía o guarda los eventos pendientes; Redis conserva lo que se le dio.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorio: el servidor de GrantForge, el fichero del token del agente, un nombre para este agente y un directorio escribible para la instantánea firmada, los eventos en cola y la posición de lectura del registro de auditoría |
| `grantforge.signing.key.file` | Fichero opcional que fija la clave de firma de las instantáneas; copie la clave pública Ed25519 X.509 codificada en Base64 de la página de agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.redis.nodes` | Obligatorio: los nodos como `host:port`, el puerto `6379` si no se indica |
| `grantforge.redis.discover` | `true` de forma predeterminada: el resto de un clúster y las réplicas de un primario se encuentran a partir de los nodos indicados. `false` toma los nodos indicados y ningún otro, cuando las direcciones que anuncian los nodos no son alcanzables desde el agente |
| `grantforge.redis.user`, `grantforge.redis.password.file` | El usuario ACL del agente, vacío para el usuario predeterminado, y su contraseña |
| `grantforge.redis.tls.mode`, `grantforge.redis.tls.ca.file`, `grantforge.redis.tls.certificate.file`, `grantforge.redis.tls.key.file`, `grantforge.redis.tls.key.password.file` | `disable` (predeterminado), `verify-ca` o `verify-full`; los certificados CA PEM que verifican los servidores, vacío para lo que Java confía; un certificado de cliente PEM, su clave si está en un archivo propio, y la contraseña de la clave |
| `grantforge.redis.timeout.ms` | Cuánto pueden tardar la conexión y un comando; `10000` de forma predeterminada, en el rango `1..600000` milisegundos |
| `grantforge.redis.unmanaged.users` | Usuarios que el agente deja en paz, separados por comas, como cuentas de servicio. Siempre deja en paz a su propio usuario y a los usuarios con todos los comandos sobre todas las claves |
| `grantforge.sync.interval.ms` | Con qué frecuencia el agente pone Redis de acuerdo, por defecto `10000`, rango `1000..3600000` milisegundos |
| `grantforge.audit.log`, `grantforge.audit.log.interval.ms` | `true` de forma predeterminada; `false` no lee ningún registro ACL. Con qué frecuencia se leen los registros ACL, `5000` milisegundos de forma predeterminada |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura hacia GrantForge, por defecto `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide políticas nuevas, por defecto `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (por defecto `500`), tamaño de cola (por defecto `10000`), intervalo de envío (por defecto `5000` milisegundos) y el límite de la cola en disco (por defecto 64 MiB) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo se agrupan en un evento los accesos permitidos idénticos, por defecto `10000`, hasta `3600000` milisegundos; `0` envía cada uno |

## Informar de los rechazos

Redis no guarda registro de lo que permitió, así que el agente informa de lo que rechazó, desde el registro ACL de cada nodo: a quién se le rechazó qué, desde dónde y cuándo, y si las políticas deciden como Redis. Redis agrupa un rechazo repetido en menos de un minuto con la entrada anterior; el agente informa de nuevo de una entrada cada vez que se actualiza.

| Motivo registrado | Evento de acceso |
| --- | --- |
| `key` | `read` o `write` sobre la clave, según marque Redis el comando |
| `channel` | `subscribe` o `publish` sobre el canal |
| `command` | `monitor` sobre el nodo para los comandos de supervisión; los demás comandos se registran sin sus claves y se omiten |
| `auth` | Omitido: un inicio de sesión rechazado no nombra ningún acceso |

## Usuarios, grupos y roles

Las políticas nombran a los usuarios ACL de Redis por su nombre. Redis no tiene roles ni grupos propios, así que los grupos son aquellos en los que GrantForge coloca a los usuarios.

## Decisiones y limitaciones

- Sin una instantánea el agente no cambia nada. Un token vinculado a un servicio sin uso, o a un servicio cuyo tipo no es `redis`, no concede nada: el agente vacía los selectores de los usuarios que atiende.
- Mientras el agente no se ejecuta, Redis conserva lo último que recibió y los cambios de políticas esperan. Un cambio llega a Redis dentro de los intervalos de actualización y sincronización.
- El agente mantiene todos los selectores de los usuarios que atiende: un selector que el operador añade a mano se elimina en la siguiente sincronización. Dé los permisos adicionales en los permisos raíz, o deje el usuario al operador.
- La ACL de Redis nombra claves y canales, no bases de datos, así que un patrón cubre sus claves en todas las bases de datos de un nodo.
- Un comando rechazado que el usuario no puede ejecutar en absoluto se registra en Redis sin sus claves y no se informa.
- Redis no filtra nada dentro de un valor ni enmascara nada.

## Guías relacionadas

- [Redis](/es/plugins/redis/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
