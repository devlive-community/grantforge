---
title: MySQL
description: Instale e configure o plug-in de serviço MySQL, consulte bancos de dados, tabelas e colunas e gerencie políticas de acesso, mascaramento e filtragem de linhas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in MySQL oferece no GrantForge a consulta de bancos de dados, tabelas e colunas, além de políticas de acesso, mascaramento e filtragem de linhas. O ID do plug-in e o tipo de serviço são ambos `mysql`. O plug-in de serviço roda no servidor GrantForge; um [agente proxy do MySQL](/pt-br/external/mysql-agent/) independente verifica o SQL das aplicações.

O teste de conexão aceita MySQL 8.0 ou posterior. Os testes de integração com Testcontainers no repositório cobrem `mysql:8.0` e `mysql:8.4`; isso não certifica todas as outras versões. Servidores MariaDB e MySQL 5.7 não fazem parte do escopo de suporte. `jdbc:mariadb://` é um formato de URL do driver, não uma indicação de suporte a servidores MariaDB.

## Instalação

A distribuição inclui o plug-in em `plugins/mysql`. Confirme em **Gestão da plataforma → Plug-ins** que `mysql` está ativado; depois de atualizá-lo, verifique o diretório de plug-ins novamente. O plug-in conversa com o MySQL pelo MariaDB Connector/J (LGPL), que entende o protocolo do MySQL, incluindo `caching_sha2_password` e TLS, em um carregador de classes próprio. O Connector/J do próprio MySQL é GPL e não é distribuído.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e escolha MySQL (`mysql`).
2. Informe a URL JDBC do servidor, por exemplo `jdbc:mysql://mysql.example.com:3306`. `jdbc:mariadb://` funciona da mesma forma, assim como as formas de failover do driver, como `jdbc:mysql:replication://primary:3306,replica:3306`.
3. Informe o usuário de consulta e a senha, teste a conexão e salve o serviço. O teste faz login, verifica a versão do servidor e lê `information_schema` como as consultas fazem.

O usuário de consulta permite ao GrantForge descobrir recursos e é separado dos usuários com os quais os clientes das aplicações entram no MySQL. Um teste de conexão bem-sucedido não significa que o SQL das aplicações já esteja sujeito às políticas.

| Configuração | Finalidade |
| --- | --- |
| `jdbc.url` | URL JDBC obrigatória: `jdbc:mysql://host[:port]` ou `jdbc:mariadb://…`, com banco de dados e `?options` opcionais; credenciais não ficam na URL |
| `username` | Usuário de consulta obrigatório |
| `password` | Senha do usuário de consulta; armazenada criptografada |
| `jdbc.properties` | Opções do driver, um `key=value` por linha, como `sslMode=verify-full` e `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | Tempo limite de conexão, padrão `10000`, intervalo `1..600000` |
| `read.timeout.ms` | Tempo limite de leitura de uma consulta, padrão `30000`, intervalo `1..600000` |

Opções que fariam o servidor ler arquivos locais, executar comandos ao conectar, permitir vários comandos de uma vez, levar credenciais ou carregar classes são recusadas tanto na URL quanto em `jdbc.properties`, independentemente de maiúsculas: `user`, `password`, `allowLocalInfile`, `allowMultiQueries`, `initSql`, `createDatabaseIfNotExist`, `socketFactory`, `credentialType` e as opções de pool. As conexões de consulta são somente leitura.

Para usar TLS com verificação da identidade do servidor, preencha `jdbc.properties` assim:

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

O arquivo de certificado precisa estar legível no **servidor GrantForge**, e o nome do host na URL deve corresponder ao certificado. O plug-in não exige TLS por padrão; configure-o explicitamente. Esses nomes de opções são os do MariaDB Connector/J.

`information_schema` mostra apenas objetos nos quais o usuário tem privilégios. Por exemplo, para descobrir recursos apenas em `sales`, um administrador do banco de dados pode conceder estas permissões. Substitua `grantforge-host` pelo endereço de origem do servidor GrantForge:

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

Use `REFERENCES ON *.*` somente quando for necessário descobrir todos os bancos de dados. A conta de metadados do proxy também precisa de `SHOW_ROUTINE`; consulte o guia do agente.

## Recursos e tipos de acesso

| Nível | Correspondência | Observações |
| --- | --- | --- |
| `database` | Curingas, diferencia maiúsculas | Políticas podem terminar aqui |
| `table` | Curingas, diferencia maiúsculas | Abaixo de um banco; políticas podem terminar aqui |
| `column` | Curingas, sem diferenciar maiúsculas | Abaixo de uma tabela; tipos de acesso `select`, `insert` e `update` |

Bancos de dados e tabelas são comparados diferenciando maiúsculas, como o MySQL os armazena no Linux. Em um servidor com `lower_case_table_names` igual a 1 ou 2, o agente converte para minúsculas os nomes que verifica; escreva essas políticas em minúsculas. Nomes de coluna sempre são comparados sem diferenciar maiúsculas, como no MySQL.

Os tipos de acesso seguem os privilégios do MySQL: `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `index` e `all`, que inclui esses oito tipos. `all` não equivale ao `ALL PRIVILEGES` do MySQL e não remove as restrições do proxy sobre as instruções.

