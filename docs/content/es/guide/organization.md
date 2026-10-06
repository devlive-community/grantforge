---
title: Departamentos, grupos y puestos
description: Mantener el árbol de departamentos, crear grupos de usuarios según sea necesario y gestionar los puestos, todos ellos utilizables como objeto de asignación de roles y de alcance de datos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Estructura de la organización

**Control de acceso → Estructura de la organización** mantiene la jerarquía de departamentos.

![Estructura de la organización](/screenshots/org.png)

- Los departamentos tienen como máximo 16 niveles; puedes arrastrarlos o usar "Mover a…" para cambiar su posición, pero no se pueden mover por debajo de sus propios subordinados.
- Cada cuenta tiene un departamento principal y puede pertenecer a la vez a varios departamentos.
- Los departamentos son una base importante de los permisos sobre los datos: "Mi departamento", "Mi departamento y subordinados" y "Departamentos indicados" se calculan según esta estructura.
- Solo se pueden eliminar los departamentos que no tienen departamentos subordinados.

## Grupos de usuarios

**Control de acceso → Grupos de usuarios**: pon en el mismo grupo las cuentas que necesitan los mismos permisos y después asigna roles al grupo. Los grupos de usuarios son independientes de la estructura de la organización y sirven para conjuntos que cruzan departamentos, como un "grupo de guardia" o un "grupo de proyecto". Los miembros se pueden añadir y quitar en lotes, como máximo 500 personas cada vez.

![Grupos de usuarios](/screenshots/groups.png)

## Puestos

**Control de acceso → Puestos**: mantén los puestos de la organización (por ejemplo "Gerente de finanzas") y asígnales uno o varios puestos al editar a un usuario. A los puestos también se les pueden asignar roles: cuando una persona cambia de puesto, basta con cambiarle el puesto y sus permisos cambian con él.

![Puestos](/screenshots/positions.png)
