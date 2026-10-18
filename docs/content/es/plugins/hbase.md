---
title: Apache HBase
description: Instala y configura el plug-in de servicio HBase, encuentra el clúster por ZooKeeper o por servidores de arranque, inicia sesión en modo simple o con Kerberos, busca espacios de nombres, tablas y familias de columnas, y gestiona políticas de acceso en los ámbitos propios de HBase.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de HBase busca espacios de nombres, tablas y familias de columnas, y gestiona políticas de acceso en los ámbitos de los permisos propios de HBase: el ámbito global, los espacios de nombres, las tablas, las familias de columnas y las columnas. Su ID de plug-in y su tipo de servicio son ambos `hbase`. El plug-in de servicio se ejecuta en el servidor GrantForge; el [agente de HBase](/es/external/hbase-agent/), instalado en los masters y region servers, aplica las políticas.

Las pruebas de integración con Testcontainers del repositorio cubren HBase `2.5.16`, `2.6.7` y `3.0.0`, construidos a partir de las versiones de Apache verificadas con SHA-512, y un clúster Kerberos de `2.6.7`; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/hbase`. Comprueba que `hbase` está habilitado en **Administración de la plataforma → Plug-ins**; vuelve a escanear el directorio de plug-ins después de actualizarlo. El plug-in habla con el clúster mediante el cliente propio de HBase (`hbase-shaded-client-byo-hadoop` 2.6.7) sobre el cliente shaded de Hadoop 3.5.0, en un cargador de clases propio, así que funciona tanto con HBase 2.x como con 3.x.

## Añadir un servicio de datos

1. Abre **Permisos de datos → Servicios de datos**, añade un servicio y selecciona Apache HBase (`hbase`).
2. Introduce el quórum de ZooKeeper, o los servidores de arranque de un clúster HBase 2.5 o posterior.
3. Elige cómo inicia sesión el usuario de búsqueda, introdúcelo, prueba la conexión y guarda el servicio. La prueba inicia sesión y describe el clúster: su ID, su versión y cuántos region servers están activos.

Los ajustes usan los nombres de los clientes de HBase, así que pueden copiarse de un `hbase-site.xml`. El usuario de búsqueda permite a GrantForge descubrir recursos; es distinto de los clientes que leen y escriben. Una prueba de conexión correcta no significa que las solicitudes ya estén sujetas a las políticas.

| Ajuste | Propósito |
| --- | --- |
| `hbase.zookeeper.quorum` | Hosts de ZooKeeper, `host` o `host:puerto` separados por comas, como `zk1,zk2,zk3:2181`; obligatorio sin servidores de arranque |
| `hbase.zookeeper.property.clientPort` | El puerto de los hosts del quórum que no indican uno, `2181` por defecto |
| `zookeeper.znode.parent` | La raíz del clúster en ZooKeeper, `/hbase` por defecto |
| `hbase.client.bootstrap.servers` | En lugar de ZooKeeper, desde HBase 2.5: masters o region servers, `host:puerto` separados por comas |
| `hbase.security.authentication` | `simple` (por defecto): un nombre de usuario; `kerberos`: un principal con keytab o contraseña |
| `username` | Usuario de búsqueda obligatorio; con Kerberos, el principal completo, como `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: la contraseña del principal, sin keytab; se guarda cifrada |
| `keytab` | Kerberos: ruta del keytab del principal, legible en el **servidor GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del reino del principal, `host[:puerto]` separados por comas; vacío usa el `krb5.conf` del servidor |
| `hbase.master.kerberos.principal` | Kerberos: el principal de los masters, por defecto `hbase/_HOST@` y el reino del principal de búsqueda |
| `hbase.regionserver.kerberos.principal` | Kerberos: el principal de los region servers, por defecto el de los masters |
| `hbase.rpc.protection` | `authentication` (por defecto), `integrity` o `privacy`, como el del clúster |
| `hbase.rpc.timeout` | Tiempo de espera de RPC, `30000` por defecto, rango `1..600000` |

Un servicio es exactamente lo que dicen sus ajustes: no se lee ningún `hbase-site.xml` del servidor GrantForge. Los ajustes de Kerberos se rechazan con autenticación simple; Kerberos necesita una contraseña o un keytab. Las búsquedas se rinden tras unos pocos intentos en lugar de los minutos de HBase.

