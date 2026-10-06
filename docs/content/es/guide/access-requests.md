---
title: Solicitudes de acceso y aprobaciones
description: Los usuarios piden roles por tiempo limitado, quien aprueba los concede, los rechaza o los revoca antes de tiempo, y al vencerse el plazo se retiran solos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Administrador: roles que se pueden pedir

En **Control de acceso → Aprobación de permisos**, haz clic en **Roles solicitables** y elige qué roles personalizados se pueden pedir y durante cuántos días como máximo se puede pedir cada uno (de 1 a 365). Los roles del sistema no se pueden pedir.

## Usuario: solicitar

Cualquier usuario con sesión iniciada ve en **Espacio de trabajo → Mis solicitudes** los roles que puede pedir. Elige el rol, escribe el motivo y los días, y envía la solicitud; puedes retirarla antes de que se apruebe. No se puede repetir una solicitud si ya tienes el rol o si ya hay una pendiente de aprobar.

![Mis solicitudes](/screenshots/requests.png)

Un usuario recién creado tiene que cambiar primero la contraseña inicial para poder usar esta página.

## Quien aprueba: conceder, rechazar y revocar

**Control de acceso → Aprobación de permisos** enumera las solicitudes pendientes, las ya concedidas o todas.

![Aprobación de permisos](/screenshots/access-approvals.png)

- **Conceder**: puedes acortar el número de días y escribir un comentario. Al concederla, el usuario obtiene el rol de inmediato y este caduca al cumplirse el plazo.
- **Rechazar**: puedes escribir el motivo.
- **Revocar**: retira el rol antes de tiempo en una solicitud ya concedida.

Conceder una solicitud equivale a que quien aprueba asigne el rol, así que se aplican las mismas reglas:

- no se puede conceder un rol que vaya más allá de los permisos de quien aprueba;
- se respetan las restricciones de [Separación de funciones](/es/guide/sod/);
- nadie puede aprobar su propia solicitud;
- quienes aprueban y tienen activada la verificación en dos pasos deben haber verificado en los últimos 10 minutos.

## Retirada al vencer

El rol concedido deja de tener efecto en el momento de la fecha límite. En segundo plano se limpian las asignaciones caducadas cada 5 minutos y la solicitud se marca como "vencida". Todo el proceso (solicitud, retirada, concesión, rechazo, revocación y vencimiento) queda registrado en el registro de auditoría.
