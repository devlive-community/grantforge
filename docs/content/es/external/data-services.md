---
title: Servicios de datos, políticas y agentes
description: Gestiona con plug-ins los permisos de sistemas externos como HDFS y Hive, incluidos los servicios de datos, las políticas de acceso, los agentes y la auditoría de accesos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El grupo "Permisos sobre los datos" gestiona los permisos de los sistemas de datos ajenos a GrantForge, con una arquitectura similar a la de Apache Ranger: los plug-ins definen los tipos de servicio, los administradores escriben las políticas en la consola y los agentes desplegados en el sistema de destino descargan las políticas y deciden el acceso localmente.

> [!NOTE]
> La versión actual ofrece el marco de plug-ins, el editor de políticas general, la distribución de políticas y la auditoría de accesos, el tipo de servicio `hdfs`, el agente NameNode de Hadoop 3.5.0 y el plug-in de ejemplo (`example`). El plug-in de Hive y los agentes de otras versiones de Hadoop siguen en desarrollo.

```mermaid
flowchart LR
  C[Consola: servicios de datos y políticas] --> S[Servidor GrantForge]
  S -->|instantánea de políticas firmada| A[Agente (dentro de HDFS / Hive)]
  A -->|latido y auditoría de accesos| S
  U[El usuario accede a los datos] --> A
```

## Plug-ins

**Administración de la plataforma → Plug-ins** lista los plug-ins de tipos de servicio cargados. Los plug-ins integrados se proporcionan con el servidor; los demás plug-ins se colocan en el directorio `plugins` y luego se pulsa "Volver a buscar"; cada plug-in se carga de forma independiente y, si falla, solo se desactiva a sí mismo. El desarrollo de plug-ins se describe en [Plug-ins y tipos de servicio](/es/develop/plugins/).

![Plug-ins](/screenshots/plugins.png)

## HDFS

El paquete publicado incluye el plug-in de HDFS (`plugins/hdfs`), el tipo de servicio `hdfs`, igual que el servicio HDFS de Apache Ranger:

- Los recursos tienen un solo nivel, `path`, que se compara por ruta: `/data/sales` coincide consigo mismo y, si marcas "recursivo", también con todos los archivos y directorios que contiene; admite exclusiones.
- Los tipos de acceso `read`, `write` y `execute` se corresponden con los bits de permisos de HDFS.
- El plug-in se conecta al cluster con el propio cliente de Hadoop; la prueba de conexión comprueba que el directorio de consulta existe y que su contenido se puede listar. Al escribir una política, introducir una ruta muestra los subdirectorios y archivos de ese directorio, con los directorios antes que los archivos.
- El plug-in del servidor se encarga de la administración y las consultas; para que las políticas restrinjan el acceso a HDFS, además hay que desplegar el [agente NameNode de Apache Hadoop HDFS](/es/external/hdfs-agent/).

| Configuración | Explicación |
| --- | --- |
| `username` | Usuario con el que se consultan los directorios; con Kerberos, el principal, por ejemplo `grantforge@EXAMPLE.COM` |
| `password` / `keytab` | Con Kerberos, uno de los dos: la contraseña del principal o la ruta del archivo keytab en el servidor de GrantForge |
| `fs.default.name` | `hdfs://namenode:8020`, `hdfs://nameservice1` en alta disponibilidad o `webhdfs://namenode:9870` |
| `hadoop.security.authentication` | `simple` o `kerberos` |
| `hadoop.security.authorization`, `hadoop.security.auth_to_local` | Igual que en el core-site.xml del cluster |
| `dfs.namenode.kerberos.principal` y similares | Los principales de NameNode, DataNode y Secondary NameNode, por ejemplo `nn/_HOST@EXAMPLE.COM` |
| `hadoop.rpc.protection` | `authentication`, `integrity` o `privacy`, igual que en el cluster |
| Configuración adicional de Hadoop | Una pareja `key=value` por línea, para alta disponibilidad y otros ajustes, por ejemplo `dfs.nameservices=nameservice1`, `dfs.ha.namenodes.nameservice1=nn1,nn2`, `dfs.namenode.rpc-address.nameservice1.nn1=nn1:8020`, `dfs.client.failover.proxy.provider.nameservice1=org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider` |
| `lookup.path` | Directorio de consulta, `/` por defecto; si se pone `/data`, una entrada vacía muestra el contenido de `/data` y las entradas relativas se completan a partir de ahí. Útil en clusters donde el usuario de consulta no tiene permiso para listar el directorio raíz |
| `lookup.max.entries` | Número máximo de entradas que se analizan en una consulta de directorio, `10000` por defecto, rango `1..100000`; al superar el límite se devuelve un error, para no omitir candidatos en silencio |

