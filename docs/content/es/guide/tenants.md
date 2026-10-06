---
title: Inquilinos
description: Crear, editar, desactivar y reactivar inquilinos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Gestión de plataforma → Gestión de inquilinos**: cada inquilino es una organización aislada de las demás, con sus propios usuarios y sus propios permisos. El inquilino de plataforma aloja a los administradores de la plataforma y no se puede desactivar.

![Gestión de inquilinos](/screenshots/tenants.png)

## Crear un inquilino

Escribe el código y el nombre del inquilino, y el nombre de usuario, el nombre visible y la contraseña de su primer administrador. Ese primer administrador tiene el rol de sistema "Administrador del inquilino" y debe cambiar la contraseña en su primer inicio de sesión. El nombre de usuario es único en toda la plataforma.

## Desactivar y reactivar

Al desactivar un inquilino, todas sus cuentas cierran sesión de inmediato y ya no pueden iniciarla, y los tokens de aplicación emitidos dejan de renovarse. Los datos no se borran: al reactivarlo se recuperan.

## Inquilino de plataforma

El inquilino de plataforma es el primero que se crea al inicializar; sus administradores pueden gestionar todos los inquilinos, el [Catálogo de recursos y API](/es/guide/catalog/), el servidor de autorización y los plug-ins. Las aplicaciones de negocio y sus recursos son compartidos por toda la plataforma, y cada inquilino autoriza esos recursos en sus propios roles.
