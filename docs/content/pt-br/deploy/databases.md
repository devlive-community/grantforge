---
title: Bancos de dados
description: Os bancos de dados e versões suportados, a forma de conexão, os drivers e as particularidades de cada banco de dados.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Suporte

| Banco de dados | Versões verificadas | Driver |
| --- | --- | --- |
| H2 | inclusa com a versão | incluído; só é recomendado para experimentar |
| PostgreSQL | 14, 17 | incluído |
| MySQL | 8.0, 8.4 | precisa ser colocado à mão em `drivers/` (o Connector/J tem licença GPL e não é distribuído com o pacote de lançamento) |
| MariaDB | 10.11, 11.4 | incluído |
| Oracle | Free 23 | incluído |
| SQL Server | 2022 | incluído |

Cada versão é submetida na CI a uma "inicialização sobre um banco de dados vazio mais todos os testes de integração". Os bancos de dados chineses (DM, KingbaseES, openGauss, OceanBase, etc.) ficam fora do escopo suportado.

## Exemplos de conexão

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle (nome do serviço)
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

O nome de usuário e a senha são definidos com `GRANTFORGE_DB_USER` e `GRANTFORGE_DB_PASSWORD`, respectivamente. O banco de dados precisa ser criado antes, e a conta precisa de permissão para criar tabelas: no primeiro início, o GrantForge cria todas as tabelas com o Liquibase, e as migrações das versões posteriores também são executadas automaticamente pelo Liquibase. O Hibernate apenas valida a estrutura das tabelas, nunca a modifica.

No PostgreSQL, o GrantForge tenta ativar a extensão `pg_trgm` e cria um índice de trigramas sobre o nome de login, o nome de exibição e o e-mail, para que a busca "contém" sobre milhões de contas se mantenha em algumas dezenas de milissegundos. A partir do PostgreSQL 13, ela é uma extensão confiável e pode ser ativada pelo proprietário do banco de dados; se a conta não tiver essa permissão, o serviço inicia mesmo assim, a busca passa a ser uma varredura completa da tabela e, depois que um administrador executar `CREATE EXTENSION pg_trgm`, o índice é recriado automaticamente no próximo início.

## Conjuntos de caracteres

- **MySQL / MariaDB**: ao criar o banco de dados, use o conjunto de caracteres `utf8mb4`, para que o chinês e os emoji sejam guardados integralmente.
- **SQL Server, Oracle**: as colunas de texto que podem conter chinês usam `NVARCHAR`; o texto longo é `NVARCHAR(MAX)` no SQL Server e `CLOB` no Oracle, independentemente do conjunto de caracteres padrão do banco de dados.
- **Oracle**: a cadeia vazia é tratada como `NULL`, portanto o GrantForge considera os valores em branco como "não preenchidos" de forma uniforme na camada de domínio; o comportamento é o mesmo dos demais bancos de dados.

## Cópias de segurança e restauração

Todos os dados de negócio estão no banco de dados (as sessões também), então basta fazer uma cópia de segurança do banco de dados; se você usa plug-ins, copie também `plugins/`. Se `grantforge.security.encryption-key` não estiver definida, a chave de criptografia também está no banco de dados e restaurar a cópia é suficiente para descriptografar; se uma chave estiver definida, você terá de guardar também essa chave.
