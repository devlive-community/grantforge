---
title: Roles y permisos
description: Crea roles, otorga páginas, botones e interfaces, configura la herencia, asigna roles a personas, grupos, departamentos o puestos y previsualiza el impacto antes de modificar.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Control de acceso → Gestión de roles**: un rol es un conjunto de permisos que surte efecto cuando se asigna a usuarios, grupos de usuarios, departamentos o puestos.

![Gestión de roles](/screenshots/roles.png)

## Roles del sistema y roles personalizados

Cada inquilino tiene el rol de sistema **administrador del inquilino** y el inquilino plataforma tiene además el rol **administrador de la plataforma**. Los roles del sistema tienen automáticamente todos los recursos de su módulo y no se pueden modificar; cuando necesites permisos parecidos pero más reducidos, **copia** el rol del sistema y modifica la copia.

Los roles personalizados tienen un código (letras minúsculas, dígitos, punto, guion o guion bajo) y un nombre, y se pueden deshabilitar: un rol deshabilitado no concede ningún permiso ni transmite permisos por herencia.

## Permisos

Pulsa **Permisos** en la fila de un rol para abrir la matriz de permisos:

![Matriz de permisos](/screenshots/role-grants.png)

- Cambia de aplicación; los recursos se despliegan como un árbol de catálogo y en cada recurso puedes elegir "Permitir" o "Denegar".
- **Al permitir un botón se deducen automáticamente la página en la que está y las interfaces que necesita**; no hace falta marcarlos uno a uno. Los recursos deducidos llevan una marca en la matriz.
- **La denegación tiene prioridad** y se aplica a los recursos subordinados: si deniegas una página, los botones que contiene no están disponibles aunque otro rol los permita.
- Solo puedes conceder los permisos que tú mismo tienes, para evitar excederlos. Quien tiene un rol del sistema puede conceder cualquier recurso de las aplicaciones de negocio.

Antes de guardar, GrantForge muestra el **impacto** de la modificación: qué recursos pasan a estar disponibles o dejan de estarlo y cuántos usuarios tienen este rol.

## Herencia

Pulsa **Herencia** y elige los roles que hereda este rol: obtiene todo lo que los roles heredados permiten y deniegan, así como los roles que esos heredan a su vez. La herencia no puede formar ciclos y solo puedes heredar permisos que tú mismo tengas. Sirve para relaciones acumulativas como "gerente = empleado + aprobación".

## Asignación

Pulsa **Asignar** para asignar el rol a:

| Objeto | Descripción |
| --- | --- |
| Usuario | Directamente a una cuenta |
| Grupo de usuarios | Todos los miembros del grupo obtienen el rol |
| Departamento | Los miembros del departamento obtienen el rol, con la opción "Incluir departamentos subordinados" |
| Puesto | Obtiene el rol quien ocupa ese puesto |

Cada asignación permite fijar la **fecha de inicio** y la **fecha de fin**; al llegar la fecha de fin pierde vigor automáticamente, lo que resulta útil para autorizaciones temporales. También puedes dejar que los usuarios pidan ellos mismos roles por tiempo limitado mediante [Solicitudes de acceso y aprobaciones](/es/guide/access-requests/).

La asignación y la concesión de permisos están sujetas a la [separación de funciones](/es/guide/sod/): se rechaza una asignación que haría que una persona tuviera a la vez roles mutuamente excluyentes.

## Permisos sobre los datos y sobre los campos

En la fila del rol, **Permisos sobre los datos** y **Permisos sobre los campos** determinan respectivamente qué filas puede ver quien tiene el rol y cómo puede ver y modificar qué campos; ver [Permisos sobre los datos](/es/guide/data-permissions/) y [Permisos sobre los campos](/es/guide/field-permissions/).

## Copiar y eliminar

**Copiar** copia también los permisos, los permisos sobre los datos y los permisos sobre los campos. Al eliminar un rol se eliminan a la vez sus asignaciones, sus permisos y sus políticas, y no se puede deshacer.
