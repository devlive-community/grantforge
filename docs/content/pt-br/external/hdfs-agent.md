---
title: Agente NameNode do Apache Hadoop HDFS
description: Escolha o agente NameNode correspondente à versão do Hadoop, aplique as políticas de caminho do GrantForge e envie a auditoria de acessos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in de servidor `grantforge-plugin-hdfs` fornece de forma unificada o tipo de serviço `hdfs`, os recursos e a configuração de conexão. Os agentes NameNode são construídos separadamente por versão do Hadoop e verificam os acessos por meio do `INodeAttributeProvider` e do `AccessControlEnforcer` da versão correspondente, compartilhando políticas assinadas, cache, avaliação de políticas e lógica de auditoria.

No código-fonte, o plug-in de servidor está em `plugins/grantforge-plugin-hdfs`, os agentes versionados estão em `agents/grantforge-agent-hdfs-<line>` e a lógica de produção comum, que não depende do Hadoop, está em `agents/grantforge-agent-hdfs-common`. Os adaptadores nativos compartilhados ficam no módulo Maven de produção `agents/grantforge-agent-hdfs-native`, com baseline Java 8 / Hadoop 2.7.7; cada módulo numerado o referencia como dependência binária do Maven, preservando os pontos de entrada e os callbacks específicos de cada versão, sem recompilar as fontes de produção compartilhadas. O protocolo, o cache de instantâneos e a infraestrutura de auditoria continuam em `core/grantforge-agent-core`.

O plug-in de servidor mantém um único tipo `hdfs` e a versão do cliente é independente da versão dos agentes. Para Hadoop 2.x, a conexão e a consulta de caminhos usam `webhdfs://namenode:50070` (com HTTPS ativado, o endereço `swebhdfs://` correspondente); o Hadoop 3.x aceita RPC `hdfs://` ou WebHDFS. A matriz de contêineres valida o WebHDFS em 2.x e o RPC e o WebHDFS em 3.x; o RPC em 2.x não é validado e não há troca automática do protocolo de conexão.

No Apache Hadoop 2.7.7, com as extensões de atributos de inode ativadas, a consulta direta do caminho raiz `/` por um usuário comum dispara um ponteiro nulo no código nativo, antes do callback do agente. Nesta versão, as operações de dados e o `lookup.path` do serviço devem usar diretórios de dados reais, por exemplo `/data`; os testes mantêm uma asserção independente da falha no caminho raiz.

## Escolha da versão

| Sufixo do módulo do agente | Apache Hadoop validado | JVM do contêiner | Interface de autorização | Callback de caminho de superusuário |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | por parâmetro | não |
| `2.10` | 2.10.2 | Java 8 | por parâmetro | não |
| `3.2` | 3.2.4 | Java 8 | por parâmetro | não |
| `3.3` | 3.3.6 | Java 8 | por contexto | não |
| `3.4` | 3.4.3 | Java 11 | por contexto, callbacks de superusuário e de negação | sim |
| `3.5` | 3.5.0 | Java 17 | por contexto, callbacks de superusuário e de negação | sim |

Por exemplo, para Hadoop 2.10.2, instale `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar`. Cada NameNode tem apenas um agente de versão instalado; na atualização, remova o jar antigo e mantenha o nome da classe do provider na configuração. Na inicialização, o agente confere as versões principal e secundária do Hadoop e os callbacks obrigatórios; uma versão incorreta impede a inicialização. Outros patches e ramificações de fornecedores da mesma versão principal/secundária ainda exigem validação independente.

Os códigos principais dos agentes `common` e de `2.7` a `3.4` usam bytecode Java 8; o `3.5` usa Java 17. A tabela lista as JVM efetivamente usadas nos testes de integração, o que não significa que o 3.4.3 esteja validado com Java 8. A imagem de testes oficial do 3.3.6 é fornecida apenas para amd64; em hospedeiros ARM, ela é executada explicitamente por emulação amd64.

## Relação com as permissões

