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

En el catálogo de una aplicación con recursos, pulsa **Exportar** para descargar un archivo JSON de referencia para el desarrollo o para herramientas de IA. Contiene la información de la aplicación, todos los recursos organizados por jerarquía y sus dependencias, con los códigos de los recursos en ambos extremos; la búsqueda y los filtros no cambian lo exportado. `formatVersion` indica la versión del formato de exportación.

Elige la aplicación de destino, pulsa **Importar**, sube un archivo de exportación de la versión 1 y haz primero la comprobación previa. La importación reutiliza los recursos con el mismo código en la misma aplicación solo si coinciden el tipo y el padre, y no sobrescribe la configuración existente; un conflicto de tipo, padre o clase de dependencia anula toda la importación. Los recursos y dependencias nuevos se escriben juntos tras confirmar, y repetir una importación no crea duplicados. Las aplicaciones integradas no admiten importación; las dependencias declaradas pasan a ser manuales tras importarlas. Importar recursos requiere el permiso de crear recursos, e importar dependencias requiere además el de modificarlos.

La exportación solo contiene lo registrado en GrantForge: `route` de las páginas y recursos similares es la ruta del frontend, y `code` de los recursos de API es el código de permiso. Los métodos HTTP y rutas de las API de negocio externas no se deducen si no están registrados. El JSON es una instantánea del momento de la exportación y no incluye las concesiones de ningún usuario; vuelve a exportar cuando cambien los recursos.

## Catálogo de API

**Gestión de plataforma → Catálogo de API** enumera todas las interfaces que el servidor registra solo al arrancar y lo que exige cada una para acceder: ser pública, bastar con iniciar sesión o necesitar un código de permiso. Las interfaces que requieren autorización se agrupan en el catálogo de recursos por su código de permiso, y los roles citan esos permisos al autorizarse. Cuando una interfaz se añade, se retira o cambia su código de permiso, aparece como "cambio pendiente de confirmar" y el catálogo se actualiza al confirmarlo.

![Catálogo de API](/screenshots/apis.png)

## Revisión del catálogo

**Gestión de plataforma → Revisión del catálogo** encuentra las configuraciones que han dejado de funcionar sin que nadie se diera cuenta: autorizaciones sin efecto, botones que no funcionan (les falta la API que necesitan), API que nadie puede llamar y dependencias rotas. La revisión solo lee datos y no modifica nada.

![Revisión del catálogo](/screenshots/health.png)
