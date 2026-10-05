---
title: 数据库
description: 支持的数据库与版本、连接方式、驱动和多数据库注意事项。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 支持范围

| 数据库 | 验证的版本 | 驱动 |
| --- | --- | --- |
| H2 | 随版本内置 | 内置，仅建议试用 |
| PostgreSQL | 14、17 | 内置 |
| MySQL | 8.0、8.4 | 需要自行放入 `drivers/`（Connector/J 采用 GPL 许可，不随发行包分发） |
| MariaDB | 10.11、11.4 | 内置 |
| Oracle | Free 23 | 内置 |
| SQL Server | 2022 | 内置 |

每个版本都在 CI 中执行“空库初始化 + 全部集成测试”。国产数据库（达梦、金仓、openGauss、OceanBase 等）不在支持范围内。

## 连接示例

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle（服务名）
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

用户名与密码分别用 `GRANTFORGE_DB_USER` 与 `GRANTFORGE_DB_PASSWORD` 设置。数据库需要事先创建，账号需要建表权限：首次启动时 GrantForge 用 Liquibase 创建全部表，之后的版本升级也由 Liquibase 自动迁移。Hibernate 只校验表结构，从不修改它。

## 字符集

- **MySQL / MariaDB**：创建数据库时使用 `utf8mb4` 字符集，中文与表情符号才能完整保存。
- **SQL Server、Oracle**：可能包含中文的文本列使用 `NVARCHAR`，长文本在 SQL Server 上是 `NVARCHAR(MAX)`、Oracle 上是 `CLOB`，与数据库的默认字符集无关。
- **Oracle**：空字符串会被视为 `NULL`，GrantForge 在领域层统一把空白值当作“未填写”，行为与其他数据库一致。

## 备份与恢复

所有业务数据都在数据库里（会话也在），备份数据库即可；如果使用插件，同时备份 `plugins/`。未设置 `grantforge.security.encryption-key` 时，加密密钥也保存在数据库中，恢复备份即可解密；设置了密钥则需要同时保管好这把密钥。
