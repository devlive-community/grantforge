---
title: Trino
description: Instalar y configurar el plug-in de servicio de Trino, iniciar sesión con contraseña, token web JSON o Kerberos sobre TLS, buscar catálogos, esquemas, tablas, columnas y propiedades de sesión, y gestionar políticas de acceso, filtrado de filas y enmascaramiento en los términos de Trino.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Trino busca catálogos, esquemas, tablas, columnas y propiedades de sesión, y gestiona políticas de acceso, filtrado de filas y enmascaramiento sobre lo que Trino comprueba: datos, rutinas, propiedades de sesión, usuarios y la información del sistema. Su ID de plug-in y su tipo de servicio son ambos `trino`. El plug-in de servicio se ejecuta en el servidor GrantForge; el [agente de Trino](/es/external/trino-agent/), instalado en el coordinator, aplica las políticas.

Las pruebas de integración con Testcontainers del repositorio cubren Trino `470`, `476`, `479` y `484`, y en `470` y `484` HTTPS con un archivo de contraseñas y tokens web JSON, además de Kerberos. Esto no certifica todas las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/trino`. Confirme que `trino` está habilitado en **Administración de la plataforma → Plug-ins**; vuelva a escanear el directorio de plug-ins tras actualizarlo. El plug-in habla con el coordinator mediante el controlador JDBC de Trino (`trino-jdbc` 484), en un cargador de clases propio, que habla con todas las versiones de Trino anteriores.

## Añadir un servicio de datos

1. Abra **Permisos de datos → Servicios de datos**, añada un servicio y seleccione Trino (`trino`).
2. Introduzca la URL JDBC del coordinator, por ejemplo `jdbc:trino://trino.example.com:8443`.
3. Elija cómo inicia sesión el usuario de búsqueda, introdúzcalo, pruebe la conexión y guarde el servicio. La prueba inicia sesión y describe el coordinator: su versión y cuántos catálogos ve el usuario.

Los ajustes usan los nombres del controlador, así que se leen como la documentación JDBC de Trino. El usuario de búsqueda permite a GrantForge descubrir recursos; es distinto de los usuarios que consultan. Una prueba de conexión correcta no significa que las consultas ya estén sujetas a políticas.

| Ajuste | Propósito |
| --- | --- |
| `jdbc.url` | Obligatorio: `jdbc:trino://host:port`, opcionalmente con un catálogo y un esquema y con propiedades del controlador que no sean credenciales, como `?SSL=true` |
| `authentication` | `none` (por defecto): solo el nombre de usuario; `password`; `jwt`: un token web JSON; `kerberos`: un principal con keytab o contraseña |
| `user` | Usuario de búsqueda obligatorio; con Kerberos, el principal completo, como `grantforge@EXAMPLE.COM` |
| `password` | La contraseña del usuario, o con Kerberos la del principal si no se da un keytab; se guarda cifrada |
| `accessToken` | `jwt`: el token que acepta el coordinator; se guarda cifrado |
| `keytab` | Kerberos: ruta del keytab del principal, legible en el **servidor GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del realm del principal, `host[:port]` separados por comas; vacío usa el `krb5.conf` del servidor |
| `KerberosRemoteServiceName` | Kerberos: la primera parte del principal del coordinator, por defecto `trino` |
| `KerberosUseCanonicalHostname` | Kerberos: si el principal del coordinator toma el nombre canónico del host de la URL, por defecto `true`; desactívelo tras un balanceador de carga o alias cuyo nombre lleve el principal |
| `SSL` | Si se habla TLS, por defecto `false`; cualquier inicio de sesión salvo `none` lo necesita, ya que Trino solo inicia sesión de usuarios sobre TLS |
| `SSLTrustStorePath`, `SSLTrustStorePassword`, `SSLTrustStoreType` | TLS: el almacén de confianza que verifica el coordinator, su contraseña y su tipo (`JKS` o `PKCS12`); vacío confía en lo que confía Java |
| `SSLVerification` | TLS: `FULL` (por defecto) comprueba el certificado y el nombre de host, `CA` solo el certificado, `NONE` nada y no admite almacén de confianza |
| `timeout` | Tiempo de espera de las peticiones en milisegundos, por defecto `30000`, rango `1..600000` |

El servicio es exactamente lo que dicen sus campos. La URL no puede llevar credenciales ni nada que inicie sesión de otro modo: `user`, `password`, `accessToken`, `sessionUser`, `extraCredentials`, la autenticación externa y OAuth 2.0, los archivos y la delegación de Kerberos y las contraseñas de almacenes de claves y de confianza se rechazan allí. También se rechazan los campos de otro inicio de sesión, como un token con autenticación por contraseña.

No se habla con un coordinator cuyo certificado no puede verificarse: la prueba lo informa como inalcanzable. Un inicio de sesión rechazado, la falta de permisos y un coordinator inalcanzable se informan como tales.

### Kerberos

Con `kerberos`, GrantForge inicia la sesión del principal, desde el keytab o con la contraseña, y entrega al controlador la credencial de ese inicio de sesión; nada más en el servidor GrantForge necesita ajustes de Kerberos. Los realms cuyos KDC se introducen en `kerberos.kdc` se declaran para todo el servidor GrantForge, igual que hacen los plug-ins de HDFS, Hive, Kafka y HBase, de modo que varios servicios pueden usar realms distintos a la vez. El usuario de sesión es el nombre del principal antes del realm, como Trino asigna los principales por defecto.

