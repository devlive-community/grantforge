---
title: Agente do Elasticsearch
description: Executar o agente do Elasticsearch ao lado do cluster, para que as políticas do GrantForge virem os papéis próprios do Elasticsearch, dados a usuários nativos e associados a usuários de outros realms, com segurança em nível de campo e de documento onde a licença as tem, que padrões de índice continuem padrões para que novos índices fiquem acessíveis na hora, e que os acessos sejam informados a partir dos arquivos do log de auditoria.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do Elasticsearch é um serviço ao lado do cluster. Ele mantém o controle de acesso próprio do Elasticsearch em sintonia com as políticas assinadas de um serviço de dados `elasticsearch`: um papel por usuário que contém exatamente o que as políticas lhe dão, com segurança em nível de campo e de documento onde a licença do cluster as tem. Um usuário nativo tem o papel entre os seus próprios papéis; um usuário de outro realm, como LDAP, SAML ou OpenID Connect, o recebe por uma associação de papéis do seu nome. O Elasticsearch então verifica cada requisição por conta própria, e o agente nunca fica entre os clientes e o cluster. A distribuição o entrega como um JAR executável, `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`, que requer Java 17 ou mais recente.

Os testes de integração com Testcontainers do repositório executam o agente, e o JAR como distribuído, contra o Elasticsearch `8.19`, `9.3`, `9.4` e `9.5` com a segurança ativada. Isso não certifica todas as outras versões; o agente recusa clusters anteriores a `8.19`, clusters sem segurança e clusters do OpenSearch, que o [agente do OpenSearch](/pt-br/external/opensearch-agent/) mantém. Para o plug-in de serviço, veja [Elasticsearch](/pt-br/plugins/elasticsearch/).

## Como funciona

- **Um papel por usuário contém o que o GrantForge dá.** Para cada usuário a quem as políticas dão algo, o agente mantém um papel com o nome dele, `grantforge_alice` para `alice`. Seus metadados nomeiam o usuário e o marcam como do agente, e o agente não toma como seu nenhum papel sem essa marca, de modo que não altera nenhum outro papel.
- **Usuários nativos têm o papel; outros realms o recebem pelo nome.** O agente adiciona o papel aos papéis de um usuário nativo e mantém todos os outros papéis dele. Associações de papéis valem para todos os realms exceto o nativo e o de arquivos, então o agente também mantém uma associação de papéis de mesmo nome que dá o papel ao nome do usuário, para usuários que entram por LDAP, Active Directory, SAML, OpenID Connect, JWT, Kerberos ou PKI. O realm de arquivos não pode ser alterado pela API do Elasticsearch; seus usuários não recebem papel.
- **Padrões de índice continuam padrões.** Um padrão de índice de uma política, como `logs-*`, é concedido como padrão onde todas as políticas decidem igual em toda a sua extensão: toda política de qualquer tipo cujos índices se sobrepõem ao padrão o cobre por inteiro. O agente decide isso com exatidão, com autômatos finitos dos padrões. Índices criados depois, como os logs de amanhã, ficam então acessíveis na hora, também quando o primeiro documento escrito os cria. Onde uma política decide outra coisa dentro de um padrão, como uma negação de `logs-secret` dentro de `logs-*`, o agente concede cada índice e fluxo de dados do cluster pelo nome, e os novos na próxima sincronização.
- **Segurança em nível de campo só onde algo abaixo é decidido de outra forma.** Um índice que o usuário pode ler inteiro, sem política que possa tirar um campo, é lido sem segurança em nível de campo, de modo que campos adicionados depois são lidos na hora. Caso contrário, o usuário lê os campos que uma política permite, dentre os que seus documentos têm e os que as políticas nomeiam, cada um com seus multicampos, como `name.keyword` com `name`.
- **Filtros de linhas são segurança em nível de documento.** Um filtro de linhas vira a consulta da entrada. A API validate do Elasticsearch verifica cada consulta antes de concedê-la; uma consulta que o Elasticsearch não executaria não mostra nenhum documento ao usuário, e é registrada.
- **Nunca mais do que as políticas permitem abaixo do Platinum.** O Elasticsearch tem segurança em nível de documento e de campo a partir de uma licença Platinum, e abaixo dela desativa todo papel que as use. O agente lê a licença a cada sincronização. Abaixo do Platinum, uma leitura que precisa de qualquer uma delas, porque uma política restringe campos ou documentos do índice, não é concedida, e o agente registra qual usuário não pode ler qual índice e por quê; a leitura de índices inteiros e todo o resto são concedidos como de costume.
- **Uma entrada por conjunto de privilégios.** Índices e padrões com os mesmos privilégios compartilham uma entrada; as entradas nunca se sobrepõem, pois o Elasticsearch combina a segurança em nível de campo e de documento de entradas sobrepostas.
- **As políticas do momento presente.** Cada sincronização planeja pelas políticas como se aplicam naquele momento, de modo que os períodos de validade começam e terminam na hora.
- **Papéis de usuários nativos são grupos.** Uma política para um grupo vale para usuários nativos que têm um papel com esse nome, junto com os grupos em que o GrantForge coloca os usuários.
- **Mudanças feitas à mão são desfeitas.** O agente compara a cada sincronização seus papéis, suas associações de papéis e os papéis dos usuários nativos com o que o Elasticsearch guarda; o que difere é escrito de novo, e o papel de um usuário que outro tem é retirado. Os papéis são escritos antes de serem dados, em uma requisição, e retirados antes de serem excluídos. Quando nada mudou, o agente não envia nada; uma mudança que o Elasticsearch recusa é registrada e não interrompe as outras.
- **Papéis fora do GrantForge são informados.** O Elasticsearch concede o que qualquer papel de um usuário dá, então o agente não pode tirar o que outro papel dá. Ele avisa uma vez de cada papel assim, que um usuário nativo tem ou que uma associação de papéis do seu nome dá, que dá o que uma política nega, nomeando o papel, o usuário, o acesso, o índice e a política, e de usuários com o papel `superuser`, que nenhum papel limita.
- **Os acessos vêm dos arquivos do log de auditoria.** O Elasticsearch escreve seu log de auditoria somente em um arquivo em cada nó. O agente lê os arquivos que recebe, de onde a última leitura terminou, os acompanha quando o Elasticsearch os rotaciona e informa os eventos `access_granted` e `access_denied`. Cada um nomeia o usuário, o tipo de acesso, cada índice ou fluxo de dados alcançado, ou o cluster, e diz se o GrantForge decidiu ou se um papel fora do GrantForge permitiu.