O agente executa primeiro a verificação de permissões nativa do HDFS e depois as políticas do GrantForge: o usuário precisa atender aos requisitos das permissões nativas e das políticas ao mesmo tempo. As políticas de permissão do GrantForge não ignoram permissões POSIX, ACL, verificação de proprietário ou sticky bit; políticas de negação sempre negam. Os ajustes de permissões nativas continuam sendo mantidos pelas ferramentas de administração do Hadoop.

Por padrão `grantforge.hdfs.native.fallback=false`: sem instantâneo de políticas local, sem política correspondente ou com o agente ainda não iniciado, o acesso aos dados é negado. Com o valor `true`, acessos que nenhuma política decide usam as permissões nativas; políticas de negação explícita continuam valendo. Quando o servidor está temporariamente inacessível, o último instantâneo local que passou pela verificação de assinatura continua em uso.

As projeções de ancestrais, destino, subárvore e caminhos de instantâneo de um mesmo callback de autorização usam a mesma versão do instantâneo de políticas; políticas atualizadas passam a valer no callback seguinte, evitando combinar regras de permissão de versões diferentes. A auditoria de acessos registra a versão da política efetivamente usada.

O agente verifica as permissões `read`, `write` e `execute` de que um usuário comum precisa para acessar o destino, além dos diretórios pai, dos diretórios ancestrais e dos subdiretórios que exigem verificação recursiva. Operações como criar, excluir e renomear envolvem vários caminhos, e as políticas de permissão precisam cobri-los. No modo estrito, a política `read` apenas sobre o arquivo de destino não basta: é preciso configurar para o usuário a política `execute` sobre os diretórios ancestrais, por exemplo permitir `execute` em `/` e marcar recursivo, e então configurar permissões de leitura e escrita no diretório de dados real.

Os caminhos de instantâneo são verificados tanto no caminho realmente solicitado quanto no caminho original sem `.snapshot/<nome-do-instantâneo>`, por exemplo `/data/.snapshot/s1/secret` também verifica `/data/secret`. Por isso, a política de negação no caminho original também restringe o instantâneo; é possível ainda definir restrições mais estritas para o caminho de instantâneo explícito. As consultas de metadados seguem a semântica de permissões de travessia de diretórios do HDFS.

Uma autorização recursiva verifica no máximo `100000` inodes; ao ultrapassar o limite, a operação é negada, evitando alocação de memória sem limite dentro do NameNode. Caminhos muito longos são avaliados pela política usando o caminho completo; a exibição do recurso na auditoria é limitada a `1000` caracteres, e o comprimento original e o resumo SHA-256 são registrados no detalhamento da requisição.

No Hadoop 2.7, 2.10, 3.2 e 3.3, o superusuário é ignorado antes da chamada ao agente, que não pode controlar nem auditar esses acessos. No Hadoop 3.4 e 3.5, os callbacks de superusuário com caminho passam primeiro pela verificação nativa e depois verificam a política pelo nome da operação: leitura de arquivos e consultas de metadados exigem `read`, enumeração de diretórios exige `read` + `execute` e operações de modificação conhecidas exigem `write`. Operações desconhecidas, ausentes ou que não podem ser inferidas com precisão (por exemplo `checkAccess` e `concat`) exigem, de forma conservadora, as três permissões.

Os callbacks de superusuário não têm o contexto completo de inodes e subárvore; chamadas de administração do cluster sem caminho mantêm a verificação nativa; não é possível restringir todas as operações recursivas do superusuário com políticas de subdiretório. Consumidores de dados devem usar usuários Hadoop comuns.

## Métricas

