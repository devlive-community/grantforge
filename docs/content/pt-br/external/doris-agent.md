---
title: Agente do Apache Doris
description: Executar o agente do Apache Doris ao lado do cluster, para que as políticas do GrantForge virem os papéis, privilégios de colunas, privilégios de grupos de carga de trabalho e políticas de linha do próprio Doris, dados a cada identidade de um usuário, concedidos sobre bancos de dados e catálogos inteiros só onde o Doris não daria mais, e que os acessos sejam informados a partir da tabela do log de auditoria do Doris.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do Apache Doris é um serviço ao lado do cluster. Ele mantém o controle de acesso próprio do Doris em sintonia com as políticas assinadas de um serviço de dados `doris`: um papel por usuário que contém exatamente o que as políticas lhe dão, até colunas e grupos de carga de trabalho, e uma política de linha por filtro de linhas. O Doris então verifica cada instrução por conta própria, e o agente nunca fica entre os clientes e os frontends. A distribuição o entrega como um JAR executável, `agents/doris/grantforge-agent-doris-<version>.jar`, que requer Java 17 ou mais recente.

Os testes de integração com Testcontainers do repositório executam o agente, e o JAR como distribuído, contra o Apache Doris `2.1`, `3.1`, `4.0` e `4.1`. Isso não certifica todas as outras versões; o agente recusa clusters anteriores a `2.1`. Para o plug-in de serviço, veja [Apache Doris](/pt-br/plugins/doris/).

## Como funciona

- **Um papel por usuário contém o que o GrantForge dá.** Para cada usuário a quem as políticas dão algo, o agente mantém um papel com o nome dele, `grantforge_alice` para `alice`, com um comentário que nomeia o usuário, e o concede a cada identidade do usuário, como `'alice'@'%'` e `'alice'@'10.%'`. `SHOW ROLES` mostra o que o GrantForge dá a `alice`; o agente não altera nenhum outro papel nem nenhum privilégio que não concedeu.
- **Bancos de dados e catálogos inteiros onde nada abaixo é decidido de outra forma.** Ler, carregar e mostrar visões que uma política permite em todo um banco de dados ou catálogo, sem política que possa tirá-los abaixo, são concedidos no banco de dados ou no catálogo, de modo que tabelas criadas depois os têm na hora. Caso contrário, são concedidos tabela por tabela, e a leitura coluna por coluna. Alterar é concedido tabela por tabela, pois o privilégio do Doris sobre um banco de dados também o renomearia.
- **Nunca mais do que as políticas permitem.** O privilégio do Doris de criar bancos de dados em um catálogo é o de criar tabelas em todos eles, e o de excluir um banco de dados o de excluir todas as suas tabelas. O agente concede cada um só onde as políticas dão também o privilégio mais amplo; caso contrário, retém-no e registra uma vez qual usuário não pode fazer o quê e por quê.
- **Grupos de carga de trabalho são concedidos pelo nome.** Uma política sobre grupos de carga de trabalho dá `USAGE_PRIV` sobre cada grupo que permite, para que um usuário possa executar consultas nele; grupos criados depois são cobertos na próxima sincronização.
- **Filtros de linhas viram políticas de linha.** Um filtro de linhas é uma política de linha permissiva do papel do usuário na tabela. Um filtro novo está no lugar antes dos privilégios que restringe; um filtro que muda é substituído por trás de uma política restritiva que não mostra linhas, de modo que o usuário nunca vê mais do que qualquer dos dois mostra. Cada filtro é executado antes na sua tabela; um que o Doris não consegue executar ali ou recusa, como um que nomeia uma coluna que a tabela não tem, é substituído por um que não mostra nenhuma linha ao usuário, e registrado.
- **As políticas do momento presente.** Cada sincronização planeja pelas políticas como se aplicam naquele momento, de modo que os períodos de validade começam e terminam na hora, e pelos usuários, catálogos, bancos de dados, tabelas e colunas do Doris, de modo que os novos são cobertos na próxima sincronização.
- **Papéis do Doris são grupos.** Uma política para um grupo vale para usuários que têm um papel do Doris com esse nome, junto com os grupos em que o GrantForge os coloca.
- **Mudanças feitas à mão são desfeitas.** O agente compara a cada sincronização os privilégios dos seus papéis, como `SHOW ROLES` os mostra, e as suas políticas de linha com os planos, e restaura o que difere. O Doris mostra privilégios de colunas só por usuário, unidos sobre todos os seus privilégios: o agente concede as colunas que um plano nomeia onde um usuário não consegue ler todas, e retira as outras do seu papel uma vez; uma coluna que continua legível vem de um privilégio fora do GrantForge. Quando nada mudou, o agente não envia nada.
- **Privilégios fora do GrantForge são informados.** O Doris soma privilégios e une políticas de linha permissivas com OR, então o agente não pode tirar o que um usuário tem diretamente ou por outro papel. Ele avisa uma vez de cada privilégio, privilégio de coluna ou política de linha assim que dá o que uma política nega, nomeando o usuário, o privilégio e sua origem, e de usuários com `ADMIN_PRIV` ou `NODE_PRIV`, que nenhum papel limita.
- **Os acessos vêm do log de auditoria.** O agente lê `__internal_schema.audit_log`, que cada frontend escreve, e informa cada instrução como um evento por tabela, banco de dados ou catálogo que ela tocou; quais tabelas, ele lê da própria instrução com o analisador de Doris do Druid, pois o log não nomeia nenhuma. Uma recusa é um evento negado para o privilégio e o objeto que o erro do Doris nomeia. Cada evento diz se o GrantForge decidiu, isto é, se as políticas decidem como o Doris, ou se um privilégio fora do GrantForge permitiu. O log é lido atrás do presente pelo intervalo de lotes dos frontends, `audit_plugin_max_batch_interval_sec`, mais cinco segundos.

