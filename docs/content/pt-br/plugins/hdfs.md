---
title: Apache Hadoop HDFS
description: Instalar e configurar o plug-in HDFS, navegar por diretórios e gerenciar políticas de acesso a caminhos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in Apache Hadoop HDFS oferece conexões a clusters, consulta de caminhos e gestão de políticas no GrantForge. O ID do plug-in e o tipo de serviço são ambos `hdfs`.

## Instalação

A distribuição inclui o plug-in em `plugins/hdfs`. Confirme que `hdfs` está habilitado em **Gestão da plataforma → Plug-ins**; verifique novamente o diretório após atualizar o plug-in.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione HDFS (`hdfs`).
2. Informe a URI do cluster e o usuário de consulta. O Hadoop 2.x usa `webhdfs://namenode:50070`; o 3.x pode usar `hdfs://namenode:8020` ou `webhdfs://namenode:9870`. Para HTTPS, use `swebhdfs://` com a porta real do cluster.
3. Defina o diretório de consulta e teste a conexão: o diretório deve existir e permitir a listagem. Depois salve o serviço.

| Configuração | Finalidade |
| --- | --- |
| `fs.default.name` | URI obrigatória do cluster, sem subdiretório, credenciais ou parâmetros; HA pode usar `hdfs://nameservice1` com as propriedades adicionais correspondentes |
| `username` | Usuário de consulta obrigatório; com Kerberos, um principal como `grantforge@EXAMPLE.COM` |
| `hadoop.security.authentication` | Padrão `simple`; selecione `kerberos` para um cluster Kerberos |
| `hadoop.security.authorization` | Se o Hadoop verifica permissões, padrão `false`; compatível com o core-site.xml do cluster |
| `hadoop.security.auth_to_local` | Regras de mapeamento de principals Kerberos para nomes de usuário, compatíveis com o core-site.xml do cluster |
| `password` / `keytab` | Senha Kerberos ou caminho de um arquivo keytab no servidor GrantForge |
| `dfs.namenode.kerberos.principal`, `dfs.datanode.kerberos.principal`, `dfs.secondary.namenode.kerberos.principal` | Principals dos componentes do cluster com Kerberos, como `nn/_HOST@EXAMPLE.COM`, compatíveis com a configuração do cluster |
| `lookup.path` | Diretório inicial de consulta e navegação, padrão `/`, por exemplo `/data`; a navegação fica dentro dele |
| `lookup.max.entries` | Limite para varreduras completas, padrão `10000`, intervalo `1..100000` |
| `hadoop.config` | Um `key=value` por linha para HA e outras propriedades Hadoop; sobrescreve configurações de conexão de mesmo nome |
| `hadoop.rpc.protection` | `authentication`, `integrity` ou `privacy`, conforme o cluster |
| `ssl.client.truststore.location` | Caminho no servidor GrantForge do truststore que verifica os certificados dos NameNodes `swebhdfs://`; vazio confia no que o Java do servidor confia |
| `ssl.client.truststore.password` | A senha do truststore, quando protegido; guardada criptografada |
| `ssl.client.truststore.type` | `jks` (padrão) ou `pkcs12` |

Com a extensão de atributos NameNode habilitada no Hadoop 2.7.7, usuários comuns que consultam o caminho raiz `/` acionam uma `NullPointerException` upstream confirmada; defina `lookup.path` como um diretório existente, por exemplo `/data` (veja o [guia do agente](/pt-br/external/hdfs-agent/)).

O Kerberos também exige KDC acessível, o `krb5.conf` do servidor e regras `hadoop.security.auth_to_local` e principals de serviço compatíveis. A conta de consulta obtém metadados dos diretórios. Testado contra um cluster Kerberos do Hadoop 3.5.0: buscas e navegação por RPC (keytab ou senha) e por swebhdfs (truststore próprio do serviço e SPNEGO); credenciais erradas, truststore ausente e um cliente simple falham. As demais versões ainda não foram verificadas.

Com Kerberos, o GrantForge reaproveita um login entre buscas em vez de consultar o KDC toda vez: o Hadoop renova um login por keytab quando o ticket está para vencer, e um login por senha é refeito quando resta menos de um quinto da vida do ticket (e pelo menos um minuto); uma senha trocada ou um keytab atualizado faz login de novo. Cada serviço usa o próprio truststore, que um `ssl-client.xml` no classpath do servidor não substitui.

A configuração é validada ao salvar: em `hadoop.config`, `fs.defaultFS` e `fs.default.name` são alias, portanto configure apenas um; a URI do cluster não deve conter credenciais, caminho, consulta ou fragmento; `kerberos` exige `password` ou `keytab`; `lookup.path` deve ser um caminho absoluto sem `..`; `lookup.max.entries` deve estar entre `1` e `100000`. As propriedades adicionais sobrescrevem configurações de conexão de mesmo nome, e tanto a validação quanto o login usam os valores sobrescritos.

## Navegar por caminhos

Selecione o serviço em **Permissões de dados → Políticas** e use **Navegar** ao lado de `path`.

- Abra diretórios, navegue pelo caminho ou volte ao diretório pai e carregue as próximas páginas quando necessário.
- Consulte indicadores de arquivo/diretório, proprietário, grupo, permissões, tamanho e data de modificação.
- Selecione vários arquivos ou diretórios, ou o diretório atual, para adicionar à política; digitar caminhos continua oferecendo sugestões.

RPC e endpoints WebHDFS com listagens em lote usam paginação nativa. Endpoints antigos leem diretórios dentro do limite de varredura e retornam erro ao ultrapassá-lo. Falhas de permissão, autenticação ou conexão mostram o motivo e permitem tentar novamente.

A entrada pode omitir a `/` inicial, `/` e `.` repetidos são permitidos, e `..` e caminhos absolutos fora de `lookup.path` são rejeitados; um diretório inexistente não retorna candidatos. A navegação não substitui o controle de acesso próprio do HDFS; links simbólicos e montagens ViewFS seguem a configuração do cluster.

## Aplicar políticas

O único nível de recurso é `path`, com acessos `read`, `write` e `execute`. Políticas de caminhos aceitam recursão e exclusões.

O plug-in do servidor gerencia e consulta recursos. A aplicação das políticas também exige um agente NameNode compatível com a versão Hadoop; os usuários precisam atender às permissões nativas HDFS e às políticas GrantForge. Os agentes numerados cobrem 2.7, 2.10, 3.2, 3.3, 3.4 e 3.5. Veja no guia do agente as combinações verificadas, a cobertura de autenticação e HA e as limitações do superusuário.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente NameNode do Apache Hadoop HDFS](/pt-br/external/hdfs-agent/)
- [Desenvolvimento de plug-ins e tipos de serviço](/pt-br/develop/plugins/)
