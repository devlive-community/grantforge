---
title: Trino
description: Instalar e configurar o plug-in de serviço do Trino, entrar com senha, token web JSON ou Kerberos sobre TLS, consultar catálogos, esquemas, tabelas, colunas e propriedades de sessão, e gerenciar políticas de acesso, filtragem de linhas e mascaramento nos termos do Trino.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do Trino consulta catálogos, esquemas, tabelas, colunas e propriedades de sessão, e gerencia políticas de acesso, filtragem de linhas e mascaramento sobre o que o Trino verifica: dados, rotinas, propriedades de sessão, usuários e as informações do sistema. O ID do plug-in e o tipo de serviço são ambos `trino`. O plug-in de serviço é executado no servidor GrantForge; o [agente do Trino](/pt-br/external/trino-agent/), instalado no coordinator, aplica as políticas.

Os testes de integração com Testcontainers do repositório cobrem o Trino `470`, `476`, `479` e `484` e, no `470` e no `484`, HTTPS com arquivo de senhas e tokens web JSON, além de Kerberos. Isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/trino`. Confirme que `trino` está habilitado em **Gestão da plataforma → Plug-ins**; verifique novamente o diretório de plug-ins depois de atualizá-lo. O plug-in fala com o coordinator pelo driver JDBC do próprio Trino (`trino-jdbc` 484), em um carregador de classes próprio, que fala com todas as versões do Trino acima.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione Trino (`trino`).
2. Informe a URL JDBC do coordinator, como `jdbc:trino://trino.example.com:8443`.
3. Escolha como o usuário de consulta entra, informe-o, teste a conexão e salve o serviço. O teste entra e descreve o coordinator: sua versão e quantos catálogos o usuário vê.

As configurações usam os nomes do próprio driver, então se leem como a documentação JDBC do Trino. O usuário de consulta permite que o GrantForge descubra recursos; ele é separado dos usuários que consultam. Um teste de conexão bem-sucedido não significa que as consultas já estão sujeitas a políticas.

| Configuração | Finalidade |
| --- | --- |
| `jdbc.url` | Obrigatória: `jdbc:trino://host:port`, opcionalmente com catálogo e esquema e com propriedades do driver que não sejam credenciais, como `?SSL=true` |
| `authentication` | `none` (padrão): apenas o nome de usuário; `password`; `jwt`: um token web JSON; `kerberos`: um principal com keytab ou senha |
| `user` | Usuário de consulta obrigatório; com Kerberos, o principal completo, como `grantforge@EXAMPLE.COM` |
| `password` | A senha do usuário, ou com Kerberos a do principal, se não houver keytab; armazenada criptografada |
| `accessToken` | `jwt`: o token que o coordinator aceita; armazenado criptografado |
| `keytab` | Kerberos: caminho do keytab do principal, legível no **servidor GrantForge** |
| `kerberos.kdc` | Kerberos: KDCs do realm do principal, `host[:port]` separados por vírgulas; vazio usa o `krb5.conf` do servidor |
| `KerberosRemoteServiceName` | Kerberos: a primeira parte do principal do coordinator, padrão `trino` |
| `KerberosUseCanonicalHostname` | Kerberos: se o principal do coordinator usa o nome canônico do host da URL, padrão `true`; desative atrás de um balanceador de carga ou alias cujo nome o principal carrega |
| `SSL` | Se usa TLS, padrão `false`; qualquer entrada exceto `none` precisa dele, pois o Trino só autentica usuários sobre TLS |
| `SSLTrustStorePath`, `SSLTrustStorePassword`, `SSLTrustStoreType` | TLS: o truststore que verifica o coordinator, sua senha e tipo (`JKS` ou `PKCS12`); vazio confia no que o Java confia |
| `SSLVerification` | TLS: `FULL` (padrão) verifica o certificado e o nome do host, `CA` só o certificado, `NONE` nada e não aceita truststore |
| `timeout` | Tempo limite das requisições em milissegundos, padrão `30000`, intervalo `1..600000` |

O serviço é exatamente o que seus campos dizem. A URL não pode levar credenciais nem nada que entre de outra forma: `user`, `password`, `accessToken`, `sessionUser`, `extraCredentials`, autenticação externa e OAuth 2.0, arquivos e delegação Kerberos e senhas de keystore e truststore são recusados ali. Campos de outra forma de entrada também são recusados, como um token com autenticação por senha.

Não se fala com um coordinator cujo certificado não pode ser verificado: o teste o informa como inacessível. Uma entrada recusada, permissões ausentes e um coordinator inacessível são informados como tais.

### Kerberos

Com `kerberos`, o GrantForge autentica o principal, pelo keytab ou com a senha, e entrega ao driver a credencial dessa autenticação; nada mais no servidor GrantForge precisa de configurações Kerberos. Os realms cujos KDCs são informados em `kerberos.kdc` são declarados para todo o servidor GrantForge, como fazem os plug-ins do HDFS, Hive, Kafka e HBase, de modo que vários serviços podem usar realms diferentes lado a lado. O usuário da sessão é o nome do principal antes do realm, como o Trino mapeia principais por padrão.

### O que o usuário de consulta vê