### Qué ve el usuario de búsqueda

Cuando el [agente de Trino](/es/external/trino-agent/) aplica las políticas, los listados omiten lo que el usuario de búsqueda no puede usar: dele una política que permita `select` en el catálogo `*`, o en los catálogos cuyos recursos deba sugerir el editor de políticas.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Tipos de acceso |
| --- | --- | --- |
| `catalog` | Comodines, sin mayúsculas | `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `all` |
| `schema` | Comodines, sin mayúsculas; bajo un catálogo | Como `catalog` |
| `table` | Comodines, sin mayúsculas; bajo un esquema; tablas, vistas y vistas materializadas | Como `catalog` |
| `column` | Comodines, sin mayúsculas; bajo una tabla | `select`, `update` |
| `function` | Comodines, sin mayúsculas; bajo un esquema | `execute`, `create_function`, `drop_function` |
| `procedure` | Comodines, sin mayúsculas; bajo un esquema | `execute` |
| `session_property` | Comodines, sin mayúsculas; bajo un catálogo, nombrada sin él | `set` |
| `system_session_property` | Comodines, sin mayúsculas | `set` |
| `user` | Comodines, con mayúsculas | `impersonate`, `view_query`, `kill_query` |
| `system_information` | Su único valor, `*` | `read`, `write` |

`all` implica `select`, `insert`, `update`, `delete`, `create`, `drop` y `alter`.

Un permiso sobre un catálogo, esquema o tabla cubre todo lo que contiene: `select` en el esquema `sales` lee cada una de sus tablas, `create` en un catálogo crea esquemas y tablas en él. Una comprobación se decide en cada ámbito que nombra, y una denegación en cualquiera de ellos la rechaza; así, una política puede permitir un esquema y denegar una columna de una de sus tablas. Las funciones, procedimientos y propiedades de sesión se conceden nombrándolos, con `*` para todos los de un esquema o catálogo, y las funciones tienen sus propios tipos de acceso, de modo que una concesión sobre rutinas nunca vale como una sobre tablas.

En los valores de política, `*` coincide con cualquier número de caracteres y `?` con uno. El editor de políticas sugiere catálogos, esquemas dentro de los catálogos que nombra una política, tablas dentro de sus esquemas, columnas dentro de sus tablas y propiedades de sesión de sus catálogos y del sistema; no se sugieren funciones, procedimientos ni usuarios.

## Filtros de filas y máscaras

Las políticas de filtrado de filas en una tabla, y de enmascaramiento en una columna, son SQL de Trino, que el coordinator aplica a cada consulta que las lee. Un filtro de filas es una condición sobre las columnas de la tabla, como `region = 'EU'`; los nombres que contiene se resuelven en el catálogo y el esquema de la tabla.

| Tipo de máscara | Muestra |
| --- | --- |
| `mask` | Las letras como `X` o `x`, los dígitos como `n` |
| `mask_show_last_4`, `mask_show_first_4` | Solo los cuatro últimos o primeros caracteres |
| `mask_hash` | El SHA-256 del valor, en hexadecimal |
| `mask_null` | `NULL` |
| `mask_none` | El valor, sin enmascarar |
| `mask_date_show_year` | Solo el año de una fecha o marca de tiempo |
| `custom` | Una expresión SQL de Trino de la política, con `{col}` para la columna |

El agente ajusta cada máscara al tipo de la columna, para que una máscara nunca rompa la consulta que lee la columna: vea el [agente de Trino](/es/external/trino-agent/).

## Ejemplo de políticas

Suponga que el catálogo `hive` tiene un esquema `sales` con las tablas `orders`, cuyas columnas incluyen `region`, `customer` y `card`, y `returns`. Cree estas políticas habilitadas en el mismo servicio de datos:

| Política | Recursos | Regla |
| --- | --- | --- |
| Acceso | `catalog=hive`, `schema=sales` | Permitir `select` al grupo `analysts` |
| Acceso | `catalog=hive`, `schema=sales`, `table=orders`, `column=card` | Denegar `select` al grupo `analysts` |
| Acceso | `catalog=hive`, `schema=sales`, `table=returns` | Permitir `insert` al usuario `alice` |
| Filtro de filas | `catalog=hive`, `schema=sales`, `table=orders` | `region = 'EU'` para el usuario `alice` |
| Enmascaramiento | `catalog=hive`, `schema=sales`, `table=orders`, `column=customer` | `mask_hash` para el grupo `analysts` |
| Acceso | `catalog=system`, `schema=runtime` | Permitir `select` al usuario `alice` |
| Acceso | `user=bob` | Permitir `view_query` al usuario `alice` |

Cuando el agente aplica la nueva instantánea, los miembros de `analysts` leen todas las tablas de `sales` salvo `orders.card`, y ven `customer` con hash; `alice` solo ve los pedidos europeos, escribe en `returns` y ve las consultas de Bob en `system.runtime.queries` junto a las suyas.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y busca recursos. Aplicarlas requiere el agente de Trino en el coordinator. Vea el [agente de Trino](/es/external/trino-agent/) para su despliegue y lo que comprueba.

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de Trino](/es/external/trino-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
