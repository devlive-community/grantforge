---
title: Agente de Elasticsearch
description: Ejecutar el agente de Elasticsearch junto al clúster para que las políticas de GrantForge se conviertan en los roles propios de Elasticsearch, dados a los usuarios nativos y asignados a los usuarios de otros realms, con seguridad a nivel de campo y de documento donde la licencia la tiene; los patrones de índices siguen siendo patrones para que los índices nuevos sean accesibles al instante, y los accesos se informan desde los ficheros del registro de auditoría.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de Elasticsearch es un servicio junto al clúster. Mantiene el control de acceso propio de Elasticsearch de acuerdo con las políticas firmadas de un servicio de datos `elasticsearch`: un rol por usuario que contiene exactamente lo que las políticas le dan, con seguridad a nivel de campo y de documento donde la licencia del clúster la tiene. Un usuario nativo tiene el rol entre sus propios roles; un usuario de otro realm, como LDAP, SAML u OpenID Connect, lo recibe mediante una asignación de roles de su nombre. Elasticsearch comprueba entonces cada solicitud por sí mismo, y el agente nunca se interpone entre los clientes y el clúster. La distribución lo entrega como un JAR ejecutable, `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`, que requiere Java 17 o posterior.

Las pruebas de integración con Testcontainers del repositorio ejecutan el agente, y el JAR tal como se entrega, contra Elasticsearch `8.19`, `9.3`, `9.4` y `9.5` con la seguridad activada. Esto no certifica todas las demás versiones; el agente rechaza clústeres anteriores a `8.19`, clústeres sin seguridad y clústeres de OpenSearch, que mantiene el [agente de OpenSearch](/es/external/opensearch-agent/). El plug-in de servicio se describe en [Elasticsearch](/es/plugins/elasticsearch/).

## Funcionamiento

- **Un rol por usuario contiene lo que da GrantForge.** Para cada usuario al que las políticas dan algo, el agente mantiene un rol con su nombre, `grantforge_alice` para `alice`. Sus metadatos nombran al usuario y lo marcan como del agente, y el agente no toma como propio ningún rol sin esa marca, de modo que no cambia ningún otro rol.
- **Los usuarios nativos tienen el rol; los demás realms lo reciben por nombre.** El agente añade el rol a los roles de un usuario nativo y conserva todos sus demás roles. Las asignaciones de roles se aplican a todos los realms salvo el nativo y el de ficheros, así que el agente mantiene además una asignación de roles del mismo nombre que da el rol al nombre del usuario, para los usuarios que inician sesión mediante LDAP, Active Directory, SAML, OpenID Connect, JWT, Kerberos o PKI. El realm de ficheros no se puede cambiar mediante la API de Elasticsearch; sus usuarios no reciben ningún rol.
- **Los patrones de índices siguen siendo patrones.** El patrón de índices de una política, como `logs-*`, se concede como patrón donde todas las políticas deciden igual sobre él: toda política de cualquier tipo cuyos índices se solapen con el patrón lo cubre entero. El agente lo decide con exactitud, con autómatas finitos de los patrones. Los índices creados después, como los logs de mañana, son entonces accesibles al instante, también cuando el primer documento escrito los crea. Donde una política decide otra cosa dentro de un patrón, como una denegación de `logs-secret` dentro de `logs-*`, el agente concede cada índice y flujo de datos del clúster por su nombre, y los nuevos en la siguiente sincronización.
- **Seguridad a nivel de campo solo donde algo inferior se decide de otra forma.** Un índice que el usuario puede leer entero, sin ninguna política que pudiera quitarle un campo, se lee sin seguridad a nivel de campo, así que los campos añadidos después se leen al instante. Si no, el usuario lee los campos que permite una política, de los que tienen sus documentos y los que nombran las políticas, cada uno con sus multicampos, como `name.keyword` con `name`.
- **Los filtros de filas son seguridad a nivel de documento.** Un filtro de filas se convierte en la consulta de la entrada. La API validate de Elasticsearch comprueba cada consulta antes de concederla; una consulta que Elasticsearch no ejecutaría no muestra ningún documento al usuario, y se registra.
- **Nunca más de lo que permiten las políticas por debajo de Platinum.** Elasticsearch tiene seguridad a nivel de documento y de campo a partir de una licencia Platinum, y por debajo desactiva todo rol que la use. El agente lee la licencia en cada sincronización. Por debajo de Platinum, una lectura que necesita cualquiera de las dos, porque una política restringe campos o documentos del índice, no se concede en absoluto, y el agente registra qué usuario no puede leer qué índice y por qué; la lectura de índices completos y todo lo demás se concede como siempre.
- **Una entrada por conjunto de privilegios.** Los índices y patrones con los mismos privilegios comparten una entrada; las entradas nunca se solapan, ya que Elasticsearch une la seguridad a nivel de campo y de documento de entradas solapadas.
- **Las políticas del momento presente.** Cada sincronización planifica con las políticas tal como se aplican en ese momento, de modo que los periodos de validez empiezan y terminan a tiempo.
- **Los roles de los usuarios nativos son grupos.** Una política para un grupo se aplica a los usuarios nativos que tienen un rol con ese nombre, junto con los grupos en los que GrantForge pone a los usuarios.
- **Los cambios hechos a mano se deshacen.** En cada sincronización el agente compara sus roles, sus asignaciones de roles y los roles de los usuarios nativos con lo que tiene Elasticsearch; lo que difiere se vuelve a escribir, y el rol de un usuario que tiene otro se retira. Los roles se escriben en una solicitud antes de darse y se retiran antes de eliminarse. Cuando nada cambió, el agente no envía nada; un cambio que Elasticsearch rechaza se registra y no detiene los demás.
- **Se informan los roles ajenos a GrantForge.** Elasticsearch concede lo que da cualquiera de los roles de un usuario, así que el agente no puede quitar lo que da otro rol. Avisa una vez de cada rol así, que tenga un usuario nativo o dé una asignación de roles de su nombre, que dé lo que una política deniega, nombrando el rol, el usuario, el acceso, el índice y la política, y de los usuarios con el rol `superuser`, a los que ningún rol limita.
- **Los accesos vienen de los ficheros del registro de auditoría.** Elasticsearch escribe su registro de auditoría solo en un fichero de cada nodo. El agente lee los ficheros que se le indican, desde donde terminó la última lectura, los sigue cuando Elasticsearch los rota e informa de los eventos `access_granted` y `access_denied`. Cada uno nombra el usuario, el tipo de acceso, cada índice o flujo de datos alcanzado, o el clúster, y dice si lo decidió GrantForge o lo permitió un rol ajeno a GrantForge.

