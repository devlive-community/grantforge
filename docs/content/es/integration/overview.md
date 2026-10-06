---
title: Visión general de la integración
description: GrantForge es a la vez servidor de autorización y centro de permisos; las aplicaciones de negocio inician sesión a sus usuarios con protocolos estándar y consultan sus permisos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge tiene dos papeles frente a las aplicaciones de negocio:

- **Servidor de autorización** (OAuth 2.1 / OpenID Connect): el usuario inicia sesión en GrantForge y la aplicación obtiene el token de acceso y el token de ID.
- **Centro de permisos**: con el mismo token, la aplicación le pregunta a GrantForge qué roles, qué recursos (menús, páginas, botones), qué permisos de API y qué alcance de datos tiene ese usuario en esta aplicación.

## Correspondencia de conceptos

| Concepto | Dónde se configura | Explicación |
| --- | --- | --- |
| Aplicación | Gestión de plataforma → Catálogo de recursos | un sistema de negocio, por ejemplo `shop` |
| Recurso | el árbol de recursos de la aplicación en el catálogo de recursos | módulos, menús, páginas, botones, API. Las páginas y los botones controlan la interfaz, y los recursos de API son los códigos de permiso de API (por ejemplo `orders.read`) |
| Cliente | Catálogo de recursos → el "Cliente OAuth" de la aplicación | la identidad con la que la aplicación inicia sesión a los usuarios y obtiene tokens. Las aplicaciones de navegador usan un **cliente público** y las de servidor un **cliente confidencial** |
| scope | los ajustes del cliente | `openid`, `profile` y `email` sirven para iniciar sesión; `permissions` permite que el token consulte los permisos; `catalog` permite que la aplicación declare entidades de datos en su propio nombre |
| Roles y autorizaciones | Control de acceso → Gestión de roles | se autorizan los recursos de la aplicación a un rol y después el rol se asigna a usuarios, grupos, departamentos o puestos |
| Políticas de datos | Rol → Permisos sobre los datos | las entidades que declara la aplicación (`<código-de-aplicación>:<entidad>`) se configuran igual que las entidades propias de la consola: todos, todo el inquilino actual, solo yo, mi departamento, departamentos indicados o según condición |

Los administradores del inquilino (quienes tienen el rol del sistema) pueden autorizar cualquier recurso de las aplicaciones de negocio a los roles de su inquilino; los permisos de la propia consola siguen permitiendo autorizar solo la parte que uno tiene.

## Flujo

```mermaid
sequenceDiagram
  participant B as Navegador
  participant A as Aplicación de negocio
  participant G as GrantForge
  B->>G: /oauth2/authorize (PKCE)
  G-->>B: si no ha iniciado sesión, se le lleva a la página de inicio de sesión de la consola; tras iniciarla, vuelve a la petición de autorización
  G-->>B: vuelve a la dirección de callback de la aplicación con el code
  B->>G: /oauth2/token (code + code_verifier)
  G-->>B: token de acceso, token de ID
  B->>G: /api/v1/open/me/authorization (Bearer)
  G-->>B: roles, recursos, permisos de API (ETag)
  B->>A: llama a la API de la aplicación (Bearer)
  A->>G: /api/v1/open/me/authorization, /data-access (el mismo token)
  A-->>B: devuelve solo los datos que el usuario puede usar
```

## Pasos para integrar

1. Crea una aplicación nueva en **Gestión de plataforma → Catálogo de recursos**, y añade sus páginas, sus botones y sus recursos de API.
2. Registra un cliente para la aplicación: elige "Público" si la aplicación es de navegador y "Confidencial" si es de servidor; pon como dirección de callback la de inicio de sesión de la aplicación; en scope, elige al menos `openid` y `permissions`. La clave del cliente confidencial solo se muestra una vez.
3. En **Control de acceso → Gestión de roles**, crea el rol, autoriza sus recursos y asígnalo a usuarios.
4. Integra el SDK en la aplicación: para Java, el [SDK de Java (Spring Boot)](/es/integration/java/); para el navegador, el [SDK de JavaScript](/es/integration/javascript/); los detalles del protocolo, en [OAuth 2.1 y OpenID Connect](/es/integration/oauth/) y en la [API abierta de consulta de permisos](/es/integration/open-api/).

En el repositorio, `samples/` contiene dos ejemplos completos (una tienda y una libreta de notas), cubiertos por pruebas de extremo a extremo; consulta las [Aplicaciones de ejemplo](/es/integration/samples/).
