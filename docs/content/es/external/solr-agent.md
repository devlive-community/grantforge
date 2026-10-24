---
title: Agente de Apache Solr
description: Instalar el plug-in de autorización en los nodos de Solr para decidir con políticas de GrantForge quién puede consultar y actualizar qué colecciones, por qué rutas de solicitud, administrar qué colecciones y el clúster, y desde qué redes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de Solr es un plug-in de autorización de los nodos: Solr le pregunta por cada solicitud, y responde a partir de las políticas firmadas del servicio de datos `solr`. La distribución lo incluye como un JAR de Java 11, `agents/solr/grantforge-agent-solr-<version>.jar`, con sus dependencias integradas y reubicadas.

Las pruebas de integración con Testcontainers del repositorio ejecutan el JAR dentro de nodos SolrCloud de Solr `9.4.1`, `9.8.1`, `9.11.0` y `10.0.0`. Esto no certifica todas las demás versiones. El plug-in de servicio se describe en [Apache Solr](/es/plugins/solr/).

## Funcionamiento

- **Las solicitudes se clasifican como las clasifica Solr.** Cada manejador de solicitudes nombra el permiso que necesita, como `read` o `collection-admin-edit`, igual que para el plug-in basado en reglas de Solr; el agente convierte ese nombre en un tipo de acceso, y un manejador que no nombra ninguno se decide por el tipo de la solicitud.
- **Cada solicitud a una colección se decide también en su ruta de solicitud.** El agente decide una consulta de `sales` a través de `/select` en la colección `sales` y en `sales` con la ruta `/select`: una denegación en cualquiera de las dos la rechaza, de modo que una política puede denegar un manejador de una colección.
- **Los equipos administran sus propias colecciones.** Una solicitud de la API Collections que nombra colecciones necesita `admin_collection` en cada una, de modo que una política puede dejar que un equipo cree, recargue o elimine solo las colecciones cuyo nombre empieza por su prefijo.
- **Los administradores del clúster pueden hacerlo todo.** `admin` en el clúster concede cualquier solicitud, como el permiso `all` de Solr.
- **Las políticas pueden aplicarse solo a algunas redes.** Cada comprobación lleva la dirección del cliente; detrás de los proxies inversos en los que confía el agente, el cliente es la última dirección de `X-Forwarded-For` que no añadió ningún proxy de confianza.
- **Los roles verificados de un token web JSON cuentan como grupos.** Con la autenticación JWT de Solr, los roles que la reclamación del token da al usuario son grupos de la decisión, junto a aquellos en los que GrantForge coloca al usuario.
- **Las denegaciones se registran con su motivo.** Solr responde a una solicitud denegada con su propio 403; el registro del nodo nombra la política de GrantForge que la denegó, o indica que ninguna la permite.
- **Las decisiones se conservan y los accesos se agrupan.** Las decisiones de la instantánea actual se conservan, por defecto 100 000 durante 10 segundos, y una instantánea nueva empieza de cero. Los accesos permitidos idénticos dentro de una ventana, por defecto 10 segundos, se notifican como un único evento de acceso que indica cuántos hubo; las denegaciones se notifican una a una, de inmediato. Cada evento nombra la colección y la ruta, o el clúster, y la dirección del cliente.
- **Los nodos no se comprueban.** Las solicitudes entre los nodos, que Solr firma como el propio nodo, pasan; a una solicitud sin usuario se le pide que inicie sesión.

| Solicitud | Acceso necesario |
| --- | --- |
| Buscar, obtener, exportar, consultar mediante cualquier manejador de lectura | `query` en la colección y la ruta |
| Añadir, actualizar o eliminar documentos | `update` en la colección y la ruta |
| Leer o cambiar el esquema de una colección | `schema_read` o `schema_edit` |
| Leer o cambiar la config de una colección | `config_read` o `config_edit` |
| Solicitudes de la API Collections que nombran colecciones: crear, recargar, respaldar, restaurar, eliminar, modificar, su estado en el clúster | `admin_collection` en cada colección nombrada |
| Comprobaciones de salud; métricas | `health`; `metrics` en el clúster |
| Listar colecciones, estado de cores, lecturas de ZooKeeper, del file store y de paquetes | `admin_read` en el clúster |
| Leer o cambiar la autenticación y la autorización | `security` en el clúster |
| Administración de cores, cambios en el file store y los paquetes, y lo que ningún manejador nombra | `admin` en el clúster |

