---
title: Apache Solr
description: Instalar e configurar o plug-in de serviço do Solr, alcançar um ou vários nós, entrar com autenticação basic ou um token web JSON, consultar coleções e seus caminhos de requisição, e gerenciar quem pode consultar, atualizar e administrar o quê.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do Solr consulta coleções e os caminhos de requisição dentro delas, e gerencia políticas de acesso sobre o que o Solr decide: consultar, atualizar, ler e alterar esquemas e configs, administrar coleções e administrar o cluster. Seu ID de plug-in e seu tipo de serviço são ambos `solr`. O plug-in de serviço roda no servidor GrantForge; o [agente do Solr](/pt-br/external/solr-agent/), instalado nos nós, aplica as políticas.

Os testes de integração com Testcontainers do repositório cobrem nós SolrCloud do Solr `9.4.1`, `9.8.1`, `9.11.0` e `10.0.0`; isso não certifica todas as demais versões.

## Instalação

A distribuição inclui o plug-in em `plugins/solr`. Confirme em **Gestão da plataforma → Plug-ins** que `solr` está habilitado; examine novamente o diretório de plug-ins após uma atualização. O plug-in fala com os nós pelo cliente HTTP do JDK do SolrJ 9.11, em seu próprio carregador de classes.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e escolha Apache Solr (`solr`).
2. Informe a URL base de um nó, ou de vários.
3. Escolha como o usuário de consulta entra, informe suas credenciais, teste a conexão e salve o serviço. O teste entra e descreve o cluster: sua versão do Solr, se roda SolrCloud e quantas coleções tem.

O usuário de consulta permite ao GrantForge descobrir recursos; ele é separado dos usuários que consultam e atualizam. Um teste de conexão bem-sucedido não significa que o acesso já esteja sujeito a políticas.

| Configuração | Finalidade |
| --- | --- |
| `solr.urls` | Obrigatória: a URL base de um nó, como `https://solr1.example.com:8983/solr`; várias separadas por vírgulas, tentadas em sequência quando uma não pode ser alcançada |
| `authentication` | `none` (padrão), `basic` ou `jwt` |
| `username`, `password` | basic: o usuário de consulta e sua senha; a senha é armazenada criptografada |
| `token` | jwt: o token web JSON do usuário de consulta; armazenado criptografado |
| `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword`, `javax.net.ssl.trustStoreType` | https: o trust store, no **servidor GrantForge**, contra o qual os certificados dos nós são verificados, sua senha e seu tipo, padrão `PKCS12`; vazio usa o da JVM |
| `timeout` | Quanto tempo uma requisição pode levar, padrão `30000`, faixa `1..600000` milissegundos |

As configurações de um modo de entrada são recusadas com outro, de modo que um serviço diz exatamente como entra. Uma entrada recusada, um usuário que não pode ver algo e um nó que não pode ser alcançado, também atrás de um proxy que responde por ele, são relatados como tais.

### O que o usuário de consulta vê

Listar coleções é a permissão `collection-admin-read` do Solr, e os caminhos de requisição de uma coleção vêm de sua config, `config-read`. Conceda-as ao usuário de consulta nas regras do próprio Solr ou, depois de instalar o agente, `admin_read` no cluster e `config_read` nas coleções. Os caminhos das coleções que o usuário de consulta não pode ver ficam fora das sugestões. Um nó que roda sem SolrCloud tem cores em vez de coleções, e o plug-in os apresenta como coleções.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `collection` | Curinga, diferencia maiúsculas; o nome da coleção | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit`, `admin_collection` |
| `path` | Curinga, diferencia maiúsculas; abaixo de uma coleção; o caminho de requisição, como `/select`, `/export` ou `/update/json` | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit` |
| `cluster` | Seu único valor, `*` | `health`, `metrics`, `admin_read`, `security`, `admin` |