Nos valores de política, `*` corresponde a qualquer quantidade de caracteres e `?` a um caractere. O editor sugere nomes pelo prefixo: bancos de dados, tabelas dos bancos que a política nomeia e colunas de suas tabelas. `%` e `_` na entrada de sugestões são caracteres literais.

## Mascaramento

Políticas de mascaramento se aplicam a colunas. Cada tipo de máscara é uma expressão do MySQL 8 em que `{col}` representa a coluna:

| Tipo de máscara | Mostra |
| --- | --- |
| `mask` | Letras ASCII como `X`/`x` e dígitos como `n`; outros caracteres são preservados |
| `mask_show_last_4` | Apenas os quatro últimos caracteres |
| `mask_show_first_4` | Apenas os quatro primeiros caracteres |
| `mask_hash` | Um hash SHA-256 sem sal |
| `mask_null` | `NULL` |
| `mask_none` | O valor sem alteração |
| `mask_date_show_year` | 1º de janeiro do ano da data |
| `custom` | Uma expressão do MySQL sua, com `{col}` para a coluna, como `CONCAT('****', RIGHT({col}, 4))` |

As máscaras que mostram os primeiros ou os últimos quatro caracteres preservam o valor inteiro quando ele tem até quatro caracteres. Por exemplo, `mask_show_last_4` mostra `4111111111111111` como `xxxxxxxxxxxx1111`.

## Filtros de linhas

Políticas de filtragem de linhas se aplicam a tabelas. Um filtro é uma condição do MySQL sobre as colunas da tabela, como `region = 'EU'`: o usuário vê apenas as linhas que a atendem, e instruções `UPDATE` e `DELETE` sobre uma única tabela alteram apenas essas linhas. Escritas envolvendo várias tabelas são recusadas quando a tabela de destino exige um filtro de linhas. Veja outras restrições no guia do agente.

Máscaras e filtros de linhas não concedem acesso. Uma política de acesso separada precisa permitir a operação.

## Exemplo de políticas

Suponha que o MySQL tenha uma tabela `sales.orders` com as colunas `id`, `region` e `card`. Crie três políticas ativadas no mesmo serviço de dados para o usuário de login do MySQL `alice`:

| Tipo de política | Recursos | Regra |
| --- | --- | --- |
| Acesso | `database=sales`, `table=orders`, `column=*` | Permitir `select` ao usuário `alice` |
| Mascaramento | `database=sales`, `table=orders`, `column=card` | Aplicar `mask_show_last_4` ao usuário `alice` |
| Filtro de linhas | `database=sales`, `table=orders` | Aplicar `region = 'EU'` ao usuário `alice` |

Preencha o usuário da política como `alice`, não `alice@host`. O próprio MySQL também precisa permitir que essa conta leia a tabela. Depois que o proxy aplica o novo snapshot, as consultas de `alice` pelo proxy retornam apenas linhas EU, com somente os quatro últimos caracteres de cada cartão visíveis. Predicados e expressões de junção também veem os valores mascarados. Veja o [agente proxy do MySQL](/pt-br/external/mysql-agent/) para implantação e verificação.

## Aplicar políticas

O plug-in de serviço gerencia as políticas e consulta os recursos. Para aplicá-las, máscaras e filtros incluídos, é preciso o agente proxy do MySQL, ao qual os clientes se conectam no lugar do servidor. Veja o guia do agente para a implantação e o que ele verifica.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente proxy do MySQL](/pt-br/external/mysql-agent/)
- [Desenvolvimento de plug-ins e tipos de serviço](/pt-br/develop/plugins/)
