---
title: Agente Apache ZooKeeper
description: Implante o agente em cada nó do ZooKeeper para aplicar as políticas do GrantForge por meio de ACLs de marcador.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in de servidor `grantforge-plugin-zookeeper` fornece de forma unificada o tipo de serviço `zookeeper`, os recursos e a configuração de conexão. Aplicar as políticas exige ainda uma segunda peça, que roda dentro do próprio ZooKeeper: o agente, que participa das decisões de ACL dos servidores como provedor de autenticação SASL. Ele fica em `agents/grantforge-agent-zookeeper`; o comando `mvn -pl agents/grantforge-agent-zookeeper -am package` gera um único JAR shaded compatível com Java 8, com suas dependências, que a distribuição inclui em `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. A implementação é voltada às APIs do ZooKeeper 3.6–3.9; a compatibilidade com as versões reais ainda precisa ser validada. Assim, nem toda a matriz de versões passou nos testes, nem a homologação para produção foi concluída.

## Implantação

1. Instale o JAR no diretório `lib` de **cada servidor ZooKeeper**, incluindo todos os nós que podem receber solicitações de clientes. Crie **credenciais de Agent** no serviço de dados `zookeeper` correspondente do GrantForge. Salve o token em um arquivo separado que somente a conta de execução do ZooKeeper possa ler; não grave o token em si no `zoo.cfg`.
2. Adicione estas configurações ao `zoo.cfg` de cada servidor, substituindo o endereço, os caminhos e o identificador de instância do exemplo. `grantforge.server.url` (`http[s]://GrantForge`), `grantforge.token.file`, um `grantforge.cache.dir` local e `grantforge.instance` são obrigatórios. A conta de execução do ZooKeeper precisa ter permissão de escrita no diretório de cache.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. Este provedor **substitui o provedor SASL nativo**; não mantenha outro provedor registrado para o mesmo esquema `sasl` em outra entrada `authProvider.N`. `enableEagerACLCheck=false` é obrigatório: com essa verificação ativada, o registro do provedor é recusado e o marcador não permite acesso. Mantenha e verifique a autenticação normal do ZooKeeper com SASL, JAAS e Kerberos; o agente não substitui a autenticação. Reinicie os servidores seguindo o procedimento de manutenção do ensemble.
4. Opcionalmente, defina `grantforge.signing.key.file` com o caminho do arquivo que contém a chave pública de assinatura fixada como confiável. `grantforge.principal.mapping=short` é o padrão e mapeia `kafka/host@REALM` para `kafka`; `full` mantém o principal completo. Os usuários das políticas devem corresponder às identidades resultantes.

## ACLs e limitações

- Somente a ACL de marcador `sasl:grantforge` delega às políticas do GrantForge, por exemplo `sasl:grantforge:cdrwa`, em que `c=create`, `d=delete`, `r=read`, `w=write` e `a=admin`. As demais ACLs nativas, incluindo outras identidades SASL, continuam funcionando normalmente.
- O marcador recusa acesso se o agente não estiver em execução, não houver um snapshot de políticas utilizável, o `serviceType` do snapshot não for `zookeeper` ou nenhuma política corresponder/permitir o acesso. **Não é uma negação global**: outras ACLs, `world:anyone` ou uma identidade `super` ainda podem permitir a solicitação.
- As políticas comparam o caminho absoluto de destino no servidor. Para `create`/`delete`, é o znode a ser criado/excluído, não o pai que fornece a ACL. Solicitações com chroot precisam incluir o prefixo chroot real no caminho da política: `/brokers` sob o chroot `/kafka` corresponde a `/kafka/brokers`. O `create` sequencial verifica o prefixo absoluto da solicitação no servidor antes de acrescentar o número de sequência; os `read`/`delete` posteriores verificam o nome completo do nó gerado.
- As ACLs nativas não são migradas automaticamente e as ACLs não são herdadas recursivamente; novos znodes precisam de uma ACL de marcador explícita. Antes de usar `setACL`, valide permissões e recusas em um escopo pequeno e mantenha um canal de administração independente. Não aplique o marcador ao nó raiz sem uma validação cuidadosa, pois isso pode bloquear o acesso.
- Um servidor sem o agente trata `sasl:grantforge` como a identidade SASL nativa `grantforge`, em vez de avaliar políticas. Por isso, a implantação em todos os nós é obrigatória; uma implantação parcial não protege o ensemble.
- Escritas encaminhadas de um follower/observer ao leader podem não ter conexão de cliente nesta interface do provedor, portanto o contexto `clientAddress` pode estar vazio. Não se garantem políticas de ponta a ponta no ensemble baseadas no IP do cliente.
- Este provedor não consegue conhecer o resultado final da solicitação. Portanto, não envia eventos de auditoria finais `AccessEvent` nem oferece auditoria completa de acesso.
