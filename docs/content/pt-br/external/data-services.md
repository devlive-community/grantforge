---
title: Serviços de dados, políticas e agentes
description: "Gerencie com plug-ins as permissões de sistemas externos como HDFS e Hive: serviços de dados, políticas de acesso, agentes e auditoria de acessos."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O grupo "Permissões de dados" gerencia as permissões dos sistemas de dados externos ao GrantForge, com uma arquitetura semelhante à do Apache Ranger: os plug-ins definem os tipos de serviço, os administradores escrevem as políticas no console e os agentes implantados no sistema de destino baixam as políticas e decidem o acesso localmente.

> [!NOTE]
> A versão atual oferece o framework de plug-ins, o editor de políticas genérico, a distribuição de políticas e a auditoria de acessos, o tipo de serviço `hdfs` e o agente NameNode do Hadoop 3.5.0, além do plug-in de exemplo (`example`). O plug-in do Hive e os agentes de outras versões do Hadoop ainda estão em desenvolvimento.

```mermaid
flowchart LR
  C[Console: serviços de dados e políticas] --> S[Servidor GrantForge]
  S -->|instantâneo de políticas assinado| A[Agente (dentro de HDFS / Hive)]
  A -->|heartbeat e auditoria de acessos| S
  U[O usuário acessa os dados] --> A
```

## Plug-ins

**Gestão da plataforma → Plug-ins** lista os plug-ins de tipos de serviço carregados. Os plug-ins integrados vêm com o servidor; os demais plug-ins devem ser colocados no diretório `plugins` e, em seguida, clica-se em "Verificar novamente"; cada plug-in é carregado de forma independente e, se falhar, apenas ele mesmo é desativado. O desenvolvimento de plug-ins é descrito em [Plug-ins e tipos de serviço](/pt-br/develop/plugins/).

![Plug-ins](/screenshots/plugins.png)

## HDFS

O pacote de lançamento inclui o plug-in de HDFS (`plugins/hdfs`), com o tipo de serviço `hdfs`, equivalente ao serviço HDFS do Apache Ranger:

- os recursos têm um único nível, `path`, comparado por caminho: `/data/sales` corresponde a si mesmo e, ao marcar "recursivo", também a todos os arquivos e diretórios abaixo dele; há suporte a exclusões;
- os tipos de acesso `read`, `write` e `execute` correspondem aos bits de permissão do HDFS;
- o plug-in se conecta ao cluster com o próprio cliente do Hadoop; o teste de conexão verifica se o diretório de consulta existe e se seu conteúdo pode ser listado. Ao escrever uma política, digitar um caminho lista os subdiretórios e arquivos desse diretório, com os diretórios antes dos arquivos;
- o plug-in do servidor cuida da gestão e das consultas; para que as políticas restrinjam o acesso ao HDFS, também é necessário implantar o [agente NameNode](/pt-br/external/hdfs-agent/).

| Configuração | Descrição |
| --- | --- |
| `username` | Usuário usado para consultar diretórios; com Kerberos, o principal, por exemplo `grantforge@EXAMPLE.COM` |
| `password` / `keytab` | Com Kerberos, um dos dois: a senha do principal ou o caminho do arquivo keytab no servidor GrantForge |
| `fs.default.name` | `hdfs://namenode:8020`, `hdfs://nameservice1` em alta disponibilidade ou `webhdfs://namenode:9870` |
| `hadoop.security.authentication` | `simple` ou `kerberos` |
| `hadoop.security.authorization`, `hadoop.security.auth_to_local` | Igual ao do core-site.xml do cluster |
| `dfs.namenode.kerberos.principal` e afins | Principais do NameNode, do DataNode e do Secondary NameNode, por exemplo `nn/_HOST@EXAMPLE.COM` |
| `hadoop.rpc.protection` | `authentication`, `integrity` ou `privacy`, igual ao do cluster |
| Configuração adicional do Hadoop | Um par `key=value` por linha, para alta disponibilidade e outras configurações, por exemplo `dfs.nameservices=nameservice1`, `dfs.ha.namenodes.nameservice1=nn1,nn2`, `dfs.namenode.rpc-address.nameservice1.nn1=nn1:8020`, `dfs.client.failover.proxy.provider.nameservice1=org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider` |
| `lookup.path` | Diretório de consulta, `/` por padrão; por exemplo, ao definir `/data`, uma entrada vazia lista o conteúdo de `/data` e entradas relativas são completadas a partir daí. Útil em clusters em que o usuário de consulta não tem permissão para listar o diretório raiz |
| `lookup.max.entries` | Número máximo de entradas analisadas em uma consulta de diretório, `10000` por padrão, intervalo `1..100000`; ao ultrapassar o limite é retornado um erro, para evitar omitir candidatos silenciosamente |

