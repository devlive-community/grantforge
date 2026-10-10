---
title: Apache Hadoop HDFS
description: Instalar y configurar el plug-in HDFS, explorar directorios y administrar políticas de acceso a rutas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in Apache Hadoop HDFS ofrece conexiones a clústeres, búsqueda de rutas y gestión de políticas en GrantForge. Tanto el ID del plug-in como el tipo de servicio son `hdfs`.

## Instalación

La distribución incluye el plug-in en `plugins/hdfs`. Comprueba que `hdfs` esté habilitado en **Administración de la plataforma → Plug-ins**; vuelve a escanear el directorio tras actualizarlo.

## Añadir un servicio de datos

1. Abre **Permisos de datos → Servicios de datos**, añade un servicio y selecciona HDFS (`hdfs`).
2. Introduce la URI del clúster y el usuario de consulta. Hadoop 2.x utiliza `webhdfs://namenode:50070`; 3.x permite `hdfs://namenode:8020` o `webhdfs://namenode:9870`. Para HTTPS, usa `swebhdfs://` con el puerto real del clúster.
3. Configura el directorio de consulta y prueba la conexión: el directorio debe existir y poder listarse. Después guarda el servicio.

| Configuración | Función |
| --- | --- |
| `fs.default.name` | URI obligatoria del clúster, sin subdirectorio, credenciales ni parámetros; HA puede usar `hdfs://nameservice1` con las propiedades adicionales correspondientes |
| `username` | Usuario de consulta obligatorio; en Kerberos, un principal como `grantforge@EXAMPLE.COM` |
| `hadoop.security.authentication` | Predeterminado `simple`; selecciona `kerberos` para un clúster Kerberos |
| `hadoop.security.authorization` | Si Hadoop comprueba permisos, predeterminado `false`; coherente con el core-site.xml del clúster |
| `hadoop.security.auth_to_local` | Reglas de asignación de principales Kerberos a nombres de usuario, coherentes con el core-site.xml del clúster |
| `password` / `keytab` | Contraseña Kerberos o ruta de un archivo keytab en el servidor GrantForge |
| `dfs.namenode.kerberos.principal`, `dfs.datanode.kerberos.principal`, `dfs.secondary.namenode.kerberos.principal` | Principales de los componentes del clúster con Kerberos, como `nn/_HOST@EXAMPLE.COM`, coherentes con la configuración del clúster |
| `lookup.path` | Directorio inicial de consulta y exploración, por defecto `/`, por ejemplo `/data`; la exploración queda dentro de él |
| `lookup.max.entries` | Límite para escaneos completos, por defecto `10000`, intervalo `1..100000` |
| `hadoop.config` | Un `key=value` por línea para HA y otras propiedades Hadoop; sobrescribe la configuración de conexión del mismo nombre |
| `hadoop.rpc.protection` | `authentication`, `integrity` o `privacy`, de acuerdo con el clúster |
| `ssl.client.truststore.location` | Ruta en el servidor de GrantForge del almacén de confianza que verifica los certificados de los NameNode `swebhdfs://`; vacío confía en lo que confía Java en el servidor |
| `ssl.client.truststore.password` | La contraseña del almacén, si está protegido; se guarda cifrada |
| `ssl.client.truststore.type` | `jks` (predeterminado) o `pkcs12` |

Con la extensión de atributos NameNode habilitada en Hadoop 2.7.7, las consultas de usuarios normales a la ruta raíz `/` provocan una `NullPointerException` confirmada en upstream; establece `lookup.path` en un directorio real como `/data` (consulta la [guía del agente](/es/external/hdfs-agent/)).

Kerberos también requiere un KDC accesible, el `krb5.conf` del servidor y reglas `hadoop.security.auth_to_local` y principals de servicio compatibles. La cuenta de consulta obtiene metadatos de directorios.

Con Kerberos, GrantForge reutiliza un inicio de sesión entre búsquedas en lugar de consultar al KDC cada vez: Hadoop renueva un inicio con keytab cuando su ticket está por vencer, y uno con contraseña se repite cuando queda menos de una quinta parte de la vida del ticket (y al menos un minuto); una contraseña cambiada o un keytab actualizado inician sesión de nuevo. Cada servicio usa su propio almacén de confianza, que un `ssl-client.xml` en el classpath del servidor no reemplaza.

La configuración se valida al guardar: en `hadoop.config`, `fs.defaultFS` y `fs.default.name` son alias, así que configure solo uno; la URI del clúster no debe contener credenciales, ruta, consulta ni fragmento; `kerberos` requiere `password` o `keytab`; `lookup.path` debe ser una ruta absoluta sin `..`; `lookup.max.entries` debe estar entre `1` y `100000`. Las propiedades adicionales sobrescriben la configuración de conexión del mismo nombre, y tanto la validación como el inicio de sesión usan los valores sobrescritos.

## Explorar rutas

Selecciona el servicio en **Permisos de datos → Políticas** y utiliza **Explorar** junto a `path`.

- Abre directorios, navega por la ruta o vuelve al directorio padre y carga las siguientes páginas cuando las necesites.
- Consulta indicadores de archivo/directorio, propietario, grupo, permisos, tamaño y fecha de modificación.
- Selecciona varios archivos o directorios, o el directorio actual, para añadirlos a la política; escribir rutas sigue ofreciendo sugerencias.

RPC y los endpoints WebHDFS con listados por lotes usan paginación nativa. Los endpoints antiguos leen directorios dentro del límite de escaneo y devuelven un error al superarlo. Los fallos de permisos, autenticación o conexión muestran el motivo y permiten reintentar.

La entrada puede omitir la `/` inicial, se permiten `/` y `.` repetidos, y se rechazan `..` y las rutas absolutas fuera de `lookup.path`; un directorio inexistente no devuelve candidatos. La exploración no sustituye el control de acceso propio de HDFS; los enlaces simbólicos y montajes ViewFS siguen la configuración del clúster.

## Aplicar políticas

El único nivel de recurso es `path`, con accesos `read`, `write` y `execute`. Las políticas de rutas admiten recursión y exclusiones.

El plug-in del servidor gestiona y consulta recursos. Para aplicar las políticas también se necesita el agente NameNode correspondiente a la versión Hadoop; el usuario debe cumplir los permisos nativos HDFS y las políticas GrantForge. Hay agentes numerados para 2.7, 2.10, 3.2, 3.3, 3.4 y 3.5. La guía del agente detalla combinaciones verificadas, cobertura de autenticación y HA y límites del superusuario.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente NameNode de Apache Hadoop HDFS](/es/external/hdfs-agent/)
- [Desarrollo de plug-ins y tipos de servicio](/es/develop/plugins/)
