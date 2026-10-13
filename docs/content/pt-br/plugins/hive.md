---
title: Apache Hive
description: Instale e configure o plug-in de serviço Hive, entre com senha ou Kerberos, consulte bancos, tabelas, colunas e UDFs e gerencie políticas de acesso, mascaramento e filtragem de linhas.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do Hive oferece para o HiveServer2 a consulta de bancos, tabelas, colunas e UDFs permanentes, além de políticas de acesso, mascaramento e filtragem de linhas, no modelo do serviço Hive do Apache Ranger. O ID do plug-in e o tipo de serviço são ambos `hive`. O plug-in de serviço roda no servidor GrantForge; o [agente do Hive](/pt-br/external/hive-agent/), instalado no HiveServer2, aplica as políticas.

Os testes de integração com Testcontainers do repositório cobrem o HiveServer2 `3.1.3`, `4.0.1` e `4.1.0`, incluindo um login Kerberos no `4.0.1`; isso não certifica as demais versões.

## Instalação

A distribuição inclui o plug-in em `plugins/hive`. Confirme em **Gestão da plataforma → Plug-ins** que `hive` está habilitado; examine novamente o diretório de plug-ins após atualizá-lo. O plug-in conversa com o HiveServer2 pelo próprio driver JDBC do Hive (`hive-jdbc` 4.0.1, standalone), em um carregador de classes próprio, e por isso funciona tanto com o HiveServer2 3.1 quanto com o 4.x.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione Apache Hive (`hive`).
2. Informe a URL JDBC do HiveServer2, como `jdbc:hive2://hiveserver2.example.com:10000/default`. A descoberta pelo ZooKeeper também funciona: `jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`.
3. Escolha como o usuário de consulta entra, informe-o, teste a conexão e salve o serviço. O teste entra e lista os bancos, como fazem as consultas de recursos.

O usuário de consulta serve apenas para o GrantForge descobrir recursos; ele é separado dos usuários que executam consultas. Um teste de conexão bem-sucedido não significa que as consultas já estejam sujeitas às políticas.

| Configuração | Finalidade |
| --- | --- |
| `jdbc.url` | URL do HiveServer2 obrigatória `jdbc:hive2://hosts/banco;variáveis de sessão`, com TLS (`;ssl=true;sslTrustStore=…`), o transporte HTTP ou, com Kerberos, o principal do servidor (`;principal=hive/_HOST@EXAMPLE.COM`) |
| `authentication` | `password` (padrão): um usuário com sua senha, para LDAP, PAM ou sem autenticação; `kerberos`: um principal com keytab ou senha |
| `username` | Usuário de consulta obrigatório; com Kerberos, o principal completo, como `grantforge@EXAMPLE.COM` |
| `password` | A senha do usuário ou, sem keytab, a senha Kerberos do principal; armazenada criptografada |
| `keytab` | Kerberos: caminho da keytab do principal, legível no **servidor GrantForge** |
| `kerberos.kdc` | Kerberos: KDCs do realm do principal, `host[:port]` separados por vírgulas; vazio usa o `krb5.conf` do servidor |
| `read.timeout.ms` | Tempo limite de leitura, padrão `30000`, intervalo `1..600000` |

A URL não pode levar credenciais nem nada que leia arquivos ou tokens no servidor GrantForge: `user`, `password`, `initFile`, `kerberosAuthType`, `delegationToken`, `storePasswordPath`, `jwt` e as variáveis `browser…` são recusados, independentemente de maiúsculas. `auth` só pode ser `noSasl`. Um serviço Kerberos precisa de `principal` na URL; um serviço com senha não pode ter.

### Kerberos

Com `kerberos`, o GrantForge autentica o principal por conta própria, com a keytab ou a senha, e abre a conexão como esse principal; nenhuma outra configuração Kerberos é necessária no servidor GrantForge. Os realms cujos KDCs são informados em `kerberos.kdc` são declarados para todo o servidor GrantForge, como faz o plug-in do HDFS, de modo que vários serviços Hive e HDFS podem usar realms diferentes lado a lado.

