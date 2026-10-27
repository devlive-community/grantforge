---
title: Elasticsearch
description: Instalar e configurar o plug-in de serviço do Elasticsearch, alcançar um ou vários nós, entrar com uma senha, uma chave de API ou um certificado de cliente TLS, consultar índices, fluxos de dados, campos e o cluster, e gerenciar quem pode ler, escrever e administrar o quê com os privilégios próprios do Elasticsearch, até campos e documentos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do Elasticsearch consulta índices, fluxos de dados, campos e o cluster, e gerencia políticas de acesso nos termos do Elasticsearch: ler campos, indexar e excluir documentos, criar, excluir e administrar índices, e as operações do próprio cluster, com filtros de linhas de índices. O ID do plug-in e o tipo de serviço são ambos `elasticsearch`. O plug-in de serviço roda no servidor do GrantForge; o [agente do Elasticsearch](/pt-br/external/elasticsearch-agent/), um serviço ao lado do cluster, aplica as políticas com os papéis próprios do Elasticsearch, a segurança em nível de campo e a segurança em nível de documento.

Os testes de integração com Testcontainers do repositório cobrem o Elasticsearch `8.19`, `9.3`, `9.4` e `9.5` com a segurança ativada; isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/elasticsearch`. Confirme que `elasticsearch` está ativado em **Gestão da plataforma → Plug-ins**; reexamine o diretório de plug-ins depois de atualizá-lo. O plug-in se comunica com os nós pelo cliente REST de baixo nível do Elasticsearch, em um carregador de classes próprio.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione Elasticsearch (`elasticsearch`).
2. Informe um nó como URL, ou vários nós.
3. Escolha como o usuário de consulta entra, informe suas credenciais, teste a conexão e salve o serviço. O teste entra e descreve o cluster: sua versão do Elasticsearch e seu nome, a licença e se ela tem segurança em nível de documento e de campo, o usuário com que entrou e quantos índices e fluxos de dados ele tem. Um cluster sem segurança não passa no teste, já que o agente aplica as políticas com os papéis do Elasticsearch.

O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos usuários que leem e escrevem. Um teste de conexão bem-sucedido não significa que o acesso já está sujeito a políticas.

| Configuração | Finalidade |
| --- | --- |
| `elasticsearch.hosts` | Obrigatório: um nó como URL, por exemplo `https://es1.example.com:9200`; vários separados por vírgulas, tentados em sequência quando um não pode ser alcançado. Sem esquema um nó é `https`, e a porta padrão é `9200`; os nós de um serviço são todos `http` ou todos `https` |
| `authentication` | `password` (padrão), `api_key` ou `certificate`: um certificado de cliente TLS que um realm PKI associa a um usuário |
| `username`, `password` | password: o usuário de consulta e sua senha, guardada criptografada |
| `apiKey` | api_key: a chave de API codificada, como o Elasticsearch a devolve ao criá-la, guardada criptografada |
| `ssl.verificationMode` | Como o Kibana chama: `full` (padrão) verifica os certificados e nomes dos nós, `certificate` só os certificados, `none` nada |
| `ssl.certificateAuthorities` | Caminho no **servidor do GrantForge** dos certificados de CA PEM com que os nós são verificados, como o `http_ca.crt` do Elasticsearch; vazio usa os da JVM |
| `ssl.certificate`, `ssl.key` | certificate: caminhos no servidor do GrantForge do certificado de cliente PEM e de sua chave privada, PKCS #8 ou o formato OpenSSL antigo; exigem nós `https` |
| `ssl.keyPassphrase` | certificate: a frase secreta de uma chave privada criptografada, guardada criptografada |
| `timeout` | Quanto a conexão e uma requisição podem levar, padrão `30000`, intervalo `1..600000` milissegundos |

As configurações de um método de entrada são recusadas para os outros, de modo que o serviço diz exatamente como entra. Entradas recusadas, chaves de API revogadas, privilégios ausentes, nós inalcançáveis e certificados que não podem ser verificados são informados como tais.

### O que o usuário de consulta vê

