---
title: Агент Apache Hive
description: Установка агента в HiveServer2 для проверки операторов, фильтрации SHOW, маскирования столбцов и фильтрации строк по политикам GrantForge.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Агент Hive — авторизатор API авторизации Hive, как и плагин Hive в Apache Ranger: HiveServer2 обращается к нему перед каждым оператором, а он отвечает по подписанным политикам сервиса данных `hive`. Дистрибутив поставляет его одним совместимым с Java 8 JAR-файлом `agents/hive/grantforge-agent-hive-<version>.jar` с перенесёнными внутрь зависимостями.

Интеграционные тесты Testcontainers в репозитории запускают JAR внутри HiveServer2 `3.1.3`, `4.0.1` и `4.1.0`. Это не означает сертификации остальных версий. Плагин сервиса описан в разделе [Apache Hive](/ru/plugins/hive/).

## Как это работает

- **Операторы проверяются.** Для каждого объекта, который оператор читает или пишет, агент запрашивает у политик тип доступа, нужный операции, как Ranger; первый отказ завершает оператор ошибкой `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`. Запросы проверяются по столбцам.
- **Списки фильтруются.** `SHOW DATABASES` и `SHOW TABLES` показывают только то, что пользователь может использовать: любой доступ к базе данных или таблице либо к чему-то внутри них. `USE` и `DESCRIBE DATABASE` требуют того же.
- **Запросы маскируются и фильтруются.** Если у пользователя есть маскированные столбцы или фильтр строк в таблице, HiveServer2 переписывает запрос с масками и условием, поэтому соединения, условия и функции видят только то, что пользователю разрешено видеть.
- **Маски нельзя обойти.** Запись, изменение или экспорт таблицы, для которой у пользователя есть маска или фильтр строк, отклоняются, как Ranger делает по умолчанию.
- **Права и роли принадлежат GrantForge.** `GRANT`, `REVOKE`, `SHOW GRANT`, `CREATE ROLE`, `SET ROLE` и остальные операторы ролей Hive отклоняются; `SHOW CURRENT ROLES` показывает роли, которые GrantForge назначил пользователю.
- **Небезопасные операторы отклоняются.** Команды `dfs`, которые обращались бы к файловой системе с правами самого HiveServer2, отклоняются. В сеансах HiveServer2 запрещены скрипты `TRANSFORM`, а пользователи могут менять через `SET` только параметры, разрешённые стандартной SQL-авторизацией Hive.
- **Каждое решение сообщается** в GrantForge с пользователем, адресом клиента, ресурсом, типом доступа, результатом, операцией и оператором.

