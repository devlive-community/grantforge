---
title: Apache Pulsar
description: Instalar y configurar el plug-in de servicio de Pulsar, conectar con uno o varios brokers, iniciar sesión con un token, una contraseña, OAuth 2.0 o un certificado de cliente, buscar tenants, namespaces, topics y suscripciones y gestionar quién puede producir, consumir y administrar qué.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Pulsar busca tenants, namespaces, topics, suscripciones y clústeres, y gestiona políticas de acceso sobre lo que Pulsar decide: producir, consumir, functions, sources, sinks y packages, y administrar topics, namespaces, tenants y clústeres. El ID del plug-in y el tipo de servicio son ambos `pulsar`. El plug-in de servicio se ejecuta en el servidor de GrantForge; el [agente de Pulsar](/es/external/pulsar-agent/) instalado en los brokers aplica las políticas.

Las pruebas de integración con Testcontainers del repositorio cubren los brokers de Pulsar `3.0.17`, `3.3.9`, `4.0.14` y `4.2.5`; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/pulsar`. Compruebe en **Administración de la plataforma → Plug-ins** que `pulsar` está habilitado; vuelva a escanear el directorio de plug-ins tras una actualización. El plug-in habla con el servicio web de los brokers mediante el propio cliente de administración de Pulsar 4.2.5, en su propio cargador de clases.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y elija Apache Pulsar (`pulsar`).
2. Introduzca la URL del servicio web de un broker o de varios.
3. Elija cómo inicia sesión el rol de búsqueda, introduzca sus credenciales, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el clúster: sus clústeres y cuántos tenants tiene.

Los ajustes llevan los nombres del propio `client.conf` de Pulsar y pueden copiarse de él. El rol de búsqueda permite a GrantForge descubrir recursos; es independiente de los roles que producen y consumen. Una prueba de conexión correcta no significa que el acceso ya esté sujeto a políticas.

| Ajuste | Propósito |
| --- | --- |
| `webServiceUrl` | Obligatorio: el servicio web de un broker, `http://host:port` o `https://host:port`, puerto predeterminado `8080` u `8443`; varios brokers separados por comas, por ejemplo `http://broker1:8080,broker2:8080` |
| `authentication` | `none` (predeterminado), `token`, `basic`, `oauth2` o `tls` |
| `token` | token: el token del rol de búsqueda; se guarda cifrado |
| `username`, `password` | basic: el usuario y la contraseña del rol de búsqueda; la contraseña se guarda cifrada |
| `issuerUrl`, `privateKey`, `audience`, `scope` | oauth2: el servidor de autorización, la ruta del archivo JSON de credenciales con `client_id` y `client_secret` en el **servidor de GrantForge**, y la audiencia y el alcance, si los brokers los esperan |
| `tlsCertificateFilePath`, `tlsKeyFilePath` | tls: rutas del certificado de cliente y su clave privada, PEM, en el **servidor de GrantForge** |
| `tlsTrustCertsFilePath` | https: los certificados de CA con que se comprueban los de los brokers, PEM; vacío usa los de la JVM |
| `tlsEnableHostnameVerification` | https: si el certificado de un broker debe nombrar su host, predeterminado `true` |
| `timeout` | Cuánto puede durar una petición, predeterminado `30000`, rango `1..600000` milisegundos |

Los ajustes de un modo de inicio de sesión se rechazan con otro, de modo que un servicio dice exactamente cómo inicia sesión. Un inicio de sesión rechazado, un rol que no puede ver algo y un broker inalcanzable, también detrás de un proxy que responde por él, se notifican como tales.

### Qué ve el rol de búsqueda

Pulsar solo deja a sus superusuarios listar clústeres y tenants. Nombre el rol de búsqueda en `superUserRoles` de los brokers o, una vez instalado el agente, permítale `admin` en el clúster, lo que también lo convierte en superusuario. Los namespaces, topics y suscripciones que el rol de búsqueda no puede ver quedan fuera de las sugerencias.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `tenant` | Comodín, distingue mayúsculas | Todos salvo `admin` |
| `namespace` | Comodín, distingue mayúsculas; bajo un tenant; el nombre propio del namespace, por ejemplo `orders` | `produce`, `consume`, `functions`, `sources`, `sinks`, `packages`, `admin_topic`, `admin_namespace` |
| `topic` | Comodín, distingue mayúsculas; bajo un namespace; el nombre propio del topic, por ejemplo `payments` | `produce`, `consume`, `admin_topic` |
| `subscription` | Comodín, distingue mayúsculas; bajo un topic; el nombre de la suscripción | `consume` |
| `cluster` | Comodín, distingue mayúsculas; el nombre del clúster, por ejemplo `standalone` | `admin`: el acceso de superusuario de Pulsar |

