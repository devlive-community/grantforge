---
title: Referencia de configuración
description: Todas las opciones de configuración, sus valores por defecto y las variables de entorno correspondientes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

La configuración se puede escribir en `configure/application.properties` o sobrescribir con variables de entorno. Las reglas de vinculación flexible de Spring Boot también se aplican: `grantforge.security.mfa.step-up-window` se puede escribir como la variable de entorno `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`. Las duraciones se escriben en formas como `30m`, `12h` o `90d`.

## Servicios y base de datos

| Opción de configuración | Variable de entorno | Valor por defecto | Explicación |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | Puerto HTTP |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | base de datos de archivo H2 integrada | Dirección JDBC, ver [Bases de datos](/es/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | Usuario de la base de datos |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | vacía | Contraseña de la base de datos |
| — | `GRANTFORGE_HOME` | directorio de instalación | Directorio donde están los datos de H2 y los logs |
| — | `GRANTFORGE_ID_NODE` | automático | Número de nodo único por instancia dentro del cluster (0–1023) |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | Tamaño máximo del archivo de importación CSV |

## Inicialización y registro

| Opción de configuración | Valor por defecto | Explicación |
| --- | --- | --- |
| `grantforge.setup.token` | vacío | Token de inicialización fijo (`GRANTFORGE_SETUP_TOKEN`); si está vacío se genera uno aleatorio y se imprime en el log |
| `grantforge.security.registration-enabled` | `false` | Si se permite a los visitantes registrarse por sí mismos |
| `grantforge.security.registration-tenant` | `default` | Inquilino al que pertenecen las cuentas de registro propio |

## Contraseñas y bloqueo

| Opción de configuración | Valor por defecto | Explicación |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | Longitud mínima, al menos 8 |
| `grantforge.security.password.max-length` | `128` | Longitud máxima, como máximo 1024 |
| `grantforge.security.password.required-character-classes` | `1` | Número de clases de carácter que hay que mezclar (minúsculas, mayúsculas, cifras, otras), 1–4 |
| `grantforge.security.password.history-size` | `0` | La contraseña nueva no puede coincidir con las últimas N, 0–24 |
| `grantforge.security.password.max-age` | sin caducidad | Validez de la contraseña; al caducar hay que cambiarla al iniciar sesión |
| `grantforge.security.lockout.max-attempts` | `5` | Número de intentos fallidos consecutivos tras los que se bloquea |
| `grantforge.security.lockout.duration` | `15m` | Duración del bloqueo |

La contraseña no puede contener el nombre de usuario.

## Sesiones y cookies

| Opción de configuración | Valor por defecto | Explicación |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | Tiempo de inactividad tras el que caduca la sesión (`GRANTFORGE_SESSION_TIMEOUT`) |
| `grantforge.security.sessions.max-per-account` | `0` | Número máximo de sesiones simultáneas por cuenta; 0 significa sin límite |
| `grantforge.security.sessions.activity-interval` | `1m` | Intervalo con el que se registra la última actividad de una sesión |
| `grantforge.security.cookie-secure` | `false` | Ponlo a `true` cuando TLS termine en el proxy (`GRANTFORGE_COOKIE_SECURE`) |

## Verificación en dos pasos

| Opción de configuración | Valor por defecto | Explicación |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | Durante cuánto tiempo cubre las operaciones sensibles una verificación en dos pasos, de 1 minuto a 12 horas |
| `grantforge.security.mfa.required-for-sensitive` | `false` | Si las operaciones sensibles exigen que la cuenta tenga activada la verificación en dos pasos |

## Cifrado y servidor de autorización

| Opción de configuración | Valor por defecto | Explicación |
| --- | --- | --- |
| `grantforge.security.encryption-key` | generada automáticamente | Clave Base64 de 32 bytes con la que se cifran los secretos almacenados; en producción hay que definirla sin falta |
| `grantforge.oauth.issuer` | dirección de la solicitud | Emisor de OIDC, por ejemplo `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | Periodo de rotación automática de las claves de firma; 0 la desactiva |
| `grantforge.oauth.signing-key-retention` | `2d` | Tiempo que las claves antiguas siguen publicándose; tiene que ser mayor que la validez de cualquier token |

## Auditoría, plug-ins y agentes

| Opción de configuración | Valor por defecto | Explicación |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | Tiempo que se conservan los logs de auditoría |
| `grantforge.audit.archive-directory` | vacío | Directorio en el que se archivan las auditorías caducadas antes de borrarlas |
| `grantforge.access-audit.retention` | `90d` | Tiempo que se conserva la auditoría de accesos que informan los agentes |
| `grantforge.plugins.directory` | `plugins` | Directorio de plug-ins |
| `grantforge.plugins.call-timeout` | `10s` | Tiempo de espera de las llamadas a plug-ins (probar la conexión, buscar recursos) |
| `grantforge.agents.refresh-interval` | `30s` | Intervalo aconsejado con el que los agentes recuperan las políticas |

## Observabilidad

| Opción de configuración | Valor por defecto | Explicación |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | Si `/actuator/prometheus` es accesible sin iniciar sesión (`GRANTFORGE_PROMETHEUS_PUBLIC`) |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Ponlo a `ecs` o `logstash` para emitir logs en JSON |
