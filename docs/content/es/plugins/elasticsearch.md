---
title: Elasticsearch
description: Instalar y configurar el plug-in de servicio de Elasticsearch, llegar a uno o varios nodos, iniciar sesión con una contraseña, una clave de API o un certificado de cliente TLS, buscar índices, flujos de datos, campos y el clúster, y gestionar quién puede leer, escribir y administrar qué con los privilegios propios de Elasticsearch, hasta campos y documentos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Elasticsearch busca índices, flujos de datos, campos y el clúster, y gestiona las políticas de acceso en los términos propios de Elasticsearch: leer campos, indexar y eliminar documentos, crear, eliminar y administrar índices, y las operaciones propias del clúster, con filtros de filas de índices. El ID del plug-in y el tipo de servicio son ambos `elasticsearch`. El plug-in de servicio se ejecuta en el servidor de GrantForge; el [agente de Elasticsearch](/es/external/elasticsearch-agent/), un servicio junto al clúster, aplica las políticas con los roles propios de Elasticsearch, la seguridad a nivel de campo y la seguridad a nivel de documento.

Las pruebas de integración con Testcontainers del repositorio cubren Elasticsearch `8.19`, `9.3`, `9.4` y `9.5` con la seguridad activada; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/elasticsearch`. Confirme en **Administración de la plataforma → Plug-ins** que `elasticsearch` está activado; vuelva a explorar el directorio de plug-ins tras actualizarlo. El plug-in habla con los nodos mediante el cliente REST de bajo nivel de Elasticsearch, en un cargador de clases propio.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y seleccione Elasticsearch (`elasticsearch`).
2. Introduzca un nodo como URL, o varios nodos.
3. Elija cómo inicia sesión el usuario de búsqueda, introduzca sus credenciales, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el clúster: la versión de Elasticsearch y su nombre, la licencia y si tiene seguridad a nivel de documento y de campo, el usuario con el que inició sesión y cuántos índices y flujos de datos tiene. Un clúster sin seguridad no supera la prueba, ya que el agente aplica las políticas con los roles de Elasticsearch.

El usuario de búsqueda permite a GrantForge descubrir recursos; es distinto de los usuarios que leen y escriben. Una prueba de conexión correcta no significa que el acceso ya esté sujeto a políticas.

| Ajuste | Propósito |
| --- | --- |
| `elasticsearch.hosts` | Obligatorio: un nodo como URL, por ejemplo `https://es1.example.com:9200`; varios separados por comas, probados por turno cuando uno no responde. Sin esquema un nodo es `https` y el puerto por defecto es `9200`; los nodos de un servicio son todos `http` o todos `https` |
| `authentication` | `password` (por defecto), `api_key` o `certificate`: un certificado de cliente TLS que un realm PKI asigna a un usuario |
| `username`, `password` | password: el usuario de búsqueda y su contraseña, guardada cifrada |
| `apiKey` | api_key: la clave de API codificada, tal como Elasticsearch la devuelve al crearla, guardada cifrada |
| `ssl.verificationMode` | Como lo llama Kibana: `full` (por defecto) verifica los certificados y nombres de los nodos, `certificate` solo sus certificados, `none` nada |
| `ssl.certificateAuthorities` | Ruta en el **servidor de GrantForge** de los certificados CA PEM con los que se verifican los nodos, como el `http_ca.crt` de Elasticsearch; vacío usa los de la JVM |
| `ssl.certificate`, `ssl.key` | certificate: rutas en el servidor de GrantForge del certificado de cliente PEM y su clave privada, PKCS #8 o el formato OpenSSL anterior; requieren nodos `https` |
| `ssl.keyPassphrase` | certificate: la frase de contraseña de una clave privada cifrada, guardada cifrada |
| `timeout` | Cuánto pueden durar la conexión y una solicitud, por defecto `30000`, rango `1..600000` milisegundos |

Los ajustes de un método de inicio de sesión se rechazan para los demás, de modo que el servicio dice exactamente cómo inicia sesión. Los inicios de sesión rechazados, las claves de API revocadas, los privilegios que faltan, los nodos que no responden y los certificados que no se pueden verificar se informan como tales.

### Qué ve el usuario de búsqueda

