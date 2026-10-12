---
title: Apache ZooKeeper
description: Instalar e configurar o plug-in do ZooKeeper, navegar por znodes e gerenciar políticas de acesso a caminhos de znodes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O plug-in do Apache ZooKeeper oferece ao GrantForge conexões com ensembles, busca de caminhos de znodes e gestão de políticas. O ID do plug-in e o tipo de serviço são ambos `zookeeper`.

## Instalação

A distribuição inclui o plug-in em `plugins/zookeeper`. Confirme em **Gestão da plataforma → Plug-ins** que `zookeeper` está ativado; depois de atualizá-lo, verifique o diretório de plug-ins novamente. O plug-in usa o cliente do ZooKeeper 3.9 em um carregador de classes próprio, separado das bibliotecas do servidor.

## Adicionar um serviço de dados

1. Abra **Permissões de dados → Serviços de dados**, adicione um serviço e escolha ZooKeeper (`zookeeper`).
2. Informe a string de conexão: os servidores do ensemble com um chroot opcional, como `zk1:2181,zk2:2181,zk3:2181/kafka`. O cliente alterna entre os servidores sozinho.
3. Escolha a autenticação, defina o znode de busca e teste a conexão (verifica que o znode existe e que seus filhos podem ser listados); depois salve o serviço.

| Configuração | Finalidade |
| --- | --- |
| `zookeeper.connect` | String de conexão obrigatória: `host[:port]` separados por vírgulas, seguidos opcionalmente de `/chroot`; o chroot não pode terminar em `/` |
| `authentication` | `none` (padrão), `digest` ou `kerberos` |
| `username` | Digest: o usuário digest das ACLs (sem dois-pontos); Kerberos: o principal, como `grantforge@EXAMPLE.COM` |
| `password` | Senha do usuário digest; armazenada criptografada |
| `keytab` | Kerberos: caminho do keytab do principal no servidor do GrantForge |
| `zookeeper.sasl.client.username` | Kerberos: primeira parte dos principals dos servidores, padrão `zookeeper`, como em `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos: principal completo dos servidores quando não tem essa forma, como `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | Se a porta de cliente TLS dos servidores é usada, padrão `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | Truststore que verifica os servidores; vazio, confia no que o Java do servidor confia; tipo `jks`, `pkcs12` ou `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | Keystore com o certificado de cliente, para servidores que o exigem |
| `session.timeout.ms` | Tempo limite da sessão, padrão `15000`, faixa `2000..120000` |
| `connection.timeout.ms` | Espera apenas pela conexão inicial e pela autenticação, padrão `10000` ms, faixa `1000..60000`; não limita as solicitações síncronas posteriores |
| `request.timeout.ms` | Espera máxima por uma solicitação síncrona, padrão `8000` ms, faixa `1000..60000` |
| `lookup.path` | Znode inicial de busca e navegação, abaixo do chroot, padrão `/`; por exemplo `/brokers`, com a navegação limitada a ele |
| `lookup.max.entries` | Máximo de filhos lidos de um znode de uma vez, padrão `10000`, faixa `1..100000` |

## Autenticação

- **none**: sem login; apenas znodes cuja ACL está aberta a `world:anyone` podem ser lidos.
- **digest**: faz login como `username:password`, que corresponde às identidades `digest` das ACLs do ZooKeeper. Uma senha errada não é recusada no login: é outra identidade, e os znodes protegidos aparecem como acesso negado.
- **kerberos**: faz login via SASL com um keytab; o `krb5.conf` do servidor precisa levar ao KDC. Cada principal e seu keytab recebem uma seção JAAS própria ao lado da configuração do processo, para que um mesmo GrantForge alcance vários ensembles como principals diferentes. O login Kerberos apenas com senha ainda não é suportado.

A configuração é validada ao salvar: o formato da string de conexão, usuário e senha para digest, principal e keytab para Kerberos, keytab e principal do servidor somente com Kerberos, e as faixas dos tempos limite e do limite.

Com TLS desativado, os caminhos e as senhas do truststore e do keystore devem ficar vazios. Uma senha de store exige o caminho do store correspondente. Os únicos tipos aceitos para ambos são `jks`, `pkcs12` e `pem`.

Os espaços no início e no fim das senhas dos stores TLS são preservados. A configuração do serviço define o modo TLS e os stores usados nesta conexão, substituindo as configurações de TLS e de stores do ZooKeeper já presentes na JVM; os certificados e nomes de host dos servidores continuam sendo verificados.

