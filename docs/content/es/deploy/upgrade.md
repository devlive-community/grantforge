---
title: Actualizar y migrar desde versiones antiguas
description: La actualización entre versiones 2.x y la migración de cuentas, roles y menús desde 1.x (AuthX / GrantForge 1.x).
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Actualizar entre versiones 2.x

1. Haz una copia de seguridad de la base de datos (y de `plugins/` y `configure/`).
2. Detén el servicio: `bin/shutdown.sh`.
3. Sustituye el `lib/` y el `bin/` antiguos por los del paquete publicado de la nueva versión.
4. Arranca: `bin/startup.sh`. Liquibase ejecuta automáticamente las migraciones de la nueva versión, y la sonda de preparación solo devuelve 200 cuando han terminado.

Al actualizar un clúster, detén primero todas las instancias y después arranca la nueva versión, para evitar que la versión antigua y la nueva lean y escriban a la vez. Las migraciones ya publicadas no se modifican nunca, y cada versión se ha verificado en todas las bases de datos admitidas con el paso desde la versión anterior.

## Migrar desde 1.x

1.x guarda los datos en otro conjunto de tablas y 2.x no las lee. La forma de migrar es: instala 2.x sobre una **base de datos nueva** y completa la inicialización, detén el servicio y después importa las cuentas, los roles y los menús de la base de datos antigua a un inquilino:

```bash
# Primero un ensayo: solo genera el informe, no escribe
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
# Importa de verdad una vez confirmado el informe
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

Los dos comandos escriben `logs/legacy-import-report.json`, que indica qué se ha creado (o se creará), qué se ha omitido y por qué, y el nuevo ID que le corresponde a cada objeto antiguo. El controlador JDBC de la base de datos antigua se pone en `drivers/`, y la contraseña se indica con `GRANTFORGE_LEGACY_SOURCE_PASSWORD` (si no lo haces, el script la pregunta). Ejecutar la importación otra vez solo añade lo que falta.

Reglas de importación:

- Las **cuentas** conservan su contraseña, que se sustituye sola por el nuevo algoritmo de hash en el primer inicio de sesión. Se omiten las cuentas que no cumplen las reglas de nombre de usuario de 2.x (de 3 a 64 letras, números o `._@-`), las que no tienen contraseña y las cuyo nombre de usuario ya está ocupado en otro inquilino.
- Los **roles** conservan el nombre, y su código pasa a minúsculas (`GLY` → `gly`).
- Los **menús** pasan a ser recursos de la aplicación `legacy`: un menú con dirección `#` se convierte en un grupo, otras direcciones en páginas, y los menús que cuelgan de una página en botones. La dirección del menú y su método HTTP pasan a ser el recurso de API `api:<método>:<ruta>`; una dirección que termina en `*` se convierte en `<ruta>/**`, que se compara por segmentos de ruta y no por prefijo de caracteres, y el informe las enumera una a una para que las revises.
- Solo se migran las **autorizaciones explícitas**: 1.x permitía que cualquiera accediera a las direcciones registradas como menús; 2.x no lo hace.

> [!WARNING]
> Antes de importar, revisa en el informe las cuentas omitidas y las direcciones con comodín, y solo entonces ejecuta `--apply`.
