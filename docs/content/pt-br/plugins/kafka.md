---
title: Apache Kafka
description: Instale e configure o plug-in de serviço Kafka, entre com PLAIN, SCRAM ou Kerberos em texto puro ou TLS, consulte tópicos, grupos, IDs transacionais e usuários e gerencie políticas de acesso nos termos do próprio Kafka.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in Kafka consulta tópicos, grupos de consumidores, IDs transacionais, usuários SCRAM e tokens de delegação, e gerencia políticas de acesso nos termos do próprio Kafka: seus tipos de recurso são os níveis de recurso e suas operações de ACL os tipos de acesso. O ID do plug-in e o tipo de serviço são ambos `kafka`. O plug-in de serviço é executado no servidor GrantForge; o [agente do Kafka](/pt-br/external/kafka-agent/), instalado nos brokers e controllers, aplica as políticas.

Os testes de integração com Testcontainers do repositório cobrem o Kafka `3.7.2`, `3.9.2`, `4.1.2` e `4.3.1` com logins SASL PLAIN e SCRAM e o autorizador do próprio Kafka, além de Kerberos (keytab e senha) e TLS contra o `4.3.1`; isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/kafka`. Confirme que `kafka` está habilitado em **Gestão da plataforma → Plug-ins**; verifique novamente o diretório de plug-ins depois de atualizá-lo. O plug-in conversa com os brokers pelo cliente Admin do próprio Kafka (`kafka-clients` 4.3.1), em um carregador de classes próprio.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione Apache Kafka (`kafka`).
2. Informe os servidores de bootstrap e como o listener dos brokers é protegido.
3. Informe o usuário de consulta, teste a conexão e salve o serviço. O teste entra e descreve o cluster: seu ID e quantos brokers ele tem.

As configurações usam os nomes dos clientes do Kafka e podem ser copiadas de uma configuração de cliente. O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos clientes que produzem e consomem. Um teste de conexão bem-sucedido não significa que as solicitações já estão sujeitas às políticas.

| Configuração | Finalidade |
| --- | --- |
| `bootstrap.servers` | Brokers iniciais obrigatórios, `host:porta` separados por vírgulas, como `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT` (padrão), `SSL`, `SASL_PLAINTEXT` ou `SASL_SSL` |
| `sasl.mechanism` | Somente protocolos SASL: `PLAIN`, `SCRAM-SHA-256` ou `SCRAM-SHA-512` com senha, `GSSAPI` para Kerberos |
| `username` | SASL: o usuário de consulta; com Kerberos, o principal completo, como `grantforge@EXAMPLE.COM` |
| `password` | A senha do usuário ou, sem keytab, a senha Kerberos do principal; armazenada criptografada |
| `keytab` | Kerberos: caminho do keytab do principal, legível no **servidor GrantForge** |
| `kerberos.kdc` | Kerberos: KDCs do realm do principal, `host[:porta]` separados por vírgulas; vazio usa o `krb5.conf` do servidor |
| `sasl.kerberos.service.name` | Kerberos: a primeira parte dos principais dos brokers, padrão `kafka` |
| `ssl.truststore.location`, `ssl.truststore.password`, `ssl.truststore.type` | TLS: o truststore que verifica os brokers (`JKS`, `PKCS12` ou `PEM`); vazio confia no que o Java confia |
| `ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password` | TLS: o certificado do GrantForge, para brokers que pedem um aos clientes |
| `ssl.verify.hostname` | TLS: se os certificados dos brokers precisam nomear os hosts conectados, padrão `true` |
| `request.timeout.ms` | Tempo limite das solicitações, padrão `30000`, intervalo `1..600000` |

As configurações SASL são recusadas com `PLAINTEXT` e `SSL`, e as de TLS com `PLAINTEXT` e `SASL_PLAINTEXT`. `PLAIN` e SCRAM precisam de senha; `GSSAPI` precisa de senha ou keytab.

### Kerberos

Com `GSSAPI`, o GrantForge autentica o principal pelo login Kerberos do próprio Kafka, com o keytab ou com a senha, que o GrantForge entrega ao login diretamente; nada mais no servidor GrantForge precisa de configurações Kerberos. Realms cujos KDCs são informados em `kerberos.kdc` são declarados para todo o servidor GrantForge, como fazem os plug-ins HDFS e Hive, de modo que vários serviços podem usar realms diferentes lado a lado.

### O que o usuário de consulta vê

O Kafka mostra a um usuário apenas o que ele pode descrever, então o usuário de consulta precisa de `describe` nos tópicos, grupos e IDs transacionais que deve sugerir, e de `describe` no cluster para usuários SCRAM. O Kafka omite tópicos e grupos que ele não pode descrever, então as sugestões ficam vazias em vez de recusadas. Tópicos internos, como `__consumer_offsets`, são sugeridos quando o texto digitado começa com `_`. Um cluster sem tokens de delegação, ou sem SCRAM, não sugere nenhum.

Quando o [agente do Kafka](/pt-br/external/kafka-agent/) aplica as políticas, elas decidem também o que o usuário de consulta pode descrever: dê a ele uma política que permita `describe`, ou nomeie-o em `super.users` dos brokers.

## Recursos e tipos de acesso

| Nível | Recurso do Kafka | Tipos de acesso |
| --- | --- | --- |
| `topic` | Tópicos | `read`, `write`, `create`, `delete`, `alter`, `describe`, `describe_configs`, `alter_configs`, `all` |
| `group` | Grupos de consumidores | `read`, `delete`, `describe`, `all` |
| `transactional_id` | IDs transacionais | `write`, `describe`, `all` |
| `cluster` | O cluster, sempre chamado `kafka-cluster` | `create`, `alter`, `describe`, `cluster_action`, `describe_configs`, `alter_configs`, `idempotent_write`, `all` |
| `delegation_token` | Tokens de delegação, por ID do token | `describe`, `all` |
| `user` | Usuários cujos tokens de delegação são criados ou descritos | `create_tokens`, `describe_tokens`, `all` |

Cada nível é independente; uma política nomeia um. Os valores diferenciam maiúsculas de minúsculas: `*` corresponde a qualquer número de caracteres e `?` a um. Os tipos de acesso implicam outros como as operações de ACL do Kafka: `read`, `write`, `delete` e `alter` implicam `describe`, `alter_configs` implica `describe_configs`, e `all` implica todos os outros.

As implicações valem também para negações: uma política que nega `write` em um tópico nega também o `describe` que `write` implica, então o usuário deixa de ver o tópico. As ACLs do próprio Kafka aplicam implicações apenas a operações permitidas.

O editor de políticas sugere nomes do cluster por prefixo; para `cluster`, sugere `kafka-cluster`.

## Exemplo de políticas

Suponha que produtores do usuário `alice` escrevem em tópicos cujos nomes começam com `orders`, e que o grupo `analysts` os lê com o grupo de consumidores `billing`. Crie três políticas de acesso habilitadas no mesmo serviço de dados:

| Recursos | Regra |
| --- | --- |
| `topic=orders*` | Permitir `write` para o usuário `alice` |
| `topic=orders*` | Permitir `read` para o grupo `analysts` |
| `group=billing` | Permitir `read` para o grupo `analysts` |

Depois que o agente aplica o novo snapshot, `alice` pode produzir em `orders-eu`, inclusive com um produtor idempotente e sem `idempotent_write` no cluster; os membros de `analysts` o consomem como `billing`; outros tópicos e grupos recusam ambos. `kafka-acls --list` mostra as três políticas como ACLs com prefixo e literais.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta recursos. Para aplicá-las, cada broker e controller precisa do agente do Kafka. Veja o [agente do Kafka](/pt-br/external/kafka-agent/) para sua implantação e o que ele verifica.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do Kafka](/pt-br/external/kafka-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
