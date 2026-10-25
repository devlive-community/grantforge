---
title: Agente do ClickHouse
description: Executar o agente do ClickHouse ao lado dos servidores, para que as políticas do GrantForge se tornem os papéis, privilégios e políticas de linhas do próprio ClickHouse, até colunas e linhas, e os acessos sejam relatados a partir do log de consultas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do ClickHouse é um serviço ao lado dos servidores. Ele mantém o controle de acesso do próprio ClickHouse de acordo com as políticas assinadas de um serviço de dados `clickhouse`: um papel por usuário que tem exatamente o que as políticas dão ao usuário, e uma política de linhas por filtro de linhas. O ClickHouse então verifica ele mesmo cada consulta, em cada um de seus protocolos, e o agente nunca fica entre os clientes e o servidor. A distribuição o entrega como um JAR executável, `agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`, que exige Java 17 ou mais recente.

Os testes de integração com Testcontainers do repositório executam o agente, e o JAR como distribuído, contra o ClickHouse `24.8`, `25.3`, `25.8`, `26.3` e `26.8`. Isso não certifica todas as demais versões; o agente recusa servidores anteriores ao `24.8`. O plug-in de serviço está descrito em [ClickHouse](/pt-br/plugins/clickhouse/).

## Como funciona

- **Um papel por usuário tem o que o GrantForge dá.** Para cada usuário a quem as políticas dão algo, o agente mantém um papel com o nome dele, `grantforge_alice` para `alice`, e o concede ao usuário como papel padrão. `SHOW GRANTS FOR grantforge_alice` mostra tudo o que o GrantForge dá a `alice`; o agente não altera nenhum outro papel nem nenhum privilégio que não concedeu.
- **Bancos de dados inteiros onde nada abaixo é decidido de outro modo.** Um tipo de acesso que uma política permite em todo um banco de dados, sem nenhuma política que possa tirá-lo de uma tabela ou coluna dele, é concedido no banco de dados, de modo que tabelas criadas depois o têm na hora. Caso contrário, é concedido tabela a tabela, e leitura e escrita coluna a coluna.
- **Filtros de linhas viram políticas de linhas.** Um filtro de linhas é uma política de linhas permissiva do papel do usuário na tabela. Um filtro novo ou alterado está no lugar antes dos privilégios que ele restringe. Um filtro que o ClickHouse recusa, ou um cujo texto faria a política de linhas se aplicar a outros, é substituído por um que não mostra nenhuma linha ao usuário, e registrado.
- **As políticas do momento presente.** Cada sincronização planeja pelas políticas como valem naquele momento, de modo que os períodos de validade começam e terminam na hora, e pelos usuários, bancos de dados, tabelas e colunas que o ClickHouse tem, de modo que os novos ficam cobertos na sincronização seguinte.
- **Papéis do ClickHouse são grupos.** Uma política para um grupo vale para os usuários que têm um papel do ClickHouse com esse nome, diretamente ou por outros papéis, junto com os grupos em que o GrantForge os coloca.
- **Mudanças feitas à mão são desfeitas.** O agente lembra o que deu a cada papel e política de linhas; o que estiver diferente é restabelecido na sincronização seguinte. Quando nada mudou, o agente não envia nada.
- **Privilégios fora do GrantForge são relatados.** O ClickHouse soma privilégios, então o agente não pode tirar o que um usuário tem diretamente ou por outro papel. Ele avisa uma vez sobre cada privilégio assim que dê o que uma política nega, nomeando o usuário, o privilégio, o papel pelo qual ele vem e a política.
- **Os acessos vêm do log de consultas.** O agente lê `system.query_log` e relata cada instrução como um evento por tabela, banco de dados ou servidor em que tocou, e uma recusa como um evento negado do que o erro do ClickHouse nomeia. Cada evento diz se o GrantForge o decidiu, isto é, se as políticas decidem como o ClickHouse, ou se um privilégio fora do GrantForge o permitiu.

| Instrução | Evento de acesso |
| --- | --- |
| `SELECT`, a parte de leitura de `INSERT … SELECT` | `select` em cada tabela lida |
| `INSERT` | `insert` na tabela escrita |
| `ALTER`, `ALTER UPDATE`, `ALTER DELETE`, `DELETE`, `UPDATE` | `alter` na tabela |
| `DROP TABLE`, `TRUNCATE`, `OPTIMIZE` | `drop`, `truncate`, `optimize` na tabela |
| `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` | `create` no banco de dados |
| `DROP DATABASE` | `drop_database` no banco de dados |
| `CREATE DATABASE`, `KILL QUERY`, `SYSTEM …` | `create_database`, `kill_query`, `system` no servidor |

## Implantação

