---
title: Agente de Apache Kafka
description: Instala el agente en los brokers y controladores de Kafka para autorizar cada solicitud con las políticas de GrantForge, mostrar las políticas como ACL de solo lectura y agrupar los accesos repetidos en un único evento de auditoría.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de Kafka es un autorizador de la API `Authorizer` de Kafka, indicado en `authorizer.class.name`: brokers y controladores le preguntan por cada solicitud, y responde a partir de las políticas firmadas del servicio de datos `kafka`. La distribución lo incluye como un único JAR compatible con Java 8, `agents/kafka/grantforge-agent-kafka-<version>.jar`, con sus dependencias reubicadas dentro.

Las pruebas de integración con Testcontainers del repositorio ejecutan el JAR dentro de Kafka `3.7.2`, `3.9.2`, `4.1.2` y `4.3.1`, en nodos KRaft que son broker y controlador a la vez. Esto no certifica todas las demás versiones. Consulta [Apache Kafka](/es/plugins/kafka/) para el plug-in de servicio.

## Funcionamiento

- **Se comprueba cada solicitud.** Para cada recurso que toca una solicitud, Kafka pregunta por una operación ACL; el agente responde a partir de las políticas, con los tipos de recurso de Kafka como niveles de recurso y sus operaciones como tipos de acceso. Una denegación llega al cliente como el error propio de Kafka, como `TOPIC_AUTHORIZATION_FAILED`.
- **Un productor idempotente solo necesita un topic.** Kafka deja arrancar a un productor sin `idempotent_write` en el clúster cuando puede escribir en algún topic, y se lo pregunta al autorizador. El agente responde a partir de las propias políticas: cuenta un `write` permitido en cualquier topic, y una denegación solo cuenta en una política que cubre todos los topics.
- **Las políticas se muestran como ACL.** `kafka-acls --list` y `describeAcls` de la API Admin muestran las políticas que se aplican ahora: un valor sin comodines como ACL literal, un valor como `orders*` como ACL con prefijo y `*` como el comodín de Kafka; los usuarios como principales `User:`, los grupos como `Group:`, los roles como `Role:` y el grupo `public` como `User:*`. Lo que las ACL no pueden expresar se omite: otros comodines, valores excluidos, elementos con condiciones y los elementos de una política que tiene excepciones a ellos. Crear o eliminar ACL se rechaza: *the ACLs of this cluster are GrantForge policies; change them in GrantForge*.
- **Se conservan las decisiones.** Un broker hace las mismas preguntas en cada producción y lectura; el agente conserva las decisiones de la instantánea actual, por defecto 100 000 durante 10 segundos. Una instantánea nueva empieza de cero, así que un cambio de política se aplica en cuanto el agente lo recibe.
- **Se agrupan los accesos repetidos.** Los accesos permitidos idénticos dentro de una ventana, de 10 segundos por defecto, se notifican como un único evento de acceso que indica cuántos hubo y cuándo ocurrió el último; la consola muestra *N accesos idénticos*. Los accesos son idénticos cuando comparten usuario, dirección del cliente, recurso, tipo de acceso, tipo de solicitud y política decisiva. Las denegaciones se notifican de una en una al instante. Kafka elige qué merece notificarse: no pide, por ejemplo, notificar los topics que omite de un listado.
- **Los superusuarios no se comprueban.** Los principales de `super.users` pueden hacerlo todo, como con el autorizador de Kafka, y no se auditan.

## Despliegue

1. Añade un servicio de datos `kafka` y configura sus políticas. Abre **Permisos sobre los datos → Agentes**, selecciona el servicio y emite un token de agente. Solo se muestra una vez; guárdalo en un archivo que solo pueda leer la cuenta de Kafka.
2. Copia el JAR en `libs` de **cada broker y controlador**, como `/opt/kafka/libs`.
3. Añade los ajustes al `server.properties` de cada uno, sustituyendo los principales, la dirección, las rutas y el nombre de instancia de ejemplo:

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` debe nombrar los principales que brokers y controladores usan entre sí, como el del listener entre brokers, o las políticas deben permitirles lo que hacen, como `cluster_action` en el clúster. Las conexiones a un listener `PLAINTEXT` son `User:ANONYMOUS`; nómbralo solo cuando ningún cliente pueda alcanzar un listener así. El directorio de caché debe tener permiso de escritura para la cuenta de Kafka.
4. Reinicia los brokers y controladores de uno en uno. Los listeners distintos de los de los controladores se abren cuando el agente tiene su primera instantánea, como mucho tras `grantforge.start.timeout.ms`; hasta entonces, y siempre que no haya instantánea, las solicitudes se deniegan.
5. **Migra primero las ACL existentes:** las ACL que guardó el autorizador de Kafka dejan de aplicarse cuando el agente lo sustituye. Lístalas antes con `kafka-acls --list` y crea las políticas equivalentes.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor GrantForge, el archivo del token del agente, un nombre para este broker o controlador y un directorio con escritura para la instantánea firmada y los eventos encolados |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copia la clave pública Ed25519 X.509 codificada en Base64 desde la página de agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura con GrantForge, `5000` / `8000` milisegundos por defecto |
| `grantforge.refresh.interval.ms` | Cada cuánto pide el agente nuevas políticas, `30000` por defecto, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (`500` por defecto), tamaño de la cola (`10000` por defecto), intervalo de envío (`5000` milisegundos por defecto) y límite de encolado (64 MiB por defecto) |
| `grantforge.audit.fold.window.ms` | Cuánto tiempo se agrupan los accesos permitidos idénticos en un evento, `10000` por defecto, hasta `3600000` milisegundos; `0` notifica cada uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Máximo de decisiones conservadas, `100000` por defecto, hasta `10000000`, `0` no conserva ninguna; y durante cuánto tiempo, `10000` por defecto, `1..3600000` milisegundos |
| `grantforge.start.timeout.ms` | Cuánto esperan los listeners la primera instantánea, `30000` por defecto, `0..600000` milisegundos |

El agente lee sus ajustes de la configuración que Kafka entrega a su autorizador. Un nodo que es broker y controlador a la vez crea un autorizador por rol; ambos comparten un mismo agente.

## Usuarios, grupos y roles

Las políticas nombran el principal que Kafka asigna a la conexión: el nombre de usuario SASL, el nombre corto de un principal Kerberos según `sasl.kerberos.principal.to.local.rules`, o el nombre de un certificado TLS según `ssl.principal.mapping.rules`. Kafka no tiene grupos ni roles; los de las políticas son los que GrantForge asigna al usuario.

## Decisiones y limitaciones

- Sin una instantánea utilizable, se deniega todo salvo las solicitudes de los superusuarios. Un servicio que no está en uso deja Kafka sin comprobar, como sin autorizador.
- Un token vinculado a un servicio cuyo tipo no es `kafka` lo deniega todo.
- Denegar un tipo de acceso deniega también lo que implica: denegar `write` en un topic deniega `describe`, así que el usuario no lo ve. Las ACL propias de Kafka difieren en esto.
- La pregunta de Kafka sobre si un usuario puede actuar sobre algún recurso de un tipo no es un acceso y no se notifica.
- Las decisiones se conservan durante `grantforge.decision.cache.ttl.ms`, así que el periodo de validez de una política empieza o termina con hasta ese retraso.
- El agente funciona en brokers y controladores. La API REST de Kafka Connect, los registros de esquemas y otros servicios en torno a Kafka no están cubiertos.

## Guías relacionadas

- [Apache Kafka](/es/plugins/kafka/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