## Recursos e tipos de acesso

| Nível | Correspondência | Observações |
| --- | --- | --- |
| `database` | Curingas, sem diferenciar maiúsculas | As políticas podem terminar aqui |
| `table` | Curingas, sem diferenciar maiúsculas | Abaixo de um banco; as políticas podem terminar aqui |
| `column` | Curingas, sem diferenciar maiúsculas | Abaixo de uma tabela |
| `udf` | Curingas, sem diferenciar maiúsculas | Funções permanentes, abaixo de um banco |
| `url` | Caminhos, diferenciam maiúsculas, recursivos | Dados que as instruções leem ou gravam, como `hdfs://nn:8020/landing`; tipos de acesso `read` e `write` |
| `hiveservice` | Curingas | O próprio HiveServer2; tipo de acesso `serviceadmin` |
| `global` | Curingas | Funções temporárias e arquivos adicionados; tipo de acesso `tempudfadmin` |

Os tipos de acesso são os do Hive: `select`, `update`, `create`, `drop`, `alter`, `index`, `lock`, `read`, `write`, `repladmin`, `serviceadmin`, `tempudfadmin`, `refresh` e `all`. Como no Ranger, `all` inclui todos os tipos, exceto `tempudfadmin`, que permite a um usuário executar o código que quiser dentro do HiveServer2.

Nos valores das políticas, `*` corresponde a qualquer número de caracteres e `?` a um caractere. O editor de políticas sugere nomes por prefixo: bancos, tabelas e UDFs dos bancos que a política nomeia e colunas de suas tabelas.

## Mascaramento

As políticas de mascaramento se aplicam a colunas. Cada tipo de máscara é uma das funções de mascaramento do Hive, em que `{col}` representa a coluna:

| Tipo de máscara | Mostra |
| --- | --- |
| `mask` | Maiúsculas como `X`, minúsculas como `x` e dígitos como `n` |
| `mask_show_last_4` | Apenas os quatro últimos caracteres |
| `mask_show_first_4` | Apenas os quatro primeiros caracteres |
| `mask_hash` | Um hash SHA-256 |
| `mask_null` | `NULL` |
| `mask_none` | O valor inalterado |
| `mask_date_show_year` | 1º de janeiro do ano da data |
| `custom` | Uma expressão Hive própria, com `{col}` para a coluna, como `concat('****', substr({col}, -4))` |

Por exemplo, `mask_show_last_4` exibe `4111111111111111` como `xxxxxxxxxxxx1111`.

## Filtros de linhas

As políticas de filtragem de linhas se aplicam a tabelas. Um filtro é uma condição Hive sobre as colunas da tabela, como `region = 'EU'`: as consultas veem apenas as linhas que a atendem.

Máscaras e filtros de linhas não concedem acesso. Uma política de acesso separada deve permitir a consulta.

## Exemplo de políticas

Suponha que o Hive tenha uma tabela `sales.orders` com as colunas `id`, `region` e `card`. Crie três políticas habilitadas no mesmo serviço de dados para o usuário `alice`:

| Tipo de política | Recursos | Regra |
| --- | --- | --- |
| Acesso | `database=sales`, `table=orders`, `column=*` | Permitir `select` para o usuário `alice` |
| Mascaramento | `database=sales`, `table=orders`, `column=card` | Usar `mask_show_last_4` para o usuário `alice` |
| Filtro de linhas | `database=sales`, `table=orders` | Usar `region = 'EU'` para o usuário `alice` |

Assim que o agente aplica o novo snapshot, `alice` vê apenas as linhas EU de `sales.orders`, com cada cartão mascarado exceto os quatro últimos caracteres; `SHOW DATABASES` lista `sales` para ela, e os outros bancos a recusam.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta os recursos. Para aplicá-las, incluindo máscaras e filtros de linhas, cada HiveServer2 precisa do agente do Hive. A implantação e as verificações estão no [agente do Hive](/pt-br/external/hive-agent/).

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do Hive](/pt-br/external/hive-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
