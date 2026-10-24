---
title: Apache Solr
description: Instalar y configurar el plug-in de servicio de Solr, llegar a uno o varios nodos, iniciar sesión con autenticación basic o un token web JSON, buscar colecciones y sus rutas de solicitud, y gestionar quién puede consultar, actualizar y administrar qué.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Solr busca colecciones y las rutas de solicitud dentro de ellas, y gestiona políticas de acceso sobre lo que Solr decide: consultar, actualizar, leer y modificar esquemas y configs, administrar colecciones y administrar el clúster. Su ID de plug-in y su tipo de servicio son ambos `solr`. El plug-in de servicio se ejecuta en el servidor GrantForge; el [agente de Solr](/es/external/solr-agent/), instalado en los nodos, aplica las políticas.

Las pruebas de integración con Testcontainers del repositorio cubren nodos SolrCloud de Solr `9.4.1`, `9.8.1`, `9.11.0` y `10.0.0`; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/solr`. Compruebe en **Administración de la plataforma → Plug-ins** que `solr` está habilitado; vuelva a escanear el directorio de plug-ins tras una actualización. El plug-in habla con los nodos mediante el cliente HTTP del JDK de SolrJ 9.11, en su propio cargador de clases.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y elija Apache Solr (`solr`).
2. Introduzca la URL base de un nodo, o de varios.
3. Elija cómo inicia sesión el usuario de búsqueda, introduzca sus credenciales, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el clúster: su versión de Solr, si ejecuta SolrCloud y cuántas colecciones tiene.

El usuario de búsqueda permite a GrantForge descubrir recursos; es independiente de los usuarios que consultan y actualizan. Una prueba de conexión correcta no significa que el acceso ya esté sujeto a políticas.

| Ajuste | Propósito |
| --- | --- |
| `solr.urls` | Obligatorio: la URL base de un nodo, como `https://solr1.example.com:8983/solr`; varias separadas por comas, probadas por turno cuando una no se puede alcanzar |
| `authentication` | `none` (predeterminado), `basic` o `jwt` |
| `username`, `password` | basic: el usuario de búsqueda y su contraseña; la contraseña se guarda cifrada |
| `token` | jwt: el token web JSON del usuario de búsqueda; se guarda cifrado |
| `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword`, `javax.net.ssl.trustStoreType` | https: el almacén de confianza, en el **servidor GrantForge**, con el que se comprueban los certificados de los nodos, su contraseña y su tipo, `PKCS12` por defecto; vacío usa el de la JVM |
| `timeout` | Cuánto puede tardar una solicitud, `30000` por defecto, rango `1..600000` milisegundos |

Los ajustes de un modo de inicio de sesión se rechazan con otro, de modo que un servicio dice exactamente cómo inicia sesión. Un inicio de sesión rechazado, un usuario que no puede ver algo y un nodo inalcanzable, también detrás de un proxy que responde por él, se notifican como tales.

### Qué ve el usuario de búsqueda

Listar colecciones es el permiso `collection-admin-read` de Solr, y las rutas de solicitud de una colección provienen de su config, `config-read`. Concédaselos al usuario de búsqueda en las reglas de Solr o, una vez instalado el agente, `admin_read` en el clúster y `config_read` en las colecciones. Las rutas de las colecciones que el usuario de búsqueda no puede ver quedan fuera de las sugerencias. Un nodo que se ejecuta sin SolrCloud tiene cores en lugar de colecciones, y el plug-in los presenta como colecciones.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `collection` | Comodín, distingue mayúsculas; el nombre de la colección | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit`, `admin_collection` |
| `path` | Comodín, distingue mayúsculas; bajo una colección; la ruta de solicitud, como `/select`, `/export` o `/update/json` | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit` |
| `cluster` | Su único valor, `*` | `health`, `metrics`, `admin_read`, `security`, `admin` |

