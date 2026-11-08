---
title: Redis
description: Instalar y configurar el plug-in de servicio de Redis para Redis y Valkey, conectarse a un servidor, sus réplicas o un clúster, iniciar sesión con un usuario ACL, con TLS si se desea, buscar claves, canales y nodos, y gestionar quién puede leer y escribir qué claves, usar qué canales y supervisar qué nodos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Redis busca claves, canales y nodos de despliegues de Redis y Valkey, y gestiona políticas de acceso en los términos propios de Redis: leer y escribir claves por patrón, suscribirse y publicar en canales, y supervisar cada nodo. El ID del plug-in y el tipo de servicio son ambos `redis`. El plug-in de servicio se ejecuta en el servidor de GrantForge; el [agente de Redis](/es/external/redis-agent/), que se ejecuta junto al despliegue, aplica las políticas con la ACL propia de Redis en cada nodo.

Las pruebas de integración con Testcontainers del repositorio cubren un primario con una réplica de Redis `7.2`, `7.4`, `8.2` y `8.10` y Valkey `8.1` y `9.1`, y un clúster de Redis `8.10`. Esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/redis`. Confirme en **Administración de la plataforma → Plug-ins** que `redis` está activado; vuelva a examinar tras actualizar el directorio de plug-ins. El plug-in habla con Redis mediante Jedis, en un cargador de clases propio.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y seleccione Redis (`redis`).
2. Introduzca uno o más nodos. El resto de un clúster y las réplicas de un primario se encuentran a partir de ellos.
3. Indique el usuario de búsqueda, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el despliegue: Redis o Valkey y su versión, si es un solo servidor, un primario con réplicas o un clúster, y quién inició sesión.

El usuario de búsqueda permite a GrantForge encontrar recursos y no es el usuario que lee o escribe. Que la prueba de conexión tenga éxito no significa que el acceso ya siga las políticas.

| Ajuste | Propósito |
| --- | --- |
| `redis.nodes` | Obligatorio: los nodos como `host:port`, como `redis1.example.com:6379`, separados por comas y probados en orden; el puerto es `6379` si no se indica |
| `username` | El usuario de búsqueda, un usuario ACL; vacío inicia sesión como el usuario predeterminado |
| `password` | Su contraseña, guardada cifrada |
| `tls.mode` | `disable` (predeterminado), `verify-ca` para comprobar los certificados de los servidores, o `verify-full` para comprobar también sus nombres |
| `tls.ca.file` | TLS: ruta en el **servidor de GrantForge** de los certificados CA PEM con los que se comprueban los servidores. Vacío usa los de la JVM |
| `tls.certificate.file` | TLS, para servidores que piden certificados de cliente: ruta en el **servidor de GrantForge** del certificado de cliente PEM, que puede contener también su clave |
| `tls.key.file` | Ruta en el **servidor de GrantForge** de la clave PEM del certificado, cuando tiene un archivo propio |
| `tls.key.password` | La contraseña de una clave cifrada, guardada cifrada |
| `timeout` | Cuánto pueden tardar la conexión y un comando; `10000` de forma predeterminada, en el rango `1..600000` milisegundos |

Un inicio de sesión rechazado, un usuario de búsqueda sin un permiso y nodos inalcanzables se informan como tales.

### Qué ve el usuario de búsqueda

Las claves se encuentran con `SCAN` en cada primario, unas pocas rondas como mucho para que una búsqueda siga siendo rápida haya las claves que haya; los canales son los que tienen suscriptores ahora. `SCAN` lista todas las claves sin importar los patrones de claves del usuario, así que el usuario de búsqueda no necesita permiso para leer valores:

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `key` | Comodines, distingue mayúsculas. Una clave o un patrón de claves | `read`, `write` |
| `channel` | Comodines, distingue mayúsculas. Un canal o un patrón de canales | `subscribe`, `publish` |
| `node` | Comodines, sin distinguir mayúsculas. La dirección de un nodo como `host:port` | `monitor` |

| Tipo de acceso | Qué permite Redis |
| --- | --- |
| `read` | Los comandos de `@read` sobre la clave, `%R~` en ella |
| `write` | Los comandos de `@write` sobre la clave, `%W~` en ella, que también la borran |
| `subscribe` | `SUBSCRIBE`, `PSUBSCRIBE`, `SSUBSCRIBE` sobre el canal, `&` |
| `publish` | `PUBLISH`, `SPUBLISH` sobre el canal, `&` |
| `monitor` | `INFO`, `DBSIZE`, `LATENCY LATEST`, `LATENCY HISTORY`, `MEMORY STATS` |

En los valores de las políticas, `*` coincide con cualquier texto y `?` con un carácter, como en los patrones propios de Redis. Redis concede claves y canales por patrón y no puede listarlos al agente, así que el valor de una política se concede como un patrón propio, donde las políticas deciden al usuario igual en todo él. Un patrón en el que las políticas deciden de otro modo, como `sales:*` cuando una política deniega `sales:card:*`, se retiene y se indica; nombre en su lugar claves o patrones que las políticas decidan igual. Los valores con corchetes, barras invertidas, paréntesis o espacios nunca se conceden.

Los comandos que Redis considera peligrosos, como `FLUSHALL` y `KEYS`, nunca se dan, y `SCAN` y `RANDOMKEY`, que listan claves sin importar sus patrones, solo a un usuario que puede leer todas las claves. Los patrones nombran claves en todas las bases de datos de un nodo, ya que la ACL de Redis no tiene bases de datos.

Redis no filtra nada dentro de un valor ni enmascara nada, así que este tipo de servicio no ofrece ninguna de las dos cosas.

## Ejemplo de políticas

Cree estas políticas activadas en un servicio de datos:

| Recurso | Regla |
| --- | --- |
| `key=sales:*` | Permitir `read` al grupo `analysts` |
| `key=sales:card:*` | Permitir `read` y `write` al usuario `billing` |
| `key=cart:*` | Permitir `read` y `write` al usuario `shop` |
| `channel=orders.*` | Permitir `subscribe` al grupo `analysts`; permitir `publish` al usuario `shop` |
| `node=*` | Permitir `monitor` al usuario `dashboards` |

Cuando el agente aplique la instantánea, los miembros de `analysts` leerán todas las claves `sales:` y seguirán los canales de pedidos; `billing` leerá y escribirá las claves de tarjetas, un patrón dentro del cual ninguna otra política decide distinto; `shop` mantendrá carritos y publicará eventos de pedidos; y `dashboards` verá el estado de cada nodo y ningún dato.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Para aplicarlas hace falta un agente de Redis por despliegue; consulte [Agente de Redis](/es/external/redis-agent/) para el despliegue y las comprobaciones.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de Redis](/es/external/redis-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