| Acción | Evento de acceso |
| --- | --- |
| `indices:data/read/*`: búsqueda, obtención, recuento | `read` en cada índice o flujo de datos |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/delete`, `data_stream/delete` | `delete_index` |
| `indices:admin/get`, `mappings/get`, `settings/get`, `resolve/*` | `view_index_metadata` |
| `indices:monitor/*`; otros `indices:admin/*` | `monitor`; `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` y `repository/*`, `slm/*` | `cluster_monitor`, `create_snapshot`, `manage_slm` en el clúster |
| Plantillas de índice y de componentes, pipelines de ingesta, `cluster:admin/ilm/*` | `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` en el clúster |

## Despliegue

1. Añada un servicio de datos `elasticsearch` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, seleccione el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un fichero que solo pueda leer la cuenta del agente.
2. Dé al agente un usuario con el privilegio de clúster `manage_security`, que gestiona roles, asignaciones de roles y usuarios, y el derecho a leer los mappings de cada índice y validar consultas, como un usuario con el rol `superuser`. Puede iniciar sesión con una contraseña, una clave de API que creó ese usuario o un certificado de cliente que un realm PKI le asigna.
3. Para informar de los accesos, active el registro de auditoría (`xpack.security.audit.enabled: true`), que requiere una licencia Gold o superior, y deje que el agente lea los ficheros de auditoría de los nodos, `<nombre del clúster>_audit.json` en el directorio de logs de cada nodo: ejecute el agente en un nodo o reúna los ficheros en un directorio que pueda leer. `xpack.security.audit.logfile.events.include: access_granted, access_denied` limita el registro a lo que informa el agente.
4. Escriba el fichero de ajustes sustituyendo las direcciones, rutas y nombres de ejemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   Estas rutas están en la máquina del agente, y el directorio de caché debe poder escribirse. Los ficheros de contraseña y de clave se leen como UTF-8 y se recortan.
5. Inicie el agente, por ejemplo como servicio de systemd:

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   Sustituya `<version>` por la versión de la distribución. Los ajustes indicados como `java -Dgrantforge.…=… -jar …` sobrescriben el fichero. El agente se detiene limpiamente con SIGTERM y envía o guarda los eventos pendientes; Elasticsearch conserva lo que se le dio.
6. **Pase los roles existentes a políticas:** los roles que los usuarios tienen fuera de GrantForge siguen aplicándose junto a los del agente. Cree las políticas equivalentes y retire luego esos roles a los usuarios; los avisos del agente nombran los roles que dan lo que una política deniega.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorio: el servidor de GrantForge, el fichero del token del agente, un nombre para este agente y un directorio escribible para la instantánea firmada, los eventos en cola y las posiciones de lectura del registro de auditoría |
| `grantforge.signing.key.file` | Fichero opcional que fija la clave de firma de las instantáneas; copie la clave pública Ed25519 X.509 codificada en Base64 de la página de agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.elasticsearch.hosts` | Obligatorio: los nodos como URL, probados por turno cuando uno no responde; sin esquema un nodo es `https` y el puerto por defecto es `9200` |
| `grantforge.elasticsearch.user`, `grantforge.elasticsearch.password.file` | El usuario del agente y el fichero de su contraseña |
| `grantforge.elasticsearch.api.key.file` | En su lugar: el fichero de una clave de API codificada, tal como Elasticsearch la devuelve al crearla |
| `grantforge.elasticsearch.tls.cert`, `grantforge.elasticsearch.tls.key` | En su lugar: certificado de cliente PEM y su clave privada, juntos, para iniciar sesión con un certificado; `grantforge.elasticsearch.tls.key.passphrase.file` contiene la frase de contraseña de una clave cifrada |
| `grantforge.elasticsearch.ssl.verification.mode` | `full` (por defecto) verifica los certificados y nombres de los nodos, `certificate` solo sus certificados, `none` nada |
| `grantforge.elasticsearch.tls.ca` | Certificados CA PEM que verifican los nodos, como el `http_ca.crt` de Elasticsearch; vacío confía en lo que confía Java |
| `grantforge.elasticsearch.timeout.ms` | Cuánto pueden durar la conexión y una solicitud, por defecto `30000`, rango `1..600000` milisegundos |
| `grantforge.elasticsearch.role.prefix` | El inicio de los nombres de los roles del agente, por defecto `grantforge_`; minúsculas, dígitos, guiones y guiones bajos, terminado en guion bajo, como mucho 32 caracteres. Al agente le pertenecen los roles que marcó cuyos nombres empiezan así |
| `grantforge.elasticsearch.unmanaged.users` | Usuarios que el agente deja en paz, separados por comas, como cuentas de servicio; siempre deja en paz a su propio usuario, a los usuarios reservados de Elasticsearch y a los usuarios con el rol `superuser` |
| `grantforge.sync.interval.ms` | Con qué frecuencia el agente pone Elasticsearch de acuerdo con las políticas, por defecto `10000`, rango `1000..3600000` milisegundos |
| `grantforge.audit.log.files` | Los ficheros del registro de auditoría que leer, separados por comas; sin ellos el agente no lee el registro de auditoría ni informa de accesos |
| `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.lines` | Con qué frecuencia se leen los ficheros, por defecto `5000` milisegundos, y cuántas líneas como máximo toma una lectura de cada uno, por defecto `5000`, para que un atraso se lea en varias vueltas |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura hacia GrantForge, por defecto `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide políticas nuevas, por defecto `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (por defecto `500`), tamaño de cola (por defecto `10000`), intervalo de envío (por defecto `5000` milisegundos) y el límite de la cola en disco (por defecto 64 MiB) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo se agrupan en un evento los accesos permitidos idénticos, por defecto `10000`, hasta `3600000` milisegundos; `0` envía cada uno |

## Usuarios, grupos y roles

Las políticas nombran a los usuarios de Elasticsearch por sus nombres de usuario. El agente mantiene roles para los usuarios nativos, para los usuarios que nombran las políticas, que pueden iniciar sesión mediante otro realm, y para los usuarios a los que antes mantenía roles; deja en paz a los usuarios reservados de Elasticsearch, como `kibana_system`. Los grupos son los demás roles de un usuario nativo junto con los grupos en los que GrantForge pone al usuario; el agente no conoce los grupos que da un directorio al iniciar sesión, así que dé a esos usuarios sus grupos en GrantForge. Los roles son los roles que GrantForge da al usuario.

## Decisiones y limitaciones

- Sin instantánea el agente no cambia nada. Un servicio que no está en uso, o un token vinculado a un servicio cuyo tipo no es `elasticsearch`, no concede nada: el agente retira sus roles y los elimina con sus asignaciones de roles, y se aplican los roles propios de Elasticsearch.
- Mientras el agente no se ejecuta, Elasticsearch conserva lo último que se le dio; los cambios de políticas lo esperan. Un cambio llega a Elasticsearch dentro del intervalo de actualización y del de sincronización.
- Los índices concedidos por nombre, y no mediante un patrón, reciben sus privilegios en la siguiente sincronización.
- Por debajo de Platinum, un usuario cuya lectura de un índice restringe una política no lee nada de él; por debajo de Gold, el agente no informa de accesos.
- Una asignación de roles del nombre de un usuario se aplica a todo realm con ese nombre de usuario; las políticas nombran usuarios, no realms.
- Los alias alcanzan los índices que hay detrás, así que las políticas nombran índices y flujos de datos, no alias. Los índices cuyos nombres empiezan por un punto solo se alcanzan mediante patrones de políticas que empiezan por un punto.
- El agente no gestiona los espacios ni los privilegios de aplicación de Kibana.

## Guías relacionadas

- [Elasticsearch](/es/plugins/elasticsearch/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
