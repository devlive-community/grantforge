---
title: OpenSearch
description: Instalar y configurar el plug-in de servicio de OpenSearch, llegar a uno o varios nodos, iniciar sesión con contraseña o certificado de cliente TLS, buscar índices, flujos de datos, campos y el clúster, y gestionar quién puede leer, escribir y administrar qué, hasta campos, valores enmascarados y documentos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de OpenSearch busca índices, flujos de datos, campos y el clúster, y gestiona políticas de acceso en los términos de OpenSearch: leer campos, indexar, eliminar, crear y administrar índices, y las operaciones del propio clúster, con máscaras de campos y filtros de filas de índices. Su ID de plug-in y su tipo de servicio son ambos `opensearch`. El plug-in de servicio se ejecuta en el servidor de GrantForge; el [agente de OpenSearch](/es/external/opensearch-agent/), un servicio junto al clúster, aplica las políticas con los roles, la seguridad a nivel de campo, los campos enmascarados y la seguridad a nivel de documento del plug-in de seguridad.

Las pruebas de integración con Testcontainers del repositorio cubren OpenSearch `2.19`, `3.3`, `3.6` y `3.9` con el plug-in de seguridad; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/opensearch`. Confirme que `opensearch` está activado en **Administración de la plataforma → Plug-ins**; vuelva a explorar el directorio de plug-ins tras actualizarlo. El plug-in se comunica con los nodos mediante el cliente REST de bajo nivel de OpenSearch, en un cargador de clases propio.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y seleccione OpenSearch (`opensearch`).
2. Introduzca un nodo como URL, o varios nodos.
3. Elija cómo inicia sesión el usuario de búsqueda, introduzca sus credenciales, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el clúster: su versión de OpenSearch y su nombre, el usuario con el que entró y cuántos índices y flujos de datos tiene. Un clúster sin plug-in de seguridad no supera la prueba, ya que el agente aplica las políticas con él.

El usuario de búsqueda permite a GrantForge descubrir recursos; es distinto de los usuarios que leen y escriben. Una prueba de conexión correcta no significa que el acceso ya esté sujeto a políticas.

| Ajuste | Propósito |
| --- | --- |
| `opensearch.hosts` | Obligatorio: un nodo como URL, por ejemplo `https://opensearch1.example.com:9200`; varios separados por comas, probados por turno cuando uno no está accesible. Sin esquema un nodo es `https`, y el puerto predeterminado es `9200`; los nodos de un servicio son todos `http` o todos `https` |
| `authentication` | `password` (predeterminado), o `certificate`: un certificado de cliente TLS que el plug-in de seguridad asigna a un usuario |
| `username`, `password` | password: el usuario de búsqueda y su contraseña, guardada cifrada |
| `ssl.verificationMode` | Como lo nombra OpenSearch Dashboards: `full` (predeterminado) comprueba los certificados y los nombres de los nodos, `certificate` solo sus certificados, `none` nada |
| `ssl.certificateAuthorities` | Ruta en el **servidor de GrantForge** de los certificados de CA PEM con los que se comprueban los nodos; vacío usa los de la JVM |
| `ssl.certificate`, `ssl.key` | certificate: rutas en el servidor de GrantForge del certificado de cliente PEM y su clave privada, PKCS #8 o la forma antigua de OpenSSL; requieren nodos `https` |
| `ssl.keyPassphrase` | certificate: la frase de contraseña de una clave privada cifrada, guardada cifrada |
| `timeout` | Cuánto pueden tardar la conexión y una petición, predeterminado `30000`, rango `1..600000` milisegundos |

Los ajustes de un método de inicio de sesión se rechazan para el otro, de modo que el servicio dice exactamente cómo inicia sesión. Los inicios de sesión rechazados, los permisos que faltan, los nodos inaccesibles y los certificados que no se pueden verificar se informan como tales.

### Qué ve el usuario de búsqueda

El usuario de búsqueda necesita leer lo que lista: la API resolve de OpenSearch para índices y flujos de datos, las capacidades de campos (field capabilities) para los campos y la descripción del clúster para su nombre. El rol integrado `readall` da los dos primeros; un usuario sin `cluster:monitor/main` supera igualmente la prueba de conexión, que entonces no nombra la versión. Los índices y flujos de datos cuyo nombre empieza por un punto, ocultos y del sistema, solo se listan cuando lo escrito empieza por un punto; los índices de respaldo de los flujos de datos se listan por su flujo. Los campos son los de los documentos: se omiten los campos de metadatos propios de OpenSearch, los objetos y los multicampos que indexan un campo una vez más, como `name.keyword`, ya que la seguridad a nivel de campo los muestra u oculta con su campo.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `index` | Comodines, distingue mayúsculas; el nombre de un índice o flujo de datos | `index`, `delete`, `create_index`, `manage_aliases`, `monitor`, `manage` |
| `field` | Comodines, distingue mayúsculas; bajo un índice, la ruta de un campo como `address.city` | `read` |
| `cluster` | Comodines, distingue mayúsculas; el nombre del clúster | `cluster_monitor`, `manage_snapshots`, `manage_templates`, `manage_pipelines` |