| Tipo de acesso | Permissões do Solr | Implica |
| --- | --- | --- |
| `query` | `read`: pesquisar, obter, exportar | — |
| `update` | `update` | — |
| `schema_read`, `schema_edit` | `schema-read`, `schema-edit` | `schema_edit` implica `schema_read` |
| `config_read`, `config_edit` | `config-read`, `config-edit` | `config_edit` implica `config_read` |
| `admin_collection` | `collection-admin-read` e `collection-admin-edit` das requisições da API Collections que nomeiam a coleção: criar, recarregar, fazer backup, excluir e afins | `query`, `update`, `schema_edit`, `config_edit` |
| `health`, `metrics` | `health`, `metrics-read` | — |
| `admin_read` | `collection-admin-read` do cluster inteiro, `core-admin-read`, `zk-read`, `filestore-read`, `package-read`, e a leitura da config e do esquema dos nós | `health`, `metrics` |
| `security` | `security-read`, `security-edit` | — |
| `admin` | Tudo | `admin_read`, `security`, `admin_collection` |

Uma requisição a uma coleção é decidida na coleção e na coleção com seu caminho de requisição, e uma negação em qualquer um dos dois a recusa; assim, uma política pode permitir uma coleção e negar seu handler `/export`, ou permitir atualizar apenas por `/update*`. Uma requisição a várias coleções precisa de cada uma delas. Abaixo de uma coleção, o caminho de requisição é o handler para o qual o Solr encaminha a requisição, como o Solr o nomeia.

Uma requisição da API Collections que nomeia coleções, por seu parâmetro `name` ou `collection`, precisa de `admin_collection` em cada uma; assim, uma política em `team-*` permite a uma equipe criar coleções cujo nome começa com `team-`, e recarregá-las, fazer backup delas ou excluí-las, mas nenhuma outra. Uma requisição que não nomeia nenhuma, como listar todas as coleções, pertence ao cluster. `admin` no cluster concede tudo, como a permissão `all` do próprio Solr. Nos valores de política, `*` corresponde a qualquer sequência de caracteres e `?` a um. O editor de políticas sugere as coleções e os caminhos de requisição das coleções que uma política nomeia.

### Endereços de cliente

Um item de política pode valer apenas para alguns clientes: sua condição **Endereços de cliente** (`ip-range`) aceita endereços e faixas como `10.1.0.0/16` ou `2001:db8::/32`. Nomes de host não são resolvidos. Atrás de um proxy reverso, o cliente é o proxy, a menos que o agente confie nele: então o cliente é o último endereço de `X-Forwarded-For` que nenhum proxy confiável adicionou, de modo que um cliente não pode alegar um endereço de sua escolha.

## Exemplo de políticas

Suponha que o cluster tenha as coleções `sales` e `hr`. Crie estas políticas de acesso habilitadas no mesmo serviço de dados:

| Recursos | Regra |
| --- | --- |
| `collection=sales` | Permitir `query` ao grupo `analysts` |
| `collection=sales`, `path=/export` | Negar `query` ao usuário `alice` |
| `collection=sales`, `path=/update*` | Permitir `update` ao usuário `ingest` |
| `collection=team-*` | Permitir `admin_collection` ao grupo `search-team` |
| `cluster=*` | Permitir `metrics` e `health` ao usuário `monitoring` |
| `collection=hr` | Permitir `query` ao grupo `hr-staff`, a partir de `10.0.0.0/8` |
| `cluster=*` | Permitir `admin` ao grupo `solr-admins` |

Quando o agente aplica o novo snapshot, os membros de `analysts` consultam `sales`, `alice` tudo menos seu handler de exportação; `ingest` atualiza `sales` por seus handlers de atualização e não pode consultá-la; os membros de `search-team` criam e administram suas próprias coleções `team-`; `monitoring` lê métricas e saúde; os membros de `hr-staff` consultam `hr` apenas a partir da rede interna; e os membros de `solr-admins` podem fazer tudo.

## Aplicar as políticas

O plug-in de serviço gerencia políticas e consulta recursos. Aplicá-las exige o agente do Solr em cada nó. Implantação e verificações estão no [agente do Solr](/pt-br/external/solr-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do Solr](/pt-br/external/solr-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
