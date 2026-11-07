---
title: MongoDB
description: Instalar y configurar el plug-in de servicio de MongoDB, conectarse a un conjunto de réplicas o clúster fragmentado, iniciar sesión con SCRAM, certificado de cliente, LDAP o Kerberos, buscar bases de datos y colecciones, y gestionar quién puede leer, escribir y administrar qué, colección por colección.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de MongoDB busca bases de datos y colecciones y gestiona políticas de acceso en los términos propios de MongoDB: lectura, flujos de cambios, inserción, actualización y eliminación, índices, opciones de colección, omitir la validación de documentos y borrar, por colección; crear colecciones y borrar la base de datos, por base de datos; listar todas las bases de datos y supervisar, para el despliegue en conjunto. El ID del plug-in y el tipo de servicio son ambos `mongodb`. El plug-in de servicio se ejecuta en el servidor de GrantForge; el [agente de MongoDB](/es/external/mongodb-agent/), que se ejecuta junto al despliegue, aplica las políticas con los roles propios de MongoDB.

Las pruebas de integración con Testcontainers del repositorio cubren conjuntos de réplicas de MongoDB `6.0`, `7.0`, `8.0` y `9.0`, y Kerberos en Percona Server for MongoDB `8.0`. Esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/mongodb`. Confirme en **Administración de la plataforma → Plug-ins** que `mongodb` está activado; vuelva a examinar tras actualizar el directorio de plug-ins. El plug-in habla con MongoDB mediante el controlador Java oficial de MongoDB, en un cargador de clases propio.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y seleccione MongoDB (`mongodb`).
2. Introduzca la cadena de conexión del despliegue, tal como la toma cualquier cliente de MongoDB.
3. Indique cómo inicia sesión el usuario de búsqueda, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el despliegue: la versión de MongoDB, si es un conjunto de réplicas, un clúster fragmentado o un solo servidor, quién inició sesión y cuántas bases de datos ve ese usuario.

El usuario de búsqueda permite a GrantForge encontrar recursos y no es el usuario que lee o escribe. Que la prueba de conexión tenga éxito no significa que el acceso ya siga las políticas.

| Ajuste | Propósito |
| --- | --- |
| `mongodb.uri` | Obligatorio: la cadena de conexión, como `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` o `mongodb+srv://cluster.example.com/`. Las opciones como `tls=true` van aquí; las credenciales no, y se rechaza una cadena que las contenga |
| `auth.mechanism` | `SCRAM-SHA-256` (predeterminado) o `SCRAM-SHA-1` para usuarios con contraseña, `MONGODB-X509` para un certificado de cliente, `PLAIN` para LDAP, `GSSAPI` para Kerberos |
| `username` | El usuario de búsqueda. Con `GSSAPI`, el principal; con `MONGODB-X509`, vacío lo toma del certificado |
| `password` | La contraseña del usuario de búsqueda, guardada cifrada |
| `auth.source` | SCRAM: la base de datos en la que se define el usuario, `admin` de forma predeterminada. Los demás mecanismos usan `$external` |
| `tls.ca.file` | Con `tls=true`: ruta en el **servidor de GrantForge** de los certificados CA PEM con los que se comprueban los servidores. Vacío usa los de la JVM |
| `tls.certificate.key.file` | Con `tls=true`: ruta en el **servidor de GrantForge** de un archivo PEM con el certificado de cliente y su clave, como lo toma `mongosh` |
| `tls.certificate.key.password` | La contraseña de una clave cifrada, guardada cifrada |
| `kerberos.keytab` | `GSSAPI`: ruta en el **servidor de GrantForge** del keytab del principal. Vacío usa la contraseña |
| `kerberos.kdc` | `GSSAPI`: los KDC del reino, separados por comas. Vacío usa el `krb5.conf` del servidor |
| `kerberos.service.name` | `GSSAPI`: el nombre de servicio del principal de los servidores, `mongodb` de forma predeterminada |
| `timeout` | Cuánto pueden tardar encontrar un servidor, conectar y un comando; `30000` de forma predeterminada, en el rango `1..600000` milisegundos |

