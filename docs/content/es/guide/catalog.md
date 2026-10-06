---
title: Catálogo de recursos y API
description: Mantén el árbol de recursos y las dependencias de cada aplicación y sus clientes OAuth, consulta las API registradas automáticamente y encuentra las configuraciones que han dejado de funcionar con la revisión del catálogo.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Catálogo de recursos

**Gestión de plataforma → Catálogo de recursos** mantiene el árbol de recursos de cada aplicación. La consola (`grantforge-console`) es la aplicación integrada: sus páginas, sus botones y sus API se registran solos al arrancar y no se pueden borrar.

![Catálogo de recursos](/screenshots/resources.png)

- **Aplicaciones**: crea, edita y borra aplicaciones de negocio; una aplicación que tiene clientes o recursos no se puede borrar.
- **Recursos**: módulos, menús, páginas, pestañas, botones, API, entidades de datos y campos. El tipo decide dónde puede estar cada cosa: un botón solo bajo una página o una pestaña, y un campo solo bajo una entidad de datos. Los recursos se pueden recolocar arrastrándolos, con un máximo de 15 niveles.
- **Estado**: se puede ocultar o desactivar un recurso; cuando falta un permiso, se puede elegir entre ocultar el botón o desactivarlo.
- **Dependencias**: un botón "necesita" la API que llama y una página "necesita" la API de la que carga los datos; al autorizar, las dependencias se deducen también y la página de detalle las muestra en un gráfico.
- **Clientes OAuth**: registra clientes para aplicaciones de negocio; consulta [OAuth 2.1 y OpenID Connect](/es/integration/oauth/).
- **Campos**: al seleccionar un campo se muestra en qué interfaces aparece (las que lo devuelven o lo reciben).

## Catálogo de API

**Gestión de plataforma → Catálogo de API** enumera todas las interfaces que el servidor registra solo al arrancar y lo que exige cada una para acceder: ser pública, bastar con iniciar sesión o necesitar un código de permiso. Las interfaces que requieren autorización se agrupan en el catálogo de recursos por su código de permiso, y los roles citan esos permisos al autorizarse. Cuando una interfaz se añade, se retira o cambia su código de permiso, aparece como "cambio pendiente de confirmar" y el catálogo se actualiza al confirmarlo.

![Catálogo de API](/screenshots/apis.png)

## Revisión del catálogo

**Gestión de plataforma → Revisión del catálogo** encuentra las configuraciones que han dejado de funcionar sin que nadie se diera cuenta: autorizaciones sin efecto, botones que no funcionan (les falta la API que necesitan), API que nadie puede llamar y dependencias rotas. La revisión solo lee datos y no modifica nada.

![Revisión del catálogo](/screenshots/health.png)
