---
title: Explicar, simular y auditar los permisos
description: Consulta qué puede hacer una persona y por qué, simula cambios de rol y busca y exporta los registros de auditoría.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Permisos vigentes

En **Gestión de usuarios**, haz clic en "Ver permisos vigentes" en la fila de un usuario para ver todo lo que puede hacer ahora mismo: roles, menús y botones, API, alcance sobre los datos y campos restringidos.

![Permisos vigentes](/screenshots/user-permissions.png)

Haz clic en **Explicación** en cualquiera de esos elementos y GrantForge responderá "por qué puede hacerlo": a partir de qué rol, de qué asignaciones (directa, mediante grupo, departamento o puesto) y de qué recursos se deduce. Cuando no pueda, indicará si es que no tiene autorización o si lo bloquea alguna denegación.

## Simular cambios

Abre **Simular cambios** dentro de los permisos vigentes: supón que añades o quitas algunos roles a ese usuario y mira qué menús, botones y API gana o pierde. La simulación solo hace cálculos, no guarda nada, y sirve para confirmar el efecto antes de ajustar los permisos.

## Registro de auditoría

**Control de acceso → Registro de auditoría** deja constancia de quién hizo qué y cuándo: inicios de sesión, cambios de autorización, operaciones de administración y llamadas rechazadas.

![Registro de auditoría](/screenshots/audit.png)

- Filtra por evento, resultado, autor, objeto y fecha; el resultado se puede exportar a CSV.
- Cada evento lleva la IP de origen, el navegador y el ID de solicitud, que se corresponde con el registro del servidor.
- Solo se muestran los eventos que tus permisos sobre los datos te permiten ver.
- La auditoría se conserva 365 días por defecto (`grantforge.audit.retention`) y se puede configurar para archivarla en un directorio antes de borrarla.

Los eventos registrados incluyen: inicios de sesión correctos y fallidos, bloqueos, cierres de sesión, fin de sesión y cambios de contraseña; cambios en inquilinos, departamentos, usuarios, grupos y puestos; cambios en el catálogo de recursos y de API; cambios en roles, autorizaciones, herencias y asignaciones; cambios en las políticas de datos y de campos; cada paso de la separación de funciones, de las solicitudes de acceso y de las revisiones; cambios en las fuentes de identidad y en los clientes OAuth, y las llamadas a la API rechazadas.
