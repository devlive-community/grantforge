---
title: Permisos sobre los datos
description: "Determina qué filas de cada tipo de datos puede leer, modificar, eliminar y exportar quien tiene el rol: las propias, las de su departamento, las de departamentos indicados o según una condición."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Los permisos sobre los datos determinan **qué filas** de cada tipo de datos puede leer, modificar, eliminar y exportar quien tiene el rol. Pulsa **Permisos sobre los datos** en la fila de un rol para configurarlos.

![Permisos sobre los datos](/screenshots/role-data.png)

## Reglas

Cada regla consta de cuatro partes:

| Parte | Opciones |
| --- | --- |
| Entidad de datos | Usuarios, departamentos, grupos de usuarios, puestos, eventos de auditoría y las entidades que declaran las aplicaciones de negocio (como `shop:order`) |
| Acción | Ver, modificar, eliminar, exportar |
| Alcance | Todos los inquilinos (solo para roles del inquilino plataforma), todo el inquilino actual, mi departamento y subordinados, mi departamento, departamentos indicados, solo yo, según condición |
| Efecto | Permitir o denegar |

Combinación de reglas:

- **Si no hay ninguna regla de permiso, no se ven datos.**
- **La denegación tiene prioridad sobre el permiso**: cualquier fila a la que afecte una regla de denegación no está disponible.
- Las reglas de los distintos roles de una persona se aplican juntas: los permisos se unen y las denegaciones también se unen.
- Los roles del sistema llevan implícito el alcance correspondiente (el administrador del inquilino tiene todo el inquilino actual), salvo en las entidades de las aplicaciones de negocio.

## Según condición

Cuando el alcance es "Según condición", combina condiciones con el editor de condiciones:

- Compara campos de la entidad, por ejemplo "Estado igual a Normal" o "Último inicio de sesión anterior a ahora". El texto admite contiene y empieza por; los números y las fechas admiten comparaciones de mayor y menor; también se admite pertenece, no pertenece, está vacío y no está vacío.
- El valor puede ser fijo o un **atributo del usuario actual**: su propio ID, nombre de usuario, departamento, grupos de usuarios, puestos que ocupa y el momento actual.
- Las condiciones se pueden agrupar con "Se cumplen todas" / "Se cumple alguna", se pueden negar y el anidamiento llega como máximo a 4 niveles.

## Previsualización

En el editor, elige un usuario y pulsa **Previsualizar** para ver cuántas filas y exactamente cuáles le permiten ver las reglas de este rol.

## Dónde se aplica

Las listas y los detalles de usuarios, departamentos, grupos de usuarios y puestos de la consola, la importación y exportación y los registros de auditoría respetan todos los permisos sobre los datos; las filas que no puedes ver no aparecen en la lista y, si accedes directamente por su ID, la respuesta es "No existe". Las aplicaciones de negocio obtienen las mismas reglas mediante el [SDK de Java](/es/integration/java/) o la [API abierta](/es/integration/open-api/).
