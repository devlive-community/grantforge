---
title: Redis
description: Instalar e configurar o plug-in de serviço do Redis para Redis e Valkey, conectar a um servidor, suas réplicas ou um cluster, entrar com um usuário ACL, com TLS se desejado, consultar chaves, canais e nós, e gerenciar quem pode ler e gravar quais chaves, usar quais canais e monitorar quais nós.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do Redis consulta chaves, canais e nós de implantações Redis e Valkey e gerencia políticas de acesso nos termos do próprio Redis: ler e gravar chaves por padrão, assinar e publicar em canais, e monitorar cada nó. O ID do plug-in e o tipo de serviço são ambos `redis`. O plug-in de serviço roda no servidor GrantForge; o [agente do Redis](/pt-br/external/redis-agent/), que roda ao lado da implantação, aplica as políticas com a ACL do próprio Redis em cada nó.

Os testes de integração Testcontainers do repositório cobrem um primário com uma réplica do Redis `7.2`, `7.4`, `8.2` e `8.10` e do Valkey `8.1` e `9.1`, e um cluster Redis `8.10`. Isso não certifica todas as outras versões.

## Instalação

A distribuição inclui o plug-in em `plugins/redis`. Confirme que `redis` está ativado em **Gestão da plataforma → Plug-ins**; reexamine o diretório de plug-ins após atualizá-lo. O plug-in conversa com o Redis pelo Jedis, em um class loader próprio.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e selecione Redis (`redis`).
2. Informe um ou mais nós. O restante de um cluster e as réplicas de um primário são encontrados a partir deles.
3. Informe o usuário de consulta, teste a conexão e salve o serviço. O teste entra e descreve a implantação: Redis ou Valkey e a versão, se é um único servidor, um primário com réplicas ou um cluster, e quem entrou.

O usuário de consulta permite que o GrantForge encontre recursos e não é o usuário que lê ou grava. Um teste de conexão bem-sucedido não significa que o acesso já siga as políticas.

| Configuração | Finalidade |
| --- | --- |
| `redis.nodes` | Obrigatória: os nós como `host:port`, como `redis1.example.com:6379`, separados por vírgulas e tentados em ordem; a porta é `6379` se não indicada |
| `username` | O usuário de consulta, um usuário ACL; vazio entra como o usuário padrão |
| `password` | Sua senha, armazenada criptografada |
| `tls.mode` | `disable` (padrão), `verify-ca` para verificar os certificados dos servidores, ou `verify-full` para verificar também seus nomes |
| `tls.ca.file` | TLS: caminho no **servidor GrantForge** dos certificados CA PEM com que os servidores são verificados. Vazio usa os da JVM |
| `tls.certificate.file` | TLS, para servidores que pedem certificados de cliente: caminho no **servidor GrantForge** do certificado de cliente PEM, que pode conter também sua chave |
| `tls.key.file` | Caminho no **servidor GrantForge** da chave PEM do certificado, quando tem um arquivo próprio |
| `tls.key.password` | A senha de uma chave criptografada, armazenada criptografada |
| `timeout` | Quanto podem levar a conexão e um comando, `10000` por padrão, no intervalo `1..600000` milissegundos |

Uma entrada recusada, um usuário de consulta sem uma permissão e nós inacessíveis são relatados como tais.

### O que o usuário de consulta vê

As chaves são encontradas com `SCAN` em cada primário, no máximo algumas rodadas para que uma consulta continue rápida não importa quantas chaves existam; os canais são os que têm assinantes agora. `SCAN` lista todas as chaves independentemente dos padrões de chaves do usuário, então o usuário de consulta não precisa de permissão para ler valores:

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## Recursos e tipos de acesso

| Nível | Correspondência | Tipos de acesso |
| --- | --- | --- |
| `key` | Curingas, diferencia maiúsculas. Uma chave ou um padrão de chaves | `read`, `write` |
| `channel` | Curingas, diferencia maiúsculas. Um canal ou um padrão de canais | `subscribe`, `publish` |
| `node` | Curingas, sem diferenciar maiúsculas. O endereço de um nó como `host:port` | `monitor` |

| Tipo de acesso | O que o Redis permite |
| --- | --- |
| `read` | Os comandos de `@read` na chave, `%R~` nela |
| `write` | Os comandos de `@write` na chave, `%W~` nela, que também a excluem |
| `subscribe` | `SUBSCRIBE`, `PSUBSCRIBE`, `SSUBSCRIBE` no canal, `&` |
| `publish` | `PUBLISH`, `SPUBLISH` no canal, `&` |
| `monitor` | `INFO`, `DBSIZE`, `LATENCY LATEST`, `LATENCY HISTORY`, `MEMORY STATS` |

Nos valores das políticas, `*` corresponde a qualquer texto e `?` a um caractere, como nos padrões do próprio Redis. O Redis concede chaves e canais por padrão e não pode listá-los ao agente, então o valor de uma política é concedido como um padrão próprio, onde as políticas decidem o usuário da mesma forma em todo ele. Um padrão dentro do qual as políticas decidem de outro modo, como `sales:*` quando uma política nega `sales:card:*`, é retido e informado; nomeie em vez disso chaves ou padrões que as políticas decidam da mesma forma. Valores com colchetes, barras invertidas, parênteses ou espaços nunca são concedidos.

Comandos que o Redis considera perigosos, como `FLUSHALL` e `KEYS`, nunca são dados, e `SCAN` e `RANDOMKEY`, que listam chaves independentemente dos padrões, só a um usuário que pode ler todas as chaves. Os padrões nomeiam chaves em todos os bancos de um nó, pois a ACL do Redis não tem bancos.

O Redis não filtra nada dentro de um valor nem mascara nada, então este tipo de serviço não oferece nenhum dos dois.

## Exemplo de políticas

Crie estas políticas ativadas em um serviço de dados:

| Recurso | Regra |
| --- | --- |
| `key=sales:*` | Permitir `read` ao grupo `analysts` |
| `key=sales:card:*` | Permitir `read` e `write` ao usuário `billing` |
| `key=cart:*` | Permitir `read` e `write` ao usuário `shop` |
| `channel=orders.*` | Permitir `subscribe` ao grupo `analysts`; permitir `publish` ao usuário `shop` |
| `node=*` | Permitir `monitor` ao usuário `dashboards` |

Depois que o agente aplicar o snapshot, os membros de `analysts` leem todas as chaves `sales:` e acompanham os canais de pedidos; `billing` lê e grava as chaves de cartões, um padrão dentro do qual nenhuma outra política decide diferente; `shop` mantém carrinhos e publica eventos de pedidos; e `dashboards` vê o estado de cada nó e nenhum dado.

## Aplicar as políticas

O plug-in de serviço gerencia as políticas e consulta recursos. Para aplicá-las é preciso um agente do Redis por implantação; veja [Agente do Redis](/pt-br/external/redis-agent/) para a implantação e as verificações.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Agente do Redis](/pt-br/external/redis-agent/)
- [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)