| Tipo de acceso | Implica |
| --- | --- |
| `produce`, `consume`, `functions`, `sources`, `sinks`, `packages` | — |
| `admin_topic`: terminar, compactar, descargar al almacenamiento por niveles, liberar y truncar topics, y cambiar sus políticas | `produce`, `consume` |
| `admin_namespace`: crear y eliminar topics, y cambiar las políticas del namespace | `admin_topic` |
| `admin_tenant`: crear, listar y eliminar namespaces | `admin_namespace`, `functions`, `sources`, `sinks`, `packages` |
| `admin`: todo lo que pueden los superusuarios de Pulsar | `admin_tenant` |

Como con los propios permisos de Pulsar, un permiso sobre un tenant o un namespace cubre lo que contiene: `produce` en el namespace `orders` del tenant `sales` produce en cada uno de sus topics. Una comprobación se decide sucesivamente en el tenant, el namespace, el topic y la suscripción, y una denegación en cualquiera de ellos la rechaza; así, una política puede permitir un namespace y denegar uno de sus topics. Bajo un topic, un nivel de suscripción indica a través de qué suscripciones puede consumir un rol: `consume` en el topic `*` con la suscripción `billing-*` deja a un rol consumir cada topic del namespace, pero solo mediante suscripciones cuyo nombre empieza por `billing-`.

Un topic se decide por su nombre propio, sin su dominio, así que un topic persistente y uno no persistente del mismo nombre comparten sus políticas; una partición de un topic particionado se decide como el topic particionado. En los valores de política, `*` coincide con cualquier número de caracteres y `?` con uno. El editor de políticas sugiere los clústeres, los tenants, y los namespaces, topics y suscripciones de lo que nombra una política.

### Direcciones de cliente

Un elemento de política puede aplicarse solo a algunos clientes: su condición **Direcciones de cliente** (`ip-range`) admite direcciones y rangos como `10.1.0.0/16` o `2001:db8::/32`. Los nombres de host no se resuelven. Detrás de un proxy, el cliente es el proxy, salvo que reenvíe la dirección propia del cliente: con el protocolo HAProxy para productores y consumidores, y con `X-Forwarded-For` para el servicio web cuando los brokers confían en él.

## Ejemplo de políticas

Suponga que el tenant `sales` tiene los namespaces `orders` y `returns`. Cree estas políticas de acceso habilitadas en el mismo servicio de datos:

| Recursos | Regla |
| --- | --- |
| `tenant=sales`, `namespace=orders` | Permitir `produce` al grupo `analysts` |
| `tenant=sales`, `namespace=orders`, `topic=audit` | Denegar `produce` al usuario `alice` |
| `tenant=sales`, `namespace=orders`, `topic=*`, `subscription=billing-*` | Permitir `consume` al usuario `alice` |
| `tenant=sales` | Permitir `admin_tenant` al usuario `bob` |
| `tenant=public` | Permitir `produce` al grupo `ingest`, desde `10.0.0.0/8` |
| `cluster=standalone` | Permitir `admin` al grupo `pulsar-admins` |

Cuando el agente aplica la nueva instantánea, los miembros de `analysts` producen en cada topic de `sales/orders`, `alice` en todos salvo `audit`; `alice` consume allí solo mediante las suscripciones `billing-`; `bob` crea y administra namespaces y topics en `sales`; los miembros de `ingest` producen en el tenant `public` solo desde la red interna; y los miembros de `pulsar-admins` son superusuarios de Pulsar.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca los recursos. Para aplicarlas hace falta el agente de Pulsar en cada broker. El despliegue y las comprobaciones se describen en el [agente de Pulsar](/es/external/pulsar-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de Pulsar](/es/external/pulsar-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
