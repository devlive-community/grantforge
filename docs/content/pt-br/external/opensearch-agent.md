---
title: Agente do OpenSearch
description: Executar o agente do OpenSearch ao lado do cluster, para que as políticas do GrantForge se tornem os próprios papéis do plug-in de segurança, com segurança em nível de campo, campos mascarados e segurança em nível de documento, os padrões de índice continuem padrões para que índices novos fiquem acessíveis na hora, e os acessos sejam informados pelo log de auditoria.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do OpenSearch é um serviço ao lado do cluster. Ele mantém o controle de acesso próprio do plug-in de segurança em sintonia com as políticas assinadas de um serviço de dados `opensearch`: um papel por usuário que contém exatamente o que as políticas lhe dão, associado somente a esse usuário, com segurança em nível de campo, campos mascarados e segurança em nível de documento. O OpenSearch então verifica cada requisição por conta própria, e o agente nunca fica entre os clientes e o cluster. A distribuição o fornece como JAR executável, `agents/opensearch/grantforge-agent-opensearch-<version>.jar`, que requer Java 17 ou mais recente.

Os testes de integração com Testcontainers do repositório executam o agente, e o JAR como distribuído, contra o OpenSearch `2.19`, `3.3`, `3.6` e `3.9` com o plug-in de segurança. Isso não certifica todas as outras versões; o agente recusa clusters anteriores a `2.19` e clusters sem plug-in de segurança. Para o plug-in de serviço, veja [OpenSearch](/pt-br/plugins/opensearch/).

## Como funciona

- **Um papel por usuário contém o que o GrantForge dá.** Para cada usuário a quem as políticas dão algo, o agente mantém um papel com o nome dele, `grantforge_alice` para `alice`, associado somente a esse usuário. Sua descrição o marca como do agente, e o agente não toma nenhum papel sem essa marca por um dos seus, de modo que não altera nenhum outro papel nem associação.
- **Padrões de índice continuam padrões.** Um padrão de índice de uma política, como `logs-*`, é concedido como padrão onde todas as políticas decidem igual em toda a sua extensão: toda política de qualquer tipo cujos índices se sobrepõem ao padrão o cobre por inteiro. O agente decide isso com exatidão, com autômatos finitos dos padrões. Índices criados depois, como os logs de amanhã, ficam então acessíveis na hora, também quando o primeiro documento escrito os cria. Onde uma política decide diferente dentro de um padrão, como uma negação de `logs-secret` dentro de `logs-*`, o agente concede pelo nome cada índice e fluxo de dados que o cluster tem, e os novos na próxima sincronização.
- **Segurança em nível de campo só onde algo abaixo é decidido de outra forma.** Um índice que o usuário pode ler inteiro, sem política que possa tirar um campo, é lido sem segurança em nível de campo, de modo que campos adicionados depois são lidos na hora. Caso contrário, o usuário lê os campos que uma política permite, entre os que seus documentos têm e os que as políticas nomeiam; multicampos como `name.keyword` acompanham seu campo.
- **Máscaras e filtros de linhas são os do plug-in de segurança.** Uma máscara vira um campo mascarado da entrada, e um filtro de linhas sua consulta de segurança em nível de documento. A API validate do OpenSearch verifica cada consulta antes de ela ser concedida; uma consulta que o OpenSearch não executaria não mostra nenhum documento ao usuário, e é registrada. Uma máscara que o plug-in de segurança não conhece esconde o campo em vez disso.
- **Uma entrada por conjunto de permissões.** Índices e padrões com as mesmas permissões compartilham uma entrada; as entradas nunca se sobrepõem, pois o plug-in de segurança combina a segurança em nível de campo e de documento de entradas sobrepostas.
- **As operações compostas do cluster vêm com a leitura e a escrita.** A leitura traz `cluster_composite_ops_ro`, para multi-search, multi-get e rolagem, e a escrita `cluster_composite_ops`, para bulk.
- **As mudanças são um patch.** O agente escreve seus papéis e associações como patches JSON, que o plug-in de segurança aplica de uma vez em todo o cluster: papéis antes das associações que os nomeiam, associações antes dos papéis que nomeiam e que são removidos. Um patch que o plug-in de segurança recusa é aplicado mudança por mudança, de modo que um papel com problema falha sozinho.
- **As políticas do momento presente.** Cada sincronização planeja pelas políticas como se aplicam naquele momento, de modo que os períodos de validade começam e terminam na hora.
- **Papéis de backend são grupos.** Uma política para um grupo vale para usuários internos que têm um papel de backend com esse nome, junto com os grupos em que o GrantForge coloca os usuários.
- **Mudanças feitas à mão são desfeitas.** O agente compara a cada sincronização seus papéis e associações com o que o plug-in de segurança guarda; o que difere é escrito de novo. Quando nada mudou, o agente não envia nada.
- **Papéis fora do GrantForge são informados.** O OpenSearch concede o que qualquer papel de um usuário dá, então o agente não pode tirar o que outro papel associado ao usuário dá. Ele avisa uma vez de cada papel assim que dá o que uma política nega, nomeando o papel, o usuário, o acesso, o índice e a política, e dos usuários associados a `all_access`, que nenhum papel limita.
- **Os acessos vêm do log de auditoria.** O agente lê o log de auditoria do plug-in de segurança nos índices em que ele o escreve, `security-auditlog-*`: o que recusou, como `MISSING_PRIVILEGES`, e o que permitiu, como `GRANTED_PRIVILEGES` quando a configuração de auditoria os registra. Cada evento nomeia o usuário, o tipo de acesso, cada índice ou fluxo de dados alcançado, ou o cluster, e diz se o GrantForge decidiu ou se um papel fora do GrantForge permitiu.