| Ação | Evento de acesso |
| --- | --- |
| `indices:data/read/*`: busca, leitura, contagem | `read` em cada índice ou fluxo de dados |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/delete`, `data_stream/delete` | `delete_index` |
| `indices:admin/get`, `mappings/get`, `settings/get`, `resolve/*` | `view_index_metadata` |
| `indices:monitor/*`; outros `indices:admin/*` | `monitor`; `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` e `repository/*`, `slm/*` | `cluster_monitor`, `create_snapshot`, `manage_slm` no cluster |
| Modelos de índice e de componentes, pipelines de ingestão, `cluster:admin/ilm/*` | `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` no cluster |

## Implantação

1. Adicione um serviço de dados `elasticsearch` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é mostrado só uma vez; guarde-o em um arquivo que só a conta do agente possa ler.
2. Dê ao agente um usuário com o privilégio de cluster `manage_security`, que gerencia papéis, associações de papéis e usuários, e o direito de ler os mappings de cada índice e validar consultas, como um usuário com o papel `superuser`. Ele pode entrar com uma senha, uma chave de API que esse usuário criou ou um certificado de cliente que um realm PKI associa a ele.
3. Para informar os acessos, ative o log de auditoria (`xpack.security.audit.enabled: true`), que exige uma licença Gold ou superior, e deixe o agente ler os arquivos de auditoria dos nós, `<nome do cluster>_audit.json` no diretório de logs de cada nó: execute o agente em um nó ou reúna os arquivos em um diretório que ele possa ler. `xpack.security.audit.logfile.events.include: access_granted, access_denied` limita o log ao que o agente informa.
4. Escreva o arquivo de configurações, substituindo os endereços, caminhos e nomes de exemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   Esses caminhos ficam na máquina do agente, e o diretório de cache precisa permitir escrita. Os arquivos de senha e de chave são lidos como UTF-8 e aparados.
5. Inicie o agente, por exemplo como serviço do systemd:

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   Substitua `<version>` pela versão da distribuição. Configurações passadas como `java -Dgrantforge.…=… -jar …` sobrepõem o arquivo. O agente para de forma limpa com SIGTERM e envia ou enfileira os eventos pendentes; o Elasticsearch mantém o que recebeu.
6. **Levar os papéis existentes para políticas:** papéis que os usuários têm fora do GrantForge continuam valendo ao lado dos do agente. Crie as políticas correspondentes e depois retire esses papéis dos usuários; os avisos do agente nomeiam os papéis que dão o que uma política nega.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórios: o servidor do GrantForge, o arquivo do token de agente, um nome para este agente e um diretório com escrita para o snapshot assinado, os eventos enfileirados e as posições de leitura do log de auditoria |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página Agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.elasticsearch.hosts` | Obrigatório: os nós como URLs, tentados em sequência quando um não pode ser alcançado; sem esquema um nó é `https`, e a porta padrão é `9200` |
| `grantforge.elasticsearch.user`, `grantforge.elasticsearch.password.file` | O usuário do agente e o arquivo de sua senha |
| `grantforge.elasticsearch.api.key.file` | Em vez disso: o arquivo de uma chave de API codificada, como o Elasticsearch a devolve ao criá-la |
| `grantforge.elasticsearch.tls.cert`, `grantforge.elasticsearch.tls.key` | Em vez disso: certificado de cliente PEM e sua chave privada, juntos, para entrar com um certificado; `grantforge.elasticsearch.tls.key.passphrase.file` contém a frase secreta de uma chave criptografada |
| `grantforge.elasticsearch.ssl.verification.mode` | `full` (padrão) verifica os certificados e os nomes dos nós, `certificate` só os certificados, `none` nada |
| `grantforge.elasticsearch.tls.ca` | Certificados de CA PEM que verificam os nós, como o `http_ca.crt` do Elasticsearch; vazio confia no que o Java confia |
| `grantforge.elasticsearch.timeout.ms` | Quanto a conexão e uma requisição podem levar, padrão `30000`, intervalo `1..600000` milissegundos |
| `grantforge.elasticsearch.role.prefix` | O início dos nomes dos papéis do agente, padrão `grantforge_`; letras minúsculas, dígitos, hifens e sublinhados, terminando em sublinhado, no máximo 32 caracteres. O agente é dono dos papéis que marcou cujos nomes começam assim |
| `grantforge.elasticsearch.unmanaged.users` | Usuários que o agente deixa em paz, separados por vírgulas, como contas de serviço; ele sempre deixa em paz o próprio usuário, os usuários reservados do Elasticsearch e os usuários com o papel `superuser` |
| `grantforge.sync.interval.ms` | Com que frequência o agente coloca o Elasticsearch em sintonia, padrão `10000`, intervalo `1000..3600000` milissegundos |
| `grantforge.audit.log.files` | Os arquivos do log de auditoria a ler, separados por vírgulas; sem eles o agente não lê nenhum log de auditoria e não informa acessos |
| `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.lines` | Com que frequência os arquivos são lidos, padrão `5000` milissegundos, e o máximo de linhas que uma leitura pega de cada um, padrão `5000`, para que um acúmulo seja lido em várias rodadas |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` envia cada um |

## Usuários, grupos e papéis

As políticas nomeiam os usuários do Elasticsearch pelos seus nomes de usuário. O agente mantém papéis para os usuários nativos, para os usuários que as políticas nomeiam, que podem entrar por outro realm, e para os usuários para os quais já mantinha papéis; os usuários reservados do Elasticsearch, como `kibana_system`, são deixados em paz. Os grupos são os outros papéis de um usuário nativo junto com os grupos em que o GrantForge coloca o usuário; o agente não conhece os grupos que um diretório dá na entrada, então dê a esses usuários seus grupos no GrantForge. Os papéis são os papéis que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem snapshot o agente não muda nada. Um serviço que não está em uso, ou um token vinculado a um serviço cujo tipo não é `elasticsearch`, não concede nada: o agente retira seus papéis e os exclui com suas associações de papéis, e valem os papéis próprios do Elasticsearch.
- Enquanto o agente não roda, o Elasticsearch mantém o que recebeu por último; as mudanças de políticas esperam por ele. Uma mudança chega ao Elasticsearch dentro do intervalo de atualização e do de sincronização.
- Índices concedidos pelo nome, e não por meio de um padrão, recebem seus privilégios na próxima sincronização.
- Abaixo do Platinum, um usuário cuja leitura de um índice uma política restringe não lê nada dele; abaixo do Gold, o agente não informa acessos.
- Uma associação de papéis do nome de um usuário vale para todo realm com esse nome de usuário; as políticas nomeiam usuários, não realms.
- Aliases alcançam os índices por trás deles, por isso as políticas nomeiam índices e fluxos de dados, não aliases. Índices cujo nome começa com ponto só são alcançados por padrões de política que começam com ponto.
- O agente não gerencia os espaços nem os privilégios de aplicação do Kibana.

## Guias relacionados

- [Elasticsearch](/pt-br/plugins/elasticsearch/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
