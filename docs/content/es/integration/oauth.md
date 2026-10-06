---
title: OAuth 2.1 y OpenID Connect
description: Los endpoints del servidor de autorización, los tipos de cliente, las reglas de los tokens y la clave de firma.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge incorpora un servidor de autorización basado en Spring Authorization Server que cumple los requisitos de seguridad de OAuth 2.1: solo admite el flujo de código de autorización (con PKCE obligatorio), el token de actualización y las credenciales de cliente; no admite el flujo implícito ni el modo de contraseña.

![Servidor de autorización](/screenshots/oauth.png)

## Documento de descubrimiento y endpoints

El documento de descubrimiento está en `<GrantForge>/.well-known/openid-configuration`, y en la página **Gestión de plataforma → Servidor de autorización** se puede copiar directamente. El emisor es de forma predeterminada la dirección por la que llega la petición; fíjalo con `grantforge.oauth.issuer` cuando despliegues GrantForge detrás de un proxy inverso.

| Endpoint | Explicación |
| --- | --- |
| `/oauth2/authorize` | flujo de código de autorización; todos los clientes deben usar PKCE (S256) |
| `/oauth2/token` | código de autorización, token de actualización y credenciales de cliente. El token de actualización se renueva en cada uso; si el antiguo vuelve a aparecer, se revoca toda la autorización |
| `/oauth2/revoke` | revocar tokens |
| `/oauth2/jwks` | clave pública de firma (RS256) |
| `/userinfo` | `sub`, `tid` y `preferred_username`; con el ámbito `profile` trae `name`, y con el ámbito `email`, `email` |

El token de acceso y el token de ID incluyen `tid` (el ID del inquilino) y `preferred_username`; el `auth_time` del token de ID es el momento en que el usuario inició sesión en la consola.

## Clientes

En **Gestión de plataforma → Catálogo de recursos**, selecciona la aplicación y pulsa "Cliente OAuth" para gestionar sus clientes.

| Ajuste | Regla |
| --- | --- |
| Tipo | el **cliente público** se usa en aplicaciones que no pueden guardar una clave, como las de navegador o las móviles; el **cliente confidencial** se usa en aplicaciones de servidor y tiene clave |
| Direcciones de callback | como máximo 10, direcciones absolutas, sin comodines ni fragmento; deben ser https, o http en local (localhost, 127.0.0.1, [::1]), o el protocolo personalizado de una aplicación nativa |
| scope | `openid`, `profile`, `email`, `permissions` (consultar permisos) y `catalog` (declarar entidades de datos, solo con credenciales de cliente) |
| Formas de autorización | código de autorización, token de actualización (requiere código de autorización y solo lo obtienen los clientes confidenciales) y credenciales de cliente (solo clientes confidenciales) |
| Validez de los tokens | token de acceso de 1 minuto a 24 horas (15 minutos por defecto); token de actualización de 1 hora a 90 días (30 días por defecto) |

La clave del cliente confidencial solo se muestra una vez, al registrarlo o al rotarla; GrantForge guarda solo su hash. Al rotarla se puede fijar un periodo de gracia (de 7 días como máximo) durante el que la clave antigua y la nueva siguen siendo válidas, para facilitar el despliegue progresivo.

## Reglas de los tokens

- Los tokens se guardan como hash: ni siquiera con una filtración de la base de datos se obtiene un token utilizable.
- Los tokens ya emitidos dejan de renovarse en cuanto ocurre cualquiera de estas cosas: se desactiva o se elimina el cliente, se desactiva o se bloquea la cuenta, la cuenta debe cambiar la contraseña o se desactiva el inquilino.
- Origen cruzado del navegador: GrantForge permite que el origen de las direcciones de callback de los clientes activados llame de forma cruzada a los endpoints de token y a la API abierta, sin enviar cookies.

## Clave de firma

La clave de firma se genera con RSA 2048 y la clave privada se guarda cifrada. Por defecto se rota automáticamente cada 90 días (`grantforge.oauth.signing-key-rotation`), y la clave pública antigua se sigue publicando en el JWKS durante 2 días (`signing-key-retention`), para que los tokens emitidos antes de la rotación se puedan verificar. Cuando hace falta, se puede rotar de inmediato en la página del servidor de autorización (es una operación sensible: las cuentas con la verificación en dos pasos activada tienen que verificarse otra vez).

## Usar GrantForge para iniciar sesión en sistemas distintos de la consola

Cualquier sistema compatible con OpenID Connect (Grafana, GitLab, Jenkins, etc.) puede usar GrantForge como IdP: créale en el catálogo de recursos una aplicación y un cliente confidencial, y pon la dirección del documento de descubrimiento, el `client_id` y la clave en su configuración OIDC. Al revés, GrantForge también permite iniciar sesión con otros IdP; consulta [Fuentes de identidad](/es/guide/identity-sources/).
