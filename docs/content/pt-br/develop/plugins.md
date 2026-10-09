---
title: Plug-ins e tipos de serviço
description: "Estenda a gestão de políticas do GrantForge a sistemas de dados externos com plug-ins de tipo de serviço: contrato, empacotamento, isolamento e distribuição."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Um plug-in de tipo de serviço descreve um sistema externo: quais níveis de recursos ele tem, quais tipos de acesso conhece, se suporta mascaramento e filtragem de linhas, e quais configurações uma conexão exige. Com base nisso, o GrantForge oferece para esse tipo de sistema serviços de dados, um editor de políticas genérico, instantâneos de políticas e auditoria de acessos (ver [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)).

## Dependências

O plug-in depende apenas de `grantforge-plugin-api` (que, por sua vez, depende apenas do JDK e do JSpecify), introduzido com o escopo `provided`:

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## Implementar ServiceTypeProvider

```java
public final class ExampleProvider implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("example").label("Example warehouse")
                .resources(ResourceDefinition.builder("database").label("Database").lookupSupported(true).validLeaf(true)
                                .excludesSupported(false).build(),
                        ResourceDefinition.builder("table").label("Table").parent("database").lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder("column").label("Column").parent("table").accessTypes("select").build(),
                        ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "select", "update"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", "redact({col})"))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .configFields(ConfigField.builder("url").label("Address").type(ConfigFieldType.STRING).mandatory().pattern("example://.+").build(),
                        ConfigField.builder("timeout").label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return "example".equals(config.get("password")) ? ConnectionResult.succeeded()
                : ConnectionResult.failed("the example warehouse refused the password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        // devolve os valores candidatos abaixo do nível request.resource() que comecem com request.userInput(), no máximo request.limit()
        return List.of();
    }
}
```

O trecho acima foi retirado do plug-in de exemplo `plugins/grantforge-plugin-example` e pode ser copiado diretamente como modelo.

A definição é validada de uma só vez na construção e relata todos os problemas de uma vez: pais desconhecidos ou cíclicos, nomes repetidos, referências a tipos de acesso ou recursos não declarados etc. Os nomes precisam corresponder a `[a-z][a-z0-9_-]{0,63}`.

| Componente | Descrição |
| --- | --- |
| Recursos | Hierarquia, forma de comparação (exata, curingas, caminho, expressão regular), se diferencia maiúsculas de minúsculas, se é obrigatório, se suporta exclusões e recursão (apenas em caminhos), se suporta busca, se pode ser folha |
| Tipos de acesso | Nome, nome de exibição, os outros tipos de acesso que implica (por exemplo `all` implica `select`); pode ser limitado a recursos |
| Mascaramento, filtragem de linhas | Declara quais recursos os suportam e quais formas de mascaramento existem; a execução ocorre no sistema de destino |
| Condições | Condições que podem ser anexadas a uma política (por exemplo um intervalo de IP), avaliadas pelo SPI de condições do mecanismo de políticas |
| Campos de configuração | String, texto longo, inteiro, booleano, segredo, enumeração; campos de segredo são salvos criptografados e não podem ter valor padrão |

O provedor precisa de um construtor público sem parâmetros e tem de ser thread-safe. `validateConfig`, `testConnection` e `lookup` têm implementações padrão: sobrescreva-as conforme necessário.

### Quando uma busca falha

Quando `lookup` falhar, lance uma `LookupException` com um motivo: o console mostra o motivo e permite tentar de novo, em vez de mostrar que não há valores. Use uma única linha, sem segredos; o servidor também oculta nela as configurações secretas do serviço.

- `NOT_FOUND`: o lugar onde buscar não existe, como o diretório de busca configurado
- `ACCESS_DENIED`: o sistema de destino recusou o usuário de busca
- `UNREACHABLE`: não é possível alcançar o sistema de destino
- `AUTHENTICATION_FAILED`: o login no sistema de destino falhou
- `LIMIT_EXCEEDED`: há valores demais; a busca precisa ser restringida
- `INVALID_INPUT`: a entrada não pode ser buscada, como um caminho fora do diretório permitido
- `FAILED`: qualquer outra falha

Qualquer outra exceção lançada por um plug-in é tratada como `FAILED`, então plug-ins feitos para a API 1.0 não precisam de mudanças. `LookupException` está disponível desde a API 1.1.0.

## Descritor e empacotamento

Coloque `grantforge-plugin.yaml` no diretório raiz do plug-in:

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.1"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

Um plug-in pode ser:

- um jar, com o descritor no diretório raiz do jar;
- um diretório ou um zip: `grantforge-plugin.yaml`, `classes/` e `lib/*.jar`.

Coloque-o em `grantforge.plugins.directory` (por padrão `plugins`) e clique em "Verificar novamente" na página "Plug-ins" do console; não é preciso reiniciar.

Quando o plug-in tem dependências, empacote-o com assembly como um zip da classificação `plugin`, como em `plugins/grantforge-plugin-hdfs` (descritor no nível superior, `classes/`, `lib/`), e copie as dependências de runtime para `target/plugin-lib` na fase `generate-resources`:

