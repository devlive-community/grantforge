---
title: Recorrido por la consola
description: La disposición de la consola, los grupos de menús y por qué los menús varían de una persona a otra.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

![Vista general del espacio de trabajo](/screenshots/dashboard.png)

## Disposición

- El **menú de la izquierda** está organizado por grupos: Espacio de trabajo, Control de acceso, Permisos sobre los datos, Administración de la plataforma.
- La **barra superior** tiene acceso rápido (⌘K o Ctrl+K, buscar páginas por su nombre), cambio de idioma, tema claro/oscuro y menú personal.
- La página **Vista general** muestra el número de cuentas, departamentos, grupos de usuarios y puestos del inquilino actual, además de los miembros del espacio de trabajo y los pasos iniciales.

## Grupos de menús

| Grupo | Menús | Descripción |
| --- | --- | --- |
| Espacio de trabajo | Vista general, Mis solicitudes | Lo ve cualquier usuario que haya iniciado sesión |
| Control de acceso | Gestión de usuarios, Estructura de la organización, Grupos de usuarios, Puestos, Importación y exportación, Gestión de roles, Separación de funciones, Aprobación de permisos, Revisión de permisos, Sesiones en línea, Fuentes de identidad, Registros de auditoría | La identidad y los permisos de este inquilino |
| Permisos sobre los datos | Servicios de datos, Políticas, Agentes, Auditoría de acceso | Los permisos sobre los sistemas de datos externos (HDFS, Hive, etc.); ver [Servicios de datos, políticas y agentes](/es/external/data-services/) |
| Administración de la plataforma | Gestión de inquilinos, Catálogo de recursos, Catálogo de API, Servidor de autorización, Revisión del catálogo, Plug-ins | Solo disponible en el inquilino plataforma |

## ¿Por qué mi menú es distinto del de los demás?

La consola es en sí misma una aplicación gestionada por GrantForge: cada página y cada botón son recursos del catálogo de recursos, y qué menús y qué botones ves depende por completo de tus roles.

- Quien tiene el rol de sistema **administrador del inquilino** ve todos los menús de "Control de acceso" y "Permisos sobre los datos" de este inquilino.
- Quien tiene el rol de sistema **administrador de la plataforma** ve además "Administración de la plataforma".
- Las demás personas solo ven las páginas autorizadas por sus roles; cuando en un grupo no hay ninguna página, el grupo completo no se muestra.

> [!NOTE]
> Ocultar menús es solo una comodidad. El servidor vuelve a comprobar los permisos en cada llamada a la API: incluso si introduces la dirección directamente, una operación para la que no tienes permiso se rechaza.

Tras un cambio de permisos no hace falta iniciar sesión de nuevo: cada respuesta lleva el número de versión de los permisos vigentes y, cuando la versión cambia, la consola recarga el menú automáticamente.
