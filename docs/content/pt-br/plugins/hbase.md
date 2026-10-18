---
title: Apache HBase
description: Instale e configure o plug-in de serviço HBase, encontre o cluster pelo ZooKeeper ou por servidores de bootstrap, entre no modo simples ou com Kerberos, consulte namespaces, tabelas e famílias de colunas e gerencie políticas de acesso nos escopos do próprio HBase.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in HBase consulta namespaces, tabelas e famílias de colunas, e gerencia políticas de acesso nos escopos das permissões do próprio HBase: o escopo global, namespaces, tabelas, famílias de colunas e colunas. O ID do plug-in e o tipo de serviço são ambos `hbase`. O plug-in de serviço é executado no servidor GrantForge; o [agente do HBase](/pt-br/external/hbase-agent/), instalado nos masters e region servers, aplica as políticas.

Os testes de integração com Testcontainers do repositório cobrem o HBase `2.5.16`, `2.6.7` e `3.0.0`, construídos a partir das versões da Apache verificadas por SHA-512, e um cluster Kerberos do `2.6.7`; isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/hbase`. Confirme que `hbase` está habilitado em **Gestão da plataforma → Plug-ins**; verifique novamente o diretório de plug-ins depois de atualizá-lo. O plug-in conversa com o cluster pelo cliente do próprio HBase (`hbase-shaded-client-byo-hadoop` 2.6.7) sobre o cliente shaded do Hadoop 3.5.0, em um carregador de classes próprio, então funciona com HBase 2.x e 3.x.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione Apache HBase (`hbase`).
2. Informe o quórum do ZooKeeper, ou os servidores de bootstrap de um cluster HBase 2.5 ou posterior.
3. Escolha como o usuário de consulta entra, informe-o, teste a conexão e salve o serviço. O teste entra e descreve o cluster: seu ID, sua versão e quantos region servers estão ativos.

As configurações usam os nomes dos clientes do HBase e podem ser copiadas de um `hbase-site.xml`. O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos clientes que leem e escrevem. Um teste de conexão bem-sucedido não significa que as solicitações já estão sujeitas às políticas.

| Configuração | Finalidade |
| --- | --- |
| `hbase.zookeeper.quorum` | Hosts do ZooKeeper, `host` ou `host:porta` separados por vírgulas, como `zk1,zk2,zk3:2181`; obrigatório sem servidores de bootstrap |
| `hbase.zookeeper.property.clientPort` | A porta dos hosts do quórum que não indicam uma, padrão `2181` |
| `zookeeper.znode.parent` | A raiz do cluster no ZooKeeper, padrão `/hbase` |
| `hbase.client.bootstrap.servers` | Em vez do ZooKeeper, a partir do HBase 2.5: masters ou region servers, `host:porta` separados por vírgulas |
| `hbase.security.authentication` | `simple` (padrão): um nome de usuário; `kerberos`: um principal com keytab ou senha |
| `username` | Usuário de consulta obrigatório; com Kerberos, o principal completo, como `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: a senha do principal, sem keytab; armazenada criptografada |
| `keytab` | Kerberos: caminho do keytab do principal, legível no **servidor GrantForge** |
| `kerberos.kdc` | Kerberos: KDCs do realm do principal, `host[:porta]` separados por vírgulas; vazio usa o `krb5.conf` do servidor |
| `hbase.master.kerberos.principal` | Kerberos: o principal dos masters, padrão `hbase/_HOST@` e o realm do principal de consulta |
| `hbase.regionserver.kerberos.principal` | Kerberos: o principal dos region servers, padrão o dos masters |
| `hbase.rpc.protection` | `authentication` (padrão), `integrity` ou `privacy`, como o do cluster |
| `hbase.rpc.timeout` | Tempo limite de RPC, padrão `30000`, intervalo `1..600000` |

Um serviço é exatamente o que suas configurações dizem: nenhum `hbase-site.xml` do servidor GrantForge é lido. As configurações Kerberos são recusadas com autenticação simples; Kerberos precisa de senha ou keytab. As consultas desistem após poucas tentativas em vez dos minutos do HBase.

### Kerberos

