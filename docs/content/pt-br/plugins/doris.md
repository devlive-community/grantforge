---
title: Apache Doris
description: Instalar e configurar o plug-in de serviço do Apache Doris, alcançar um ou vários frontends, entrar com uma senha, consultar catálogos, bancos de dados, tabelas, colunas e grupos de carga de trabalho, e gerenciar quem pode ler, carregar e administrar o quê, até colunas e linhas, e quem pode executar consultas em qual grupo de carga de trabalho.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do Apache Doris consulta catálogos, bancos de dados, tabelas, colunas e grupos de carga de trabalho, e gerencia políticas de acesso nos termos do próprio Doris: ler colunas, carregar, alterar e excluir tabelas e mostrar a definição de suas visões, criar tabelas em um banco de dados e excluí-lo, criar bancos de dados em um catálogo e executar consultas em um grupo de carga de trabalho, com filtros de linhas de tabelas. O ID do plug-in e o tipo de serviço são ambos `doris`. O plug-in de serviço roda no servidor do GrantForge; o [agente do Apache Doris](/pt-br/external/doris-agent/), um serviço ao lado do cluster, aplica as políticas com os papéis, privilégios e políticas de linha do próprio Doris.

Os testes de integração com Testcontainers do repositório cobrem o Apache Doris `2.1`, `3.1`, `4.0` e `4.1`, cada um em um cluster de um frontend e um backend; isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/doris`. Confirme que `doris` está ativado em **Gestão da plataforma → Plug-ins**; reexamine o diretório de plug-ins depois de atualizá-lo. O plug-in se comunica com o protocolo MySQL dos frontends pelo MariaDB Connector/J, em um carregador de classes próprio.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione Apache Doris (`doris`).
2. Informe o endereço do protocolo MySQL de um frontend, ou de vários.
3. Informe o nome e a senha do usuário de consulta, teste a conexão e salve o serviço. O teste entra e descreve o cluster: a versão do Doris, o usuário com que entrou, quantos catálogos ele vê e quantos bancos de dados do catálogo interno. Ele só pergunta ao frontend, então responde mesmo sem nenhum backend no ar.

O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos usuários que leem e carregam. Um teste de conexão bem-sucedido não significa que o acesso já está sujeito a políticas.

| Configuração | Finalidade |
| --- | --- |
| `doris.hosts` | Obrigatório: o endereço do protocolo MySQL de um frontend, por exemplo `fe1.example.com:9030`; vários separados por vírgulas, tentados em sequência quando um não pode ser alcançado. Sem porta, é `9030` |
| `username` | Obrigatório: o usuário de consulta |
| `password` | A senha do usuário de consulta; guardada criptografada |
| `ssl.mode` | `disable` (padrão), `trust` para criptografar sem verificar o frontend, `verify-ca` para verificar seu certificado ou `verify-full` para verificar também seu nome; para frontends que oferecem TLS no protocolo MySQL |
| `ssl.root.certificate` | verify-ca e verify-full: o certificado de CA PEM com que os frontends são verificados, no **servidor do GrantForge**; vazio usa os da JVM |
| `timeout` | Quanto a conexão e uma instrução podem levar, padrão `30000`, intervalo `1..600000` milissegundos |

Entradas recusadas, um usuário de consulta sem um privilégio necessário e frontends inalcançáveis são informados como tais.

### O que o usuário de consulta vê

As instruções `SHOW` do Doris listam só os catálogos, bancos de dados, tabelas e colunas sobre os quais um usuário tem algum privilégio, então uma consulta mostra o que o usuário de consulta pode ver. Dê a ele `SELECT_PRIV` sobre aquilo para que se escreverão políticas, por exemplo `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`; `SHOW WORKLOAD GROUPS` lista os grupos que ele pode ver. Os bancos de dados próprios do Doris, `information_schema`, `mysql` e `__internal_schema`, são omitidos: as políticas não os decidem.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `catalog` | Curingas, diferencia maiúsculas; o catálogo interno ou um externo | `create_database` |
| `database` | Curingas, diferencia maiúsculas; sob um catálogo | `create`, `drop_database` |
| `table` | Curingas, diferencia maiúsculas; sob um banco de dados, uma tabela ou visão | `load`, `alter`, `drop`, `show_view` |
| `column` | Curingas, sem diferenciar maiúsculas, como o Doris compara nomes de coluna; sob uma tabela | `select` |
| `workload_group` | Curingas, diferencia maiúsculas; o nome do grupo | `usage` |

| Tipo de acesso | Privilégio do Doris |
| --- | --- |
| `select` | `SELECT_PRIV`, sobre colunas, ou sobre uma tabela, um banco de dados ou um catálogo inteiros |
| `load` | `LOAD_PRIV`: cargas, `INSERT`, `UPDATE`, `DELETE` e `TRUNCATE` |
| `alter`, `drop` | `ALTER_PRIV`, `DROP_PRIV` sobre a tabela |
| `show_view` | `SHOW_VIEW_PRIV`: `SHOW CREATE VIEW` |
| `create` | `CREATE_PRIV` sobre o banco de dados: criá-lo, e criar tabelas e visões nele |
| `drop_database` | `DROP_PRIV` sobre o banco de dados |
| `create_database` | `CREATE_PRIV` sobre o catálogo |
| `usage` | `USAGE_PRIV` sobre o grupo de carga de trabalho |

A leitura é decidida por coluna, como o Doris a concede: uma política `select` nomeia colunas, `*` para todas. Uma política pode assim permitir uma tabela e negar uma de suas colunas, e o usuário lê todas as outras. Nos valores das políticas, `*` corresponde a qualquer sequência de caracteres e `?` a um caractere. O editor de políticas sugere os catálogos, os bancos de dados dos catálogos que a política nomeia, suas tabelas e colunas, e os grupos de carga de trabalho.

O privilégio do Doris sobre um banco de dados alcança também cada tabela dele, e sobre um catálogo cada banco de dados dele. Por isso o agente concede `create_database` só onde as políticas também deixam o usuário criar tabelas em cada banco de dados do catálogo, e `drop_database` só onde também o deixam excluir cada tabela do banco de dados; caso contrário, retém o privilégio e avisa, em vez de conceder mais do que as políticas permitem. `ADMIN_PRIV`, `GRANT_PRIV` e `NODE_PRIV` não são tipos de acesso: nenhuma política os dá.

O Doris mascara valores apenas por meio do Apache Ranger, por isso este tipo de serviço não oferece mascaramento.

### Filtros de linhas

Uma política de filtro de linhas de uma tabela mostra aos seus usuários as linhas para as quais uma condição vale. A condição é um predicado do Doris, como as políticas de linha o recebem, por exemplo `region = 'EU'`; o agente o executa antes na tabela, e um filtro que não pode ser executado ali não mostra nenhuma linha. Como em outros serviços, um usuário recebe o filtro da primeira política que se aplica a ele.

## Exemplo de políticas

Suponha que o cluster tenha os bancos de dados `sales` e `hr` no seu catálogo interno e um grupo de carga de trabalho `etl`. Crie estas políticas ativadas no mesmo serviço de dados:

| Recursos | Regra |
| --- | --- |
| `catalog=internal`, `database=sales`, `table=*`, `column=*` | Permitir `select` ao grupo `analysts` |
| `catalog=internal`, `database=sales`, `table=orders`, `column=card` | Negar `select` ao grupo `analysts` |
| `catalog=internal`, `database=sales`, `table=orders` | Permitir `load` ao usuário `ingest` |
| `catalog=internal`, `database=team_*` | Permitir `create` ao grupo `data-team` |
| `catalog=internal`, `database=team_*`, `table=*` | Permitir `load`, `alter` e `drop` ao grupo `data-team` |
| `workload_group=etl` | Permitir `usage` ao usuário `ingest` e ao grupo `data-team` |
| `catalog=internal`, `database=sales`, `table=orders` (filtro de linhas) | Mostrar ao usuário `emea` as linhas `region = 'EMEA'` |

Quando o agente aplica o novo snapshot, os membros de `analysts` leem todas as tabelas de `sales` exceto os números de cartão de `orders`; `ingest` carrega `orders` e pode executar suas cargas em `etl`; os membros de `data-team` criam tabelas nos bancos de dados `team_` que um administrador criou, e as carregam, alteram e excluem; e `emea`, se uma política o deixar ler `orders`, vê só as linhas da sua região.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta recursos. Aplicá-las exige o agente do Apache Doris, um por cluster. A implantação e as verificações estão em [Agente do Apache Doris](/pt-br/external/doris-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do Apache Doris](/pt-br/external/doris-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
