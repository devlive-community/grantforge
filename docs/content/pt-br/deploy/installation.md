---
title: Instalar o pacote de lançamento
description: Instala, inicia, para e atualiza o pacote de lançamento do GrantForge em uma máquina física ou virtual.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Requisitos de ambiente

| Item | Requisito |
| --- | --- |
| Java | 17 ou superior (o pacote de lançamento é compilado com Java 17; recomenda-se o 21) |
| Memória | 1 GB no mínimo; para produção, 2 GB ou mais |
| Banco de dados | H2 integrado para experimentar; em produção, PostgreSQL, MySQL, MariaDB, Oracle ou SQL Server; consulte [Bancos de dados](/pt-br/deploy/databases/) |
| Navegadores | Chrome, Edge, Firefox ou Safari em suas duas últimas versões principais |

## Estrutura de diretórios

Ao descompactar `grantforge-release.tar.gz`, obtém-se o diretório `grantforge/`:

| Diretório | Conteúdo |
| --- | --- |
| `bin/` | `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` e `import-legacy.sh` |
| `configure/` | `application.properties`, onde se sobrescrevem as configurações padrão |
| `lib/` | os jars do servidor e suas dependências |
| `drivers/` | drivers JDBC adicionais (o de MySQL precisa ser adicionado à mão) |
| `plugins/` | plug-ins de tipo serviço; consulte [Plug-ins e tipos de serviço](/pt-br/develop/plugins/) |
| `agents/` | os jars de agente que são implantados nos sistemas de destino, como o [agente NameNode do Apache Hadoop HDFS](/pt-br/external/hdfs-agent/) |
| `data/` | o arquivo do banco de dados H2 integrado (criado no primeiro início) |
| `logs/` | `grantforge.log`; `console.out` guarda a saída anterior à inicialização do sistema de registro |

## Iniciar e parar

```bash
bin/startup.sh     # inicia em segundo plano e escreve o pid no arquivo de pid
bin/shutdown.sh    # para com elegância usando o arquivo de pid
bin/restart.sh     # para e volta a iniciar
bin/debug.sh       # roda em primeiro plano, com os registros também no console; Ctrl+C o interrompe
```

Os scripts podem ser executados de qualquer diretório: o diretório de instalação é o que está um nível acima do diretório do script, mas também pode ser indicado pela variável de ambiente `GRANTFORGE_HOME`.

## Escolher o banco de dados

Por padrão, é usado o banco de dados de arquivo H2 em `data/grantforge`, que basta para experimentar. Em produção, indique o banco de dados em `configure/application.properties` ou por variáveis de ambiente:

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

Na primeira conexão, o GrantForge cria todas as tabelas com o Liquibase; depois, cada início executa as migrações que ainda faltarem.

## Primeira inicialização

No primeiro início, é impresso no log um token de inicialização de uso único: abra o console, informe o token e crie o primeiro administrador. O processo está em [Configuração em cinco minutos](/pt-br/start/quick-start/).

## Verificação de saúde e monitoramento

| Endereço | Uso |
| --- | --- |
| `/actuator/health/liveness` | sonda de vida |
| `/actuator/health/readiness` | sonda de prontidão: devolve 200 quando o banco de dados está disponível e as migrações foram aplicadas |
| `/actuator/prometheus` | métricas do Prometheus; por padrão exige início de sessão, mas pode ser aberto a uma rede confiável com `GRANTFORGE_PROMETHEUS_PUBLIC=true` |

Se precisar de registros estruturados, defina `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (ou `logstash`). Cada linha traz o ID da requisição, que corresponde ao `requestId` das respostas de erro das interfaces.

## Implantação em cluster

Várias instâncias podem compartilhar um mesmo banco de dados e atender ao mesmo tempo: as sessões são guardadas no banco de dados, então qualquer instância pode tratar qualquer requisição. Cada instância precisa de um `GRANTFORGE_ID_NODE` diferente (de 0 a 1023), que decide o número de nó usado ao gerar os IDs. O balanceador de carga não precisa de afinidade de sessão.

## Atualizar

Pare o serviço, substitua o `lib/` antigo pelo da nova versão (mantendo `configure/`, `data/`, `drivers/` e `plugins/`), inicie novamente e as migrações do banco de dados serão executadas automaticamente. Faça uma cópia de segurança do banco de dados antes de atualizar. Para migrar da versão 1.x, consulte [Atualizar e migrar de versões antigas](/pt-br/deploy/upgrade/).
