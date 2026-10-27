---
title: OpenSearch
description: Instalar e configurar o plug-in de serviço do OpenSearch, alcançar um ou vários nós, entrar com senha ou certificado de cliente TLS, consultar índices, fluxos de dados, campos e o cluster, e gerenciar quem pode ler, escrever e administrar o quê, até campos, valores mascarados e documentos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do OpenSearch consulta índices, fluxos de dados, campos e o cluster, e gerencia políticas de acesso nos termos do OpenSearch: ler campos, indexar, excluir, criar e administrar índices, e as operações do próprio cluster, com máscaras de campos e filtros de linhas de índices. O ID do plug-in e o tipo de serviço são ambos `opensearch`. O plug-in de serviço roda no servidor do GrantForge; o [agente do OpenSearch](/pt-br/external/opensearch-agent/), um serviço ao lado do cluster, aplica as políticas com os papéis, a segurança em nível de campo, os campos mascarados e a segurança em nível de documento do plug-in de segurança.

Os testes de integração com Testcontainers do repositório cobrem o OpenSearch `2.19`, `3.3`, `3.6` e `3.9` com o plug-in de segurança; isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/opensearch`. Confirme que `opensearch` está ativado em **Gestão da plataforma → Plug-ins**; reexamine o diretório de plug-ins depois de atualizá-lo. O plug-in se comunica com os nós pelo cliente REST de baixo nível do OpenSearch, em um carregador de classes próprio.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione OpenSearch (`opensearch`).
2. Informe um nó como URL, ou vários nós.
3. Escolha como o usuário de consulta entra, informe suas credenciais, teste a conexão e salve o serviço. O teste entra e descreve o cluster: sua versão do OpenSearch e seu nome, o usuário com que entrou e quantos índices e fluxos de dados ele tem. Um cluster sem plug-in de segurança não passa no teste, já que o agente aplica as políticas por meio dele.

O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos usuários que leem e escrevem. Um teste de conexão bem-sucedido não significa que o acesso já está sujeito a políticas.

| Configuração | Finalidade |
| --- | --- |
| `opensearch.hosts` | Obrigatório: um nó como URL, por exemplo `https://opensearch1.example.com:9200`; vários separados por vírgulas, tentados em sequência quando um não pode ser alcançado. Sem esquema um nó é `https`, e a porta padrão é `9200`; os nós de um serviço são todos `http` ou todos `https` |
| `authentication` | `password` (padrão), ou `certificate`: um certificado de cliente TLS que o plug-in de segurança associa a um usuário |
| `username`, `password` | password: o usuário de consulta e sua senha, guardada criptografada |
| `ssl.verificationMode` | Como o OpenSearch Dashboards o nomeia: `full` (padrão) verifica os certificados e os nomes dos nós, `certificate` só os certificados, `none` nada |
| `ssl.certificateAuthorities` | Caminho no **servidor do GrantForge** dos certificados de CA PEM contra os quais os nós são verificados; vazio usa os da JVM |
| `ssl.certificate`, `ssl.key` | certificate: caminhos no servidor do GrantForge do certificado de cliente PEM e de sua chave privada, PKCS #8 ou a forma antiga do OpenSSL; exigem nós `https` |
| `ssl.keyPassphrase` | certificate: a frase secreta de uma chave privada criptografada, guardada criptografada |
| `timeout` | Quanto a conexão e uma requisição podem levar, padrão `30000`, intervalo `1..600000` milissegundos |

As configurações de um método de entrada são recusadas para o outro, de modo que o serviço diz exatamente como entra. Entradas recusadas, permissões ausentes, nós inalcançáveis e certificados que não podem ser verificados são informados como tais.

### O que o usuário de consulta vê

O usuário de consulta precisa ler o que lista: a API resolve do OpenSearch para índices e fluxos de dados, as capacidades de campos (field capabilities) para campos e a descrição do cluster para seu nome. O papel embutido `readall` dá os dois primeiros; um usuário sem `cluster:monitor/main` ainda passa no teste de conexão, que então não nomeia a versão. Índices e fluxos de dados cujo nome começa com ponto, ocultos e de sistema, só são listados quando o que foi digitado começa com ponto; os índices de apoio dos fluxos de dados são listados pelo fluxo. Os campos são os dos documentos: os campos de metadados do próprio OpenSearch, os objetos e os multicampos que indexam um campo mais uma vez, como `name.keyword`, ficam de fora, pois a segurança em nível de campo os mostra ou esconde junto com seu campo.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `index` | Curingas, diferencia maiúsculas; o nome de um índice ou fluxo de dados | `index`, `delete`, `create_index`, `manage_aliases`, `monitor`, `manage` |
| `field` | Curingas, diferencia maiúsculas; abaixo de um índice, o caminho de um campo como `address.city` | `read` |
| `cluster` | Curingas, diferencia maiúsculas; o nome do cluster | `cluster_monitor`, `manage_snapshots`, `manage_templates`, `manage_pipelines` |

