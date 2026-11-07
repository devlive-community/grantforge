---
title: MongoDB
description: Instalar e configurar o plug-in de serviço do MongoDB, conectar a um replica set ou cluster fragmentado, entrar com SCRAM, certificado de cliente, LDAP ou Kerberos, consultar bancos de dados e coleções, e gerenciar quem pode ler, gravar e administrar o quê, coleção por coleção.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do MongoDB consulta bancos de dados e coleções e gerencia políticas de acesso nos termos do próprio MongoDB: leitura, change streams, inserção, atualização e remoção, índices, opções de coleção, ignorar a validação de documentos e excluir, por coleção; criar coleções e excluir o banco, por banco de dados; listar todos os bancos e monitorar, para a implantação como um todo. O ID do plug-in e o tipo de serviço são ambos `mongodb`. O plug-in de serviço roda no servidor GrantForge; o [agente do MongoDB](/pt-br/external/mongodb-agent/), que roda ao lado da implantação, aplica as políticas com os papéis do próprio MongoDB.

Os testes de integração Testcontainers do repositório cobrem replica sets do MongoDB `6.0`, `7.0`, `8.0` e `9.0`, e Kerberos no Percona Server for MongoDB `8.0`. Isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/mongodb`. Confirme que `mongodb` está ativado em **Gestão da plataforma → Plug-ins**; reexamine o diretório de plug-ins após atualizá-lo. O plug-in conversa com o MongoDB pelo driver Java oficial do MongoDB, em um class loader próprio.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione MongoDB (`mongodb`).
2. Informe a string de conexão da implantação, como qualquer cliente MongoDB a aceita.
3. Informe como o usuário de consulta entra, teste a conexão e salve o serviço. O teste entra e descreve a implantação: a versão do MongoDB, se é um replica set, um cluster fragmentado ou um único servidor, quem entrou e quantos bancos esse usuário vê.

O usuário de consulta permite que o GrantForge encontre recursos e não é o usuário que lê ou grava. Um teste de conexão bem-sucedido não significa que o acesso já siga as políticas.

| Configuração | Finalidade |
| --- | --- |
| `mongodb.uri` | Obrigatória: a string de conexão, como `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` ou `mongodb+srv://cluster.example.com/`. Opções como `tls=true` ficam aqui; credenciais não, e uma string que as contenha é recusada |
| `auth.mechanism` | `SCRAM-SHA-256` (padrão) ou `SCRAM-SHA-1` para usuários com senha, `MONGODB-X509` para um certificado de cliente, `PLAIN` para LDAP, `GSSAPI` para Kerberos |
| `username` | O usuário de consulta. Com `GSSAPI`, o principal; com `MONGODB-X509`, vazio o obtém do certificado |
| `password` | A senha do usuário de consulta, armazenada criptografada |
| `auth.source` | SCRAM: o banco em que o usuário está definido, `admin` por padrão. Os outros mecanismos usam `$external` |
| `tls.ca.file` | Com `tls=true`: caminho no **servidor GrantForge** dos certificados CA PEM com que os servidores são verificados. Vazio usa os da JVM |
| `tls.certificate.key.file` | Com `tls=true`: caminho no **servidor GrantForge** de um arquivo PEM com o certificado de cliente e sua chave, como o `mongosh` o aceita |
| `tls.certificate.key.password` | A senha de uma chave criptografada, armazenada criptografada |
| `kerberos.keytab` | `GSSAPI`: caminho no **servidor GrantForge** do keytab do principal. Vazio usa a senha |
| `kerberos.kdc` | `GSSAPI`: os KDCs do realm, separados por vírgulas. Vazio usa o `krb5.conf` do servidor |
| `kerberos.service.name` | `GSSAPI`: o nome de serviço do principal dos servidores, `mongodb` por padrão |
| `timeout` | Quanto podem levar encontrar um servidor, conectar e um comando, `30000` por padrão, no intervalo `1..600000` milissegundos |

