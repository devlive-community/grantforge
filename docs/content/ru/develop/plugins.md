---
title: Плагины и типы сервисов
description: "Расширение управления политиками GrantForge на внешние системы данных с помощью плагинов типов сервисов: контракты, упаковка, изоляция и распространение."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Плагин типа сервиса описывает внешнюю систему: какие у неё уровни ресурсов, какие типы доступа она поддерживает, возможны ли маскирование и фильтрация строк, а также какая конфигурация требуется подключению. На основе этого описания GrantForge предоставляет для таких систем сервисы данных, универсальный редактор политик, снапшоты политик и аудит доступа (см. [Сервисы данных, политики и агенты](/ru/external/data-services/)).

## Зависимости

Плагин зависит только от `grantforge-plugin-api` (который сам зависит лишь от JDK и JSpecify), подключаемой со scope `provided`:

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## Реализация ServiceTypeProvider

```java
public final class ExampleProvider implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("example").label("Example warehouse")
                .resources(ResourceDefinition.builder("database").label("Database").lookupSupported(true).validLeaf(true)
                                .excludesSupported(false).build(),
                        ResourceDefinition.builder("table").label("Table").parent("database").lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder("column").label("Column").parent("table").accessTypes("select").build(),
                        ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "select", "update"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", "redact({col})"))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .configFields(ConfigField.builder("url").label("Address").type(ConfigFieldType.STRING).mandatory().pattern("example://.+").build(),
                        ConfigField.builder("timeout").label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return "example".equals(config.get("password")) ? ConnectionResult.succeeded()
                : ConnectionResult.failed("the example warehouse refused the password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        // 返回 request.resource() 层级下以 request.userInput() 开头的候选值，最多 request.limit() 个
        return List.of();
    }
}
```

Приведённый выше код — фрагмент примера плагина `plugins/grantforge-plugin-example`; используйте его прямо как шаблон.

Определение проверяется один раз при конструировании и сразу сообщает обо всех проблемах: неизвестные или циклические родители, повторяющиеся имена, ссылки на необъявленные типы доступа или ресурсы и т. д. Имена должны соответствовать шаблону `[a-z][a-z0-9_-]{0,63}`.

| Часть | Описание |
| --- | --- |
| Ресурсы | Иерархия, сопоставление (точное, шаблон, путь, регулярное выражение), чувствительность к регистру, обязательность, поддержка исключений и рекурсии (только для путей), поддержка поиска и возможность быть листом |
| Типы доступа | Имя, отображаемое имя, подразумеваемые типы доступа (например `all` подразумевает `select`), при необходимости — ограничение ресурсами |
| Маскирование, фильтр строк | Какие ресурсы их поддерживают и какие типы маскирования существуют; применение выполняется в целевой системе |
| Условия | Условия, которые политика может приложить (например диапазон IP), обрабатываемые через condition SPI движка политик |
| Поля конфигурации | Строка, длинный текст, целое число, булево значение, секрет, перечисление; секретные поля хранятся в зашифрованном виде и не могут иметь значений по умолчанию |

Провайдер должен иметь конструктор без аргументов и быть потокобезопасным. У `validateConfig`, `testConnection` и `lookup` есть реализации по умолчанию; переопределяйте их при необходимости.

### Если поиск не удался

Если `lookup` не удался, выбросьте `LookupException` с причиной: консоль покажет причину и позволит повторить поиск, а не покажет пустой результат. Сообщение должно быть в одну строку и без секретов; сервер также скрывает в нём секретные настройки сервиса.

- `NOT_FOUND`: места поиска не существует, например настроенного каталога поиска
- `ACCESS_DENIED`: целевая система отказала пользователю поиска
- `UNREACHABLE`: целевая система недоступна
- `AUTHENTICATION_FAILED`: не удалось войти в целевую систему
- `LIMIT_EXCEEDED`: значений слишком много, поиск нужно сузить
- `INVALID_INPUT`: ввод нельзя найти, например путь вне разрешённого каталога
- `FAILED`: любая другая ошибка