| Tipo de acesso | Grupo de ações do plug-in de segurança |
| --- | --- |
| `read` | `read`: buscas, leituras, contagens e afins |
| `index` | `index`: indexar e atualizar documentos, também por bulk |
| `delete` | `delete`: excluir documentos |
| `create_index` | `create_index`: criar índices, também quando a primeira escrita cria um |
| `manage_aliases`, `monitor` | `manage_aliases`, `indices_monitor` |
| `manage` | `manage`: toda ação administrativa de um índice, inclusive excluí-lo; implica `create_index`, `manage_aliases` e `monitor` |
| `cluster_monitor`, `manage_snapshots` | `cluster_monitor`, `manage_snapshots` |
| `manage_templates`, `manage_pipelines` | `cluster_manage_index_templates`, `cluster_manage_pipelines` |

A leitura é decidida por campo, como a segurança em nível de campo mostra os campos dos documentos: uma política `read` nomeia campos, e `*` é todo campo. Assim uma política pode permitir um índice e negar um de seus campos, e o usuário lê todos os outros. Nos valores de política, `*` corresponde a qualquer sequência de caracteres e `?` a um caractere. O editor de políticas sugere índices e fluxos de dados, os campos dos índices que a política nomeia e o cluster.

Usar o que uma política permite exige mais no OpenSearch: multi-search, multi-get e rolagem para ler, e bulk para escrever. Não são tipos de acesso próprios; o agente concede com eles as operações compostas do cluster.

### Máscaras

Uma política de mascaramento de um campo mostra ao usuário seus valores mascarados, como os campos mascarados do plug-in de segurança, em documentos e em agregações igualmente:

| Máscara | O que o usuário vê |
| --- | --- |
| Ocultar (Redact) | Maiúsculas como `X`, minúsculas como `x` e dígitos como `n` |
| Máscara parcial: mostrar os últimos 4 / os primeiros 4 | Cada outro caractere como `x` |
| Hash (SHA-256) | O hash SHA-256 do valor |
| Hash com o sal do cluster (BLAKE2b) | O hash próprio do plug-in de segurança, com o sal do cluster como chave, de modo que valores iguais continuam iguais mas não podem ser descobertos |
| Personalizada | Um algoritmo de hash como `SHA-512`, ou expressões regulares com suas substituições como `/[0-9]/::*` |
| Sem máscara | O valor como está |

### Filtros de linhas

Uma política de filtro de linhas de um índice mostra ao usuário apenas os documentos que uma consulta encontra. A consulta é a DSL de consultas do OpenSearch como a segurança em nível de documento a aceita, por exemplo `{"term": {"region": "EU"}}`, e pode usar as substituições do plug-in de segurança como `${user.name}`. Como em outros serviços, um usuário recebe o filtro da primeira política que se aplica a ele. Um filtro de linhas só pode testar campos que o usuário pode ler: a segurança em nível de campo também esconde os demais da consulta.

## Exemplo de políticas

Suponha que o cluster tenha os índices `logs-2026.10.26` e `hr`. Crie estas políticas ativadas no mesmo serviço de dados:

| Recurso | Regra |
| --- | --- |
| `index=logs-*`, `field=*` | Permitir ao grupo `analysts` `read` |
| `index=logs-*`, `field=client_ip` | Negar `read` ao grupo `analysts` |
| `index=logs-*` | Permitir ao usuário `logstash` `index` e `create_index` |
| `index=hr`, `field=*` | Permitir ao grupo `hr` `read` |
| `index=hr`, `field=salary` (mascaramento) | Mostrar ao grupo `hr` o hash com o sal do cluster |
| `index=hr` (filtro de linhas) | Mostrar ao usuário `emea-hr` apenas documentos de `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Permitir ao grupo `ops` `cluster_monitor` |

Quando o agente aplica o novo snapshot, os membros de `analysts` leem todos os campos dos logs exceto o endereço do cliente; `logstash` escreve na hora o `logs-2026.10.27` de amanhã, criando-o com o primeiro documento; os membros de `hr` veem os salários como hash; `emea-hr` vê apenas os funcionários da região; e os membros de `ops` leem a saúde e as estatísticas do cluster.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta recursos; aplicá-las exige um agente do OpenSearch por cluster. A implantação e as verificações estão em [Agente do OpenSearch](/pt-br/external/opensearch-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do OpenSearch](/pt-br/external/opensearch-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
