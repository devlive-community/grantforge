---
title: Agente do Apache Pulsar
description: Instalar o provedor de autorização nos brokers do Pulsar para decidir com políticas do GrantForge quem pode produzir e consumir onde, por quais assinaturas, executar functions, sources e sinks, administrar tópicos, namespaces, tenants e clusters, e a partir de quais redes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do Pulsar é um provedor de autorização dos brokers: o Pulsar pergunta a ele quem pode produzir, consumir, executar functions, sources e sinks, e administrar tópicos, namespaces, tenants e clusters, e ele responde a partir das políticas assinadas do serviço de dados `pulsar`. A distribuição o fornece como um JAR Java 17, `agents/pulsar/grantforge-agent-pulsar-<version>.jar`, com as dependências embutidas e realocadas.

Os testes de integração com Testcontainers do repositório executam o JAR nos brokers do Pulsar `3.0.17`, `3.3.9`, `4.0.14` e `4.2.5`. Isso não certifica todas as outras versões. O plug-in de serviço está descrito em [Apache Pulsar](/pt-br/plugins/pulsar/).

## Como funciona

- **Cada verificação é decidida em cada nível do nome do tópico.** Como com as permissões do Pulsar, uma permissão sobre um tenant ou um namespace cobre o que há nele, então o agente decide uma produção em `persistent://sales/orders/payments` no tenant `sales`, no namespace `orders` e no tópico `payments`: uma negação em qualquer um recusa, uma permissão em qualquer um concede.
- **O consumo também é decidido pela assinatura.** Em cada um desses níveis o agente também pergunta pelo tópico com a assinatura, de modo que uma política pode deixar um papel consumir só por algumas assinaturas, por exemplo `billing-*`.
- **Os administradores do cluster são os superusuários do Pulsar.** `admin` no cluster do broker torna um papel um deles, e o Pulsar deixa seus superusuários fazerem tudo, então as políticas abaixo não os restringem. As operações de broker e de cluster do Pulsar exigem `admin` no cluster que nomeiam.
- **As políticas podem valer só para algumas redes.** Cada verificação leva o endereço do cliente, e o agente avalia a condição **Endereços de cliente** com as faixas de endereços do Netty, que a pilha de rede do broker usa.
- **Conceder permissões pelo Pulsar é recusado.** As permissões do próprio Pulsar e os papéis de administração de seus tenants não são usados, então `pulsar-admin namespaces grant-permission` e afins recebem HTTP 405, com a indicação de conceder no GrantForge.
- **As recusas são registradas com o motivo.** O Pulsar redige a recusa que o cliente recebe; o log do broker nomeia a política do GrantForge que negou, ou que nenhuma permite.
- **As decisões são mantidas e os acessos agrupados.** As decisões do snapshot atual são mantidas, por padrão 100.000 por 10 segundos, e um novo snapshot começa do zero. Acessos permitidos idênticos dentro de uma janela, por padrão 10 segundos, são informados como um único evento de acesso que diz quantos foram; as recusas são informadas uma a uma e imediatamente. Cada evento nomeia o tópico, o namespace, o tenant ou o cluster e o endereço do cliente.
- **Um agente atende o broker inteiro.** Um broker carrega seu provedor de autorização para o protocolo binário, o serviço web e o functions worker; eles compartilham um agente, que para junto com o broker.

| Operação | Acesso necessário |
| --- | --- |
| Produzir, ou criar um tópico produzindo nele | `produce` no tópico |
| Assinar, consumir, cancelar a assinatura, pular, espiar, expirar mensagens, redefinir um cursor | `consume` no tópico com a assinatura |
| Consultar um tópico, ler suas estatísticas, metadados e políticas; listar os tópicos de um namespace | `produce` ou `consume` |
| Encerrar, compactar, descarregar, liberar ou truncar um tópico; alterar suas políticas | `admin_topic` no tópico |
| Criar e excluir tópicos; alterar as políticas de um namespace | `admin_namespace` no namespace |
| Executar functions, sources, sinks; usar packages | `functions`, `sources`, `sinks`, `packages` no namespace |
| Criar, listar e excluir os namespaces de um tenant | `admin_tenant` no tenant |
| Criar, alterar e excluir tenants e clusters; as operações de broker do Pulsar | `admin` no cluster |

