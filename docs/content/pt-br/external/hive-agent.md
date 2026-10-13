---
title: Agente do Apache Hive
description: Instale o agente no HiveServer2 para verificar instruções, filtrar SHOW, mascarar colunas e filtrar linhas com as políticas do GrantForge.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do Hive é um autorizador da API de autorização do Hive, como o plug-in do Hive do Apache Ranger: o HiveServer2 o consulta antes de cada instrução, e ele responde com base nas políticas assinadas do serviço de dados `hive`. A distribuição o entrega como um único JAR compatível com Java 8, `agents/hive/grantforge-agent-hive-<version>.jar`, com as dependências realocadas dentro dele.

Os testes de integração com Testcontainers do repositório executam o JAR dentro do HiveServer2 `3.1.3`, `4.0.1` e `4.1.0`. Isso não certifica as demais versões. O plug-in de serviço está descrito em [Apache Hive](/pt-br/plugins/hive/).

## Como funciona

- **As instruções são verificadas.** Para cada objeto que uma instrução lê ou grava, o agente pergunta às políticas pelo tipo de acesso que a operação exige, como o Ranger; a primeira recusa faz a instrução falhar com `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`. As consultas são verificadas coluna a coluna.
- **As listagens são filtradas.** `SHOW DATABASES` e `SHOW TABLES` listam apenas o que o usuário pode usar: qualquer acesso ao banco ou à tabela, ou a algo dentro dele. `USE` e `DESCRIBE DATABASE` exigem o mesmo.
- **As consultas são mascaradas e filtradas.** Quando o usuário tem colunas mascaradas ou um filtro de linhas em uma tabela, o HiveServer2 reescreve a consulta com as máscaras e a condição, de modo que junções, condições e funções veem apenas o que o usuário pode ver.
- **As máscaras não podem ser contornadas.** Gravar, alterar ou exportar uma tabela na qual o usuário tem máscara ou filtro de linhas é recusado, como o Ranger faz por padrão.
- **Permissões e papéis são do GrantForge.** `GRANT`, `REVOKE`, `SHOW GRANT`, `CREATE ROLE`, `SET ROLE` e as demais instruções de papéis do Hive são recusadas; `SHOW CURRENT ROLES` lista os papéis que o GrantForge dá ao usuário.
- **Instruções inseguras são recusadas.** Comandos `dfs`, que chegariam ao sistema de arquivos com os direitos do próprio HiveServer2, são recusados. Nas sessões do HiveServer2, scripts `TRANSFORM` são proibidos e os usuários só podem alterar com `SET` as configurações que a autorização SQL padrão do Hive permite.
- **Cada decisão é informada** ao GrantForge com o usuário, o endereço do cliente, o recurso, o tipo de acesso, o resultado, a operação e a instrução.

| Operação | Tipo de acesso |
| --- | --- |
| Consultas, `SHOW CREATE TABLE`, `SHOW PARTITIONS`, `SHOW TBLPROPERTIES`, `DESCRIBE`, `SHOW COLUMNS`, `ANALYZE` | `select` |
| `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `LOAD` (destino) | `update` |
| `CREATE DATABASE`, `CREATE TABLE`, `CREATE VIEW`, `CREATE FUNCTION` | `create` no que é criado, `select` no que é lido |
| `ALTER …`, `MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`, `UNLOCK` | `lock` |
| `SHOW DATABASES`, `SHOW TABLES`, `USE`, `DESCRIBE DATABASE` | Qualquer acesso ao objeto ou a algo dentro dele |
| `REPL DUMP`, `REPL LOAD`, `REPL STATUS` | `repladmin` |
| `KILL QUERY`, planos de recursos, administração do LLAP | `serviceadmin` em `hiveservice` |
| Funções temporárias, `ADD JAR`, `COMPILE` | `tempudfadmin` em `global` |
| Locais que uma instrução lê ou grava | `read` ou `write` em `url` |

## Implantação

1. Adicione um serviço de dados `hive` e configure suas políticas, incluindo uma que permita aos usuários com que o HiveServer2 faz seu próprio trabalho, como `hive`, tudo de que precisam. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é mostrado uma única vez; guarde-o em um arquivo que só a conta do HiveServer2 possa ler.
2. Copie o JAR para `lib` de **cada HiveServer2**, como `/opt/hive/lib`.
3. Adicione as configurações ao `hiveserver2-site.xml` ou `hive-site.xml` de cada HiveServer2, substituindo o endereço, os caminhos e o nome de instância de exemplo:

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

   O diretório de cache deve ser gravável pela conta do HiveServer2. Como no Ranger, `hive.server2.enable.doAs=false` faz o HiveServer2 ler e gravar dados com a própria conta, para que as políticas decidam quem vê o quê.
4. Reinicie o HiveServer2. O agente inicia com a primeira sessão e para com o servidor, enviando ou guardando em fila os eventos pendentes.
5. **Feche os caminhos que contornam o HiveServer2:** o agente verifica apenas as instruções executadas no HiveServer2. Clientes que acessam diretamente o metastore ou os arquivos do warehouse, como a CLI do Hive, o Spark ou clientes HDFS, não são verificados; proteja-os com as permissões do próprio metastore e do HDFS, ou com o [agente do HDFS](/pt-br/external/hdfs-agent/).

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: o servidor GrantForge, o arquivo do token do agente, um nome para este HiveServer2 e um diretório gravável para o snapshot assinado e os eventos em fila |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública Ed25519 X.509 codificada em Base64 da página de agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |

O agente lê suas configurações dos arquivos de configuração do HiveServer2 e das propriedades de sistema (`-Dgrantforge.…`, que prevalecem), nunca das configurações de uma sessão, que um cliente pode escolher.

## Usuários, grupos e papéis

As políticas nomeiam o usuário que o HiveServer2 atribui à sessão: o nome com que o cliente entra, ou o nome curto do seu principal Kerberos (`alice` para `alice@EXAMPLE.COM`). Os grupos são os que o mapeamento de grupos do Hadoop dá ao usuário no HiveServer2, junto com os grupos em que o GrantForge coloca o usuário; os papéis são os que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem um snapshot utilizável, ou quando as políticas não podem ser lidas, toda instrução é recusada. Um serviço fora de uso deixa o HiveServer2 sem verificação, como sem autorizador.
- Um token vinculado a um serviço cujo tipo não é `hive` recusa tudo.
- As políticas `url` decidem os locais que as instruções leem e gravam; diferentemente do Ranger, o agente não recorre às permissões do sistema de arquivos.
- As instruções para as quais o Hive não nomeia objeto são verificadas em todos os bancos: instruções `REPL` sem objeto precisam de `repladmin` e trocar o proprietário de uma tabela ou banco sem objeto precisa de `alter`, no banco `*`.
- `EXPLAIN`, `DESCRIBE FUNCTION`, `SHOW FUNCTIONS`, `SET` e as demais instruções que o Ranger não verifica também não são verificadas; chamadas de metadados de clientes JDBC, como `getTables`, não são filtradas.
- O agente funciona no HiveServer2. A autorização dentro do metastore não é coberta.

## Guias relacionados

- [Apache Hive](/pt-br/plugins/hive/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