A configuração adicional do Hadoop prevalece sobre as configurações de conexão de mesmo nome, e tanto a validação da configuração quanto o login usam os valores prevalecentes. `fs.defaultFS` e `fs.default.name` são aliases, portanto apenas um deles pode ser definido na configuração adicional; chaves repetidas, endereços que não são do cluster e configurações Kerberos sem credenciais são rejeitadas ao salvar. No endereço do cluster deve constar apenas a URI do cluster; os subdiretórios a consultar devem ir em `lookup.path`.

`lookup.path` limita o escopo de navegação dos caminhos candidatos e não substitui o controle de acesso próprio do HDFS; links simbólicos e montagens ViewFS continuam seguindo a configuração do cluster. Na entrada, a `/` inicial pode ser omitida, `/` e `.` repetidos são permitidos, e `..` e caminhos absolutos fora do escopo são rejeitados. Um diretório inexistente retorna candidatos vazios; permissões insuficientes e falhas de conexão exibem erro.

Ao usar Kerberos, o servidor GrantForge precisa conseguir localizar o KDC: configure `/etc/krb5.conf` ou indique-o com `-Djava.security.krb5.conf=`.

## Serviços de dados

**Permissões de dados → Serviços de dados**: um serviço é uma instância de um sistema externo cujas permissões o GrantForge gerencia, por exemplo um cluster HDFS. Ao adicionar um serviço, escolha o tipo de serviço e preencha as informações de conexão conforme os campos de configuração definidos pelo plug-in; antes, é possível **testar a conexão**. Configurações sensíveis, como senhas, são salvas criptografadas e não são mais exibidas após salvar.

![Serviços de dados](/screenshots/services.png)

## Políticas

**Permissões de dados → Políticas** decide quem pode fazer o quê sobre os recursos de um serviço de dados:

- as **políticas de acesso** permitem ou negam o acesso;
- as **políticas de mascaramento** ocultam campos;
- as **políticas de filtragem de linhas** liberam apenas algumas linhas.

A hierarquia de recursos (em Hive, bancos, tabelas e colunas, por exemplo), os tipos de acesso (como select, update) e as condições vêm do plug-in do tipo de serviço; ao preencher o recurso, é possível pesquisar recursos que existem de fato no sistema de destino. As políticas têm como alvo usuários, grupos de usuários ou papéis.

Níveis navegáveis, como caminhos do HDFS, têm um botão **Navegar**: abra diretórios nível a nível, veja dono, grupo e permissões e escolha vários arquivos ou diretórios de uma vez. Se uma busca ou a navegação falhar, o motivo é mostrado (sem permissão, inacessível ou diretório grande demais) e você pode tentar de novo.

![Políticas](/screenshots/policies.png)

## Agentes

**Permissões de dados → Agentes**: os agentes são implantados dentro do sistema de destino e, com um token, enviam heartbeats periodicamente e baixam um instantâneo de políticas assinado, decidindo o acesso localmente. Aqui são emitidos os tokens de agente (exibidos apenas uma vez) e é possível verificar se cada agente já está usando as políticas mais recentes.

![Agentes](/screenshots/agents.png)

## Auditoria de acessos

**Permissões de dados → Auditoria de acessos**: cada decisão de acesso relatada por um agente — quem fez o quê, quando, de onde e sobre qual recurso, se o acesso foi permitido ou negado e qual política o decidiu. Os registros são mantidos por 90 dias por padrão.

![Auditoria de acessos](/screenshots/access-audit.png)
