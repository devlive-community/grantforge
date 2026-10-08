---
title: Desenvolvimento, testes e CI
description: Compilação local, testes, normas de código e verificações de CI.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Ambiente

- JDK 21 (os artefatos são bytecode Java 17; o mecanismo de políticas é Java 8)
- Node.js 22 e pnpm 8.10.2
- Docker (testes de integração de bancos de dados, benchmarks de desempenho e imagens)
- Python 3.12 (scripts de CI)

## Comandos frequentes

```bash
./mvnw verify                          # compila e testa todos os módulos Java (incluindo o console)
./mvnw verify -DskipFrontend           # pula a compilação do console
bash script/ci/web.sh test             # testes unitários do console
bash script/ci/web.sh e2e              # testes de navegador do console (backend simulado)
bash script/ci/e2e_fullstack.sh        # empacota, inicia o serviço real e executa os testes full-stack
bash script/ci/db_integration.sh postgres:17   # executa os testes de integração no banco de dados indicado
bash script/ci/perf_benchmark.sh smoke # benchmark de desempenho em pequena escala
```

Para desenvolver o console, execute `pnpm dev` (`core/grantforge-web`); o Vite encaminha as requisições de `/api` e afins para o serviço em `localhost:9999`.

## Iniciar pela IDE

Execute diretamente `org.devlive.grantforge.server.GrantForge` (módulo `grantforge-server`); por padrão, usa o banco de dados H2. O servidor carrega automaticamente os módulos de plug-in já compilados que estiverem em `plugins/` do repositório (ver [Plug-ins e tipos de serviço](/pt-br/develop/plugins/)); antes de usar um módulo de plug-in pela primeira vez, execute uma vez `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` para copiar suas dependências.

## Normas de código

- Backend: Error Prone + NullAway (não nulo por padrão; nos pontos anuláveis, `@Nullable` do JSpecify), Checkstyle, PMD e SpotBugs; o ArchUnit protege as convenções comuns (sem injeção por campos, sem SQL nativo, entidades não aparecem na API, `Optional.get()` não é permitido, etc.).
- Frontend: modo estrito do TypeScript e ESLint sem avisos; todos os textos passam por i18n, as chaves precisam ser literais e as chaves em chinês e em inglês coincidem exatamente.
- Todo arquivo-fonte tem o cabeçalho de licença MIT; toda classe de código principal tem sua classe de teste correspondente (as exceções estão registradas em `script/ci/test_mapping_exclusions.txt`).
- Os limites de cobertura são definidos por módulo (`script/ci/coverage_thresholds.txt`).
- As migrações de banco de dados são YAML do Liquibase, uma mudança por arquivo, e só podem ser adicionadas, nunca modificadas; os tipos usam propriedades que funcionam em todos os bancos, como `${text}`.
- As mensagens de commit seguem Conventional Commits, e o título não passa de 72 caracteres.

## CI

| Job | Conteúdo |
| --- | --- |
| Repository hygiene | Cabeçalhos de licença, caminhos proibidos, mapeamento de testes, i18n, catálogo de permissões, formato de arquivos e verificações de scripts e workflows |
| Commit messages | Formato das mensagens de commit |
| CI script unit tests | Testes unitários dos próprios scripts de CI |
| Java 17 / 21 / 25 / latest | Compilação e teste de todos os módulos Java, verificação da versão do bytecode |
| Java static analysis | Cobertura, Checkstyle, SpotBugs e PMD |
| Frontend | Coerência dos tipos da API com o contrato, verificação de tipos e compilação, ESLint, testes unitários e testes de navegador |
| JavaScript SDK | Verificação de tipos, compilação, ESLint e testes unitários |
| Database | Migrações e testes de integração em H2, PostgreSQL 14/17, MySQL 8.0/8.4, MariaDB 10.11/11.4, Oracle 23 e SQL Server 2022 |
| Plugin API compatibility | Comparação do contrato de plug-ins com a última versão publicada |
| Full-stack acceptance | Empacotamento, inicialização do serviço e execução dos testes de navegador full-stack sobre PostgreSQL |
| Docs | Verificação, testes e compilação do site de documentação |

Todas as noites também são executados benchmarks de desempenho; o workflow de segurança analisa dependências e segredos.

## Publicação

O número de versão é `ano.versão secundária.revisão` (por exemplo `2026.0.0`), e as versões candidatas recebem `-rc.N`. As versões em todos os pom, nos pacotes npm, no appVersion do Helm Chart, na barra lateral do console e no README precisam coincidir, e o CI verifica isso com `check_versions.py`.

Para publicar a partir da branch `dev`, um único comando:

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # apenas verifica e pré-visualiza as notas da versão, sem fazer alterações
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # define a versão, cria a tag v2026.1.0 e a envia, e move o dev para a versão seguinte
```

O script exige que a área de trabalho esteja limpa, que a branch local não esteja atrás do remoto e que a tag não exista; após a confirmação, ele cria o commit `chore(release): prepare <versão>`, cria a tag anotada e a envia. A tag dispara `release.yml`:

- `script/ci/release.sh` compila o pacote de lançamento, uma SBOM CycloneDX apenas com as dependências de publicação e `SHA256SUMS`;
- imagens multiarquitetura são enviadas para `ghcr.io/devlive-community/grantforge`;
- os artefatos Maven (incluindo o pacote de fontes e o Javadoc) são publicados no GitHub Packages; quando o repositório tem `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (token do Central Portal), `GPG_PRIVATE_KEY` e `GPG_PASSPHRASE` configurados, eles são assinados e publicados no Maven Central;
- é criada uma GitHub Release cujo corpo reúne todos os commits desde a última versão publicada (uma tag `v*` ou numérica, como `1.0.6`), agrupados por novas funcionalidades, correções, desempenho etc., com links para os commits.

Versões candidatas são marcadas como pré-lançamento e não atualizam o `latest` das imagens. Com o profile `central` ativado localmente, por padrão nada é publicado (`central.skip=true`); apenas o workflow de publicação passa explicitamente `-Dcentral.skip=false`.

## Catálogo de permissões

As páginas e os botões do console, e as API de que precisam, são declarados em `core/grantforge-web/src/permissions/`. O `check_permission_manifest.py` garante que cada API declarada existe e que cada interface que exige permissão está coberta por algum botão ou página (as exceções de chamada direta estão registradas em `script/ci/permission_direct_apis.txt`).

## Documentação

Este site fica em `docs/` e usa exportação estática do Next.js e Tailwind CSS:

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # verificação de páginas, links e imagens
pnpm build      # saída em docs/out
```

As páginas são Markdown em `docs/content/`, e a navegação fica em `docs/lib/navigation.ts`. A referência da API e os códigos de erro são gerados na compilação a partir do contrato e do código-fonte. As capturas de tela são geradas pelo `script/docs/screenshots.sh`, que inicia o serviço real, grava dados de exemplo e depois usa o Playwright.
