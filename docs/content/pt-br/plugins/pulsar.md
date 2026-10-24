---
title: Apache Pulsar
description: Instalar e configurar o plug-in de serviço do Pulsar, conectar a um ou mais brokers, entrar com um token, uma senha, OAuth 2.0 ou um certificado de cliente, consultar tenants, namespaces, tópicos e assinaturas e gerenciar quem pode produzir, consumir e administrar o quê.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do Pulsar consulta tenants, namespaces, tópicos, assinaturas e clusters, e gerencia políticas de acesso sobre o que o Pulsar decide: produzir, consumir, functions, sources, sinks e packages, e administrar tópicos, namespaces, tenants e clusters. O ID do plug-in e o tipo de serviço são ambos `pulsar`. O plug-in de serviço roda no servidor do GrantForge; o [agente do Pulsar](/pt-br/external/pulsar-agent/) instalado nos brokers aplica as políticas.

Os testes de integração com Testcontainers do repositório cobrem os brokers do Pulsar `3.0.17`, `3.3.9`, `4.0.14` e `4.2.5`; isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/pulsar`. Confirme em **Gestão da plataforma → Plug-ins** que `pulsar` está habilitado; examine novamente o diretório de plug-ins após uma atualização. O plug-in fala com o serviço web dos brokers pelo próprio cliente de administração do Pulsar 4.2.5, em seu próprio carregador de classes.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e escolha Apache Pulsar (`pulsar`).
2. Informe a URL do serviço web de um broker ou de vários.
3. Escolha como o papel de consulta entra, informe suas credenciais, teste a conexão e salve o serviço. O teste entra e descreve o cluster: seus clusters e quantos tenants ele tem.

As configurações usam os nomes do próprio `client.conf` do Pulsar e podem ser copiadas dele. O papel de consulta permite que o GrantForge descubra recursos; ele é separado dos papéis que produzem e consomem. Um teste de conexão bem-sucedido não significa que o acesso já está sujeito às políticas.

| Configuração | Finalidade |
| --- | --- |
| `webServiceUrl` | Obrigatória: o serviço web de um broker, `http://host:port` ou `https://host:port`, porta padrão `8080` ou `8443`; vários brokers separados por vírgulas, por exemplo `http://broker1:8080,broker2:8080` |
| `authentication` | `none` (padrão), `token`, `basic`, `oauth2` ou `tls` |
| `token` | token: o token do papel de consulta; armazenado criptografado |
| `username`, `password` | basic: o usuário e a senha do papel de consulta; a senha é armazenada criptografada |
| `issuerUrl`, `privateKey`, `audience`, `scope` | oauth2: o servidor de autorização, o caminho do arquivo JSON de credenciais com `client_id` e `client_secret` no **servidor do GrantForge**, e a audiência e o escopo, se os brokers os esperarem |
| `tlsCertificateFilePath`, `tlsKeyFilePath` | tls: caminhos do certificado de cliente e de sua chave privada, PEM, no **servidor do GrantForge** |
| `tlsTrustCertsFilePath` | https: os certificados de CA com que os dos brokers são verificados, PEM; vazio usa os da JVM |
| `tlsEnableHostnameVerification` | https: se o certificado de um broker deve nomear seu host, padrão `true` |
| `timeout` | Quanto tempo uma requisição pode levar, padrão `30000`, intervalo `1..600000` milissegundos |

As configurações de um modo de entrada são recusadas com outro, de modo que um serviço diz exatamente como entra. Uma entrada recusada, um papel que não pode ver algo e um broker inalcançável, também atrás de um proxy que responde por ele, são informados como tais.

### O que o papel de consulta vê

O Pulsar só deixa seus superusuários listarem clusters e tenants. Nomeie o papel de consulta em `superUserRoles` dos brokers ou, com o agente instalado, permita-lhe `admin` no cluster, o que também o torna superusuário. Namespaces, tópicos e assinaturas que o papel de consulta não pode ver ficam fora das sugestões.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `tenant` | Curinga, diferencia maiúsculas | Todos exceto `admin` |
| `namespace` | Curinga, diferencia maiúsculas; sob um tenant; o nome próprio do namespace, por exemplo `orders` | `produce`, `consume`, `functions`, `sources`, `sinks`, `packages`, `admin_topic`, `admin_namespace` |
| `topic` | Curinga, diferencia maiúsculas; sob um namespace; o nome próprio do tópico, por exemplo `payments` | `produce`, `consume`, `admin_topic` |
| `subscription` | Curinga, diferencia maiúsculas; sob um tópico; o nome da assinatura | `consume` |
| `cluster` | Curinga, diferencia maiúsculas; o nome do cluster, por exemplo `standalone` | `admin`: o acesso de superusuário do Pulsar |

