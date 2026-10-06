---
title: Usuarios
description: Crear, buscar, editar, deshabilitar, bloquear y eliminar cuentas, restablecer la contraseña y la verificación en dos pasos, y ver los permisos vigentes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Control de acceso → Gestión de usuarios** gestiona las cuentas de la organización: a qué departamento pertenecen, si pueden iniciar sesión y el restablecimiento de la contraseña.

![Gestión de usuarios](/screenshots/users.png)

## Búsqueda

Busca por nombre de usuario, nombre visible o correo electrónico, filtra por estado (normal, deshabilitada, bloqueada, pendiente de cambio de contraseña) y por departamento, y elige si quieres incluir los departamentos subordinados. La lista solo muestra las cuentas que tus permisos sobre los datos te permiten ver; campos como el correo electrónico pueden ocultarse o enmascararse según tus permisos sobre los campos.

## Creación y edición

"Crear usuario" pide el nombre de usuario (de 3–64 letras, dígitos o `._@-`, único en toda la plataforma), la contraseña inicial, el nombre visible, el correo electrónico, el departamento principal, los departamentos secundarios y los puestos. Las cuentas nuevas deben cambiar la contraseña en el primer inicio de sesión.

## Operaciones sobre las cuentas

| Operación | Efecto |
| --- | --- |
| Deshabilitar / Habilitar | Una vez deshabilitada no puede iniciar sesión y las sesiones abiertas terminan de inmediato |
| Bloquear / Desbloquear | El bloqueo aplicado por un administrador no se levanta automáticamente; al iniciar sesión se indica que se contacte con un administrador; se usa cuando se sospecha un robo |
| Restablecer contraseña | Establece una nueva contraseña inicial; el usuario debe cambiarla en el siguiente inicio de sesión y las sesiones abiertas terminan |
| Restablecer verificación en dos pasos | Para cuando se pierde el autenticador: desactiva la verificación en dos pasos de la cuenta y cierra sus sesiones |
| Ver roles | Los roles que tiene la cuenta y el origen de cada uno (asignación directa, grupo de usuarios, departamento, puesto) |
| Ver permisos vigentes | Ver [Explicar, simular y auditar los permisos](/es/guide/explain/) |
| Eliminar | Elimina la cuenta y sus relaciones con los departamentos; los registros de auditoría se conservan |

La cuenta del sistema (el administrador creado durante la inicialización) y tu propia cuenta no se pueden deshabilitar, bloquear ni eliminar.

## Registro automático

Está desactivado de forma predeterminada. Después de establecer `grantforge.security.registration-enabled=true`, en la página de inicio de sesión aparece el acceso "Crear cuenta"; las cuentas registradas entran en el inquilino indicado en `grantforge.security.registration-tenant` y son cuentas normales sin ningún rol.