Uma entrada recusada, um usuário de consulta sem um privilégio e uma implantação inacessível são relatados como tais.

### O que o usuário de consulta vê

O MongoDB lista os bancos e as coleções em que um usuário tem algum privilégio, então uma consulta mostra o que o usuário de consulta pode ver. Dê a ele `read` naquilo para que as políticas são escritas, ou `readAnyDatabase` para tudo. Os bancos do próprio MongoDB, `admin`, `config` e `local`, e as coleções de sistema são omitidos: as políticas não os decidem.

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `deployment` | Curingas, diferencia maiúsculas. O nome do replica set, `sharded` para um cluster fragmentado, `standalone` para um único servidor | `list_databases`, `monitor` |
| `database` | Curingas, diferencia maiúsculas | `create`, `drop_database` |
| `collection` | Curingas, diferencia maiúsculas. Uma coleção ou visão de um banco | `read`, `change_stream`, `insert`, `update`, `remove`, `create_index`, `drop_index`, `alter`, `bypass_validation`, `drop` |

| Tipo de acesso | Ações do MongoDB |
| --- | --- |
| `read` | `find`, `listIndexes`, `collStats`, `killCursors`; em um banco inteiro também `listCollections` e `dbStats` |
| `change_stream` | `changeStream`; para abrir um, o MongoDB também exige `find` |
| `insert`, `update`, `remove` | `insert`, `update`, `remove` |
| `create_index`, `drop_index` | `createIndex`, `dropIndex` |
| `alter` | `collMod`, `validate`, `convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | `createCollection` no banco: criar coleções e visões |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`: o nome de cada banco, não só daqueles em que o usuário tem um privilégio |
| `monitor` | `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `inprog` |

Nos valores das políticas, `*` corresponde a qualquer texto e `?` a um caractere. O editor de políticas sugere a implantação, os bancos e as coleções dos bancos que uma política nomeia.

O privilégio do MongoDB de excluir um banco é o privilégio de excluir cada coleção dele, então o agente concede `drop_database` só onde as políticas também permitem excluir cada coleção. Caso contrário, retém o privilégio e o informa, em vez de dar mais do que as políticas permitem. A administração de usuários e papéis não é tipo de acesso, e nenhuma política a dá.

### Linhas e campos

O MongoDB não filtra linhas nem mascara campos por papel, então este tipo de serviço não oferece nenhum dos dois. Uma visão dá uma leitura restrita: o MongoDB verifica o leitor de uma visão apenas em relação à visão, de modo que uma política que permite `read` em uma visão, e nada em sua origem, mostra ao usuário o que a visão mostra e nada mais.

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## Exemplo de políticas

Suponha que a implantação tenha os bancos `sales` e `hr`. Crie estas políticas ativadas em um serviço de dados:

| Recurso | Regra |
| --- | --- |
| `database=sales`, `collection=*` | Permitir `read` ao grupo `analysts` |
| `database=sales`, `collection=orders_public` | Permitir `read` ao usuário `support` |
| `database=sales`, `collection=orders` | Permitir `insert` e `update` ao usuário `ingest`; negar `read` ao grupo `contractors` |
| `database=team_*` | Permitir `create` ao grupo `data-team` |
| `database=team_*`, `collection=*` | Permitir `read`, `insert`, `update`, `remove`, `create_index` e `drop` ao grupo `data-team` |
| `deployment=*` | Permitir `monitor` ao usuário `dashboards` |

Depois que o agente aplicar o snapshot, os membros de `analysts` leem todo o `sales`, também as coleções criadas depois; `support` lê a visão sem sua origem; `ingest` grava em `orders` sem lê-lo; os membros de `data-team` trabalham livremente nos bancos `team_`; e `dashboards` vê o estado do servidor e nenhum dado.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta recursos. Para aplicá-las é preciso um agente do MongoDB por implantação; veja [Agente do MongoDB](/pt-br/external/mongodb-agent/) para a implantação e as verificações.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do MongoDB](/pt-br/external/mongodb-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
