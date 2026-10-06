---
title: "2026.0.0 (reconstrucción)"
description: "GrantForge reescrito desde cero: identidad multi-inquilinos, permisos sobre los recursos y los roles, permisos sobre los datos y los campos, integración mediante protocolos estándar, gobernanza empresarial y permisos sobre los sistemas externos mediante plug-ins."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

2026.0.0 es una reescritura completa que sustituye a 1.x (AuthX). Ya no es una plantilla de administración, sino una plataforma de identidad y permisos de despliegue independiente. Las cuentas, los roles y los menús de 1.x pueden importarse; consulta [Actualización y migración desde versiones antiguas](/es/deploy/upgrade/).

## Plataforma

- Spring Boot 4 con entorno de ejecución de Java 17; un paquete publicado incluye el servidor y la consola, y además se ofrecen imágenes de Docker, Compose y un Helm Chart.
- Compatible con H2, PostgreSQL, MySQL, MariaDB, Oracle y SQL Server; las migraciones las gestiona Liquibase y cada commit se prueba en nueve versiones de base de datos.
- Un asistente de inicialización en el primer arranque crea el administrador de la plataforma con un token de un solo uso que aparece en el registro del servicio.
- Despliegue en clúster: las sesiones se guardan en la base de datos y los ID son TSID ordenados por tiempo.
- Consola totalmente nueva: Vue 3, Tailwind CSS, temas claro y oscuro, chino e inglés.

## Identidad y organización

- Multi-inquilinos: las cuentas, la organización y los permisos de cada inquilino están completamente aislados.
- Usuarios, árbol de departamentos, grupos, puestos e importación y exportación masiva en CSV.
- Contraseñas Argon2id, política de contraseñas y bloqueo configurables, gestión de sesiones, verificación en dos pasos con TOTP y códigos de recuperación, y revalidación de las operaciones sensibles.
- Fuentes de identidad LDAP / Active Directory y OIDC, con sincronización e inicio de sesión federado.

## Autorización

- Catálogo de recursos: módulos, menús, páginas, etiquetas, botones, API, entidades de datos y campos, y las dependencias entre ellos. Las páginas, los botones y las API de la propia consola también están en el catálogo y reciben la misma autorización.
- Roles y permisos: permitir y denegar, herencia, asignación por usuario / grupo / departamento / puesto y periodo de validez.
- Permisos sobre los datos: limitar las filas visibles por ámbito de organización o por condición estructurada.
- Permisos sobre los campos: ocultar, enmascarar o marcar como solo lectura campos según el rol.
- Explicación de permisos, simulación por usuario, registro de auditoría completo y comprobación de configuraciones inválidas.

## Gobernanza

- Separación de funciones: los roles mutuamente excluyentes se rechazan al asignarlos, heredarlos o solicitarlos, y permite detectar conflictos ya existentes.
- Solicitudes de acceso: los usuarios piden los roles que está permitido solicitar; si el aprobador los aprueba, tienen efecto durante un tiempo limitado y se retiran automáticamente al caducar.
- Revisión periódica de permisos.

## Integración de aplicaciones

- Servidor de autorización OAuth 2.1 / OpenID Connect integrado: código de autorización + PKCE, credenciales de cliente, rotación de tokens de actualización y rotación de claves de firma.
- API abierta de consulta de permisos, con versión y ETag.
- Spring Boot Starter y SDK de JavaScript, con aplicaciones de ejemplo listas para ejecutar.

## Permisos sobre los sistemas externos

- Tipos de servicio mediante plug-ins: cada plug-in define la jerarquía de recursos, los tipos de acceso, el enmascarado y el filtrado de filas; cada plug-in se carga de forma independiente.
- Editor de políticas genérico, instantáneas de política firmadas con Ed25519, heartbeats de los agentes y auditoría de accesos.
- Plug-in de ejemplo, tipo de servicio HDFS y agente NameNode para Hadoop 3.5.0; el plug-in de Hive y los agentes para otras versiones de Hadoop están en desarrollo.

## Calidad

- Pruebas de rendimiento a escala de un millón de cuentas que se ejecutan cada noche y fallan si se superan los límites.
- Análisis estático (NullAway, Error Prone, Checkstyle, PMD, SpotBugs, ArchUnit), umbrales de cobertura y pruebas de navegador de pila completa.
- El sitio de documentación se ha reconstruido con Next.js y Tailwind CSS.