Com `kerberos`, o GrantForge autentica o principal pelo login do próprio Hadoop, com o keytab ou a senha, mantém o login e o renova a partir do keytab; nada mais no servidor GrantForge precisa de configurações Kerberos. Realms cujos KDCs são informados em `kerberos.kdc` são declarados para todo o servidor GrantForge, como fazem os plug-ins HDFS e Hive, de modo que vários serviços podem usar realms diferentes lado a lado.

Um cluster fecha a conexão de um cliente que entra de forma diferente da esperada, sem dizer por quê. O plug-in relata isso como um login recusado e aponta para `hbase.security.authentication` e `hbase.rpc.protection`, que precisam corresponder aos do cluster.

### O que o usuário de consulta vê

Quando o [agente do HBase](/pt-br/external/hbase-agent/) aplica as políticas, as listagens omitem os namespaces e tabelas que o usuário de consulta não pode usar: dê a ele uma política que permita `read` no namespace `*`, ou nomeie-o em `hbase.superuser` do cluster.

## Recursos e tipos de acesso

| Nível | Corresponde a | Tipos de acesso |
| --- | --- | --- |
| `global` | Seu único valor, `*` | `admin`: operações sobre todo o cluster, como o balanceador, as cotas e a replicação |
| `namespace` | Curingas, diferenciando maiúsculas | `read`, `write`, `create`, `admin` |
| `table` | Curingas, diferenciando maiúsculas; abaixo de um namespace, nomeada sem ele | `read`, `write`, `create`, `admin` |
| `column_family` | Curingas, diferenciando maiúsculas; abaixo de uma tabela | `read`, `write` |
| `column` | Curingas, diferenciando maiúsculas; abaixo de uma família de colunas; nomeada como texto UTF-8 | `read`, `write` |

Os tipos de acesso são os do HBase: `read`, `write`, `create` e `admin`. O `exec` do HBase, para endpoints de coprocessadores, não é oferecido, porque o agente não consegue verificar essas chamadas em todas as versões do HBase.

Como no HBase, uma permissão cobre tudo no seu escopo: `read` no namespace `sales` lê todas as tabelas dele, `write` em uma família de colunas escreve todas as suas colunas. Uma solicitação é decidida em cada escopo que nomeia, e uma negação em qualquer um deles a recusa; assim, uma política pode permitir uma tabela e negar uma de suas colunas. O escopo global é o próprio cluster, não todas as tabelas; políticas no namespace `*` cobrem todas as tabelas.

Nos valores das políticas, `*` corresponde a qualquer número de caracteres e `?` a um. O editor de políticas sugere namespaces, tabelas dos namespaces que uma política nomeia e famílias de colunas das suas tabelas; colunas guardam dados e não são sugeridas.

## Exemplo de políticas

Suponha que o namespace `sales` tenha uma tabela `orders` com as famílias de colunas `d`, que contém `name` e `ssn`, e `meta`. Crie estas políticas de acesso habilitadas no mesmo serviço de dados:

| Recursos | Regra |
| --- | --- |
| `namespace=sales` | Permitir `read` para o usuário `alice` |
| `namespace=sales`, `table=orders`, `column_family=d` | Permitir `read` para o usuário `bob` |
| `namespace=sales`, `table=orders` | Permitir `read` para o grupo `auditors` |
| `namespace=sales`, `table=orders`, `column_family=d`, `column=ssn` | Negar `read` para o grupo `auditors` |
| `namespace=analytics` | Permitir `create` e `admin` para o usuário `dave` |

Depois que o agente aplica o novo snapshot, `alice` lê todas as tabelas de `sales`; `bob` lê `orders` e recebe só as colunas de `d`, e ao listar tabelas vê só `orders`; os membros de `auditors` leem todo o `orders` exceto `d:ssn`; `dave` cria o namespace `analytics` e suas tabelas, as administra, e tem tabelas em outros lugares recusadas.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta recursos. Para aplicá-las, cada master e region server precisa do agente do HBase. Veja o [agente do HBase](/pt-br/external/hbase-agent/) para sua implantação e o que ele verifica.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do HBase](/pt-br/external/hbase-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
