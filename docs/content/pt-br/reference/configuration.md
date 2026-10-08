---
title: Referência de configuração
description: Todas as opções de configuração, seus valores padrão e as variáveis de ambiente correspondentes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

A configuração pode ser escrita em `configure/application.properties` ou sobrescrita por variáveis de ambiente. As regras de vinculação flexível do Spring Boot também se aplicam: `grantforge.security.mfa.step-up-window` pode ser escrita como a variável de ambiente `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`. As durações são escritas em formas como `30m`, `12h` ou `90d`.

## Serviço e banco de dados

| Opção de configuração | Variável de ambiente | Valor padrão | Explicação |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | Porta HTTP |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | banco de dados de arquivo H2 embutido | Endereço JDBC; consulte [bancos de dados](/pt-br/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | Usuário do banco de dados |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | vazia | Senha do banco de dados |
| — | `GRANTFORGE_HOME` | diretório de instalação | Diretório onde ficam os dados do H2 e os registros |
| — | `GRANTFORGE_ID_NODE` | automático | Número de nó único por instância dentro do cluster (0–1023) |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | Tamanho máximo do arquivo de importação CSV |

## Inicialização e registro

| Opção de configuração | Valor padrão | Explicação |
| --- | --- | --- |
| `grantforge.setup.token` | vazio | Token de inicialização fixo (`GRANTFORGE_SETUP_TOKEN`); quando vazio, um token aleatório é gerado e impresso no registro |
| `grantforge.security.registration-enabled` | `false` | Se visitantes podem se registrar sozinhos |
| `grantforge.security.registration-tenant` | `default` | Tenant a que pertencem as contas de registro próprio |

## Senhas e bloqueio

| Opção de configuração | Valor padrão | Explicação |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | Comprimento mínimo, de no mínimo 8 |
| `grantforge.security.password.max-length` | `128` | Comprimento máximo, de no máximo 1024 |
| `grantforge.security.password.required-character-classes` | `1` | Número de classes de caractere que devem ser misturadas (minúsculas, maiúsculas, dígitos, outras), 1–4 |
| `grantforge.security.password.history-size` | `0` | A senha nova não pode coincidir com as últimas N senhas, 0–24 |
| `grantforge.security.password.max-age` | sem expiração | Validade da senha; ao expirar, é preciso alterá-la no login |
| `grantforge.security.lockout.max-attempts` | `5` | Número de falhas consecutivas após as quais ocorre o bloqueio |
| `grantforge.security.lockout.duration` | `15m` | Duração do bloqueio |

A senha não pode conter o nome de usuário.

## Sessões e cookies

| Opção de configuração | Valor padrão | Explicação |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | Tempo de inatividade após o qual a sessão expira (`GRANTFORGE_SESSION_TIMEOUT`) |
| `grantforge.security.sessions.max-per-account` | `0` | Número máximo de sessões simultâneas por conta; 0 significa sem limite |
| `grantforge.security.sessions.activity-interval` | `1m` | Intervalo com que a última atividade da sessão é registrada |
| `grantforge.security.cookie-secure` | `false` | Defina como `true` quando o TLS for encerrado no proxy (`GRANTFORGE_COOKIE_SECURE`) |

## Autenticação de dois fatores

| Opção de configuração | Valor padrão | Explicação |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | Por quanto tempo uma autenticação de dois fatores cobre as operações sensíveis, de 1 minuto a 12 horas |
| `grantforge.security.mfa.required-for-sensitive` | `false` | Se as operações sensíveis exigem que a conta tenha a autenticação de dois fatores ativada |

## Criptografia e servidor de autorização

| Opção de configuração | Valor padrão | Explicação |
| --- | --- | --- |
| `grantforge.security.encryption-key` | gerada automaticamente | Chave Base64 de 32 bytes com a qual os segredos armazenados são criptografados; em produção, é indispensável defini-la |
| `grantforge.oauth.issuer` | endereço da requisição | Emissor do OIDC, por exemplo `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | Período de rotação automática das chaves de assinatura; 0 desativa |
| `grantforge.oauth.signing-key-retention` | `2d` | Tempo durante o qual as chaves antigas continuam sendo publicadas; deve ser maior que a validade de qualquer token |

## Auditoria, plug-ins e agentes

| Opção de configuração | Valor padrão | Explicação |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | Tempo de retenção dos registros de auditoria |
| `grantforge.audit.archive-directory` | vazio | Diretório para o qual as auditorias expiradas são arquivadas antes de serem excluídas |
| `grantforge.access-audit.retention` | `90d` | Tempo de retenção da auditoria de acessos reportada pelos agentes |
| `grantforge.plugins.directory` | `plugins` | Diretório de plug-ins |
| `grantforge.plugins.call-timeout` | `10s` | Tempo limite das chamadas a plug-ins (testar a conexão, localizar recursos) |
| `grantforge.agents.refresh-interval` | `30s` | Intervalo recomendado com que os agentes buscam as políticas |

## Observabilidade

| Opção de configuração | Valor padrão | Explicação |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | Se `/actuator/prometheus` é acessível sem login (`GRANTFORGE_PROMETHEUS_PUBLIC`) |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Defina como `ecs` ou `logstash` para emitir registros em JSON |
