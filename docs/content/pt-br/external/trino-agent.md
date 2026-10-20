---
title: Agente do Trino
description: Instalar o controle de acesso do sistema no coordinator do Trino para verificar consultas com as políticas do GrantForge, listar apenas o que um usuário pode usar, filtrar linhas e mascarar colunas, e sujeitar a personificação e as consultas de outros usuários a políticas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do Trino é um controle de acesso do sistema chamado `grantforge`: o coordinator pergunta a ele sobre cada catálogo, esquema, tabela, coluna, rotina, propriedade de sessão e usuário que uma consulta toca, e ele responde a partir das políticas assinadas do serviço de dados `trino`. O Trino 470 a 478 roda em Java 23 ou 24 e o Trino 479 e posteriores em Java 25, por isso a distribuição traz dois JARs, cada um com suas dependências realocadas dentro:

| Trino | JAR |
| --- | --- |
| 470 a 478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 e posteriores | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

Os testes de integração com Testcontainers do repositório executam o primeiro JAR dentro do Trino `470` e `476` e o segundo dentro do `479` e `484`. Isso não certifica todas as outras versões. Veja [Trino](/pt-br/plugins/trino/) para o plug-in de serviço.

## Como funciona

- **Cada verificação é decidida em cada escopo que nomeia.** Uma permissão em um catálogo, esquema ou tabela cobre o que está dentro, então o agente decide uma coluna em seu catálogo, seu esquema, sua tabela e nela mesma: uma negação em qualquer um deles recusa, uma permissão em qualquer um deles concede.
- **As listagens omitem o que não pode ser usado.** `SHOW CATALOGS`, `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW FUNCTIONS`, `information_schema` e os metadados do driver mostram apenas o que o usuário pode usar, ou dentro do que pode usar algo.
- **O que descreve os dados fica aberto.** O `information_schema` de cada catálogo, os esquemas `jdbc` e `metadata` do catálogo `system`, que drivers JDBC e ferramentas de BI leem, e as funções internas do Trino não precisam de política; suas linhas são filtradas como as listagens.
- **Linhas são filtradas e colunas mascaradas.** As políticas de filtragem de linhas se aplicam a cada consulta que lê a tabela, e as máscaras são ajustadas ao tipo da coluna, para que uma máscara nunca quebre uma consulta: máscaras de texto se aplicam a colunas `varchar` e `char` e são convertidas de volta ao tipo da coluna, a máscara de ano a datas e timestamps, uma expressão personalizada é convertida ao tipo, e qualquer outra coluna que uma máscara de texto nomeie é lida como um `NULL` de seu tipo.
- **Personificação e consultas alheias ficam sujeitas a políticas.** Executar uma consulta como outro usuário requer `impersonate` sobre esse usuário; ver ou encerrar as consultas de outro usuário requer `view_query` ou `kill_query` sobre ele. Todos podem executar consultas, e ver e encerrar as próprias.
- **As recusas dizem o porquê.** O cliente recebe `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`, ou que nenhuma política permite, ou que nenhuma política chegou ainda.
- **Decisões são guardadas e acessos agrupados.** As decisões do snapshot atual são guardadas, por padrão 100.000 por 10 segundos, e um novo snapshot recomeça do zero. Acessos permitidos idênticos dentro de uma janela, por padrão 10 segundos, são informados como um único evento de acesso que diz quantos foram; recusas são informadas uma a uma, imediatamente. Cada evento nomeia a consulta.
- **Os privilégios são do GrantForge.** As instruções `GRANT`, `REVOKE`, `DENY` e de papéis do Trino são recusadas, pois as políticas são gerenciadas no GrantForge.