| Tipo de acesso | Implica |
| --- | --- |
| `produce`, `consume`, `functions`, `sources`, `sinks`, `packages` | — |
| `admin_topic`: encerrar, compactar, descarregar no armazenamento em camadas, liberar e truncar tópicos, e alterar suas políticas | `produce`, `consume` |
| `admin_namespace`: criar e excluir tópicos, e alterar as políticas do namespace | `admin_topic` |
| `admin_tenant`: criar, listar e excluir namespaces | `admin_namespace`, `functions`, `sources`, `sinks`, `packages` |
| `admin`: tudo o que os superusuários do Pulsar podem fazer | `admin_tenant` |

Como com as próprias permissões do Pulsar, uma permissão sobre um tenant ou um namespace cobre o que há nele: `produce` no namespace `orders` do tenant `sales` produz em cada um de seus tópicos. Uma verificação é decidida sucessivamente no tenant, no namespace, no tópico e na assinatura, e uma negação em qualquer um deles a recusa; assim, uma política pode permitir um namespace e negar um de seus tópicos. Sob um tópico, um nível de assinatura indica por quais assinaturas um papel pode consumir: `consume` no tópico `*` com a assinatura `billing-*` deixa um papel consumir cada tópico do namespace, mas só por assinaturas cujo nome começa com `billing-`.

Um tópico é decidido pelo nome próprio, sem o domínio, então um tópico persistente e um não persistente de mesmo nome compartilham suas políticas; uma partição de um tópico particionado é decidida como o tópico particionado. Nos valores de política, `*` corresponde a qualquer número de caracteres e `?` a um. O editor de políticas sugere os clusters, os tenants, e os namespaces, tópicos e assinaturas do que uma política nomeia.

### Endereços de cliente

Um item de política pode valer só para alguns clientes: sua condição **Endereços de cliente** (`ip-range`) aceita endereços e faixas como `10.1.0.0/16` ou `2001:db8::/32`. Nomes de host não são resolvidos. Atrás de um proxy, o cliente é o proxy, a menos que ele encaminhe o endereço do próprio cliente: com o protocolo HAProxy para produtores e consumidores, e com `X-Forwarded-For` para o serviço web quando os brokers confiam nele.

## Exemplo de políticas

Suponha que o tenant `sales` tenha os namespaces `orders` e `returns`. Crie estas políticas de acesso habilitadas no mesmo serviço de dados:

| Recursos | Regra |
| --- | --- |
| `tenant=sales`, `namespace=orders` | Permitir `produce` ao grupo `analysts` |
| `tenant=sales`, `namespace=orders`, `topic=audit` | Negar `produce` ao usuário `alice` |
| `tenant=sales`, `namespace=orders`, `topic=*`, `subscription=billing-*` | Permitir `consume` ao usuário `alice` |
| `tenant=sales` | Permitir `admin_tenant` ao usuário `bob` |
| `tenant=public` | Permitir `produce` ao grupo `ingest`, a partir de `10.0.0.0/8` |
| `cluster=standalone` | Permitir `admin` ao grupo `pulsar-admins` |

Quando o agente aplica o novo snapshot, os membros de `analysts` produzem em cada tópico de `sales/orders`, `alice` em todos exceto `audit`; `alice` consome ali só pelas assinaturas `billing-`; `bob` cria e administra namespaces e tópicos em `sales`; os membros de `ingest` produzem no tenant `public` só a partir da rede interna; e os membros de `pulsar-admins` são superusuários do Pulsar.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta os recursos. Para aplicá-las, é preciso o agente do Pulsar em cada broker. A implantação e as verificações estão descritas no [agente do Pulsar](/pt-br/external/pulsar-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do Pulsar](/pt-br/external/pulsar-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