## Navegar por znodes

Selecione o serviço em **Permissões de dados → Políticas** e use **Navegar** ao lado de `path`:

- Os filhos são listados em páginas, por nome; um znode com filhos próprios pode ser aberto em seguida.
- Cada znode mostra sua ACL (`scheme:id:cdrwa`, como as ferramentas do ZooKeeper a escrevem), o tamanho dos dados e a última modificação.
- Um znode cuja ACL o usuário de busca não pode ler continua listado pelo nome; ao abri-lo, o motivo é informado.

Se znodes forem excluídos durante a paginação, o cursor continua avançando a partir do último nome de filho examinado, mesmo que esse nó já não exista. A lista de filhos não é um snapshot: inclusões e exclusões simultâneas seguem a semântica do ZooKeeper e podem alterar as páginas seguintes.

Ao digitar um caminho, surgem sugestões; um pai inexistente não tem nenhuma, e caminhos fora de `lookup.path` são recusados. Um znode com mais filhos que `lookup.max.entries` gera um erro em vez de mostrar só uma parte.

## Aplicar políticas

Há um único nível de recurso, `path`, e os tipos de acesso seguem as permissões de ACL do ZooKeeper: `read`, `write`, `create`, `delete` e `admin`; políticas de caminho suportam recursão e exclusões.

O plug-in do serviço gerencia, busca e navega; o agente dentro do servidor aplica políticas às ACLs de marcador por meio de um provedor de autenticação SASL. Compile com `mvn -pl agents/grantforge-agent-zookeeper -am package` para gerar um único JAR shaded compatível com Java 8, com suas dependências. A distribuição o inclui em `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. A implementação é voltada às APIs do ZooKeeper 3.6–3.9; a compatibilidade com as versões reais ainda precisa ser validada. Isso não significa que toda a matriz de versões passou nos testes nem que a homologação para produção foi concluída.

### Implantação

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

### ACLs e limitações

- Somente a ACL de marcador `sasl:grantforge` delega às políticas do GrantForge, por exemplo `sasl:grantforge:cdrwa`, em que `c=create`, `d=delete`, `r=read`, `w=write` e `a=admin`. As demais ACLs nativas, incluindo outras identidades SASL, continuam funcionando normalmente.
- O marcador recusa acesso se o agente não estiver em execução, não houver um snapshot de políticas utilizável, o `serviceType` do snapshot não for `zookeeper` ou nenhuma política corresponder/permitir o acesso. **Não é uma negação global**: outras ACLs, `world:anyone` ou uma identidade `super` ainda podem permitir a solicitação.
- As políticas comparam o caminho absoluto de destino no servidor. Para `create`/`delete`, é o znode a ser criado/excluído, não o pai que fornece a ACL. Solicitações com chroot precisam incluir o prefixo chroot real no caminho da política: `/brokers` sob o chroot `/kafka` corresponde a `/kafka/brokers`. O `create` sequencial verifica o prefixo absoluto da solicitação no servidor antes de acrescentar o número de sequência; os `read`/`delete` posteriores verificam o nome completo do nó gerado.
- As ACLs nativas não são migradas automaticamente e as ACLs não são herdadas recursivamente; novos znodes precisam de uma ACL de marcador explícita. Antes de usar `setACL`, valide permissões e recusas em um escopo pequeno e mantenha um canal de administração independente. Não aplique o marcador ao nó raiz sem uma validação cuidadosa, pois isso pode bloquear o acesso.
- Um servidor sem o agente trata `sasl:grantforge` como a identidade SASL nativa `grantforge`, em vez de avaliar políticas. Por isso, a implantação em todos os nós é obrigatória; uma implantação parcial não protege o ensemble.
- Escritas encaminhadas de um follower/observer ao leader podem não ter conexão de cliente nesta interface do provedor, portanto o contexto `clientAddress` pode estar vazio. Não se garantem políticas de ponta a ponta no ensemble baseadas no IP do cliente.
- Este provedor não consegue conhecer o resultado final da solicitação. Portanto, não envia eventos de auditoria finais `AccessEvent` nem oferece auditoria completa de acesso.

## Guias relacionados

- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
- [Desenvolvimento de plug-ins e tipos de serviço](/pt-br/develop/plugins/)