La configuración adicional de Hadoop prevalece sobre los ajustes de conexión del mismo nombre, y tanto la validación de configuración como el inicio de sesión usan los valores prevalecidos. `fs.defaultFS` y `fs.default.name` son alias, así que en la configuración adicional solo se puede definir uno de los dos. Las claves repetidas, las direcciones que no son del cluster y la configuración de Kerberos sin credenciales se rechazan al guardar. En la dirección del cluster se escribe solo la URI del cluster; los subdirectorios que se quieran consultar van en `lookup.path`.

`lookup.path` limita el rango de exploración de las rutas candidatas; no sustituye al control de accesos propio de HDFS: los enlaces simbólicos y los montajes ViewFS siguen la configuración del cluster. En la entrada se puede omitir la `/` inicial, se admiten `/` y `.` repetidos y se rechazan `..` y las rutas absolutas fuera del rango. Un directorio inexistente devuelve candidatos vacíos; los permisos insuficientes y los fallos de conexión muestran un error.

Con Kerberos, el servidor de GrantForge tiene que poder encontrar el KDC: configura `/etc/krb5.conf` o indícalo con `-Djava.security.krb5.conf=`.

## Servicios de datos

**Permisos sobre los datos → Servicios de datos**: un servicio es una instancia de un sistema externo cuyos permisos administra GrantForge, por ejemplo un cluster de HDFS. Al añadir un servicio se elige el tipo de servicio y se rellenan los datos de conexión según los elementos de configuración definidos por el plug-in; antes puedes **probar la conexión**. La configuración sensible, como las contraseñas, se guarda cifrada y ya no se muestra después de guardar.

![Servicios de datos](/screenshots/services.png)

## Políticas

**Permisos sobre los datos → Políticas** decide quién puede hacer qué sobre los recursos de un servicio de datos:

- Las **políticas de acceso** permiten o deniegan el acceso.
- Las **políticas de enmascaramiento** ocultan campos.
- Las **políticas de filtrado de filas** solo dejan pasar algunas filas.

La jerarquía de recursos (en Hive, bases de datos, tablas y columnas, por ejemplo), los tipos de acceso (select, update, por ejemplo) y las condiciones provienen del plug-in del tipo de servicio; al escribir el recurso puedes buscar los recursos que existen realmente en el sistema de destino. Las políticas se aplican a usuarios, grupos de usuarios o roles.

Los niveles que se pueden explorar, como las rutas de HDFS, tienen un botón **Explorar**: abra directorios nivel a nivel, vea su propietario, grupo y permisos, y elija varios archivos o directorios a la vez. Si una búsqueda o la exploración fallan, se muestra el motivo (sin permiso, inaccesible o directorio demasiado grande) y puede reintentarlo.

![Políticas](/screenshots/policies.png)

## Agentes

**Permisos sobre los datos → Agentes**: los agentes se despliegan dentro del sistema de destino; con un token envían periódicamente un latido y descargan una instantánea de políticas firmada, y deciden el acceso localmente. Aquí se emiten los tokens de agente (se muestran una sola vez) y se consulta si cada agente ya usa las últimas políticas.

![Agentes](/screenshots/agents.png)

## Auditoría de accesos

**Permisos sobre los datos → Auditoría de accesos**: cada decisión de acceso que informa un agente —quién hizo qué, cuándo, desde dónde y sobre qué recurso, si se permitió o se denegó el acceso y qué política lo decidió. Los registros se conservan 90 días por defecto.

![Auditoría de accesos](/screenshots/access-audit.png)
