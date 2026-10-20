---
title: Apache Hadoop YARN
description: Instalar e configurar o plug-in de serviço do YARN, conectar a um ResourceManager ou a um par de alta disponibilidade, entrar no modo simples ou com Kerberos, consultar filas e nomes de aplicações e gerenciar quem pode enviar quais aplicações e para onde.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do YARN consulta as filas e os nomes das aplicações nelas, e gerencia políticas de acesso sobre o que o YARN decide: enviar aplicações a filas, administrar filas e administrar o cluster. O ID do plug-in e o tipo de serviço são ambos `yarn`. O plug-in de serviço roda no servidor do GrantForge; o [agente do YARN](/pt-br/external/yarn-agent/) instalado nos ResourceManagers aplica as políticas.

Os testes de integração com Testcontainers do repositório cobrem os ResourceManagers do Hadoop `3.2.4`, construído a partir da versão Apache verificada com SHA-512, `3.3.6`, `3.4.3` e `3.5.0`, além de um ResourceManager com Kerberos `3.4.3`; isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/yarn`. Confirme em **Gestão da plataforma → Plug-ins** que `yarn` está habilitado; examine novamente o diretório de plug-ins após uma atualização. O plug-in fala com o ResourceManager pelo próprio cliente YARN do Hadoop, sobre o cliente sombreado do Hadoop 3.5.0, em seu próprio carregador de classes.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e escolha Apache Hadoop YARN (`yarn`).
2. Informe o endereço de cliente do ResourceManager, ou o de cada ResourceManager de um cluster de alta disponibilidade.
3. Escolha como o usuário de consulta entra, informe-o, teste a conexão e salve o serviço. O teste entra e descreve o cluster: quantos NodeManagers e quantas filas ele tem.

As configurações usam os nomes do próprio Hadoop e podem ser copiadas de um `yarn-site.xml`. O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos usuários que enviam aplicações. Um teste de conexão bem-sucedido não significa que os envios já estão sujeitos às políticas.

| Configuração | Finalidade |
| --- | --- |
| `yarn.resourcemanager.address` | Obrigatória: o ResourceManager, `host` ou `host:port`, porta padrão `8032`; vários, separados por vírgulas, para um cluster de alta disponibilidade, entre os quais o cliente alterna |
| `hadoop.security.authentication` | `simple` (padrão): um nome de usuário; `kerberos`: um principal com keytab ou senha |
| `username` | Usuário de consulta obrigatório; com Kerberos, o principal completo, por exemplo `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: a senha do principal, quando nenhum keytab é informado; armazenada criptografada |
| `keytab` | Kerberos: caminho do keytab do principal, legível no **servidor do GrantForge** |
| `kerberos.kdc` | Kerberos: KDCs do realm do principal, `host[:port]`, separados por vírgulas; vazio usa o `krb5.conf` do servidor |
| `yarn.resourcemanager.principal` | Kerberos: o principal dos ResourceManagers, padrão `yarn/_HOST@` e o realm do principal de consulta |
| `hadoop.rpc.protection` | `authentication` (padrão), `integrity` ou `privacy`, como no cluster |
| `timeout` | Quanto tempo uma requisição pode levar, padrão `30000`, intervalo `1..600000` milissegundos; o próprio cliente do YARN tentaria de novo por quinze minutos |

O serviço é exatamente o que suas configurações dizem: nenhum `core-site.xml` ou `yarn-site.xml` do servidor do GrantForge é lido. As configurações de Kerberos são recusadas com autenticação simples; o Kerberos exige uma senha ou um keytab. Um ResourceManager que recusa a entrada, por exemplo porque espera Kerberos, é informado como entrada recusada.

### Kerberos

Com `kerberos`, o GrantForge autentica o principal pelo próprio login do Hadoop, a partir do keytab ou com a senha, mantém esse login e o renova a partir do keytab; nada mais no servidor do GrantForge precisa de configurações de Kerberos. Os realms cujos KDCs constam em `kerberos.kdc` são declarados para todo o servidor do GrantForge, como fazem os plug-ins do HDFS, Hive, Kafka, HBase e Trino, de modo que vários serviços podem usar realms diferentes lado a lado.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `queue` | Curinga, diferencia maiúsculas; o caminho da fila a partir da raiz, por exemplo `root.sales.etl` | `submit_app`, `admin_queue` |
| `application` | Curinga, diferencia maiúsculas; sob uma fila; o nome da aplicação | `submit_app` |
| `cluster` | Seu único valor, `*` | `admin`: a administração do YARN, por exemplo recarregar filas, nós e rótulos de nó |

`admin_queue` implica `submit_app`, já que os administradores de uma fila podem enviar a ela.

Como no YARN, uma permissão sobre uma fila cobre as filas abaixo dela: `submit_app` em `root.sales` envia a `root.sales.etl`. Uma verificação é decidida em cada fila do caminho, a partir da raiz, e uma negação em qualquer uma delas a recusa; assim, uma política pode permitir `root.sales` e negar `root.sales.adhoc`. Sob uma fila, um nível de aplicação indica a quais aplicações uma permissão se aplica: `submit_app` em `root.hr` com a aplicação `payroll-*` só aceita aplicações cujo nome começa com `payroll-`, em `root.hr` e nas filas abaixo.

Nos valores de política, `*` corresponde a qualquer número de caracteres e `?` a um. O editor de políticas sugere filas pelo caminho, ou pelo próprio nome quando é isso que se digita, seja qual for o escalonador do cluster, além dos nomes das aplicações de que o ResourceManager ainda se lembra nas filas nomeadas por uma política.

### Endereços de cliente

Um item de política pode valer só para alguns clientes: sua condição **Endereços de cliente** (`ip-range`) aceita endereços, faixas como `10.1.0.0/16` e nomes de host, como as listas de hosts do próprio Hadoop. Atrás de um proxy, por exemplo o proxy web do YARN ou um router, o cliente é o primeiro endereço que os proxies encaminharam. Endereços e faixas são melhores que nomes de host, que custam uma resolução de nome do cliente. O YARN pergunta sobre sua administração sem o endereço do cliente, então a condição nunca vale em `cluster`.

## Exemplo de políticas

Suponha que o cluster execute as filas `root.sales.etl`, `root.sales.adhoc` e `root.hr`. Crie estas políticas de acesso habilitadas no mesmo serviço de dados:

| Recursos | Regra |
| --- | --- |
| `queue=root.sales` | Permitir `submit_app` ao grupo `analysts` |
| `queue=root.sales.adhoc` | Negar `submit_app` ao usuário `alice` |
| `queue=root.hr`, `application=payroll-*` | Permitir `submit_app` ao usuário `bob` |
| `queue=root.sales` | Permitir `admin_queue` ao usuário `carol` |
| `queue=root` | Permitir `submit_app` ao grupo `etl-robots`, a partir de `10.0.0.0/8` |
| `cluster=*` | Permitir `admin` ao grupo `yarn-admins` |

Quando o agente aplica o novo snapshot, os membros de `analysts` enviam a `root.sales.etl` e `root.sales.adhoc`, mas `alice` só a `root.sales.etl`; `bob` envia aplicações de folha de pagamento a `root.hr` e nada mais; `carol` envia às filas de vendas e encerra suas aplicações; os membros de `etl-robots` enviam a qualquer fila, mas só a partir da rede interna; e os membros de `yarn-admins` recarregam as filas.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta os recursos. Para aplicá-las, é preciso o agente do YARN em cada ResourceManager. A implantação e as verificações estão descritas no [agente do YARN](/pt-br/external/yarn-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do YARN](/pt-br/external/yarn-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
