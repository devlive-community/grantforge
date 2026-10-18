---
title: Agente do Apache HBase
description: Instale o coprocessador nos masters e region servers do HBase para verificar operações, leituras e escritas com as políticas do GrantForge, devolver só as colunas que um usuário pode ler e ocultar o que ele não pode usar.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do HBase é um coprocessador de masters, regiões e region servers: o HBase o consulta a cada operação, leitura e escrita, e ele responde a partir das políticas assinadas do serviço de dados `hbase`. A distribuição o inclui como um único JAR compatível com Java 8, `agents/hbase/grantforge-agent-hbase-<version>.jar`, com as dependências realocadas dentro.

Os testes de integração com Testcontainers do repositório executam o JAR no HBase `2.5.16`, `2.6.7` e `3.0.0`. Isso não certifica todas as outras versões. Veja [Apache HBase](/pt-br/plugins/hbase/) para o plug-in de serviço.

## Como funciona

- **Cada solicitação é verificada em cada escopo que nomeia.** No HBase uma permissão cobre tudo no seu escopo, então o agente decide uma solicitação no seu namespace, na sua tabela, na sua família de colunas e na sua coluna: uma negação em qualquer um deles a recusa, uma permissão em qualquer um a concede.
- **Os donos de um namespace têm suas tabelas.** `create` e `admin` em um namespace permitem criá-lo, criar tabelas nele e administrá-las, sem uma política por tabela.
- **As leituras devolvem o que pode ser lido.** Quando um usuário só pode ler parte de uma tabela, como uma família de colunas, um get ou scan devolve essas colunas em vez de falhar; um usuário que não pode ler nada da tabela é recusado.
- **As listagens omitem o que não pode ser usado.** Listar namespaces e tabelas mostra só os que o usuário pode usar, ou em que pode usar algo, e listar snapshots só os das tabelas que ele administra; descrever um namespace que ele não pode usar é recusado.
- **As recusas dizem por quê.** O cliente recebe `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)`, ou a política que negou.
- **As decisões são guardadas e os acessos agrupados.** As decisões do snapshot atual são guardadas, por padrão 100.000 por 10 segundos, e um snapshot novo começa do zero. Acessos permitidos idênticos dentro de uma janela, por padrão 10 segundos, são relatados como um único evento de acesso que informa quantos foram; recusas são relatadas uma a uma, imediatamente.
- **Os servidores não são verificados.** Os superusuários do HBase, nomeados em `hbase.superuser`, e o trabalho dos próprios servidores não são verificados nem auditados. As tabelas do namespace `hbase`, como `hbase:meta`, podem ser lidas por todos, como os clientes precisam.

| Operação | Acesso necessário |
| --- | --- |
| Get, scan | `read` na tabela, ou nas colunas devolvidas |
| Put, increment, append, delete de colunas | `write` em cada coluna escrita |
| Delete de uma família de colunas ou de uma linha | `write` em cada família de colunas, pois o HBase exclui uma linha família por família |
| Check-and-mutate | `read` na coluna comparada, ou na tabela com um filtro, e `write` no que é escrito |
| Criar tabela | `create` no namespace |
| Excluir, truncar, modificar, habilitar, desabilitar, flush, split, merge, compactar | `admin` ou `create` na tabela |
| Mover, atribuir, desatribuir, colocar offline | `admin` na tabela |
| Criar, restaurar, excluir ou listar um snapshot | `admin` na tabela; clonar também exige `create` no namespace de destino |
| Carga em massa | `create` na tabela |
| Criar, modificar, excluir namespace | `admin` no namespace |
| Descrever um namespace; listar namespaces e tabelas | Qualquer acesso a ele ou dentro dele |
| Balanceador, desligamento, cotas, pares de replicação, grupos de region servers, procedimentos, desativação, parada de region servers, rotação de WAL | `admin` em `global` |

## Implantação

1. Adicione um serviço de dados `hbase` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é exibido só uma vez; guarde-o em um arquivo que apenas a conta do HBase possa ler.
2. Copie o JAR para `lib` de **cada master e region server**, como `/opt/hbase/lib`.
3. Adicione as configurações ao `hbase-site.xml` de cada um, substituindo o endereço, os caminhos e o nome da instância de exemplo:

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   O diretório de cache precisa ser gravável pela conta do HBase. Substitua o `AccessController` do próprio HBase nas listas de coprocessadores em vez de carregar ambos.
4. Reinicie os masters e region servers um de cada vez. Até o agente ter seu primeiro snapshot, as solicitações que não forem de superusuários são recusadas.
5. **Migre antes as permissões existentes:** as permissões que o `AccessController` do HBase armazenou em `hbase:acl` deixam de valer. Liste-as antes com `user_permission` e crie as políticas correspondentes.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: o servidor GrantForge, o arquivo do token do agente, um nome para este master ou region server e um diretório gravável para o snapshot assinado e os eventos em fila |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública Ed25519 X.509 codificada em Base64 da página de agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` relata cada um |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Máximo de decisões guardadas, padrão `100000`, até `10000000`, `0` não guarda nenhuma; e por quanto tempo, padrão `10000`, `1..3600000` milissegundos |

O HBase inicia um coprocessador para o master, para o region server e para cada região que abre; todos compartilham um mesmo agente, um cache de decisões e o agrupamento de eventos.

## Usuários, grupos e papéis

As políticas nomeiam o nome curto que o HBase dá ao usuário da conexão: o nome de usuário da autenticação simples, ou o nome curto de um principal Kerberos por `hadoop.security.auth_to_local`. Os grupos são os que o mapeamento de grupos do Hadoop dá ao usuário no servidor, junto com os grupos em que o GrantForge o coloca; os papéis são os que o GrantForge atribui ao usuário.

## Decisões e limitações

- Sem um snapshot utilizável, tudo é recusado, exceto as solicitações dos superusuários e as leituras do namespace `hbase`. Um serviço que não está em uso deixa o HBase sem verificação, como sem autorizador.
- Um token vinculado a um serviço cujo tipo não é `hbase` recusa tudo.
- Os comandos `grant`, `revoke` e `user_permission` do HBase falham com *AccessController is not loaded*: as permissões são políticas do GrantForge.
- Chamadas a endpoints de coprocessadores não são verificadas; carregue só endpoints confiáveis.
- Aqui o HBase não dá ao criador de uma tabela nenhuma permissão própria; uma política de namespace, como acima, dá aos donos suas tabelas.
- O HBase registra algumas recusas em vez de devolvê-las, como a do interruptor do balanceador: o interruptor fica como estava, mas o cliente não é avisado.
- As decisões são guardadas por `grantforge.decision.cache.ttl.ms`, então o período de validade de uma política começa ou termina com até esse atraso.
- As permissões no nível de célula e os rótulos de visibilidade do HBase não são cobertos. As colunas são comparadas como texto UTF-8.

## Guias relacionados

- [Apache HBase](/pt-br/plugins/hbase/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
