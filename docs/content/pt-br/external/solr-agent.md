---
title: Agente do Apache Solr
description: Instalar o plug-in de autorização nos nós do Solr para decidir com políticas do GrantForge quem pode consultar e atualizar quais coleções, por quais caminhos de requisição, administrar quais coleções e o cluster, e a partir de quais redes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do Solr é um plug-in de autorização dos nós: o Solr o consulta sobre cada requisição, e ele responde a partir das políticas assinadas do serviço de dados `solr`. A distribuição o entrega como um JAR Java 11, `agents/solr/grantforge-agent-solr-<version>.jar`, com suas dependências embutidas e realocadas.

Os testes de integração com Testcontainers do repositório executam o JAR dentro de nós SolrCloud do Solr `9.8.1`, `9.10.1`, `9.11.0` e `10.0.0`. Isso não certifica todas as demais versões. O plug-in de serviço é descrito em [Apache Solr](/pt-br/plugins/solr/).

## Como funciona

- **As requisições são classificadas como o Solr as classifica.** Cada handler de requisição nomeia a permissão de que precisa, como `read` ou `collection-admin-edit`, como faz para o plug-in baseado em regras do próprio Solr; o agente transforma esse nome em um tipo de acesso, e um handler que não nomeia nenhuma é decidido pelo tipo da requisição.
- **Cada requisição a uma coleção também é decidida em seu caminho de requisição.** O agente decide uma consulta de `sales` por `/select` na coleção `sales` e em `sales` com o caminho `/select`: uma negação em qualquer um dos dois a recusa, de modo que uma política pode negar um handler de uma coleção.
- **As equipes administram suas próprias coleções.** Uma requisição da API Collections que nomeia coleções precisa de `admin_collection` em cada uma, de modo que uma política pode deixar uma equipe criar, recarregar ou excluir apenas as coleções cujo nome começa com seu prefixo.
- **Os administradores do cluster podem fazer tudo.** `admin` no cluster concede qualquer requisição, como a permissão `all` do próprio Solr.
- **As políticas podem valer apenas para algumas redes.** Cada verificação leva o endereço do cliente; atrás de proxies reversos em que o agente confia, o cliente é o último endereço de `X-Forwarded-For` que nenhum proxy confiável adicionou.
- **Os papéis verificados de um token web JSON contam como grupos.** Com a autenticação JWT do Solr, os papéis que a claim do token dá ao usuário são grupos da decisão, ao lado daqueles em que o GrantForge coloca o usuário.
- **As recusas são registradas com seu motivo.** O Solr responde a uma requisição recusada com seu próprio 403; o log do nó nomeia a política do GrantForge que a negou, ou informa que nenhuma a permite.
- **As decisões são mantidas e os acessos agrupados.** As decisões do snapshot atual são mantidas, por padrão 100.000 por 10 segundos, e um novo snapshot recomeça do zero. Acessos permitidos idênticos dentro de uma janela, por padrão 10 segundos, são relatados como um único evento de acesso que informa quantos foram; recusas são relatadas uma a uma, imediatamente. Cada evento nomeia a coleção e o caminho, ou o cluster, e o endereço do cliente.
- **Os nós não são verificados.** As requisições entre os nós, que o Solr assina como o próprio nó, passam; uma requisição sem usuário é solicitada a entrar.

| Requisição | Acesso necessário |
| --- | --- |
| Pesquisar, obter, exportar, consultar por qualquer handler de leitura | `query` na coleção e no caminho |
| Adicionar, atualizar ou excluir documentos | `update` na coleção e no caminho |
| Ler ou alterar o esquema de uma coleção | `schema_read` ou `schema_edit` |
| Ler ou alterar a config de uma coleção | `config_read` ou `config_edit` |
| Requisições da API Collections que nomeiam coleções: criar, recarregar, fazer backup, restaurar, excluir, modificar, seu status no cluster | `admin_collection` em cada coleção nomeada |
| Verificações de saúde; métricas | `health`; `metrics` no cluster |
| Listar coleções, status de cores, leituras do ZooKeeper, do file store e de pacotes | `admin_read` no cluster |
| Ler ou alterar a autenticação e a autorização | `security` no cluster |
| Administração de cores, alterações no file store e em pacotes, e o que nenhum handler nomeia | `admin` no cluster |

## Implantação

1. Adicione um serviço de dados `solr` e configure suas políticas, incluindo `admin` no cluster para os administradores do Solr. Abra **Permissões de dados → Agentes**, escolha o serviço e emita um token de agente. Ele só é exibido uma vez; guarde-o em um arquivo que só a conta do Solr possa ler.
2. Copie o JAR para `server/solr-webapp/webapp/WEB-INF/lib` de **cada nó**, como `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`, e reinicie os nós um de cada vez.
3. Defina a seção de autorização do `security.json` do cluster, ao lado de um plug-in de autenticação, substituindo o endereço, os caminhos e o nome da instância do exemplo:

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   Envie-o com `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>`; os nós o adotam sem reinício. A autenticação precisa estar habilitada, para que as requisições tenham usuários.
4. **No Solr 9, atenção ao gerenciador de segurança do Java**, que o Solr habilita por padrão: mantenha o token, a chave fixada e o diretório de cache sob `SOLR_HOME`, como `/var/solr/data`, e conceda ao agente o que ele precisa além da política do próprio Solr, em `server/etc/security.policy` ou em um arquivo de política próprio que `-Djava.security.properties` adiciona como `policy.url.3`:

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   O Solr 10 roda sem o gerenciador de segurança.
5. **Migre primeiro as permissões existentes:** as permissões e os papéis de usuário do plug-in baseado em regras do Solr deixam de valer. Crie antes as políticas correspondentes. Até o agente ter seu primeiro snapshot, as requisições que não sejam entre os nós são recusadas.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: o servidor GrantForge, o arquivo do token de agente, um nome para os nós do cluster e um diretório gravável para o snapshot assinado e os eventos em fila |
| `grantforge.trusted.proxies` | Proxies reversos cujo `X-Forwarded-For` o agente aceita, endereços ou faixas separados por vírgulas; nenhum por padrão |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página Agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite do buffer em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` relata cada um |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | O máximo de decisões mantidas, padrão `100000`, até `10000000`, `0` não mantém nenhuma; e por quanto tempo, padrão `10000`, `1..3600000` milissegundos |

## Usuários, grupos e papéis

As políticas nomeiam o usuário com que a autenticação do Solr entra, como um usuário da autenticação basic ou o principal de um token web JSON. Os grupos são os papéis que um token web JSON verificou para o usuário, junto com os grupos em que o GrantForge coloca o usuário; os papéis são os que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem um snapshot utilizável, tudo é recusado, exceto as requisições entre os nós. Um serviço que não está em uso deixa o Solr sem verificação, como sem um plug-in de autorização.
- Um token vinculado a um serviço cujo tipo não é `solr` recusa tudo.
- Uma requisição da API Collections que nomeia suas coleções apenas em um corpo JSON, como algumas requisições da API v2, não nomeia nenhuma que o agente possa ver, e precisa de `admin` no cluster.
- O Solr resolve os aliases em suas coleções antes de perguntar, então as políticas nomeiam coleções, não aliases.
- A API `/admin/authorization` do Solr não pode editar as regras do agente: o GrantForge guarda as políticas.
- Endereços de cliente são apenas endereços e faixas; um nome de host não corresponde a nada.
- As decisões são mantidas por `grantforge.decision.cache.ttl.ms`, então uma política começa ou deixa de valer até esse tempo depois.

## Guias relacionados

- [Apache Solr](/pt-br/plugins/solr/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
