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
| Declaración del teléfono | Lee `phone` de forma predeterminada (Casdoor); otros proveedores pueden usar `phone_number`. Para sincronizar el teléfono, el ámbito debe incluir `phone`. Si el proveedor no devuelve ningún número al iniciar sesión, se conserva el existente |
| Dirección del avatar | Se sincroniza desde la declaración estándar de OIDC `picture`; el ámbito debe incluir `profile`. Si no se devuelve, se conserva la dirección existente |
| Parámetros adicionales de la solicitud de autorización | Objeto JSON opcional cuyos valores deben ser cadenas. Por ejemplo, en Casdoor `{"provider_hint":"github"}` elige un proveedor configurado. Los parámetros aparecen en la dirección del navegador, así que nunca pongas secretos en ellos |

GrantForge gestiona los parámetros esenciales del protocolo (incluidos `state`, `nonce`, `redirect_uri` y los de PKCE); los parámetros adicionales no pueden sustituirlos.

Una vez activada, en la página de inicio de sesión aparece el botón "Iniciar sesión con <nombre>". El usuario inicia sesión en el proveedor y vuelve a GrantForge; las cuentas con la verificación en dos pasos activada deben introducir además un código de verificación.

## Reglas de las cuentas

- El identificador único del usuario de la fuente de identidad (`entryUUID`/`objectGUID` en LDAP, `sub` en OIDC) se corresponde con una cuenta de GrantForge; cambiar de nombre no afecta a esa correspondencia.
- Si ya existe una cuenta local con el mismo nombre, **no se asocia automáticamente**: el inicio de sesión se rechaza y un administrador debe cambiar antes el nombre o eliminar la cuenta local. Así se evita que un usuario homónimo del directorio se apropie de una cuenta existente.
- Una fuente de identidad que no crea cuentas automáticamente solo permite iniciar sesión a los usuarios ya asociados.
- Tras deshabilitar una fuente de identidad, sus usuarios no pueden iniciar sesión; una fuente de identidad que aún usan cuentas no se puede eliminar.
- A las cuentas de una fuente de identidad se les pueden asignar roles y activar la verificación en dos pasos con total normalidad.

La sincronización de la estructura organizativa se configura en una fuente de datos aparte, sin cambiar los ajustes de inicio de sesión OIDC.

## Fuentes de datos y sincronización de la organización

Las fuentes de datos sincronizan la estructura organizativa y las fuentes de identidad siguen encargándose del inicio de sesión OIDC o LDAP. Se configuran por separado: deshabilitar una fuente de datos no afecta al inicio de sesión OIDC.

1. Configura el inicio de sesión OpenID Connect existente en "Control de acceso → Fuentes de identidad".
2. En "Control de acceso → Fuentes de datos", añade una fuente de DingTalk y elige la fuente de identidad OIDC asociada.
3. Introduce CorpId, AppKey, AppSecret y el ID del departamento raíz (normalmente `1`). El AppSecret se cifra por separado, la API nunca lo devuelve en claro y, si al editar dejas el campo vacío, se conserva el valor anterior.
4. Puedes montarla bajo un departamento local. El intervalo de sincronización es de 30 minutos de forma predeterminada y admite de 15 a 10080 minutos; vacío significa solo sincronización manual.
5. Después de guardar, prueba la conexión y ejecuta la primera sincronización. La aplicación de DingTalk necesita permiso de lectura sobre los departamentos y el directorio de empleados, y su alcance autorizado debe cubrir el subárbol elegido.
6. Cuando un usuario inicia sesión por SSO, el empleado se busca por el correo OIDC de confianza y sus departamentos y su departamento principal se rellenan automáticamente. El correo debe administrarlo un administrador, no debe permitir que un empleado suplante a otro y debe coincidir con el correo de DingTalk. Si falta el correo del empleado, se usa el correo corporativo.

El inicio de sesión solo lee la instantánea local del directorio y no consulta a DingTalk. Con MFA, la pertenencia se actualiza tras superar el segundo paso. Las cuentas que inician sesión antes de la primera sincronización guardan el correo de confianza y se vinculan cuando termina la sincronización; el correo del perfil, editable localmente, nunca se usa para la correspondencia. Un correo ausente, repetido o ya vinculado a otra cuenta no crea vínculos nuevos.

El vínculo con un empleado se basa en su ID externo y no pasa a otro empleado cuando cambia el correo. La sincronización cubre la pertenencia a varios departamentos; el departamento principal sigue siendo el anterior mientras sea válido y, si no, se elige por el orden estable de los ID de departamento. Se conservan los departamentos locales, los grupos de usuarios, los puestos y las asignaciones directas de roles. Al asignar un rol a un departamento sincronizado, los usuarios lo heredan según las reglas existentes, y un traslado actualiza sus permisos.

En cuanto existen una instantánea de la organización o cuentas pendientes de vincular, la fuente de identidad asociada, la empresa, la aplicación, el departamento raíz y el punto de montaje quedan fijos. Los departamentos y pertenencias sincronizados son de solo lectura y no se pueden sobrescribir a mano desde la gestión de usuarios o de la organización. Los departamentos que desaparecen se conservan para proteger los permisos existentes; cuando un empleado sale del directorio autorizado, se quitan las pertenencias que administra esta fuente, pero su cuenta no se deshabilita automáticamente. Una fuente de datos con departamentos propios no se puede eliminar directamente, pero sí deshabilitar.

La sincronización obtiene todas las páginas antes de aplicar el resultado de forma atómica. Ante errores de lectura, jerarquías anómalas o cambios del alcance autorizado, se mantiene la última instantánea correcta; los administradores revisan el resultado con el resumen de sincronización, la lista de incidencias y el registro de auditoría. Si el alcance cambió, restablece primero el alcance aprobado y luego vuelve a sincronizar.

Se usa el [SDK oficial de servidor de DingTalk](https://open.dingtalk.com/document/development/download-the-server-side-sdk), sin llamadas HTTP propias. Los adaptadores de proveedor producen instantáneas neutras de departamentos y empleados, de modo que un futuro adaptador de Feishu podrá reutilizar la misma lógica de sincronización, vinculación y actualización de permisos.
