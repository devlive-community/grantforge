---
title: Справочник конфигурации
description: Все ключи конфигурации, их значения по умолчанию и соответствующие переменные окружения.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Конфигурацию можно записывать в `configure/application.properties` или переопределять переменными окружения. Действуют правила слабого связывания Spring Boot: `grantforge.security.mfa.step-up-window` можно задать переменной окружения `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`. Длительности записываются в виде `30m`, `12h` и `90d`.

## Сервис и база данных

| Ключ | Переменная окружения | По умолчанию | Описание |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | HTTP-порт |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | встроенная файловая база данных H2 | JDBC URL, см. [Базы данных](/ru/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | Пользователь базы данных |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | пусто | Пароль базы данных |
| — | `GRANTFORGE_HOME` | каталог установки | Каталог с данными и журналами H2 |
| — | `GRANTFORGE_ID_NODE` | автоматически | Номер узла, уникальный для каждого экземпляра в кластере (0–1023) |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | Ограничение размера файлов при импорте CSV |

## Настройка и регистрация

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `grantforge.setup.token` | пусто | Фиксированный токен настройки (`GRANTFORGE_SETUP_TOKEN`); если он пуст, генерируется случайный и выводится в лог |
| `grantforge.security.registration-enabled` | `false` | Могут ли посетители регистрироваться самостоятельно |
| `grantforge.security.registration-tenant` | `default` | Тенант, которому принадлежат самостоятельно зарегистрированные учётные записи |

## Пароли и блокировки

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | Минимальная длина, не менее 8 |
| `grantforge.security.password.max-length` | `128` | Максимальная длина, не более 1024 |
| `grantforge.security.password.required-character-classes` | `1` | Число классов символов, которые должны быть смешаны (строчные, прописные, цифры, прочие), 1–4 |
| `grantforge.security.password.history-size` | `0` | Новый пароль не должен повторять последние N паролей, 0–24 |
| `grantforge.security.password.max-age` | без срока действия | Срок жизни пароля; по истечении срока пароль необходимо сменить при входе |
| `grantforge.security.lockout.max-attempts` | `5` | Число подряд неудачных попыток до блокировки |
| `grantforge.security.lockout.duration` | `15m` | Продолжительность блокировки |

Пароли не должны содержать имя пользователя.

## Сессии и cookie

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | Тайм-аут простоя сессии (`GRANTFORGE_SESSION_TIMEOUT`) |
| `grantforge.security.sessions.max-per-account` | `0` | Максимальное число одновременных сессий на учётную запись; 0 означает без ограничения |
| `grantforge.security.sessions.activity-interval` | `1m` | Интервал, с которым записывается последняя активность сессии |
| `grantforge.security.cookie-secure` | `false` | Установите `true`, если TLS завершается на прокси (`GRANTFORGE_COOKIE_SECURE`) |

## Двухэтапная проверка

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | На какое время одной двухэтапной проверки хватает для чувствительных операций — от 1 минуты до 12 часов |
| `grantforge.security.mfa.required-for-sensitive` | `false` | Требуется ли, чтобы у учётной записи была включена двухэтапная проверка для чувствительных операций |

## Шифрование и сервер авторизации

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `grantforge.security.encryption-key` | генерируется автоматически | 32-байтовый ключ Base64 для шифрования сохранённых секретов; в продакшене всегда задавайте его вручную |
| `grantforge.oauth.issuer` | URL запроса | Издатель OIDC, например `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | Период автоповорота ключа подписи; 0 отключает его |
| `grantforge.oauth.signing-key-retention` | `2d` | Как долго старые ключи остаются опубликованными; должно быть больше времени жизни любого токена |

## Аудит, плагины и агенты

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | Срок хранения журналов аудита |
| `grantforge.audit.archive-directory` | пусто | Каталог, в который устаревшие записи аудита архивируются перед удалением |
| `grantforge.access-audit.retention` | `90d` | Срок хранения аудита доступа, сообщаемого агентами |
| `grantforge.plugins.directory` | `plugins` | Каталог плагинов |
| `grantforge.plugins.call-timeout` | `10s` | Тайм-аут вызовов плагинов (тесты подключения, поиск ресурсов) |
| `grantforge.agents.refresh-interval` | `30s` | Интервал, с которым агентам рекомендуется подтягивать политики |

## Наблюдаемость

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | Доступен ли `/actuator/prometheus` без входа в систему (`GRANTFORGE_PROMETHEUS_PUBLIC`) |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Установите `ecs` или `logstash`, чтобы выводить журналы в формате JSON |