| Tipo de acceso | Permisos de Solr | Implica |
| --- | --- | --- |
| `query` | `read`: buscar, obtener, exportar | — |
| `update` | `update` | — |
| `schema_read`, `schema_edit` | `schema-read`, `schema-edit` | `schema_edit` implica `schema_read` |
| `config_read`, `config_edit` | `config-read`, `config-edit` | `config_edit` implica `config_read` |
| `admin_collection` | `collection-admin-read` y `collection-admin-edit` de las solicitudes de la API Collections que nombran la colección: crear, recargar, respaldar, eliminar y similares | `query`, `update`, `schema_edit`, `config_edit` |
| `health`, `metrics` | `health`, `metrics-read` | — |
| `admin_read` | `collection-admin-read` de todo el clúster, `core-admin-read`, `zk-read`, `filestore-read`, `package-read`, y la lectura de la config y el esquema de los nodos | `health`, `metrics` |
| `security` | `security-read`, `security-edit` | — |
| `admin` | Todo | `admin_read`, `security`, `admin_collection` |

Una solicitud a una colección se decide en la colección y en la colección con su ruta de solicitud, y una denegación en cualquiera de las dos la rechaza; así, una política puede permitir una colección y denegar su manejador `/export`, o permitir actualizar solo a través de `/update*`. Una solicitud a varias colecciones necesita cada una de ellas. Bajo una colección, la ruta de solicitud es el manejador al que Solr dirige la solicitud, tal como Solr lo nombra.

Una solicitud de la API Collections que nombra colecciones, mediante su parámetro `name` o `collection`, necesita `admin_collection` en cada una; así, una política sobre `team-*` permite a un equipo crear colecciones cuyo nombre empieza por `team-`, y recargarlas, respaldarlas o eliminarlas, pero ninguna otra. Una solicitud que no nombra ninguna, como listar todas las colecciones, pertenece al clúster. `admin` en el clúster lo concede todo, como el permiso `all` de Solr. En los valores de política, `*` coincide con cualquier secuencia de caracteres y `?` con uno. El editor de políticas sugiere las colecciones y las rutas de solicitud de las colecciones que nombra una política.

### Direcciones de cliente

Un elemento de política puede aplicarse solo a algunos clientes: su condición **Direcciones de cliente** (`ip-range`) acepta direcciones y rangos como `10.1.0.0/16` o `2001:db8::/32`. Los nombres de host no se resuelven. Detrás de un proxy inverso, el cliente es el proxy salvo que el agente confíe en él: entonces el cliente es la última dirección de `X-Forwarded-For` que no añadió ningún proxy de confianza, de modo que un cliente no puede atribuirse una dirección elegida por él.

## Ejemplo de políticas

Suponga que el clúster tiene las colecciones `sales` y `hr`. Cree estas políticas de acceso habilitadas en el mismo servicio de datos:

| Recursos | Regla |
| --- | --- |
| `collection=sales` | Permitir `query` al grupo `analysts` |
| `collection=sales`, `path=/export` | Denegar `query` al usuario `alice` |
| `collection=sales`, `path=/update*` | Permitir `update` al usuario `ingest` |
| `collection=team-*` | Permitir `admin_collection` al grupo `search-team` |
| `cluster=*` | Permitir `metrics` y `health` al usuario `monitoring` |
| `collection=hr` | Permitir `query` al grupo `hr-staff`, desde `10.0.0.0/8` |
| `cluster=*` | Permitir `admin` al grupo `solr-admins` |

Cuando el agente aplica la nueva instantánea, los miembros de `analysts` consultan `sales`, `alice` todo salvo su manejador de exportación; `ingest` actualiza `sales` a través de sus manejadores de actualización y no puede consultarla; los miembros de `search-team` crean y administran sus propias colecciones `team-`; `monitoring` lee métricas y salud; los miembros de `hr-staff` consultan `hr` solo desde la red interna; y los miembros de `solr-admins` pueden hacerlo todo.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Aplicarlas requiere el agente de Solr en cada nodo. El despliegue y las comprobaciones están en el [agente de Solr](/es/external/solr-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de Solr](/es/external/solr-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
