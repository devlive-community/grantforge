---
title: Apache Hive
description: Instala y configura el plug-in de servicio Hive, inicia sesión con contraseña o Kerberos, consulta bases de datos, tablas, columnas y UDF, y gestiona políticas de acceso, enmascaramiento y filtrado de filas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de Hive ofrece para HiveServer2 la consulta de bases de datos, tablas, columnas y UDF permanentes, y políticas de acceso, enmascaramiento y filtrado de filas, siguiendo el modelo del servicio Hive de Apache Ranger. Su ID de plug-in y su tipo de servicio son ambos `hive`. El plug-in de servicio se ejecuta en el servidor GrantForge; el [agente de Hive](/es/external/hive-agent/), instalado en HiveServer2, aplica las políticas.

Las pruebas de integración con Testcontainers del repositorio cubren HiveServer2 `3.1.3`, `4.0.1` y `4.1.0`, incluido un inicio de sesión Kerberos en `4.0.1`; esto no certifica las demás versiones.

## Instalación

La distribución incluye el plug-in en `plugins/hive`. Comprueba en **Administración de la plataforma → Plug-ins** que `hive` está habilitado; vuelve a explorar el directorio de plug-ins tras actualizarlo. El plug-in se comunica con HiveServer2 mediante el propio controlador JDBC de Hive (`hive-jdbc` 4.0.1, standalone) en un cargador de clases propio, por lo que funciona igual con HiveServer2 3.1 y 4.x.

## Añadir un servicio de datos

1. Abre **Permisos de datos → Servicios de datos**, añade un servicio y elige Apache Hive (`hive`).
2. Introduce la URL JDBC de HiveServer2, por ejemplo `jdbc:hive2://hiveserver2.example.com:10000/default`. También funciona el descubrimiento por ZooKeeper: `jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`.
3. Elige cómo inicia sesión el usuario de consulta, introdúcelo, prueba la conexión y guarda el servicio. La prueba inicia sesión y lista las bases de datos, como hacen las consultas de recursos.

El usuario de consulta solo sirve para que GrantForge descubra recursos; es distinto de los usuarios que ejecutan consultas. Una prueba de conexión correcta no significa que las consultas ya estén sujetas a políticas.

| Ajuste | Propósito |
| --- | --- |
| `jdbc.url` | URL de HiveServer2 obligatoria `jdbc:hive2://hosts/base;variables de sesión`, con TLS (`;ssl=true;sslTrustStore=…`), el transporte HTTP o, con Kerberos, el principal del servidor (`;principal=hive/_HOST@EXAMPLE.COM`) |
| `authentication` | `password` (predeterminado): un usuario con su contraseña, para LDAP, PAM o sin autenticación; `kerberos`: un principal con keytab o contraseña |
| `username` | Usuario de consulta obligatorio; con Kerberos, el principal completo, como `grantforge@EXAMPLE.COM` |
| `password` | La contraseña del usuario o, sin keytab, la contraseña Kerberos del principal; se guarda cifrada |
| `keytab` | Kerberos: ruta de la keytab del principal, legible en el **servidor GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del reino del principal, `host[:port]` separados por comas; vacío usa el `krb5.conf` del servidor |
| `read.timeout.ms` | Tiempo de espera de lectura, `30000` por defecto, rango `1..600000` |

La URL no puede llevar credenciales ni nada que lea archivos o tokens en el servidor GrantForge: `user`, `password`, `initFile`, `kerberosAuthType`, `delegationToken`, `storePasswordPath`, `jwt` y las variables `browser…` se rechazan, sin importar mayúsculas. `auth` solo puede ser `noSasl`. Un servicio Kerberos necesita `principal` en la URL; un servicio con contraseña no puede tenerlo.

### Kerberos

Con `kerberos`, GrantForge inicia la sesión del principal por sí mismo, con la keytab o la contraseña, y abre la conexión como ese principal; no hacen falta otros ajustes de Kerberos en el servidor GrantForge. Los reinos cuyos KDC se indican en `kerberos.kdc` se declaran para todo el servidor GrantForge, como hace el plug-in de HDFS, de modo que varios servicios Hive y HDFS pueden usar reinos distintos a la vez.

## Recursos y tipos de acceso