| Tipo de acceso | Grupo de acciones del plug-in de seguridad |
| --- | --- |
| `read` | `read`: búsquedas, lecturas, recuentos y similares |
| `index` | `index`: indexar y actualizar documentos, también por bulk |
| `delete` | `delete`: eliminar documentos |
| `create_index` | `create_index`: crear índices, también cuando la primera escritura crea uno |
| `manage_aliases`, `monitor` | `manage_aliases`, `indices_monitor` |
| `manage` | `manage`: toda acción administrativa de un índice, incluida su eliminación; implica `create_index`, `manage_aliases` y `monitor` |
| `cluster_monitor`, `manage_snapshots` | `cluster_monitor`, `manage_snapshots` |
| `manage_templates`, `manage_pipelines` | `cluster_manage_index_templates`, `cluster_manage_pipelines` |

La lectura se decide por campo, como la seguridad a nivel de campo muestra los campos de los documentos: una política `read` nombra campos, y `*` es todo campo. Así una política puede permitir un índice y denegar uno de sus campos, y el usuario lee todos los demás. En los valores de política, `*` coincide con cualquier secuencia de caracteres y `?` con un carácter. El editor de políticas sugiere índices y flujos de datos, los campos de los índices que nombra la política y el clúster.

Usar lo que permite una política requiere más en OpenSearch: multi-search, multi-get y desplazamiento para leer, y bulk para escribir. No son tipos de acceso propios; el agente concede con ellos las operaciones compuestas del clúster.

### Máscaras

Una política de enmascaramiento de un campo muestra al usuario sus valores enmascarados, como los campos enmascarados del plug-in de seguridad, en documentos y en agregaciones por igual:

| Máscara | Qué ve el usuario |
| --- | --- |
| Ocultar (Redact) | Mayúsculas como `X`, minúsculas como `x` y dígitos como `n` |
| Máscara parcial: mostrar los últimos 4 / los primeros 4 | Cada otro carácter como `x` |
| Hash (SHA-256) | El hash SHA-256 del valor |
| Hash con la sal del clúster (BLAKE2b) | El hash propio del plug-in de seguridad, con la sal del clúster como clave, de modo que valores iguales siguen siendo iguales pero no se pueden averiguar |
| Personalizada | Un algoritmo de hash como `SHA-512`, o expresiones regulares con sus reemplazos como `/[0-9]/::*` |
| Sin enmascarar | El valor tal cual |

### Filtros de filas

Una política de filtro de filas de un índice muestra al usuario solo los documentos que coinciden con una consulta. La consulta es el DSL de consultas de OpenSearch tal como lo admite la seguridad a nivel de documento, por ejemplo `{"term": {"region": "EU"}}`, y puede usar las sustituciones del plug-in de seguridad como `${user.name}`. Como en otros servicios, un usuario recibe el filtro de la primera política que se le aplica. Un filtro de filas solo puede comprobar campos que el usuario puede leer: la seguridad a nivel de campo también oculta los demás a la consulta.

## Ejemplo de políticas

Suponga que el clúster tiene los índices `logs-2026.10.26` y `hr`. Cree estas políticas activadas en el mismo servicio de datos:

| Recurso | Regla |
| --- | --- |
| `index=logs-*`, `field=*` | Permitir al grupo `analysts` `read` |
| `index=logs-*`, `field=client_ip` | Denegar `read` al grupo `analysts` |
| `index=logs-*` | Permitir al usuario `logstash` `index` y `create_index` |
| `index=hr`, `field=*` | Permitir al grupo `hr` `read` |
| `index=hr`, `field=salary` (enmascaramiento) | Mostrar al grupo `hr` el hash con la sal del clúster |
| `index=hr` (filtro de filas) | Mostrar al usuario `emea-hr` solo los documentos de `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Permitir al grupo `ops` `cluster_monitor` |

Cuando el agente aplica la nueva instantánea, los miembros de `analysts` leen todos los campos de los registros salvo la dirección del cliente; `logstash` escribe al instante el `logs-2026.10.27` de mañana, creándolo con su primer documento; los miembros de `hr` ven los salarios como hash; `emea-hr` solo ve a los empleados de la región; y los miembros de `ops` leen el estado y las estadísticas del clúster.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos; aplicarlas requiere un agente de OpenSearch por clúster. El despliegue y las comprobaciones están en [Agente de OpenSearch](/es/external/opensearch-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de OpenSearch](/es/external/opensearch-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
