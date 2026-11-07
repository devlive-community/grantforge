---
title: Agente do MongoDB
description: Executar o agente do MongoDB ao lado de uma implantação para transformar as políticas do GrantForge nos papéis do próprio MongoDB, substituir os privilégios de um papel em um só passo, ler o que dão os papéis integrados e personalizados fora do GrantForge, e relatar acessos a partir do log de auditoria ou do profiler.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do MongoDB é um serviço ao lado da implantação. Ele mantém o controle de acesso do MongoDB alinhado às políticas assinadas de um serviço de dados `mongodb`: um papel por usuário que contém exatamente o que as políticas lhe dão. O MongoDB então verifica cada comando por conta própria, e o agente não fica no caminho de nenhum cliente. A distribuição o fornece como o JAR executável `agents/mongodb/grantforge-agent-mongodb-<version>.jar`; ele precisa do Java 17 ou posterior.

Os testes de integração Testcontainers do repositório executam o agente, e o JAR como é distribuído, em replica sets do MongoDB `6.0`, `7.0`, `8.0` e `9.0` e em um cluster fragmentado `9.0`, e leem o log de auditoria do Percona Server for MongoDB `7.0` e `8.0`. Isso não certifica todas as outras versões; o agente recusa implantações anteriores à `6.0`. Para o plug-in de serviço, veja [MongoDB](/pt-br/plugins/mongodb/).

## Como funciona

- **Um papel por usuário contém o que o GrantForge dá.** Para cada usuário a quem as políticas dão algo, o agente mantém em `admin` um papel com o nome dele, `grantforge_alice` para `alice`, e o concede a cada usuário do MongoDB com esse nome, como `alice@admin` e `alice@sales`. `rolesInfo` mostra o que o GrantForge dá a `alice`. O agente não altera nenhum outro papel.
- **Uma mudança acontece em um só passo.** `updateRole` substitui de uma vez os privilégios de um papel, de modo que um usuário nunca tem os antigos e os novos ao mesmo tempo, nem nenhum dos dois.
- **Bancos inteiros onde nada abaixo decide o contrário.** Um tipo de acesso que uma política permite em todo um banco, sem política que possa tirá-lo abaixo, é concedido no banco, de modo que as coleções criadas depois o têm de imediato. Caso contrário, é concedido coleção por coleção, e as novas são cobertas na sincronização seguinte.
- **Nunca mais do que as políticas permitem.** O privilégio do MongoDB de excluir um banco é o privilégio de excluir cada coleção dele. O agente o concede só onde as políticas também dão isso; caso contrário, retém e registra uma vez qual usuário não pode fazer o quê e por quê.
- **As políticas do momento presente.** Cada sincronização planeja segundo as políticas como se aplicam naquele momento, de modo que os períodos de validade começam e terminam na hora, e segundo os usuários, bancos e coleções que o MongoDB tem, de modo que os novos são cobertos na sincronização seguinte.
- **Papéis do MongoDB são grupos.** Uma política para um grupo se aplica aos usuários que têm um papel do MongoDB com esse nome, junto com os grupos em que o GrantForge os coloca.
- **Mudanças manuais são desfeitas.** A cada sincronização o agente compara seus papéis com os planos e corrige privilégios alterados à mão, papéis que foram feitos herdar, concessões de um papel a outra pessoa e papéis de que ninguém precisa. Se nada mudou, não envia nada.
- **Os papéis fora do GrantForge são lidos pelo que dão.** O MongoDB soma o que todos os papéis de um usuário dão, então o agente não pode tirar o que um usuário tem por outro papel. O MongoDB diz o que cada papel dá, também os integrados e herdados, então o agente avisa, uma vez cada, de um papel como `readWrite@hr` que dá o que uma política nega, nomeando o usuário, o acesso e a política. Também avisa dos usuários que podem administrar usuários, e assim se dar qualquer coisa, dos usuários com `root` ou toda ação em todo recurso, e de um servidor que não verifica nenhuma autorização.
- **Os acessos vêm do log de auditoria ou do profiler.** Veja Relatar acessos abaixo. Cada evento diz se o GrantForge decidiu, ou seja, se as políticas decidem como o MongoDB, ou se um papel externo permitiu o comando. Uma agregação é relatada para cada coleção que seus estágios `$lookup`, `$graphLookup` e `$unionWith` leem e `$out` e `$merge` gravam. Os eventos nomeiam o comando e seu namespace, nunca seus argumentos, que podem conter documentos e senhas.