## Despliegue

1. Añada un servicio de datos `solr` y configure sus políticas, incluido `admin` en el clúster para los administradores de Solr. Abra **Permisos sobre los datos → Agentes**, elija el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un archivo que solo pueda leer la cuenta de Solr.
2. Copie el JAR en `server/solr-webapp/webapp/WEB-INF/lib` de **cada nodo**, como `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`, y reinicie los nodos de uno en uno.
3. Defina la sección de autorización del `security.json` del clúster, junto a un plug-in de autenticación, sustituyendo la dirección, las rutas y el nombre de instancia del ejemplo:

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   Súbalo con `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>`; los nodos lo aplican sin reiniciar. La autenticación debe estar habilitada para que las solicitudes tengan usuarios.
4. **En Solr 9, tenga en cuenta el gestor de seguridad de Java**, que Solr habilita por defecto: mantenga el token, la clave fijada y el directorio de caché bajo `SOLR_HOME`, como `/var/solr/data`, y conceda al agente lo que necesita más allá de la política de Solr, en `server/etc/security.policy` o en un archivo de política propio que `-Djava.security.properties` añade como `policy.url.3`:

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10 se ejecuta sin gestor de seguridad.
5. **Migre primero los permisos existentes:** los permisos y roles de usuario del plug-in basado en reglas de Solr dejan de aplicarse. Cree antes las políticas equivalentes. Hasta que el agente tenga su primera instantánea, se deniegan las solicitudes que no sean entre los nodos.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor GrantForge, el archivo del token de agente, un nombre para los nodos del clúster y un directorio con escritura para la instantánea firmada y los eventos en cola |
| `grantforge.trusted.proxies` | Proxies inversos cuyo `X-Forwarded-For` acepta el agente, direcciones o rangos separados por comas; ninguno por defecto |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copie la clave pública X.509 Ed25519 codificada en Base64 desde la página Agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura con GrantForge, `5000` / `8000` milisegundos por defecto |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide políticas nuevas, `30000` por defecto, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (`500` por defecto), tamaño de cola (`10000` por defecto), intervalo de envío (`5000` milisegundos por defecto) y límite del búfer en disco (64 MiB por defecto) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo se agrupan en un evento los accesos permitidos idénticos, `10000` por defecto, hasta `3600000` milisegundos; `0` notifica cada uno |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | El máximo de decisiones conservadas, `100000` por defecto, hasta `10000000`, `0` no conserva ninguna; y durante cuánto tiempo, `10000` por defecto, `1..3600000` milisegundos |

## Usuarios, grupos y roles

Las políticas nombran al usuario con el que la autenticación de Solr inicia sesión, como un usuario de la autenticación basic o el principal de un token web JSON. Los grupos son los roles que un token web JSON verificó para el usuario, junto con los grupos en los que GrantForge coloca al usuario; los roles son los que GrantForge da al usuario.

## Decisiones y limitaciones

- Sin una instantánea utilizable se deniega todo salvo las solicitudes entre los nodos. Un servicio que no está en uso deja Solr sin comprobar, como sin plug-in de autorización.
- Un token vinculado a un servicio cuyo tipo no es `solr` lo deniega todo.
- Una solicitud de la API Collections que nombra sus colecciones solo en un cuerpo JSON, como algunas solicitudes de la API v2, no nombra ninguna que el agente pueda ver, y necesita `admin` en el clúster.
- Solr resuelve los alias en sus colecciones antes de preguntar, así que las políticas nombran colecciones, no alias.
- La API `/admin/authorization` de Solr no puede editar las reglas del agente: GrantForge guarda las políticas.
- Las direcciones de cliente son solo direcciones y rangos; un nombre de host no coincide con nada.
- Las decisiones se conservan durante `grantforge.decision.cache.ttl.ms`, así que una política empieza o deja de aplicarse hasta ese tiempo después.

## Guías relacionadas

- [Apache Solr](/es/plugins/solr/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