O usuário de consulta precisa ler o que lista: a API resolve do Elasticsearch para índices e fluxos de dados, as capacidades de campos (field capabilities) para campos e a descrição do cluster para seu nome. Um papel com `read` e `view_index_metadata` sobre os índices dá os dois primeiros; um usuário sem o privilégio de cluster `monitor` ainda passa no teste de conexão, que então não nomeia a versão nem a licença. Índices e fluxos de dados cujo nome começa com ponto, ocultos e de sistema, só são listados quando o que foi digitado começa com ponto; os índices de apoio dos fluxos de dados são listados pelo seu fluxo de dados. Os campos são os dos documentos: os campos de metadados próprios do Elasticsearch, objetos e multicampos que indexam um campo de novo, como `name.keyword`, são omitidos, pois o agente os concede junto com seu campo.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `index` | Curingas, diferencia maiúsculas; o nome de um índice ou fluxo de dados | `index`, `delete`, `create_index`, `delete_index`, `view_index_metadata`, `monitor`, `manage` |
| `field` | Curingas, diferencia maiúsculas; sob um índice, o caminho de um campo como `address.city` | `read` |
| `cluster` | Curingas, diferencia maiúsculas; o nome do cluster | `cluster_monitor`, `create_snapshot`, `manage_slm`, `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` |

Cada tipo de acesso é o privilégio do Elasticsearch de mesmo nome, exceto `cluster_monitor`, que é o privilégio de cluster `monitor`:

| Tipo de acesso | O que permite |
| --- | --- |
| `read` | Buscas, leituras, contagens, buscas múltiplas e scroll |
| `index`, `delete` | Indexar e atualizar documentos, também em bulk; excluir documentos |
| `create_index`, `delete_index` | Criar índices, também quando a primeira escrita cria um; excluir índices e fluxos de dados |
| `view_index_metadata`, `monitor` | Ler mappings, configurações e aliases; ler estatísticas e recuperação |
| `manage` | Toda ação administrativa de um índice; implica `create_index`, `delete_index`, `view_index_metadata` e `monitor` |
| `cluster_monitor` | Ler a saúde, o estado e as estatísticas do cluster |
| `create_snapshot`, `manage_slm` | Criar snapshots; gerenciar políticas de ciclo de vida de snapshots, o que implica `create_snapshot` |
| `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` | Gerenciar modelos de índice, pipelines de ingestão e políticas de ciclo de vida de índices |

A leitura é decidida por campo, assim como a segurança em nível de campo mostra os campos dos documentos: uma política `read` nomeia campos, e `*` é todo campo. Uma política pode assim permitir um índice e negar um de seus campos, e o usuário lê todos os outros. Nos valores das políticas, `*` corresponde a qualquer sequência de caracteres e `?` a um caractere. O editor de políticas sugere índices e fluxos de dados, os campos dos índices que a política nomeia e o cluster.

### Filtros de linhas

Uma política de filtro de linhas de um índice mostra ao usuário só os documentos que correspondem a uma consulta. A consulta é a Query DSL do Elasticsearch como a segurança em nível de documento a recebe, por exemplo `{"term": {"region": "EU"}}`, e pode ser um modelo, por exemplo com `{{_user.username}}`. Como em outros serviços, um usuário recebe o filtro da primeira política que se aplica a ele. Um filtro de linhas só pode consultar campos que o usuário pode ler: a segurança em nível de campo esconde os demais também da consulta.

O Elasticsearch não mascara valores, por isso o tipo de serviço não oferece mascaramento.

### A licença

O Elasticsearch tem segurança em nível de documento e de campo a partir de uma licença Platinum. Abaixo dela, uma política que deixa um usuário ler só alguns campos ou alguns documentos de um índice não pode ser aplicada; assim o agente não concede a esse usuário a leitura do índice em vez de mais do que as políticas permitem, e avisa. Políticas que permitem índices inteiros, escrita e administração funcionam com qualquer licença.

## Exemplo de políticas

Suponha que o cluster, com licença Platinum, tenha os índices `logs-2026.10.26` e `hr`. Crie estas políticas ativadas no mesmo serviço de dados:

| Recurso | Regra |
| --- | --- |
| `index=logs-*`, `field=*` | Permitir `read` ao grupo `analysts` |
| `index=logs-*`, `field=client_ip` | Negar `read` ao grupo `analysts` |
| `index=logs-*` | Permitir `index` e `create_index` ao usuário `logstash` |
| `index=hr`, `field=*` | Permitir `read` ao grupo `hr` |
| `index=hr` (filtro de linhas) | Mostrar ao usuário `emea-hr` só documentos de `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Permitir `cluster_monitor` ao grupo `ops` |

Quando o agente aplica o novo snapshot, os membros de `analysts` leem todos os campos dos logs exceto o endereço do cliente; `logstash` escreve na hora o `logs-2026.10.27` de amanhã, criando-o com o primeiro documento; os membros de `hr` leem os funcionários; `emea-hr` vê só os funcionários da região; e os membros de `ops` leem a saúde e as estatísticas do cluster.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta recursos; aplicá-las exige um agente do Elasticsearch por cluster. A implantação e as verificações estão em [Agente do Elasticsearch](/pt-br/external/elasticsearch-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do Elasticsearch](/pt-br/external/elasticsearch-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
