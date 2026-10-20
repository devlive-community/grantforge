---
title: Apache Hadoop YARN
description: Instalar y configurar el plug-in de servicio de YARN, conectar con un ResourceManager o un par de alta disponibilidad, iniciar sesión en modo simple o con Kerberos, buscar colas y nombres de aplicaciones y gestionar quién puede enviar qué aplicaciones y a dónde.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de YARN busca las colas y los nombres de las aplicaciones que contienen, y gestiona políticas de acceso sobre lo que YARN decide: enviar aplicaciones a colas, administrar colas y administrar el clúster. El ID del plug-in y el tipo de servicio son ambos `yarn`. El plug-in de servicio se ejecuta en el servidor de GrantForge; el [agente de YARN](/es/external/yarn-agent/) instalado en los ResourceManagers aplica las políticas.

Las pruebas de integración con Testcontainers del repositorio cubren los ResourceManagers de Hadoop `3.2.4`, construido a partir de la versión de Apache verificada con SHA-512, `3.3.6`, `3.4.3` y `3.5.0`, además de un ResourceManager con Kerberos de `3.4.3`; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/yarn`. Compruebe en **Administración de la plataforma → Plug-ins** que `yarn` está habilitado; vuelva a escanear el directorio de plug-ins tras una actualización. El plug-in habla con el ResourceManager mediante el propio cliente de YARN de Hadoop, sobre el cliente sombreado de Hadoop 3.5.0, en su propio cargador de clases.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y elija Apache Hadoop YARN (`yarn`).
2. Introduzca la dirección de cliente del ResourceManager, o la de cada ResourceManager de un clúster de alta disponibilidad.
3. Elija cómo inicia sesión el usuario de búsqueda, introdúzcalo, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el clúster: cuántos NodeManagers y cuántas colas tiene.

Los ajustes llevan los nombres propios de Hadoop y pueden copiarse de un `yarn-site.xml`. El usuario de búsqueda permite a GrantForge descubrir recursos; es independiente de los usuarios que envían aplicaciones. Una prueba de conexión correcta no significa que los envíos ya estén sujetos a políticas.

| Ajuste | Propósito |
| --- | --- |
| `yarn.resourcemanager.address` | Obligatorio: el ResourceManager, `host` o `host:port`, puerto predeterminado `8032`; varios, separados por comas, para un clúster de alta disponibilidad, entre los que el cliente conmuta |
| `hadoop.security.authentication` | `simple` (predeterminado): un nombre de usuario; `kerberos`: un principal con keytab o contraseña |
| `username` | Usuario de búsqueda obligatorio; con Kerberos, el principal completo, por ejemplo `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: la contraseña del principal, si no se indica keytab; se guarda cifrada |
| `keytab` | Kerberos: ruta del keytab del principal, legible en el **servidor de GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del realm del principal, `host[:port]`, separados por comas; vacío usa el `krb5.conf` del servidor |
| `yarn.resourcemanager.principal` | Kerberos: el principal de los ResourceManagers, predeterminado `yarn/_HOST@` y el realm del principal de búsqueda |
| `hadoop.rpc.protection` | `authentication` (predeterminado), `integrity` o `privacy`, como en el clúster |
| `timeout` | Cuánto puede durar una petición, predeterminado `30000`, rango `1..600000` milisegundos; el propio cliente de YARN reintentaría durante un cuarto de hora |

El servicio es exactamente lo que dicen sus ajustes: no se lee ningún `core-site.xml` ni `yarn-site.xml` del servidor de GrantForge. Los ajustes de Kerberos se rechazan con autenticación simple; Kerberos necesita una contraseña o un keytab. Un ResourceManager que rechaza el inicio de sesión, por ejemplo porque espera Kerberos, se notifica como inicio de sesión rechazado.

### Kerberos