## Implantação

1. Adicione um serviço de dados `mongodb` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é mostrado só uma vez; guarde-o em um arquivo que só a conta do agente possa ler.
2. Crie o usuário do MongoDB do agente. Ele cria e altera papéis e lê usuários e papéis com `userAdminAnyDatabase`; lista coleções com um papel que só tem isso; e lê as opções do servidor e o profiler com `clusterMonitor`. `userAdminAnyDatabase` pode se dar qualquer coisa, então proteja este usuário como a um administrador. O nome do papel de listagem não deve começar com o prefixo de papéis do agente.

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. Para relatar acessos, configure uma das fontes de Relatar acessos abaixo.
4. Escreva o arquivo de configurações, substituindo os endereços, caminhos e nomes de exemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   Esses caminhos ficam na máquina do agente, e o diretório de cache precisa permitir escrita. Os arquivos de senha são lidos como UTF-8 e aparados.
5. Inicie o agente, por exemplo como serviço do systemd:

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   Substitua `<version>` pela versão da distribuição. Configurações passadas como `java -Dgrantforge.…=… -jar …` sobrepõem o arquivo. O agente para de forma limpa com SIGTERM e envia ou enfileira os eventos pendentes; o MongoDB mantém o que recebeu.
6. **Levar os privilégios existentes para políticas:** papéis concedidos fora do GrantForge continuam valendo ao lado dos do agente. Crie as políticas correspondentes e depois revogue esses papéis. Os avisos do agente apontam os que dão o que uma política nega.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórios: o servidor do GrantForge, o arquivo do token de agente, um nome para este agente e um diretório com escrita para o snapshot assinado, os eventos enfileirados e a posição de leitura do log de auditoria |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página Agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.mongodb.uri` | Obrigatória: a string de conexão, sem credenciais |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256` (padrão), `SCRAM-SHA-1`, `MONGODB-X509`, `PLAIN` ou `GSSAPI` |
| `grantforge.mongodb.user`, `grantforge.mongodb.password.file`, `grantforge.mongodb.auth.source` | O usuário do agente, com `GSSAPI` seu principal e com `MONGODB-X509` opcional; sua senha; e para SCRAM o banco em que está definido, `admin` por padrão |
| `grantforge.mongodb.tls.ca.file`, `grantforge.mongodb.tls.certificate.key.file`, `grantforge.mongodb.tls.certificate.key.password.file` | Com `tls=true` na string de conexão: os certificados CA PEM que verificam os servidores, vazio para aquilo em que o Java confia; um arquivo PEM com o certificado de cliente e sua chave; e a senha de uma chave criptografada |
| `grantforge.mongodb.kerberos.keytab`, `grantforge.mongodb.kerberos.service.name` | `GSSAPI`: o keytab do principal, senão a senha é usada, e o nome de serviço do principal dos servidores, `mongodb` por padrão. O realm vem do `krb5.conf` da máquina |
| `grantforge.mongodb.timeout.ms` | Quanto podem levar encontrar um servidor, conectar e um comando, `30000` por padrão, no intervalo `1..600000` milissegundos |
| `grantforge.mongodb.role.prefix` | Como começam os nomes dos papéis do agente, `grantforge_` por padrão. Letras, dígitos e sublinhados, terminando em sublinhado, no máximo 32 caracteres. Todo papel de `admin` cujo nome começa assim pertence ao agente |
| `grantforge.mongodb.unmanaged.users` | Usuários que o agente deixa em paz, separados por vírgulas, como contas de serviço. Ele sempre deixa em paz o próprio usuário e usuários com `root`, `__system` ou toda ação em todo recurso |
| `grantforge.mongodb.databases` | Os bancos em que o agente concede por coleção, separados por vírgulas, vazio para todos. Os demais recebem só o que pode ser concedido no todo, para que um banco com muitíssimas coleções não seja listado a cada sincronização |
| `grantforge.mongodb.audit.files` | Os arquivos do log de auditoria a ler, separados por vírgulas. Vazio lê o profiler |
| `grantforge.sync.interval.ms` | Com que frequência o agente coloca o MongoDB em sintonia, padrão `10000`, intervalo `1000..3600000` milissegundos |
| `grantforge.audit.log` | `true` por padrão. `false` não lê nem relata acessos |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Quanto atrás do presente o profiler é lido (`2000` por padrão), com que frequência os acessos são lidos (`5000` milissegundos por padrão) e o máximo de entradas que uma leitura toma de um arquivo ou banco (`1000` por padrão) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` envia cada um |

## Relatar acessos

| Fonte | Onde | O que informa | Como ativar |
| --- | --- | --- | --- |
| Log de auditoria | MongoDB Enterprise e Percona Server for MongoDB | O que foi permitido e o que foi recusado, em cada membro cujo arquivo o agente pode ler | Inicie o `mongod` com `--auditDestination file --auditFormat JSON --auditPath <path>` e `--setParameter auditAuthorizationSuccess=true`, e nomeie os arquivos em `grantforge.mongodb.audit.files` |
| Profiler | Todas as edições | O que rodou no membro com que o agente fala, o primário de um replica set; nenhuma recusa | `db.setProfilingLevel(2)` em cada banco a relatar. Tem custo para o servidor, então o agente nunca o ativa, e `system.profile` é uma pequena coleção limitada |

O log de auditoria é lido de arquivos, então execute o agente no nó ou dê a ele um diretório que os reúna. Os testes leem o log de auditoria do Percona Server, que tem o formato do MongoDB Enterprise. Um `mongos` não tem profiler, então um cluster fragmentado relata acessos apenas pelo log de auditoria.

| Comando | Evento de acesso |
| --- | --- |
| `find`, `aggregate`, `count`, `distinct`, `listIndexes` | `read` em cada coleção lida |
| `aggregate` que começa com `$changeStream` | `change_stream` |
| `insert`, `update`, `delete`, `findAndModify`, `bulkWrite` | `insert`, `update` ou `remove` em cada coleção gravada; também `bypass_validation` quando pedido |
| `createIndexes`, `dropIndexes` | `create_index`, `drop_index` |
| `collMod`, `validate`, `convertToCapped` | `alter` |
| `drop`, `create`, `dropDatabase` | `drop`, `create`, `drop_database` |
| `listDatabases`; `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `currentOp` | `list_databases`; `monitor` |

