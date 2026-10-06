---
title: Permisos sobre los campos
description: Oculta, enmascara o deja en solo lectura campos controlados según el rol, por ejemplo el correo del usuario y la fecha del último inicio de sesión.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Los permisos sobre los campos deciden cómo **ve y modifica** cada campo controlado quien tiene un rol. Haz clic en **Permisos sobre los campos** en la fila del rol para configurarlos.

![Permisos sobre los campos](/screenshots/role-fields.png)

## Modos de visualización

| Modo | Efecto |
| --- | --- |
| Visible | Muestra el valor original |
| Enmascarar | Oculta parcialmente según el modo de enmascarado: correo (conserva la primera letra y el dominio), teléfono (138\*\*\*5678), documento de identidad (conserva los 6 primeros y los 4 últimos caracteres), conserva el primer y el último carácter, oculta todo |
| Oculto | No devuelve este campo y la columna no se muestra en las listas |

## Modos de modificación

| Modo | Efecto |
| --- | --- |
| Modificable | Se puede rellenar y modificar |
| Solo lectura | Desactivado en el formulario; al modificarlo llamando directamente a la API se devuelve un error que indica de qué campo se trata |

## Reglas de combinación

- Los campos sin configurar los deciden los otros roles de quien los tiene; si ningún rol los configura, el campo es visible y modificable.
- Cuando varios roles configuran el mismo campo, se aplica el **más permisivo** (visible > enmascarado > oculto, modificable > solo lectura).
- La búsqueda y la exportación también respetan los permisos sobre los campos: los campos ocultos no se pueden usar para buscar y, al exportar, se ocultan o se enmascaran según la regla.

## Campos controlados

Los campos controlados se declaran en el código del servidor (por ahora el correo del usuario y la fecha del último inicio de sesión) y en **Gestión de plataforma → Catálogo de recursos** se muestra en qué interfaces aparece cada uno. El control de campos de las aplicaciones de negocio puede implementarlo la propia aplicación, y las reglas se obtienen igualmente desde la API abierta.