Con `kerberos`, GrantForge inicia la sesión del principal mediante el propio inicio de sesión de Hadoop, desde el keytab o con la contraseña, conserva esa sesión y la renueva desde el keytab; nada más en el servidor de GrantForge necesita ajustes de Kerberos. Los realms cuyos KDC figuran en `kerberos.kdc` se declaran para todo el servidor de GrantForge, como hacen los plug-ins de HDFS, Hive, Kafka, HBase y Trino, de modo que varios servicios pueden usar realms distintos a la vez.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `queue` | Comodín, distingue mayúsculas; la ruta de la cola desde la raíz, por ejemplo `root.sales.etl` | `submit_app`, `admin_queue` |
| `application` | Comodín, distingue mayúsculas; bajo una cola; el nombre de la aplicación | `submit_app` |
| `cluster` | Su único valor, `*` | `admin`: la administración de YARN, por ejemplo recargar colas, nodos y etiquetas de nodo |

`admin_queue` implica `submit_app`, ya que los administradores de una cola pueden enviar a ella.

Como en YARN, un permiso sobre una cola cubre las colas que hay debajo: `submit_app` en `root.sales` envía a `root.sales.etl`. Una comprobación se decide en cada cola de la ruta, desde la raíz, y una denegación en cualquiera de ellas la rechaza; así, una política puede permitir `root.sales` y denegar `root.sales.adhoc`. Bajo una cola, un nivel de aplicación indica a qué aplicaciones se aplica un permiso: `submit_app` en `root.hr` con la aplicación `payroll-*` solo acepta aplicaciones cuyo nombre empieza por `payroll-`, en `root.hr` y las colas de debajo.

En los valores de política, `*` coincide con cualquier número de caracteres y `?` con uno. El editor de políticas sugiere colas por su ruta, o por su propio nombre si es lo que se escribe, sea cual sea el planificador del clúster, así como los nombres de las aplicaciones que el ResourceManager aún recuerda en las colas que nombra una política.

### Direcciones de cliente

Un elemento de política puede aplicarse solo a algunos clientes: su condición **Direcciones de cliente** (`ip-range`) admite direcciones, rangos como `10.1.0.0/16` y nombres de host, como las listas de hosts de Hadoop. Detrás de un proxy, por ejemplo el proxy web de YARN o un router, el cliente es la primera dirección que reenviaron los proxys. Son preferibles las direcciones y los rangos a los nombres de host, que cuestan una resolución de nombre del cliente. YARN pregunta por su administración sin la dirección del cliente, así que la condición nunca se cumple en `cluster`.

## Ejemplo de políticas

Suponga que el clúster ejecuta las colas `root.sales.etl`, `root.sales.adhoc` y `root.hr`. Cree estas políticas de acceso habilitadas en el mismo servicio de datos:

| Recursos | Regla |
| --- | --- |
| `queue=root.sales` | Permitir `submit_app` al grupo `analysts` |
| `queue=root.sales.adhoc` | Denegar `submit_app` al usuario `alice` |
| `queue=root.hr`, `application=payroll-*` | Permitir `submit_app` al usuario `bob` |
| `queue=root.sales` | Permitir `admin_queue` al usuario `carol` |
| `queue=root` | Permitir `submit_app` al grupo `etl-robots`, desde `10.0.0.0/8` |
| `cluster=*` | Permitir `admin` al grupo `yarn-admins` |

Cuando el agente aplica la nueva instantánea, los miembros de `analysts` envían a `root.sales.etl` y `root.sales.adhoc`, pero `alice` solo a `root.sales.etl`; `bob` envía aplicaciones de nómina a `root.hr` y nada más; `carol` envía a las colas de ventas y detiene sus aplicaciones; los miembros de `etl-robots` envían a cualquier cola, pero solo desde la red interna; y los miembros de `yarn-admins` recargan las colas.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca los recursos. Para aplicarlas hace falta el agente de YARN en cada ResourceManager. El despliegue y las comprobaciones se describen en el [agente de YARN](/es/external/yarn-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de YARN](/es/external/yarn-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