Любое другое исключение плагина считается `FAILED`, поэтому плагины для API 1.0 не нужно менять. `LookupException` доступен начиная с API 1.1.0.

### Просмотр каталогов

Уровень путей может позволять администраторам выбирать значения, просматривая каталоги: объявите на уровне `browseSupported(true)` (только с сопоставлением `PATH`) и реализуйте `browse(BrowseRequest)`, возвращающий одну `BrowsePage`: начальный каталог `root`, выведенный каталог, его элементы и курсор `nextCursor` следующей страницы (`null` на последней). Каждый `BrowseEntry` содержит имя, значение для политики, признак каталога и, при наличии, владельца, группу, права, размер и время изменения.

Курсор принадлежит плагину; сервер возвращает его без изменений. Сохраняйте порядок от страницы к странице, не пропуская и не повторяя элементы. На странице не больше 500 элементов, а страницу больше запрошенной сервер считает ошибкой плагина. Если целевая система не умеет постранично выводить данные и каталог превышает лимит сканирования, выбросьте `LIMIT_EXCEEDED`, а не возвращайте часть. Ошибки просмотра используют `LookupException`, как и поиск.

## Дескриптор и упаковка

Поместите `grantforge-plugin.yaml` в корень плагина:

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.1"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

`version` — собственная версия плагина, которую выбирает автор; плагины, поставляемые с GrantForge, например HDFS и пример плагина, выходят вместе с продуктом и несут его версию, которую подставляет сборка. `apiVersion` — версия контракта, нужная плагину; см. «Совместимость» ниже.

Плагин может быть:

- jar-файлом с дескриптором в его корне;
- каталогом или zip-архивом: `grantforge-plugin.yaml`, `classes/` и `lib/*.jar`.

Поместите его в `grantforge.plugins.directory` (по умолчанию `plugins`) и нажмите Rescan на странице «Plugins» консоли; перезапуск не требуется.

Если плагин имеет зависимости, упакуйте его как `plugins/grantforge-plugin-hdfs`: с помощью assembly соберите zip-архив с классификатором `plugin` (дескриптор на верхнем уровне, плюс `classes/` и `lib/`) и скопируйте runtime-зависимости в `target/plugin-lib` на фазе `generate-resources`:

