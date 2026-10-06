---
title: Separación de funciones
description: Configura los roles que una misma persona no puede tener a la vez, impide la asignación o limítate a reportarla, y consulta los conflictos actuales.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Control de acceso → Separación de funciones** configura los roles que una misma persona no puede tener a la vez (por ejemplo, pago y aprobación) y muestra las cuentas que infringen ahora las restricciones.

![Separación de funciones](/screenshots/sod.png)

## Restricciones

| Ajuste | Explicación |
| --- | --- |
| Roles excluyentes entre sí | de 2 a 50 roles |
| Máximo por cuenta | 1 por defecto; se puede definir como "dos de los tres como máximo" |
| Modo | **Obligatorio**: rechaza las asignaciones y las herencias de roles que crearían un conflicto; **Solo reporte**: permite el cambio y lo muestra únicamente en la lista de conflictos |
| Activa | una restricción desactivada ni rechaza ni reporta |

"Tener" un rol incluye todas las vías: asignación directa, obtención a través de un grupo, un departamento o un puesto, y herencia de roles. Las asignaciones fuera de su periodo de validez y los roles desactivados no cuentan.

## Cuándo se aplica

En modo obligatorio, los siguientes cambios comprueban antes de guardar cada cuenta a la que afectan:

- asignar un rol, o modificar el periodo de validez de una asignación o si incluye los departamentos subordinados;
- modificar las relaciones de herencia de un rol;
- aprobar solicitudes de acceso (consulta [Solicitudes de acceso y aprobaciones](/es/guide/access-requests/)).

Solo se rechazan los conflictos **que crea este cambio**, y se indica qué persona, qué roles y qué restricción se infringe. Los conflictos que ya existían antes de que la restricción entrara en vigor no bloquean otros cambios sin relación con ellos: siguen en la lista de conflictos y hay que resolverlos a mano.

> [!WARNING]
> Esta comprobación no se hace al añadir a una persona a un grupo, un departamento o un puesto. Los conflictos que surjan por esas vías aparecen en la lista de conflictos; consúltala con regularidad.

## Lista de conflictos

A la derecha se enumeran todas las cuentas que infringen alguna restricción activa (en cualquier modo): cuenta, restricción, roles que tiene y límite permitido.
