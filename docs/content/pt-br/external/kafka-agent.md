---
title: Agente do Apache Kafka
description: Instale o agente nos brokers e controllers do Kafka para autorizar cada solicitação com as políticas do GrantForge, mostrar as políticas como ACLs somente leitura e agrupar acessos repetidos em um único evento de auditoria.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do Kafka é um autorizador da API `Authorizer` do Kafka, indicado em `authorizer.class.name`: brokers e controllers o consultam a cada solicitação, e ele responde a partir das políticas assinadas do serviço de dados `kafka`. A distribuição o inclui como um único JAR compatível com Java 8, `agents/kafka/grantforge-agent-kafka-<version>.jar`, com as dependências realocadas dentro.

Os testes de integração com Testcontainers do repositório executam o JAR no Kafka `3.7.2`, `3.9.2`, `4.1.2` e `4.3.1`, em nós KRaft que são broker e controller ao mesmo tempo. Isso não certifica todas as outras versões. Veja [Apache Kafka](/pt-br/plugins/kafka/) para o plug-in de serviço.

## Como funciona

- **Cada solicitação é verificada.** Para cada recurso que uma solicitação toca, o Kafka pergunta por uma operação de ACL; o agente responde a partir das políticas, com os tipos de recurso do Kafka como níveis de recurso e suas operações como tipos de acesso. Uma recusa chega ao cliente como o erro do próprio Kafka, como `TOPIC_AUTHORIZATION_FAILED`.
- **Produtores idempotentes precisam só de um tópico.** O Kafka deixa um produtor iniciar sem `idempotent_write` no cluster quando ele pode escrever em algum tópico, e pergunta isso ao autorizador. O agente responde a partir das próprias políticas: um `write` permitido em qualquer tópico conta, e uma negação conta apenas em uma política que cobre todos os tópicos.
- **As políticas aparecem como ACLs.** `kafka-acls --list` e `describeAcls` da API Admin mostram as políticas que se aplicam agora: um valor sem curingas como ACL literal, um valor como `orders*` como ACL com prefixo e `*` como o curinga do Kafka; usuários como principais `User:`, grupos como `Group:`, papéis como `Role:` e o grupo `public` como `User:*`. O que as ACLs não conseguem expressar fica de fora: outros curingas, valores excluídos, itens com condições e os itens de uma política que tem exceções a eles. Criar ou excluir ACLs é recusado: *the ACLs of this cluster are GrantForge policies; change them in GrantForge*.
- **As decisões são guardadas.** Um broker faz as mesmas perguntas a cada produção e leitura; o agente guarda as decisões do snapshot atual, por padrão 100.000 delas por 10 segundos. Um snapshot novo começa do zero, então uma mudança de política vale assim que o agente a recebe.
- **Acessos repetidos são agrupados.** Acessos permitidos idênticos dentro de uma janela, por padrão 10 segundos, são relatados como um único evento de acesso que informa quantos foram e quando ocorreu o último; o console mostra *N acessos idênticos*. Acessos são idênticos quando compartilham usuário, endereço do cliente, recurso, tipo de acesso, tipo de solicitação e política decisiva. Recusas são relatadas uma a uma, imediatamente. O Kafka escolhe o que vale relatar: ele não pede, por exemplo, para relatar os tópicos que omite de uma listagem.
- **Superusuários não são verificados.** Os principais de `super.users` podem fazer tudo, como com o autorizador do próprio Kafka, e não são auditados.

## Implantação

1. Adicione um serviço de dados `kafka` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é exibido só uma vez; guarde-o em um arquivo que apenas a conta do Kafka possa ler.
2. Copie o JAR para `libs` de **cada broker e controller**, como `/opt/kafka/libs`.
3. Adicione as configurações ao `server.properties` de cada um, substituindo os principais, o endereço, os caminhos e o nome da instância de exemplo:

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` precisa nomear os principais que brokers e controllers usam entre si, como o do listener entre brokers, ou políticas precisam permitir a eles o que fazem, como `cluster_action` no cluster. Conexões a um listener `PLAINTEXT` são `User:ANONYMOUS`; nomeie-o apenas quando nenhum cliente puder alcançar esse listener. O diretório de cache precisa ser gravável pela conta do Kafka.
4. Reinicie os brokers e controllers um de cada vez. Os listeners que não são dos controllers abrem quando o agente tem seu primeiro snapshot, no máximo após `grantforge.start.timeout.ms`; até lá, e sempre que não houver snapshot, as solicitações são recusadas.
5. **Migre antes as ACLs existentes:** as ACLs que o autorizador do próprio Kafka armazenou deixam de valer quando o agente o substitui. Liste-as antes com `kafka-acls --list` e crie as políticas correspondentes.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: o servidor GrantForge, o arquivo do token do agente, um nome para este broker ou controller e um diretório gravável para o snapshot assinado e os eventos em fila |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública Ed25519 X.509 codificada em Base64 da página de agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` relata cada um |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Máximo de decisões guardadas, padrão `100000`, até `10000000`, `0` não guarda nenhuma; e por quanto tempo, padrão `10000`, `1..3600000` milissegundos |
| `grantforge.start.timeout.ms` | Quanto os listeners esperam pelo primeiro snapshot, padrão `30000`, `0..600000` milissegundos |

O agente lê suas configurações da configuração que o Kafka entrega ao seu autorizador. Um nó que é broker e controller ao mesmo tempo cria um autorizador por papel; ambos compartilham um mesmo agente.

## Usuários, grupos e papéis

As políticas nomeiam o principal que o Kafka dá à conexão: o nome de usuário SASL, o nome curto de um principal Kerberos por `sasl.kerberos.principal.to.local.rules`, ou o nome de um certificado TLS por `ssl.principal.mapping.rules`. O Kafka não tem grupos nem papéis; os das políticas são os que o GrantForge atribui ao usuário.

## Decisões e limitações

- Sem um snapshot utilizável, tudo é recusado, exceto as solicitações dos superusuários. Um serviço que não está em uso deixa o Kafka sem verificação, como sem autorizador.
- Um token vinculado a um serviço cujo tipo não é `kafka` recusa tudo.
- Negar um tipo de acesso nega também o que ele implica: negar `write` em um tópico nega `describe`, então o usuário não o vê. As ACLs do próprio Kafka diferem nisso.
- A pergunta do Kafka sobre se um usuário pode agir sobre algum recurso de um tipo não é um acesso e não é relatada.
- As decisões são guardadas por `grantforge.decision.cache.ttl.ms`, então o período de validade de uma política começa ou termina com até esse atraso.
- O agente funciona em brokers e controllers. A API REST do Kafka Connect, registros de esquemas e outros serviços em torno do Kafka não são cobertos.

## Guias relacionados

- [Apache Kafka](/pt-br/plugins/kafka/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
