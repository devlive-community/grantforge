---
title: Agente do Apache Hadoop YARN
description: Instalar o provedor de autorização nos ResourceManagers do YARN para decidir com políticas do GrantForge quem pode enviar quais aplicações a qual fila, administrar filas e o cluster, e a partir de quais redes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do YARN é um provedor de autorização do ResourceManager: o YARN pergunta a ele quem pode enviar a uma fila, administrar uma fila e administrar o cluster, e ele responde a partir das políticas assinadas do serviço de dados `yarn`. A distribuição o fornece como um JAR compatível com Java 8, `agents/yarn/grantforge-agent-yarn-<version>.jar`, com as dependências embutidas e realocadas.

Os testes de integração com Testcontainers do repositório executam o JAR nos ResourceManagers do Hadoop `3.2.4`, `3.3.6`, `3.4.3` e `3.5.0`. Isso não certifica todas as outras versões. O plug-in de serviço está descrito em [Apache Hadoop YARN](/pt-br/plugins/yarn/).

## Como funciona

- **Cada verificação é decidida em cada fila do caminho.** Como no YARN, uma permissão sobre uma fila cobre as de baixo, então o agente decide um envio a `root.sales.etl` em `root`, `root.sales` e `root.sales.etl`: uma negação em qualquer uma recusa, uma permissão em qualquer uma concede.
- **Os envios também são decididos pelo nome da aplicação.** Em cada uma dessas filas, o agente também pergunta pela fila com o nome da aplicação, de modo que uma política pode aceitar só algumas aplicações, por exemplo `payroll-*`, em uma fila e nas de baixo.
- **As políticas podem valer só para algumas redes.** Cada verificação leva o endereço do cliente, atrás de um proxy o primeiro encaminhado, e o agente avalia a condição **Endereços de cliente** com as listas de hosts do próprio Hadoop.
- **A administração do cluster está sujeita às políticas.** As operações de administração do YARN, que de outra forma o `yarn.admin.acl` concede, exigem `admin` no cluster.
- **As recusas são registradas com o motivo.** O YARN redige a recusa que o cliente recebe; o log do ResourceManager nomeia a política do GrantForge que negou, ou que nenhuma permite.
- **As decisões são mantidas e os acessos agrupados.** As decisões do snapshot atual são mantidas, por padrão 100.000 por 10 segundos, e um novo snapshot começa do zero. Acessos permitidos idênticos dentro de uma janela, por padrão 10 segundos, são informados como um único evento de acesso que diz quantos foram; as recusas são informadas uma a uma e imediatamente. Cada evento nomeia a aplicação, a fila e o endereço do cliente.
- **O ResourceManager não é verificado.** O usuário do próprio ResourceManager, por exemplo `yarn`, não é verificado nem auditado.

| Operação | Acesso necessário |
| --- | --- |
| Enviar uma aplicação | `submit_app` na sua fila, ou na sua fila com o nome da aplicação; `admin_queue` na fila também serve |
| Encerrar, mover, alterar a prioridade ou ver todos os detalhes da aplicação de outro usuário | `admin_queue` na sua fila, e para mover também `submit_app` na nova; o dono da aplicação sempre pode, assim como quem as ACLs dela permitem |
| `yarn rmadmin`: recarregar filas, nós, grupos de usuários e ACLs, alterar rótulos e atributos de nó e as demais operações de administração | `admin` no cluster |

## Implantação

1. Adicione um serviço de dados `yarn` e configure suas políticas. Abra **Permissões de dados → Agentes**, escolha o serviço e emita um token de agente. Ele só é exibido uma vez; guarde-o em um arquivo que só a conta do YARN possa ler.
2. Copie o JAR para `share/hadoop/yarn/lib` **de cada ResourceManager**, por exemplo `/opt/hadoop/share/hadoop/yarn/lib`.
3. Acrescente as configurações ao `yarn-site.xml` de cada ResourceManager, substituindo o endereço, os caminhos, o usuário e o nome de instância do exemplo:

   ```xml
   <configuration>
     <property>
       <name>yarn.acl.enable</name>
       <value>true</value>
     </property>
     <property>
       <name>yarn.authorization-provider</name>
       <value>org.devlive.grantforge.yarn.agent.GrantForgeYarnAuthorizer</value>
     </property>
     <property>
       <name>yarn.admin.acl</name>
       <value>yarn</value>
     </property>
     <property>
       <name>grantforge.server.url</name>
       <value>https://grantforge.example.com</value>
     </property>
     <property>
       <name>grantforge.token.file</name>
       <value>/etc/hadoop/grantforge.token</value>
     </property>
     <property>
       <name>grantforge.instance</name>
       <value>resourcemanager-1</value>
     </property>
     <property>
       <name>grantforge.cache.dir</name>
       <value>/var/lib/hadoop-yarn/grantforge-cache</value>
     </property>
   ```

   `yarn.admin.acl` deve nomear apenas o usuário do próprio ResourceManager: o YARN deixa seus administradores fazerem qualquer coisa com qualquer aplicação antes de perguntar ao agente pela fila. O diretório de cache precisa ser gravável pela conta do YARN.
4. Reinicie os ResourceManagers um após o outro. Até o agente ter seu primeiro snapshot, as requisições que não sejam do ResourceManager são recusadas.
5. **Migre antes as permissões existentes:** as ACLs de fila da configuração do escalonador, por exemplo `acl_submit_applications` e `acl_administer_queue`, deixam de valer. Crie antes as políticas equivalentes.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: o servidor do GrantForge, o arquivo do token de agente, um nome para este ResourceManager e um diretório gravável para o snapshot assinado e os eventos em espera |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página Agentes. Sem ele, a chave é obtida do servidor no primeiro contato |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite do spool (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` informa cada um |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Máximo de decisões mantidas, padrão `100000`, até `10000000`, `0` não mantém nenhuma; e por quanto tempo, padrão `10000`, `1..3600000` milissegundos |

## Usuários, grupos e papéis

As políticas nomeiam o nome curto que o YARN dá a quem chama: o nome de usuário da autenticação simples, ou o nome curto de um principal Kerberos segundo `hadoop.security.auth_to_local`. Os grupos são os que o mapeamento de grupos do Hadoop dá ao usuário no ResourceManager, junto com os grupos em que o GrantForge coloca o usuário; os papéis são os que o GrantForge atribui a ele.

## Decisões e limitações

- Sem um snapshot utilizável, tudo é recusado, exceto as requisições do próprio ResourceManager. Um serviço não usado deixa o YARN sem verificação, como com `yarn.acl.enable` desligado.
- Um token vinculado a um serviço cujo tipo não é `yarn` recusa tudo.
- O YARN pergunta sobre sua administração sem o endereço do cliente, então uma condição Endereços de cliente nunca vale em `cluster`.
- O dono de uma aplicação e os usuários que as ACLs dela nomeiam a gerenciam independentemente das políticas, como no YARN.
- Verificações de outros tipos de acesso, por exemplo ACLs de prioridade de aplicação, são recusadas.
- Um nome de host em uma condição Endereços de cliente custa uma resolução de nome do cliente sempre que uma decisão é tomada em vez de mantida.
- As decisões são mantidas por `grantforge.decision.cache.ttl.ms`, então uma política começa ou deixa de valer até esse tempo depois.

## Guias relacionados

- [Apache Hadoop YARN](/pt-br/plugins/yarn/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
