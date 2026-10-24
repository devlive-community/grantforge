---
title: Agente de Apache Pulsar
description: Instalar el proveedor de autorización en los brokers de Pulsar para decidir con políticas de GrantForge quién puede producir y consumir dónde, mediante qué suscripciones, ejecutar functions, sources y sinks, administrar topics, namespaces, tenants y clústeres, y desde qué redes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de Pulsar es un proveedor de autorización de los brokers: Pulsar le pregunta quién puede producir, consumir, ejecutar functions, sources y sinks, y administrar topics, namespaces, tenants y clústeres, y responde a partir de las políticas firmadas del servicio de datos `pulsar`. La distribución lo incluye como un JAR de Java 17, `agents/pulsar/grantforge-agent-pulsar-<version>.jar`, con las dependencias incluidas y reubicadas.

Las pruebas de integración con Testcontainers del repositorio ejecutan el JAR en los brokers de Pulsar `3.0.17`, `3.3.9`, `4.0.14` y `4.2.5`. Esto no certifica todas las demás versiones. El plug-in de servicio se describe en [Apache Pulsar](/es/plugins/pulsar/).

## Funcionamiento

- **Cada comprobación se decide en cada nivel del nombre del topic.** Como con los permisos de Pulsar, un permiso sobre un tenant o un namespace cubre lo que contiene, así que el agente decide una producción en `persistent://sales/orders/payments` en el tenant `sales`, el namespace `orders` y el topic `payments`: una denegación en cualquiera rechaza, un permiso en cualquiera concede.
- **El consumo también se decide por la suscripción.** En cada uno de esos niveles el agente pregunta también por el topic con la suscripción, de modo que una política puede dejar a un rol consumir solo mediante algunas suscripciones, por ejemplo `billing-*`.
- **Los administradores del clúster son los superusuarios de Pulsar.** `admin` en el clúster del broker convierte a un rol en uno de ellos, y Pulsar deja a sus superusuarios hacerlo todo, así que las políticas de debajo no los restringen. Las operaciones de broker y de clúster de Pulsar necesitan `admin` en el clúster que nombran.
- **Las políticas pueden aplicarse solo a algunas redes.** Cada comprobación lleva la dirección del cliente, y el agente evalúa la condición **Direcciones de cliente** con los rangos de direcciones de Netty, que usa la pila de red del broker.
- **Conceder permisos mediante Pulsar se rechaza.** Los permisos propios de Pulsar y los roles de administración de sus tenants no se usan, así que `pulsar-admin namespaces grant-permission` y similares reciben HTTP 405, con la indicación de conceder en GrantForge.
- **Los rechazos se registran con su motivo.** Pulsar redacta el rechazo que recibe el cliente; el registro del broker nombra la política de GrantForge que denegó, o que ninguna lo permite.
- **Las decisiones se conservan y los accesos se agrupan.** Se conservan las decisiones de la instantánea actual, de forma predeterminada 100.000 durante 10 segundos, y una nueva instantánea empieza de cero. Los accesos permitidos idénticos dentro de una ventana, de forma predeterminada 10 segundos, se notifican como un único evento de acceso que indica cuántos fueron; los rechazos se notifican uno a uno y de inmediato. Cada evento nombra el topic, el namespace, el tenant o el clúster y la dirección del cliente.
- **Un agente sirve a todo el broker.** Un broker carga su proveedor de autorización para su protocolo binario, su servicio web y su functions worker; comparten un agente, que se detiene con el broker.

| Operación | Acceso necesario |
| --- | --- |
| Producir, o crear un topic produciendo en él | `produce` en el topic |
| Suscribirse, consumir, cancelar la suscripción, saltar, inspeccionar, hacer caducar mensajes, reiniciar un cursor | `consume` en el topic con la suscripción |
| Buscar un topic, leer sus estadísticas, metadatos y políticas; listar los topics de un namespace | `produce` o `consume` |
| Terminar, compactar, descargar, liberar o truncar un topic; cambiar sus políticas | `admin_topic` en el topic |
| Crear y eliminar topics; cambiar las políticas de un namespace | `admin_namespace` en el namespace |
| Ejecutar functions, sources, sinks; usar packages | `functions`, `sources`, `sinks`, `packages` en el namespace |
| Crear, listar y eliminar los namespaces de un tenant | `admin_tenant` en el tenant |
| Crear, cambiar y eliminar tenants y clústeres; las operaciones de broker de Pulsar | `admin` en el clúster |

