---
title: Modelo de permisos
description: La semántica exacta de los recursos, la derivación de autorizaciones, la herencia y las asignaciones, y las reglas de datos y campos, junto con la instantánea de permisos y la versión.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Este artículo describe las reglas exactas de la evaluación. Para una primera toma de contacto con los conceptos, consulta los [conceptos básicos](/es/start/concepts/).

## Árbol de recursos y tipos

Los recursos pertenecen a una aplicación, se organizan en árbol y tienen como máximo 15 niveles. El tipo determina dónde se pueden colocar:

| Tipo | Padre permitido |
| --- | --- |
| Módulo | Nivel superior, módulo |
| Menú, página | Nivel superior, módulo, menú |
| Etiqueta | Página, etiqueta |
| Botón | Página, etiqueta |
| API, entidad de datos | Nivel superior, módulo |
| Campo | Entidad de datos |

Entre los recursos se pueden declarar dependencias: "necesaria" (al conceder un recurso se conceden también los recursos de los que depende) u "opcional" (solo avisa al administrador, no se concede automáticamente); las dependencias no pueden formar ciclos. Las páginas y los botones de la consola, y las API que necesitan, se declaran en la lista de permisos del frontend, y el servidor las sincroniza como recursos integrados al arrancar.

## Derivación de autorizaciones

Para una cuenta, el evaluador determina primero los **roles efectivos**:

1. Los roles asignados directamente a la cuenta;
2. los roles asignados a los grupos y departamentos a los que pertenece la cuenta (incluidas las asignaciones de departamentos superiores marcadas con "subordinados incluidos"), así como a los puestos que ocupa;
3. solo se toman las asignaciones vigentes en el momento actual y los roles que están habilitados;
4. se despliega la herencia: todos los roles ancestros de un rol (un ancestro deshabilitado no transmite sus permisos).

Después se combinan las autorizaciones de esos roles:

- Permitir un recurso equivale a permitir el propio recurso, sus ancestros en el árbol (para que sea visible) y los recursos de los que depende como "necesarios" (de forma recursiva).
- Denegar un recurso actúa sobre él y sobre todos sus subordinados, y **prevalece sobre cualquier permiso**.
- Un recurso deshabilitado y sus subordinados no tienen efecto.
- Un rol de sistema equivale a permitir todo el subárbol de su módulo (por ejemplo `system`, `data`, `platform`).

El resultado es el conjunto de recursos utilizables y los códigos de permiso que corresponden a los recursos de API entre ellos.

## Protección frente a la escalada de privilegios

- Al conceder un "permiso", quien concede debe poder usar él mismo ese recurso (quien ostenta un rol de sistema no está sujeto a este límite frente a las aplicaciones de negocio).
- Al asignar un rol o establecer una herencia, quien concede debe cubrir todos los recursos que ese rol cubre en cada aplicación.
- El rol de administrador de la plataforma solo lo pueden asignar, modificar o retirar sus poseedores actuales.
- La "denegación" no tiene restricciones: cualquier persona con permiso de autorización puede endurecer los permisos.

## Reglas de datos

Las políticas de datos pertenecen a los roles y definen su alcance según "entidad × acción × efecto": `ALL` (solo para el inquilino plataforma), `TENANT`, `ORG_AND_CHILDREN`, `ORG`, `CUSTOM_ORGS`, `SELF`, `CONDITION`. Una fila es utilizable si y solo si cumple al menos una regla de permiso y no cumple ninguna regla de denegación.

Las condiciones son JSON estructurado, no ejecutable, que se valida antes de guardarse en la base de datos:

```json
{ "and": [
  { "field": "status", "op": "eq", "value": "ACTIVE" },
  { "not": { "field": "orgUnitId", "op": "in", "value": { "var": "subject.orgUnitIds" } } }
] }
```

Las variables son únicamente `subject.id`, `subject.username`, `subject.orgUnitIds`, `subject.groupCodes`, `subject.positionCodes` y `now`; los operadores de comparación se limitan según el tipo del campo; la anidación no pasa de 5 niveles y los nodos no pasan de 50. El servidor traduce las reglas a `Specification` de JPA, y el SDK las traduce con la misma semántica dentro de la aplicación de negocio.

## Reglas de campos

Las políticas de campos definen la forma de lectura (visible, enmascarado, oculto) y la forma de escritura (editable, solo lectura); cuando hay varios roles se toma el ajuste más permisivo, y si no hay ningún ajuste el campo queda totalmente abierto. Las reglas de lectura se aplican al serializar a JSON (un mismo DTO se presenta de forma distinta ante personas distintas) y las reglas de escritura se aplican en la capa de servicio; modificar un campo de solo lectura devuelve `GF-FIELD-001` e indica el campo.

## Instantánea y versión

El resultado de la evaluación es una **instantánea de permisos**: los recursos utilizables, los códigos de permiso, las reglas de datos y las reglas de campos. La instantánea se guarda en caché por cuenta; cualquier cambio en las autorizaciones, las asignaciones, la herencia, el catálogo de recursos, las políticas o las relaciones de pertenencia eleva el número de versión del ámbito correspondiente (el catálogo o el inquilino), lo que invalida la caché. Cada interfaz que necesita permisos devuelve la versión actual en la cabecera de respuesta `X-Authorization-Version`, y la consola decide con ella si debe recargar el menú; la API abierta expresa esa misma versión mediante ETag.