| Instrução | Evento de acesso |
| --- | --- |
| `SELECT`, a parte de leitura de `INSERT … SELECT` | `select` em cada tabela lida |
| `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` | `load` na tabela escrita |
| `ALTER TABLE`, `DROP TABLE`, `SHOW CREATE VIEW` | `alter`, `drop`, `show_view` na tabela |
| `CREATE TABLE`, `CREATE VIEW` | `create` no banco de dados |
| `DROP DATABASE` | `drop_database` no banco de dados |
| `CREATE DATABASE` | `create_database` no catálogo |

## Implantação

1. Adicione um serviço de dados `doris` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é mostrado só uma vez; guarde-o em um arquivo que só a conta do agente possa ler.
2. Crie o usuário do Doris do agente. Ele cria papéis e políticas de linha, concede o que as políticas dão e lê os privilégios de cada usuário e o log de auditoria, o que `ADMIN_PRIV` permite:

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. Para informar os acessos, ative o log de auditoria do Doris onde ele está desativado, como no Doris 2.1: `SET GLOBAL enable_audit_plugin = true`. Um `audit_plugin_max_batch_interval_sec` mais curto que os 60 segundos padrão informa os acessos mais cedo.
4. Escreva o arquivo de configurações, substituindo os endereços, caminhos e nomes de exemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   Esses caminhos ficam na máquina do agente, e o diretório de cache precisa permitir escrita. Os arquivos de senha são lidos como UTF-8 e aparados.
5. Inicie o agente, por exemplo como serviço do systemd:

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   Substitua `<version>` pela versão da distribuição. Configurações passadas como `java -Dgrantforge.…=… -jar …` sobrepõem o arquivo. O agente para de forma limpa com SIGTERM e envia ou enfileira os eventos pendentes; o Doris mantém o que recebeu.
6. **Levar os privilégios existentes para políticas:** privilégios concedidos fora do GrantForge continuam valendo ao lado dos papéis do agente. Crie as políticas correspondentes e depois revogue esses privilégios; os avisos do agente nomeiam os que dão o que uma política nega.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórios: o servidor do GrantForge, o arquivo do token de agente, um nome para este agente e um diretório com escrita para o snapshot assinado, os eventos enfileirados e a posição de leitura do log de auditoria |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página Agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.doris.hosts`, `grantforge.doris.user` | Obrigatórios: os endereços do protocolo MySQL dos frontends, tentados em sequência quando um não pode ser alcançado, a porta `9030` se não informada, e o usuário do Doris do agente |
| `grantforge.doris.password.file` | A senha do usuário; se omitida, o agente entra sem ela |
| `grantforge.doris.ssl.mode`, `grantforge.doris.tls.ca` | `disable` (padrão), `trust`, `verify-ca` ou `verify-full`, para frontends que oferecem TLS; e o certificado de CA PEM que os verifica, vazio para o que o Java confia |
| `grantforge.doris.timeout.ms` | Quanto a conexão e uma instrução podem levar, padrão `30000`, intervalo `1..600000` milissegundos |
| `grantforge.doris.role.prefix` | O início dos nomes dos papéis do agente, padrão `grantforge_`; letras, dígitos e sublinhados, terminando em sublinhado, no máximo 32 caracteres. O agente é dono de cada papel e política de linha cujo nome começa assim |
| `grantforge.doris.unmanaged.users` | Usuários que o agente deixa em paz, separados por vírgulas, como contas de serviço; ele sempre deixa em paz o próprio usuário, `root`, `admin` e os usuários com `ADMIN_PRIV` ou `NODE_PRIV` |
| `grantforge.doris.catalogs` | Os catálogos em que o agente concede por banco de dados, tabela e coluna, separados por vírgulas; vazio para todos. Os outros recebem só o que pode ser concedido sobre eles inteiros, de modo que um catálogo externo com muitas tabelas não precisa ser listado a cada sincronização |
| `grantforge.sync.interval.ms` | Com que frequência o agente coloca o Doris em sintonia, padrão `10000`, intervalo `1000..3600000` milissegundos |
| `grantforge.audit.log` | Padrão `true`; `false` não lê nenhum log de auditoria e não informa acessos |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Quanto atrás do presente o log é lido, por padrão o intervalo de lotes do cluster mais cinco segundos; com que frequência, padrão `5000` milissegundos; e o máximo de entradas por leitura, padrão `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` envia cada um |

## Usuários, grupos e papéis

As políticas nomeiam os usuários do Doris pelos seus nomes de usuário; um nome de usuário com vários hosts é um só usuário, e o papel do agente vai para cada uma de suas identidades. Os grupos são os papéis do Doris de um usuário junto com os grupos em que o GrantForge o coloca; os papéis são os que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem snapshot o agente não muda nada. Um serviço que não está em uso, ou um token vinculado a um serviço cujo tipo não é `doris`, não concede nada: o agente exclui seus papéis e políticas de linha, e valem os privilégios próprios do Doris.
- Enquanto o agente não roda, o Doris mantém o que recebeu por último; as mudanças de políticas esperam por ele. Uma mudança chega ao Doris dentro do intervalo de atualização e do de sincronização.
- Tabelas criadas em um banco de dados em que o agente concede tabela por tabela, e bancos de dados de um catálogo em que concede banco por banco, recebem seus privilégios na próxima sincronização.
- As políticas de linha do Doris não conseguem limitar `root` e `admin`. Um usuário para quem o Doris não mostra nenhuma política de linha vê todas as linhas de uma tabela; políticas de linha permissivas criadas fora do GrantForge para o mesmo usuário ampliam o que o seu filtro mostra.
- O Doris verifica `USAGE_PRIV` em um grupo de carga de trabalho quando uma consulta roda nos backends; uma consulta que o frontend responde sozinho, como uma constante ou um resultado em cache, roda sem a verificação. Todo usuário pode usar o grupo `normal`, pois o Doris o dá a todos.
- Os usuários para quem o agente mantém papéis precisam existir no Doris; usuários que entram por LDAP sem um usuário próprio no Doris não recebem papel.
- O Doris mascara valores apenas por meio do Apache Ranger, por isso as políticas não podem mascarar valores.

## Guias relacionados

- [Apache Doris](/pt-br/plugins/doris/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
