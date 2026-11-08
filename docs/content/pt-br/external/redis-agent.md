---
title: Agente do Redis
description: Executar o agente do Redis ao lado de uma implantação Redis ou Valkey para manter os seletores ACL de cada usuário em cada nó alinhados às políticas do GrantForge, substituídos em um comando, perguntar ao Redis com ACL DRYRUN o que as permissões fora do GrantForge permitem, e relatar recusas a partir dos logs ACL dos nós.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente do Redis é um serviço ao lado da implantação. Ele mantém a ACL do próprio Redis alinhada às políticas assinadas de um serviço de dados `redis` em cada nó, inclusive nas réplicas e em cada nó de um cluster, pois o Redis mantém uma ACL em cada um. O Redis então verifica cada comando por conta própria, e o agente não fica no caminho de nenhum cliente. A distribuição o fornece como o JAR executável `agents/redis/grantforge-agent-redis-<version>.jar`; ele precisa do Java 17 ou posterior.

Os testes de integração Testcontainers do repositório executam o agente, e o JAR como é distribuído, em um primário com uma réplica do Redis `7.2`, `7.4`, `8.2` e `8.10` e do Valkey `8.1` e `9.1`, e em um cluster Redis `8.10`. Isso não certifica todas as outras versões; o agente recusa Redis anterior ao `7.2`, cujo log ACL não numera as entradas. Para o plug-in de serviço, veja [Redis](/pt-br/plugins/redis/).

## Como funciona

- **Os seletores são do GrantForge, as permissões raiz do operador.** O Redis permite um comando quando as permissões raiz de um usuário ou um de seus seletores o permitem. O agente mantém todos os seletores dos usuários de que cuida e nunca toca nas permissões raiz nem nas senhas deles: um seletor contém as chaves que o usuário pode ler, gravar ou ambos, um os canais que pode assinar, um aqueles em que pode publicar, e nos nós que pode monitorar, mais um.
- **Uma mudança acontece em um só comando.** `ACL SETUSER user clearselectors (…)` substitui de uma vez todos os seletores de um usuário em um nó, de modo que um usuário nunca tem os antigos e os novos, nem nenhum; seletores alterados à mão são corrigidos da mesma forma na sincronização seguinte.
- **Padrões, concedidos só onde valem.** Um padrão de chaves ou canais de uma política é concedido como padrão próprio do Redis onde as políticas decidem o usuário da mesma forma em todo ele; o agente corta o padrão ao longo de cada política que o sobrepõe e decide cada parte, de modo que a política de outro usuário não o retém. Um padrão dentro do qual as políticas decidem de outro modo é retido e registrado uma vez.
- **Nada perigoso, nada de listar chaves alheias.** Os seletores nunca dão o que o Redis considera perigoso, e dão `SCAN` e `RANDOMKEY` só a um usuário que pode ler todas as chaves.
- **Cada nó, salvo onde for possível.** O agente encontra as réplicas e os nós do cluster a partir dos nós indicados e alinha cada um. Um nó que guarda sua ACL em um arquivo a salva com `ACL SAVE`; um que não guarda perde os seletores ao reiniciar e os recebe de novo na sincronização seguinte.
- **Pergunta-se ao Redis o que ele permite.** As permissões raiz decidem ao lado dos seletores do agente. Em vez de ler suas regras, o agente pergunta ao Redis com `ACL DRYRUN` se um usuário pode executar um comando em uma chave, canal ou nó que uma política lhe nega, e avisa uma vez cada caso, nomeando o usuário, o acesso e a política. Também avisa dos usuários que nenhum seletor limita, com todos os comandos em todas as chaves, e dos usuários como os quais qualquer pessoa pode entrar sem senha.
- **As recusas vêm do log ACL de cada nó.** Veja Relatar recusas abaixo.
- **As políticas do momento presente.** Cada sincronização planeja segundo as políticas como se aplicam naquele momento, de modo que os períodos de validade começam e terminam na hora, e segundo os usuários e nós que o Redis tem.

## Implantação