| Ação | Evento de acesso |
| --- | --- |
| `indices:data/read/*`: busca, leitura, contagem | `read` em cada índice ou fluxo de dados |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/aliases*`, `indices:monitor/*` | `manage_aliases`, `monitor` |
| Outros `indices:admin/*` | `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` e `repository/*` | `cluster_monitor`, `manage_snapshots` no cluster |
| Modelos de índice e de componentes, pipelines de ingestão | `manage_templates`, `manage_pipelines` no cluster |

## Implantação

1. Adicione um serviço de dados `opensearch` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é mostrado só uma vez; guarde-o em um arquivo que só a conta do agente possa ler.
2. Dê ao agente um usuário que possa alterar a configuração do plug-in de segurança: um usuário associado a `all_access` enquanto `plugins.security.restapi.roles_enabled` o listar, ou o certificado de administração nomeado por `plugins.security.authcz.admin_dn`. Os testes executam o agente como o `admin` da configuração de demonstração, e com seu certificado de administração.
3. Para informar os acessos, mantenha o log de auditoria no cluster (`plugins.security.audit.type: internal_opensearch`). Para o que o OpenSearch permitiu, tire também `GRANTED_PRIVILEGES` das categorias desativadas da configuração de auditoria; as negações são registradas por padrão.
4. Escreva o arquivo de configurações, substituindo os endereços, caminhos e nomes de exemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   Esses caminhos ficam na máquina do agente, e o diretório de cache precisa permitir escrita. Os arquivos de senha são lidos como UTF-8 e aparados.
5. Inicie o agente, por exemplo como serviço do systemd:

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   Substitua `<version>` pela versão da distribuição. Configurações passadas como `java -Dgrantforge.…=… -jar …` sobrepõem o arquivo. O agente para de forma limpa com SIGTERM e envia ou enfileira os eventos pendentes; o OpenSearch mantém o que recebeu.
6. **Levar os papéis existentes para políticas:** papéis associados a usuários fora do GrantForge continuam valendo ao lado dos do agente. Crie as políticas correspondentes e depois remova essas associações; os avisos do agente nomeiam os papéis que dão o que uma política nega.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórios: o servidor do GrantForge, o arquivo do token de agente, um nome para este agente e um diretório com escrita para o snapshot assinado, os eventos enfileirados e a posição de leitura do log de auditoria |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página Agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.opensearch.hosts` | Obrigatório: os nós como URLs, tentados em sequência quando um não pode ser alcançado; sem esquema um nó é `https`, e a porta padrão é `9200` |
| `grantforge.opensearch.user`, `grantforge.opensearch.password.file` | O usuário do agente e o arquivo de sua senha |
| `grantforge.opensearch.tls.cert`, `grantforge.opensearch.tls.key` | Em vez disso: certificado de cliente PEM e sua chave privada, juntos, para entrar com um certificado; `grantforge.opensearch.tls.key.passphrase.file` contém a frase secreta de uma chave criptografada |
| `grantforge.opensearch.ssl.verification.mode` | `full` (padrão) verifica os certificados e os nomes dos nós, `certificate` só os certificados, `none` nada |
| `grantforge.opensearch.tls.ca` | Certificados de CA PEM que verificam os nós; vazio confia no que o Java confia |
| `grantforge.opensearch.timeout.ms` | Quanto a conexão e uma requisição podem levar, padrão `30000`, intervalo `1..600000` milissegundos |
| `grantforge.opensearch.role.prefix` | O início dos nomes dos papéis do agente, padrão `grantforge_`; letras minúsculas, dígitos, hifens e sublinhados, terminando em sublinhado, no máximo 32 caracteres. O agente é dono dos papéis que marcou cujos nomes começam por ele |
| `grantforge.opensearch.unmanaged.users` | Usuários que o agente deixa em paz, separados por vírgulas, como contas de serviço; ele sempre deixa em paz o próprio usuário e os usuários associados a `all_access` |
| `grantforge.sync.interval.ms` | Com que frequência o agente coloca o OpenSearch em sintonia, padrão `10000`, intervalo `1000..3600000` milissegundos |
| `grantforge.audit.log` | Padrão `true`; `false` não lê nenhum log de auditoria e não informa acessos |
| `grantforge.audit.log.index`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.documents` | Os índices do log de auditoria, padrão `security-auditlog-*`; com que frequência ele é lido, padrão `5000` milissegundos; e o máximo de documentos por leitura, padrão `1000`, de modo que um acúmulo seja lido em várias rodadas |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` envia cada um |

## Usuários, grupos e papéis

As políticas nomeiam os usuários do OpenSearch como o plug-in de segurança os conhece. O agente mantém papéis para os usuários internos, para os usuários que as políticas nomeiam, que podem entrar por um diretório como LDAP ou SAML, e para os usuários para os quais já mantinha papéis; os usuários reservados e ocultos do plug-in de segurança, como `kibanaserver`, são deixados em paz. Os grupos são os papéis de backend de um usuário interno junto com os grupos em que o GrantForge coloca o usuário; o agente não conhece os papéis de backend que um diretório dá na entrada, então dê a esses usuários seus grupos no GrantForge. Os papéis são os que o GrantForge dá ao usuário.

## Decisões e limitações

- Sem snapshot o agente não muda nada. Um serviço que não está em uso, ou um token vinculado a um serviço cujo tipo não é `opensearch`, não concede nada: o agente remove seus papéis e associações, e valem os papéis próprios do plug-in de segurança.
- Enquanto o agente não roda, o OpenSearch mantém o que recebeu por último; as mudanças de políticas esperam por ele. Uma mudança chega ao OpenSearch dentro do intervalo de atualização e do de sincronização.
- Índices concedidos pelo nome, e não por meio de um padrão, recebem suas permissões na próxima sincronização.
- Aliases alcançam os índices por trás deles: buscar em um alias exige permissões em cada índice que ele contém, por isso as políticas nomeiam índices e fluxos de dados, não aliases.
- Um filtro de linhas só pode testar campos que o usuário pode ler, e uma máscara se aplica a valores de campos text e keyword.
- Índices cujo nome começa com ponto só são alcançados por padrões de política que começam com ponto.
- O agente não gerencia os tenants do plug-in de segurança para o OpenSearch Dashboards.

## Guias relacionados

- [OpenSearch](/pt-br/plugins/opensearch/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
