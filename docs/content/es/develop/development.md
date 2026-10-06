---
title: Desarrollo, pruebas e integración continua
description: Compilación local, pruebas, normas de código y comprobaciones de CI.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Entorno

- JDK 21 (los artefactos son código de bytes Java 17; el motor de políticas es Java 8)
- Node.js 22 y pnpm 8.10.2
- Docker (pruebas de integración de bases de datos, benchmarks de rendimiento e imágenes)
- Python 3.12 (scripts de CI)

## Comandos habituales

```bash
./mvnw verify                          # compila y prueba todos los módulos Java (incluida la consola)
./mvnw verify -DskipFrontend           # omite la compilación de la consola
bash script/ci/web.sh test             # pruebas unitarias de la consola
bash script/ci/web.sh e2e              # pruebas de navegador de la consola (backend simulado)
bash script/ci/e2e_fullstack.sh        # empaqueta, arranca el servicio real y ejecuta las pruebas full-stack
bash script/ci/db_integration.sh postgres:17   # ejecuta las pruebas de integración sobre la base de datos indicada
bash script/ci/perf_benchmark.sh smoke # benchmark de rendimiento a pequeña escala
```

Para desarrollar la consola, ejecuta `pnpm dev` (`core/grantforge-web`); Vite reenvía las solicitudes de `/api` y similares al servicio en `localhost:9999`.

## Arrancar en el IDE

Ejecuta directamente `org.devlive.grantforge.server.GrantForge` (módulo `grantforge-server`); por defecto usa la base de datos H2. El servidor carga automáticamente los módulos de plug-in ya compilados que hay en `plugins/` del repositorio (ver [Plug-ins y tipos de servicio](/es/develop/plugins/)); antes de usar un módulo de plug-in por primera vez, ejecuta una vez `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` para copiar sus dependencias.

## Normas de código

- Backend: Error Prone + NullAway (no nulo por defecto; en los puntos anulables, `@Nullable` de JSpecify), Checkstyle, PMD y SpotBugs; ArchUnit protege las convenciones comunes (nada de inyección por campos, nada de SQL nativo, las entidades no aparecen en la API, no se permite `Optional.get()`, etc.).
- Frontend: modo estricto de TypeScript y ESLint sin advertencias; todos los textos pasan por i18n, las claves tienen que ser literales y las claves en chino y en inglés coinciden exactamente.
- Cada archivo de código fuente tiene la cabecera de licencia MIT; cada clase principal de código tiene su clase de prueba correspondiente (las excepciones están registradas en `script/ci/test_mapping_exclusions.txt`).
- Los umbrales de cobertura se definen por módulo (`script/ci/coverage_thresholds.txt`).
- Las migraciones de base de datos son YAML de Liquibase, un cambio por archivo y solo se pueden añadir, no modificar; los tipos usan propiedades multiplataforma como `${text}`.
- Los mensajes de commit siguen Conventional Commits y el título no pasa de 72 caracteres.

## CI

| Trabajo | Contenido |
| --- | --- |
| Repository hygiene | Cabeceras de licencia, rutas prohibidas, mapeo de pruebas, i18n, catálogo de permisos, formato de archivos y comprobaciones de scripts y flujos de trabajo |
| Commit messages | Formato de los mensajes de commit |
| CI script unit tests | Pruebas unitarias de los propios scripts de CI |
| Java 17 / 21 / 25 / latest | Compilación y pruebas de todos los módulos Java, comprobación de la versión del código de bytes |
| Java static analysis | Cobertura, Checkstyle, SpotBugs y PMD |
| Frontend | Coincidencia de los tipos de la API con el contrato, comprobación de tipos y compilación, ESLint, pruebas unitarias y pruebas de navegador |
| JavaScript SDK | Comprobación de tipos, compilación, ESLint y pruebas unitarias |
| Database | Migraciones y pruebas de integración en H2, PostgreSQL 14/17, MySQL 8.0/8.4, MariaDB 10.11/11.4, Oracle 23 y SQL Server 2022 |
| Plugin API compatibility | Comparación del contrato de plug-ins con la última versión publicada |
| Full-stack acceptance | Empaquetado, arranque del servicio y ejecución de las pruebas de navegador full-stack sobre PostgreSQL |
| Docs | Comprobación, pruebas y compilación del sitio de documentación |

Cada noche se ejecutan además benchmarks de rendimiento; el flujo de trabajo de seguridad analiza las dependencias y los secretos.

## Publicación

El número de versión es `año.versión menor.revisión` (por ejemplo `2026.0.0`), y las versiones candidatas añaden `-rc.N`. Las versiones de todos los pom, los paquetes npm, el appVersion del Helm Chart, la barra lateral de la consola y el README tienen que coincidir, y CI lo comprueba con `check_versions.py`.

Para publicar en la rama `dev`, un solo comando:

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # solo comprueba y previsualiza las notas de la versión, no cambia nada
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # fija la versión, crea la etiqueta v2026.1.0 y la sube, y pasa dev a la versión siguiente
```

El script exige que el área de trabajo esté limpia, que la rama local no vaya por detrás del remoto y que la etiqueta no exista; tras la confirmación, crea el commit `chore(release): prepare <versión>`, crea la etiqueta con anotación y la sube. La etiqueta dispara `release.yml`:

- `script/ci/release.sh` compila el paquete publicado, una SBOM CycloneDX con solo las dependencias de publicación y `SHA256SUMS`;
- sube imágenes multiarquitectura a `ghcr.io/devlive-community/grantforge`;
- publica los artefactos de Maven (con el paquete de código fuente y el Javadoc) en GitHub Packages; cuando el repositorio tiene configurados `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (token de Central Portal), `GPG_PRIVATE_KEY` y `GPG_PASSPHRASE`, los firma y los publica en Maven Central;
- crea una GitHub Release cuyo cuerpo recoge todos los commits desde la última versión publicada (una etiqueta `v*` o numérica como `1.0.6`), agrupados por nuevas funciones, correcciones, rendimiento, etc., con enlaces a los commits.

Las versiones candidatas se marcan como preliminares y no actualizan el `latest` de las imágenes. Con el perfil `central` activado en local, por defecto no se publica (`central.skip=true`); solo el flujo de trabajo de publicación pasa explícitamente `-Dcentral.skip=false`.

## Catálogo de permisos

Las páginas y botones de la consola, y las API que necesitan, se declaran en `core/grantforge-web/src/permissions/`. `check_permission_manifest.py` garantiza que cada API declarada existe y que cada interfaz que necesita permisos está cubierta por algún botón o página (las excepciones de llamada directa están registradas en `script/ci/permission_direct_apis.txt`).

## Documentación

Este sitio está en `docs/` y usa la exportación estática de Next.js y Tailwind CSS:

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # comprobación de páginas, enlaces e imágenes
pnpm build      # salida en docs/out
```

Las páginas son Markdown en `docs/content/` y la navegación está en `docs/lib/navigation.ts`. La referencia de la API y los códigos de error se generan al compilar a partir del contrato y del código fuente. Las capturas de pantalla las genera `script/docs/screenshots.sh` arrancando el servicio real, escribiendo datos de ejemplo y usando después Playwright.