## Despliegue

1. Añada un servicio de datos `pulsar` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, elija el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un archivo que solo pueda leer la cuenta de Pulsar.
2. Copie el JAR en `lib` **de cada broker**, por ejemplo `/pulsar/lib`.
3. Añada los ajustes al `broker.conf` de cada broker, o al `standalone.conf`, sustituyendo la dirección, las rutas y el nombre de instancia del ejemplo:

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   La autenticación también debe estar habilitada, para que los clientes tengan roles. `superUserRoles` y `proxyRoles` deben nombrar solo el rol propio de los brokers y los de los proxys: el agente no los comprueba, y Pulsar también comprueba el cliente original de un proxy. Dé a los administradores `admin` en el clúster en su lugar. La cuenta de Pulsar debe poder escribir en el directorio de caché. En un contenedor, escriba los ajustes `grantforge.*` en el archivo: el shell de algunas imágenes de Pulsar descarta las variables de entorno cuyo nombre lleva puntos, como `PULSAR_PREFIX_grantforge.token.file`.
4. Reinicie los brokers uno tras otro. Hasta que el agente tenga su primera instantánea, se rechazan las peticiones que no sean de los brokers ni de los proxys.
5. **Traslade antes los permisos existentes:** los permisos propios de Pulsar sobre namespaces, topics y suscripciones, y los roles de administración de los tenants, dejan de aplicarse. Cree antes las políticas equivalentes.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor de GrantForge, el archivo del token de agente, un nombre para este broker y un directorio escribible para la instantánea firmada y los eventos en cola |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copie la clave pública X.509 Ed25519 codificada en Base64 desde la página Agentes. Sin él, la clave se obtiene del servidor en el primer contacto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura hacia GrantForge, predeterminados `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia pide el agente nuevas políticas, predeterminado `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (predeterminado `500`), tamaño de cola (predeterminado `10000`), intervalo de envío (predeterminado `5000` milisegundos) y límite del spool (predeterminado 64 MiB) |
| `grantforge.audit.fold.window.ms` | Cuánto tiempo se agrupan accesos permitidos idénticos en un evento, predeterminado `10000`, hasta `3600000` milisegundos; `0` notifica cada uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Máximo de decisiones conservadas, predeterminado `100000`, hasta `10000000`, `0` no conserva ninguna; y durante cuánto, predeterminado `10000`, `1..3600000` milisegundos |

## Usuarios, grupos y roles

Las políticas nombran como usuario el rol que la autenticación de Pulsar da al cliente, por ejemplo el sujeto de un token o el nombre común de un certificado. Pulsar no tiene grupos propios: los grupos son los que GrantForge asigna al rol, y los roles los que GrantForge le da.

## Decisiones y limitaciones

- Sin una instantánea utilizable se rechaza todo salvo las peticiones de los brokers y los proxys. Un servicio que no se usa deja Pulsar sin comprobar, como con `authorizationEnabled` desactivado.
- Un token vinculado a un servicio cuyo tipo no es `pulsar` lo rechaza todo.
- Antes de muchas comprobaciones, Pulsar pregunta si un rol es superusuario o administrador de un tenant; solo se notifican las respuestas que dejan pasar una petición, y la comprobación propia de la petición se notifica siempre. Una petición que Pulsar decide solo con esa pregunta, como hace Pulsar 3.0 con tenants y clústeres, se registra únicamente en nivel debug cuando se rechaza.
- `admin` en un clúster convierte a un rol en superusuario solo de los brokers de ese clúster.
- Los listados de permisos de Pulsar, como `pulsar-admin namespaces permissions`, fallan: GrantForge guarda las políticas.
- Las direcciones de cliente son solo direcciones y rangos; un nombre de host no coincide con ningún cliente.
- Las decisiones se conservan durante `grantforge.decision.cache.ttl.ms`, así que una política empieza o deja de aplicarse hasta ese tiempo después.

## Guías relacionadas

- [Apache Pulsar](/es/plugins/pulsar/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