### Kerberos

Con `kerberos`, GrantForge inicia la sesión del principal mediante el inicio de sesión propio de Hadoop, con el keytab o la contraseña, conserva la sesión y la renueva desde el keytab; nada más en el servidor GrantForge necesita ajustes de Kerberos. Los reinos cuyos KDC se introducen en `kerberos.kdc` se declaran para todo el servidor GrantForge, igual que hacen los plug-ins de HDFS y Hive, de modo que varios servicios pueden usar reinos distintos a la vez.

Un clúster cierra la conexión de un cliente que inicia sesión de otra forma que la esperada, sin decir por qué. El plug-in lo notifica como un inicio de sesión rechazado y remite a `hbase.security.authentication` y `hbase.rpc.protection`, que deben coincidir con los del clúster.

### Qué ve el usuario de búsqueda

Cuando el [agente de HBase](/es/external/hbase-agent/) aplica las políticas, los listados omiten los espacios de nombres y tablas que el usuario de búsqueda no puede usar: dale una política que permita `read` en el espacio de nombres `*`, o nómbralo en `hbase.superuser` del clúster.

## Recursos y tipos de acceso

| Nivel | Coincide con | Tipos de acceso |
| --- | --- | --- |
| `global` | Su único valor, `*` | `admin`: operaciones sobre todo el clúster, como el balanceador, las cuotas y la replicación |
| `namespace` | Comodines, con mayúsculas y minúsculas | `read`, `write`, `create`, `admin` |
| `table` | Comodines, con mayúsculas y minúsculas; bajo un espacio de nombres, nombrada sin él | `read`, `write`, `create`, `admin` |
| `column_family` | Comodines, con mayúsculas y minúsculas; bajo una tabla | `read`, `write` |
| `column` | Comodines, con mayúsculas y minúsculas; bajo una familia de columnas; nombrada como texto UTF-8 | `read`, `write` |

Los tipos de acceso son los de HBase: `read`, `write`, `create` y `admin`. El `exec` de HBase, para los endpoints de coprocesadores, no se ofrece, porque el agente no puede comprobar esas llamadas en todas las versiones de HBase.

Como en HBase, un permiso cubre todo lo que hay en su ámbito: `read` sobre el espacio de nombres `sales` lee todas sus tablas, `write` sobre una familia de columnas escribe todas sus columnas. Una solicitud se decide en cada ámbito que nombra, y una denegación en cualquiera de ellos la rechaza; así, una política puede permitir una tabla y denegar una de sus columnas. El ámbito global es el propio clúster, no todas las tablas; las políticas sobre el espacio de nombres `*` cubren todas las tablas.

En los valores de las políticas, `*` coincide con cualquier número de caracteres y `?` con uno. El editor de políticas sugiere espacios de nombres, tablas de los espacios de nombres que nombra una política y familias de columnas de sus tablas; las columnas contienen datos y no se sugieren.

## Ejemplo de políticas

Supón que el espacio de nombres `sales` tiene una tabla `orders` con las familias de columnas `d`, que contiene `name` y `ssn`, y `meta`. Crea estas políticas de acceso habilitadas en el mismo servicio de datos:

| Recursos | Regla |
| --- | --- |
| `namespace=sales` | Permitir `read` al usuario `alice` |
| `namespace=sales`, `table=orders`, `column_family=d` | Permitir `read` al usuario `bob` |
| `namespace=sales`, `table=orders` | Permitir `read` al grupo `auditors` |
| `namespace=sales`, `table=orders`, `column_family=d`, `column=ssn` | Denegar `read` al grupo `auditors` |
| `namespace=analytics` | Permitir `create` y `admin` al usuario `dave` |

Cuando el agente aplica la nueva instantánea, `alice` lee todas las tablas de `sales`; `bob` lee `orders` y solo obtiene las columnas de `d`, y al listar tablas solo ve `orders`; los miembros de `auditors` leen todo `orders` salvo `d:ssn`; `dave` crea el espacio de nombres `analytics` y sus tablas, las administra, y se le deniegan tablas en otros lugares.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Para aplicarlas, cada master y region server necesita el agente de HBase. Consulta el [agente de HBase](/es/external/hbase-agent/) para su despliegue y lo que comprueba.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de HBase](/es/external/hbase-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
