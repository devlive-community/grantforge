---
title: PostgreSQL
description: Instalar e configurar o plug-in de serviço do PostgreSQL, alcançar um ou vários servidores, entrar com uma senha ou um certificado de cliente TLS, consultar bancos de dados, esquemas, tabelas e colunas, e gerenciar quem pode ler, gravar e criar o quê, até colunas e linhas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do PostgreSQL consulta bancos de dados, esquemas, tabelas e colunas, e gerencia políticas de acesso nos termos do próprio PostgreSQL: ler, inserir e atualizar colunas, excluir linhas, esvaziar tabelas e criar gatilhos nelas, criar objetos em um esquema, e criar esquemas e tabelas temporárias em um banco de dados, com filtros de linhas nas tabelas. O ID do plug-in e o tipo de serviço são ambos `postgresql`. O plug-in de serviço roda no servidor GrantForge; o [agente do PostgreSQL](/pt-br/external/postgresql-agent/), um serviço ao lado dos servidores, aplica as políticas com os privilégios e as políticas de segurança de linhas do próprio PostgreSQL.

Os testes de integração com Testcontainers do repositório cobrem o PostgreSQL `15`, `16`, `17` e `18`, as versões que o PostgreSQL mantém; isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/postgresql`. Confirme em **Gestão da plataforma → Plug-ins** que `postgresql` está habilitado; examine novamente o diretório de plug-ins depois de atualizá-lo. O plug-in fala com os servidores pelo driver JDBC do PostgreSQL, em um carregador de classes próprio.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e escolha PostgreSQL (`postgresql`).
2. Informe um servidor como `host:port`, ou vários servidores.
3. Escolha como o usuário de consulta entra, informe o nome e as credenciais dele, teste a conexão e salve o serviço. O teste entra e descreve o servidor: a versão do PostgreSQL, o usuário com que entrou e quantos bancos de dados o servidor tem.

O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos usuários que leem e gravam. Um teste de conexão bem-sucedido não significa que o acesso já esteja sujeito a políticas.

| Configuração | Finalidade |
| --- | --- |
| `postgresql.hosts` | Obrigatório: um servidor como `host:port`, como `postgres1.example.com:5432`; vários separados por vírgulas, tentados em sequência quando um não pode ser alcançado. A porta padrão é `5432` |
| `postgresql.database` | O banco de dados em que o usuário de consulta entra primeiro, para listar os bancos de dados; padrão `postgres` |
| `authentication` | `password` (padrão) ou `certificate`: um certificado de cliente TLS que a autenticação `cert` do PostgreSQL associa ao usuário |
| `username` | Obrigatório: o usuário de consulta |
| `password` | password: a senha do usuário de consulta; armazenada criptografada |
| `ssl.mode` | TLS como o `sslmode` da libpq o nomeia: `disable`, `allow`, `prefer` (padrão), `require`, `verify-ca` ou `verify-full`, que também verifica o nome do servidor |
| `ssl.root.certificate` | verify-ca e verify-full: o certificado de CA PEM contra o qual os servidores são verificados, no **servidor GrantForge**; vazio usa o da JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate: o certificado de cliente PEM e sua chave privada PKCS #8, PEM ou DER, no servidor GrantForge; entrar com certificado requer `require` ou um modo de verificação, e um modo de verificação requer então `ssl.root.certificate` |
| `timeout` | Quanto podem levar a conexão e uma consulta, padrão `30000`, intervalo `1..600000` milissegundos |

As configurações de uma forma de entrada são recusadas com outra, de modo que um serviço diz exatamente como entra. O driver do PostgreSQL recusa um arquivo de chave privada que outros além do dono possam ler. Uma entrada recusada, um banco de dados inexistente, um servidor inalcançável ou cujo certificado não pode ser verificado são informados como tais.

### O que o usuário de consulta vê

Os catálogos do PostgreSQL mostram a todo usuário os nomes dos bancos de dados e, em cada banco a que ele pode se conectar, os nomes dos esquemas, tabelas e colunas, sem ler dados. O usuário de consulta só precisa de `CONNECT` nos bancos de dados para os quais as políticas serão escritas, que PUBLIC tem a menos que tenha sido revogado. Uma consulta de esquemas, tabelas ou colunas entra em cada banco de dados que a política nomeia, no máximo 20, e pula os bancos a que o usuário de consulta não pode se conectar. Os esquemas do próprio PostgreSQL, os que começam com `pg_` e `information_schema`, ficam de fora: as políticas não decidem sobre eles.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `database` | Curingas, diferenciando maiúsculas; o nome do banco de dados | `create_schema`, `temporary` |
| `schema` | Curingas, diferenciando maiúsculas; sob um banco de dados | `create` |
| `table` | Curingas, diferenciando maiúsculas; tabelas, tabelas particionadas, views, views materializadas e tabelas estrangeiras sob um esquema | `delete`, `truncate`, `trigger` |
| `column` | Curingas, diferenciando maiúsculas; sob uma tabela | `select`, `insert`, `update` |

| Tipo de acesso | Privilégios do PostgreSQL |
| --- | --- |
| `select`, `insert`, `update` | `SELECT`, `INSERT`, `UPDATE`, em colunas ou na tabela inteira |
| `delete`, `truncate`, `trigger` | `DELETE`, `TRUNCATE`, `TRIGGER` na tabela |
| `create` | `CREATE` no esquema: tabelas, views, sequências, funções e outros objetos nele |
| `create_schema` | `CREATE` no banco de dados: esquemas nele |
| `temporary` | `TEMPORARY` no banco de dados |

Leitura, inserção e atualização são decididas por coluna, como o PostgreSQL as concede: uma política de `select`, `insert` ou `update` nomeia colunas, `*` para todas. Assim uma política pode permitir uma tabela e negar uma das colunas, e o usuário pode ler todas as outras. Nos valores das políticas, `*` corresponde a qualquer sequência de caracteres e `?` a um. O editor de políticas sugere os bancos de dados, os esquemas dos bancos que uma política nomeia, suas tabelas e colunas.

Alcançar o que uma política permite exige mais do PostgreSQL: `CONNECT` no banco de dados, `USAGE` no esquema e, para inserir em uma coluna serial, `USAGE` na sequência dela. Não são tipos de acesso próprios: o agente os concede junto com o que precisa deles. O PostgreSQL não tem mascaramento de colunas, então este tipo de serviço não oferece nenhum.

### Filtros de linhas

Uma política de filtragem de linhas em uma tabela mostra aos seus usuários as linhas para as quais uma condição vale. A condição é uma expressão do PostgreSQL, como o `USING` de uma política de segurança de linhas a aceita, como `region = 'EU'` ou `owner = current_user`. Um usuário vê o filtro da primeira política que se aplica a ele, como nos outros serviços. O PostgreSQL filtra apenas as linhas de tabelas e tabelas particionadas: uma view ou objeto parecido cujas linhas uma política filtra para um usuário não é legível de forma alguma para esse usuário.

## Exemplo de políticas

Suponha que o servidor tenha os bancos de dados `sales` e `hr`, e que `sales` tenha o esquema `crm`. Crie estas políticas habilitadas no mesmo serviço de dados:

| Recursos | Regra |
| --- | --- |
| `database=sales`, `schema=crm`, `table=*`, `column=*` | Permitir `select` ao grupo `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=card` | Negar `select` ao grupo `analysts` |
| `database=sales`, `schema=crm`, `table=orders`, `column=*` | Permitir `insert` ao usuário `ingest` |
| `database=sales`, `schema=team_*` | Permitir `create` ao grupo `data-team` |
| `database=sales`, `schema=team_*`, `table=*` | Permitir `delete` e `truncate` ao grupo `data-team` |
| `database=sales`, `schema=team_*`, `table=*`, `column=*` | Permitir `select`, `insert` e `update` ao grupo `data-team` |
| `database=sales` | Permitir `create_schema` ao grupo `data-team` |
| `database=sales`, `schema=crm`, `table=orders` (filtro de linhas) | Mostrar ao usuário `emea` as linhas `region = 'EMEA'` |

Depois que o agente aplica o novo snapshot, os membros de `analysts` leem todas as tabelas de `crm`, exceto os números de cartão de `orders`; `ingest` insere em `orders`, incluindo os ids serial, e não lê nada; os membros de `data-team` criam esquemas e, nos que começam com `team_`, criam, leem, alteram e esvaziam tabelas; e `emea`, se uma política o deixar ler `orders`, vê apenas as linhas da própria região.

## Aplicar as políticas

O plug-in de serviço gerencia políticas e consulta recursos. Aplicá-las requer o agente do PostgreSQL, um por servidor ou primário. Implantação e verificações estão no [agente do PostgreSQL](/pt-br/external/postgresql-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do PostgreSQL](/pt-br/external/postgresql-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
