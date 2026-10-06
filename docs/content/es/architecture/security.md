---
title: Diseño de seguridad
description: El diseño de las sesiones, CSRF, las contraseñas y el bloqueo, la verificación en dos pasos y la reverificación, el almacenamiento cifrado, los tokens y la auditoría.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Sesiones de la consola

- Las sesiones se guardan en la base de datos (Spring Session JDBC) y el navegador solo conserva la cookie `GRANTFORGE_SESSION`: HttpOnly, SameSite=Lax, y con Secure bajo HTTPS (o forzada con `grantforge.security.cookie-secure`).
- Al iniciar sesión, al completar la verificación en dos pasos y al completar el inicio de sesión federado se cambian tanto el identificador de sesión como el token CSRF, para impedir la fijación de sesión.
- Las peticiones que modifican estado deben llevar la cabecera `X-XSRF-TOKEN`, cuyo valor proviene de la cookie `XSRF-TOKEN`.
- Los administradores pueden listar y cerrar cualquier sesión; deshabilitar, bloquear o restablecer la contraseña cierra de inmediato todas las sesiones de esa cuenta; cambiar la contraseña cierra las sesiones de los demás dispositivos.

## Contraseñas

- Las contraseñas nuevas se hashean con Argon2id; todavía se pueden verificar los hashes de BCrypt y los importados de la versión 1.x, y se actualizan al algoritmo actual en el siguiente inicio de sesión.
- La política es configurable en longitud, categorías de caracteres, historial y validez, y la contraseña no puede contener el nombre de usuario.
- Tras alcanzar el número de fallos consecutivos se bloquea la cuenta; con un nombre de usuario desconocido también se realiza una comparación de hash, de modo que el tiempo de respuesta no revela qué nombres de usuario existen; una cuenta o un inquilino deshabilitados solo se indican después de que la contraseña sea correcta.

## Verificación en dos pasos y reverificación

- TOTP (RFC 6238, SHA-1, 6 dígitos, 30 segundos, con un paso de tiempo de tolerancia antes y después); el código de un mismo paso de tiempo solo se puede usar una vez, y los 10 códigos de recuperación de un solo uso se guardan con SHA-256.
- En una cuenta con la verificación en dos pasos activada, tras acertar la contraseña la sesión queda en estado "pendiente" durante 5 minutos, y no se considera iniciada hasta que se completa el segundo paso.
- Las interfaces sensibles se marcan con `@RequireStepUp`: una cuenta con la verificación en dos pasos activada debe haber verificado dentro de una ventana de tiempo configurable; de lo contrario se devuelve `GF-SECURITY-006` y la consola reintenta tras la confirmación en una ventana emergente.

## Almacenamiento cifrado

Las contraseñas de enlace de las fuentes de identidad y los secretos de cliente, los secretos del verificador, la configuración sensible de los servicios de datos y la clave privada de firma del servidor de autorización se guardan cifrados con AES-GCM. La clave proviene de `grantforge.security.encryption-key`; si no está configurada, se genera automáticamente y se guarda en la base de datos (opción apta solo para pruebas). Los tokens de los agentes y los secretos de cliente de OAuth solo se guardan como hash.

## Tokens

- El servidor de autorización guarda el hash de los tokens, no los tokens en sí.
- El token de refresco se rota en cada uso, y cuando el antiguo se reproduce se revoca toda la autorización.
- Que la cuenta esté deshabilitada, bloqueada o deba cambiar la contraseña, que el cliente esté deshabilitado o que el inquilino esté deshabilitado impide la renovación del token; la API abierta confirma en cada llamada que el token sigue siendo válido.
- Las instantáneas de políticas se firman con Ed25519 y los agentes solo las usan después de verificar la firma.

## Protección de las interfaces

- Cada interfaz debe declarar su forma de acceso, y una interfaz sin declaración impide el arranque; las interfaces que necesitan permisos se comprueban en cada llamada contra la instantánea más reciente.
- Las llamadas rechazadas se registran en la auditoría (sin bloquear la petición).
- Una cuenta local que comparte nombre con una fuente de identidad externa no se vincula automáticamente, para impedir la toma de control de cuentas.
- Al exportar CSV se añade un prefijo a las celdas que empiezan por `=`, `+`, `-` o `@`, para impedir la inyección de fórmulas.

## Auditoría

Todas las operaciones de administración, los cambios de autorización, los eventos de inicio de sesión y las llamadas rechazadas se registran en el registro de auditoría; los cambios relacionados con permisos y su auditoría se confirman dentro de la misma transacción, de modo que si el cambio se revierte la auditoría tampoco queda.
