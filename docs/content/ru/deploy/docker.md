---
title: Docker, Compose и Helm
description: Запуск GrantForge в контейнерном образе, проверка работы с разными базами данных через Compose и развёртывание в Kubernetes с помощью Helm.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Образ

Каждый релиз публикует образ `ghcr.io/devlive-community/grantforge:<版本>` (linux/amd64 и linux/arm64); стабильные релизы также обновляют `latest`:

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

Образ собран из дистрибутива на базе `eclipse-temurin:21-jre`, работает под непривилегированным пользователем (UID 10001), пишет логи в консоль и поставляется без базы данных. Вы также можете собрать его из исходного кода:

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

Соглашения образа:

| Путь / переменная | Описание |
| --- | --- |
| `/opt/grantforge/data` | Том: файлы данных встроенной H2 |
| `/opt/grantforge/plugins` | Том: плагины типов сервисов |
| `/opt/grantforge/drivers` | Дополнительные JDBC-драйверы (сюда помещается MySQL Connector/J) |
| `9999` | Порт сервиса |
| `HEALTHCHECK` | Вызывает `/actuator/health/readiness` |

## Примеры Compose

В `deploy/compose/` лежит по одному примеру для каждой базы данных: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver` и `oracle`.

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

Затем откройте http://127.0.0.1:9999/. Пароль базы данных по умолчанию, используемый в примерах, предназначен только для оценки; перед реальным использованием замените его через `GRANTFORGE_DB_PASSWORD`. Для примера MySQL сначала поместите `mysql-connector-j-<版本>.jar` в `deploy/compose/drivers/`.

## Helm

`deploy/helm/grantforge` — это Helm-чарт: StatefulSet и внешняя база данных, при этом каждая реплика получает собственный номер ID-узла на основе порядкового номера Pod (требуется Kubernetes 1.28 или новее).

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

Основные параметры:

| Параметр | По умолчанию | Описание |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / appVersion чарта | Образ |
| `replicaCount` | `1` | Число реплик; может быть больше 1 |
| `database.url` / `username` / `password` | — | Подключение к базе данных; пароль следует передавать через `existingSecret` |
| `setupToken` | пусто | Задать токен настройки заранее; если пусто, он выводится в лог |
| `encryptionKey` | пусто | Ключ Base64 длиной 32 байта для шифрования сохранённых секретов (пароли источников идентификации, секреты аутентификаторов, приватные ключи подписи и т. п.); если пусто, генерируется автоматически и сохраняется в базе данных |
| `cookieSecure` | `false` | Укажите `true`, если TLS завершается на ingress; cookie сессии всегда помечаются как Secure |
| `ingress.*` | отключено | Открывает доступ к консоли и API |
| `plugins.persistence.enabled` | `false` | Монтирует постоянный том для каталога плагинов |
| `podDisruptionBudget.enabled` | `false` | Рекомендуется при работе нескольких реплик |

> [!IMPORTANT]
> Всегда задавайте `encryptionKey` в продакшене. Если он не задан, ключ хранится в базе данных, поэтому любой, кто получит резервную копию базы, сможет расшифровать сохранённые в ней секреты.