Un inicio de sesión rechazado, un usuario de búsqueda sin un privilegio y un despliegue inalcanzable se informan como tales.

### Qué ve el usuario de búsqueda

MongoDB lista las bases de datos y colecciones sobre las que un usuario tiene algún privilegio, de modo que una búsqueda muestra lo que el usuario de búsqueda puede ver. Dele `read` sobre aquello para lo que se escriben políticas, o `readAnyDatabase` para todo. Las bases de datos propias de MongoDB, `admin`, `config` y `local`, y las colecciones de sistema se omiten: las políticas no las deciden.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `deployment` | Comodines, distingue mayúsculas. El nombre del conjunto de réplicas, `sharded` para un clúster fragmentado, `standalone` para un solo servidor | `list_databases`, `monitor` |
| `database` | Comodines, distingue mayúsculas | `create`, `drop_database` |
| `collection` | Comodines, distingue mayúsculas. Una colección o vista de una base de datos | `read`, `change_stream`, `insert`, `update`, `remove`, `create_index`, `drop_index`, `alter`, `bypass_validation`, `drop` |

| Tipo de acceso | Acciones de MongoDB |
| --- | --- |
| `read` | `find`, `listIndexes`, `collStats`, `killCursors`; en una base de datos completa también `listCollections` y `dbStats` |
| `change_stream` | `changeStream`; para abrir uno, MongoDB también exige `find` |
| `insert`, `update`, `remove` | `insert`, `update`, `remove` |
| `create_index`, `drop_index` | `createIndex`, `dropIndex` |
| `alter` | `collMod`, `validate`, `convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | `createCollection` en la base de datos: crear colecciones y vistas |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`: el nombre de cada base de datos, no solo de aquellas sobre las que el usuario tiene un privilegio |
| `monitor` | `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `inprog` |

En los valores de las políticas, `*` coincide con cualquier texto y `?` con un carácter. El editor de políticas sugiere el despliegue, las bases de datos y las colecciones de las bases de datos que nombra una política.

El privilegio de MongoDB para borrar una base de datos es el privilegio de borrar cada colección que contiene, así que el agente concede `drop_database` solo donde las políticas también permiten borrar cada colección. En caso contrario retiene el privilegio y lo indica, en lugar de dar más de lo que permiten las políticas. La administración de usuarios y roles no son tipos de acceso, y ninguna política los da.

### Filas y campos

MongoDB no filtra filas ni enmascara campos por rol, así que este tipo de servicio no ofrece ninguna de las dos cosas. Una vista da una lectura restringida: MongoDB comprueba al lector de una vista solo contra la vista, de modo que una política que permite `read` en una vista, y nada en su origen, muestra al usuario lo que muestra la vista y nada más.

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## Ejemplo de políticas

Suponga que el despliegue tiene las bases de datos `sales` y `hr`. Cree estas políticas activadas en un servicio de datos:

| Recurso | Regla |
| --- | --- |
| `database=sales`, `collection=*` | Permitir `read` al grupo `analysts` |
| `database=sales`, `collection=orders_public` | Permitir `read` al usuario `support` |
| `database=sales`, `collection=orders` | Permitir `insert` y `update` al usuario `ingest`; denegar `read` al grupo `contractors` |
| `database=team_*` | Permitir `create` al grupo `data-team` |
| `database=team_*`, `collection=*` | Permitir `read`, `insert`, `update`, `remove`, `create_index` y `drop` al grupo `data-team` |
| `deployment=*` | Permitir `monitor` al usuario `dashboards` |

Cuando el agente aplique la instantánea, los miembros de `analysts` leerán todo `sales`, también las colecciones creadas después; `support` leerá la vista sin su origen; `ingest` escribirá en `orders` sin leerlo; los miembros de `data-team` trabajarán libremente en las bases de datos `team_`; y `dashboards` verá el estado del servidor y ningún dato.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Para aplicarlas hace falta un agente de MongoDB por despliegue; consulte [Agente de MongoDB](/es/external/mongodb-agent/) para el despliegue y las comprobaciones.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de MongoDB](/es/external/mongodb-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