El usuario de búsqueda necesita leer lo que lista: la API resolve de Elasticsearch para índices y flujos de datos, las capacidades de campos para los campos y la descripción del clúster para su nombre. Un rol con `read` y `view_index_metadata` sobre los índices da los dos primeros; un usuario sin el privilegio de clúster `monitor` supera igualmente la prueba de conexión, que entonces no nombra la versión ni la licencia. Los índices y flujos de datos cuyos nombres empiezan por un punto, los ocultos y los de sistema, se listan solo cuando lo escrito empieza por un punto; los índices de respaldo de los flujos de datos se listan por su flujo de datos. Los campos son los de los documentos: se omiten los campos de metadatos propios de Elasticsearch, los objetos y los multicampos que indexan un campo otra vez, como `name.keyword`, ya que el agente los concede con su campo.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `index` | Comodines, distingue mayúsculas; el nombre de un índice o flujo de datos | `index`, `delete`, `create_index`, `delete_index`, `view_index_metadata`, `monitor`, `manage` |
| `field` | Comodines, distingue mayúsculas; bajo un índice, la ruta de un campo como `address.city` | `read` |
| `cluster` | Comodines, distingue mayúsculas; el nombre del clúster | `cluster_monitor`, `create_snapshot`, `manage_slm`, `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` |

Cada tipo de acceso es el privilegio de Elasticsearch del mismo nombre, salvo `cluster_monitor`, que es el privilegio de clúster `monitor`:

| Tipo de acceso | Qué permite |
| --- | --- |
| `read` | Búsquedas, obtenciones, recuentos, búsqueda múltiple y scroll |
| `index`, `delete` | Indexar y actualizar documentos, también por bulk; eliminar documentos |
| `create_index`, `delete_index` | Crear índices, también cuando la primera escritura crea uno; eliminar índices y flujos de datos |
| `view_index_metadata`, `monitor` | Leer mappings, ajustes y alias; leer estadísticas y recuperación |
| `manage` | Toda acción administrativa de un índice; implica `create_index`, `delete_index`, `view_index_metadata` y `monitor` |
| `cluster_monitor` | Leer la salud, el estado y las estadísticas del clúster |
| `create_snapshot`, `manage_slm` | Crear snapshots; gestionar políticas de ciclo de vida de snapshots, lo que implica `create_snapshot` |
| `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` | Gestionar plantillas de índice, pipelines de ingesta y políticas de ciclo de vida de índices |

La lectura se decide por campo, como la seguridad a nivel de campo muestra los campos de los documentos: una política `read` nombra campos, y `*` es todo campo. Así una política puede permitir un índice y denegar uno de sus campos, y el usuario lee todos los demás. En los valores de las políticas, `*` coincide con cualquier secuencia de caracteres y `?` con un carácter. El editor de políticas sugiere índices y flujos de datos, los campos de los índices que nombra la política y el clúster.

### Filtros de filas

Una política de filtro de filas de un índice muestra al usuario solo los documentos que coinciden con una consulta. La consulta es el DSL de consultas de Elasticsearch tal como lo toma la seguridad a nivel de documento, por ejemplo `{"term": {"region": "EU"}}`, y puede ser una plantilla, por ejemplo con `{{_user.username}}`. Como en otros servicios, un usuario recibe el filtro de la primera política que se le aplica. Un filtro de filas solo puede consultar campos que el usuario puede leer: la seguridad a nivel de campo oculta los demás también a la consulta.

Elasticsearch no enmascara valores, por lo que el tipo de servicio no ofrece enmascaramiento.

### La licencia

Elasticsearch tiene seguridad a nivel de documento y de campo a partir de una licencia Platinum. Por debajo, una política que deja a un usuario leer solo algunos campos o algunos documentos de un índice no se puede aplicar, así que el agente no concede a ese usuario la lectura del índice en lugar de más de lo que permiten las políticas, y lo dice. Las políticas que permiten índices completos, escribir y administrar funcionan con cualquier licencia.

## Ejemplo de políticas

Supongamos que el clúster, con licencia Platinum, tiene los índices `logs-2026.10.26` y `hr`. Cree estas políticas habilitadas en el mismo servicio de datos:

| Recurso | Regla |
| --- | --- |
| `index=logs-*`, `field=*` | Permitir `read` al grupo `analysts` |
| `index=logs-*`, `field=client_ip` | Denegar `read` al grupo `analysts` |
| `index=logs-*` | Permitir `index` y `create_index` al usuario `logstash` |
| `index=hr`, `field=*` | Permitir `read` al grupo `hr` |
| `index=hr` (filtro de filas) | Mostrar al usuario `emea-hr` solo documentos de `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Permitir `cluster_monitor` al grupo `ops` |

Una vez que el agente aplica la nueva instantánea, los miembros de `analysts` leen todo campo de los logs salvo la dirección del cliente; `logstash` escribe al instante el `logs-2026.10.27` de mañana y lo crea con su primer documento; los miembros de `hr` leen los empleados; `emea-hr` ve solo los empleados de la región; y los miembros de `ops` leen la salud y las estadísticas del clúster.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos; aplicarlas requiere un agente de Elasticsearch por clúster. El despliegue y las comprobaciones están en [Agente de Elasticsearch](/es/external/elasticsearch-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de Elasticsearch](/es/external/elasticsearch-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