O agente reporta métricas pelo sistema Metrics2 do Hadoop, usando os mesmos sinks das métricas dfs do próprio NameNode, e aparece no JMX do NameNode como `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (o exportador JMX do Prometheus pode capturá-lo diretamente). Cada métrica carrega os rótulos `instance` (nome da instância do serviço de dados) e `agentVersion`, o que permite consultar separadamente os dois NameNodes de um HA. A falha no registro das métricas custa apenas as métricas em si: o agente registra um aviso e continua executando autorizações sem métricas.

| Métrica | Descrição |
| --- | --- |
| `Callbacks` | Número de callbacks de autorização executados pelo agente |
| `SuperuserCallbacks` | Número de callbacks de superusuário executados pelo agente |
| `NativeDenies` | Número de acessos negados pelo Hadoop antes de chegar ao agente |
| `EvaluationFailures` | Número de callbacks que falham fechados por exceção na avaliação de políticas |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Número de permissões que a política decide permitir / negar / deixar sem decisão; sem decisão também é negado no modo estrito |
| `MissingSnapshots` | Número de callbacks atendidos sem um instantâneo de políticas verificado |
| `SnapshotVersion` | Versão do instantâneo de políticas em uso; 0 indica que não há |
| `QueuedEvents` / `DroppedEvents` | Número de eventos de auditoria pendentes de envio na memória / número de eventos descartados por fila ou buffer de disco cheio |
| `ServerReachable` | Se o último acesso ao servidor de políticas foi bem-sucedido (1/0) |

## Implantação

1. Adicione um serviço `hdfs` nos serviços de dados do GrantForge, salve a configuração e teste a conexão; configure políticas de caminho para os nomes de usuário curtos, grupos de usuários ou papéis reais do Hadoop.
2. Em "Permissões de dados → Agentes", emita um token para este serviço. Escreva o token em texto simples em um arquivo local de cada NameNode, por exemplo `/etc/hadoop/grantforge/token`, com leitura permitida ao usuário que executa o NameNode.
3. Escolha, pela tabela acima, o jar do agente correspondente em `agents/hdfs/<linha-de-versão-do-Hadoop>/` e coloque-o no classpath do NameNode, por exemplo `$HADOOP_HOME/share/hadoop/hdfs/lib/`. O jar do agente já inclui o próprio mecanismo de políticas, o Jackson e a biblioteca de assinatura; as classes do Hadoop são fornecidas pelo NameNode; a versão do agente no heartbeat inclui a versão do produto e a versão do Hadoop de compilação.
4. Configure as seguintes propriedades no `hdfs-site.xml` de cada NameNode; os dois NameNodes de um HA usam `instance` diferentes e seus próprios diretórios de cache locais.

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. Confirme que `dfs.permissions.enabled=true` e que `dfs.namenode.inode.attributes.provider.bypass.users` está vazio; na inicialização, o agente recusa configurações que permitam ignorar os callbacks de autorização. Reinicie o NameNode e verifique o heartbeat e a versão das políticas na página de agentes do GrantForge. O agente lê a configuração existente do NameNode; não modifica os atributos nativos dos inodes.

Em uma primeira implantação, é possível começar com `native.fallback=true`, confirmar que o instantâneo de políticas já foi sincronizado e completar as permissões dos diretórios ancestrais, antes de voltar ao modo estrito. O tipo de serviço vinculado ao token precisa ser `hdfs`; configuração incorreta ou vinculada a outro tipo de serviço nega o acesso.

## Configurações opcionais

| Propriedade | Valor padrão | Uso |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Tempo limite de conexão com o GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Tempo limite de leitura da resposta |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Intervalo de atualização de políticas quando o servidor está inacessível, no mínimo `1000`; o heartbeat normal usa o intervalo sugerido pelo servidor |
| `grantforge.hdfs.signing.key.file` | não definido | Arquivo opcional com a chave pública de assinatura, contendo a chave pública X.509 Base64 fornecida pelo console; depois de configurado, apenas assinaturas dessa chave pública são aceitas |
| `grantforge.hdfs.audit.batch.size` | `500` | Número máximo de eventos por envio, intervalo `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | Capacidade da fila de auditoria em memória, intervalo `1..1000000`, suficiente para acomodar pelo menos um lote; com a fila cheia, eventos novos são contados e descartados |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Intervalo de descarregamento da auditoria, inteiro positivo, máximo `2147483647`; reduzi-lo diminui a latência de envio |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Limite do buffer em disco quando o servidor está inacessível, inteiro não negativo; `0` desativa o buffer em disco |

