---
title: Multi-inquilinos y aislamiento de datos
description: Cómo aíslan los datos los inquilinos, qué datos comparte la plataforma y las restricciones de las operaciones entre inquilinos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Forma de aislamiento

Salvo los datos que comparte la plataforma, todas las tablas de negocio tienen una columna `tenant_id`. Cuando entra una petición, la cadena de filtros vincula al hilo actual el inquilino de la cuenta que ha iniciado sesión, el filtro de inquilinos de Hibernate añade automáticamente la condición de inquilino a las consultas y, al escribir, se rellena automáticamente el `tenant_id`. El código de negocio no puede "olvidarse" de añadir la condición de inquilino.

Los pocos escenarios que necesitan cruzar inquilinos (por ejemplo, buscar una cuenta por su nombre de usuario al iniciar sesión, contar las cuentas de cada inquilino o las tareas programadas en segundo plano) deben entrar de forma explícita en el "contexto de sistema", de modo que en la revisión de código se pueden localizar de un vistazo.

## Compartido e independiente

| Compartido por la plataforma | Independiente en cada inquilino |
| --- | --- |
| Catálogo de aplicaciones y recursos, catálogo de API, clientes de OAuth, plug-ins | Cuentas, departamentos, grupos, puestos, roles, autorizaciones, asignaciones, políticas de datos y de campos, separación de funciones, solicitudes y revisiones, fuentes de identidad, servicios de datos, auditoría |

El nombre de usuario es único en toda la plataforma, por lo que al iniciar sesión no hace falta elegir inquilino.

## El inquilino plataforma

El inquilino plataforma lo crea la inicialización y no se puede deshabilitar. Sus administradores gestionan los datos compartidos por la plataforma y los demás inquilinos; solo los roles del inquilino plataforma pueden usar el alcance de datos "todos los inquilinos".

## Identificadores

Todas las claves primarias son TSID: 64 bits, ordenadas por tiempo y con unicidad garantizada en el clúster mediante el número de nodo. Son mayores que los enteros que JavaScript puede representar con exactitud, por lo que en JSON se pasan siempre como cadena de texto. Cada instancia del clúster necesita un `GRANTFORGE_ID_NODE` distinto.
