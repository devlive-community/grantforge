---
title: Presentación del producto
description: Qué es GrantForge, qué problemas resuelve y en qué se diferencia de las soluciones habituales.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge es una plataforma de permisos unificada de código abierto (MIT). Centraliza "quién puede hacer qué y qué datos puede ver": en la consola mantienes los usuarios y la organización, defines roles y concedes a los roles menús, botones, API, filas de datos y campos; tu aplicación, por su parte, inicia sesión de los usuarios mediante OAuth 2.1 / OpenID Connect y determina los permisos con la API abierta o un SDK.

![Vista general de la consola de GrantForge](/screenshots/dashboard.png)

## Qué problemas resuelve

Cuando un sistema alcanza cierto tamaño, los permisos suelen quedar dispersos: los menús se escriben en la configuración del frontend, cada interfaz hace sus propias comprobaciones con anotaciones, el alcance de los datos depende de condiciones escritas a mano en el SQL, y cuando alguien se va o cambia de puesto nadie sabe decir con certeza qué puede seguir haciendo. GrantForge unifica todo esto en un solo modelo:

- **Definir una vez, aplica en todas partes**: las páginas de la consola, los botones, las interfaces REST, las entidades de datos y los campos son todos "recursos"; los roles se autorizan sobre recursos, y una misma autorización rige a la vez la visibilidad en el frontend y la interceptación en el backend.
- **Permisos visibles**: en cualquier momento puedes responder "por qué esta persona ve esta página" y "a quién afectará cambiar este rol"; cada cambio de autorización tiene vista previa y deja auditoría.
- **Conforme a los requisitos de gobernanza**: la separación de funciones, las solicitudes de acceso con plazo, la revisión periódica y la verificación en dos pasos cumplen los requisitos habituales de la protección clasificada y de las auditorías de control interno.
- **Integración con protocolos estándar**: la aplicación no necesita incrustar su propio sistema de usuarios; basta con iniciar sesión por OIDC y consultar los permisos con el token de acceso.

## Funciones de un vistazo

| Área | Función |
| --- | --- |
| Identidad y organización | Multi-inquilinos, árbol de departamentos, grupos, puestos; importación y exportación masivas por CSV; inicio de sesión y sincronización LDAP/AD, inicio de sesión federado OIDC |
| Seguridad de las cuentas | Gestión de sesiones, política de contraseñas y bloqueo, verificación en dos pasos TOTP y códigos de recuperación, verificación adicional en operaciones sensibles |
| Autorización de funciones | Catálogo de recursos (módulos, menús, páginas, etiquetas, botones, API), herencia de roles, matriz de autorizaciones, análisis de impacto |
| Permisos sobre los datos | Limita las filas visibles por condición (uno mismo, el departamento propio y sus subordinados, departamentos designados, condición personalizada); la lectura y la escritura se controlan por separado |
| Permisos sobre los campos | Campos ocultos, enmascarados (correo electrónico, número de teléfono, número de documento, etc.) o de solo lectura |
| Explicabilidad y auditoría | Explicación de permisos, simulación de autorizaciones, consulta y exportación del registro de auditoría |
| Gobernanza | Restricciones de separación de funciones, solicitud y aprobación de accesos, revisión periódica de permisos |
| Integración de aplicaciones | Servidor de autorización OAuth 2.1 / OIDC, API abierta de consulta de permisos, SDK para Java (Spring Boot) y JavaScript |
| Sistemas externos | Tipos de servicio en plug-in y motor de políticas (similar a Apache Ranger), servicios de datos, políticas de acceso y agentes |
| Entrega | Paquete publicado único, imagen Docker, ejemplos de Compose, Helm Chart; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

## Diferencias con las soluciones habituales

> [!NOTE]
> GrantForge no es una biblioteca que solo hace RBAC, ni un IdP que solo hace inicio de sesión único. Reúne identidad, autorización y gobernanza en un mismo modelo, y hace que cada autorización sea explicable.

- **Frente a escribir los permisos a mano en el código**: las reglas de permisos se mantienen en la consola, cambiar una autorización no exige publicar la aplicación; antes de autorizar ves el alcance del impacto y después queda auditoría.
- **Frente a un IdP que solo autentica (Keycloak, entre otros)**: GrantForge incluye un modelo de autorización que llega hasta los botones, las filas de datos y los campos, además de funciones de gobernanza como la separación de funciones y la revisión; a la vez puede actuar como IdP o incorporar un LDAP u OIDC existente como fuente de identidad.
- **Frente a Apache Ranger**: GrantForge toma prestados los tipos de servicio, las políticas y la arquitectura de agentes de Ranger para gestionar permisos de sistemas de datos externos; pero ante todo es una plataforma de permisos para aplicaciones de negocio.

## Próximos pasos

- [Puesta en marcha en cinco minutos](/es/start/quick-start/): descargar, arrancar, completar la inicialización y conceder el primer rol.
- [Conceptos básicos](/es/start/concepts/): la relación entre recursos, roles, autorizaciones, asignaciones y evaluación.
- [Visión general de la integración de aplicaciones](/es/integration/overview/): haz que tu aplicación use GrantForge.
