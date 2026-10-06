---
title: Revisión periódica de permisos
description: Revisa cada cierto tiempo quién tiene qué roles; quien revisa decide conservarlos o retirarlos uno a uno, y al completarse la ronda se eliminan las asignaciones retiradas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Control de acceso → Revisión de permisos**: revisa cada cierto tiempo quién tiene qué roles; quien revisa decide uno a uno conservarlos o retirarlos, y al completarse la ronda se eliminan las asignaciones retiradas.

![Revisión de permisos](/screenshots/access-reviews.png)

## Plan de revisión

| Ajuste | Explicación |
| --- | --- |
| Nombre, descripción | por ejemplo, "Revisión trimestral de los roles de finanzas" |
| Roles | los roles que se revisan; cada ronda enumera todas sus asignaciones actuales |
| Duración de cada ronda | de 1 a 90 días; al vencerse, la ronda se completa automáticamente |
| Intervalo de repetición | déjalo vacío para empezar las rondas solo a mano; si no, la siguiente empieza automáticamente según el intervalo |
| Elementos sin revisar | los que se quedan sin decisión al terminar la ronda: **conservar** o **retirar** |
| Activo | solo afecta a si las rondas empiezan solas según el plan |

## Una ronda de revisión

1. Haz clic en **Empezar ahora** o espera a que el plan la inicie. GrantForge genera un elemento de revisión por cada asignación (salvo los roles del sistema de las cuentas del sistema).
2. Quien revisa elige **Conservar** o **Retirar** elemento a elemento o por lotes; al retirar se puede escribir una nota, y la decisión se puede deshacer antes de que termine la ronda.
3. No se pueden revisar los roles que uno tiene por asignación directa, grupo, departamento o puesto.
4. Un administrador puede **Completar la ronda** (aplicar todas las decisiones y tratar las que falten según lo que diga el plan) o **Cancelar la ronda** (sin cambiar nada). Una ronda vencida sin completar se completa automáticamente.

Las asignaciones retiradas se borran al completarse la ronda. Cada paso queda en el registro de auditoría, así que sirve como rastro para una auditoría de control interno.
