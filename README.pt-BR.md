<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="Logo do GrantForge" />

# GrantForge

Plataforma unificada de permissões · usuários, papéis, menus, APIs, linhas e campos de dados · sistemas externos de dados

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Español](README.es.md) · Português · [Italiano](README.it.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.1.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

O GrantForge (antes AuthX) é uma plataforma unificada de permissões de código aberto (MIT). Ele responde, em um único lugar, a duas perguntas: **quem pode fazer o quê** (permissão funcional) e **quem pode ver quais dados** (permissão sobre dados e campos). As permissões são definidas, explicadas e auditadas no console, as aplicações de negócio se integram por protocolos padrão, e os sistemas externos de dados (HDFS, por exemplo) entram no mesmo modelo de políticas por meio de plug-ins e agentes.

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="Console do GrantForge" />
</p>

## Funcionalidades

| Área | O que faz |
| --- | --- |
| Identidade e organização | Multi-tenant, árvore de departamentos, grupos e cargos; importação e exportação em massa por CSV; login e sincronização com LDAP / Active Directory, federação OIDC |
| Segurança das contas | Gerenciamento de sessões e encerramento forçado, política de senhas com bloqueio por tentativas, autenticação de dois fatores com TOTP e códigos de recuperação, segunda verificação nas operações sensíveis |
| Permissão funcional | Catálogo de recursos (módulos, menus, páginas, abas, botões, APIs), herança entre papéis, matriz de permissões, análise de impacto antes de conceder |
| Permissões sobre os dados | Visibilidade das linhas limitada por condições (o próprio usuário, o departamento e seus descendentes, departamentos designados, condições personalizadas), com leitura e escrita controladas separadamente |
| Permissões sobre os campos | Um campo pode ser ocultado, mascarado (e-mail, número de telefone, número de documento) ou marcado como somente leitura |
| Explicabilidade e auditoria | Explicação das permissões (de onde vem cada uma), simulação de uma concessão, consulta e exportação do log de auditoria |
| Governança | Restrições de separação de funções (SOD), solicitação de acesso com aprovação, revisão periódica dos acessos |
| Integração de aplicações | Servidor de autorização OAuth 2.1 / OIDC, API aberta de consulta de permissões, SDKs em Java (Spring Boot Starter) e JavaScript |
| Sistemas externos | Tipos de serviço por plug-in e motor de políticas: serviços de dados, políticas de acesso, agentes e auditoria de acessos |
| Entrega | Uma única versão publicada executável, imagem Docker, exemplos de Compose, chart do Helm; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

O suporte a sistemas externos entrega o framework de plug-ins, o editor de políticas, a distribuição assinada, a auditoria de acessos, o tipo de serviço HDFS e agentes NameNode numerados para Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 e 3.5. O plug-in do Hive ainda está em desenvolvimento.

## Versões de destino dos agentes HDFS

| Base do Hadoop | Java do contêiner | Diretório do agente |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8 (imagem amd64) | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

Escolha `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` em `agents/hdfs/<line>/` conforme a linha Hadoop do cluster. O código compartilhado de enforcement é compilado para Java 8 e o adaptador 3.5 para Java 17.

O Hadoop 2.7, 2.10, 3.2 e 3.3 não oferece o callback de autorização de superusuário usado pelo agente. O Hadoop mantém o controle desses acessos de superusuário; use usuários comuns para o acesso a dados governado pelo GrantForge.

## Como funciona: dois planos

- **Plano de administração**: o servidor do GrantForge (Spring Boot 4.1, bytecode de Java 17) e o console Vue 3 concentram tenants, contas, organização, papéis, concessões, auditoria, serviços de dados e políticas.
- **Plano de dados**: agentes embutidos no sistema protegido. Um agente baixa com seu token instantâneos de política assinados com Ed25519 e os guarda em cache local, decide cada acesso antes que ele aconteça (negando quando nenhuma política está disponível) e devolve os eventos de acesso para auditoria.

Seu sistema não precisa seguir o padrão do HDFS. Aplicações comuns avaliam as permissões dentro do próprio processo pela API aberta ou pelo Spring Boot starter; apenas sistemas que precisam interceptar o acesso dentro de um banco de dados, sistema de arquivos ou armazenamento semelhante exigem um agente escrito contra `core/grantforge-agent-core`.

## Integrando sua aplicação

- **OAuth 2.1 / OpenID Connect**: o GrantForge é ele mesmo um servidor de autorização, então as aplicações fazem login de seus usuários nele; fontes de identidade existentes (LDAP / AD / OIDC) também podem ser conectadas.
- **Aplicações Java**: o `sdk/grantforge-spring-boot-starter` adiciona `@RequirePermission` nos endpoints, `@GrantForgeEntity` nas entidades de dados e `GrantForgeDataScopes.scope(...)` para transformar as permissões sobre dados da plataforma em `Specification`s JPA.
- **Aplicações front-end**: o `@grantforge/client` faz o login do usuário com OIDC + PKCE a partir da sua própria origem e consulta suas permissões.
- **API aberta**: `/api/v1/open/me/authorization`, `/api/v1/open/me/data-access`, `/api/v1/open/catalog/data-entities`.
- **Exemplos executáveis**: `samples/shop` e `samples/notes` se integram como faria um terceiro.

## Início rápido

É preciso Java 17 ou mais recente. O serviço escuta na porta `9999` e imprime na primeira execução um **token de inicialização** de uso único; abra <http://127.0.0.1:9999/> no navegador, informe o token e crie o primeiro administrador.

```bash
# A partir da versão publicada (ou compile do código-fonte com ./mvnw clean package, saída em dist/)
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# Ou com Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# Ou com Compose contra um banco de dados
docker compose -f deploy/compose/postgres.yml up -d

# Ou no Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

O banco de arquivos H2 embutido é o padrão, então nenhuma configuração é necessária para iniciar. O driver do MySQL não é distribuído com a versão publicada por causa de sua licença GPL; coloque-o você mesmo em `drivers/`. A instalação, a configuração da primeira execução e a primeira concessão de permissão estão na [documentação](https://grantforge.devlive.org).

## Bancos de dados

O padrão é o banco de arquivos H2 embutido (`${GRANTFORGE_HOME}/data`), então nenhuma configuração é necessária para iniciar. Em produção a troca é feita por variáveis de ambiente e o esquema de tabelas é gerenciado pelo Liquibase:

| Banco de dados | Versões (verificadas na CI) | Exemplo de `GRANTFORGE_DB_URL` |
| --- | --- | --- |
| PostgreSQL | 14, 17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0, 8.4 | `jdbc:mysql://host:3306/grantforge` (coloque você mesmo o `mysql-connector-j` em `lib/`; sua licença GPL o mantém fora da versão publicada) |
| MariaDB | 10.11, 11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

Defina também `GRANTFORGE_DB_USER` e `GRANTFORGE_DB_PASSWORD`; em uma instalação em cluster cada instância deve receber seu próprio `GRANTFORGE_ID_NODE` (0-1023).

## Estrutura do projeto

Coordenada Maven raiz: `org.devlive.grantforge:grantforge:2026.0.0`. Prefixo dos pacotes Java: `org.devlive.grantforge`. Classe principal: `org.devlive.grantforge.server.GrantForge`.

O `core/` contém o servidor e a infraestrutura compartilhada, o `plugins/` contém os plug-ins de tipos de serviço carregados pelo servidor, e o `agents/` contém os agentes instalados dentro dos sistemas que protegem. A biblioteca compartilhada `grantforge-agent-core` fica no `core/`; o agente NameNode do HDFS está em `agents/grantforge-agent-hdfs-*`.

| Módulo | Responsabilidade |
| --- | --- |
| `core/grantforge-server` | Ponto de entrada do Spring Boot: API REST, configuração de segurança, API aberta, e ele hospeda o console web |
| `core/grantforge-web` | Console em Vue 3 / TypeScript / Tailwind CSS |
| `core/grantforge-common` | Códigos de erro e problem details, CSV, anotações de acesso nos endpoints |
| `core/grantforge-persistence` | Entidades, filtro por tenant, TSID, Liquibase, SPI de permissões sobre dados e campos |
| `core/grantforge-audit` | Registro, consulta, retenção e arquivamento dos eventos de auditoria |
| `core/grantforge-identity` | Tenants, contas, departamentos, grupos, cargos, login e sessões, autenticação de dois fatores, fontes de identidade |
| `core/grantforge-authz` | Catálogo de recursos, papéis, permissões, atribuições e avaliação, políticas sobre dados e campos, separação de funções, solicitações e revisões |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | Contrato dos plug-ins de tipos de serviço, além de carregamento, isolamento e invocação dos plug-ins |
| `core/grantforge-policy-engine` | Motor de avaliação de políticas para sistemas externos (API de Java 8, embutível em agentes) |
| `core/grantforge-agent-core` | Código compartilhado dos agentes: ajustes, instantâneos assinados, decisões de acesso, envio de auditoria |
| `core/grantforge-service` | Serviços de dados, assinatura e distribuição de instantâneos de política, agentes e auditoria de acessos |
| `core/grantforge-oauth` | Servidor OAuth 2.1 / OIDC construído sobre o Spring Authorization Server |
| `plugins/grantforge-plugin-hdfs` | Plug-in do tipo de serviço HDFS: gestão de políticas e consulta de recursos |
| `plugins/grantforge-plugin-example` | Plug-in de exemplo para um tipo de serviço personalizado |
| `agents/grantforge-agent-hdfs-common` | Enforcement compartilhado do HDFS, configuração, instantâneos e auditoria (Java 8) |
| `agents/grantforge-agent-hdfs-native` | Módulo Maven de produção dos adaptadores HDFS nativos compartilhados (base Java 8 / Hadoop 2.7.7) |
| `agents/grantforge-agent-hdfs-*` | Agentes NameNode numerados para Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 e 3.5: autorização e auditoria de acessos |
| `sdk/grantforge-spring-boot-starter`, `sdk/grantforge-js` | SDKs em Java e JavaScript para integrar aplicações |
| `script/ci`, `deploy/` | Scripts de verificação da CI (os mesmos localmente e na CI) e recursos de implantação (Dockerfile, Compose, Helm) |

## Operação e observabilidade

- Sondas de saúde: `/actuator/health/liveness`, `/actuator/health/readiness` (apenas o status, sem detalhes; a sonda de prontidão devolve 200 quando o banco de dados está alcançável e as migrações já rodaram).
- Métricas: `/actuator/prometheus` (com o rótulo `application="grantforge"`, exigem login por padrão; `GRANTFORGE_PROMETHEUS_PUBLIC=true` as abre para redes confiáveis).
- Logs: por padrão texto legível, com um ID de requisição em cada linha; defina `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (ou `logstash`) para obter logs em JSON.
- Scripts da versão publicada: `bin/startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` e `import-legacy.sh`.

## Desenvolvimento e verificação

A compilação exige JDK 17 ou mais recente. O servidor e o adaptador do Hadoop 3.5 miram Java 17; o motor de políticas, o núcleo do agente e os adaptadores Hadoop 2.7–3.4 miram Java 8. Error Prone + NullAway se ativam a partir do JDK 21. O front-end usa Vue 3.5, Tailwind CSS 4, Node.js 22.12+ e pnpm 8.10.2.

```sh
# Compilação Java e testes unitários (sem compilar o console)
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# Testes de integração de persistência em um banco específico (precisa de Docker, exceto para h2)
bash script/ci/db_integration.sh postgres:17

# Empacotamento da versão publicada (incluindo a compilação do console), saída em dist/
./mvnw clean package

# Desenvolvimento e verificações do front-end
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# Aplicações de exemplo e SDKs
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# Contrato da API: regenere openapi.json e os tipos do front-end após mudar o servidor (a CI verifica os dois)
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# Site de documentação (docs/, Next.js + Tailwind CSS)
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # regenere as capturas de tela com um serviço real e dados de exemplo

# Verificações do repositório (as mesmas que a CI roda)
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## Links

- [Repositório](https://github.com/devlive-community/grantforge)
- [Documentação](https://grantforge.devlive.org): início rápido, guia de uso, integração e referências técnicas, com as fontes em [`docs/`](docs/)
- [Como contribuir](CONTRIBUTING.md) · [Código de conduta](CODE_OF_CONDUCT.md) · [Registro de alterações](CHANGELOG)
