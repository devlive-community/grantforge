---
title: Apache Kafka
description: Instala y configura el plug-in de servicio Kafka, inicia sesión con PLAIN, SCRAM o Kerberos en texto plano o TLS, busca topics, grupos, IDs transaccionales y usuarios, y gestiona políticas de acceso en los términos de Kafka.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Kafka busca topics, grupos de consumidores, IDs transaccionales, usuarios SCRAM y tokens de delegación, y gestiona políticas de acceso en los términos de Kafka: sus tipos de recurso son los niveles de recurso y sus operaciones ACL los tipos de acceso. Su ID de plug-in y su tipo de servicio son ambos `kafka`. El plug-in de servicio se ejecuta en el servidor GrantForge; el [agente de Kafka](/es/external/kafka-agent/), instalado en los brokers y controladores, aplica las políticas.

Las pruebas de integración con Testcontainers del repositorio cubren Kafka `3.7.2`, `3.9.2`, `4.1.2` y `4.3.1` con inicios de sesión SASL PLAIN y SCRAM y el autorizador propio de Kafka, además de Kerberos (keytab y contraseña) y TLS contra `4.3.1`; esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/kafka`. Comprueba que `kafka` está habilitado en **Administración de la plataforma → Plug-ins**; vuelve a escanear el directorio de plug-ins después de actualizarlo. El plug-in habla con los brokers mediante el cliente Admin de Kafka (`kafka-clients` 4.3.1), en un cargador de clases propio.

## Añadir un servicio de datos

1. Abre **Permisos de datos → Servicios de datos**, añade un servicio y selecciona Apache Kafka (`kafka`).
2. Introduce los servidores de arranque y cómo está protegido el listener de los brokers.
3. Introduce el usuario de búsqueda, prueba la conexión y guarda el servicio. La prueba inicia sesión y describe el clúster: su ID y cuántos brokers tiene.

Los ajustes usan los nombres de los clientes de Kafka, así que pueden copiarse de una configuración de cliente. El usuario de búsqueda permite a GrantForge descubrir recursos; es distinto de los clientes que producen y consumen. Una prueba de conexión correcta no significa que las solicitudes ya estén sujetas a las políticas.

| Ajuste | Propósito |
| --- | --- |
| `bootstrap.servers` | Brokers de inicio obligatorios, `host:puerto` separados por comas, como `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT` (por defecto), `SSL`, `SASL_PLAINTEXT` o `SASL_SSL` |
| `sasl.mechanism` | Solo protocolos SASL: `PLAIN`, `SCRAM-SHA-256` o `SCRAM-SHA-512` con contraseña, `GSSAPI` para Kerberos |
| `username` | SASL: el usuario de búsqueda; con Kerberos, el principal completo, como `grantforge@EXAMPLE.COM` |
| `password` | La contraseña del usuario o, sin keytab, la contraseña Kerberos del principal; se guarda cifrada |
| `keytab` | Kerberos: ruta del keytab del principal, legible en el **servidor GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del reino del principal, `host[:puerto]` separados por comas; vacío usa el `krb5.conf` del servidor |
| `sasl.kerberos.service.name` | Kerberos: la primera parte de los principales de los brokers, `kafka` por defecto |
| `ssl.truststore.location`, `ssl.truststore.password`, `ssl.truststore.type` | TLS: el almacén de confianza que verifica los brokers (`JKS`, `PKCS12` o `PEM`); vacío confía en lo que confía Java |
| `ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password` | TLS: el certificado de GrantForge, para brokers que piden uno a los clientes |
| `ssl.verify.hostname` | TLS: si los certificados de los brokers deben nombrar los hosts a los que se conecta, `true` por defecto |
| `request.timeout.ms` | Tiempo de espera de las solicitudes, `30000` por defecto, rango `1..600000` |

Los ajustes SASL se rechazan con `PLAINTEXT` y `SSL`, y los de TLS con `PLAINTEXT` y `SASL_PLAINTEXT`. `PLAIN` y SCRAM necesitan una contraseña; `GSSAPI` necesita una contraseña o un keytab.

### Kerberos

Con `GSSAPI`, GrantForge inicia la sesión del principal con el inicio de sesión Kerberos de Kafka, desde el keytab o con la contraseña, que GrantForge le entrega directamente; nada más en el servidor GrantForge necesita ajustes de Kerberos. Los reinos cuyos KDC se introducen en `kerberos.kdc` se declaran para todo el servidor GrantForge, igual que hacen los plug-ins de HDFS y Hive, de modo que varios servicios pueden usar reinos distintos a la vez.

### Qué ve el usuario de búsqueda

Kafka solo muestra a un usuario lo que puede describir, así que el usuario de búsqueda necesita `describe` sobre los topics, grupos e IDs transaccionales que debe sugerir, y `describe` sobre el clúster para los usuarios SCRAM. Kafka omite los topics y grupos que no puede describir, de modo que las sugerencias quedan vacías en lugar de rechazarse. Los topics internos, como `__consumer_offsets`, se sugieren cuando el texto escrito empieza por `_`. Un clúster sin tokens de delegación, o sin SCRAM, no sugiere ninguno.

Cuando el [agente de Kafka](/es/external/kafka-agent/) aplica las políticas, estas deciden también lo que el usuario de búsqueda puede describir: dale una política que permita `describe`, o nómbralo en `super.users` de los brokers.

## Recursos y tipos de acceso

| Nivel | Recurso de Kafka | Tipos de acceso |
| --- | --- | --- |
| `topic` | Topics | `read`, `write`, `create`, `delete`, `alter`, `describe`, `describe_configs`, `alter_configs`, `all` |
| `group` | Grupos de consumidores | `read`, `delete`, `describe`, `all` |
| `transactional_id` | IDs transaccionales | `write`, `describe`, `all` |
| `cluster` | El clúster, siempre llamado `kafka-cluster` | `create`, `alter`, `describe`, `cluster_action`, `describe_configs`, `alter_configs`, `idempotent_write`, `all` |
| `delegation_token` | Tokens de delegación, por ID de token | `describe`, `all` |
| `user` | Usuarios cuyos tokens de delegación se crean o describen | `create_tokens`, `describe_tokens`, `all` |

Cada nivel es independiente; una política nombra uno. Los valores distinguen mayúsculas y minúsculas: `*` coincide con cualquier número de caracteres y `?` con uno. Los tipos de acceso implican otros como las operaciones ACL de Kafka: `read`, `write`, `delete` y `alter` implican `describe`, `alter_configs` implica `describe_configs`, y `all` implica todos los demás.

Las implicaciones cuentan también en las denegaciones: una política que deniega `write` en un topic deniega también el `describe` que `write` implica, de modo que el usuario deja de ver el topic. Las ACL propias de Kafka solo aplican implicaciones a las operaciones permitidas.

El editor de políticas sugiere nombres del clúster por prefijo; para `cluster` sugiere `kafka-cluster`.

## Ejemplo de políticas

Supón que los productores del usuario `alice` escriben en topics cuyo nombre empieza por `orders`, y que el grupo `analysts` los lee con el grupo de consumidores `billing`. Crea tres políticas de acceso habilitadas en el mismo servicio de datos:

| Recursos | Regla |
| --- | --- |
| `topic=orders*` | Permitir `write` al usuario `alice` |
| `topic=orders*` | Permitir `read` al grupo `analysts` |
| `group=billing` | Permitir `read` al grupo `analysts` |

Cuando el agente aplica la nueva instantánea, `alice` puede producir en `orders-eu`, también con un productor idempotente y sin `idempotent_write` en el clúster; los miembros de `analysts` lo consumen como `billing`; los demás topics y grupos se lo deniegan a ambos. `kafka-acls --list` muestra las tres políticas como ACL con prefijo y literales.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Para aplicarlas, cada broker y controlador necesita el agente de Kafka. Consulta el [agente de Kafka](/es/external/kafka-agent/) para su despliegue y lo que comprueba.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de Kafka](/es/external/kafka-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