| Instrução | Acesso necessário |
| --- | --- |
| `SELECT` | `select` em cada coluna lida; uma consulta que não lê nenhuma coluna, como `count(*)`, na tabela |
| `INSERT`, `DELETE`, `TRUNCATE` | `insert`; `delete` para as outras duas |
| `UPDATE` | `update` em cada coluna escrita |
| `CREATE SCHEMA`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW` | `create` no que é criado |
| `DROP` de um esquema, tabela, view ou view materializada | `drop` nele |
| `ALTER` de uma tabela, view ou coluna, `COMMENT`, `SET PROPERTIES`, `SET AUTHORIZATION`, `ALTER TABLE ... EXECUTE` | `alter` nele |
| Renomear um esquema, tabela, view ou view materializada | `alter` nele e `create` no novo nome |
| `REFRESH MATERIALIZED VIEW` | `insert` nela |
| Uma view que o proprietário executa | Seu proprietário precisa de `select` nas colunas e `execute` nas funções que ela lê |
| `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW COLUMNS`, `SHOW FUNCTIONS`, `SHOW CREATE` | Qualquer acesso a ele ou dentro dele |
| `CREATE CATALOG`, `DROP CATALOG` | `create` ou `drop` no catálogo |
| Chamar uma função; `CREATE FUNCTION`, `DROP FUNCTION` | `execute`; `create_function`, `drop_function` na função |
| `CALL` | `execute` no procedimento |
| `SET SESSION` | `set` na propriedade de sessão do catálogo ou do sistema |
| Executar como outro usuário, ver ou encerrar consultas alheias | `impersonate`, `view_query`, `kill_query` no usuário |
| Ler ou escrever informações do sistema: nós, estatísticas e despejos de threads da API REST e da interface web do Trino | `read` ou `write` em `system_information` |

A partir do Trino 479, o segundo JAR também verifica branches de tabela: listá-los requer ver a tabela, e criar, remover ou avançar um requer `alter` nela; atualizar uma view e alterar o proprietário ou o comentário de uma view materializada requerem `alter` nela.

## Implantação

1. Adicione um serviço de dados `trino` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é mostrado uma única vez; guarde-o em um arquivo que só a conta do Trino possa ler.
2. Copie o JAR da versão do coordinator para um diretório próprio no diretório de plug-ins do Trino, como `/usr/lib/trino/plugin/grantforge/`. Só o coordinator verifica acesso; os workers não precisam de nada.
3. Crie `etc/access-control.properties` no coordinator, substituindo o endereço, os caminhos e o nome de instância de exemplo:

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   O diretório de cache deve ser gravável pela conta do Trino. O GrantForge substitui o controle de acesso baseado em arquivo ou outro do Trino em vez de rodar ao lado dele.
4. Reinicie o coordinator. Até o agente ter seu primeiro snapshot, as consultas são recusadas, exceto o que descreve os dados.
5. **Migre primeiro as regras existentes:** as regras do controle de acesso baseado em arquivo do Trino deixam de valer. Crie antes as políticas correspondentes.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: o servidor GrantForge, o arquivo do token de agente, um nome para este coordinator e um diretório gravável para o snapshot assinado e os eventos em fila |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública Ed25519 X.509 codificada em Base64 da página Agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite do spool (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` informa cada um |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | O máximo de decisões guardadas, padrão `100000`, até `10000000`, `0` não guarda nenhuma; e por quanto tempo, padrão `10000`, `1..3600000` milissegundos |

## Usuários, grupos e papéis

As políticas nomeiam o usuário da sessão da consulta, como o Trino o fornece após a autenticação e o mapeamento de usuários. Os grupos são os que o provedor de grupos do Trino dá ao usuário, junto com os grupos em que o GrantForge o coloca; os papéis são os que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem um snapshot utilizável, tudo é recusado exceto o que descreve os dados, executar consultas e as próprias consultas. Um serviço que não está em uso deixa o Trino sem verificação, como sem controle de acesso; as instruções de privilégios e papéis do Trino são recusadas mesmo assim.
- Um token vinculado a um serviço cujo tipo não é `trino` recusa tudo.
- O Trino pergunta antes de tudo se um usuário pode usar um catálogo: um usuário sem nenhuma permissão em um catálogo recebe o `Cannot access catalog` do próprio Trino.
- Um `SELECT` que lê uma coluna que o usuário não pode ler é recusado, nomeando a primeira dessas colunas; colunas não são removidas do resultado.
- O Trino não escreve em nenhuma tabela cujas colunas mascara para o usuário: tal `INSERT` falha enquanto a máscara se aplicar.
- Uma máscara é convertida ao tipo da coluna, então um `varchar` limitado mostra o quanto couber do valor mascarado, como 3 caracteres de um hash em `varchar(3)`.
- Um filtro de linhas ou máscara que o snapshot não consegue ler não mostra linhas, ou mostra a coluna como `NULL`, em vez dos dados.
- As decisões são guardadas por `grantforge.decision.cache.ttl.ms`, então o período de validade de uma política começa ou termina até esse tempo depois.

## Guias relacionados

- [Trino](/pt-br/plugins/trino/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
