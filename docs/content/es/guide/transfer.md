---
title: Importación y exportación en masa
description: Importa o exporta en masa usuarios y la estructura de la organización con archivos CSV; primero una comprobación previa y después la escritura.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Control de acceso → Importación y exportación** permite importar o exportar en masa usuarios y la estructura de la organización mediante archivos CSV.

![Importación y exportación](/screenshots/transfer.png)

## Importación

1. Pulsa **Descargar plantilla** y rellénala según la "Descripción de columnas". La cabecera no distingue entre mayúsculas y minúsculas y las columnas sobrantes se ignoran; el departamento y el puesto de los usuarios se indican por su código y, si hay varios, se separan con punto y coma.
2. Sube el archivo y pulsa **Comprobación previa**: GrantForge lo revisa línea por línea e informa de cada problema (número de fila, columna y motivo).
3. Cuando todo pase, pulsa **Confirmar importación de N filas**. Si alguna fila tiene un problema no se escribe nada, para evitar importaciones a medias.

Reglas:

- La codificación del archivo puede ser UTF-8 o GBK (los CSV en chino guardados desde Excel son GBK de forma predeterminada) y se detecta automáticamente.
- Un archivo admite como máximo 1000 usuarios o 5000 departamentos y no puede superar los 2 MB (configurable).
- Los departamentos se importan ordenándose automáticamente por su relación de subordinación: en el archivo, un departamento superior puede aparecer escrito después de uno subordinado; si dentro del archivo se forma un ciclo, se informa de ello.
- La contraseña inicial de los usuarios debe cumplir la política de contraseñas y los usuarios importados deben cambiarla en el primer inicio de sesión.
- Solo se puede importar a los departamentos y puestos que tus permisos sobre los datos te permiten ver.

## Exportación

Exporta los usuarios que cumplan las condiciones actuales o todos los departamentos; el nombre del archivo lleva la fecha, por ejemplo `users-2026-10-05.csv`. La exportación también respeta los permisos sobre los datos y los permisos sobre los campos: las filas que no puedes ver no se exportan y los campos restringidos se ocultan o se enmascaran según sus reglas. A las celdas que empiezan por `=`, `+`, `-` o `@` se les añade un prefijo para evitar que el programa de hoja de cálculo las ejecute como fórmulas.
