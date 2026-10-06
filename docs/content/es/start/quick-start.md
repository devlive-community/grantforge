---
title: Puesta en marcha en cinco minutos
description: Inicia GrantForge con el paquete publicado o con Docker, completa la inicialización, crea un usuario y concede el primer rol.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Este artículo pone en marcha GrantForge en tu propia máquina con la base de datos H2 integrada que viene por defecto. Para producción, consulta [instalar el paquete publicado](/es/deploy/installation/) y [bases de datos](/es/deploy/databases/).

## 1. Puesta en marcha del servicio

Necesitas Java 17 o una versión posterior. Descarga el paquete publicado desde [GitHub Releases](https://github.com/devlive-community/grantforge/releases) o compílalo tú mismo ejecutando `./mvnw -DskipTests package` en el directorio del código fuente (el artefacto queda en `dist/grantforge-release.tar.gz`); después descomprímelo y arranca:

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

También puedes usar Docker: construye primero la imagen a partir del paquete publicado y arráncala después con un ejemplo de Compose:

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

El servicio escucha por defecto en el puerto `9999`. En el primer arranque se crean las tablas de la base de datos y en el registro se imprime una sola vez el **token de inicialización**:

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

Los registros del paquete publicado están en `logs/grantforge.log`; con Docker se consultan con `docker compose logs`.

## 2. Completar la inicialización

Abre http://127.0.0.1:9999/ en el navegador y la consola entrará automáticamente en la página de inicialización. Rellena el token que aparece en el registro, el nombre de la organización y el usuario y la contraseña del primer administrador (al menos 12 caracteres).

![Página de inicialización y de inicio de sesión](/screenshots/login.png)

> [!TIP]
> En instalaciones automatizadas puedes fijar el token por adelantado con la variable de entorno `GRANTFORGE_SETUP_TOKEN`; consulta la [referencia de configuración](/es/reference/configuration/).

Una vez completada la inicialización, este administrador ostenta a la vez los roles de sistema **administrador del inquilino** y **administrador de la plataforma**, y puede usar todas las funciones de la consola. La página de inicialización queda cerrada para siempre a partir de ese momento.

## 3. Crear usuarios

Entra en **Control de acceso → Gestión de usuarios**, pulsa "Crear usuario" y rellena el nombre de usuario, la contraseña inicial y el departamento principal. Los usuarios nuevos deben cambiar la contraseña en su primer inicio de sesión.

## 4. Crear un rol y autorizarlo

1. Entra en **Control de acceso → Gestión de roles**, pulsa "Nuevo rol" y ponle por ejemplo "Auditor de solo lectura".
2. En la fila del rol pulsa "Autorizar" y marca en la matriz de autorizaciones la página "Registro de auditoría". La matriz saca automáticamente las API que necesita esa página.
3. Pulsa "Asignar" para asignar el rol al usuario que acabas de crear.

![Gestión de roles](/screenshots/roles.png)

## 5. Comprobar el resultado

Inicia sesión con el usuario nuevo: en el menú de la izquierda solo aparece "Registro de auditoría". Vuelve a la cuenta de administrador, pulsa el icono "Ver permisos efectivos" en la fila del usuario y podrás ver el origen de cada uno de sus permisos.

## Próximos pasos

- Conoce los [conceptos básicos](/es/start/concepts/).
- Familiarízate con cada menú con la [guía de uso](/es/guide/console/).
- Haz que tu aplicación [se integre con GrantForge](/es/integration/overview/).