1. Adicione um serviço de dados `clickhouse` e configure suas políticas. Abra **Permissões de dados → Agentes**, escolha o serviço e emita um token de agente. Ele só é exibido uma vez; guarde-o em um arquivo que só a conta do agente possa ler.
2. Crie o usuário do ClickHouse do agente. Ele concede o que as políticas dão, então precisa desses privilégios com a opção de concessão, e da gestão de acessos para manter papéis e políticas de linhas:

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   Os usuários para os quais o agente mantém papéis precisam estar armazenados onde possam ser alterados, no diretório de usuários `local_directory` ou `replicated`, onde o `CREATE USER` os armazena. Usuários do `users.xml` e do LDAP ficam como estão e são registrados uma vez. Um cluster deve manter seus usuários, papéis e políticas de linhas em um diretório de usuários `replicated`: assim um único agente atende o cluster inteiro.
3. Escreva o arquivo de configurações, substituindo os endereços, caminhos e nomes do exemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   Esses caminhos ficam na máquina do agente, e o diretório de cache precisa permitir escrita. Arquivos de senha são lidos como UTF-8 e aparados.
4. Inicie o agente, por exemplo como serviço do systemd:

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   Substitua `<version>` pela versão da distribuição. Configurações informadas como `java -Dgrantforge.…=… -jar …` substituem as do arquivo. O agente para de forma limpa com SIGTERM e envia ou enfileira os eventos pendentes; o ClickHouse mantém o que recebeu.
5. **Passe os privilégios existentes para políticas:** os privilégios concedidos fora do GrantForge continuam valendo ao lado dos papéis do agente. Crie as políticas correspondentes e depois revogue esses privilégios; os avisos do agente nomeiam os que dão o que uma política nega.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: o servidor GrantForge, o arquivo do token de agente, um nome para este agente e um diretório gravável para o snapshot assinado, os eventos em fila e a posição de leitura do log de consultas |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página de agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.clickhouse.urls`, `grantforge.clickhouse.user` | Obrigatórias: as interfaces HTTP dos servidores, tentadas em sequência quando uma não pode ser alcançada, e o usuário do ClickHouse do agente |
| `grantforge.clickhouse.password.file` | A senha do usuário; se omitida, o agente entra sem senha |
| `grantforge.clickhouse.tls.ca` | Certificado de CA em PEM que verifica servidores `https://`; vazio confia no que o Java confia |
| `grantforge.clickhouse.tls.cert`, `grantforge.clickhouse.tls.key` | Certificado de cliente em PEM e chave privada, juntos, para entrar com um certificado (`IDENTIFIED WITH ssl_certificate`) em vez de uma senha |
| `grantforge.clickhouse.timeout.ms` | Quanto tempo uma requisição ao ClickHouse pode levar, padrão `30000`, intervalo `1..600000` milissegundos |
| `grantforge.clickhouse.role.prefix` | O início dos nomes de papel do agente, padrão `grantforge_`; letras, dígitos e sublinhados, terminando em sublinhado. O agente é dono de todo papel e toda política de linhas cujo nome começa assim |
| `grantforge.clickhouse.unmanaged.users` | Usuários que o agente deixa em paz, separados por vírgulas, como administradores; ele sempre deixa em paz o próprio usuário |
| `grantforge.sync.interval.ms` | Com que frequência o agente põe o ClickHouse de acordo, padrão `10000`, intervalo `1000..3600000` milissegundos |
| `grantforge.audit.query.log` | Padrão `true`; `false` não lê nenhum log de consultas nem relata acessos |
| `grantforge.audit.query.log.cluster` | Um cluster cujos logs das réplicas são lidos por `clusterAllReplicas`; vazio lê o servidor que o agente consulta |
| `grantforge.audit.query.log.delay.ms`, `grantforge.audit.query.log.interval.ms`, `grantforge.audit.query.log.batch` | Quanto atrás do presente o log é lido, padrão `10000` milissegundos, já que o ClickHouse o escreve a cada 7,5 segundos; com que frequência, padrão `5000` milissegundos; e o máximo de entradas por leitura, padrão `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` envia cada um |

## Usuários, grupos e papéis

As políticas nomeiam usuários do ClickHouse. Os grupos são os papéis do ClickHouse que um usuário tem, diretamente ou por outros papéis, junto com os grupos em que o GrantForge coloca o usuário; os papéis são os que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem snapshot, o agente não muda nada. Um serviço que não está em uso, ou um token vinculado a um serviço cujo tipo não é `clickhouse`, não concede nada: o agente remove seus papéis e políticas de linhas, e valem os privilégios do próprio ClickHouse.
- Enquanto o agente não está em execução, o ClickHouse mantém o que recebeu por último; as mudanças de políticas esperam por ele. Uma mudança chega ao ClickHouse dentro do intervalo de atualização e do intervalo de sincronização.
- Tabelas criadas em um banco de dados que o agente concede tabela a tabela recebem seus privilégios na sincronização seguinte.
- Um usuário para quem o ClickHouse não mostra nenhuma política de linhas vê todas as linhas de uma tabela, como a configuração `users_without_row_policies_can_read_rows` do ClickHouse permite por padrão; com ela desativada, usuários sem filtro não veem nenhuma linha das tabelas filtradas. Políticas de linhas permissivas criadas fora do GrantForge para o mesmo usuário ampliam o que seu filtro mostra.
- `system` concede toda instrução `SYSTEM`, incluindo `SYSTEM SHUTDOWN`.
- O log de consultas relata o que o ClickHouse registra: instruções de usuários com `log_queries` desativado não são relatadas. Em uma recusa, o ClickHouse pode nomear a tabela quando falta ao usuário uma coluna; o evento então nomeia a tabela.
- O ClickHouse não tem mascaramento de colunas fora do ClickHouse Cloud, então as políticas não podem mascarar valores.

## Guias relacionados

- [ClickHouse](/pt-br/plugins/clickhouse/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
