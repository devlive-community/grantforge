---
title: Inicio de sesión, cuentas y verificación en dos pasos
description: Inicio de sesión y sesiones, centro personal, cambio de contraseña, verificación en dos pasos y códigos de recuperación, así como la verificación adicional para operaciones sensibles.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Inicio de sesión

![Página de inicio de sesión](/screenshots/login.png)

El nombre de usuario no distingue entre mayúsculas y minúsculas. Si introduces mal la contraseña 5 veces seguidas (configurable), la cuenta se bloquea durante 15 minutos; una cuenta bloqueada por un administrador debe desbloquearla un administrador. Cuando hay fuentes de identidad habilitadas, la página de inicio de sesión muestra además el botón "Iniciar sesión con X"; ver [Fuentes de identidad](/es/guide/identity-sources/).

Las sesiones se guardan en el servidor y el navegador solo conserva una cookie de sesión HttpOnly. Tras 30 minutos de inactividad (configurable) la sesión caduca y hay que iniciar sesión de nuevo.

## Centro personal

Pulsa el avatar de la esquina superior derecha para entrar al **centro personal**:

![Centro personal](/screenshots/account.png)

- **Datos básicos**: cambia el nombre visible y el correo electrónico. El nombre de usuario y la organización a la que pertenece los mantiene un administrador.
- **Cambio de contraseña**: hay que introducir la contraseña actual. Después del cambio terminan todas tus sesiones en otros dispositivos. Las cuentas que inician sesión mediante una fuente de identidad no ven aquí el cambio de contraseña, ya que la contraseña la gestiona la fuente de identidad.
- **Verificación en dos pasos**: ver más abajo.
- **Mis dispositivos de inicio de sesión**: la lista de navegadores con sesión abierta; puedes cerrar las sesiones que no reconozcas.
- **Registro de inicios de sesión recientes**: los últimos 10 inicios de sesión, cierres de sesión e intentos fallidos, incluidos los intentos de otras personas de iniciar sesión con tu nombre de usuario.

Después de que un administrador restablezca la contraseña o de que la contraseña caduque, el siguiente inicio de sesión entra primero en "Cambio de contraseña" y el resto de funciones no está disponible hasta que la cambies.

## Verificación en dos pasos

Una vez activada, al iniciar sesión hace falta, además de la contraseña, el código de 6 dígitos de una aplicación de autenticación (Google Authenticator, Microsoft Authenticator, 1Password, etc.).

1. En el centro personal, pulsa **Configurar autenticador** en "Verificación en dos pasos".
2. Añade la cuenta en la aplicación de autenticación: introduce la clave que aparece en la página o abre el enlace otpauth en un dispositivo con la aplicación instalada.
3. Introduce el código que muestra la aplicación y pulsa **Activar**.
4. La página muestra **10 códigos de recuperación**, solo una vez. Guárdalos bien: si pierdes el autenticador, cada código de recuperación sirve para iniciar sesión una vez en lugar del código de verificación.

Una vez activada, puedes regenerar los códigos de recuperación (los antiguos caducan de inmediato) o desactivar la verificación en dos pasos; ambas operaciones requieren introducir un código de verificación. Si pierdes el autenticador y no tienes códigos de recuperación, pide a un administrador que restablezca la verificación en dos pasos de la cuenta en **Gestión de usuarios**.

> [!TIP]
> Cada código de verificación solo se puede usar una vez. Los errores consecutivos de código de verificación cuentan para el bloqueo igual que los errores de contraseña.

## Verificación adicional para operaciones sensibles

Para las cuentas con la verificación en dos pasos activada, las siguientes operaciones requieren haber verificado en los últimos 10 minutos (configurable): rotar la clave de firma, crear un cliente o rotar su clave, crear o deshabilitar un inquilino, restablecer la contraseña o la verificación en dos pasos de otra persona, asignar roles, modificar permisos, añadir o modificar fuentes de identidad, aprobar solicitudes de acceso.

Si ejecutas esas operaciones pasado ese tiempo, la consola abre el cuadro de diálogo "Confirmar identidad" y, tras introducir el código de verificación, continúa automáticamente con la operación que habías empezado. Establecer `grantforge.security.mfa.required-for-sensitive=true` exige que las cuentas que ejecutan esas operaciones tengan activada la verificación en dos pasos.

## Sesiones en línea

En **Control de acceso → Sesiones en línea**, los administradores ven todos los navegadores con sesión abierta en este inquilino (cuenta, IP, navegador, hora de inicio de sesión, actividad reciente) y pueden cerrar sesiones sospechosas. Deshabilitar la cuenta, bloquearla o restablecer la contraseña cierra de inmediato todas las sesiones de esa cuenta.

![Sesiones en línea](/screenshots/sessions.png)