| Операция | Тип доступа |
| --- | --- |
| Запросы, `SHOW CREATE TABLE`, `SHOW PARTITIONS`, `SHOW TBLPROPERTIES`, `DESCRIBE`, `SHOW COLUMNS`, `ANALYZE` | `select` |
| `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `LOAD` (цель) | `update` |
| `CREATE DATABASE`, `CREATE TABLE`, `CREATE VIEW`, `CREATE FUNCTION` | `create` для создаваемого, `select` для читаемого |
| `ALTER …`, `MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`, `UNLOCK` | `lock` |
| `SHOW DATABASES`, `SHOW TABLES`, `USE`, `DESCRIBE DATABASE` | Любой доступ к объекту или к чему-то внутри него |
| `REPL DUMP`, `REPL LOAD`, `REPL STATUS` | `repladmin` |
| `KILL QUERY`, планы ресурсов, администрирование LLAP | `serviceadmin` для `hiveservice` |
| Временные функции, `ADD JAR`, `COMPILE` | `tempudfadmin` для `global` |
| Места, которые оператор читает или пишет | `read` или `write` для `url` |

## Развёртывание

1. Добавьте сервис данных `hive` и настройте его политики, в том числе разрешающую всё необходимое пользователям, от имени которых HiveServer2 выполняет собственную работу, например `hive`. Откройте **Права доступа к данным → Агенты**, выберите сервис и выпустите токен агента. Он показывается один раз; сохраните его в файле, доступном для чтения только учётной записи HiveServer2.
2. Скопируйте JAR в `lib` **каждого HiveServer2**, например `/opt/hive/lib`.
3. Добавьте параметры в `hiveserver2-site.xml` или `hive-site.xml` каждого HiveServer2, заменив адрес, пути и имя экземпляра из примера:

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   Каталог кеша должен быть доступен для записи учётной записи HiveServer2. Как и с Ranger, `hive.server2.enable.doAs=false` заставляет HiveServer2 читать и писать данные от своей учётной записи, так что кто что видит, решают политики.
4. Перезапустите HiveServer2. Агент запускается с первым сеансом и останавливается вместе с сервером, отправляя или сохраняя в очередь ожидающие события.
5. **Закройте обходные пути мимо HiveServer2:** агент проверяет только операторы, выполняемые в HiveServer2. Клиенты, обращающиеся напрямую к metastore или к файлам хранилища, например Hive CLI, Spark или клиенты HDFS, не проверяются; защитите их собственными правами metastore и HDFS или [агентом HDFS](/ru/external/hdfs-agent/).

| Параметр | Назначение |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Обязательные: сервер GrantForge, файл токена агента, имя этого HiveServer2 и доступный для записи каталог для подписанного снимка и событий в очереди |
| `grantforge.signing.key.file` | Необязательный файл, закрепляющий ключ подписи снимков; скопируйте открытый ключ Ed25519 X.509 в кодировке Base64 со страницы агентов. Если не задан, ключ запрашивается у сервера при первом обращении |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Тайм-ауты подключения и чтения к GrantForge, по умолчанию `5000` / `8000` миллисекунд |
| `grantforge.refresh.interval.ms` | Как часто агент запрашивает новые политики, по умолчанию `30000`, не меньше `1000` миллисекунд |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | События доступа: размер пакета (по умолчанию `500`), размер очереди (по умолчанию `10000`), интервал отправки (по умолчанию `5000` миллисекунд) и предел очереди на диске (по умолчанию 64 МиБ) |

Агент читает параметры из конфигурационных файлов HiveServer2 и из системных свойств (`-Dgrantforge.…`, они имеют приоритет), но никогда — из параметров сеанса, которые может задать клиент.

## Пользователи, группы и роли

Политики называют пользователя, которого HiveServer2 назначает сеансу: имя, с которым входит клиент, или краткое имя его принципала Kerberos (`alice` для `alice@EXAMPLE.COM`). Группы — это группы, которые сопоставление групп Hadoop даёт пользователю в HiveServer2, вместе с группами, в которые GrantForge включает пользователя; роли — те, что GrantForge назначает пользователю.

## Решения и ограничения

- Без пригодного снимка или если политики невозможно прочитать, отклоняется любой оператор. Неиспользуемый сервис оставляет HiveServer2 без проверок, как без авторизатора.
- Токен, привязанный к сервису с типом, отличным от `hive`, отклоняет всё.
- Места, которые читают и пишут операторы, определяют политики `url`; в отличие от Ranger, агент не обращается к правам файловой системы.
- Операторы, для которых Hive не называет объект, проверяются для всех баз данных: операторам `REPL` без объекта нужен `repladmin`, смене владельца таблицы или базы без объекта — `alter`, для базы данных `*`.
- `EXPLAIN`, `DESCRIBE FUNCTION`, `SHOW FUNCTIONS`, `SET` и другие операторы, которые не проверяет Ranger, тоже не проверяются; вызовы метаданных клиентов JDBC, например `getTables`, не фильтруются.
- Агент работает в HiveServer2. Авторизация внутри metastore не охватывается.

## Связанные руководства

- [Apache Hive](/ru/plugins/hive/)
- [Сервисы данных, политики и агенты](/ru/external/data-services/)
