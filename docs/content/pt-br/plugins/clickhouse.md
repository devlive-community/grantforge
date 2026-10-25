---
title: ClickHouse
description: Instalar e configurar o plug-in de serviço do ClickHouse, alcançar um ou vários servidores, entrar com uma senha ou um certificado de cliente TLS, consultar bancos de dados, tabelas e colunas, e gerenciar quem pode ler, escrever e administrar o quê, até colunas e linhas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do ClickHouse consulta bancos de dados, tabelas e colunas, e gerencia políticas de acesso nos termos do ClickHouse: ler e escrever colunas, alterar, excluir, esvaziar e otimizar tabelas, criar tabelas em um banco de dados e excluí-lo, e no servidor a criação de bancos de dados, `KILL QUERY` e as instruções `SYSTEM`, com filtros de linhas em tabelas. Seu ID de plug-in e seu tipo de serviço são ambos `clickhouse`. O plug-in de serviço roda no servidor GrantForge; o [agente do ClickHouse](/pt-br/external/clickhouse-agent/), um serviço ao lado dos servidores, aplica as políticas com os privilégios e as políticas de linhas do próprio ClickHouse.

Os testes de integração com Testcontainers do repositório cobrem o ClickHouse `24.8`, `25.3`, `25.8`, `26.3` e `26.8`, as versões com suporte de longo prazo dos últimos dois anos; isso não certifica todas as demais versões.

## Instalação

A distribuição inclui o plug-in em `plugins/clickhouse`. Confirme em **Gestão da plataforma → Plug-ins** que `clickhouse` está habilitado; examine novamente o diretório de plug-ins após uma atualização. O plug-in fala com os servidores por HTTP pelo cliente Java do próprio ClickHouse, em seu próprio carregador de classes.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e escolha ClickHouse (`clickhouse`).
2. Informe a URL da interface HTTP de um servidor, ou de vários.
3. Escolha como o usuário de consulta entra, informe o nome de usuário e as credenciais, teste a conexão e salve o serviço. O teste entra e descreve o servidor: sua versão do ClickHouse, o usuário com que entrou e quantos bancos de dados esse usuário vê.

O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos usuários que leem e escrevem. Um teste de conexão bem-sucedido não significa que o acesso já esteja sujeito a políticas.

| Configuração | Finalidade |
| --- | --- |
| `clickhouse.urls` | Obrigatório: a interface HTTP de um servidor, como `https://clickhouse1.example.com:8443`; várias separadas por vírgulas, tentadas em sequência quando uma não pode ser alcançada |
| `authentication` | `password` (padrão) ou `certificate`: um certificado de cliente TLS que o ClickHouse associa ao usuário (`IDENTIFIED WITH ssl_certificate`) |
| `username` | Obrigatório: o usuário de consulta |
| `password` | password: a senha do usuário de consulta; armazenada criptografada |
| `ssl.root.certificate` | https: o certificado de CA em PEM, no **servidor GrantForge**, com que os servidores são verificados; vazio usa o da JVM |
| `ssl.client.certificate`, `ssl.client.key` | certificate: o certificado de cliente em PEM e sua chave privada, no servidor GrantForge; a entrada com certificado exige servidores `https://` |
| `timeout` | Quanto tempo uma requisição pode levar, padrão `30000`, intervalo `1..600000` milissegundos |

As configurações de um modo de entrada são recusadas com outro, de modo que um serviço diz exatamente como entra. Uma entrada recusada, um usuário somente leitura, um servidor que não pode ser alcançado ou cujo certificado não pode ser verificado, e algo que não é o ClickHouse respondendo em seu lugar, como um proxy, são relatados como tais.

### O que o usuário de consulta vê

