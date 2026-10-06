---
title: Fuentes de identidad (LDAP y OIDC)
description: Permite que los usuarios inicien sesión con el directorio de la empresa (LDAP/AD) o con un proveedor de OpenID Connect, creando cuentas automáticamente y sincronizando.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Control de acceso → Fuentes de identidad** configura los directorios o proveedores de identidad con los que los usuarios pueden iniciar sesión. La contraseña de este tipo de cuentas se guarda en la fuente de identidad: GrantForge no la guarda ni puede modificarla.

![Fuentes de identidad](/screenshots/identity-sources.png)

## LDAP / Active Directory

Pulsa "Añadir fuente de identidad" y elige el tipo "Directorio LDAP":

| Ajuste | Descripción |
| --- | --- |
| Dirección del directorio | `ldap://` o `ldaps://`; varias direcciones separadas por espacios para la conmutación por error |
| Base DN de los usuarios | Por ejemplo `ou=people,dc=example,dc=com` |
| Cuenta de búsqueda | La cuenta (Bind DN) y su contraseña que se usan para buscar usuarios; si se deja vacía, la búsqueda es anónima |
| Filtro de usuarios | De forma predeterminada `(&(objectClass=person)(uid={0}))`; `{0}` es el nombre que introduce el usuario |
| Atributos | Los atributos del nombre de usuario, el nombre visible, el correo electrónico y el identificador único; de forma predeterminada se adaptan a OpenLDAP (`uid`, `cn`, `mail`, `entryUUID`); para Active Directory indica `sAMAccountName` y `objectGUID` |
| Crear cuentas automáticamente para los usuarios nuevos | Si se activa, la cuenta se crea en el primer inicio de sesión de un usuario del directorio |
| Intervalo de sincronización | Si se deja vacío, solo se sincroniza manualmente; el mínimo son 15 minutos |
| Deshabilitar las cuentas de usuarios que ya no están en el directorio | Al sincronizar se deshabilitan los usuarios que ya no existen en el directorio |

Después de guardar, pulsa **Probar conexión** para confirmar que la configuración es correcta.

Al iniciar sesión, GrantForge busca primero al usuario con la cuenta de búsqueda y después comprueba la contraseña vinculándose al directorio con la identidad de ese usuario y la contraseña que ha introducido.

La **sincronización** crea cuentas para los usuarios nuevos del directorio, actualiza el nombre y el correo de las cuentas existentes y, según la configuración, deshabilita a los usuarios que se han marchado (cerrando también sus sesiones). El resultado de la sincronización se muestra en la tarjeta de la fuente de identidad.

## OpenID Connect

Elige el tipo "OpenID Connect" para conectar con Keycloak, Azure AD, Okta, otra instancia de GrantForge, etc.:

| Ajuste | Descripción |
| --- | --- |
| Dirección del emisor | La dirección del emisor del proveedor; GrantForge obtiene los extremos y las claves de su documento de descubrimiento |
| ID / secreto del cliente | El cliente registrado en el proveedor; si no hay secreto, se usa como cliente público con PKCE |
| URL de retorno | La `<GrantForge>/api/v1/auth/federated/callback/<codificación>` que se muestra en la página debe registrarse en el cliente del proveedor |
| Ámbitos y declaraciones | De forma predeterminada `openid profile email`; el nombre de usuario, el nombre visible y el correo se toman de las declaraciones `preferred_username`, `name` y `email` |

Una vez activada, en la página de inicio de sesión aparece el botón "Iniciar sesión con <nombre>". El usuario inicia sesión en el proveedor y vuelve a GrantForge; las cuentas con la verificación en dos pasos activada deben introducir además un código de verificación.

## Reglas de las cuentas

- El identificador único del usuario de la fuente de identidad (`entryUUID`/`objectGUID` en LDAP, `sub` en OIDC) se corresponde con una cuenta de GrantForge; cambiar de nombre no afecta a esa correspondencia.
- Si ya existe una cuenta local con el mismo nombre, **no se asocia automáticamente**: el inicio de sesión se rechaza y un administrador debe cambiar antes el nombre o eliminar la cuenta local. Así se evita que un usuario homónimo del directorio se apropie de una cuenta existente.
- Una fuente de identidad que no crea cuentas automáticamente solo permite iniciar sesión a los usuarios ya asociados.
- Tras deshabilitar una fuente de identidad, sus usuarios no pueden iniciar sesión; una fuente de identidad que aún usan cuentas no se puede eliminar.
- A las cuentas de una fuente de identidad se les pueden asignar roles y activar la verificación en dos pasos con total normalidad.