```xml
<plugin>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>plugin-lib</id>
      <phase>generate-resources</phase>
      <goals><goal>copy-dependencies</goal></goals>
      <configuration>
        <includeScope>runtime</includeScope>
        <outputDirectory>${project.build.directory}/plugin-lib</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## При запуске из исходного кода

При прямом запуске `org.devlive.grantforge.server.GrantForge` в IDE классы сервера берутся из `target/classes` каждого модуля. В этом случае, если `grantforge.plugins.directory` не настроен и в рабочем каталоге нет каталога `plugins`, используется каталог `plugins/` репозитория: собранные там модули плагинов (дескриптор в `target/classes` и `target/plugin-lib`, созданный сборкой) загружаются напрямую как плагины — классы берутся из `target/classes`, а зависимости из `target/plugin-lib`; модули, не создающие `plugin-lib` (например, пример плагина, используемый для тестов), не загружаются. После изменения кода плагина перекомпилируйте его в IDE и нажмите Rescan на странице «Plugins» консоли, чтобы изменения вступили в силу. Перед первым использованием модуля плагина соберите его один раз с помощью Maven, чтобы скопировать зависимости:

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## Совместимость

`apiVersion` объявляет версию контракта, необходимую плагину. Хост сейчас предоставляет `1.1.0`; плагин загружается только если мажорная версия совпадает и предоставленная версия не ниже требуемой, иначе он помечается как «несовместимый». Любое изменение контракта повышает версию; CI сравнивает с предыдущим релизом с помощью japicmp (`script/ci/check_plugin_api_compat.py`), а несовместимые изменения требуют повышения мажорной версии.

GrantForge 2026.1.0 предоставляет API плагинов 1.1.0. Плагину с `apiVersion: "1.1"` нужен хост 2026.1.0 или новее; плагин с `1.0` работает на новом хосте без изменений. Версия продукта и версия API плагинов независимы: версия API растёт только при изменении контракта.

## Изоляция

- Каждый плагин получает собственный загрузчик классов, родителем которого является платформенный загрузчик классов; в хост делегируются только `org.devlive.grantforge.plugin.api.` и `org.jspecify.annotations.`. Плагин не видит классы Spring или сервера и может поставлять любые версии своих зависимостей.
- Ошибка чтения, несовместимая версия, дубликат, ошибка конструирования или таймаут лишь помечают плагин как неудачный и фиксируют причину; сервер продолжает работать.
- Каждый вызов в плагин ограничен таймаутом (`grantforge.plugins.call-timeout`, по умолчанию 10 секунд).

## Агенты и снапшоты

Плагины типов сервисов лежат в `plugins/` и загружаются сервером GrantForge; конкретные агенты лежат в `agents/` и после упаковки развёртываются в целевых системах, например `agents/grantforge-agent-hdfs-*` — в HDFS NameNode. Общая инфраструктура агентов расположена в `core/grantforge-agent-core`.

Агенты внутри целевых систем используют токен агента для доступа к `/api/v1/agent/**`:

| Эндпоинт | Назначение |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | Сообщает состояние агента и текущую версию снапшота |
| `GET /api/v1/agent/policies` | Загружает снапшот политик; возвращает 304, если он не изменился; ответ содержит подпись Ed25519 |
| `GET /api/v1/agent/signing-key` | Открытый ключ для проверки подписей |
| `POST /api/v1/agent/access-events` | Пакетно передаёт события доступа, пополняя аудит доступа |

Агенты выполняют локальную оценку с помощью `grantforge-policy-engine` (API на Java 8, которое можно встроить в старые системы) и не обязаны вызывать GrantForge при каждом обращении.

Агентам не нужно самим реализовывать эти протоколы; `core/grantforge-agent-core` (Java 8) уже оборачивает их:

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // 条件求值器，按名称

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- Отправляет heartbeat с интервалом, требуемым сервером; загружает снапшот при изменении версии политики (304, если ETag не изменился), проверяет его открытым ключом Ed25519 сервера (закрепите ключ в настройках либо получите и сохраните его с сервера при первом контакте), заменяет локальную копию только после успешной проверки и сохраняет её в каталоге кэша; при запуске, когда сервер недоступен, используется последний снапшот.
- Снапшот разворачивает роли и группы до пользователей, а `decide` добавляет к запросу роли и группы пользователя из снапшота. Если сервер отключён или снапшота ещё нет, результат — `NOT_DETERMINED`, и агент сам решает, перейти ли на собственные проверки системы или отказать.
- События доступа попадают в ограниченную очередь (при переполнении отбрасываются и подсчитываются, никогда не блокируя систему) и отправляются пакетами; когда сервер недоступен, они записываются в `audit-spool/` в каталоге кэша и повторно отправляются после восстановления связи, причём самые старые события отбрасываются при превышении предела.
- Зависимости — Jackson 2 и Bouncy Castle (Ed25519 появился только в JDK 15); в целевых системах поставляются другие версии этих библиотек, поэтому пакет агента должен переносить их через shade.

## Пример

`plugins/grantforge-plugin-example` — полноценный плагин: тип `example` (database → table → column, плюс path), типы доступа select, update и all, маскирование столбцов, фильтрация строк по таблице, условие по диапазону IP и конфигурация url, timeout и password (тест подключения успешен, если пароль — `example`); он также позволяет искать примеры баз данных и таблиц. Сквозной end-to-end тест использует его, чтобы пройти весь путь «добавить сервис → написать политику → выдать токен → агент забирает снапшот → аудит доступа».