O ClickHouse mostra a um usuário apenas os bancos de dados, tabelas e colunas sobre os quais ele tem algum privilégio. Dê ao usuário de consulta `SHOW` sobre aquilo para o que políticas serão escritas, como `GRANT SHOW ON *.* TO grantforge_lookup`, que o deixa ver nomes sem ler dados. Os bancos de dados do próprio ClickHouse, `system` e `information_schema`, ficam de fora: as políticas não os decidem.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `database` | Curinga, diferencia maiúsculas; o nome do banco de dados | `create`, `drop_database` |
| `table` | Curinga, diferencia maiúsculas; sob um banco de dados | `alter`, `drop`, `truncate`, `optimize` |
| `column` | Curinga, diferencia maiúsculas; sob uma tabela | `select`, `insert` |
| `server` | Seu único valor, `*` | `create_database`, `kill_query`, `system` |

| Tipo de acesso | Privilégios do ClickHouse |
| --- | --- |
| `select`, `insert` | `SELECT`, `INSERT`, em colunas |
| `alter` | `ALTER`: qualquer alteração de uma tabela, incluindo `ALTER UPDATE` e `ALTER DELETE` |
| `drop` | `DROP TABLE`, `DROP VIEW`, `DROP DICTIONARY` |
| `truncate`, `optimize` | `TRUNCATE`, `OPTIMIZE` |
| `create` | `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` no banco de dados |
| `drop_database` | `DROP DATABASE` |
| `create_database`, `kill_query`, `system` | `CREATE DATABASE`, `KILL QUERY`, `SYSTEM`: qualquer instrução `SYSTEM` |

Leitura e escrita são decididas por coluna, como o ClickHouse as concede: uma política de `select` ou `insert` nomeia colunas, `*` para todas. Assim, uma política pode permitir uma tabela e negar uma de suas colunas, e o usuário pode ler todas as outras. Nos valores das políticas, `*` corresponde a qualquer sequência de caracteres e `?` a um. O editor de políticas sugere os bancos de dados, as tabelas dos bancos que uma política nomeia e suas colunas.

Os tipos de acesso não são nenhum dos grupos de privilégios do ClickHouse, como `ALL`: cada um é o que diz, de modo que uma política nunca dá mais do que nomeia. O ClickHouse não tem mascaramento de colunas fora do ClickHouse Cloud, então este tipo de serviço não oferece nenhum.

### Filtros de linhas

Uma política de filtro de linhas em uma tabela mostra a seus usuários as linhas para as quais uma condição vale. A condição é uma expressão do ClickHouse, como as políticas de linhas a recebem, por exemplo `region = 'EU'` ou `owner = currentUser()`. Um usuário vê o filtro da primeira política que se aplica a ele, como nos demais serviços.

## Exemplo de políticas

Suponha que o servidor tenha os bancos de dados `sales` e `hr`. Crie estas políticas habilitadas no mesmo serviço de dados:

| Recursos | Regra |
| --- | --- |
| `database=sales`, `table=*`, `column=*` | Permitir `select` ao grupo `analysts` |
| `database=sales`, `table=orders`, `column=card` | Negar `select` ao grupo `analysts` |
| `database=sales`, `table=orders`, `column=*` | Permitir `insert` ao usuário `ingest` |
| `database=team_*` | Permitir `create` ao grupo `data-team` |
| `database=team_*`, `table=*` | Permitir `alter`, `drop`, `truncate` e `optimize` ao grupo `data-team` |
| `database=team_*`, `table=*`, `column=*` | Permitir `select` e `insert` ao grupo `data-team` |
| `server=*` | Permitir `create_database` ao grupo `data-team` |
| `database=sales`, `table=orders` (filtro de linhas) | Mostrar ao usuário `emea` as linhas `region = 'EMEA'` |

Assim que o agente aplica o novo snapshot, os membros de `analysts` leem todas as tabelas de `sales`, exceto os números de cartão de `orders`; `ingest` escreve `orders` e não lê nada; os membros de `data-team` criam bancos de dados e, naqueles cujo nome começa com `team_`, tabelas, que leem, escrevem e alteram; e `emea`, se uma política o deixar ler `orders`, vê apenas as linhas da própria região.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta recursos. Aplicá-las exige o agente do ClickHouse, um por servidor ou cluster. A implantação e as verificações estão no [agente do ClickHouse](/pt-br/external/clickhouse-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do ClickHouse](/pt-br/external/clickhouse-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
