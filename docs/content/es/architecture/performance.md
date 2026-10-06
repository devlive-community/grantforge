---
title: Rendimiento y benchmarks
description: Los objetivos de rendimiento a escala de un millón de cuentas, los datos y el método de las pruebas comparativas, y cómo ejecutarlas en local.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Los objetivos de rendimiento de GrantForge apuntan a una organización grande: **un millón de cuentas, diez mil roles y cien mil recursos**. Cada noche se ejecutan las pruebas comparativas del sistema sobre PostgreSQL 17, y cualquier métrica que supere su umbral hace fallar la compilación.

## Objetivos

| Métrica | Significado | Umbral |
| --- | --- | --- |
| `authz.snapshot.hit` | Instantánea de permisos ya en caché (se usa en cada llamada a una interfaz) | p99 1 ms |
| `authz.snapshot.build` | Cálculo por primera vez de la instantánea de consola de una cuenta | p95 50 ms |
| `authz.snapshot.app` | Cálculo de la instantánea en una aplicación con un árbol de recursos grande | p95 50 ms |
| `api.users.page` | Una página de la lista de usuarios (dentro de las 500 primeras páginas) | p95 200 ms |
| `api.users.search` | Búsqueda de usuarios por nombre | p95 200 ms |
| `api.groups.page` | Una página de la lista de grupos | p95 200 ms |
| `api.roles.list` | Lista de roles (sin paginar) | p95 200 ms |
| `api.me.authorization` | Los permisos de la cuenta actual | p95 200 ms |
| `api.member.groups` | Una cuenta con permiso sobre una sola página accede a una lista protegida | p95 200 ms |
| `jmh.derivation.*` | Preparar la derivación para un árbol de recursos grande y derivar veinte autorizaciones (JMH) | media de 50 / 5 ms |

Los umbrales solo se pueden endurecer; relajar cualquiera de ellos exige una decisión documentada.

## Datos

Las pruebas comparativas arrancan un servicio real, completan la inicialización y después escriben a través de las propias entidades de la aplicación:

- 100 departamentos, un grupo por cada mil cuentas y 100 puestos;
- cada cuenta pertenece a un departamento y a un grupo, una de cada diez cuentas ocupa un puesto y una de cada veinte tiene un rol asignado directamente;
- uno de cada diez roles hereda de uno de entre los cien primeros roles; cada grupo tiene tres roles, cada departamento dos y cada puesto uno; cada rol autoriza cinco páginas de la consola;
- una aplicación: cien páginas repartidas en varios módulos, con nueve operaciones por página; a una décima parte de los roles se les autorizan veinte de esas páginas y de esas operaciones.

Cada métrica se mide llamando una a una después del calentamiento.

## Ejecutarlas en local

```bash
bash script/ci/perf_benchmark.sh full            # escala objetivo, postgres:17 (necesita Docker), comprueba los umbrales
bash script/ci/perf_benchmark.sh smoke           # pequeña escala sobre H2, solo confirma que las pruebas se ejecutan
bash script/ci/perf_benchmark.sh full mysql:8.4  # otras bases de datos
```

El informe se escribe en `perf/target/perf-report.json` y en el registro se imprime además una tabla. Puedes pasar parámetros adicionales con `PERF_OPTS`:

| Parámetro | Valor por defecto | Significado |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2` o `<motor>:<versión>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | Tamaño de los datos |
| `perf.samples` / `perf.warmup` | 1000 / 200 | Mediciones y calentamientos por métrica |
| `perf.jmh` | `true` | Ejecutar también las pruebas comparativas de JMH |
| `perf.enforce` | `true` | Salir con el código 1 cuando se supera un umbral |

## Por qué es rápido

- La instantánea de permisos se guarda en caché por cuenta y se invalida en bloque según el número de versión del catálogo y del inquilino, de modo que un acierto no es más que una lectura en memoria.
- La derivación se hace en memoria: el árbol de recursos, las relaciones de herencia y las asignaciones se cargan por adelantado como estructuras compactas, lo que evita consultar registro a registro.
- Las interfaces de lista están totalmente paginadas, y la ordenación y el filtrado recaen sobre columnas indexadas; las asociaciones diferidas se cargan en lotes de 64 y las escrituras se confirman en lotes de 50 (los identificadores los genera la aplicación, por lo que se pueden insertar por lotes).
