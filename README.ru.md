<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="Логотип GrantForge" />

# GrantForge

Унифицированная платформа разрешений · пользователи, роли, меню, API, строки и поля данных · внешние системы данных

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · Русский · [한국어](README.ko.md) · [日本語](README.ja.md) · [Deutsch](README.de.md) · [Французский](README.fr.md) · [Испанский](README.es.md) · [Португальский](README.pt-BR.md) · [Итальянский](README.it.md)

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

Поддержка внешних систем включает фреймворк плагинов, редактор политик, подписанное распространение, аудит доступа и плагины сервисов HDFS, ZooKeeper, MySQL, Hive, Kafka, HBase, Trino, YARN, Pulsar, Solr, ClickHouse и PostgreSQL. HDFS использует нумерованные агенты NameNode, ZooKeeper — агент внутри сервера, MySQL — отдельный сетевой прокси, Hive — авторизатор внутри HiveServer2, Kafka — авторизатор внутри брокеров и контроллеров, HBase — сопроцессор внутри master и region server, Trino — системный контроль доступа внутри coordinator, YARN — провайдер авторизации внутри ResourceManager, Pulsar — провайдер авторизации внутри брокеров, Solr — плагин авторизации внутри узлов, а ClickHouse — сервис рядом с серверами, который синхронизирует собственные роли и политики строк ClickHouse. PostgreSQL использует сервис рядом с серверами, который синхронизирует собственные роли, привилегии и политики защиты строк PostgreSQL. Подключение, поиск баз данных, таблиц и столбцов и возможности политик описаны в [руководстве по плагину MySQL](https://grantforge.devlive.org/ru/plugins/mysql/); развёртывание и ограничения применения политик — в [руководстве по прокси-агенту MySQL](https://grantforge.devlive.org/ru/external/mysql-agent/). Hive описан в [руководстве по плагину Hive](https://grantforge.devlive.org/ru/plugins/hive/) и [руководстве по агенту Hive](https://grantforge.devlive.org/ru/external/hive-agent/). Kafka описана в [руководстве по плагину Kafka](https://grantforge.devlive.org/ru/plugins/kafka/) и [руководстве по агенту Kafka](https://grantforge.devlive.org/ru/external/kafka-agent/). HBase описана в [руководстве по плагину HBase](https://grantforge.devlive.org/ru/plugins/hbase/) и [руководстве по агенту HBase](https://grantforge.devlive.org/ru/external/hbase-agent/). Trino описан в [руководстве по плагину Trino](https://grantforge.devlive.org/ru/plugins/trino/) и [руководстве по агенту Trino](https://grantforge.devlive.org/ru/external/trino-agent/). YARN описан в [руководстве по плагину YARN](https://grantforge.devlive.org/ru/plugins/yarn/) и [руководстве по агенту YARN](https://grantforge.devlive.org/ru/external/yarn-agent/). Pulsar описан в [руководстве по плагину Pulsar](https://grantforge.devlive.org/ru/plugins/pulsar/) и [руководстве по агенту Pulsar](https://grantforge.devlive.org/ru/external/pulsar-agent/). Solr описан в [руководстве по плагину Solr](https://grantforge.devlive.org/ru/plugins/solr/) и [руководстве по агенту Solr](https://grantforge.devlive.org/ru/external/solr-agent/). ClickHouse описан в [руководстве по плагину ClickHouse](https://grantforge.devlive.org/ru/plugins/clickhouse/) и [руководстве по агенту ClickHouse](https://grantforge.devlive.org/ru/external/clickhouse-agent/). PostgreSQL описан в [руководстве по плагину PostgreSQL](https://grantforge.devlive.org/ru/plugins/postgresql/) и [руководстве по агенту PostgreSQL](https://grantforge.devlive.org/ru/external/postgresql-agent/).


## Поддерживаемые компоненты и версии

Версии ниже проверяются интеграционными тестами Testcontainers в репозитории. Другие версии не сертифицированы.

| Компонент | Сервисный плагин | Проверенные версии | Агент применения | Версии агента | Java агента |
| --- | --- | --- | --- | --- | --- |
| Apache Hadoop HDFS | `plugins/grantforge-plugin-hdfs` | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 | Агент NameNode | 2.7, 2.10, 3.2, 3.3, 3.4, 3.5 | 8 (3.5: 17) |
| Apache Hadoop YARN | `plugins/grantforge-plugin-yarn` | 3.2.4, 3.3.6, 3.4.3, 3.5.0 | Провайдер авторизации ResourceManager | Проверено на тех же линиях Hadoop | 8 |
| Apache Hive | `plugins/grantforge-plugin-hive` | HiveServer2 3.1.3, 4.0.1, 4.1.0 | Авторизатор HiveServer2 | Проверено на тех же линиях HiveServer2 | 8 |
| Apache Kafka | `plugins/grantforge-plugin-kafka` | 3.7.2, 3.9.2, 4.1.2, 4.3.1 | Авторизатор брокеров и контроллеров | Проверено на тех же линиях Kafka | 8 |
| Apache Pulsar | `plugins/grantforge-plugin-pulsar` | 3.0.17, 3.3.9, 4.0.14, 4.2.5 | Провайдер авторизации брокеров | Проверено на тех же линиях Pulsar | 17 |
| Apache Solr | `plugins/grantforge-plugin-solr` | 9.8.1, 9.10.1, 9.11.0, 10.0.0 | Плагин авторизации узлов | Проверено на тех же линиях Solr | 11 |
| ClickHouse | `plugins/grantforge-plugin-clickhouse` | 24.8, 25.3, 25.8, 26.3, 26.8 | Сервис синхронизации ролей и политик строк | Проверено на тех же линиях ClickHouse | 17 |
| PostgreSQL | `plugins/grantforge-plugin-postgresql` | 15, 16, 17, 18 | Сервис, синхронизирующий роли, привилегии и политики защиты строк | Проверено на тех же версиях PostgreSQL | 17 |
| Apache HBase | `plugins/grantforge-plugin-hbase` | 2.5.16, 2.6.7, 3.0.0 | Координатор мастеров и region server | Проверено на тех же линиях HBase | 8 |
| Trino | `plugins/grantforge-plugin-trino` | 470, 476, 479, 484 | Системный контроль доступа | 470 (470–478), 479 (479 и новее) | 23 / 25 |
| MySQL | `plugins/grantforge-plugin-mysql` | 8.0, 8.4 (MySQL 5.7 и серверы MariaDB не поддерживаются) | Самостоятельный сетевой прокси | Один JAR прокси | 17 |
| Apache ZooKeeper | `plugins/grantforge-plugin-zookeeper` | Не указан в тестах репозитория | Агент внутри сервера | 3.6.4 (цель сборки) | 8 |

Столбец **Java агента** показывает цель байт-кода каждого агента. Агенты HDFS выбираются по линии Hadoop (например, `agents/hdfs/2.10/` для Hadoop 2.10.2); см. [руководство по агенту HDFS](https://grantforge.devlive.org/ru/external/hdfs-agent/). Hadoop 2.7, 2.10, 3.2 и 3.3 не предоставляют callback авторизации суперпользователя, поэтому на этих линиях доступ суперпользователя по-прежнему контролирует Hadoop.

## Как это работает: две плоскости

- **Управляющая плоскость**: сервер GrantForge (Spring Boot 4.1, байт-код Java 17) и консоль Vue 3 владеют тенантами, учётными записями, организацией, ролями, разрешениями, аудитом, сервисами данных и политиками.
- **Плоскость данных**: агенты, встроенные в защищаемую систему или развёрнутые как отдельные сетевые прокси. Они получают подписанные Ed25519 снимки политик по своим токенам, кэшируют их локально, проверяют права доступа к ресурсам и отправляют события аудита. При временной недоступности сервера продолжает действовать уже применённый снимок; границы проверок описаны в руководстве каждого агента.

Вашим системам не обязательно следовать модели HDFS. Обычные приложения оценивают разрешения в своём процессе через открытый API или Spring Boot starter; только системам, которые должны перехватывать доступ внутри базы данных, файловой системы или аналогичного хранилища либо в сетевом пути доступа к ним, нужен агент, написанный поверх `core/grantforge-agent-core`.

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


## Эксплуатация и наблюдаемость

- Зонды готовности: `/actuator/health/liveness`, `/actuator/health/readiness` (только статус, без деталей; readiness возвращает 200, когда база данных доступна и миграции выполнены).
- Метрики: `/actuator/prometheus` (с меткой `application="grantforge"`, по умолчанию требуется вход; откройте для доверенных сетей через `GRANTFORGE_PROMETHEUS_PUBLIC=true`).
- Логирование: по умолчанию читаемый текст с идентификатором запроса в каждой строке; задайте `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (или `logstash`) для логов в JSON.
- Скрипты релиза: `bin/startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` и `import-legacy.sh`.

## Разработка и проверка

Для сборки нужен JDK 17 или новее. Сервер и адаптер Hadoop 3.5 рассчитаны на Java 17; движок политик, ядро агента и адаптеры Hadoop 2.7–3.4 — на Java 8. Error Prone + NullAway включаются начиная с JDK 21. Фронтенд использует Vue 3.5, Tailwind CSS 4, Node.js 22.12+ и pnpm 8.10.2.

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