```xml
<plugin>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>plugin-lib</id>
      <phase>generate-resources</phase>
      <goals><goal>copy-dependencies</goal></goals>
      <configuration>
        <includeScope>runtime</includeScope>
        <outputDirectory>${project.build.directory}/plugin-lib</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Ao iniciar a partir do código-fonte

Quando você inicia `org.devlive.grantforge.server.GrantForge` diretamente na IDE, as classes do servidor vêm dos `target/classes` dos vários módulos. Nesse momento, se `grantforge.plugins.directory` não estiver configurado e não existir um diretório `plugins` no diretório de trabalho, é usado o diretório `plugins/` do repositório: os módulos de plug-in já compilados que lá estão (os que têm um descritor em `target/classes` e um `target/plugin-lib` gerado pela compilação) são carregados diretamente como plug-ins, com as classes de `target/classes` e as dependências de `target/plugin-lib`; módulos que não geram `plugin-lib` (como o plug-in de exemplo usado nos testes) não são carregados. Depois de alterar o código de um plug-in, deixe a IDE recompilá-lo e clique em "Verificar novamente" na página "Plug-ins" do console para que a alteração surta efeito. Antes de usar um módulo de plug-in pela primeira vez, compile-o uma vez com o Maven para copiar as dependências:

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## Compatibilidade

O `apiVersion` declara a versão do contrato de que o plug-in precisa. O host atualmente fornece `1.1.0`; só são carregados plug-ins cuja versão principal coincida e cuja versão disponível não seja inferior à exigida; caso contrário, são marcados como "incompatíveis". Cada mudança no contrato eleva a versão, e o CI a compara com o japicmp contra a última versão publicada (`script/ci/check_plugin_api_compat.py`); mudanças incompatíveis precisam elevar a versão principal.

O GrantForge 2026.1.0 fornece a API de plug-ins 1.1.0. Um plug-in que declara `apiVersion: "1.1"` precisa de um host 2026.1.0 ou mais novo; um que declara `1.0` funciona sem mudanças no novo host. A versão do produto e a da API de plug-ins são independentes: a versão da API só sobe quando o contrato muda.

## Isolamento

- Cada plug-in tem seu próprio carregador de classes, cujo pai é o carregador de classes da plataforma, e apenas `org.devlive.grantforge.plugin.api.` e `org.jspecify.annotations.` são delegados ao host; o plug-in não vê as classes do Spring nem as do servidor e pode trazer dependências em qualquer versão.
- Falha de leitura, versão incompatível, duplicidade, exceção no construtor ou estouro de tempo limite apenas marcam aquele plug-in como falho, registrando o motivo; o servidor continua funcionando normalmente.
- Cada chamada a um plug-in tem tempo limite (`grantforge.plugins.call-timeout`, 10 segundos por padrão).

## Agentes e instantâneos

O código-fonte dos plug-ins de tipo de serviço fica em `plugins/` e é carregado pelo servidor GrantForge; o código-fonte dos agentes concretos fica em `agents/` e, depois de empacotado, é implantado nos sistemas de destino, por exemplo `agents/grantforge-agent-hdfs-*` no NameNode do HDFS. A infraestrutura de agentes compartilhada fica em `core/grantforge-agent-core`.

Os agentes nos sistemas de destino acessam `/api/v1/agent/**` com um token de agente:

| Endpoint | Função |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | Relata o estado do agente e a versão atual do instantâneo |
| `GET /api/v1/agent/policies` | Baixa o instantâneo de políticas; retorna 304 quando não houve mudança; o cabeçalho da resposta traz a assinatura Ed25519 |
| `GET /api/v1/agent/signing-key` | A chave pública para verificar a assinatura |
| `POST /api/v1/agent/access-events` | Reporta eventos de acesso em lote, que vão para a auditoria de acessos |

Os agentes avaliam localmente com o `grantforge-policy-engine` (uma API Java 8, que pode ser embutida em sistemas mais antigos), sem precisar chamar o GrantForge a cada acesso.

Os agentes não precisam implementar esses protocolos por conta própria: o `core/grantforge-agent-core` (Java 8) já os encapsula:

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // avaliadores de condições, por nome

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- Envia heartbeats no intervalo exigido pelo servidor; quando a versão das políticas muda, baixa o instantâneo (304 se o ETag não mudou) e verifica a assinatura com a chave pública Ed25519 do servidor (a chave pública pode ser fixada nas configurações; caso contrário, é obtida do servidor no primeiro contato e memorizada). Somente com a verificação aprovada a cópia é substituída e salva no diretório de cache; se o servidor estiver inacessível na inicialização, o último instantâneo continua em uso.
- O instantâneo expande papéis e grupos até os usuários, e `decide` adiciona à requisição os papéis e grupos do usuário no instantâneo. Com o serviço desativado ou ainda sem instantâneo, o resultado é `NOT_DETERMINED`, e o agente decide se recua para as verificações do próprio sistema ou se nega.
- Os eventos de acesso entram em uma fila limitada (quando cheia, são descartados e contados, mas nunca bloqueiam o sistema) e são enviados em lotes; com o servidor inacessível, são gravados em `audit-spool/`, dentro do diretório de cache, e reenviados na recuperação, descartando os mais antigos acima do limite.
- Depende de Jackson 2 e Bouncy Castle (Ed25519 não existe antes do JDK 15); como o sistema de destino traz outras versões dessas bibliotecas, o agente precisa relocalizá-las com shade no empacotamento.

## Exemplo

`plugins/grantforge-plugin-example` é um plug-in completo: o tipo `example` (database → table → column e path), os tipos de acesso select, update e all, mascaramento de colunas, filtragem de linhas de tabela, uma condição de intervalo de IP e as configurações url, timeout e password (o teste de conexão é bem-sucedido quando a senha é `example`), além de busca de bancos e tabelas de exemplo. O teste end-to-end full-stack percorre com ele todo o caminho: "adicionar serviço → escrever política → emitir token → o agente baixa → auditoria de acessos".