## Implantação

1. Adicione um serviço de dados `pulsar` e configure suas políticas. Abra **Permissões de dados → Agentes**, escolha o serviço e emita um token de agente. Ele só é exibido uma vez; guarde-o em um arquivo que só a conta do Pulsar possa ler.
2. Copie o JAR para `lib` **de cada broker**, por exemplo `/pulsar/lib`.
3. Acrescente as configurações ao `broker.conf` de cada broker, ou ao `standalone.conf`, substituindo o endereço, os caminhos e o nome de instância do exemplo:

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   A autenticação também precisa estar habilitada, para que os clientes tenham papéis. `superUserRoles` e `proxyRoles` devem nomear apenas o papel dos próprios brokers e os dos proxies: o agente não os verifica, e o Pulsar também verifica o cliente original de um proxy. Dê aos administradores `admin` no cluster em vez disso. O diretório de cache precisa ser gravável pela conta do Pulsar. Em um contêiner, escreva as configurações `grantforge.*` no arquivo: o shell de algumas imagens do Pulsar descarta variáveis de ambiente cujo nome tem pontos, como `PULSAR_PREFIX_grantforge.token.file`.
4. Reinicie os brokers um após o outro. Até o agente ter seu primeiro snapshot, as requisições que não sejam dos brokers e dos proxies são recusadas.
5. **Migre antes as permissões existentes:** as permissões do próprio Pulsar em namespaces, tópicos e assinaturas, e os papéis de administração dos tenants, deixam de valer. Crie antes as políticas equivalentes.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: o servidor do GrantForge, o arquivo do token de agente, um nome para este broker e um diretório gravável para o snapshot assinado e os eventos em espera |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página Agentes. Sem ele, a chave é obtida do servidor no primeiro contato |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite do spool (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` informa cada um |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Máximo de decisões mantidas, padrão `100000`, até `10000000`, `0` não mantém nenhuma; e por quanto tempo, padrão `10000`, `1..3600000` milissegundos |

## Usuários, grupos e papéis

As políticas nomeiam como usuário o papel que a autenticação do Pulsar dá ao cliente, por exemplo o sujeito de um token ou o nome comum de um certificado. O Pulsar não tem grupos próprios: os grupos são aqueles em que o GrantForge coloca o papel, e os papéis os que o GrantForge lhe atribui.

## Decisões e limitações

- Sem um snapshot utilizável, tudo é recusado, exceto as requisições dos brokers e dos proxies. Um serviço não usado deixa o Pulsar sem verificação, como com `authorizationEnabled` desligado.
- Um token vinculado a um serviço cujo tipo não é `pulsar` recusa tudo.
- Antes de muitas verificações, o Pulsar pergunta se um papel é superusuário ou administrador de um tenant; só as respostas que deixam uma requisição passar são informadas, e a verificação da própria requisição é informada de qualquer forma. Uma requisição que o Pulsar decide apenas por essa pergunta, como o Pulsar 3.0 faz com tenants e clusters, só é registrada em nível debug quando recusada.
- `admin` em um cluster torna um papel superusuário apenas dos brokers desse cluster.
- As listagens de permissões do Pulsar, como `pulsar-admin namespaces permissions`, falham: o GrantForge guarda as políticas.
- Endereços de cliente são apenas endereços e faixas; um nome de host não corresponde a nenhum cliente.
- As decisões são mantidas por `grantforge.decision.cache.ttl.ms`, então uma política começa ou deixa de valer até esse tempo depois.

## Guias relacionados

- [Apache Pulsar](/pt-br/plugins/pulsar/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
