---
title: Agente de Apache Hadoop YARN
description: Instalar el proveedor de autorización en los ResourceManagers de YARN para decidir con políticas de GrantForge quién puede enviar qué aplicaciones a qué cola, administrar colas y el clúster, y desde qué redes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de YARN es un proveedor de autorización del ResourceManager: YARN le pregunta quién puede enviar a una cola, administrar una cola y administrar el clúster, y responde a partir de las políticas firmadas del servicio de datos `yarn`. La distribución lo incluye como un JAR compatible con Java 8, `agents/yarn/grantforge-agent-yarn-<version>.jar`, con las dependencias incluidas y reubicadas.

Las pruebas de integración con Testcontainers del repositorio ejecutan el JAR en los ResourceManagers de Hadoop `3.2.4`, `3.3.6`, `3.4.3` y `3.5.0`. Esto no certifica todas las demás versiones. El plug-in de servicio se describe en [Apache Hadoop YARN](/es/plugins/yarn/).

## Funcionamiento

- **Cada comprobación se decide en cada cola de la ruta.** Como en YARN, un permiso sobre una cola cubre las de debajo, así que el agente decide un envío a `root.sales.etl` en `root`, `root.sales` y `root.sales.etl`: una denegación en cualquiera rechaza, un permiso en cualquiera concede.
- **Los envíos también se deciden por el nombre de la aplicación.** En cada una de esas colas, el agente pregunta también por la cola con el nombre de la aplicación, de modo que una política puede aceptar solo algunas aplicaciones, por ejemplo `payroll-*`, en una cola y las de debajo.
- **Las políticas pueden aplicarse solo a algunas redes.** Cada comprobación lleva la dirección del cliente, detrás de un proxy la primera reenviada, y el agente evalúa la condición **Direcciones de cliente** con las listas de hosts de Hadoop.
- **La administración del clúster está sujeta a políticas.** Las operaciones de administración de YARN, que de otro modo concede `yarn.admin.acl`, necesitan `admin` en el clúster.
- **Los rechazos se registran con su motivo.** YARN redacta el rechazo que recibe el cliente; el registro del ResourceManager nombra la política de GrantForge que denegó, o que ninguna lo permite.
- **Las decisiones se conservan y los accesos se agrupan.** Se conservan las decisiones de la instantánea actual, de forma predeterminada 100.000 durante 10 segundos, y una nueva instantánea empieza de cero. Los accesos permitidos idénticos dentro de una ventana, de forma predeterminada 10 segundos, se notifican como un único evento de acceso que indica cuántos fueron; los rechazos se notifican uno a uno y de inmediato. Cada evento nombra la aplicación, la cola y la dirección del cliente.
- **El ResourceManager no se comprueba.** El usuario propio del ResourceManager, por ejemplo `yarn`, no se comprueba ni se audita.

| Operación | Acceso necesario |
| --- | --- |
| Enviar una aplicación | `submit_app` en su cola, o en su cola con el nombre de la aplicación; `admin_queue` en la cola también sirve |
| Detener, mover, cambiar la prioridad o ver todos los detalles de la aplicación de otro usuario | `admin_queue` en su cola, y para mover también `submit_app` en la nueva; el propietario de la aplicación siempre puede, igual que quienes permiten sus propias ACL |
| `yarn rmadmin`: recargar colas, nodos, grupos de usuarios y ACL, cambiar etiquetas y atributos de nodo, y el resto de operaciones de administración | `admin` en el clúster |

## Despliegue

1. Añada un servicio de datos `yarn` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, elija el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un archivo que solo pueda leer la cuenta de YARN.
2. Copie el JAR en `share/hadoop/yarn/lib` **de cada ResourceManager**, por ejemplo `/opt/hadoop/share/hadoop/yarn/lib`.
3. Añada los ajustes al `yarn-site.xml` de cada ResourceManager, sustituyendo la dirección, las rutas, el usuario y el nombre de instancia del ejemplo:

   ```xml
   <property><name>yarn.acl.enable</name><value>true</value></property>
   <property><name>yarn.authorization-provider</name>
     <value>org.devlive.grantforge.yarn.agent.GrantForgeYarnAuthorizer</value></property>
   <property><name>yarn.admin.acl</name><value>yarn</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hadoop/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>resourcemanager-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hadoop-yarn/grantforge-cache</value></property>
   ```

   `yarn.admin.acl` debe nombrar solo al usuario propio del ResourceManager: YARN deja a sus administradores hacer cualquier cosa con cualquier aplicación antes de preguntar al agente por la cola. La cuenta de YARN debe poder escribir en el directorio de caché.
4. Reinicie los ResourceManagers uno tras otro. Hasta que el agente tenga su primera instantánea, se rechazan las peticiones que no sean del ResourceManager.
5. **Traslade antes los permisos existentes:** las ACL de cola de la configuración del planificador, por ejemplo `acl_submit_applications` y `acl_administer_queue`, dejan de aplicarse. Cree antes las políticas equivalentes.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor de GrantForge, el archivo del token de agente, un nombre para este ResourceManager y un directorio escribible para la instantánea firmada y los eventos en cola |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copie la clave pública X.509 Ed25519 codificada en Base64 desde la página Agentes. Sin él, la clave se obtiene del servidor en el primer contacto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura hacia GrantForge, predeterminados `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia pide el agente nuevas políticas, predeterminado `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (predeterminado `500`), tamaño de cola (predeterminado `10000`), intervalo de envío (predeterminado `5000` milisegundos) y límite del spool (predeterminado 64 MiB) |
| `grantforge.audit.fold.window.ms` | Cuánto tiempo se agrupan accesos permitidos idénticos en un evento, predeterminado `10000`, hasta `3600000` milisegundos; `0` notifica cada uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Máximo de decisiones conservadas, predeterminado `100000`, hasta `10000000`, `0` no conserva ninguna; y durante cuánto, predeterminado `10000`, `1..3600000` milisegundos |

## Usuarios, grupos y roles

Las políticas nombran el nombre corto que YARN da al llamante: el nombre de usuario de la autenticación simple, o el nombre corto de un principal de Kerberos según `hadoop.security.auth_to_local`. Los grupos son los que el mapeo de grupos de Hadoop da al usuario en el ResourceManager, junto con los grupos en los que GrantForge coloca al usuario; los roles son los que GrantForge le asigna.

## Decisiones y limitaciones

- Sin una instantánea utilizable se rechaza todo salvo las peticiones propias del ResourceManager. Un servicio que no se usa deja YARN sin comprobar, como con `yarn.acl.enable` desactivado.
- Un token vinculado a un servicio cuyo tipo no es `yarn` lo rechaza todo.
- YARN pregunta por su administración sin la dirección del cliente, así que una condición Direcciones de cliente nunca se cumple en `cluster`.
- El propietario de una aplicación y los usuarios que nombran sus propias ACL la gestionan con independencia de las políticas, como en YARN.
- Se rechazan las comprobaciones de otros tipos de acceso, por ejemplo las ACL de prioridad de aplicación.
- Un nombre de host en una condición Direcciones de cliente cuesta una resolución de nombre del cliente cada vez que una decisión se toma en lugar de conservarse.
- Las decisiones se conservan durante `grantforge.decision.cache.ttl.ms`, así que una política empieza o deja de aplicarse hasta ese tiempo después.

## Guías relacionadas

- [Apache Hadoop YARN](/es/plugins/yarn/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