Em picos de acesso, é possível aumentar a fila de auditoria para reduzir transbordamentos; encurtar o intervalo de descarregamento diminui a latência da auditoria, mas aumenta a frequência de envio. O limite do buffer em disco controla o uso de disco durante falhas prolongadas de rede; com o buffer desativado ou esgotado, eventos podem ser perdidos. O envio da auditoria ocorre em segundo plano e não aguarda a resposta do servidor de políticas.

Sem uma chave pública de assinatura configurada, o agente a obtém do servidor na primeira vez e a salva junto com o instantâneo. O agente usa o nome de usuário curto e os grupos passados pelo Hadoop; papéis e grupos adicionais vêm do instantâneo assinado; o mapeamento do nome curto do principal Kerberos é decidido pelo `hadoop.security.auth_to_local` do cluster.

## Compilar e verificar a partir do código-fonte

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

O artefato do agente está em `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`. Os testes unitários cobrem os callbacks de autorização do NameNode, a configuração, os metadados de versão e as decisões de política; os testes de WebHDFS e Kerberos iniciam serviços temporários locais. Antes de ir para a produção, ainda é preciso validar no cluster de destino leitura e escrita, criação, renomeação, exclusão recursiva, troca de HA e o comportamento do cache após perda de rede.

A verificação de integração roda na fase `verify` com Testcontainers (os testes unitários não iniciam cluster; o `verify` precisa de um Docker daemon disponível):

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# usa o mesmo ponto de entrada que o nightly
bash script/ci/hdfs_integration.sh
# verifica apenas a linha de versão indicada
bash script/ci/hdfs_integration.sh 2.10
```

Os testes usam imagens do Apache Hadoop de versão fixa; imagens antigas são construídas com uma imagem base Java 8 de digest fixo e um pacote de distribuição Apache verificado por SHA-512. O jar real do agente da versão correspondente é colocado no classpath do NameNode, e as versões reais do Hadoop e da JVM são verificadas por asserção. O Testcontainers cria uma rede isolada e gerencia o ciclo de vida do NameNode e do DataNode, validando leitura e escrita, criação, acréscimo, renomeação, exclusão, negações recursivas e de instantâneo, permissões nativas e auditoria, atualização de políticas, reinício do NameNode usando o cache assinado após desconectar o servidor de políticas, e o modo estrito com fallback para permissões nativas quando não há instantâneo.

É preciso um Docker daemon em execução e permissão para baixar as imagens de teste. Sem Docker disponível, os testes falham, sem serem silenciosamente ignorados. O cliente de sistema de arquivos é executado dentro do contêiner Hadoop, e o serviço HTTP de políticas usa o encaminhamento de portas do hospedeiro do Testcontainers; não é necessário um cluster Hadoop externo. Ao final dos testes, os contêineres e a rede de teste são limpos, e os logs ficam em `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`.

Os testes de HA iniciam dois NameNodes, um DataNode e um JournalNode, com nomes de instância e diretórios de cache independentes para os dois agentes. Eles usam um cliente HDFS lógico para alternar manualmente o nó ativo e validam leitura, escrita e políticas de negação após a troca; o JournalNode único serve apenas aos testes: não valida tolerância a falhas por maioria nem envolve failover automático com ZooKeeper.

O código-fonte dos testes fica no `src/test` dos módulos de produção reais. Os testes unitários das políticas comuns rodam no common, e os testes unitários nativos compartilhados rodam em `agents/grantforge-agent-hdfs-native/src/test`, sem serem compilados nos seis agentes versionados. Os testes de callbacks de cada versão permanecem nos módulos numerados correspondentes, e o código-fonte compartilhado dos testes de contêiner `agents/grantforge-agent-hdfs-common/src/test/shared` continua sendo compilado nos módulos numerados; não existe um projeto Maven de testes separado. O Testcontainers é apenas dependência de test scope. O nightly valida as seis versões do Hadoop em hospedeiros Java 17 e 21, e os contêineres Hadoop usam as JVM da tabela acima. A cobertura atual de contêineres abrange autenticação Simple e HA manual; Kerberos, TLS e patches de fornecedores ainda exigem validação no ambiente de destino.

Os pontos de entrada de extensão do Hadoop e a semântica de permissões estão em [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) e [HDFS Permissions Guide](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
