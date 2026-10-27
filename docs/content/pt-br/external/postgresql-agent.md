---
title: Agente do PostgreSQL
description: Rodar o agente do PostgreSQL ao lado dos servidores, para que as políticas do GrantForge virem papéis, privilégios e políticas de segurança de linhas do próprio PostgreSQL, até colunas e linhas, banco de dados a banco de dados em uma transação cada, e os acessos sejam informados a partir do log do servidor.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do PostgreSQL é um serviço ao lado dos servidores. Ele mantém o controle de acesso do próprio PostgreSQL de acordo com as políticas assinadas de um serviço de dados `postgresql`: um papel por usuário que tem exatamente o que as políticas dão ao usuário, em cada banco de dados que mantém, e uma política de segurança de linhas por filtro de linhas. O PostgreSQL então verifica cada instrução sozinho, e o agente nunca fica entre os clientes e o servidor. A distribuição o entrega como um JAR executável, `agents/postgresql/grantforge-agent-postgresql-<version>.jar`, que requer Java 17 ou mais recente.

Os testes de integração com Testcontainers do repositório rodam o agente, e o JAR como distribuído, contra o PostgreSQL `15`, `16`, `17` e `18` com pgaudit. Isso não certifica todas as outras versões; o agente recusa servidores anteriores ao `15`. Veja [PostgreSQL](/pt-br/plugins/postgresql/) para o plug-in de serviço.

## Como funciona

- **Um papel por usuário tem o que o GrantForge dá.** Para cada usuário a quem as políticas dão algo, o agente mantém um papel com o nome dele, `grantforge_alice` para `alice`, e torna o usuário um membro que tem os privilégios do papel sem `SET ROLE`. O agente escreve um comentário em cada um dos seus papéis e não toma nenhum papel sem ele por seu, de modo que não altera nenhum outro papel nem nenhum privilégio que não concedeu.
- **Cada banco de dados muda em uma transação.** O agente entra em cada banco de dados que mantém e faz ali as suas mudanças em uma transação, de modo que os usuários veem uma versão das políticas aplicada a um banco inteiro ou não aplicada.
- **Tabelas inteiras onde nada abaixo é decidido de outro modo.** Um tipo de acesso que uma política permite em toda uma tabela, sem nenhuma política que possa tirá-lo de uma coluna, é concedido na tabela, de modo que colunas adicionadas depois o têm na hora. Caso contrário, leitura, inserção e atualização são concedidas coluna a coluna. `CONNECT` no banco de dados, `USAGE` no esquema e nas sequências das colunas serial em que o usuário pode inserir vêm junto.
- **Filtros de linhas só restringem.** Um filtro de linhas é uma política de segurança de linhas restritiva do papel do usuário na tabela, para `SELECT`. Onde a segurança de linhas está desligada, o agente a liga junto com uma política permissiva que mostra todas as linhas a todos, com o nome do seu prefixo (`grantforge`), de modo que nada muda para os outros; ele a desliga de novo quando a tabela não tem mais filtro nem política própria. A segurança de linhas que um dono ligou continua sendo do dono, e as políticas do agente só restringem o que ela mostra. Um filtro que o PostgreSQL recusa, um que mudaria a quem a política se aplica, ou um texto de mais de uma instrução é substituído por uma política que não mostra nenhuma linha ao usuário, e registrado.
- **As políticas do momento presente.** Cada sincronização planeja pelas políticas como valem naquele momento, de modo que períodos de validade começam e terminam na hora, e pelos usuários, esquemas, tabelas e colunas que o PostgreSQL tem, de modo que os novos são cobertos na sincronização seguinte.
- **Papéis são grupos.** Uma política para um grupo vale para usuários membros de um papel do PostgreSQL com esse nome, diretamente ou por outros papéis, junto com os grupos em que o GrantForge os coloca.
- **Mudanças feitas à mão são desfeitas.** A cada sincronização o agente compara os privilégios dos seus papéis com o que o PostgreSQL mostra, e as suas políticas de segurança de linhas com o que criou por último; o que difere é restaurado na sincronização seguinte. Quando nada mudou, o agente não envia nada.
- **Privilégios fora do GrantForge são informados.** O PostgreSQL soma privilégios, então o agente não pode tirar o que um usuário tem por si, por outro papel ou por PUBLIC, nem o que pode fazer com as tabelas de que é dono. Ele avisa uma vez de cada privilégio desses que dá o que uma política nega, nomeando o usuário, o privilégio, como o usuário o tem e a política.
- **Os acessos vêm do log do servidor.** O agente lê o log JSON do servidor PostgreSQL: o log de sessão do pgaudit para o que o PostgreSQL permitiu, um evento por tabela que uma instrução leu ou gravou, e as recusas do PostgreSQL por falta de um privilégio, um evento negado para o que o erro nomeia. Cada evento diz se o GrantForge o decidiu, ou seja, se as políticas decidem como o PostgreSQL, ou se um privilégio fora do GrantForge o permitiu.