1. Adicione um serviço de dados `redis` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é mostrado só uma vez; guarde-o em um arquivo que só a conta do agente possa ler.
2. Crie o usuário ACL do agente em cada nó. Ele lê usuários, define seus seletores, salva o arquivo ACL, lê o log ACL, faz testes a seco e encontra os nós do cluster. `+acl|setuser` pode se dar qualquer coisa, então proteja este usuário como a um administrador.

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. Limite as permissões raiz dos usuários de que o agente cuida ao que qualquer usuário precisa, como entrar; o resto o agente dá. Permissões raiz que dão mais são encontradas e avisadas.

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. Escreva o arquivo de configurações, substituindo os endereços, caminhos e nomes de exemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   Esses caminhos ficam na máquina do agente, e o diretório de cache precisa permitir escrita. Os arquivos de senha são lidos como UTF-8 e aparados.
5. Inicie o agente, por exemplo como serviço do systemd:

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   Substitua `<version>` pela versão da distribuição. Configurações passadas como `java -Dgrantforge.…=… -jar …` sobrepõem o arquivo. O agente para de forma limpa com SIGTERM e envia ou enfileira os eventos pendentes; o Redis mantém o que recebeu.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórios: o servidor do GrantForge, o arquivo do token de agente, um nome para este agente e um diretório com escrita para o snapshot assinado, os eventos enfileirados e a posição de leitura do log de auditoria |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública X.509 Ed25519 codificada em Base64 da página Agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.redis.nodes` | Obrigatória: os nós como `host:port`, a porta `6379` se não indicada |
| `grantforge.redis.discover` | `true` por padrão: o restante de um cluster e as réplicas de um primário são encontrados a partir dos nós indicados. `false` usa os nós indicados e nenhum outro, quando os endereços que os nós anunciam não são alcançáveis pelo agente |
| `grantforge.redis.user`, `grantforge.redis.password.file` | O usuário ACL do agente, vazio para o usuário padrão, e sua senha |
| `grantforge.redis.tls.mode`, `grantforge.redis.tls.ca.file`, `grantforge.redis.tls.certificate.file`, `grantforge.redis.tls.key.file`, `grantforge.redis.tls.key.password.file` | `disable` (padrão), `verify-ca` ou `verify-full`; os certificados CA PEM que verificam os servidores, vazio para aquilo em que o Java confia; um certificado de cliente PEM, sua chave quando em arquivo próprio, e a senha da chave |
| `grantforge.redis.timeout.ms` | Quanto podem levar a conexão e um comando, `10000` por padrão, no intervalo `1..600000` milissegundos |
| `grantforge.redis.unmanaged.users` | Usuários que o agente deixa em paz, separados por vírgulas, como contas de serviço. Ele sempre deixa em paz o próprio usuário e usuários com todos os comandos em todas as chaves |
| `grantforge.sync.interval.ms` | Com que frequência o agente coloca o Redis em sintonia, padrão `10000`, intervalo `1000..3600000` milissegundos |
| `grantforge.audit.log`, `grantforge.audit.log.interval.ms` | `true` por padrão; `false` não lê nenhum log ACL. Com que frequência os logs ACL são lidos, `5000` milissegundos por padrão |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura com o GrantForge, padrão `5000` / `8000` milissegundos |
| `grantforge.refresh.interval.ms` | Com que frequência o agente pede novas políticas, padrão `30000`, no mínimo `1000` milissegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acesso: tamanho do lote (padrão `500`), tamanho da fila (padrão `10000`), intervalo de envio (padrão `5000` milissegundos) e limite da fila em disco (padrão 64 MiB) |
| `grantforge.audit.fold.window.ms` | Por quanto tempo acessos permitidos idênticos são agrupados em um evento, padrão `10000`, até `3600000` milissegundos; `0` envia cada um |

## Relatar recusas

O Redis não guarda registro do que permitiu, então o agente relata o que recusou, a partir do log ACL de cada nó: quem teve o quê recusado, de onde e quando, e se as políticas decidem como o Redis. O Redis junta uma recusa repetida em menos de um minuto à entrada anterior; o agente relata uma entrada de novo cada vez que ela é atualizada.

| Motivo registrado | Evento de acesso |
| --- | --- |
| `key` | `read` ou `write` na chave, conforme o Redis marca o comando |
| `channel` | `subscribe` ou `publish` no canal |
| `command` | `monitor` no nó para os comandos de monitoramento; os demais comandos são registrados sem suas chaves e são omitidos |
| `auth` | Omitido: uma entrada recusada não nomeia nenhum acesso |

## Usuários, grupos e papéis

As políticas nomeiam os usuários ACL do Redis pelo nome. O Redis não tem papéis nem grupos próprios, então os grupos são aqueles em que o GrantForge coloca os usuários.

## Decisões e limitações

- Sem snapshot, o agente não muda nada. Um token vinculado a um serviço sem uso, ou a um serviço cujo tipo não é `redis`, não concede nada: o agente esvazia os seletores dos usuários de que cuida.
- Enquanto o agente não roda, o Redis mantém o que recebeu por último e as mudanças de políticas esperam. Uma mudança chega ao Redis dentro dos intervalos de atualização e sincronização.
- O agente mantém todos os seletores dos usuários de que cuida: um seletor que o operador adiciona à mão é removido na sincronização seguinte. Dê as permissões extras nas permissões raiz, ou deixe o usuário com o operador.
- A ACL do Redis nomeia chaves e canais, não bancos, então um padrão cobre suas chaves em todos os bancos de um nó.
- Um comando recusado que o usuário não pode executar de forma alguma é registrado pelo Redis sem suas chaves e não é relatado.
- O Redis não filtra nada dentro de um valor nem mascara nada.

## Guias relacionados

- [Redis](/pt-br/plugins/redis/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