| Nivel | Coincidencia | Notas |
| --- | --- | --- |
| `database` | Comodines, sin distinguir mayúsculas | Las políticas pueden terminar aquí |
| `table` | Comodines, sin distinguir mayúsculas | Bajo una base de datos; las políticas pueden terminar aquí |
| `column` | Comodines, sin distinguir mayúsculas | Bajo una tabla |
| `udf` | Comodines, sin distinguir mayúsculas | Funciones permanentes, bajo una base de datos |
| `url` | Rutas, distinguen mayúsculas, recursivas | Datos que leen o escriben las sentencias, como `hdfs://nn:8020/landing`; tipos de acceso `read` y `write` |
| `hiveservice` | Comodines | El propio HiveServer2; tipo de acceso `serviceadmin` |
| `global` | Comodines | Funciones temporales y archivos añadidos; tipo de acceso `tempudfadmin` |

Los tipos de acceso son los de Hive: `select`, `update`, `create`, `drop`, `alter`, `index`, `lock`, `read`, `write`, `repladmin`, `serviceadmin`, `tempudfadmin`, `refresh` y `all`. Como en Ranger, `all` incluye todos los tipos salvo `tempudfadmin`, que permite a un usuario ejecutar el código que quiera dentro de HiveServer2.

En los valores de las políticas, `*` coincide con cualquier número de caracteres y `?` con uno. El editor de políticas sugiere nombres por prefijo: bases de datos, tablas y UDF de las bases que nombra la política, y columnas de sus tablas.

## Enmascaramiento

Las políticas de enmascaramiento se aplican a columnas. Cada tipo de máscara es una de las funciones de enmascaramiento de Hive, en la que `{col}` representa la columna:

| Tipo de máscara | Muestra |
| --- | --- |
| `mask` | Mayúsculas como `X`, minúsculas como `x` y dígitos como `n` |
| `mask_show_last_4` | Solo los cuatro últimos caracteres |
| `mask_show_first_4` | Solo los cuatro primeros caracteres |
| `mask_hash` | Un hash SHA-256 |
| `mask_null` | `NULL` |
| `mask_none` | El valor sin cambios |
| `mask_date_show_year` | El 1 de enero del año de la fecha |
| `custom` | Una expresión de Hive propia, con `{col}` para la columna, como `concat('****', substr({col}, -4))` |

Por ejemplo, `mask_show_last_4` muestra `4111111111111111` como `xxxxxxxxxxxx1111`.

## Filtros de filas

Las políticas de filtrado de filas se aplican a tablas. Un filtro es una condición de Hive sobre las columnas de la tabla, como `region = 'EU'`: las consultas solo ven las filas que la cumplen.

Las máscaras y los filtros de filas no conceden acceso. Una política de acceso aparte debe permitir la consulta.

## Ejemplo de políticas

Supón que Hive tiene una tabla `sales.orders` con las columnas `id`, `region` y `card`. Crea tres políticas habilitadas en el mismo servicio de datos para el usuario `alice`:

| Tipo de política | Recursos | Regla |
| --- | --- | --- |
| Acceso | `database=sales`, `table=orders`, `column=*` | Permitir `select` al usuario `alice` |
| Enmascaramiento | `database=sales`, `table=orders`, `column=card` | Usar `mask_show_last_4` para el usuario `alice` |
| Filtro de filas | `database=sales`, `table=orders` | Usar `region = 'EU'` para el usuario `alice` |

En cuanto el agente aplica la nueva instantánea, `alice` solo ve las filas EU de `sales.orders`, con cada tarjeta enmascarada salvo sus cuatro últimos caracteres; `SHOW DATABASES` le lista `sales` y las demás bases de datos la rechazan.

## Aplicar las políticas

El plug-in de servicio gestiona las políticas y consulta los recursos. Para aplicarlas, incluidas máscaras y filtros de filas, cada HiveServer2 necesita el agente de Hive. Su despliegue y lo que comprueba se describen en el [agente de Hive](/es/external/hive-agent/).

## Guías relacionadas

- [Servicios de datos, políticas y agentes](/es/external/data-services/)
- [Agente de Hive](/es/external/hive-agent/)
- [Plug-ins y tipos de servicio](/es/develop/plugins/)
