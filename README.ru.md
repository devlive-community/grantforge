<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="Логотип GrantForge" />

# GrantForge

Унифицированная платформа разрешений · пользователи, роли, меню, API, строки и поля данных · внешние системы данных

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · Русский · [한국어](README.ko.md) · [日本語](README.ja.md) · [Deutsch](README.de.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.1.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge (ранее AuthX) — это открытая (MIT) унифицированная платформа разрешений. Она отвечает в одном месте на два вопроса: **кто может что делать** (функциональная авторизация) и **кто какие данные видит** (авторизация по данным и полям). Разрешения определяются, объясняются и аудируются в консоли, приложения интегрируются через стандартные протоколы, а внешние системы данных, такие как HDFS, включаются в ту же модель политик с помощью плагинов и агентов.

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="Консоль GrantForge" />
</p>

## Возможности

| Направление | Что делает |
| --- | --- |
| Идентификация и организация | Мультитенантность, дерево подразделений, группы пользователей и должности; массовый импорт/экспорт CSV; вход и синхронизация через LDAP / Active Directory, федерация OIDC |
| Безопасность учётных записей | Управление сессиями и принудительный выход, парольная политика с блокировкой, двухфакторная аутентификация TOTP с кодами восстановления, повышенная проверка для чувствительных операций |
| Функциональная авторизация | Каталог ресурсов (модули, меню, страницы, вкладки, кнопки, API), наследование ролей, матрица разрешений, анализ влияния перед выдачей |
| Разрешения на данные | Условия на уровне строк (сам пользователь, своё подразделение и его дочерние, указанные подразделения, пользовательские условия), чтение и запись контролируются раздельно |
| Разрешения на поля | Поля можно скрывать, маскировать (электронная почта, номер телефона, номер документа) или делать доступными только для чтения |
| Объяснимость и аудит | Объяснение разрешений (откуда взялось каждое право), симуляция выдачи, запрос и экспорт журнала аудита |
| Управление | Ограничения разделения обязанностей, запросы доступа с согласованием, периодические проверки доступа |
| Интеграция приложений | Сервер авторизации OAuth 2.1 / OIDC, открытый API разрешений, SDK для Java (Spring Boot starter) и JavaScript |
| Внешние системы | Плагинные типы служб и движок политики: сервисы данных, политики доступа, агенты и аудит доступа |
| Доставка | Единый исполняемый релиз, Docker-образ, примеры Compose, Helm-чарт; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

Поддержка внешних систем сегодня включает фреймворк плагинов, универсальный редактор политик, подписанное распространение политик, аудит доступа, тип службы HDFS и агент Hadoop 3.5.0 NameNode; плагин Hive и агенты для других версий Hadoop ещё в разработке.

## Как это работает: две плоскости

- **Управляющая плоскость**: сервер GrantForge (Spring Boot 4.1, байт-код Java 17) и консоль Vue 3 владеют тенантами, учётными записями, организацией, ролями, разрешениями, аудитом, сервисами данных и политиками.
- **Плоскость данных**: агенты, встроенные в защищаемую систему. Агент забирает подписанные Ed25519 снимки политик по своему токену и кэширует их локально, принимает решение о каждом доступе до его совершения (запрещая, когда политик нет), и отправляет события доступа обратно для аудита.

Вашим системам не обязательно следовать модели HDFS. Обычные приложения оценивают разрешения в своём процессе через открытый API или Spring Boot starter; только системам, которые должны перехватывать доступ внутри базы данных, файловой системы или аналогичного хранилища, нужен агент, написанный поверх `core/grantforge-agent-core`.

## Интеграция вашего приложения

- **OAuth 2.1 / OpenID Connect**: GrantForge — это сервер авторизации, поэтому приложения используют его для входа пользователей; существующие источники идентичности (LDAP / AD / OIDC) тоже можно подключить.
- **Java-приложения**: `sdk/grantforge-spring-boot-starter` добавляет `@RequirePermission` для эндпоинтов, `@GrantForgeEntity` для сущностей данных и `GrantForgeDataScopes.scope(...)`, чтобы превращать разрешения на данные платформы в JPA `Specification`.
- **Фронтенд-приложения**: `@grantforge/client` выполняет вход пользователей через OIDC + PKCE с вашего собственного origin и запрашивает их разрешения.
- **Открытый API**: `/api/v1/open/me/authorization`, `/api/v1/open/me/data-access`, `/api/v1/open/catalog/data-entities`.
- **Работающие примеры**: `samples/shop` и `samples/notes` интегрируются так, как это сделал бы сторонний разработчик.

## Быстрый старт

Требуется Java 17 или новее. Сервис слушает порт `9999` и при первом запуске печатает одноразовый **токен настройки**; откройте <http://127.0.0.1:9999/> в браузере, введите токен и создайте первого администратора.

```bash
# From the release (or build it from source with ./mvnw clean package, output in dist/)
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# Or with Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# Or with Compose against a database
docker compose -f deploy/compose/postgres.yml up -d

# Or on Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

По умолчанию используется встроенная файловая база H2, поэтому для запуска никакая настройка не нужна. Драйвер MySQL не поставляется с релизом из-за его лицензии GPL; положите его в `drivers/`. Установка, первоначальная настройка и ваша первая выдача разрешения описаны в [документации](https://grantforge.devlive.org).

## Базы данных

По умолчанию используется встроенная файловая база H2 (`${GRANTFORGE_HOME}/data`), поэтому для запуска настройка не нужна. В продакшене переключение выполняется через переменные окружения; схемой управляет Liquibase:

| База данных | Версии (проверено в CI) | Пример `GRANTFORGE_DB_URL` |
| --- | --- | --- |
| PostgreSQL | 14, 17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0, 8.4 | `jdbc:mysql://host:3306/grantforge` (добавьте `mysql-connector-j` в `lib/`; из-за лицензии GPL он не входит в релиз) |
| MariaDB | 10.11, 11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

Также задайте `GRANTFORGE_DB_USER` и `GRANTFORGE_DB_PASSWORD`; каждый экземпляр в кластере должен задать собственный `GRANTFORGE_ID_NODE` (0-1023).

## Структура проекта

Корневая координата Maven: `org.devlive.grantforge:grantforge:2026.0.0`. Префикс пакета Java: `org.devlive.grantforge`. Главный класс: `org.devlive.grantforge.server.GrantForge`.

`core/` содержит сервер и общую инфраструктуру, `plugins/` — плагины типов служб, загружаемые сервером, а `agents/` — агенты, размещаемые внутри защищаемых ими систем. Общая библиотека `grantforge-agent-core` остаётся в `core/`; агент HDFS NameNode находится в `agents/grantforge-agent-hdfs`.

| Модуль | Назначение |
| --- | --- |
| `core/grantforge-server` | Точка входа Spring Boot: REST API, конфигурация безопасности, открытый API и раздача веб-консоли |
| `core/grantforge-web` | Консоль на Vue 3 / TypeScript / Tailwind CSS |
| `core/grantforge-common` | Коды ошибок и problem details, CSV, аннотации доступа к эндпоинтам |
| `core/grantforge-persistence` | Сущности, фильтрация по тенантам, TSID, Liquibase, SPI разрешений на данные и поля |
| `core/grantforge-audit` | Запись, запрос, хранение и архивирование событий аудита |
| `core/grantforge-identity` | Тенанты, учётные записи, подразделения, группы, должности, вход и сессии, двухфакторная аутентификация, источники идентичности |
| `core/grantforge-authz` | Каталог ресурсов, роли, разрешения, назначения и оценка, политики данных и полей, разделение обязанностей, запросы и проверки |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | Контракт плагина типа службы, а также загрузка, изоляция и вызов плагинов |
| `core/grantforge-policy-engine` | Движок оценки политик для внешних систем (API на Java 8, встраивается в агенты) |
| `core/grantforge-agent-core` | Общий код агентов: настройки, подписанные снимки, решения о доступе, отправка данных аудита |
| `core/grantforge-service` | Сервисы данных, подписание и распространение снимков политик, агенты и аудит доступа |
| `core/grantforge-oauth` | Сервер OAuth 2.1 / OIDC на базе Spring Authorization Server |
| `plugins/grantforge-plugin-hdfs` | Плагин типа службы HDFS: управление политиками и поиск ресурсов |
| `plugins/grantforge-plugin-example` | Пример плагина для собственного типа службы |
| `agents/grantforge-agent-hdfs` | Агент Hadoop 3.5.0 NameNode: накладная авторизация и аудит доступа |
| `sdk/grantforge-spring-boot-starter`, `sdk/grantforge-js` | SDK для Java и JavaScript для интеграции приложений |
| `script/ci`, `deploy/` | Скрипты проверок CI (те же локально и в CI) и ресурсы развёртывания (Dockerfile, Compose, Helm) |

## Эксплуатация и наблюдаемость

- Зонды готовности: `/actuator/health/liveness`, `/actuator/health/readiness` (только статус, без деталей; readiness возвращает 200, когда база данных доступна и миграции выполнены).
- Метрики: `/actuator/prometheus` (с меткой `application="grantforge"`, по умолчанию требуется вход; откройте для доверенных сетей через `GRANTFORGE_PROMETHEUS_PUBLIC=true`).
- Логирование: по умолчанию читаемый текст с идентификатором запроса в каждой строке; задайте `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (или `logstash`) для логов в JSON.
- Скрипты релиза: `bin/startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` и `import-legacy.sh`.

## Разработка и проверка

Для сборки нужен JDK 17 или новее (байт-код Java 17; движок политик нацелен на Java 8); Error Prone + NullAway включаются автоматически на JDK 21+. Фронтенд использует Vue 3.5, Tailwind CSS 4, Node.js 22.12+ и pnpm 8.10.2.

```sh
# Java build and unit tests (skipping the console build)
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# Persistence integration tests on a given database (needs Docker, except h2)
bash script/ci/db_integration.sh postgres:17

# Package the release (including the console build) into dist/
./mvnw clean package

# Front-end development and checks
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# Sample applications and SDKs
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# API contract: regenerate openapi.json and the front-end types after a server change (CI checks both)
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# Documentation site (docs/, Next.js + Tailwind CSS)
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # regenerate the screenshots with a real service and sample data

# Repository checks (the same ones CI runs)
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## Ссылки

- [Репозиторий](https://github.com/devlive-community/grantforge)
- [Документация](https://grantforge.devlive.org): быстрый старт, руководство пользователя, интеграция и технические справочники, исходники в [`docs/`](docs/)
- [Участие](CONTRIBUTING.md) · [Кодекс поведения](CODE_OF_CONDUCT.md) · [Журнал изменений](CHANGELOG)