Quando o [agente do Trino](/pt-br/external/trino-agent/) aplica as políticas, as listagens omitem o que o usuário de consulta não pode usar: dê a ele uma política que permita `select` no catálogo `*`, ou nos catálogos cujos recursos o editor de políticas deve sugerir.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `catalog` | Curingas, sem diferenciar maiúsculas | `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `all` |
| `schema` | Curingas, sem diferenciar maiúsculas; sob um catálogo | Como `catalog` |
| `table` | Curingas, sem diferenciar maiúsculas; sob um esquema; tabelas, views e views materializadas | Como `catalog` |
| `column` | Curingas, sem diferenciar maiúsculas; sob uma tabela | `select`, `update` |
| `function` | Curingas, sem diferenciar maiúsculas; sob um esquema | `execute`, `create_function`, `drop_function` |
| `procedure` | Curingas, sem diferenciar maiúsculas; sob um esquema | `execute` |
| `session_property` | Curingas, sem diferenciar maiúsculas; sob um catálogo, nomeada sem ele | `set` |
| `system_session_property` | Curingas, sem diferenciar maiúsculas | `set` |
| `user` | Curingas, diferenciando maiúsculas | `impersonate`, `view_query`, `kill_query` |
| `system_information` | Seu único valor, `*` | `read`, `write` |

`all` implica `select`, `insert`, `update`, `delete`, `create`, `drop` e `alter`.

Uma permissão em um catálogo, esquema ou tabela cobre tudo o que está dentro: `select` no esquema `sales` lê cada uma de suas tabelas, `create` em um catálogo cria esquemas e tabelas nele. Uma verificação é decidida em cada escopo que nomeia, e uma negação em qualquer um deles a recusa; assim, uma política pode permitir um esquema e negar uma coluna de uma de suas tabelas. Funções, procedimentos e propriedades de sessão são concedidos pelo nome, com `*` para todos de um esquema ou catálogo, e funções têm tipos de acesso próprios, de modo que uma concessão sobre rotinas nunca vale como uma sobre tabelas.

Nos valores de política, `*` corresponde a qualquer quantidade de caracteres e `?` a um. O editor de políticas sugere catálogos, esquemas dos catálogos que uma política nomeia, tabelas de seus esquemas, colunas de suas tabelas e propriedades de sessão de seus catálogos e do sistema; funções, procedimentos e usuários não são sugeridos.

## Filtros de linhas e máscaras

As políticas de filtragem de linhas em uma tabela, e de mascaramento em uma coluna, são SQL do Trino, que o coordinator aplica a cada consulta que as lê. Um filtro de linhas é uma condição sobre as colunas da tabela, como `region = 'EU'`; os nomes nele são resolvidos no catálogo e no esquema da tabela.

| Tipo de máscara | Mostra |
| --- | --- |
| `mask` | Letras como `X` ou `x`, dígitos como `n` |
| `mask_show_last_4`, `mask_show_first_4` | Apenas os quatro últimos ou primeiros caracteres |
| `mask_hash` | O SHA-256 do valor, em hexadecimal |
| `mask_null` | `NULL` |
| `mask_none` | O valor, sem máscara |
| `mask_date_show_year` | Apenas o ano de uma data ou timestamp |
| `custom` | Uma expressão SQL do Trino da política, com `{col}` para a coluna |

O agente ajusta cada máscara ao tipo da coluna, para que uma máscara nunca quebre a consulta que lê a coluna: veja o [agente do Trino](/pt-br/external/trino-agent/).

## Exemplo de políticas

Suponha que o catálogo `hive` tenha um esquema `sales` com as tabelas `orders`, cujas colunas incluem `region`, `customer` e `card`, e `returns`. Crie estas políticas habilitadas no mesmo serviço de dados:

| Política | Recursos | Regra |
| --- | --- | --- |
| Acesso | `catalog=hive`, `schema=sales` | Permitir `select` para o grupo `analysts` |
| Acesso | `catalog=hive`, `schema=sales`, `table=orders`, `column=card` | Negar `select` para o grupo `analysts` |
| Acesso | `catalog=hive`, `schema=sales`, `table=returns` | Permitir `insert` para o usuário `alice` |
| Filtro de linhas | `catalog=hive`, `schema=sales`, `table=orders` | `region = 'EU'` para o usuário `alice` |
| Mascaramento | `catalog=hive`, `schema=sales`, `table=orders`, `column=customer` | `mask_hash` para o grupo `analysts` |
| Acesso | `catalog=system`, `schema=runtime` | Permitir `select` para o usuário `alice` |
| Acesso | `user=bob` | Permitir `view_query` para o usuário `alice` |

Quando o agente aplica o novo snapshot, os membros de `analysts` leem todas as tabelas de `sales` exceto `orders.card`, e veem `customer` com hash; `alice` vê apenas os pedidos europeus, escreve em `returns` e vê as consultas de Bob em `system.runtime.queries` ao lado das suas.

## Aplicar as políticas

O plug-in de serviço gerencia políticas e consulta recursos. Aplicá-las requer o agente do Trino no coordinator. Veja o [agente do Trino](/pt-br/external/trino-agent/) para a implantação e o que ele verifica.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do Trino](/pt-br/external/trino-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