O MongoDB recusa um comando como um todo sem dizer qual de suas coleções foi o motivo, então uma recusa é relatada para as que as políticas negam, ou para a primeira quando não negam nenhuma.

## Usuários, grupos e papéis

As políticas nomeiam os usuários do MongoDB pelo nome de usuário. Um nome definido em vários bancos é um só usuário, e o papel do agente é concedido a todos. Grupos são os papéis do MongoDB de um usuário e os grupos em que o GrantForge o coloca. Papéis são os que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem snapshot, o agente não muda nada. Um token vinculado a um serviço sem uso, ou a um serviço cujo tipo não é `mongodb`, não concede nada: o agente exclui seus papéis, e os do próprio MongoDB permanecem.
- Enquanto o agente não roda, o MongoDB mantém o que recebeu por último e as mudanças de políticas esperam. Uma mudança chega ao MongoDB dentro dos intervalos de atualização e sincronização; um `mongos` a aplica quando seu cache de usuários expira, por padrão em até 30 segundos (`userCacheInvalidationIntervalSecs`).
- Os privilégios do MongoDB nomeiam bancos e coleções com exatidão, então um curinga em uma política é concedido para o que existe. Um banco existe quando tem uma coleção: até lá, `create` em um banco que uma política nomeia não tem sobre o que ser concedido.
- Usuários não definidos em lugar nenhum do MongoDB, como aqueles que a autorização LDAP mapeia para papéis por conta própria, não são vistos, e o agente não mantém papel para eles.
- Um privilégio em um banco inteiro cobre suas coleções, mas não suas coleções `system.`, e os bancos do próprio MongoDB ficam com o MongoDB.
- Renomear coleções e as demais ações administrativas do MongoDB não são tipos de acesso: ficam com papéis fora do GrantForge.
- O MongoDB não filtra linhas nem mascara campos por papel; uma [visão](/pt-br/plugins/mongodb/) dá uma leitura restrita.

## Guias relacionados

- [MongoDB](/pt-br/plugins/mongodb/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
