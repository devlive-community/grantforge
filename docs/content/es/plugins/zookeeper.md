---
title: Apache ZooKeeper
description: Instalar y configurar el plug-in de ZooKeeper, explorar znodes y administrar políticas de acceso a rutas de znodes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Apache ZooKeeper aporta a GrantForge la conexión con ensembles, la búsqueda de rutas de znodes y la gestión de políticas. Su ID de plug-in y su tipo de servicio son ambos `zookeeper`.

## Instalación

La distribución incluye el plug-in en `plugins/zookeeper`. Comprueba en **Administración de la plataforma → Plug-ins** que `zookeeper` está activado; tras actualizarlo, vuelve a analizar el directorio de plug-ins. El plug-in usa el cliente de ZooKeeper 3.9 en su propio cargador de clases, aparte de las bibliotecas del servidor.

## Añadir un servicio de datos

1. Abre **Permisos de datos → Servicios de datos**, añade un servicio y elige ZooKeeper (`zookeeper`).
2. Introduce la cadena de conexión: los servidores del ensemble con un chroot opcional, como `zk1:2181,zk2:2181,zk3:2181/kafka`. El cliente cambia de servidor por sí solo.
3. Elige la autenticación, define el znode de búsqueda y prueba la conexión (comprueba que el znode existe y que se pueden listar sus hijos); después guarda el servicio.

| Ajuste | Uso |
| --- | --- |
| `zookeeper.connect` | Cadena de conexión obligatoria: `host[:port]` separados por comas, seguidos opcionalmente de `/chroot`; el chroot no puede terminar en `/` |
| `authentication` | `none` (predeterminado), `digest` o `kerberos` |
| `username` | Digest: el usuario digest de las ACL (sin dos puntos); Kerberos: el principal, como `grantforge@EXAMPLE.COM` |
| `password` | Contraseña del usuario digest; se guarda cifrada |
| `keytab` | Kerberos: ruta del keytab del principal en el servidor de GrantForge |
| `zookeeper.sasl.client.username` | Kerberos: primera parte de los principals de los servidores, `zookeeper` por defecto, como en `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos: principal completo de los servidores cuando no tiene esa forma, como `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | Si se usa el puerto de cliente TLS de los servidores, `false` por defecto |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | Almacén de confianza que verifica los servidores; vacío, confía en lo que confía el Java del servidor; tipo `jks`, `pkcs12` o `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | Almacén de claves con el certificado de cliente, para servidores que lo exigen |
| `session.timeout.ms` | Tiempo de sesión, `15000` por defecto, rango `2000..120000` |
| `connection.timeout.ms` | Espera solo a la conexión inicial y a la autenticación, `10000` ms por defecto, rango `1000..60000`; no limita las solicitudes síncronas posteriores |
| `request.timeout.ms` | Espera máxima para una solicitud síncrona, `8000` ms por defecto, rango `1000..60000` |
| `lookup.path` | Znode inicial de búsqueda y exploración, bajo el chroot, `/` por defecto; por ejemplo `/brokers`, con la exploración limitada a él |
| `lookup.max.entries` | Máximo de hijos leídos de un znode a la vez, `10000` por defecto, rango `1..100000` |

## Autenticación

- **none**: sin inicio de sesión; solo se leen los znodes cuya ACL está abierta a `world:anyone`.
- **digest**: inicia sesión como `username:password`, que corresponde a las identidades `digest` de las ACL de ZooKeeper. Una contraseña errónea no se rechaza al iniciar sesión: es otra identidad, y los znodes protegidos aparecen como acceso denegado.
- **kerberos**: inicia sesión por SASL con un keytab; el `krb5.conf` del servidor debe llevar al KDC. Cada principal y su keytab reciben una sección JAAS propia junto a la configuración del proceso, de modo que un mismo GrantForge llega a varios ensembles como principals distintos. Aún no se admite iniciar sesión en Kerberos solo con contraseña.

La configuración se valida al guardar: la forma de la cadena de conexión, usuario y contraseña para digest, principal y keytab para Kerberos, keytab y principal del servidor solo con Kerberos, y los rangos de los tiempos y del límite.

Con TLS desactivado, las rutas y contraseñas de los almacenes de confianza y de claves deben quedar vacías. Una contraseña de almacén requiere la ruta del almacén correspondiente. Los únicos tipos admitidos para ambos almacenes son `jks`, `pkcs12` y `pem`.

Se conservan los espacios al principio y al final de las contraseñas de los almacenes TLS. La configuración del servicio determina el modo TLS y los almacenes usados para esta conexión y sustituye los ajustes de TLS y de almacenes de ZooKeeper que ya existan en la JVM; se siguen verificando los certificados y nombres de host de los servidores.

## Explorar znodes

Elige el servicio en **Permisos de datos → Políticas** y usa **Explorar** junto a `path`:

- Los hijos se listan por páginas, por nombre; un znode con hijos propios se puede seguir abriendo.
- Cada znode muestra su ACL (`scheme:id:cdrwa`, como la escriben las herramientas de ZooKeeper), el tamaño de sus datos y su última modificación.
- Un znode cuya ACL no puede leer el usuario de búsqueda se sigue listando por su nombre; al abrirlo se indica por qué no.

Si se eliminan znodes durante la paginación, el cursor sigue avanzando desde el último nombre de hijo examinado, aunque ese nodo ya no exista. La lista de hijos no es una instantánea: las altas y eliminaciones simultáneas siguen la semántica de ZooKeeper y pueden cambiar las páginas posteriores.

Al escribir una ruta se ofrecen sugerencias; un padre inexistente no tiene ninguna y las rutas fuera de `lookup.path` se rechazan. Un znode con más hijos que `lookup.max.entries` produce un error en lugar de mostrar solo una parte.

## Aplicar políticas

Hay un único nivel de recurso, `path`, y los tipos de acceso siguen los permisos de ACL de ZooKeeper: `read`, `write`, `create`, `delete` y `admin`; las políticas de ruta admiten recursión y exclusiones.

El plug-in del servicio administra, busca y explora; aplicar las políticas requiere además el agente dentro del servidor, que las aplica a las ACL de marcador mediante un proveedor de autenticación SASL y debe desplegarse en todos los nodos del ensemble. Los pasos de despliegue, la ACL de marcador y sus limitaciones se describen en la guía del agente.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente Apache ZooKeeper](/es/external/zookeeper-agent/)
- [Desarrollo de plug-ins y tipos de servicio](/es/develop/plugins/)