| Instrução | Evento de acesso |
| --- | --- |
| `SELECT`, `COPY … TO`, o lado de leitura de outras instruções | `select` em cada tabela lida |
| `INSERT`, `COPY … FROM` | `insert` na tabela gravada |
| `UPDATE`, `MERGE`, `SELECT … FOR UPDATE` | `update` na tabela |
| `DELETE`, `TRUNCATE` | `delete`, `truncate` na tabela |
| `CREATE TRIGGER` | `trigger` na tabela |
| `CREATE TABLE`, `CREATE VIEW` e outros objetos | `create` no esquema |
| `CREATE SCHEMA`, `CREATE TEMPORARY TABLE` | `create_schema`, `temporary` no banco de dados |
| Uma conexão recusada, um esquema recusado por falta de `USAGE` | `connect` no banco de dados, `usage` no esquema |

## Implantação

1. Adicione um serviço de dados `postgresql` e configure as suas políticas. Abra **Permissões de dados → Agentes**, escolha o serviço e emita um token de agente. Ele é mostrado só uma vez; guarde-o em um arquivo que só a conta do agente possa ler.
2. Crie o usuário do PostgreSQL do agente como superusuário. Ele concede privilégios em tabelas de que não é dono, liga e desliga a segurança de linhas, mantém papéis e lê o log do servidor:

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   Os testes rodam o agente como superusuário. Superusuários não são limitados nem por privilégios nem pela segurança de linhas, então o agente não mantém papel para eles.
3. Para informar os acessos, faça o PostgreSQL gravar o log do servidor em JSON e carregar o pgaudit, dos pacotes do PGDG como `postgresql-17-pgaudit` ou da plataforma, em `postgresql.conf`, e reinicie o servidor:

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   Sem o pgaudit o agente informa o que o PostgreSQL recusou, não o que permitiu; sem o log JSON ele não informa nada e diz isso uma vez.
4. Escreva o arquivo de configuração, substituindo os endereços, caminhos e nomes de exemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   Esses caminhos ficam na máquina do agente, e o diretório de cache precisa ser gravável. Arquivos de senha são lidos como UTF-8 e aparados.
5. Inicie o agente, por exemplo como serviço do systemd:

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   Substitua `<version>` pela versão da distribuição. Configurações passadas como `java -Dgrantforge.…=… -jar …` substituem as do arquivo. O agente para de forma limpa com SIGTERM e envia ou enfileira os eventos pendentes; o PostgreSQL mantém o que recebeu.
6. **Passar os privilégios existentes para políticas:** privilégios concedidos fora do GrantForge continuam valendo ao lado dos papéis do agente. Crie as políticas correspondentes e então revogue os privilégios; os avisos do agente nomeiam os que dão o que uma política nega.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórios: o servidor GrantForge, o arquivo do token do agente, um nome para este agente e um diretório gravável para o snapshot assinado, os eventos enfileirados e a posição de leitura do log do servidor |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública Ed25519 X.509 codificada em Base64 da página Agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.postgresql.hosts`, `grantforge.postgresql.user` | Obrigatórios: os servidores como `host:port`, tentados em sequência até que um alcançável seja o primário, e o usuário do PostgreSQL do agente |
| `grantforge.postgresql.database` | O banco de dados em que o agente entra primeiro, para ler os papéis, os bancos de dados e o log do servidor, padrão `postgres` |
| `grantforge.postgresql.password.file` | A senha do usuário; se omitida, o agente entra sem senha |
| `grantforge.postgresql.ssl.mode` | TLS como o `sslmode` da libpq o nomeia, padrão `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca e verify-full: certificado de CA PEM que verifica os servidores; vazio confia no que o Java confia |
| `grantforge.postgresql.tls.cert`, `grantforge.postgresql.tls.key` | Certificado de cliente PEM e sua chave privada PKCS #8, juntos, para entrar com um certificado (autenticação `cert`) em vez de uma senha; eles requerem `require` ou um modo de verificação, e um modo de verificação requer então `grantforge.postgresql.tls.ca` |
| `grantforge.postgresql.timeout.ms` | Quanto podem levar a conexão e uma instrução, padrão `30000`, intervalo `1..600000` milissegundos |
| `grantforge.postgresql.databases` | Os bancos de dados que o agente mantém, separados por vírgulas; vazio mantém todo banco que aceita conexões |
| `grantforge.postgresql.role.prefix` | O começo dos nomes de papel do agente, padrão `grantforge_`; letras, dígitos e sublinhados, terminando em sublinhado, com no máximo 32 caracteres e sem começar com `pg_`. O agente é dono dos papéis que marcou cujo nome começa assim, e das políticas de segurança de linhas com o nome deles ou do prefixo sem o último sublinhado |
| `grantforge.postgresql.unmanaged.users` | Usuários que o agente deixa em paz, separados por vírgulas, como administradores; ele sempre deixa em paz o próprio usuário e os superusuários |
| `grantforge.sync.interval.ms` | Com que frequência o agente põe o PostgreSQL de acordo, padrão `10000`, intervalo `1000..3600000` milissegundos |
| `grantforge.audit.server.log` | Padrão `true`; `false` não lê log do servidor e não informa acessos |
| `grantforge.audit.server.log.interval.ms`, `grantforge.audit.server.log.max.bytes` | Com que frequência o log é lido, padrão `5000` milissegundos, e o máximo de bytes de uma leitura, padrão 16 MiB, de modo que um atraso é lido em várias rodadas |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura do GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` envia cada um |

## Usuários, grupos e papéis

As políticas nomeiam usuários do PostgreSQL, os papéis que podem entrar. Grupos são os papéis de que um usuário é membro, diretamente ou por outros papéis, junto com os grupos em que o GrantForge coloca o usuário; papéis são os que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem snapshot o agente não muda nada. Um serviço que não está em uso, ou um token vinculado a um serviço cujo tipo não é `postgresql`, não concede nada: o agente remove os seus papéis e as suas políticas de segurança de linhas, e valem os privilégios do próprio PostgreSQL.
- Enquanto o agente não roda, o PostgreSQL mantém o que recebeu por último; as mudanças de políticas esperam por ele. Uma mudança chega ao PostgreSQL dentro do intervalo de atualização e do de sincronização.
- Tabelas criadas depois de uma sincronização recebem os seus privilégios na sincronização seguinte.
- Superusuários e os donos de uma tabela podem fazer tudo com ela; superusuários, papéis com `BYPASSRLS` e os donos de uma tabela não são limitados pela segurança de linhas dela, a menos que o dono a imponha.
- Uma view lê as suas tabelas com os privilégios do dono, a menos que seja `security_invoker`: o log do servidor informa as tabelas que uma view leu como permitidas por um privilégio fora do GrantForge.
- Ler uma partição diretamente não é filtrado pela segurança de linhas da sua tabela particionada.
- O pgaudit registra uma instrução antes de o PostgreSQL executá-la, então uma instrução que falha depois, como uma inserção cuja sequência é recusada, é informada como permitida e a recusa como negada. As instruções de uma sessão que o pgaudit não registra não são informadas.
- As recusas do PostgreSQL nomeiam uma tabela sem o esquema: o evento nomeia a tabela que a instrução qualifica, a única tabela com esse nome ou a de `public`. Um objeto criado sem esquema é informado em `public`.
- O agente lê o log do servidor em que entra, o primário; os logs das réplicas não são lidos.
- O PostgreSQL não tem mascaramento de colunas, então as políticas não podem mascarar valores.

## Guias relacionados

- [PostgreSQL](/pt-br/plugins/postgresql/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
